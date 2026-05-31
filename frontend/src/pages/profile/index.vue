<template>
  <view class="page profile-page">
    <Header title="我的" />

    <scroll-view class="scroll-wrap" scroll-y>
    <!-- ===== 未登录：登录/注册 ===== -->
    <template v-if="!isLoggedIn">
      <view class="login-wrapper">
        <!-- 品牌区 -->
        <view class="brand-area">
          <image class="brand-logo" src="/static/icons/food.svg" />
          <text class="brand-title">食在交大</text>
          <text class="brand-subtitle">登录后收藏美食，记录评价</text>
        </view>

        <!-- 登录卡片 -->
        <view class="login-card">
          <view class="form-group">
            <view class="input-wrap" :class="{ focused: focusField === 'studentId' }">
              <image class="input-icon" src="/static/icons/user.svg" />
              <input
                v-model="form.studentId"
                class="form-input"
                placeholder="学号（如 20240001）"
                type="number"
                maxlength="10"
                @focus="focusField = 'studentId'"
                @blur="focusField = ''"
              />
            </view>
          </view>

          <view class="form-group">
            <view class="input-wrap code-wrap" :class="{ focused: focusField === 'code' }">
              <image class="input-icon" src="/static/icons/lock.svg" />
              <input
                v-model="form.code"
                class="form-input code-input"
                placeholder="验证码"
                type="number"
                maxlength="6"
                @focus="focusField = 'code'"
                @blur="focusField = ''"
              />
              <text
                class="get-code-btn"
                :class="{ disabled: codeCountdown > 0 }"
                @tap="handleGetCode"
              >
                {{ codeCountdown > 0 ? `${codeCountdown}s` : '获取验证码' }}
              </text>
            </view>
          </view>

          <AppButton
            text="登录 / 注册"
            :loading="userStore.loading"
            margin="0"
            @click="handleLogin"
          />

          <view class="login-hint">
            <text class="hint-line">首次登录将自动创建账号</text>
            <text class="hint-line">验证码发送至 {{ form.studentId || 'xxxx' }}@bjtu.edu.cn</text>
          </view>
        </view>
      </view>
    </template>

    <!-- ===== 已登录：个人中心 ===== -->
    <template v-else>
      <!-- 用户信息 header（简约卡片） -->
      <view class="user-header">
        <view class="user-info-row">
          <view class="avatar-wrap" @tap="handleEditAvatar">
            <image v-if="userInfo?.avatar" :src="getImageUrl(userInfo.avatar)" class="avatar" />
            <view v-else class="avatar avatar-empty">
              <image class="avatar-fallback" src="/static/icons/food.svg" />
            </view>
          </view>
          <view class="user-meta">
            <view class="nickname-row" @tap="handleEditNickname">
              <text class="nickname">{{ userInfo?.nickname || '未知用户' }}</text>
            </view>
            <text class="user-id">学号 {{ userInfo?.id }}</text>
          </view>
        </view>
      </view>

      <!-- 数据统计 -->
      <view class="stats-row">
        <view class="stat-item">
          <text class="stat-value">{{ userStore.userStats.favoriteCount }}</text>
          <text class="stat-label">收藏</text>
        </view>
        <view class="stat-divider" />
        <view class="stat-item">
          <text class="stat-value">{{ userStore.userStats.reviewCount }}</text>
          <text class="stat-label">评价</text>
        </view>
      </view>

      <!-- 功能菜单 -->
      <view class="menu-section">
        <view class="menu-item" @tap="goToReviews">
          <image class="menu-icon" src="/static/icons/star.svg" />
          <text class="menu-label">我的评价</text>
          <image class="menu-arrow" src="/static/icons/right.svg" />
        </view>
        <view class="menu-item" @tap="goToAbout">
          <image class="menu-icon" src="/static/icons/location.svg" />
          <text class="menu-label">关于食在交大</text>
          <image class="menu-arrow" src="/static/icons/right.svg" />
        </view>
      </view>

      <!-- 退出登录 -->
      <view class="logout-wrap">
        <AppButton text="退出登录" type="outline" @click="handleLogout" />
      </view>
    </template>

    <!-- 编辑昵称弹窗 -->
    <view v-if="showNicknameEditor" class="modal-mask" @tap="showNicknameEditor = false">
      <view class="modal-content" @tap.stop>
        <text class="modal-title">修改昵称</text>
        <input
          v-model="editingNickname"
          class="modal-input"
          placeholder="输入新昵称"
          maxlength="20"
          confirm-type="done"
          @confirm="confirmEditNickname"
        />
        <view class="modal-actions">
          <text class="modal-btn modal-btn-cancel" @tap="showNicknameEditor = false">取消</text>
          <text class="modal-btn modal-btn-confirm" @tap="confirmEditNickname">确认</text>
        </view>
      </view>
    </view>

    </scroll-view>
    <CustomTabBar current="/pages/profile/index" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useUserStore } from '@/stores/user'
