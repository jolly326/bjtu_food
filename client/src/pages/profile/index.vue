<template>
  <view class="page profile-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="我的" :showBack="showBack" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 用户卡：游客（未认证）显示食客短 ID；已认证显示昵称 + 绑定邮箱 -->
      <view
        class="user-card"
        role="button"
        :aria-label="isVerified ? '查看或编辑个人资料' : '游客身份'"
        @tap="onUserCardTap"
      >
        <view class="user-card-head">
          <view class="avatar-wrap">
            <ImageFallback v-if="userInfo?.avatar" :src="userInfo.avatar" class="avatar" />
            <view v-else class="avatar avatar-empty">
              <IconSvg name="user" :size="52" color="var(--text-tertiary)" />
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
          <view v-if="!isVerified" class="verify-badge">
            <IconSvg name="lock" :size="24" color="var(--color-primary)" />
            <text class="verify-badge-text">未认证</text>
          </view>
          <IconSvg name="arrow" :size="32" color="var(--text-secondary)" class="card-arrow" />
        </view>
      </view>

      <!-- 我的入口：系统通知 / 我发布的 / 最新活动 / 意见反馈 / 关于我们（需认证入口不置灰，点击弹认证引导） -->
      <view class="entry-group">
        <view
          v-for="e in entryItems"
          :key="e.key"
          class="entry-row"
          :class="{ pressed: pressedKey === e.key }"
          role="button"
          :aria-label="e.label"
          @touchstart="pressedKey = e.key"
          @touchend="pressedKey = ''"
          @touchcancel="pressedKey = ''"
          @mousedown="pressedKey = e.key"
          @mouseup="pressedKey = ''"
          @mouseleave="pressedKey = ''"
          @tap="e.action"
        >
          <IconSvg :name="e.icon" :size="40" color="var(--color-primary)" class="entry-icon" />
          <text class="entry-label">{{ e.label }}</text>
          <!-- 需认证入口的未认证提示（不置灰，仅弱化标识） -->
          <text v-if="e.authLocked && !isVerified" class="entry-lock">认证</text>
          <!-- 系统通知未读红点角标 -->
          <view v-if="e.key === 'notify' && notifyStore.unreadCount > 0" class="entry-badge" aria-hidden="true">
            <text class="entry-badge-text">{{ notifyStore.unreadCount > 99 ? '99+' : notifyStore.unreadCount }}</text>
          </view>
          <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="entry-arrow" />
        </view>
      </view>
    </scroll-view>

    <!-- 认证弹层：游客点击需认证功能时弹出 -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import IconSvg from '@/components/IconSvg.vue'
import ImageFallback from '@/components/ImageFallback.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { useAuthSheetStore } from '@/stores/authSheet'
import { useNotifyStore } from '@/stores/notify'
import { backToHome } from '@/utils/nav'
import { getGuestShortId as getLocalGuestShortId } from '@/utils/guest'

const theme = useThemeStore()
const userStore = useUserStore()
const authSheetStore = useAuthSheetStore()
const notifyStore = useNotifyStore()
const userInfo = computed(() => userStore.userInfo)
/** 已认证（verified=true）——微信静默登录后恒有登录态，游客/认证用 verified 区分（§5.y） */
const isVerified = computed(() => userStore.isVerified())
const bindEmail = computed(() => userStore.userInfo?.bindEmail || '')
/** 游客展示短 ID：优先后端 guestShortId（食客+ID 尾 4 位），未提供回退本地游客 ID */
const guestShortId = computed(() => userInfo.value?.guestShortId || getLocalGuestShortId())
const pressedKey = ref('')

// 是否从首页头像 navigateTo 进入（带 ?from=home），是则显示返回箭头
const showBack = ref(false)
onLoad((q) => {
  showBack.value = q?.from === 'home'
  // 进入「我的」确保静默登录已就绪（游客态才有认证前提）
  userStore.silentLogin()
})

