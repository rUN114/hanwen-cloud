<script setup lang="ts">
import PageHeading from "../../components/common/PageHeading.vue";
import { home } from "../../stores/session";
</script>
<template>
  <PageHeading
    title="此页不在卷中"
    description="地址可能有误，或页面已经移走。"
  />
  <div class="empty-state">
    <img
      src="/assets/desk.svg"
      alt=""
      aria-hidden="true"
    />
    <RouterLink :to="home()">
      <el-button type="primary">返回首页</el-button>
    </RouterLink>
  </div>
</template>
