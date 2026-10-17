<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { onBeforeRouteLeave, onBeforeRouteUpdate } from "vue-router";
import { ElMessageBox, ElMessage } from "element-plus";
import { api } from "../../api/client";
import { session } from "../../stores/session";
import { useTask, time } from "../../composables/useTask";
import { revisionDiff } from "../../utils/revision-diff";
import StatePanel from "../common/StatePanel.vue";
const props = defineProps<{ submissionId: string; submissionVersion: number; status: string }>();
type Revision = {
  id: string;
  requestText: string;
  answerText: string | null;
  studentNote: string | null;
  teacherComment: string | null;
  status: string;
  version: number;
  openedAt: string;
  submittedAt: string | null;
  reviewedAt: string | null;
};
const rows = ref<Revision[]>([]);
const original = ref("");
const courseStatus = ref("");
const selectedId = ref("");
const requestText = ref("");
const draftText = ref("");
const draftNote = ref("");
const comment = ref("");
const decision = ref("ACCEPTED");
const localNotice = ref("");
const { busy, error, run } = useTask();
const teacher = computed(() => session.value?.user.role === "TEACHER");
const active = computed(() => rows.value.find((x) => ["OPEN", "SUBMITTED"].includes(x.status)));
const selected = computed(() => rows.value.find((x) => x.id === selectedId.value));
const writable = computed(() => courseStatus.value === "PUBLISHED");
const comparisons = computed(() =>
  revisionDiff(original.value, selected.value?.answerText || original.value),
);
const statusNames: Record<string, string> = {
  OPEN: "待修订",
  SUBMITTED: "待先生评阅",
  ACCEPTED: "修订已认可",
  CHANGES: "需再修改",
  CANCELLED: "邀请已取消",
};
const draftKey = computed(() =>
  !teacher.value && active.value?.status === "OPEN"
    ? "hw:revision:" + session.value?.user.id + ":" + props.submissionId + ":" + active.value.id
    : "",
);
watch([draftText, draftNote], () => {
  if (draftKey.value)
    try {
      localStorage.setItem(
        draftKey.value,
        JSON.stringify({ answerText: draftText.value, note: draftNote.value }),
      );
      localNotice.value = "修订草稿已保留在本浏览器。";
    } catch {
      localNotice.value = "浏览器无法保留草稿，请及时提交或另存正文。";
    }
});
async function fetchRows() {
  const id = props.submissionId;
  const h = await api<{ originalText: string; courseStatus: string; items: Revision[] }>(
    "/api/submissions/" + id + "/revisions",
  );
  if (id !== props.submissionId) return;
  original.value = h.originalText;
  courseStatus.value = h.courseStatus;
  rows.value = h.items;
  selectedId.value = [...h.items].reverse().find((x) => x.answerText)?.id || "";
  if (draftKey.value) {
    draftText.value =
      [...h.items].reverse().find((x) => x.answerText)?.answerText || h.originalText;
    draftNote.value = "";
    try {
      const saved = JSON.parse(localStorage.getItem(draftKey.value) || "null");
      if (saved) {
        draftText.value = saved.answerText;
        draftNote.value = saved.note;
        localNotice.value = "已恢复本浏览器的修订草稿。";
      }
    } catch {
      localNotice.value = "旧草稿无法读取，请核对正文后重新填写。";
    }
  }
}
function load() {
  run(fetchRows);
}
watch(() => [props.submissionId, props.submissionVersion], load, { immediate: true });
function invite() {
  run(async () => {
    await api("/api/submissions/" + props.submissionId + "/revisions", {
      method: "POST",
      body: { requestText: requestText.value, expectedSubmissionVersion: props.submissionVersion },
    });
    requestText.value = "";
    await fetchRows();
    ElMessage.success("修订邀请已保存");
  });
}
function submit() {
  run(async () => {
    await ElMessageBox.confirm(
      "提交后本轮修订正文固定，等待先生反馈。正式答卷与原分数保持。",
      "提交修订稿",
      { confirmButtonText: "提交修订稿", cancelButtonText: "继续修改" },
    );
    const key = draftKey.value;
    await api(
      "/api/submissions/" + props.submissionId + "/revisions/" + active.value!.id + "/submit",
      {
        method: "POST",
        body: {
          answerText: draftText.value,
          studentNote: draftNote.value,
          expectedVersion: active.value!.version,
        },
      },
    );
    try {
      localStorage.removeItem(key);
    } catch {}
    await fetchRows();
    ElMessage.success("修订稿已提交");
  });
}
function review() {
  run(async () => {
    await ElMessageBox.confirm(
      decision.value === "CHANGES"
        ? "会保留本轮评阅并开启下一轮修订。"
        : "会保留本轮评阅，正式分数保持。",
      "保存修订反馈",
      { confirmButtonText: "保存反馈", cancelButtonText: "继续评阅" },
    );
    await api(
      "/api/submissions/" + props.submissionId + "/revisions/" + active.value!.id + "/review",
      {
        method: "POST",
        body: {
          decision: decision.value,
          comment: comment.value,
          expectedVersion: active.value!.version,
        },
      },
    );
    comment.value = "";
    await fetchRows();
    ElMessage.success("修订反馈已保存");
  });
}
function cancel() {
  run(async () => {
    await ElMessageBox.confirm("取消未提交的修订邀请，记录仍然保留。", "取消邀请");
    await api(
      "/api/submissions/" + props.submissionId + "/revisions/" + active.value!.id + "/cancel",
      { method: "POST", body: { expectedVersion: active.value!.version } },
    );
    await fetchRows();
  });
}
async function leave() {
  if (!teacher.value || (!requestText.value && !comment.value)) return true;
  try {
    await ElMessageBox.confirm("修订要求或反馈尚未保存。", "离开修订笺", {
      confirmButtonText: "丢弃并离开",
      cancelButtonText: "继续填写",
    });
    return true;
  } catch {
    return false;
  }
}
onBeforeRouteLeave(leave);
onBeforeRouteUpdate(leave);
</script>
<template>
  <section class="paper revision-panel">
    <div class="toolbar">
      <span
        class="small-seal"
        aria-hidden="true"
      >
        改
      </span>
      <h2>修订笺</h2>
      <el-button
        :disabled="busy"
        @click="load"
      >
        更新修订记录
      </el-button>
    </div>
    <p class="revision-note">
      修订稿与回笺独立留存。原答卷、正式评分及课程总评保持；正式成绩更正仍由教师按评分流程留痕。
    </p>
    <StatePanel
      :busy="busy && !rows.length"
      :error="error"
      @retry="load"
    />
    <p
      v-if="!writable && courseStatus"
      class="read-only-note"
    >
      课程已归档，修订记录只读。
    </p>
    <form
      v-if="teacher && writable && !active && status === 'GRADED'"
      @submit.prevent="invite"
    >
      <label>
        修订要求
        <textarea
          v-model="requestText"
          rows="3"
          maxlength="2000"
          required
          placeholder="指出需补充的原文依据、论证或表达。"
        />
      </label>
      <el-button
        native-type="submit"
        type="primary"
        :loading="busy"
      >
        邀请学生修订
      </el-button>
    </form>
    <p v-if="!rows.length && !busy">
      {{ status === "GRADED" ? "尚无修订邀请。" : "正式答卷批阅后，教师可邀请修订。" }}
    </p>
    <section v-if="active">
      <h3>本轮：{{ statusNames[active.status] }}</h3>
      <p class="plain-text">{{ active.requestText }}</p>
      <el-button
        v-if="teacher && active.status === 'OPEN' && writable"
        :disabled="busy"
        @click="cancel"
      >
        取消本轮邀请
      </el-button>
    </section>
    <form
      v-if="!teacher && active?.status === 'OPEN' && writable"
      @submit.prevent="submit"
    >
      <label>
        修订正文
        <textarea
          v-model="draftText"
          rows="10"
          maxlength="10000"
          required
        />
      </label>
      <label>
        修改说明
        <textarea
          v-model="draftNote"
          rows="3"
          maxlength="1000"
          placeholder="说明你依据回笺修改了哪些地方。"
        />
      </label>
      <p
        class="revision-note"
        role="status"
      >
        {{ localNotice }}
      </p>
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
      >
        提交修订稿
      </el-button>
    </form>
    <template v-if="selected?.answerText">
      <h3>原稿与修订稿对照</h3>
      <p class="revision-note">
        朱线标出包含修改的段落区间；共同开头与结尾保留。此处对照正式原稿。
      </p>
      <div class="revision-comparison">
        <article>
          <h3>正式原稿</h3>
          <div class="answer-copy">
            <p
              v-for="(x, i) in comparisons.before"
              :key="i"
              :class="x.changed ? 'diff-changed' : 'diff-same'"
            >
              {{ x.changed ? "〔修改区间〕\n" : "" }}{{ x.text }}
            </p>
          </div>
        </article>
        <article>
          <h3>本轮修订稿</h3>
          <div class="answer-copy">
            <p
              v-for="(x, i) in comparisons.after"
              :key="i"
              :class="x.changed ? 'diff-changed' : 'diff-same'"
            >
              {{ x.changed ? "〔修改区间〕\n" : "" }}{{ x.text }}
            </p>
          </div>
          <p
            v-if="selected.studentNote"
            class="plain-text"
          >
            修改说明：{{ selected.studentNote }}
          </p>
        </article>
      </div>
    </template>
    <form
      v-if="teacher && writable && active?.status === 'SUBMITTED' && selectedId === active.id"
      @submit.prevent="review"
    >
      <fieldset>
        <legend>本轮结论</legend>
        <label>
          <input
            v-model="decision"
            type="radio"
            value="ACCEPTED"
          />
          认可修订
        </label>
        <label>
          <input
            v-model="decision"
            type="radio"
            value="CHANGES"
          />
          仍需修改，开启下一轮
        </label>
      </fieldset>
      <label>
        修订反馈
        <textarea
          v-model="comment"
          rows="4"
          maxlength="2000"
          required
        />
      </label>
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
      >
        保存修订反馈
      </el-button>
    </form>
    <el-button
      v-else-if="teacher && active?.status === 'SUBMITTED' && selectedId !== active.id"
      @click="selectedId = active.id"
    >
      切回待阅修订稿
    </el-button>
    <div
      v-if="rows.length"
      class="revision-timeline"
    >
      <article
        v-for="(r, i) in rows"
        :key="r.id"
        class="revision-entry"
      >
        <h3>
          第 {{ i + 1 }} 轮
          <span class="revision-status">{{ statusNames[r.status] }}</span>
        </h3>
        <p class="field-hint">
          邀请 {{ time(r.openedAt) }}
          <template v-if="r.submittedAt">· 提交 {{ time(r.submittedAt) }}</template>
        </p>
        <p class="plain-text">修订要求：{{ r.requestText }}</p>
        <p
          v-if="r.teacherComment"
          class="plain-text"
        >
          先生回笺：{{ r.teacherComment }}
        </p>
        <el-button
          v-if="r.answerText"
          :disabled="busy"
          @click="selectedId = r.id"
        >
          对照此轮修订
        </el-button>
      </article>
    </div>
  </section>
</template>
