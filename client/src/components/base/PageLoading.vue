<script setup lang="ts">
/**
 * 页面级加载提示（规格见 docs/ui/client/公共组件与形态基线.md §2.1.7）。
 * 全屏同色底 + 居中 44rpx 线性图标（仅图标本体以 `--duration-spin` 匀速旋转，无外围圆环）。
 */
import AppIcon from './AppIcon.vue'

defineOptions({ options: { virtualHost: true } })

withDefaults(
  defineProps<{
    /** 加载提示文案，默认「加载中」 */
    text?: string
  }>(),
  { text: '加载中' },
)
</script>

<template>
  <view class="page-loading">
    <view class="page-loading__icon">
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

/* 仅图标本体旋转：无圆环、无描边、无底色 */
.page-loading__icon {
  display: flex;
  animation: page-loading-rotate var(--duration-spin) linear infinite;
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
