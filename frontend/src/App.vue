<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, nextTick } from "vue";
import { useRoute } from "vue-router";
import { session, reauthUser } from "./stores/session";
import SessionNotice from "./components/common/SessionNotice.vue";
import { roleNames } from "./composables/useTask";
import zhCn from "element-plus/es/locale/lang/zh-cn";
const route = useRoute();
const open = ref(false);
const mobileQuery = window.matchMedia("(max-width: 800px)");
const mobile = ref(mobileQuery.matches);
const menuButton = ref<HTMLButtonElement | null>(null);
const sidebar = ref<HTMLElement | null>(null);
function resizeNavigation(event: MediaQueryListEvent) {
  mobile.value = event.matches;
  open.value = false;
}
onMounted(() => mobileQuery.addEventListener("change", resizeNavigation));
onUnmounted(() => mobileQuery.removeEventListener("change", resizeNavigation));
function closeNavigation() {
  if (!open.value) return;
  open.value = false;
  menuButton.value?.focus();
}
async function toggleNavigation() {
  if (open.value) return closeNavigation();
  open.value = true;
  await nextTick();
  sidebar.value?.querySelector<HTMLAnchorElement>("a")?.focus();
}
const currentUser = computed(() => session.value?.user || reauthUser.value);
const role = computed(() => currentUser.value?.role);
const links = computed(() => [
  {
    to: "/home",
    name: role.value === "TEACHER" ? "先生案头" : role.value === "ADMIN" ? "书院总览" : "我的书案",
  },
  ...(role.value === "ADMIN"
    ? [
        { to: "/admin/users", name: "用户管理" },
        { to: "/admin/audit", name: "书院事务录" },
      ]
    : role.value === "TEACHER"
      ? [
          { to: "/teacher/courses", name: "我的课程" },
          { to: "/teacher/question-bank", name: "问学题笺" },
          { to: "/teacher/review", name: "朱批书案" },
          { to: "/teacher/practice-queue", name: "文字题待阅" },
          { to: "/teacher/literature", name: "先生编选" },
        ]
      : [
          { to: "/student/courses", name: "课程广场" },
          { to: "/student/enrollments", name: "我的课程" },
          { to: "/student/notes", name: "学习札记" },
          { to: "/student/favorites", name: "我的藏书" },
          { to: "/student/grades", name: "我的成绩" },
          { to: "/student/review-book", name: "温故册" },
        ]),
  ...(role.value === "ADMIN"
    ? []
    : [
        {
          to: "/classes",
          name: role.value === "TEACHER" ? "我的学舍" : "同窗学舍",
        },
        { to: "/agenda", name: "书院课历" },
        { to: "/inbox", name: "书院来信" },
      ]),
  { to: "/library", name: "诗文书库" },
  { to: "/paintings", name: "书院画卷" },
]);
</script>
<template>
  <el-config-provider :locale="zhCn">
    <RouterView v-if="route.path === '/login'" />
    <div
      v-else
      class="app-shell"
      @keydown.esc="closeNavigation"
    >
      <a
        class="skip-link"
        href="#main-content"
      >
        跳至主要内容
      </a>
      <aside
        id="primary-navigation"
        ref="sidebar"
        class="sidebar"
        :class="{ open }"
        :inert="mobile && !open"
        :aria-hidden="mobile && !open ? true : undefined"
      >
        <RouterLink
          class="brand"
          @click="closeNavigation"
          :to="'/home'"
        >
          <span
            class="brand-seal"
            aria-hidden="true"
          >
            汉
            <br />
            文
          </span>
          <span>
            汉文学
            <small>锦卷书院</small>
          </span>
        </RouterLink>
        <div
          class="sidebar-divider"
          aria-hidden="true"
        ></div>
        <nav aria-label="主导航">
          <RouterLink
            v-for="link in links"
            :key="link.to"
            :to="link.to"
            @click="closeNavigation"
          >
            {{ link.name }}
          </RouterLink>
          <RouterLink
            to="/account"
            @click="closeNavigation"
          >
            入院名帖
          </RouterLink>
        </nav>
        <div
          class="sidebar-scene"
          aria-hidden="true"
        >
          <img
            src="/assets/fan.svg"
            alt=""
          />
          <p>
            以文会友
            <br />
            与古为新
          </p>
        </div>
        <div class="sidebar-footer">
          <span>{{ currentUser?.displayName }}</span>
          <small>{{ role ? roleNames[role] : "" }}</small>
        </div>
      </aside>
      <button
        v-if="open"
        class="sidebar-overlay"
        aria-label="关闭导航"
        @click="closeNavigation"
      ></button>
      <div class="workspace">
        <header class="topbar">
          <button
            class="menu-button"
            ref="menuButton"
            :aria-expanded="open"
            aria-controls="primary-navigation"
            :aria-label="open ? '关闭主导航' : '打开主导航'"
            @click="toggleNavigation"
          >
            <svg
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <span class="topbar-title">一卷风雅，读见千年</span>
          <RouterLink
            to="/account"
            class="user-chip"
            :title="currentUser?.displayName"
          >
            <span class="avatar">{{ currentUser?.displayName.slice(0, 1) }}</span>
            <span class="user-chip-name">{{ currentUser?.displayName }}</span>
          </RouterLink>
        </header>
        <main
          id="main-content"
          tabindex="-1"
        >
          <SessionNotice />
          <RouterView />
        </main>
        <footer class="page-footer">
          <img
            src="/assets/wave.svg"
            alt=""
            aria-hidden="true"
          />
          <span>汉文学在线教学管理系统</span>
        </footer>
      </div>
    </div>
  </el-config-provider>
</template>
