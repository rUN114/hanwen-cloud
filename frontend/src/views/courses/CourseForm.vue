<script setup lang="ts">
import { canTeach, courseRoles } from "../../utils/course-access";
import { reactive, ref, onMounted, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../../api/client";
import { useTask } from "../../composables/useTask";
import type { Course } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
const route = useRoute();
const router = useRouter();
const id = String(route.params.courseId || "");
const archived = ref(false);
const loaded = ref(!id);
const form = reactive({ name: "", category: "古代文学", summary: "" });
const { busy, error, run } = useTask();
function load() {
  run(async () => {
    const c = await api<Course>("/api/courses/" + id);
    form.name = c.name;
    form.category = c.category;
    form.summary = c.summary || "";
    archived.value = c.status === "ARCHIVED" || !canTeach(c);
    loaded.value = true;
  });
}
if (id) onMounted(load);
function save() {
  run(async () => {
    const c = await api<Course>("/api/teacher/courses" + (id ? "/" + id : ""), {
      method: id ? "PUT" : "POST",
      body: form,
    });
    await router.push("/teacher/courses/" + c.id + "/chapters");
  });
}
</script>
<template>
  <PageHeading
    :title="id ? '课程介绍' : '新建课程'"
    back="/teacher/courses"
    description="先保存课程介绍，再编写章节；发布为单独操作。"
  />
  <StatePanel
    :busy="busy && !loaded"
    :error="error"
    @retry="id ? load() : save()"
  />
  <form
    v-if="loaded"
    class="paper form-paper"
    @submit.prevent="save"
  >
    <div
      v-if="archived"
      class="read-only-note"
    >
      当前身份或归档状态下，课程介绍只读。
    </div>
    <div class="form-field">
      <label for="course-name">课程名称</label>
      <el-input
        id="course-name"
        v-model="form.name"
        required
        maxlength="100"
        :disabled="archived"
      />
    </div>
    <div class="form-field">
      <label for="category">分类</label>
      <el-select
        id="category"
        v-model="form.category"
        :disabled="archived"
        filterable
        allow-create
        default-first-option
      >
        <el-option
          v-for="c in ['古代文学', '现代文学', '文学理论']"
          :key="c"
          :label="c"
          :value="c"
        />
      </el-select>
      <span class="field-hint">分类最多30字。</span>
    </div>
    <div class="form-field">
      <label for="course-summary">课程简介</label>
      <el-input
        id="course-summary"
        v-model="form.summary"
        type="textarea"
        :rows="7"
        maxlength="2000"
        :disabled="archived"
      />
      <span class="field-hint">可留空，最多2000字；只作纯文本展示。</span>
    </div>
    <div class="form-actions">
      <el-button
        v-if="!archived"
        type="primary"
        native-type="submit"
        :loading="busy"
      >
        保存并管理章节
      </el-button>
      <RouterLink to="/teacher/courses">返回课程列表</RouterLink>
    </div>
  </form>
</template>
