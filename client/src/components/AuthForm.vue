<template>
  <view class="auth-shell">
    <!-- 登录态：简洁标题 + 单表单 -->
    <view v-if="mode === 'login'">
      <view class="form-head">
        <text class="form-title">登录</text>
        <text class="form-note">学号即邮箱，无需单独输入</text>
      </view>

      <view v-if="formError" class="form-error" @tap="clearError">
        <text class="form-error-text">{{ formError }}</text>
      </view>

      <view class="group-card">
        <view class="input-field">
          <input v-model="loginForm.account" class="input-control" placeholder="学号" @input="clearError" />
        </view>
        <view class="input-field">
          <input
            v-model="loginForm.password"
            class="input-control"
            :placeholder="loginType === 'password' ? '密码' : '邮箱验证码'"
            :password="loginType === 'password'"
            @input="clearError"
          />
          <text v-if="loginType === 'code'" class="code-action" :class="{ disabled: codeCountdown > 0 }" @tap="sendCode">{{ codeButtonText }}</text>
        </view>
        <view v-if="loginType === 'code' && loginForm.account.trim()" class="email-hint">验证码将发送至 {{ deriveCampusEmail(loginForm.account) }}</view>
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

      <view class="row-actions">
        <text class="link-text" @tap="toggleLoginType">{{ loginType === 'password' ? '验证码登录' : '密码登录' }}</text>
        <view class="row-right">
          <text class="link-text" @tap="setMode('reset')">忘记密码</text>
        </view>
      </view>
      <view class="bottom-prompt">
        <text class="prompt-muted">没有账号？</text>
        <text class="prompt-link" @tap="setMode('register')">学号注册</text>
      </view>
    </view>

    <!-- 注册：学号/昵称/密码/验证码 -->
    <view v-else-if="mode === 'register'">
      <view class="form-head">
        <text class="form-title">注册</text>
        <text class="form-note">用学号创建账号，验证码发至校园邮箱</text>
      </view>

      <view v-if="formError" class="form-error" @tap="clearError">
        <text class="form-error-text">{{ formError }}</text>
      </view>

      <view class="group-card">
        <view class="input-field">
          <input v-model="registerForm.username" class="input-control" placeholder="学号" @input="clearError" />
        </view>
        <view class="input-field">
          <input v-model="registerForm.nickname" class="input-control" placeholder="昵称" @input="clearError" />
        </view>
        <view class="input-field">
          <input v-model="registerForm.password" class="input-control" placeholder="设置密码，至少 6 位" password @input="clearError" />
        </view>
        <view class="input-field">
          <input v-model="registerForm.code" class="input-control" placeholder="邮箱验证码" @input="clearError" />
          <text class="code-action" :class="{ disabled: codeCountdown > 0 }" @tap="sendCode">{{ codeButtonText }}</text>
        </view>
        <view v-if="registerForm.username.trim()" class="email-hint">验证码将发送至 {{ deriveCampusEmail(registerForm.username) }}</view>
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

      <view class="bottom-prompt">
        <text class="prompt-muted">已有账号？</text>
        <text class="prompt-link" @tap="setMode('login')">返回登录</text>
      </view>
    </view>

    <!-- 找回密码：学号/新密码/验证码 -->
    <view v-else>
      <view class="form-head">
        <text class="form-title">找回密码</text>
        <text class="form-note">验证码发至校园邮箱确认身份</text>
      </view>

      <view v-if="formError" class="form-error" @tap="clearError">
        <text class="form-error-text">{{ formError }}</text>
      </view>

      <view class="group-card">
        <view class="input-field">
          <input v-model="resetForm.username" class="input-control" placeholder="学号" @input="clearError" />
        </view>
        <view class="input-field">
          <input v-model="resetForm.newPassword" class="input-control" placeholder="新密码，至少 6 位" password @input="clearError" />
        </view>
        <view class="input-field">
          <input v-model="resetForm.code" class="input-control" placeholder="邮箱验证码" @input="clearError" />
          <text class="code-action" :class="{ disabled: codeCountdown > 0 }" @tap="sendCode">{{ codeButtonText }}</text>
        </view>
        <view v-if="resetForm.username.trim()" class="email-hint">验证码将发送至 {{ deriveCampusEmail(resetForm.username) }}</view>
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

      <view class="bottom-prompt">
        <text class="prompt-muted">想起来了？</text>
        <text class="prompt-link" @tap="setMode('login')">返回登录</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { sendEmailCode, resetPassword, deriveCampusEmail } from '@/api/user'

