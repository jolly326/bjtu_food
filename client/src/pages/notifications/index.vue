<template>
  <view class="page notifications-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下） -->
    <PageWallpaper fixed />
    <Header title="系统通知" @back="backToHome">
      <!-- 全部已读：页面头部操作区，胶囊按钮与下方通知卡同一表面语言。
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
          <AppIcon name="check" :size="28" :color="(hasUnread && !readAllBusy) ? COLOR_MAP['primary'] : COLOR_MAP['text-tertiary']" />
          <text class="read-all-text">全部已读</text>
        </view>
      </template>
    </Header>

    <!-- 滚动容器：数据更新 / 恢复走「首屏 load + onShow 重拉闸门 + 失败重试块 @tap」，容器为普通滚动容器。 -->
    <scroll-view class="scroll-wrap v-scroll" scroll-y @scroll="onScroll" @scrolltolower="loadMore">
      <view class="list">
        <!-- 单张列表卡：全部通知行收纳在同一块壳内，行间 1rpx 浅分隔线。
             壳走公共 `CardSection`（bare+flush：行自带内距、外距由滚动区内距承担），
             页面不再手写「底色 + 圆角 + 阴影 + 裁切」四件套。 -->
        <CardSection v-if="list.length" bare flush>
          <view :style="{ height: topPad + 'px' }" />
          <view
            v-for="n in visible"
            :key="n.id"
            class="msg-item v-item"
            :class="{ unread: !n.isRead }"
            role="button"
            :aria-label="msgAriaLabel(n)"
            hover-class="pressed"
            @tap="onTap(n)"
          >
            <!-- 未读左侧暖橙细竖条 + 红点：纯装饰，读屏信息已由行 aria-label 承担 -->
            <view v-if="!n.isRead" class="msg-unread-bar" aria-hidden="true" />
            <view class="msg-dot" :class="{ read: n.isRead }" aria-hidden="true" />
            <view class="msg-body">
              <view class="msg-title-row">
                <text class="msg-title">{{ n.title }}</text>
                <text class="msg-time">{{ formatDateTime(n.createdAt) }}</text>
              </view>
              <text class="msg-content">{{ n.content }}</text>
            </view>
          </view>
          <view :style="{ height: bottomPad + 'px' }" />
        </CardSection>
      </view>

      <!-- 三态判断序固定 **失败 > 在途 > 空**（有错不显示空）：
           ① 失败（P3-03 公共组件）—— 首屏请求失败 ≠ 无通知，先于在途与空态渲染；
           ② 在途 —— 只给文字行（全局 `.list-foot`），不给骨架屏（禁的是伪内容与抖动，不是文字）；
           ③ 空 —— 零通知显式呈现（铃铛图标 + 标题 + 说明），避免整页空白被判读为「页面坏了」。 -->
      <RetryBlock v-if="loadFailed && !loading" @retry="onRetryLoad" />
      <view v-else-if="loading && !list.length" class="list-foot">
        <text class="list-foot-text">加载中…</text>
      </view>
      <EmptyState
        v-else-if="loaded && !list.length"
        icon="bell"
        :icon-size="48"
        title="暂无通知"
        :desc="emptyDesc"
      />

      <!-- 触底反馈（基线 §1.17「列表底部反馈」）：列表有数据时在列表末尾给出
           「加载更多在途」/「到底」两态 —— 触底加载与封口不再静默无信号。 -->
      <view v-if="loading && list.length" class="list-foot">
        <text class="list-foot-text">正在加载更多…</text>
      </view>
      <view v-else-if="finished && list.length" class="list-foot">
        <text class="list-foot-text">没有更多了</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import AppIcon from '@/components/AppIcon.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import CardSection from '@/components/CardSection.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useNotifyStore } from '@/stores/notify'
import { useOnShowRefresh } from '@/composables/useOnShowRefresh'
import { toastError, toastInfo, toastSuccess } from '@/utils/error'
import { listNotifications, readNotification, readAllNotifications, type Notification } from '@/api/notify'
import { formatDateTime } from '@/utils/time'
import { backToHome } from '@/utils/back'
import { COLOR_MAP } from '@/theme/tokens'
import { usePagedList, useVirtualList } from '@/composables/usePagedList'
import { MAX_LIST_PAGES } from '@/constants/paging'

const notifyStore = useNotifyStore()

/** 全部已读进行中（并发守卫 + 行内禁用态） */
const readAllBusy = ref(false)
/** 首屏是否已加载完成（用于空态判断，避免加载前闪现空态） */
const loaded = ref(false)

/**
 * 分页列表（公共 composable）。
 *
 * 首屏失败语义（MP-012）：失败置 `loadFailed` 渲染「加载失败 · 点击重试」块，与「暂无通知」区分
 * （`GET /my/notifications` 为登录级端点，无认证级 4031 分流；零通知时由空态承载）。
 *
 * 本页差异经选项注入：成功后刷新未读数（保持红点同步）、首屏结束置 `loaded`。
 */
