<script setup lang="ts">
import { reactive, ref, onMounted, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { api, ApiError } from "../../api/client";
import { saveSession, home, type Session } from "../../stores/session";
import { useTask } from "../../composables/useTask";
import StatePanel from "../../components/common/StatePanel.vue";
let savedUsername = "";
try {
  savedUsername = localStorage.getItem("hw:login-username") || "";
} catch {}
const rememberAccount = ref(Boolean(savedUsername));
const form = reactive({ username: savedUsername, password: "" });
const router = useRouter();
const route = useRoute();
const { busy, error, run } = useTask();
const lastDemoRole = ref<string | null>(null);
const demoAccounts = ref<{ role: string; label: string; username: string }[]>([]);
onMounted(async () => {
  if (!import.meta.env.DEV || !["127.0.0.1", "localhost", "[::1]"].includes(location.hostname))
    return;
  try {
    const response = await fetch("/__local-demo/accounts", { cache: "no-store" });
    if (response.ok) demoAccounts.value = await response.json();
  } catch {}
});
watch(rememberAccount, (value) => {
  if (!value) {
    try {
      localStorage.removeItem("hw:login-username");
    } catch {}
  }
});
async function finishLogin(result: Session) {
  saveSession(result);
  try {
    if (rememberAccount.value) localStorage.setItem("hw:login-username", result.user.username);
    else localStorage.removeItem("hw:login-username");
  } catch {}
  form.password = "";
  const next = String(route.query.next || "");
  await router.push(
    next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/login")
      ? next
      : home(result.user.role),
  );
}
function demoLogin(role: string) {
  if (!import.meta.env.DEV) return;
  if (busy.value) return;
  lastDemoRole.value = role;
  run(async () => {
    const response = await fetch("/__local-demo/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ role }),
    }).catch(() => {
      throw new ApiError(503, "NETWORK_ERROR", "暂时无法连接，请稍后重试");
    });
    const result = await response.json();
    if (!response.ok)
      throw new ApiError(
        response.status,
        result.code || "DEMO_LOGIN_FAILED",
        result.message || "登录未完成",
      );
    await finishLogin(result.data);
  });
}
function submit() {
  if (busy.value) return;
  lastDemoRole.value = null;
  run(async () => {
    const result = await api<Session>("/api/auth/login", { method: "POST", body: form });
    await finishLogin(result);
  });
}
</script>
<template>
  <main class="login-page">
    <section
      class="login-frame"
      aria-label="锦卷书院登录"
    >
      <div class="login-illustration">
        <div class="login-copy">
          <h1>锦卷书院</h1>
          <p>
            展卷读文章
            <br />
            落笔见风雅
          </p>
          <span
            class="brand-seal"
            aria-hidden="true"
          >
            汉
            <br />
            文
          </span>
        </div>
        <small class="login-art-credit">AI界面插画 · 梅庭迎客</small>
      </div>
      <div class="login-form">
        <img
          class="login-flower"
          src="/assets/plum.svg"
          alt=""
          aria-hidden="true"
        />
        <h2>欢迎入院</h2>
        <p>使用管理员为你分配的账号登录。</p>
        <form @submit.prevent="submit">
          <div class="form-field">
            <label for="username">账号</label>
            <el-input
              id="username"
              name="username"
              v-model="form.username"
              autocomplete="username"
              autofocus
              required
              maxlength="50"
              placeholder="请输入账号"
            />
          </div>
          <div class="form-field">
            <label for="password">密码</label>
            <el-input
              id="password"
              name="password"
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              show-password
              required
              placeholder="请输入密码"
            />
          </div>
          <el-checkbox
            v-model="rememberAccount"
            :disabled="busy"
          >
            记住账号
          </el-checkbox>
          <StatePanel
            :error="error"
            @retry="lastDemoRole ? demoLogin(lastDemoRole) : submit()"
          />
          <el-button
            class="login-submit"
            native-type="submit"
            type="primary"
            :loading="busy"
          >
            登录书院
          </el-button>
        </form>
        <section
          v-if="demoAccounts.length"
          class="login-demo"
          aria-label="本机演示快捷登录"
        >
          <p>本机演示 · 免输入账号密码</p>
          <div class="login-demo-actions">
            <el-button
              v-for="account in demoAccounts"
              :key="account.role"
              native-type="button"
              :disabled="busy"
              @click="demoLogin(account.role)"
            >
              {{ account.label }}
            </el-button>
          </div>
        </section>
        <div class="login-bottom">
          账号问题请联系管理员
          <img
            src="/assets/fan.svg"
            alt=""
            aria-hidden="true"
          />
        </div>
      </div>
    </section>
  </main>
</template>
<style scoped>
.login-demo {
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px solid #d5c5a6;
}
.login-demo p {
  color: #5c6558;
  font-size: 13px;
  margin: 0 0 12px;
}
.login-demo-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.login-demo-actions .el-button {
  margin: 0;
  min-width: 0;
}
</style>
