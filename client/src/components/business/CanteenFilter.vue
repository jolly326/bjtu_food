<script setup lang="ts">
/**
 * 食堂筛选（规格见 docs/ui/client/公共组件与形态基线.md §2.2 CanteenFilter）。
 * 形态：右上角下拉，用原生 picker（零自绘）。默认「全部」。
 */
import { computed } from 'vue'
import AppIcon from '../base/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    options: { id: number; name: string }[]
    modelValue?: number
  }>(),
  { modelValue: undefined },
)

const emit = defineEmits<{ (e: 'pick', id?: number): void }>()

const ALL = '全部'

const labels = computed(() => [ALL, ...props.options.map((o) => o.name)])

const index = computed(() => {
  if (props.modelValue == null) return 0
  const found = props.options.findIndex((o) => o.id === props.modelValue)
  return found === -1 ? 0 : found + 1
})

const current = computed(() => labels.value[index.value] ?? ALL)

function onChange(e: { detail: { value: number } }) {
  const i = e.detail.value
  emit('pick', i === 0 ? undefined : props.options[i - 1]?.id)
}
</script>

<template>
  <picker :range="labels" :value="index" @change="onChange">
    <view class="filter">
      <text class="filter__label">{{ current }}</text>
      <AppIcon name="arrow-down" :size="28" tone="default" />
    </view>
  </picker>
</template>

<style lang="scss" scoped>
.filter {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: var(--tap-target-size);
  padding: 0 var(--spacing-sm);
  background: var(--bg-page);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-pill);
}

.filter__label {
  font-size: var(--font-body);
  color: var(--text-body);
}
</style>
