"""Read-only authenticated local mixed-workload test, bounded to a dedicated verification course."""
import concurrent.futures as cf,datetime as dt,json,time,statistics,urllib.request,urllib.error
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE='http://127.0.0.1:8088'
f=json.loads((ROOT/'.local/platform-fixtures.json').read_text());student=f['students'][0]
def get(path,token=None,body=None):
 headers={'Accept':'application/json','Origin':'http://127.0.0.1:5173'}
 if token:headers['Authorization']='Bearer '+token
 if body is not None:headers['Content-Type']='application/json'
 req=urllib.request.Request(BASE+path,headers=headers,data=None if body is None else json.dumps(body).encode())
 try:
  with urllib.request.urlopen(req,timeout=15) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,{}
 except Exception:return 0,{}
status,data=get('/api/auth/login',body={'username':student['username'],'password':student['initialPassword']});assert status==200;token=data['data']['accessToken'];cid=f['courseId']
paths=['/api/courses/'+cid,'/api/courses/'+cid+'/chapters','/api/grades/courses/'+cid,'/api/assignments/'+f['taskId'],'/api/literature/'+f['workId'],'/api/classes/'+f['roomId']]
def one(index):
 start=time.perf_counter();status,result=get(paths[index%len(paths)],token);return {'path':paths[index%len(paths)],'status':status,'ms':round((time.perf_counter()-start)*1000,3)}
stages=[]
for concurrency in (1,4,8):
 start=time.perf_counter()
 with cf.ThreadPoolExecutor(max_workers=concurrency) as pool:rows=list(pool.map(one,range(96)))
 elapsed=time.perf_counter()-start;latencies=sorted(x['ms'] for x in rows);ok=sum(x['status']==200 for x in rows)
 stages.append({'concurrency':concurrency,'requests':len(rows),'success':ok,'errors':len(rows)-ok,'elapsedSeconds':round(elapsed,3),'requestsPerSecond':round(len(rows)/elapsed,2),'p50ms':statistics.median(latencies),'p95ms':latencies[int(len(rows)*.95)-1],'maxMs':max(latencies),'responses':rows})
out={'at':dt.datetime.now().astimezone().isoformat(),'result':'PASS' if all(x['errors']==0 for x in stages) else 'FAIL','stages':stages,'scope':'288 authenticated read requests over 6 real interfaces; local Windows host, small dedicated two-student course; no production capacity or concurrent-user claim'}
(ROOT/'evidence/runtime-load.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps({k:v for k,v in out.items() if k!='stages'},ensure_ascii=False));print(json.dumps([{k:v for k,v in s.items() if k!='responses'} for s in stages],ensure_ascii=False));assert out['result']=='PASS'
