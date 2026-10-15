<script setup lang="ts">
import { computed } from 'vue';
import { session } from '../session';
const props = defineProps<{ courseId: string; active?: string }>();
const links = computed(() => [
  ['书案', '/student/courses/' + props.courseId, 'workspace'],
  ['课程总评', '/courses/' + props.courseId + '/gradebook', 'gradebook'],
  ['章节练习', '/courses/' + props.courseId + '/quizzes', 'practice'],
  ['资源书架', '/courses/' + props.courseId + '/resources', 'resources'],
  ['公告与答疑', '/courses/' + props.courseId + '/community', 'community'],
]);
</script>
<template>
  <nav v-if="session?.user.role === 'STUDENT'" class="course-ribbon" aria-label="本课程导航">
    <RouterLink v-for="[label, path, name] in links" :key="name" :to="path!" :aria-current="active === name ? 'page' : undefined">{{ label }}</RouterLink>
  </nav>
</template>
