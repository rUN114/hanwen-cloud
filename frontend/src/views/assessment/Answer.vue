<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from "vue";
import { useRoute, onBeforeRouteLeave } from "vue-router";
import { ElMessageBox, ElMessage } from "element-plus";
import { api, ApiError } from "../../api/client";
import { session } from "../../stores/session";
import { download } from "../../utils/export";
import { useTask, time, length } from "../../composables/useTask";
import type { AnswerDraft, Task, Course, Submission, GradeBrief, Page } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
import CourseLinks from "../../components/courses/CourseLinks.vue";
import AttachmentPanel from "../../components/assignments/AttachmentPanel.vue";
const id = String(useRoute().params.assignmentId);
const task = ref<Task | null>(null);
const course = ref<Course | null>(null);
const submission = ref<Submission | null>(null);
const answer = ref("");
const base = ref("");
const loaded = ref(false);
const draftRestored = ref(false);
const now = ref(Date.now());
const review = ref<Submission | null>(null);
const dialog = ref(false);
const { busy, error, run } = useTask();
const pendingLocal = ref<string | null>(null);
const key = "hw:draft:" + session.value?.user.id + ":" + id;
const readonly = computed(
  () =>
    !task.value ||
    task.value.status !== "PUBLISHED" ||
    course.value?.status !== "PUBLISHED" ||
    submission.value?.status === "GRADED" ||
    (!!task.value.deadlineAt && new Date(task.value.deadlineAt).getTime() <= now.value),
);
const dirty = computed(() => loaded.value && !readonly.value && answer.value !== base.value);
let clock: ReturnType<typeof setInterval>;
let draftTimer: ReturnType<typeof setTimeout> | undefined;
let draftSaving: Promise<void> | null = null;
const draftVersion = ref(-1);
const draftReady = ref(false);
const draftMessage = ref("");
const draftIssue = ref("");
const draftConflict = ref(false);
let lastSavedDraft = "";
async function persistDraft(): Promise<void> {
  if (!draftReady.value || !loaded.value || readonly.value || draftConflict.value) return;
  if (draftSaving) {
    await draftSaving;
    if (draftIssue.value) return;
    if (answer.value !== lastSavedDraft && !draftConflict.value) return persistDraft();
    return;
  }
  if (answer.value === lastSavedDraft) return;
  const text = answer.value;
  draftMessage.value = "正在保存到本人账号…";
  draftSaving = api<AnswerDraft>("/api/assignments/" + id + "/draft", {
    method: "PUT",
    body: { answerText: text, expectedVersion: draftVersion.value },
  })
    .then((d) => {
      draftVersion.value = d.version;
      lastSavedDraft = text;
      draftMessage.value = "草稿已保存 · " + time(d.updatedAt);
      draftIssue.value = "";
    })
    .catch((e) => {
      draftIssue.value = e instanceof Error ? e.message : "草稿未能保存，请重试";
      if (e instanceof ApiError && e.status === 409) draftConflict.value = true;
      draftMessage.value = "草稿尚未保存";
    })
    .finally(() => {
      draftSaving = null;
    });
  await draftSaving;
}
function retryDraft() {
  if (draftConflict.value) return;
  void persistDraft();
}

