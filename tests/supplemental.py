"""Additional live concurrency, DTO ownership and transaction checks."""
import integration as it
import concurrent.futures
import json
from pathlib import Path

it.RESULTS = it.ROOT / 'evidence/supplemental-tests.json'
accounts=json.loads((it.PRIVATE/'demo-accounts.json').read_text(encoding='utf-8'))
teacher=it.login(accounts[0]['username'],accounts[0]['password'])['accessToken']
student=it.login(accounts[3]['username'],accounts[3]['password'])['accessToken']
c,h=it.course(teacher,'并发校验课程')
it.call('/api/teacher/courses/'+c['id']+'/publish',teacher)
def fresh_join():
 with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
  records=list(pool.map(lambda _:it.call('/api/enrollments',student,body={'courseId':c['id']}),range(6)))
 it.check(len({r['id'] for r in records})==1)
 it.check(it.get('/api/enrollments/me?courseId='+c['id'],student)['total']==1)
 return records[0]
it.record('ST01','六次并发首次加入唯一且全部返回同一记录',fresh_join)
e=it.get('/api/enrollments/me?courseId='+c['id'],student)['items'][0]
def fresh_complete():
 with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
  records=list(pool.map(lambda _:it.call('/api/enrollments/'+e['id']+'/chapters/'+h['id']+'/complete',student,'PUT'),range(6)))
 it.check(len({r['completedAt'] for r in records})==1)
 it.check(it.get('/api/enrollments/'+e['id']+'/progress',student)['completedCount']==1)
it.record('ST02','六次并发首次完成唯一且首次时间一致',fresh_complete)
it.record('ST03','请求中伪造归属字段被DTO拒绝',lambda:it.call('/api/teacher/courses',teacher,body={'name':'伪造归属','summary':None,'category':'古代文学','teacherId':'1'},expected=400))
task=it.call('/api/teacher/assignments',teacher,body={'courseId':c['id'],'title':'事务校验','description':'验证非法分数不留下评分或状态变化。','maxScore':'100','deadlineAt':None})
it.call('/api/teacher/assignments/'+task['id']+'/publish',teacher)
s=it.call('/api/assignments/'+task['id']+'/submissions',student,body={'answerText':'用于事务校验的作答。'})
before=it.get('/api/submissions/'+s['id'],student)
def rollback():
 it.call('/api/submissions/'+s['id']+'/grade',teacher,'PUT',{'score':'100.01','comment':'不可保存','expectedVersion':0},400)
 it.check(it.get('/api/submissions/'+s['id'],student)==before)
it.record('ST04','非法评分后答案、版本、状态及评分保持原值',rollback)
it.call('/api/submissions/'+s['id']+'/grade',teacher,'PUT',{'score':'80','comment':'待清除的评语','expectedVersion':0})
def clear_comment():
 it.call('/api/submissions/'+s['id']+'/grade',teacher,'PUT',{'score':'80','comment':None,'expectedVersion':1})
 it.check(it.get('/api/submissions/'+s['id'],student)['grade']['comment'] is None)
it.record('ST05','更正评分可清除原评语',clear_comment)
print('Supplemental checks complete.')
