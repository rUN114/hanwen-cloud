"""Read-only provider outage/recovery exercise. Dedicated local environment only; restarts in finally."""
from pathlib import Path
import datetime as dt,json,os,signal,subprocess,time,urllib.request,urllib.error
ROOT=Path(__file__).resolve().parents[1];private=ROOT/'.local';f=json.loads((private/'platform-fixtures.json').read_text());cfg=json.loads((private/'runtime.json').read_text(encoding='utf-8-sig'))
BASE='http://127.0.0.1:8088';events=[]
def request(path,token=None,body=None):
 h={'Content-Type':'application/json','Origin':'http://127.0.0.1:5173'}
 if token:h['Authorization']='Bearer '+token
 req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=h)
 try:
  with urllib.request.urlopen(req,timeout=12) as res:return res.status,json.load(res).get('data')
 except urllib.error.HTTPError as e:return e.code,None
token=request('/api/auth/login',body={'username':f['teacher']['username'],'password':f['teacher']['password']})[1]['accessToken']
processes=json.loads((private/'processes.json').read_text(encoding='utf-8-sig'));records=[x for x in processes if x['module']=='course-service'];assert {x['port'] for x in records}=={8102,8105}
for item in records:
 cmd=subprocess.run(['powershell','-NoProfile','-Command',f"(Get-CimInstance Win32_Process -Filter 'ProcessId={item['pid']}').CommandLine"],capture_output=True,text=True).stdout
 assert str(ROOT) in cmd and 'course-service-1.0.0.jar' in cmd and '--server.port='+str(item['port']) in cmd
def event(name,ok,detail=None):
 assert ok,name;events.append({'at':dt.datetime.now().astimezone().isoformat(),'name':name,'result':'PASS','detail':detail})
event('故障前总评接口可用',request('/api/grades/courses/'+f['courseId'],token)[0]==200)
start=time.monotonic()
try:
 for item in records:os.kill(item['pid'],signal.SIGTERM)
 time.sleep(2)
 for i in range(3):event('下游故障第'+str(i+1)+'次总评拒绝返回伪零分',request('/api/grades/courses/'+f['courseId'],token)[0]==503)
 event('独立用户服务在课程故障时仍可读',request('/api/users/me',token)[0]==200)
 subprocess.run([os.environ.get('HW_PYTHON','python'),str(ROOT/'tools/healthcheck.py'),'--gateway-port','8088','--output',str(ROOT/'evidence/runtime-health-interrupted.json')],capture_output=True)
 health=json.loads((ROOT/'evidence/runtime-health-interrupted.json').read_text());event('健康检查实际检测到双课程实例故障',health['result']=='FAIL' and sum(x.get('result')=='FAIL' for x in health['checks'] if x.get('servicePort') in (8102,8105))==2)
finally:
 env=dict(os.environ);env.update({k:str(v) for k,v in cfg.items()});java=Path(os.environ['JAVA_HOME'])/'bin/java.exe'
 for item in records:
  log=ROOT/'evidence/runtime-logs'/('recovered-course-'+str(item['port'])+'.log')
  with log.open('ab') as stream:proc=subprocess.Popen([str(java),'-Xms64m','-Xmx256m','-jar',str(ROOT/'backend/course-service/target/course-service-1.0.0.jar'),'--server.port='+str(item['port'])],env=env,stdout=stream,stderr=stream,creationflags=subprocess.CREATE_NO_WINDOW)
  item['pid']=proc.pid;item['supervisedSession']=False;item['started']=dt.datetime.now().astimezone().isoformat()
 (private/'processes.json').write_text(json.dumps(processes,indent=2),encoding='utf8')
for item in records:
 for _ in range(60):
  try:
   with urllib.request.urlopen('http://127.0.0.1:'+str(item['port'])+'/actuator/health',timeout=2) as res:
    if json.load(res)['status']=='UP':break
  except Exception:pass
  time.sleep(1)
 else:raise RuntimeError('Provider failed to recover: '+str(item['port']))
# Registry refresh and half-open probing are request-driven; poll real requests, never assume an idle timer proves recovery.
statuses=[]
for _ in range(30):
 status,_=request('/api/grades/courses/'+f['courseId'],token);statuses.append(status)
 if status==200:break
 time.sleep(1)
event('提供者恢复后真实请求使总评恢复',statuses[-1]==200,{'pollStatuses':statuses,'outageAndRecoverySeconds':round(time.monotonic()-start,3)})
(ROOT/'evidence/runtime-recovery.json').write_text(json.dumps({'result':'PASS','checks':events,'scope':'Actual two-provider local interruption; no teaching writes performed. Not a production restart SLA.'},ensure_ascii=False,indent=2),encoding='utf8');print('Actual provider outage, fault detection and recovery PASS:',len(events))
