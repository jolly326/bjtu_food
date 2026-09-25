<template>
  <view class="page mine-page">
    <!-- 「我的」是 TabBar 主根页（TabBar 经 reLaunch 切换，无带参跳转），恒不需要返回箭头：
         showBack 显式传 false（AppHeader 默认值为 true，不能省略） -->
    <Header title="我的" :show-back="false" />

    <view class="mine-content">
      <!-- 用户卡：游客（未认证）显示「游客 + 食客短 ID」；已认证显示昵称 + 绑定邮箱。
           整卡点击进入「我的主页」（游客与认证态同达，无认证拦截）；
           认证动作的单一入口为宫格「身份认证」格，用户卡不放「去认证」按钮 -->
      <view
        class="user-card"
        :class="isVerified ? 'user-card--verified' : 'user-card--guest'"
        role="button"
        aria-label="查看我的主页"
        @tap="onUserCardTap"
      >
        <view class="user-card-head">
          <view class="avatar-wrap">
            <ImageFallback v-if="userInfo?.avatar" :src="userInfo.avatar" class="avatar" />
            <view v-else class="avatar avatar-empty">
              <IconSvg name="user" :size="60" :color="COLOR_MAP['text-tertiary']" />
            </view>
          </view>
          <view class="user-meta">
            <text class="nickname" :class="{ 'nickname--guest': !isVerified }">
              {{ isVerified ? (userInfo?.nickname || '食客') : (userInfo?.nickname || '游客') }}
            </text>
            <text v-if="isVerified && userInfo?.username" class="user-id">
              {{ userInfo.username }}
            </text>
            <text v-else-if="!isVerified" class="user-id">游客 {{ guestLabel }}</text>
          </view>
          <IconSvg name="arrow" :size="28" :color="COLOR_MAP['text-tertiary']" class="card-arrow" />
        </view>
      </view>

      <!-- 功能宫格：一行 3 格（意见反馈 / 系统通知 / 身份认证），每格整格热区；
           个人信息编辑已并入「我的主页」页（用户卡点击直接进入，无认证拦截） -->
      <view class="grid">
        <view
          v-for="cell in gridCells"
          :key="cell.key"
          class="grid-cell"
          role="button"
          :aria-label="cell.label"
          hover-class="pressed"
          @tap="cell.action"
        >
          <view class="grid-cell-icon">
            <IconSvg :name="cell.icon" :size="44" :color="COLOR_MAP['primary']" />
            <!-- 系统通知：存在未读时右上红点（无未读 / 未登录不显示） -->
            <view v-if="cell.key === 'notify' && notifyStore.unreadCount > 0" class="badge badge-dot" aria-hidden="true" />
            <!-- 身份认证：已认证时右上主色圆点（状态徽章） -->
            <view v-else-if="cell.key === 'cert' && isVerified" class="badge badge-dot badge-cert" aria-hidden="true" />
          </view>
          <text class="grid-cell-label">{{ cell.label }}</text>
        </view>
      </view>

      <!-- 「其他」分组列表：三行独立入口（用户协议 / 隐私政策 / 注销账号），行间细分隔线；
           每行整行热区（role="button"），注销行为危险弱化色 -->
      <view class="more-group">
        <view
          v-for="row in moreRows"
          :key="row.key"
          class="more-row"
          :class="{ 'more-row--danger': row.danger }"
          role="button"
          :aria-label="row.label"
          hover-class="pressed"
          @tap="row.action"
        >
          <text class="more-row-text">{{ row.label }}</text>
          <IconSvg name="arrow" :size="28" :color="COLOR_MAP['text-tertiary']" class="more-row-arrow" />
        </view>
      </view>

      <!-- 版本行：纯展示（aria-hidden），独立于列表之外居中 -->
      <view class="app-footer" aria-hidden="true">
        <text class="app-footer-line">知行食记 v{{ appVersion }}</text>
        <text class="app-footer-line">北京交通大学 · 校园美食分享圈</text>
      </view>
    </view>

    <!-- 底部常驻菜单栏：首页/我的 两主区切换（仅主根页显示） -->
    <TabBar />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { showTab } from '@/stores/route'
