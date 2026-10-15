"""V2 live tests on the dedicated local coursework environment; retain V1 evidence.

Creates clearly named fixtures. Does not modify existing demo passwords or delete courses.
"""
import integration as it
import concurrent.futures
import json
import secrets
import urllib.parse

raw_request=it.request
def encoded_request(path,*args,**kwargs):
    return raw_request(urllib.parse.quote(path,safe='/?&=:%+'),*args,**kwargs)
it.request=encoded_request

it.RESULTS=it.ROOT/'evidence/v2-api-tests.json'
accounts=json.loads((it.PRIVATE/'demo-accounts.json').read_text(encoding='utf8'))
sessions=[it.login(a['username'],a['password']) for a in accounts]
teacher,teacher2,student,student2=[s['accessToken'] for s in sessions]
student_id=sessions[2]['user']['id']
admin=it.login('admin',it.cfg['HW_ADMIN_PASSWORD'])['accessToken']
c,h=it.course(teacher,'V2平台权限验证 · '+secrets.token_hex(2))
other,other_h=it.course(teacher2,'V2跨章节验证 · '+secrets.token_hex(2))
it.call('/api/teacher/courses/'+c['id']+'/publish',teacher)
it.call('/api/teacher/courses/'+other['id']+'/publish',teacher2)
e=it.call('/api/enrollments',student,body={'courseId':c['id']})
cid=c['id'];fixtures={'courseId':cid,'chapterId':h['id'],'enrollmentId':e['id']}
def record(n,text,fn):it.record('V2-'+str(n).zfill(2),text,fn)
def denied(path,token,method='GET',body=None,status=403):return it.request(path,token,method,body,status)

