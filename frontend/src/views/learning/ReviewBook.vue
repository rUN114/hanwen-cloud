<script setup lang="ts">
import ListPager from "../../components/common/ListPager.vue";
import { ref, computed, onMounted } from "vue";
import { api } from "../../api/client";
import { useTask, time } from "../../composables/useTask";
import { allPages } from "../../utils/export";
import type { Enrollment } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
type Card = {
  id: string;
  courseId: string;
  quizId: string;
  prompt: string;
  questionType: string;
  wrongCount: number;
  reviewCount: number;
  correctStreak: number;
  mastered: boolean;
  nextReviewAt: string;
  version: number;
  previousChoice?: string;
  optionA?: string;
  optionB?: string;
  optionC?: string;
  optionD?: string;
};
type Result = Card & { correct: boolean; correctOption: string; explanation: string };
const rows = ref<Card[]>([]);
const page = ref(1);
const total = ref(0);
const state = ref("ALL");
const course = ref("");
const summary = ref({ total: 0, due: 0, mastered: 0 });
const current = ref<Card | null>(null);
const choice = ref<string[]>([]);
const result = ref<Result | null>(null);
const serverTime = ref("");
const { busy, error, run } = useTask();
const courses = ref<Enrollment[]>([]);
const courseName = (id: string) =>
  courses.value.find((x) => x.courseId === id)?.courseName || "原课程";
