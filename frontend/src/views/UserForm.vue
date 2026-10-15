<script setup lang="ts">
import { reactive } from "vue";
import { useRouter } from "vue-router";
import { api } from "../api";
import { useTask } from "../useTask";
import PageHeading from "../components/PageHeading.vue";
import StatePanel from "../components/StatePanel.vue";
const router = useRouter(),
  form = reactive({
    username: "",
    password: "",
    displayName: "",
    role: "STUDENT",
  });
const { busy, error, run } = useTask();
function save() {
  run(async () => {
    await api("/api/admin/users", { method: "POST", body: form });
    await router.push("/admin/users");
  });
}
</script>
<template>
  <PageHeading
    title="新增用户"
    back="/admin/users"
    description="创建教师或学生账号；初始状态为启用。"
  />
  <form class="paper form-paper" @submit.prevent="save">
    <div class="form-field">
      <label for="new-user">账号</label
      ><el-input
        id="new-user"
        v-model="form.username"
        required
        minlength="3"
        maxlength="50"
        autocomplete="off"
      /><span class="field-hint"
        >3至50位小写字母、数字、下划线、点或短横线。</span
      >
    </div>
    <div class="form-field">
      <label for="new-name">显示名</label
      ><el-input
        id="new-name"
        v-model="form.displayName"
        required
        maxlength="50"
      />
    </div>
    <div class="form-field">
      <label for="new-role">角色</label
      ><el-select id="new-role" v-model="form.role"
        ><el-option label="学生" value="STUDENT" /><el-option
          label="教师"
          value="TEACHER"
      /></el-select>
    </div>
    <div class="form-field">
      <label for="new-password">初始密码</label
      ><el-input
        id="new-password"
        v-model="form.password"
        required
        minlength="8"
        type="password"
        show-password
        autocomplete="new-password"
      /><span class="field-hint"
        >至少8个字符，UTF-8编码不超过72字节；请妥善交付给本人。</span
      >
    </div>
    <StatePanel :error="error" @retry="save" />
    <div class="form-actions">
      <el-button native-type="submit" type="primary" :loading="busy"
        >创建账号</el-button
      ><RouterLink to="/admin/users">返回用户列表</RouterLink>
    </div>
  </form>
</template>
