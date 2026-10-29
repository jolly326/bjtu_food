<script setup lang="ts">
/**
 * DishSheet 私有胶囊按钮（反馈选项 / 分页导航 / 确认气泡操作）。
 * 统一浅杏底、细棕描边、大圆角、按压回弹与置灰禁用。
 */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    disabled?: boolean
    /** 主操作变体：杏色加深一档 */
    accent?: boolean
    ariaLabel?: string
  }>(),
  { disabled: false, accent: false, ariaLabel: '' },
)

const emit = defineEmits<{
  (e: 'press'): void
}>()

function onTap(): void {
  if (props.disabled) return
  emit('press')
}
</script>

<template>
  <view
    class="sheet-pill"
    :class="{
      'sheet-pill--accent': accent,
      'sheet-pill--disabled': disabled,
    }"
    :hover-class="disabled ? 'none' : 'sheet-pill--pressed'"
    aria-role="button"
    :aria-disabled="disabled"
    :aria-label="ariaLabel || undefined"
    @tap="onTap"
  >
    <text class="sheet-pill__label"><slot /></text>
  </view>
</template>

<style lang="scss" scoped>
.sheet-pill {
  display: flex;
  flex: 1 1 0;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: var(--tap-target-size);
  padding: 0 var(--spacing-md);
  border: 2rpx solid var(--sheet-pill-border);
  border-radius: var(--radius-pill);
  background: var(--sheet-pill-bg);
  color: var(--text-title);
  font-family: var(--font-sans);
  font-size: var(--font-subtitle);
  line-height: 1.2;
  text-align: center;
  transition:
    transform var(--duration-fast) var(--ease-bounce),
    opacity var(--duration-fast) var(--ease-out);
}

/* 主操作：杏色加深一档 */
.sheet-pill--accent {
  background: var(--sheet-pill-bg-strong);
}

.sheet-pill__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sheet-pill--pressed {
  transform: scale(0.96);
}

/* 置灰：暖灰棕描边与文字（不用冷灰、不用灰度滤镜） */
.sheet-pill--disabled {
  border-color: var(--text-disabled);
  background: var(--sheet-pill-bg);
  color: var(--text-disabled);
}

@media (prefers-reduced-motion: reduce) {
  .sheet-pill {
    transition: none;
  }

  .sheet-pill--pressed {
    transform: none;
  }
}
</style>
