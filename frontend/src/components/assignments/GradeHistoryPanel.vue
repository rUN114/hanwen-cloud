<script setup lang="ts">
import { ref, watch } from "vue";
import { api } from "../../api/client";
import { useTask, time } from "../../composables/useTask";
import type { GradeRevision, Page } from "../../types/models";
import StatePanel from "../common/StatePanel.vue";
const props = defineProps<{ submissionId: string; version?: number }>();
const rows = ref<GradeRevision[]>([]);
const page = ref(1);
const total = ref(0);
const { busy, error, run } = useTask();
function load() {
  run(async () => {
    const p = await api<Page<GradeRevision>>(
      "/api/submissions/" + props.submissionId + "/grade-history?page=" + page.value,
    );
    rows.value = p.items;
    total.value = p.total;
  });
}
watch(
  () => [props.submissionId, props.version],
  () => {
    page.value = 1;
    load();
  },
  { immediate: true },
);
</script>
<template>
  <section class="paper grade-history">
    <h2>批阅留痕</h2>
    <p class="field-hint">保存每次评分及更正。旧版答卷从升级时的当前评分开始记录。</p>
    <StatePanel
      :error="error"
      @retry="load"
    />
    <ol>
      <li
        v-for="r in rows"
        :key="r.id"
      >
        <div
          class="revision-marker"
          aria-hidden="true"
        >
          阅
        </div>
        <div>
          <strong>{{ r.teacherName }} · {{ r.score }} 分</strong>
          <small>答卷版本 {{ r.submissionVersion }} · {{ time(r.gradedAt) }}</small>
          <p
            v-if="r.rubricScores?.length"
            class="field-hint"
          >
            分项得分：{{ r.rubricScores.join(" / ") }}（按题目细则顺序）
          </p>
          <p class="plain-text">{{ r.comment || "未填写评语" }}</p>
        </div>
      </li>
    </ol>
    <p v-if="!rows.length && !busy">尚无评分记录。</p>
    <el-pagination
      v-if="total"
      v-model:current-page="page"
      :total="total"
      :page-size="20"
      layout="prev,pager,next,total"
      @current-change="load"
    />
  </section>
</template>
