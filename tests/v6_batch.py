"""Real all-or-nothing task transitions in dedicated V6 courses."""
from v6_review import call,login,ROOT,PRIVATE
import datetime as dt,json
f=json.loads((PRIVATE/'v6-fixtures.json').read_text());t=login(f['teacher']);other=login(f['otherTeacher']);sa=login(f['students'][0]);cid=f['courseId'];checks=[]
def check(name,ok=True):
 assert ok,name;checks.append({'name':name,'result':'PASS'});(ROOT/'evidence/v6-batch-api.json').write_text(json.dumps({'checks':checks,'scope':'Fresh isolated task batches; invalid mixed batch state equality checked.'},ensure_ascii=False,indent=2),encoding='utf8')
future=(dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))+dt.timedelta(days=7)).isoformat();past=(dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))-dt.timedelta(days=1)).isoformat()
def create(name,course=cid,deadline=future):return call('/api/teacher/assignments',t,{'courseId':course,'title':name,'description':'验证批量状态操作，原教学作业不参与。','maxScore':'100','deadlineAt':deadline,'rubric':[]})
def item(x):return {k:x[k] for k in ['id','status','updatedAt']}
def batch(xs,action='PUBLISH',token=t,expected=200):return call('/api/teacher/assignments/batch',token,{'courseId':cid,'action':action,'items':[item(x) for x in xs]},expected=expected)
a=create('批次验证甲');b=create('批次验证乙');expired=create('已截止草稿验证',deadline=past)
batch([a],token=sa,expected=403);check('学生不能批量改变作业')
batch([a],token=other,expected=403);check('其他教师不能批量改变此课程')
batch([],expected=400);check('空批次拒绝')
batch([a,a],expected=400);check('重复编号拒绝')
batch([a]*51,expected=400);check('超过50份上限拒绝')
call('/api/teacher/assignments/batch',t,{'courseId':cid,'action':'DELETE','items':[item(a)]},expected=400);check('未知动作拒绝')
batch([a,expired],expected=400);check('混入截止草稿时整批拒绝',call('/api/assignments/'+a['id'],t)['status']=='DRAFT')
foreign=call('/api/teacher/courses',t,{'name':'V6批次跨课程验证 '+f['stamp'],'category':'古典文学','summary':'仅用于验证跨课程批次拒绝。'});call('/api/teacher/courses/'+foreign['id']+'/chapters',t,{'title':'批次隔离验证','content':'独立测试章节','sortNo':1});call('/api/teacher/courses/'+foreign['id']+'/publish',t,method='POST');x=create('另册作业',foreign['id']);batch([a,x],expected=403);check('混入其他课程时本批草稿保持',call('/api/assignments/'+a['id'],t)['status']=='DRAFT')
changed=call('/api/teacher/assignments/'+a['id'],t,{'title':'批次验证甲 · 已修改','description':a['description'],'maxScore':'100','deadlineAt':future,'rubric':[]},method='PUT');batch([a,b],expected=409);check('旧更新时间拒绝且另一份保持草稿',call('/api/assignments/'+b['id'],t)['status']=='DRAFT')
updated=[changed,b];published=batch(updated);check('两份新鲜草稿一起发布',published['changed']==2 and all(x['status']=='PUBLISHED' for x in published['items']));batch(updated,expected=409);check('重复旧批次不能重复发布')
p=published['items'];batch([p[0],expired],'CLOSE',expected=409);check('混合状态不能部分关闭',all(call('/api/assignments/'+x['id'],t)['status']=='PUBLISHED' for x in p))
closed=batch(p,'CLOSE');check('两份已发布作业一起关闭',closed['changed']==2 and all(x['status']=='CLOSED' for x in closed['items']));batch(p,'CLOSE',expected=409);check('旧批次关闭请求拒绝')
check('原习作正式成绩保持80分',call('/api/submissions/'+f['submissionId'],sa)['grade']['score']=='80.00')
ui=[create('书院批量 · 甲'),create('书院批量 · 乙')];f['uiBatchTasks']=ui;f['foreignCourseId']=foreign['id'];(PRIVATE/'v6-fixtures.json').write_text(json.dumps(f,ensure_ascii=False,indent=2),encoding='utf8')
print('R3 actual batch checks PASS:',len(checks))
