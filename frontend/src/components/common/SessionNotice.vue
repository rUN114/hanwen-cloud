<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import { api, renewSession } from "../../api/client";
import {
  session,
  reauthUser,
  sessionNotice,
  saveSession,
  type Session,
} from "../../stores/session";
import { useTask } from "../../composables/useTask";
import StatePanel from "./StatePanel.vue";
const dialog = ref(false);
const password = ref("");
const { busy, error, run } = useTask();
let timer: ReturnType<typeof setInterval>;
function check() {
  if (session.value && new Date(session.value.expiresAt).getTime() <= Date.now() + 60000)
    void renewSession().catch(() => {});
}
onMounted(() => {
  timer = setInterval(check, 30000);
  check();
});
onUnmounted(() => clearInterval(timer));
function login() {
  run(async () => {
    const result = await api<Session>("/api/auth/login", {
      method: "POST",
      body: { username: reauthUser.value?.username, password: password.value },
    });
    if (result.user.id !== reauthUser.value?.id) throw Error("请使用原账号登录，避免混用作业");
    saveSession(result);
    password.value = "";
    dialog.value = false;
    window.dispatchEvent(new Event("hw:session-resumed"));
  });
}
</script>
<template>
  <div
    v-if="sessionNotice"
    class="session-notice"
    role="status"
  >
    <span
      class="small-seal"
      aria-hidden="true"
    >
      续
    </span>
    <p>{{ sessionNotice }}</p>
    <el-button
      v-if="reauthUser"
      type="primary"
      @click="dialog = true"
    >
      重新登录，继续编辑
    </el-button>
    <el-button
      v-else
      @click="check"
    >
      重试续期
    </el-button>
  </div>
  <el-dialog
    v-model="dialog"
    title="重新入院"
    width="430px"
    @closed="password = ''"
  >
    <p>
      继续使用 {{ reauthUser?.displayName }}（{{
        reauthUser?.username
      }}）的账号。当前页面与文字保持原样。
    </p>
    <form @submit.prevent="login">
      <label class="form-field">
        密码
        <el-input
          v-model="password"
          type="password"
          show-password
          autocomplete="current-password"
          required
        />
      </label>
      <StatePanel
        :error="error"
        @retry="login"
      />
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
      >
        登录并继续
      </el-button>
    </form>
  </el-dialog>
</template>
