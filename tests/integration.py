"""Live API regression against the local coursework environment.

Creates named demo accounts and courses; never runs against an arbitrary URL.
Passwords, tokens and full answers are excluded from public results.
"""
import concurrent.futures
import datetime as dt
import json
import secrets
import time
import urllib.error
import urllib.request
from pathlib import Path

from fixture_guard import require_fixture_environment

require_fixture_environment(Path(__file__).resolve().parents[1])

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:8080'
PRIVATE = ROOT / '.local'
RESULTS = ROOT / 'evidence' / 'api-tests.json'
cfg = json.loads((PRIVATE / 'runtime.json').read_text(encoding='utf-8-sig'))
results = []
fixtures = {}


def request(path, token=None, method='GET', body=None, expected=200, headers=None, base=BASE):
    h = {'Accept': 'application/json', **(headers or {})}
    if token:
        h['Authorization'] = 'Bearer ' + token
    if body is not None:
        h['Content-Type'] = 'application/json'
    req = urllib.request.Request(base + path, headers=h, method=method,
                                 data=None if body is None else json.dumps(body, ensure_ascii=False).encode())
    try:
        with urllib.request.urlopen(req, timeout=12) as response:
            status = response.status
            raw = response.read()
    except urllib.error.HTTPError as e:
        status, raw = e.code, e.read()
    data = json.loads(raw)
    assert status == expected, (path, status, expected, data.get('code'), data.get('message'))
    return data.get('data'), data.get('traceId')


def get(path, token):
    return request(path, token)[0]


def call(path, token, method='POST', body=None, expected=200):
    return request(path, token, method, body, expected)[0]


def login(name, password, expected=200):
    data, _ = request('/api/auth/login', method='POST', body={'username': name, 'password': password}, expected=expected)
    return data


def record(case, detail, fn):
    started = time.monotonic()
    try:
        fn()
        entry = {'id': case, 'result': 'PASS', 'detail': detail}
    except Exception as e:
        entry = {'id': case, 'result': 'FAIL', 'detail': detail, 'error': str(e)}
        results.append(entry)
        save()
        raise
    entry['seconds'] = round(time.monotonic() - started, 3)
    results.append(entry)
    save()
    print(case, 'PASS', flush=True)


def save():
    RESULTS.parent.mkdir(parents=True, exist_ok=True)
    RESULTS.write_text(json.dumps({'at': dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat(),
                                  'base': BASE, 'cases': results}, ensure_ascii=False, indent=2), encoding='utf-8')


def course(token, name):
    c = call('/api/teacher/courses', token, body={'name': name, 'summary': '以作品细读、意象辨析与文字表达为线索，学习古典文学。', 'category': '古代文学'})
    h = call('/api/teacher/courses/' + c['id'] + '/chapters', token, body={'title': '诗歌中的意象与情感', 'sortNo': 1, 'content': '阅读诗歌时，可以先观察景物，再思考情感如何由景物展开。\n\n意象不是孤立的名词。月、流水与远山在不同语境中，可能承载思念、时间或行旅经验。分析时应结合词语、句式与作品整体，避免把一种景物固定解释为一种情绪。\n\n本章练习：选择一段自己熟悉的诗歌，用自己的语言说明画面、情感与表达之间的关系。'})
    return c, h


