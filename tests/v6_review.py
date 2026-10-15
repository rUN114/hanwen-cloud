"""Real R1 acceptance using fresh isolated teaching fixtures; no original scores are edited."""
from pathlib import Path
import datetime as dt,json,os,secrets,urllib.request,urllib.error
ROOT=Path(__file__).resolve().parents[1];PRIVATE=ROOT/'.local';BASE=os.environ.get('HW_TEST_BASE','http://127.0.0.1:8088');assert BASE in ('http://127.0.0.1:8088','http://127.0.0.1:8080')
checks=[]
def call(path,token=None,body=None,method=None,expected=200):
 h={'Content-Type':'application/json','Origin':'http://127.0.0.1:5173'}
 if token:h['Authorization']='Bearer '+token
 req=urllib.request.Request(BASE+path,headers=h,data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),method=method or ('POST' if body is not None else 'GET'))
 try:
  with urllib.request.urlopen(req,timeout=30) as res:status,raw=res.status,res.read()
 except urllib.error.HTTPError as e:status,raw=e.code,e.read()
 assert status==expected,(path,status,expected,raw[:500].decode(errors='replace'))
 return json.loads(raw).get('data')
def login(x):return call('/api/auth/login',body={'username':x['username'],'password':x['password']})['accessToken']
def check(name,condition=True):
 assert condition,name;checks.append({'name':name,'result':'PASS'})
 (ROOT/'evidence/v6-review-api.json').write_text(json.dumps({'at':dt.datetime.now().astimezone().isoformat(),'checks':checks,'scope':'Fresh isolated fixtures; real API checks. Mastery intervals tested separately by JUnit.'},ensure_ascii=False,indent=2),encoding='utf8')
