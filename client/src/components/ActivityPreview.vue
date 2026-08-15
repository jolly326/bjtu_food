<template>
  <view class="activity-preview">
    <SectionTitle title="最新活动" @tap="goActivityList">
      <view class="more-entry" :class="{ pressed }" @touchstart="pressed = true" @touchend="pressed = false" @touchcancel="pressed = false" @mousedown="pressed = true" @mouseup="pressed = false" @mouseleave="pressed = false" @tap.stop="goActivityList">
        <text class="more-text">查看全部</text>
        <IconSvg name="arrow" :size="24" color="var(--text-tertiary)" />
      </view>
    </SectionTitle>

    <view v-if="items.length > 0" class="activity-list">
      <view
        v-for="act in items"
        :key="act.id"
        class="activity-card"
        :class="{ pressed: cardPressed === act.id }"
        @touchstart="cardPressed = act.id"
        @touchend="cardPressed = null"
        @touchcancel="cardPressed = null"
        @mousedown="cardPressed = act.id"
        @mouseup="cardPressed = null"
        @mouseleave="cardPressed = null"
        @tap="openActivity(act)"
      >
        <view class="activity-card-main">
          <text class="activity-title">{{ act.title }}</text>
          <text class="activity-time">{{ act.publishTime ? relativeTime(act.publishTime) : '' }}</text>
          <text v-if="act.description" class="activity-desc">{{ act.description }}</text>
        </view>
        <view class="activity-card-arrow">
          <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" />
        </view>
      </view>
    </view>

    <view v-else class="activity-empty">
      <text class="activity-empty-text">暂无活动，敬请期待</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import IconSvg from './IconSvg.vue'
import SectionTitle from './SectionTitle.vue'
import { relativeTime } from '@/utils/time'
import type { ActivityItem } from '@/api/activity'

const props = defineProps<{
  items: ActivityItem[]
}>()

const pressed = ref(false)
const cardPressed = ref<number | null>(null)

function goActivityList() {
  uni.navigateTo({ url: '/pages/activity/index' })
}

function openActivity(act: ActivityItem) {
  if (act.articleUrl) {
    uni.navigateTo({ url: `/pages/webview?url=${encodeURIComponent(act.articleUrl)}` })
  } else {
    goActivityList()
  }
}
</script>

<style scoped>
.activity-preview {
  margin: var(--spacing-lg) var(--spacing-md) 0;
}
.more-entry {
  display: flex;
  align-items: center;
  gap: 2rpx;
  transition: transform 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.more-entry.pressed { transform: scale(var(--press-scale)); }
.more-text {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  font-weight: var(--weight-medium);
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-sm);
}
.activity-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  transition: background-color 120ms ease, transform 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.activity-card.pressed {
  background-color: var(--bg-soft);
  transform: scale(var(--press-scale));
}
.activity-card-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.activity-title {
  font-size: var(--font-card);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.activity-time {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}
.activity-desc {
  font-size: var(--font-aux);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2rpx;
}
.activity-card-arrow {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.activity-empty {
  margin-top: var(--spacing-sm);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  display: flex;
  align-items: center;
  justify-content: center;
}
.activity-empty-text {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}
</style>
