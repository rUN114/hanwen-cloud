<script setup lang="ts">
import { canTeach, courseRoles } from "../../utils/course-access";
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessageBox, ElMessage } from "element-plus";
import { session } from "../../stores/session";
import { api } from "../../api/client";
import { useTask } from "../../composables/useTask";
import type { Course, Page } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
import StatusTag from "../../components/common/StatusTag.vue";
const route = useRoute();
const router = useRouter();
const teacher = computed(() => session.value?.user.role === "TEACHER");
const rows = ref<Course[]>([]);
const page = ref(1);
const total = ref(0);
const keyword = ref("");
const category = ref("");
const status = ref("");
const { busy, error, run } = useTask();
async function fetchRows() {
  let path = teacher.value ? "/api/teacher/courses" : "/api/courses";
  const q = new URLSearchParams({
    page: String(page.value),
    size: "9",
    keyword: keyword.value,
    category: category.value,
    status: status.value,
  });
  const p = await api<Page<Course>>(path + "?" + q);
  rows.value = p.items;
  total.value = p.total;
}
function load() {
  run(fetchRows);
}
watch(
  () => route.path,
  () => {
    page.value = 1;
    load();
  },
  { immediate: true },
);
function transition(c: Course, publish: boolean) {
  run(async () => {
    await ElMessageBox.confirm(
      publish
        ? "发布后章节集合与顺序固定，仍可修改标题和正文。"
        : "归档后停止新加入、学习完成及作业提交，已有内容和成绩保留。",
      publish ? "发布课程" : "归档课程",
      {
        confirmButtonText: publish ? "确认发布" : "确认归档",
        cancelButtonText: "取消",
      },
    );
    await api("/api/teacher/courses/" + c.id + (publish ? "/publish" : "/archive"), {
      method: "POST",
    });
    ElMessage.success(publish ? "课程已发布" : "课程已归档");
    await fetchRows();
  });
}
function copyCourse(c: Course) {
  run(async () => {
    const input = await ElMessageBox.prompt(
      "复制介绍、章节和未归档练习为新草稿。学生、成绩、资料和作业仍留在原课程中。",
      "复制课程",
      {
        inputValue: (c.name + " · 新学期").slice(0, 100),
        confirmButtonText: "复制为草稿",
        cancelButtonText: "取消",
        inputValidator: (v: string) =>
          (!!v?.trim() && [...v.trim()].length <= 100) || "课程名称须为1至100字",
      },
    );
    const result = await api<{
      course: Course;
      chapters: number;
      quizzes: number;
    }>("/api/teacher/courses/" + c.id + "/copy", {
      method: "POST",
      body: { name: input.value },
    });
    ElMessage.success(
      "已复制" + result.chapters + "个章节和" + result.quizzes + "份练习，请核对后发布",
    );
    await router.push("/teacher/courses/" + result.course.id + "/chapters");
  });
}
const coverNames = ["bamboo", "plum", "lotus", "mountain"];
function coverName(c: Course) {
  return coverNames[Number(BigInt(c.id) % 4n)];
}
function cover(c: Course) {
  const paintings = { bamboo: "mountains", plum: "plum", lotus: "seasons", mountain: "mountains" };
  return (
    "/assets/paintings/" + paintings[coverName(c) as keyof typeof paintings] + "-small-v1.webp"
  );
}
</script>
<template>
  <PageHeading
    :title="teacher ? '我的课程' : '课程广场'"
    :description="
      teacher ? '从一篇文章、一节课程开始，带学生走进文学。' : '择一卷好书，与文字相逢。'
    "
  >
    <RouterLink
      v-if="teacher"
      to="/teacher/courses/new"
    >
      <el-button type="primary">新建课程</el-button>
    </RouterLink>
  </PageHeading>
  <section
    v-if="!teacher"
    class="hero art-scene"
    aria-label="青绿山水课程画卷"
  >
    <div>
      <h2>
        诗文有境
        <br />
        读写有声
      </h2>
      <p>
        在诗歌、散文与文学理论之间，
        <br />
        读懂作品，也写下自己的理解。
      </p>
    </div>
    <RouterLink
      class="scene-colophon"
      to="/paintings"
    >
      <span aria-hidden="true">画</span>
      AI界面插画 · 赏馆藏
    </RouterLink>
  </section>
  <form
    class="toolbar"
    @submit.prevent="
      page = 1;
      load();
    "
  >
    <template v-if="!teacher">
      <el-input
        v-model="keyword"
        placeholder="搜索课程名称"
        aria-label="课程名称"
        clearable
      />
      <el-select
        v-model="category"
        placeholder="全部分类"
        aria-label="分类"
        clearable
      >
        <el-option
          v-for="c in [
            '唐诗研读',
            '宋词研读',
            '古典散文',
            '经典阅读',
            '阅读写作',
            '汉文化专题',
            '古代文学',
            '现代文学',
            '文学理论',
          ]"
          :key="c"
          :label="c"
          :value="c"
        />
      </el-select>
    </template>
    <el-select
      v-else
      v-model="status"
      placeholder="全部状态"
      aria-label="课程状态"
      clearable
    >
      <el-option
        label="草稿"
        value="DRAFT"
      />
      <el-option
        label="已发布"
        value="PUBLISHED"
      />
      <el-option
        label="已归档"
        value="ARCHIVED"
      />
    </el-select>
    <el-button
      native-type="submit"
      :loading="busy"
    >
      查询
    </el-button>
  </form>
  <StatePanel
    :error="error"
    :busy="busy && !rows.length"
    :empty="!busy && !error && !rows.length"
    empty-text="暂无课程，可调整筛选条件"
    @retry="load"
  />
  <section
    v-if="rows.length"
    class="course-grid"
    aria-label="课程列表"
  >
    <article
      v-for="c in rows"
      :key="c.id"
      class="course-card"
    >
      <div
        class="book-cover has-cover"
        :class="'cover-' + coverName(c)"
      >
        <img
          :src="cover(c)"
          alt=""
          aria-hidden="true"
          class="course-cover-art"
          loading="lazy"
          decoding="async"
        />
        <h3>{{ c.name }}</h3>
        <span class="book-category">{{ c.category }}</span>
      </div>
      <div class="course-card-body">
        <StatusTag
          v-if="teacher"
          :status="c.status"
        />
        <span
          v-if="teacher"
          class="course-staff-label"
        >
          {{ courseRoles[c.accessRole || "NONE"] }}
        </span>
        <p>{{ c.summary || "课程简介尚未填写" }}</p>
        <small>授课教师 {{ c.teacherName || "教师" }}</small>
        <div class="course-card-actions">
          <template v-if="teacher">
            <el-button
              v-if="c.accessRole === 'OWNER'"
              :disabled="busy"
              @click="copyCourse(c)"
            >
              复制课程
            </el-button>
            <RouterLink :to="'/teacher/courses/' + c.id + '/chapters'">
              <el-button type="primary">章节</el-button>
            </RouterLink>
            <RouterLink
              v-if="canTeach(c)"
              :to="'/teacher/courses/' + c.id + '/edit'"
            >
              <el-button>{{ c.status === "ARCHIVED" ? "查看介绍" : "编辑介绍" }}</el-button>
            </RouterLink>
            <RouterLink
              v-if="c.status !== 'DRAFT'"
              :to="'/teacher/courses/' + c.id + '/assignments'"
            >
              <el-button>作业</el-button>
            </RouterLink>
            <RouterLink :to="'/courses/' + c.id + '/community'">
              <el-button>公告答疑</el-button>
            </RouterLink>
            <RouterLink :to="'/teacher/courses/' + c.id + '/analytics'">
              <el-button>学情</el-button>
            </RouterLink>
            <RouterLink :to="'/teacher/courses/' + c.id + '/team'">
              <el-button>共教名录</el-button>
            </RouterLink>
            <RouterLink :to="'/courses/' + c.id + '/resources'">
              <el-button>资源</el-button>
            </RouterLink>
            <RouterLink :to="'/courses/' + c.id + '/quizzes'">
              <el-button>练习</el-button>
            </RouterLink>
            <el-button
              v-if="c.accessRole === 'OWNER' && c.status === 'DRAFT'"
              :disabled="busy"
              @click="transition(c, true)"
            >
              发布
            </el-button>
            <el-button
              v-if="c.accessRole === 'OWNER' && c.status === 'PUBLISHED'"
              :disabled="busy"
              @click="transition(c, false)"
            >
              归档
            </el-button>
          </template>
          <RouterLink
            v-else
            :to="'/student/courses/' + c.id"
          >
            <el-button type="primary">查看课程</el-button>
          </RouterLink>
        </div>
      </div>
    </article>
  </section>
  <el-pagination
    v-if="total"
    v-model:current-page="page"
    :total="total"
    :page-size="9"
    layout="prev,pager,next,total"
    @current-change="load"
  />
</template>