n=it.call('/api/teacher/courses/'+cid+'/notices',teacher,body={'title':'开卷与问学','content':'请结合原文提出问题；也欢迎记录自己的理解。','pinned':True})
record(1,'教师发布公告，已选学生读取真实置顶记录',lambda:it.check(it.get('/api/courses/'+cid+'/notices',student)['items'][0]['id']==n['id']))
record(2,'未选学生不能阅读课程公告',lambda:denied('/api/courses/'+cid+'/notices',student2))
record(3,'其他教师不能发布公告',lambda:denied('/api/teacher/courses/'+cid+'/notices',teacher2,'POST',{'title':'越权','content':'不应保存','pinned':False}))
np='/api/teacher/courses/'+cid+'/notices/'+n['id']
record(4,'公告版本更新，旧版本编辑冲突',lambda:(it.call(np,teacher,'PUT',{'title':'开卷与问学','content':'更新：提问请写明文本依据。','pinned':True,'expectedVersion':0}),denied(np,teacher,'PUT',{'title':'覆盖','content':'旧版本','pinned':False,'expectedVersion':0},409)))
record(5,'未选学生不能发问',lambda:denied('/api/courses/'+cid+'/questions',student2,'POST',{'title':'未选','content':'越权','chapterId':None}))
record(6,'问题关联的跨课程章节被拒绝',lambda:denied('/api/courses/'+cid+'/questions',student,'POST',{'title':'错章','content':'不应关联','chapterId':other_h['id']},404))
q=it.call('/api/courses/'+cid+'/questions',student,body={'title':'意象与情感如何相连？','content':'同一个景物在不同作品中是否可能承载不同情感？','chapterId':h['id']});fixtures['questionId']=q['id']
record(7,'问题带真实提问者显示名并支持未解决筛选',lambda:it.check(it.get('/api/courses/'+cid+'/questions?resolved=false&keyword=意象',teacher)['items'][0]['displayName']==sessions[2]['user']['displayName']))
record(8,'教师回复显示教师身份，未选学生不能回复',lambda:(it.call('/api/questions/'+q['id']+'/replies',teacher,body={'content':'请比较作品中的动词、视角和上下文，避免把意象固定解释为一种情绪。'}),it.check(it.get('/api/questions/'+q['id']+'/replies',student)['items'][0]['role']=='TEACHER'),denied('/api/questions/'+q['id']+'/replies',student2,'POST',{'content':'未选越权'})))
e2=it.call('/api/enrollments',student2,body={'courseId':cid})
record(9,'同课其他学生不能更改问题解决状态',lambda:denied('/api/questions/'+q['id']+'/resolved',student2,'PUT',{'resolved':True,'expectedVersion':0}))
record(10,'提问者可标记解决，旧解决版本冲突',lambda:(it.call('/api/questions/'+q['id']+'/resolved',student,'PUT',{'resolved':True,'expectedVersion':0}),denied('/api/questions/'+q['id']+'/resolved',teacher,'PUT',{'resolved':False,'expectedVersion':0},409)))
record(11,'教师可重新打开问题',lambda:it.check(not it.call('/api/questions/'+q['id']+'/resolved',teacher,'PUT',{'resolved':False,'expectedVersion':1})['resolved']))
note=it.call('/api/notes',student,body={'enrollmentId':e['id'],'chapterId':h['id'],'title':'月光也要放回语境','content':'意象需要联系作品中的词语、视角与上下文。'});fixtures['noteId']=note['id']
record(12,'本人札记筛选与正文持久化',lambda:it.check(it.get('/api/notes?enrollmentId='+e['id']+'&chapterId='+h['id']+'&keyword=月光',student)['items'][0]['content']==note['content']))
record(13,'教师不能读取学生私人札记',lambda:denied('/api/notes',teacher))
record(14,'其他学生列表没有本人札记，不能修改或删除',lambda:(it.check(it.get('/api/notes?keyword=月光也要放回语境',student2)['total']==0),denied('/api/notes/'+note['id'],student2,'PUT',{'title':'窃改','content':'不应保存','expectedVersion':0}),denied('/api/notes/'+note['id']+'?expectedVersion=0',student2,'DELETE')))
record(15,'札记跨课程章节及伪造studentId被拒绝',lambda:(denied('/api/notes',student,'POST',{'enrollmentId':e['id'],'chapterId':other_h['id'],'title':'错章','content':'不应保存'},404),denied('/api/notes',student,'POST',{'enrollmentId':e['id'],'chapterId':h['id'],'title':'伪造归属','content':'不应保存','studentId':sessions[3]['user']['id']},400)))
record(16,'札记更新检查版本，失效版本拒绝删除',lambda:(it.call('/api/notes/'+note['id'],student,'PUT',{'title':note['title'],'content':note['content']+' 留意动词。','expectedVersion':0}),denied('/api/notes/'+note['id']+'?expectedVersion=0',student,'DELETE',status=409)))
record(17,'札记所属章节不能在更新时更改',lambda:denied('/api/notes/'+note['id'],student,'PUT',{'enrollmentId':e2['id'],'title':'移动','content':'不应保存','expectedVersion':1},400))
works=it.get('/api/literature?keyword=静夜思&dynasty=唐&genre=诗',student)
classic=works['items'][0]
record(18,'经典诗文按篇名时代体裁检索且包含来源说明',lambda:it.check(it.get('/api/literature/'+classic['id'],student)['sourceUrl'].startswith('https://zh.wikisource.org/')))
record(19,'教师也不能修改全局经典示例',lambda:denied('/api/teacher/literature/'+classic['id']+'/status',teacher,'PUT',{'status':'ARCHIVED','expectedVersion':classic['version']}))
write={'title':'V2编选验证篇','author':'课程测试','dynasty':'现代','genre':'文','content':'用于验证编选权限的自写示例。','readingGuide':'观察文本依据。','sourceUrl':None,'sourceNote':'测试原创文本。'}
w=it.call('/api/teacher/literature',teacher,body=write);fixtures['workId']=w['id']
record(20,'学生不能编选诗文，另一教师不能改作品',lambda:(denied('/api/teacher/literature',student,'POST',write),denied('/api/teacher/literature/'+w['id'],teacher2,'PUT',{**write,'expectedVersion':0})))
record(21,'来源链接拒绝脚本协议，诗文旧版本更新冲突',lambda:(denied('/api/teacher/literature/'+w['id'],teacher,'PUT',{**write,'sourceUrl':'javascript:alert(1)','expectedVersion':0},400),it.call('/api/teacher/literature/'+w['id'],teacher,'PUT',{**write,'content':'已修订的测试原创文本。','expectedVersion':0}),denied('/api/teacher/literature/'+w['id'],teacher,'PUT',{**write,'expectedVersion':0},409)))
def concurrent_favorite():
 before=it.get('/api/enrollments/summary',student)['favorites']
 with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:res=list(pool.map(lambda _:it.call('/api/favorites',student,body={'workId':w['id']}),range(6)))
 it.check(all(x['favorited'] for x in res));it.check(it.get('/api/enrollments/summary',student)['favorites']==before+1)
