<script setup lang="ts">
/**
 * 海报滑动容器（规格见 docs/ui/client/公共组件与形态基线.md §2.2 PosterSwiper）。
 * 一屏一张、右侧露边、可回看上一张、无循环；卡片竖直，高度填满宿主（由页面分配，到 TabBar 上沿）。
 * 为空 / 加载失败 ⇒ 渲染 3 张占位卡（同样可横向滑动，见 docs/ui/client/档口海报.md §2 / §4）。
 *
 * 高度实现：原生 `swiper` **不参与 flex 拉伸**（自带默认高度），故本组件绝对定位铺满**定位祖先**
 * （页面里的 `.posterArea` 已 `position: relative` 且由 `flex: 1` 分配高度）—— 高度确定后
 * `swiper-item` / 卡片 / 占位才能按 100% 正确撑满。
 */
import { computed } from 'vue'
import ImagePlaceholder from '../base/ImagePlaceholder.vue'

/** 空 / 失败时铺开的占位卡张数（口径见 docs/ui/client/档口海报.md §2） */
const PLACEHOLDER_COUNT = 3

/**
 * 露边用原生 swiper 的 `next-margin` 实现（该属性只接受字面长度、不认 `var()`）：
 * 取值 = `--page-gutter`(32rpx) + `--poster-peek`(48rpx)，与设计变量同值。
 */
const NEXT_MARGIN = '80rpx'

const props = defineProps<{
  posters: { id: number; imageUrl: string }[]
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
    :next-margin="NEXT_MARGIN"
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
            <ImagePlaceholder />
          </template>
          <template #loading>
            <ImagePlaceholder />
          </template>
        </image>
        <ImagePlaceholder v-else />
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

/* 只留左侧 --page-gutter：卡宽 = 100vw - 2 × gutter - peek、卡间距 = gutter、露边 = peek */
.poster__item {
  display: flex;
  box-sizing: border-box;
  height: 100%;
  padding-left: var(--page-gutter);
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
