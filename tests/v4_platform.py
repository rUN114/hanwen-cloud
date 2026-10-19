"""V4 live checks in the dedicated local course environment. No raw credentials in evidence."""
import base64,concurrent.futures,datetime,hashlib,json,secrets,subprocess,time,urllib.request,urllib.error
from http.cookies import SimpleCookie
from pathlib import Path
from cryptography.hazmat.primitives import serialization,hashes
from cryptography.hazmat.primitives.asymmetric import padding

from fixture_guard import require_fixture_environment

require_fixture_environment(Path(__file__).resolve().parents[1])
ROOT=Path(__file__).resolve().parents[1];PRIVATE=ROOT/'.local';BASE='http://127.0.0.1:8080'
cfg=json.loads((PRIVATE/'runtime.json').read_text(encoding='utf-8-sig'));accounts=json.loads((PRIVATE/'demo-accounts.json').read_text())
results=[];stamp=secrets.token_hex(4);fixtures={}
def api(path,token=None,body=None,method=None,cookie=None,expected=200,origin='http://127.0.0.1:5173'):
 h={'Accept':'application/json'}
 if token:h['Authorization']='Bearer '+token
 if cookie:h['Cookie']='hw_refresh='+cookie
 if origin is not None:h['Origin']=origin
 if body is not None:h['Content-Type']='application/json'
 req=urllib.request.Request(BASE+path,headers=h,data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),method=method or ('POST' if body is not None else 'GET'))
 try:
  with urllib.request.urlopen(req,timeout=40) as r:status,raw,headers=r.status,r.read(),r.headers
 except urllib.error.HTTPError as e:status,raw,headers=e.code,e.read(),e.headers
 data=json.loads(raw) if raw else {};assert status==expected,(path,status,expected,data.get('code'))
 return data.get('data'),headers

def login(name,password):return api('/api/auth/login',body={'username':name,'password':password})
def cookie(headers):
 c=SimpleCookie();c.load(headers['Set-Cookie']);return c['hw_refresh'].value

def sql(query):
 out=subprocess.run(['C:/Program Files/MySQL/MySQL Server 9.7/bin/mysql.exe','--defaults-file='+str(PRIVATE/'backup-client.ini'),'--batch','--skip-column-names','--execute='+query],capture_output=True,check=True)
 return out.stdout.decode().strip()

def check(n,label,fn):
 try:fn();results.append({'case':'V4-'+str(n).zfill(2),'detail':label,'result':'PASS'});print('PASS V4-'+str(n).zfill(2))
 except Exception:
  results.append({'case':'V4-'+str(n).zfill(2),'detail':label,'result':'FAIL'});write();raise

def write():
 (ROOT/'evidence/v4-api-tests.json').write_text(json.dumps({'at':datetime.datetime.now().astimezone().isoformat(),'cases':results,'fixtures':fixtures},ensure_ascii=False,indent=2),encoding='utf8')