def run():
    admin_data = login('admin', cfg['HW_ADMIN_PASSWORD'])
    admin = admin_data['accessToken']
    account_file = PRIVATE / 'demo-accounts.json'
    accounts = json.loads(account_file.read_text(encoding='utf-8')) if account_file.exists() else []
    if not accounts:
        for username, display, role in [('lincheng', '林澄', 'TEACHER'), ('shenyan', '沈砚', 'TEACHER'), ('lihe', '李禾', 'STUDENT'), ('zhouyan', '周言', 'STUDENT')]:
            password = secrets.token_urlsafe(18) + 'Aa9!'
            u = call('/api/admin/users', admin, body={'username': username, 'password': password, 'displayName': display, 'role': role})
            accounts.append({'username': username, 'password': password, 'user': u})
        account_file.write_text(json.dumps(accounts, ensure_ascii=False, indent=2), encoding='utf-8')
    run_name = 'access' + secrets.token_hex(4)
    sessions = [login(a['username'], a['password']) for a in accounts]
    teacher, teacher2, student, student2 = [s['accessToken'] for s in sessions]
    ta, tb, sa, sb = [a['user']['id'] for a in accounts]
    record('TC01', '管理员、两名教师及两名学生真实登录，角色来自服务端', lambda: check([s['user']['role'] for s in sessions] == ['TEACHER', 'TEACHER', 'STUDENT', 'STUDENT']))
    record('TC02', '错误密码和无凭证请求均401', lambda: (login('lincheng', 'wrong-password', 401), request('/api/users/me', expected=401)))
    record('TC03', '学生访问用户管理403', lambda: request('/api/admin/users', student, expected=403))
    extra_password = secrets.token_urlsafe(18) + 'Aa9!'
    extra = call('/api/admin/users', admin, body={'username': run_name, 'password': extra_password, 'displayName': '权限验证账号', 'role': 'STUDENT'})
    old_extra = login(run_name, extra_password)['accessToken']
    record('TC04', '禁用后登录和旧凭证均401', lambda: (call('/api/admin/users/' + extra['id'] + '/access', admin, 'PUT', {'role': 'STUDENT', 'status': 'DISABLED'}), login(run_name, extra_password, 401), request('/api/users/me', old_extra, expected=401)))
    c, h = course(teacher, '中国古代文学导读')
    c2, h2 = course(teacher2, '诗词意象与表达')
    call('/api/teacher/courses/' + c2['id'] + '/publish', teacher2)
    draft, draft_h = course(teacher, '古典散文阅读 · 验证草稿')
    record('TC05', '建课、编写章节、发布后学生可读简介', lambda: (call('/api/teacher/courses/' + c['id'] + '/publish', teacher), get('/api/courses/' + c['id'], student)))
    record('TC06', '另一教师不能修改课程和章节', lambda: (call('/api/teacher/courses/' + c['id'], teacher2, 'PUT', {'name': '越权修改', 'summary': '', 'category': '古代文学'}, 403), call('/api/teacher/courses/' + c['id'] + '/chapters/' + h['id'], teacher2, 'PUT', {'title': '越权', 'content': '越权', 'sortNo': 1}, 403)))
    enrollment = call('/api/enrollments', student, body={'courseId': c['id']})
    record('TC07', '本人加入记录出现在本人列表', lambda: check(get('/api/enrollments/me?courseId=' + c['id'], student)['items'][0]['id'] == enrollment['id']))
    def concurrent_join():
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            entries = list(pool.map(lambda _: call('/api/enrollments', student, body={'courseId': c['id']}), range(6)))
        check({e['id'] for e in entries} == {enrollment['id']})
        check(get('/api/enrollments/me?courseId=' + c['id'], student)['total'] == 1)
    record('TC08', '六个并发重复加入只保留一个记录', concurrent_join)
    record('TC09', '草稿课程拒绝新加入', lambda: call('/api/enrollments', student, body={'courseId': draft['id']}, expected=409))
    record('TC10', '完成本人章节成功，跨课程章节拒绝', lambda: (call('/api/enrollments/' + enrollment['id'] + '/chapters/' + h['id'] + '/complete', student, 'PUT'), call('/api/enrollments/' + enrollment['id'] + '/chapters/' + h2['id'] + '/complete', student, 'PUT', expected=404)))
    task = call('/api/teacher/assignments', teacher, body={'courseId': c['id'], 'title': '文学作品阅读分析', 'description': '选择一段诗文，结合画面、词语与表达方式，分析作品的情感。请以自己的语言写出观察与理解。', 'maxScore': '100.00', 'deadlineAt': None})
    record('TC11', '所属教师可发布作业，另一教师403', lambda: (call('/api/teacher/assignments/' + task['id'] + '/publish', teacher2, expected=403), call('/api/teacher/assignments/' + task['id'] + '/publish', teacher)))
    answer = '我认为诗文中的景物并不是情感的简单替代。作品先呈现空间和时间，再由人物的观察把外部画面与内心经验相连。分析时，需要同时注意词语的色彩、句式的节奏和语境中的变化，才能理解具体的情感。'
    submission = call('/api/assignments/' + task['id'] + '/submissions', student, body={'answerText': answer})
    record('TC12', '截止前修改保留同一提交且版本递增', lambda: check(call('/api/submissions/' + submission['id'], student, 'PUT', {'answerText': answer + '\n我会进一步用作品中的具体词语支持判断。', 'expectedVersion': 0})['version'] == 1))
    expired = call('/api/teacher/assignments', teacher, body={'courseId': c['id'], 'title': '截止时间验证', 'description': '验证截止后拒绝提交。', 'maxScore': '10.00', 'deadlineAt': (dt.datetime.now(dt.timezone(dt.timedelta(hours=8))) + dt.timedelta(seconds=4)).isoformat()})
    call('/api/teacher/assignments/' + expired['id'] + '/publish', teacher)
    time.sleep(4)
    record('TC13', '未加入学生403，截止后提交409', lambda: (call('/api/assignments/' + task['id'] + '/submissions', student2, body={'answerText': '未加入'}, expected=403), call('/api/assignments/' + expired['id'] + '/submissions', student, body={'answerText': '超时'}, expected=409)))
    def concurrent_grade():
        def execute(which):
            path = '/api/submissions/' + submission['id'] + ('/grade' if which == 'grade' else '')
            token = teacher if which == 'grade' else student
            body = {'score': '83.00', 'comment': '分析方向清楚，建议引用具体词语说明意象的变化。', 'expectedVersion': 1} if which == 'grade' else {'answerText': answer + '\n并发修改验证。', 'expectedVersion': 1}
            try:
                request(path, token, 'PUT', body)
                return 200
            except AssertionError as e:
                check(e.args[0][1] == 409)
                return 409
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            statuses = list(pool.map(execute, ['grade', 'modify']))
        check(sorted(statuses) == [200, 409])
        current = get('/api/submissions/' + submission['id'], teacher)
        if current['status'] != 'GRADED':
            call('/api/submissions/' + submission['id'] + '/grade', teacher, 'PUT', {'score': '83.00', 'comment': '分析方向清楚，建议引用具体词语说明意象的变化。', 'expectedVersion': current['version']})
        check(get('/api/submissions/' + submission['id'], student)['status'] == 'GRADED')
    record('TC14', '评分与答案修改并发：一个成功、一个409，评分对应锁定版本', concurrent_grade)
    current = get('/api/submissions/' + submission['id'], teacher)
    record('TC15', '负分、超过满分和三位小数被拒绝；合法83分已保存', lambda: ([call('/api/submissions/' + submission['id'] + '/grade', teacher, 'PUT', {'score': score, 'comment': None, 'expectedVersion': current['version']}, 400) for score in ['-1', '100.01', '83.001']], check(current['grade']['score'] == '83.00')))
    record('TC16', '学生与教师归属隔离，已评分答案锁定', lambda: (request('/api/submissions/' + submission['id'], student2, expected=403), call('/api/submissions/' + submission['id'], student, 'PUT', {'answerText': '评分后修改', 'expectedVersion': current['version']}, 409), call('/api/submissions/' + submission['id'] + '/grade', teacher2, 'PUT', {'score': '80', 'comment': '', 'expectedVersion': current['version']}, 403)))
    record('TC17', 'Eureka真实注册及Gateway业务路由', lambda: (check_eureka(), check(get('/api/courses', student)['total'] >= 2)))
    _, trace = request('/api/enrollments/' + enrollment['id'] + '/progress', student)
    record('TC18', '真实资格校验，跨服务共用traceId', lambda: check_logs(trace, ['learning-service-8103', 'user-service-8101']))
    def instances():
        calls = [request('/api/teacher/diagnostics/course-instance', teacher) for _ in range(12)]
        ports = {data['port'] for data, _ in calls}
        check(ports == {8102, 8105})
        for data, trace in calls:
            check(data['traceId'] == trace)
            check_logs(trace, ['course-service-' + str(data['port'])])
    record('TC19', '消费者经服务名访问两课程实例，核对实际处理端口和日志', instances)
    record('AT01', '退出、禁用、密码重置与角色变更使旧凭证立即失效', lambda: auth_revocation(admin, extra, extra_password))
    record('AT02', '伪造身份/内部头不提权，直连业务也认证', lambda: (request('/api/admin/users', student, expected=403, headers={'X-Role': 'ADMIN', 'X-User-Id': '1', 'X-Service-Key': 'forged'}), request('/api/courses', expected=401, base='http://127.0.0.1:8102'), request('/internal/instance', teacher, expected=403, base='http://127.0.0.1:8102')))
    record('AT03', '两个并发请求均不能禁用最后启用管理员', lambda: last_admin(admin, admin_data['user']['id']))
    record('AT04', '已有课程/加入记录的账号拒绝不兼容角色修改', lambda: (call('/api/admin/users/' + ta + '/access', admin, 'PUT', {'role': 'STUDENT', 'status': 'ENABLED'}, 409), call('/api/admin/users/' + sa + '/access', admin, 'PUT', {'role': 'TEACHER', 'status': 'ENABLED'}, 409)))
    record('AT05', '发布后章节增删重排被拒，正文可改', lambda: (call('/api/teacher/courses/' + c['id'] + '/chapters', teacher, body={'title': '新增', 'content': '新增', 'sortNo': 2}, expected=409), call('/api/teacher/courses/' + c['id'] + '/chapters/' + h['id'], teacher, 'DELETE', expected=409), call('/api/teacher/courses/' + c['id'] + '/chapters/' + h['id'], teacher, 'PUT', {'title': h['title'], 'content': h['content'], 'sortNo': 2}, 409), call('/api/teacher/courses/' + c['id'] + '/chapters/' + h['id'], teacher, 'PUT', {'title': h['title'], 'content': h['content'], 'sortNo': 1})))
    before = get('/api/enrollments/' + enrollment['id'] + '/progress', student)
    record('AT06', '重复完成保留首次时间，不重复累计', lambda: (call('/api/enrollments/' + enrollment['id'] + '/chapters/' + h['id'] + '/complete', student, 'PUT'), check(get('/api/enrollments/' + enrollment['id'] + '/progress', student) == before)))
    record('AT07', '发布后关键字段冻结，关闭作业拒绝提交', lambda: freeze_task(teacher, student, task, c))
    def correction():
        old = get('/api/submissions/' + submission['id'], teacher)
        g = call('/api/submissions/' + submission['id'] + '/grade', teacher, 'PUT', {'score': '86.00', 'comment': '更正验证：表达更完整。', 'expectedVersion': old['version']})
        check(g['submissionVersion'] == old['version'] + 1)
        call('/api/submissions/' + submission['id'] + '/grade', teacher, 'PUT', {'score': '90.00', 'comment': '', 'expectedVersion': old['version']}, 409)
        fresh = get('/api/submissions/' + submission['id'], teacher)
        check(fresh['answerText'] == old['answerText'])
        call('/api/submissions/' + submission['id'] + '/grade', teacher, 'PUT', {'score': '83.00', 'comment': '分析方向清楚，建议引用具体词语说明意象的变化。', 'expectedVersion': fresh['version']})
    record('AT08', '更正更新当前评分与版本，旧版本409，答案不变', correction)
    record('AT09', '他人详情被拒，成绩列表只含本人提交', lambda: (request('/api/submissions/' + submission['id'], student2, expected=403), check(get('/api/grades/me?assignmentId=' + task['id'], student2)['items'] == [])))
    record('AT10', '草稿及未加入章节受限，已发布简介可读', lambda: (request('/api/courses/' + draft['id'], student, expected=403), request('/api/courses/' + c['id'] + '/chapters', student2, expected=403), get('/api/courses/' + c['id'], student2)))
    record('AT11', '本人提交按作业在服务端定位，未评分为null，重复首次提交409', lambda: filter_submission(teacher, student, student2, c, task, submission))
    record('AT12', '归档历史可读，新增完成拒绝，既有答案仍可评分', lambda: archived(teacher, student, student2))
    record('AT13', '本人加入详情与课程筛选，另一学生403，章节拼接404', lambda: (check(get('/api/enrollments/' + enrollment['id'], student)['courseId'] == c['id']), request('/api/enrollments/' + enrollment['id'], student2, expected=403), check(get('/api/enrollments/me?courseId=' + c['id'], student)['total'] == 1), call('/api/enrollments/' + enrollment['id'] + '/chapters/' + h2['id'] + '/complete', student, 'PUT', expected=404)))
    fixtures.update({'mainCourseId': c['id'], 'mainChapterId': h['id'], 'mainEnrollmentId': enrollment['id'], 'mainTaskId': task['id'], 'mainSubmissionId': submission['id'], 'teacherId': ta, 'studentId': sa})
    (PRIVATE / 'integration-fixture.json').write_text(json.dumps(fixtures, indent=2), encoding='utf-8')
    record('TC20', '停止两课程实例时拒绝提交，恢复后经真实请求成功', lambda: fault_recovery(teacher, student, c))
    print('API suite complete; TC21 SQL restore is validated separately.', flush=True)


