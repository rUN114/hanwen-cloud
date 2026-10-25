<script setup lang="ts">
import ListPager from "../../components/common/ListPager.vue";
import { canTeach } from "../../utils/course-access";
import { ref, reactive, onMounted, computed, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../../api/client";
import { session } from "../../stores/session";
import { useTask, time } from "../../composables/useTask";
import type { Course, Quiz, Chapter, Page } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
import CourseLinks from "../../components/courses/CourseLinks.vue";
import StatusTag from "../../components/common/StatusTag.vue";
const id = String(useRoute().params.courseId);
const teacher = computed(() => session.value?.user.role === "TEACHER");
const route = useRoute();
const selectedChapter = ref(String(route.query.chapterId || ""));
const course = ref<Course | null>(null);
const rows = ref<Quiz[]>([]);
const chapters = ref<Chapter[]>([]);
const page = ref(1);
const total = ref(0);
const dialog = ref(false);
const results = ref<
  {
    id: string;
    quizId: string;
    displayName: string;
    score: number | null;
    gradingStatus: string;
    total: number;
    completedAt: string;
  }[]
>([]);
const resultsPage = ref(1);
const resultsTotal = ref(0);
const router = useRouter();
const form = reactive({ chapterId: "", title: "", instructions: "" });
const { busy, error, run } = useTask();
async function fetchResults() {
  if (!teacher.value) return;
  const p = await api<Page<(typeof results.value)[number]>>(
    "/api/teacher/courses/" + id + "/practice-results?page=" + resultsPage.value,
  );
  results.value = p.items;
  resultsTotal.value = p.total;
}
async function fetchRows() {
  const p = await api<Page<Quiz>>(
    "/api/courses/" +
      id +
      "/quizzes?page=" +
      page.value +
      (selectedChapter.value ? "&chapterId=" + encodeURIComponent(selectedChapter.value) : ""),
  );
  rows.value = p.items;
  total.value = p.total;
}
function load() {
  run(async () => {
    course.value = await api<Course>("/api/courses/" + id);
    chapters.value = await api<Chapter[]>("/api/courses/" + id + "/chapters");
    await fetchRows();
    await fetchResults();
  });
}
onMounted(load);
watch(
  () => route.query.chapterId,
  () => {
    selectedChapter.value = String(route.query.chapterId || "");
    page.value = 1;
    load();
  },
);
function create() {
  run(async () => {
    const q = await api<Quiz>("/api/teacher/courses/" + id + "/quizzes", {
      method: "POST",
      body: form,
    });
    await router.push("/teacher/quizzes/" + q.id);
  });
}
</script>
<template>
  <PageHeading
    :title="course?.name || '章节练习'"
    back="/classes"
    description="从字句出发，在小题中检验理解。"
  >
    <el-button
      v-if="teacher && canTeach(course) && course?.status !== 'ARCHIVED'"
      @click="dialog = true"
    >
      拟一份练习
    </el-button>
  </PageHeading>
  <CourseLinks
    v-if="course"
    :course-id="id"
    active="practice"
  />
  <div
    v-if="selectedChapter"
    class="read-only-note"
  >
    正在查看{{ chapters.find((c) => c.id === selectedChapter)?.title || "所选章节" }}的练习。
    <el-button @click="router.replace({ query: {} })">查看全课程练习</el-button>
  </div>
  <div class="practice-banner">
    <div>
      <span class="eyebrow">读 · 思 · 练</span>
      <h2>读后有问，问后再读</h2>
      <p>
        单选辨意，多选寻证，判断明理；填空忆句，简答析文。各卷设置分值、时限和尝试机会，文字题交由教师批阅。
      </p>
    </div>
    <span
      class="practice-scroll"
      aria-hidden="true"
    >
      温
      <br />
      故
      <br />
      知
      <br />
      新
    </span>
  </div>
  <StatePanel
    :busy="busy && !course"
    :error="error"
    :empty="!busy && !error && !rows.length"
    empty-text="本课程尚无章节练习"
    @retry="load"
  />
  <section class="quiz-grid">
    <article
      v-for="(q, i) in rows"
      :key="q.id"
      class="quiz-cover"
    >
      <span
        class="quiz-ribbon"
        aria-hidden="true"
      >
        练
      </span>
      <small>{{ chapters.find((c) => c.id === q.chapterId)?.title || "章节练习" }}</small>
      <h2>{{ q.title }}</h2>
      <p>{{ q.instructions || "逐题作答，以文本为依据。" }}</p>
      <div>
        <StatusTag :status="q.status" />
        <span>{{ q.questionCount }} 题 / {{ q.totalPoints }} 分</span>
      </div>
      <p class="field-hint">
        {{ q.timeLimitMinutes ? q.timeLimitMinutes + " 分钟" : "不限时" }} · 最多
        {{ q.maxAttempts }} 次{{ q.closesAt ? " · 截止 " + time(q.closesAt) : "" }}
      </p>
      <RouterLink :to="teacher ? '/teacher/quizzes/' + q.id : '/practice/' + q.id">
        <el-button>
          {{ teacher ? (q.status === "DRAFT" ? "编写题目" : "查看练习") : "展开练习" }}
        </el-button>
      </RouterLink>
    </article>
  </section>
  <ListPager
    v-if="total"
    v-model:page="page"
    :total="total"
    :page-size="12"
    @change="() => run(fetchRows)"
  />
  <section
    v-if="teacher"
    class="paper practice-records"
  >
    <h2>同窗练习记录</h2>
    <div
      class="practice-record-row"
      v-for="r in results"
      :key="r.id"
    >
      <strong>{{ r.displayName }}</strong>
      <span>练习编号 {{ r.quizId }}</span>
      <b>{{ r.gradingStatus === "PENDING" ? "待人工批阅" : r.score + " / " + r.total + " 分" }}</b>
      <RouterLink :to="'/teacher/practice-attempts/' + r.id">
        {{ r.gradingStatus === "PENDING" ? "批阅答卷" : "回看评分" }}
      </RouterLink>
      <small>{{ time(r.completedAt) }}</small>
    </div>
    <p v-if="!results.length">等待同窗提交练习。</p>
    <ListPager
      v-if="resultsTotal"
      v-model:page="resultsPage"
      :total="resultsTotal"
      :page-size="20"
      @change="() => run(fetchResults)"
    />
  </section>
  <el-dialog
    v-model="dialog"
    title="拟写章节练习"
    width="min(560px,92vw)"
  >
    <form @submit.prevent="create">
      <div class="form-field">
        <label for="quiz-title">练习标题</label>
        <el-input
          id="quiz-title"
          v-model="form.title"
          required
          maxlength="100"
        />
      </div>
      <div class="form-field">
        <label for="quiz-chapter">所属章节</label>
        <select
          id="quiz-chapter"
          class="native-select"
          v-model="form.chapterId"
          required
        >
          <option value="">请选择章节</option>
          <option
            v-for="c in chapters"
            :key="c.id"
            :value="c.id"
          >
            {{ c.title }}
          </option>
        </select>
      </div>
      <div class="form-field">
        <label for="quiz-instructions">练习说明</label>
        <el-input
          id="quiz-instructions"
          v-model="form.instructions"
          type="textarea"
          maxlength="2000"
        />
      </div>
      <StatePanel
        :error="error"
        @retry="create"
      />
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
      >
        创建草稿
      </el-button>
    </form>
  </el-dialog>
</template>