record(22,'六次首次收藏并发唯一，仪表汇总仅增加一条',concurrent_favorite)
record(23,'收藏归属来自登录人，拒绝伪造studentId及不存在作品',lambda:(denied('/api/favorites',student,'POST',{'workId':w['id'],'studentId':sessions[3]['user']['id']},400),denied('/api/favorites',student,'POST',{'workId':'999999999'},404),it.check(not it.get('/api/favorites/'+w['id'],student2)['favorited'])))
record(24,'作品归档后不能新增收藏或读正文，原收藏保留元信息',lambda:(it.call('/api/teacher/literature/'+w['id']+'/status',teacher,'PUT',{'status':'ARCHIVED','expectedVersion':1}),denied('/api/literature/'+w['id'],student),denied('/api/favorites',student2,'POST',{'workId':w['id']},409),it.check(any(x['workId']==w['id'] and x['status']=='ARCHIVED' for x in it.get('/api/favorites?size=100',student)['items']))))
record(25,'归档作品允许本人取消收藏，重复取消幂等',lambda:(it.call('/api/favorites/'+w['id'],student,'DELETE'),it.call('/api/favorites/'+w['id'],student,'DELETE'),it.check(not it.get('/api/favorites/'+w['id'],student)['favorited'])))
task=it.call('/api/teacher/assignments',teacher,body={'courseId':cid,'title':'V2学情统计验证','description':'依据文本回答。','maxScore':'20','deadlineAt':None})
it.call('/api/teacher/assignments/'+task['id']+'/publish',teacher)
sub=it.call('/api/assignments/'+task['id']+'/submissions',student,body={'answerText':'用具体文本支持理解。'})
it.call('/api/submissions/'+sub['id']+'/grade',teacher,'PUT',{'score':'16','comment':'依据明确。','expectedVersion':0})
it.call('/api/enrollments/'+e['id']+'/chapters/'+h['id']+'/complete',student,'PUT')
def stats_check():
 roster=it.get('/api/teacher/courses/'+cid+'/learners',teacher);a=it.get('/api/teacher/courses/'+cid+'/assignment-stats',teacher)
 it.check(roster['total']==2);studentrow=next(r for r in roster['items'] if r['studentId']==student_id)
 it.check(studentrow['completed']==1 and studentrow['chapters']==1 and studentrow['noteCount']==1 and 'content' not in studentrow)
 it.check(a['tasks'][0]['submitted']==1 and a['tasks'][0]['graded']==1 and a['tasks'][0]['averageScore']=='16.00');it.check(a['learners'][0]['averagePercent']=='80.00')
