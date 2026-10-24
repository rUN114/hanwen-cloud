<script setup lang="ts">
import ListPager from "../common/ListPager.vue";
import { ref, computed, onMounted, onUnmounted } from "vue";
import { api, apiFile, saveBlob } from "../../api/client";
import { session } from "../../stores/session";
import { canTeach } from "../../utils/course-access";
const resolvedEditable = ref(false);
import { useTask } from "../../composables/useTask";
import type { Course, ResourceFile, Chapter, Page } from "../../types/models";
import { ElMessage, ElMessageBox } from "element-plus";
import StatePanel from "../common/StatePanel.vue";
const props = defineProps<{
  courseId: string;
  chapterId?: string;
  courseStatus?: string;
  canEdit?: boolean;
}>();
const teacher = computed(
  () => session.value?.user.role === "TEACHER" && (props.canEdit ?? resolvedEditable.value),
);
const rows = ref<ResourceFile[]>([]);
const page = ref(1);
const total = ref(0);
const chapters = ref<Chapter[]>([]);
const title = ref("");
const description = ref("");
const chapterId = ref(props.chapterId || "");
const file = ref<File | null>(null);
const preview = ref<ResourceFile | null>(null);
const url = ref("");
const dialog = ref(false);
const { busy, error, run } = useTask();
async function fetchRows() {
  const p = await api<Page<ResourceFile>>(
    "/api/courses/" +
      props.courseId +
      "/resources?page=" +
      page.value +
      "&size=12" +
      (props.chapterId ? "&chapterId=" + props.chapterId : ""),
  );
  rows.value = p.items;
  total.value = p.total;
}
function load() {
  run(fetchRows);
}
onMounted(async () => {
  if (session.value?.user.role === "TEACHER" && props.canEdit === undefined) {
    try {
      resolvedEditable.value = canTeach(await api<Course>("/api/courses/" + props.courseId));
    } catch {
      resolvedEditable.value = false;
    }
  }
  load();
});
onUnmounted(() => {
  if (url.value) URL.revokeObjectURL(url.value);
});
function selectFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] || null;
  if (file.value && !title.value) title.value = file.value.name.replace(/\.[^.]+$/, "");
}
function openUpload() {
  run(async () => {
    chapters.value = await api<Chapter[]>("/api/courses/" + props.courseId + "/chapters");
    dialog.value = true;
  });
}
function upload() {
  run(async () => {
    if (!file.value) throw Error("请先选择资源文件");
    if (file.value.size > 20 * 1024 * 1024) throw Error("单个文件不能超过20MB");
    const data = new FormData();
    data.append("title", title.value);
    data.append("description", description.value);
    if (chapterId.value) data.append("chapterId", chapterId.value);
    data.append("file", file.value);
    await api("/api/teacher/courses/" + props.courseId + "/resources", {
      method: "POST",
      body: data,
    });
    dialog.value = false;
    title.value = "";
    description.value = "";
    file.value = null;
    await fetchRows();
    ElMessage.success("资源已上架");
  });
}
function download(f: ResourceFile) {
  run(async () => saveBlob(await apiFile("/api/resources/" + f.id + "/file"), f.originalName));
}
function show(f: ResourceFile) {
  run(async () => {
    if (url.value) URL.revokeObjectURL(url.value);
    preview.value = null;
    url.value = URL.createObjectURL(await apiFile("/api/resources/" + f.id + "/file"));
    preview.value = f;
  });
}
function remove(f: ResourceFile) {
  run(async () => {
    await ElMessageBox.confirm("将资源从课程书架下架，已有学习记录保留。", "下架资源");
    await api("/api/resources/" + f.id, { method: "DELETE" });
    await fetchRows();
  });
}
function kind(f: ResourceFile) {
  return f.mimeType.startsWith("audio/")
    ? "朗读"
    : f.mimeType.startsWith("image/")
      ? "图笺"
      : f.mimeType.includes("pdf")
        ? "课件"
        : "文稿";
}
const size = (b: number) =>
  b >= 1048576 ? (b / 1048576).toFixed(1) + " MB" : Math.ceil(b / 1024) + " KB";
