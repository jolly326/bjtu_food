<template>
  <view class="interact-bar">
    <view class="interact-btn" :class="{ active: usefulActive }" @tap="onUseful">
      <IconSvg name="thumb" :size="32" class="interact-icon" :color="usefulActive ? 'var(--color-like)' : 'var(--text-secondary)'" />
      <text class="interact-count">{{ usefulCount > 0 ? usefulCount : '有用' }}</text>
    </view>
    <view class="interact-btn" @tap="onComment">
      <IconSvg name="comment" :size="32" color="var(--text-secondary)" class="interact-icon" />
      <text class="interact-count">{{ commentCount > 0 ? commentCount : '评论' }}</text>
    </view>
    <view class="interact-btn report" @tap="onReport">
      <IconSvg name="report" :size="32" color="var(--text-secondary)" class="interact-icon" />
      <text class="interact-count">举报</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import IconSvg from '@/components/IconSvg.vue'

const props = defineProps<{
  usefulActive: boolean
  usefulCount: number
  commentCount: number
}>()

const emit = defineEmits<{
  (e: 'useful'): void
  (e: 'comment'): void
  (e: 'report'): void
}>()

function onUseful() { emit('useful') }
function onComment() { emit('comment') }
function onReport() { emit('report') }
</script>

<style scoped>
.interact-bar { display: flex; align-items: center; gap: var(--spacing-md); margin: var(--spacing-md) var(--spacing-md) 0; padding: var(--spacing-sm) var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); }
.interact-btn { display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-md); border-radius: var(--radius-tag); border: 2rpx solid var(--border-light); transition: transform 0.12s ease, background 0.12s ease, border-color 0.12s ease; -webkit-tap-highlight-color: transparent; }
.interact-btn:active { transform: scale(0.97); }
.interact-btn.active { border-color: var(--color-like); background: var(--color-like-soft); }
.interact-icon { font-size: 30rpx; line-height: 1; color: var(--text-secondary); }
.interact-btn.active .interact-icon { color: var(--color-like); }
.interact-count { font-size: 24rpx; font-weight: 600; color: var(--text-secondary); }
.interact-btn.active .interact-count { color: var(--color-like); }
.interact-btn.report { margin-left: auto; }
.interact-btn.report .interact-icon { color: var(--text-tertiary); }
.interact-btn.report .interact-count { color: var(--text-tertiary); }
</style>
