"""Stage and activate local project JARs, with verified previous-slot recovery. Windows only."""
import argparse,datetime as dt,hashlib,json,os,re,signal,subprocess,time,uuid,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
MODULES={'user-service','course-service','learning-service','assignment-service','gateway'}
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def save(path,data):
 tmp=path.with_suffix('.tmp');tmp.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8');tmp.replace(path)
def schema():
 return {p.name:digest(p) for p in sorted((ROOT/'database/migrations').glob('*.sql'))}
def validate(slot):
 path=slot.resolve();assert path.parent==(ROOT/'.local/releases').resolve(),'Release outside project slots'
 info=json.loads((path/'manifest.json').read_text());assert info['module'] in MODULES
 jar=path/'application.jar';assert jar.is_file() and not jar.is_symlink() and digest(jar)==info['sha256'],'JAR changed after staging'
 with zipfile.ZipFile(jar) as z:assert z.testzip() is None and 'META-INF/MANIFEST.MF' in z.namelist(),'Invalid JAR'
 assert info['schema']==schema(),'Schema migration set differs; do not automatically roll back schema'
 return info,jar
def healthy(records,seconds):
 deadline=time.monotonic()+seconds
 while time.monotonic()<deadline:
  good=True
  for record in records:
   try:
    with urllib.request.urlopen('http://127.0.0.1:'+str(record['port'])+'/actuator/health',timeout=1) as res:good=good and json.load(res)['status']=='UP'
   except Exception:good=False
  if good:return True
  time.sleep(.5)
 return False
def stop(record):
 result=subprocess.run(['powershell','-NoProfile','-Command',f"(Get-CimInstance Win32_Process -Filter 'ProcessId={int(record['pid'])}').CommandLine"],capture_output=True,text=True,check=True)
 command=result.stdout.strip()
 if not command:return
 assert str(ROOT) in command and record['module']+'-1.0.0.jar' in command and '--server.port='+str(record['port']) in command,'Refuse unrelated process'
 os.kill(record['pid'],signal.SIGTERM)
def launch(records,env):
 for record in records:
  jar=ROOT/('backend/'+record['module']+'/target/'+record['module']+'-1.0.0.jar')
  with (ROOT/'.local'/('release-'+record['module']+'-'+str(record['port'])+'.log')).open('ab') as stream:
   proc=subprocess.Popen([env['JAVA_HOME']+'/bin/java.exe','-Xms64m','-Xmx256m','-jar',str(jar),'--server.port='+str(record['port'])],env=env,stdout=stream,stderr=stream,creationflags=subprocess.CREATE_NO_WINDOW)
  record.update(pid=proc.pid,started=dt.datetime.now().astimezone().isoformat(),supervisedSession=False)
def stage(module):
 jar=ROOT/('backend/'+module+'/target/'+module+'-1.0.0.jar');assert jar.is_file() and not jar.is_symlink()
 with zipfile.ZipFile(jar) as z:assert z.testzip() is None
 slot=ROOT/'.local/releases'/(dt.datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+module+'-'+uuid.uuid4().hex[:6]);slot.mkdir(parents=True)
 (slot/'application.jar').write_bytes(jar.read_bytes());save(slot/'manifest.json',{'module':module,'sha256':digest(jar),'schema':schema(),'at':dt.datetime.now().astimezone().isoformat()});return slot
def activate(slot,deadline):
 info,source=validate(slot);private=ROOT/'.local';records=json.loads((private/'processes.json').read_text(encoding='utf-8-sig'));selected=[x for x in records if x['module']==info['module']];assert selected,'Module not running'
 assert healthy(selected,5),'Current service must be healthy before replacement'
 previous=stage(info['module']);_,old=validate(previous)
 cfg=json.loads((private/'runtime.json').read_text(encoding='utf-8-sig'));env=dict(os.environ);env.update({k:str(v) for k,v in cfg.items()});env['JAVA_HOME']=os.environ.get('JAVA_HOME','C:/Program Files/Eclipse Adoptium/jdk-25.0.4.1-hotspot')
 target=ROOT/('backend/'+info['module']+'/target/'+info['module']+'-1.0.0.jar');result='FAILED';started=dt.datetime.now().astimezone().isoformat()
 try:
  for record in selected:stop(record)
  time.sleep(1);target.write_bytes(source.read_bytes());launch(selected,env);save(private/'processes.json',records)
  if healthy(selected,deadline):result='ACTIVATED'
  else:
   for record in selected:stop(record)
   time.sleep(1);target.write_bytes(old.read_bytes());launch(selected,env);save(private/'processes.json',records)
   assert healthy(selected,90),'Recovery health check failed';result='ROLLED_BACK'
 except Exception:
  # Restore the original artifact on any activation error; refuse unrelated processes.
  for record in selected:stop(record)
  time.sleep(1);target.write_bytes(old.read_bytes());launch(selected,env);save(private/'processes.json',records)
  if healthy(selected,90):result='ROLLED_BACK_AFTER_ERROR'
  else:raise RuntimeError('Recovery health failed; inspect private logs') from None
 event={'at':started,'finishedAt':dt.datetime.now().astimezone().isoformat(),'module':info['module'],'result':result,'candidateSha256':info['sha256'],'previousSha256':digest(old),'candidateSlot':slot.name,'previousSlot':previous.name,'healthDeadlineSeconds':deadline,'scope':'Local application artifact only; schema and data are never rolled back.'}
 with (private/'releases/history.jsonl').open('a',encoding='utf8') as stream:stream.write(json.dumps(event)+'\n')
 return event
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['stage','activate']);p.add_argument('--module',choices=sorted(MODULES));p.add_argument('--slot');p.add_argument('--health-seconds',type=int,default=60);args=p.parse_args()
 assert os.name=='nt','This launcher is Windows only';assert 1<=args.health_seconds<=180
 private=ROOT/'.local';private.mkdir(exist_ok=True);lock=private/'release.lock'
 try:handle=lock.open('x')
 except FileExistsError:raise SystemExit('A release is already running; inspect the lock after an interrupted run.')
 try:
  with handle:handle.write(str(os.getpid()))
  if args.action=='stage':assert args.module;slot=stage(args.module);print(json.dumps({'slot':slot.name,'module':args.module,'sha256':digest(slot/'application.jar')}))
  else:
   assert args.slot and re.fullmatch(r'[a-zA-Z0-9_-]+',args.slot),'Invalid slot name';print(json.dumps(activate(private/'releases'/args.slot,args.health_seconds)))
 finally:lock.unlink(missing_ok=True)
if __name__=='__main__':main()
