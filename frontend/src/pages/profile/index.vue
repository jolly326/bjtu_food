<template>
  <view class="page profile-page">
    <Header title="我的" />

    <scroll-view class="scroll-wrap" scroll-y>
      <template v-if="!isLoggedIn">
        <view class="auth-shell">
          <view class="auth-hero">
            <view class="hero-badge">
              <image class="hero-logo" src="/static/icons/food.svg" />
            </view>
            <text class="hero-title">{{ authTitle }}</text>
            <text class="hero-subtitle">{{ authSubtitle }}</text>
          </view>

          <view class="auth-panel">
            <template v-if="mode === 'login'">
              <view class="form-head">
                <text class="form-title">欢迎回来</text>
                <text class="form-note">使用账号密码登录，继续收藏和评价校园美食</text>
              </view>

              <view class="input-field">
                <image class="input-icon" src="/static/icons/user.svg" />
                <input v-model="loginForm.account" class="input-control" placeholder="账号 / 学号 / 校园邮箱" />
              </view>

              <view v-if="loginType === 'password'" class="input-field">
                <image class="input-icon" src="/static/icons/lock.svg" />
                <input v-model="loginForm.password" class="input-control" placeholder="密码" password />
              </view>

              <template v-else>
                <view class="input-field">
                  <image class="input-icon" src="/static/icons/user.svg" />
                  <input v-model="loginCodeForm.email" class="input-control" placeholder="校园邮箱" />
                </view>
                <view class="input-field code-field">
                  <image class="input-icon" src="/static/icons/lock.svg" />
                  <input v-model="loginCodeForm.code" class="input-control" placeholder="邮箱验证码" />
                  <text class="code-action" :class="{ disabled: codeCountdown > 0 }" @tap="sendCode('login')">{{ codeButtonText }}</text>
                </view>
              </template>

              <view class="row-actions">
                <text class="link-text" @tap="toggleLoginType">{{ loginType === 'password' ? '验证码登录' : '密码登录' }}</text>
                <text class="link-text" @tap="setMode('reset')">忘记密码</text>
              </view>

              <view class="primary-action" :class="{ disabled: isBusy }" @tap="loginType === 'password' ? handlePasswordLogin() : handleEmailLogin()">
                <text class="primary-action-text">{{ isBusy ? '正在登录...' : '登录' }}</text>
              </view>

              <view class="bottom-prompt">
                <text class="prompt-muted">还没有账号？</text>
                <text class="prompt-link" @tap="setMode('register')">使用校园邮箱注册</text>
              </view>
            </template>

            <template v-else-if="mode === 'register'">
              <view class="form-head">
                <text class="form-title">创建账号</text>
                <text class="form-note">首次注册需要绑定校园邮箱，并设置之后登录使用的账号密码</text>
              </view>

              <view class="input-field">
                <image class="input-icon" src="/static/icons/user.svg" />
                <input v-model="registerForm.username" class="input-control" placeholder="账号 / 学号" />
              </view>
              <view class="input-field">
                <image class="input-icon" src="/static/icons/user.svg" />
                <input v-model="registerForm.email" class="input-control" placeholder="校园邮箱，如 20240002@bjtu.edu.cn" />
              </view>
              <view class="input-field">
                <image class="input-icon" src="/static/icons/food.svg" />
                <input v-model="registerForm.nickname" class="input-control" placeholder="昵称" />
              </view>
              <view class="input-field">
                <image class="input-icon" src="/static/icons/lock.svg" />
                <input v-model="registerForm.password" class="input-control" placeholder="设置密码，至少 6 位" password />
              </view>
              <view class="input-field code-field">
                <image class="input-icon" src="/static/icons/lock.svg" />
                <input v-model="registerForm.code" class="input-control" placeholder="邮箱验证码" />
                <text class="code-action" :class="{ disabled: codeCountdown > 0 }" @tap="sendCode('register')">{{ codeButtonText }}</text>
              </view>

              <view class="primary-action" :class="{ disabled: isBusy }" @tap="handleRegister">
                <text class="primary-action-text">{{ isBusy ? '正在注册...' : '注册' }}</text>
              </view>

              <view class="bottom-prompt">
                <text class="prompt-muted">已有账号？</text>
                <text class="prompt-link" @tap="setMode('login')">返回登录</text>
              </view>
            </template>

            <template v-else>
              <view class="form-head">
                <text class="form-title">找回密码</text>
                <text class="form-note">通过已绑定的校园邮箱验证码重新设置密码</text>
              </view>

              <view class="input-field">
                <image class="input-icon" src="/static/icons/user.svg" />
                <input v-model="resetForm.email" class="input-control" placeholder="已绑定的校园邮箱" />
              </view>
              <view class="input-field">
                <image class="input-icon" src="/static/icons/lock.svg" />
                <input v-model="resetForm.newPassword" class="input-control" placeholder="新密码，至少 6 位" password />
              </view>
              <view class="input-field code-field">
                <image class="input-icon" src="/static/icons/lock.svg" />
                <input v-model="resetForm.code" class="input-control" placeholder="邮箱验证码" />
                <text class="code-action" :class="{ disabled: codeCountdown > 0 }" @tap="sendCode('reset')">{{ codeButtonText }}</text>
              </view>

              <view class="primary-action" :class="{ disabled: isBusy }" @tap="handleResetPassword">
                <text class="primary-action-text">{{ isBusy ? '正在重置...' : '重置密码' }}</text>
              </view>

              <view class="bottom-prompt">
                <text class="prompt-muted">想起来了？</text>
                <text class="prompt-link" @tap="setMode('login')">返回登录</text>
              </view>
            </template>
          </view>
        </view>
      </template>

      <template v-else>
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
              <text class="user-id">用户 ID {{ userInfo?.id }}</text>
            </view>
          </view>
        </view>

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

        <view class="logout-wrap">
          <AppButton text="退出登录" type="outline" @click="handleLogout" />
        </view>
      </template>

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
    </scroll-view>

    <CustomTabBar current="/pages/profile/index" />
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useUserStore } from '@/stores/user'
import { getImageUrl } from '@/utils/image'
import { uploadImage } from '@/api/upload'
import { resetPassword, sendEmailCode } from '@/api/user'

