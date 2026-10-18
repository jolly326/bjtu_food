<script setup lang="ts">
/**
 * 海报滑动容器（规格见 docs/ui/client/公共组件与形态基线.md §2.2 PosterSwiper）。
 * 一屏一张、左侧 `--page-gutter`(48rpx) / 右侧露下一张边缘（peek）、可回看上一张、无循环；
 * 卡片占满宿主高度（卡顶紧接板块标题、卡底靠近 TabBar）。
 * 为空 / 加载失败 ⇒ 渲染 3 张占位卡（同样可横向滑动，见 docs/ui/client/档口海报.md §2 / §4）。
 *
 * 高度实现：原生 `swiper` **不参与 flex 拉伸**（自带默认高度），故本组件绝对定位铺满**定位祖先**
 * （页面里的 `.posterArea` 已 `position: relative` 且由 `flex: 1` 分配高度）。
 */
import { computed } from 'vue'
import ImagePlaceholder from '../base/ImagePlaceholder.vue'
import { windowWidth } from '@/utils/safeArea'

import type { PosterOut } from '@/api'

/** 空 / 失败时铺开的占位卡张数（口径见 docs/ui/client/档口海报.md §2） */
const PLACEHOLDER_COUNT = 3

/** 右侧 peek 宽度：48rpx 换算为 px（swiper `next-margin` 只接受带单位长度） */
const PEEK_RPX = 48
const rpxToPx = windowWidth() / 750
const nextMargin = `${Math.round(PEEK_RPX * rpxToPx)}px`

const props = defineProps<{
  posters: PosterOut[]
}>()

/** 有数据用真实海报；否则用等卡位的占位卡（负 id 只作 key，不与真实主键冲突） */
const items = computed(() => {
  if (props.posters.length) return props.posters
  return Array.from({ length: PLACEHOLDER_COUNT }, (_, index) => ({
    id: -1 - index,
    imageUrl: '',
  }))
})
</script>

<template>
  <swiper
    class="poster"
    :indicator-dots="false"
    :circular="false"
    :next-margin="nextMargin"
  >
    <swiper-item v-for="item in items" :key="item.id" class="poster__item">
      <view class="poster__card">
        <image
          v-if="item.imageUrl"
          class="poster__img"
          :src="item.imageUrl"
          mode="aspectFill"
          :lazy-load="true"
        >
          <template #error>
            <ImagePlaceholder :size="48" />
          </template>
          <template #loading>
            <ImagePlaceholder :size="48" />
          </template>
        </image>
        <ImagePlaceholder v-else :size="48" />
      </view>
    </swiper-item>
  </swiper>
</template>

<style lang="scss" scoped>
/* 绝对定位铺满定位祖先（页面的 .posterArea）—— 原生 swiper 不接受 flex 拉伸 */
.poster {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

/* 左 --page-gutter / 右 --spacing-md（与 peek 出的下一张之间留呼吸位） */
/* 卡片占满轮播区（卡顶紧接「为你推荐」，卡底靠近 TabBar） */
.poster__item {
  display: flex;
  align-items: stretch;
  box-sizing: border-box;
  height: 100%;
  padding: 0 var(--spacing-md) 0 var(--page-gutter);
}

/* 卡片圆角 / 阴影与美食页单卡统一 */
.poster__card {
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--bg-placeholder);
  border-radius: var(--radius-card-stack);
  box-shadow: var(--shadow-card-stack);
}

.poster__img {
  width: 100%;
  height: 100%;
}
</style>
