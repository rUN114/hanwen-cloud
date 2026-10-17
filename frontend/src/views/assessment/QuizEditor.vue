<script setup lang="ts">
import { canTeach, courseRoles } from "../../utils/course-access";
import { ref, reactive, onMounted, computed } from "vue";
import { useRoute } from "vue-router";
import { api } from "../../api/client";
import { useTask, time } from "../../composables/useTask";
import type { Quiz, QuizQuestion, Course, BankQuestion, Page } from "../../types/models";
import {
  emptyQuestion,
  questionDraft,
  typeNames,
  options,
  manual,
  localInput,
  offsetInput,
} from "../../utils/question-ui";
import { ElMessage, ElMessageBox } from "element-plus";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
import StatusTag from "../../components/common/StatusTag.vue";
import QuestionFields from "../../components/assessment/QuestionFields.vue";
const id = String(useRoute().params.id);
const quiz = ref<Quiz | null>(null);
const course = ref<Course | null>(null);
const editing = ref(false);
const editingHeader = ref(false);
const editId = ref("");
const sortNo = ref(1);
const form = ref(emptyQuestion());
const header = reactive({
  title: "",
  instructions: "",
  opensAt: "",
  closesAt: "",
  timeLimitMinutes: 0,
  maxAttempts: 1,
});
const picker = ref(false);
const bankRows = ref<BankQuestion[]>([]);
const bankPage = ref(1);
const bankTotal = ref(0);
const bankFilter = reactive({ keyword: "", type: "" });
const editable = computed(
  () =>
    canTeach(course.value) && quiz.value?.status === "DRAFT" && course.value?.status !== "ARCHIVED",
);
const { busy, error, run } = useTask();
async function fetchData() {
  quiz.value = await api<Quiz>("/api/quizzes/" + id);
  course.value = await api<Course>("/api/courses/" + quiz.value.courseId);
  Object.assign(header, {
    title: quiz.value.title,
    instructions: quiz.value.instructions || "",
    opensAt: localInput(quiz.value.opensAt),
    closesAt: localInput(quiz.value.closesAt),
    timeLimitMinutes: quiz.value.timeLimitMinutes,
    maxAttempts: quiz.value.maxAttempts,
  });
}
function load() {
  run(fetchData);
}
onMounted(load);
function edit(q?: QuizQuestion) {
  editId.value = q?.id || "";
  sortNo.value =
    q?.sortNo || Math.max(0, ...(quiz.value?.questions || []).map((x) => x.sortNo)) + 1;
  form.value = q ? questionDraft(q) : emptyQuestion();
  editing.value = true;
}
function saveQuestion() {
  run(async () => {
    quiz.value = await api<Quiz>(
      "/api/teacher/quizzes/" + id + "/questions" + (editId.value ? "/" + editId.value : ""),
      {
        method: editId.value ? "PUT" : "POST",
        body: { ...form.value, sortNo: sortNo.value, expectedVersion: quiz.value!.version },
      },
    );
    editing.value = false;
    ElMessage.success("题目已保存");
  });
}
function remove(q: QuizQuestion) {
  run(async () => {
    await ElMessageBox.confirm("删除这道草稿题目？", "删除题目");
    await api(
      "/api/teacher/quizzes/" +
        id +
        "/questions/" +
        q.id +
        "?expectedVersion=" +
        quiz.value!.version,
      { method: "DELETE" },
    );
    await fetchData();
  });
}
function saveHeader() {
  run(async () => {
    await api("/api/teacher/courses/" + quiz.value!.courseId + "/quizzes/" + id, {
      method: "PUT",
      body: {
        ...header,
        opensAt: offsetInput(header.opensAt),
        closesAt: offsetInput(header.closesAt),
        chapterId: quiz.value!.chapterId,
        expectedVersion: quiz.value!.version,
      },
    });
    editingHeader.value = false;
    await fetchData();
  });
}
function state(status: string) {
  run(async () => {
    await ElMessageBox.confirm(
      status === "PUBLISHED"
        ? "发布后题目、分值和测验规则冻结。确认时间与尝试次数都正确。"
        : "归档后停止新作答，保留已有答卷。",
      "确认状态",
    );
    await api("/api/teacher/quizzes/" + id + "/status", {
      method: "PUT",
      body: { status, expectedVersion: quiz.value!.version },
    });
    await fetchData();
  });
}
async function fetchBank() {
  const p = await api<Page<BankQuestion>>(
    "/api/teacher/question-bank?" +
      new URLSearchParams({ ...bankFilter, page: String(bankPage.value) }),
  );
  bankRows.value = p.items;
  bankTotal.value = p.total;
}
function openBank() {
  picker.value = true;
  run(fetchBank);
}
function importQuestion(q: BankQuestion) {
  run(async () => {
    quiz.value = await api<Quiz>("/api/teacher/quizzes/" + id + "/from-bank", {
      method: "POST",
      body: { bankId: q.id, bankVersion: q.version, expectedVersion: quiz.value!.version },
    });
    ElMessage.success("已加入试卷，题目副本与题库修订 " + q.version + " 对应");
  });
}
</script>
<template>
  <PageHeading
    :title="quiz?.title || '拟题案'"
    :back="quiz ? '/courses/' + quiz.courseId + '/quizzes' : '/teacher/courses'"
  >
    <el-button
      v-if="editable"
      @click="editingHeader = !editingHeader"
    >
      测验规则
    </el-button>
    <el-button
      v-if="editable"
      @click="edit()"
    >
      添加题目
    </el-button>
    <el-button
      v-if="editable"
      @click="openBank"
    >
      从题库组卷
    </el-button>
    <el-button
      v-if="editable && course?.status === 'PUBLISHED'"
      type="primary"
      @click="state('PUBLISHED')"
    >
      发布练习
    </el-button>
    <el-button
      v-if="canTeach(course) && quiz?.status === 'PUBLISHED' && course?.status === 'PUBLISHED'"
      @click="state('ARCHIVED')"
    >
      归档
    </el-button>
  </PageHeading>
  <StatePanel
    :busy="busy && !quiz"
    :error="error"
    @retry="load"
  />
  <template v-if="quiz">
    <section class="paper quiz-instructions">
      <StatusTag :status="quiz.status" />
      <h2>试卷提要</h2>
      <p>{{ quiz.instructions || "暂无说明" }}</p>
      <div class="quiz-rules">
        <span>{{ quiz.questionCount }} 题 / {{ quiz.totalPoints }} 分</span>
        <span>{{ quiz.timeLimitMinutes ? quiz.timeLimitMinutes + " 分钟" : "不限时" }}</span>
        <span>最多 {{ quiz.maxAttempts }} 次</span>
      </div>
      <p class="field-hint">
        开放：{{ quiz.opensAt ? time(quiz.opensAt) : "发布后" }}；截止：{{
          quiz.closesAt ? time(quiz.closesAt) : "不设截止"
        }}。每次完整作答留存独立答卷，主观题批阅后参与课程总评。
      </p>
      <p class="field-hint">
        多选全对得分，少选、多选不得分；填空与简答须人工批阅。解析在尝试次数用完、截止或归档后开放；超时作答消耗一次机会，不生成已提交成绩。
      </p>
    </section>
    <form
      v-if="editingHeader && editable"
      class="paper form-paper"
      @submit.prevent="saveHeader"
    >
      <div class="form-field">
        <label for="edit-quiz-title">练习标题</label>
        <el-input
          id="edit-quiz-title"
          v-model="header.title"
          maxlength="100"
          required
        />
      </div>
      <div class="form-field">
        <label for="edit-quiz-instructions">说明</label>
        <el-input
          id="edit-quiz-instructions"
          v-model="header.instructions"
          type="textarea"
          maxlength="2000"
        />
      </div>
      <div class="form-row">
        <div class="form-field">
          <label for="quiz-opens">开放时间（本地时间，可留空）</label>
          <input
            id="quiz-opens"
            class="native-select"
            type="datetime-local"
            v-model="header.opensAt"
          />
        </div>
        <div class="form-field">
          <label for="quiz-closes">截止时间（本地时间，可留空）</label>
          <input
            id="quiz-closes"
            class="native-select"
            type="datetime-local"
            v-model="header.closesAt"
          />
        </div>
      </div>
      <div class="form-row">
        <div class="form-field">
          <label for="quiz-limit">限时分钟（0 为不限时）</label>
          <el-input-number
            id="quiz-limit"
            v-model="header.timeLimitMinutes"
            :min="0"
            :max="180"
            :precision="0"
          />
        </div>
        <div class="form-field">
          <label for="quiz-attempts">最多尝试次数</label>
          <el-input-number
            id="quiz-attempts"
            v-model="header.maxAttempts"
            :min="1"
            :max="5"
            :precision="0"
          />
        </div>
      </div>
      <el-button
        native-type="submit"
        type="primary"
        :loading="busy"
      >
        保存规则
      </el-button>
    </form>
    <article
      v-for="q in quiz.questions"
      :key="q.id"
      class="paper question-manuscript"
    >
      <span class="question-tab">
        第 {{ q.sortNo }} 题 · {{ typeNames[q.questionType] }} · {{ q.points }} 分
      </span>
      <h2>{{ q.prompt }}</h2>
      <ol class="answer-options">
        <li
          v-for="o in options(q)"
          :key="o.key"
          :class="{ 'correct-option': q.correctOption?.includes(o.key) }"
        >
          <b>{{ o.key }}</b>
          <span>{{ o.text }}</span>
        </li>
      </ol>
      <div
        v-if="manual(q.questionType)"
        class="written-reference"
      >
        <strong>参考答案 · 人工批阅</strong>
        <p class="plain-text">{{ q.correctOption }}</p>
      </div>
      <p
        v-if="q.knowledgePoint"
        class="field-hint"
      >
        知识点：{{ q.knowledgePoint }}
      </p>
      <div class="question-explanation">
        <strong>解析</strong>
        <p>{{ q.explanation || "未填写解析" }}</p>
      </div>
      <small
        v-if="q.bankId"
        class="field-hint"
      >
        来源题笺 {{ q.bankId }}，修订 {{ q.bankVersion }}；当前为试卷中的独立副本。
      </small>
      <div
        v-if="editable"
        class="course-nav-actions"
      >
        <el-button @click="edit(q)">编辑</el-button>
        <el-button @click="remove(q)">删除</el-button>
      </div>
    </article>
    <p
      v-if="!quiz.questions?.length"
      class="paper"
    >
      尚无题目，可从问学题笺选入，或直接编写。
    </p>
  </template>
  <el-dialog
    v-model="editing"
    :title="editId ? '修改题目' : '添加题目'"
    width="min(680px,94vw)"
  >
    <form @submit.prevent="saveQuestion">
      <QuestionFields
        v-model="form"
        prefix="quiz-question"
        knowledge-field
      />
      <div class="form-field">
        <label for="question-sort">排序号</label>
        <el-input-number
          id="question-sort"
          v-model="sortNo"
          :min="1"
          :max="100000"
          :precision="0"
        />
      </div>
      <StatePanel
        :error="error"
        @retry="saveQuestion"
      />
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
      >
        保存题目
      </el-button>
    </form>
  </el-dialog>
  <el-dialog
    v-model="picker"
    title="从问学题笺组卷"
    width="min(840px,94vw)"
  >
    <p>
      已选入 {{ quiz?.questionCount }} 题，共 {{ quiz?.totalPoints }} 分。每次选入保存一份副本。
    </p>
    <form
      class="bank-picker-filter"
      @submit.prevent="
        bankPage = 1;
        run(fetchBank);
      "
    >
      <el-input
        aria-label="题库关键词"
        v-model="bankFilter.keyword"
        placeholder="检索题笺"
      />
      <select
        class="native-select"
        aria-label="题库题型"
        v-model="bankFilter.type"
      >
        <option value="">全部题型</option>
        <option
          v-for="(name, key) in typeNames"
          :key="key"
          :value="key"
        >
          {{ name }}
        </option>
      </select>
      <el-button native-type="submit">查找</el-button>
    </form>
    <StatePanel
      :error="error"
      @retry="() => run(fetchBank)"
    />
    <article
      v-for="q in bankRows"
      :key="q.id"
      class="bank-picker-row"
    >
      <div>
        <small>
          {{ typeNames[q.questionType] }} · {{ q.points }} 分 · {{ q.topic || "未分类" }}
        </small>
        <h3>{{ q.prompt }}</h3>
        <p>正确答案：{{ q.correctOption }}</p>
      </div>
      <el-button
        :disabled="busy || (quiz?.questionCount || 0) >= 100"
        @click="importQuestion(q)"
      >
        选入试卷
      </el-button>
    </article>
    <p v-if="!bankRows.length && !busy">没有匹配题笺，可调整筛选，或前往题库新建。</p>
    <RouterLink to="/teacher/question-bank">前往问学题笺</RouterLink>
    <el-pagination
      v-if="bankTotal"
      v-model:current-page="bankPage"
      :total="bankTotal"
      :page-size="12"
      layout="prev,pager,next,total"
      @current-change="() => run(fetchBank)"
    />
  </el-dialog>
</template>
