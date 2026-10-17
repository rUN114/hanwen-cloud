<script setup lang="ts">
import { statusNames } from "../../composables/useTask";
defineProps<{ status: string }>();
</script>
<template>
  <span
    class="status-tag"
    :class="status.toLowerCase()"
  >
    {{ statusNames[status] || status }}
  </span>
</template>
