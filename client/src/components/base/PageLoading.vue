<script setup lang="ts">
/**
 * 页面级加载提示（Julienne 风格）。
 *
 * - 全屏固定，背景与页面同色（--bg-page），避免黑白遮罩突兀。
 * - 中间线性刷新图标（refresh-cw），暖橙黄主色，360ms 线性旋转。
 * - 下方衬线文案，默认「加载中」，可通过 props 覆盖。
 * - 深色模式自动适配（跟随页面根视图 data-theme）。
 */
defineOptions({ options: { virtualHost: true } })

import AppIcon from './AppIcon.vue'

const props = withDefaults(
  defineProps<{
    /** 加载提示文案，默认「加载中」 */
    text?: string
  }>(),
  { text: '加载中' },
)
</script>

<template>
  <view class="page-loading">
    <view class="page-loading__ring">
      <AppIcon name="refresh-cw" :size="44" tone="primary" />
    </view>
    <text class="page-loading__text">{{ text }}</text>
  </view>
</template>

<style lang="scss" scoped>
.page-loading {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
}

.page-loading__ring {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: 120rpx;
  height: 120rpx;
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-pill);
  animation: page-loading-rotate var(--duration-slow) linear infinite;
}

.page-loading__text {
  margin-top: var(--spacing-md);
  font-family: var(--font-serif);
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  line-height: 1.2;
  color: var(--text-title);
}

@keyframes page-loading-rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>