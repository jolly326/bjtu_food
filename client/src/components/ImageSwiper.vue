<template>
  <swiper
    class="image-swiper"
    :style="{ height }"
    :indicator-dots="showIndicator"
    :indicator-color="SWIPER_INDICATOR_COLOR"
    :indicator-active-color="indicatorActiveColor"
    :autoplay="autoplay"
    :interval="interval"
    :circular="circular"
  >
    <swiper-item v-for="(img, idx) in displayImages" :key="idx">
      <!-- onload 淡入：图片加载完成前保持占位底色，加载后 0.3s 淡入（Apple §12 materialize） -->
      <image v-if="img" :src="getImageUrl(img)" mode="aspectFill" class="image-swiper-img" :class="{ 'img-loaded': loadedSet.has(idx) }" @load="onImgLoad(idx)" />
      <view v-else class="image-swiper-placeholder">
        <IconSvg name="empty" :size="64" color="var(--text-tertiary)" class="placeholder-icon" />
      </view>
    </swiper-item>
  </swiper>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { getImageUrl } from '@/utils/image'
import IconSvg from './IconSvg.vue'
// 微信原生 <swiper> 的 indicator-active-color / indicator-color 不接受 var()，此处为已知的原生属性限制例外（见 constants/ui.ts 注释），必须用真实色值
import { SWIPER_INDICATOR_ACTIVE_COLOR, SWIPER_INDICATOR_COLOR } from '@/constants/ui'

const props = withDefaults(defineProps<{
  images: string[]
  height?: string
  indicatorDots?: boolean
  indicatorActiveColor?: string
  autoplay?: boolean
  interval?: number
  circular?: boolean
}>(), {
  height: '400rpx',
  indicatorDots: true,
  indicatorActiveColor: SWIPER_INDICATOR_ACTIVE_COLOR,
  autoplay: true,
  interval: 4000,
  circular: true,
})

/** 空图片也保留轮播项目数，用占位图显示 */
const displayImages = computed(() => {
  return props.images.length > 0 ? props.images : ['']
})

/** 单张图时不显示指示器 */
const showIndicator = computed(() => props.indicatorDots && displayImages.value.length > 1)

/** 已加载图片集合：onload 后标记，驱动 0.3s 淡入（Apple §12 materialize） */
const loadedSet = ref<Set<number>>(new Set())
function onImgLoad(idx: number) {
  if (!loadedSet.value.has(idx)) {
    loadedSet.value = new Set(loadedSet.value).add(idx)
  }
}
</script>

<style scoped>
.image-swiper {
  width: 100%;
}
.image-swiper-img {
  width: 100%;
  height: 100%;
  background: var(--bg-page);
  opacity: 0;
  filter: blur(12px);
  transform: scale(1.04);
  transition: opacity 0.3s ease, filter 0.3s ease, transform 0.3s ease;
}
/* 加载完成：由模糊放大淡入至清晰；reduced-motion 下直接显示 */
.image-swiper-img.img-loaded { opacity: 1; filter: blur(0); transform: scale(1); }
@media (prefers-reduced-motion: reduce) {
  .image-swiper-img { opacity: 1; filter: none; transform: none; transition: none; }
}
.image-swiper-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
}
.placeholder-icon {
  font-size: 80rpx;
  line-height: 1;
}
</style>
