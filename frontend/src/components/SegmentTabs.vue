<template>
  <view class="segment-tabs" :class="{ 'seg-light': light }">
    <view
      v-for="(tab, i) in tabs"
      :key="tab.key"
      class="seg-item"
      :class="{ active: modelValue === tab.key }"
      @tap="select(tab.key)"
    >
      <IconSvg
        v-if="tab.icon"
        :name="tab.icon"
        :size="26"
        :color="modelValue === tab.key ? 'var(--text-white)' : 'var(--text-secondary)'"
      />
      <text class="seg-label">{{ tab.label }}</text>
    </view>
    <view class="seg-thumb" :style="thumbStyle" />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import IconSvg from '@/components/IconSvg.vue'

export interface SegmentTab {
  key: string
  label: string
  icon?: string
}

const props = withDefaults(defineProps<{
  tabs: SegmentTab[]
  modelValue: string
  /** 浅色模式（白底高亮），用于 find 筛选结果页排序条 */
  light?: boolean
}>(), {
  light: false,
})

const emit = defineEmits<{ (e: 'update:modelValue', key: string): void }>()

const activeIndex = computed(() => Math.max(0, props.tabs.findIndex(t => t.key === props.modelValue)))

// 滑块位置：等宽均分，按激活索引平移（damping 1.0 / response 0.3，临界阻尼无回弹）
const thumbStyle = computed(() => ({
  width: `${100 / props.tabs.length}%`,
  transform: `translateX(${activeIndex.value * 100}%)`,
  transition: 'transform 0.3s cubic-bezier(0.32, 0.72, 0, 1)',
}))

function select(key: string) {
  if (key === props.modelValue) return
  emit('update:modelValue', key)
}
</script>

<style scoped>
.segment-tabs {
  position: relative;
  display: flex;
  background: var(--bg-placeholder);
  border-radius: 999rpx;
  padding: 6rpx;
  box-sizing: border-box;
}
.seg-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  height: 60rpx;
  z-index: 1;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--text-secondary);
  transition: color 0.2s ease;
  -webkit-tap-highlight-color: transparent;
}
.seg-item.active { color: var(--text-white); }
.seg-label { line-height: 1; }

/* 滑块（高亮背景），随激活项平移 */
.seg-thumb {
  position: absolute;
  top: 6rpx;
  left: 6rpx;
  bottom: 6rpx;
  width: 0;
  border-radius: 999rpx;
  background: var(--color-primary);
  box-shadow: var(--shadow-card);
  z-index: 0;
  will-change: transform;
}

/* 浅色模式：白底高亮（find 排序条） */
.seg-light { background: var(--bg-card); }
.seg-light .seg-thumb { background: var(--color-primary); }

@media (prefers-reduced-motion: reduce) {
  .seg-thumb { transition: none !important; }
}
</style>
