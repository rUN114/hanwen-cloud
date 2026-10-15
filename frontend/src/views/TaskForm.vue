<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../api";
import {canTeach} from "../course-access";
import { useTask, length } from "../useTask";
import type { Task, Course } from "../models";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
const route = useRoute(),
  router = useRouter(),
  id = String(route.params.assignmentId || ""),
  courseId = ref(String(route.params.courseId || "")),
  course = ref<Course | null>(null),
  readonly = ref(false),
  form = reactive({
    title: "",
    description: "",
    maxScore: "100.00",
    rubric: [] as {name:string;maxScore:string}[],
    deadlineLocal: "",
  });
const { busy, error, run } = useTask();
function load() {
  run(async () => {
    if (id) {
      const t = await api<Task>("/api/assignments/" + id);
      courseId.value = t.courseId;
      form.title = t.title;
      form.description = t.description;
      form.maxScore = t.maxScore;
      form.rubric = t.rubric || [];
      form.deadlineLocal = t.deadlineAt ? t.deadlineAt.slice(0, 16) : "";
      readonly.value = t.status !== "DRAFT";
    }
    course.value = await api<Course>("/api/courses/" + courseId.value);
    readonly.value = readonly.value || !canTeach(course.value) || course.value.status !== "PUBLISHED";
  });
}
onMounted(load);
function save() {
  run(async () => {
    const body = {
      title: form.title,
      description: form.description,
      maxScore: form.maxScore,
      rubric: form.rubric,
      deadlineAt: form.deadlineLocal ? form.deadlineLocal + ":00+08:00" : null,
      ...(!id ? { courseId: courseId.value } : {}),
    };
    await api("/api/teacher/assignments" + (id ? "/" + id : ""), {
      method: id ? "PUT" : "POST",
      body,
    });
    await router.push("/teacher/courses/" + courseId.value + "/assignments");
  });
}
</script>
<template>
  <PageHeading
    :title="id ? '编辑作业草稿' : '新建作业'"
    :back="'/teacher/courses/' + courseId + '/assignments'"
    description="保存后在课程作业页单独发布。"
  /><StatePanel :busy="busy && !course" :error="error" @retry="load" />
  <form v-if="course" class="paper form-paper" @submit.prevent="save">
    <div v-if="readonly" class="read-only-note">
      当前课程身份、作业或课程状态下，此页只读。
    </div>
    <p class="muted">所属课程：{{ course.name }}</p>
    <div class="form-field">
      <label for="task-title">作业标题</label
      ><el-input
        id="task-title"
        v-model="form.title"
        required
        maxlength="100"
        :disabled="readonly"
      />
    </div>
    <div class="form-field">
      <label for="task-description">题目与要求</label
      ><el-input
        id="task-description"
        v-model="form.description"
        type="textarea"
        :rows="9"
        :disabled="readonly"
      /><span class="field-hint"
        >{{ length(form.description) }} / 10000 字，纯文本。</span
      >
    </div>
    <div class="form-field">
      <label for="task-max">满分</label
      ><el-input
        id="task-max"
        v-model="form.maxScore"
        required
        inputmode="decimal"
        :disabled="readonly"
      /><span class="field-hint">大于0且不超过100，最多两位小数。</span>
    </div>
    <div class="form-field">
      <label for="task-deadline">截止时间（北京时间）</label
      ><input
        id="task-deadline"
        v-model="form.deadlineLocal"
        type="datetime-local"
        class="native-date"
        :disabled="readonly"
      /><span class="field-hint"
        >可留空；发布时须为空或在未来，发布后不能修改。</span
      >
    </div>
    <fieldset class="rubric-editor" :disabled="readonly">
      <legend>评分细则</legend><p class="field-hint">可设置理解、证据、表达等评分项。分项满分之和须等于作业满分，发布后冻结。</p>
      <div v-for="(item,index) in form.rubric" :key="index" class="rubric-row"><el-input v-model="item.name" :aria-label="'评分项'+(index+1)" placeholder="评分项名称" maxlength="80"/><el-input v-model="item.maxScore" :aria-label="'分项满分'+(index+1)" inputmode="decimal" placeholder="满分"/><el-button @click="form.rubric.splice(index,1)">移除</el-button></div>
      <el-button v-if="!readonly && form.rubric.length<12" @click="form.rubric.push({name:'',maxScore:''})">添加评分项</el-button>
    </fieldset>
    <div class="form-actions">
      <el-button
        v-if="!readonly"
        type="primary"
        native-type="submit"
        :loading="busy"
        >保存草稿</el-button
      ><RouterLink :to="'/teacher/courses/' + courseId + '/assignments'"
        >返回课程作业</RouterLink
      >
    </div>
  </form>
</template>
