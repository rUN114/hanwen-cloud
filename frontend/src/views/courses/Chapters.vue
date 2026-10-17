<script setup lang="ts">
import { canTeach, courseRoles } from "../../utils/course-access";
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { ElMessageBox } from "element-plus";
import { api } from "../../api/client";
import { useTask } from "../../composables/useTask";
import type { Course, Chapter } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
import StatusTag from "../../components/common/StatusTag.vue";
const id = String(useRoute().params.courseId);
const course = ref<Course | null>(null);
const rows = ref<Chapter[]>([]);
const { busy, error, run } = useTask();
async function fetchRows() {
  [course.value, rows.value] = await Promise.all([
    api<Course>("/api/courses/" + id),
    api<Chapter[]>("/api/courses/" + id + "/chapters"),
  ]);
}
function load() {
  run(fetchRows);
}
onMounted(load);
function remove(ch: Chapter) {
  run(async () => {
    await ElMessageBox.confirm("删除此草稿章节后无法恢复。", "删除章节", {
      confirmButtonText: "删除",
      cancelButtonText: "保留",
    });
    await api("/api/teacher/courses/" + id + "/chapters/" + ch.id, {
      method: "DELETE",
    });
    await fetchRows();
  });
}
</script>
<template>
  <PageHeading
    :title="course?.name || '章节管理'"
    back="/teacher/courses"
    description="排序号决定阅读顺序；发布后章节集合和顺序固定。"
  >
    <RouterLink
      v-if="canTeach(course) && course?.status === 'DRAFT'"
      :to="'/teacher/courses/' + id + '/chapters/new'"
    >
      <el-button type="primary">新增章节</el-button>
    </RouterLink>
    <RouterLink
      v-if="course && course.status !== 'DRAFT'"
      :to="'/teacher/courses/' + id + '/assignments'"
    >
      <el-button>课程作业</el-button>
    </RouterLink>
  </PageHeading>
  <StatePanel
    :busy="busy && !course"
    :error="error"
    @retry="load"
  />
  <div
    v-if="course"
    class="paper"
  >
    <StatusTag :status="course.status" />
    <p class="field-hint">
      {{
        course.status === "ARCHIVED"
          ? "归档课程的章节只读。"
          : course.status === "PUBLISHED"
            ? "可修改标题和正文，不能新增、删除或调整排序。"
            : "请检查章节内容后，到课程列表发布。"
      }}
    </p>
    <el-table
      :data="rows"
      empty-text="尚无章节，请先新增"
    >
      <el-table-column
        prop="sortNo"
        label="顺序"
        width="90"
      />
      <el-table-column
        prop="title"
        label="章节标题"
        min-width="200"
      />
      <el-table-column
        label="操作"
        min-width="180"
      >
        <template #default="{ row }">
          <RouterLink :to="'/teacher/courses/' + id + '/chapters/' + row.id + '/edit'">
            <el-button size="small">
              {{ course.status === "ARCHIVED" || !canTeach(course) ? "查看" : "编辑" }}
            </el-button>
          </RouterLink>
          <el-button
            v-if="canTeach(course) && course.status === 'DRAFT'"
            size="small"
            type="danger"
            plain
            :disabled="busy"
            @click="remove(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
