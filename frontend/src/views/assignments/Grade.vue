<script setup lang="ts">
import RevisionPanel from "../../components/assignments/RevisionPanel.vue";
import { computed, reactive, ref, watch, onMounted, onUnmounted } from "vue";
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from "vue-router";
import { ElMessageBox, ElMessage } from "element-plus";
import { api } from "../../api/client";
import { useTask, time, length } from "../../composables/useTask";
import type { Submission, Grade } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
import GradeHistoryPanel from "../../components/assignments/GradeHistoryPanel.vue";
import AttachmentPanel from "../../components/assignments/AttachmentPanel.vue";
const route = useRoute();
const router = useRouter();
const id = computed(() => String(route.params.submissionId));
const submission = ref<Submission | null>(null);
const form = reactive({ score: "", comment: "", rubricScores: [] as string[] });
const base = ref("");
const review = ref<Submission | null>(null);
const dialog = ref(false);
const { busy, error, run } = useTask();
const dirty = computed(() => !!submission.value && base.value !== JSON.stringify(form));
const sum = computed(() => form.rubricScores.reduce((a, b) => a + (Number(b) || 0), 0).toFixed(2));
const queue = computed(() => {
  try {
    return JSON.parse(sessionStorage.getItem("hw:review-queue") || "[]") as string[];
  } catch {
    return [];
  }
});
const position = computed(() => queue.value.indexOf(id.value));
function fill(s: Submission) {
  submission.value = s;
  form.score = s.grade?.score || "";
  form.comment = s.grade?.comment || "";
  form.rubricScores = s.grade?.rubricScores || s.rubric?.map(() => "") || [];
  base.value = JSON.stringify(form);
}
function load() {
  const requested = id.value;
  submission.value = null;
  run(async () => {
    const s = await api<Submission>("/api/submissions/" + requested);
    if (requested === id.value) fill(s);
  });
}
watch(id, load, { immediate: true });
async function leave() {
  if (!dirty.value) return true;
  try {
    await ElMessageBox.confirm("当前朱批尚未保存，离开会丢弃本次填写。", "离开答卷", {
      confirmButtonText: "丢弃并离开",
      cancelButtonText: "继续批阅",
    });
    return true;
  } catch {
    return false;
  }
}
onBeforeRouteLeave(leave);
onBeforeRouteUpdate(leave);
function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value) {
    e.preventDefault();
    e.returnValue = "";
  }
}
onMounted(() => window.addEventListener("beforeunload", beforeUnload));
onUnmounted(() => window.removeEventListener("beforeunload", beforeUnload));
async function navigate(delta: number) {
  const next = queue.value[position.value + delta];
  if (next) await router.push("/teacher/submissions/" + next + "/grade?queue=1");
}
function save() {
  run(async () => {
    await ElMessageBox.confirm(
      "将保存 " + submission.value?.studentName + " 的答卷评分，所有更正会保留记录。",
      "确认评分",
      { confirmButtonText: "保存评分", cancelButtonText: "继续批阅" },
    );
    const g = await api<Grade>("/api/submissions/" + id.value + "/grade", {
      method: "PUT",
      body: {
        score: form.score,
        comment: form.comment,
        rubricScores: form.rubricScores,
        expectedVersion: submission.value!.version,
      },
    });
    submission.value!.grade = g;
    submission.value!.version = g.submissionVersion;
    submission.value!.status = "GRADED";
    base.value = JSON.stringify(form);
    ElMessage.success("评分已保存");
  });
}
function latest() {
  run(async () => {
    review.value = await api<Submission>("/api/submissions/" + id.value);
    dialog.value = true;
  });
}
function feedback(text: string) {
  form.comment += (form.comment ? "\n" : "") + text;
}
</script>
<template>
  <PageHeading
    :title="submission?.taskTitle || '批改作业'"
    back="/teacher/review"
    description="细读答卷，按项朱批。"
  >
    <template v-if="route.query.queue">
      <el-button
        :disabled="position <= 0 || busy"
        @click="navigate(-1)"
      >
        上一份
      </el-button>
      <span>{{ position + 1 }} / {{ queue.length }}</span>
      <el-button
        :disabled="position >= queue.length - 1 || busy"
        @click="navigate(1)"
      >
        下一份
      </el-button>
    </template>
  </PageHeading>
  <StatePanel
    :busy="busy && !submission"
    :error="error"
    @retry="error?.status === 409 ? latest() : load()"
  />
  <div
    v-if="submission"
    class="grade-layout"
  >
    <article class="paper answer-scroll">
      <span class="small-seal">答卷</span>
      <h2>{{ submission.studentName }}</h2>
      <p class="submission-note">
        版本 {{ submission.version }} · {{ time(submission.submittedAt) }}
      </p>
      <div class="answer-copy">{{ submission.answerText }}</div>
      <AttachmentPanel :submission-id="id" />
    </article>
    <form
      class="paper red-paper"
      @submit.prevent="save"
    >
      <h2>朱批笺</h2>
      <p
        v-if="submission.grade"
        class="read-only-note"
      >
        已评分于 {{ time(submission.grade.gradedAt) }}。更正保留历史。
      </p>
      <fieldset
        v-if="submission.rubric?.length"
        class="rubric-editor"
      >
        <legend>分项评分</legend>
        <label
          v-for="(item, index) in submission.rubric"
          :key="item.name"
          class="rubric-row"
        >
          <span>
            {{ item.name }}
            <small>满分 {{ item.maxScore }}</small>
          </span>
          <el-input
            v-model="form.rubricScores[index]"
            inputmode="decimal"
            :aria-label="item.name + '得分'"
            required
          />
        </label>
        <strong>合计 {{ sum }} / {{ submission.maxScore }}</strong>
      </fieldset>
      <label
        v-else
        class="form-field"
      >
        分数
        <el-input
          v-model="form.score"
          inputmode="decimal"
          required
          aria-label="分数"
        />
        <small>0 至 {{ submission.maxScore }}，最多两位小数。</small>
      </label>
      <div class="feedback-stamps">
        <span>常用评语</span>
        <button
          v-for="text in [
            '原文证据充分，分析紧扣语境。',
            '请补充具体诗句，说明判断依据。',
            '观点清晰，可进一步比较不同意象。',
            '请核对引用出处与作品版本。',
          ]"
          :key="text"
          type="button"
          @click="feedback(text)"
        >
          {{ text }}
        </button>
      </div>
      <label class="form-field">
        评语
        <el-input
          v-model="form.comment"
          type="textarea"
          :rows="7"
          aria-label="评语"
        />
        <small>{{ length(form.comment) }} / 2000 字</small>
      </label>
      <div class="form-actions">
        <el-button
          type="primary"
          native-type="submit"
          :loading="busy"
        >
          {{ submission.grade ? "保存更正" : "保存评分" }}
        </el-button>
        <el-button
          @click="latest"
          :disabled="busy"
        >
          核对最新答案
        </el-button>
      </div>
    </form>
  </div>
  <el-dialog
    v-model="dialog"
    title="核对最新答案"
    width="700px"
  >
    <template v-if="review">
      <p>{{ review.studentName }} · 版本 {{ review.version }}</p>
      <div class="answer-copy">{{ review.answerText }}</div>
      <p>分数 {{ review.grade?.score ?? "待评分" }}；评语 {{ review.grade?.comment || "无" }}</p>
    </template>
    <template #footer>
      <el-button @click="dialog = false">保留当前填写</el-button>
      <el-button
        type="primary"
        @click="
          fill(review!);
          dialog = false;
          error = null;
        "
      >
        使用最新版本
      </el-button>
    </template>
  </el-dialog>
  <GradeHistoryPanel
    v-if="submission"
    :submission-id="id"
    :version="submission.version"
  />
  <RevisionPanel
    v-if="submission"
    :key="id"
    :submission-id="id"
    :submission-version="submission.version"
    :status="submission.status"
  />
</template>
