"""V3 live integration tests. Uses dedicated local services and named new fixtures."""
import integration as it
import concurrent.futures
import datetime as dt
import hashlib
import io
import json
import secrets
import struct
import urllib.parse
import urllib.request
import urllib.error
import wave
import zlib

raw_request = it.request
it.request = lambda path, *a, **k: raw_request(urllib.parse.quote(path, safe='/?&=:%+'), *a, **k)
it.RESULTS = it.ROOT / 'evidence/v3-api-tests.json'
accounts = json.loads((it.PRIVATE / 'demo-accounts.json').read_text(encoding='utf8'))
sessions = [it.login(a['username'], a['password']) for a in accounts]
t, t2, s, s2 = [x['accessToken'] for x in sessions]
c, h = it.course(t, 'V3教学闭环验证 · ' + secrets.token_hex(3))
other, oh = it.course(t2, 'V3权限边界验证 · ' + secrets.token_hex(3))
cid, hid = c['id'], h['id']
it.fixtures = {'courseId': cid, 'chapterId': hid}
def record(n, text, fn): it.record('V3-' + str(n).zfill(2), text, fn)
def deny(path, token, method='GET', body=None, status=403): return it.request(path, token, method, body, status)
def upload(name, data, token=t, chapter=hid, expected=200):
    boundary = 'hanwen' + secrets.token_hex(12)
    fields = {'title': 'V3资源验证 ' + name, 'description': '自动验证文件，非课程正式教材。'}
    if chapter is not None: fields['chapterId'] = chapter
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode() for key,value in fields.items()]
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode() + data + b'\r\n')
    parts.append(f'--{boundary}--\r\n'.encode())
    req=urllib.request.Request(it.BASE+'/api/teacher/courses/'+cid+'/resources', data=b''.join(parts), method='POST', headers={'Authorization':'Bearer '+token,'Content-Type':'multipart/form-data; boundary='+boundary})
    try:
        with urllib.request.urlopen(req,timeout=20) as res: status, raw=res.status,res.read()
    except urllib.error.HTTPError as e: status,raw=e.code,e.read()
    parsed=json.loads(raw); assert status==expected,(name,status,expected,parsed.get('message'))
    return parsed.get('data')
def download(resource, token, expected=200):
    req=urllib.request.Request(it.BASE+'/api/resources/'+resource['id']+'/file',headers={'Authorization':'Bearer '+token})
    try:
        with urllib.request.urlopen(req,timeout=12) as res: status,raw,mime=res.status,res.read(),res.headers.get('Content-Type')
    except urllib.error.HTTPError as e: status,raw,mime=e.code,e.read(),e.headers.get('Content-Type')
    assert status==expected,(status,expected)
    return raw,mime

record(1,'未公开课程不能建班、另一教师不能建本课班级',lambda:(deny('/api/teacher/classes',t,'POST',{'courseId':cid,'name':'未公开','semester':'2026秋'},409),deny('/api/teacher/classes',t2,'POST',{'courseId':cid,'name':'越权','semester':'2026秋'})))
it.call('/api/teacher/courses/'+cid+'/publish',t)
it.call('/api/teacher/courses/'+other['id']+'/publish',t2)
room=it.call('/api/teacher/classes',t,body={'courseId':cid,'name':'诗文细读甲班','semester':'2026秋','description':'V3流程验证班级'})
rid=room['id']; it.fixtures['classId']=rid
record(2,'班级带邀请口令和课程名，学生未加入不可查看',lambda:(it.check(len(room['joinCode'])==18 and room['courseName']==c['name']),deny('/api/classes/'+rid,s)))
def invalid_import():
    before=it.get('/api/teacher/classes/'+rid+'/members',t)['total']
    deny('/api/teacher/classes/'+rid+'/members',t,'POST',{'usernames':[accounts[2]['username'],'missing'+secrets.token_hex(5)]},400)
    it.check(it.get('/api/teacher/classes/'+rid+'/members',t)['total']==before)
