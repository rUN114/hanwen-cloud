<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import { api } from "../api";
import { session } from "../session";
import { useTask, time } from "../useTask";
import type { AgendaTask } from "../models";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
const today = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Shanghai",
  }).format(new Date()),
  month = ref(today.slice(0, 7)),
  selected = ref(""),
  rows = ref<AgendaTask[]>([]),
  more = ref(false),
  teacher = computed(() => session.value?.user.role === "TEACHER");
const { busy, error, run } = useTask();
const cells = computed(() => {
  if (!/^\d{4}-\d{2}$/.test(month.value)) return [];
  const [year, m] = month.value.split("-").map(Number) as [number, number],
    first = (new Date(year, m - 1, 1).getDay() + 6) % 7,
    days = new Date(year, m, 0).getDate();
  return Array.from({ length: Math.ceil((first + days) / 7) * 7 }, (_, i) => {
    const day = i - first + 1,
      key = month.value + "-" + String(day).padStart(2, "0");
    return {
      day: day > 0 && day <= days ? day : 0,
      key,
      items: rows.value.filter((t) => t.deadlineAt?.slice(0, 10) === key),
    };
  });
});
const shown = computed(() =>
  selected.value
    ? rows.value.filter((t) => t.deadlineAt?.slice(0, 10) === selected.value)
    : rows.value,
);
function load() {
  run(async () => {
    const p = await api<{ items: AgendaTask[]; hasMore: boolean }>(
      "/api/assignments/agenda?month=" + month.value,
    );
    rows.value = p.items;
    more.value = p.hasMore;
  });
}
onMounted(load);
watch(month, () => {
  selected.value = "";
  load();
});
function step(n: number) {
  const [y, m] = month.value.split("-").map(Number) as [number, number];
  const d = new Date(y, m - 1 + n, 1);
  month.value =
    d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0");
}
function target(t: AgendaTask) {
  return teacher.value
    ? "/teacher/assignments/" + t.id + "/submissions"
    : t.submissionId
      ? "/student/submissions/" + t.submissionId
      : "/student/assignments/" + t.id + "/submit";
}
</script>
<template>
  <PageHeading
    title="书院课历"
    description="以真实作业截止时间安排读写，日期均为北京时间。"
    ><el-button
      @click="
        month = today.slice(0, 7);
        selected = today;
      "
      >回到今天</el-button
    ></PageHeading
  ><StatePanel :error="error" @retry="load" />
  <div class="agenda-layout">
    <section class="paper calendar-paper">
      <div class="calendar-heading">
        <el-button aria-label="上个月" @click="step(-1)">‹</el-button
        ><label class="calendar-month"
          ><span class="month-seal" aria-hidden="true">月</span
          ><input
            type="month"
            aria-label="选择月份"
            v-model="month"
            required /></label
        ><el-button aria-label="下个月" @click="step(1)">›</el-button>
      </div>
      <div class="calendar-grid">
        <div
          v-for="d in ['一', '二', '三', '四', '五', '六', '日']"
          :key="d"
          class="week-day"
        >
          {{ d }}
        </div>
        <template v-for="c in cells" :key="c.key"
          ><button
            v-if="c.day"
            class="calendar-day"
            :class="{
              today: c.key === today,
              selected: c.key === selected,
              'has-task': c.items.length,
            }"
            :aria-label="c.key + '，' + c.items.length + '项截止任务'"
            :aria-pressed="c.key === selected"
            @click="selected = c.key"
          >
            <b>{{ c.day }}</b
            ><small v-if="c.items.length">{{ c.items.length }} 项</small
            ><span v-if="c.key === today" class="today-mark" aria-hidden="true"
              >今</span
            >
          </button>
          <div v-else class="calendar-blank"></div
        ></template>
      </div>
      <p class="field-hint">课程作业截止日会留下朱签，点击日期展开任务。</p>
    </section>
    <aside class="paper agenda-tasks">
      <div class="section-heading">
        <h2>{{ selected || "本月" }}任务</h2>
        <el-button v-if="selected" text @click="selected = ''"
          >看整月</el-button
        >
      </div>
      <p v-if="more" class="read-only-note">
        任务超过200项，此处显示本月最早200项。
      </p>
      <article v-for="t in shown" :key="t.id" class="agenda-task">
        <span class="agenda-pin" aria-hidden="true"></span>
        <h3>
          <RouterLink :to="target(t)">{{ t.title }}</RouterLink>
        </h3>
        <time>{{ time(t.deadlineAt) }}</time>
        <p>
          {{
            teacher
              ? t.status === "CLOSED"
                ? "作业已关闭"
                : "待核对提交"
              : t.submissionStatus === "GRADED"
                ? "已批阅"
                : t.submissionStatus === "SUBMITTED"
                  ? "已提交"
                  : t.status === "CLOSED"
                    ? "已截止关闭"
                    : "待作答"
          }}
        </p>
      </article>
      <div v-if="!shown.length && !busy" class="agenda-rest">
        <span aria-hidden="true">闲</span>
        <p>本{{ selected ? "日" : "月" }}暂无有截止日期的任务。</p>
      </div>
      <RouterLink to="/inbox">查阅书院来信 →</RouterLink>
    </aside>
  </div>
</template>