// 每次进入「我的」刷新未读通知数（红点角标；通知属认证专属，仅认证用户刷新未读数）
onShow(() => {
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

/** 用户卡：已认证点击进个人信息页；游客点击唤起底部认证弹窗（统一认证入口，不单独写页） */
function onUserCardTap() {
  if (!userStore.isVerified()) {
    authSheetStore.show()
    return
  }
  uni.navigateTo({ url: '/pages/pages-user/profile-edit/index' })
}

/** 我的入口：系统通知 / 我发布的 / 最新活动 / 意见反馈 / 关于我们 */
const entryItems = [
  { key: 'notify', icon: 'bell', label: '系统通知', authLocked: true, action: () => requireAuth(() => uni.navigateTo({ url: '/pages/profile/notifications/index' })) },
  { key: 'moments', icon: 'comment', label: '我发布的', authLocked: true, action: () => requireAuth(() => uni.navigateTo({ url: '/pages/pages-user/my-moments/index' })) },
  { key: 'activity', icon: 'broadcast', label: '最新活动', authLocked: false, action: () => uni.navigateTo({ url: '/pages/activity/index' }) },
  { key: 'feedback', icon: 'report', label: '意见反馈', authLocked: false, action: () => uni.navigateTo({ url: '/pages/feedback/index' }) },
  { key: 'about', icon: 'contact', label: '关于我们', authLocked: false, action: () => uni.navigateTo({ url: '/pages/about/index' }) },
]


</script>

<style scoped>
.profile-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
/* 顶部留白由 user-card 的 margin-top 提供（md，与首页广播条-卡间距一致） */
.scroll-wrap { flex: 1; overflow-y: auto; padding-top: 0; padding-bottom: env(safe-area-inset-bottom); }

/* 用户卡（白底圆角卡 + 轻阴影；圆形头像 + 昵称 + ID；整卡可点；按压背景微变+缩放） */
.user-card {
  display: flex; flex-direction: column; gap: var(--spacing-md);
  margin: var(--spacing-md) var(--spacing-md) var(--spacing-sm);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  transition: background-color var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.user-card:active { background-color: var(--bg-soft); transform: scale(var(--press-scale)); }
.user-card-head { display: flex; align-items: center; gap: var(--spacing-md); }
.avatar-wrap { flex-shrink: 0; width: 112rpx; height: 112rpx; }
.avatar { width: 112rpx; height: 112rpx; border-radius: 16rpx; overflow: hidden; background: var(--bg-soft); }
.avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.user-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-sm); }
.nickname { font-size: var(--font-card); font-weight: var(--weight-bold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.nickname--guest { color: var(--text-primary); }
.user-id { font-size: var(--font-aux); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 未认证角标：锁图标 + 「未认证」小字 */
.verify-badge { flex-shrink: 0; display: flex; align-items: center; gap: 6rpx; padding: 6rpx 12rpx; border-radius: var(--radius-pill); background: var(--color-primary-soft); }
.verify-badge-text { font-size: 20rpx; color: var(--color-primary); font-weight: var(--weight-semibold); }
.card-arrow { flex-shrink: 0; }

/* 我的入口（白底圆角卡 + 行布局 + 右箭头；图标 40rpx 主色；按压背景微变+缩放） */
.entry-group {
  margin: 0 var(--spacing-md) var(--spacing-sm);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}
.entry-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  height: 104rpx;
  padding: 0 var(--spacing-lg);
  transition: background-color var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.entry-row:not(:last-child) { border-bottom: 1rpx solid var(--border-color); }
.entry-row.pressed { background-color: var(--bg-soft); }
.entry-row.pressed:active { transform: scale(var(--press-scale)); }
.entry-icon { flex-shrink: 0; }
.entry-label { flex: 1; min-width: 0; font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 需认证入口的未认证弱标识（不置灰） */
.entry-lock { flex-shrink: 0; font-size: 20rpx; color: var(--text-tertiary); font-weight: var(--weight-semibold); }
.entry-arrow { flex-shrink: 0; }

/* 系统通知未读红点角标 */
.entry-badge {
  flex-shrink: 0;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  border-radius: 16rpx;
  background: var(--color-error);
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}
.entry-badge-text { font-size: 20rpx; color: var(--bg-card); font-weight: var(--weight-semibold); line-height: 1; }

@media (prefers-reduced-motion: reduce) {
  .user-card, .entry-row { transition: none; }
  .user-card:active, .entry-row.pressed:active { transform: none; }
}
</style>
