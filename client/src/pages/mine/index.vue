<template>
  <view class="page mine-page">
    <Header title="我的" :showBack="showBack" @back="backToHome" />

    <view class="mine-content">
      <!-- 用户卡：游客（未认证）显示食客短 ID +「去认证」；已认证显示昵称 + 绑定邮箱。
           点击行为二分：游客整卡唤起认证弹层（不进入编辑页）；认证态进入个人信息编辑页（/pages/me/profile/index） -->
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
              <IconSvg name="user" :size="60" color="var(--text-tertiary)" />
            </view>
          </view>
          <view class="user-meta">
            <text class="nickname" :class="{ 'nickname--guest': !isVerified }">
              {{ isVerified ? (userInfo?.nickname || '食客') : (userInfo?.nickname || '游客') }}
            </text>
            <text v-if="isVerified && (bindEmail || userInfo?.email)" class="user-id">
              {{ bindEmail || userInfo?.email }}
            </text>
            <text v-else-if="!isVerified" class="user-id">游客 {{ guestShortId }}</text>
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
          <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="card-arrow" />
        </view>
      </view>

      <!-- 2×2 功能宫格：最新活动 / 意见反馈 / 系统通知 / 我发布的（每格整格热区，角标贴右上角） -->
      <view class="grid">
        <view v-for="(row, ri) in gridRows" :key="ri" class="grid-row">
          <view
            v-for="cell in row"
            :key="cell.key"
            class="grid-cell"
            role="button"
            :aria-label="cell.label"
            hover-class="pressed"
            @tap="cell.action"
          >
            <view class="grid-cell-icon">
              <IconSvg :name="cell.icon" :size="44" color="var(--color-primary)" />
              <!-- 系统通知：存在未读时右上红点（无未读 / 未登录不显示） -->
              <view v-if="cell.key === 'notify' && notifyStore.unreadCount > 0" class="badge badge-dot" aria-hidden="true" />
            </view>
            <text class="grid-cell-label">{{ cell.label }}</text>
          </view>
        </view>
      </view>

      <!-- 底部静态信息区：纯展示（版本 / 学校），无点击与跳转，位于 TabBar 上方 -->
      <view class="app-footer" aria-hidden="true">
        <text class="app-footer-line">知行食记 v{{ appVersion }}</text>
        <text class="app-footer-line">北京交通大学 · 校园美食分享圈</text>
      </view>
    </view>

    <!-- 认证弹层：游客点击用户卡或需认证功能时弹出 -->
    <AuthSheet />

    <!-- 底部常驻菜单栏：首页/动态/我的 三主区切换（仅主根页显示） -->
    <TabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
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
import { backToHome } from '@/utils/nav'
import { getGuestShortId as getLocalGuestShortId } from '@/utils/guest'
import { FEATURE_GATES, resolveGate } from '@/utils/feature-gates'

const userStore = useUserStore()
const authSheetStore = useAuthSheetStore()
const notifyStore = useNotifyStore()
const userInfo = computed(() => userStore.userInfo)
/** 已认证（verified=true）——微信静默登录后恒有登录态，游客/认证用 verified 区分（§5.y） */
const isVerified = computed(() => userStore.isVerified())
const bindEmail = computed(() => userStore.userInfo?.bindEmail || '')
/** 游客展示短 ID：优先后端 guestShortId（食客+ID 尾 4 位），未提供回退本地游客 ID */
const guestShortId = computed(() => userInfo.value?.guestShortId || getLocalGuestShortId())
/** 版本号：构建期由 vite.config.ts 从 manifest.json versionName 注入（小程序运行时读不到 manifest） */
const appVersion = __APP_VERSION__

// 是否从首页头像 navigateTo 进入（带 ?from=home），是则显示返回箭头
const showBack = ref(false)
onLoad((q) => {
  showBack.value = q?.from === 'home'
  // 进入「我的」确保静默登录已就绪（游客态才有认证前提）
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

/** 用户卡点击二分：游客整卡直接唤起认证弹层；已认证点击进个人信息编辑页（/pages/me/profile/index） */
function onUserCardTap() {
  if (!userStore.isVerified()) {
    authSheetStore.show()
    return
  }
  uni.navigateTo({ url: PATH.profile })
}

/** 2×2 功能宫格数据（顺序固定：第一行 最新活动/意见反馈，第二行 系统通知/我发布的）；每格整格热区 */
interface GridCell {
  key: string
  icon: string
  label: string
  action: () => void
}
/** 暂缓开放格点击：按 feature-gates 读取——open ? 跳转登记路由 : toast 提示（文案不在此硬编码） */
function gateTap(gateKey: keyof typeof FEATURE_GATES): () => void {
  return () => {
    const gate = resolveGate(gateKey)
    if (gate.open) uni.navigateTo({ url: gate.url })
    else uni.showToast({ title: gate.toast, icon: 'none' })
  }
}
const gridRows: GridCell[][] = [
  [
    // 最新活动：暂缓开放登记于 utils/feature-gates.ts
    { key: 'activity', icon: 'broadcast', label: '最新活动', action: gateTap('activity') },
    { key: 'feedback', icon: 'report', label: '意见反馈', action: () => uni.navigateTo({ url: PATH.feedback }) },
  ],
  [
    { key: 'notify', icon: 'bell', label: '系统通知', action: () => uni.navigateTo({ url: PATH.notifications }) },
    // 我发布的：暂缓开放登记于 utils/feature-gates.ts
    { key: 'moments', icon: 'comment', label: '我发布的', action: gateTap('publishMine') },
  ],
]
</script>

<style scoped>
/* mine 属静态短内容页，内容可放下时不再设置常驻 scroll-view；
   页面以自然文档滚动承载超高内容（超大字体/小屏），并保留底部 TabBar 避让留白 */
.mine-page { display: flex; flex-direction: column; min-height: 100vh; background: var(--bg-page); }
.mine-content { padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom)); }

/* 用户卡（tab-pages-visual-unify）：认证态与游客态**同为**白底一级身份卡 + 柔和投影，
   与首页/动态卡片表面语言一致。两态差异仅由顶部主色软条纹与卡片内容
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
.verify-action-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-medium); }
.card-arrow { flex-shrink: 0; }

/* 2×2 功能宫格：四格等尺寸圆角白卡，格间间距均匀，每格整格热区 */
.grid { display: flex; flex-direction: column; gap: var(--spacing-md); margin: var(--spacing-md) var(--spacing-md) 0; }
.grid-row { display: flex; gap: var(--spacing-md); }
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
.grid-cell-label { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--text-primary); }
/* 角标：贴卡片（图标 chip）右上角，不遮蔽图标主体 */
.badge { position: absolute; top: -6rpx; right: -6rpx; z-index: 1; }
.badge-dot { width: 14rpx; height: 14rpx; border-radius: var(--radius-circle); background: var(--color-error); }

/* 底部静态信息区：与宫格之间留大片留白，居中小号浅灰、纯展示（无点击/跳转），位于 TabBar 之上 */
.app-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-xs);
  padding: calc(var(--spacing-xl) + var(--spacing-xl)) var(--spacing-md) var(--spacing-lg);
}
.app-footer-line { font-size: var(--font-tiny); color: var(--text-tertiary); line-height: 1.5; }

@media (prefers-reduced-motion: reduce) {
  .user-card, .grid-cell { transition: none; }
}
</style>
