<script setup lang="ts">
import { ref } from "vue";
import { onBeforeRouteLeave } from "vue-router";
import { ElMessageBox, ElMessage } from "element-plus";
import { api } from "../../api/client";
import { csv, download } from "../../utils/export";
import { parseRoster, type ImportRow } from "../../utils/import-users";
import { useTask } from "../../composables/useTask";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
const text = ref("");
const role = ref("STUDENT");
const preview = ref<ImportRow[]>([]);
const created = ref<(ImportRow & { initialPassword: string })[]>([]);
const downloaded = ref(false);
const { busy, error, run } = useTask();
function template() {
  download(
    csv([
      ["账号", "姓名"],
      ["student_example", "示例学生"],
    ]),
    "开户名册模板.csv",
    "text/csv;charset=utf-8",
  );
}
function read(event: Event) {
  run(async () => {
    const f = (event.target as HTMLInputElement).files?.[0];
    if (!f) return;
    if (f.size > 100000) throw Error("名册请控制在100KB以内");
    text.value = new TextDecoder("utf-8", { fatal: true }).decode(await f.arrayBuffer());
    preview.value = [];
  });
}
function validate() {
  run(async () => {
    preview.value = parseRoster(text.value);
  });
}
function create() {
  run(async () => {
    const rows = parseRoster(text.value);
    await ElMessageBox.confirm(
      "将整批创建 " +
        rows.length +
        "个" +
        (role.value === "TEACHER" ? "教师" : "学生") +
        "账号。成功后请下载初始密码，核对后交给本人。",
      "确认批量开户",
      { confirmButtonText: "创建账号", cancelButtonText: "继续核对" },
    );
    const data = await api<{
      created: number;
      items: (ImportRow & { initialPassword: string })[];
    }>("/api/admin/users/import", {
      method: "POST",
      body: { role: role.value, rows },
    });
    created.value = data.items;
    preview.value = [];
    text.value = "";
    ElMessage.success("已创建" + data.created + "个账号");
  });
}
function exportCredentials() {
  download(
    csv([
      ["账号", "姓名", "初始密码"],
      ...created.value.map((u) => [u.username, u.displayName, u.initialPassword]),
    ]),
    "新开户凭据-请妥善保管.csv",
    "text/csv;charset=utf-8",
  );
  downloaded.value = true;
}
onBeforeRouteLeave(async () => {
  if (!created.value.length || downloaded.value) return true;
  try {
    await ElMessageBox.confirm(
      "初始密码尚未下载。离开后本页不会再次显示，可由管理员重新设置密码。",
      "离开开户页",
      { confirmButtonText: "确认离开", cancelButtonText: "留下下载" },
    );
    return true;
  } catch {
    return false;
  }
});
</script>
<template>
  <PageHeading
    title="师生入院名册"
    back="/admin/users"
    description="先核对名单，再整批开户；每人生成独立的随机初始密码。"
  >
    <el-button @click="template">下载两列模板</el-button>
  </PageHeading>
  <StatePanel
    :error="error"
    @retry="created.length ? exportCredentials() : validate()"
  />
  <div class="admission-layout">
    <section
      v-if="!created.length"
      class="paper admission-ledger"
    >
      <div class="ledger-heading">
        <span
          class="small-seal"
          aria-hidden="true"
        >
          籍
        </span>
        <h2>录入名册</h2>
      </div>
      <label class="form-field">
        开户角色
        <el-select v-model="role">
          <el-option
            label="学生"
            value="STUDENT"
          />
          <el-option
            label="教师"
            value="TEACHER"
          />
        </el-select>
      </label>
      <label class="form-field">
        上传CSV名册
        <input
          type="file"
          accept=".csv,.txt"
          @change="read"
          :disabled="busy"
        />
      </label>
      <label class="form-field">
        账号与姓名
        <el-input
          v-model="text"
          type="textarea"
          :rows="9"
          placeholder="账号,姓名&#10;zhangsan,张三&#10;lisi,李四"
          :disabled="busy"
          @input="preview = []"
        />
      </label>
      <p class="field-hint">两列UTF-8 CSV，每批1至50人。重复、已存在或无效账号会导致整批拒绝。</p>
      <el-button
        type="primary"
        @click="validate"
        :loading="busy"
      >
        核对名册
      </el-button>
      <template v-if="preview.length">
        <h3>待开户 {{ preview.length }} 人</h3>
        <el-table :data="preview">
          <el-table-column
            prop="username"
            label="账号"
          />
          <el-table-column
            prop="displayName"
            label="姓名"
          />
        </el-table>
        <el-button
          type="primary"
          class="admission-submit"
          @click="create"
          :loading="busy"
        >
          确认整批开户
        </el-button>
      </template>
    </section>
    <section
      v-else
      class="paper admission-ledger"
    >
      <div class="ledger-heading">
        <span
          class="small-seal"
          aria-hidden="true"
        >
          成
        </span>
        <h2>已入册 {{ created.length }} 人</h2>
      </div>
      <el-table :data="created">
        <el-table-column
          prop="username"
          label="账号"
        />
        <el-table-column
          prop="displayName"
          label="姓名"
        />
        <el-table-column label="初始密码">
          <template #default>已生成，随凭据导出</template>
        </el-table-column>
      </el-table>
      <p>初始密码仅在本次响应中提供。下载后请妥善保管，交给本人，并提醒首次使用后修改密码。</p>
      <el-button
        type="primary"
        @click="exportCredentials"
      >
        下载新开户凭据
      </el-button>
      <p
        v-if="downloaded"
        class="field-hint"
      >
        已发起下载，请确认文件已保存，再离开此页。
      </p>
    </section>
    <aside class="admission-side">
      <img
        src="/assets/folio.svg"
        alt=""
        aria-hidden="true"
      />
      <h2>一人一帖，各有所属</h2>
      <p>账号负责识别身份，姓名用于课堂显示。开户完成后，教师可把这些学生账号导入班级名册。</p>
      <ol>
        <li>填写两列名单</li>
        <li>核对账号与姓名</li>
        <li>下载并交付凭据</li>
      </ol>
      <RouterLink to="/admin/audit">查看开户操作记录</RouterLink>
    </aside>
  </div>
</template>
