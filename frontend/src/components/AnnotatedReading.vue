<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from "vue";
import { api } from "../api";
import { allPages } from "../export";
import { useTask, time } from "../useTask";
import type { Annotation } from "../models";
import StatePanel from "./StatePanel.vue";
import { ElMessage } from "element-plus";
const props = defineProps<{
    enrollmentId: string;
    chapterId: string;
    content: string;
    readonly: boolean;
  }>(),
  body = ref<HTMLElement | null>(null),
  rows = ref<Annotation[]>([]),
  quote = ref(""),
  start = ref(0),
  end = ref(0),
  comment = ref(""),
  color = ref("RED");
const { busy, error, run } = useTask();
const palette = { RED: "朱批", PINE: "松绿", GOLD: "金签" };
async function highlights() {
  await nextTick();
  if (!body.value?.firstChild || !("highlights" in CSS)) return;
  for (const color of ["RED", "PINE", "GOLD"]) {
    const ranges = rows.value
      .filter(
        (a) =>
          a.color === color &&
          a.anchorValid &&
          props.content.slice(a.startOffset, a.endOffset) === a.quoteText,
      )
      .map((a) => {
        const range = new Range();
        range.setStart(body.value!.firstChild!, a.startOffset);
        range.setEnd(body.value!.firstChild!, a.endOffset);
        return range;
      });
    CSS.highlights.set(
      "hanwen-" + color.toLowerCase(),
      new Highlight(...ranges),
    );
  }
}
async function fetchRows() {
  rows.value = await allPages<Annotation>(
    "/api/annotations?enrollmentId=" +
      props.enrollmentId +
      "&chapterId=" +
      props.chapterId,
  );
  await highlights();
}
function load() {
  run(fetchRows);
}
function select() {
  const s = window.getSelection();
  if (!s || s.isCollapsed || !body.value || !s.rangeCount) return;
  const range = s.getRangeAt(0);
  if (
    !body.value.contains(range.startContainer) ||
    !body.value.contains(range.endContainer)
  )
    return;
  const before = range.cloneRange();
  before.selectNodeContents(body.value);
  before.setEnd(range.startContainer, range.startOffset);
  const value = range.toString();
  if (!value || value.length > 500) return;
  start.value = before.toString().length;
  end.value = start.value + value.length;
  quote.value = value;
}
onMounted(() => {
  load();
  document.addEventListener("selectionchange", select);
});
onUnmounted(() => {
  document.removeEventListener("selectionchange", select);
  if ("highlights" in CSS)
    for (const c of ["red", "pine", "gold"])
      CSS.highlights.delete("hanwen-" + c);
});
watch(
  () => props.content,
  () => highlights(),
);
function save() {
  run(async () => {
    await api("/api/annotations", {
      method: "POST",
      body: {
        enrollmentId: props.enrollmentId,
        chapterId: props.chapterId,
        startOffset: start.value,
        endOffset: end.value,
        quoteText: quote.value,
        comment: comment.value,
        color: color.value,
      },
    });
    quote.value = "";
    comment.value = "";
    await fetchRows();
    ElMessage.success("章边批注已保存");
  });
}
function remove(a: Annotation) {
  run(async () => {
    await api("/api/annotations/" + a.id, { method: "DELETE" });
    await fetchRows();
  });
}
</script>
<template>
  <div class="annotated-reading">
    <div
      ref="body"
      class="reading-body annotation-text"
      v-text="content"
      @mouseup="select"
      @keyup="select"
    ></div>
    <aside class="marginalia">
      <div class="marginalia-title">
        <span class="small-seal">朱批</span>
        <div>
          <h3>章边批注</h3>
          <small>选中1至500字的原文，写下你的理解。</small>
        </div>
      </div>
      <StatePanel :error="error" @retry="load" />
      <form
        v-if="quote && !readonly"
        class="annotation-compose"
        @submit.prevent="save"
      >
        <blockquote>{{ quote }}</blockquote>
        <label :for="'annotation-comment-' + chapterId">我的批注</label
        ><el-input
          :id="'annotation-comment-' + chapterId"
          v-model="comment"
          type="textarea"
          :rows="3"
          required
          maxlength="1000"
        />
        <fieldset class="annotation-colors">
          <legend>批注颜色</legend>
          <label
            v-for="(name, value) in palette"
            :key="value"
            :class="'annotation-' + value"
            ><input
              type="radio"
              v-model="color"
              :value="value"
              name="annotation-color"
            />{{ name }}</label
          >
        </fieldset>
        <el-button type="primary" native-type="submit" :loading="busy"
          >落笔保存</el-button
        >
      </form>
      <div v-if="!rows.length && !quote" class="margin-empty">
        <span aria-hidden="true">思</span>
        <p>在文句间，留下自己的思考。</p>
      </div>
      <article
        v-for="a in rows"
        :key="a.id"
        class="annotation-slip"
        :class="'annotation-' + a.color"
      >
        <blockquote>{{ a.quoteText }}</blockquote>
        <p class="plain-text">{{ a.comment }}</p>
        <small>{{ time(a.createdAt) }}</small
        ><small v-if="!a.anchorValid" class="anchor-changed"
          >原文已修改，保留摘录，不再标记旧位置。</small
        ><el-button text @click="remove(a)">删除批注</el-button>
      </article>
      <p class="field-hint">批注仅本人可读，课程归档后保留。</p>
    </aside>
  </div>
</template>
