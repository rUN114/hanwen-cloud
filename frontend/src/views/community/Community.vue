<script setup lang="ts">
import ListPager from "../../components/common/ListPager.vue";
import { canTeach, courseRoles } from "../../utils/course-access";
import { ref, watch, computed } from "vue";
import { useRoute } from "vue-router";
import { api } from "../../api/client";
import { session } from "../../stores/session";
import { useTask, time } from "../../composables/useTask";
import { ElMessage, ElMessageBox } from "element-plus";
import type { Course, Page, Notice, Question, Chapter } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
import CourseLinks from "../../components/courses/CourseLinks.vue";
const route = useRoute();
const id = computed(() => String(route.params.courseId));
const teacher = computed(() => session.value!.user.role === "TEACHER");
const course = ref<Course | null>(null);
const tab = ref(route.query.tab === "questions" ? "questions" : "notices");
const notices = ref<Notice[]>([]);
const questions = ref<Question[]>([]);
const chapters = ref<Chapter[]>([]);
const page = ref(1);
const total = ref(0);
const keyword = ref("");
const resolved = ref("");
const editing = ref<Notice | null>(null);
const showForm = ref(false);
const form = ref({ title: "", content: "", pinned: false, chapterId: "" });
const { busy, error, run } = useTask();
const writable = computed(() => course.value?.status === "PUBLISHED");
async function fetchRows() {
  const q = new URLSearchParams({
    page: String(page.value),
    size: "8",
    keyword: keyword.value,
  });
  if (resolved.value) q.set("resolved", resolved.value);
  if (tab.value === "notices") {
    const p = await api<Page<Notice>>("/api/courses/" + id.value + "/notices?" + q);
    notices.value = p.items;
    total.value = p.total;
  } else {
    const p = await api<Page<Question>>("/api/courses/" + id.value + "/questions?" + q);
    questions.value = p.items;
    total.value = p.total;
  }
}
function load() {
  run(async () => {
    [course.value, chapters.value] = await Promise.all([
      api<Course>("/api/courses/" + id.value),
      api<Chapter[]>("/api/courses/" + id.value + "/chapters"),
    ]);
    await fetchRows();
  });
}
watch(
  () => route.params.courseId,
  () => {
    page.value = 1;
    showForm.value = false;
    load();
  },
  { immediate: true },
);
function switchTab(value: string) {
  tab.value = value;
  page.value = 1;
  showForm.value = false;
  run(fetchRows);
}
function start(n?: Notice) {
  editing.value = n || null;
  form.value = {
    title: n?.title || "",
    content: n?.content || "",
    pinned: n?.pinned || false,
    chapterId: "",
  };
  showForm.value = true;
}
function save() {
  run(async () => {
    if (tab.value === "notices") {
      await api(
        "/api/teacher/courses/" +
          id.value +
          "/notices" +
          (editing.value ? "/" + editing.value.id : ""),
        {
          method: editing.value ? "PUT" : "POST",
          body: {
            title: form.value.title,
            content: form.value.content,
            pinned: form.value.pinned,
            expectedVersion: editing.value?.version,
          },
        },
      );
    } else
      await api("/api/courses/" + id.value + "/questions", {
        method: "POST",
        body: {
          title: form.value.title,
          content: form.value.content,
          chapterId: form.value.chapterId || null,
        },
      });
    ElMessage.success(tab.value === "notices" ? "公告已保存" : "问题已发布");
    showForm.value = false;
    page.value = 1;
    await fetchRows();
  });
}
function remove(n: Notice) {
  run(async () => {
    await ElMessageBox.confirm("删除这条公告？", "删除公告", {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
    });
    await api(
      "/api/teacher/courses/" + id.value + "/notices/" + n.id + "?expectedVersion=" + n.version,
      { method: "DELETE" },
    );
    await fetchRows();
  });
}
</script>
<template>
  <PageHeading
    :title="course?.name || '课堂交流'"
    description="读书有疑，师友共问。"
    :back="teacher ? '/teacher/courses' : '/student/enrollments'"
  >
    <RouterLink
      v-if="teacher"
      :to="'/teacher/courses/' + id + '/analytics'"
    >
      <el-button>查看学情</el-button>
    </RouterLink>
  </PageHeading>
  <CourseLinks
    v-if="course"
    :course-id="id"
    active="community"
  />
  <nav
    class="folio-tabs"
    aria-label="课堂内容"
  >
    <button
      :aria-current="tab === 'notices' ? 'page' : undefined"
      @click="switchTab('notices')"
    >
      书院告示
    </button>
    <button
      :aria-current="tab === 'questions' ? 'page' : undefined"
      @click="switchTab('questions')"
    >
      问学堂
    </button>
  </nav>
  <StatePanel
    :busy="busy && !course"
    :error="error"
    @retry="load"
  />
  <p
    v-if="course && !writable"
    class="read-only-note"
  >
    {{
      course.status === "ARCHIVED"
        ? "课程已归档，公告与交流仅供回顾。"
        : "课程尚未发布，课堂交流暂未开放。"
    }}
  </p>
  <div class="toolbar">
    <form
      v-if="tab === 'questions'"
      class="inline-form"
      @submit.prevent="
        page = 1;
        run(fetchRows);
      "
    >
      <el-input
        v-model="keyword"
        placeholder="搜索问题标题"
        aria-label="问题标题"
        clearable
      />
      <el-select
        v-model="resolved"
        placeholder="全部问题"
        aria-label="解决状态"
        clearable
      >
        <el-option
          label="尚未解决"
          value="false"
        />
        <el-option
          label="已经解决"
          value="true"
        />
      </el-select>
      <el-button
        native-type="submit"
        :loading="busy"
      >
        查询
      </el-button>
    </form>
    <el-button
      v-if="writable && (tab === 'notices' ? teacher && canTeach(course) : !teacher)"
      type="primary"
      @click="start()"
    >
      {{ tab === "notices" ? "发布公告" : "提出问题" }}
    </el-button>
  </div>
  <form
    v-if="showForm"
    class="paper form-paper"
    @submit.prevent="save"
  >
    <h2>
      {{ tab === "notices" ? (editing ? "编辑告示" : "新写告示") : "留下你的疑问" }}
    </h2>
    <label class="form-field">
      标题
      <el-input
        v-model="form.title"
        maxlength="100"
        required
      />
    </label>
    <label
      v-if="tab === 'questions'"
      class="form-field"
    >
      关联章节（选填）
      <el-select
        v-model="form.chapterId"
        placeholder="整门课程"
        clearable
      >
        <el-option
          v-for="c in chapters"
          :key="c.id"
          :value="c.id"
          :label="c.title"
        />
      </el-select>
    </label>
    <label class="form-field">
      正文
      <el-input
        v-model="form.content"
        type="textarea"
        :rows="6"
        maxlength="5000"
        required
      />
    </label>
    <label
      v-if="tab === 'notices'"
      class="check-line"
    >
      <input
        v-model="form.pinned"
        type="checkbox"
      />
      置顶此公告
    </label>
    <div class="form-actions">
      <el-button
        native-type="submit"
        type="primary"
        :loading="busy"
      >
        {{ tab === "notices" ? "保存公告" : "发布问题" }}
      </el-button>
      <el-button
        :disabled="busy"
        @click="showForm = false"
      >
        取消
      </el-button>
    </div>
  </form>
  <section
    v-if="tab === 'notices'"
    class="notice-board"
    aria-label="课程公告"
  >
    <article
      v-for="n in notices"
      :key="n.id"
      class="paper notice-paper"
    >
      <span
        v-if="n.pinned"
        class="small-seal"
      >
        置顶
      </span>
      <h2>{{ n.title }}</h2>
      <small>{{ time(n.updatedAt) }}</small>
      <p class="plain-text">{{ n.content }}</p>
      <div
        v-if="teacher && canTeach(course) && writable"
        class="form-actions"
      >
        <el-button
          size="small"
          :disabled="busy"
          @click="start(n)"
        >
          编辑
        </el-button>
        <el-button
          size="small"
          :disabled="busy"
          @click="remove(n)"
        >
          删除
        </el-button>
      </div>
    </article>
    <p
      v-if="!busy && !error && !notices.length"
      class="paper muted"
    >
      先生尚未发布公告。
    </p>
  </section>
  <section
    v-else
    class="paper"
  >
    <article
      v-for="q in questions"
      :key="q.id"
      class="question-row"
    >
      <span
        class="question-sign"
        :class="{ resolved: q.resolved }"
      >
        {{ q.resolved ? "解" : "问" }}
      </span>
      <div>
        <RouterLink :to="'/questions/' + q.id">
          <h2>{{ q.title }}</h2>
        </RouterLink>
        <p>{{ q.content.slice(0, 100) }}{{ q.content.length > 100 ? "…" : "" }}</p>
        <small>
          {{ q.displayName || "学生 " + q.studentId }} · {{ time(q.createdAt) }} ·
          {{ q.replyCount }} 条回复
        </small>
      </div>
      <span class="field-hint">{{ q.resolved ? "已解决" : "待探讨" }}</span>
    </article>
    <p
      v-if="!busy && !error && !questions.length"
      class="muted"
    >
      还没有符合条件的问题。
    </p>
  </section>
  <ListPager
    v-if="total"
    v-model:page="page"
    :total="total"
    :page-size="8"
    @change="run(fetchRows)"
  />
</template>
