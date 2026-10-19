<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { useRoute } from "vue-router";
import { api } from "../../api/client";
import { session } from "../../stores/session";
import { useTask } from "../../composables/useTask";
import { download } from "../../utils/export";
import type { Work } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
const route = useRoute();
const work = ref<Work | null>(null);
const favorited = ref(false);
const vertical = ref(false);
const large = ref(false);
const compare = ref(true);
const student = computed(() => session.value!.user.role === "STUDENT");
const poem = computed(() => ["诗", "词"].includes(work.value?.genre || ""));
const { busy, error, run } = useTask();
function load() {
  run(async () => {
    work.value = await api<Work>("/api/literature/" + route.params.id);
    if (student.value)
      favorited.value = (
        await api<{ favorited: boolean }>("/api/favorites/" + route.params.id)
      ).favorited;
  });
}
watch(() => route.params.id, load, { immediate: true });
function favorite() {
  run(async () => {
    await api("/api/favorites" + (favorited.value ? "/" + work.value!.id : ""), {
      method: favorited.value ? "DELETE" : "POST",
      body: favorited.value ? undefined : { workId: work.value!.id },
    });
    favorited.value = !favorited.value;
  });
}
function exportWork() {
  const w = work.value!;
  download(
    w.title +
      "\n" +
      w.dynasty +
      " · " +
      w.author +
      "\n\n" +
      w.content +
      "\n\n阅读提示（项目编写）\n" +
      (w.readingGuide || "") +
      "\n\n来源：" +
      (w.sourceUrl || "由编选教师录入") +
      "\n版本说明：" +
      (w.sourceNote || ""),
    w.title + ".txt",
  );
}
</script>
<template>
  <PageHeading
    title="展卷"
    back="/library"
  >
    <el-button
      v-if="student && work"
      :loading="busy"
      :disabled="!favorited && work.status !== 'PUBLISHED'"
      @click="favorite"
    >
      {{ favorited ? "移出藏书" : "收入藏书" }}
    </el-button>
    <el-button
      v-if="work"
      @click="exportWork"
    >
      导出原文
    </el-button>
  </PageHeading>
  <StatePanel
    :busy="busy && !work"
    :error="error"
    @retry="load"
  />
  <div
    v-if="work"
    class="literature-reading"
  >
    <article class="paper poem-sheet">
      <header class="poem-heading">
        <span class="small-seal">{{ work.genre }}</span>
        <h1>{{ work.title }}</h1>
        <p>{{ work.dynasty }} · {{ work.author }}</p>
        <div class="reading-tools">
          <button
            v-if="poem"
            :aria-pressed="vertical"
            @click="vertical = !vertical"
          >
            {{ vertical ? "横排阅读" : "竖排阅读" }}
          </button>
          <button
            :aria-pressed="large"
            @click="large = !large"
          >
            {{ large ? "常规字号" : "大字阅读" }}
          </button>
        </div>
      </header>
      <button
        v-if="work.translation"
        class="compare-toggle"
        :aria-pressed="compare"
        @click="compare = !compare"
      >
        {{ compare ? "只读原文" : "原文译文对照" }}
      </button>
      <div
        class="literature-parallel"
        :class="{ comparing: compare && work.translation }"
      >
        <div
          class="classic-text"
          :class="{ vertical: vertical && poem, large, prose: !poem }"
        >
          {{ work.content }}
        </div>
        <section
          v-if="compare && work.translation"
          class="translation-sheet"
        >
          <h2>参考译意</h2>
          <p class="plain-text">{{ work.translation }}</p>
          <small>参考译意由编选者撰写，请结合原文与版本说明阅读。</small>
        </section>
      </div>
      <section
        v-if="work.annotationsText"
        class="word-notes"
      >
        <h2>字词笺注</h2>
        <p class="plain-text">{{ work.annotationsText }}</p>
      </section>
      <footer class="poem-colophon">
        <span>锦卷书院 · {{ work.teacherId ? "先生编选" : "古籍选读" }}</span>
        <span v-if="work.status === 'ARCHIVED'">已归档</span>
      </footer>
    </article>
    <aside class="paper reading-companion">
      <h2>读有所思</h2>
      <p class="plain-text">
        {{ work.readingGuide || "阅读之后，试着用自己的语言解释作品中最打动你的句子。" }}
      </p>
      <small class="field-hint">阅读提示由编选者撰写，也欢迎提出有原文依据的不同理解。</small>
      <section
        v-if="work.allusions?.length"
        class="allusion-shelf"
      >
        <h3>典故寻踪</h3>
        <article
          v-for="x in work.allusions"
          :key="x.term"
        >
          <h4>{{ x.term }}</h4>
          <p class="plain-text">{{ x.explanation }}</p>
          <RouterLink
            v-if="x.workId"
            :to="'/library/' + x.workId"
          >
            展卷关联作品
          </RouterLink>
          <a
            v-if="x.sourceUrl"
            :href="x.sourceUrl"
            target="_blank"
            rel="noopener noreferrer"
          >
            阅读出处
          </a>
        </article>
      </section>
      <div class="source-record">
        <h3>版本与来源</h3>
        <p class="plain-text">
          {{ work.sourceNote || "编选教师尚未补充版本说明。" }}
        </p>
        <a
          v-if="work.sourceUrl"
          :href="work.sourceUrl"
          target="_blank"
          rel="noopener noreferrer"
        >
          查看来源原页 ↗
        </a>
        <p
          v-else
          class="field-hint"
        >
          当前条目未填写来源链接。
        </p>
      </div>
      <RouterLink
        v-if="student"
        to="/student/favorites"
      >
        查看我的藏书 →
      </RouterLink>
    </aside>
  </div>
</template>