import Header from '@/components/AppHeader.vue'
import IconSvg from '@/components/IconSvg.vue'
import ImageFallback from '@/components/ImageFallback.vue'
import TabBar from '@/components/TabBar.vue'
import { useUserStore } from '@/stores/user'
import { useAuthStore } from '@/stores/auth'
import { useNotifyStore } from '@/stores/notify'
import { PATH } from '@/utils/routes'
import { getLocalGuestLabel } from '@/utils/guest'
import { deleteAccount } from '@/api/user'
import { COLOR_MAP, MODAL_CONFIRM_PRIMARY_COLOR } from '@/theme/tokens'

const userStore = useUserStore()
const authStore = useAuthStore()
const notifyStore = useNotifyStore()
const userInfo = computed(() => userStore.userInfo)
/** 已认证（bindEmail 非空）——微信静默登录后恒有登录态，游客 / 认证由 isVerified() 单点派生区分（§5.y） */
const isVerified = computed(() => userStore.isVerified())
/**
 * 游客展示短 ID：由账号 `id` 派生「食客 + ID 尾 4 位」（id 不足 4 位取全量）。
 * spec §7.32：短标识不再由接口出参（纯派生值），展示层现算；
 * `id` 不可得（静默登录未完成 / 失败）时回退本地游客 ID 兜底，保证不空白。
 */
const guestLabel = computed(() => {
  const id = userInfo.value?.id
  if (!id) return getLocalGuestLabel()
  const s = String(id)
  return `食客${s.length > 4 ? s.slice(-4) : s}`
})
/** 版本号：构建期由 vite.config.ts 从 manifest.json versionName 注入（小程序运行时读不到 manifest） */
const appVersion = __APP_VERSION__

onLoad(() => {
  // 进入「我的」确保静默登录已就绪（游客态才有认证前提）；
  // 全仓无任何带 ?from=home 跳转
  // 到本页的调用点（TabBar 经 reLaunch 切换、无参数），该状态恒为 false，属死状态。
  userStore.silentLogin()
})

// 每次进入「我的」刷新未读通知数（宫格红点角标；通知属认证专属，仅认证用户刷新未读数）
onShow(() => {
  // 锚定底部菜单栏：我的页始终显示并高亮
  showTab('profile')
  if (userStore.isVerified()) notifyStore.fetchUnread()
})

/** 用户卡点击：查看我的评价（游客直接进入——页内信息条与空态自洽，认证要求仅在评论操作时） */
function onUserCardTap() {
  uni.navigateTo({ url: PATH.myReviews })
}

/** 身份认证格（认证动作的**单一入口**）：未认证跳转独立认证页；已认证轻提示 */
function onCertTap() {
  if (!userStore.isVerified()) {
    authStore.requestAuth()
    return
  }
  uni.showToast({ title: '已完成身份认证', icon: 'none' })
}

/** 功能宫格数据（一行 3 格，顺序固定：意见反馈 / 系统通知 / 身份认证）；每格整格热区 */
interface GridCell {
  key: string
  icon: string
  label: string
  action: () => void
}

/** 合规入口：隐私政策（应用内页面，不依赖外部域名） */
function goPrivacy() {
  uni.navigateTo({ url: PATH.privacy })
}

/** 合规入口：用户协议（与隐私政策各自独立页面，入口分叉） */
function goAgreement() {
  uni.navigateTo({ url: PATH.agreement })
}

/** 注销账号（合规硬需求）：二次确认 → 后端匿名化 → 清本地态（旧 token 已被后端拉黑，静默登录建新游客号） */
function onAccountDelete() {
  uni.showModal({
    title: '注销账号',
    content: '注销后账号将匿名化且不可恢复：重新登录将创建全新账号，你的评价与反馈会保留但不再关联身份，也不会回到新账号。',
    confirmText: '确认注销',
    confirmColor: MODAL_CONFIRM_PRIMARY_COLOR,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteAccount()
        uni.showToast({ title: '账号已注销', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: e instanceof Error && e.message ? e.message : '注销失败，请稍后重试', icon: 'none' })
      } finally {
        userStore.forceLogout()
      }
    },
  })
}

const gridCells: GridCell[] = [
  { key: 'feedback', icon: 'lightbulb-fill', label: '意见反馈', action: () => uni.navigateTo({ url: PATH.feedback }) },
  { key: 'notify', icon: 'bell', label: '系统通知', action: () => uni.navigateTo({ url: PATH.notifications }) },
  { key: 'cert', icon: 'badge-check', label: '身份认证', action: onCertTap },
]

