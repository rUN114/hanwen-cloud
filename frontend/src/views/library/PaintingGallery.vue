<script setup lang="ts">
import { computed, ref } from "vue";
import PageHeading from "../../components/common/PageHeading.vue";
import paintings from "../../data/museum-paintings.json";
type Painting = (typeof paintings)[number];
const selected = ref<Painting | null>(null);
const show = ref(false);
const imageIndex = ref(0);
const zoom = ref(false);
const currentImage = computed(() => selected.value?.images[imageIndex.value]);
function open(painting: Painting) {
  selected.value = painting;
  imageIndex.value = 0;
  zoom.value = false;
  show.value = true;
}
function chooseImage(index: number) {
  imageIndex.value = index;
  zoom.value = false;
}
</script>
<template>
  <PageHeading
    title="书院画卷"
    description="看真实馆藏，读画中笔墨。"
  >
    <RouterLink to="/home"><el-button>回到书案</el-button></RouterLink>
  </PageHeading>
  <section class="painting-intro">
    <span
      class="painting-seal"
      aria-hidden="true"
    >
      馆藏
      <br />
      选读
    </span>
    <div>
      <h2>从诗文走进画卷</h2>
      <p>
        六件来自克利夫兰艺术博物馆的真实馆藏。作者、年代与归属说明依据馆藏记录，每幅均可查看原始来源。
      </p>
    </div>
  </section>
  <div class="painting-grid">
    <article
      v-for="painting in paintings"
      :key="painting.id"
      class="painting-leaf"
    >
      <button
        class="painting-preview museum-preview"
        type="button"
        :aria-label="'展开' + painting.title"
        @click="open(painting)"
      >
        <img
          :src="painting.preview"
          :alt="painting.title + (painting.previewIsDetail ? '馆藏局部' : '完整作品')"
          loading="lazy"
          decoding="async"
        />
        <span
          v-if="painting.previewIsDetail"
          class="painting-detail-label"
        >
          局部照片
        </span>
        <span class="painting-open">展开画卷</span>
      </button>
      <div class="painting-caption">
        <div class="painting-title">
          <h2>{{ painting.title }}</h2>
          <span>{{ painting.author }}</span>
        </div>
        <p class="painting-record">{{ painting.date }} · {{ painting.medium }}</p>
        <p>{{ painting.description }}</p>
        <ul aria-label="观画线索">
          <li
            v-for="detail in painting.details"
            :key="detail"
          >
            {{ detail }}
          </li>
        </ul>
        <a
          class="museum-source"
          :href="painting.source"
          target="_blank"
          rel="noopener noreferrer"
        >
          查看馆藏记录 · {{ painting.accession }}
        </a>
        <small class="museum-credit">
          {{ painting.museum }} · {{ painting.license }}
          <br />
          {{ painting.originalTitle }}
          <br />
          {{ painting.titleNote }}
        </small>
      </div>
    </article>
  </div>
  <p class="painting-provenance">
    本页使用馆方开放的藏品数字照片，保留原比例、题跋和印章；仅转换网页格式，未重绘或使用 AI
    修复。译名、约年与“传”等归属限定均保留说明。
  </p>
  <el-dialog
    v-model="show"
    :title="selected?.title || '书院画卷'"
    width="min(1100px, 94vw)"
    align-center
    class="painting-dialog"
  >
    <template v-if="selected && currentImage">
      <p class="museum-dialog-record">
        {{ selected.author }} · {{ selected.date }} · {{ selected.medium }}
      </p>
      <div class="museum-view-controls">
        <el-button
          v-for="(photo, index) in selected.images"
          :key="photo.src"
          :type="imageIndex === index ? 'primary' : 'default'"
          :aria-pressed="imageIndex === index"
          @click="chooseImage(index)"
        >
          {{ photo.label }}
        </el-button>
        <el-button
          :aria-pressed="zoom"
          @click="zoom = !zoom"
        >
          {{ zoom ? "适合窗口" : "放大看笔墨" }}
        </el-button>
      </div>
      <p
        v-if="zoom"
        class="museum-view-hint"
      >
        可在画面内横向或纵向滚动，手机可滑动查看。
      </p>
      <div
        :key="currentImage.src + String(zoom)"
        class="museum-viewer"
        :class="{ zoomed: zoom }"
        tabindex="0"
        role="region"
        :aria-label="selected.title + '观览区'"
      >
        <img
          class="painting-full"
          :src="currentImage.src"
          :alt="selected.title + ' · ' + currentImage.label"
          :style="zoom ? { height: Math.min(currentImage.height, 620) + 'px' } : undefined"
        />
      </div>
      <p class="museum-dialog-credit">
        {{ currentImage.label }} · {{ selected.museum }} · {{ selected.license }} ·
        <a
          :href="selected.source"
          target="_blank"
          rel="noopener noreferrer"
        >
          核对馆藏来源
        </a>
      </p>
      <p>{{ selected.description }}</p>
    </template>
  </el-dialog>
</template>
