<script setup lang="ts">
import { typeNames, options, toggleChoice, manual, type QuestionDraft } from '../question-ui';
const form = defineModel<QuestionDraft>({ required: true });
defineProps<{prefix: string; knowledgeField?:boolean}>();
function changeType() {
  form.value.correctOption = manual(form.value.questionType)?'':form.value.questionType === 'MULTIPLE' ? 'AB' : 'A';
  if(form.value.questionType === 'TRUE_FALSE') Object.assign(form.value,{optionA:'正确',optionB:'错误',optionC:'',optionD:''});
}
</script>
<template>
  <div class="form-row">
    <div class="form-field"><label :for="prefix+'-type'">题型</label><select :id="prefix+'-type'" class="native-select" v-model="form.questionType" @change="changeType"><option v-for="(name,key) in typeNames" :key="key" :value="key">{{ name }}</option></select></div>
    <div class="form-field"><label :for="prefix+'-points'">分值</label><el-input-number :id="prefix+'-points'" v-model="form.points" :min="1" :max="100" :precision="0" /></div>
  </div>
  <div class="form-field"><label :for="prefix+'-prompt'">题目</label><el-input :id="prefix+'-prompt'" v-model="form.prompt" type="textarea" :rows="3" maxlength="2000" required /></div>
  <label v-if="knowledgeField" class="form-field">知识点（可选）<el-input v-model="form.knowledgePoint" maxlength="60" placeholder="例如：意象与情感" aria-label="题目知识点"/></label>
  <template v-if="!manual(form.questionType) && form.questionType !== 'TRUE_FALSE'">
    <div class="form-field" v-for="letter in ['A','B','C','D']" :key="letter"><label :for="prefix+'-option-'+letter">选项 {{ letter }}</label><el-input :id="prefix+'-option-'+letter" v-model="form[('option'+letter) as 'optionA']" maxlength="500" required /></div>
  </template>
  <div v-if="manual(form.questionType)" class="form-field"><label :for="prefix+'-reference'">参考答案与评分依据</label><el-input :id="prefix+'-reference'" v-model="form.correctOption" type="textarea" :rows="4" maxlength="4000" required/><p class="field-hint">填空与简答均由教师批阅，允许文学表达差异；不按字符串完全相同自动打分。</p></div>
  <fieldset v-else class="answer-key"><legend>正确答案{{ form.questionType === 'MULTIPLE' ? '（至少两项，全对得分）' : '' }}</legend>
    <label v-for="o in options(form)" :key="o.key">
      <input v-if="form.questionType === 'MULTIPLE'" type="checkbox" :checked="form.correctOption.includes(o.key)" @change="form.correctOption=toggleChoice(form.correctOption,o.key,($event.target as HTMLInputElement).checked)" />
      <input v-else type="radio" :name="prefix+'-correct'" v-model="form.correctOption" :value="o.key" />
      {{ o.key }}{{ form.questionType === 'TRUE_FALSE' ? ' '+o.text : '' }}
    </label>
  </fieldset>
  <div class="form-field"><label :for="prefix+'-explanation'">解析</label><el-input :id="prefix+'-explanation'" v-model="form.explanation" type="textarea" :rows="3" maxlength="2000" /></div>
</template>
