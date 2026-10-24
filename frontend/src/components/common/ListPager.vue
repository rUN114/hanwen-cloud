<script setup lang="ts">
const page = defineModel<number>("page", { required: true });
defineProps<{ total: number; pageSize: number }>();
const emit = defineEmits<{ change: [value: number] }>();
</script>

<template>
  <el-pagination
    v-model:current-page="page"
    :total="total"
    :page-size="pageSize"
    layout="prev,pager,next,total"
    @current-change="(value: number) => emit('change', value)"
  />
</template>
