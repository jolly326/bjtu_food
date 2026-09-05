<template>
  <view class="page profile-page">
    <Header title="我的" :showBack="showBack" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 用户卡：游客（未认证）显示食客短 ID；已认证显示昵称 + 绑定邮箱 -->
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
          <!-- 未认证：主色文字按钮「去认证」——把状态提示转为行动引导（点击弹 AuthSheet） -->
          <view
            v-if="!isVerified"
            class="verify-action"
            role="button"
            aria-label="去认证"
            @tap.stop="onVerifyTap"
          >
            <text class="verify-action-text">去认证</text>
          </view>
          <IconSvg name="arrow" :size="32" color="var(--text-secondary)" class="card-arrow" />
        </view>
      </view>

      <!-- 功能方块卡：意见反馈 / 最新活动（顶部高亮，区别于下方常规入口；网格布局，与常规列表明显分层） -->
      <view class="feature-grid">
        <view
          v-for="f in featuredItems"
          :key="f.key"
          class="feature-card"
          role="button"
          :aria-label="f.label"
          @tap="f.action"
        >
          <view class="feature-card-icon">
            <IconSvg :name="f.icon" :size="40" color="var(--color-primary)" />
            <text v-if="f.key === 'activity'" class="feature-card-tag">新</text>
          </view>
          <text class="feature-card-label">{{ f.label }}</text>
        </view>
      </view>

      <!-- 我的入口：系统通知 / 我发布的 / 关于我们（意见反馈、最新活动已抽离至顶部方块卡；需认证入口不置灰，点击弹认证引导） -->
      <view class="entry-group">
        <view
          v-for="e in entryItems"
          :key="e.key"
          class="entry-row"
          role="button"
          :aria-label="e.label"
          @tap="e.action"
        >
          <IconSvg :name="e.icon" :size="40" color="var(--color-primary)" class="entry-icon" />
          <text class="entry-label">{{ e.label }}</text>
          <!-- 认证提示已移除（tab-pages-visual-unify）：需认证入口不置灰、行内不显示「认证」弱标识，
               未认证用户点击时由 requireAuth → AuthSheet 弹出引导，页面只保留用户卡上的「去认证」入口 -->
          <!-- 系统通知未读红点角标 -->
          <view v-if="e.key === 'notify' && notifyStore.unreadCount > 0" class="entry-badge" aria-hidden="true">
            <text class="entry-badge-text">{{ notifyStore.unreadCount > 99 ? '99+' : notifyStore.unreadCount }}</text>
          </view>
          <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="entry-arrow" />
        </view>
      </view>

      <!-- 版本号 footer：构建期注入（vite.config.ts 读取 manifest versionName） -->
      <view class="app-version">
        <text class="app-version-text">知行食记 v{{ appVersion }}</text>
      </view>
    </scroll-view>

    <!-- 认证弹层：游客点击需认证功能时弹出 -->
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
import ImageFallback from '@/components/ImageFallback.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import TabBar from '@/components/TabBar.vue'
import { useUserStore } from '@/stores/user'
import { useAuthSheetStore } from '@/stores/auth-sheet'
import { useNotifyStore } from '@/stores/notify'
import { backToHome } from '@/utils/nav'
import { getGuestShortId as getLocalGuestShortId } from '@/utils/guest'

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

// 每次进入「我的」刷新未读通知数（红点角标；通知属认证专属，仅认证用户刷新未读数）
onShow(() => {
  // 锚定底部菜单栏：我的页始终显示并高亮
  showTab('profile')
  if (userStore.isVerified()) notifyStore.fetchUnread()
})

/** 需认证入口统一拦截：未认证（verified=false）弹认证引导，认证成功后自动继续原动作（§5.y 入口不置灰） */
function requireAuth(action: () => void) {
  if (userStore.isVerified()) {
    action()
    return
  }
  authSheetStore.requireAuth(action)
}

/** 「去认证」：与用户卡点击同源，复用底部认证弹层（不单独写认证页） */
function onVerifyTap() {
  authSheetStore.show()
}

/** 用户卡：已认证点击进个人信息页；游客点击唤起底部认证弹窗（统一认证入口，不单独写页） */
function onUserCardTap() {
  if (!userStore.isVerified()) {
    authSheetStore.show()
    return
  }
  uni.navigateTo({ url: '/pages/profile-edit/index' })
}

/** 功能凸显区块：意见反馈 / 最新活动（抽离至顶部高亮，区别于常规入口） */
const featuredItems = [
  { key: 'activity', icon: 'broadcast', label: '最新活动', action: () => uni.showToast({ title: '功能暂未实现', icon: 'none' }) },
  { key: 'feedback', icon: 'report', label: '意见反馈', action: () => uni.navigateTo({ url: '/pages/feedback/index' }) },
]

