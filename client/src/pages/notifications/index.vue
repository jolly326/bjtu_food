<template>
  <view class="page notifications-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下） -->
    <PageWallpaper fixed />
    <Header title="系统通知" @back="backToHome">
      <!-- 全部已读（§7.18）：页面头部操作区，胶囊按钮与下方通知卡同一表面语言。
           无未读时置灰不可点（常驻不隐藏）——位置稳定不跳动，用户随时能看到该动作存在。 -->
      <template v-if="userStore.isVerified()" #action>
        <view
          class="read-all"
          :class="{ 'is-disabled': !hasUnread || readAllBusy }"
          role="button"
          aria-label="全部已读"
          hover-class="pressed"
          @tap="onReadAll"
        >
          <IconSvg name="check" :size="26" :color="hasUnread ? COLOR_MAP['primary'] : COLOR_MAP['text-tertiary']" />
          <text class="read-all-text">全部已读</text>
        </view>
      </template>
    </Header>

    <!-- 滚动容器：数据更新 / 恢复走「首屏 load + onShow 重拉闸门（MP-07）+ 失败重试块 @tap」，容器为普通滚动容器。 -->
    <scroll-view class="scroll-wrap" scroll-y @scrolltolower="loadMore">
      <view class="list">
        <!-- 卡片式通知：仅标题 + 内容 + 时间；未读左侧红点 + 浅主色底 -->
        <CardSection
          v-for="n in list"
          :key="n.id"
          class="msg-item"
          :class="{ unread: !n.isRead }"
          flush
          @tap="onTap(n)"
        >
          <view class="msg-dot" :class="{ read: n.isRead }" />
          <view class="msg-body">
            <view class="msg-title-row">
              <text class="msg-title">{{ n.title }}</text>
              <text class="msg-time">{{ formatDateTime(n.createdAt) }}</text>
            </view>
            <text class="msg-content">{{ n.content }}</text>
          </view>
        </CardSection>
      </view>

      <!-- 加载失败重试块（MP-012 同族，P3-03 上提为公共组件）：首屏请求失败 ≠ 无通知——
           先于空态渲染，避免网络失败被误读为「暂无通知」；恢复走重试块 @tap。
           C1 修复：游客请求被拒（4031/403）SHALL 静默——未认证时不渲染失败态（client-auth-boundary）。 -->
      <RetryBlock v-if="loadFailed && !loading && userStore.isVerified()" @retry="onRetryLoad" />
      <!-- 空态：仅已认证用户展示轻提示；游客无个人通知一律静默（见 client-auth-boundary）。
           空态不含重试按钮、错误提示与认证引导。 -->
      <!-- 统一空态组件：不再本页手写 `.empty-tip` -->
      <EmptyState
        v-else-if="loaded && !list.length && userStore.isVerified()"
        title="暂无通知"
        desc="反馈处理结果会在这里通知你"
      />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import IconSvg from '@/components/IconSvg.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useUserStore } from '@/stores/user'
import { useNotifyStore } from '@/stores/notify'
import { useOnShowRefresh } from '@/composables/useOnShowRefresh'
import { toastError, toastInfo, toastSuccess } from '@/utils/error'
import { getNotifications, readNotification, readAllNotifications, type Notification } from '@/api/notify'
import { formatDateTime } from '@/utils/time'
import { backToHome } from '@/utils/back'
import { COLOR_MAP } from '@/theme/tokens'
import { usePagedList } from '@/composables/usePagedList'

const userStore = useUserStore()
const notifyStore = useNotifyStore()

/** 全部已读进行中（并发守卫 + 行内禁用态） */
const readAllBusy = ref(false)
/** 首屏是否已加载完成（用于空态判断，避免加载前闪现空态） */
const loaded = ref(false)

/**
 * 分页列表（公共 composable，UI 统一 Loop Round 17 抽取）。
 *
 * 首屏失败语义（MP-012）与认证边界（C1）保持不变：失败置 `loadFailed` 渲染「加载失败 · 点击重试」块，
 * 与「暂无通知」区分；游客请求成功时照常得到空列表走空态，未认证被拒（4031/403）由模板 `isVerified()` 门控，
 * 不渲染失败块也不弹认证引导（client-auth-boundary）。
 *
 * 本页差异经选项注入：成功后刷新未读数（保持红点同步）、首屏结束置 `loaded`、游客不触发触底加载。
 */
const { list, loading, loadFailed, finished, load, loadMore } = usePagedList<Notification>({
  fetchPage: async (page, pageSize) => (await getNotifications({ page, pageSize })).list,
  canLoadMore: () => userStore.isVerified(),
  onLoadSuccess: () => { notifyStore.fetchUnread() },
  onLoadSettled: () => { loaded.value = true },
})

