<script setup lang="ts">
/**
 * 食堂筛选（规格见 docs/ui/client/公共组件与形态基线.md §2.2 CanteenFilter）。
 * 形态：右上角下拉，用原生 picker（零自绘）。默认「全部」。
 * 轻量化：细描边 + 低饱和度（弱化文字 / 图标），不抢视觉重心；命中区保持触达下限。
 */
import { computed } from 'vue'
import AppIcon from '@/components/base/AppIcon.vue'

import type { CanteenOut } from '@/api'

/** 宿主节点虚拟化：组件根直接参与页头 flex 布局 */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    options: CanteenOut[]
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
      <AppIcon name="arrow-down" :size="28" tone="muted" />
    </view>
  </picker>
</template>

<style lang="scss" scoped>
/* 筛选胶囊：细描边 + 小幅度柔和圆角（非全圆），轻量化不抢视觉重心 */
.filter {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  box-sizing: border-box;
  height: var(--tap-target-size);
  padding: 0 var(--spacing-md);
  background: var(--color-primary-soft);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-btn);
}

.filter__label {
  font-size: var(--font-small);
  color: var(--text-tertiary);
}
</style>
