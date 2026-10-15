"""P3-P5 real API checks against isolated test students/course; original teaching records untouched."""
import datetime as dt,json,os,secrets,urllib.request,urllib.error,urllib.parse,hashlib,base64
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];PRIVATE=ROOT/'.local';BASE=os.environ.get('HW_TEST_BASE','http://127.0.0.1:8088')
assert BASE in ('http://127.0.0.1:8088','http://127.0.0.1:8080')
cfg=json.loads((PRIVATE/'runtime.json').read_text(encoding='utf-8-sig'));accounts=json.loads((PRIVATE/'demo-accounts.json').read_text());checks=[]
def request(path,token=None,body=None,method=None,expected=200,binary=None):
 headers={'Accept':'application/json','Origin':'http://127.0.0.1:5173'}
 if token:headers['Authorization']='Bearer '+token
 if binary:headers['Content-Type']=binary[1];payload=binary[0]
 elif body is not None:headers['Content-Type']='application/json';payload=json.dumps(body,ensure_ascii=False).encode()
 else:payload=None
 req=urllib.request.Request(BASE+path,data=payload,headers=headers,method=method or ('POST' if payload is not None else 'GET'))
 try:
  with urllib.request.urlopen(req,timeout=30) as res:status,raw=res.status,res.read()
 except urllib.error.HTTPError as e:status,raw=e.code,e.read()
 assert status==expected,(path,status,expected,raw[:500].decode(errors='replace'))
 if '/file' in path and status==200:return raw
 return json.loads(raw).get('data')
def login(name,pw):return request('/api/auth/login',body={'username':name,'password':pw})['accessToken']
def eq(a,b):assert a==b,(a,b)
def check(name,fn):
 fn();checks.append({'name':name,'result':'PASS'});(ROOT/'evidence/platform-api-tests.json').write_text(json.dumps({'at':dt.datetime.now().astimezone().isoformat(),'checks':checks,'scope':'Fresh isolated P3-P5 teaching fixtures; original student work not modified'},ensure_ascii=False,indent=2),encoding='utf8')
def upload(path,token,name,data,fields=None,expected=200):
 boundary='HW'+secrets.token_hex(12);parts=[]
 for key,value in (fields or {}).items():parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
 parts.extend([f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode(),data,f'\r\n--{boundary}--\r\n'.encode()])
 return request(path,token,binary=(b''.join(parts),'multipart/form-data; boundary='+boundary),expected=expected)