/** 重试块 @tap：从第 1 页重拉（与首屏同一条重拉路径）（MP-012） */
function onRetryLoad() {
  load()
}

/** 是否存在未读：驱动「全部已读」入口的禁用态（无未读时置灰不可点，入口常驻不隐藏） */
const hasUnread = computed(() => list.value.some(n => !n.isRead))

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
    toastSuccess('已全部标为已读')
  } catch (err) {
    console.error('[notifications] 全部已读失败', err)
    // 失败文案走统一出口 utils/error（默认兜底即「操作失败，请稍后重试」）
    toastError(err)
  } finally {
    readAllBusy.value = false
  }
}

/**
 * onShow 重拉闸门（MP-07）：首次进入必拉；之后从二级页（如菜品详情）返回时，
 * 30s 内且本页无「写失败遗留」则跳过重拉，避免列表被无谓重置、浏览位置丢失。
 * 失败重试块不经过闸门（用户显式意图 → 直接 load）。
 */
const { markDirty, refreshOnShow } = useOnShowRefresh(load)

/**
 * 点击通知：仅标记已读。
 * 跳转口径：**一律停留本页**（回执正文已在内容区展示，无落地页）——
 * 端上不读取通知类型、不臆测目标页（`type` 已按「零消费即删」不出参）。
 */
async function onTap(n: Notification) {
  if (!n.isRead) {
    // 乐观更新已读态
    n.isRead = true
    notifyStore.fetchUnread()
    try {
      await readNotification(n.id)
    } catch {
      // 失败静默；但本地已乐观置位、与服务端不一致 → 置脏，下次进入本页必然重拉对齐（MP-07）
      markDirty()
    }
  }
  // 无跳转分支：一律停留本页（端上不读取通知类型）
}

onShow(() => {
  refreshOnShow()
})
</script>

<style scoped>
/* 页面根不带底色：底色下沉到全局 `page{}`，否则会盖住负层级壁纸层 */
.notifications-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; }
.scroll-wrap { flex: 1; min-height: 0; padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-md) + var(--spacing-lg)); box-sizing: border-box; }

.list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
/* 卡片壳走公共 `CardSection`：内距统一到 `--spacing-md`
   （原 `--spacing-lg`）；`flush` ⇒ 块间距由 `.list` 的 `gap` 统管；
   未读态由下方 `.msg-item.unread` 覆写（强调态用 `shadow-warm`）。 */
.msg-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
  box-sizing: border-box;
}
.msg-item.pressed { background-color: var(--bg-soft); }
/* 未读：白卡 + **红点** + 淡主色标题字（UI 统一 Loop Round 13 裁决 7A：左侧主色竖条已删，
   与右上红点语义重复；红点更轻、与「我的」页角标同语言。`shadow-warm` 保留 = 未读属「强调」态） */
.msg-item.unread {
  background: var(--bg-card);
  box-shadow: var(--shadow-warm);
}
/* （已删除原 `.msg-item.unread::before` 左侧竖条 —— 与红点语义重复） */

/* 未读红点：上偏置走 `--spacing-xs`（8rpx）—— 6A 归档：原裸 10rpx 不在 4pt 栅格 */
.msg-dot { flex-shrink: 0; width: 16rpx; height: 16rpx; border-radius: var(--radius-circle); background: var(--color-primary); margin-top: var(--spacing-xs); }
.msg-dot.read { background: transparent; }

.msg-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.msg-title-row { display: flex; align-items: baseline; justify-content: space-between; gap: var(--spacing-sm); }
.msg-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; min-width: 0; }
.msg-item.unread .msg-title { color: var(--color-primary-text); }
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

/* 空态已上提为公共组件 components/EmptyState.vue；
   失败态为 components/RetryBlock.vue（P3-03）—— 两者样式随之收敛，此处不再保留副本 */

/* 「全部已读」胶囊：按压反馈走全局 .pressed(opacity) 兜底，此处再局部覆盖为 bg-soft 底色语言
   （App.vue 全局注释明确允许页面 scoped 覆盖）；禁用态复用全局 .is-disabled */
.read-all {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
  padding: var(--spacing-2xs) var(--spacing-sm);
  background: var(--bg-card);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-pill);
  box-shadow: var(--shadow-card);
  -webkit-tap-highlight-color: transparent;
}
.read-all.pressed { background: var(--bg-soft); opacity: 1; }

@media (prefers-reduced-motion: reduce) {
  .msg-item { transition: none; }
}
</style>
