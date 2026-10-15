"""Real archived-course revision/batch boundaries in a separate fixture course."""
from v6_review import call,login,ROOT,PRIVATE
import json,datetime as dt
f=json.loads((PRIVATE/'v6-fixtures.json').read_text());t=login(f['teacher']);sa=login(f['students'][0]);cid=f['foreignCourseId'];checks=[]
def check(name,ok=True):
 assert ok,name;checks.append({'name':name,'result':'PASS'});(ROOT/'evidence/v6-archive-api.json').write_text(json.dumps({'checks':checks,'scope':'Actual dedicated archived-course checks. Main V6 review/revision course remains published.'},ensure_ascii=False,indent=2),encoding='utf8')
call('/api/enrollments',sa,{'courseId':cid});deadline=(dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))+dt.timedelta(days=7)).isoformat()
task=call('/api/teacher/assignments',t,{'courseId':cid,'title':'归档修订验证','description':'独立课程验证归档后的读取与拒绝写入。','maxScore':'100','deadlineAt':deadline,'rubric':[]});call('/api/teacher/assignments/'+task['id']+'/publish',t,method='POST');s=call('/api/assignments/'+task['id']+'/submissions',sa,{'answerText':'归档前正式原稿。'});sid=s['id'];path='/api/submissions/'+sid+'/revisions';call('/api/submissions/'+sid+'/grade',t,{'score':'80','comment':'补充依据','expectedVersion':s['version'],'rubricScores':[]},method='PUT');s=call('/api/submissions/'+sid,sa);r=call(path,t,{'requestText':'补充原文依据。','expectedSubmissionVersion':s['version']});r=call(path+'/'+r['id']+'/submit',sa,{'answerText':'归档前补充了原文依据的修订稿。','studentNote':'补充依据','expectedVersion':r['version']});rp=path+'/'+r['id']
call(rp+'/review',t,{'decision':None,'comment':'空结论','expectedVersion':r['version']},expected=400);check('空修订结论返回输入错误且保留待阅',call(path,t)['items'][-1]['status']=='SUBMITTED')
draft=call('/api/teacher/assignments',t,{'courseId':cid,'title':'归档前草稿','description':'应保持草稿','maxScore':'100','deadlineAt':deadline,'rubric':[]});call('/api/teacher/courses/'+cid+'/archive',t,method='POST')
check('归档后学生与教师仍可回看修订',call(path,sa)['courseStatus']=='ARCHIVED' and call(path,t)['items'][-1]['answerText']==r['answerText'])
call(rp+'/review',t,{'decision':'ACCEPTED','comment':'归档后不能改','expectedVersion':r['version']},expected=409);check('归档后拒绝教师评阅且状态保持',call(path,sa)['items'][-1]['status']=='SUBMITTED')
call(rp+'/submit',sa,{'answerText':'归档后覆盖','expectedVersion':r['version']},expected=409);check('归档后拒绝学生修订')
call(path,t,{'requestText':'归档后邀请','expectedSubmissionVersion':s['version']},expected=409);check('归档后不能发起新邀请')
call('/api/teacher/assignments/batch',t,{'courseId':cid,'action':'PUBLISH','items':[{k:draft[k] for k in ['id','status','updatedAt']}]},expected=409);check('归档课程整批发布拒绝且草稿保持',call('/api/assignments/'+draft['id'],t)['status']=='DRAFT')
check('归档边界检查不改变正式80分及原文',call('/api/submissions/'+sid,sa)['grade']['score']=='80.00' and call('/api/submissions/'+sid,sa)['answerText']==s['answerText'])
print('Archive boundary API checks PASS:',len(checks))
