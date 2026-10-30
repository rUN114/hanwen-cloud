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

  it("selects the current account's refresh cookie", async () => {
    const state = await import("../stores/session");
    state.saveSession(account("old"));
    const fetch = vi.fn().mockResolvedValue(reply(200, account("new")));
    vi.stubGlobal("fetch", fetch);
    const { renewSession } = await import("./client");
    await renewSession();
    expect(fetch.mock.calls[0]![1].headers["X-Refresh-User"]).toBe("3");
  });

  it("serializes renewal across tabs sharing a rotating cookie", async () => {
    let queue = Promise.resolve();
    const lock = vi.fn((_name: string, task: () => Promise<void>) => {
      const next = queue.then(task);
      queue = next.catch(() => {});
      return next;
    });
    vi.stubGlobal("navigator", { locks: { request: lock } });
    let active = 0;
    let maximum = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        active++;
        maximum = Math.max(maximum, active);
        await new Promise((resolve) => setTimeout(resolve, 10));
        active--;
        return reply(200, account("new"));
      }),
    );
    const firstState = await import("../stores/session");
    firstState.saveSession(account("tab-one"));
    const firstClient = await import("./client");
    vi.resetModules();
    const secondState = await import("../stores/session");
    secondState.saveSession(account("tab-two"));
    const secondClient = await import("./client");
    await Promise.all([firstClient.renewSession(), secondClient.renewSession()]);
    expect(lock).toHaveBeenCalledTimes(2);
    expect(maximum).toBe(1);
    expect(firstState.session.value?.accessToken).toBe("new");
    expect(secondState.session.value?.accessToken).toBe("new");
  });

  it("does not clear a new login when an old renewal is rejected", async () => {
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
    resolve(reply(401));
    await expect(pending).rejects.toMatchObject({ status: 401 });
    expect(state.session.value?.accessToken).toBe("other");
  });

  it("does not retry a rejected request under another account", async () => {
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
    const { api } = await import("./client");
    const pending = api("/api/courses");
    state.saveSession(account("other", "4"));
    resolve(reply(401));
    await expect(pending).rejects.toMatchObject({ code: "SESSION_CHANGED" });
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(state.session.value?.accessToken).toBe("other");
  });

  it("does not clear a new login when a retried request is rejected", async () => {
    const state = await import("../stores/session");
    state.saveSession(account("old"));
    let resolve!: (response: Response) => void;
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(reply(401))
      .mockResolvedValueOnce(reply(200, account("renewed")))
      .mockImplementationOnce(
        () =>
          new Promise<Response>((done) => {
            resolve = done;
          }),
      );
    vi.stubGlobal("fetch", fetch);
    const { api } = await import("./client");
    const pending = api("/api/courses");
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(3));
    state.saveSession(account("other", "4"));
    resolve(reply(401));
    await expect(pending).rejects.toMatchObject({ status: 401 });
    expect(state.session.value?.accessToken).toBe("other");
  });
});
