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
/* 扁平容器（不带卡片背景/圆角/外边距，由父级卡片控制整体样式；
   注意：mp-weixin 组件样式隔离，父级 :deep() 无法命中本组件根节点，
   顶部留白必须写在本组件内，避免点赞栏与上方分隔线贴合） */
.interact-bar { display: flex; align-items: center; gap: var(--spacing-md); margin: 0; padding: var(--spacing-md) 0 0; }
.interact-btn { display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-md); border-radius: var(--radius-tag); border: 2rpx solid var(--border-light); background: var(--bg-soft); transition: transform 0.12s ease, background 0.12s ease, border-color 0.12s ease; -webkit-tap-highlight-color: transparent; }
.interact-btn:active { transform: scale(var(--press-scale)); }
.interact-btn.active { border-color: var(--color-like); background: var(--color-like-soft); }
.interact-icon { font-size: 30rpx; line-height: 1; color: var(--text-secondary); }
.interact-btn.active .interact-icon { color: var(--color-like); }
.interact-count { font-size: 24rpx; font-weight: var(--weight-semibold); color: var(--text-secondary); }
.interact-btn.active .interact-count { color: var(--color-like); }
.interact-btn.report { margin-left: auto; }
.interact-btn.report .interact-icon { color: var(--text-tertiary); }
.interact-btn.report .interact-count { color: var(--text-tertiary); }
</style>
