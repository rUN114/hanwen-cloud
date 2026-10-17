<script setup lang="ts">
import { ref, onMounted } from "vue";
import { api } from "../../api/client";
import { useTask, time } from "../../composables/useTask";
import { ElMessageBox } from "element-plus";
import type { Page, Favorite } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
const rows = ref<Favorite[]>([]);
const page = ref(1);
const total = ref(0);
const { busy, error, run } = useTask();
async function fetchRows() {
  const p = await api<Page<Favorite>>("/api/favorites?page=" + page.value + "&size=9");
  rows.value = p.items;
  total.value = p.total;
}
function load() {
  run(fetchRows);
}
onMounted(load);
function remove(w: Favorite) {
  run(async () => {
    await ElMessageBox.confirm("将《" + w.title + "》移出你的藏书？", "移出藏书", {
      confirmButtonText: "移出",
      cancelButtonText: "保留",
    });
    await api("/api/favorites/" + w.workId, { method: "DELETE" });
    await fetchRows();
  });
}
</script>
<template>
  <PageHeading
    title="我的藏书"
    description="重读一篇好文字，让相遇成为长久。"
  >
    <RouterLink to="/library"><el-button type="primary">去诗文书库</el-button></RouterLink>
  </PageHeading>
  <StatePanel
    :busy="busy && !rows.length"
    :error="error"
    :empty="!busy && !error && !rows.length"
    empty-text="藏书夹尚空，读到喜欢的诗文时可收入藏书。"
    @retry="load"
  />
  <section class="library-grid">
    <article
      v-for="w in rows"
      :key="w.workId"
      class="literature-card ink-1"
    >
      <span class="small-seal">藏</span>
      <small>{{ w.dynasty }} · {{ w.author }}</small>
      <h2>{{ w.title }}</h2>
      <p class="field-hint">收藏于 {{ time(w.createdAt) }}</p>
      <p
        v-if="w.status !== 'PUBLISHED'"
        class="read-only-note"
      >
        作品已归档或暂不可用，保留收藏书目。
      </p>
      <div class="form-actions">
        <RouterLink
          v-if="w.status === 'PUBLISHED'"
          :to="'/library/' + w.workId"
        >
          <el-button type="primary">展开阅读</el-button>
        </RouterLink>
        <el-button
          :disabled="busy"
          @click="remove(w)"
        >
          移出藏书
        </el-button>
      </div>
    </article>
  </section>
  <el-pagination
    v-if="total"
    v-model:current-page="page"
    :total="total"
    :page-size="9"
    layout="prev,pager,next,total"
    @current-change="load"
  />
</template>
