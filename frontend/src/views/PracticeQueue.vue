<script setup lang="ts">
import {ref,onMounted} from 'vue';
import {api} from '../api';
import {useTask,time} from '../useTask';
import type {Page} from '../models';
import PageHeading from '../components/PageHeading.vue';
import StatePanel from '../components/StatePanel.vue';
interface Pending {id:string;courseId:string;quizId:string;studentName:string;completedAt:string}
const rows=ref<Pending[]>([]),total=ref(0),page=ref(1),{busy,error,run}=useTask();
function load(){run(async()=>{const result=await api<Page<Pending>>('/api/teacher/practice-attempts/pending?page='+page.value+'&size=12');rows.value=result.items;total.value=result.total;});}
function change(value:number){page.value=value;load();}
onMounted(load);
</script>
<template>
 <PageHeading title="文字题待阅" back="/home" description="合卷之前，逐字细读。汇集你所授课程与协作课程中的待批答卷。"/>
 <section class="mark-frontispiece"><span class="mark-seal" aria-hidden="true">待<br/>阅</span><div><h2>待批文字答卷 {{total}} 份</h2><p>按交卷先后排列；填空和简答经人工批阅后纳入正式成绩。已批答卷可在课程练习记录中重新查看。</p></div><el-button :loading="busy" @click="load">刷新待阅</el-button></section>
 <StatePanel :busy="busy" :error="error" @retry="load"/>
 <section v-if="!error" class="paper"><p v-if="!busy&&!rows.length" class="muted">本页没有待批答卷。</p><article v-for="row in rows" :key="row.id" class="list-line"><div><h2>{{row.studentName}}</h2><small>{{time(row.completedAt)}}交卷 · 课程 {{row.courseId}} · 练习 {{row.quizId}}</small></div><RouterLink :to="'/teacher/practice-attempts/'+row.id"><el-button type="primary">展卷批阅</el-button></RouterLink></article><el-pagination v-if="total>12" :current-page="page" :page-size="12" :total="total" layout="prev,pager,next,total" :disabled="busy" @current-change="change"/></section>
</template>
