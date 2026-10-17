<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, onBeforeRouteLeave, onBeforeRouteUpdate } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { api } from "../../api/client";
import { useTask, time } from "../../composables/useTask";
import { courseRoles } from "../../utils/course-access";
import type { Course } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
interface Member {
  userId: string;
  displayName: string;
  role: string;
  updatedAt?: string;
}
interface Event {
  id: string;
  actorName: string;
  targetName: string;
  action: string;
  detail: string;
  createdAt: string;
}
interface Team {
  courseId: string;
  version: number;
  accessRole: string;
  members: Member[];
  events: Event[];
  eventsLimited: boolean;
}
const route = useRoute();
const id = computed(() => String(route.params.courseId));
const course = ref<Course | null>(null);
const team = ref<Team | null>(null);
const username = ref("");
const role = ref("CO_TEACHER");
const targetId = ref("");
const reason = ref("");
const retain = ref(true);
const tab = ref<"roster" | "handover">("roster");
const { busy, error, run } = useTask();
onBeforeRouteLeave(() => !busy.value);
onBeforeRouteUpdate(() => !busy.value);
const owner = computed(() => team.value?.accessRole === "OWNER");
const writable = computed(() => owner.value && course.value?.status !== "ARCHIVED");
const targets = computed(() => team.value?.members.filter((m) => m.role === "CO_TEACHER") || []);
const actions: Record<string, string> = {
  MEMBER_SET: "委任",
  MEMBER_REMOVED: "卸任",
  TRANSFER: "交接",
};
let generation = 0;
let pendingReload = false;
async function fetchData() {
  const current = ++generation;
  const courseId = id.value;
  const [c, t] = await Promise.all([
    api<Course>("/api/courses/" + courseId),
    api<Team>("/api/teacher/courses/" + courseId + "/team"),
  ]);
  if (current !== generation) return;
  course.value = c;
  team.value = t;
}
async function load() {
  if (busy.value) {
    pendingReload = true;
    return;
  }
  try {
    await run(fetchData);
  } finally {
    if (pendingReload) {
      pendingReload = false;
      void load();
    }
  }
}
watch(
  id,
  () => {
    generation++;
    course.value = null;
    team.value = null;
    targetId.value = "";
    load();
  },
  { immediate: true },
);
function save() {
  run(async () => {
    await ElMessageBox.confirm(
      "将为该教师账号赋予本课程的" + courseRoles[role.value] + "权限。请确认账号与身份。",
      "委任协作成员",
    );
    team.value = await api<Team>("/api/teacher/courses/" + id.value + "/team", {
      method: "PUT",
      body: { username: username.value, role: role.value, expectedVersion: team.value!.version },
    });
    username.value = "";
    ElMessage.success("协作身份已保存");
  });
}
function remove(m: Member) {
  run(async () => {
    await ElMessageBox.confirm(
      "移除" + m.displayName + "后，该账号将失去本课程的协作权限。已有教学记录保留。",
      "移除成员",
      { confirmButtonText: "确认移除", cancelButtonText: "保留" },
    );
    team.value = await api<Team>(
      "/api/teacher/courses/" + id.value + "/team/" + m.userId + "/remove",
      { method: "POST", body: { expectedVersion: team.value!.version } },
    );
    ElMessage.success("成员已移除");
  });
}
function transfer() {
  run(async () => {
    const target = targets.value.find((m) => m.userId === targetId.value);
    if (!target) return;
    await ElMessageBox.confirm(
      "将课程主讲交接给" +
        target.displayName +
        "。你将" +
        (retain.value ? "保留协同授课权限" : "退出本课程团队") +
        "，主讲管理权限立即转移。学生和历史成绩保留。",
      "核对课程交接",
      { confirmButtonText: "确认交接", cancelButtonText: "继续核对" },
    );
    await api("/api/teacher/courses/" + id.value + "/transfer", {
      method: "POST",
      body: {
        targetId: targetId.value,
        retainPrevious: retain.value,
        reason: reason.value,
        expectedVersion: team.value!.version,
      },
    });
    if (retain.value) {
      await fetchData();
      tab.value = "roster";
    } else {
      team.value = null;
      course.value = null;
      ElMessage.success("已交接并退出课程，请返回课程列表");
    }
    ElMessage.success("课程交接完成");
  });
}
</script>
<template>
  <PageHeading
    :title="(course?.name || '课程') + ' · 共教名录'"
    back="/teacher/courses"
    description="一人主讲，诸师共教。让分工有据，让交接有迹。"
  />
  <StatePanel
    :busy="busy && !team"
    :error="error"
    @retry="load"
  />
  <template v-if="team && course">
    <section class="team-frontispiece">
      <div
        class="team-spine"
        aria-hidden="true"
      >
        共教名录
      </div>
      <div>
        <span class="eyebrow">锦卷书院 · 教学分工</span>
        <h2>同席研文，各有所司</h2>
        <p>
          你在本课担任
          <strong>{{ courseRoles[team.accessRole] }}</strong>
          。课程介绍、教学记录与学生成果随课程保留。
        </p>
        <p
          v-if="course.status === 'ARCHIVED'"
          class="team-muted"
        >
          本课已归档，团队名录与交接记录只读。
        </p>
      </div>
      <span
        class="team-seal"
        aria-hidden="true"
      >
        共
        <br />
        教
      </span>
    </section>
    <nav
      class="team-tabs"
      aria-label="团队内容"
    >
      <button
        :aria-pressed="tab === 'roster'"
        @click="tab = 'roster'"
      >
        同席名录
      </button>
      <button
        :aria-pressed="tab === 'handover'"
        @click="tab = 'handover'"
      >
        课程交接 · 留痕
      </button>
      <RouterLink :to="'/teacher/courses/' + id + '/analytics'">查看学情</RouterLink>
    </nav>
    <section
      v-if="tab === 'roster'"
      class="team-layout"
    >
      <div class="team-roster">
        <h2>
          本课诸师
          <small>{{ team.members.length }} 人</small>
        </h2>
        <article
          v-for="(m, i) in team.members"
          :key="m.userId"
          class="team-member"
        >
          <span
            class="team-order"
            aria-hidden="true"
          >
            {{ String(i + 1).padStart(2, "0") }}
          </span>
          <div>
            <h3>{{ m.displayName }}</h3>
            <small>教师编号 {{ m.userId }}</small>
            <p>
              {{
                m.role === "OWNER"
                  ? "统筹课程、管理团队与交接"
                  : m.role === "CO_TEACHER"
                    ? "编写章节、安排教学与批阅"
                    : "批阅习作、查看学情与答疑"
              }}
            </p>
          </div>
          <span
            class="team-role"
            :class="m.role"
          >
            {{ courseRoles[m.role] }}
          </span>
          <el-button
            v-if="writable && m.role !== 'OWNER'"
            :disabled="busy"
            @click="remove(m)"
          >
            移除
          </el-button>
        </article>
      </div>
      <aside class="team-commission">
        <h2>{{ writable ? "委任协作成员" : "分工须知" }}</h2>
        <p>协作身份只在这门课程中生效。已有教师账号可担任协同教师或助教；主讲可调整身份。</p>
        <form
          v-if="writable"
          @submit.prevent="save"
        >
          <label class="form-field">
            教师账号
            <el-input
              v-model="username"
              maxlength="50"
              required
              placeholder="输入已有教师账号"
              aria-label="协作教师账号"
            />
          </label>
          <label class="form-field">
            课程内身份
            <el-select
              v-model="role"
              aria-label="课程协作身份"
            >
              <el-option
                label="协同教师 · 编课与批阅"
                value="CO_TEACHER"
              />
              <el-option
                label="助教 · 批阅与答疑"
                value="ASSISTANT"
              />
            </el-select>
          </label>
          <el-button
            type="primary"
            native-type="submit"
            :disabled="!username.trim()"
            :loading="busy"
          >
            保存委任
          </el-button>
        </form>
        <dl>
          <dt>主讲</dt>
          <dd>团队、发布与归档、课程复制、交接</dd>
          <dt>协同教师</dt>
          <dd>章节、资源、练习、作业、班级与总评规则</dd>
          <dt>助教</dt>
          <dd>批阅、修订、催交、学情与答疑；课程内容只读</dd>
        </dl>
      </aside>
    </section>
    <section
      v-else
      class="team-layout"
    >
      <div class="team-ledger">
        <h2>交接与委任记录</h2>
        <p
          v-if="!team.events.length"
          class="team-muted"
        >
          尚无协作变更记录，第一份委任将在此落笔。
        </p>
        <ol>
          <li
            v-for="e in team.events"
            :key="e.id"
          >
            <time>{{ time(e.createdAt) }}</time>
            <div>
              <span>{{ actions[e.action] || e.action }}</span>
              <h3>{{ e.actorName }} → {{ e.targetName }}</h3>
              <p>{{ courseRoles[e.detail] || e.detail }}</p>
            </div>
          </li>
        </ol>
        <p
          v-if="team.eventsLimited"
          class="field-hint"
        >
          展示最近 200 条记录。
        </p>
      </div>
      <aside class="team-commission">
        <h2>课程交接书</h2>
        <p>交接给一位当前协同教师。学生、课程内容、答卷及评分保留；历史作者不会改名。</p>
        <form
          v-if="writable"
          @submit.prevent="transfer"
        >
          <label class="form-field">
            新主讲
            <el-select
              v-model="targetId"
              placeholder="选择协同教师"
              aria-label="新主讲教师"
            >
              <el-option
                v-for="m in targets"
                :key="m.userId"
                :label="m.displayName + ' · ' + m.userId"
                :value="m.userId"
              />
            </el-select>
          </label>
          <label class="form-field">
            交接说明
            <el-input
              v-model="reason"
              type="textarea"
              :rows="4"
              maxlength="500"
              required
              placeholder="填写当前进度与待办"
              aria-label="交接说明"
            />
          </label>
          <el-checkbox v-model="retain">我保留为协同教师</el-checkbox>
          <el-button
            type="primary"
            native-type="submit"
            :disabled="!targetId || !reason.trim()"
            :loading="busy"
          >
            核对并交接
          </el-button>
          <p
            v-if="!targets.length"
            class="field-hint"
          >
            请先在同席名录中委任一位协同教师。
          </p>
        </form>
        <p
          v-else
          class="team-muted"
        >
          由当前主讲发起交接。
        </p>
      </aside>
    </section>
  </template>
</template>
