<template>
  <view class="page notifications-page">
    <Header title="系统通知" @back="backToHome">
      <!-- 全部已读（§7.18）：页面头部操作区，胶囊按钮与下方通知卡同一表面语言。
           无未读时置灰不可点（常驻不隐藏）——位置稳定不跳动，用户随时能看到该动作存在。 -->
      <template v-if="userStore.isVerified()" #action>
        <view
          class="read-all"
          :class="{ 'is-disabled': !hasUnread || readAllBusy }"
          role="button"
          aria-label="全部已读"
          hover-class="read-all-pressed"
          @tap="onReadAll"
        >
          <IconSvg name="check" :size="26" :color="hasUnread ? 'var(--color-primary)' : 'var(--text-tertiary)'" />
          <text class="read-all-text">全部已读</text>
        </view>
      </template>
    </Header>

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
              <text class="msg-time">{{ formatDateTime(n.createdAt) }}</text>
            </view>
            <text class="msg-content">{{ n.content }}</text>
          </view>
        </view>
      </view>

      <!-- 加载失败重试块（MP-012 同族）：首屏请求失败 ≠ 无通知——极简「加载失败 · 点击重试」行内块，
           先于空态渲染，避免网络失败被误读为「暂无通知」；恢复走重试块 @tap 或下拉刷新。
           C1 修复：游客请求被拒（4031/403）SHALL 静默——未认证时不渲染失败态（client-auth-boundary）。 -->
      <view
        v-if="loadFailed && !loading && userStore.isVerified()"
        class="notify-retry"
        role="button"
        aria-label="加载失败，点击重试"
        hover-class="pressed"
        @tap="onRetryLoad"
      >
        <IconSvg name="report" :size="44" color="var(--text-tertiary)" />
        <text class="notify-retry-title">加载失败</text>
        <text class="notify-retry-hint">网络似乎不太顺畅 · 点击重试</text>
      </view>
      <!-- 空态：仅已认证用户展示轻提示；游客无个人通知一律静默（见 client-auth-boundary）。
           空态不含重试按钮、错误提示与认证引导。 -->
      <view v-else-if="loaded && !list.length && userStore.isVerified()" class="empty-tip">
        <text class="empty-title">暂无通知</text>
        <text class="empty-desc">菜品审核结果与反馈处理结果会在这里通知你</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import Header from '@/components/AppHeader.vue'
import IconSvg from '@/components/IconSvg.vue'
import { useUserStore } from '@/stores/user'
import { useNotifyStore } from '@/stores/notify'
import { getNotifications, readNotification, readAllNotifications, type Notification } from '@/api/notify'
import { formatDateTime } from '@/utils/time'
import { backToHome } from '@/utils/nav'
import { dishDetailUrl } from '@/utils/routes'

const userStore = useUserStore()
const notifyStore = useNotifyStore()

const list = ref<Notification[]>([])
/** 全部已读进行中（并发守卫 + 行内禁用态） */
const readAllBusy = ref(false)
const loading = ref(false)
const refresherTriggered = ref(false)
/** 首屏是否已加载完成（用于空态判断，避免加载前闪现空态） */
const loaded = ref(false)
/** 首屏/下拉刷新是否失败（MP-012）：失败 ≠ 无通知，失败渲染重试块而非空态；分页失败保持静默可再触底 */
const loadFailed = ref(false)
// 分页与防重复加载（onShow / 下拉刷新）
let page = 1
const pageSize = 20
const finished = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await getNotifications({ page: 1, pageSize })
    // 成功即清失败态（重试成功后错误块消失）
    loadFailed.value = false
    list.value = res.list
    page = 1
    // 本页不足 pageSize 即到底
    finished.value = res.list.length < pageSize
    // 刷新后重拉未读数，保持红点同步
    notifyStore.fetchUnread()
  } catch (err) {
    // MP-012：首屏失败不再静默吞成空态——置 loadFailed 渲染「加载失败 · 点击重试」块，
    // 与「暂无通知」区分；游客免认证口径不变（请求成功时游客照常得到空列表走空态）。
    // C1：未认证（游客）请求被拒（4031/403）属正常业务边界，SHALL 静默——
    // 置位后由模板 isVerified() 门控，游客不渲染失败块也不弹认证引导（client-auth-boundary）。
    console.error('[notifications] 加载通知失败', err)
    loadFailed.value = true
  } finally {
    loading.value = false
    loaded.value = true
  }
}

/** 重试块 @tap：从第 1 页重拉（与下拉刷新同路径，仅无下拉动画）（MP-012） */
function onRetryLoad() {
  load()
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

/** 是否存在未读：驱动「全部已读」入口的禁用态（无未读时置灰不可点，入口常驻不隐藏） */
const hasUnread = computed(() => list.value.some(n => n.isRead === 0))

/**
 * 全部已读（§7.18）：PUT /my/notifications/read-all（需登录、幂等）。
 * 成功后重拉列表 + 未读数（不本地乐观改 list，避免与服务端真实态偏差）；
 * 失败只提示、不改变任何本地状态；请求中 readAllBusy 守卫防重复点击。
 */
async function onReadAll() {
  if (readAllBusy.value || !hasUnread.value) return
  readAllBusy.value = true
  try {
    await readAllNotifications()
    await load()
    uni.showToast({ title: '已全部标为已读', icon: 'none' })
  } catch (err) {
    console.error('[notifications] 全部已读失败', err)
    uni.showToast({ title: '操作失败，请稍后重试', icon: 'none' })
  } finally {
    readAllBusy.value = false
  }
}

/** 点击通知：标记已读；dish_audit 跳菜品详情；feedback_handle 停留本页（回执正文已在内容区展示，不做跳转） */
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

/* 空态（仅已认证用户）：轻提示，无重试按钮 / 错误提示 / 认证引导 */
.empty-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-2xl) var(--spacing-lg);
}
.empty-title { font-size: var(--font-body); color: var(--text-secondary); font-weight: var(--weight-medium); }
.empty-desc { font-size: var(--font-aux); color: var(--text-tertiary); text-align: center; }

/* 加载失败重试块（MP-012）：与 find/feed 重试块同族视觉
   （居中、凹陷面 bg-soft、次级文字色），整块 @tap 触发重拉，无独立按钮 */
.notify-retry {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  margin-top: var(--spacing-lg);
  padding: var(--spacing-xl) var(--spacing-lg);
  background: var(--bg-soft);
  border-radius: var(--radius-card);
  -webkit-tap-highlight-color: transparent;
}
.notify-retry.pressed { opacity: 0.7; }
.notify-retry-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-secondary); text-align: center; }
.notify-retry-hint { font-size: var(--font-aux); color: var(--text-tertiary); text-align: center; }

@media (prefers-reduced-motion: reduce) {
  .msg-item { transition: none; }
}
</style>
