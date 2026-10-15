"""Actual multipart boundary checks on a new isolated teaching task."""
from v6_review import call,login,ROOT,PRIVATE,BASE
import datetime as dt,hashlib,json,secrets,urllib.request,urllib.error
checks=[]
def check(name,ok=True):assert ok,name;checks.append({'name':name,'result':'PASS'})
def upload(tid,token,name,data,expected=200):
 boundary='hanwen-'+secrets.token_hex(12);body=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="'+name+'"\r\nContent-Type: application/octet-stream\r\n\r\n').encode()+data+('\r\n--'+boundary+'--\r\n').encode()
 req=urllib.request.Request(BASE+'/api/assignments/'+tid+'/attachments',headers={'Authorization':'Bearer '+token,'Origin':'http://127.0.0.1:5173','Content-Type':'multipart/form-data; boundary='+boundary},data=body)
 try:
  with urllib.request.urlopen(req,timeout=90) as r:status,raw=r.status,r.read()
 except urllib.error.HTTPError as e:status,raw=e.code,e.read()
 assert status==expected,(status,expected,raw[:100]);return json.loads(raw).get('data')
def file(fid,token,expected=200):
 req=urllib.request.Request(BASE+'/api/submissions/attachments/'+fid+'/file',headers={'Authorization':'Bearer '+token})
 try:
  with urllib.request.urlopen(req,timeout=60) as r:status,data=r.status,r.read()
 except urllib.error.HTTPError as e:status,data=e.code,e.read()
 assert status==expected,(status,expected);return data
if __name__=='__main__':
 f=json.loads((PRIVATE/'v7-stability-fixtures.json').read_text());demo=json.loads((PRIVATE/'demo-accounts.json').read_text());teacher=login(demo[0]);sa=login(f['students'][0]);sb=login(f['students'][1]);tid=call('/api/teacher/assignments',teacher,{'courseId':f['courseId'],'title':'V7附件边界验收','description':'隔离测试','maxScore':'100','rubric':[]})['id'];call('/api/teacher/assignments/'+tid+'/publish',teacher,method='POST')
 upload(tid,sa,'empty.txt',b'',400);check('空文件拒绝');upload(tid,sa,'fake.png',b'not-a-real-image',400);check('内容不匹配扩展名拒绝');upload(tid,sa,'large.txt',b'x'*(20*1024**2+1),413);check('20MB加1字节拒绝413')
 data=b'x'*(20*1024**2);saved=upload(tid,sa,'limit.txt',data);check('20MB整界文件允许并保存正确SHA256',saved['bytes']==len(data) and saved['sha256']==hashlib.sha256(data).hexdigest());check('20MB实际下载逐字节相同',file(saved['id'],sa)==data);file(saved['id'],sb,403);check('其他学生不能下载验收附件')
 for i in range(4):upload(tid,sa,'note'+str(i)+'.txt',b'v7 boundary check')
 upload(tid,sa,'sixth.txt',b'not saved',400);check('每份作业最多5个附件，第6个拒绝',len(call('/api/assignments/'+tid+'/attachments',sa))==5)
 sub=call('/api/assignments/'+tid+'/submissions',sa,{'answerText':'附件边界验收'});check('真实教师可下载已交答卷附件',file(saved['id'],teacher)==data);call('/api/submissions/'+sub['id']+'/grade',teacher,{'score':'80','comment':'验收附件','expectedVersion':sub['version'],'rubricScores':[]},method='PUT');upload(tid,sa,'graded.txt',b'cannot edit',409);check('评分后附件只读')
 (ROOT/'evidence/v7-attachment.json').write_text(json.dumps({'at':dt.datetime.now().astimezone().isoformat(),'result':'PASS','checks':checks,'scope':'Real local gateway multipart uploads; 20MiB exact, 20MiB+1, MIME content, count, read permissions and graded read-only. Files remain in isolated validation task.'},ensure_ascii=False,indent=2),encoding='utf8');print('V7 attachment boundary PASS:',len(checks))