record(26,'教师真实学情：两名选课者、一份16/20评分，札记不暴露正文',stats_check)
record(27,'其他教师与学生均不能查看课程名册或作业汇总',lambda:(denied('/api/teacher/courses/'+cid+'/learners',teacher2),denied('/api/teacher/courses/'+cid+'/assignment-stats',teacher2),denied('/api/teacher/courses/'+cid+'/learners',student)))
record(28,'教师首页统计及待批列表仅所属数据',lambda:(it.check(isinstance(it.get('/api/teacher/courses/summary',teacher)['PUBLISHED'],int)),it.check(isinstance(it.get('/api/teacher/assignments/summary',teacher)['pendingGrading'],int))))
record(29,'学生首页待作答只含已选公开课程且排除已交作业',lambda:(it.check(task['id'] not in [t['id'] for t in it.get('/api/assignments/pending?size=100',student)['items']]),it.check(task['id'] in [t['id'] for t in it.get('/api/assignments/pending?size=100',student2)['items']])))
record(30,'后台账号汇总仅管理员可见',lambda:(it.check(it.get('/api/admin/users/summary',admin)['STUDENT']>=2),denied('/api/admin/users/summary',student)))
record(31,'资料白名单拒绝角色伪造和非法邮箱',lambda:(denied('/api/users/me',student,'PUT',{'displayName':'越权','email':None,'biography':None,'role':'ADMIN'},400),denied('/api/users/me',student,'PUT',{'displayName':sessions[2]['user']['displayName'],'email':'not-an-email','biography':None},400)))
# Test password changes on a fresh account, not the user's demo accounts.
name='v2pwd'+secrets.token_hex(4);old=secrets.token_urlsafe(20)+'Aa9!';new=secrets.token_urlsafe(20)+'Bb8!'
u=it.call('/api/admin/users',admin,body={'username':name,'displayName':'V2资料与改密验证','password':old,'role':'STUDENT'})
ut=it.login(name,old)['accessToken']
record(32,'个人资料持久化并可清除可选字段',lambda:(it.call('/api/users/me',ut,'PUT',{'displayName':'V2资料验证','email':'reader@example.org','biography':'喜欢诗文细读。'}),it.check(it.get('/api/users/me',ut)['email']=='reader@example.org'),it.call('/api/users/me',ut,'PUT',{'displayName':'V2资料验证','email':None,'biography':None}),it.check(it.get('/api/users/me',ut)['biography'] is None)))
record(33,'错误当前密码、重复密码、短密码拒绝且会话仍有效',lambda:(denied('/api/users/me/password',ut,'PUT',{'currentPassword':'wrong-password','newPassword':new},400),denied('/api/users/me/password',ut,'PUT',{'currentPassword':old,'newPassword':old},400),denied('/api/users/me/password',ut,'PUT',{'currentPassword':old,'newPassword':'short'},400),it.get('/api/users/me',ut)))
record(34,'改密后旧JWT与旧密码失效，新密码可登录',lambda:(it.call('/api/users/me/password',ut,'PUT',{'currentPassword':old,'newPassword':new}),denied('/api/users/me',ut,status=401),it.login(name,old,401),it.check(it.login(name,new)['user']['id']==u['id'])))
# Restore original role guard on independent favorite-only and literature-only data.
ut=it.login(name,new)['accessToken'];it.call('/api/favorites',ut,body={'workId':classic['id']})
record(35,'只有收藏而未选课的账号仍不能跨角色转换',lambda:denied('/api/admin/users/'+u['id']+'/access',admin,'PUT',{'role':'TEACHER','status':'ENABLED'},409))
record(36,'伪造作者与请求归属字段均被拒绝',lambda:(denied('/api/courses/'+cid+'/questions',student,'POST',{'title':'伪造','content':'不保存','chapterId':None,'studentId':'1'},400),denied('/api/teacher/courses/'+cid+'/notices',teacher,'POST',{'title':'伪造','content':'不保存','pinned':False,'authorId':'1'},400)))
it.call('/api/teacher/courses/'+cid+'/archive',teacher)
record(37,'课程归档后公告答疑历史可读，新增公告问题回复与解决均拒绝',lambda:(it.get('/api/courses/'+cid+'/notices',student),it.get('/api/questions/'+q['id']+'/replies',student),denied('/api/teacher/courses/'+cid+'/notices',teacher,'POST',{'title':'归档后','content':'不应保存','pinned':False},409),denied('/api/courses/'+cid+'/questions',student,'POST',{'title':'归档后','content':'不应保存','chapterId':None},409),denied('/api/questions/'+q['id']+'/replies',teacher,'POST',{'content':'归档后'},409),denied('/api/questions/'+q['id']+'/resolved',teacher,'PUT',{'resolved':True,'expectedVersion':2},409)))
record(38,'归档课程允许整理本人札记，实际学情仍可查询',lambda:(it.call('/api/notes/'+note['id'],student,'PUT',{'title':note['title'],'content':'归档后复习记录。','expectedVersion':1}),it.get('/api/teacher/courses/'+cid+'/learners',teacher)))
record(39,'分页参数边界在新接口同样拒绝',lambda:(denied('/api/notes?size=101',student,status=400),denied('/api/literature?page=0',student,status=400),denied('/api/courses/'+cid+'/questions?resolved=false&size=0',teacher,status=400)))
record(40,'札记显式版本删除后列表消失',lambda:(it.call('/api/notes/'+note['id']+'?expectedVersion=2',student,'DELETE'),it.check(it.get('/api/notes?enrollmentId='+e['id'],student)['total']==0)))
(it.PRIVATE/'v2-fixture.json').write_text(json.dumps(fixtures,ensure_ascii=False,indent=2),encoding='utf8')
print('V2 live tests complete; demo credentials unchanged.')
