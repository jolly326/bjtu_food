<template>
  <view class="page mine-page">
    <!-- 「我的」是 TabBar 主根页（TabBar 经 reLaunch 切换，无带参跳转），恒不需要返回箭头：
         showBack 显式传 false（AppHeader 默认值为 true，不能省略）；原 `?from=home` 死分支已删（P2-11） -->
    <Header title="我的" :show-back="false" />

    <view class="mine-content">
      <!-- 用户卡：游客（未认证）显示食客短 ID +「去认证」；已认证显示昵称 + 绑定邮箱。
           点击行为二分：游客整卡唤起认证弹层（不进入编辑页）；认证态进入个人信息编辑页（/pages/profile/index） -->
      <view
        class="user-card"
        :class="isVerified ? 'user-card--verified' : 'user-card--guest'"
        role="button"
        :aria-label="isVerified ? '查看或编辑个人资料' : '游客身份'"
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
            <text v-if="isVerified && bindEmail" class="user-id">
              {{ bindEmail }}
            </text>
            <text v-else-if="!isVerified" class="user-id">游客 {{ guestLabel }}</text>
          </view>
          <!-- 未认证：主色文字按钮「去认证」——把状态提示转为行动引导（点击弹认证） -->
          <view
            v-if="!isVerified"
            class="verify-action"
            role="button"
            aria-label="去认证"
            @tap.stop="onVerifyTap"
          >
            <text class="verify-action-text">去认证</text>
          </view>
          <IconSvg name="arrow" :size="28" :color="COLOR_MAP['text-tertiary']" class="card-arrow" />
        </view>
      </view>

      <!-- 功能宫格：一行三列（意见反馈 / 系统通知 / 我的评价），三格等宽等高，每格整格热区 -->
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
          </view>
          <text class="grid-cell-label">{{ cell.label }}</text>
        </view>
      </view>

      <!-- 底部信息区：两行纯展示（版本 / 学校，aria-hidden）+ 一行可点合规入口。
           合规入口是该区域内唯一可点元素，做可点性最小差异化（主色 + 描边胶囊），
           故不能再把 aria-hidden 挂在整个容器上（否则可点元素对辅助技术不可见）。 -->
      <view class="app-footer">
        <view class="app-footer-lines" aria-hidden="true">
          <text class="app-footer-line">知行食记 v{{ appVersion }}</text>
          <text class="app-footer-line">北京交通大学 · 校园美食分享圈</text>
        </view>
        <view class="app-footer-links">
          <text
            class="app-footer-link"
            role="button"
            aria-label="隐私政策与用户协议"
            @tap="goPrivacy"
          >隐私政策 · 用户协议</text>
          <text
            class="app-footer-link app-footer-link--danger"
            role="button"
            aria-label="注销账号"
            @tap="onAccountDelete"
          >注销账号</text>
        </view>
      </view>
    </view>

    <!-- 认证弹层：游客点击用户卡或需认证功能时弹出 -->
    <AuthSheet />

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
import ImageFallback from './ImageFallback.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import TabBar from '@/components/TabBar.vue'
import { useUserStore } from '@/stores/user'
import { useAuthSheetStore } from '@/stores/auth-sheet'
import { useNotifyStore } from '@/stores/notify'
import { PATH } from '@/utils/routes'
import { getLocalGuestLabel } from '@/utils/guest'
import { deleteAccount } from '@/api/user'
import { COLOR_MAP, MODAL_CONFIRM_PRIMARY_COLOR } from '@/theme/tokens'

const userStore = useUserStore()
const authSheetStore = useAuthSheetStore()
const notifyStore = useNotifyStore()
const userInfo = computed(() => userStore.userInfo)
/** 已认证（verified=true）——微信静默登录后恒有登录态，游客/认证用 verified 区分（§5.y） */
const isVerified = computed(() => userStore.isVerified())
const bindEmail = computed(() => userStore.userInfo?.bindEmail || '')
/**
 * 游客展示短 ID：由账号 `id` 派生「食客 + ID 尾 4 位」（id 不足 4 位取全量）。
 * 2026-09-21 spec §7.32：短标识不再由接口出参（纯派生值），展示层现算；
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
  // 原 `showBack = q?.from === 'home'` 分支已删（P2-11 / PR-05）：全仓无任何带 ?from=home 跳转
  // 到本页的调用点（TabBar 经 reLaunch 切换、无参数），该状态恒为 false，属死状态。
  userStore.silentLogin()
})

// 每次进入「我的」刷新未读通知数（宫格红点角标；通知属认证专属，仅认证用户刷新未读数）
onShow(() => {
  // 锚定底部菜单栏：我的页始终显示并高亮
  showTab('profile')
  if (userStore.isVerified()) notifyStore.fetchUnread()
})

/** 「去认证」：与用户卡点击同源，复用底部认证弹层（不单独写认证页） */
function onVerifyTap() {
  authSheetStore.show()
}

