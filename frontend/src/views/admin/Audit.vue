<script setup lang="ts">
import { ref, onMounted } from "vue";
import { api } from "../../api/client";
import { useTask, time } from "../../composables/useTask";
import type { Page } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
interface Event {
  id: string;
  actorName: string;
  targetName: string | null;
  action: string;
  summary: string;
  traceId: string;
  createdAt: string;
}
const rows = ref<Event[]>([]);
const page = ref(1);
const total = ref(0);
const { busy, error, run } = useTask();
const labels: Record<string, string> = {
  LOGIN: "入院",
  LOGOUT: "退出",
  CREATE_USER: "开户",
  IMPORT_USERS: "入册",
  ACCESS_CHANGE: "权限",
  PASSWORD_RESET: "重置",
  PASSWORD_CHANGE: "改密",
};
function load() {
  run(async () => {
    const p = await api<Page<Event>>("/api/admin/audit?page=" + page.value + "&size=20");
    rows.value = p.items;
    total.value = p.total;
  });
}
onMounted(load);
</script>
<template>
  <PageHeading
    title="书院事务录"
    description="按实际发生顺序记录开户、权限和登录事务；不记录密码、登录凭据或作业正文。"
  >
    <el-button
      @click="load"
      :loading="busy"
    >
      刷新记录
    </el-button>
  </PageHeading>
  <StatePanel
    :error="error"
    :busy="busy && !rows.length"
    :empty="!busy && !error && !rows.length"
    empty-text="尚无事务记录，开户或登录后会在这里出现。"
    @retry="load"
  />
  <section
    class="audit-scroll"
    aria-label="账号事务记录"
  >
    <article
      v-for="item in rows"
      :key="item.id"
      class="audit-entry"
    >
      <span class="audit-stamp">{{ labels[item.action] || "事务" }}</span>
      <div>
        <time>{{ time(item.createdAt) }}</time>
        <h2>{{ item.summary }}</h2>
        <p>
          操作人 {{ item.actorName || "已停用账号" }}
          <span v-if="item.targetName">· 相关账号 {{ item.targetName }}</span>
        </p>
        <details>
          <summary>查看问题编号</summary>
          <code>{{ item.traceId }}</code>
        </details>
      </div>
    </article>
  </section>
  <el-pagination
    v-if="total"
    v-model:current-page="page"
    :total="total"
    :page-size="20"
    layout="prev,pager,next,total"
    @current-change="load"
  />
</template>
