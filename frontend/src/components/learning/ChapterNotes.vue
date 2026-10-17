<script setup lang="ts">
import { ref, onMounted, watch } from "vue";
import { api } from "../../api/client";
import { session } from "../../stores/session";
import { useTask } from "../../composables/useTask";
import { ElMessage } from "element-plus";
import type { Page, Note } from "../../types/models";
import StatePanel from "../common/StatePanel.vue";
const props = defineProps<{ enrollmentId: string; chapterId: string }>();
const rows = ref<Note[]>([]);
const total = ref(0);
const title = ref("");
const content = ref("");
const { busy, error, run } = useTask();
const key =
  "hw:note-draft:" + session.value!.user.id + ":" + props.enrollmentId + ":" + props.chapterId;
try {
  const d = JSON.parse(sessionStorage.getItem(key) || "null");
  if (d) {
    title.value = d.title;
    content.value = d.content;
  }
} catch {}
watch([title, content], () => {
  sessionStorage.setItem(key, JSON.stringify({ title: title.value, content: content.value }));
});
async function fetchRows() {
  const p = await api<Page<Note>>(
    "/api/notes?enrollmentId=" + props.enrollmentId + "&chapterId=" + props.chapterId + "&size=3",
  );
  rows.value = p.items;
  total.value = p.total;
}
onMounted(() => run(fetchRows));
function save() {
  run(async () => {
    await api("/api/notes", {
      method: "POST",
      body: {
        enrollmentId: props.enrollmentId,
        chapterId: props.chapterId,
        title: title.value,
        content: content.value,
      },
    });
    title.value = "";
    content.value = "";
    sessionStorage.removeItem(key);
    await fetchRows();
    ElMessage.success("本章札记已保存");
  });
}
</script>
<template>
  <section class="paper chapter-notes">
    <div class="section-heading">
      <h2>章边札记</h2>
      <RouterLink :to="'/student/notes?enrollmentId=' + enrollmentId">
        管理札记（{{ total }} 篇本章记录）
      </RouterLink>
    </div>
    <StatePanel
      :error="error"
      :busy="busy && !rows.length"
      @retry="run(fetchRows)"
    />
    <form @submit.prevent="save">
      <label class="form-field">
        记一个标题
        <el-input
          v-model="title"
          maxlength="100"
          required
          placeholder="例如：这首诗里的月光"
        />
      </label>
      <label class="form-field">
        记下此刻的理解
        <el-input
          v-model="content"
          type="textarea"
          :rows="4"
          maxlength="10000"
          required
          placeholder="引一句原文，写一点领会。"
        />
      </label>
      <div class="form-actions">
        <el-button
          native-type="submit"
          type="primary"
          :loading="busy"
        >
          保存本章札记
        </el-button>
        <small>正文仅本人可见；未保存文字暂存在本次浏览会话。</small>
      </div>
    </form>
    <article
      v-for="n in rows"
      :key="n.id"
      class="list-note"
    >
      <h3>{{ n.title }}</h3>
      <p class="plain-text">{{ n.content }}</p>
    </article>
  </section>
</template>
