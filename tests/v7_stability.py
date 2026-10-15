"""Bounded local concurrency and persistence checks in a dedicated course."""
from v6_review import call,login,ROOT,PRIVATE,BASE
import concurrent.futures as cf,datetime as dt,json,secrets,time,urllib.request,urllib.error,statistics
checks=[];latencies=[]
def check(name,ok=True):
 assert ok,name;checks.append({'name':name,'result':'PASS'})
def status(path,token,body):
 req=urllib.request.Request(BASE+path,headers={'Authorization':'Bearer '+token,'Origin':'http://127.0.0.1:5173','Content-Type':'application/json'},data=json.dumps(body).encode(),method='PUT')
 try:
  with urllib.request.urlopen(req,timeout=30) as r:return r.status,json.load(r)['data']
 except urllib.error.HTTPError as e:return e.code,None
def parallel(fn,values,workers=8):
 with cf.ThreadPoolExecutor(max_workers=workers) as pool:return list(pool.map(fn,values))
if __name__=='__main__':
 cfg=json.loads((PRIVATE/'runtime.json').read_text(encoding='utf-8-sig'));demo=json.loads((PRIVATE/'demo-accounts.json').read_text());admin=login({'username':'admin','password':cfg['HW_ADMIN_PASSWORD']});teacher=login(demo[0]);stamp=secrets.token_hex(4)
 rows=call('/api/admin/users/import',admin,{'role':'STUDENT','rows':[{'username':'v7_load_'+stamp+'_'+str(i),'displayName':'并发验收同窗'+str(i+1)} for i in range(24)]})['items'];students=[{'username':x['username'],'password':x['initialPassword'],'id':x['id']} for x in rows];tokens=list(map(login,students))
 c=call('/api/teacher/courses',teacher,{'name':'V7并发验收 '+stamp,'category':'验收专用','summary':'独立课程，不混入功能演示学情。'});cid=c['id'];ch=call('/api/teacher/courses/'+cid+'/chapters',teacher,{'title':'乡思','content':'低头思故乡。','sortNo':1});call('/api/teacher/courses/'+cid+'/publish',teacher,method='POST')
 for token in tokens:call('/api/enrollments',token,{'courseId':cid})
 q=call('/api/teacher/courses/'+cid+'/quizzes',teacher,{'chapterId':ch['id'],'title':'并发答卷','instructions':'稳定性测试','timeLimitMinutes':0,'maxAttempts':1})
 q=call('/api/teacher/quizzes/'+q['id']+'/questions',teacher,{'questionType':'SHORT_ANSWER','prompt':'解释乡思','correctOption':'对故乡的思念。','explanation':'结合诗句。','points':10,'sortNo':1,'knowledgePoint':'乡思','expectedVersion':q['version']})
 q=call('/api/teacher/quizzes/'+q['id']+'/status',teacher,{'status':'PUBLISHED','expectedVersion':q['version']},method='PUT');qid=q['id'];first=tokens[0]
 call('/api/practices/'+qid+'/state',first)
 starts=parallel(lambda _:call('/api/practices/'+qid+'/start',first,method='POST'),range(8));run=starts[0];check('同一学生8路同时开始只生成同一作答',len({x['id'] for x in starts})==1)
 body={'runId':run['id'],'answers':[{'questionId':run['questions'][0]['id'],'choice':'思念故乡。'}]};attempts=parallel(lambda _:call('/api/practices/'+qid+'/attempts',first,body),range(8));aid=attempts[0]['id'];check('同一作答8路重复交卷只返回一份答卷',len({x['id'] for x in attempts})==1 and len(call('/api/practices/'+qid+'/state',first)['history'])==1)
 d=call('/api/teacher/practice-attempts/'+aid,teacher);items=[{'id':d['items'][0]['id'],'score':7,'comment':'并发评分验收'}];marked=parallel(lambda _:status('/api/teacher/practice-attempts/'+aid+'/grade',teacher,{'expectedVersion':0,'items':items}),range(2),2);check('两位操作同时批阅同版本仅一次保存，另一请求409',sorted(x[0] for x in marked)==[200,409]);d=call('/api/teacher/practice-attempts/'+aid,teacher);check('并行批阅只有一条历史且正式7分',d['score']==7 and d['gradingVersion']==1 and len(d['history'])==1)
 def flow(token):
  started=time.perf_counter();r=call('/api/practices/'+qid+'/start',token,method='POST');draft=call('/api/practices/'+qid+'/runs/'+r['id']+'/draft',token,{'expectedVersion':r['version'],'answers':[{'questionId':r['questions'][0]['id'],'choice':'低头表达对故乡的思念。'}]},method='PUT');a=call('/api/practices/'+qid+'/attempts',token,{'runId':r['id'],'answers':[{'questionId':r['questions'][0]['id'],'choice':'低头表达对故乡的思念。'}]});state=call('/api/practices/'+qid+'/state',token);assert len(state['history'])==1 and a['gradingStatus']=='PENDING' and a['score'] is None and len(a['items'])==1;latencies.append(time.perf_counter()-started);return a['id']
 rest=parallel(flow,tokens[1:]);check('24名学生在8个工作线程下各自保存一份答卷',len(set([aid,*rest]))==24)
 results=call('/api/teacher/courses/'+cid+'/practice-results?size=100',teacher)
 check('教师结果列表24份，不混入重复提交',results['total']==24)
 until=time.monotonic()+60;reads=[]
 def read(token):
  start=time.perf_counter();a=call('/api/practices/'+qid+'/result',token);assert a['total']==10;return time.perf_counter()-start
 while time.monotonic()<until:
  reads.extend(parallel(read,tokens[:8]));time.sleep(1)
 check('60秒重复读取中成绩与答卷持续可读',len(reads)>=120);check('持续读取后答卷数量仍为24',call('/api/teacher/courses/'+cid+'/practice-results?size=100',teacher)['total']==24)
 (PRIVATE/'v7-stability-fixtures.json').write_text(json.dumps({'courseId':cid,'quizId':qid,'students':students},ensure_ascii=False,indent=2),encoding='utf8')
 sorted_times=sorted(latencies);data={'at':dt.datetime.now().astimezone().isoformat(),'result':'PASS','checks':checks,'students':24,'workers':8,'parallelDuplicateRequests':8,'sustainedReadSeconds':60,'successfulReads':len(reads),'flowMeanMs':round(statistics.mean(latencies)*1000,2),'flowP95Ms':round(sorted_times[max(0,int(len(sorted_times)*.95)-1)]*1000,2),'readP95Ms':round(sorted(reads)[max(0,int(len(reads)*.95)-1)]*1000,2),'scope':'Single Windows PC, local gateway 8088, real MySQL 3307. Flow timing includes start/draft/submit/state. Bounded concurrency and a 60-second read run are not production capacity or long-term soak evidence.'};(ROOT/'evidence/v7-stability.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8');print('V7 real concurrency and 60-second read persistence PASS:',len(checks),'checks;',len(reads),'reads')
