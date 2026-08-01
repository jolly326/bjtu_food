<template>
  <view class="stats-row">
    <view
      v-for="item in cells"
      :key="item.key"
      class="stat-cell"
      :class="{ pressed: pressedKey === item.key, disabled: item.key === 'pending' && (item.value ?? 0) === 0 }"
      @touchstart="pressedKey = item.key"
      @touchend="pressedKey = ''"
      @touchcancel="pressedKey = ''"
      @mousedown="pressedKey = item.key"
      @mouseup="pressedKey = ''"
      @mouseleave="pressedKey = ''"
      @tap="onCellTap(item.key)"
    >
      <text class="stat-value">{{ item.value }}</text>
      <text class="stat-label">{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

export type StatsCellKey = 'review' | 'published' | 'pending'

const props = defineProps<{
  reviewCount?: number
  publishedCount?: number
  pendingCount?: number
}>()

const emit = defineEmits<{ (e: 'tap', key: StatsCellKey): void }>()

const pressedKey = ref('')

function onCellTap(key: StatsCellKey) {
  if (key === 'pending' && (props.pendingCount ?? 0) === 0) return
  emit('tap', key)
}

const cells = computed(() => [
  { key: 'review' as StatsCellKey, label: '我的评价', value: props.reviewCount ?? 0 },
  { key: 'published' as StatsCellKey, label: '已发布', value: props.publishedCount ?? 0 },
  { key: 'pending' as StatsCellKey, label: '待审核', value: props.pendingCount ?? 0 },
])
</script>

<style scoped>
.stats-row { display: flex; gap: var(--spacing-sm); }
.stat-cell {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
  padding: var(--spacing-xs) 0;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
  transition: transform 120ms var(--ease-out);
}
.stat-cell.pressed { transform: scale(var(--press-scale)); }
.stat-cell.disabled { opacity: 0.4; color: var(--text-tertiary); }
.stat-cell.disabled.pressed { transform: none; }
.stat-value { font-size: var(--font-card); font-weight: 700; color: var(--text-primary); line-height: 1.1; }
.stat-label { font-size: var(--font-tiny); color: var(--text-tertiary); }
</style>
