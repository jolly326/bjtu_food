<template>
  <view class="page notifications-page">
    <Header title="系统通知" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh" @scrolltolower="loadMore">
      <view class="list">
        <!-- 卡片式通知：仅标题 + 内容 + 时间；未读左侧红点 + 浅主色底 -->
        <view
          v-for="n in list"
          :key="n.id"
          class="msg-item"
          :class="{ unread: n.isRead === 0 }"
          @tap="onTap(n)"
        >
          <view class="msg-dot" :class="{ read: n.isRead === 1 }" />
          <view class="msg-body">
            <view class="msg-title-row">
              <text class="msg-title">{{ n.title }}</text>
              <text class="msg-time">{{ formatTime(n.createdAt) }}</text>
            </view>
            <text class="msg-content">{{ n.content }}</text>
          </view>
        </view>
      </view>

    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import Header from '@/components/AppHeader.vue'
import { useUserStore } from '@/stores/user'
import { useNotifyStore } from '@/stores/notify'
import { getNotifications, readNotification, type Notification } from '@/api/notify'
import { backToHome } from '@/utils/nav'
import { dishDetailUrl } from '@/utils/routes'

const userStore = useUserStore()
const notifyStore = useNotifyStore()

const list = ref<Notification[]>([])
const loading = ref(false)
const refresherTriggered = ref(false)
// 分页与防重复加载（onShow / 下拉刷新）
let page = 1
const pageSize = 20
const finished = ref(false)

function formatTime(iso?: string) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (x: number) => String(x).padStart(2, '0')
  return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  loading.value = true
  try {
    const res = await getNotifications({ page: 1, pageSize })
    list.value = res.list
    page = 1
    // 本页不足 pageSize 即到底
    finished.value = res.list.length < pageSize
    // 刷新后重拉未读数，保持红点同步
    notifyStore.fetchUnread()
  } catch (err) {
    // client-auth-boundary：查看系统通知免认证——游客无个人数据与请求失败均静默（无空态/错误态），异常仅记录，恢复靠下拉刷新
    console.error('[notifications] 加载通知失败', err)
  } finally {
    loading.value = false
  }
}

/** #4 触底加载下一页（游客无个人数据，列表为空时不会触发） */
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
  if (n.type === 'dish_audit' && n.relatedId) {
    uni.navigateTo({ url: dishDetailUrl(n.relatedId) })
  }
}

// 进入/返回本页即加载（游客亦可进入；无个人数据时展示空态）
onShow(() => {
  load()
})
</script>

<style scoped>
.notifications-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; background: var(--bg-page); }
.scroll-wrap { flex: 1; min-height: 0; overflow-y: auto; padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-md) + var(--spacing-lg)); box-sizing: border-box; }

.list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.msg-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
  box-sizing: border-box;
}
.msg-item.pressed { background-color: var(--bg-soft); }
/* 未读：白卡 + 左侧主色竖条 + 淡主色标题字（不再整卡铺色，卡片观感更清爽） */
.msg-item.unread {
  background: var(--bg-card);
  box-shadow: var(--shadow-warm);
}
.msg-item.unread::before {
  content: '';
  position: absolute;
  left: 0;
  top: var(--spacing-lg);
  bottom: var(--spacing-lg);
  width: 6rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
}

.msg-dot { flex-shrink: 0; width: 16rpx; height: 16rpx; border-radius: var(--radius-circle); background: var(--color-primary); margin-top: 10rpx; }
.msg-dot.read { background: transparent; }

.msg-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.msg-title-row { display: flex; align-items: baseline; justify-content: space-between; gap: var(--spacing-sm); }
.msg-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; min-width: 0; }
.msg-item.unread .msg-title { color: var(--color-primary); }
/* 时间收进标题行右侧（次级灰小字），通知只剩「标题 + 内容 + 时间」三要素 */
.msg-time { flex-shrink: 0; font-size: var(--font-tiny); color: var(--text-tertiary); }
.msg-content {
  font-size: var(--font-small);
  color: var(--text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

@media (prefers-reduced-motion: reduce) {
  .msg-item { transition: none; }
}
</style>
