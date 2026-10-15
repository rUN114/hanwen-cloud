<script setup lang="ts">
import { ref, onMounted } from "vue";
import { api } from "../api";
import { useTask, time } from "../useTask";
import type { GradeBrief, Page, Enrollment } from "../models";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
import StatusTag from "../components/StatusTag.vue";
const rows = ref<GradeBrief[]>([]),
  page = ref(1),
  total = ref(0);
const { busy, error, run } = useTask();
function load() {
  run(async () => {
    const p = await api<Page<GradeBrief>>(
      "/api/grades/me?page=" + page.value + "&size=10",
    );
    rows.value = p.items;
    total.value = p.total;
  });
}
onMounted(load);
</script>
<template>
  <PageHeading
    title="我的成绩"
    description="展示本人已有提交与当前评分；待评分不计作0分。"
  /><StatePanel :error="error" @retry="load" />
  <section class="paper">
    <el-table :data="rows" v-loading="busy" empty-text="尚无已提交作业"
      ><el-table-column
        prop="taskTitle"
        label="作业"
        min-width="220"
      /><el-table-column label="状态" min-width="100"
        ><template #default="{ row }"
          ><StatusTag :status="row.status" /></template></el-table-column
      ><el-table-column label="分数 / 满分" min-width="140"
        ><template #default="{ row }">{{
          row.score === null ? "待评分" : row.score + " / " + row.maxScore
        }}</template></el-table-column
      ><el-table-column label="评分时间" min-width="190"
        ><template #default="{ row }">{{
          row.gradedAt ? time(row.gradedAt) : "待评分"
        }}</template></el-table-column
      ><el-table-column label="操作" width="120"
        ><template #default="{ row }"
          ><RouterLink :to="'/student/submissions/' + row.submissionId"
            ><el-button size="small">查看反馈</el-button></RouterLink
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