/** 我的入口：系统通知 / 我发布的 / 关于我们（意见反馈、最新活动已抽离至顶部凸显区块） */
// 注：原 authLocked 字段已移除——菜单行内不再渲染「认证」弱标识，
// 需认证行为由 action 内的 requireAuth() 直接表达（未认证点击即弹 AuthSheet）。
const entryItems = [
  { key: 'notify', icon: 'bell', label: '系统通知', action: () => uni.navigateTo({ url: '/pages/notifications/index' }) },
  { key: 'moments', icon: 'comment', label: '我发布的', action: () => requireAuth(() => uni.navigateTo({ url: '/pages/my-published/index' })) },
  { key: 'about', icon: 'contact', label: '关于我们', action: () => uni.navigateTo({ url: '/pages/about/index' }) },
]


</script>

<style scoped>
.profile-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); overflow: hidden; }
/* 顶部留白由 user-card 的 margin-top 提供（md，与首页广播条-卡间距一致） */
.scroll-wrap { flex: 1; min-height: 0; overflow-y: auto; padding-top: 0; padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom)); }

/* 用户卡（tab-pages-visual-unify）：认证态与游客态**同为**白底一级身份卡 + 柔和投影，
   与首页/动态卡片表面语言一致。两态差异仅由顶部主色软条纹与卡片内容
   （昵称/绑定邮箱、游客态的「去认证」引导）表达，不再用「透明 vs 白底」区分。 */
.user-card {
  display: flex; flex-direction: column; gap: var(--spacing-md);
  margin: var(--spacing-md) var(--spacing-md) var(--spacing-sm);
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
/* 头像：正圆 + 浅底色（头像统一 50%；tab-pages-visual-polish-3 放大） */
.avatar { width: 120rpx; height: 120rpx; border-radius: var(--radius-circle); overflow: hidden; background: var(--bg-soft); }
.avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.user-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-sm); }
.nickname { font-size: var(--font-subtitle); font-weight: var(--weight-bold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.nickname--guest { color: var(--text-primary); }
.user-id { font-size: var(--font-aux); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 「去认证」行动按钮：主色文字 + 细边框轻量胶囊（tab-pages-visual-polish-3：边框 1rpx、字重 500） */
.verify-action {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-pill);
  border: 1rpx solid var(--color-primary);
  background: transparent;
  -webkit-tap-highlight-color: transparent;
}
.verify-action:active { opacity: 0.7; }
.verify-action-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-medium); }
.card-arrow { flex-shrink: 0; }

/* 功能方块卡（网格 2 列，区别于下方常规列表，视觉分层） */
.feature-grid {
  display: flex;
  gap: var(--spacing-md);
  margin: var(--spacing-sm) var(--spacing-md) var(--spacing-sm);
}
.feature-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  flex: 1;
  min-width: 0;
  /* tab-pages-visual-polish-3：垂直内边距收窄压缩高度 */
  padding: var(--spacing-md) var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  /* 二级功能卡：微抬阴影，弱于一级身份卡 */
  box-shadow: var(--shadow-card-soft);
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.feature-card.pressed { background-color: var(--bg-soft); }
.feature-card-icon {
  position: relative;
  width: 80rpx;
  height: 80rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.feature-card-tag {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  font-size: var(--font-tiny);
  color: var(--bg-card);
  background: var(--color-error);
  border-radius: var(--radius-pill);
  padding: 2rpx 8rpx;
  font-weight: var(--weight-semibold);
  line-height: 1.4;
}
/* 功能卡标题：一级标题档（32rpx / 600），与菜名/昵称同档 */
.feature-card-label { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--text-primary); }

/* 版本号 footer：右下角 + 最小字号三级灰（tab-pages-visual-polish-3：再降一档） */
.app-version { display: flex; justify-content: flex-end; padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-lg) + env(safe-area-inset-bottom)); }
.app-version-text { font-size: var(--font-tiny); color: var(--text-tertiary); }

/* 我的入口（白底圆角卡 + 行布局 + 右箭头；图标 40rpx 主色；按压背景微变+缩放） */
.entry-group {
  margin: 0 var(--spacing-md) var(--spacing-sm);
  /* 三级列表：凹陷表面 + 去悬浮阴影，以发丝线分隔行，读作分组列表而非悬浮卡 */
  background: var(--bg-soft);
  border-radius: var(--radius-card);
  box-shadow: none;
  overflow: hidden;
}
.entry-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  /* tab-pages-visual-unify：菜单行高收紧（104→96rpx） */
  height: 96rpx;
  padding: 0 var(--spacing-lg);
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.entry-row:not(:last-child) { border-bottom: 1rpx solid var(--border-color); }
.entry-row.pressed { background-color: var(--bg-soft); }
.entry-icon { flex-shrink: 0; }
/* 菜单主标题：一级标题档（32rpx / 600），与菜名/昵称/功能卡标题同档 */
.entry-label { flex: 1; min-width: 0; font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.entry-arrow { flex-shrink: 0; }

/* 系统通知未读红点角标 */
.entry-badge {
  flex-shrink: 0;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  border-radius: var(--radius-xs);
  background: var(--color-error);
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}
.entry-badge-text { font-size: var(--font-tiny); color: var(--bg-card); font-weight: var(--weight-semibold); line-height: 1; }

@media (prefers-reduced-motion: reduce) {
  .user-card, .entry-row, .feature-card { transition: none; }
}
</style>
