<template>
  <view class="page profile-page">
    <Header title="我的" />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 未登录态：居中卡片式 AuthForm（§1.4，登录/注册/找回同卡 + 账号密码/邮箱验证码双路） -->
      <template v-if="!isLoggedIn">
        <AuthForm />
      </template>

      <!-- 已登录态：用户信息卡 + 统计 + 菜单（主页不渲染 Tab） -->
      <template v-else>
        <!-- 用户卡（含头像 + 昵称 + 统计三宫格 + 我要贡献主操作入口） -->
        <view class="user-card enter-up" :style="{ '--enter-i': 0 }">
          <view class="user-card-head">
            <view class="avatar-wrap" @tap="handleEditAvatar">
            <image v-if="userInfo?.avatar" :src="getImageUrl(userInfo.avatar)" class="avatar" />
            <view v-else class="avatar avatar-empty">
              <IconSvg name="user" :size="48" color="var(--text-tertiary)" />
            </view>
          </view>
          <view class="user-meta">
            <view class="nickname-row" @tap="handleEditNickname">
              <text class="nickname">{{ userInfo?.nickname || '未知用户' }}</text>
              <IconSvg name="edit" :size="24" color="var(--text-tertiary)" class="nickname-edit" />
            </view>
            <StatusBadge v-if="userInfo?.role" :role="userInfo.role === 'admin' ? 'admin' : 'student'" />
          </view>
          </view>

          <!-- 统计三宫格（评价 / 已发布 / 待审核），融合进 user-card -->
          <StatsRow
            v-if="!userStore.statsLoading"
            class="user-stats"
            :review-count="userStore.userStats.reviewCount ?? 0"
            :published-count="userStore.userStats.publishedCount ?? 0"
            :pending-count="userStore.userStats.pendingCount ?? 0"
            @tap="onStatsTap"
          />
          <view v-else class="stats-skeleton">
            <view v-for="n in 3" :key="n" class="sk-cell">
              <view class="sk-value" />
              <view class="sk-label" />
            </view>
          </view>

          <!-- 我要贡献（卡片主操作，spring + scale(0.97) 由 AppButton 处理） -->
          <AppButton text="+ 我要贡献" type="primary" class="contribute-cta" @click="contributeOpen = true" />
        </view>

        <!-- 菜单组（SettingGroup + SettingCell，图标走 IconSvg） -->
        <SettingGroup title="我的" class="enter-up" :style="{ '--enter-i': 1 }">
          <SettingCell icon="comment" label="我的动态" @tap="goToMyMoments" />
          <SettingCell icon="bell" label="消息中心" :badge-count="notifyStore.unreadCount" @tap="goToNotify" />
        </SettingGroup>

        <SettingGroup title="通用" class="enter-up" :style="{ '--enter-i': 2 }">
          <SettingCell icon="list" label="我的发布" hint="我的发布 / 我的贡献" @tap="goToMessagesServices" />
          <SettingCell icon="contact" label="意见反馈" hint="建议/Bug反馈" @tap="goToFeedback" />
          <SettingCell icon="settings" label="设置" @tap="goToSettings" />
        </SettingGroup>

        <view class="version-row">
          <text class="version-text">食在交大 v1.0.0</text>
        </view>
      </template>
    </scroll-view>

    <CustomTabBar current="/pages/profile/index" />

    <!-- 我要贡献 Sheet（ContributeSheet，spring 0.8/0.3 + ic-close） -->
    <ContributeSheet :open="contributeOpen" @update:open="contributeOpen = $event" @pick="onContributePick" />

    <!-- 申请下架/纠错 Sheet（ApplySheet 跨页共用，profile 自由申请） -->
    <ApplySheet :open="applyOpen" @update:open="applyOpen = $event" @submitted="onApplySubmitted" />

    <!-- 昵称编辑 Sheet（NicknameSheet，复用 ContributeSheet 视觉语言 + spring 0.3 进场） -->
    <NicknameSheet :open="nicknameOpen" :value="userInfo?.nickname" @update:open="nicknameOpen = $event" @confirm="confirmEditNickname" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import IconSvg from '@/components/IconSvg.vue'
import AuthForm from '@/components/AuthForm.vue'
import StatsRow from '@/components/StatsRow.vue'
import ContributeSheet from '@/components/ContributeSheet.vue'
import ApplySheet from '@/components/ApplySheet.vue'
import NicknameSheet from '@/components/NicknameSheet.vue'
import SettingGroup from '@/components/SettingGroup.vue'
import SettingCell from '@/components/SettingCell.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useUserStore } from '@/stores/user'
import { useNotifyStore } from '@/stores/notify'
import { getImageUrl } from '@/utils/image'
import { uploadImage } from '@/api/upload'

const userStore = useUserStore()
const notifyStore = useNotifyStore()
const userInfo = computed(() => userStore.userInfo)
const isLoggedIn = computed(() => userStore.isLoggedIn())

const contributeOpen = ref(false)
const applyOpen = ref(false)
const nicknameOpen = ref(false)