import { getImageUrl } from '@/utils/image'
import { uploadImage } from '@/api/upload'

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
/** 直接追踪 store 的 token 和 userInfo 属性（不要包装成函数调用，否则失去响应式追踪） */
const isLoggedIn = computed(() => !!userStore.token && !!userStore.userInfo)

// ===== 登录表单 =====
const focusField = ref('')
const codeCountdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null
const form = ref({ studentId: '', code: '' })

function startCountdown() {
  codeCountdown.value = 60
  clearInterval(countdownTimer!)
  countdownTimer = setInterval(() => {
    codeCountdown.value--
    if (codeCountdown.value <= 0) {
      clearInterval(countdownTimer!)
      countdownTimer = null
    }
  }, 1000)
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

function handleGetCode() {
  if (codeCountdown.value > 0 || !form.value.studentId) {
    if (!form.value.studentId) uni.showToast({ title: '请先输入学号', icon: 'none' })
    return
  }
  uni.showToast({ title: '验证码已发送', icon: 'success' })
  startCountdown()
}

async function handleLogin() {
  if (!form.value.studentId || !form.value.code) {
    uni.showToast({ title: '请填写学号和验证码', icon: 'none' })
    return
  }
  try {
    await userStore.login(form.value.code, form.value.studentId)
    uni.showToast({ title: '登录成功', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '登录失败', icon: 'none' })
  }
}

// ===== 用户操作 =====
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
        // 先上传图片获取 URL，再更新头像
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

// ===== 页面跳转 =====
function goToReviews() {
  uni.showToast({ title: '功能开发中', icon: 'none' })
}

function goToAbout() {
  uni.showToast({ title: '食在交大 v1.0', icon: 'none' })
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

// ===== 启动时加载统计数据 =====
onMounted(() => {
  if (userStore.isLoggedIn()) {
    userStore.fetchStats()
  }
})
</script>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-page);
}
.scroll-wrap {
  flex: 1;
  overflow-y: auto;
  padding-bottom: calc(var(--tabbar-height) + 30rpx + env(safe-area-inset-bottom));
}

/* ===================== 未登录区域 ===================== */
.login-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 40rpx 0;
  margin: 0 var(--spacing-md);
}

.brand-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 60rpx;
}
.brand-logo {
  width: 120rpx;
  height: 120rpx;
  margin-bottom: 20rpx;
}
.brand-title {
  font-size: var(--font-h1);
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8rpx;
}
.brand-subtitle {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}

.login-card {
  width: 100%;
  background: var(--bg-card);
  border-radius: var(--radius-modal);
  padding: var(--spacing-xl);
  box-shadow: var(--shadow-card);
}