type Mode = 'login' | 'register' | 'reset'
type LoginType = 'password' | 'email'
type CodePurpose = 'login' | 'register' | 'reset'

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
const isLoggedIn = computed(() => !!userStore.token && !!userStore.userInfo)
const isBusy = computed(() => userStore.loading)

const mode = ref<Mode>('login')
const loginType = ref<LoginType>('password')
const codeCountdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const loginForm = reactive({ account: '', password: '' })
const loginCodeForm = reactive({ email: '', code: '' })
const registerForm = reactive({ username: '', email: '', nickname: '', password: '', code: '' })
const resetForm = reactive({ email: '', newPassword: '', code: '' })

const authTitle = computed(() => {
  if (mode.value === 'register') return '加入食在交大'
  if (mode.value === 'reset') return '重设你的密码'
  return '食在交大'
})

const authSubtitle = computed(() => {
  if (mode.value === 'register') return '校园邮箱认证，只为校内用户开放'
  if (mode.value === 'reset') return '验证码确认身份后即可设置新密码'
  return '发现食堂窗口、收藏好菜、记录评价'
})

const codeButtonText = computed(() => codeCountdown.value > 0 ? `${codeCountdown.value}s` : '获取验证码')

function setMode(next: Mode) {
  mode.value = next
  codeCountdown.value = 0
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function toggleLoginType() {
  loginType.value = loginType.value === 'password' ? 'email' : 'password'
}

function startCountdown() {
  codeCountdown.value = 60
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    codeCountdown.value -= 1
    if (codeCountdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

function getEmailForPurpose(purpose: CodePurpose) {
  if (purpose === 'register') return registerForm.email.trim()
  if (purpose === 'reset') return resetForm.email.trim()
  return loginCodeForm.email.trim()
}

function isCampusEmail(email: string) {
  return /^[^\s@]+@bjtu\.edu\.cn$/i.test(email)
}

async function sendCode(purpose: CodePurpose) {
  if (codeCountdown.value > 0) return
  const email = getEmailForPurpose(purpose)
  if (!isCampusEmail(email)) {
    uni.showToast({ title: '请填写 @bjtu.edu.cn 校园邮箱', icon: 'none' })
    return
  }
  try {
    await sendEmailCode(email, purpose)
    uni.showToast({ title: '验证码已发送', icon: 'success' })
    startCountdown()
  } catch (e: any) {
    uni.showToast({ title: e.message || '验证码发送失败', icon: 'none' })
  }
}

async function handlePasswordLogin() {
  console.log('[profile] password login tapped')
  if (isBusy.value) return
  if (!loginForm.account.trim() || !loginForm.password) {
    uni.showToast({ title: '请填写账号和密码', icon: 'none' })
    return
  }
  try {
    await userStore.loginByPassword(loginForm.account.trim(), loginForm.password)
    uni.showToast({ title: '登录成功', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '登录失败', icon: 'none' })
  }
}

async function handleEmailLogin() {
  if (isBusy.value) return
  if (!isCampusEmail(loginCodeForm.email) || !loginCodeForm.code.trim()) {
    uni.showToast({ title: '请填写校园邮箱和验证码', icon: 'none' })
    return
  }
  try {
    await userStore.loginByEmailCode(loginCodeForm.email.trim(), loginCodeForm.code.trim())
    uni.showToast({ title: '登录成功', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '登录失败', icon: 'none' })
  }
}

async function handleRegister() {
  if (isBusy.value) return
  if (!registerForm.username.trim() || !registerForm.nickname.trim() || !registerForm.password || !registerForm.code.trim()) {
    uni.showToast({ title: '请完整填写注册信息', icon: 'none' })
    return
  }
  if (!isCampusEmail(registerForm.email)) {
    uni.showToast({ title: '请填写 @bjtu.edu.cn 校园邮箱', icon: 'none' })
    return
  }
  if (registerForm.password.length < 6) {
    uni.showToast({ title: '密码至少 6 位', icon: 'none' })
    return
  }
  try {
    await userStore.register({
      username: registerForm.username.trim(),
      email: registerForm.email.trim(),
      nickname: registerForm.nickname.trim(),
      password: registerForm.password,
      code: registerForm.code.trim(),
    })
    uni.showToast({ title: '注册成功，请登录', icon: 'success' })
    loginForm.account = registerForm.username.trim()
    setMode('login')
    loginType.value = 'password'
  } catch (e: any) {
    uni.showToast({ title: e.message || '注册失败', icon: 'none' })
  }
}

async function handleResetPassword() {
  if (isBusy.value) return
  if (!isCampusEmail(resetForm.email) || !resetForm.code.trim() || !resetForm.newPassword) {
    uni.showToast({ title: '请完整填写找回密码信息', icon: 'none' })
    return
  }
  if (resetForm.newPassword.length < 6) {
    uni.showToast({ title: '新密码至少 6 位', icon: 'none' })
    return
  }
  try {
    await resetPassword({
      email: resetForm.email.trim(),
      code: resetForm.code.trim(),
      newPassword: resetForm.newPassword,
    })
    uni.showToast({ title: '密码已重置，请登录', icon: 'success' })
    loginForm.account = resetForm.email.trim()
    setMode('login')
    loginType.value = 'password'
  } catch (e: any) {
    uni.showToast({ title: e.message || '重置失败', icon: 'none' })
  }
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

onMounted(() => {
  if (userStore.isLoggedIn()) {
    userStore.fetchStats()
  }
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<style scoped>
.profile-page { display: flex; flex-direction: column; height: 100vh; background: #f6f4ef; }
.scroll-wrap { flex: 1; overflow-y: auto; padding-bottom: calc(var(--tabbar-height) + 30rpx + env(safe-area-inset-bottom)); }
.auth-shell { min-height: calc(100vh - var(--tabbar-height)); padding: 24rpx 30rpx 56rpx; box-sizing: border-box; }
.auth-hero { min-height: 330rpx; padding: 42rpx 36rpx 126rpx; border-radius: 28rpx; background: linear-gradient(135deg, #8b3a2b 0%, #c95c3f 58%, #2f7d72 100%); box-sizing: border-box; color: #fff; }
.hero-badge { width: 96rpx; height: 96rpx; border-radius: 28rpx; background: rgba(255,255,255,.18); display: flex; align-items: center; justify-content: center; margin-bottom: 26rpx; border: 1rpx solid rgba(255,255,255,.24); }
.hero-logo { width: 58rpx; height: 58rpx; filter: brightness(10); }
.hero-title { display: block; font-size: 48rpx; line-height: 1.15; font-weight: 800; color: #fff; letter-spacing: 0; }
.hero-subtitle { display: block; margin-top: 14rpx; font-size: 26rpx; line-height: 1.5; color: rgba(255,255,255,.84); }
.auth-panel { position: relative; margin: -88rpx 12rpx 0; padding: 36rpx 30rpx 32rpx; background: #fff; border-radius: 20rpx; box-shadow: 0 18rpx 54rpx rgba(56, 42, 34, .14); box-sizing: border-box; }
.form-head { margin-bottom: 26rpx; }
.form-title { display: block; font-size: 36rpx; line-height: 1.25; font-weight: 760; color: #2d2521; }
.form-note { display: block; margin-top: 10rpx; font-size: 24rpx; line-height: 1.5; color: #8c817a; }
.input-field { min-height: 92rpx; display: flex; align-items: center; gap: 18rpx; margin-top: 18rpx; padding: 0 22rpx; background: #f8f6f2; border: 2rpx solid #ebe4dd; border-radius: 16rpx; box-sizing: border-box; }
.input-icon { width: 36rpx; height: 36rpx; opacity: .52; flex-shrink: 0; }
.input-control { flex: 1; height: 90rpx; font-size: 28rpx; color: #2d2521; min-width: 0; }
.code-field { padding-right: 0; }
.code-action { min-width: 154rpx; height: 90rpx; padding: 0 22rpx; display: flex; align-items: center; justify-content: center; border-left: 2rpx solid #ebe4dd; color: #8b3a2b; font-size: 24rpx; font-weight: 650; white-space: nowrap; }
.code-action.disabled { color: #aaa19a; }
.row-actions { display: flex; align-items: center; justify-content: space-between; margin: 22rpx 2rpx 0; }
.link-text { font-size: 25rpx; color: #7a6f68; }
.primary-action { height: 92rpx; margin-top: 32rpx; border-radius: 18rpx; background: #8b3a2b; display: flex; align-items: center; justify-content: center; box-shadow: 0 12rpx 28rpx rgba(139,58,43,.22); }
.primary-action:active { transform: scale(.99); opacity: .92; }
.primary-action.disabled { opacity: .58; }
.primary-action-text { color: #fff; font-size: 30rpx; font-weight: 720; }
.bottom-prompt { display: flex; align-items: center; justify-content: center; gap: 8rpx; margin-top: 28rpx; }
.prompt-muted { font-size: 25rpx; color: #9a918b; }
.prompt-link { font-size: 25rpx; color: #2f7d72; font-weight: 680; }
.user-header { margin: var(--spacing-md) var(--spacing-md) 0; }
.user-info-row { display: flex; align-items: center; padding: var(--spacing-md); gap: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); }
.avatar-wrap { flex-shrink: 0; }
.avatar { width: 100rpx; height: 100rpx; border-radius: 50%; background: var(--bg-page); }
.avatar-empty { display: flex; align-items: center; justify-content: center; }
.avatar-fallback { width: 50rpx; height: 50rpx; }
.user-meta { flex: 1; min-width: 0; }
.nickname-row { margin-bottom: 4rpx; }
.nickname { font-size: var(--font-subtitle); font-weight: 600; color: var(--text-primary); }
.user-id { font-size: var(--font-aux); color: var(--text-tertiary); }
.stats-row { display: flex; align-items: center; background: var(--bg-card); margin: var(--spacing-sm) var(--spacing-md) 0; padding: var(--spacing-md) 0; border-radius: var(--radius-card); box-shadow: var(--shadow-card); }
.stat-item { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6rpx; }
.stat-value { font-size: var(--font-h2); font-weight: 700; color: var(--text-primary); }
.stat-label { font-size: var(--font-aux); color: var(--text-tertiary); }
.stat-divider { width: 2rpx; height: 40rpx; background: var(--border-color); }
.menu-section { background: var(--bg-card); margin: var(--spacing-sm) var(--spacing-md); border-radius: var(--radius-card); box-shadow: var(--shadow-card); overflow: hidden; }
.menu-item { display: flex; align-items: center; padding: var(--spacing-md) var(--spacing-lg); gap: var(--spacing-sm); border-bottom: 2rpx solid var(--border-color); }
.menu-item:last-child { border-bottom: none; }
.menu-icon { width: 40rpx; height: 40rpx; flex-shrink: 0; opacity: 0.6; }
.menu-label { flex: 1; font-size: var(--font-body); color: var(--text-primary); }
.menu-arrow { width: 28rpx; height: 28rpx; opacity: 0.3; flex-shrink: 0; }
.logout-wrap { padding: var(--spacing-md); }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal-content { width: 560rpx; background: var(--bg-card); border-radius: var(--radius-modal); padding: var(--spacing-xl); }
.modal-title { display: block; font-size: var(--font-card); font-weight: 600; color: var(--text-primary); text-align: center; margin-bottom: 32rpx; }
.modal-input { width: 100%; height: 80rpx; border: 2rpx solid var(--border-color); border-radius: var(--radius-card); padding: 0 var(--spacing-md); font-size: var(--font-body); box-sizing: border-box; }
.modal-actions { display: flex; justify-content: space-between; margin-top: var(--spacing-lg); gap: var(--spacing-sm); }
.modal-btn { flex: 1; height: 80rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-card); font-size: var(--font-body); font-weight: 500; }
.modal-btn-cancel { background: var(--bg-page); color: var(--text-secondary); }
.modal-btn-confirm { background: var(--color-primary); color: var(--text-white); }
</style>