/** 「其他」分组列表（三行固定：合规两行 + 账号危险操作一行；注销为 danger 弱化） */
const moreRows = [
  { key: 'agreement', label: '用户协议', danger: false, action: goAgreement },
  { key: 'privacy', label: '隐私政策', danger: false, action: goPrivacy },
  { key: 'deleteAccount', label: '注销账号', danger: true, action: onAccountDelete },
]
</script>

<style scoped>
/* mine 属静态短内容页，内容可放下时不再设置常驻 scroll-view；
   页面以自然文档滚动承载超高内容（超大字体/小屏），并保留底部 TabBar 避让留白 */
.mine-page { display: flex; flex-direction: column; min-height: 100vh; background: var(--bg-page); }
.mine-content { padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom)); }

/* 用户卡（tab-pages-visual-unify）：认证态与游客态**同为**白底一级身份卡 + 柔和投影，
   与首页卡片表面语言一致。两态差异仅由顶部主色软条纹与卡片内容
   （昵称/绑定邮箱、游客态副行）表达，不再用「透明 vs 白底」区分。 */
.user-card {
  display: flex; flex-direction: column; gap: var(--spacing-md);
  margin: var(--spacing-md) var(--spacing-md) var(--spacing-md);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  border-top: 6rpx solid transparent;
  box-shadow: var(--shadow-card);
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
/* 已认证：顶部主色软条纹 */
.user-card--verified {
  border-top-color: var(--color-primary-soft);
}
/* 游客：无条纹（表面与认证态一致；认证入口为宫格「身份认证」格） */
.user-card--guest {
  border-top-color: transparent;
}
.user-card:active { background-color: var(--bg-soft); }
.user-card-head { display: flex; align-items: center; gap: var(--spacing-md); }
.avatar-wrap { flex-shrink: 0; width: 120rpx; height: 120rpx; }
.avatar { width: 120rpx; height: 120rpx; border-radius: var(--radius-circle); overflow: hidden; background: var(--bg-soft); }
.avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.user-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-sm); }
.nickname { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.nickname--guest { color: var(--text-primary); }
.user-id { font-size: var(--font-aux); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-arrow { flex-shrink: 0; }

/* 功能宫格：一行 3 格等宽等高圆角白卡，每格整格热区 */
.grid { display: flex; flex-wrap: wrap; gap: var(--spacing-md); margin: var(--spacing-md) var(--spacing-md) 0; }
.grid-cell { flex: 0 0 calc((100% - 2 * var(--spacing-md)) / 3); min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) var(--spacing-sm);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.grid-cell.pressed { background-color: var(--bg-soft); }
/* 浅粉圆底 + 2px 线性线条图标（复用既有 feature 卡 chip 基线，图标在上、标题在下） */
.grid-cell-icon {
  position: relative;
  width: 96rpx;
  height: 96rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.grid-cell-label { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--text-primary); white-space: nowrap; text-align: center; }
/* 角标：贴卡片（图标 chip）右上角，不遮蔽图标主体 */
.badge { position: absolute; top: -6rpx; right: -6rpx; z-index: 1; }
.badge-dot { width: 14rpx; height: 14rpx; border-radius: var(--radius-circle); background: var(--color-error); }
/* 身份认证已认证徽章：主色圆点（区别于通知红点） */
.badge-cert { background: var(--color-primary); }

/* 「其他」分组列表：白底分组卡 + 三行独立列表项（细分隔线），行内文字 + 右箭头 */
.more-group {
  margin: var(--spacing-lg) var(--spacing-md) 0;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}
.more-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88rpx;
  padding: 0 var(--spacing-md);
  border-bottom: 2rpx solid var(--border-color);
  -webkit-tap-highlight-color: transparent;
  transition: background-color var(--duration-fast) var(--ease-out);
}
.more-row.pressed { background-color: var(--bg-soft); }
.more-row:last-child { border-bottom: none; }
.more-row-text { font-size: var(--font-body); color: var(--text-body); }
.more-row--danger .more-row-text { color: var(--color-error); }
.more-row-arrow { flex-shrink: 0; }

/* 版本行：独立于列表之外的居中纯展示 */
.app-footer {
  padding: var(--spacing-xl) 0 calc(var(--tabbar-height) + env(safe-area-inset-bottom) + var(--spacing-md));
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-2xs);
}
.app-footer-line {
  font-size: var(--font-tiny);
  color: var(--text-tertiary);
  line-height: 1.5;
}

@media (prefers-reduced-motion: reduce) {
  .user-card, .grid-cell, .more-row { transition: none; }
}
</style>
