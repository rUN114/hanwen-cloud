<script setup lang="ts">
import {canTeach,courseRoles} from "../course-access";
import { reactive, ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, ApiError } from "../api";
import { useTask, length } from "../useTask";
import type { Course, Chapter } from "../models";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
import DocumentEditor from "../components/DocumentEditor.vue";
const route = useRoute(),
  router = useRouter(),
  id = String(route.params.courseId),
  chapterId = String(route.params.chapterId || ""),
  course = ref<Course | null>(null),
  form = reactive({ title: "", sortNo: 1, content: "", lessonDocument:"" });
const { busy, error, run } = useTask();
function load() {
  run(async () => {
    course.value = await api<Course>("/api/courses/" + id);
    if (chapterId) {
      const h = await api<Chapter>(
        "/api/courses/" + id + "/chapters/" + chapterId,
      );
      form.title = h.title;
      form.sortNo = h.sortNo;
      form.content = h.content || "";
      form.lessonDocument=h.lessonDocument||"";
    }
  });
}
onMounted(load);
function save() {
  run(async () => {
    if (length(form.content.trim()) < 1 || length(form.content.trim()) > 20000)
      throw new ApiError(400, "VALIDATION_ERROR", "正文须为1至20000字");
    await api(
      "/api/teacher/courses/" +
        id +
        "/chapters" +
        (chapterId ? "/" + chapterId : ""),
      { method: chapterId ? "PUT" : "POST", body: form },
    );
    await router.push("/teacher/courses/" + id + "/chapters");
  });
}
</script>
<template>
  <PageHeading
    :title="chapterId ? '章节内容' : '新增章节'"
    :back="'/teacher/courses/' + id + '/chapters'"
    description="原文保留逐字批注，讲义可另行排版、配图与引用。"
  /><StatePanel :busy="busy && !course" :error="error" @retry="load" />
  <form v-if="course" class="paper form-paper" @submit.prevent="save">
    <div v-if="course.status !== 'DRAFT'" class="read-only-note">
      {{
        course.status === "ARCHIVED"
          ? "归档课程只读。"
          : "已发布课程排序不可修改。"
      }}
    </div>
    <div class="form-field">
      <label for="chapter-title">章节标题</label
      ><el-input
        id="chapter-title"
        v-model="form.title"
        required
        maxlength="100"
        :disabled="!canTeach(course) || course.status === 'ARCHIVED'"
      />
    </div>
    <div class="form-field">
      <label for="chapter-sort">排序号</label
      ><el-input-number
        id="chapter-sort"
        v-model="form.sortNo"
        :min="1"
        :max="100000"
        :disabled="course.status !== 'DRAFT'"
      />
    </div>
    <div class="form-field">
      <label for="chapter-content">正文</label
      ><el-input
        id="chapter-content"
        v-model="form.content"
        type="textarea"
        :rows="16"
        :disabled="!canTeach(course) || course.status === 'ARCHIVED'"
      /><span class="field-hint">{{ length(form.content) }} / 20000 字</span>
    </div>
    <label class="form-field">排版讲义（选填）<DocumentEditor v-model="form.lessonDocument" :course-id="id" :disabled="!canTeach(course)||course.status==='ARCHIVED'"/></label>
    <div class="form-actions">
      <el-button
        v-if="canTeach(course) && course.status !== 'ARCHIVED'"
        native-type="submit"
        type="primary"
        :loading="busy"
        >保存章节</el-button
      ><RouterLink :to="'/teacher/courses/' + id + '/chapters'"
        >返回目录</RouterLink
      >
    </div>
  </form>
</template>
