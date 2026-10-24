import {
  session,
  expireSession,
  saveSession,
  sessionNotice,
  type Session,
} from "../stores/session";
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public traceId = "",
  ) {
    super(message);
  }
}
// 同一页面的并发请求共用一次续期，避免轮换后的旧刷新令牌被重复使用。
let renewal: Promise<void> | null = null;
export async function renewSession(): Promise<void> {
  if (renewal) return renewal;
  const prior = session.value;
  if (!prior) throw new ApiError(401, "AUTH_REQUIRED", "请重新登录后继续");
  renewal = (async () => {
    let response: Response;
    try {
      response = await fetch("/api/auth/refresh", {
        method: "POST",
        credentials: "same-origin",
        headers: { Accept: "application/json" },
      });
    } catch {
      sessionNotice.value = "暂时无法续期，草稿继续保留。连接恢复后会重试。";
      throw new ApiError(503, "NETWORK_ERROR", sessionNotice.value);
    }
    const result = await response.json().catch(() => ({}));
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) expireSession();
      else sessionNotice.value = "暂时无法续期，草稿继续保留。连接恢复后会重试。";
      throw new ApiError(
        response.status,
        result.code || "REFRESH_FAILED",
        result.message || "暂时无法续期",
        result.traceId || "",
      );
    }
    // 等待续期时可能已经退出或切换账号，旧响应不能覆盖新的登录身份。
    const next = result.data as Session;
    if (
      !next?.accessToken ||
      next.user?.id !== prior.user.id ||
      session.value?.accessToken !== prior.accessToken
    )
      throw new ApiError(401, "SESSION_CHANGED", "登录身份已改变，请重新打开页面");
    saveSession(next);
  })().finally(() => {
    renewal = null;
  });
  return renewal;
}
async function request(
  path: string,
  options: { method?: string; body?: unknown },
): Promise<Response> {
  const auth = !["/api/auth/login", "/api/auth/refresh"].includes(path);
  if (auth && !session.value)
    throw new ApiError(401, "AUTH_REQUIRED", "请重新登录后继续，当前文字已保留");
  if (auth && session.value && new Date(session.value.expiresAt).getTime() <= Date.now() + 60000)
    await renewSession();
  const send = () =>
    fetch(path, {
      method: options.method || "GET",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        ...(options.body !== undefined && !(options.body instanceof FormData)
          ? { "Content-Type": "application/json" }
          : {}),
        ...(session.value ? { Authorization: "Bearer " + session.value.accessToken } : {}),
      },
      body:
        options.body === undefined
          ? undefined
          : options.body instanceof FormData
            ? options.body
            : JSON.stringify(options.body),
    });
  try {
    const token = session.value?.accessToken;
    let response = await send();
    if (response.status === 401 && auth) {
      // 其他请求已换好令牌时直接重发，避免再次续期。
      if (token === session.value?.accessToken) await renewSession();
      response = await send();
      if (response.status === 401) expireSession();
    }
    return response;
  } catch (e) {
    if (e instanceof ApiError) throw e;
    throw new ApiError(503, "NETWORK_ERROR", "暂时无法连接，请检查网络后重试");
  }
}
export async function api<T>(
  path: string,
  options: { method?: string; body?: unknown } = {},
): Promise<T> {
  const response = await request(path, options);
  let result;
  try {
    result = await response.json();
  } catch {
    throw new ApiError(response.status, "INVALID_RESPONSE", "服务未返回有效结果，请稍后重试");
  }
  if (!response.ok)
    throw new ApiError(
      response.status,
      result.code || "REQUEST_FAILED",
      result.message || "操作未完成",
      result.traceId || "",
    );
  return result.data as T;
}
export async function apiFile(path: string): Promise<Blob> {
  const response = await request(path, {});
  if (!response.ok) {
    const d = await response.json().catch(() => ({}));
    throw new ApiError(
      response.status,
      d.code || "DOWNLOAD_FAILED",
      d.message || "资源暂时无法读取",
      d.traceId || "",
    );
  }
  return response.blob();
}
export function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
