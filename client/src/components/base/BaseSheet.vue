<script setup lang="ts">
/**
 * 底部弹层（口径见 docs/ui/client/公共组件与形态基线.md §2.1.4）。
 * 本项目用法：美食地图点磁贴唤起菜品弹窗（DishSheet），承载详情 / 点赞 / 纠错。
 */
import { ref } from 'vue'

/** 宿主节点虚拟化：组件根直接参与页面布局 */
defineOptions({ options: { virtualHost: true } })

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
    <view class="sheet__panel">
      <view class="sheet__handle" @touchstart="onDragStart" @touchend="onDragEnd">
        <view class="sheet__grabber" />
      </view>
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
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: var(--z-sheet);
}

.sheet__scrim {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: var(--overlay-scrim);
}

.sheet__panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: var(--spacing-md) var(--page-gutter) calc(var(--spacing-lg) + env(safe-area-inset-bottom));
  max-height: 88vh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  background: var(--overlay-panel-bg);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  animation: sheet-rise var(--duration-slow) var(--ease-bounce);
}

.sheet__handle {
  display: flex;
  justify-content: center;
  padding: var(--spacing-sm) 0;
  margin-bottom: var(--spacing-xs);
}

.sheet__grabber {
  width: 72rpx;
  height: 8rpx;
  background: var(--border-bold);
  border-radius: var(--radius-pill);
}

.sheet__title {
  margin-bottom: var(--spacing-md);
  font-family: var(--font-sans);
  font-size: var(--font-subtitle);
  font-weight: var(--weight-regular);
  color: var(--text-title);
}

.sheet__body {
  display: flex;
  flex-direction: column;
}

/* Q 弹升起：配合 --ease-bounce 的过冲，面板会略微冲过头再回落（动森式的「弹」） */
@keyframes sheet-rise {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .sheet__panel {
    animation: none;
  }
}
</style>
