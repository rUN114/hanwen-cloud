<script setup lang="ts">
import ListPager from "../../components/common/ListPager.vue";
import { canTeach, courseRoles } from "../../utils/course-access";
import { ref, computed, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessageBox, ElMessage } from "element-plus";
import { session } from "../../stores/session";
import { api } from "../../api/client";
import { useTask, time } from "../../composables/useTask";
import type { Course, Task, Page, Enrollment } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
import StatusTag from "../../components/common/StatusTag.vue";
import CourseLinks from "../../components/courses/CourseLinks.vue";
import { allPages } from "../../utils/export";
const remindTask = ref<Task | null>(null);
const candidates = ref<{ studentId: string; displayName: string }[]>([]);
const selected = ref<string[]>([]);
const reminder = ref("请在截止前提交习作；如有疑问，可到课堂交流中提问。");
const dialog = ref(false);
const copyDialog = ref(false);
const sources = ref<Course[]>([]);
const sourceId = ref("");
function openReminder(t: Task) {
  run(async () => {
    remindTask.value = t;
    selected.value = [];
    candidates.value = await api("/api/teacher/assignments/" + t.id + "/reminder-candidates");
    dialog.value = true;
  });
}
function sendReminder() {
  run(async () => {
    await ElMessageBox.confirm(
      "将向选中的 " + selected.value.length + " 位未交同窗发送站内提醒。",
      "确认催交",
    );
    const result = await api<{ sent: number; alreadySentToday: number }>(
      "/api/teacher/assignments/" + remindTask.value!.id + "/remind",
      { method: "POST", body: { studentIds: selected.value, content: reminder.value } },
    );
    ElMessage.success(
      "已发送 " + result.sent + " 条，今日已发送 " + result.alreadySentToday + " 条",
    );
    dialog.value = false;
  });
}
function openCopy() {
  run(async () => {
    sources.value = (await allPages<Course>("/api/teacher/courses")).filter(
      (c) => c.id !== courseId.value && canTeach(c),
    );
    copyDialog.value = true;
  });
}
function copyTasks() {
  run(async () => {
    await ElMessageBox.confirm(
      "将源课程全部作业复用为本课程的新草稿，日期需重新填写。重复操作会再次生成草稿。",
      "复用作业",
    );
    const result = await api<{ copied: number }>("/api/teacher/assignments/copy", {
      method: "POST",
      body: { fromCourseId: sourceId.value, toCourseId: courseId.value },
    });
    ElMessage.success("已复用 " + result.copied + " 份作业草稿");
    copyDialog.value = false;
    await fetchData();
  });
}

