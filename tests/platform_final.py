"""Read-only final regression against existing isolated validation fixtures. No teaching writes."""
from pathlib import Path
import datetime as dt,json,urllib.request,urllib.error
R=Path(__file__).resolve().parents[1];f=json.loads((R/'.local/platform-fixtures.json').read_text());accounts=json.loads((R/'.local/demo-accounts.json').read_text());checks=[];BASE='http://127.0.0.1:8088'
def call(path,token=None,body=None,expected=200):
 h={'Content-Type':'application/json','Origin':'http://127.0.0.1:5173'}
 if token:h['Authorization']='Bearer '+token
 req=urllib.request.Request(BASE+path,headers=h,data=None if body is None else json.dumps(body).encode())
 try:
  with urllib.request.urlopen(req,timeout=30) as res:status,data=res.status,json.load(res).get('data')
 except urllib.error.HTTPError as e:status,data=e.code,None
 assert status==expected,(path,status,expected);return data
def login(s):return call('/api/auth/login',body={'username':s['username'],'password':s.get('initialPassword',s.get('password'))})['accessToken']
def check(name,ok):assert ok,name;checks.append({'name':name,'result':'PASS'})
t=login(f['teacher']);sa=login(f['students'][0]);sb=login(f['students'][1]);other=login(accounts[1]);legacy=login(accounts[3]);cid=f['courseId'];sid=f['uiSubmissionIds'][0]
q=call('/api/submissions/queue?courseId='+cid+'&status=',t);check('全部状态包含已评分与其他历史答卷',set(f['uiSubmissionIds']).issubset({x['id'] for x in q['items']}))
check('已评分筛选准确',all(x['status']=='GRADED' for x in call('/api/submissions/queue?courseId='+cid+'&status=GRADED',t)['items']))
check('待评分队列不混入已评分答卷',call('/api/submissions/queue?courseId='+cid+'&status=SUBMITTED',t)['total']==0)
call('/api/submissions/queue?courseId='+cid,sa,expected=403);check('学生不能使用批阅队列',True)
call('/api/submissions/queue?courseId='+cid,other,expected=403);check('其他教师不能使用此课程队列',True)
book=call('/api/grades/courses/'+cid,sa);check('最终总评仍为86.70',book['students'][0]['total']=='86.70')
check('最高得分率与排除缺交规则已经保存',book['policy']['attemptRule']=='HIGHEST' and book['policy']['missingRule']=='EXCLUDE')
check('同窗乙未完成练习保持未结算',call('/api/grades/courses/'+cid,sb)['students'][0]['total'] is None)
sub=call('/api/submissions/'+sid,sa);check('最终80分与分项50/30一致',sub['grade']['score']=='80.00' and sub['grade']['rubricScores']==['50','30'])
check('新评分更正留痕完整',len(call('/api/submissions/'+sid+'/grade-history',sa)['items'])>=2)
chapter=call('/api/courses/'+cid+'/chapters/'+f['chapterId'],sa);check('安全预览检查后讲义保持有效内容','<img' not in chapter['lessonDocument'] and '课后思考' in chapter['lessonDocument'])
check('既有学生原教学答卷88分保持',call('/api/submissions/22',legacy)['grade']['score']=='88.00')
check('既有单选测验答卷仍可回看',call('/api/practices/2/result',legacy)['items'][0]['questionType']=='SINGLE')
(R/'evidence/platform-final-api.json').write_text(json.dumps({'at':dt.datetime.now().astimezone().isoformat(),'result':'PASS','checks':checks,'scope':'Read-only regression after final queue fix and actual browser grading. Existing teaching records retained.'},ensure_ascii=False,indent=2),encoding='utf8');print('Final read-only regression PASS:',len(checks))
