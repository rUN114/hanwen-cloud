"""Actual revision loop checks; revisions never overwrite formal answers or grades."""
from v6_review import call,login,ROOT,PRIVATE
import datetime as dt,json
f=json.loads((PRIVATE/'v6-fixtures.json').read_text());t=login(f['teacher']);other=login(f['otherTeacher']);sa=login(f['students'][0]);sb=login(f['students'][1]);checks=[];cid=f['courseId']
def check(name,ok=True):
 assert ok,name;checks.append({'name':name,'result':'PASS'});(ROOT/'evidence/v6-revision-api.json').write_text(json.dumps({'checks':checks,'scope':'Isolated submission and revision loop; formal answer and score equality checked.'},ensure_ascii=False,indent=2),encoding='utf8')
future=(dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))+dt.timedelta(days=7)).isoformat();task=call('/api/teacher/assignments',t,{'courseId':cid,'title':'乡思习作 · 修订闭环','description':'结合举头与低头的动作，说明月光如何关联乡思。','maxScore':'100','deadlineAt':future,'rubric':[]});call('/api/teacher/assignments/'+task['id']+'/publish',t,method='POST');original='月光令诗人想到故乡。\n举头与低头形成情感转折。';s=call('/api/assignments/'+task['id']+'/submissions',sa,{'answerText':original});sid=s['id'];path='/api/submissions/'+sid+'/revisions'
call(path,t,{'requestText':'补充原文依据。','expectedSubmissionVersion':s['version']},expected=409);check('未评分答卷不能发起修订')
call('/api/submissions/'+sid+'/grade',t,{'score':'80','comment':'请补充原文依据。','expectedVersion':s['version'],'rubricScores':[]},method='PUT');s=call('/api/submissions/'+sid,t)
call(path,sa,{'requestText':'学生不能发邀请','expectedSubmissionVersion':s['version']},expected=403);check('学生不能邀请修订')
call(path,other,expected=403);check('其他教师不能读修订记录')
call(path,sb,expected=403);check('其他学生不能读修订记录')
call(path,t,{'requestText':'补充原文依据。','expectedSubmissionVersion':0},expected=409);check('旧正式答卷版本不能发邀请')
r=call(path,t,{'requestText':'补充举头望明月与低头思故乡的原文依据，并分析动作。','expectedSubmissionVersion':s['version']});check('教师发起修订保留基准版本',r['status']=='OPEN' and r['baseSubmissionVersion']==s['version'])
call(path,t,{'requestText':'重复','expectedSubmissionVersion':s['version']},expected=409);check('已有未完成修订时不能重复邀请')
rp=path+'/'+r['id'];call(rp+'/submit',sb,{'answerText':'越权','studentNote':'','expectedVersion':0},expected=403);check('其他学生不能提交修订')
call(rp+'/submit',sa,{'answerText':original,'studentNote':'未改正文','expectedVersion':0},expected=400);check('正文未修改时拒绝')
call(rp+'/submit',sa,{'answerText':'修订','studentNote':'','expectedVersion':99},expected=409);check('旧修订版本不能覆盖')
changed='月光令诗人想到故乡。\n“举头望明月”与“低头思故乡”通过视线变化连接月色与乡思。';r=call(rp+'/submit',sa,{'answerText':changed,'studentNote':'补充两句原文并解释视线变化。','expectedVersion':0});check('修订正文独立保存为待阅状态',r['answerText']==changed and r['status']=='SUBMITTED')
call(rp+'/submit',sa,{'answerText':'重复覆盖','expectedVersion':r['version']},expected=409);check('提交后修订稿不可直接覆盖')
call(rp+'/review',other,{'decision':'ACCEPTED','comment':'越权','expectedVersion':r['version']},expected=403);check('非本人教师不能评阅修订')
call(rp+'/review',t,{'decision':'NOPE','comment':'验证','expectedVersion':r['version']},expected=400);check('无效修订结论拒绝')
call(rp+'/review',t,{'decision':'CHANGES','comment':'请进一步说明动作变化如何产生乡思。','expectedVersion':r['version']});h=call(path,sa);check('要求再修改保留旧轮并新开邀请',h['items'][0]['status']=='CHANGES' and h['items'][1]['status']=='OPEN' and h['items'][0]['answerText']==changed)
r2=h['items'][1];nextText=changed+'\n从仰望外界的月亮转向内心对故乡的思念，动作变化推动情感。';r2=call(path+'/'+r2['id']+'/submit',sa,{'answerText':nextText,'studentNote':'说明由外界转向内心的情感变化。','expectedVersion':r2['version']});call(path+'/'+r2['id']+'/review',t,{'decision':'ACCEPTED','comment':'原文依据和情感分析完整，认可本轮修订。','expectedVersion':r2['version']});h=call(path,sa);check('认可后的两轮修订及反馈可回看',len(h['items'])==2 and h['items'][1]['status']=='ACCEPTED' and h['originalText']==original)
current=call('/api/submissions/'+sid,sa);check('正式原稿版本及80分保持',current['answerText']==original and current['version']==s['version'] and current['grade']['score']=='80.00');check('修订不伪造正式评分历史',call('/api/submissions/'+sid+'/grade-history',sa)['total']==1)
r3=call(path,t,{'requestText':'取消验证邀请','expectedSubmissionVersion':s['version']});call(path+'/'+r3['id']+'/cancel',t,{'expectedVersion':r3['version']});check('取消邀请仍留存记录',call(path,sa)['items'][-1]['status']=='CANCELLED')
call(path,t,{'requestText':'请比较“月光”和“故乡”两处意象，再补充一段分析。','expectedSubmissionVersion':s['version']});f['submissionId']=sid;f['taskId']=task['id'];(PRIVATE/'v6-fixtures.json').write_text(json.dumps(f,ensure_ascii=False,indent=2),encoding='utf8')
print('R2 actual revision checks PASS:',len(checks))
