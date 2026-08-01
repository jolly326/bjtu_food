<template>
  <view class="auth-shell">
    <view class="auth-hero">
      <view class="hero-badge">
        <IconSvg name="logo" :size="56" color="var(--color-primary)" class="hero-logo" />
      </view>
      <text class="hero-title">{{ authTitle }}</text>
      <text class="hero-subtitle">{{ authSubtitle }}</text>
    </view>

    <view class="auth-panel">
      <view v-if="formError" class="form-error" @tap="clearError">
        <IconSvg name="close" :size="28" color="var(--color-error)" class="form-error-icon" />
        <text class="form-error-text">{{ formError }}</text>
      </view>

      <view class="form-head">
        <text class="form-title">{{ modeTitle }}</text>
        <text class="form-note">{{ modeNote }}</text>
      </view>

      <view class="group-card">
        <!-- 账号密码登录 -->
        <template v-if="mode === 'login' && loginType === 'password'">
          <view class="input-field">
            <IconSvg name="user" :size="32" color="var(--text-tertiary)" class="input-icon" />
            <input v-model="loginForm.account" class="input-control" placeholder="账号 / 学号 / 校园邮箱" @input="clearError" />
          </view>
          <view class="input-field">
            <IconSvg name="lock" :size="32" color="var(--text-tertiary)" class="input-icon" />
            <input v-model="loginForm.password" class="input-control" placeholder="密码" password @input="clearError" />
          </view>
        </template>

        <!-- 邮箱验证码登录 -->
        <template v-else-if="mode === 'login' && loginType === 'email'">
          <view class="input-field">
            <IconSvg name="mail" :size="32" color="var(--text-tertiary)" class="input-icon" />
            <input v-model="loginCodeForm.email" class="input-control" placeholder="校园邮箱" @input="clearError" />
          </view>
          <view class="input-field code-field">
            <IconSvg name="lock" :size="32" color="var(--text-tertiary)" class="input-icon" />
            <input v-model="loginCodeForm.code" class="input-control" placeholder="邮箱验证码" @input="clearError" />
            <text class="code-action" :class="{ disabled: codeCountdown > 0 }" @tap="sendCode('login')">{{ codeButtonText }}</text>
          </view>
        </template>

        <!-- 注册 -->
        <template v-else-if="mode === 'register'">
          <view class="input-field">
            <IconSvg name="user" :size="32" color="var(--text-tertiary)" class="input-icon" />
            <input v-model="registerForm.username" class="input-control" placeholder="账号 / 学号" @input="clearError" />
          </view>
          <view class="input-field">
            <IconSvg name="mail" :size="32" color="var(--text-tertiary)" class="input-icon" />
            <input v-model="registerForm.email" class="input-control" placeholder="校园邮箱，如 20240002@bjtu.edu.cn" @input="clearError" />
          </view>
          <view class="input-field">
            <IconSvg name="user" :size="32" color="var(--text-tertiary)" class="input-icon" />
            <input v-model="registerForm.nickname" class="input-control" placeholder="昵称" @input="clearError" />
          </view>
          <view class="input-field">
            <IconSvg name="lock" :size="32" color="var(--text-tertiary)" class="input-icon" />
            <input v-model="registerForm.password" class="input-control" placeholder="设置密码，至少 6 位" password @input="clearError" />
          </view>
          <view class="input-field code-field">
            <IconSvg name="lock" :size="32" color="var(--text-tertiary)" class="input-icon" />
            <input v-model="registerForm.code" class="input-control" placeholder="邮箱验证码" @input="clearError" />
            <text class="code-action" :class="{ disabled: codeCountdown > 0 }" @tap="sendCode('register')">{{ codeButtonText }}</text>
          </view>
        </template>

        <!-- 找回密码 -->
        <template v-else>
          <view class="input-field">
            <IconSvg name="mail" :size="32" color="var(--text-tertiary)" class="input-icon" />
            <input v-model="resetForm.email" class="input-control" placeholder="已绑定的校园邮箱" @input="clearError" />
          </view>
          <view class="input-field">
            <IconSvg name="lock" :size="32" color="var(--text-tertiary)" class="input-icon" />
            <input v-model="resetForm.newPassword" class="input-control" placeholder="新密码，至少 6 位" password @input="clearError" />
          </view>
          <view class="input-field code-field">
            <IconSvg name="lock" :size="32" color="var(--text-tertiary)" class="input-icon" />
            <input v-model="resetForm.code" class="input-control" placeholder="邮箱验证码" @input="clearError" />
            <text class="code-action" :class="{ disabled: codeCountdown > 0 }" @tap="sendCode('reset')">{{ codeButtonText }}</text>
          </view>
        </template>
      </view>

      <view class="row-actions">
        <template v-if="mode === 'login'">
          <text class="link-text" @tap="toggleLoginType">{{ loginType === 'password' ? '验证码登录' : '密码登录' }}</text>
          <text class="link-text" @tap="setMode('reset')">忘记密码</text>
        </template>
        <text v-else class="link-text" @tap="setMode('login')">返回登录</text>
      </view>

      <view
        class="primary-action"
        :class="{ pressed: primaryPressed, disabled: isBusy }"
        @touchstart="primaryPressed = true"
        @touchend="primaryPressed = false"
        @touchcancel="primaryPressed = false"
        @mousedown="primaryPressed = true"
        @mouseup="primaryPressed = false"
        @mouseleave="primaryPressed = false"
        @tap="submit"
      >
        <text class="primary-action-text">{{ primaryText }}</text>
      </view>

      <view class="bottom-prompt" v-if="mode !== 'register'">
        <text class="prompt-muted">{{ mode === 'login' ? '还没有账号？' : '想起来了？' }}</text>
        <text class="prompt-link" @tap="setMode(mode === 'login' ? 'register' : 'login')">
          {{ mode === 'login' ? '使用校园邮箱注册' : '返回登录' }}
        </text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, onUnmounted } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import { useUserStore } from '@/stores/user'
