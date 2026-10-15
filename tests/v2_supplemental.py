import sys,json,secrets,subprocess,hashlib
from pathlib import Path
import integration as it
it.RESULTS=it.ROOT/'evidence/v2-supplemental-tests.json'
accounts=json.loads((it.PRIVATE/'demo-accounts.json').read_text(encoding='utf8'))
admin=it.login('admin',it.cfg['HW_ADMIN_PASSWORD'])['accessToken'];teacher=it.login(accounts[0]['username'],accounts[0]['password'])['accessToken'];student=it.login(accounts[2]['username'],accounts[2]['password'])['accessToken']
name='v2teach'+secrets.token_hex(4);pwd=secrets.token_urlsafe(20)+'Aa9!';u=it.call('/api/admin/users',admin,body={'username':name,'password':pwd,'displayName':'V2独立编选权限验证','role':'TEACHER'});ut=it.login(name,pwd)['accessToken']
w=it.call('/api/teacher/literature',ut,body={'title':'V2独立编选角色验证','author':'课程测试','dynasty':'现代','genre':'文','content':'只有自有诗文编选，没有课程记录的教师角色验证。','readingGuide':None,'sourceUrl':None,'sourceNote':'权限测试原创。'})
it.record('V2-S01','只有诗文编选记录的教师也不能跨角色转换',lambda:it.call('/api/admin/users/'+u['id']+'/access',admin,'PUT',{'role':'STUDENT','status':'ENABLED'},409))
it.call('/api/teacher/literature/'+w['id']+'/status',ut,'PUT',{'status':'ARCHIVED','expectedVersion':0})
it.record('V2-S02','收藏内部元信息接口无法从Gateway或无内部密钥直连访问',lambda:(it.request('/internal/literature/briefs?ids=1',student,expected=404),it.request('/internal/literature/briefs?ids=1',student,expected=403,base='http://127.0.0.1:8102')))
it.record('V2-S03','课程双实例提供者负载均衡仍返回两个真实端口',lambda:it.check({it.get('/api/teacher/diagnostics/course-instance',teacher)['port'] for _ in range(8)}=={8102,8105}))
mysql=Path('C:/Program Files/MySQL/MySQL Server 9.7/bin/mysql.exe');defaults='--defaults-file='+str(it.PRIVATE/'backup-client.ini')
def query(sql):
 p=subprocess.run([str(mysql),defaults,'--batch','--skip-column-names','--execute='+sql],capture_output=True)
 if p.returncode:raise RuntimeError('Database verification failed')
 return p.stdout
def migration_idempotence():
 sql="SELECT TABLE_SCHEMA,TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA IN ('hw_user','hw_course','hw_learning','hw_assignment') ORDER BY TABLE_SCHEMA,TABLE_NAME;"
 schema=query(sql).decode().strip().splitlines();it.check(len(schema)==14)
 before={row:hashlib.sha256(query('SELECT * FROM `'+row.split('\t')[0]+'`.`'+row.split('\t')[1]+'` ORDER BY id;')).hexdigest() for row in schema}
 p=subprocess.run([str(mysql),defaults,'--batch'],input=(it.ROOT/'database/migrations/V2__learning-platform.sql').read_bytes(),capture_output=True)
 if p.returncode:raise RuntimeError('Migration replay failed')
 after={row:hashlib.sha256(query('SELECT * FROM `'+row.split('\t')[0]+'`.`'+row.split('\t')[1]+'` ORDER BY id;')).hexdigest() for row in schema}
 it.check(before==after)
it.record('V2-S04','重复增量迁移不新增重复古籍，十四表全部行数据不变',migration_idempotence)
print('Four supplemental V2 checks PASS')
