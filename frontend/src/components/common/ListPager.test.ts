import { mount } from "@vue/test-utils";
import { defineComponent, h, ref } from "vue";
import { ElPagination } from "element-plus";
import { expect, it } from "vitest";
import ListPager from "./ListPager.vue";

it("updates the parent page before loading the next page", async () => {
  const page = ref(1);
  const observed: number[] = [];
  const host = defineComponent({
    setup: () => () =>
      h(ListPager, {
        page: page.value,
        total: 50,
        pageSize: 8,
        "onUpdate:page": (value: number) => {
          page.value = value;
        },
        onChange: (value: number) => {
          observed.push(page.value);
          expect(value).toBe(page.value);
        },
      }),
  });
  const wrapper = mount(host, { global: { components: { ElPagination } } });
  try {
    await wrapper.get(".btn-next").trigger("click");
    expect(page.value).toBe(2);
    expect(observed).toEqual([2]);
    expect(wrapper.findComponent(ElPagination).props("pageSize")).toBe(8);
  } finally {
    wrapper.unmount();
  }
});

it("preserves disabled pagination", async () => {
  const wrapper = mount(ListPager, {
    props: { page: 1, total: 40, pageSize: 10 },
    attrs: { disabled: true },
    global: { components: { ElPagination } },
  });
  try {
    expect(wrapper.get(".btn-next").attributes()).toHaveProperty("disabled");
    await wrapper.get(".btn-next").trigger("click");
    expect(wrapper.emitted("change")).toBeUndefined();
  } finally {
    wrapper.unmount();
  }
});