def check(condition):
    assert condition, 'Expected invariant did not hold'


def check_eureka():
    req = urllib.request.Request('http://127.0.0.1:8761/eureka/apps', headers={'Accept': 'application/json'})
    with urllib.request.urlopen(req) as r:
        apps = {a['name']: len(a['instance']) for a in json.load(r)['applications']['application']}
    check(all(name in apps for name in ['HW-GATEWAY', 'HW-USER-SERVICE', 'HW-COURSE-SERVICE', 'HW-LEARNING-SERVICE', 'HW-ASSIGNMENT-SERVICE']))
    check(apps['HW-COURSE-SERVICE'] == 2)


def check_logs(trace, names):
    for name in names:
        path = ROOT / 'evidence' / 'runtime-logs' / (name + '.stdout.log')
        check(trace in path.read_text(encoding='utf-8', errors='replace'))


def last_admin(token, id):
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(lambda _: call('/api/admin/users/' + id + '/access', token, 'PUT', {'role': 'ADMIN', 'status': 'DISABLED'}, 409), range(2)))


def auth_revocation(admin, extra, original_password):
    call('/api/admin/users/' + extra['id'] + '/access', admin, 'PUT', {'role': 'STUDENT', 'status': 'ENABLED'})
    token = login(extra['username'], original_password)['accessToken']
    call('/api/auth/logout', token)
    request('/api/users/me', token, expected=401)
    token = login(extra['username'], original_password)['accessToken']
    new = secrets.token_urlsafe(18) + 'Aa9!'
    call('/api/admin/users/' + extra['id'] + '/password-reset', admin, body={'newPassword': new})
    request('/api/users/me', token, expected=401)
    token = login(extra['username'], new)['accessToken']
    call('/api/admin/users/' + extra['id'] + '/access', admin, 'PUT', {'role': 'TEACHER', 'status': 'ENABLED'})
    request('/api/users/me', token, expected=401)