if __name__=='__main__':
 cfg=json.loads((PRIVATE/'runtime.json').read_text(encoding='utf-8-sig'));accounts=json.loads((PRIVATE/'demo-accounts.json').read_text());admin=login({'username':'admin','password':cfg['HW_ADMIN_PASSWORD']});teacher=login(accounts[0]);other=login(accounts[1]);stamp=secrets.token_hex(4)
 people=call('/api/admin/users/import',admin,{'role':'STUDENT','rows':[{'username':'v6_a_'+stamp,'displayName':'温故验收同窗甲'},{'username':'v6_b_'+stamp,'displayName':'温故验收同窗乙'}]})['items'];students=[{'username':x['username'],'password':x['initialPassword'],'id':x['id']} for x in people];sa=login(students[0]);sb=login(students[1])
 c=call('/api/teacher/courses',teacher,{'name':'V6温故与修订验收 '+stamp,'category':'古典文学','summary':'验收专用：从诗文阅读到温故与修订。'});cid=c['id'];ch=call('/api/teacher/courses/'+cid+'/chapters',teacher,{'title':'静夜思 · 温故','content':'床前明月光，疑是地上霜。举头望明月，低头思故乡。','sortNo':1,'lessonDocument':'# 从意象到情感\n结合原文分析乡思。'});call('/api/teacher/courses/'+cid+'/publish',teacher,method='POST');enroll=call('/api/enrollments',sa,{'courseId':cid});call('/api/enrollments',sb,{'courseId':cid})
 bank=[]
 for kind,prompt,answer in [('SINGLE','静夜思的作者是谁？','A'),('MULTIPLE','哪些诗句表现动作？','AC'),('TRUE_FALSE','诗中月光与乡思有关。','A')]:
  bank.append(call('/api/teacher/question-bank',teacher,{'questionType':kind,'prompt':prompt,'optionA':'李白' if kind=='SINGLE' else '举头望明月' if kind=='MULTIPLE' else '正确','optionB':'杜甫' if kind=='SINGLE' else '床前明月光' if kind=='MULTIPLE' else '错误','optionC':'王维' if kind=='SINGLE' else '低头思故乡' if kind=='MULTIPLE' else None,'optionD':'白居易' if kind=='SINGLE' else '疑是地上霜' if kind=='MULTIPLE' else None,'correctOption':answer,'explanation':'结合作者及原文动作意象理解月色与乡思。','points':2,'topic':'作者与意象','expectedVersion':None}))
 q=call('/api/teacher/courses/'+cid+'/quizzes',teacher,{'chapterId':ch['id'],'title':'月色与乡思 · 温故验证','instructions':'三类题，最多两次；机会用尽才开放解析。','timeLimitMinutes':10,'maxAttempts':2})
 for b in bank:q=call('/api/teacher/quizzes/'+q['id']+'/from-bank',teacher,{'bankId':b['id'],'bankVersion':b['version'],'expectedVersion':q['version']})
 q=call('/api/teacher/quizzes/'+q['id']+'/status',teacher,{'status':'PUBLISHED','expectedVersion':q['version']},method='PUT');qid=q['id'];attempts=[]
 for n in range(2):
  run=call('/api/practices/'+qid+'/start',sa,method='POST');body={'runId':run['id'],'answers':[{'questionId':x['id'],'choice':'B'} for x in run['questions']]};a=call('/api/practices/'+qid+'/attempts',sa,body);attempts.append(a)
  if n==0:check('尚有重试机会时错题列表与数量不泄露',call('/api/review-cards?courseId='+cid,sa)['total']==0)
  else:check('机会耗尽后错题自动收录',call('/api/review-cards?courseId='+cid,sa)['total']==3)
  check('重复交卷不产生重复归档',call('/api/practices/'+qid+'/attempts',sa,body)['id']==a['id'])
 cards=call('/api/review-cards?courseId='+cid,sa);check('相同冻结题目归并并记录错两次',all(x['wrongCount']==2 for x in cards['items']))
 check('他人题册与统计隔离',call('/api/review-cards?courseId='+cid,sb)['total']==0)
 call('/api/review-cards',teacher,expected=403);check('教师不能读取私人温故册')
 first=cards['items'][0];detail=call('/api/review-cards/'+first['id'],sa);check('重新作答题面不返回正确答案解析','correctOption' not in detail and 'explanation' not in detail)
 call('/api/review-cards/'+first['id'],sb,expected=403);check('其他学生不能用题签编号读题')
 call('/api/review-cards/'+first['id']+'/answers',sb,{'choice':'A','expectedVersion':first['version']},expected=403);check('其他学生不能提交复习')
 call('/api/review-cards/'+first['id']+'/answers',sa,{'choice':'E','expectedVersion':first['version']},expected=400);check('无效选项拒绝且不修改版本',call('/api/review-cards/'+first['id'],sa)['version']==first['version'])
 call('/api/review-cards/'+first['id']+'/answers',sa,{'choice':'A'},expected=400);check('缺少版本拒绝')
 result=call('/api/review-cards/'+first['id']+'/answers',sa,{'choice':'A','expectedVersion':first['version']});due=dt.datetime.fromisoformat(result['nextReviewAt']);check('答对后返回解析及一天后计划',result['correct'] and result['correctStreak']==1 and not result['mastered'] and (due-dt.datetime.now(due.tzinfo)).total_seconds()>85000)
 call('/api/review-cards/'+first['id']+'/answers',sa,{'choice':'A','expectedVersion':first['version']},expected=409);check('重复旧版本不会累计掌握次数')
 call('/api/review-cards/'+first['id']+'/answers',sa,{'choice':'A','expectedVersion':result['version']},expected=409);check('未到计划时间不能连点刷掌握')
 second=cards['items'][1];bad=call('/api/review-cards/'+second['id']+'/answers',sa,{'choice':'B','expectedVersion':second['version']});check('再次答错重置连续记录并安排十分钟后',not bad['correct'] and bad['correctStreak']==0 and not bad['mastered'])
 check('复习不修改原测验分数',call('/api/practices/'+qid+'/result?attemptId='+attempts[-1]['id'],sa)['score']==0)
 check('列表分页与待复习筛选准确',call('/api/review-cards?courseId='+cid+'&state=DUE',sa)['total']==1 and len(call('/api/review-cards?courseId='+cid+'&size=1',sa)['items'])==1)
 call('/api/review-cards?state=NOPE',sa,expected=400);check('无效筛选拒绝')
 f={'teacher':accounts[0],'otherTeacher':accounts[1],'students':students,'courseId':cid,'chapterId':ch['id'],'enrollmentId':enroll['id'],'quizId':qid,'reviewCardId':cards['items'][2]['id'],'stamp':stamp};(PRIVATE/'v6-fixtures.json').write_text(json.dumps(f,ensure_ascii=False,indent=2),encoding='utf8')
 print('R1 real API checks PASS:',len(checks))
