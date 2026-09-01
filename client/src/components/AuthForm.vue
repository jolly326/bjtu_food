<template>
  <view class="auth-shell">
    <!-- 学号邮箱认证：学号 + 验证码（purpose=verify，无密码/无注册切换，§5.y） -->
    <view class="form-head">
      <text class="form-title">学号邮箱认证</text>
      <text class="form-note">验证码发至校园邮箱，认证后解锁发布 / 评价 / 点赞 / 动态</text>
    </view>

    <view v-if="formError" class="form-error" role="alert" aria-live="assertive" @tap="clearError">
      <text class="form-error-text">{{ formError }}</text>
    </view>

    <view class="group-card">
      <view class="input-field">
        <input
          v-model="form.username"
          class="input-control"
          type="number"
          placeholder="学号"
          aria-label="学号"
          :aria-invalid="formError ? 'true' : 'false'"
          @input="clearError"
        />
      </view>
      <view class="input-field">
        <input
          v-model="form.code"
          class="input-control"
          placeholder="邮箱验证码"
          aria-label="邮箱验证码"
          :aria-invalid="formError ? 'true' : 'false'"
          @input="clearError"
        />
        <text
          class="code-action"
          :class="{ disabled: codeCountdown > 0 }"
          role="button"
          :aria-label="codeCountdown > 0 ? `${codeCountdown}s 后重发验证码` : '获取验证码'"
          :aria-disabled="codeCountdown > 0 ? 'true' : 'false'"
          @tap="sendCode"
        >{{ codeButtonText }}</text>
      </view>
      <view v-if="form.username.trim()" class="email-hint">验证码将发送至 {{ deriveCampusEmail(form.username) }}</view>
    </view>

    <view
      class="primary-action"
      :class="{ pressed: primaryPressed, disabled: isBusy }"
      role="button"
      aria-label="认证"
      :aria-disabled="isBusy ? 'true' : 'false'"
      :aria-busy="isBusy ? 'true' : 'false'"
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

    <view class="bottom-note">
      <IconSvg name="lock" :size="24" color="var(--text-tertiary)" />
      <text class="note-text">仅用于确认校园身份，认证即绑定当前微信</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import { useUserStore } from '@/stores/user'
import { sendEmailCode, deriveCampusEmail } from '@/api/user'

const props = defineProps<{
  /** 由 AuthSheet 持有的发码冷却值（关闭弹层卸载时仍可续接，前端不辅助绕过冷却） */
  codeCountdown?: number
}>()
const emit = defineEmits<{
  (e: 'cooldown-change', v: number): void
}>()

const userStore = useUserStore()

const form = ref({ username: '', code: '' })
const codeCountdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null
const primaryPressed = ref(false)

// 外部（AuthSheet）传入的冷却值变化时回填到本地，重开弹层后倒计时继续
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

/** 发码请求进行中（防连点重复发码） */
const sendingCode = ref(false)
const isBusy = computed(() => userStore.loading)

const primaryText = computed(() => (isBusy.value ? '认证中...' : '认证'))
const codeButtonText = computed(() => (codeCountdown.value > 0 ? `${codeCountdown.value}s` : '获取验证码'))

function startCountdown() {
  // 若已由外部回填（重开弹层续接），不重置为 60，沿用当前剩余值
  if (codeCountdown.value <= 0) codeCountdown.value = 60
  emit('cooldown-change', codeCountdown.value)
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    codeCountdown.value -= 1
    emit('cooldown-change', codeCountdown.value)
    if (codeCountdown.value <= 0 && countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
  }, 1000)
}

async function sendCode() {
  if (codeCountdown.value > 0 || sendingCode.value) return
  const username = form.value.username.trim()
  if (!username) { setError('请填写学号'); return }
  clearError()
  sendingCode.value = true
  try {
    await sendEmailCode(username, 'verify')
    uni.showToast({ title: '验证码已发送', icon: 'success' })
    startCountdown()
  } catch (e: any) { setError(e.message || '验证码发送失败') } finally { sendingCode.value = false }
}

async function submit() {
  if (isBusy.value) return
  if (!form.value.username.trim() || !form.value.code.trim()) { setError('请填写学号和验证码'); return }
  clearError()
  try {
    await userStore.verifyEmail(form.value.code.trim())
    uni.showToast({ title: '认证成功', icon: 'success' })
  } catch (e: any) { setError(e.message || '认证失败') }
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

/* 输入项：与全站表单同款浅底无边框字段 */
.group-card { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.input-field { min-height: 92rpx; display: flex; align-items: center; gap: var(--spacing-sm); padding: 0 var(--spacing-md); background: var(--bg-page); border-radius: var(--radius-card); box-sizing: border-box; }
.input-control { flex: 1; height: 90rpx; font-size: var(--font-small); color: var(--text-primary); min-width: 0; }
.code-action { min-width: 154rpx; height: 90rpx; padding: 0 var(--spacing-sm); display: flex; align-items: center; justify-content: center; border-left: 2rpx solid var(--border-color); color: var(--color-primary); font-size: var(--font-small); font-weight: var(--weight-semibold); white-space: nowrap; }
.code-action.disabled { color: var(--text-quaternary); }
.email-hint { padding: 0 var(--spacing-xs); font-size: var(--font-aux); line-height: 1.5; color: var(--text-tertiary); }

/* 主按钮：与全站主操作同款（radius-btn + shadow-bar-primary + 按压缩放） */
.primary-action { height: 92rpx; margin-top: var(--spacing-lg); border-radius: var(--radius-btn); background: var(--color-primary); box-shadow: var(--shadow-bar-primary); display: flex; align-items: center; justify-content: center; transition: transform var(--duration-fast) var(--ease-out), opacity var(--duration-fast) var(--ease-out); }
.primary-action.pressed { transform: scale(var(--press-scale)); opacity: 0.92; }
.primary-action.disabled { opacity: 0.58; }
.primary-action-text { color: var(--color-on-primary); font-size: var(--font-subtitle); font-weight: var(--weight-bold); }

/* 底部安全说明：锁图标 + 简短文案（认证即绑定，不公开传播） */
.bottom-note { display: flex; align-items: center; justify-content: center; gap: var(--spacing-xs); margin-top: var(--spacing-md); }
.note-text { font-size: var(--font-aux); color: var(--text-tertiary); }
</style>
