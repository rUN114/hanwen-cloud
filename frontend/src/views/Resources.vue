<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api";
import {canTeach} from "../course-access";
import { useTask } from "../useTask";
import type { Course } from "../models";
import ResourcePanel from "../components/ResourcePanel.vue";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
import CourseLinks from "../components/CourseLinks.vue";
const id = String(useRoute().params.courseId),
  course = ref<Course | null>(null);
const { busy, error, run } = useTask();
function load() {
  run(async () => {
    course.value = await api<Course>("/api/courses/" + id);
  });
}
onMounted(load);
</script>
<template>
  <PageHeading
    :title="course?.name || '资源书架'"
    back="/classes"
    description="案上有书，耳畔有声。"
  /><StatePanel
    :busy="busy && !course"
    :error="error"
    @retry="load"
  /><CourseLinks v-if="course" :course-id="id" active="resources" /><ResourcePanel
    v-if="course"
    :course-id="id"
    :course-status="course.status" :can-edit="canTeach(course)"
  />
</template>
