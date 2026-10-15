import { ref } from "vue";
import { ApiError } from "./api";
export function useTask() {
  const busy = ref(false),
    error = ref<ApiError | null>(null);
  async function run<T>(fn: () => Promise<T>): Promise<T | undefined> {
    if (busy.value) return;
    busy.value = true;
    error.value = null;
    try {
      return await fn();
    } catch (e) {
      if (e === "cancel" || e === "close") return;
      error.value =
        e instanceof ApiError
          ? e
          : new ApiError(
              500,
              "UNEXPECTED_ERROR",
              e instanceof Error ? e.message : "操作未完成",
            );
      return undefined;
    } finally {
      busy.value = false;
    }
  }
  return { busy, error, run };
}
export function time(value?: string | null) {
  return value
    ? new Intl.DateTimeFormat("zh-CN", {
        timeZone: "Asia/Shanghai",
        dateStyle: "medium",
        timeStyle: "short",
        hour12: false,
      }).format(new Date(value))
    : "未设置截止";
}
export const statusNames: Record<string, string> = {
  DRAFT: "草稿",
  PUBLISHED: "已发布",
  ARCHIVED: "已归档",
  CLOSED: "已关闭",
  SUBMITTED: "待评分",
  GRADED: "已评分",
  ENABLED: "启用",
  DISABLED: "禁用",
};
export const roleNames: Record<string, string> = {
  ADMIN: "管理员",
  TEACHER: "教师",
  STUDENT: "学生",
};
export function length(value: string) {
  return Array.from(value).length;
}
