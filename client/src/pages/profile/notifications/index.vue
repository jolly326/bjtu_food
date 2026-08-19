<template>
  <view class="page notifications-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="系统通知" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh" @scrolltolower="loadMore">
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
      <EmptyState v-else text="暂无通知" />
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
const refresherTriggered = ref(false)
const pressedId = ref(0)
// 分页与防重复加载：#4 触底加载下一页；#8 onShow 与 watch 双触发去重（认证瞬间不重复请求）
let page = 1
const pageSize = 20
const finished = ref(false)
let hasLoaded = false

const TYPE_LABEL: Record<NotificationType, string> = {
  moment_audit: '动态审核',
  dish_audit: '菜品审核',
  comment: '评论',
  useful: '有用',
}

function typeLabel(t: NotificationType) {
  return TYPE_LABEL[t] || '系统'
}

function formatTime(iso?: string) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (x: number) => String(x).padStart(2, '0')
  return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  if (!userStore.isVerified()) return
  loading.value = true
  loadFailed.value = false
  try {
    const res = await getNotifications({ page: 1, pageSize })
    list.value = res.list
    page = 1
    // 本页不足 pageSize 即到底
    finished.value = res.list.length < pageSize
    // 刷新后重拉未读数，保持红点同步
    notifyStore.fetchUnread()
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

/** #4 修复：触底加载下一页 */
async function loadMore() {
  if (finished.value || loading.value || !userStore.isVerified()) return
  loading.value = true
  try {
    page += 1
    const res = await getNotifications({ page, pageSize })
    // 去重（极端情况下分页跳号），避免重复
    const existIds = new Set(list.value.map(n => n.id))
    list.value = list.value.concat(res.list.filter(n => !existIds.has(n.id)))
    if (res.list.length < pageSize) finished.value = true
  } catch {
    page -= 1 // 失败回退页码
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  refresherTriggered.value = true
  await load()
  refresherTriggered.value = false
}

/** 点击通知：标记已读；审核类跳对应详情页（type 编码目标类型，relatedId 为目标对象 ID） */
async function onTap(n: Notification) {
  if (n.isRead === 0) {
    // 乐观更新已读态
    n.isRead = 1
    notifyStore.fetchUnread()
    try {
      await readNotification(n.id)
    } catch { /* 失败静默，下轮刷新对齐 */ }
  }
  if (n.type === 'moment_audit' && n.relatedId) {
    uni.navigateTo({ url: `/pages/pages-detail/moment?id=${n.relatedId}` })
  } else if (n.type === 'dish_audit' && n.relatedId) {
    uni.navigateTo({ url: `/pages/pages-detail/dish?id=${n.relatedId}` })
  }
  // comment / useful 无独立目标页，仅标已读
}

// 通知属认证专属：游客访问由 profile 入口 requireAuth 弹认证；认证成功后（isVerified 由 false→true）
// 必须显式触发 load，否则游客态 userInfo 引用不变、浅比较 watch 不会触发，导致列表永久空白。
watch(
  () => userStore.isVerified(),
  (ok) => { if (ok) load() },
)
onShow(() => {
  // 返回本页时刷新（从详情返回/首次进入）。认证成功瞬间与 watch 可能重叠触发一次，
  // 属低危轻微重复请求，可接受；hasLoaded 标志防止「首次进入 + watch 认证」双触发重复首载。
  if (!userStore.isVerified()) return
  if (!hasLoaded) {
    hasLoaded = true
  }
  load()
})
</script>

<style scoped>
.notifications-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; background: var(--bg-page); }
.scroll-wrap { flex: 1; min-height: 0; overflow-y: auto; padding: var(--spacing-md); box-sizing: border-box; }

.skeleton-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.sk-item { height: 160rpx; border-radius: var(--radius-card); }
.skeleton { background: var(--bg-card); }

.list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.msg-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  transition: background-color var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.msg-item.pressed { background-color: var(--bg-soft); }
.msg-item.pressed:active { transform: scale(var(--press-scale)); }
.msg-item.unread { background: var(--color-primary-soft); }

.msg-dot { flex-shrink: 0; width: 16rpx; height: 16rpx; border-radius: 50%; background: var(--color-error); margin-top: 12rpx; }
.msg-dot.read { background: transparent; }

.msg-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.msg-title-row { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); }
.msg-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.msg-type { flex-shrink: 0; font-size: var(--font-tiny); color: var(--color-primary); }
.msg-content { font-size: var(--font-small); color: var(--text-secondary); line-height: 1.5; }
.msg-time { font-size: var(--font-tiny); color: var(--text-tertiary); }

@media (prefers-reduced-motion: reduce) {
  .msg-item { transition: none; }
  .msg-item.pressed:active { transform: none; }
}
</style>
