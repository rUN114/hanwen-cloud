<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessageBox, ElMessage } from "element-plus";
import { api } from "../api";
import { session, clearSession, updateUser } from "../session";
import { useTask, roleNames, time } from "../useTask";
import type { Profile } from "../models";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
const user = ref<Profile | null>(null),
  router = useRouter(),
  form = ref({ displayName: "", email: "", biography: "" }),
  pwd = ref({ currentPassword: "", newPassword: "", confirmation: "" });
const { busy, error, run } = useTask();
function load() {
  run(async () => {
    user.value = await api<Profile>("/api/users/me");
    form.value = {
      displayName: user.value.displayName,
      email: user.value.email || "",
      biography: user.value.biography || "",
    };
  });
}
onMounted(load);
function profile() {
  run(async () => {
    user.value = await api<Profile>("/api/users/me", {
      method: "PUT",
      body: form.value,
    });
    updateUser(user.value);
    ElMessage.success("入院名帖已更新");
  });
}
function password() {
  run(async () => {
    if (pwd.value.newPassword !== pwd.value.confirmation)
      throw new Error("两次新密码不一致");
    await api("/api/users/me/password", {
      method: "PUT",
      body: {
        currentPassword: pwd.value.currentPassword,
        newPassword: pwd.value.newPassword,
      },
    });
    pwd.value = { currentPassword: "", newPassword: "", confirmation: "" };
    clearSession();
    ElMessage.success("密码已修改，请用新密码重新登录");
    await router.push("/login");
  });
}
function logout() {
  run(async () => {
    await ElMessageBox.confirm(
      "退出将使该账号的其他登录会话一并失效。",
      "退出登录",
      { confirmButtonText: "退出", cancelButtonText: "留在书院" },
    );
    await api("/api/auth/logout", { method: "POST" });
    clearSession();
    await router.push("/login");
  });
}
function localClear() {
  clearSession();
  router.push("/login");
}
</script>
<template>
  <PageHeading
    title="入院名帖"
    description="写下自己的名字与简介，也可在这里修改登录密码。"
  /><StatePanel :busy="busy && !user" :error="error" @retry="load" />
  <div v-if="user" class="account-grid">
    <form class="paper form-paper" @submit.prevent="profile">
      <span class="small-seal">名帖</span>
      <h2>我的资料</h2>
      <p class="field-hint">
        {{ user.username }} · {{ roleNames[user.role] }} · 本次入院最长至
        {{ time(session?.sessionExpiresAt || session?.expiresAt) }}
      </p>
      <label class="form-field"
        >显示名<el-input
          v-model="form.displayName"
          maxlength="50"
          required
          autocomplete="nickname" /></label
      ><label class="form-field"
        >邮箱（选填）<el-input
          v-model="form.email"
          maxlength="120"
          type="email"
          autocomplete="email" /></label
      ><label class="form-field"
        >个人简介（选填）<el-input
          v-model="form.biography"
          maxlength="500"
          type="textarea"
          :rows="5"
          placeholder="你喜欢的作家、篇章，或正在研究的问题。" /></label
      ><small class="field-hint">邮箱和简介只在本人的资料页显示。</small>
      <div class="form-actions">
        <el-button native-type="submit" type="primary" :loading="busy"
          >保存资料</el-button
        >
      </div>
    </form>
    <section class="paper form-paper">
      <h2>修改密码</h2>
      <form @submit.prevent="password">
        <label class="form-field"
          >当前密码<el-input
            v-model="pwd.currentPassword"
            type="password"
            show-password
            autocomplete="current-password"
            required /></label
        ><label class="form-field"
          >新密码<el-input
            v-model="pwd.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
            required
            minlength="8" /></label
        ><label class="form-field"
          >确认新密码<el-input
            v-model="pwd.confirmation"
            type="password"
            show-password
            autocomplete="new-password"
            required
            minlength="8"
        /></label>
        <p class="field-hint">
          至少 8 个字符，UTF-8 不超过 72 字节；修改成功后所有旧会话失效。
        </p>
        <el-button native-type="submit" type="primary" :loading="busy"
          >修改并重新登录</el-button
        >
      </form>
      <hr class="section-rule" />
      <el-button :loading="busy" @click="logout">退出登录</el-button>
      <div v-if="error" class="read-only-note">
        远程退出若未完成，可仅清除本机会话。<el-button @click="localClear"
          >清除本机会话</el-button
        >
      </div>
    </section>
  </div>
</template>
