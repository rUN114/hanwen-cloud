import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { shallowMount, flushPromises } from "@vue/test-utils";
import { api, apiFile } from "../../api/client";
import ResourcePanel from "./ResourcePanel.vue";

vi.mock("../../api/client", () => ({ api: vi.fn(), apiFile: vi.fn(), saveBlob: vi.fn() }));

beforeEach(() => {
  vi.mocked(api).mockResolvedValue({
    items: [{ id: "1", title: "图笺", mimeType: "image/png", bytes: 10 }],
    total: 1,
  });
  vi.stubGlobal("URL", {
    createObjectURL: vi.fn().mockReturnValue("blob:preview"),
    revokeObjectURL: vi.fn(),
  });
});
afterEach(() => vi.unstubAllGlobals());

function mountPanel() {
  return shallowMount(ResourcePanel, {
    props: { courseId: "11", canEdit: false },
    global: { stubs: { ElButton: { template: "<button><slot /></button>" } } },
  });
}

it("does not create an unreachable blob URL when the download finishes after unmount", async () => {
  let finish!: (blob: Blob) => void;
  vi.mocked(apiFile).mockReturnValue(new Promise((resolve) => (finish = resolve)));
  const panel = mountPanel();
  await flushPromises();
  await panel.get(".resource-actions button").trigger("click");
  panel.unmount();
  finish(new Blob(["image"], { type: "image/png" }));
  await flushPromises();
  expect(URL.createObjectURL).not.toHaveBeenCalled();
});

it("releases an existing preview URL on unmount", async () => {
  vi.mocked(apiFile).mockResolvedValue(new Blob(["image"], { type: "image/png" }));
  const panel = mountPanel();
  await flushPromises();
  await panel.get(".resource-actions button").trigger("click");
  await flushPromises();
  expect(URL.createObjectURL).toHaveBeenCalledOnce();
  panel.unmount();
  expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:preview");
});