type Mode = 'login' | 'register' | 'reset'
type LoginType = 'password' | 'code'

const props = defineProps<{
  /** N08：由 AuthSheet 持有的发码冷却值（关闭弹层卸载时仍可续接，前端不辅助绕过冷却） */
  codeCountdown?: number
}>()
const emit = defineEmits<{
  (e: 'cooldown-change', v: number): void
}>()

const userStore = useUserStore()

const mode = ref<Mode>('login')
const loginType = ref<LoginType>('password')
const codeCountdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null
const primaryPressed = ref(false)

// N08 修复：外部（AuthSheet）传入的冷却值变化时回填到本地，重开弹层后倒计时继续
watch(
  () => props.codeCountdown,
  (v) => {
    if (v && v > 0 && codeCountdown.value <= 0) {
      codeCountdown.value = v
      startCountdown()
    }
  },
)


const formError = ref('')
function setError(msg: string) { formError.value = msg }
function clearError() { formError.value = '' }

const loginForm = ref({ account: '', password: '' })
const registerForm = ref({ username: '', nickname: '', password: '', code: '' })
const resetForm = ref({ username: '', newPassword: '', code: '' })

const isBusy = computed(() => userStore.loading)

const primaryText = computed(() => {
  if (isBusy.value) return mode.value === 'register' ? '注册中...' : mode.value === 'reset' ? '提交中...' : '登录中...'
  return mode.value === 'register' ? '注册' : mode.value === 'reset' ? '重置密码' : '登录'
})
const codeButtonText = computed(() => codeCountdown.value > 0 ? `${codeCountdown.value}s` : '获取验证码')

function setMode(next: Mode) {
  mode.value = next
  codeCountdown.value = 0
  emit('cooldown-change', 0)
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}
function toggleLoginType() {
  loginType.value = loginType.value === 'password' ? 'code' : 'password'
}

function startCountdown() {
  // N08：若已由外部回填（重开弹层续接），不重置为 60，沿用当前剩余值
  if (codeCountdown.value <= 0) codeCountdown.value = 60
  emit('cooldown-change', codeCountdown.value)
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    codeCountdown.value -= 1
    emit('cooldown-change', codeCountdown.value)
    if (codeCountdown.value <= 0 && countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
  }, 1000)
}

/** 取当前模式下的学号（校园邮箱 = {学号}@bjtu.edu.cn，由后端推导） */
function getAccount() {
  if (mode.value === 'register') return registerForm.value.username.trim()
  if (mode.value === 'reset') return resetForm.value.username.trim()
  return loginForm.value.account.trim()
}

async function sendCode() {
  if (codeCountdown.value > 0) return
  const account = getAccount()
  if (!account) { setError('请填写学号'); return }
  clearError()
  try {
    await sendEmailCode(account, mode.value)
    uni.showToast({ title: '验证码已发送', icon: 'success' })
    startCountdown()
  } catch (e: any) { setError(e.message || '验证码发送失败') }
}

async function submit() {
  if (mode.value === 'login') {
    if (loginType.value === 'password') await handlePasswordLogin()
    else await handleCodeLogin()
  } else if (mode.value === 'register') {
    await handleRegister()
  } else {
    await handleResetPassword()
  }
}

async function handlePasswordLogin() {
  if (isBusy.value) return
  if (!loginForm.value.account.trim() || !loginForm.value.password) { setError('请填写学号和密码'); return }
  clearError()
  try {
    await userStore.loginByPassword(loginForm.value.account.trim(), loginForm.value.password)
    uni.showToast({ title: '登录成功', icon: 'success' })
  } catch (e: any) { setError(e.message || '登录失败') }
}

async function handleCodeLogin() {
  if (isBusy.value) return
  const f = loginForm.value
  if (!f.account.trim() || !f.password) { setError('请填写学号和验证码'); return }
  clearError()
  try {
    await userStore.loginByEmailCode(f.account.trim(), f.password)
    uni.showToast({ title: '登录成功', icon: 'success' })
  } catch (e: any) { setError(e.message || '登录失败') }
}

