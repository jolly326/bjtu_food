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
    <view v-if="badge" class="menu-badge" />
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
}>()

defineEmits<{ (e: 'tap'): void }>()

const pressed = ref(false)
</script>

<style scoped>
.menu-item {
  display: flex;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  gap: var(--spacing-sm);
  border-bottom: 2rpx solid var(--border-color);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
  transition: transform 120ms var(--ease-out), background 0.12s ease;
}
.menu-item:last-child { border-bottom: none; }
.menu-item.pressed { transform: scale(var(--press-scale)); background: var(--bg-soft); }
.menu-icon { flex-shrink: 0; }
.menu-icon-placeholder { width: 36rpx; flex-shrink: 0; }
.menu-label { flex: 1; font-size: var(--font-body); color: var(--text-primary); }
.menu-hint { font-size: var(--font-aux); color: var(--text-tertiary); flex-shrink: 0; }
.menu-arrow { flex-shrink: 0; transform: rotate(180deg); }
.menu-badge { width: 16rpx; height: 16rpx; border-radius: 50%; background: var(--color-error); flex-shrink: 0; margin-right: calc(-1 * var(--spacing-xs)); }
</style>
