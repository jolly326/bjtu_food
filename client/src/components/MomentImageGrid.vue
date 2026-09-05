<template>
  <!-- 动态/评论图片九宫格（抽取自 MomentCard ×2 / MomentDetailCard / CommentItem 的内联重复，
       视觉与淡入逻辑统一；inset=true 用于详情/评论卡内需要白底内嵌的场景） -->
  <view class="m-images" :class="{ inset }">
    <view
      v-for="(img, idx) in images"
      :key="idx"
      class="m-image-wrap"
      @tap.stop="onPreview(idx)"
    >
      <image
        class="m-image"
        :class="{ loaded: loadedSet.has(idx) }"
        :src="getImageUrl(getThumbUrl(img))"
        mode="aspectFill"
        lazy-load
        @load="loadedSet.add(idx)"
      />
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { previewImages, getImageUrl, getThumbUrl } from '@/utils/image'

const props = withDefaults(defineProps<{
  images?: string[]
  /** 内嵌白底样式（详情/评论卡用；列表卡用纯网格） */
  inset?: boolean
}>(), {
  images: () => [],
  inset: false,
})

const emit = defineEmits<{
  (e: 'preview', idx: number): void
}>()

/** 图片淡入：记录已加载下标，配合 .m-image.loaded 做 opacity 过渡 */
const loadedSet = reactive(new Set<number>())

function onPreview(idx: number) {
  emit('preview', idx)
  previewImages(props.images, idx)
}
</script>

<style scoped>
.m-images { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--spacing-xs); margin-top: var(--spacing-md); }
.m-images.inset { padding: var(--spacing-md); background: var(--bg-card); }
.m-image-wrap { aspect-ratio: 1 / 1; width: 100%; border-radius: var(--radius-xs); overflow: hidden; background: var(--bg-page); }
.m-image { width: 100%; height: 100%; opacity: 0; transition: opacity var(--duration-slow) var(--ease-out); }
.m-image.loaded { opacity: 1; }
</style>