import { sendEmailCode, resetPassword } from '@/api/user'

type Mode = 'login' | 'register' | 'reset'
type LoginType = 'password' | 'email'
type CodePurpose = 'login' | 'register' | 'reset'

const userStore = useUserStore()

const mode = ref<Mode>('login')
const loginType = ref<LoginType>('password')
const codeCountdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null
const primaryPressed = ref(false)

const formError = ref('')
function setError(msg: string) { formError.value = msg }
function clearError() { formError.value = '' }

const loginForm = ref({ account: '', password: '' })
const loginCodeForm = ref({ email: '', code: '' })
const registerForm = ref({ username: '', email: '', nickname: '', password: '', code: '' })
const resetForm = ref({ email: '', newPassword: '', code: '' })

const isBusy = computed(() => userStore.loading)

const authTitle = computed(() => {
  if (mode.value === 'register') return '加入食在交大'
  if (mode.value === 'reset') return '重设你的密码'
  return '食在交大'
})
const authSubtitle = computed(() => {
  if (mode.value === 'register') return '校园邮箱认证，只为校内用户开放'
  if (mode.value === 'reset') return '验证码确认身份后即可设置新密码'
  return '发现食堂美食、分享用餐体验'
})
const modeTitle = computed(() => {
  if (mode.value === 'register') return '创建账号'
  if (mode.value === 'reset') return '找回密码'
  return '欢迎回来'
})
const modeNote = computed(() => {
  if (mode.value === 'register') return '首次注册需要绑定校园邮箱，并设置之后登录使用的账号密码'
  if (mode.value === 'reset') return '通过已绑定的校园邮箱验证码重新设置密码'
  return '使用账号密码登录，发现和评价校园美食'
})
const primaryText = computed(() => {
  if (isBusy.value) return mode.value === 'register' ? '正在注册...' : mode.value === 'reset' ? '正在重置...' : '正在登录...'
  return mode.value === 'register' ? '注册' : mode.value === 'reset' ? '重置密码' : '登录'
})
const codeButtonText = computed(() => codeCountdown.value > 0 ? `${codeCountdown.value}s` : '获取验证码')

