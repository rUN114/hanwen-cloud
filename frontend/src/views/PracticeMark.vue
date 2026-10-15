<script setup lang="ts">
import {computed,ref,onMounted,onUnmounted,watch} from 'vue';
import {useRoute,onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router';
import {ElMessageBox,ElMessage} from 'element-plus';
import {api} from '../api';import {useTask,time} from '../useTask';
import {manual,typeNames} from '../question-ui';
import PageHeading from '../components/PageHeading.vue';import StatePanel from '../components/StatePanel.vue';
interface Item{id:string;questionType:keyof typeof typeNames;prompt:string;selectedOption:string;correctOption:string;explanation:string;points:number;knowledgePoint:string|null;awardedPoints:number|null;teacherComment:string|null}
interface MarkEvent{id:string;teacherName:string;score:number;version:number;itemsJson:string;createdAt:string}
interface Attempt{id:string;courseId:string;quizId:string;studentName:string;score:number|null;total:number;gradingStatus:string;gradingVersion:number;completedAt:string;items:Item[];history:MarkEvent[]}
const route=useRoute(),id=computed(()=>String(route.params.id)),attempt=ref<Attempt|null>(null),marks=ref<Record<string,{score:number|undefined;comment:string}>>({}),dirty=ref(false);
const {busy,error,run}=useTask(),written=computed(()=>attempt.value?.items.filter(x=>manual(x.questionType))||[]);
const provisional=computed(()=>attempt.value?.items.reduce((sum,x)=>sum+(manual(x.questionType)?marks.value[x.id]?.score??0:x.awardedPoints??0),0)||0);
const complete=computed(()=>written.value.length>0&&written.value.every(x=>marks.value[x.id]?.score!==undefined));
function load(){run(async()=>{attempt.value=await api<Attempt>('/api/teacher/practice-attempts/'+id.value);marks.value=Object.fromEntries(written.value.map(x=>[x.id,{score:x.awardedPoints??undefined,comment:x.teacherComment||''}]));dirty.value=false;});}
function save(){run(async()=>{if(!complete.value)return;await ElMessageBox.confirm('本次整卷评分 '+provisional.value+' / '+attempt.value!.total+' 分。保存后更新正式成绩并留存评分历史；学生按练习的解析开放规则查看。','核对批阅');attempt.value=await api<Attempt>('/api/teacher/practice-attempts/'+id.value+'/grade',{method:'PUT',body:{expectedVersion:attempt.value!.gradingVersion,items:written.value.map(x=>({id:x.id,score:marks.value[x.id]!.score,comment:marks.value[x.id]!.comment}))}});dirty.value=false;ElMessage.success('评分与批语已保存');});}
function discard(){run(async()=>{await ElMessageBox.confirm('重新读取会替换本页未保存评分与批语。','重新读取答卷');attempt.value=await api<Attempt>('/api/teacher/practice-attempts/'+id.value);marks.value=Object.fromEntries(written.value.map(x=>[x.id,{score:x.awardedPoints??undefined,comment:x.teacherComment||''}]));dirty.value=false;});}
function unload(e:BeforeUnloadEvent){if(dirty.value){e.preventDefault();e.returnValue='';}}
onMounted(()=>{load();window.addEventListener('beforeunload',unload);});onUnmounted(()=>window.removeEventListener('beforeunload',unload));
async function canLeave(){if(busy.value)return false;if(!dirty.value)return true;try{await ElMessageBox.confirm('还有未保存的评分与批语。确认离开？','离开批阅',{confirmButtonText:'离开',cancelButtonText:'继续批阅'});return true;}catch{return false;}}
onBeforeRouteLeave(canLeave);onBeforeRouteUpdate(canLeave);
watch(id,()=>{attempt.value=null;dirty.value=false;load();});
function historyItems(e:MarkEvent){try{return JSON.parse(e.itemsJson) as {id:string;score:number;comment:string|null}[];}catch{return [];}}
</script>
<template>
 <PageHeading :title="(attempt?.studentName||'同窗')+' · 练习批阅'" :back="attempt?'/courses/'+attempt.courseId+'/quizzes':'/teacher/courses'" description="字句可以辨对错，理解还需细读。"/>
 <StatePanel :busy="busy&&!attempt" :error="error" @retry="dirty?discard():load()"/>
 <template v-if="attempt">
 <section class="mark-frontispiece"><span class="mark-seal" aria-hidden="true">朱<br/>批</span><div><h2>{{attempt.gradingStatus==='PENDING'?'待阅文字题':'已完成批阅'}}</h2><p>{{attempt.studentName}} · {{time(attempt.completedAt)}}交卷</p><p>整卷 {{attempt.total}} 分 · {{written.length}} 道文字题 · 当前正式成绩 {{attempt.score===null?'待评分':attempt.score+' 分'}}</p></div><el-button :disabled="busy" @click="dirty?discard():load()">重新读取</el-button></section>
 <form class="mark-layout" @submit.prevent="save"><section>
 <article v-for="(q,i) in attempt.items" :key="q.id" class="paper mark-question"><span class="question-tab">第 {{i+1}} 题 · {{typeNames[q.questionType]}} · {{q.points}} 分</span><h2>{{q.prompt}}</h2><p v-if="q.knowledgePoint" class="field-hint">知识点 · {{q.knowledgePoint}}</p><section class="written-response"><strong>同窗作答</strong><p class="plain-text">{{q.selectedOption}}</p></section><details class="written-reference"><summary>参考答案与解析</summary><p class="plain-text">{{q.correctOption}}</p><p class="plain-text">{{q.explanation||'未填写解析'}}</p></details>
 <div v-if="manual(q.questionType)" class="mark-inputs"><label>本题得分<el-input-number v-model="marks[q.id]!.score" :min="0" :max="q.points" :precision="0" :aria-label="'第'+(i+1)+'题得分'" @change="dirty=true"/> / {{q.points}}</label><label class="form-field">先生批语<el-input v-model="marks[q.id]!.comment" type="textarea" :rows="3" maxlength="2000" :aria-label="'第'+(i+1)+'题批语'" placeholder="指出原文依据、理解与表达的改进处" @input="dirty=true"/></label></div><p v-else class="field-hint">客观题自动评分：{{q.awardedPoints}} / {{q.points}} 分</p>
 </article></section>
 <aside class="paper mark-summary"><h2>合卷核分</h2><dl><dt>本页试算</dt><dd>{{provisional}} <small>/ {{attempt.total}}</small></dd></dl><p>{{complete?'全部文字题已填写分值，可核对后保存。':'未填分项暂按0参与本页试算；不会作为正式成绩保存。'}}</p><p class="field-hint">待评分保持未知，完成批阅后再纳入成绩册。修订评分会保留此前分值与批语。</p><el-button v-if="written.length" native-type="submit" type="primary" :disabled="!complete" :loading="busy">核对并保存批阅</el-button><p v-else>这份答卷全部为客观题，无需人工评分。</p><span v-if="dirty" class="field-hint" role="status">本页修改尚未保存</span></aside></form>
 <section class="paper mark-history"><h2>朱批留痕</h2><p v-if="!attempt.history.length">尚无人工评分记录。</p><details v-for="e in attempt.history" :key="e.id"><summary>第 {{e.version}} 次评分 · {{e.score}} / {{attempt.total}} 分 · {{e.teacherName}} · {{time(e.createdAt)}}</summary><p v-for="item in historyItems(e)" :key="item.id">题项 {{item.id}} · {{item.score}} 分<br/>{{item.comment||'未写批语'}}</p></details></section>
 </template>
</template>
