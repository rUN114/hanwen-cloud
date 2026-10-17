import type { Chapter, GradeBrief, Page, ProgressSummary, Task } from "../types/models";

export function nextChapter(chapters: Chapter[], progress: ProgressSummary | null) {
  if (!progress) return null;
  const completed = new Set(progress.items.filter((p) => p.completed).map((p) => p.chapterId));
  return chapters.find((chapter) => !completed.has(chapter.id)) || null;
}

export function taskState(
  task: Task,
  grade: GradeBrief | undefined,
  known: boolean,
  courseStatus: string,
  now: number,
) {
  if (grade?.status === "GRADED")
    return { kind: "graded", label: "已评分", action: "查看反馈", writable: false };
  if (grade) return { kind: "submitted", label: "待评分", action: "查看提交", writable: false };
  if (!known)
    return { kind: "unknown", label: "提交状态未加载", action: "查看习作", writable: false };
  if (courseStatus !== "PUBLISHED")
    return { kind: "closed", label: "课程已归档", action: "查看习作", writable: false };
  if (task.status !== "PUBLISHED")
    return { kind: "closed", label: "已关闭", action: "查看习作", writable: false };
  if (task.deadlineAt && new Date(task.deadlineAt).getTime() <= now)
    return { kind: "closed", label: "已截止", action: "查看习作", writable: false };
  return { kind: "pending", label: "未提交", action: "写习作", writable: true };
}

export function orderTasks(
  tasks: Task[],
  grades: GradeBrief[] | null,
  courseStatus: string,
  now: number,
) {
  const found = new Map(grades?.map((g) => [g.assignmentId, g]) || []);
  return [...tasks].sort((a, b) => {
    const rank = (t: Task) =>
      taskState(t, found.get(t.id), grades !== null, courseStatus, now).writable ? 0 : 1;
    return (
      rank(a) - rank(b) ||
      (a.deadlineAt ? new Date(a.deadlineAt).getTime() : Infinity) -
        (b.deadlineAt ? new Date(b.deadlineAt).getTime() : Infinity) ||
      a.id.localeCompare(b.id, undefined, { numeric: true })
    );
  });
}

// A bounded workspace is not a replacement for the complete paginated business lists.
export async function workspacePages<T>(
  path: string,
  fetchPage: (path: string) => Promise<Page<T>>,
) {
  const rows: T[] = [];
  let total: number | undefined;
  for (let page = 1; page <= 5; page++) {
    const result = await fetchPage(
      path + (path.includes("?") ? "&" : "?") + "size=100&page=" + page,
    );
    if (result.total > 500) throw new Error("记录超过本页汇总上限，请使用完整列表。");
    if (total !== undefined && result.total !== total) throw new Error("记录已更新，请重新加载。");
    total = result.total;
    rows.push(...result.items);
    if (rows.length === total) return rows;
    if (!result.items.length || rows.length > total) throw new Error("记录已更新，请重新加载。");
  }
  throw new Error("无法完整读取记录，请使用完整列表或重试。");
}
