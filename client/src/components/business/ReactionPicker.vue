<script setup lang="ts">
/**
 * 三态评分控件（规格见 docs/ui/client/公共组件与形态基线.md §2.2 ReactionPicker）。
 * 喜欢 5（smile）/ 一般 3（meh）/ 不喜欢 1（frown）；已评分 => 只读展示，不可再点。
 */
import { computed } from 'vue'
import AppIcon from '../base/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    modelValue?: number | null
    readonly?: boolean
  }>(),
  { modelValue: null, readonly: false },
)

const emit = defineEmits<{ (e: 'pick', value: number): void }>()

const OPTIONS = [
  { value: 5, label: '喜欢', icon: 'smile' },
  { value: 3, label: '一般', icon: 'meh' },
  { value: 1, label: '不喜欢', icon: 'frown' },
] as const

/** 已评分只展示选中项；个人档位固定三态 */
const visibleOptions = computed(() =>
  props.modelValue == null ? OPTIONS : OPTIONS.filter((o) => o.value === props.modelValue),
)

function onTap(value: number) {
  if (props.readonly || props.modelValue != null) return
  emit('pick', value)
}
</script>

<template>
  <view class="picker" :class="{ 'picker--readonly': modelValue != null }">
    <view
      v-for="opt in visibleOptions"
      :key="opt.value"
      class="picker__item"
      :class="{ 'picker__item--on': modelValue === opt.value }"
      :hover-class="modelValue == null ? 'picker__item--pressed' : 'none'"
      @tap="onTap(opt.value)"
    >
      <AppIcon
        :name="opt.icon"
        :size="28"
        :tone="modelValue === opt.value ? 'primary' : 'muted'"
      />
      <text class="picker__label">{{ opt.label }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.picker {
  display: flex;
  gap: var(--spacing-md);
  width: 100%;
}

.picker__item {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  min-width: var(--reaction-item-min-width);
  height: var(--tap-target-size);
  background: var(--bg-page);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-pill);
}

/* 已评分：只显示选中项，收成紧凑胶囊 */
.picker--readonly .picker__item {
  flex: 0 0 auto;
  min-width: 0;
  padding: 0 var(--spacing-lg);
}

.picker__item--pressed {
  opacity: 0.7;
}

.picker__item--on {
  border-color: var(--color-primary);
}

.picker__label {
  font-size: var(--font-body);
  color: var(--text-tertiary);
}

.picker__item--on .picker__label {
  font-weight: var(--weight-medium);
  color: var(--text-title);
}
</style>
