<template>
  <view class="page notify-page">
    <Header title="消息中心" showBack />

    <!-- 全部已读 -->
    <view class="action-row" v-if="notifications.length > 0">
      <text class="read-all" @tap="readAll">全部标为已读</text>
    </view>

    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh" @scrolltolower="onScrollToLower">
      <view v-if="loading && notifications.length === 0" class="skeleton-list">
        <view v-for="s in 3" :key="s" class="sk-card skeleton" />
      </view>

      <EmptyState
        v-else-if="notifications.length === 0"
        text="暂无消息"
        icon="broadcast"
        :retry="loadFailed"
        @retry="loadData(true)"
      />

      <view v-else class="notify-list">
        <view
          v-for="n in notifications"
          :key="n.id"
          class="notify-item"
          :class="{ unread: n.isRead === 0 }"
          @tap="onTap(n)"
        >
          <view class="notify-badge" :class="`type-${n.type}`">
            <IconSvg :name="typeIcon(n.type)" :size="36" class="notify-badge-icon" />
          </view>
          <view class="notify-body">
            <view class="notify-head-row">
              <text class="notify-title">{{ n.title }}</text>
              <view v-if="n.isRead === 0" class="unread-dot" />
            </view>
            <text class="notify-content">{{ n.content }}</text>
            <text class="notify-time">{{ relativeTime(n.createdAt) }}</text>
          </view>
        </view>

        <view v-if="loadingMore" class="list-footer loading">
          <view class="footer-spinner" />
          <text class="footer-text">加载中…</text>
        </view>
        <view v-else-if="finished" class="list-footer finished">
          <text class="footer-text">— 已经到底啦 —</text>
        </view>
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Header from '@/components/header.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconSvg from '@/components/IconSvg.vue'
import { relativeTime } from '@/utils/time'
import { useUserStore } from '@/stores/user'
import { useNotifyStore } from '@/stores/notify'
import * as notifyApi from '@/api/notify'
import type { Notification, NotificationType } from '@/api/notify'

const userStore = useUserStore()
const notifyStore = useNotifyStore()
const notifications = ref<Notification[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)
const loadFailed = ref(false)
const refresherTriggered = ref(false)

let page = 1
const pageSize = 20

function typeIcon(type: NotificationType): string {
  switch (type) {
    case 'moment_audit': return 'comment'
    case 'dish_audit': return 'dish'
    case 'comment': return 'comment'
    case 'useful': return 'thumb'
    default: return 'broadcast'
  }
}

async function loadData(reset = true) {
  if (!userStore.requireAuth()) return
  if (reset) {
    page = 1
    finished.value = false
    notifications.value = []
  }
  loading.value = true
  loadFailed.value = false
  try {
    const res = await notifyApi.getNotifications({ page, pageSize })
    notifications.value = page === 1 ? res.list : [...notifications.value, ...res.list]
    if (notifications.value.length >= res.total) finished.value = true
    page += 1
  } catch (e: any) {
    loadFailed.value = true
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function onScrollToLower() {
  if (loading.value || loadingMore.value || finished.value) return
  loadingMore.value = true
  try {
    await loadData(false)
  } finally {
    loadingMore.value = false
  }
}

async function onTap(n: Notification) {
  // 标记已读
  if (n.isRead === 0) {
    n.isRead = 1
    try {
      await notifyApi.readNotification(n.id)
      notifyStore.fetchUnread()
    } catch { /* 忽略，UI 已更新 */ }
  }
  // 按 type 跳详情
  if (n.relatedId) {
    if (n.type === 'moment_audit' || n.type === 'comment' || n.type === 'useful') {
      uni.navigateTo({ url: `/pages/pages-detail/moment?id=${n.relatedId}` })
      return
    }
    if (n.type === 'dish_audit') {
      uni.navigateTo({ url: `/pages/pages-detail/dish?id=${n.relatedId}` })
      return
    }
  }
  uni.showToast({ title: '暂无关联详情', icon: 'none' })
}

async function readAll() {
  try {
    await notifyApi.readAllNotifications()
    notifications.value.forEach(n => (n.isRead = 1))
    notifyStore.fetchUnread()
    uni.showToast({ title: '已全部标为已读', icon: 'none' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '操作失败', icon: 'none' })
  }
}

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData(true).finally(() => { refresherTriggered.value = false })
}

onMounted(() => { loadData(true) })
</script>

<style scoped>
.notify-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.action-row { display: flex; justify-content: flex-end; padding: var(--spacing-sm) var(--spacing-lg); background: var(--bg-card); border-bottom: 2rpx solid var(--border-color); }
.read-all { font-size: var(--font-aux); color: var(--color-primary); font-weight: 600; }
.notify-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-sm); }
.notify-item { display: flex; gap: var(--spacing-sm); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); transition: transform 0.12s ease; -webkit-tap-highlight-color: transparent; }
.notify-item.unread { background: var(--color-primary-soft2); }
.notify-item:active { transform: scale(0.97); }
.notify-badge { width: 72rpx; height: 72rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.notify-badge-icon { font-size: 36rpx; line-height: 1; }
.notify-badge.type-moment_audit,
.notify-badge.type-dish_audit { background: var(--color-warning-soft); }
.notify-badge.type-comment { background: var(--color-primary-soft); }
.notify-badge.type-useful { background: var(--color-like-soft); }
.notify-body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.notify-head-row { display: flex; align-items: center; justify-content: space-between; }
.notify-title { font-size: var(--font-body); font-weight: 700; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.unread-dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: var(--color-error); flex-shrink: 0; }
.notify-content { font-size: var(--font-aux); color: var(--text-secondary); line-height: 1.5; margin-top: 4rpx; }
.notify-time { font-size: var(--font-aux); color: var(--text-tertiary); margin-top: 4rpx; }
.skeleton-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-sm); }
.sk-card { width: 100%; height: 140rpx; }
.list-footer { display: flex; align-items: center; justify-content: center; padding: var(--spacing-md) 0; gap: var(--spacing-xs); }
.footer-spinner { width: 28rpx; height: 28rpx; border: 4rpx solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
.footer-text { font-size: var(--font-aux); color: var(--text-tertiary); }
@keyframes spin { to { transform: rotate(360deg); } }

@media (prefers-reduced-motion: reduce) {
  .footer-spinner { animation-duration: 1.4s; }
}
</style>