function load() {
  run(async () => {
    loaded.value = false;
    const savedLocal = sessionStorage.getItem(key);
    draftReady.value = false;
    draftConflict.value = false;
    task.value = await api<Task>("/api/assignments/" + id);
    course.value = await api<Course>("/api/courses/" + task.value.courseId);
    const p = await api<Page<GradeBrief>>("/api/grades/me?assignmentId=" + id);
    if (p.items.length > 1)
      throw new ApiError(500, "DATA_CONFLICT", "本人提交记录不一致，请联系管理员");
    submission.value = p.items[0]
      ? await api<Submission>("/api/submissions/" + p.items[0].submissionId)
      : null;
    base.value = submission.value?.answerText || "";
    answer.value = base.value;
    const localDraft = savedLocal;
    const serverDraft = await api<AnswerDraft | null>("/api/assignments/" + id + "/draft");
    draftVersion.value = serverDraft?.version ?? -1;
    lastSavedDraft = serverDraft?.answerText ?? base.value;
    draftRestored.value = !!(serverDraft || localDraft) && !readonly.value;
    if (draftRestored.value) answer.value = serverDraft?.answerText ?? base.value;
    pendingLocal.value =
      !readonly.value && localDraft !== null && localDraft !== answer.value ? localDraft : null;
    draftMessage.value = serverDraft
      ? "草稿已保存 · " + time(serverDraft.updatedAt)
      : "编辑后自动保存到本人账号";
    draftIssue.value = "";
    loaded.value = true;
    draftReady.value = true;
    if (answer.value !== lastSavedDraft) void persistDraft();
  });
}
onMounted(() => {
  load();
  clock = setInterval(() => (now.value = Date.now()), 30000);
  window.addEventListener("beforeunload", beforeUnload);
  window.addEventListener("hw:session-resumed", retryDraft);
});
onUnmounted(() => {
  window.removeEventListener("hw:session-resumed", retryDraft);
  clearInterval(clock);
  if (draftTimer) clearTimeout(draftTimer);
  window.removeEventListener("beforeunload", beforeUnload);
});
watch(
  answer,
  () => {
    if (!loaded.value) return;
    if (!loaded.value || readonly.value) return;
    if (answer.value !== base.value) sessionStorage.setItem(key, answer.value);
    else sessionStorage.removeItem(key);
    if (draftTimer) clearTimeout(draftTimer);
    if (draftReady.value && !draftConflict.value) {
      draftMessage.value = "草稿待保存";
      draftTimer = setTimeout(() => {
        void persistDraft();
      }, 1200);
    }
  },
  { flush: "sync" },
);
function restoreLocal() {
  if (pendingLocal.value !== null) {
    answer.value = pendingLocal.value;
    pendingLocal.value = null;
  }
}
function useAccountDraft() {
  pendingLocal.value = null;
  sessionStorage.removeItem(key);
}
function exportAnswer() {
  download(answer.value, (task.value?.title || "习作") + "-本人草稿.txt");
}
function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value && answer.value !== lastSavedDraft) {
    e.preventDefault();
    e.returnValue = "";
  }
}
onBeforeRouteLeave(async () => {
  if (!dirty.value) return true;
  try {
    if (draftTimer) clearTimeout(draftTimer);
    await persistDraft();
    await ElMessageBox.confirm(
      answer.value === lastSavedDraft
        ? "草稿已保存到本人账号，但尚未正式提交作业。确认离开？"
        : "草稿尚未成功保存，请先复制未提交内容。确认离开？",
      "离开作业页",
      { confirmButtonText: "离开", cancelButtonText: "继续写作" },
    );
    return true;
  } catch {
    return false;
  }
});
function save() {
  run(async () => {
    if (length(answer.value.trim()) < 1 || length(answer.value.trim()) > 10000)
      throw new ApiError(400, "VALIDATION_ERROR", "答案须为1至10000字");
    if (draftTimer) clearTimeout(draftTimer);
    await persistDraft();
    draftReady.value = false;
    const existing = submission.value;
    try {
      submission.value = await api<Submission>(
        existing ? "/api/submissions/" + existing.id : "/api/assignments/" + id + "/submissions",
        {
          method: existing ? "PUT" : "POST",
          body: existing
            ? { answerText: answer.value, expectedVersion: existing.version }
            : { answerText: answer.value },
        },
      );
      base.value = submission.value.answerText || answer.value;
      answer.value = base.value;
      sessionStorage.removeItem(key);
      draftRestored.value = false;
      lastSavedDraft = answer.value;
      draftVersion.value = -1;
      draftReady.value = true;
      draftMessage.value = "答案已正式提交";
      draftIssue.value = "";
      draftConflict.value = false;
      ElMessage.success("答案已提交");
    } finally {
      draftReady.value = true;
    }
  });
}
function latest() {
  run(async () => {
    const p = await api<Page<GradeBrief>>("/api/grades/me?assignmentId=" + id);
    if (!p.items[0]) throw new ApiError(404, "NOT_FOUND", "尚无服务器提交");
    review.value = await api<Submission>("/api/submissions/" + p.items[0].submissionId);
    dialog.value = true;
  });
}
function acceptLatest() {
  submission.value = review.value;
  base.value = review.value?.answerText || "";
  dialog.value = false;
  error.value = null;
}
</script>
<template>
  <PageHeading
    :title="task?.title || '作业提交'"
    :back="course ? '/student/courses/' + course.id : '/student/enrollments'"
    description="答案按纯文本保存；提交资格与截止由服务器确认。"
  />
  <StatePanel
    :busy="busy && !loaded"
    :error="error"
    @retry="error?.status === 409 ? latest() : load()"
  />
  <CourseLinks
    v-if="course"
    :course-id="course.id"
  />
  <section
    v-if="loaded && !readonly"
    class="draft-status"
    aria-live="polite"
  >
    <span
      class="draft-dot"
      :class="{ unsaved: !!draftIssue }"
    ></span>
    <span>{{ draftMessage }}</span>
    <small>保存草稿不等于提交作业</small>
    <p v-if="draftIssue">
      {{ draftIssue }}
      <template v-if="draftConflict">
        。草稿版本或作业状态已变化，请先复制当前文字，再重新打开核对。
      </template>
      <el-button
        v-else
        text
        @click="retryDraft"
      >
        重试保存草稿
      </el-button>
    </p>
  </section>
  <section
    v-if="pendingLocal !== null"
    class="paper draft-recovery"
    role="status"
  >
    <span
      class="small-seal"
      aria-hidden="true"
    >
      存
    </span>
    <div>
      <h2>此页还有一份未同步的草稿</h2>
      <p>与账号中保存的文字不同。请核对后决定恢复，系统不会替你覆盖。</p>
      <pre>{{ pendingLocal }}</pre>
      <el-button
        type="primary"
        @click="restoreLocal"
      >
        恢复此页草稿
      </el-button>
      <el-button @click="download(pendingLocal!, '未同步草稿.txt')">下载未同步草稿</el-button>
      <el-button @click="useAccountDraft()">使用账号草稿</el-button>
    </div>
  </section>
  <section
    v-if="loaded && task"
    class="paper form-paper submit-answer"
  >
    <h2>题目与要求</h2>
    <p class="plain-text">{{ task.description }}</p>
    <p class="field-hint">
      满分 {{ task.maxScore }} ·
      {{ task.deadlineAt ? "截止（北京时间） " + time(task.deadlineAt) : "未设置截止" }}
    </p>
    <div
      v-if="readonly"
      class="read-only-note"
    >
      {{
        submission?.status === "GRADED"
          ? "已评分，答案只读。"
          : course?.status === "ARCHIVED"
            ? "课程已归档，停止提交。"
            : task.status === "CLOSED"
              ? "作业已关闭，停止提交。"
              : "作业已截止或未开放，不能提交。"
      }}
    </div>
    <p
      v-if="draftRestored"
      class="draft-warning"
    >
      已恢复未提交草稿，请核对内容后正式提交。
    </p>
    <form @submit.prevent="save">
      <div class="form-field">
        <label for="answer">我的答案</label>
        <el-input
          id="answer"
          v-model="answer"
          type="textarea"
          :rows="14"
          :disabled="readonly"
        />
        <span class="field-hint">{{ length(answer) }} / 10000字</span>
      </div>
      <p
        v-if="submission"
        class="field-hint"
      >
        服务器最近提交：{{ time(submission.submittedAt) }} · 当前版本
        {{ submission.version }}
      </p>
      <div class="form-actions">
        <el-button @click="exportAnswer">下载当前文字</el-button>
        <el-button
          v-if="!readonly"
          type="primary"
          native-type="submit"
          :loading="busy"
        >
          {{ submission ? "提交修改" : "提交答案" }}
        </el-button>
        <RouterLink
          v-if="submission?.status === 'GRADED'"
          :to="'/student/submissions/' + submission.id"
        >
          <el-button type="primary">查看批改反馈</el-button>
        </RouterLink>
        <el-button
          v-if="submission"
          :disabled="busy"
          @click="latest"
        >
          查看服务器答案
        </el-button>
        <span
          v-if="dirty"
          class="draft-warning"
        >
          有未提交的修改
        </span>
      </div>
    </form>
  </section>
  <el-dialog
    v-model="dialog"
    title="服务器当前答案"
    width="700px"
  >
    <template v-if="review">
      <p>
        版本 {{ review.version }} ·
        {{ review.status === "GRADED" ? "已评分" : "待评分" }}
      </p>
      <div class="answer-copy">{{ review.answerText }}</div>
      <p class="field-hint">核对后使用新版本继续；不会自动覆盖服务器或替换你的草稿。</p>
    </template>
    <template #footer>
      <el-button @click="dialog = false">关闭，保留原版本</el-button>
      <el-button
        type="primary"
        @click="acceptLatest"
      >
        已核对，使用新版本
      </el-button>
    </template>
  </el-dialog>
  <AttachmentPanel
    v-if="task"
    :assignment-id="id"
    :writable="!readonly"
  />
</template>