record(3,'名单中有无效账号时整批拒绝，不写入部分名册',invalid_import)
it.call('/api/teacher/classes/'+rid+'/members',t,body={'usernames':[accounts[2]['username'],accounts[2]['username']]})
record(4,'重复导入同一学生保持一个成员和一条选课关系',lambda:(it.call('/api/teacher/classes/'+rid+'/members',t,body={'usernames':[accounts[2]['username']]}),it.check(it.get('/api/teacher/classes/'+rid+'/members',t)['total']==1),it.check(it.get('/api/enrollments/me?courseId='+cid,s)['total']==1)))
e=it.get('/api/enrollments/me?courseId='+cid,s)['items'][0]; eid=e['id']; it.fixtures['enrollmentId']=eid
record(5,'学生班级详情不泄露口令，另一教师不能查名册',lambda:(it.check('joinCode' not in it.get('/api/classes/'+rid,s)),deny('/api/teacher/classes/'+rid+'/members',t2)))
rotated=it.call('/api/teacher/classes/'+rid+'/rotate-code?expectedVersion=0',t)
record(6,'换码后旧口令失效，旧版本换码冲突',lambda:(deny('/api/classes/join',s2,'POST',{'code':room['joinCode']},400),deny('/api/teacher/classes/'+rid+'/rotate-code?expectedVersion=0',t,'POST',status=409)))
record(7,'新邀请码加入并发唯一，学生获得真实选课关系',lambda:(it.call('/api/classes/join',s2,body={'code':rotated['joinCode']}),it.call('/api/classes/join',s2,body={'code':rotated['joinCode']}),it.check(it.get('/api/teacher/classes/'+rid+'/members',t)['total']==2)))
record(8,'名册退班保留选课历史，可重新导入恢复成员',lambda: (it.call('/api/teacher/classes/'+rid+'/members/'+it.get('/api/teacher/classes/'+rid+'/members',t)['items'][0]['id'],t,'DELETE'),it.call('/api/teacher/classes/'+rid+'/members',t,body={'usernames':[accounts[2]['username'],accounts[3]['username']]}),it.check(it.get('/api/teacher/classes/'+rid+'/members',t)['total']==2)))
record(9,'结课后不接受加入，恢复班级需要新版本',lambda:(it.call('/api/teacher/classes/'+rid,t,'PUT',{'name':room['name'],'semester':room['semester'],'description':room['description'],'status':'CLOSED','expectedVersion':1}),deny('/api/classes/join',s,'POST',{'code':rotated['joinCode']},409),it.call('/api/teacher/classes/'+rid,t,'PUT',{'name':room['name'],'semester':room['semester'],'description':room['description'],'status':'ACTIVE','expectedVersion':2})))

