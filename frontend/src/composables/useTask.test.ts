import { describe, expect, it } from "vitest";
import { ApiError } from "../api/client";
import { useTask } from "./useTask";

describe("task execution", () => {
  it("prevents duplicate work and releases busy state after completion", async () => {
    const task = useTask();
    let finish!: (value: number) => void;
    const first = task.run(
      () =>
        new Promise<number>((done) => {
          finish = done;
        }),
    );
    expect(task.busy.value).toBe(true);
    let called = false;
    await task.run(async () => {
      called = true;
    });
    expect(called).toBe(false);
    finish(7);
    expect(await first).toBe(7);
    expect(task.busy.value).toBe(false);
  });

  it("preserves API errors and converts unexpected failures", async () => {
    const task = useTask();
    const error = new ApiError(409, "CONFLICT", "内容已更新");
    await task.run(async () => {
      throw error;
    });
    expect(task.error.value?.code).toBe("CONFLICT");
    expect(task.error.value?.status).toBe(409);
    await task.run(async () => {
      throw new Error("读取失败");
    });
    expect(task.error.value?.code).toBe("UNEXPECTED_ERROR");
    expect(task.error.value?.message).toBe("读取失败");
    expect(task.busy.value).toBe(false);
  });

  it.each(["cancel", "close"])("does not report a user %s as an error", async (reason) => {
    const task = useTask();
    await task.run(async () => {
      throw reason;
    });
    expect(task.error.value).toBeNull();
    expect(task.busy.value).toBe(false);
  });
});
