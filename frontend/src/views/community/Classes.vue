<script setup lang="ts">
import ListPager from "../../components/common/ListPager.vue";
import { ref, reactive, onMounted, computed } from "vue";
import { api } from "../../api/client";
import { canTeach } from "../../utils/course-access";
import { session } from "../../stores/session";
import { useTask } from "../../composables/useTask";
import { allPages } from "../../utils/export";
import type { Classroom, Course, Page } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
import { useRouter } from "vue-router";
const teacher = computed(() => session.value?.user.role === "TEACHER");
const rows = ref<Classroom[]>([]);
const courses = ref<Course[]>([]);
const page = ref(1);
const total = ref(0);
const dialog = ref(false);
const code = ref("");
const semester = ref("");
const status = ref("");
const router = useRouter();
const form = reactive({
  courseId: "",
  name: "",
  semester: "",
  description: "",
});
const { busy, error, run } = useTask();
const endpoint = () => (teacher.value ? "/api/teacher/classes" : "/api/classes");
async function fetchRows() {
  const p = await api<Page<Classroom>>(
    endpoint() +
      "?page=" +
      page.value +
      "&size=9&semester=" +
      encodeURIComponent(semester.value) +
      "&status=" +
      status.value,
  );
  rows.value = p.items;
  total.value = p.total;
}
function load() {
  run(fetchRows);
}
onMounted(load);
function open() {
  run(async () => {
    courses.value = await allPages<Course>("/api/teacher/courses?status=PUBLISHED");
    courses.value = courses.value.filter(canTeach);
    dialog.value = true;
  });
}
function create() {
  run(async () => {
    const c = await api<Classroom>("/api/teacher/classes", {
      method: "POST",
      body: form,
    });
    dialog.value = false;
    await router.push("/classes/" + c.id);
  });
}
function join() {
  run(async () => {
    const c = await api<Classroom>("/api/classes/join", {
      method: "POST",
      body: { code: code.value.trim() },
    });
    await router.push("/classes/" + c.id);
  });
}
</script>
<template>
  <PageHeading
    :title="teacher ? '我的学舍' : '同窗学舍'"
    :description="
      teacher ? '把课程、同窗与学期安排在同一间学舍。' : '凭先生的邀请码入舍，在共同的课程里读书。'
    "
  >
    <el-button
      v-if="teacher"
      type="primary"
      @click="open"
    >
      开设班级
    </el-button>
  </PageHeading>
  <div class="academy-banner">
    <div
      class="academy-plaque"
      aria-hidden="true"
    >
      学舍
    </div>
    <div>
      <h2>同窗相伴，读书有序</h2>
      <p>每个班级关联一门课程；加入班级后同时获得该课程的学习资格。</p>
    </div>
    <span
      class="bamboo-lines"
      aria-hidden="true"
    ></span>
  </div>
  <form
    v-if="!teacher"
    class="paper invitation-entry"
    @submit.prevent="join"
  >
    <label for="class-code">入舍邀请码</label>
    <el-input
      id="class-code"
      v-model="code"
      required
      maxlength="24"
      placeholder="输入教师提供的邀请码"
    />
    <el-button
      type="primary"
      native-type="submit"
      :loading="busy"
    >
      加入学舍
    </el-button>
  </form>
  <div class="toolbar">
    <el-input
      v-model="semester"
      aria-label="学期筛选"
      placeholder="完整学期，如2026秋"
      clearable
      @keyup.enter="
        page = 1;
        load();
      "
    />
    <el-select
      v-model="status"
      aria-label="班级状态"
      clearable
      placeholder="全部学舍"
      @change="
        page = 1;
        load();
      "
    >
      <el-option
        label="在读班级"
        value="ACTIVE"
      />
      <el-option
        label="已结课"
        value="CLOSED"
      />
    </el-select>
    <el-button
      @click="
        page = 1;
        load();
      "
    >
      筛选学期
    </el-button>
  </div>
  <p class="field-hint">
    学舍组织同窗，选课决定学习资格。移出学舍不会删除选课、答卷或成绩；结课后仍可查阅历史。
  </p>
  <StatePanel
    :busy="busy && !rows.length"
    :error="error"
    :empty="!busy && !error && !rows.length"
    empty-text="暂无班级，先开设或加入一间学舍"
    @retry="load"
  />
  <section class="classroom-grid">
    <article
      v-for="(c, i) in rows"
      :key="c.id"
      class="classroom-card"
      :class="'class-tone-' + (i % 3)"
    >
      <span
        class="roof-line"
        aria-hidden="true"
      ></span>
      <span class="term-tag">{{ c.semester }}</span>
      <h2>{{ c.name }}</h2>
      <p class="class-course">{{ c.courseName }}</p>
      <p>{{ c.description || "一间学舍，一卷共读。" }}</p>
      <div class="class-card-foot">
        <span>
          {{ c.memberCount }} 位同窗 ·
          {{
            c.courseStatus === "ARCHIVED" ? "课程归档" : c.status === "ACTIVE" ? "在读" : "已结课"
          }}
        </span>
        <RouterLink :to="'/classes/' + c.id">进入学舍 →</RouterLink>
      </div>
    </article>
  </section>
  <ListPager
    v-if="total"
    v-model:page="page"
    :total="total"
    :page-size="9"
    @change="load"
  />
  <el-dialog
    v-model="dialog"
    title="开设班级"
    width="min(560px,92vw)"
  >
    <form @submit.prevent="create">
      <div class="form-field">
        <label for="class-name">班级名称</label>
        <el-input
          id="class-name"
          v-model="form.name"
          required
          maxlength="100"
        />
      </div>
      <div class="form-field">
        <label for="class-semester">学期</label>
        <el-input
          id="class-semester"
          v-model="form.semester"
          required
          maxlength="40"
          placeholder="例如：2026年秋季"
        />
      </div>
      <div class="form-field">
        <label for="class-course">关联已发布课程</label>
        <select
          id="class-course"
          v-model="form.courseId"
          class="native-select"
          required
        >
          <option value="">请选择本人已发布的课程</option>
          <option
            v-for="c in courses"
            :key="c.id"
            :value="c.id"
          >
            {{ c.name }}
          </option>
        </select>
      </div>
      <div class="form-field">
        <label for="class-desc">班级说明</label>
        <el-input
          id="class-desc"
          v-model="form.description"
          type="textarea"
          maxlength="1000"
        />
      </div>
      <StatePanel
        :error="error"
        @retry="create"
      />
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
      >
        开设学舍
      </el-button>
    </form>
  </el-dialog>
</template>
