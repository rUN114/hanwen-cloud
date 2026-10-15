"""Optional complete sample course. Requires initialized private demo accounts.
Run after service startup. Preserves old data; records own IDs for repeat-safe setup.
"""
from pathlib import Path
import sys,json,datetime as dt,urllib.request,secrets
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'tests'))
import integration as it
marker=root/'.local/teaching-sample.json'
if marker.exists():
    print('Sample course was already created; existing content kept.');sys.exit(0)
accounts=json.loads((root/'.local/demo-accounts.json').read_text(encoding='utf8'))
t=it.login(accounts[0]['username'],accounts[0]['password'])['accessToken']
c=it.call('/api/teacher/courses',t,body={'name':'唐诗细读：月色、春声与故乡','category':'示范课程','summary':'从《静夜思》与《春晓》出发，先观察文本中的感官与动作，再通过比较阅读和短篇习作形成自己的解释。本课程为项目自编示范，古诗来源与版本在章节中列明，学习任务和解析为项目原创。'})
texts=[
('先看见，再解释：诗歌细读入门', '''阅读前，先把解释放慢。

一、观察文本
找出诗中可直接观察的词语：景物、动作、声音和视角。区分“我看见了什么”与“我认为它意味着什么”。

二、把意象放回语境
月亮在不同作品里未必表示同一种情感。解释时要把词语与相邻诗句、人物动作和篇章转折联系起来。仅写“月亮代表思乡”还缺少文本依据。

三、组织一段论证
可以采用“判断—引用—分析”的结构。例如：诗中视线由眼前转向远方；依据是“举头望明月”；动作与视角的变化为结尾的思乡铺垫。还需要继续解释“低头”与“故乡”的关系。

本章任务
读第二章时，圈出两个动作词；在章边批注中写下每个动作改变了什么。批注属于你本人，教师可看到学习进度，但不能阅读你的私人批注。

来源说明：本章导读为项目原创教学示例。'''),
('月色与视线：《静夜思》', '''《静夜思》
唐·李白

床前明月光，
疑是地上霜。
举头望明月，
低头思故乡。

观察：从“疑”到“望”
开头写眼前的光，随后出现“疑”，再以“举头”改变观察方向。先记录这些词语，不急于把所有景物解释成固定符号。

细读：两个动作怎样连接情感
“举头”把视线投向明月，“低头”把思绪带回自身。解释思乡时，需要联系“思故乡”这一明确表达，再讨论动作与情感之间的关系。

比较提问
如果删去“低头”而只保留“思故乡”，画面与节奏会怎样变化？请用原句作为依据，提出一种可能的解释。

练习与习作
先完成本章选择题，再写一段120至200字的分析。选择题检验文本观察；习作允许有不同解释，但需要明确依据。

古诗版本：维基文库《唐诗三百首》所列版本，繁体转简体，按诗句分行。
原文来源：https://zh.wikisource.org/w/index.php?title=靜夜思&oldid=8747585
导读、问题与练习解析：项目原创示例。'''),
('听觉与留白：《春晓》', '''《春晓》
唐·孟浩然

春眠不觉晓，
处处闻啼鸟。
夜来风雨声，
花落知多少。

观察：哪些内容来自听觉
“闻啼鸟”是醒来后的听觉经验，“风雨声”把时间带回夜间。末句没有直接报出落花数量，而是留下一个疑问。

细读：末句为什么不写确定的数量
“知多少”保留了不确定性。分析时可以讨论这种不确定性如何影响读者想象，但不要把自己的解释写成唯一答案。

比较阅读
《静夜思》以动作和视线展开，《春晓》以声音和时间转换展开。尝试为两首诗各选一处文本证据，比较它们组织感受的方式。

复习任务
完成本章练习，并在“声音与视线比较”习作中写一段比较。可以先保存草稿，重新登录后继续写作，再正式提交。

古诗版本：维基文库所列“夜来风雨声”版本，繁体转简体，按诗句分行；其他版本存在异文，引用时需说明版本。
原文来源：https://zh.wikisource.org/w/index.php?title=春曉_(孟浩然)&oldid=10374454
导读、问题与练习解析：项目原创示例。''')]
chapters=[it.call('/api/teacher/courses/'+c['id']+'/chapters',t,body={'title':title,'sortNo':i+1,'content':text}) for i,(title,text) in enumerate(texts)]
it.call('/api/teacher/courses/'+c['id']+'/publish',t)
room=it.call('/api/teacher/classes',t,body={'courseId':c['id'],'name':'见月听春 · 共读学舍','semester':'2026年秋季','description':'先读原文，再谈解释；以批注、练习与习作为线索共同学习。'})
it.call('/api/teacher/classes/'+room['id']+'/members',t,body={'usernames':[accounts[2]['username']]})
it.call('/api/teacher/courses/'+c['id']+'/notices',t,body={'title':'本周共读安排','content':'先完成第一章导读，在第二章圈出“举头”“低头”并写下自己的观察。选择题完成后可以查看解析；习作先保存草稿，确认后正式提交。示范材料不代表真实课堂记录。','pinned':True})
questions=[
 [('解释月亮意象时，最需要结合的是？','相邻诗句与动作','所有作品通用的符号表','页面背景颜色','作者名字的字数','A','意象的意义需要具体语境支撑，不能只套用固定符号。'),('论证一项阅读判断时，哪一种写法更有依据？','我觉得很好，所以正确','引用诗句并分析词语作用','只给结论，不解释','用更长的形容词替代依据','B','引用与分析使读者能够检验你的解释。')],
 [('《静夜思》中改变观察方向的动作词是？','地上与床前','明月与故乡','举头与低头','疑是与地上','C','举头和低头直接表现身体动作与视线变化。'),('诗中哪一句直接写出了思乡？','床前明月光','疑是地上霜','举头望明月','低头思故乡','D','“思故乡”直接表达思乡；其他诗句提供画面与情境。')],
 [('《春晓》中直接表明听觉经验的词语是？','闻啼鸟','花落','不觉晓','春眠','A','“闻”表明听到，“啼鸟”是所闻的声音。'),('末句“花落知多少”的表达特点是？','报告精确数量','保留疑问与不确定性','交代人物姓名','直接叙述完整事件','B','末句以疑问收束，留下想象空间；具体情感解释还应联系全诗。')]
]
quizzes=[]
for chapter,qs in zip(chapters,questions):
    q=it.call('/api/teacher/courses/'+c['id']+'/quizzes',t,body={'chapterId':chapter['id'],'title':chapter['title'].split('：')[0]+' · 随堂练习','instructions':'每题选择一项。每份练习限提交一次，完成后查看原文依据与解析。'})
    for i,values in enumerate(qs):
        prompt,A,B,C,D,correct,explanation=values
        it.call('/api/teacher/quizzes/'+q['id']+'/questions',t,body={'prompt':prompt,'optionA':A,'optionB':B,'optionC':C,'optionD':D,'correctOption':correct,'explanation':explanation,'sortNo':i+1,'expectedVersion':i})
    it.call('/api/teacher/quizzes/'+q['id']+'/status',t,'PUT',{'status':'PUBLISHED','expectedVersion':len(qs)})
    quizzes.append(q['id'])
