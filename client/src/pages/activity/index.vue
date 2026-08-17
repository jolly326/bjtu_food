<template>
  <view class="page activity-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="最新活动" @back="backToHome" />
    <scroll-view
      class="scroll-wrap"
      scroll-y
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      :scroll-with-animation="!reduceMotion"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view class="activity-list" v-if="list.length > 0">
        <view
          v-for="act in list"
          :key="act.id"
          class="activity-card"
          :class="{ pressed: pressedId === act.id }"
          @touchstart="pressedId = act.id"
          @touchend="pressedId = null"
          @touchcancel="pressedId = null"
          @mousedown="pressedId = act.id"
          @mouseup="pressedId = null"
          @mouseleave="pressedId = null"
          @tap="openActivity(act)"
        >
          <view class="activity-card-head">
            <text class="activity-title">{{ act.title }}</text>
            <text v-if="act.publishTime" class="activity-time">{{ formatTime(act.publishTime) }}</text>
          </view>
          <text v-if="act.description" class="activity-desc">{{ act.description }}</text>
          <view class="activity-card-foot">
            <text class="activity-link">{{ act.articleUrl ? '查看活动详情 ›' : '敬请关注' }}</text>
          </view>
        </view>
      </view>
      <EmptyState v-else-if="!loading" text="暂无活动，敬请期待" icon="broadcast" />

      <view class="loading-more" v-if="loading">加载中…</view>
      <view class="loading-more" v-else-if="finished && list.length > 0">没有更多了</view>

      <view style="height: calc(var(--spacing-lg) + env(safe-area-inset-bottom))" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { getActivities, type ActivityItem } from '@/api/activity'
import { relativeTime } from '@/utils/time'
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import EmptyState from '@/components/EmptyState.vue'

const theme = useThemeStore()

const page = ref(1)
const pageSize = 20
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const list = ref<ActivityItem[]>([])
const pressedId = ref<number | null>(null)

const reduceMotion = ref(false)
if (typeof window !== 'undefined') {
  reduceMotion.value = !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

function formatTime(t: string) {
  return relativeTime(t)
}

async function fetchPage(reset: boolean) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await getActivities({ page: page.value, pageSize })
    if (reset) list.value = res
    else list.value = list.value.concat(res)
    finished.value = res.length < pageSize
    if (!reset && res.length > 0) page.value += 1
  } catch (e) {
    console.error('[activity] 加载失败', e)
    if (reset) list.value = []
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function loadMore() {
  if (!finished.value) fetchPage(false)
}

function onRefresh() {
  page.value = 1
  finished.value = false
  refreshing.value = true
  fetchPage(true)
}

function openActivity(act: ActivityItem) {
  if (act.articleUrl) {
    uni.navigateTo({ url: `/pages/webview?url=${encodeURIComponent(act.articleUrl)}` })
  }
}

onLoad(() => {
  page.value = 1
  fetchPage(true)
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  background: var(--bg-page);
}
.scroll-wrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-bottom: env(safe-area-inset-bottom);
}
.activity-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
}
.activity-card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-lg);
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
.activity-card-head {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.activity-title {
  font-size: var(--font-title);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  line-height: 1.4;
}
.activity-time {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}
.activity-desc {
  font-size: var(--font-body);
  color: var(--text-secondary);
  line-height: 1.6;
}
.activity-card-foot {
  display: flex;
  align-items: center;
}
.activity-link {
  font-size: var(--font-aux);
  font-weight: var(--weight-semibold);
  color: var(--color-primary);
}
.loading-more {
  text-align: center;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  padding: var(--spacing-md);
}
</style>
