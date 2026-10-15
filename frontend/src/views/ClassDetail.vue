<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api";
import { session } from "../session";
import { useTask, time } from "../useTask";
import { allPages, csv, download } from "../export";
import type { Classroom, ClassMember, Page } from "../models";
import { ElMessage, ElMessageBox } from "element-plus";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
const id = String(useRoute().params.id),
  teacher = computed(() => session.value?.user.role === "TEACHER"),
  room = ref<Classroom | null>(null),
  rows = ref<ClassMember[]>([]),
  page = ref(1),
  total = ref(0),
  usernames = ref(""),
  editing = ref(false);
const form = reactive({
  name: "",
  semester: "",
  description: "",
  status: "ACTIVE",
});
const { busy, error, run } = useTask();
async function fetchRoster() {
  if (!teacher.value) return;
  const p = await api<Page<ClassMember>>(
    "/api/teacher/classes/" + id + "/members?page=" + page.value,
  );
  rows.value = p.items;
  total.value = p.total;
}
async function fetchData() {
  room.value = await api<Classroom>("/api/classes/" + id);
  Object.assign(form, {
    name: room.value.name,
    semester: room.value.semester,
    description: room.value.description || "",
    status: room.value.status,
  });
  await fetchRoster();
}
function load() {
  run(fetchData);
}
onMounted(load);
function edit() {
  run(async () => {
    await api("/api/teacher/classes/" + id, {
      method: "PUT",
      body: { ...form, expectedVersion: room.value!.version },
    });
    editing.value = false;
    await fetchData();
  });
}
function rotate() {
  run(async () => {
    await ElMessageBox.confirm(
      "旧邀请码将立即失效，已有同窗不会受到影响。",
      "更新邀请码",
    );
    room.value = await api<Classroom>(
      "/api/teacher/classes/" +
        id +
        "/rotate-code?expectedVersion=" +
        room.value!.version,
      { method: "POST" },
    );
  });
}
function copy() {
  run(async () => {
    await navigator.clipboard.writeText(room.value!.joinCode!);
    ElMessage.success("邀请码已复制");
  });
}
function importMembers() {
  run(async () => {
    const names = usernames.value
      .replace(/^\uFEFF/, "")
      .split(/[\s,，;；]+/)
      .map((x) => x.replace(/^"|"$/g, ""))
      .filter((x) => x && !["username", "账号", "学生账号"].includes(x));
    const result = await api<{ processed: number; added: number }>(
      "/api/teacher/classes/" + id + "/members",
      { method: "POST", body: { usernames: names } },
    );
    usernames.value = "";
    await fetchData();
    ElMessage.success(
      "核对 " +
        result.processed +
        " 个账号，新增或重新加入 " +
        result.added +
        " 位同窗",
    );
  });
}
function readFile(event: Event) {
  run(async () => {
    const f = (event.target as HTMLInputElement).files?.[0];
    if (f) {
      if (f.size > 100000) throw Error("名册文件请控制在100KB以内");
      usernames.value = await f.text();
    }
  });
}
function remove(m: ClassMember) {
  run(async () => {
    await ElMessageBox.confirm(
      "将" +
        m.displayName +
        "移出班级名册。课程学习资格与历史记录仍保留，这项操作不会退课。",
      "移出同窗",
    );
    await api("/api/teacher/classes/" + id + "/members/" + m.id, {
      method: "DELETE",
    });
    await fetchData();
  });
}
function exportRoster() {
  run(async () => {
    const all = await allPages<ClassMember>(
      "/api/teacher/classes/" + id + "/members",
    );
    download(
      csv([
        ["姓名", "学生编号", "入舍时间"],
        ...all.map((m) => [m.displayName, m.studentId, m.joinedAt]),
      ]),
      room.value!.name + "-名册.csv",
      "text/csv;charset=utf-8",
    );
  });
}
</script>
<template>
  <PageHeading :title="room?.name || '学舍'" back="/classes"
    ><el-button v-if="teacher&&room?.canEdit" @click="editing = !editing"
      >班级设置</el-button
    ></PageHeading
  ><StatePanel :busy="busy && !room" :error="error" @retry="load" />
  <template v-if="room"
    ><section class="class-overview">
      <article class="paper class-ledger">
        <span class="term-tag">{{ room.semester }}</span>
        <h2>{{ room.courseName }}</h2>
        <p>{{ room.description || "同窗共读，逐章前行。" }}</p>
        <div class="class-metrics">
          <span
            ><b>{{ room.memberCount }}</b
            >位同窗</span
          ><span>{{
            room.courseStatus === "ARCHIVED"
              ? "课程已归档"
              : room.status === "ACTIVE"
                ? "在读学舍"
                : "已结课"
          }}</span>
        </div>
        <div class="course-nav-actions">
          <RouterLink
            v-if="teacher"
            :to="'/teacher/courses/' + room.courseId + '/chapters'"
            ><el-button>章节编排</el-button></RouterLink
          ><RouterLink v-else :to="'/student/courses/' + room.courseId"
            ><el-button type="primary">进入课程</el-button></RouterLink
          ><RouterLink :to="'/courses/' + room.courseId + '/resources'"
            ><el-button>资源书架</el-button></RouterLink
          ><RouterLink :to="'/courses/' + room.courseId + '/quizzes'"
            ><el-button>章节练习</el-button></RouterLink
          ><RouterLink
            v-if="teacher"
            :to="'/teacher/courses/' + room.courseId + '/analytics'"
            ><el-button>课程学情</el-button></RouterLink
          >
        </div>
      </article>
      <article v-if="teacher" class="invitation-paper">
        <span class="small-seal">入舍</span>
        <h2>同窗邀请帖</h2>
        <p>将邀请码交给本班学生</p>
        <code>{{ room.joinCode }}</code>
        <div class="course-nav-actions">
          <el-button @click="copy">复制邀请码</el-button
          ><el-button
            :disabled="
              room.status !== 'ACTIVE' || room.courseStatus !== 'PUBLISHED'
            "
            v-if="room.canEdit" @click="rotate"
            >更新邀请码</el-button
          >
        </div>
        <small>每次更新后旧码失效</small>
      </article>
    </section>
    <form
      v-if="editing && teacher && room.canEdit"
      class="paper form-paper"
      @submit.prevent="edit"
    >
      <h2>班级设置</h2>
      <div class="form-field">
        <label for="edit-class-name">班级名称</label
        ><el-input
          id="edit-class-name"
          v-model="form.name"
          required
          maxlength="100"
        />
      </div>
      <div class="form-field">
        <label for="edit-term">学期</label
        ><el-input
          id="edit-term"
          v-model="form.semester"
          required
          maxlength="40"
        />
      </div>
      <div class="form-field">
        <label for="edit-description">说明</label
        ><el-input
          id="edit-description"
          v-model="form.description"
          type="textarea"
          maxlength="1000"
        />
      </div>
      <div class="form-field">
        <label for="class-status">班级状态</label
        ><select id="class-status" class="native-select" v-model="form.status">
          <option value="ACTIVE">开放加入</option>
          <option value="CLOSED">结课，停止加入</option>
        </select>
      </div>
      <el-button native-type="submit" type="primary" :loading="busy"
        >保存设置</el-button
      >
    </form>
    <section v-if="teacher" class="paper roster-paper">
      <div class="section-heading">
        <h2>同窗名册</h2>
        <el-button @click="exportRoster">导出名册</el-button>
      </div>
      <form
        v-if="room.canEdit && room.status === 'ACTIVE' && room.courseStatus === 'PUBLISHED'"
        class="roster-import"
        @submit.prevent="importMembers"
      >
        <div>
          <label for="student-list">批量加入已有学生账号</label
          ><el-input
            id="student-list"
            v-model="usernames"
            type="textarea"
            :rows="3"
            placeholder="每行一个账号，或用逗号分隔；例如 lihe, zhouyan"
          /><small>整批核对，遇到无效账号不会部分导入。</small>
        </div>
        <div>
          <label class="file-label" for="roster-file">读取单列 CSV / TXT</label
          ><input
            id="roster-file"
            type="file"
            accept=".csv,.txt"
            @change="readFile"
          /><el-button type="primary" native-type="submit" :loading="busy"
            >加入名册</el-button
          >
        </div>
      </form>
      <div class="roster-list">
        <article v-for="(m, i) in rows" :key="m.id">
          <span class="roster-no" aria-hidden="true">{{
            String((page - 1) * 20 + i + 1).padStart(2, "0")
          }}</span
          ><span class="name-token">{{ m.displayName.slice(0, 1) }}</span>
          <div>
            <strong>{{ m.displayName }}</strong
            ><small>入舍于 {{ time(m.joinedAt) }}</small>
          </div>
          <el-button v-if="room.canEdit" @click="remove(m)">移出名册</el-button>
        </article>
        <p v-if="!rows.length">尚无同窗，可批量加入或发送邀请码。</p>
      </div>
      <el-pagination
        v-if="total"
        v-model:current-page="page"
        :total="total"
        :page-size="20"
        layout="prev,pager,next,total"
        @current-change="() => run(fetchRoster)"
      /></section
  ></template>
</template>
