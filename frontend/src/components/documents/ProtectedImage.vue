<script setup lang="ts">
import { ref, watch, onUnmounted } from "vue";
import { apiFile } from "../../api/client";
const props = defineProps<{ id: string; alt: string }>();
const url = ref("");
const error = ref("");
let generation = 0;
watch(
  () => props.id,
  async () => {
    const n = ++generation;
    error.value = "";
    if (url.value) URL.revokeObjectURL(url.value);
    url.value = "";
    try {
      const blob = await apiFile("/api/resources/" + props.id + "/file");
      if (n !== generation) return;
      if (!["image/png", "image/jpeg"].includes(blob.type)) throw Error("此资源不是图片");
      url.value = URL.createObjectURL(blob);
    } catch (e) {
      if (n === generation) error.value = e instanceof Error ? e.message : "图片暂不可读";
    }
  },
  { immediate: true },
);
onUnmounted(() => {
  generation++;
  if (url.value) URL.revokeObjectURL(url.value);
});
</script>
<template>
  <figure class="lesson-figure">
    <img
      v-if="url"
      :src="url"
      :alt="alt"
    />
    <p
      v-else
      role="status"
    >
      {{ error || "图片加载中" }}
    </p>
    <figcaption>{{ alt }}</figcaption>
  </figure>
</template>