/** 用户卡点击二分：游客整卡直接唤起认证弹层；已认证点击进个人信息编辑页（/pages/profile/index） */
function onUserCardTap() {
  if (!userStore.isVerified()) {
    authSheetStore.show()
    return
  }
  uni.navigateTo({ url: PATH.profile })
}

/** 功能宫格数据（一行三列，顺序固定：意见反馈 / 系统通知 / 我的评价）；每格整格热区 */
interface GridCell {
  key: string
  icon: string
  label: string
  action: () => void
}

/** 「我的评价」：需认证入口（未认证弹 AuthSheet，认证成功后自动续跑进入本页） */
function goMyReviews() {
  if (!userStore.requireAuth(goMyReviews)) return
  uni.navigateTo({ url: PATH.myReviews })
}

/** 底部合规入口：隐私政策与用户协议（应用内页面，不依赖外部域名） */
function goPrivacy() {
  uni.navigateTo({ url: PATH.privacy })
}

/** 注销账号（合规硬需求）：二次确认 → 后端匿名化 → 清本地态（旧 token 已被后端拉黑，静默登录建新游客号） */
function onAccountDelete() {
  uni.showModal({
    title: '注销账号',
    content: '注销后账号将匿名化且不可恢复：你的评价与反馈会保留，但不再关联你的身份；注销后需重新登录。',
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
  { key: 'feedback', icon: 'report', label: '意见反馈', action: () => uni.navigateTo({ url: PATH.feedback }) },
  { key: 'notify', icon: 'bell', label: '系统通知', action: () => uni.navigateTo({ url: PATH.notifications }) },
  { key: 'myReviews', icon: 'star', label: '我的评价', action: goMyReviews },
]
</script>

<style scoped>
/* mine 属静态短内容页，内容可放下时不再设置常驻 scroll-view；
   页面以自然文档滚动承载超高内容（超大字体/小屏），并保留底部 TabBar 避让留白 */
.mine-page { display: flex; flex-direction: column; min-height: 100vh; background: var(--bg-page); }
.mine-content { padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom)); }

/* 用户卡（tab-pages-visual-unify）：认证态与游客态**同为**白底一级身份卡 + 柔和投影，
   与首页卡片表面语言一致。两态差异仅由顶部主色软条纹与卡片内容
   （昵称/绑定邮箱、游客态的「去认证」引导）表达，不再用「透明 vs 白底」区分。 */
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
/* 游客：无条纹（表面与认证态一致，引导由「去认证」按钮承担） */
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
/* 「去认证」行动按钮：主色文字 + 细边框轻量胶囊 */
.verify-action {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: var(--spacing-xs);
  border-radius: var(--radius-pill);
  border: 1rpx solid var(--color-primary);
  background: transparent;
  -webkit-tap-highlight-color: transparent;
}
.verify-action:active { opacity: 0.7; }
.verify-action-text { font-size: var(--font-aux); color: var(--color-primary-text); font-weight: var(--weight-medium); }
.card-arrow { flex-shrink: 0; }

/* 功能宫格：一行三列等宽等高圆角白卡，格间间距均匀，每格整格热区 */
.grid { display: flex; align-items: stretch; gap: var(--spacing-md); margin: var(--spacing-md) var(--spacing-md) 0; }
.grid-cell {
  flex: 1;
  min-width: 0;
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

/* 底部信息区：与宫格之间留大片留白，位于 TabBar 之上。
   两行纯展示（版本 / 学校）走 aria-hidden 子容器；合规入口为该区域唯一可点元素 */
.app-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: calc(var(--spacing-xl) + var(--spacing-xl)) var(--spacing-md) var(--spacing-lg);
}
.app-footer-lines { display: flex; flex-direction: column; align-items: center; gap: var(--spacing-xs); }
.app-footer-line { font-size: var(--font-tiny); color: var(--text-tertiary); line-height: 1.5; }
/* 合规入口可点性最小差异化：主色 + 描边胶囊（与浅灰纯展示行明确区分） */
.app-footer-link {
  padding: var(--spacing-2xs) var(--spacing-sm);
  font-size: var(--font-tiny);
  color: var(--color-primary-text);
  border: 1rpx solid var(--color-primary);
  border-radius: var(--radius-pill);
  -webkit-tap-highlight-color: transparent;
}
.app-footer-link:active { opacity: 0.7; }
.app-footer-links { display: flex; align-items: center; gap: var(--spacing-md); }
/* 注销账号：danger 弱化（合规入口但非主行动），与隐私胶囊同行 */
.app-footer-link--danger { color: var(--color-error); border-color: var(--color-error); opacity: 0.8; }

@media (prefers-reduced-motion: reduce) {
  .user-card, .grid-cell { transition: none; }
}
</style>
