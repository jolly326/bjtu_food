<script setup lang="ts">
/**
 * 风物页 · 海报横向滑动器（slider）：单卡大圆角 + 柔和弥散阴影，图铺满卡、无留白；
 * 卡片在原生 swiper 内横向排布，右邻卡露出一小段左缘（`next-margin`）暗示可横滑；
 * 规格见 docs/ui/client/风物.md §2 / §3 与基线 §2.2。
 *
 * peek 实现：原生 swiper 的 `next-margin` 只认「当前项右缘到屏右的距离」；下一项的卡片
 * 还要加上自身的左内距才可见 ⇒ next-margin = 左 gutter + 露出量，项内**不设右内距**
 * （卡间呼吸位由下一项的左内距承担）。
 *
 * 高度实现：原生 `swiper` **不参与 flex 拉伸**（自带默认高度），故本组件根节点绝对定位铺满
 * 定位祖先（页面里的 `.posterArea__swiper` 已 `position: relative` 且由 `flex: 1` 分配高度）。
 */
import { computed } from 'vue'
import ImagePlaceholder from '@/components/base/ImagePlaceholder.vue'
import { windowWidth } from '@/utils/safeArea'

import type { PosterOut } from '@/api'

/** 宿主节点虚拟化：根直接参与页面布局；内部 swiper 绝对定位铺满定位祖先 */
defineOptions({ options: { virtualHost: true } })

/** 空 / 失败时铺开的占位卡张数（口径见 docs/ui/client/风物.md §4） */
const PLACEHOLDER_COUNT = 3
/** 右侧露出下一张卡左缘的量：24rpx（一小截，暗示可滑动） */
const PEEK_RPX = 24
/** 卡片左 gutter：32rpx（与 CSS --page-gutter 同步；下一张卡的左内距 ⇒ 卡间呼吸位同值） */
const GUTTER_RPX = 32
const rpxToPx = windowWidth() / 750
const nextMargin = `${Math.round((GUTTER_RPX + PEEK_RPX) * rpxToPx)}px`

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
  <view class="poster">
    <swiper
      class="poster__swiper"
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
  </view>
</template>

<style lang="scss" scoped>
/* 绝对定位铺满定位祖先（页面的 .posterArea__swiper）—— 内部 swiper 填满本节点 */
.poster {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}

.poster__swiper {
  width: 100%;
  height: 100%;
}

/* 左 --page-gutter（卡片左缘）；**无右内距** —— 卡间呼吸位由下一项的左内距承担，
   当前卡右缘即露出区的起点，保证下一张卡的左缘真实可见 */
/* 卡片占满轮播区（卡顶紧接「今日食单」，卡底靠近 TabBar 容器） */
.poster__item {
  display: flex;
  align-items: stretch;
  box-sizing: border-box;
  height: 100%;
  padding: 0 0 0 var(--page-gutter);
}

/* 单卡：大圆角 + 柔和弥散阴影（浮起层次）；图铺满卡、无内距留白；卡底仅作图未就绪兜底 */
.poster__card {
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--bg-page);
  border-radius: var(--radius-card-stack);
  box-shadow: var(--shadow-card-stack);
}

.poster__img {
  width: 100%;
  height: 100%;
}
</style>
