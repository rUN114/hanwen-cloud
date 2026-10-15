import { ref } from "vue";
import type { User, Role } from "./models";
export interface Session {
  accessToken: string;
  expiresAt: string;
  sessionExpiresAt?: string;
  user: User;
}
function read(): Session | null {
  try {
    const s = JSON.parse(sessionStorage.getItem("hw:session") || "null");
    return s?.accessToken && s?.user?.id ? s : null;
  } catch {
    return null;
  }
}
export const session = ref<Session | null>(read());
export const reauthUser = ref<User | null>(null);
export const sessionNotice = ref("");
export function clearSession() {
  session.value = null;
  sessionStorage.removeItem("hw:session");
}
export function expireSession() {
  reauthUser.value = session.value?.user || reauthUser.value;
  clearSession();
  sessionNotice.value =
    "登录已失效。当前标签页的未提交作业仍保留，请重新登录后继续。";
}
export function saveSession(value: Session) {
  session.value = value;
  sessionStorage.setItem("hw:session", JSON.stringify(value));
  reauthUser.value = null;
  sessionNotice.value = "";
}
export function updateUser(user: User) {
  if (session.value) saveSession({ ...session.value, user });
}
export function home(_role?: Role) {
  return "/home";
}
