<script setup lang="ts">
import PageHeading from "../components/PageHeading.vue";
import { session, home } from "../session";
</script>
<template>
  <PageHeading
    title="此页不在卷中"
    description="地址可能有误，或页面已经移走。"
  />
  <div class="empty-state">
    <img src="/assets/desk.svg" alt="" aria-hidden="true" /><RouterLink
      :to="home(session?.user.role)"
      ><el-button type="primary">返回首页</el-button></RouterLink
    >
  </div>
</template>
