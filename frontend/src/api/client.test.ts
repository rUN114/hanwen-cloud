import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

function account(token: string, id = "3") {
  return {
    accessToken: token,
    expiresAt: new Date(Date.now() + 900000).toISOString(),
    user: {
      id,
      username: "student",
      displayName: "学生",
      role: "STUDENT" as const,
      status: "ENABLED" as const,
    },
  };
}
function reply(status: number, data?: unknown) {
  return new Response(
    JSON.stringify({ code: status === 200 ? "OK" : "AUTH_INVALID", message: "请求结果", data }),
    { status },
  );
}
beforeEach(() => {
  vi.resetModules();
  sessionStorage.clear();
});
afterEach(() => vi.unstubAllGlobals());

describe("session renewal", () => {
  it("coalesces concurrent renewal requests and saves one rotated token", async () => {
    const state = await import("../stores/session");
    state.saveSession(account("old"));
    let resolve!: (response: Response) => void;
    const fetch = vi.fn(
      () =>
        new Promise<Response>((done) => {
          resolve = done;
        }),
    );
    vi.stubGlobal("fetch", fetch);
    const { renewSession } = await import("./client");
    const first = renewSession();
    const second = renewSession();
    expect(fetch).toHaveBeenCalledTimes(1);
    resolve(reply(200, account("new")));
    await Promise.all([first, second]);
    expect(state.session.value?.accessToken).toBe("new");
  });

  it("clears a rejected session but keeps it during a service outage", async () => {
    const state = await import("../stores/session");
    state.saveSession(account("old"));
    const fetch = vi.fn().mockResolvedValueOnce(reply(503)).mockResolvedValueOnce(reply(401));
    vi.stubGlobal("fetch", fetch);
    const { renewSession } = await import("./client");
    await expect(renewSession()).rejects.toMatchObject({ status: 503 });
    expect(state.session.value?.accessToken).toBe("old");
    await expect(renewSession()).rejects.toMatchObject({ status: 401 });
    expect(state.session.value).toBeNull();
    expect(sessionStorage.getItem("hw:session")).toBeNull();
  });

  it("does not overwrite an account changed while renewal is pending", async () => {
    const state = await import("../stores/session");
    state.saveSession(account("old"));
    let resolve!: (response: Response) => void;
    vi.stubGlobal(
      "fetch",
      vi.fn(
        () =>
          new Promise<Response>((done) => {
            resolve = done;
          }),
      ),
    );
    const { renewSession } = await import("./client");
    const pending = renewSession();
    state.saveSession(account("other", "4"));
    resolve(reply(200, account("new")));
    await expect(pending).rejects.toMatchObject({ code: "SESSION_CHANGED" });
    expect(state.session.value?.accessToken).toBe("other");
  });

  it("retries an unauthorized request with the renewed token", async () => {
    const state = await import("../stores/session");
    state.saveSession(account("old"));
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(reply(401))
      .mockResolvedValueOnce(reply(200, account("new")))
      .mockResolvedValueOnce(reply(200, { title: "课程" }));
    vi.stubGlobal("fetch", fetch);
    const { api } = await import("./client");
    await expect(api("/api/courses")).resolves.toEqual({ title: "课程" });
    expect(fetch.mock.calls.map((call) => call[0])).toEqual([
      "/api/courses",
      "/api/auth/refresh",
      "/api/courses",
    ]);
    expect(fetch.mock.calls[2]![1].headers.Authorization).toBe("Bearer new");
  });
});