def freeze_task(teacher, student, task, c):
    call('/api/teacher/assignments/' + task['id'], teacher, 'PUT', {'title': '改变题目', 'description': '改变', 'maxScore': '50.00', 'deadlineAt': None}, 409)
    t = call('/api/teacher/assignments', teacher, body={'courseId': c['id'], 'title': '关闭作业验证', 'description': '用于验证关闭后的写入限制。', 'maxScore': '100.00', 'deadlineAt': None})
    call('/api/teacher/assignments/' + t['id'] + '/publish', teacher)
    call('/api/teacher/assignments/' + t['id'] + '/close', teacher)
    call('/api/assignments/' + t['id'] + '/submissions', student, body={'answerText': '关闭后提交'}, expected=409)


def filter_submission(teacher, student, student2, c, main_task, main_submission):
    for i in range(11):
        t = call('/api/teacher/assignments', teacher, body={'courseId': c['id'], 'title': '分页验证 ' + str(i+1), 'description': '本题用于定位本人提交，不作为成绩总评。', 'maxScore': '100.00', 'deadlineAt': None})
        call('/api/teacher/assignments/' + t['id'] + '/publish', teacher)
        call('/api/assignments/' + t['id'] + '/submissions', student, body={'answerText': '分页定位测试答案。'})
    own = get('/api/grades/me?assignmentId=' + main_task['id'], student)
    check(own['total'] == 1 and own['items'][0]['submissionId'] == main_submission['id'])
    check(get('/api/grades/me?assignmentId=' + t['id'], student)['items'][0]['score'] is None)
    check(get('/api/grades/me?assignmentId=' + main_task['id'], student2)['items'] == [])
    call('/api/assignments/' + main_task['id'] + '/submissions', student, body={'answerText': '重复首次提交'}, expected=409)


