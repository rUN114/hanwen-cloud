"""Stop only this project's two course instances; always attempt restart in finally."""
import integration as it
import json,os,signal,subprocess,time,winreg,urllib.request,datetime
from pathlib import Path
it.RESULTS=it.ROOT/'evidence/v2-recovery-tests.json'
accounts=json.loads((it.PRIVATE/'demo-accounts.json').read_text(encoding='utf8'))
student=it.login(accounts[2]['username'],accounts[2]['password'])['accessToken'];teacher=it.login(accounts[0]['username'],accounts[0]['password'])['accessToken']
# A fresh published fixture avoids dependency on demonstration IDs.
c,h=it.course(teacher,'V2下游故障验证');it.call('/api/teacher/courses/'+c['id']+'/publish',teacher)
e=it.call('/api/enrollments',student,body={'courseId':c['id']})
w=it.call('/api/teacher/literature',teacher,body={'title':'V2故障收藏验证','author':'课程测试','dynasty':'现代','genre':'文','content':'验证故障时不写入收藏。','sourceNote':'测试原创。'})
process_path=it.PRIVATE/'processes.json';processes=json.loads(process_path.read_text(encoding='utf-8-sig'));records=[p for p in processes if p['module']=='course-service'];it.check({r['port'] for r in records}=={8102,8105})
before=it.get('/api/enrollments/summary',student)
for r in records:
 command=subprocess.run(['powershell','-NoProfile','-Command',f"(Get-CimInstance Win32_Process -Filter 'ProcessId={r['pid']}').CommandLine"],capture_output=True,text=True).stdout
 it.check(str(it.ROOT) in command and 'course-service-1.0.0.jar' in command)
try:
 for r in records:os.kill(r['pid'],signal.SIGTERM)
 it.record('V2-R01','双提供者故障时新札记503且无正文写入',lambda:(it.call('/api/notes',student,body={'enrollmentId':e['id'],'chapterId':h['id'],'title':'不能保存','content':'故障时不得绕过章节验证'},expected=503),it.check(it.get('/api/notes?enrollmentId='+e['id'],student)['total']==0)))
 it.record('V2-R02','双提供者故障时新收藏503且收藏数量不变',lambda:(it.call('/api/favorites',student,body={'workId':w['id']},expected=503),it.check(it.get('/api/enrollments/summary',student)['favorites']==before['favorites'])))
 it.record('V2-R03','学情及待作答查询依赖故障时返回503',lambda:(it.request('/api/teacher/courses/'+c['id']+'/learners',teacher,expected=503),it.request('/api/assignments/pending',student,expected=503)))
finally:
 env=dict(os.environ);env.update({k:str(v) for k,v in it.cfg.items()})
 with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE,r'SYSTEM\CurrentControlSet\Control\Session Manager\Environment') as key:java=Path(winreg.QueryValueEx(key,'JAVA_HOME')[0])/'bin/java.exe'
 for r in records:
  log=it.ROOT/'evidence/runtime-logs'
  with (log/f"course-service-{r['port']}.stdout.log").open('ab') as out,(log/f"course-service-{r['port']}.stderr.log").open('ab') as err:
   proc=subprocess.Popen([str(java),'-Xms64m','-Xmx256m','-jar',str(it.ROOT/'backend/course-service/target/course-service-1.0.0.jar'),f"--server.port={r['port']}"],env=env,stdout=out,stderr=err,creationflags=subprocess.CREATE_NO_WINDOW)
  r['pid']=proc.pid;r['started']=datetime.datetime.now().astimezone().isoformat()
 process_path.write_text(json.dumps(processes,indent=2),encoding='utf8')
 for r in records:
  for _ in range(45):
   try:
    with urllib.request.urlopen(f"http://127.0.0.1:{r['port']}/actuator/health",timeout=2) as response:
     if json.load(response)['status']=='UP':break
   except Exception:pass
   time.sleep(1)
  else:raise RuntimeError('Course service recovery failed')
time.sleep(7)
it.record('V2-R04','JDK25双实例恢复后真实资格校验允许札记与收藏写入',lambda:(it.call('/api/notes',student,body={'enrollmentId':e['id'],'chapterId':h['id'],'title':'恢复后记录','content':'恢复后通过实际章节校验。'}),it.call('/api/favorites',student,body={'workId':w['id']})))
it.call('/api/favorites/'+w['id'],student,'DELETE');it.call('/api/teacher/literature/'+w['id']+'/status',teacher,'PUT',{'status':'ARCHIVED','expectedVersion':0});it.call('/api/teacher/courses/'+c['id']+'/archive',teacher)
print('V2 dependency fault and recovery checks complete')
