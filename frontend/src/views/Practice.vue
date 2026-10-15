<script setup lang="ts">
import {ref,onMounted,onUnmounted,reactive,computed,watch} from 'vue';
import {useRoute,onBeforeRouteLeave} from 'vue-router';
import {api} from '../api';
import {useTask,time} from '../useTask';
import type {Quiz,QuizAttempt,QuizRun,PracticeState,Course} from '../models';
import {typeNames,options,toggleChoice,manual} from '../question-ui';
import {ElMessageBox} from 'element-plus';
import PageHeading from '../components/PageHeading.vue';
import StatePanel from '../components/StatePanel.vue';
import CourseLinks from '../components/CourseLinks.vue';
const id=String(useRoute().params.id),quiz=ref<Quiz|null>(null),course=ref<Course|null>(null),
 state=ref<PracticeState|null>(null),active=ref<QuizRun|null>(null),result=ref<QuizAttempt|null>(null),
 answers=reactive<Record<string,string>>({}),dirty=ref(false),saving=ref(false),saveError=ref(''),
 now=ref(Date.now()),clock={server:Date.now(),anchor:performance.now()};
let restoring=false,stopped=false,timeoutRequested=false,saveTimer:ReturnType<typeof setTimeout>|undefined,
 ticker:ReturnType<typeof setInterval>|undefined,pendingSave:Promise<void>|null=null;
const {busy,error,run}=useTask();
const answered=computed(()=>Object.values(answers).filter(a=>a.trim().length>0).length),
 remaining=computed(()=>active.value?.deadlineAt?Math.max(0,Math.ceil((new Date(active.value.deadlineAt).getTime()-now.value)/1000)):null),
 countdown=computed(()=>remaining.value===null?'不限时':Math.floor(remaining.value/60)+':'+String(remaining.value%60).padStart(2,'0')),
 percent=computed(()=>result.value?.score!==null&&result.value?Math.round(result.value.score/result.value.total*100):0),
 writable=computed(()=>quiz.value?.status==='PUBLISHED'&&course.value?.status==='PUBLISHED'&&remaining.value!==0),
 canStart=computed(()=>quiz.value?.status==='PUBLISHED'&&course.value?.status==='PUBLISHED'
  &&(!quiz.value.opensAt||now.value>=new Date(quiz.value.opensAt).getTime())
  &&(!quiz.value.closesAt||now.value<new Date(quiz.value.closesAt).getTime())
  &&!!state.value&&state.value.usedAttempts<state.value.maxAttempts);
function setClock(serverTime:string){clock.server=new Date(serverTime).getTime();clock.anchor=performance.now();now.value=clock.server}
function restoreRun(r:QuizRun|null){
 active.value=r;restoring=true;
 for(const key of Object.keys(answers)) delete answers[key];
 for(const q of r?.questions||[]) if(q.selectedOption) answers[q.id]=q.selectedOption;
 restoring=false;dirty.value=false;saveError.value='';timeoutRequested=false;
 if(r) setClock(r.serverTime);
}
async function fetchData(preserve=false){
 quiz.value=await api<Quiz>('/api/quizzes/'+id);
 course.value=await api<Course>('/api/courses/'+quiz.value.courseId);
 const next=await api<PracticeState>('/api/practices/'+id+'/state');
 if(stopped) return;
 state.value=next;setClock(next.serverTime);
 if(!preserve||!dirty.value) restoreRun(next.active);
 else if(next.active?.id!==active.value?.id) {active.value=null;dirty.value=false;saveError.value='本次作答已结束，未保存内容不能再提交。';}
 if(next.history.length) result.value=await api<QuizAttempt>('/api/practices/'+id+'/result');
}
function load(){run(()=>fetchData())}
function resumed(){run(()=>fetchData(true))}
onMounted(()=>{
 load();window.addEventListener('hw:session-resumed',resumed);window.addEventListener('beforeunload',unload);
 ticker=setInterval(()=>{
  now.value=clock.server+performance.now()-clock.anchor;
  if(active.value&&remaining.value===0&&!timeoutRequested){timeoutRequested=true;run(()=>fetchData(true));}
 },1000);
});
onUnmounted(()=>{stopped=true;if(ticker)clearInterval(ticker);if(saveTimer)clearTimeout(saveTimer);
 window.removeEventListener('hw:session-resumed',resumed);window.removeEventListener('beforeunload',unload)});
