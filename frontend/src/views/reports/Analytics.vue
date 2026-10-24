<script setup lang="ts">
import ListPager from "../../components/common/ListPager.vue";
import { ref, watch, computed } from "vue";
import { useRoute } from "vue-router";
import { api } from "../../api/client";
import { allPages, csv, download } from "../../utils/export";
import { useTask, time } from "../../composables/useTask";
import type { Course, Learner, Page, AssignmentStats } from "../../types/models";
import LearningInsights from "../../components/learning/LearningInsights.vue";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
const route = useRoute();
const id = computed(() => String(route.params.courseId));
const course = ref<Course | null>(null);
const rows = ref<Learner[]>([]);
const stats = ref<AssignmentStats>({ tasks: [], learners: [] });
const page = ref(1);
const total = ref(0);
const { busy, error, run } = useTask();
const assignmentByStudent = computed(() =>
  Object.fromEntries(stats.value.learners.map((r) => [r.studentId, r])),
);
const submitted = computed(() => stats.value.tasks.reduce((a, t) => a + t.submitted, 0));
const graded = computed(() => stats.value.tasks.reduce((a, t) => a + t.graded, 0));
async function fetchRows() {
  const p = await api<Page<Learner>>(
    "/api/teacher/courses/" + id.value + "/learners?page=" + page.value + "&size=10",
  );
  rows.value = p.items;
  total.value = p.total;
}
function load() {
  run(async () => {
    [course.value, stats.value] = await Promise.all([
      api<Course>("/api/courses/" + id.value),
      api<AssignmentStats>("/api/teacher/courses/" + id.value + "/assignment-stats"),
    ]);
    await fetchRows();
  });
}
watch(
  () => route.params.courseId,
  () => {
    page.value = 1;
    load();
  },
  { immediate: true },
);
function exportCsv() {
  run(async () => {
    const [learners, current] = await Promise.all([
      allPages<Learner>("/api/teacher/courses/" + id.value + "/learners"),
      api<AssignmentStats>("/api/teacher/courses/" + id.value + "/assignment-stats"),
    ]);
    const map = Object.fromEntries(current.learners.map((s) => [s.studentId, s]));
    const data: unknown[][] = [
      [
        "学生编号",
        "显示名",
        "加入时间",
        "完成章节",
        "课程章节",
        "完成率(%)",
        "札记篇数",
        "已提交作业",
        "公开作业总数",
        "已评分作业",
        "已评分平均得分率(%)",
      ],
    ];
    for (const r of learners) {
      const a = map[r.studentId];
      data.push([
        r.studentId,
        r.displayName,
        time(r.joinedAt),
        r.completed,
        r.chapters,
        r.percent,
        r.noteCount,
        a?.submitted || 0,
        current.tasks.length,
        a?.graded || 0,
        a?.averagePercent ?? "未评分",
      ]);
    }
    download(csv(data), "课程学情_" + id.value + ".csv", "text/csv;charset=utf-8");
  });
}
</script>
<template>
  <RouterLink
    :to="'/courses/' + id + '/gradebook'"
    class="course-ribbon"
  >
    打开课程成绩册与总评规则
  </RouterLink>
  <PageHeading
    :title="(course?.name || '课程') + ' · 学情'"
    back="/teacher/courses"
    description="依据选课、章节完成与作业记录汇总。"
  >
    <RouterLink :to="'/courses/' + id + '/community'"><el-button>公告与答疑</el-button></RouterLink>
    <el-button
      :loading="busy"
      :disabled="!course"
      type="primary"
      @click="exportCsv"
    >
      导出全体学情 CSV
    </el-button>
  </PageHeading>
  <StatePanel
    :busy="busy && !course"
    :error="error"
    @retry="load"
  />
  <template v-if="course && !error">
    <section class="metric-grid">
      <article
        v-for="m in [
          ['已选学生', total],
          ['公开作业', stats.tasks.length],
          ['收到作答', submitted],
          ['完成评分', graded],
        ]"
        :key="String(m[0])"
        class="metric-paper"
      >
        <small>{{ m[0] }}</small>
        <strong>{{ m[1] }}</strong>
      </article>
    </section>
    <section class="paper">
      <h2>学生名册</h2>
      <p class="field-hint">
        完成率以本课程章节为分母；作业数含已关闭作业。均分率仅计算已评分作业。札记只显示篇数。
      </p>
      <el-table
        :data="rows"
        empty-text="尚无选课学生"
      >
        <el-table-column
          label="学生"
          min-width="140"
        >
          <template #default="{ row }">
            <b>{{ row.displayName }}</b>
            <small class="block">编号 {{ row.studentId }}</small>
          </template>
        </el-table-column>
        <el-table-column
          label="章节进度"
          min-width="170"
        >
          <template #default="{ row }">
            <el-progress
              :percentage="Number(row.percent)"
              color="#234c41"
            />
            <small>{{ row.completed }} / {{ row.chapters }} 章</small>
          </template>
        </el-table-column>
        <el-table-column
          prop="noteCount"
          label="札记篇数"
          width="100"
        />
        <el-table-column
          label="已交 / 作业"
          width="120"
        >
          <template #default="{ row }">
            {{ assignmentByStudent[row.studentId]?.submitted || 0 }} / {{ stats.tasks.length }}
          </template>
        </el-table-column>
        <el-table-column
          label="已评分"
          width="90"
        >
          <template #default="{ row }">
            {{ assignmentByStudent[row.studentId]?.graded || 0 }}
          </template>
        </el-table-column>
        <el-table-column
          label="均分率"
          width="110"
        >
          <template #default="{ row }">
            {{
              assignmentByStudent[row.studentId]?.averagePercent !== null &&
              assignmentByStudent[row.studentId]?.averagePercent !== undefined
                ? assignmentByStudent[row.studentId].averagePercent + "%"
                : "未评分"
            }}
          </template>
        </el-table-column>
      </el-table>
      <ListPager
        v-if="total"
        v-model:page="page"
        :total="total"
        :page-size="10"
        @change="run(fetchRows)"
      />
    </section>
    <section class="paper">
      <div class="section-heading">
        <h2>作业回览</h2>
        <RouterLink :to="'/teacher/courses/' + id + '/assignments'">管理作业</RouterLink>
      </div>
      <el-table
        :data="stats.tasks"
        empty-text="尚无公开作业"
      >
        <el-table-column
          prop="title"
          label="作业"
          min-width="200"
        />
        <el-table-column
          label="提交 / 选课"
          width="120"
        >
          <template #default="{ row }">{{ row.submitted }} / {{ total }}</template>
        </el-table-column>
        <el-table-column
          prop="graded"
          label="已评分"
          width="100"
        />
        <el-table-column
          label="平均得分 / 满分"
          width="160"
        >
          <template #default="{ row }">
            {{ row.averageScore ?? "未评分" }} / {{ row.maxScore }}
          </template>
        </el-table-column>
        <el-table-column
          label="批阅"
          width="110"
        >
          <template #default="{ row }">
            <RouterLink :to="'/teacher/assignments/' + row.id + '/submissions'">
              查看作答
            </RouterLink>
          </template>
        </el-table-column>
      </el-table>
    </section>
    <LearningInsights :course-id="id" />
  </template>
</template>