def assert_true(value):assert value
admin,ah=login('admin',cfg['HW_ADMIN_PASSWORD']);a=admin['accessToken']
teacher,th=login(accounts[0]['username'],accounts[0]['password']);t=teacher['accessToken']
student,sh=login(accounts[2]['username'],accounts[2]['password']);s=student['accessToken'];original=cookie(sh)
check(1,'登录返回HttpOnly和SameSite严格Cookie，JSON不含原始续期令牌',lambda:(assert_true('HttpOnly' in sh['Set-Cookie'] and 'SameSite=Strict' in sh['Set-Cookie'] and 'Path=/api/auth' in sh['Set-Cookie']),assert_true(original not in json.dumps(student))))
check(2,'刷新接口拒绝无来源、其他来源和无Cookie',lambda:(api('/api/auth/refresh',method='POST',cookie=original,origin=None,expected=403),api('/api/auth/refresh',method='POST',cookie=original,origin='https://other.invalid',expected=403),api('/api/auth/refresh',method='POST',expected=401)))
renewed,rh=api('/api/auth/refresh',method='POST',cookie=original);current=cookie(rh)
check(3,'续期轮换令牌并保留八小时绝对会话上限',lambda:(assert_true(current!=original and renewed['accessToken']!=s),assert_true(renewed['sessionExpiresAt']==student['sessionExpiresAt']),api('/api/users/me',renewed['accessToken'])))
check(4,'已轮换的旧Cookie再次使用返回401',lambda:api('/api/auth/refresh',method='POST',cookie=original,expected=401))
check(5,'数据库只保存SHA256摘要，不保存Cookie明文',lambda:assert_true(sql("SELECT COUNT(*) FROM hw_user.auth_refresh_session WHERE token_hash='"+hashlib.sha256(current.encode()).hexdigest()+"';")=='1'))
newnames=[{'username':'v4_'+stamp+'_s1','displayName':'V4验证同窗甲'},{'username':'v4_'+stamp+'_s2','displayName':'V4验证同窗乙'}]
created,_=api('/api/admin/users/import',a,{'role':'STUDENT','rows':newnames});new=created['items'];fixtures['newStudentIds']=[u['id'] for u in new]
(PRIVATE/'v4-test-accounts.json').write_text(json.dumps(new,ensure_ascii=False),encoding='utf8')
check(6,'整批开户生成各自独立初始密码，可实际登录',lambda:(assert_true(created['created']==2 and new[0]['initialPassword']!=new[1]['initialPassword'] and all(x['initialPassword'].startswith('Hw') for x in new)),login(new[0]['username'],new[0]['initialPassword']),login(new[1]['username'],new[1]['initialPassword'])))
check(7,'学生及教师不能批量开户或读取事务记录',lambda:(api('/api/admin/users/import',s,{'role':'STUDENT','rows':newnames},expected=403),api('/api/admin/users/import',t,{'role':'STUDENT','rows':newnames},expected=403),api('/api/admin/audit',s,expected=403)))
badname='v4_'+stamp+'_not_created'
check(8,'包含已存在账号时整批拒绝，不写入其他新账号',lambda:(api('/api/admin/users/import',a,{'role':'STUDENT','rows':[{'username':badname,'displayName':'不应开户'},newnames[0]]},expected=409),assert_true(sql("SELECT COUNT(*) FROM hw_user.sys_user WHERE username='"+badname+"';")=='0')))
check(9,'大小写重复账号、空名单、管理员角色、超过50人均拒绝',lambda:(api('/api/admin/users/import',a,{'role':'STUDENT','rows':[newnames[0],{'username':newnames[0]['username'].upper(),'displayName':'重复'}]},expected=400),api('/api/admin/users/import',a,{'role':'STUDENT','rows':[]},expected=400),api('/api/admin/users/import',a,{'role':'ADMIN','rows':newnames},expected=400),api('/api/admin/users/import',a,{'role':'STUDENT','rows':[{'username':'s'+str(i)+'_v4','displayName':'验证'} for i in range(51)]},expected=400)))
u=new[0];session,headers=login(u['username'],u['initialPassword']);oldcookie=cookie(headers);token=session['accessToken']
nextpassword=secrets.token_urlsafe(18)+'Aa9!'
api('/api/users/me/password',token,{'currentPassword':u['initialPassword'],'newPassword':nextpassword},method='PUT')
check(10,'本人改密同时使旧JWT和续期Cookie失效',lambda:(api('/api/users/me',token,expected=401),api('/api/auth/refresh',method='POST',cookie=oldcookie,expected=401)))
session,headers=login(u['username'],nextpassword);oldcookie=cookie(headers);token=session['accessToken']
resetpassword=secrets.token_urlsafe(18)+'Aa9!';api('/api/admin/users/'+u['id']+'/password-reset',a,{'newPassword':resetpassword})
check(11,'管理员重置密码撤销旧会话且允许使用新密码',lambda:(api('/api/auth/refresh',method='POST',cookie=oldcookie,expected=401),api('/api/users/me',token,expected=401),login(u['username'],resetpassword)))
session,headers=login(u['username'],resetpassword);oldcookie=cookie(headers);token=session['accessToken']
api('/api/admin/users/'+u['id']+'/access',a,{'role':'STUDENT','status':'DISABLED'},method='PUT')
check(12,'禁用账号不能续期或再次登录',lambda:(api('/api/auth/refresh',method='POST',cookie=oldcookie,expected=401),api('/api/auth/login',body={'username':u['username'],'password':resetpassword},expected=401)))
api('/api/admin/users/'+u['id']+'/access',a,{'role':'STUDENT','status':'ENABLED'},method='PUT')
session,headers=login(u['username'],resetpassword);oldcookie=cookie(headers);token=session['accessToken']
_,logoutheaders=api('/api/auth/logout',token,method='POST')
check(13,'退出清Cookie并撤销原会话',lambda:(assert_true('Max-Age=0' in logoutheaders['Set-Cookie']),api('/api/auth/refresh',method='POST',cookie=oldcookie,expected=401)))
session,headers=login(u['username'],resetpassword);oldcookie=cookie(headers)
sql("UPDATE hw_user.auth_refresh_session SET expires_at=DATE_SUB(NOW(3),INTERVAL 1 HOUR) WHERE token_hash='"+hashlib.sha256(oldcookie.encode()).hexdigest()+"';")
check(14,'超过绝对会话有效期返回401',lambda:api('/api/auth/refresh',method='POST',cookie=oldcookie,expected=401))
session,headers=login(u['username'],resetpassword);rawcookie=cookie(headers)
def expired_jwt():
 now=int(time.time());actor=json.loads(base64.urlsafe_b64decode(session['accessToken'].split('.')[1]+'=='))
 actor.update({'iat':now-3600,'exp':now-120});enc=lambda x:base64.urlsafe_b64encode(json.dumps(x,separators=(',',':')).encode()).decode().rstrip('=')
 data=(enc({'alg':'RS256','typ':'JWT'})+'.'+enc(actor)).encode();key=serialization.load_pem_private_key(Path(cfg['HW_PRIVATE_KEY_FILE']).read_bytes(),None)
 jwt=data.decode()+'.'+base64.urlsafe_b64encode(key.sign(data,padding.PKCS1v15(),hashes.SHA256())).decode().rstrip('=')
 api('/api/users/me',jwt,expected=401);fresh,_=api('/api/auth/refresh',method='POST',cookie=rawcookie);api('/api/users/me',fresh['accessToken'])
