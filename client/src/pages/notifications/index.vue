<template>
  <view class="page notifications-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下） -->
    <PageWallpaper fixed />
    <Header title="系统通知" @back="backToHome">
      <!-- 全部已读（§7.18）：页面头部操作区，胶囊按钮与下方通知卡同一表面语言。
           无未读时置灰不可点（常驻不隐藏）——位置稳定不跳动，用户随时能看到该动作存在。 -->
      <template #action>
        <view
          class="read-all"
          :class="{ 'is-active': hasUnread && !readAllBusy, 'is-disabled': !hasUnread || readAllBusy }"
          role="button"
          aria-label="全部已读"
          hover-class="pressed"
          @tap="onReadAll"
        >
          <IconSvg name="check" :size="26" :color="(hasUnread && !readAllBusy) ? COLOR_MAP['primary'] : COLOR_MAP['text-tertiary']" />
          <text class="read-all-text">全部已读</text>
        </view>
      </template>
    </Header>

    <!-- 滚动容器：数据更新 / 恢复走「首屏 load + onShow 重拉闸门（MP-07）+ 失败重试块 @tap」，容器为普通滚动容器。 -->
    <scroll-view class="scroll-wrap" scroll-y @scrolltolower="loadMore">
      <view class="list">
        <!-- 单张白卡：全部通知行收纳在同一张卡内，行间 1rpx 浅分隔线 -->
        <view v-if="list.length" class="list-card">
          <view
            v-for="n in list"
            :key="n.id"
            class="msg-item"
            :class="{ unread: !n.isRead }"
            @tap="onTap(n)"
          >
            <!-- 未读左侧暖橙细竖条（与红点共同表达未读，强化层级） -->
            <view v-if="!n.isRead" class="msg-unread-bar" />
            <view class="msg-dot" :class="{ read: n.isRead }" />
            <view class="msg-body">
              <view class="msg-title-row">
                <text class="msg-title">{{ n.title }}</text>
                <text class="msg-time">{{ formatDateTime(n.createdAt) }}</text>
              </view>
              <text class="msg-content">{{ n.content }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 加载失败重试块（P3-03 公共组件）：首屏请求失败 ≠ 无通知——
           先于空态渲染，避免网络失败被误读为「暂无通知」；恢复走重试块 @tap。 -->
      <RetryBlock v-if="loadFailed && !loading" @retry="onRetryLoad" />
      <!-- 空态：零通知时展示（铃铛图标 + 标题 + 说明），避免整页空白被判读为「页面坏了」 -->
      <EmptyState
        v-else-if="loaded && !list.length"
        icon="bell"
        :icon-size="48"
        title="暂无通知"
        :desc="emptyDesc"
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
import { useNotifyStore } from '@/stores/notify'
import { useOnShowRefresh } from '@/composables/useOnShowRefresh'
import { toastError, toastSuccess } from '@/utils/error'
import { listNotifications, readNotification, readAllNotifications, type Notification } from '@/api/notify'
import { formatDateTime } from '@/utils/time'
import { backToHome } from '@/utils/back'
import { COLOR_MAP } from '@/theme/tokens'
import { usePagedList } from '@/composables/usePagedList'

const notifyStore = useNotifyStore()

/** 全部已读进行中（并发守卫 + 行内禁用态） */
const readAllBusy = ref(false)
/** 首屏是否已加载完成（用于空态判断，避免加载前闪现空态） */
const loaded = ref(false)

/**
 * 分页列表（公共 composable）。
 *
 * 首屏失败语义（MP-012）：失败置 `loadFailed` 渲染「加载失败 · 点击重试」块，与「暂无通知」区分；
 * 未认证被拒（4031/403）不渲染失败块（认证边界），零通知时由空态给出认证引导。
 *
 * 本页差异经选项注入：成功后刷新未读数（保持红点同步）、首屏结束置 `loaded`。
 */
const { list, loading, loadFailed, load, loadMore } = usePagedList<Notification>({
  fetchPage: async (page, pageSize) => (await listNotifications({ page, pageSize })).list,
  onLoadSuccess: () => { notifyStore.fetchUnread() },
  onLoadSettled: () => { loaded.value = true },
})

/** 重试块 @tap：从第 1 页重拉（与首屏同一条重拉路径）（MP-012） */
function onRetryLoad() {
  load()
}

/** 是否存在未读：驱动「全部已读」入口的禁用态（无未读时置灰不可点，入口常驻不隐藏） */
const hasUnread = computed(() => list.value.some(n => !n.isRead))

/** 空态说明文案：消息中心为登录级能力，两态同一句（游客提交的反馈 / 举报 / 纠错同样收到回执） */
const emptyDesc = '反馈处理结果会在这里通知你'

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
    // 「全部已读」属本地读操作：强制刷新未读数（绕过 TTL），确保红点即时归零
    await notifyStore.fetchUnread({ force: true })
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
    notifyStore.fetchUnread({ force: true })
    try {
      await readNotification(n.id)
    } catch {
      // 失败静默；但本地已乐观置位、与服务端不一致 → 置脏，下次进入本页必然重拉对齐（MP-07）
      markDirty()
    }
  }
}

onShow(() => {
  refreshOnShow()
})
</script>

<style scoped>
/* 页面根不带底色：底色下沉到全局 `page{}`，否则会盖住负层级壁纸层 */
.notifications-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; }
.scroll-wrap { flex: 1; min-height: 0; padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-md) + var(--spacing-lg)); box-sizing: border-box; }

/* 列表容器：单张白卡装全部行；`.list` 仅作占位 wrapper（行少时不渲染空卡） */
.list { display: block; }
.list-card {
  background: var(--bg-card);
  border-radius: var(--radius-btn);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}
.msg-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  min-height: 88rpx;
  padding: var(--spacing-md) var(--spacing-lg);
  box-sizing: border-box;
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
/* 行间细分隔线（最上 / 最下无线）；统一 --border-color 1rpx */
.msg-item + .msg-item { border-top: 1rpx solid var(--border-color); }
.msg-item.pressed { background-color: var(--bg-soft); }
/* 未读：暖橙细竖条 + 红点 + 淡主色标题字，共同表达未读层级 */
.msg-unread-bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6rpx;
  background: var(--color-primary);
}
/* 未读红点：上偏置走 `--spacing-xs`（8rpx） */
.msg-dot { flex-shrink: 0; width: 16rpx; height: 16rpx; border-radius: var(--radius-circle); background: var(--color-primary); margin-top: var(--spacing-xs); }
.msg-dot.read { background: transparent; }

.msg-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.msg-title-row { display: flex; align-items: baseline; justify-content: space-between; gap: var(--spacing-sm); }
.msg-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; min-width: 0; }
.msg-item.unread .msg-title { color: var(--color-primary-text); }
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

/* 空态 / 失败态样式由公共组件 EmptyState / RetryBlock 承担 */

/* 「全部已读」胶囊：默认中性白底灰描边；有未读时（is-active）整体转暖橙描边 + 暖橙文字；
   禁用态（is-disabled）常驻灰显、不可点；按压走全局 .pressed 兜底 */
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
.read-all.is-active { border-color: var(--color-primary); }
.read-all.is-active .read-all-text { color: var(--color-primary-text); }
.read-all-text {
  font-size: var(--font-small);
  font-weight: var(--weight-medium);
  color: var(--text-tertiary);
}
.read-all.pressed { background: var(--bg-soft); opacity: 1; }

@media (prefers-reduced-motion: reduce) {
  .msg-item { transition: none; }
}
</style>
