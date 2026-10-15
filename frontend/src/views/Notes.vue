<script setup lang="ts">
import { ref, watch, onMounted, computed } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api";
import { useTask, time } from "../useTask";
import { allPages, download } from "../export";
import { ElMessage, ElMessageBox } from "element-plus";
import type { Page, Note, Enrollment, Chapter } from "../models";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
const route = useRoute(),
  rows = ref<Note[]>([]),
  enrollments = ref<Enrollment[]>([]),
  chapters = ref<Chapter[]>([]),
  page = ref(1),
  total = ref(0),
  keyword = ref(""),
  filterEnrollment = ref(String(route.query.enrollmentId || "")),
  editing = ref<Note | null>(null),
  showForm = ref(false),
  form = ref({ enrollmentId: "", chapterId: "", title: "", content: "" });
const { busy, error, run } = useTask();
const enrollmentNames = computed(() =>
  Object.fromEntries(enrollments.value.map((e) => [e.id, e.courseName])),
);
function query() {
  const q = new URLSearchParams({ keyword: keyword.value });
  if (filterEnrollment.value) q.set("enrollmentId", filterEnrollment.value);
  return "/api/notes?" + q;
}
async function fetchRows() {
  const p = await api<Page<Note>>(query() + "&page=" + page.value);
  rows.value = p.items;
  total.value = p.total;
}
function load() {
  run(async () => {
    enrollments.value = await allPages<Enrollment>("/api/enrollments/me");
    await fetchRows();
  });
}
onMounted(load);
watch(
  () => route.query.enrollmentId,
  () => {
    filterEnrollment.value = String(route.query.enrollmentId || "");
    page.value = 1;
    run(fetchRows);
  },
);
async function getChapters() {
  chapters.value = [];
  form.value.chapterId = "";
  if (form.value.enrollmentId) {
    const e = enrollments.value.find((e) => e.id === form.value.enrollmentId)!;
    chapters.value = await api<Chapter[]>(
      "/api/courses/" + e.courseId + "/chapters",
    );
  }
}
function selectCourse() {
  run(getChapters);
}
function start(n?: Note) {
  run(async () => {
    editing.value = n || null;
    form.value = {
      enrollmentId:
        n?.enrollmentId ||
        filterEnrollment.value ||
        enrollments.value[0]?.id ||
        "",
      chapterId: "",
      title: n?.title || "",
      content: n?.content || "",
    };
    await getChapters();
    form.value.chapterId = n?.chapterId || chapters.value[0]?.id || "";
    showForm.value = true;
  });
}
function save() {
  run(async () => {
    await api("/api/notes" + (editing.value ? "/" + editing.value.id : ""), {
      method: editing.value ? "PUT" : "POST",
      body: { ...form.value, expectedVersion: editing.value?.version },
    });
    showForm.value = false;
    page.value = 1;
    await fetchRows();
    ElMessage.success("札记已保存");
  });
}
function remove(n: Note) {
  run(async () => {
    await ElMessageBox.confirm(
      "删除《" + n.title + "》这篇札记？",
      "删除札记",
      { confirmButtonText: "删除", cancelButtonText: "保留" },
    );
    await api("/api/notes/" + n.id + "?expectedVersion=" + n.version, {
      method: "DELETE",
    });
    await fetchRows();
  });
}
function exportNotes() {
  run(async () => {
    const all = await allPages<Note>(query());
    download(
      all
        .map(
          (n) =>
            "# " +
            n.title +
            "\n\n课程：" +
            (enrollmentNames.value[n.enrollmentId] || n.enrollmentId) +
            " · 章节编号 " +
            n.chapterId +
            "\n更新：" +
            time(n.updatedAt) +
            "\n\n" +
            n.content,
        )
        .join("\n\n-----\n\n"),
      "我的学习札记.md",
      "text/markdown;charset=utf-8",
    );
    ElMessage.success("已导出当前筛选下的 " + all.length + " 篇札记");
  });
}
</script>
<template>
  <PageHeading
    title="学习札记"
    description="一字一思，留在自己的行笺里。札记正文仅本人可见。"
    ><el-button
      :disabled="busy || !enrollments.length"
      type="primary"
      @click="start()"
      >新写札记</el-button
    ><el-button :loading="busy" @click="exportNotes"
      >导出当前筛选</el-button
    ></PageHeading
  >
  <form
    class="toolbar"
    @submit.prevent="
      page = 1;
      run(fetchRows);
    "
  >
    <el-input
      v-model="keyword"
      placeholder="搜索标题或札记正文"
      aria-label="札记关键词"
      clearable
    /><el-select
      v-model="filterEnrollment"
      placeholder="全部课程"
      aria-label="筛选课程"
      clearable
      ><el-option
        v-for="e in enrollments"
        :key="e.id"
        :value="e.id"
        :label="e.courseName" /></el-select
    ><el-button native-type="submit" :loading="busy">寻记</el-button>
  </form>
  <StatePanel :busy="busy && !rows.length" :error="error" @retry="load" />
  <form
    v-if="showForm"
    class="paper note-editor form-paper"
    @submit.prevent="save"
  >
    <h2>{{ editing ? "修订札记" : "新写札记" }}</h2>
    <div class="form-columns">
      <label class="form-field"
        >所属课程<el-select
          v-model="form.enrollmentId"
          :disabled="!!editing"
          @change="selectCourse"
          ><el-option
            v-for="e in enrollments"
            :key="e.id"
            :value="e.id"
            :label="e.courseName" /></el-select></label
      ><label class="form-field"
        >所属章节<el-select v-model="form.chapterId" :disabled="!!editing"
          ><el-option
            v-for="h in chapters"
            :key="h.id"
            :value="h.id"
            :label="h.title" /></el-select
      ></label>
    </div>
    <label class="form-field"
      >札记标题<el-input v-model="form.title" maxlength="100" required /></label
    ><label class="form-field"
      >所思所记<el-input
        v-model="form.content"
        type="textarea"
        :rows="8"
        maxlength="10000"
        required
    /></label>
    <div class="form-actions">
      <el-button
        native-type="submit"
        type="primary"
        :loading="busy"
        :disabled="!form.chapterId"
        >保存札记</el-button
      ><el-button :disabled="busy" @click="showForm = false">取消</el-button>
    </div>
  </form>
  <section class="note-grid">
    <article v-for="n in rows" :key="n.id" class="paper note-paper">
      <span class="note-fold" aria-hidden="true"></span
      ><small
        >{{ enrollmentNames[n.enrollmentId] || "课程 " + n.enrollmentId }} ·
        {{ time(n.updatedAt) }}</small
      >
      <h2>{{ n.title }}</h2>
      <p class="plain-text">{{ n.content }}</p>
      <div class="form-actions">
        <el-button size="small" :disabled="busy" @click="start(n)"
          >修订</el-button
        ><el-button size="small" :disabled="busy" @click="remove(n)"
          >删除</el-button
        ><RouterLink
          :to="
            '/student/enrollments/' +
            n.enrollmentId +
            '/chapters/' +
            n.chapterId
          "
          >回到原章 →</RouterLink
        >
      </div>
    </article>
  </section>
  <StatePanel
    :empty="!busy && !error && !rows.length"
    empty-text="尚无札记。可以在章节阅读时记下想法，也可以从这里新写。"
  /><el-pagination
    v-if="total"
    v-model:current-page="page"
    :total="total"
    :page-size="10"
    layout="prev,pager,next,total"
    @current-change="run(fetchRows)"
  />
</template>
