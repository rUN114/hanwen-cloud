"""Actual course-provider fault test; dedicated environment only. Restarts in finally."""
import integration as it
import json,os,signal,subprocess,time,winreg,urllib.request,datetime
from pathlib import Path
it.RESULTS=it.ROOT/'evidence/v3-recovery-tests.json'
a=json.loads((it.PRIVATE/'demo-accounts.json').read_text(encoding='utf8'))
t=it.login(a[0]['username'],a[0]['password'])['accessToken'];s=it.login(a[2]['username'],a[2]['password'])['accessToken']
c,h=it.course(t,'V3真实下游故障验证');it.call('/api/teacher/courses/'+c['id']+'/publish',t)
e=it.call('/api/enrollments',s,body={'courseId':c['id']})
room=it.call('/api/teacher/classes',t,body={'courseId':c['id'],'name':'V3故障加入验证','semester':'2026秋'})
q=it.call('/api/teacher/courses/'+c['id']+'/quizzes',t,body={'chapterId':h['id'],'title':'V3故障判分验证'})
qd=it.call('/api/teacher/quizzes/'+q['id']+'/questions',t,body={'prompt':'应如何理解文本？','optionA':'依据原文','optionB':'固定标签','optionC':'随意编造','optionD':'忽略语境','correctOption':'A','explanation':'联系原文。','sortNo':1,'expectedVersion':0})
it.call('/api/teacher/quizzes/'+q['id']+'/status',t,'PUT',{'status':'PUBLISHED','expectedVersion':1})
annotation={'enrollmentId':e['id'],'chapterId':h['id'],'startOffset':0,'endOffset':4,'quoteText':h['content'][:4],'comment':'恢复验证','color':'RED'}
answers={'answers':[{'questionId':qd['questions'][0]['id'],'choice':'A'}]}
mysql=Path('C:/Program Files/MySQL/MySQL Server 9.7/bin/mysql.exe')
def count(table,column,identity):
 assert identity.isdigit()
 p=subprocess.run([str(mysql),'--defaults-file='+str(it.PRIVATE/'backup-client.ini'),'--batch','--skip-column-names','--execute='+f'SELECT COUNT(*) FROM hw_learning.{table} WHERE {column}={identity};'],capture_output=True)
 assert p.returncode==0,'Database verification failed'
 return int(p.stdout)
process_path=it.PRIVATE/'processes.json';processes=json.loads(process_path.read_text(encoding='utf-8-sig'))
records=[p for p in processes if p['module']=='course-service'];it.check({p['port'] for p in records}=={8102,8105})
for r in records:
 command=subprocess.run(['powershell','-NoProfile','-Command',f"(Get-CimInstance Win32_Process -Filter 'ProcessId={r['pid']}').CommandLine"],capture_output=True,text=True).stdout
 it.check(str(it.ROOT) in command and 'course-service-1.0.0.jar' in command)
try:
 for r in records:os.kill(r['pid'],signal.SIGTERM)
 it.record('V3-R01','双提供者故障时批注503，数据库无批注写入',lambda:(it.call('/api/annotations',s,body=annotation,expected=503),it.check(count('reading_annotation','enrollment_id',e['id'])==0)))
 it.record('V3-R02','双提供者故障时入班503，数据库无成员写入',lambda:(it.call('/api/classes/join',s,body={'code':room['joinCode']},expected=503),it.check(count('class_member','class_id',room['id'])==0)))
 it.record('V3-R03','双提供者故障时交卷503，数据库无成绩和快照写入',lambda:(it.call('/api/practices/'+q['id']+'/attempts',s,body=answers,expected=503),it.check(count('quiz_attempt','quiz_id',q['id'])==0)))
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
it.record('V3-R04','双实例恢复后重新校验资格，批注入班与判分正常保存',lambda:(it.call('/api/annotations',s,body=annotation),it.call('/api/classes/join',s,body={'code':room['joinCode']}),it.call('/api/practices/'+q['id']+'/attempts',s,body=answers)))
it.call('/api/teacher/courses/'+c['id']+'/archive',t)
print('V3 provider fault and recovery checks complete')
