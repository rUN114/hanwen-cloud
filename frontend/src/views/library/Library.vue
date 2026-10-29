<script setup lang="ts">
import ListPager from "../../components/common/ListPager.vue";
import { ref, watch, computed } from "vue";
import { useRoute } from "vue-router";
import { api } from "../../api/client";
import { useTask } from "../../composables/useTask";
import { session } from "../../stores/session";
import { ElMessageBox } from "element-plus";
import type { Page, Work } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
const route = useRoute();
const mine = computed(() => route.path === "/teacher/literature");
const rows = ref<Work[]>([]);
const keyword = ref("");
const dynasty = ref("");
const genre = ref("");
const page = ref(1);
const total = ref(0);
const { busy, error, run } = useTask();
async function fetchRows() {
  const q = new URLSearchParams({
    page: String(page.value),
    size: "9",
    keyword: keyword.value,
    dynasty: dynasty.value,
    genre: genre.value,
  });
  const p = await api<Page<Work>>(
    (mine.value ? "/api/teacher/literature" : "/api/literature") + "?" + q,
  );
  rows.value = p.items;
  total.value = p.total;
}
function load() {
  run(fetchRows);
}
watch(
  () => route.path,
  () => {
    page.value = 1;
    load();
  },
  { immediate: true },
);
function state(w: Work) {
  run(async () => {
    const status = w.status === "PUBLISHED" ? "ARCHIVED" : "PUBLISHED";
    await ElMessageBox.confirm(
      status === "ARCHIVED"
        ? "归档后其他读者不能阅读正文，已有收藏保留书目。"
        : "重新公开后所有登录读者可阅读。",
      status === "ARCHIVED" ? "归档诗文" : "公开诗文",
      { confirmButtonText: "确认", cancelButtonText: "取消" },
    );
    await api("/api/teacher/literature/" + w.id + "/status", {
      method: "PUT",
      body: { status, expectedVersion: w.version },
    });
    await fetchRows();
  });
}
</script>
<template>
  <PageHeading
    :title="mine ? '先生编选' : '诗文书库'"
    :description="mine ? '把值得共读的篇章编成书目，注明来源与版本。' : '循一缕诗意，寻一篇佳作。'"
  >
    <RouterLink
      v-if="session?.user.role === 'TEACHER'"
      :to="mine ? '/teacher/literature/new' : '/teacher/literature'"
    >
      <el-button type="primary">{{ mine ? "录入诗文" : "管理我的编选" }}</el-button>
    </RouterLink>
    <RouterLink
      v-if="mine"
      to="/library"
    >
      <el-button>公开书库</el-button>
    </RouterLink>
  </PageHeading>
  <section
    v-if="!mine"
    class="library-banner art-scene"
  >
    <span class="small-seal">文脉</span>
    <div>
      <h2>字里山河 · 书中四时</h2>
      <p>诗、文与各自的时代相遇。读原文，记所思，藏下想再读的一篇。</p>
    </div>
    <RouterLink
      class="scene-colophon"
      to="/paintings"
    >
      <span aria-hidden="true">画</span>
      查看书院馆藏
    </RouterLink>
  </section>
  <form
    class="toolbar"
    @submit.prevent="
      page = 1;
      load();
    "
  >
    <el-input
      v-model="keyword"
      placeholder="搜索篇名或作者"
      aria-label="篇名或作者"
      clearable
    />
    <template v-if="!mine">
      <el-input
        v-model="dynasty"
        placeholder="时代，例如唐、宋"
        aria-label="时代"
        clearable
      />
      <el-select
        v-model="genre"
        placeholder="全部体裁"
        aria-label="体裁"
        clearable
      >
        <el-option
          v-for="g in ['诗', '词', '文']"
          :key="g"
          :label="g"
          :value="g"
        />
      </el-select>
    </template>
    <el-button
      native-type="submit"
      :loading="busy"
    >
      寻篇
    </el-button>
  </form>
  <StatePanel
    :busy="busy && !rows.length"
    :error="error"
    :empty="!busy && !error && !rows.length"
    empty-text="未找到诗文，可调整条件或录入新篇。"
    @retry="load"
  />
  <section class="library-grid">
    <article
      v-for="(w, i) in rows"
      :key="w.id"
      class="literature-card"
      :class="'ink-' + (i % 3)"
    >
      <div class="work-ribbon">{{ w.genre }}</div>
      <span
        class="library-corner"
        aria-hidden="true"
      ></span>
      <small>{{ w.dynasty }} · {{ w.author }}</small>
      <RouterLink :to="'/library/' + w.id">
        <h2>{{ w.title }}</h2>
      </RouterLink>
      <div
        class="book-thread"
        aria-hidden="true"
      >
        <i></i>
        <i></i>
        <i></i>
      </div>
      <footer>
        <RouterLink :to="'/library/' + w.id">
          {{ w.status === "ARCHIVED" ? "查看归档" : "展开阅读" }}
          <span aria-hidden="true">→</span>
        </RouterLink>
        <span>
          {{ w.status === "ARCHIVED" ? "已归档" : w.teacherId ? "先生编选" : "古籍选读" }}
        </span>
      </footer>
      <div
        v-if="mine"
        class="form-actions"
      >
        <RouterLink
          v-if="w.status === 'PUBLISHED'"
          :to="'/teacher/literature/' + w.id + '/edit'"
        >
          <el-button size="small">编辑</el-button>
        </RouterLink>
        <el-button
          size="small"
          :disabled="busy"
          @click="state(w)"
        >
          {{ w.status === "PUBLISHED" ? "归档" : "重新公开" }}
        </el-button>
      </div>
    </article>
  </section>
  <ListPager
    v-if="total"
    v-model:page="page"
    :total="total"
    :page-size="9"
    @change="load"
  />
</template>
