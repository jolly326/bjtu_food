<script setup lang="ts">
/**
 * 加载失败块（规格见 docs/ui/client/公共组件与形态基线.md §2.1.3）。
 * 整块可点即重试；点击后父级立即切到加载态，本块随之卸载。
 */
import AppIcon from './AppIcon.vue'

/** 宿主节点虚拟化：组件根直接参与页面 flex 布局 */
defineOptions({ options: { virtualHost: true } })

const emit = defineEmits<{ (e: 'retry'): void }>()
</script>

<template>
  <view
    class="retry"
    hover-class="retry--pressed"
    aria-role="button"
    aria-label="加载失败，点击重试"
    @tap="emit('retry')"
  >
    <AppIcon name="warning-circle" :size="44" />
    <text class="retry__title">加载失败</text>
  </view>
</template>

<style lang="scss" scoped>
.retry {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  gap: var(--spacing-xs);
  padding: var(--spacing-xl) var(--spacing-lg);
}

.retry--pressed {
  opacity: 0.7;
}

.retry__title {
  font-family: var(--font-sans);
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  line-height: 1.2;
  color: var(--text-body);
}
</style>
