"""V3 tests for missing membership, deadlines, scoring and named grading screens."""
import integration as it
import json,secrets,time,datetime as dt,urllib.request,hashlib
it.RESULTS=it.ROOT/'evidence/v3-supplemental-tests.json'
accounts=json.loads((it.PRIVATE/'demo-accounts.json').read_text(encoding='utf8'))
sessions=[it.login(a['username'],a['password']) for a in accounts]
t,t2,s,s2=[x['accessToken'] for x in sessions]
admin=it.login('admin',it.cfg['HW_ADMIN_PASSWORD'])['accessToken']
c,h=it.course(t,'V3补充边界验证 · '+secrets.token_hex(3));cid=c['id']
it.call('/api/teacher/courses/'+cid+'/publish',t)
it.call('/api/enrollments',s,body={'courseId':cid})
it.record('V3-S01','未选学生不能看资源或练习列表，课程公开简介可访问',lambda:(it.request('/api/courses/'+cid+'/resources',s2,expected=403),it.request('/api/courses/'+cid+'/quizzes',s2,expected=403),it.get('/api/courses/'+cid,s2)))
past=(dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))+dt.timedelta(seconds=8)).isoformat()
a=it.call('/api/teacher/assignments',t,body={'courseId':cid,'title':'截止草稿验证','description':'截止后拒绝写入','maxScore':'20','deadlineAt':past})
it.call('/api/teacher/assignments/'+a['id']+'/publish',t)
time.sleep(9)
it.record('V3-S02','已截止作业不接受草稿或提交，服务端未保存草稿',lambda:(it.call('/api/assignments/'+a['id']+'/draft',s,'PUT',{'answerText':'截止后','expectedVersion':-1},409),it.call('/api/assignments/'+a['id']+'/submissions',s,body={'answerText':'截止后'},expected=409),it.check(it.get('/api/assignments/'+a['id']+'/draft',s) is None)))
quiz=it.call('/api/teacher/courses/'+cid+'/quizzes',t,body={'chapterId':h['id'],'title':'错题解析验证','instructions':'用于检验真实0分结果。'})
q=it.call('/api/teacher/quizzes/'+quiz['id']+'/questions',t,body={'prompt':'月亮意象如何解释？','optionA':'结合语境','optionB':'只用固定符号','optionC':'只看字体','optionD':'只数文字','correctOption':'A','explanation':'不能脱离具体文本。','sortNo':1,'expectedVersion':0})
it.call('/api/teacher/quizzes/'+quiz['id']+'/status',t,'PUT',{'status':'PUBLISHED','expectedVersion':1})
r=it.call('/api/practices/'+quiz['id']+'/attempts',s,body={'answers':[{'questionId':q['questions'][0]['id'],'choice':'B'}]})
it.record('V3-S03','错误选项产生真实零分、错误标记、原选择与正确解析',lambda:it.check(r['score']==0 and r['total']==1 and r['items'][0]['correct'] is False and r['items'][0]['selectedOption']=='B' and r['items'][0]['correctOption']=='A'))
it.record('V3-S04','内部判分与学生目录接口即使带公开JWT直连仍403',lambda:(it.request('/internal/quizzes/'+quiz['id']+'/grade',s,'POST',{'answers':[]},403,base='http://127.0.0.1:8102'),it.request('/internal/users/students-by-username',t,'POST',[accounts[2]['username']],403,base='http://127.0.0.1:8101')))
it.record('V3-S05','课程提供者确实命中两个服务实例',lambda:it.check({it.get('/api/teacher/diagnostics/course-instance',t)['port'] for _ in range(8)}=={8102,8105}))
sample=json.loads((it.ROOT/'evidence/v3-teaching-sample.json').read_text(encoding='utf8'))
rows=it.get('/api/assignments/'+sample['assignments'][0]+'/submissions',t)['items']
student_row=next(x for x in rows if x['studentId']==sessions[3]['user']['id'])
it.record('V3-S06','待批名册和答卷显示真实学生姓名，不泄漏密码或私人批注',lambda:(it.check(student_row['studentName']==sessions[3]['user']['displayName']),it.check(it.get('/api/submissions/'+student_row['id'],t)['studentName']==sessions[3]['user']['displayName']),it.check('password' not in student_row and 'annotations' not in student_row)))
draftcourse,dh=it.course(t,'V3草稿引用约束 · '+secrets.token_hex(3))
dq=it.call('/api/teacher/courses/'+draftcourse['id']+'/quizzes',t,body={'chapterId':dh['id'],'title':'章节引用验证'})
it.record('V3-S07','草稿章节被练习引用后不能删除，原练习仍可读',lambda:(it.call('/api/teacher/courses/'+draftcourse['id']+'/chapters/'+dh['id'],t,'DELETE',expected=409),it.get('/api/quizzes/'+dq['id'],t)))
def shared_files():
 resource=next(x for x in it.get('/api/courses/'+sample['courseId']+'/resources',s)['items'] if x['mimeType']=='audio/wav')
 for port in [8102,8105]:
  req=urllib.request.Request(f'http://127.0.0.1:{port}/api/resources/'+resource['id']+'/file',headers={'Authorization':'Bearer '+s})
  with urllib.request.urlopen(req,timeout=12) as response: it.check(hashlib.sha256(response.read()).hexdigest()==resource['sha256'])
it.record('V3-S08','共享存储的两个真实实例返回同一鉴权音频且内容SHA256一致',shared_files)
it.call('/api/teacher/courses/'+cid+'/archive',t)
print('V3 supplemental checks complete:',len(it.results))
