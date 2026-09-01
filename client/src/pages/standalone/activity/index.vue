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
          <!-- 公众号文章卡片：来源标识 + 日期 / 标题 / 摘要 / 阅读原文 -->
          <view class="activity-card-head">
            <view class="activity-source">
              <view class="activity-source-icon">
                <IconSvg name="broadcast" :size="26" color="var(--color-primary)" />
              </view>
              <text class="activity-source-text">食堂公众号</text>
            </view>
            <text v-if="act.publishTime" class="activity-time">{{ formatTime(act.publishTime) }}</text>
          </view>
          <text class="activity-title">{{ act.title }}</text>
          <text v-if="act.description" class="activity-desc">{{ act.description }}</text>
          <view class="activity-card-foot">
            <text class="activity-link" :class="{ 'activity-link--muted': !act.articleUrl }">{{ act.articleUrl ? '阅读原文' : '敬请关注' }}</text>
            <IconSvg v-if="act.articleUrl" name="arrow" :size="24" color="var(--color-primary)" />
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
import { useReducedMotion } from '@/composables/useReducedMotion'
import { onLoad } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { getActivities, type ActivityItem } from '@/api/activity'
import { formatDateTime } from '@/utils/time'
import { backToHome } from '@/utils/nav'
import Header from '@/components/AppHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconSvg from '@/components/IconSvg.vue'

const theme = useThemeStore()

const page = ref(1)
const pageSize = 20
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const list = ref<ActivityItem[]>([])
const pressedId = ref<number | null>(null)

const reduceMotion = useReducedMotion().reduceMotion

function formatTime(t: string) {
  return formatDateTime(t)
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
    // 修复：跳转路径必须带 /index（pages.json 注册的是 pages/webview/index），
    // 否则 uni.navigateTo 找不到页面导致活动文章打不开
    uni.navigateTo({ url: `/pages/standalone/webview/index?url=${encodeURIComponent(act.articleUrl)}` })
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
  transition: background-color var(--duration-fast) ease, transform var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
.activity-card.pressed {
  background-color: var(--bg-soft);
  transform: scale(var(--press-scale));
}
.activity-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}
/* 来源标识：公众号小图标 + 文字 */
.activity-source {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  flex-shrink: 0;
}
.activity-source-icon {
  width: 40rpx;
  height: 40rpx;
  border-radius: var(--radius-icon);
  background: var(--color-primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.activity-source-text {
  font-size: var(--font-aux);
  font-weight: var(--weight-semibold);
  color: var(--color-primary);
}
.activity-title {
  margin-top: var(--spacing-xs);
  font-size: var(--font-title);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  line-height: 1.4;
}
.activity-time {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  flex-shrink: 0;
}
.activity-desc {
  font-size: var(--font-body);
  color: var(--text-secondary);
  line-height: 1.6;
}
.activity-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--spacing-xs);
}
.activity-link {
  font-size: var(--font-small);
  font-weight: var(--weight-semibold);
  color: var(--color-primary);
}
.activity-link--muted {
  color: var(--text-tertiary);
}
.loading-more {
  text-align: center;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  padding: var(--spacing-md);
}
</style>
