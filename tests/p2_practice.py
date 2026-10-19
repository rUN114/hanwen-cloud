"""Real, isolated P2 API workflow. Does not change existing students' answers or grades."""
import concurrent.futures,datetime as dt,json,os,secrets,time,urllib.error,urllib.request
from pathlib import Path

from fixture_guard import require_fixture_environment

require_fixture_environment(Path(__file__).resolve().parents[1])
ROOT=Path(__file__).resolve().parents[1];PRIVATE=ROOT/'.local'
BASE=os.environ.get('HW_TEST_BASE','http://127.0.0.1:8088')
assert BASE in ('http://127.0.0.1:8088','http://127.0.0.1:8080')
cfg=json.loads((PRIVATE/'runtime.json').read_text(encoding='utf-8-sig'))
accounts=json.loads((PRIVATE/'demo-accounts.json').read_text())
cases=[]
def request(path,token=None,body=None,method=None,expected=200):
 headers={'Accept':'application/json','Origin':'http://127.0.0.1:5173'}
 if token:headers['Authorization']='Bearer '+token
 if body is not None:headers['Content-Type']='application/json'
 req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),headers=headers,method=method or ('POST' if body is not None else 'GET'))
 try:
  with urllib.request.urlopen(req,timeout=30) as r:status,raw=r.status,r.read()
 except urllib.error.HTTPError as e:status,raw=e.code,e.read()
 data=json.loads(raw);assert status==expected,(path,status,expected,data.get('code'))
 return data.get('data')
def login(name,pw):return request('/api/auth/login',body={'username':name,'password':pw})['accessToken']
def equal(a,b):assert a==b,(a,b)
def check(name,fn):
 fn();cases.append({'name':name,'result':'PASS'})
 (ROOT/'evidence/p2-api-tests.json').write_text(json.dumps({'at':dt.datetime.now().astimezone().isoformat(),'checks':cases,'scope':'isolated P2 fixtures; real services; deadlines use wall time'},ensure_ascii=False,indent=2),encoding='utf8')
def secret_free(value):
 if isinstance(value,dict):
  assert not {'correctOption','explanation','correct'}.intersection(value),value.keys()
  for v in value.values():secret_free(v)
 elif isinstance(value,list):
  for v in value:secret_free(v)
stamp=secrets.token_hex(4)
topic='P2唐诗意象 '+stamp
admin=login('admin',cfg['HW_ADMIN_PASSWORD']);teacher=login(accounts[0]['username'],accounts[0]['password'])
other_teacher=login(accounts[1]['username'],accounts[1]['password'])
new=request('/api/admin/users/import',admin,{'role':'STUDENT','rows':[{'username':'p2_'+stamp,'displayName':'P2题库验证同窗'}]})['items'][0]
student=login(new['username'],new['initialPassword']);other_student=login(accounts[2]['username'],accounts[2]['password'])
course=request('/api/teacher/courses',teacher,{'name':'P2问学验证 · 唐诗意象 '+stamp,'category':'古典文学','summary':'独立的题库、组卷与限时作答验证课程。所列题目和成绩仅用于测试。'})
cid=course['id'];chapter=request('/api/teacher/courses/'+cid+'/chapters',teacher,{'title':'字句与意象','content':'此章为P2验证材料。文学理解应以原文及语境为依据。','sortNo':1})
request('/api/teacher/courses/'+cid+'/publish',teacher,method='POST')
check('未选课不能开始测验准备',lambda:request('/api/courses/'+cid+'/quizzes',student,expected=403))
request('/api/enrollments',student,{'courseId':cid})
check('学生不能读取教师题库',lambda:request('/api/teacher/question-bank',student,expected=403))
bank=[]
specs=[('SINGLE',2,'A','理解“举头望明月”时，应首先依据什么？'),('MULTIPLE',5,'AC','赏析古诗时，哪些材料可以作为判断依据？'),('TRUE_FALSE',3,'B','脱离原文也能仅凭页面配色确定诗歌主题。')]
for typ,points,answer,prompt in specs:
 bank.append(request('/api/teacher/question-bank',teacher,{'questionType':typ,'points':points,'prompt':prompt,
 'optionA':'字句','optionB':'按钮','optionC':'语境','optionD':'账号','correctOption':answer,'explanation':'这是项目自编的P2验证题。','topic':topic}))
