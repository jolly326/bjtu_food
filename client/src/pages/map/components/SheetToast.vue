<script setup lang="ts">
/**
 * 成功提示 toast（DishSheet 私有组件）。
 * 奶米底、细棕描边、无图标；`leaving` 驱动淡出动画（时序由组合器控制）。
 */
withDefaults(
  defineProps<{
    visible: boolean
    message?: string
    leaving?: boolean
  }>(),
  { visible: false, message: '反馈已提交', leaving: false },
)
</script>

<template>
  <view
    v-if="visible"
    class="sheet-toast"
    :class="{ 'sheet-toast--leaving': leaving }"
    aria-role="status"
  >
    <text class="sheet-toast__text">{{ message }}</text>
  </view>
</template>

<style lang="scss" scoped>
.sheet-toast {
  position: fixed;
  bottom: calc(var(--spacing-2xl) + env(safe-area-inset-bottom));
  left: 50%;
  z-index: calc(var(--z-sheet) + 200);
  padding: var(--spacing-sm) var(--spacing-lg);
  border: 1rpx solid var(--sheet-pill-border);
  border-radius: var(--radius-pill);
  background: var(--overlay-panel-bg);
  pointer-events: none;
  transform: translateX(-50%);
  animation: sheet-toast-in 180ms var(--ease-out);
}

.sheet-toast--leaving {
  animation: sheet-toast-out 180ms var(--ease-out) forwards;
}

.sheet-toast__text {
  color: var(--text-title);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  line-height: 1.3;
  white-space: nowrap;
}

@keyframes sheet-toast-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

@keyframes sheet-toast-out {
  from {
    opacity: 1;
  }

  to {
    opacity: 0;
  }
}
</style>
