<script setup lang="ts">
/**
 * 底部弹层（口径见 docs/ui/client/公共组件与形态基线.md §2.1.4）。
 * 本设计唯一用法：我的食谱页的「纠错选项」弹层（单选、无图标）。
 */
import { ref } from 'vue'

withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
  }>(),
  { title: '' },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'close'): void
}>()

/** 下拉关闭：位移超过 120rpx（= 60px）即关闭 */
const CLOSE_DRAG_PX = 60
const dragStartY = ref(0)

function onDragStart(event: TouchEvent) {
  dragStartY.value = event.touches[0].clientY
}

function onDragEnd(event: TouchEvent) {
  if (event.changedTouches[0].clientY - dragStartY.value > CLOSE_DRAG_PX) close()
}

function close() {
  emit('update:modelValue', false)
  emit('close')
}
</script>

<template>
  <view v-if="modelValue" class="sheet">
    <view class="sheet__scrim" @tap="close" />
    <view
      class="sheet__panel"
      @touchstart="onDragStart"
      @touchend="onDragEnd"
    >
      <view class="sheet__grabber" />
      <view v-if="title" class="sheet__title">{{ title }}</view>
      <view class="sheet__body">
        <slot />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.sheet {
  position: fixed;
  inset: 0;
  z-index: var(--z-sheet);
}

.sheet__scrim {
  position: absolute;
  inset: 0;
  background: var(--overlay-scrim);
}

.sheet__panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: var(--spacing-md) var(--page-gutter) calc(var(--spacing-lg) + env(safe-area-inset-bottom));
  background: var(--overlay-panel-bg);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  animation: slide-up var(--duration-slow) var(--ease-out);
}

.sheet__grabber {
  width: 72rpx;
  height: 8rpx;
  margin: 0 auto var(--spacing-md);
  background: var(--border-bold);
  border-radius: var(--radius-pill);
}

.sheet__title {
  margin-bottom: var(--spacing-md);
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  color: var(--text-title);
}

.sheet__body {
  display: flex;
  flex-direction: column;
}

@keyframes slide-up {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}
</style>
