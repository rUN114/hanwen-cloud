import { api } from "../api/client";
import type { Page } from "../types/models";
export async function allPages<T>(path: string): Promise<T[]> {
  const rows: T[] = [];
  for (let page = 1; page <= 1000; page++) {
    const p = await api<Page<T>>(
      path + (path.includes("?") ? "&" : "?") + "page=" + page + "&size=100",
    );
    rows.push(...p.items);
    if (rows.length >= p.total || !p.items.length) return rows;
  }
  throw new Error("数据超过十万条，请缩小筛选范围后导出");
}
export function download(text: string, name: string, type = "text/plain;charset=utf-8") {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function csvCell(value: unknown) {
  let text = String(value ?? "");
  if (/^[\s]*[=+@-]/.test(text) || /^[\t\r]/.test(text)) text = "'" + text;
  return '"' + text.replaceAll('"', '""') + '"';
}
export function csv(rows: unknown[][]) {
  return "\ufeff" + rows.map((r) => r.map(csvCell).join(",")).join("\r\n");
}