check('题库题型筛选只返回匹配题型',lambda:equal([x['questionType'] for x in request('/api/teacher/question-bank?type=MULTIPLE&topic='+urllib.parse.quote(topic)+'&keyword='+urllib.parse.quote('赏析古诗'),teacher)['items']],['MULTIPLE']))
check('其他教师不能修改题笺',lambda:request('/api/teacher/question-bank/'+bank[0]['id'],other_teacher,{'prompt':'越权'},method='PUT',expected=403))
check('题库版本冲突被拒绝',lambda:request('/api/teacher/question-bank/'+bank[0]['id']+'/status',teacher,{'status':'ARCHIVED','expectedVersion':99},method='PUT',expected=409))
def quiz(title,**rules):
 q=request('/api/teacher/courses/'+cid+'/quizzes',teacher,{'chapterId':chapter['id'],'title':title,'instructions':'P2独立验证试卷；多选全对得分。',**rules})
 for b in bank:q=request('/api/teacher/quizzes/'+q['id']+'/from-bank',teacher,{'bankId':b['id'],'bankVersion':b['version'],'expectedVersion':q['version']})
 return request('/api/teacher/quizzes/'+q['id']+'/status',teacher,{'status':'PUBLISHED','expectedVersion':q['version']},method='PUT')
q=quiz('题型与重试验证',timeLimitMinutes=10,maxAttempts=2)
check('其他教师不能向试卷组题',lambda:request('/api/teacher/quizzes/'+q['id']+'/from-bank',other_teacher,{'bankId':bank[0]['id'],'bankVersion':0,'expectedVersion':q['version']},expected=403))
check('发布后题目被冻结',lambda:request('/api/teacher/quizzes/'+q['id']+'/questions',teacher,{'expectedVersion':q['version']},expected=409))
check('学生详情不提前返回题目与答案',lambda:equal('questions' in request('/api/quizzes/'+q['id'],student),False))
check('未选课学生不能开始作答',lambda:request('/api/practices/'+q['id']+'/start',other_student,method='POST',expected=403))
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
 starts=list(pool.map(lambda _:request('/api/practices/'+q['id']+'/start',student,method='POST'),range(4)))
r=starts[0]
check('并发开始复用同一作答且仅消耗一次机会',lambda:(equal(len({x['id'] for x in starts}),1),equal(request('/api/practices/'+q['id']+'/state',student)['usedAttempts'],1)))
check('开始返回的作答快照没有正确答案或解析',lambda:secret_free(r))
check('服务器生成限时截止时间',lambda:equal(round((dt.datetime.fromisoformat(r['deadlineAt'])-dt.datetime.fromisoformat(r['startedAt'])).total_seconds()),600))
check('他人runId不能保存草稿',lambda:request('/api/practices/'+q['id']+'/runs/'+r['id']+'/draft',other_student,{'answers':[],'expectedVersion':0},method='PUT',expected=403))
def answers(run,choices):return [{'questionId':item['id'],'choice':choice} for item,choice in zip(run['questions'],choices)]
draft=request('/api/practices/'+q['id']+'/runs/'+r['id']+'/draft',student,{'answers':answers(r,['A','CA','B']),'expectedVersion':0},method='PUT')
check('草稿恢复并规范化多选顺序',lambda:equal([x['selectedOption'] for x in request('/api/practices/'+q['id']+'/state',student)['active']['questions']],['A','AC','B']))
check('草稿旧版本不能覆盖新版本',lambda:request('/api/practices/'+q['id']+'/runs/'+r['id']+'/draft',student,{'answers':[],'expectedVersion':0},method='PUT',expected=409))
check('缺少runId不能绕过限时开始',lambda:request('/api/practices/'+q['id']+'/attempts',student,{'answers':answers(r,['A','AC','B'])},expected=400))
check('重复题目不能交卷',lambda:request('/api/practices/'+q['id']+'/attempts',student,{'runId':r['id'],'answers':[{'questionId':r['questions'][0]['id'],'choice':'A'}]*3},expected=400))
first=request('/api/practices/'+q['id']+'/attempts',student,{'runId':r['id'],'answers':answers(r,['A','CA','B'])})
check('三题型按2/5/3分计分，满分10',lambda:(equal(first['score'],10),equal(first['total'],10)))
check('有重试机会时不泄露逐题答案解析',lambda:(equal(first['answersReleased'],False),secret_free(first['items'])))
second_run=request('/api/practices/'+q['id']+'/start',student,method='POST')
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
 repeats=list(pool.map(lambda _:request('/api/practices/'+q['id']+'/attempts',student,{'runId':second_run['id'],'answers':answers(second_run,['A','A','A'])}),range(3)))
