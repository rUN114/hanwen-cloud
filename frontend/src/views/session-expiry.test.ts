import { beforeEach, expect, it, vi } from "vitest";
import { shallowMount, flushPromises } from "@vue/test-utils";
import { nextTick } from "vue";
import ElementPlus from "element-plus";
import { api } from "../api/client";
import { saveSession, expireSession, clearSession } from "../stores/session";
import Home from "./dashboard/Home.vue";
import Community from "./community/Community.vue";
import LiteratureDetail from "./library/LiteratureDetail.vue";
import QuestionDetail from "./assessment/QuestionDetail.vue";

vi.mock("vue-router", () => ({
  useRoute: () => ({ params: { id: "11", courseId: "11" }, query: {}, path: "/library/11" }),
}));
vi.mock("../api/client", async (original) => ({
  ...(await original<typeof import("../api/client")>()),
  api: vi.fn(),
}));
const work = {
  id: "11",
  title: "测试诗文",
  genre: "古文",
  author: "作者",
  dynasty: "唐",
  status: "PUBLISHED",
  content: "正文",
  sourceUrl: "https://example.org/work",
  allusions: [],
};
beforeEach(() => {
  clearSession();
  saveSession({
    accessToken: "fixture",
    expiresAt: new Date(Date.now() + 900000).toISOString(),
    user: { id: "3", username: "student", displayName: "学生", role: "STUDENT", status: "ENABLED" },
  });
  vi.mocked(api).mockImplementation(<T>(path: string): Promise<T> => {
    let data: unknown = { items: [], total: 0, page: 1, size: 10 };
    if (path === "/api/literature/11") data = work;
    if (path === "/api/courses/11")
      data = { id: "11", name: "课程", status: "PUBLISHED", accessRole: "NONE" };
    if (path.endsWith("/chapters")) data = [];
    if (path === "/api/questions/11")
      data = {
        id: "11",
        studentId: "3",
        courseId: "11",
        content: "问题",
        resolved: false,
        createdAt: "2026-10-25T12:00:00+08:00",
      };
    if (path === "/api/favorites/11") data = { favorited: false };
    return Promise.resolve(data as T);
  });
});

it.each([Home, Community, LiteratureDetail, QuestionDetail])(
  "keeps mounted view renderable after expiry: %s",
  async (component) => {
    const errors: unknown[] = [];
    const wrapper = shallowMount(component, {
      global: {
        plugins: [ElementPlus],
        renderStubDefaultSlot: true,
        stubs: { RouterLink: { template: "<span><slot /></span>" } },
        config: {
          errorHandler: (error) => {
            errors.push(error);
          },
        },
      },
    });
    try {
      await flushPromises();
      expireSession();
      await nextTick();
      expect(errors).toEqual([]);
      expect(wrapper.exists()).toBe(true);
    } finally {
      wrapper.unmount();
    }
  },
);

it("never grants resolve permission to an absent session", async () => {
  const wrapper = shallowMount(QuestionDetail, {
    global: { plugins: [ElementPlus], renderStubDefaultSlot: true },
  });
  try {
    await flushPromises();
    expect(wrapper.text()).toContain("标记为已解决");
    expireSession();
    await nextTick();
    expect(wrapper.text()).not.toContain("标记为已解决");
  } finally {
    wrapper.unmount();
  }
});
