<template>
  <!-- 全站**唯一**的图片占位视觉：
       默认 = 灰底（`--bg-placeholder`）+ 居中图标；图片「无值 / 加载失败 / 破图」三种情况一律走本组件，
       SHALL NOT 各页再自绘占位（统一视觉：灰底 + 居中图标）。
       `bare` 变体 = **无底色**、只留居中图标：给「Banner 这类本就铺满图片的区块」用 ——
       区块自身已有底（壁纸 / 白卡），再加一层灰底只会在页面里切出一块灰。 -->
  <view class="img-ph" :class="{ 'is-bare': bare }" role="img" :aria-label="ariaLabel">
    <AppIcon :name="name" :size="size" :color="COLOR_MAP['text-tertiary']" />
  </view>
</template>

<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue'
import { COLOR_MAP } from '@/theme/tokens'

withDefaults(defineProps<{
  /** 占位图标：默认「图片破损」（图片缺失 / 加载失败的标准占位） */
  name?: string
  /** 图标边长（rpx）—— 由宿主容器尺寸决定，与容器内其它图标同档 */
  size?: number
  /** 无障碍标签（读屏播报；缺省按「图片占位」） */
  ariaLabel?: string
  /** 无底色变体：只渲染居中图标，不铺灰底 */
  bare?: boolean
}>(), {
  name: 'image-broken',
  size: 64,
  ariaLabel: '图片占位',
  bare: false,
})
</script>

<style scoped>
.img-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 占位底唯一真源（暖调不透明）：不要在各消费方重复声明底色（否则又会分叉） */
  background: var(--bg-placeholder);
  box-sizing: border-box;
}
/* 无底色变体：宿主已有底色时用，避免页面出现无意义的灰块 */
.img-ph.is-bare {
  background: transparent;
}
</style>