const route = useRoute();
const teacher = computed(() => session.value?.user.role === "TEACHER");
const courseId = ref(String(route.params.courseId || ""));
const course = ref<Course | null>(null);
const rows = ref<Task[]>([]);
const page = ref(1);
const total = ref(0);
const { busy, error, run } = useTask();
const batchSelected = ref<string[]>([]);
const batchRows = computed(() => rows.value.filter((t) => batchSelected.value.includes(t.id)));
const canPublish = computed(
  () => batchRows.value.length > 0 && batchRows.value.every((t) => t.status === "DRAFT"),
);
const canClose = computed(
  () => batchRows.value.length > 0 && batchRows.value.every((t) => t.status === "PUBLISHED"),
);
function selectPage() {
  batchSelected.value = rows.value.filter((t) => t.status !== "CLOSED").map((t) => t.id);
}
function toggleTask(id: string) {
  batchSelected.value = batchSelected.value.includes(id)
    ? batchSelected.value.filter((x) => x !== id)
    : [...batchSelected.value, id];
}
function batch(action: "PUBLISH" | "CLOSE") {
  run(async () => {
    const picked = batchRows.value;
    if (!picked.length) return;
    await ElMessageBox.confirm(
      "将" +
        (action === "PUBLISH" ? "发布" : "关闭") +
        "当前页选中的 " +
        picked.length +
        " 份作业：\n" +
        picked.map((t) => t.title).join("\n") +
        "\n全部资格核对后一起保存；任何一份变化都会整批取消。",
      action === "PUBLISH" ? "批量发布作业" : "批量关闭作业",
      {
        confirmButtonText: action === "PUBLISH" ? "确认批量发布" : "确认批量关闭",
        cancelButtonText: "继续核对",
      },
    );
    const result = await api<{ changed: number }>("/api/teacher/assignments/batch", {
      method: "POST",
      body: {
        courseId: courseId.value,
        action,
        items: picked.map((t) => ({ id: t.id, status: t.status, updatedAt: t.updatedAt })),
      },
    });
    await fetchData();
    ElMessage.success(
      "已" + (action === "PUBLISH" ? "发布" : "关闭") + " " + result.changed + " 份作业",
    );
  });
}
async function fetchData() {
  const location = route.fullPath;
  const requestedPage = page.value;
  let target = String(route.params.courseId || "");
  if (!teacher.value) {
    const e = await api<Enrollment>("/api/enrollments/" + String(route.params.enrollmentId));
    target = e.courseId;
  }
  const nextCourse = await api<Course>("/api/courses/" + target);
  const p = await api<Page<Task>>(
    "/api/courses/" + target + "/assignments?page=" + requestedPage + "&size=10",
  );
  if (location !== route.fullPath) return;
  courseId.value = target;
  course.value = nextCourse;
  rows.value = p.items;
  batchSelected.value = [];
  total.value = p.total;
}
function load() {
  run(fetchData).finally(() => {
    if (pendingRoute) {
      pendingRoute = false;
      load();
    }
  });
}
let pendingRoute = false;
watch(
  () => route.fullPath,
  () => {
    page.value = 1;
    rows.value = [];
    course.value = null;
    batchSelected.value = [];
    total.value = 0;
    if (busy.value) pendingRoute = true;
    else load();
  },
  { immediate: true },
);
function transition(t: Task, publish: boolean) {
  run(async () => {
    await ElMessageBox.confirm(
      publish ? "发布后题目、满分与截止时间固定。" : "关闭后不能提交或修改答案，已有提交仍可批改。",
      publish ? "发布作业" : "关闭作业",
      {
        confirmButtonText: publish ? "确认发布" : "确认关闭",
        cancelButtonText: "取消",
      },
    );
    await api("/api/teacher/assignments/" + t.id + (publish ? "/publish" : "/close"), {
      method: "POST",
    });
    await fetchData();
  });
}
</script>
<template>
  <PageHeading
    :title="(course?.name || '课程') + ' · 作业'"
    :back="teacher ? '/teacher/courses' : '/student/enrollments'"
    description="文字作业与作品阅读分析。"
  >
    <RouterLink
      v-if="teacher && canTeach(course) && course?.status === 'PUBLISHED'"
      :to="'/teacher/courses/' + courseId + '/assignments/new'"
    >
      <el-button type="primary">新建作业</el-button>
    </RouterLink>
  </PageHeading>
  <el-button
    v-if="teacher && canTeach(course) && course?.status === 'PUBLISHED'"
    @click="openCopy"
  >
    复用其他课程作业
  </el-button>
  <StatePanel
    :busy="busy && !course"
    :error="error"
    :empty="!busy && !error && !!course && !rows.length"
    empty-text="课程尚无可见作业"
    @retry="load"
  />
  <CourseLinks
    v-if="course"
    :course-id="course.id"
  />
  <div
    v-if="course?.status === 'ARCHIVED'"
    class="read-only-note"
  >
    课程已归档，历史作业与提交可查阅；教师仍可批改既有答案。
  </div>
  <section
    v-if="teacher && canTeach(course) && course?.status === 'PUBLISHED' && rows.length"
    class="batch-task-bar"
    aria-label="批量管理作业"
  >
    <strong>当前页已选择 {{ batchSelected.length }} 份</strong>
    <el-button
      :disabled="busy"
      @click="selectPage"
    >
      选择本页可操作作业
    </el-button>
    <el-button
      :disabled="busy || !batchSelected.length"
      @click="batchSelected = []"
    >
      清空选择
    </el-button>
    <el-button
      type="primary"
      :disabled="busy || !canPublish"
      @click="batch('PUBLISH')"
    >
      批量发布
    </el-button>
    <el-button
      :disabled="busy || !canClose"
      @click="batch('CLOSE')"
    >
      批量关闭
    </el-button>
    <small v-if="batchSelected.length && !canPublish && !canClose">
      请分别选择草稿或已发布作业。
    </small>
  </section>
  <article
    v-for="t in rows"
    :key="t.id"
    class="paper"
  >
    <div class="toolbar">
      <input
        v-if="
          teacher && canTeach(course) && course?.status === 'PUBLISHED' && t.status !== 'CLOSED'
        "
        class="task-check"
        type="checkbox"
        :checked="batchSelected.includes(t.id)"
        :disabled="busy"
        :aria-label="'选择作业 ' + t.title"
        @change="toggleTask(t.id)"
      />
      <h2 style="margin: 0; flex: 1">{{ t.title }}</h2>
      <StatusTag :status="t.status" />
    </div>
    <p class="plain-text">{{ t.description }}</p>
    <p class="field-hint">
      满分 {{ t.maxScore }} ·
      {{ t.deadlineAt ? "截止（北京时间） " + time(t.deadlineAt) : "未设置截止" }}
    </p>
    <div class="course-card-actions">
      <template v-if="teacher">
        <RouterLink
          v-if="canTeach(course) && t.status === 'DRAFT' && course?.status === 'PUBLISHED'"
          :to="'/teacher/assignments/' + t.id + '/edit'"
        >
          <el-button>编辑草稿</el-button>
        </RouterLink>
        <el-button
          v-if="canTeach(course) && t.status === 'DRAFT' && course?.status === 'PUBLISHED'"
          type="primary"
          :disabled="busy"
          @click="transition(t, true)"
        >
          发布作业
        </el-button>
        <RouterLink :to="'/teacher/assignments/' + t.id + '/submissions'">
          <el-button :type="t.status !== 'DRAFT' ? 'primary' : 'default'">查看提交</el-button>
        </RouterLink>
        <el-button
          v-if="t.status === 'PUBLISHED' && course?.status === 'PUBLISHED'"
          :disabled="busy"
          @click="openReminder(t)"
        >
          定向催交
        </el-button>
        <el-button
          v-if="canTeach(course) && t.status === 'PUBLISHED' && course?.status === 'PUBLISHED'"
          :disabled="busy"
          @click="transition(t, false)"
        >
          关闭作业
        </el-button>
      </template>
      <RouterLink
        v-else
        :to="'/student/assignments/' + t.id + '/submit'"
      >
        <el-button type="primary">查看与提交</el-button>
      </RouterLink>
    </div>
  </article>
  <ListPager
    v-if="total"
    v-model:page="page"
    :disabled="busy"
    :total="total"
    :page-size="10"
    @change="load"
  />
  <el-dialog
    v-model="dialog"
    title="先生叮嘱 · 定向催交"
    width="650px"
  >
    <p>只列出本课程尚未交卷的学生。同一作业、同一学生每天至多一条。</p>
    <el-checkbox-group
      v-model="selected"
      class="recipient-list"
    >
      <el-checkbox
        v-for="c in candidates"
        :key="c.studentId"
        :value="c.studentId"
      >
        {{ c.displayName }}
      </el-checkbox>
    </el-checkbox-group>
    <p v-if="!candidates.length">同窗都已交卷，无需催交。</p>
    <label class="form-field">
      提醒内容
      <el-input
        v-model="reminder"
        type="textarea"
        :rows="4"
        maxlength="500"
      />
    </label>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button
        type="primary"
        :disabled="!selected.length"
        :loading="busy"
        @click="sendReminder"
      >
        发送站内提醒
      </el-button>
    </template>
  </el-dialog>
  <el-dialog
    v-model="copyDialog"
    title="复用作业"
    width="600px"
  >
    <p>只复制题目、要求、满分与评分细则；每份作业须重新设置日期并发布。</p>
    <el-select
      v-model="sourceId"
      aria-label="源课程"
      placeholder="选择自己的源课程"
    >
      <el-option
        v-for="c in sources"
        :key="c.id"
        :label="c.name"
        :value="c.id"
      />
    </el-select>
    <template #footer>
      <el-button @click="copyDialog = false">取消</el-button>
      <el-button
        :disabled="!sourceId"
        :loading="busy"
        type="primary"
        @click="copyTasks"
      >
        复用为草稿
      </el-button>
    </template>
  </el-dialog>
</template>