const { list, loading, loadFailed, finished, load, loadMore } = usePagedList<Notification>({
  fetchPage: async (page, pageSize) => (await listNotifications({ page, pageSize })).list,
  maxPages: MAX_LIST_PAGES,
  onLoadSuccess: () => { notifyStore.fetchUnread() },
  onLoadSettled: () => { loaded.value = true },
})

/** 虚拟列表（列表为滚动内容首块 ⇒ offset 0）：仅渲染可视窗口，节点数 O(窗口)；≤ 阈值时全渲染、行为不变 */
const { onScroll, visible, topPad, bottomPad } = useVirtualList<Notification>({
  items: list,
  estimateHeight: 96,
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
 * 全部已读：PUT /my/notifications/read-all（需登录、幂等）。
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
 * 通知行的读屏标签：把「未读」这一**纯视觉**状态转成可播报文本。
 *
 * 未读由左侧竖条 + 红点表达（`aria-hidden`），读屏信息由本标签承担；
 * 行本身可点（跳详情），故标签需同时说明「未读」与「可点」。
 */
function msgAriaLabel(n: Notification): string {
  return `${n.isRead ? '' : '未读，'}${n.title}，${formatDateTime(n.createdAt)}`
}

/**
 * onShow 重拉闸门：首次进入必拉；之后从二级页（如菜品详情）返回时，
 * 30s 内且本页无「写失败遗留」则跳过重拉，避免列表被无谓重置、浏览位置丢失。
 */
const { markDirty } = useOnShowRefresh(load)

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
      // 失败必须可见（乐观更新策略不动）：① 回滚本地已读态（消除与服务端不一致的假状态）
      n.isRead = false
      // ② 提示失败；未读数随回滚重拉对齐红点
      toastInfo('标记已读失败，请稍后重试')
      notifyStore.fetchUnread({ force: true })
      // ③ 置脏，下次进入本页必然重拉对齐（MP-07）
      markDirty()
    }
  }
}
</script>

<style scoped>
/* 页面根不带底色：底色下沉到全局 `page{}`，否则会盖住负层级壁纸层 */
.notifications-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; }
.notifications-page { min-height: 0; }
/* `min-height: 0` 必需：全局 `.page` 兜底写了 `min-height: 100vh / 100dvh`，而移动端
   `100vh`（最大视口）通常 **大于** `100dvh`（当前视口）；二者同时存在时 min 胜出
   ⇒ 页根比可视区高出一截 ⇒ **页面本身**多出一段可滚区（内容并未超屏也会滚）。
   自带滚动容器的页根必须把 min-height 归零，把高度交给 `height: 100dvh` + 内部 scroll-view。 */
/* 底部 = 呼吸位 + `env(safe-area-inset-bottom)`：通知卡 / 空态 / 失败块都是滚动区末块，
   无安全区时会被 Home Indicator 压住（同 `my-reviews` 的 `.scroll-wrap` 写法） */
.scroll-wrap { flex: 1; min-height: 0; padding: var(--spacing-md) var(--page-gutter) calc(var(--spacing-md) + var(--spacing-lg) + env(safe-area-inset-bottom)); box-sizing: border-box; }

/* 列表容器：单张模块装全部行；`.list` 仅作占位 wrapper（行少时不渲染空块） */
.list { display: block; }
.msg-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  min-height: var(--tap-target-size);
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
  /* 超长无空格串（回执里的 URL / 长英文）在 2 行折叠内换行，不被硬裁 */
  overflow-wrap: anywhere;
}

/* 空态 / 失败态样式由公共组件 EmptyState / RetryBlock 承担 */

/* 「全部已读」胶囊：默认中性白底灰描边；有未读时（is-active）整体转暖橙描边 + 暖橙文字；
   禁用态（is-disabled）常驻中性描边 + 文字三阶末档（描边 / 文字类禁用档，**不降透明**）、不可点；
   按压走全局 .pressed 兜底 */
.read-all {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
  /* 触达：min-height 兜到全站基线 88rpx（44pt），原先 padding 撑出的约 42rpx 不足触达下限 */
  min-height: var(--tap-target-size);
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
/* 禁用档（描边 / 文字类）：回落中性描边 + 文字三阶末档，**不再降透明**（opacity 只表在途 busy） */
.read-all.is-disabled { border-color: var(--border-color); }
.read-all.is-disabled .read-all-text { color: var(--text-tertiary); }
.read-all.pressed { background: var(--bg-soft); opacity: 1; }

@media (prefers-reduced-motion: reduce) {
  .msg-item { transition: none; }
}
</style>