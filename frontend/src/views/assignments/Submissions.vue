<script setup lang="ts">
import ListPager from "../../components/common/ListPager.vue";
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { api } from "../../api/client";
import { useTask, time } from "../../composables/useTask";
import type { Task, Submission, Page } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
import StatusTag from "../../components/common/StatusTag.vue";
const id = String(useRoute().params.assignmentId);
const task = ref<Task | null>(null);
const rows = ref<Submission[]>([]);
const page = ref(1);
const total = ref(0);
const status = ref("");
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
    :back="task ? '/teacher/courses/' + task.courseId + '/assignments' : '/teacher/courses'"
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
    >
      <el-option
        label="待评分"
        value="SUBMITTED"
      />
      <el-option
        label="已评分"
        value="GRADED"
      />
    </el-select>
    <el-button
      :loading="busy"
      @click="load"
    >
      刷新
    </el-button>
  </div>
  <StatePanel
    :error="error"
    @retry="load"
  />
  <section class="paper">
    <el-table
      :data="rows"
      v-loading="busy"
      empty-text="暂无符合条件的提交"
    >
      <el-table-column
        prop="studentName"
        label="学生"
        min-width="120"
      />
      <el-table-column
        label="提交时间（北京时间）"
        min-width="210"
      >
        <template #default="{ row }">{{ time(row.submittedAt) }}</template>
      </el-table-column>
      <el-table-column
        label="状态"
        min-width="110"
      >
        <template #default="{ row }"><StatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column
        prop="version"
        label="当前版本"
        width="100"
      />
      <el-table-column
        label="操作"
        width="140"
      >
        <template #default="{ row }">
          <RouterLink :to="'/teacher/submissions/' + row.id + '/grade'">
            <el-button
              type="primary"
              size="small"
            >
              {{ row.status === "GRADED" ? "查看与更正" : "批改" }}
            </el-button>
          </RouterLink>
        </template>
      </el-table-column>
    </el-table>
    <ListPager
      v-model:page="page"
      :total="total"
      :page-size="10"
      @change="load"
    />
  </section>
</template>
