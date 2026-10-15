<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api";
import { useTask, time } from "../useTask";
import type { Enrollment, Course, Chapter, ProgressSummary } from "../models";
import AnnotatedReading from "../components/AnnotatedReading.vue";
import ResourcePanel from "../components/ResourcePanel.vue";
import ChapterNotes from "../components/ChapterNotes.vue";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
import CourseLinks from "../components/CourseLinks.vue";
import LessonDocument from "../components/LessonDocument.vue";
const route = useRoute(),
  enrollment = ref<Enrollment | null>(null),
  course = ref<Course | null>(null),
  chapters = ref<Chapter[]>([]),
  chapter = ref<Chapter | null>(null),
  summary = ref<ProgressSummary | null>(null),
  menu = ref(false);
const { busy, error, run } = useTask();
const completed = computed(() =>
  summary.value?.items.find(
    (p) => p.chapterId === String(route.params.chapterId),
  ),
);
function load() {
  run(async () => {
    chapter.value = null;
    enrollment.value = await api<Enrollment>(
      "/api/enrollments/" + route.params.enrollmentId,
    );
    const id = enrollment.value.courseId;
    [course.value, chapters.value, chapter.value, summary.value] =
      await Promise.all([
        api<Course>("/api/courses/" + id),
        api<Chapter[]>("/api/courses/" + id + "/chapters"),
        api<Chapter>(
          "/api/courses/" + id + "/chapters/" + route.params.chapterId,
        ),
        api<ProgressSummary>(
          "/api/enrollments/" + enrollment.value.id + "/progress",
        ),
      ]);
    menu.value = false;
  });
}
watch(() => route.fullPath, load, { immediate: true });
function complete() {
  run(async () => {
    await api(
      "/api/enrollments/" +
        enrollment.value!.id +
        "/chapters/" +
        chapter.value!.id +
        "/complete",
      { method: "PUT" },
    );
    summary.value = await api<ProgressSummary>(
      "/api/enrollments/" + enrollment.value!.id + "/progress",
    );
  });
}
</script>
<template>
  <PageHeading :title="course?.name || '章节学习'" :back="enrollment ? '/student/courses/' + enrollment.courseId : '/student/enrollments'"
    ><el-button
      class="mobile-chapter-toggle"
      :aria-expanded="menu"
      @click="menu = !menu"
      >章节目录</el-button
    ><RouterLink
      v-if="enrollment"
      :to="'/student/enrollments/' + enrollment.id + '/assignments'"
      ><el-button>课程作业</el-button></RouterLink
    ><RouterLink
      v-if="enrollment"
      :to="'/courses/' + enrollment.courseId + '/community'"
      ><el-button>课堂交流</el-button></RouterLink
    ></PageHeading
  ><StatePanel :busy="busy && !chapter" :error="error" @retry="load" />
  <CourseLinks v-if="enrollment" :course-id="enrollment.courseId" />
  <div v-if="chapter && enrollment" class="reading-layout">
    <aside class="paper reading-menu" :class="{ 'mobile-open': menu }">
      <h2>卷中目录</h2>
      <ol class="chapter-list">
        <li v-for="h in chapters" :key="h.id">
          <RouterLink
            :class="{ current: h.id === chapter.id }"
            :aria-current="h.id === chapter.id ? 'page' : undefined"
            :to="'/student/enrollments/' + enrollment.id + '/chapters/' + h.id"
            ><span class="chapter-number">{{ h.sortNo }}</span
            >{{ h.title }}</RouterLink
          >
        </li>
      </ol>
      <small v-if="summary"
        >已完成 {{ summary.completedCount }} / {{ summary.total }} 章</small
      >
    </aside>
    <article class="paper reading-sheet">
      <span class="folio" aria-hidden="true">第 {{ chapter.sortNo }} 章</span>
      <h2>{{ chapter.title }}</h2>
      <div v-if="course?.status === 'ARCHIVED'" class="read-only-note">
        课程已归档，可阅读已有内容，不能新增完成记录。
      </div>
      <section v-if="chapter.lessonDocument" class="chapter-lesson"><h3>先生讲义</h3><LessonDocument :content="chapter.lessonDocument"/></section>
      <AnnotatedReading
        :key="chapter.id"
        :enrollment-id="enrollment.id"
        :chapter-id="chapter.id"
        :content="chapter.content || ''"
        :readonly="course?.status !== 'PUBLISHED'"
      />
      <RouterLink
        :to="'/courses/' + enrollment.courseId + '/quizzes'"
        class="chapter-practice-link"
        >读完这一章，展开章节练习 →</RouterLink
      >
      <div class="reading-footer">
        <div v-if="completed">
          <span class="completed-seal">已完成</span
          ><small class="field-hint"
            >首次完成 {{ time(completed.completedAt) }}</small
          >
        </div>
        <el-button
          v-else
          type="primary"
          :loading="busy"
          :disabled="course?.status !== 'PUBLISHED'"
          @click="complete"
          >标记本章完成</el-button
        ><small>阅读后手动标记；不会自动记录学习时长。</small>
      </div>
    </article>
  </div>
  <ResourcePanel
    v-if="chapter && enrollment"
    :key="'resources:' + chapter.id"
    :course-id="enrollment.courseId"
    :chapter-id="chapter.id"
    :course-status="course?.status"
  />
  <ChapterNotes
    v-if="chapter && enrollment"
    :key="enrollment.id + ':' + chapter.id"
    :enrollment-id="enrollment.id"
    :chapter-id="chapter.id"
  />
</template>