def archived(teacher, student, student2):
    c, first = course(teacher, '归档历史验证')
    second = call('/api/teacher/courses/' + c['id'] + '/chapters', teacher, body={'title': '散文的叙述节奏', 'content': '阅读散文时，可观察语句长短与叙述视角如何配合情绪。', 'sortNo': 2})
    call('/api/teacher/courses/' + c['id'] + '/publish', teacher)
    e = call('/api/enrollments', student, body={'courseId': c['id']})
    call('/api/enrollments/' + e['id'] + '/chapters/' + first['id'] + '/complete', student, 'PUT')
    t = call('/api/teacher/assignments', teacher, body={'courseId': c['id'], 'title': '归档评分验证', 'description': '归档后保留既有答卷。', 'maxScore': '100.00', 'deadlineAt': None})
    call('/api/teacher/assignments/' + t['id'] + '/publish', teacher)
    s = call('/api/assignments/' + t['id'] + '/submissions', student, body={'answerText': '归档前保存的答卷。'})
    call('/api/teacher/courses/' + c['id'] + '/archive', teacher)
    get('/api/courses/' + c['id'] + '/chapters/' + first['id'], student)
    call('/api/enrollments/' + e['id'] + '/chapters/' + second['id'] + '/complete', student, 'PUT', expected=409)
    call('/api/enrollments/' + e['id'] + '/chapters/' + first['id'] + '/complete', student, 'PUT')
    call('/api/enrollments', student2, body={'courseId': c['id']}, expected=409)
    call('/api/submissions/' + s['id'], student, 'PUT', {'answerText': '归档后修改', 'expectedVersion': 0}, 409)
    call('/api/submissions/' + s['id'] + '/grade', teacher, 'PUT', {'score': '80.00', 'comment': None, 'expectedVersion': 0})
    call('/api/teacher/courses/' + c['id'] + '/chapters/' + first['id'], teacher, 'PUT', {'title': first['title'], 'content': first['content'], 'sortNo': 1}, 409)


