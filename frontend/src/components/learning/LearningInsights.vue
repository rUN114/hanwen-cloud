<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { api } from "../../api/client";
import { useTask, time } from "../../composables/useTask";
import { csv, download } from "../../utils/export";
import StatePanel from "../common/StatePanel.vue";
interface Practice {
  latestAttempts: number;
  pending: number;
  graded: number;
  averagePercent: string | null;
}
interface Learner {
  studentId: string;
  displayName: string;
  joinedAt: string;
  completed: number;
  chapters: number;
  lastRecordedAt: string;
  inactive14Days: boolean;
  classes: string[];
  practice: Practice;
}
interface Classroom {
  id: string;
  name: string;
  semester: string;
  status: string;
  students: number;
  gradedStudents: number;
  completionPercent: number | null;
  averagePercent: number | null;
}
interface Point {
  knowledgePoint: string;
  responses: number;
  students: number;
  earned: number;
  maximum: number;
  percent: string;
}
interface Week {
  week: string;
  submissions: number;
  students: number;
  graded: number;
  averagePercent: string | null;
}
interface Insights {
  courseId: string;
  classId: string | null;
  learners: Learner[];
  classes: Classroom[];
  knowledgePoints: Point[];
  weeks: Week[];
  calculatedAt: string;
  from: string;
  scope: string;
}
interface GradeItem {
  kind: string;
  title: string;
  status: string;
  percent: string | null;
}
interface GradeRow {
  studentId: string;
  provisional: string | null;
  pending: number;
  missing: number;
  items: GradeItem[];
}
interface Book {
  students: GradeRow[];
  calculatedAt: string;
}
const props = defineProps<{ courseId: string }>();
const data = ref<Insights | null>(null);
const book = ref<Book | null>(null);
const classId = ref("");
const onlyAttention = ref(true);
const view = ref<"points" | "classes" | "weeks">("points");
const { busy, error, run } = useTask();
let generation = 0;
const gradeMap = computed(() => new Map(book.value?.students.map((s) => [s.studentId, s]) || []));
function reasons(l: Learner) {
  const list: string[] = [];
  const g = gradeMap.value.get(l.studentId);
  if (g?.missing) list.push("截止后缺交 " + g.missing + " 项");
  if (l.practice.averagePercent !== null && Number(l.practice.averagePercent) < 60)
    list.push("已批练习均分率低于60%");
  if (l.inactive14Days) list.push("14天无新增学习记录");
  return list;
}
function awaiting(l: Learner) {
  return gradeMap.value.get(l.studentId)?.items.filter((i) => i.status === "PENDING").length || 0;
}
const shown = computed(
  () => data.value?.learners.filter((l) => !onlyAttention.value || reasons(l).length > 0) || [],
);
const count = computed(() => data.value?.learners.filter((l) => reasons(l).length > 0).length || 0);
const maximum = computed(() => Math.max(1, ...(data.value?.weeks.map((w) => w.submissions) || [])));
function load() {
  const current = ++generation;
  const course = props.courseId;
  const selected = classId.value;
  run(async () => {
    const [d, b] = await Promise.all([
      api<Insights>(
        "/api/teacher/courses/" +
          course +
          "/learning-insights" +
          (selected ? "?classId=" + encodeURIComponent(selected) : ""),
      ),
      api<Book>("/api/grades/courses/" + course),
    ]);
    if (current !== generation) return;
    data.value = d;
    book.value = b;
  }).finally(() => {
    if (current !== generation) load();
  });
}
watch(
  () => props.courseId,
  () => {
    generation++;
    data.value = null;
    book.value = null;
    classId.value = "";
    load();
  },
  { immediate: true },
);
function exportData() {
  if (!data.value) return;
  const d = data.value;
  download(
    csv([
      [
        "学生编号",
        "学生",
        "筛选学舍",
        "完成章节",
        "全部章节",
        "已批最近答卷数",
        "待批最近答卷数",
        "已批练习均分率(%)",
        "截止后缺交项",
        "待批项",
        "最近记录时间",
        "关注原因",
        "统计时间",
      ],
      ...d.learners.map((l) => [
        l.studentId,
        l.displayName,
        d.classId || "全课程",
        l.completed,
        l.chapters,
        l.practice.graded,
        l.practice.pending,
        l.practice.averagePercent ?? "未评分",
        gradeMap.value.get(l.studentId)?.missing || 0,
        awaiting(l),
        l.lastRecordedAt,
        reasons(l).join("；") || "无规则命中",
        d.calculatedAt,
      ]),
    ]),
    "课程关注名册_" + props.courseId + ".csv",
    "text/csv;charset=utf-8",
  );
}
function exportPoints() {
  if (!data.value) return;
  download(
    csv([
      ["知识点", "作答题项数", "作答学生数", "实得分", "满分", "得分率(%)", "统计时间"],
      ...data.value.knowledgePoints.map((p) => [
        p.knowledgePoint,
        p.responses,
        p.students,
        p.earned,
        p.maximum,
        p.percent,
        data.value!.calculatedAt,
      ]),
    ]),
    "知识点回览_" + props.courseId + ".csv",
    "text/csv;charset=utf-8",
  );
}
</script>
<template>
  <section class="insights-opening">
    <span
      class="insights-caption"
      aria-hidden="true"
    >
      观学札记
    </span>
    <div>
      <h2>看见进展，也看见待解之处</h2>
      <p>以答卷、章节记录和名册为据，给下一次讲解找落点。</p>
    </div>
  </section>
  <div class="insights-toolbar">
    <label>
      范围
      <select
        v-model="classId"
        aria-label="学情学舍"
        :disabled="busy"
        @change="load"
      >
        <option value="">全课程</option>
        <option
          v-for="c in data?.classes"
          :key="c.id"
          :value="c.id"
        >
          {{ c.name }} · {{ c.semester }}
        </option>
      </select>
    </label>
    <el-button
      :disabled="busy"
      @click="load"
    >
      重新统计
    </el-button>
    <small v-if="data">统计于 {{ time(data.calculatedAt) }}</small>
  </div>
  <StatePanel
    :busy="busy && !data"
    :error="error"
    @retry="load"
  />
  <template v-if="data">
    <p
      v-if="error"
      class="read-only-note"
    >
      当前展示上次成功统计（{{
        data.classId ? data.classes.find((c) => c.id === data!.classId)?.name : "全课程"
      }}）；本次筛选或更新尚未生效。
    </p>
    <nav
      class="insights-tabs"
      aria-label="学情观察"
    >
      <button
        v-for="[key, label] in [
          ['points', '知识点回览'],
          ['classes', '学舍对照'],
          ['weeks', '八周练习笺'],
        ]"
        :key="key"
        :aria-pressed="view === key"
        @click="view = key as typeof view"
      >
        {{ label }}
      </button>
    </nav>
    <section
      v-if="view === 'points'"
      class="paper insights-points"
    >
      <div class="section-heading">
        <h3>知识点回览</h3>
        <el-button
          :disabled="!data.knowledgePoints.length"
          @click="exportPoints"
        >
          导出知识点 CSV
        </el-button>
      </div>
      <p class="field-hint">
        按每人每卷最近一次已批阅答卷统计；最近一次待批的整卷暂不纳入。得分率以题项满分加权，题项数包括同一知识点下的不同题目。
      </p>
      <article
        v-for="p in data.knowledgePoints"
        :key="p.knowledgePoint"
      >
        <div>
          <strong>{{ p.knowledgePoint }}</strong>
          <small>{{ p.students }} 位同窗 · {{ p.responses }} 题项</small>
        </div>
        <div class="insights-point-bar">
          <span :style="{ width: Math.max(0, Math.min(100, Number(p.percent))) + '%' }"></span>
        </div>
        <div>
          <b>{{ p.percent }}%</b>
          <small>{{ p.earned }} / {{ p.maximum }} 分</small>
        </div>
      </article>
      <p v-if="!data.knowledgePoints.length">此范围尚无可统计的已批答卷。可在编题时标记知识点。</p>
    </section>
    <section
      v-else-if="view === 'classes'"
      class="paper insights-classes"
    >
      <h3>学舍对照</h3>
      <p class="field-hint">
        以下始终展示全课程各学舍，按当前在册成员统计。同一学生可属于多个学舍。平均得分率先算每位学生的已批最近答卷均分，再按有成绩学生平均；没有成绩保持未知。
      </p>
      <el-table
        :data="data.classes"
        empty-text="本课程尚无学舍"
      >
        <el-table-column
          prop="name"
          label="学舍"
          min-width="150"
        />
        <el-table-column
          prop="semester"
          label="学期"
          min-width="110"
        />
        <el-table-column
          prop="students"
          label="在册"
          width="80"
        />
        <el-table-column
          label="章节完成率"
          min-width="140"
        >
          <template #default="{ row }">
            {{ row.completionPercent === null ? "暂无章节或学生" : row.completionPercent + "%" }}
          </template>
        </el-table-column>
        <el-table-column
          label="有成绩 / 在册"
          width="130"
        >
          <template #default="{ row }">{{ row.gradedStudents }} / {{ row.students }}</template>
        </el-table-column>
        <el-table-column
          label="练习均分率"
          min-width="130"
        >
          <template #default="{ row }">
            {{ row.averagePercent === null ? "未评分" : row.averagePercent + "%" }}
          </template>
        </el-table-column>
      </el-table>
    </section>
    <section
      v-else
      class="paper insights-weeks"
    >
      <h3>八周练习笺</h3>
      <p class="field-hint">
        从 {{ time(data.from) }} 起的八个自然周（ISO
        周编号）。下表展示有交卷记录的周，包含重复尝试；均分率只含已批阅答卷，不能直接视为能力提升趋势。
      </p>
      <article
        v-for="w in data.weeks"
        :key="w.week"
      >
        <time>{{ w.week }}</time>
        <div class="insights-week-bar">
          <span :style="{ width: (w.submissions / maximum) * 100 + '%' }"></span>
        </div>
        <div>
          <b>{{ w.submissions }} 份交卷</b>
          <small>
            {{ w.students }} 人 · 已批 {{ w.graded }} 份 · 均分率
            {{ w.averagePercent === null ? "未评分" : w.averagePercent + "%" }}
          </small>
        </div>
      </article>
      <p v-if="!data.weeks.length">此范围近八周尚无练习交卷。</p>
    </section>
    <section class="paper insights-attention">
      <div class="section-heading">
        <div>
          <h3>
            需要留意的同窗
            <small>{{ count }} / {{ data.learners.length }}</small>
          </h3>
        </div>
        <el-button
          :disabled="!data.learners.length"
          @click="exportData"
        >
          导出关注名册 CSV
        </el-button>
      </div>
      <p class="field-hint">
        关注条件：截止后缺交、已批练习均分率低于60%、或14天无新增学习记录。规则只提供线索，由教师结合实际情况判断；待评分单独列出，不能作为学生低分或缺交证据。
      </p>
      <el-checkbox v-model="onlyAttention">只看命中关注条件的同窗</el-checkbox>
      <div class="insights-attention-list">
        <article
          v-for="l in shown"
          :key="l.studentId"
        >
          <div>
            <h4>{{ l.displayName }}</h4>
            <small>编号 {{ l.studentId }} · {{ l.completed }} / {{ l.chapters }} 章</small>
            <p>最近记录 {{ time(l.lastRecordedAt) }} · 等待批阅 {{ awaiting(l) }} 项</p>
          </div>
          <ul>
            <li
              v-for="r in reasons(l)"
              :key="r"
            >
              {{ r }}
            </li>
            <li
              v-if="!reasons(l).length"
              class="insights-no-risk"
            >
              暂无规则命中
            </li>
          </ul>
        </article>
        <p v-if="!shown.length">
          {{ onlyAttention ? "此范围暂无同窗命中关注条件。" : "此范围暂无同窗。" }}
        </p>
      </div>
    </section>
    <details class="insights-method">
      <summary>统计口径与记录范围</summary>
      <p>{{ data.scope }}</p>
      <p>
        缺交与待批按成绩册当前公开教学项计算。名册来自当前选课与班级成员，已移出班级的学生仍可保留课程学习资格。
      </p>
    </details>
  </template>
</template>
