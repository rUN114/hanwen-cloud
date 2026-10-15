<script setup lang="ts">
defineProps<{ title: string; description?: string; back?: string }>();
</script>
<template>
  <header class="page-heading">
    <div>
      <RouterLink v-if="back" class="back-link" :to="back">返回</RouterLink>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div class="heading-actions"><slot /></div>
    <img
      class="heading-plum"
      src="/assets/plum.svg"
      alt=""
      aria-hidden="true"
    />
  </header>
</template>
