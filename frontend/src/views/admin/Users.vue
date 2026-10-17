<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessageBox, ElMessage } from "element-plus";
import { api } from "../../api/client";
import { useTask, roleNames } from "../../composables/useTask";
import type { User, Page, Role } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
import StatusTag from "../../components/common/StatusTag.vue";
const rows = ref<User[]>([]);
const page = ref(1);
const total = ref(0);
const keyword = ref("");
const role = ref("");
const { busy, error, run } = useTask();
const dialog = ref(false);
const target = ref<User | null>(null);
const access = reactive<{ role: Role; status: string }>({
  role: "STUDENT",
  status: "ENABLED",
});
async function fetchRows() {
  const p = await api<Page<User>>(
    "/api/admin/users?" +
      new URLSearchParams({
        page: String(page.value),
        size: "10",
        keyword: keyword.value,
        role: role.value,
      }),
  );
  rows.value = p.items;
  total.value = p.total;
}
function load() {
  run(fetchRows);
}
onMounted(load);
function edit(u: User) {
  target.value = u;
  access.role = u.role;
  access.status = u.status;
  dialog.value = true;
}
function save() {
  run(async () => {
    await ElMessageBox.confirm("调整权限会使该账号的所有旧登录凭证失效。", "确认权限调整", {
      confirmButtonText: "保存调整",
      cancelButtonText: "取消",
    });
    await api("/api/admin/users/" + target.value?.id + "/access", {
      method: "PUT",
      body: access,
    });
    dialog.value = false;
    ElMessage.success("权限已更新");
    await fetchRows();
  });
}
function reset(u: User) {
  run(async () => {
    const result = await ElMessageBox.prompt(
      "密码至少8个字符，UTF-8不超过72字节。请妥善交付新密码。",
      "重置 " + u.displayName + " 的密码",
      {
        inputType: "password",
        confirmButtonText: "重置密码",
        cancelButtonText: "取消",
        inputValidator: (v: string) =>
          (v && v.length >= 8 && new TextEncoder().encode(v).length <= 72) || "密码长度不符合要求",
      },
    );
    await api("/api/admin/users/" + u.id + "/password-reset", {
      method: "POST",
      body: { newPassword: result.value },
    });
    ElMessage.success("密码已重置，旧登录凭证已失效");
  });
}
</script>
<template>
  <PageHeading
    title="用户管理"
    description="为师生分配账号，管理访问权限。"
  >
    <RouterLink to="/admin/users/import"><el-button>批量开户</el-button></RouterLink>
    <RouterLink to="/admin/users/new"><el-button type="primary">新增用户</el-button></RouterLink>
  </PageHeading>
  <form
    class="toolbar"
    @submit.prevent="
      page = 1;
      load();
    "
  >
    <el-input
      v-model="keyword"
      aria-label="搜索账号或显示名"
      placeholder="账号或显示名"
      clearable
    />
    <el-select
      v-model="role"
      aria-label="角色筛选"
      placeholder="全部角色"
      clearable
    >
      <el-option
        label="管理员"
        value="ADMIN"
      />
      <el-option
        label="教师"
        value="TEACHER"
      />
      <el-option
        label="学生"
        value="STUDENT"
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
    @retry="load"
  />
  <div class="paper">
    <el-table
      :data="rows"
      v-loading="busy"
      empty-text="暂无符合条件的用户"
    >
      <el-table-column
        prop="username"
        label="账号"
        min-width="140"
      />
      <el-table-column
        prop="displayName"
        label="显示名"
        min-width="140"
      />
      <el-table-column
        label="角色"
        min-width="100"
      >
        <template #default="{ row }">{{ roleNames[row.role] }}</template>
      </el-table-column>
      <el-table-column
        label="状态"
        min-width="100"
      >
        <template #default="{ row }"><StatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column
        label="操作"
        min-width="210"
      >
        <template #default="{ row }">
          <el-button
            size="small"
            @click="edit(row)"
          >
            调整权限
          </el-button>
          <el-button
            size="small"
            @click="reset(row)"
          >
            重置密码
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :total="total"
      :page-size="10"
      layout="prev,pager,next,total"
      @current-change="load"
    />
  </div>
  <el-dialog
    v-model="dialog"
    title="调整角色与状态"
    width="470px"
  >
    <p>{{ target?.displayName }}（{{ target?.username }}）</p>
    <div class="form-field">
      <label for="access-role">角色</label>
      <el-select
        id="access-role"
        v-model="access.role"
      >
        <el-option
          v-if="target?.role === 'ADMIN'"
          label="管理员"
          value="ADMIN"
        />
        <el-option
          label="教师"
          value="TEACHER"
        />
        <el-option
          label="学生"
          value="STUDENT"
        />
      </el-select>
    </div>
    <div class="form-field">
      <label for="access-status">状态</label>
      <el-select
        id="access-status"
        v-model="access.status"
      >
        <el-option
          label="启用"
          value="ENABLED"
        />
        <el-option
          label="禁用"
          value="DISABLED"
        />
      </el-select>
    </div>
    <StatePanel
      :error="error"
      @retry="save"
    />
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button
        type="primary"
        :loading="busy"
        @click="save"
      >
        保存调整
      </el-button>
    </template>
  </el-dialog>
</template>
