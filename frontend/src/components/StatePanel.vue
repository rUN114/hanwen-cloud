<script setup lang="ts">
import type { ApiError } from "../api";
defineProps<{
  busy?: boolean;
  error?: ApiError | null;
  empty?: boolean;
  emptyText?: string;
}>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <div v-if="error" class="error-panel" role="alert">
    <strong>{{
      error.status === 409
        ? "内容或状态已改变"
        : error.status === 403
          ? "无权访问"
          : error.status === 404
            ? "资源不存在"
            : "操作未完成"
    }}</strong>
    <p>{{ error.message }}</p>
    <small v-if="error.traceId">问题编号：{{ error.traceId }}</small
    ><el-button @click="$emit('retry')">重试</el-button>
  </div>
  <el-skeleton v-else-if="busy" :rows="4" animated class="paper loading" />
  <div v-else-if="empty" class="empty-state">
    <img src="/assets/desk.svg" alt="" aria-hidden="true" />
    <p>{{ emptyText || "此处还没有内容" }}</p>
    <slot />
  </div>
</template>