function setMode(next: Mode) {
  mode.value = next
  codeCountdown.value = 0
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}
function toggleLoginType() {
  loginType.value = loginType.value === 'password' ? 'email' : 'password'
}

function startCountdown() {
  codeCountdown.value = 60
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    codeCountdown.value -= 1
    if (codeCountdown.value <= 0 && countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
  }, 1000)
}

function getEmailForPurpose(purpose: CodePurpose) {
  if (purpose === 'register') return registerForm.value.email.trim()
  if (purpose === 'reset') return resetForm.value.email.trim()
  return loginCodeForm.value.email.trim()
}
function isCampusEmail(email: string) {
  return /^[^\s@]+@bjtu\.edu\.cn$/i.test(email)
}

async function sendCode(purpose: CodePurpose) {
  if (codeCountdown.value > 0) return
  const email = getEmailForPurpose(purpose)
  if (!isCampusEmail(email)) { setError('请填写 @bjtu.edu.cn 校园邮箱'); return }
  clearError()
  try {
    await sendEmailCode(email, purpose)
    uni.showToast({ title: '验证码已发送', icon: 'success' })
    startCountdown()
  } catch (e: any) { setError(e.message || '验证码发送失败') }
}

async function submit() {
  if (mode.value === 'login') {
    if (loginType.value === 'password') await handlePasswordLogin()
    else await handleEmailLogin()
  } else if (mode.value === 'register') {
    await handleRegister()
  } else {
    await handleResetPassword()
  }
}

async function handlePasswordLogin() {
  if (isBusy.value) return
  if (!loginForm.value.account.trim() || !loginForm.value.password) { setError('请填写账号和密码'); return }
  clearError()
  try {
    await userStore.loginByPassword(loginForm.value.account.trim(), loginForm.value.password)
    uni.showToast({ title: '登录成功', icon: 'success' })
  } catch (e: any) { setError(e.message || '登录失败') }
}

async function handleEmailLogin() {
  if (isBusy.value) return
  if (!isCampusEmail(loginCodeForm.value.email) || !loginCodeForm.value.code.trim()) { setError('请填写校园邮箱和验证码'); return }
  clearError()
  try {
    await userStore.loginByEmailCode(loginCodeForm.value.email.trim(), loginCodeForm.value.code.trim())
    uni.showToast({ title: '登录成功', icon: 'success' })
  } catch (e: any) { setError(e.message || '登录失败') }
}

async function handleRegister() {
  if (isBusy.value) return
  const f = registerForm.value
  if (!f.username.trim() || !f.nickname.trim() || !f.password || !f.code.trim()) { setError('请完整填写注册信息'); return }
  if (!isCampusEmail(f.email)) { setError('请填写 @bjtu.edu.cn 校园邮箱'); return }
  if (f.password.length < 6) { setError('密码至少 6 位'); return }
  clearError()
  try {
    await userStore.register({ username: f.username.trim(), email: f.email.trim(), nickname: f.nickname.trim(), password: f.password, code: f.code.trim() })
    uni.showToast({ title: '注册成功，请登录', icon: 'success' })
    loginForm.value.account = f.username.trim()
    setMode('login'); loginType.value = 'password'
  } catch (e: any) { setError(e.message || '注册失败') }
}

async function handleResetPassword() {
  if (isBusy.value) return
  const f = resetForm.value
  if (!isCampusEmail(f.email) || !f.code.trim() || !f.newPassword) { setError('请完整填写找回密码信息'); return }
  if (f.newPassword.length < 6) { setError('新密码至少 6 位'); return }
  clearError()
  try {
    await resetPassword({ email: f.email.trim(), code: f.code.trim(), newPassword: f.newPassword })
    uni.showToast({ title: '密码已重置，请登录', icon: 'success' })
    loginForm.value.account = f.email.trim()
    setMode('login'); loginType.value = 'password'
  } catch (e: any) { setError(e.message || '重置失败') }
}

onUnmounted(() => { if (countdownTimer) clearInterval(countdownTimer) })
</script>

