<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { api, apiFile, saveBlob } from "../../api/client";
import { useTask } from "../../composables/useTask";
import { ElMessageBox } from "element-plus";
import StatePanel from "../common/StatePanel.vue";
import type { Submission } from "../../types/models";
interface FileInfo {
  id: string;
  originalName: string;
  bytes: number;
  sha256: string;
}
const props = defineProps<{ submissionId?: string; assignmentId?: string; writable?: boolean }>();
const assignment = ref("");
const rows = ref<FileInfo[]>([]);
const file = ref<File | null>(null);
const input = ref<HTMLInputElement | null>(null);
const { busy, error, run } = useTask();
const path = computed(() => "/api/assignments/" + assignment.value + "/attachments");
function load() {
  run(async () => {
    assignment.value =
      props.assignmentId ||
      (props.submissionId
        ? (await api<Submission>("/api/submissions/" + props.submissionId)).assignmentId
        : "");
    if (assignment.value)
      rows.value = await api<FileInfo[]>(
        path.value + (props.submissionId ? "?submissionId=" + props.submissionId : ""),
      );
  });
}
watch(() => [props.submissionId, props.assignmentId], load, { immediate: true });
function upload() {
  run(async () => {
    if (!file.value) throw Error("请选择附件");
    if (file.value.size > 20 * 1024 * 1024) throw Error("附件不能超过20MB");
    const data = new FormData();
    data.append("file", file.value);
    await api(path.value, { method: "POST", body: data });
    file.value = null;
    if (input.value) input.value.value = "";
    rows.value = await api<FileInfo[]>(path.value);
  });
}
function read(f: FileInfo) {
  run(async () => {
    saveBlob(await apiFile("/api/submissions/attachments/" + f.id + "/file"), f.originalName);
  });
}
function remove(f: FileInfo) {
  run(async () => {
    await ElMessageBox.confirm("移除附件 " + f.originalName + "？", "移除附件");
    await api("/api/submissions/attachments/" + f.id, { method: "DELETE" });
    rows.value = rows.value.filter((x) => x.id !== f.id);
  });
}
</script>
<template>
  <section class="attachment-panel">
    <h3>随卷附件</h3>
    <StatePanel
      :error="error"
      @retry="load"
    />
    <ul>
      <li
        v-for="f in rows"
        :key="f.id"
      >
        <span>
          {{ f.originalName }}
          <small>{{ (f.bytes / 1024).toFixed(1) }} KB</small>
        </span>
        <el-button
          :loading="busy"
          @click="read(f)"
        >
          下载
        </el-button>
        <el-button
          v-if="writable"
          :disabled="busy"
          @click="remove(f)"
        >
          移除
        </el-button>
      </li>
    </ul>
    <p
      v-if="!rows.length"
      class="field-hint"
    >
      尚无随卷附件。
    </p>
    <template v-if="writable">
      <input
        ref="input"
        type="file"
        accept=".pdf,.png,.jpg,.jpeg,.txt,.mp3,.wav"
        aria-label="选择习作附件"
        @change="file = ($event.target as HTMLInputElement).files?.[0] || null"
      />
      <el-button
        :loading="busy"
        :disabled="!file || rows.length >= 5"
        @click="upload"
      >
        上传附件
      </el-button>
      <p class="field-hint">
        最多5份，每份20MB。支持PDF、图片、UTF-8文本与音频；上传后保存到本人习作，评分后锁定。
      </p>
    </template>
  </section>
</template>
