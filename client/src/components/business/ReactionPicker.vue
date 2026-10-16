<script setup lang="ts">
/**
 * 三态评分控件（规格见 docs/ui/client/公共组件与形态基线.md §2.2 ReactionPicker）。
 * 好吃 5 / 一般 3 / 踩 1；已评分 => 只读展示，不可再点。
 */
const props = withDefaults(
  defineProps<{
    modelValue?: number | null
    readonly?: boolean
  }>(),
  { modelValue: null, readonly: false },
)

const emit = defineEmits<{ (e: 'pick', value: number): void }>()

const OPTIONS = [
  { value: 5, label: '好吃' },
  { value: 3, label: '一般' },
  { value: 1, label: '踩' },
]

function onTap(value: number) {
  if (props.readonly || props.modelValue != null) return
  emit('pick', value)
}
</script>

<template>
  <view class="picker">
    <view
      v-for="opt in OPTIONS"
      :key="opt.value"
      class="picker__item"
      :class="{
        'picker__item--on': modelValue === opt.value,
        'is-disabled': readonly || modelValue != null,
      }"
      hover-class="picker__item--pressed"
      @tap="onTap(opt.value)"
    >
      <text class="picker__label">{{ opt.label }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.picker {
  display: flex;
  gap: var(--spacing-md);
}

.picker__item {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: var(--reaction-item-min-width);
  height: var(--tap-target-size);
  background: var(--bg-page);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-pill);
}

.picker__item--pressed {
  opacity: 0.7;
}

.picker__item--on {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.picker__label {
  font-size: var(--font-body);
  color: var(--text-body);
}

.picker__item--on .picker__label {
  color: var(--color-on-primary);
}
</style>
