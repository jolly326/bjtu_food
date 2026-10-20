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
  <view class="retry" hover-class="retry--pressed" @tap="emit('retry')">
    <AppIcon name="report" :size="44" tone="default" />
    <text class="retry__title">加载失败</text>
    <text class="retry__subtitle">点此重试</text>
  </view>
</template>

<style lang="scss" scoped>
.retry {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xl) var(--spacing-lg);
}

.retry--pressed {
  opacity: 0.7;
}

.retry__title {
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--text-body);
}

.retry__subtitle {
  font-size: var(--font-small);
  color: var(--text-tertiary);
}
</style>
