<script setup lang="ts">
import { computed, h, defineComponent } from "vue";
import { blocks, spans } from "../../utils/document";
import ProtectedImage from "./ProtectedImage.vue";
const props = defineProps<{ content: string }>();
const rows = computed(() => blocks(props.content));
const Inline = defineComponent({
  props: { text: { type: String, required: true } },
  setup(p) {
    return () =>
      h(
        "span",
        spans(p.text).map((x) =>
          x.type === "strong"
            ? h("strong", x.text)
            : x.type === "em"
              ? h("em", x.text)
              : x.type === "link"
                ? h("a", { href: x.url, target: "_blank", rel: "noopener noreferrer" }, x.text)
                : x.text,
        ),
      );
  },
});
</script>
<template>
  <div class="lesson-document">
    <template
      v-for="(row, index) in rows"
      :key="index"
    >
      <ProtectedImage
        v-if="row.type === 'image'"
        :id="row.id!"
        :alt="row.text"
      />
      <component
        v-else
        :is="
          ['h2', 'h3', 'h4'].includes(row.type)
            ? row.type
            : row.type === 'quote'
              ? 'blockquote'
              : 'p'
        "
        :class="{ 'lesson-bullet': row.type === 'bullet' }"
      >
        <Inline :text="row.text || ' '" />
      </component>
    </template>
  </div>
</template>
