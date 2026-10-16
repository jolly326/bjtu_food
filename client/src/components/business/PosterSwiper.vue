<script setup lang="ts">
/**
 * 海报滑动容器（规格见 docs/ui/client/公共组件与形态基线.md §2.2 PosterSwiper）。
 * 一屏一张、右侧露边、可回看上一张、无循环。
 */
import ImagePlaceholder from '../base/ImagePlaceholder.vue'

defineProps<{
  posters: { id: number; imageUrl: string }[]
}>()
</script>

<template>
  <swiper class="poster" :indicator-dots="false" :circular="false" :previous-margin="'0rpx'">
    <swiper-item v-for="item in posters" :key="item.id" class="poster__item">
      <view class="poster__card">
        <image
          class="poster__img"
          :src="item.imageUrl"
          mode="aspectFill"
          :lazy-load="true"
        >
          <template #error>
            <ImagePlaceholder />
          </template>
          <template #loading>
            <ImagePlaceholder />
          </template>
        </image>
      </view>
    </swiper-item>
  </swiper>
</template>

<style lang="scss" scoped>
.poster {
  flex: 1;
  min-height: 0;
  max-height: calc((100vw - 2 * var(--page-gutter) - var(--poster-peek)) * 4 / 3);
}

.poster__item {
  display: flex;
  box-sizing: border-box;
  padding-right: var(--poster-peek);
}

.poster__card {
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--bg-placeholder);
  border-radius: var(--radius-card);
  box-shadow: var(--module-shadow);
}

.poster__img {
  width: 100%;
  height: 100%;
}
</style>