check(15,'真实过期JWT被拒绝，有效Cookie能获得新JWT继续使用',expired_jwt)
session,headers=login(u['username'],resetpassword);racecookie=cookie(headers)
def concurrent_refresh():
 def send(_):
  try:api('/api/auth/refresh',method='POST',cookie=racecookie);return 200
  except AssertionError as e:return e.args[0][1]
 with concurrent.futures.ThreadPoolExecutor(2) as pool:codes=list(pool.map(send,range(2)))
 assert sorted(codes)==[200,401]
check(16,'并发使用同一续期Cookie只有一次成功',concurrent_refresh)
copy,_=api('/api/teacher/courses/29/copy',t,{'name':'V4复制验证 · '+stamp});cid=copy['course']['id'];fixtures['copiedCourseId']=cid
check(17,'复制产生新草稿课程，完整复制章节并保留原始课程',lambda:(assert_true(copy['course']['status']=='DRAFT' and copy['chapters']==3),assert_true(api('/api/courses/29',t)[0]['status']=='PUBLISHED'),assert_true(len(api('/api/courses/'+cid+'/chapters',t)[0])==3)))
q,_=api('/api/courses/'+cid+'/quizzes?size=100',t);q=q['items']
check(18,'复制练习为草稿且章节映射到新课程',lambda:(assert_true(len(q)==copy['quizzes'] and all(x['status']=='DRAFT' for x in q)),assert_true(sql('SELECT COUNT(*) FROM hw_course.chapter_quiz q JOIN hw_course.course_chapter h ON q.chapter_id=h.id WHERE q.course_id='+cid+' AND h.course_id!='+cid+';')=='0')))
check(19,'新课程没有继承学生、资源、作业或答卷',lambda:(assert_true(sql('SELECT COUNT(*) FROM hw_learning.course_enrollment WHERE course_id='+cid+';')=='0'),assert_true(sql('SELECT COUNT(*) FROM hw_course.course_resource WHERE course_id='+cid+';')=='0'),assert_true(sql('SELECT COUNT(*) FROM hw_assignment.assignment_task WHERE course_id='+cid+';')=='0'),api('/api/courses/'+cid,s,expected=403)))
other,_=login(accounts[1]['username'],accounts[1]['password'])
check(20,'不能复制其他教师的课程，学生不能复制课程',lambda:(api('/api/teacher/courses/29/copy',other['accessToken'],{'name':'越权'},expected=403),api('/api/teacher/courses/29/copy',s,{'name':'越权'},expected=403),api('/api/teacher/courses/29/copy',t,{'name':''},expected=400)))
api('/api/teacher/courses/'+cid+'/publish',t,method='POST');api('/api/teacher/courses/'+cid+'/archive',t,method='POST');again,_=api('/api/teacher/courses/'+cid+'/copy',t,{'name':'V4归档复制验证 · '+stamp})
check(21,'已归档课程仍可复制为新草稿，旧课程保持归档',lambda:(assert_true(again['course']['status']=='DRAFT'),assert_true(api('/api/courses/'+cid,t)[0]['status']=='ARCHIVED')))
audit,_=api('/api/admin/audit?size=100',a);events=audit['items'];serialized=json.dumps(events)
check(22,'事务记录包含开户、改密、重置、权限、登录退出且不含原始密码',lambda:(assert_true({'IMPORT_USERS','PASSWORD_CHANGE','PASSWORD_RESET','ACCESS_CHANGE','LOGIN','LOGOUT'}<={x['action'] for x in events}),assert_true(all(x['traceId'] and x['createdAt'] for x in events)),assert_true(not any(v in serialized for v in [u['initialPassword'],nextpassword,resetpassword,rawcookie]))))
check(23,'事务分页与非法分页参数按规则处理',lambda:(assert_true(len(api('/api/admin/audit?size=2',a)[0]['items'])==2),api('/api/admin/audit?size=101',a,expected=400)))
check(24,'练习复制保留题目和答案但学生不能读取草稿',lambda:(assert_true(sql('SELECT COUNT(*) FROM hw_course.quiz_question WHERE quiz_id IN (SELECT id FROM hw_course.chapter_quiz WHERE course_id='+cid+');')==str(copy['questions'])),api('/api/courses/'+again['course']['id']+'/quizzes',s,expected=403)))
def bounded_access():
 session,headers=login(u['username'],resetpassword);raw=cookie(headers)
 sql("UPDATE hw_user.auth_refresh_session SET expires_at=DATE_ADD(NOW(3),INTERVAL 45 SECOND) WHERE token_hash='"+hashlib.sha256(raw.encode()).hexdigest()+"';")
 short,_=api('/api/auth/refresh',method='POST',cookie=raw)
 claims=json.loads(base64.urlsafe_b64decode(short['accessToken'].split('.')[1]+'=='))
 deadline=datetime.datetime.fromisoformat(short['sessionExpiresAt']).timestamp()
 assert claims['exp']<=deadline and datetime.datetime.fromisoformat(short['expiresAt']).timestamp()<=deadline
check(25,'临近八小时上限续期时，新JWT到期时间也不越过会话上限',bounded_access)
# Only newly created automation accounts are disabled; original demos are preserved.
for row in new:api('/api/admin/users/'+row['id']+'/access',a,{'role':'STUDENT','status':'DISABLED'},method='PUT')
write();print('V4 checks complete:',len(results),'PASS')
