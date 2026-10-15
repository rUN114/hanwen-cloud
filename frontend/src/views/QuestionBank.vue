<script setup lang="ts">
import {ref,reactive,onMounted} from 'vue';
import {api} from '../api';
import {useTask} from '../useTask';
import type {BankQuestion,Page} from '../models';
import {emptyQuestion,questionDraft,typeNames,options,manual} from '../question-ui';
import QuestionFields from '../components/QuestionFields.vue';
import PageHeading from '../components/PageHeading.vue';
import StatePanel from '../components/StatePanel.vue';
import {ElMessage,ElMessageBox} from 'element-plus';
const rows=ref<BankQuestion[]>([]),page=ref(1),total=ref(0),editing=ref(false),editId=ref(''),version=ref(0),
 form=ref(emptyQuestion()),topic=ref(''),filter=reactive({keyword:'',type:'',topic:'',status:'ACTIVE'});
const {busy,error,run}=useTask();
async function fetchRows(){
 const params=new URLSearchParams({...filter,page:String(page.value),size:'12'});
 const p=await api<Page<BankQuestion>>('/api/teacher/question-bank?'+params);
 rows.value=p.items;total.value=p.total;
}
function load(){run(fetchRows)}
onMounted(load);
function edit(q?:BankQuestion){
 editId.value=q?.id||'';version.value=q?.version||0;topic.value=q?.topic||'';
 form.value=q?questionDraft(q):emptyQuestion();
 editing.value=true;
}
function save(){run(async()=>{
 await api('/api/teacher/question-bank'+(editId.value?'/'+editId.value:''),{
  method:editId.value?'PUT':'POST',body:{...form.value,topic:topic.value,expectedVersion:version.value}});
 editing.value=false;await fetchRows();ElMessage.success('题笺已保存，可用于自己的课程组卷');
})}
function state(q:BankQuestion){run(async()=>{
 await ElMessageBox.confirm(q.status==='ACTIVE'?'归档后不再用于新的组卷，已有试卷保持原样。':'恢复后可重新用于组卷。','题笺状态');
 await api('/api/teacher/question-bank/'+q.id+'/status',{method:'PUT',body:{status:q.status==='ACTIVE'?'ARCHIVED':'ACTIVE',expectedVersion:q.version}});
 await fetchRows();
})}
</script>
<template>
 <PageHeading title="问学题笺" description="将字句、文意与辨析收进题库，供不同课程复用。"><el-button type="primary" @click="edit()">新建题笺</el-button></PageHeading>
 <section class="bank-intro"><div><h2>积问成册，选题成卷</h2><p>单选、多选、判断与填空、简答各有所用。组卷时保存题目副本，题库修订不会改动旧答卷。</p></div><span class="bank-spine" aria-hidden="true">问<br>学<br>录</span></section>
 <form class="paper bank-filter" @submit.prevent="page=1;load()">
  <div class="form-field"><label for="bank-keyword">题目关键词</label><el-input id="bank-keyword" v-model="filter.keyword" placeholder="如：意象、月色、李白" maxlength="100" /></div>
  <div class="form-field"><label for="bank-type">题型</label><select id="bank-type" v-model="filter.type" class="native-select"><option value="">全部题型</option><option v-for="(name,key) in typeNames" :key="key" :value="key">{{name}}</option></select></div>
  <div class="form-field"><label for="bank-topic">主题</label><el-input id="bank-topic" v-model="filter.topic" maxlength="60" placeholder="精确主题筛选" /></div>
  <div class="form-field"><label for="bank-status">状态</label><select id="bank-status" v-model="filter.status" class="native-select"><option value="ACTIVE">可用</option><option value="ARCHIVED">已归档</option></select></div>
  <el-button native-type="submit" :loading="busy">筛选题笺</el-button>
 </form>
 <StatePanel :busy="busy&&!rows.length" :error="error" :empty="!busy&&!error&&!rows.length" empty-text="尚无匹配题笺，可以新建一道或调整筛选。" @retry="load" />
 <section class="bank-grid"><article v-for="q in rows" :key="q.id" class="bank-slip">
  <div class="bank-slip-meta"><span>{{typeNames[q.questionType]}}</span><b>{{q.points}} 分</b><small>{{q.topic||'未分类'}} · 修订 {{q.version}}</small></div>
  <h2>{{q.prompt}}</h2><ol class="bank-options"><li v-for="o in options(q)" :key="o.key" :class="{'correct-option':q.correctOption?.includes(o.key)}"><b>{{o.key}}</b> {{o.text}}</li></ol>
  <details><summary>查看解析</summary><p>{{q.explanation||'未填写解析'}}</p></details>
  <div class="course-nav-actions"><el-button v-if="q.status==='ACTIVE'" @click="edit(q)">修订题笺</el-button><el-button @click="state(q)">{{q.status==='ACTIVE'?'归档':'恢复'}}</el-button></div>
 </article></section>
 <el-pagination v-if="total" v-model:current-page="page" :total="total" :page-size="12" layout="prev,pager,next,total" @current-change="load" />
 <el-dialog v-model="editing" :title="editId?'修订题笺':'新建题笺'" width="min(680px,94vw)"><form @submit.prevent="save">
  <QuestionFields v-model="form" prefix="bank-question" />
  <div class="form-field"><label for="bank-edit-topic">主题标签</label><el-input id="bank-edit-topic" v-model="topic" maxlength="60" placeholder="如：唐诗意象" /></div>
  <StatePanel :error="error" @retry="save" /><el-button native-type="submit" type="primary" :loading="busy">保存题笺</el-button>
 </form></el-dialog>
</template>
