<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api";
import { useTask, time } from "../useTask";
import type { Task, Submission, Page } from "../models";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
import StatusTag from "../components/StatusTag.vue";
const id = String(useRoute().params.assignmentId),
  task = ref<Task | null>(null),
  rows = ref<Submission[]>([]),
  page = ref(1),
  total = ref(0),
  status = ref("");
const { busy, error, run } = useTask();
function load() {
  run(async () => {
    task.value = await api<Task>("/api/assignments/" + id);
    const p = await api<Page<Submission>>(
      "/api/assignments/" +
        id +
        "/submissions?page=" +
        page.value +
        "&size=10&status=" +
        status.value,
    );
    rows.value = p.items;
    total.value = p.total;
  });
}
onMounted(load);
</script>
<template>
  <PageHeading
    :title="(task?.title || '作业') + ' · 提交'"
    :back="
      task
        ? '/teacher/courses/' + task.courseId + '/assignments'
        : '/teacher/courses'
    "
    description="只显示已有提交，不统计未提交学生。"
  />
  <div class="toolbar">
    <el-select
      v-model="status"
      aria-label="评分状态"
      placeholder="全部提交"
      clearable
      @change="
        page = 1;
        load();
      "
      ><el-option label="待评分" value="SUBMITTED" /><el-option
        label="已评分"
        value="GRADED" /></el-select
    ><el-button :loading="busy" @click="load">刷新</el-button>
  </div>
  <StatePanel :error="error" @retry="load" />
  <section class="paper">
    <el-table :data="rows" v-loading="busy" empty-text="暂无符合条件的提交"
      ><el-table-column
        prop="studentName"
        label="学生"
        min-width="120"
      /><el-table-column label="提交时间（北京时间）" min-width="210"
        ><template #default="{ row }">{{
          time(row.submittedAt)
        }}</template></el-table-column
      ><el-table-column label="状态" min-width="110"
        ><template #default="{ row }"
          ><StatusTag :status="row.status" /></template></el-table-column
      ><el-table-column
        prop="version"
        label="当前版本"
        width="100"
      /><el-table-column label="操作" width="140"
        ><template #default="{ row }"
          ><RouterLink :to="'/teacher/submissions/' + row.id + '/grade'"
            ><el-button type="primary" size="small">{{
              row.status === "GRADED" ? "查看与更正" : "批改"
            }}</el-button></RouterLink
          ></template
        ></el-table-column
      ></el-table
    ><el-pagination
      v-model:current-page="page"
      :total="total"
      :page-size="10"
      layout="prev,pager,next,total"
      @current-change="load"
    />
  </section>
</template>
