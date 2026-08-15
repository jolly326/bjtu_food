<template>
  <view class="page profile-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="我的" @back="backToHome" />
    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 用户卡：未登录显示游客态（点击弹出认证）；已登录显示完整信息（点击进个人信息页） -->
      <view class="user-card enter-up" :style="{ '--enter-i': 0 }" @tap="onUserCardTap">
        <view class="user-card-head">
          <view class="avatar-wrap">
            <image v-if="userInfo?.avatar" :src="getImageUrl(userInfo.avatar)" class="avatar" />
            <view v-else class="avatar avatar-empty">
              <IconSvg name="user" :size="52" color="var(--text-tertiary)" />
            </view>
          </view>
          <view class="user-meta">
            <text class="nickname">{{ isLoggedIn ? (userInfo?.nickname || '未知用户') : '未登录' }}</text>
            <view class="meta-line">
              <text v-if="isLoggedIn" class="user-id">{{ userInfo?.email || '游客模式' }}</text>
              <text v-else class="user-id">游客模式 · 登录解锁完整功能</text>
            </view>
          </view>
          <IconSvg name="arrow" :size="32" color="var(--text-secondary)" class="card-arrow" />
        </view>
      </view>

      <!-- 我的入口：我发布的 / 我的评价 / 活动报名 / 意见反馈 / 关于我们（白卡 + 右箭头） -->
      <view class="entry-group enter-up" :style="{ '--enter-i': 1 }">
        <view
          v-for="e in entryItems"
          :key="e.key"
          class="entry-row"
          :class="{ pressed: pressedKey === e.key }"
          @touchstart="pressedKey = e.key"
          @touchend="pressedKey = ''"
          @touchcancel="pressedKey = ''"
          @mousedown="pressedKey = e.key"
          @mouseup="pressedKey = ''"
          @mouseleave="pressedKey = ''"
          @tap="e.action"
        >
          <IconSvg :name="e.icon" :size="36" color="var(--text-secondary)" class="entry-icon" />
          <text class="entry-label">{{ e.label }}</text>
          <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="entry-arrow" />
        </view>
      </view>

      <!-- 退出登录（警示红，未登录置灰） -->
      <view class="entry-group logout-group enter-up" :style="{ '--enter-i': 2 }">
        <view
          class="entry-row logout-row"
          :class="{ pressed: logoutPressed, disabled: !isLoggedIn }"
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
import Header from '@/components/header.vue'
import IconSvg from '@/components/IconSvg.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { useAuthSheetStore } from '@/stores/authSheet'
import { getImageUrl } from '@/utils/image'
import { backToHome } from '@/utils/nav'

const theme = useThemeStore()
const userStore = useUserStore()
const authSheetStore = useAuthSheetStore()
const userInfo = computed(() => userStore.userInfo)
const isLoggedIn = computed(() => userStore.isLoggedIn())
const pressedKey = ref('')

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
  goProfileEdit()
}

/** 我的入口：我发布的 / 我的评价 / 活动报名 / 意见反馈 / 关于我们 */
const entryItems = [
  { key: 'moments', icon: 'comment', label: '我发布的', action: () => requireAuth(() => uni.navigateTo({ url: '/pages/pages-user/my-moments/index' })) },
  { key: 'reviews', icon: 'star', label: '我的评价', action: () => requireAuth(() => uni.navigateTo({ url: '/pages/pages-user/my-reviews/index' })) },
  { key: 'activity', icon: 'broadcast', label: '活动报名', action: () => uni.navigateTo({ url: '/pages/activity/index' }) },
  { key: 'feedback', icon: 'report', label: '意见反馈', action: () => uni.navigateTo({ url: '/pages/feedback/index' }) },
  { key: 'about', icon: 'bell', label: '关于我们', action: () => uni.navigateTo({ url: '/pages/about/index' }) },
]

/** 退出登录：二次确认（红色警示），未登录置灰不可点 */
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

function goProfileEdit() {
  uni.navigateTo({ url: '/pages/pages-user/profile-edit/index' })
}
</script>

<style scoped>
.profile-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-top: var(--spacing-md); padding-bottom: env(safe-area-inset-bottom); }

/* Hero 用户卡（纯白卡 + 主题色点缀；头像 + 昵称 + ID/认证 + 右侧 >，整卡点击进个人信息页；无渐变） */
.user-card {
  display: flex; flex-direction: column; gap: var(--spacing-md);
  margin: var(--spacing-md) var(--spacing-md) var(--spacing-sm);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  /* Apple highlight 按压：背景微变而非缩放（列表行卡规范） */
  transition: background-color 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.user-card:active { background-color: var(--bg-soft); }
.user-card-head { display: flex; align-items: center; gap: var(--spacing-md); }
.avatar-wrap { flex-shrink: 0; }
.avatar { width: 112rpx; height: 112rpx; border-radius: 24rpx; background: var(--bg-page); }
.avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.user-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-sm); }
/* Apple Design Typography：昵称加大（title 级 800 + 负 tracking），强化身份层级 */
.nickname { font-size: var(--font-h2); font-weight: var(--weight-heavy); color: var(--text-primary); letter-spacing: var(--tracking-h2); }
.meta-line { display: flex; align-items: center; gap: var(--spacing-sm); }
.user-id { font-size: var(--font-aux); color: var(--text-tertiary); }
.card-arrow { flex-shrink: 0; }

/* 我的入口（白卡 + 行布局 + 右箭头；按压背景微变） */
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
  transition: background-color 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.entry-row:not(:last-child) { border-bottom: 1rpx solid var(--border-color); }
.entry-row.pressed { background-color: var(--bg-soft); }
.entry-icon { flex-shrink: 0; }
.entry-label { flex: 1; min-width: 0; font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); }
.entry-arrow { flex-shrink: 0; }

/* 退出登录（警示红，独立分组 + 上间距；未登录置灰） */
.logout-group { margin-top: var(--spacing-md); }
.logout-row {
  justify-content: center;
  -webkit-tap-highlight-color: transparent;
}
.logout-label {
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--color-error);
}
.logout-row.pressed { background-color: var(--bg-soft); }
.logout-row.disabled { opacity: 0.4; }
.logout-row.disabled .logout-label { color: var(--text-tertiary); }

@media (prefers-reduced-motion: reduce) {
  .user-card, .entry-row { transition: none; }
}
</style>