<style scoped>
.auth-shell { min-height: 100vh; display: flex; flex-direction: column; padding: calc(var(--spacing-lg) + env(safe-area-inset-top)) var(--spacing-lg) calc(var(--spacing-lg) + env(safe-area-inset-bottom)); box-sizing: border-box; }

/* 顶部品牌区：轻量居中，不再用大色块堆叠 */
.auth-hero { display: flex; flex-direction: column; align-items: center; text-align: center; padding: var(--spacing-lg) 0 var(--spacing-md); }
.hero-badge { width: 104rpx; height: 104rpx; border-radius: 30rpx; background: var(--bg-card); box-shadow: var(--shadow-card); display: flex; align-items: center; justify-content: center; margin-bottom: var(--spacing-md); }
.hero-logo { color: var(--color-primary); }
.hero-title { display: block; font-size: var(--font-h1); line-height: 1.15; font-weight: 800; color: var(--text-primary); letter-spacing: -0.02em; }
.hero-subtitle { display: block; margin-top: var(--spacing-xs); font-size: var(--font-aux); line-height: 1.5; color: var(--text-tertiary); max-width: 520rpx; }

/* 表单面板：紧跟品牌区，间距紧凑，无需滚动即可看到输入框 */
.auth-panel { margin-top: var(--spacing-md); padding: var(--spacing-lg); background: var(--bg-card); border-radius: var(--radius-modal); box-shadow: var(--shadow-card); box-sizing: border-box; }
.form-head { margin-bottom: var(--spacing-lg); }
.form-title { display: block; font-size: var(--font-h2); line-height: 1.25; font-weight: 760; color: var(--text-primary); }
.form-note { display: block; margin-top: var(--spacing-xs); font-size: var(--font-aux); line-height: 1.5; color: var(--text-secondary); }
.form-error { display: flex; align-items: center; gap: var(--spacing-xs); margin-bottom: var(--spacing-md); padding: var(--spacing-sm) var(--spacing-md); background: var(--color-error-soft); border-radius: var(--radius-card); -webkit-tap-highlight-color: transparent; }
.form-error-icon { flex-shrink: 0; }
.form-error-text { flex: 1; font-size: var(--font-aux); color: var(--color-error); font-weight: 600; }

/* 输入项：独立 pill 字段，相互留间距，比连续内嵌边框更清爽 */
.group-card { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.input-field { min-height: 92rpx; display: flex; align-items: center; gap: var(--spacing-md); padding: 0 var(--spacing-md); background: var(--bg-soft); border-radius: var(--radius-card); box-sizing: border-box; }
.input-icon { flex-shrink: 0; opacity: 0.6; }
.input-control { flex: 1; height: 90rpx; font-size: 28rpx; color: var(--text-primary); min-width: 0; }
.code-field { padding-right: 0; }
.code-action { min-width: 154rpx; height: 90rpx; padding: 0 var(--spacing-sm); display: flex; align-items: center; justify-content: center; border-left: 2rpx solid var(--border-color); color: var(--color-primary); font-size: 24rpx; font-weight: 650; white-space: nowrap; }
.code-action.disabled { color: var(--text-quaternary); }
.row-actions { display: flex; align-items: center; justify-content: space-between; margin: var(--spacing-md) var(--spacing-xs) 0; }
.link-text { font-size: var(--font-aux); color: var(--text-secondary); }
.primary-action { height: 92rpx; margin-top: var(--spacing-lg); border-radius: var(--radius-btn); background: var(--color-primary); display: flex; align-items: center; justify-content: center; box-shadow: var(--shadow-bar-primary); transition: transform 120ms var(--ease-out), opacity 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.primary-action.pressed { transform: scale(var(--press-scale)); opacity: 0.92; }
.primary-action.disabled { opacity: 0.58; }
.primary-action-text { color: var(--text-white); font-size: var(--font-card); font-weight: 720; }
.bottom-prompt { display: flex; align-items: center; justify-content: center; gap: var(--spacing-xs); margin-top: var(--spacing-md); }
.prompt-muted { font-size: var(--font-aux); color: var(--text-tertiary); }
.prompt-link { font-size: var(--font-aux); color: var(--color-accent); font-weight: 680; }
</style>