def chunk(name,data): return struct.pack('>I',len(data))+name+data+struct.pack('>I',zlib.crc32(name+data)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',1,1,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b'\x00\x6e\x21\x23'))+chunk(b'IEND',b'')
wav=io.BytesIO()
with wave.open(wav,'wb') as f: f.setnchannels(1);f.setsampwidth(2);f.setframerate(8000);f.writeframes(b'\0\0'*800)
files=[upload('guide.txt','资源原文：结合语境细读。'.encode()),upload('image.png',png),upload('sample.wav',wav.getvalue()),upload('sample.pdf',b'%PDF-1.4\n% validation header only\n%%EOF')]
record(10,'文本图片音频及PDF资源保存真实元数据和SHA256',lambda:it.check(len(it.get('/api/courses/'+cid+'/resources',s)['items'])==4 and files[1]['sha256']==hashlib.sha256(png).hexdigest()))
record(11,'下载内容与上传内容一致，响应为正确图片和音频类型',lambda:(it.check(download(files[1],s)==(png,'image/png')),it.check(download(files[2],s)[0]==wav.getvalue())))
record(12,'资源权限阻止其他教师和未选学生，跨章上传拒绝',lambda:(download(files[0],t2,403),upload('bad.txt',b'x',chapter=oh['id'],expected=404)))
record(13,'拒绝HTML、伪装图片、损坏图片、无效UTF8和空文件',lambda:(upload('bad.html',b'<script/>',expected=400),upload('bad.png',b'<script/>',expected=400),upload('broken.png',png[:12],expected=400),upload('bad.txt',b'\xff',expected=400),upload('empty.txt',b'',expected=400)))
record(14,'超过20MiB的文件被限制且没有新增资源',lambda:upload('oversize.txt',b'x'*(20971520+1),expected=413))
record(15,'教师删除资源后学生无法下载，资源目录不再显示',lambda:(it.call('/api/resources/'+files[0]['id'],t,'DELETE'),download(files[0],s,404),it.check(it.get('/api/courses/'+cid+'/resources',s)['total']==3)))

annotation={'enrollmentId':eid,'chapterId':hid,'startOffset':0,'endOffset':4,'quoteText':h['content'][:4],'comment':'私人文句批注','color':'RED'}
a=it.call('/api/annotations',s,body=annotation);it.fixtures['annotationId']=a['id']
ap='/api/annotations?enrollmentId='+eid+'&chapterId='+hid
record(16,'批注保存确切原文位置和评论，锚点校验有效',lambda:it.check(it.get(ap,s)['items'][0]['anchorValid'] and it.get(ap,s)['items'][0]['quoteText']==annotation['quoteText']))
record(17,'批注对其他学生与教师私密，无法删除他人批注',lambda:(deny(ap,s2),deny(ap,t),deny('/api/annotations/'+a['id'],s2,'DELETE')))
record(18,'引用与原文不符、错误章节、越界偏移均拒绝',lambda:(deny('/api/annotations',s,'POST',{**annotation,'quoteText':'不符'},409),deny('/api/annotations',s,'POST',{**annotation,'chapterId':oh['id']},404),deny('/api/annotations',s,'POST',{**annotation,'endOffset':99999},400)))
it.call('/api/teacher/courses/'+cid+'/chapters/'+hid,t,'PUT',{'title':h['title'],'sortNo':h['sortNo'],'content':h['content']+'\n修订后的文段。'})
record(19,'教师修订原文后旧批注保留引用并标记锚点失效',lambda:it.check(not it.get(ap,s)['items'][0]['anchorValid']))

qp='/api/teacher/courses/'+cid+'/quizzes'
quiz=it.call(qp,t,body={'chapterId':hid,'title':'语境判断练习','instructions':'每题选择一项。'});qid=quiz['id'];it.fixtures['quizId']=qid
record(20,'草稿练习学生不可读，空题练习不能发布',lambda:(deny('/api/quizzes/'+qid,s),deny('/api/teacher/quizzes/'+qid+'/status',t,'PUT',{'status':'PUBLISHED','expectedVersion':0},409)))
question={'prompt':'解释意象应当先考虑什么？','optionA':'作品上下文','optionB':'固定符号表','optionC':'作者姓名长度','optionD':'字体颜色','correctOption':'A','explanation':'词语、视角和上下文共同形成意义。','sortNo':1,'expectedVersion':0}
it.call('/api/teacher/quizzes/'+qid+'/questions',t,body=question)
record(21,'题目修改检查版本并拒绝其他教师与学生写入',lambda:(deny('/api/teacher/quizzes/'+qid+'/questions',t,'POST',{**question,'sortNo':2},409),deny('/api/teacher/quizzes/'+qid+'/questions',t2,'POST',{**question,'expectedVersion':1}),deny('/api/teacher/quizzes/'+qid+'/questions',s,'POST',{**question,'expectedVersion':1})))
it.call('/api/teacher/quizzes/'+qid+'/status',t,'PUT',{'status':'PUBLISHED','expectedVersion':1})
detail=it.get('/api/quizzes/'+qid,s);question_id=detail['questions'][0]['id']
record(22,'学生提交前拿不到标准答案和解析，发布后题目冻结',lambda:(it.check('correctOption' not in detail['questions'][0] and 'explanation' not in detail['questions'][0]),deny('/api/teacher/quizzes/'+qid+'/questions',t,'POST',{**question,'expectedVersion':2,'sortNo':2},409)))
answers={'answers':[{'questionId':question_id,'choice':'A'}]}
record(23,'未作答、非法选项、伪造题目及重复题目均拒绝',lambda:(deny('/api/practices/'+qid+'/attempts',s,'POST',{'answers':[]},400),deny('/api/practices/'+qid+'/attempts',s,'POST',{'answers':[{'questionId':question_id,'choice':'X'}]},400),deny('/api/practices/'+qid+'/attempts',s,'POST',{'answers':[{'questionId':'999999','choice':'A'}]},400),deny('/api/practices/'+qid+'/attempts',s,'POST',{'answers':answers['answers']*2},400)))
def concurrent_attempt():
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: rows=list(pool.map(lambda _:it.call('/api/practices/'+qid+'/attempts',s,body=answers),range(4)))
    it.check(len({x['id'] for x in rows})==1 and all(x['score']==1 and x['total']==1 for x in rows))
record(24,'四次同时交卷只落一份成绩，判分与解析真实',concurrent_attempt)
record(25,'已交卷返回本人快照，另一学生尚无成绩，其他教师不能看学情',lambda:(it.check(it.get('/api/practices/'+qid+'/result',s)['items'][0]['correctOption']=='A'),it.check(it.get('/api/practices/'+qid+'/result',s2) is None),deny('/api/teacher/courses/'+cid+'/practice-results',t2),it.check(it.get('/api/teacher/courses/'+cid+'/practice-results',t)['total']==1)))
it.call('/api/teacher/quizzes/'+qid+'/status',t,'PUT',{'status':'ARCHIVED','expectedVersion':2})
record(26,'练习归档后既有成绩可读，尚未交卷者无法新交卷',lambda:(it.get('/api/practices/'+qid+'/result',s),deny('/api/practices/'+qid+'/attempts',s2,'POST',answers,409)))

deadline=dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))+dt.timedelta(days=2)
task=it.call('/api/teacher/assignments',t,body={'courseId':cid,'title':'文句辨析习作','description':'结合文本论证。','maxScore':'20','deadlineAt':deadline.isoformat()})
tid=task['id'];it.fixtures['assignmentId']=tid
it.call('/api/teacher/assignments/'+tid+'/publish',t)
dp='/api/assignments/'+tid+'/draft'
record(27,'草稿保存前为空，教师不可代读',lambda:(it.check(it.get(dp,s) is None),deny(dp,t)))
d=it.call(dp,s,'PUT',{'answerText':'  未正式提交的习作\n保留空格。','expectedVersion':-1})
record(28,'草稿在重新登录后保持原文，另一个学生看不到',lambda:(it.check(it.get(dp,it.login(accounts[2]['username'],accounts[2]['password'])['accessToken'])['answerText']==d['answerText']),it.check(it.get(dp,s2) is None)))
record(29,'旧草稿版本、伪造归属和超长内容被拒绝',lambda:(deny(dp,s,'PUT',{'answerText':'覆盖','expectedVersion':-1},409),deny(dp,s,'PUT',{'answerText':'伪造','expectedVersion':0,'studentId':sessions[3]['user']['id']},400),deny(dp,s,'PUT',{'answerText':'字'*10001,'expectedVersion':0},400)))
record(30,'正式提交校验失败不会删除已保存草稿',lambda:(deny('/api/assignments/'+tid+'/submissions',s,'POST',{'answerText':''},400),it.check(it.get(dp,s)['answerText']==d['answerText'])))
sub=it.call('/api/assignments/'+tid+'/submissions',s,body={'answerText':'意象的意义应联系具体作品，不宜机械套用符号。'});sid=sub['id'];it.fixtures['submissionId']=sid
record(31,'正式提交成功清除服务端草稿，历史评分最初为空',lambda:(it.check(it.get(dp,s) is None),it.check(it.get('/api/submissions/'+sid+'/grade-history',s)['total']==0)))
it.call('/api/submissions/'+sid+'/grade',t,'PUT',{'score':'16','comment':'第一次评分：论据可再补充。','expectedVersion':0})
it.call('/api/submissions/'+sid+'/grade',t,'PUT',{'score':'18','comment':'复核评分：具体语境分析明确。','expectedVersion':1})
record(32,'评分复核保留两次记录，旧评语和得分不被覆盖',lambda:(it.check(it.get('/api/submissions/'+sid+'/grade-history',s)['total']==2),it.check([x['score'] for x in it.get('/api/submissions/'+sid+'/grade-history',s)['items']]==['18.00','16.00'])))
record(33,'评分后阻止草稿与旧版本评分，其他学生教师无权读历史',lambda:(deny(dp,s,'PUT',{'answerText':'已评分再写','expectedVersion':-1},409),deny('/api/submissions/'+sid+'/grade',t,'PUT',{'score':'20','comment':'旧版本','expectedVersion':1},409),deny('/api/submissions/'+sid+'/grade-history',s2),deny('/api/submissions/'+sid+'/grade-history',t2)))
month=deadline.strftime('%Y-%m')
record(34,'课历按真实月份返回本人课程截止与已评分状态',lambda:(it.check(any(x['id']==tid and x['submissionStatus']=='GRADED' for x in it.get('/api/assignments/agenda?month='+month,s)['items'])),it.check(any(x['id']==tid for x in it.get('/api/assignments/agenda?month='+month,t)['items'])),it.check(not any(x['id']==tid for x in it.get('/api/assignments/agenda?month='+month,t2)['items'])),deny('/api/assignments/agenda?month=2026-99',s,status=400)))
notice=it.call('/api/teacher/courses/'+cid+'/notices',t,body={'title':'V3信笺公告','content':'请在截止日期前完成习作。','pinned':False})
key='NOTICE:'+notice['id']+':0'
record(35,'消息源包含本课真实公告，收件学生与教师范围隔离',lambda:(it.check(any(x['eventKey']==key for x in it.get('/api/courses/inbox',s)['items'])),it.check(not any(x['eventKey']==key for x in it.get('/api/courses/inbox',t2)['items']))))
def concurrent_receipt():
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: list(pool.map(lambda _:it.call('/api/inbox/receipts',s,body={'keys':[key,key]}),range(4)))
    rows=it.get('/api/inbox/receipts?size=100',s)['items'];it.check(sum(x['eventKey']==key for x in rows)==1)
    it.check(not any(x['eventKey']==key for x in it.get('/api/inbox/receipts?size=100',s2)['items']))
