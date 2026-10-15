<script setup lang="ts">
import { ref, onMounted } from "vue";
import { api, ApiError } from "../api";
import { useTask, time } from "../useTask";
import type { Enrollment, ProgressSummary, Page } from "../models";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
import StatusTag from "../components/StatusTag.vue";
const rows = ref<Enrollment[]>([]),
  page = ref(1),
  total = ref(0),
  progress = ref<Record<string, ProgressSummary>>({}),
  progressError = ref<Record<string, string>>({});
const { busy, error, run } = useTask();
async function loadProgress(id: string) {
  try {
    progress.value[id] = await api<ProgressSummary>(
      "/api/enrollments/" + id + "/progress",
    );
    delete progressError.value[id];
  } catch (e) {
    progressError.value[id] = e instanceof Error ? e.message : "进度暂不可用";
  }
}
function load() {
  run(async () => {
    const p = await api<Page<Enrollment>>(
      "/api/enrollments/me?page=" + page.value + "&size=10",
    );
    rows.value = p.items;
    total.value = p.total;
    progress.value = {};
    progressError.value = {};
    let next = 0;
    await Promise.all(
      Array.from({ length: Math.min(3, rows.value.length) }, async () => {
        while (next < rows.value.length) {
          const e = rows.value[next++]!;
          await loadProgress(e.id);
        }
      }),
    );
  });
}
onMounted(load);
</script>
<template>
  <PageHeading
    title="我的课程"
    description="已加入的课程与亲手记录的学习进度。"
  /><StatePanel
    :busy="busy && !rows.length"
    :error="error"
    :empty="!busy && !error && !rows.length"
    empty-text="尚未加入课程，去课程广场选一门吧。"
    @retry="load"
    ><RouterLink to="/student/courses"
      ><el-button type="primary">前往课程广场</el-button></RouterLink
    ></StatePanel
  >
  <section class="course-grid">
    <article v-for="e in rows" :key="e.id" class="course-card">
      <div class="book-cover">
        <h3>{{ e.courseName }}</h3>
      </div>
      <div class="course-card-body">
        <StatusTag :status="e.courseStatus" /><small
          >加入于 {{ time(e.joinedAt) }}</small
        ><template v-if="progress[e.id]"
          ><el-progress
            :percentage="Number(progress[e.id]!.percent)"
            :stroke-width="8"
            color="#234c41"
          /><small
            >完成 {{ progress[e.id]!.completedCount }} /
            {{ progress[e.id]!.total }} 章</small
          ></template
        >
        <div v-else-if="progressError[e.id]" class="draft-warning">
          {{ progressError[e.id]
          }}<el-button size="small" @click="loadProgress(e.id)"
            >重试进度</el-button
          >
        </div>
        <span v-else class="muted">进度加载中</span>
        <div class="course-card-actions">
          <RouterLink :to="'/student/courses/' + e.courseId"
            ><el-button type="primary">进入课程书案</el-button></RouterLink
          ><RouterLink :to="'/student/enrollments/' + e.id + '/assignments'"
            ><el-button>课程作业</el-button></RouterLink
          ><RouterLink :to="'/courses/' + e.courseId + '/community'"
            ><el-button>课堂交流</el-button></RouterLink
          >
        </div>
      </div>
    </article>
  </section>
  <el-pagination
    v-if="total"
    v-model:current-page="page"
    :total="total"
    :page-size="10"
    layout="prev,pager,next,total"
    @current-change="load"
  />
</template>