const options = computed(() =>
  current.value
    ? (["A", "B", "C", "D"] as const)
        .filter((k) => current.value![("option" + k) as keyof Card])
        .map((k) => ({ key: k, text: current.value![("option" + k) as keyof Card] }))
    : [],
);
const ready = computed(() => !!current.value && current.value.nextReviewAt <= serverTime.value);
async function fetchRows() {
  const p = await api<{
    items: Card[];
    total: number;
    summary: typeof summary.value;
    serverTime: string;
  }>(
    "/api/review-cards?page=" +
      page.value +
      "&size=10&state=" +
      state.value +
      (course.value ? "&courseId=" + encodeURIComponent(course.value) : ""),
  );
  rows.value = p.items;
  total.value = p.total;
  summary.value = p.summary;
  serverTime.value = p.serverTime;
}
function load() {
  run(fetchRows);
}
function filter() {
  page.value = 1;
  current.value = null;
  result.value = null;
  load();
}
function open(c: Card) {
  run(async () => {
    current.value = await api<Card>("/api/review-cards/" + c.id);
    choice.value = [];
    result.value = null;
  });
}
function pick(k: string) {
  if (busy.value || result.value) return;
  choice.value =
    current.value?.questionType === "MULTIPLE"
      ? choice.value.includes(k)
        ? choice.value.filter((x) => x !== k)
        : [...choice.value, k]
      : [k];
}
function submit() {
  run(async () => {
    result.value = await api<Result>("/api/review-cards/" + current.value!.id + "/answers", {
      method: "POST",
      body: { choice: [...choice.value].sort().join(""), expectedVersion: current.value!.version },
    });
    await fetchRows();
  });
}
onMounted(() =>
  run(async () => {
    courses.value = await allPages<Enrollment>("/api/enrollments/me");
    await fetchRows();
  }),
);
</script>
<template>
  <PageHeading
    title="温故册"
    description="把曾经答错的题，重新读懂。复习独立记录，原测验成绩保持。"
  >
    <el-button
      :disabled="busy"
      @click="load"
    >
      更新题册
    </el-button>
  </PageHeading>
  <section class="review-banner">
    <div>
      <span
        class="review-book-mark"
        aria-hidden="true"
      >
        温
        <br />
        故
      </span>
      <h2>温故而知新</h2>
      <p>解析开放后才收录；连续按计划答对三次，题签收入已掌握。</p>
    </div>
    <dl class="review-counts">
      <div>
        <dt>已收录</dt>
        <dd>{{ summary.total }}</dd>
      </div>
      <div>
        <dt>今日待复习</dt>
        <dd>{{ summary.due }}</dd>
      </div>
      <div>
        <dt>已掌握</dt>
        <dd>{{ summary.mastered }}</dd>
      </div>
    </dl>
  </section>
  <StatePanel
    :busy="busy && !rows.length"
    :error="error"
    @retry="load"
  />
  <section class="paper review-filters">
    <label>
      题签范围
      <select
        v-model="state"
        :disabled="busy"
        @change="filter"
      >
        <option value="ALL">全部错题</option>
        <option value="DUE">到期复习</option>
        <option value="MASTERED">已掌握</option>
      </select>
    </label>
    <label>
      课程
      <select
        v-model="course"
        aria-label="课程"
        :disabled="busy"
        @change="filter"
      >
        <option value="">全部已选课程</option>
        <option
          v-for="c in courses"
          :key="c.courseId"
          :value="c.courseId"
        >
          {{ c.courseName }}
        </option>
      </select>
    </label>
    <p>首次答对后间隔1天，再间隔3天、7天；答错后10分钟再温习。</p>
  </section>
  <div class="review-workspace">
    <section aria-label="错题题签">
      <article
        v-for="c in rows"
        :key="c.id"
        class="paper review-card"
        :class="{ chosen: current?.id === c.id }"
      >
        <div class="toolbar">
          <span class="small-seal">{{ c.mastered ? "熟" : "温" }}</span>
          <span class="field-hint">
            {{ courseName(c.courseId) }} ·
            {{
              c.questionType === "MULTIPLE"
                ? "多选"
                : c.questionType === "TRUE_FALSE"
                  ? "判断"
                  : "单选"
            }}
          </span>
        </div>
        <h2>{{ c.prompt }}</h2>
        <p>曾错 {{ c.wrongCount }} 次 · 已复习 {{ c.reviewCount }} 次</p>
        <p class="field-hint">下次 {{ time(c.nextReviewAt) }}</p>
        <el-button
          :disabled="busy"
          @click="open(c)"
        >
          展开题签
        </el-button>
      </article>
      <div
        v-if="!rows.length && !busy && !error"
        class="paper empty-state"
      >
        <span
          class="review-book-mark"
          aria-hidden="true"
        >
          新
        </span>
        <p>此范围没有题签。完成测验后，已开放解析的错题会自动收录。</p>
        <RouterLink to="/student/enrollments">回课程继续问学</RouterLink>
      </div>
      <ListPager
        v-if="total"
        v-model:page="page"
        :total="total"
        :page-size="10"
        :disabled="busy"
        @change="load"
      />
    </section>
    <article
      v-if="current"
      class="paper review-answer"
    >
      <h2>再读这一题</h2>
      <p class="plain-text">{{ current.prompt }}</p>
      <fieldset class="review-options">
        <legend>
          {{ current.questionType === "MULTIPLE" ? "可选择多个答案" : "选择一个答案" }}
        </legend>
        <button
          v-for="o in options"
          :key="o.key"
          type="button"
          :aria-pressed="choice.includes(o.key)"
          :disabled="busy || !!result"
          @click="pick(o.key)"
        >
          <span>{{ o.key }}</span>
          {{ o.text }}
        </button>
      </fieldset>
      <p
        v-if="!ready && !result"
        class="read-only-note"
      >
        尚未到计划复习时间，可回看题面；下次 {{ time(current.nextReviewAt) }}。
      </p>
      <el-button
        v-if="!result"
        type="primary"
        :disabled="!choice.length || !ready"
        :loading="busy"
        @click="submit"
      >
        核对本次温习
      </el-button>
      <section
        v-if="result"
        class="review-result"
        aria-live="polite"
      >
        <h3>{{ result.correct ? "本次答对，继续按计划温习" : "再读解析，稍后重新温习" }}</h3>
        <p>正确答案：{{ result.correctOption }}</p>
        <p class="plain-text">{{ result.explanation || "原题未填写解析。" }}</p>
        <p>计划连续答对 {{ result.correctStreak }} / 3 · 下次 {{ time(result.nextReviewAt) }}</p>
        <p>本次结果只计入温故记录，原测验分数不变。</p>
      </section>
    </article>
    <article
      v-else
      class="paper review-answer review-welcome"
    >
      <h2>翻开一枚题签</h2>
      <p>先重新作答，再核对原题解析。题面来自提交时的快照，老师之后修改题库也不会改写这份记录。</p>
    </article>
  </div>
</template>
