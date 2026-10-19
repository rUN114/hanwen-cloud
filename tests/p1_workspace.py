"""Create isolated P1 fixtures and check the real services used by the course workspace.
No existing student submissions or completion records are modified.
"""
import datetime as dt, json, os, secrets, urllib.error, urllib.request
from pathlib import Path

from fixture_guard import require_fixture_environment

require_fixture_environment(Path(__file__).resolve().parents[1])

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / '.local'
BASE = os.environ.get('HW_TEST_BASE', 'http://127.0.0.1:8088')
assert BASE in ('http://127.0.0.1:8080', 'http://127.0.0.1:8088')
cfg = json.loads((PRIVATE / 'runtime.json').read_text(encoding='utf-8-sig'))
accounts = json.loads((PRIVATE / 'demo-accounts.json').read_text())
cases = []
def request(path, token=None, body=None, method=None, expected=200):
    headers = {'Accept': 'application/json', 'Origin': 'http://127.0.0.1:5173'}
    if token: headers['Authorization'] = 'Bearer ' + token
    if body is not None: headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(BASE + path, headers=headers, data=None if body is None else json.dumps(body, ensure_ascii=False).encode(), method=method or ('POST' if body is not None else 'GET'))
    try:
        with urllib.request.urlopen(req, timeout=30) as r: status, raw = r.status, r.read()
    except urllib.error.HTTPError as e: status, raw = e.code, e.read()
    data = json.loads(raw); assert status == expected, (path, status, expected, data.get('code'))
    return data.get('data')
def login(username, password): return request('/api/auth/login', body={'username': username, 'password': password})['accessToken']
def assert_equal(actual, expected): assert actual == expected, (actual, expected)
def check(name, fn):
    fn(); cases.append({'name': name, 'result': 'PASS'})
    (ROOT / 'evidence/p1-api-tests.json').write_text(json.dumps({'at': dt.datetime.now().astimezone().isoformat(), 'checks': cases, 'scope': 'P1工作台复用接口的实际测试，所有写入仅为新建且标注的P1验证记录'}, ensure_ascii=False, indent=2), encoding='utf8')
stamp = secrets.token_hex(4)
admin = login('admin', cfg['HW_ADMIN_PASSWORD'])
teacher = login(accounts[0]['username'], accounts[0]['password'])
student_row = request('/api/admin/users/import', admin, {'role': 'STUDENT', 'rows': [{'username': 'p1_' + stamp, 'displayName': 'P1书案验证同窗'}]})['items'][0]
student = login(student_row['username'], student_row['initialPassword'])
course = request('/api/teacher/courses', teacher, {'name': 'P1课程书案验证 · 月色与诗心 ' + stamp, 'category': '古典文学', 'summary': '本课是独立的工作台验证课程。阅读三章原文导引，完成章节练习，撰写赏析习作并查看反馈。所有答卷与分数均为验证示例。'})
cid = course['id']; chapters = []
for i, title in enumerate(['月色中的故乡', '春声中的景与情', '诗歌中的意象与表达'], 1):
    chapters.append(request('/api/teacher/courses/' + cid + '/chapters', teacher, {'title': title, 'sortNo': i, 'content': 'P1独立验证导引。请从字句、意象与语境理解诗歌，阅读后确认本章完成。此文本为项目自编测试内容。'}))
request('/api/teacher/courses/' + cid + '/publish', teacher, method='POST')
check('未选课学生不能读取章节和习作', lambda: (request('/api/courses/' + cid + '/chapters', student, expected=403), request('/api/courses/' + cid + '/assignments', student, expected=403)))
enrollment = request('/api/enrollments', student, {'courseId': cid})
eid = enrollment['id']
check('初始进度为真实零完成，三个章节按序返回', lambda: (assert_equal(request('/api/enrollments/' + eid + '/progress', student)['completedCount'], 0), assert_equal([c['sortNo'] for c in request('/api/courses/' + cid + '/chapters', student)], [1,2,3])))
check('其他学生不能读取新选课的进度', lambda: request('/api/enrollments/' + eid + '/progress', login(accounts[2]['username'], accounts[2]['password']), expected=403))
quiz = request('/api/teacher/courses/' + cid + '/quizzes', teacher, {'chapterId': chapters[0]['id'], 'title': '月色意象小试', 'instructions': '这是一份工作台验证练习。'})
request('/api/teacher/quizzes/' + quiz['id'] + '/questions', teacher, {'prompt': '阅读赏析时应优先结合哪一项？', 'optionA': '字句与语境', 'optionB': '封面颜色', 'optionC': '账号顺序', 'optionD': '按钮数量', 'correctOption': 'A', 'explanation': '项目自编验证题。', 'sortNo': 1, 'expectedVersion': 0})
request('/api/teacher/quizzes/' + quiz['id'] + '/status', teacher, {'status': 'PUBLISHED', 'expectedVersion': 1}, method='PUT')
check('本章练习查询正确且未交卷不返回答案', lambda: (assert_equal(request('/api/courses/' + cid + '/quizzes?chapterId=' + chapters[0]['id'], student)['total'], 1), assert_equal('correctOption' in request('/api/quizzes/' + quiz['id'], student)['questions'][0], False)))
deadline = (dt.datetime.now(dt.timezone(dt.timedelta(hours=8))) + dt.timedelta(hours=6)).isoformat()
tasks = []
for name in ['待写赏析', '已交待阅', '已评习作', '已关闭习作']:
    t = request('/api/teacher/assignments', teacher, {'courseId': cid, 'title': 'P1验证 · ' + name, 'description': '请结合文本说明自己的理解。此任务仅用于工作台流程验收。', 'maxScore': '100', 'deadlineAt': deadline})
    request('/api/teacher/assignments/' + t['id'] + '/publish', teacher, method='POST'); tasks.append(t)
for t in tasks[1:3]:
    s = request('/api/assignments/' + t['id'] + '/submissions', student, {'answerText': 'P1验证答卷：从字句与语境出发理解诗歌。'})
    if t == tasks[2]: request('/api/submissions/' + s['id'] + '/grade', teacher, {'score': '88', 'comment': 'P1验证评语：观点明确，继续补充文本依据。', 'expectedVersion': s['version']}, method='PUT')
request('/api/teacher/assignments/' + tasks[3]['id'] + '/close', teacher, method='POST')
check('工作台能分别读取未提交、待评分、已评分与关闭记录', lambda: (assert_equal(request('/api/courses/' + cid + '/assignments?size=100', student)['total'], 4), assert_equal(sorted(g['status'] for g in request('/api/grades/me?courseId=' + cid + '&size=100', student)['items']), ['GRADED', 'SUBMITTED'])))
notice = request('/api/teacher/courses/' + cid + '/notices', teacher, {'title': 'P1验证来笺', 'content': '读后先做月色练习，再写赏析。分数和完成记录均为测试示例。', 'pinned': True})
check('公告预览来自课程真实记录', lambda: assert_equal(request('/api/courses/' + cid + '/notices?size=3', student)['items'][0]['id'], notice['id']))
fixture = {'courseId': cid, 'enrollmentId': eid, 'chapters': chapters, 'quizId': quiz['id'], 'tasks': tasks, 'username': student_row['username'], 'password': student_row['initialPassword']}
(PRIVATE / 'p1-fixtures.json').write_text(json.dumps(fixture, ensure_ascii=False, indent=2), encoding='utf8')
print('P1 actual API checks:', len(cases), 'PASS; isolated fixtures saved privately.')
