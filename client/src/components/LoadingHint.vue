<template>
  <view class="loading-hint" role="status" aria-label="加载中">
    <text class="loading-hint-text">加载中…</text>
  </view>
</template>

<script setup lang="ts"></script>

<style scoped lang="scss">
.loading-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl) var(--spacing-md);
  box-sizing: border-box;
}

.loading-hint-text {
  font-size: var(--font-body);
  color: var(--text-tertiary);
}

/* 极简静态文本：任何情况下均无过渡/动画；减少动态效果偏好下同样静态 */
@media (prefers-reduced-motion: reduce) {
  .loading-hint-text {
    transition: none;
  }
}
</style>