</script>
<template>
  <section class="resource-room">
    <div class="section-heading">
      <div>
        <h2>资源书架</h2>
        <p class="field-hint">课件、图笺与朗读，伴你逐章展开。</p>
      </div>
      <el-button
        v-if="teacher && courseStatus !== 'ARCHIVED'"
        @click="openUpload"
      >
        上架资源
      </el-button>
    </div>
    <StatePanel
      :error="error"
      :busy="busy && !rows.length"
      @retry="load"
    />
    <div class="resource-shelf">
      <article
        v-for="f in rows"
        :key="f.id"
        class="resource-volume"
        :class="{
          'audio-volume': f.mimeType.startsWith('audio/'),
          'image-volume': f.mimeType.startsWith('image/'),
        }"
      >
        <span
          class="resource-spine"
          aria-hidden="true"
        >
          {{ kind(f) }}
        </span>
        <div>
          <small>{{ f.chapterId ? "章节资源" : "全课程资源" }} · {{ size(f.bytes) }}</small>
          <h3>{{ f.title }}</h3>
          <p>{{ f.description || f.originalName }}</p>
          <div class="resource-actions">
            <el-button
              v-if="f.mimeType.startsWith('image/') || f.mimeType.startsWith('audio/')"
              @click="show(f)"
            >
              {{ f.mimeType.startsWith("audio/") ? "听朗读" : "展开图笺" }}
            </el-button>
            <el-button @click="download(f)">下载</el-button>
            <el-button
              v-if="teacher && courseStatus !== 'ARCHIVED'"
              text
              @click="remove(f)"
            >
              下架
            </el-button>
          </div>
        </div>
      </article>
      <div
        v-if="!rows.length && !busy"
        class="resource-empty"
      >
        <span aria-hidden="true">卷</span>
        <p>书架尚待充实，教师可上架本章资料。</p>
      </div>
    </div>
    <ListPager
      v-if="total"
      v-model:page="page"
      :total="total"
      :page-size="12"
      @change="load"
    />
  </section>
  <el-dialog
    :model-value="!!preview"
    :title="preview?.title || '资源预览'"
    width="min(800px,92vw)"
    @close="preview = null"
  >
    <img
      v-if="preview?.mimeType.startsWith('image/')"
      class="resource-image"
      :src="url"
      :alt="preview.title"
    />
    <div
      v-if="preview?.mimeType.startsWith('audio/')"
      class="audio-listening"
    >
      <div
        class="audio-disc"
        aria-hidden="true"
      >
        听
      </div>
      <p>{{ preview.description }}</p>
      <audio
        :src="url"
        controls
        preload="metadata"
      >
        你的浏览器不支持音频播放。
      </audio>
    </div>
  </el-dialog>
  <el-dialog
    v-model="dialog"
    title="上架教学资源"
    width="min(560px,92vw)"
  >
    <form @submit.prevent="upload">
      <div class="form-field">
        <label for="resource-title">资源标题</label>
        <el-input
          id="resource-title"
          v-model="title"
          required
          maxlength="100"
        />
      </div>
      <div class="form-field">
        <label for="resource-chapter">所属章节</label>
        <select
          id="resource-chapter"
          v-model="chapterId"
          class="native-select"
        >
          <option value="">全课程通用</option>
          <option
            v-for="c in chapters"
            :key="c.id"
            :value="c.id"
          >
            {{ c.title }}
          </option>
        </select>
      </div>
      <div class="form-field">
        <label for="resource-description">资源说明</label>
        <el-input
          id="resource-description"
          v-model="description"
          type="textarea"
          maxlength="1000"
        />
      </div>
      <div class="form-field">
        <label for="resource-file">文件</label>
        <input
          id="resource-file"
          type="file"
          accept=".pdf,.png,.jpg,.jpeg,.mp3,.wav,.txt"
          @change="selectFile"
          required
        />
        <small>PDF、图片、MP3/WAV、UTF-8文本，单个不超过20MB。上传有权用于本课程的资料。</small>
      </div>
      <StatePanel
        :error="error"
        @retry="upload"
      />
      <el-button
        native-type="submit"
        type="primary"
        :loading="busy"
      >
        保存资源
      </el-button>
    </form>
  </el-dialog>
</template>