second=repeats[0]
check('并发交卷只生成一份成绩，多选少选不得分',lambda:(equal(len({x['id'] for x in repeats}),1),equal(second['score'],2)))
check('次数耗尽后开放解析',lambda:(equal(second['answersReleased'],True),equal(second['items'][1]['correctOption'],'AC')))
check('达到次数上限不能再开始',lambda:request('/api/practices/'+q['id']+'/start',student,method='POST',expected=409))
check('两次独立成绩可分别回看',lambda:(equal(len(request('/api/practices/'+q['id']+'/state',student)['history']),2),equal(request('/api/practices/'+q['id']+'/result?attemptId='+first['id'],student)['score'],10)))
check('重复交卷不能改变原成绩',lambda:equal(request('/api/practices/'+q['id']+'/attempts',student,{'runId':r['id'],'answers':answers(r,['B','BD','A'])})['score'],10))
# Editing source bank leaves both published quiz and completed answer snapshots unchanged.
changed={k:bank[0][k] for k in ['questionType','points','prompt','optionA','optionB','optionC','optionD','correctOption','explanation','topic']}
changed.update(prompt=bank[0]['prompt']+'（题库修订）',correctOption='B',expectedVersion=0)
bank[0]=request('/api/teacher/question-bank/'+bank[0]['id'],teacher,changed,method='PUT')
check('题库修订不改变已发布试卷和历史答卷',lambda:(equal(request('/api/quizzes/'+q['id'],teacher)['questions'][0]['correctOption'],'A'),equal(request('/api/practices/'+q['id']+'/result?attemptId='+first['id'],student)['items'][0]['correctOption'],'A')))
now=dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))
future=quiz('尚未开放验证',opensAt=(now+dt.timedelta(days=1)).isoformat(),maxAttempts=1)
check('未开放测验被服务器拒绝',lambda:request('/api/practices/'+future['id']+'/start',student,method='POST',expected=409))
closed=quiz('已截止验证',closesAt=(now-dt.timedelta(minutes=1)).isoformat(),maxAttempts=1)
check('已截止测验被服务器拒绝',lambda:request('/api/practices/'+closed['id']+'/start',student,method='POST',expected=409))
expiring=quiz('真实截止时间验证',closesAt=(dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))+dt.timedelta(seconds=5)).isoformat(),timeLimitMinutes=10,maxAttempts=2)
er=request('/api/practices/'+expiring['id']+'/start',student,method='POST')
time.sleep(max(0,(dt.datetime.fromisoformat(er['deadlineAt'])-dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))).total_seconds())+.1)
check('真实超时不能交卷且未伪造成绩',lambda:(equal(request('/api/practices/'+expiring['id']+'/attempts',student,{'runId':er['id'],'answers':answers(er,['A','AC','B'])})['status'],'EXPIRED'),equal(request('/api/practices/'+expiring['id']+'/state',student)['history'],[])))
archived=quiz('归档期间拒绝交卷验证',maxAttempts=2);ar=request('/api/practices/'+archived['id']+'/start',student,method='POST')
request('/api/teacher/quizzes/'+archived['id']+'/status',teacher,{'status':'ARCHIVED','expectedVersion':archived['version']},method='PUT')
check('开始后归档仍禁止交卷',lambda:request('/api/practices/'+archived['id']+'/attempts',student,{'runId':ar['id'],'answers':answers(ar,['A','AC','B'])},expected=409))
check('教师成绩记录包含独立尝试次数',lambda:equal([x['attemptNo'] for x in request('/api/teacher/courses/'+cid+'/practice-results',teacher)['items']],[2,1]))
check('他人教师不能读取课程测验成绩',lambda:request('/api/teacher/courses/'+cid+'/practice-results',other_teacher,expected=403))
legacy=[]
for a in accounts[2:]:
 tok=login(a['username'],a['password'])
 for quiz_id in ['2','3','4']:
  try:old=request('/api/practices/'+quiz_id+'/result',tok)
  except AssertionError:continue
  if old and old['runId'] is None:legacy.append(old)
check('旧单选答卷原分数及解析继续可读',lambda:(equal(bool(legacy),True),equal(all(x['answersReleased'] and all(y['questionType']=='SINGLE' and y['points']==1 for y in x['items']) for x in legacy),True)))
browser=quiz('月色与诗心 · 读后小试',timeLimitMinutes=10,maxAttempts=2)
fixture={'courseId':cid,'chapterId':chapter['id'],'quizId':q['id'],'browserQuizId':browser['id'],'username':new['username'],'password':new['initialPassword'],'bank':bank}
(PRIVATE/'p2-fixtures.json').write_text(json.dumps(fixture,ensure_ascii=False,indent=2),encoding='utf8')
print('P2 API:',len(cases),'checks PASS; isolated fixtures saved privately.')
