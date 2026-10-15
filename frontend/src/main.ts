import { createApp } from "vue";
import {
  ElButton,
  ElInput,
  ElSelect,
  ElOption,
  ElTable,
  ElTableColumn,
  ElPagination,
  ElProgress,
  ElDialog,
  ElSkeleton,
  ElInputNumber,
  ElCheckbox,
  ElCheckboxGroup,
  ElConfigProvider,
  ElLoading,
} from "element-plus";
import "element-plus/es/components/button/style/css";
import "element-plus/es/components/input/style/css";
import "element-plus/es/components/select/style/css";
import "element-plus/es/components/table/style/css";
import "element-plus/es/components/table-column/style/css";
import "element-plus/es/components/pagination/style/css";
import "element-plus/es/components/progress/style/css";
import "element-plus/es/components/dialog/style/css";
import "element-plus/es/components/skeleton/style/css";
import "element-plus/es/components/input-number/style/css";
import "element-plus/es/components/checkbox/style/css";
import "element-plus/es/components/checkbox-group/style/css";
import "element-plus/es/components/loading/style/css";
import "element-plus/es/components/message/style/css";
import "element-plus/es/components/message-box/style/css";
import "./style.css";
import "./platform.css";
import "./teaching.css";
import "./platform-v4.css";
import "./workspace.css";
import "./question-bank.css";
import "./completion.css";
import "./continuity.css";
import "./collaboration.css";
import "./insights.css";
import "./cultural-scenes.css";
import App from "./App.vue";
import router from "./router";
const app = createApp(App).use(router);
for (const component of [
  ElButton,
  ElInput,
  ElSelect,
  ElOption,
  ElTable,
  ElTableColumn,
  ElPagination,
  ElProgress,
  ElDialog,
  ElSkeleton,
  ElInputNumber,
  ElCheckbox,
  ElCheckboxGroup,
  ElConfigProvider,
])
  app.component(component.name!, component);
app.directive("loading", ElLoading.directive).mount("#app");
