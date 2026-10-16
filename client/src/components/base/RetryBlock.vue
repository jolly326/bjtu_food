<script setup lang="ts">
/**
 * 加载失败块（规格见 docs/ui/client/公共组件与形态基线.md §2.1.3）。
 * 整块可点；在途时忽略点击。
 */
import AppIcon from './AppIcon.vue'

defineProps<{
  loading?: boolean
}>()

const emit = defineEmits<{ (e: 'retry'): void }>()

function onTap() {
  emit('retry')
}
</script>

<template>
  <view class="retry" :class="{ 'is-disabled': loading }" hover-class="retry--pressed" @tap="onTap">
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
