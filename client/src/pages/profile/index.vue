<template>
  <view class="page profile-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="我的" :showBack="showBack" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 用户卡：未登录显示「点击登录」；已登录显示头像+昵称 -->
      <view
        class="user-card"
        role="button"
        :aria-label="isLoggedIn ? '查看或编辑个人资料' : '点击登录'"
        @tap="onUserCardTap"
      >
        <view class="user-card-head">
          <view class="avatar-wrap">
            <ImageFallback v-if="isLoggedIn && userInfo?.avatar" :src="userInfo.avatar" class="avatar" />
            <view v-else class="avatar avatar-empty">
              <IconSvg name="user" :size="52" color="var(--text-tertiary)" />
            </view>
          </view>
          <view class="user-meta">
            <text class="nickname" :class="{ 'nickname--guest': !isLoggedIn }">
              {{ isLoggedIn ? (userInfo?.nickname || '未知用户') : '点击登录' }}
            </text>
            <text v-if="isLoggedIn && (userInfo?.email || userInfo?.username)" class="user-id">
              {{ userInfo?.email || userInfo?.username }}
            </text>
            <text v-else-if="!isLoggedIn" class="user-id">登录后解锁完整功能</text>
          </view>
          <IconSvg name="arrow" :size="32" color="var(--text-secondary)" class="card-arrow" />
        </view>
      </view>

      <!-- 我的入口：我发布的 / 最新活动 / 意见反馈 / 关于我们（图标 40rpx 主色，右箭头，移除"我的评价"） -->
      <view class="entry-group">
        <view
          v-for="e in entryItems"
          :key="e.key"
          class="entry-row"
          :class="{ pressed: pressedKey === e.key, disabled: e.authLocked && !isLoggedIn }"
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
          <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="entry-arrow" />
        </view>
      </view>

      <!-- 退出登录（警示红，未登录置灰） -->
      <view class="entry-group logout-group">
        <view
          class="entry-row logout-row"
          :class="{ pressed: logoutPressed, disabled: !isLoggedIn }"
          role="button"
          :aria-label="isLoggedIn ? '退出登录' : '未登录'"
          @touchstart="logoutPressed = true"
          @touchend="logoutPressed = false"
          @touchcancel="logoutPressed = false"
          @mousedown="logoutPressed = true"
          @mouseup="logoutPressed = false"
          @mouseleave="logoutPressed = false"
          @tap="onLogout"
        >
          <text class="logout-label">{{ isLoggedIn ? '退出登录' : '未登录' }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 认证弹层：游客点击需认证功能时弹出 -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import IconSvg from '@/components/IconSvg.vue'
import ImageFallback from '@/components/ImageFallback.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { useAuthSheetStore } from '@/stores/authSheet'
import { backToHome } from '@/utils/nav'

const theme = useThemeStore()
const userStore = useUserStore()
const authSheetStore = useAuthSheetStore()
const userInfo = computed(() => userStore.userInfo)
const isLoggedIn = computed(() => userStore.isLoggedIn())
const pressedKey = ref('')

// 是否从首页头像 navigateTo 进入（带 ?from=home），是则显示返回箭头
const showBack = ref(false)
onLoad((q) => {
  showBack.value = q?.from === 'home'
})

/** 需认证入口统一拦截：未登录弹认证弹层，认证成功后自动继续原动作 */
function requireAuth(action: () => void) {
  if (userStore.isLoggedIn()) {
    action()
    return
  }
  authSheetStore.requireAuth(action)
}

/** 用户卡：未登录点击弹认证；已登录点击进个人信息页 */
function onUserCardTap() {
  if (!userStore.isLoggedIn()) {
    authSheetStore.requireAuth(() => uni.navigateTo({ url: '/pages/pages-user/profile-edit/index' }))
    return
  }
  uni.navigateTo({ url: '/pages/pages-user/profile-edit/index' })
}

/** 我的入口：我发布的 / 最新活动 / 意见反馈 / 关于我们（移除"我的评价"） */
const entryItems = [
  { key: 'moments', icon: 'comment', label: '我发布的', authLocked: true, action: () => requireAuth(() => uni.navigateTo({ url: '/pages/pages-user/my-moments/index' })) },
  { key: 'activity', icon: 'broadcast', label: '最新活动', authLocked: false, action: () => uni.navigateTo({ url: '/pages/activity/index' }) },
  { key: 'feedback', icon: 'report', label: '意见反馈', authLocked: false, action: () => uni.navigateTo({ url: '/pages/feedback/index' }) },
  { key: 'about', icon: 'bell', label: '关于我们', authLocked: false, action: () => uni.navigateTo({ url: '/pages/about/index' }) },
]

/** 退出登录：二次确认（红色警示），未登录置灰 */
const logoutPressed = ref(false)
function onLogout() {
  if (!userStore.isLoggedIn()) return
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    confirmText: '退出',
    cancelText: '取消',
    confirmColor: '#FF3B30',
    success: (res) => {
      if (res.confirm) {
        userStore.logout()
        uni.showToast({ title: '已退出登录', icon: 'none' })
      }
    },
  })
}
</script>

<style scoped>
.profile-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-top: var(--spacing-md); padding-bottom: env(safe-area-inset-bottom); }

/* 用户卡（白底圆角卡 + 轻阴影；圆形头像 + 昵称 + ID；整卡可点；按压背景微变+缩放） */
.user-card {
  display: flex; flex-direction: column; gap: var(--spacing-md);
  margin: var(--spacing-md) var(--spacing-md) var(--spacing-sm);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  transition: background-color 120ms var(--ease-out), transform 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.user-card:active { background-color: var(--bg-soft); transform: scale(var(--press-scale)); }
.user-card-head { display: flex; align-items: center; gap: var(--spacing-md); }
.avatar-wrap { flex-shrink: 0; width: 112rpx; height: 112rpx; }
.avatar { width: 112rpx; height: 112rpx; border-radius: 16rpx; overflow: hidden; background: var(--bg-soft); }
.avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.user-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-sm); }
.nickname { font-size: var(--font-card); font-weight: var(--weight-bold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.nickname--guest { color: var(--text-tertiary); }
.user-id { font-size: var(--font-aux); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
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
  transition: background-color 120ms var(--ease-out), transform 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.entry-row:not(:last-child) { border-bottom: 1rpx solid var(--border-color); }
.entry-row.pressed { background-color: var(--bg-soft); }
.entry-row.pressed:active { transform: scale(var(--press-scale)); }
.entry-row.disabled { opacity: 0.4; }
.entry-icon { flex-shrink: 0; }
.entry-label { flex: 1; min-width: 0; font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.entry-arrow { flex-shrink: 0; }

/* 退出登录（警示红，独立分组 + 上间距；未登录置灰） */
.logout-group { margin-top: var(--spacing-md); }
.logout-row { justify-content: center; -webkit-tap-highlight-color: transparent; }
.logout-label { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--color-error); }
.logout-row.disabled .logout-label { color: var(--text-tertiary); }

@media (prefers-reduced-motion: reduce) {
  .user-card, .entry-row { transition: none; }
  .user-card:active, .entry-row.pressed:active { transform: none; }
}
</style>