function onContributePick(key: string) {
  contributeOpen.value = false
  if (key === 'publishDish') uni.navigateTo({ url: '/pages/profile/publish-dish' })
  else if (key === 'submitStall') uni.navigateTo({ url: '/pages/profile/submit-stall' })
  else if (key === 'submitCanteen') uni.navigateTo({ url: '/pages/profile/submit-stall?type=canteen' })
  else if (key === 'apply') applyOpen.value = true
}

function onApplySubmitted() {
  setTimeout(() => uni.navigateTo({ url: '/pages/profile/messages-services/index' }), 400)
}

function handleEditAvatar() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const tempPath = res.tempFilePaths[0]
      try {
        const url = await uploadImage(tempPath)
        await userStore.updateProfile({ avatar: url })
        uni.showToast({ title: '头像已更新', icon: 'success' })
      } catch {
        uni.showToast({ title: '更新失败', icon: 'none' })
      }
    },
  })
}

function handleEditNickname() {
  nicknameOpen.value = true
}

async function confirmEditNickname(name: string) {
  const trimmed = name.trim()
  if (!trimmed) {
    uni.showToast({ title: '昵称不能为空', icon: 'none' })
    return
  }
  try {
    await userStore.updateProfile({ nickname: trimmed })
    nicknameOpen.value = false
    uni.showToast({ title: '昵称已更新', icon: 'success' })
  } catch {
    uni.showToast({ title: '更新失败', icon: 'none' })
  }
}

function goToReviews() {
  uni.navigateTo({ url: '/pages/pages-detail/review-list' })
}
function onStatsTap(key: 'review' | 'published' | 'pending') {
  if (key === 'review') goToReviews()
  else if (key === 'published') uni.navigateTo({ url: '/pages/profile/messages-services/index?tab=published' })
  else if (key === 'pending') uni.navigateTo({ url: '/pages/profile/messages-services/index?tab=pending' })
}
function goToMyMoments() {
  uni.navigateTo({ url: '/pages/my-moments/index' })
}
function goToNotify() {
  uni.navigateTo({ url: '/pages/notify/index' })
}
function goToMessagesServices() {
  uni.navigateTo({ url: '/pages/profile/messages-services/index' })
}
function goToFeedback() {
  uni.navigateTo({ url: '/pages/feedback/index' })
}
function goToSettings() {
  uni.navigateTo({ url: '/pages/settings/index' })
}

onMounted(() => {
  if (userStore.isLoggedIn()) {
    userStore.fetchStats()
    notifyStore.fetchUnread()
  }
})
</script>

<style scoped>
.profile-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom)); }

/* 用户卡（§1.4：头像圆角正方形 16rpx，无头像兜底 ic-user；内部竖向排列，统计与入口融合） */
.user-card { display: flex; flex-direction: column; gap: var(--spacing-md); margin: var(--spacing-md) var(--spacing-md) var(--spacing-sm); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); }
.user-card-head { display: flex; align-items: center; gap: var(--spacing-md); }
.avatar-wrap { flex-shrink: 0; transition: var(--press-transition); -webkit-tap-highlight-color: transparent; }
.avatar-wrap:active { transform: scale(var(--press-scale)); }
.avatar { width: 96rpx; height: 96rpx; border-radius: var(--radius-card); background: var(--bg-page); }
.avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.user-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.nickname-row { display: flex; align-items: center; gap: var(--spacing-xs); transition: var(--press-transition); -webkit-tap-highlight-color: transparent; }
.nickname-row:active { transform: scale(var(--press-scale)); }
.nickname { font-size: var(--font-subtitle); font-weight: 700; color: var(--text-primary); }
.nickname-edit { flex-shrink: 0; }

/* 我要贡献（卡片主操作入口，落在统计三宫格下方） */
.contribute-cta { margin-top: var(--spacing-md); }

/* 统计三宫格（融合进 user-card，扁平化：去掉内层卡片阴影/背景，与卡片等宽） */
.user-stats { width: 100%; margin-top: var(--spacing-md); padding-top: var(--spacing-md); border-top: 2rpx solid var(--border-color); }
.user-card :deep(.stat-cell) { background: transparent; box-shadow: none; }
.user-card :deep(.stats-row) { gap: 0; }

.version-row { text-align: center; margin: 0 var(--spacing-md); padding: var(--spacing-lg) 0 var(--spacing-md); }
.version-text { display: block; font-size: 22rpx; font-weight: 500; color: var(--text-tertiary); }

/* 统计骨架（加载态占位，避免数字跳动） */
.stats-skeleton { display: flex; gap: var(--spacing-sm); margin-top: var(--spacing-md); padding-top: var(--spacing-md); border-top: 2rpx solid var(--border-color); }
.sk-cell { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 10rpx; padding: var(--spacing-xs) 0; }
.sk-value { width: 56rpx; height: 34rpx; border-radius: 8rpx; background: var(--bg-soft); }
.sk-label { width: 72rpx; height: 22rpx; border-radius: 6rpx; background: var(--bg-soft); }
.sk-value, .sk-label { animation: sk-pulse 1.2s ease-in-out infinite; }
@keyframes sk-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.45; } }
</style>