tasks=[]
for days,title,description in [(4,'月色中的动作与情感','结合《静夜思》中的“举头”“低头”，写120至200字分析。须引用至少一处诗句；评分关注文本依据、解释连贯与表达清楚。'),(7,'声音与视线比较','比较《静夜思》与《春晓》展开感受的方式，写200至300字。为每首诗选择一处依据，允许不同解释，但应说明理由。')]:
    deadline=(dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))+dt.timedelta(days=days)).replace(hour=20,minute=0,second=0,microsecond=0).isoformat()
    a=it.call('/api/teacher/assignments',t,body={'courseId':c['id'],'title':title,'description':description,'maxScore':'100','deadlineAt':deadline})
    it.call('/api/teacher/assignments/'+a['id']+'/publish',t);tasks.append(a['id'])
samples=root/'samples';samples.mkdir(exist_ok=True)
guide=samples/'唐诗细读_学习导引.txt';guide.write_text('唐诗细读学习导引\n\n观察文本—引用依据—解释作用—比较异同。\n\n1. 先标出动作、景物、声音。\n2. 在章边批注中保存自己的判断。\n3. 提交章节练习后阅读解析。\n4. 习作可保存到本人账号，草稿不等于正式提交。\n5. 查看教师评语时，可以展开评分历史核对复核记录。\n\n本导引为项目原创示范材料。',encoding='utf8')
def resource(path,title,description,chapter=None):
    boundary='hanwen'+secrets.token_hex(12)
    fields={'title':title,'description':description}
    if chapter: fields['chapterId']=chapter
    body=b''.join(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode() for k,v in fields.items())
    body+=f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{path.name}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode()+path.read_bytes()+f'\r\n--{boundary}--\r\n'.encode()
    req=urllib.request.Request(it.BASE+'/api/teacher/courses/'+c['id']+'/resources',data=body,method='POST',headers={'Authorization':'Bearer '+t,'Content-Type':'multipart/form-data; boundary='+boundary})
    with urllib.request.urlopen(req,timeout=20) as r:return json.load(r)['data']['id']
resources=[resource(guide,'唐诗细读 · 学习导引','项目原创导引，可下载保留。')]
audio=samples/'静夜思_合成朗读.wav'
if audio.exists():resources.append(resource(audio,'《静夜思》合成朗读','Windows中文语音合成示范；非真人录音，古诗读音请以教学讲解为准。',chapters[1]['id']))
result={'courseId':c['id'],'courseName':c['name'],'classId':room['id'],'chapters':[h['id'] for h in chapters],'quizzes':quizzes,'assignments':tasks,'resources':resources,'note':'项目原创示范课程；未预设学生分数和学习进度。'}
marker.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
(root/'evidence/v3-teaching-sample.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
print('Complete sample created: 3 chapters, 3 quizzes, 2 assignments, classroom and resources.')
