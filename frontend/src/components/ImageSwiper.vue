<template>
  <swiper
    class="image-swiper"
    :style="{ height }"
    :indicator-dots="showIndicator"
    indicator-color="rgba(255,255,255,0.4)"
    :indicator-active-color="indicatorActiveColor"
    :autoplay="autoplay"
    :interval="interval"
    :circular="circular"
  >
    <swiper-item v-for="(img, idx) in displayImages" :key="idx">
      <image v-if="img" :src="getImageUrl(img)" mode="aspectFill" class="image-swiper-img" />
      <view v-else class="image-swiper-placeholder">
        <text class="placeholder-icon">{{ EMOJI.dishPlaceholder }}</text>
      </view>
    </swiper-item>
  </swiper>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getImageUrl } from '@/utils/image'
import { EMOJI } from '@/utils/emoji'

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
  indicatorActiveColor: '#ffffff',
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
</script>

<style scoped>
.image-swiper {
  width: 100%;
}
.image-swiper-img {
  width: 100%;
  height: 100%;
  background: var(--bg-page);
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