function unload(e:BeforeUnloadEvent){if(dirty.value){e.preventDefault();e.returnValue=''}}
onBeforeRouteLeave(async()=>{
 if(!dirty.value)return true;
 try{await ElMessageBox.confirm('还有答案未保存。离开后可继续服务器已保存的部分；计时不会暂停。','离开测验',{confirmButtonText:'离开',cancelButtonText:'继续作答'});return true}catch{return false}
});
watch(answers,()=>{
 if(restoring||!active.value)return;
 dirty.value=true;if(saveTimer)clearTimeout(saveTimer);
 if(!saveError.value)saveTimer=setTimeout(saveDraft,800);
},{deep:true,flush:'sync'});
function choices(){return Object.entries(answers).filter(([,choice])=>choice).map(([questionId,choice])=>({questionId,choice}))}
async function saveDraft(){
 if(pendingSave)return pendingSave;
 if(!active.value||!writable.value||stopped)return;
 const current=active.value,body={answers:choices(),expectedVersion:current.version},fingerprint=JSON.stringify(body.answers);
 saving.value=true;saveError.value='';
 pendingSave=(async()=>{
  try{
   const saved=await api<QuizRun>('/api/practices/'+id+'/runs/'+current.id+'/draft',{method:'PUT',body});
   if(stopped||active.value?.id!==current.id)return;
   if(saved.status!=='ACTIVE'){await fetchData(true);return}
   active.value.version=saved.version;setClock(saved.serverTime);
   dirty.value=JSON.stringify(choices())!==fingerprint;
  }catch(e){saveError.value=e instanceof Error?e.message:'答案暂未保存，请重试'}
  finally{saving.value=false;pendingSave=null;if(dirty.value&&!saveError.value&&!stopped)saveTimer=setTimeout(saveDraft,800)}
 })();return pendingSave;
}
function start(){run(async()=>{
 await ElMessageBox.confirm('开始后消耗一次尝试机会。刷新页面可以继续，计时不会暂停；超时无法交卷。','开始测验');
 restoreRun(await api<QuizRun>('/api/practices/'+id+'/start',{method:'POST'}));
 state.value=await api<PracticeState>('/api/practices/'+id+'/state');result.value=null;
})}
function submit(){run(async()=>{
 await ElMessageBox.confirm('确认交卷？本次答案将冻结，文字题等待教师批阅。还有重试机会时，解析暂不开放。','提交练习');
 if(saveTimer)clearTimeout(saveTimer);if(pendingSave)await pendingSave;
 const current=active.value;if(!current)return;
 const response=await api<QuizAttempt|QuizRun>('/api/practices/'+id+'/attempts',{method:'POST',body:{runId:current.id,answers:choices()}});
 if(response.status==='EXPIRED'){dirty.value=false;await fetchData();return}
 dirty.value=false;restoreRun(null);result.value=response as QuizAttempt;
 state.value=await api<PracticeState>('/api/practices/'+id+'/state');
})}
function review(attemptId:string){run(async()=>{result.value=await api<QuizAttempt>('/api/practices/'+id+'/result?attemptId='+attemptId)})}
function reloadDraft(){run(async()=>{
 await ElMessageBox.confirm('重新载入会用服务器已保存答案替换当前未保存选择。确认重新载入？','重新载入答案');
 if(saveTimer)clearTimeout(saveTimer);if(pendingSave)await pendingSave;
 await fetchData();
})}
function jump(i:number){document.getElementById('practice-question-'+i)?.scrollIntoView({behavior:'auto',block:'start'});document.getElementById('practice-question-'+i)?.focus()}
</script>
<template>
 <PageHeading :title="quiz?.title||'读后小试'" :back="quiz?'/courses/'+quiz.courseId+'/quizzes':'/classes'" />
 <CourseLinks v-if="course" :course-id="course.id" active="practice" /><StatePanel :busy="busy&&!quiz" :error="error" @retry="load" />
 <template v-if="quiz">
  <section class="paper quiz-instructions"><h2>读后小试</h2><p>{{quiz.instructions||'逐题思考，以原文为依据。'}}</p>
   <div class="quiz-rules"><span>{{quiz.questionCount}} 题 / {{quiz.totalPoints}} 分</span><span>{{quiz.timeLimitMinutes?quiz.timeLimitMinutes+' 分钟':'不限时'}}</span><span>已用 {{state?.usedAttempts??'—'}} / {{quiz.maxAttempts}} 次</span></div>
   <p class="field-hint">开放：{{quiz.opensAt?time(quiz.opensAt):'发布后'}}；截止：{{quiz.closesAt?time(quiz.closesAt):'不设截止'}}。多选须与正确答案完全一致，填空与简答由教师批阅。超时消耗一次机会，未交卷不计为已提交成绩。</p>
  </section>
  <section v-if="state&&!active" class="paper practice-entry">
   <template v-if="canStart"><h2>{{state.usedAttempts?'再读，再试':'准备好，再展开试卷'}}</h2><p>开始后可保存答案，刷新页面继续作答。解析在尝试机会用完、截止或归档后开放。</p><el-button type="primary" :loading="busy" @click="start">{{state.usedAttempts?'开始下一次作答':'开始测验'}}</el-button></template>
   <p v-else-if="quiz.status!=='PUBLISHED'||course?.status!=='PUBLISHED'">练习或课程已归档，保留已提交答卷。</p>
   <p v-else-if="quiz.opensAt&&now<new Date(quiz.opensAt).getTime()">练习尚未开放，请按开放时间进入。</p>
   <p v-else-if="quiz.closesAt&&now>=new Date(quiz.closesAt).getTime()">练习已截止，不能继续作答。</p>
   <p v-else>尝试次数已用完，可回看下方答卷。</p>
   <p v-if="state.lastRun?.status==='EXPIRED'" class="read-only-note">第 {{state.lastRun.attemptNo}} 次作答已超时，未生成已提交成绩。</p>
  </section>
  <div v-if="active" class="practice-layout">
   <aside class="practice-index paper"><span class="practice-index-spine" aria-hidden="true">答<br>题<br>签</span><h2>第 {{active.attemptNo}} 次作答</h2><p class="practice-clock" :class="{'urgent':remaining!==null&&remaining<120}" role="timer" aria-label="剩余时间">{{countdown}}</p><p>{{active.deadlineAt?'以服务器截止时间为准':'本卷不限时'}}；已答 {{answered}} / {{active.questions?.length}}</p>
    <div class="practice-index-buttons"><button v-for="(q,i) in active.questions" :key="q.id" type="button" :class="{'is-answered':answers[q.id]}" :aria-label="'第'+(i+1)+'题，'+(answers[q.id]?'已作答':'未作答')" @click="jump(i)">{{i+1}}</button></div>
    <p class="draft-status" aria-live="polite">{{saving?'正在保存答案…':saveError|| (dirty?'答案尚未保存':'答案已保存')}}</p><el-button :loading="saving" :disabled="!writable" @click="saveDraft">保存答案</el-button>
    <el-button v-if="saveError" :disabled="saving" @click="reloadDraft">重新载入已保存答案</el-button>
    <p v-if="!writable" class="read-only-note">测验已停止接收作答，本页选择只供查看。</p>
    <p class="field-hint">计时不因刷新或离开暂停。</p>
   </aside>
   <form @submit.prevent="submit"><fieldset v-for="(q,i) in active.questions" :key="q.id" :id="'practice-question-'+i" tabindex="-1" class="paper question-manuscript"><legend>第 {{i+1}} 题 · {{typeNames[q.questionType]}} · {{q.points}} 分</legend><h2>{{q.prompt}}</h2>
    <p v-if="q.questionType==='MULTIPLE'" class="field-hint">选择所有符合题意的选项，至少选择一项。</p>
    <label v-if="manual(q.questionType)" class="written-answer-field">{{q.questionType==='FILL_BLANK'?'填写答案':'写下你的理解'}}<el-input v-model="answers[q.id]" type="textarea" :rows="q.questionType==='FILL_BLANK'?3:7" maxlength="4000" :disabled="!writable" :aria-label="'第'+(i+1)+'题文字作答'"/><small>最多 4000 字，交卷后由教师批阅。</small></label>
    <div v-else class="answer-options"><label v-for="o in options(q)" :key="o.key" :class="{'selected-option':answers[q.id]?.includes(o.key)}">
     <input v-if="q.questionType==='MULTIPLE'" type="checkbox" :checked="answers[q.id]?.includes(o.key)||false" :disabled="!writable" @change="answers[q.id]=toggleChoice(answers[q.id]||'',o.key,($event.target as HTMLInputElement).checked)" />
     <input v-else type="radio" :name="'question-'+q.id" v-model="answers[q.id]" :value="o.key" :disabled="!writable" required /><b>{{o.key}}</b><span>{{o.text}}</span>
    </label></div>
   </fieldset><div class="practice-submit-bar"><span>已答 {{answered}} / {{active.questions?.length}} 题</span><el-button type="primary" native-type="submit" :loading="busy" :disabled="answered!==active.questions?.length||!writable">确认交卷</el-button></div></form>
  </div>
  <section v-if="state?.history.length&&!active" class="paper practice-history"><h2>我的答卷册</h2><div class="practice-record-row" v-for="h in state.history" :key="h.id"><strong>第 {{h.attemptNo}} 次</strong><span>{{h.gradingStatus==='PENDING'?'待人工批阅':h.score+' / '+h.total+' 分'}}</span><small>{{time(h.completedAt)}}</small><el-button @click="review(h.id)">回看答卷</el-button></div></section>
  <template v-if="result&&!active"><section class="result-scroll"><span class="completed-seal">已交卷</span><div><h2>第 {{result.attemptNo}} 次作答</h2><p v-if="result.gradingStatus==='PENDING'">文字题待人工批阅，整卷成绩尚未确定。</p><p v-else>得分 <strong>{{result.score}}</strong> / {{result.total}}，得分率 {{percent}}%</p><small>{{time(result.completedAt)}}</small></div><div v-if="result.gradingStatus!=='PENDING'" class="result-circle" :style="{'--progress':percent+'%'}"><span>{{percent}}<small>%</small></span></div></section>
   <p v-if="!result.answersReleased" class="read-only-note">还有重试机会，暂不展示正确答案与逐题解析。尝试次数用完、截止或归档后开放。</p>
   <article v-for="(q,i) in result.items" :key="q.questionId" class="paper question-manuscript" :class="{'needs-review':result.answersReleased&&!q.correct}"><span class="question-tab">第 {{i+1}} 题 · {{typeNames[q.questionType]}} · {{q.points}} 分{{result.answersReleased?(manual(q.questionType)?(q.awardedPoints==null?' · 待批阅':' · 得分 '+q.awardedPoints):(q.correct?' · 理解正确':' · 再读一遍')):''}}</span><h2>{{q.prompt}}</h2>
    <section v-if="manual(q.questionType)" class="written-response"><strong>我的作答</strong><p class="plain-text">{{q.selectedOption}}</p><template v-if="result.answersReleased"><div class="written-reference"><strong>参考答案</strong><p class="plain-text">{{q.correctOption}}</p></div><p v-if="q.teacherComment" class="written-feedback"><b>先生批语</b><br/>{{q.teacherComment}}</p></template></section>
    <ol v-else class="answer-options"><li v-for="o in options(q)" :key="o.key" :class="{'correct-option':q.correctOption?.includes(o.key),'selected-option':q.selectedOption.includes(o.key)}"><b>{{o.key}}</b><span>{{o.text}}</span><small v-if="q.selectedOption.includes(o.key)">我的选择</small><small v-if="q.correctOption?.includes(o.key)">正确答案</small></li></ol>
    <div v-if="result.answersReleased" class="question-explanation"><strong>读解</strong><p>{{q.explanation||'未填写解析'}}</p></div>
   </article>
  </template>
 </template>
</template>