async function handleRegister() {
  if (isBusy.value) return
  const f = registerForm.value
  if (!f.username.trim() || !f.nickname.trim() || !f.password || !f.code.trim()) { setError('请完整填写注册信息'); return }
  if (f.password.length < 6) { setError('密码至少 6 位'); return }
  clearError()
  try {
    await userStore.register({ username: f.username.trim(), nickname: f.nickname.trim(), password: f.password, code: f.code.trim() })
    uni.showToast({ title: '注册成功，请登录', icon: 'success' })
    loginForm.value.account = f.username.trim()
    setMode('login'); loginType.value = 'password'
  } catch (e: any) { setError(e.message || '注册失败') }
}

async function handleResetPassword() {
  if (isBusy.value) return
  const f = resetForm.value
  if (!f.username.trim() || !f.code.trim() || !f.newPassword) { setError('请完整填写找回密码信息'); return }
  if (f.newPassword.length < 6) { setError('新密码至少 6 位'); return }
  clearError()
  try {
    await resetPassword({ username: f.username.trim(), code: f.code.trim(), newPassword: f.newPassword })
    uni.showToast({ title: '密码已重置，请登录', icon: 'success' })
    loginForm.value.account = f.username.trim()
    setMode('login'); loginType.value = 'password'
  } catch (e: any) { setError(e.message || '重置失败') }
}

onUnmounted(() => { if (countdownTimer) clearInterval(countdownTimer) })
</script>

<style scoped>
.auth-shell { padding: 0 var(--spacing-xs); box-sizing: border-box; }

/* 表单标题：简洁单行 + 一行小说明 */
.form-head { margin-bottom: var(--spacing-md); }
.form-title { display: block; font-size: var(--font-h3); line-height: 1.2; font-weight: var(--weight-heavy); color: var(--text-primary); }
.form-note { display: block; margin-top: var(--spacing-xs); font-size: var(--font-aux); line-height: 1.5; color: var(--text-tertiary); }

.form-error { margin-bottom: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md); background: var(--color-error-soft); border-radius: var(--radius-card); }
.form-error-text { font-size: var(--font-aux); color: var(--color-error); font-weight: var(--weight-semibold); }

/* 输入项：与全站表单（ApplySheet/fb-sheet）同款浅底无边框字段 */
.group-card { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.input-field { min-height: 92rpx; display: flex; align-items: center; gap: var(--spacing-sm); padding: 0 var(--spacing-md); background: var(--bg-page); border-radius: var(--radius-card); box-sizing: border-box; }
.input-control { flex: 1; height: 90rpx; font-size: var(--font-small); color: var(--text-primary); min-width: 0; }
.code-action { min-width: 154rpx; height: 90rpx; padding: 0 var(--spacing-sm); display: flex; align-items: center; justify-content: center; border-left: 2rpx solid var(--border-color); color: var(--color-primary); font-size: var(--font-small); font-weight: var(--weight-semibold); white-space: nowrap; }
.code-action.disabled { color: var(--text-quaternary); }
.email-hint { padding: 0 var(--spacing-xs); font-size: var(--font-aux); line-height: 1.5; color: var(--text-tertiary); }

/* 主按钮：与全站主操作（sheet-submit-btn）同款（radius-btn + shadow-bar-primary + 按压缩放） */
.primary-action { height: 92rpx; margin-top: var(--spacing-lg); border-radius: var(--radius-btn); background: var(--color-primary); box-shadow: var(--shadow-bar-primary); display: flex; align-items: center; justify-content: center; transition: transform 120ms var(--ease-out), opacity 120ms var(--ease-out); }
.primary-action.pressed { transform: scale(var(--press-scale)); opacity: 0.92; }
.primary-action.disabled { opacity: 0.58; }
.primary-action-text { color: var(--color-on-primary); font-size: var(--font-card); font-weight: var(--weight-bold); }

/* 底部行：次级链接 + 注册/找回入口 */
.row-actions { display: flex; align-items: center; justify-content: space-between; margin-top: var(--spacing-md); }
.link-text { font-size: var(--font-aux); color: var(--text-secondary); }
.bottom-prompt { display: flex; align-items: center; justify-content: center; gap: var(--spacing-xs); margin-top: var(--spacing-sm); }
.prompt-muted { font-size: var(--font-aux); color: var(--text-tertiary); }
.prompt-link { font-size: var(--font-aux); color: var(--color-accent); font-weight: var(--weight-bold); }
</style>
