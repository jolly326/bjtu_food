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
        <!-- 用户卡（加载中内联骨架） -->
        <view class="user-card enter-up" :style="{ '--enter-i': 0 }">
          <view class="avatar-wrap" @tap="handleEditAvatar">
            <image v-if="userInfo?.avatar" :src="getImageUrl(userInfo.avatar)" class="avatar" />
            <view v-else class="avatar avatar-empty">
              <IconSvg name="user" :size="56" color="var(--text-tertiary)" />
            </view>
          </view>
          <view class="user-meta">
            <view class="nickname-row" @tap="handleEditNickname">
              <text class="nickname">{{ userInfo?.nickname || '未知用户' }}</text>
              <IconSvg name="edit" :size="28" color="var(--text-tertiary)" class="nickname-edit" />
            </view>
            <StatusBadge v-if="userInfo?.role" :role="userInfo.role === 'admin' ? 'admin' : 'student'" />
          </view>
        </view>

        <!-- 统计行（StatsRow 三宫格：评价 / 已发布 / 待审核） -->
        <StatsRow
          class="enter-up stats-row-wrap"
          :style="{ '--enter-i': 1 }"
          :review-count="userStore.userStats.reviewCount ?? 0"
          :published-count="userStore.userStats.publishedCount ?? 0"
          :pending-count="userStore.userStats.pendingCount ?? 0"
          @tap="onStatsTap"
        />

        <!-- 我要贡献入口（与统计同宽，点击弹 ContributeSheet） -->
        <view class="contribute-card enter-up" :style="{ '--enter-i': 2 }" @tap="contributeOpen = true">
          <IconSvg name="plus" :size="40" color="var(--color-primary)" class="contribute-icon" />
          <view class="contribute-body">
            <text class="contribute-title">我要贡献</text>
            <text class="contribute-sub">发布 / 提交 / 纠错</text>
          </view>
          <IconSvg name="arrow-left" :size="28" color="var(--text-tertiary)" class="contribute-arrow" />
        </view>

        <!-- 菜单组（SettingGroup + SettingCell，图标走 IconSvg） -->
        <SettingGroup title="我的内容" class="enter-up" :style="{ '--enter-i': 3 }">
          <SettingCell icon="list" label="我的提交" hint="申请记录" @tap="goToMySubmissions" />
          <SettingCell icon="comment" label="我的动态" @tap="goToMyMoments" />
          <SettingCell icon="star" label="我的评价" @tap="goToReviews" />
          <SettingCell icon="edit" label="我的发布" hint="菜品/档口·审核态" @tap="goToMyPublish" />
        </SettingGroup>

        <SettingGroup title="消息与服务" class="enter-up" :style="{ '--enter-i': 4 }">
          <SettingCell icon="bell" label="消息中心" :badge="notifyStore.unreadCount > 0" @tap="goToNotify" />
          <SettingCell icon="contact" label="意见反馈" hint="建议/Bug反馈" @tap="goToFeedback" />
          <SettingCell icon="settings" label="设置" @tap="goToSettings" />
        </SettingGroup>

        <view class="version-row">
          <text class="version-text">食在交大 v1.0.0</text>
          <text class="version-sub">校园美食分享评价与社交内容平台</text>
        </view>

        <!-- 退出（主入口）+ 账号注销（次级弱化文字入口） -->
        <view class="logout-wrap">
          <AppButton text="退出登录" type="outline" @click="handleLogout" />
        </view>
        <view class="cancel-account" @tap="goCancelAccount">
          <text class="cancel-account-text">注销账号（不可恢复）</text>
        </view>
      </template>
    </scroll-view>

    <CustomTabBar current="/pages/profile/index" />

    <!-- 我要贡献 Sheet（ContributeSheet，spring 0.8/0.3 + ic-close） -->
    <ContributeSheet :open="contributeOpen" @update:open="contributeOpen = $event" @pick="onContributePick" />

    <!-- 申请下架/纠错 Sheet（ApplySheet 跨页共用，profile 自由申请） -->
    <ApplySheet :open="applyOpen" @update:open="applyOpen = $event" @submitted="onApplySubmitted" />

    <!-- 昵称编辑 Modal -->
    <view v-if="showNicknameEditor" class="modal-mask" @tap="showNicknameEditor = false">
      <view class="modal-content" @tap.stop>
        <text class="modal-title">修改昵称</text>
        <input v-model="editingNickname" class="modal-input" placeholder="输入新昵称" maxlength="20" confirm-type="done" @confirm="confirmEditNickname" />
        <view class="modal-actions">
          <text class="modal-btn modal-btn-cancel" @tap="showNicknameEditor = false">取消</text>
          <text class="modal-btn modal-btn-confirm" @tap="confirmEditNickname">确认</text>
        </view>
      </view>
    </view>
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
import SettingGroup from '@/components/SettingGroup.vue'
import SettingCell from '@/components/SettingCell.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useUserStore } from '@/stores/user'
import { useNotifyStore } from '@/stores/notify'
import { getImageUrl } from '@/utils/image'
import { uploadImage } from '@/api/upload'
import { deleteAccount } from '@/api/user'

const userStore = useUserStore()
const notifyStore = useNotifyStore()
const userInfo = computed(() => userStore.userInfo)
const isLoggedIn = computed(() => userStore.isLoggedIn())

const contributeOpen = ref(false)
const applyOpen = ref(false)

