<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../../api/client";
import { useTask } from "../../composables/useTask";
import { ElMessage } from "element-plus";
import type { Work, Allusion } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
const route = useRoute();
const router = useRouter();
const id = computed(() => (route.params.id ? String(route.params.id) : null));
const version = ref<number | undefined>();
const form = ref({
  title: "",
  author: "",
  dynasty: "",
  genre: "诗",
  content: "",
  readingGuide: "",
  sourceUrl: "",
  sourceNote: "",
  translation: "",
  annotationsText: "",
  allusions: [] as Allusion[],
});
const { busy, error, run } = useTask();
function load() {
  run(async () => {
    if (id.value) {
      const w = await api<Work>("/api/literature/" + id.value);
      form.value = {
        title: w.title,
        author: w.author,
        dynasty: w.dynasty,
        genre: w.genre,
        content: w.content || "",
        readingGuide: w.readingGuide || "",
        sourceUrl: w.sourceUrl || "",
        sourceNote: w.sourceNote || "",
        translation: w.translation || "",
        annotationsText: w.annotationsText || "",
        allusions: w.allusions || [],
      };
      version.value = w.version;
    }
  });
}
watch(() => route.params.id, load, { immediate: true });
function save() {
  run(async () => {
    await api("/api/teacher/literature" + (id.value ? "/" + id.value : ""), {
      method: id.value ? "PUT" : "POST",
      body: {
        ...form.value,
        allusions: form.value.allusions.map((x) => ({
          ...x,
          workId: x.workId || null,
          sourceUrl: x.sourceUrl || null,
        })),
        expectedVersion: version.value,
      },
    });
    ElMessage.success("诗文已保存并公开");
    await router.push("/teacher/literature");
  });
}
</script>
<template>
  <PageHeading
    :title="id ? '修订诗文' : '录入诗文'"
    back="/teacher/literature"
    description="保存后公开，请认真核对正文、署名与版本。"
  />
  <StatePanel
    :busy="busy"
    :error="error"
    @retry="load"
  />
  <form
    class="paper form-paper"
    @submit.prevent="save"
  >
    <div class="form-columns">
      <label class="form-field">
        篇名
        <el-input
          v-model="form.title"
          maxlength="100"
          required
        />
      </label>
      <label class="form-field">
        作者
        <el-input
          v-model="form.author"
          maxlength="50"
          required
        />
      </label>
      <label class="form-field">
        时代
        <el-input
          v-model="form.dynasty"
          maxlength="30"
          required
          placeholder="例如：唐、宋、现代"
        />
      </label>
      <label class="form-field">
        体裁
        <el-select v-model="form.genre">
          <el-option
            v-for="g in ['诗', '词', '文']"
            :key="g"
            :label="g"
            :value="g"
          />
        </el-select>
      </label>
    </div>
    <label class="form-field">
      作品正文
      <el-input
        v-model="form.content"
        type="textarea"
        :rows="10"
        maxlength="20000"
        required
        placeholder="按诗句分行，保留原作措辞。"
      />
    </label>
    <label class="form-field">
      阅读提示（选填）
      <el-input
        v-model="form.readingGuide"
        type="textarea"
        :rows="4"
        maxlength="5000"
      />
    </label>
    <label class="form-field">
      来源链接（选填）
      <el-input
        v-model="form.sourceUrl"
        type="url"
        maxlength="500"
        placeholder="https://"
      />
    </label>
    <label class="form-field">
      版本说明（选填）
      <el-input
        v-model="form.sourceNote"
        type="textarea"
        :rows="2"
        maxlength="500"
      />
    </label>
    <section class="literature-editor-notes">
      <h2>译注与典故</h2>
      <label class="form-field">
        译文（与原文分开，注明编写者）
        <el-input
          v-model="form.translation"
          type="textarea"
          :rows="7"
          maxlength="20000"
          aria-label="译文"
        />
      </label>
      <label class="form-field">
        字词注释
        <el-input
          v-model="form.annotationsText"
          type="textarea"
          :rows="5"
          maxlength="10000"
          aria-label="字词注释"
        />
      </label>
      <fieldset
        v-for="(item, index) in form.allusions"
        :key="index"
        class="allusion-edit"
      >
        <legend>典故 {{ index + 1 }}</legend>
        <label class="form-field">
          名称
          <el-input
            v-model="item.term"
            maxlength="80"
          />
        </label>
        <label class="form-field">
          释义
          <el-input
            v-model="item.explanation"
            type="textarea"
            maxlength="2000"
          />
        </label>
        <label class="form-field">
          关联公开作品编号（选填）
          <el-input
            v-model="item.workId"
            inputmode="numeric"
          />
        </label>
        <label class="form-field">
          来源网址（选填）
          <el-input
            v-model="item.sourceUrl"
            type="url"
          />
        </label>
        <el-button @click="form.allusions.splice(index, 1)">移除典故</el-button>
      </fieldset>
      <el-button
        v-if="form.allusions.length < 20"
        @click="form.allusions.push({ term: '', explanation: '', workId: null, sourceUrl: null })"
      >
        添加典故
      </el-button>
    </section>
    <div class="form-actions">
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
      >
        保存并公开
      </el-button>
      <RouterLink to="/teacher/literature"><el-button>取消</el-button></RouterLink>
    </div>
  </form>
</template>