def fault_recovery(teacher, student, c):
    import os
    import signal
    import subprocess
    records_path = PRIVATE / 'processes.json'
    processes = json.loads(records_path.read_text(encoding='utf-8-sig'))
    records = [p for p in processes if p['module'] == 'course-service']
    check({p['port'] for p in records} == {8102, 8105})
    task = call('/api/teacher/assignments', teacher, body={'courseId': c['id'], 'title': '服务恢复验证', 'description': '下游故障不允许绕过资格校验。', 'maxScore': '100.00', 'deadlineAt': None})
    call('/api/teacher/assignments/' + task['id'] + '/publish', teacher)
    for record in records:
        # PIDs originate from this project's reviewed launcher; no broad process termination.
        command = subprocess.run(['powershell', '-NoProfile', '-Command', f"(Get-CimInstance Win32_Process -Filter 'ProcessId={record['pid']}').CommandLine"], capture_output=True, text=True).stdout
        check(str(ROOT) in command and 'course-service-1.0.0.jar' in command)
        os.kill(record['pid'], signal.SIGTERM)
    try:
        for _ in range(4):
            call('/api/assignments/' + task['id'] + '/submissions', student, body={'answerText': '服务故障期间不得写入'}, expected=503)
        check(get('/api/grades/me?assignmentId=' + task['id'], student)['items'] == [])
    finally:
        env = dict(os.environ)
        env.update({k: str(v) for k,v in cfg.items()})
        import winreg
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r'SYSTEM\CurrentControlSet\Control\Session Manager\Environment') as key:
            java_home = winreg.QueryValueEx(key, 'JAVA_HOME')[0]
        java = Path(java_home) / 'bin/java.exe'
        for record in records:
            jar = ROOT / 'backend/course-service/target/course-service-1.0.0.jar'
            log_dir = ROOT / 'evidence/runtime-logs'
            with (log_dir / f"course-service-{record['port']}.stdout.log").open('ab') as stdout, (log_dir / f"course-service-{record['port']}.stderr.log").open('ab') as stderr:
                proc = subprocess.Popen([str(java), '-Xms64m', '-Xmx256m', '-jar', str(jar), f"--server.port={record['port']}"], env=env, stdout=stdout, stderr=stderr, creationflags=subprocess.CREATE_NO_WINDOW)
            record['pid'] = proc.pid
            record['started'] = dt.datetime.now().astimezone().isoformat()
        records_path.write_text(json.dumps(processes, indent=2), encoding='utf-8')
        for record in records:
            for _ in range(45):
                try:
                    with urllib.request.urlopen(f"http://127.0.0.1:{record['port']}/actuator/health", timeout=2) as r:
                        if json.load(r)['status'] == 'UP':break
                except Exception:pass
                time.sleep(1)
            else:raise AssertionError('Course instance did not recover')
    time.sleep(7)
    recovered = call('/api/assignments/' + task['id'] + '/submissions', student, body={'answerText': '恢复后通过真实资格校验再提交。'})
    check(recovered['version'] == 0)


if __name__ == '__main__':
    run()
