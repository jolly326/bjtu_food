<template>
  <view class="page messages-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="消息中心" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <view v-if="loading && list.length === 0" class="skeleton-list">
        <view v-for="s in 4" :key="s" class="sk-item skeleton" />
      </view>

      <view v-else-if="list.length > 0" class="list">
        <view
          v-for="n in list"
          :key="n.id"
          class="msg-item"
          :class="{ unread: n.isRead === 0, pressed: pressedId === n.id }"
          @touchstart="pressedId = n.id"
          @touchend="pressedId = 0"
          @touchcancel="pressedId = 0"
          @mousedown="pressedId = n.id"
          @mouseup="pressedId = 0"
          @mouseleave="pressedId = 0"
          @tap="onTap(n)"
        >
          <view class="msg-dot" :class="{ read: n.isRead === 1 }" />
          <view class="msg-body">
            <view class="msg-title-row">
              <text class="msg-title">{{ n.title }}</text>
              <text class="msg-type">{{ typeLabel(n.type) }}</text>
            </view>
            <text class="msg-content">{{ n.content }}</text>
            <text class="msg-time">{{ formatTime(n.createdAt) }}</text>
          </view>
        </view>
      </view>

      <!-- 加载失败：与空数据语义区分，提供重试 -->
      <EmptyState v-else-if="loadFailed" text="加载失败，请重试" icon="report" :retry="true" @retry="load" />
      <EmptyState v-else text="暂无消息" />
      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 认证弹层：游客直访时引导登录，认证成功后自动加载 -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import EmptyState from '@/components/EmptyState.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import { useUserStore } from '@/stores/user'
import { useNotifyStore } from '@/stores/notify'
import { useThemeStore } from '@/stores/theme'
import { getNotifications, readNotification, type Notification, type NotificationType } from '@/api/notify'
import { backToHome } from '@/utils/nav'

const userStore = useUserStore()
const notifyStore = useNotifyStore()
const theme = useThemeStore()
const list = ref<Notification[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const pressedId = ref(0)
const refresherTriggered = ref(false)

async function load(silent = false) {
  if (!userStore.requireAuth()) return
  if (!silent) loading.value = true
  loadFailed.value = false
  try {
    const { list: rows } = await getNotifications({ page: 1, pageSize: 50 })
    list.value = rows
  } catch {
    loadFailed.value = true
    /* toast 由 http 层统一处理 */
  } finally {
    loading.value = false
  }
}

function typeLabel(type: NotificationType): string {
  const map: Record<NotificationType, string> = {
    moment_audit: '动态审核',
    dish_audit: '菜品审核',
    comment: '评论',
    useful: '点赞',
  }
  return map[type] ?? '通知'
}

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  return sameDay ? `${pad(d.getHours())}:${pad(d.getMinutes())}` : `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function onTap(n: Notification) {
  if (n.isRead === 0) {
    try {
      await readNotification(n.id)
      n.isRead = 1
      notifyStore.fetchUnread()
    } catch {
      /* 已读失败不影响跳转 */
    }
  }
  // 菜品审核无独立详情页（菜品详情为弹层），标记已读后明确反馈，避免"点了没反应"
  if (n.type === 'dish_audit') {
    uni.showToast({ title: '菜品审核结果已更新', icon: 'none' })
    return
  }
  if (n.relatedId == null) return
  uni.navigateTo({ url: `/pages/pages-detail/moment?id=${n.relatedId}` })
}

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  load(true).finally(() => { refresherTriggered.value = false })
}

// 游客直访时弹认证；认证成功后自动加载
watch(
  () => userStore.isLoggedIn(),
  (v) => {
    if (v) {
      load()
      notifyStore.fetchUnread()
    }
  },
)
onShow(() => {
  if (!userStore.requireAuth()) return
  load()
  notifyStore.fetchUnread()
})
</script>

<style scoped>
.messages-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.list { display: flex; flex-direction: column; padding: var(--spacing-md); gap: var(--spacing-sm); }
.skeleton-list { display: flex; flex-direction: column; gap: var(--spacing-sm); padding: var(--spacing-md); }
.sk-item { height: 140rpx; border-radius: var(--radius-card); }

.msg-item {
  display: flex; align-items: flex-start; gap: var(--spacing-sm);
  background: var(--bg-card); border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: var(--spacing-md) var(--spacing-lg);
  transition: transform 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.msg-item.pressed { transform: scale(var(--press-scale)); }
.msg-dot {
  flex-shrink: 0; width: 16rpx; height: 16rpx; border-radius: 50%;
  background: var(--color-primary);
  margin-top: var(--spacing-sm);
}
.msg-dot.read { background: var(--border-color); }
.msg-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.msg-title-row { display: flex; align-items: center; gap: var(--spacing-xs); }
.msg-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); }
/* 未读强调（Apple Mail 式）：未读标题加粗，已读降级为常规 */
.msg-item.unread .msg-title { font-weight: var(--weight-heavy); }
.msg-item.unread .msg-content { color: var(--text-primary); }
.msg-type { font-size: var(--font-tiny); color: var(--text-tertiary); background: var(--bg-soft); border-radius: var(--radius-tag); padding: 0 10rpx; line-height: 32rpx; }
.msg-content { font-size: var(--font-aux); color: var(--text-secondary); line-height: 1.5; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
.msg-time { font-size: var(--font-tiny); color: var(--text-tertiary); }
</style>
