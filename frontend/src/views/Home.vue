<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { session } from "../session";
import { api } from "../api";
import { useTask, time } from "../useTask";
import type { Page, Course, Enrollment, Task, Work } from "../models";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
const role = computed(() => session.value!.user.role),
  summary = ref<Record<string, number>>({}),
  courses = ref<Course[]>([]),
  enrollments = ref<Enrollment[]>([]),
  pending = ref<Task[]>([]),
  pendingTotal = ref(0),
  works = ref<Work[]>([]),
  inbox = ref<
    { id: string; taskTitle: string; studentId: string; submittedAt: string }[]
  >([]);
const { busy, error, run } = useTask();
const metrics = computed(() =>
  role.value === "STUDENT"
    ? [
        ["已选课程", summary.value.courses],
        ["完成章节", summary.value.completedChapters],
        ["学习札记", summary.value.notes],
        ["案头藏书", summary.value.favorites],
      ]
    : role.value === "TEACHER"
      ? [
          ["公开课程", summary.value.PUBLISHED],
          ["待编课程", summary.value.DRAFT],
          ["未解决问题", summary.value.questions],
          ["待评分作业", summary.value.pendingGrading],
        ]
      : [
          ["教师账号", summary.value.TEACHER],
          ["学生账号", summary.value.STUDENT],
          ["管理员", summary.value.ADMIN],
          ["禁用账号", summary.value.disabled],
        ],
);
function load() {
  run(async () => {
    if (role.value === "STUDENT") {
      const [s, e, p, w] = await Promise.all([
        api<Record<string, number>>("/api/enrollments/summary"),
        api<Page<Enrollment>>("/api/enrollments/me?size=5"),
        api<Page<Task>>("/api/assignments/pending?size=5"),
        api<Page<Work>>("/api/literature?size=3"),
      ]);
      summary.value = s;
      enrollments.value = e.items;
      pending.value = p.items;
      pendingTotal.value = p.total;
      works.value = w.items;
    } else if (role.value === "TEACHER") {
      const [s, a, c] = await Promise.all([
        api<Record<string, number>>("/api/teacher/courses/summary"),
        api<{ pendingGrading: number; items: typeof inbox.value }>(
          "/api/teacher/assignments/summary",
        ),
        api<Page<Course>>("/api/teacher/courses?size=5&status=PUBLISHED"),
      ]);
      summary.value = { ...s, pendingGrading: a.pendingGrading };
      inbox.value = a.items;
      courses.value = c.items;
    } else
      summary.value = await api<Record<string, number>>(
        "/api/admin/users/summary",
      );
  });
}
onMounted(load);
</script>
<template>
  <PageHeading
    :title="
      role === 'TEACHER'
        ? '先生案头'
        : role === 'ADMIN'
          ? '书院总览'
          : '我的书案'
    "
    description="读有所思，写有所获。"
  />
  <section class="desk-hero art-scene" :class="'desk-' + role.toLowerCase()">
    <div>
      <span class="eyebrow"
        >锦卷书院 ·
        {{
          role === "TEACHER" ? "授业" : role === "ADMIN" ? "院务" : "求学"
        }}</span
      >
      <h2>{{ session?.user.displayName }}，展卷见字</h2>
      <p>把片刻的领会写进札记，把尚未解开的疑问留给课堂。</p>
      <RouterLink
        :to="
          role === 'ADMIN'
            ? '/admin/users'
            : role === 'TEACHER'
              ? '/teacher/courses'
              : '/student/enrollments'
        "
        ><el-button type="primary">{{
          role === "ADMIN"
            ? "管理书院"
            : role === "TEACHER"
              ? "进入我的课程"
              : "继续学习"
        }}</el-button></RouterLink
      >
    </div>
    <div class="desk-couplet" aria-hidden="true">
      <span>一卷山河入心</span><span>半窗风月伴读</span
      ><b class="small-seal">锦卷</b>
    </div>
    <RouterLink class="scene-colophon" to="/paintings"><span aria-hidden="true">画</span>{{ role === 'STUDENT' ? '米友仁《云山图》' : 'AI界面插画' }} · 赏馆藏</RouterLink>
  </section>
  <StatePanel
    :busy="busy && !Object.keys(summary).length"
    :error="error"
    @retry="load"
  />
  <nav v-if="role !== 'ADMIN'" class="desk-shortcuts" aria-label="教学事务">
    <RouterLink to="/classes"
      ><span aria-hidden="true">舍</span>
      <div>
        <strong>同窗学舍</strong><small>班级与课程安排</small>
      </div></RouterLink
    >
    <RouterLink to="/agenda"
      ><span aria-hidden="true">历</span>
      <div>
        <strong>书院课历</strong><small>按日查看作业截止</small>
      </div></RouterLink
    >
    <RouterLink to="/inbox"
      ><span aria-hidden="true">笺</span>
      <div>
        <strong>来往信笺</strong><small>公告、答疑与批改消息</small>
      </div></RouterLink
    >
  </nav>
  <section
    v-if="Object.keys(summary).length"
    class="metric-grid"
    aria-label="我的数据"
  >
    <article v-for="(m, i) in metrics" :key="String(m[0])" class="metric-paper">
      <span class="metric-mark" aria-hidden="true">{{
        ["卷", "读", "记", "藏"][i]
      }}</span
      ><small>{{ m[0] }}</small
      ><strong>{{ m[1] }}</strong>
    </article>
  </section>
  <div v-if="!error" class="desk-grid">
    <section v-if="role === 'STUDENT'" class="paper">
      <div class="section-heading">
        <h2>
          尚待落笔 <small>{{ pendingTotal }} 项</small>
        </h2>
        <RouterLink to="/student/enrollments">查看课程</RouterLink>
      </div>
      <p v-if="!busy && !pending.length" class="muted">
        暂无未提交且仍开放的作业。
      </p>
      <article v-for="p in pending" :key="p.id" class="list-line">
        <div>
          <RouterLink :to="'/student/assignments/' + p.id + '/submit'">{{
            p.title
          }}</RouterLink
          ><small>{{
            p.deadlineAt ? "截止 " + time(p.deadlineAt) : "未设截止时间"
          }}</small>
        </div>
        <RouterLink :to="'/student/assignments/' + p.id + '/submit'"
          ><el-button size="small">作答</el-button></RouterLink
        >
      </article>
    </section>
    <section v-if="role === 'TEACHER'" class="paper">
      <div class="section-heading">
        <h2>待批作业</h2>
        <span class="field-hint">最早提交的 8 份</span>
      </div>
      <p v-if="!busy && !inbox.length" class="muted">暂无待评分的作业。</p>
      <p><RouterLink to="/teacher/practice-queue">前往文字题待阅，批阅填空与简答</RouterLink></p>
      <article v-for="s in inbox" :key="s.id" class="list-line">
        <div>
          <RouterLink :to="'/teacher/submissions/' + s.id + '/grade'">{{
            s.taskTitle
          }}</RouterLink
          ><small>学生 {{ s.studentId }} · {{ time(s.submittedAt) }}</small>
        </div>
        <RouterLink :to="'/teacher/submissions/' + s.id + '/grade'"
          ><el-button size="small">批阅</el-button></RouterLink
        >
      </article>
    </section>
    <section v-if="role !== 'ADMIN'" class="paper">
      <div class="section-heading">
        <h2>卷中近况</h2>
        <RouterLink
          :to="role === 'TEACHER' ? '/teacher/courses' : '/student/enrollments'"
          >全部课程</RouterLink
        >
      </div>
      <p v-if="!busy && !courses.length && !enrollments.length" class="muted">
        暂无课程。
      </p>
      <article v-for="c in courses" :key="c.id" class="list-line">
        <RouterLink :to="'/teacher/courses/' + c.id + '/analytics'">{{
          c.name
        }}</RouterLink
        ><RouterLink :to="'/courses/' + c.id + '/community'"
          >公告与答疑</RouterLink
        >
      </article>
      <article v-for="e in enrollments" :key="e.id" class="list-line">
        <RouterLink :to="'/student/courses/' + e.courseId">{{
          e.courseName
        }}</RouterLink
        ><RouterLink :to="'/courses/' + e.courseId + '/community'"
          >课堂交流</RouterLink
        >
      </article>
    </section>
    <section class="paper desk-links">
      <h2>书案捷径</h2>
      <template v-if="role === 'STUDENT'"
        ><RouterLink to="/student/notes"
          ><b>札</b
          ><span>学习札记<small>拾起读书时的灵光</small></span></RouterLink
        ><RouterLink to="/student/favorites"
          ><b>藏</b
          ><span>我的藏书<small>留住愿意重读的篇章</small></span></RouterLink
        ><RouterLink to="/student/grades"
          ><b>评</b
          ><span
            >作业与评语<small>把反馈带入下一次落笔</small></span
          ></RouterLink
        ></template
      ><template v-else-if="role === 'TEACHER'"
        ><RouterLink to="/teacher/courses/new"
          ><b>授</b
          ><span
            >编一门课程<small>章节、作业、交流相连</small></span
          ></RouterLink
        ><RouterLink to="/teacher/literature"
          ><b>辑</b
          ><span
            >编选诗文<small>注明来源，留下阅读提示</small></span
          ></RouterLink
        ></template
      ><RouterLink v-else to="/admin/users/new"
        ><b>名</b
        ><span>新增师生账号<small>按身份分配教学权限</small></span></RouterLink
      ><RouterLink to="/library"
        ><b>文</b
        ><span>诗文书库<small>循时代与体裁寻一篇佳作</small></span></RouterLink
      >
    </section>
    <section v-if="role === 'STUDENT' && works.length" class="paper">
      <div class="section-heading">
        <h2>案头选读</h2>
        <RouterLink to="/library">入书库</RouterLink>
      </div>
      <RouterLink
        v-for="w in works"
        :key="w.id"
        :to="'/library/' + w.id"
        class="list-line"
        ><span>{{ w.title }}</span
        ><small>{{ w.dynasty }} · {{ w.author }}</small></RouterLink
      >
    </section>
  </div>
</template>