record(36,'已读回执并发唯一且属于本人，其他学生保持未读',concurrent_receipt)
it.call('/api/teacher/courses/'+cid+'/notices/'+notice['id'],t,'PUT',{'title':notice['title'],'content':'公告已修订，请重新阅读。','pinned':False,'expectedVersion':0})
record(37,'公告修订产生新版本消息，旧回执不会吞掉新通知',lambda:(it.check(any(x['eventKey']=='NOTICE:'+notice['id']+':1' for x in it.get('/api/courses/inbox',s)['items'])),deny('/api/inbox/receipts',s,'POST',{'keys':[key],'userId':sessions[3]['user']['id']},400),deny('/api/inbox/receipts',s,'POST',{'keys':['../../invalid']},400)))
record(38,'新列表分页和角色边界一致，网关拒绝内部判分访问',lambda:(deny('/api/classes?size=101',s,status=400),deny('/api/teacher/classes',s),deny('/api/annotations?enrollmentId='+eid+'&chapterId='+hid+'&size=0',s,status=400),deny('/internal/quizzes/'+qid+'/grade',s,'POST',answers,404)))
it.call('/api/teacher/courses/'+cid+'/archive',t)
record(39,'归档课程资源和私人批注历史可读，不能新增资源或批注',lambda:(download(files[1],s),it.get(ap,s),upload('archived.txt',b'x',expected=409),deny('/api/annotations',s,'POST',annotation,409)))
record(40,'归档后学生可删除本人批注，既有成绩与评分历史保留',lambda:(it.call('/api/annotations/'+a['id'],s,'DELETE'),it.check(it.get(ap,s)['total']==0),it.get('/api/practices/'+qid+'/result',s),it.get('/api/submissions/'+sid+'/grade-history',s)))
(it.ROOT/'evidence/v3-fixtures.json').write_text(json.dumps(it.fixtures,ensure_ascii=False,indent=2),encoding='utf8')
print('V3 live tests complete:',len(it.results),'cases')
