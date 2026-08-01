<template>
  <view
    class="menu-item"
    :class="{ pressed }"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @mousedown="pressed = true"
    @mouseup="pressed = false"
    @mouseleave="pressed = false"
    @tap="$emit('tap')"
  >
    <IconSvg v-if="icon" :name="icon" :size="36" color="var(--text-secondary)" class="menu-icon" />
    <text v-else class="menu-icon-placeholder" />
    <text class="menu-label">{{ label }}</text>
    <text v-if="hint" class="menu-hint">{{ hint }}</text>
    <view v-if="badgeCount && badgeCount > 0" class="menu-badge-count">{{ badgeCount > 99 ? '99+' : badgeCount }}</view>
    <view v-else-if="badge" class="menu-badge" />
    <IconSvg name="arrow-left" :size="28" color="var(--text-tertiary)" class="menu-arrow" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import IconSvg from '@/components/IconSvg.vue'

defineProps<{
  label: string
  icon?: string
  hint?: string
  badge?: boolean
  /** 数值型未读角标；传入 >0 时显示数字（上限 99+），优先级高于 badge 红点 */
  badgeCount?: number
}>()

defineEmits<{ (e: 'tap'): void }>()

const pressed = ref(false)
</script>

<style scoped>
.menu-item {
  display: flex;
  align-items: center;
  padding: var(--spacing-md);
  gap: var(--spacing-sm);
  border-bottom: 2rpx solid var(--border-color);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
  transition: transform 120ms var(--ease-out), background 0.12s ease;
}
.menu-item:last-child { border-bottom: none; }
.menu-item.pressed { transform: scale(var(--press-scale)); background: var(--bg-soft); }
/* 图标与文本严格中线对齐：图标 block + line-height:1，避免图标因 image 默认行高偏上 */
.menu-icon { flex-shrink: 0; display: block; line-height: 1; align-self: center; }
.menu-icon-placeholder { width: 36rpx; flex-shrink: 0; display: block; line-height: 1; }
.menu-label { flex: 1; font-size: var(--font-body); color: var(--text-primary); line-height: 1; align-self: center; }
.menu-hint { font-size: var(--font-aux); color: var(--text-tertiary); flex-shrink: 0; }
.menu-arrow { flex-shrink: 0; transform: rotate(180deg); }
.menu-badge { width: 16rpx; height: 16rpx; border-radius: 50%; background: var(--color-error); flex-shrink: 0; margin-right: calc(-1 * var(--spacing-xs)); }
.menu-badge-count {
  flex-shrink: 0;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  background: var(--color-error);
  color: var(--text-white);
  font-size: var(--font-aux);
  font-weight: 600;
  line-height: 32rpx;
  text-align: center;
  margin-right: calc(-1 * var(--spacing-xs));
}
</style>
