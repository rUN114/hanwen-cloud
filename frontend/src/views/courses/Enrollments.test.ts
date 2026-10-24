import { flushPromises, mount } from "@vue/test-utils";
import { expect, it, vi } from "vitest";
import { api } from "../../api/client";
import Enrollments from "./Enrollments.vue";

vi.mock("../../api/client", async (original) => ({
  ...(await original<typeof import("../../api/client")>()),
  api: vi.fn(),
}));

it("skips missing courses, limits progress requests to three, and finishes the queue", async () => {
  const rows = Array.from({ length: 6 }, (_, id) => ({
    id: String(id),
    courseId: String(id),
    courseName: id ? "课程" + id : "不可用课程",
    courseStatus: id ? "PUBLISHED" : "MISSING",
    joinedAt: "2026-10-24T12:00:00+08:00",
  }));
  const pending: Array<() => void> = [];
  const progressPaths: string[] = [];
  let active = 0;
  let maximum = 0;
  vi.mocked(api).mockImplementation(<T>(path: string): Promise<T> => {
    if (path.startsWith("/api/enrollments/me"))
      return Promise.resolve({ items: rows, total: 6 } as T);
    progressPaths.push(path);
    active++;
    maximum = Math.max(maximum, active);
    return new Promise<T>((resolve) => {
      pending.push(() => {
        active--;
        resolve({ total: 1, completedCount: 1, percent: "100" } as T);
      });
    });
  });
  const wrapper = mount(Enrollments, {
    global: {
      stubs: {
        PageHeading: true,
        StatePanel: true,
        StatusTag: true,
        "el-button": { template: "<button><slot /></button>" },
        "el-progress": true,
        "el-pagination": true,
        RouterLink: { props: ["to"], template: '<a :href="to"><slot /></a>' },
      },
    },
  });
  try {
    await flushPromises();
    expect(pending).toHaveLength(3);
    for (let next = 0; next < 5; next++) {
      pending[next]!();
      await flushPromises();
    }
    expect(maximum).toBe(3);
    expect(active).toBe(0);
    expect(progressPaths).toHaveLength(5);
    expect(progressPaths).not.toContain("/api/enrollments/0/progress");
    const cards = wrapper.findAll(".course-card");
    expect(cards).toHaveLength(6);
    expect(cards[0]!.findAll("a")).toHaveLength(0);
    expect(cards[0]!.text()).toContain("课程不可用");
    expect(wrapper.text()).not.toContain("进度加载中");
  } finally {
    wrapper.unmount();
  }
});
