<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from "vue";
import { api } from "../api";
import { session } from "../session";
import { allPages } from "../export";
import { useTask, time } from "../useTask";
import type { InboxEvent, GradeBrief } from "../models";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
const events = ref<InboxEvent[]>([]),
  readKeys = ref(new Set<string>()),
  filter = ref("UNREAD"),
  more = ref(false),
  teacher = computed(() => session.value?.user.role === "TEACHER");
const { busy, error, run } = useTask();
const autoUpdate=ref(true),updatedAt=ref<string|null>(null);let timer:ReturnType<typeof setInterval>|undefined;
const kindNames: Record<string, string> = {
  NOTICE: "课程公告",
  REPLY: "答疑回复",
  QUESTION: "同窗提问",
  GRADE: "批阅反馈",
  SUBMISSION: "待批答卷",
  DEADLINE:"截止提醒",NUDGE:"先生叮嘱",
};
const kindMarks: Record<string, string> = {
  NOTICE: "告",
  REPLY: "答",
  QUESTION: "问",
  GRADE: "阅",
  SUBMISSION: "卷",
  DEADLINE:"期",NUDGE:"嘱",
};
const unread = computed(
  () => events.value.filter((e) => !readKeys.value.has(e.eventKey)).length,
);
const shown = computed(() =>
  events.value.filter(
    (e) =>
      filter.value === "ALL" ||
      (filter.value === "UNREAD"
        ? !readKeys.value.has(e.eventKey)
        : readKeys.value.has(e.eventKey)),
  ),
);
async function receipts() {
  const rows = await allPages<{ eventKey: string }>("/api/inbox/receipts");
  readKeys.value = new Set(rows.map((r) => r.eventKey));
}
function load() {
  run(async () => {
    const feed = await api<{ items: InboxEvent[]; hasMore: boolean }>(
      "/api/courses/inbox",
    );
    let extra: InboxEvent[] = [];
    let extraMore=false;
    if (teacher.value) {
      const p = await api<{
        items: { id: string; taskTitle: string; submittedAt: string }[];
      }>("/api/teacher/assignments/summary");
      extra = p.items.map((r) => ({
        eventKey: "SUBMISSION:" + r.id + ":" + r.submittedAt,
        kind: "SUBMISSION",
        title: r.taskTitle,
        eventAt: r.submittedAt,
        url: "/teacher/submissions/" + r.id + "/grade",
      }));
    } else {
      const grades = await api<{items:GradeBrief[];total:number}>("/api/grades/me?page=1&size=100");extraMore=grades.total>100;
      extra = grades.items
        .filter((r) => r.gradedAt)
        .map((r) => ({
          eventKey: "GRADE:" + r.submissionId + ":" + r.gradedAt,
          kind: "GRADE",
          title: r.taskTitle,
          eventAt: r.gradedAt!,
          url: "/student/submissions/" + r.submissionId,
        }));
    }
    const reminders=teacher.value?{items:[] as InboxEvent[],hasMore:false}:await api<{items:InboxEvent[];hasMore:boolean}>('/api/assignments/reminders');
    const nextEvents = [...feed.items, ...extra, ...reminders.items].sort((a, b) =>
      b.eventAt.localeCompare(a.eventAt),
    );
    await receipts();
    events.value=nextEvents;more.value=feed.hasMore||reminders.hasMore||extraMore;updatedAt.value=new Date().toISOString();
  });
}
function visibleUpdate(){if(autoUpdate.value&&document.visibilityState==='visible'&&!busy.value&&session.value)load();}
onMounted(()=>{load();timer=setInterval(visibleUpdate,30000);document.addEventListener('visibilitychange',visibleUpdate);});
onUnmounted(()=>{if(timer)clearInterval(timer);document.removeEventListener('visibilitychange',visibleUpdate);});
function mark(e: InboxEvent) {
  run(async () => {
    await api("/api/inbox/receipts", {
      method: "POST",
      body: { keys: [e.eventKey] },
    });
    readKeys.value.add(e.eventKey);
  });
}
function markAll() {
  run(async () => {
    const keys = events.value
      .filter((e) => !readKeys.value.has(e.eventKey))
      .map((e) => e.eventKey);
    for (let i = 0; i < keys.length; i += 100)
      await api("/api/inbox/receipts", {
        method: "POST",
        body: { keys: keys.slice(i, i + 100) },
      });
    await receipts();
  });
}
</script>
<template>
  <PageHeading title="书院来信" description="公告、问答与批阅，在这里收拢。"
    ><el-button @click="load">刷新来信</el-button
    ><el-button :disabled="!unread" @click="markAll"
      >全部标为已读</el-button
    ></PageHeading
  >
  <p v-if="teacher"><RouterLink to="/teacher/practice-queue">查看文字题待批答卷</RouterLink></p>
  <section class="inbox-banner art-scene">
    <span class="letter-seal" aria-hidden="true">信</span>
    <div>
      <h2>
        {{ unread ? unread + " 封新信，等你展开" : "案头来信，已悉心收好" }}
      </h2>
      <p>课程公告、课堂答疑与作业评语，见字如晤。未读来信和已读书札都收在这里。</p>
    </div>
    <RouterLink to="/agenda">查看课历 →</RouterLink>
    <RouterLink class="scene-colophon" to="/paintings"><span aria-hidden="true">画</span>AI界面插画 · 赏馆藏</RouterLink>
  </section>
  <StatePanel :busy="busy && !events.length" :error="error" @retry="load" />
  <p class="inbox-update-note"><label><input v-model="autoUpdate" type="checkbox" aria-label="自动更新来信"> 自动更新来信</label> · {{updatedAt?'最近成功更新 '+time(updatedAt):'正在核对来信'}}<span v-if="error&&events.length">；更新失败，已保留上次内容。</span></p>
  <div class="reading-tabs" role="group" aria-label="筛选来信">
    <button
      v-for="(label, key) in { UNREAD: '未读', ALL: '全部', READ: '已读' }"
      :key="key"
      :class="{ active: filter === key }"
      @click="filter = key"
    >
      {{ label }}
    </button>
  </div>
  <p v-if="more" class="read-only-note">
    此处显示最近公告、回复和成绩各100条；更早记录可在对应课程或成绩页查看。
  </p>
  <p v-if="teacher" class="field-hint">
    待批阅来信显示最早的8份答卷，完整名单在课程作业中查看。
  </p>
  <section class="letters-list">
    <article
      v-for="e in shown"
      :key="e.eventKey"
      class="letter-card"
      :class="{ unread: !readKeys.has(e.eventKey) }"
    >
      <span class="letter-kind" aria-hidden="true">{{
        kindMarks[e.kind]
      }}</span>
      <div>
        <small
          >{{ kindNames[e.kind]
          }}<span v-if="e.courseName"> · {{ e.courseName }}</span></small
        >
        <h2>
          <RouterLink :to="e.url">{{ e.title }}</RouterLink>
        </h2>
        <time>{{ time(e.eventAt) }}</time>
      </div>
      <div class="letter-actions">
        <span>{{ readKeys.has(e.eventKey) ? "已读" : "新信" }}</span
        ><el-button v-if="!readKeys.has(e.eventKey)" @click="mark(e)"
          >标为已读</el-button
        >
      </div>
    </article>
    <div v-if="!shown.length && !busy" class="paper empty-state">
      <span class="letter-seal" aria-hidden="true">静</span>
      <p>暂无此类来信。</p>
    </div>
  </section>
</template>
