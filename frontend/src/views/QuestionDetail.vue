<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { useRoute } from "vue-router";
import { session } from "../session";
import { api } from "../api";
import { useTask, time } from "../useTask";
import { ElMessage } from "element-plus";
import type { Question, Reply, Page, Course } from "../models";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
const route = useRoute(),
  q = ref<Question | null>(null),
  course = ref<Course | null>(null),
  replies = ref<Reply[]>([]),
  page = ref(1),
  total = ref(0),
  content = ref("");
const { busy, error, run } = useTask();
const writable = computed(() => course.value?.status === "PUBLISHED"),
  canResolve = computed(
    () =>
      q.value?.studentId === session.value!.user.id ||
      (session.value!.user.role === "TEACHER" &&
        course.value?.accessRole !== "NONE" && !!course.value?.accessRole),
  );
async function fetchReplies() {
  const p = await api<Page<Reply>>(
    "/api/questions/" + route.params.id + "/replies?page=" + page.value,
  );
  replies.value = p.items;
  total.value = p.total;
}
function load() {
  run(async () => {
    q.value = await api<Question>("/api/questions/" + route.params.id);
    course.value = await api<Course>("/api/courses/" + q.value.courseId);
    await fetchReplies();
  });
}
watch(
  () => route.params.id,
  () => {
    page.value = 1;
    content.value = "";
    load();
  },
  { immediate: true },
);
function reply() {
  run(async () => {
    await api("/api/questions/" + q.value!.id + "/replies", {
      method: "POST",
      body: { content: content.value },
    });
    content.value = "";
    q.value = await api<Question>("/api/questions/" + q.value!.id);
    page.value = Math.max(1, Math.ceil(q.value.replyCount / 10));
    await fetchReplies();
    ElMessage.success("回复已发布");
  });
}
function resolve() {
  run(async () => {
    await api("/api/questions/" + q.value!.id + "/resolved", {
      method: "PUT",
      body: { resolved: !q.value!.resolved, expectedVersion: q.value!.version },
    });
    q.value = await api<Question>("/api/questions/" + q.value!.id);
  });
}
</script>
<template>
  <PageHeading
    :title="q?.title || '问学堂'"
    :back="
      q ? '/courses/' + q.courseId + '/community?tab=questions' : undefined
    "
    :description="course?.name"
    ><el-button
      v-if="q && writable && canResolve"
      :loading="busy"
      @click="resolve"
      >{{ q.resolved ? "重新打开问题" : "标记为已解决" }}</el-button
    ></PageHeading
  ><StatePanel :busy="busy && !q" :error="error" @retry="load" />
  <article v-if="q" class="paper question-main">
    <span class="small-seal">{{ q.resolved ? "已解" : "问学" }}</span>
    <p class="field-hint">
      {{ q.displayName || "学生 " + q.studentId }} · {{ time(q.createdAt)
      }}<span v-if="q.chapterId"> · 关联章节 {{ q.chapterId }}</span>
    </p>
    <div class="reading-body">{{ q.content }}</div>
  </article>
  <section v-if="q" class="paper">
    <h2>
      师友回应 <small>{{ total }} 条</small>
    </h2>
    <p v-if="!replies.length && !busy" class="muted">
      尚无回复，欢迎留下你的理解。
    </p>
    <article v-for="r in replies" :key="r.id" class="reply-paper">
      <header>
        <b>{{ r.displayName || "用户 " + r.authorId }}</b
        ><span v-if="r.role === 'TEACHER'" class="teacher-sign">先生答疑</span
        ><small>{{ time(r.createdAt) }}</small>
      </header>
      <p class="plain-text">{{ r.content }}</p>
    </article>
    <el-pagination
      v-if="total"
      v-model:current-page="page"
      :total="total"
      :page-size="10"
      layout="prev,pager,next,total"
      @current-change="run(fetchReplies)"
    />
    <form v-if="writable" class="reply-form" @submit.prevent="reply">
      <label class="form-field"
        >写一条回复<el-input
          v-model="content"
          type="textarea"
          :rows="4"
          maxlength="5000"
          required
          placeholder="尽量引用文本依据，帮助彼此把问题想清楚。" /></label
      ><el-button native-type="submit" type="primary" :loading="busy"
        >发布回复</el-button
      >
    </form>
    <p v-else class="read-only-note">归档课堂保留讨论记录，不能新增回复。</p>
  </section>
</template>