stamp=secrets.token_hex(4);admin=login('admin',cfg['HW_ADMIN_PASSWORD']);teacher=login(accounts[0]['username'],accounts[0]['password']);other_teacher=login(accounts[1]['username'],accounts[1]['password']);outsider=login(accounts[2]['username'],accounts[2]['password']);outsider_id=request('/api/users/me',outsider)['id']
students=request('/api/admin/users/import',admin,{'role':'STUDENT','rows':[{'username':'pf_a_'+stamp,'displayName':'验收同窗甲'},{'username':'pf_b_'+stamp,'displayName':'验收同窗乙'}]})['items'];sa=login(students[0]['username'],students[0]['initialPassword']);sb=login(students[1]['username'],students[1]['initialPassword'])
course=request('/api/teacher/courses',teacher,{'name':'锦卷综合验收 · 月下读诗 '+stamp,'category':'古典文学','summary':'专用验证课程：讲义、分项批阅、总评和站内叮嘱。成绩仅用于验证。'});cid=course['id']
chapter_body={'title':'静夜思 · 字句与乡思','content':'床前明月光，疑是地上霜。\n举头望明月，低头思故乡。','sortNo':1,'lessonDocument':'# 月色与乡思\n**细读原文**，结合动作和视线理解情感。\n> 举头望明月，低头思故乡。\n- 寻找诗中的意象\n- 说明判断的原文依据'}
chapter=request('/api/teacher/courses/'+cid+'/chapters',teacher,chapter_body)
image=(ROOT/'output/playwright/p2-bank-320.png').read_bytes()
resource=upload('/api/teacher/courses/'+cid+'/resources',teacher,'teacher-preview.png',image,{'title':'界面结构验证图片','description':'用于验证讲义配图鉴权，不作为诗歌插画。'})
chapter_body['lessonDocument']+='\n![界面结构验证](resource:'+resource['id']+')'
request('/api/teacher/courses/'+cid+'/chapters/'+chapter['id'],teacher,chapter_body,method='PUT')
check('教师讲义保存回显一致',lambda:eq(request('/api/courses/'+cid+'/chapters/'+chapter['id'],teacher)['lessonDocument'],chapter_body['lessonDocument']))
check('讲义拒绝HTML事件代码',lambda:request('/api/teacher/courses/'+cid+'/chapters/'+chapter['id'],teacher,{**chapter_body,'lessonDocument':'<img src=x onerror=alert(1)>'},method='PUT',expected=400))
check('讲义拒绝javascript链接',lambda:request('/api/teacher/courses/'+cid+'/chapters/'+chapter['id'],teacher,{**chapter_body,'lessonDocument':'[点击](javascript:alert)'},method='PUT',expected=400))
check('讲义拒绝不属于课程的图片编号',lambda:request('/api/teacher/courses/'+cid+'/chapters/'+chapter['id'],teacher,{**chapter_body,'lessonDocument':'![图](resource:999999999)'},method='PUT',expected=400))
request('/api/teacher/courses/'+cid+'/publish',teacher,method='POST');enrollment=request('/api/enrollments',sa,{'courseId':cid});request('/api/enrollments',sb,{'courseId':cid})
check('已选课学生能下载讲义图片',lambda:eq(request('/api/resources/'+resource['id']+'/file',sa),image))
check('未选课学生不能下载讲义图片',lambda:request('/api/resources/'+resource['id']+'/file',outsider,expected=403))
future=(dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))+dt.timedelta(hours=12)).isoformat()
rubric=[{'name':'原文理解','maxScore':'60'},{'name':'论证表达','maxScore':'40'}]
def task(title='从月色到乡思',**extra):return request('/api/teacher/assignments',teacher,{'courseId':cid,'title':title,'description':'任选一处意象，以原文为依据分析情感。译文应注明由谁编写。','maxScore':'100','deadlineAt':future,'rubric':rubric,**extra})
check('分项满分总和不等于作业满分时拒绝',lambda:request('/api/teacher/assignments',teacher,{'courseId':cid,'title':'无效细则','description':'验证','maxScore':'100','deadlineAt':future,'rubric':rubric[:1]},expected=400))
t=task();request('/api/teacher/assignments/'+t['id']+'/publish',teacher,method='POST')
check('发布后评分细则被冻结',lambda:request('/api/teacher/assignments/'+t['id'],teacher,{'title':'改题'},method='PUT',expected=409))
check('未提交学生显示48小时截止提醒',lambda:eq(any(x['kind']=='DEADLINE' for x in request('/api/assignments/reminders',sa)['items']),True))
recipients=request('/api/teacher/assignments/'+t['id']+'/reminder-candidates',teacher)
check('催交名单仅为本课程未交学生',lambda:eq({x['studentId'] for x in recipients},{str(students[0]['id']),str(students[1]['id'])}))
send={'studentIds':[students[0]['id'],students[1]['id']],'content':'请细读原文后完成习作，疑问可到课堂交流提问。'}
check('催交仅发给选中未交同窗',lambda:eq(request('/api/teacher/assignments/'+t['id']+'/remind',teacher,send)['sent'],2))
check('同一日重复催交不会重复生成',lambda:eq(request('/api/teacher/assignments/'+t['id']+'/remind',teacher,send)['sent'],0))
check('其他教师不能发出催交',lambda:request('/api/teacher/assignments/'+t['id']+'/remind',other_teacher,send,expected=403))
check('催交不能发给未选课学生',lambda:request('/api/teacher/assignments/'+t['id']+'/remind',teacher,{'studentIds':[outsider_id],'content':'越权目标'},expected=409))
event=next(x for x in request('/api/assignments/reminders',sa)['items'] if x['kind']=='NUDGE');request('/api/inbox/receipts',sa,{'keys':[event['eventKey']]})
check('叮嘱已读状态随本人账号保存',lambda:eq(any(x['eventKey']==event['eventKey'] for x in request('/api/inbox/receipts',sa)['items']),True))
attachment=upload('/api/assignments/'+t['id']+'/attachments',sa,'思考札记.txt','月色引出乡思。原文证据：低头思故乡。'.encode())
check('未交卷附件不向教师开放',lambda:request('/api/submissions/attachments/'+attachment['id']+'/file',teacher,expected=403))
check('其他学生不能读取附件',lambda:request('/api/submissions/attachments/'+attachment['id']+'/file',sb,expected=403))
check('文件内容与扩展名不一致时拒绝',lambda:upload('/api/assignments/'+t['id']+'/attachments',sa,'fake.png',b'not png',expected=400))
for i in range(4):upload('/api/assignments/'+t['id']+'/attachments',sa,f'note-{i}.txt',b'fixture')
check('每份作业第六个附件被拒绝',lambda:upload('/api/assignments/'+t['id']+'/attachments',sa,'sixth.txt',b'fixture',expected=400))
sub=request('/api/assignments/'+t['id']+'/submissions',sa,{'answerText':'举头与低头呈现视线转移，月光把观察景物引向思乡。原文“低头思故乡”明确点出情感。'})
check('交卷后不再展示截止催交提示',lambda:eq(any(x['kind']=='DEADLINE' and t['id'] in x['url'].split('/') for x in request('/api/assignments/reminders',sa)['items']),False))
check('交卷后教师能读取本课程随卷附件',lambda:eq(hashlib.sha256(request('/api/submissions/attachments/'+attachment['id']+'/file',teacher)).hexdigest(),attachment['sha256']))
check('其他教师不能读取随卷附件',lambda:request('/api/submissions/attachments/'+attachment['id']+'/file',other_teacher,expected=403))
check('集中批阅队列包含本课程待批答卷',lambda:eq(request('/api/submissions/queue?courseId='+cid,teacher)['items'][0]['id'],sub['id']))
check('学生不能使用教师集中队列',lambda:request('/api/submissions/queue',sa,expected=403))
check('教师不能查看别人的集中队列课程',lambda:request('/api/submissions/queue?courseId='+cid,other_teacher,expected=403))
policy={'assignmentWeight':100,'missingRule':'EXCLUDE','attemptRule':'HIGHEST','expectedVersion':-1};request('/api/grades/courses/'+cid+'/policy',teacher,policy,method='PUT')
check('待批阅答卷的总评保持未知',lambda:eq(request('/api/grades/courses/'+cid,sa)['students'][0]['total'],None))
check('分项超出满分时后端拒绝',lambda:request('/api/submissions/'+sub['id']+'/grade',teacher,{'score':'99','comment':'验证','expectedVersion':0,'rubricScores':['61','30']},method='PUT',expected=400))
g=request('/api/submissions/'+sub['id']+'/grade',teacher,{'score':'99','comment':'原文依据明确，分析紧扣语境。','expectedVersion':0,'rubricScores':['50','30']},method='PUT')
check('评分由后端按细则求和而非客户端总分',lambda:eq(g['score'],'80.00'))
check('已评分随卷附件锁定',lambda:request('/api/submissions/attachments/'+attachment['id'],sa,method='DELETE',expected=409))
check('学生仅看到自己总评80而非其他同窗',lambda:(eq(len(request('/api/grades/courses/'+cid,sa)['students']),1),eq(request('/api/grades/courses/'+cid,sa)['students'][0]['total'],'80.00')))
check('其他教师不能读取成绩册',lambda:request('/api/grades/courses/'+cid,other_teacher,expected=403))
check('未选课学生不能读取成绩册',lambda:request('/api/grades/courses/'+cid,outsider,expected=403))
g=request('/api/submissions/'+sub['id']+'/grade',teacher,{'score':'1','comment':'补充表述后更正表达项。','expectedVersion':1,'rubricScores':['50','32']},method='PUT')
check('评分更正保存全部分项历史',lambda:(eq(g['score'],'82.00'),eq([x['rubricScores'] for x in request('/api/submissions/'+sub['id']+'/grade-history',sa)['items']],[['50','32'],['50','30']])))
check('旧版本评分不能覆盖更正',lambda:request('/api/submissions/'+sub['id']+'/grade',teacher,{'score':'1','expectedVersion':1,'rubricScores':['50','20']},method='PUT',expected=409))
request('/api/teacher/assignments/'+t['id']+'/close',teacher,method='POST');book=request('/api/grades/courses/'+cid,teacher)
check('缺交与待评分单独计数',lambda:(eq(next(x for x in book['students'] if x['studentId']==str(students[1]['id']))['missing'],1),eq(next(x for x in book['students'] if x['studentId']==str(students[1]['id']))['pending'],0)))
request('/api/grades/courses/'+cid+'/policy',teacher,{**policy,'missingRule':'ZERO','expectedVersion':0},method='PUT')
check('缺交零分规则只针对已关闭或已截止项',lambda:eq(request('/api/grades/courses/'+cid,sb)['students'][0]['total'],'0.00'))
check('总评规则旧版本更新被拒绝',lambda:request('/api/grades/courses/'+cid+'/policy',teacher,{**policy,'expectedVersion':0},method='PUT',expected=409))
q=request('/api/teacher/courses/'+cid+'/quizzes',teacher,{'chapterId':chapter['id'],'title':'意象辨析','timeLimitMinutes':0,'maxAttempts':2});q=request('/api/teacher/quizzes/'+q['id']+'/questions',teacher,{'prompt':'“低头思故乡”直抒思乡。','questionType':'TRUE_FALSE','points':10,'correctOption':'A','sortNo':1,'expectedVersion':q['version']});q=request('/api/teacher/quizzes/'+q['id']+'/status',teacher,{'status':'PUBLISHED','expectedVersion':q['version']},method='PUT')
for choice in ['A','B']:
 run=request('/api/practices/'+q['id']+'/start',sa,method='POST');request('/api/practices/'+q['id']+'/attempts',sa,{'runId':run['id'],'answers':[{'questionId':run['questions'][0]['id'],'choice':choice}]})
