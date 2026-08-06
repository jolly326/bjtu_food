<template>
  <view class="page profile-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="我的" />

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

      <!-- 4 项功能网格（统一主色软底大图标：动态 / 反馈中心 / 消息 / 评价；未登录点击时引导认证） -->
      <view class="grid-menu enter-up" :style="{ '--enter-i': 1 }">
        <view
          v-for="g in gridItems"
          :key="g.key"
          class="grid-item"
          :class="{ pressed: pressedKey === g.key }"
          @touchstart="pressedKey = g.key"
          @touchend="pressedKey = ''"
          @touchcancel="pressedKey = ''"
          @mousedown="pressedKey = g.key"
          @mouseup="pressedKey = ''"
          @mouseleave="pressedKey = ''"
          @tap="g.action"
        >
          <view class="grid-icon">
            <IconSvg :name="g.icon" :size="42" color="var(--color-primary)" />
            <view v-if="isLoggedIn && g.key === 'notify' && notifyStore.unreadCount > 0" class="grid-badge">
              {{ notifyStore.unreadCount > 99 ? '99+' : notifyStore.unreadCount }}
            </view>
          </view>
          <text class="grid-label">{{ g.label }}</text>
        </view>
      </view>

      <!-- 设置（单列表：深色模式/关于/隐私/缓存；游客也可用，无需认证） -->
      <SettingGroup class="enter-up" :style="{ '--enter-i': 2 }">
        <SettingCell label="深色模式" icon="settings" :switch="true" :switch-value="theme.isDark" @select="theme.toggle()" />
        <SettingCell label="关于食在交大" icon="logo" @select="goAbout" />
        <SettingCell label="隐私政策" icon="lock" @select="goPrivacy" />
        <SettingCell label="清除缓存" icon="delete" @select="clearCache" />
      </SettingGroup>

      <!-- 账号注销（危险操作，仅登录后可见） -->
      <SettingGroup v-if="isLoggedIn" :style="{ '--enter-i': 3 }">
        <SettingCell label="账号注销" icon="delete" danger @select="goCancelAccount" />
      </SettingGroup>

      <view class="version-row">
        <text class="version-text">食在交大 v1.0.0</text>
      </view>
    </scroll-view>

    <!-- 认证弹层：游客点击需认证功能时弹出 -->
    <AuthSheet />

    <CustomTabBar current="/pages/profile/index" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import IconSvg from '@/components/IconSvg.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import SettingGroup from '@/components/SettingGroup.vue'
import SettingCell from '@/components/SettingCell.vue'
import { useUserStore } from '@/stores/user'
import { useNotifyStore } from '@/stores/notify'
import { useAuthSheetStore } from '@/stores/authSheet'
import { useThemeStore } from '@/stores/theme'
import { getImageUrl } from '@/utils/image'
import { deleteAccount } from '@/api/user'

const userStore = useUserStore()
const notifyStore = useNotifyStore()
const authSheetStore = useAuthSheetStore()
const theme = useThemeStore()
const userInfo = computed(() => userStore.userInfo)
const isLoggedIn = computed(() => userStore.isLoggedIn())
const pressedKey = ref('')

function openMessage() {
  uni.navigateTo({ url: '/pages/profile/messages/index' })
}

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

/** 4 项功能网格（统一主色软底大图标，克制；未登录点击时引导认证） */
const gridItems = [
  { key: 'moments', icon: 'comment', label: '我的动态', action: () => requireAuth(() => uni.navigateTo({ url: '/pages/pages-user/my-moments/index' })) },
  // 反馈不登录也可用：游客直接进提交页；登录用户进完整反馈中心（含我的反馈记录）
  { key: 'feedback', icon: 'contact', label: '反馈中心', action: () => uni.navigateTo({ url: userStore.isLoggedIn() ? '/pages/profile/messages-services/index' : '/pages/feedback/index' }) },
  { key: 'notify', icon: 'bell', label: '消息中心', action: () => requireAuth(openMessage) },
  { key: 'reviews', icon: 'star', label: '我的评价', action: () => requireAuth(() => uni.navigateTo({ url: '/pages/pages-user/my-reviews/index' })) },
]

function goProfileEdit() {
  uni.navigateTo({ url: '/pages/pages-user/profile-edit/index' })
}

// ── 设置（内嵌分组）──
function goAbout() {
  uni.showModal({
    title: '关于食在交大',
    content: '食在交大是面向北京交通大学学生的校园美食分享、评价与社交内容平台。发现食堂美食、分享用餐体验。',
    showCancel: false,
  })
}

function goPrivacy() {
  uni.showModal({
    title: '隐私政策',
    content: '我们仅收集必要的账号与登录信息用于提供服务。您的浏览足迹、动态与收藏仅用于优化你的个性化体验，不会向第三方泄露。',
    showCancel: false,
  })
}

function clearCache() {
  uni.showModal({
    title: '清除缓存',
    content: '确定清除本地缓存吗？不会删除你的账号数据。',
    success: (res) => {
      if (res.confirm) {
        uni.clearStorageSync()
        userStore.restoreFromCache()
        uni.showToast({ title: '缓存已清除', icon: 'none' })
      }
    },
  })
}

function goCancelAccount() {
  uni.showModal({
    title: '账号注销',
    content: '注销后你的菜品、动态、评价等数据将被删除且不可恢复，确定要继续吗？',
    confirmText: '确认注销',
    confirmColor: '#e54d42',
    success: (res) => {
      if (res.confirm) doDeleteAccount()
    },
  })
}

async function doDeleteAccount() {
  try {
    await deleteAccount()
    uni.removeStorageSync('token')
    uni.removeStorageSync('userInfo')
    uni.showToast({ title: '账号已注销', icon: 'none' })
    setTimeout(() => { uni.reLaunch({ url: '/pages/profile/index' }) }, 600)
  } catch (e: any) {
    uni.showToast({ title: e.message || '注销失败', icon: 'none' })
  }
}

onMounted(() => {
  if (userStore.isLoggedIn()) {
    notifyStore.fetchUnread()
  }
})
// 从消息中心页返回时刷新未读角标
onShow(() => {
  if (userStore.isLoggedIn()) {
    notifyStore.fetchUnread()
  }
})
</script>

<style scoped>
.profile-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom)); }

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

/* 4 项功能网格（4 列：纯主题色图标 + 文字，无背景块；卡片间距统一 --spacing-sm） */
.grid-menu {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin: var(--spacing-sm) var(--spacing-md) var(--spacing-sm);
  padding: var(--spacing-lg) var(--spacing-sm);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.grid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-xs) 0;
  transition: transform 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.grid-item.pressed { transform: scale(var(--press-scale)); }
.grid-icon {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}
.grid-label { font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--text-primary); line-height: 1.2; }
.grid-badge {
  position: absolute; top: -10rpx; right: -16rpx;
  min-width: 32rpx; height: 32rpx; padding: 0 8rpx; border-radius: 999rpx;
  background: var(--color-error); color: var(--text-white);
  font-size: 18rpx; font-weight: var(--weight-semibold); line-height: 32rpx; text-align: center;
  box-sizing: border-box;
}

.version-row { text-align: center; margin: 0 var(--spacing-md); padding: var(--spacing-lg) 0 var(--spacing-md); }
.version-text { display: block; font-size: var(--font-aux); font-weight: var(--weight-medium); color: var(--text-tertiary); }

@media (prefers-reduced-motion: reduce) {
  .user-card, .grid-item { transition: none; }
}
</style>