/* 表单项 */
.form-group {
  margin-bottom: var(--spacing-md);
}
.input-wrap {
  display: flex;
  align-items: center;
  background: var(--bg-page);
  border-radius: var(--radius-card);
  height: 88rpx;
  padding: 0 var(--spacing-md);
  border: 2rpx solid var(--border-color);
  transition: border-color 0.2s;
}
.input-wrap.focused {
  border-color: var(--color-primary);
}
.code-wrap {
  padding-right: 0;
}
.input-icon {
  width: 40rpx;
  height: 40rpx;
  margin-right: var(--spacing-sm);
  flex-shrink: 0;
  opacity: 0.5;
}
.form-input {
  flex: 1;
  height: 100%;
  font-size: var(--font-body);
  color: var(--text-primary);
  background: transparent;
  border: none;
  outline: none;
}
.code-input {
  margin-right: var(--spacing-xs);
}
.get-code-btn {
  font-size: var(--font-small);
  color: var(--color-primary);
  font-weight: 500;
  padding: 0 var(--spacing-md);
  flex-shrink: 0;
  white-space: nowrap;
  height: 100%;
  display: flex;
  align-items: center;
  border-left: 2rpx solid var(--border-color);
}
.get-code-btn.disabled {
  color: var(--text-tertiary);
}

.login-hint {
  margin-top: var(--spacing-md);
  text-align: center;
}
.hint-line {
  display: block;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  line-height: 1.6;
}

/* ===================== 已登录：用户 header（简约卡片） ===================== */
.user-header {
  margin: var(--spacing-md) var(--spacing-md) 0;
}
.user-info-row {
  display: flex;
  align-items: center;
  padding: var(--spacing-md);
  gap: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.avatar-wrap {
  flex-shrink: 0;
}
.avatar {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: var(--bg-page);
}
.avatar-empty {
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-fallback {
  width: 50rpx;
  height: 50rpx;
}

.user-meta {
  flex: 1;
  min-width: 0;
}
.nickname-row {
  margin-bottom: 4rpx;
}
.nickname {
  font-size: var(--font-subtitle);
  font-weight: 600;
  color: var(--text-primary);
}
.user-id {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}

/* ===================== 数据统计 ===================== */
.stats-row {
  display: flex;
  align-items: center;
  background: var(--bg-card);
  margin: var(--spacing-sm) var(--spacing-md) 0;
  padding: var(--spacing-md) 0;
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}
.stat-value {
  font-size: var(--font-h2);
  font-weight: 700;
  color: var(--text-primary);
}
.stat-label {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}
.stat-divider {
  width: 2rpx;
  height: 40rpx;
  background: var(--border-color);
}

/* ===================== 功能菜单 ===================== */
.menu-section {
  background: var(--bg-card);
  margin: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}
.menu-item {
  display: flex;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  gap: var(--spacing-sm);
  border-bottom: 2rpx solid var(--border-color);
}
.menu-item:last-child {
  border-bottom: none;
}
.menu-item:active {
  background: var(--bg-page);
}
.menu-icon {
  width: 40rpx;
  height: 40rpx;
  flex-shrink: 0;
  opacity: 0.6;
}
.menu-label {
  flex: 1;
  font-size: var(--font-body);
  color: var(--text-primary);
}
.menu-arrow {
  width: 28rpx;
  height: 28rpx;
  opacity: 0.3;
  flex-shrink: 0;
}

/* ===================== 退出登录 ===================== */
.logout-wrap {
  padding: var(--spacing-md) var(--spacing-md);
}

/* ===================== 弹窗 ===================== */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.modal-content {
  width: 560rpx;
  background: var(--bg-card);
  border-radius: var(--radius-modal);
  padding: var(--spacing-xl);
}
.modal-title {
  display: block;
  font-size: var(--font-card);
  font-weight: 600;
  color: var(--text-primary);
  text-align: center;
  margin-bottom: 32rpx;
}
.modal-input {
  width: 100%;
  height: 80rpx;
  border: 2rpx solid var(--border-color);
  border-radius: var(--radius-card);
  padding: 0 var(--spacing-md);
  font-size: var(--font-body);
  box-sizing: border-box;
}
.modal-actions {
  display: flex;
  justify-content: space-between;
  margin-top: var(--spacing-lg);
  gap: var(--spacing-sm);
}
.modal-btn {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-card);
  font-size: var(--font-body);
  font-weight: 500;
}
.modal-btn-cancel {
  background: var(--bg-page);
  color: var(--text-secondary);
}
.modal-btn-confirm {
  background: var(--color-primary);
  color: var(--text-white);
}
</style>