request('/api/grades/courses/'+cid+'/policy',teacher,{**policy,'assignmentWeight':70,'expectedVersion':1},method='PUT')
check('最高测验成绩参与70/30总评',lambda:eq(request('/api/grades/courses/'+cid,sa)['students'][0]['total'],'87.40'))
request('/api/grades/courses/'+cid+'/policy',teacher,{**policy,'assignmentWeight':70,'attemptRule':'LATEST','expectedVersion':2},method='PUT')
check('最近一次测验口径可解释且独立于历史',lambda:eq(request('/api/grades/courses/'+cid,sa)['students'][0]['total'],'57.40'))
work=request('/api/teacher/literature',teacher,{'title':'静夜思 · 译注验收 '+stamp,'author':'李白','dynasty':'唐','genre':'诗','content':chapter_body['content'],'translation':'床前洒下明亮的月光，仿佛地面结霜。抬头望月，低头思念故乡。','annotationsText':'疑：好像。此译文与注释为项目验证时编写。','sourceNote':'原文采用常见选本措辞；译注由项目编写，关联为验收示例。','allusions':[{'term':'乡思','explanation':'月色触发思乡，解读应结合原文，而非仅凭视觉装饰。','workId':1,'sourceUrl':'https://zh.wikisource.org/wiki/靜夜思'}]})
check('作品原文译注与典故保存回显一致',lambda:(eq(request('/api/literature/'+work['id'],sa)['translation'],work['translation']),eq(len(work['allusions']),1)))
check('其他教师不能修订作品译注',lambda:request('/api/teacher/literature/'+work['id'],other_teacher,{'title':'越权'},method='PUT',expected=403))
request('/api/teacher/literature/'+work['id']+'/status',teacher,{'status':'ARCHIVED','expectedVersion':work['version']},method='PUT')
check('作品归档不清除译注典故',lambda:eq(request('/api/literature/'+work['id'],teacher)['translation'],work['translation']))
request('/api/teacher/literature/'+work['id']+'/status',teacher,{'status':'PUBLISHED','expectedVersion':work['version']+1},method='PUT')
room=request('/api/teacher/classes',teacher,{'courseId':cid,'name':'月下学舍','semester':'2026秋验证'+stamp,'description':'独立验证班级，不混用原班级。'})
request('/api/classes/join',sa,{'code':room['joinCode']});request('/api/classes/join',sb,{'code':room['joinCode']})
check('学期筛选只显示目标班级',lambda:eq([x['id'] for x in request('/api/classes?semester='+urllib.parse.quote(room['semester']),sa)['items']],[room['id']]))
request('/api/teacher/classes/'+room['id'],teacher,{'name':room['name'],'semester':room['semester'],'description':room['description'],'status':'CLOSED','expectedVersion':room['version']},method='PUT')
check('结课班级仍可查阅同窗历史',lambda:eq(request('/api/classes/'+room['id'],sa)['status'],'CLOSED'))
check('结课后成绩和选课资格仍保留',lambda:eq(request('/api/grades/courses/'+cid,sa)['students'][0]['total'],'57.40'))
check('其他教师不能读取班级名单',lambda:request('/api/teacher/classes/'+room['id']+'/members',other_teacher,expected=403))
copied=request('/api/teacher/courses/'+cid+'/copy',teacher,{'name':'新学期复用验证 '+stamp})['course'];request('/api/teacher/courses/'+copied['id']+'/publish',teacher,method='POST');copy=request('/api/teacher/assignments/copy',teacher,{'fromCourseId':cid,'toCourseId':copied['id']})
ct=request('/api/courses/'+copied['id']+'/assignments',teacher)['items'][0]
check('复用作业保留细则并清空日期与成绩',lambda:(eq(ct['rubric'],t['rubric']),eq(ct['deadlineAt'],None),eq(ct['status'],'DRAFT'),eq(request('/api/assignments/'+ct['id']+'/submissions',teacher)['total'],0)))
check('复用课程讲义不引用旧课程图片',lambda:eq('resource:' in (request('/api/courses/'+copied['id']+'/chapters/'+request('/api/courses/'+copied['id']+'/chapters',teacher)[0]['id'],teacher)['lessonDocument'] or ''),False))
legacy=login(accounts[3]['username'],accounts[3]['password'])
check('原有学生历史单选答卷仍能回看',lambda:eq(request('/api/practices/2/result',legacy)['items'][0]['questionType'],'SINGLE'))
PRIVATE.joinpath('platform-fixtures.json').write_text(json.dumps({'courseId':cid,'chapterId':chapter['id'],'enrollmentId':enrollment['id'],'taskId':t['id'],'submissionId':sub['id'],'resourceId':resource['id'],'workId':work['id'],'roomId':room['id'],'copiedCourseId':copied['id'],'students':students,'teacher':accounts[0],'chapterBody':chapter_body},ensure_ascii=False,indent=2),encoding='utf8')
print('P3-P5 real API checks PASS:',len(checks))