function onContributePick(key: string) {
  contributeOpen.value = false
  if (key === 'publishDish') uni.navigateTo({ url: '/pages/profile/publish-dish' })
  else if (key === 'submitStall') uni.navigateTo({ url: '/pages/profile/submit-stall' })
  else if (key === 'submitCanteen') uni.navigateTo({ url: '/pages/profile/submit-stall?type=canteen' })
  else if (key === 'apply') applyOpen.value = true
}

function onApplySubmitted() {
  setTimeout(() => uni.navigateTo({ url: '/pages/profile/my-submissions' }), 400)
}

const showNicknameEditor = ref(false)
const editingNickname = ref('')

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
  editingNickname.value = userInfo.value?.nickname || ''
  showNicknameEditor.value = true
}

async function confirmEditNickname() {
  const name = editingNickname.value.trim()
  if (!name) {
    uni.showToast({ title: '昵称不能为空', icon: 'none' })
    return
  }
  try {
    await userStore.updateProfile({ nickname: name })
    showNicknameEditor.value = false
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
  else if (key === 'published') uni.navigateTo({ url: '/pages/profile/my-publish' })
  else if (key === 'pending') uni.navigateTo({ url: '/pages/profile/my-submissions' })
}
function goToMyPublish() {
  uni.navigateTo({ url: '/pages/profile/my-publish' })
}
function goToMyMoments() {
  uni.navigateTo({ url: '/pages/my-moments/index' })
}
function goToMySubmissions() {
  uni.navigateTo({ url: '/pages/profile/my-submissions' })
}
function goToNotify() {
  uni.navigateTo({ url: '/pages/notify/index' })
}
function goToFeedback() {
  uni.navigateTo({ url: '/pages/feedback/index' })
}
function goToSettings() {
  uni.navigateTo({ url: '/pages/settings/index' })
}

function handleLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    success: (res) => {
      if (res.confirm) userStore.logout()
    },
  })
}

/** 账号注销（≠退出，二次确认 + 不可恢复警示） */
function goCancelAccount() {
  uni.showModal({
    title: '账号注销',
    content: '注销后你的菜品、动态、评价等数据将被永久删除且不可恢复，确定要继续吗？',
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
    userStore.fetchStats()
    notifyStore.fetchUnread()
  }
})
</script>

<style scoped>
.profile-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-bottom: calc(var(--tabbar-height) + var(--spacing-lg) + env(safe-area-inset-bottom)); }

/* 用户卡（§1.4：头像圆角正方形 16rpx，无头像兜底 ic-user） */
.user-card { display: flex; align-items: center; gap: var(--spacing-md); margin: var(--spacing-md); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); }
.avatar-wrap { flex-shrink: 0; }
.avatar { width: 112rpx; height: 112rpx; border-radius: var(--radius-card); background: var(--bg-page); }
.avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.user-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.nickname-row { display: flex; align-items: center; gap: var(--spacing-xs); }
.nickname { font-size: var(--font-subtitle); font-weight: 700; color: var(--text-primary); }
.nickname-edit { flex-shrink: 0; }

/* 我要贡献入口卡（§1.4，与统计同宽） */
.stats-row-wrap { margin: 0 var(--spacing-md); }
.contribute-card { display: flex; align-items: center; gap: var(--spacing-sm); margin: var(--spacing-xs) var(--spacing-md) 0; padding: var(--spacing-xs) var(--spacing-sm); background: var(--color-primary-soft); border-radius: var(--radius-card); box-shadow: var(--shadow-card); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.contribute-card:active { transform: scale(var(--press-scale)); }
.contribute-icon { width: 56rpx; height: 56rpx; border-radius: 50%; background: var(--color-primary); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.contribute-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.contribute-title { font-size: var(--font-body); font-weight: 700; color: var(--text-primary); }
.contribute-sub { font-size: var(--font-aux); color: var(--text-secondary); }
.contribute-arrow { flex-shrink: 0; transform: rotate(180deg); }

.version-row { text-align: center; margin: 0 var(--spacing-md); padding: var(--spacing-xl) 0 var(--spacing-md); }
.version-text { display: block; font-size: 24rpx; font-weight: 600; color: var(--text-tertiary); }
.version-sub { display: block; font-size: 20rpx; color: var(--text-tertiary); margin-top: var(--spacing-xs); }
.logout-wrap { padding: var(--spacing-md) var(--spacing-md) 0; }
.cancel-account { display: flex; justify-content: center; padding: var(--spacing-md) var(--spacing-md) var(--spacing-sm); -webkit-tap-highlight-color: transparent; }
.cancel-account:active { opacity: 0.6; }
.cancel-account-text { font-size: var(--font-aux); color: var(--text-tertiary); }

.modal-mask { position: fixed; inset: 0; background: var(--overlay-scrim); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal-content { width: 560rpx; background: var(--bg-card); border-radius: var(--radius-modal); padding: var(--spacing-xl); }
.modal-title { display: block; font-size: var(--font-card); font-weight: 600; color: var(--text-primary); text-align: center; margin-bottom: var(--spacing-lg); }
.modal-input { width: 100%; height: 80rpx; border: 2rpx solid var(--border-color); border-radius: var(--radius-card); padding: 0 var(--spacing-md); font-size: var(--font-body); box-sizing: border-box; }
.modal-actions { display: flex; justify-content: space-between; margin-top: var(--spacing-lg); gap: var(--spacing-sm); }
.modal-btn { flex: 1; height: 80rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-card); font-size: var(--font-body); font-weight: 500; }
.modal-btn-cancel { background: var(--bg-page); color: var(--text-secondary); }
.modal-btn-confirm { background: var(--color-primary); color: var(--text-white); }
</style>
