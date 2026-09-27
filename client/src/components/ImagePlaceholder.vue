<template>
  <!-- 全站**唯一**的图片占位视觉（UI 统一 Loop Round 31）：
       灰底（`--bg-placeholder`）+ 居中图标。图片「无值 / 加载失败 / 破图」三种情况一律走本组件，
       SHALL NOT 各页再自绘占位（此前存在 `empty` / `dish` / `user` 三套图标 + `--bg-page` / `--bg-card` /
       `--bg-soft` / `--bg-placeholder` 四种底色，同一屏内观感不一）。 -->
  <view class="img-ph" role="img" :aria-label="ariaLabel">
    <IconSvg :name="name" :size="size" :color="COLOR_MAP['text-tertiary']" />
  </view>
</template>

<script setup lang="ts">
import IconSvg from '@/components/IconSvg.vue'
import { COLOR_MAP } from '@/theme/tokens'

withDefaults(defineProps<{
  /** 占位图标：默认「图片破损」（图片缺失 / 加载失败的标准占位） */
  name?: string
  /** 图标边长（rpx）—— 由宿主容器尺寸决定，与容器内其它图标同档 */
  size?: number
  /** 无障碍标签（读屏播报；缺省按「图片占位」） */
  ariaLabel?: string
}>(), {
  name: 'image-broken',
  size: 64,
  ariaLabel: '图片占位',
})
</script>

<style scoped>
.img-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 灰底唯一真源：不要在各消费方重复声明底色（否则又会分叉） */
  background: var(--bg-placeholder);
  box-sizing: border-box;
}
</style>
