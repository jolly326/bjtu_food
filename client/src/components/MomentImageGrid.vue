<template>
  <view v-if="images.length > 0" class="m-images" :class="{ compact: compact }">
    <view
      v-for="(img, idx) in images"
      :key="idx"
      class="m-image-wrap"
      :class="{ pressed: pressedIdx === idx }"
      @touchstart="pressedIdx = idx"
      @touchend="pressedIdx = -1"
      @touchcancel="pressedIdx = -1"
      @mousedown="pressedIdx = idx"
      @mouseup="pressedIdx = -1"
      @mouseleave="pressedIdx = -1"
      @tap="previewImage(idx)"
    >
      <image class="m-image" :src="img" mode="aspectFill" lazy-load />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { getImageUrl } from '@/utils/image'

const props = withDefaults(defineProps<{ images: string[]; compact?: boolean }>(), { compact: false })
const pressedIdx = ref(-1)

function previewImage(idx: number) {
  const urls = props.images.map(getImageUrl)
  uni.previewImage({ urls, current: urls[idx] })
}
</script>

<style scoped>
.m-images { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); padding: var(--spacing-md); background: var(--bg-card); margin-top: 2rpx; }
.m-images.compact { padding: 0; background: transparent; margin-top: var(--spacing-xs); gap: 8rpx; }
.m-image-wrap { width: 220rpx; height: 220rpx; border-radius: var(--radius-tag); overflow: hidden; background: var(--bg-page); flex-shrink: 0; transition: transform 0.12s ease; -webkit-tap-highlight-color: transparent; }
.m-images.compact .m-image-wrap { width: 132rpx; height: 132rpx; }
.m-image-wrap.pressed { transform: scale(var(--press-scale)); }
.m-image { width: 100%; height: 100%; }
</style>
