<template>
  <view class="page auth-page">
    <Header title="身份认证" @back="leaveWithoutVerify" />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 表单错误（role=alert 即时播报，点击即清） -->
      <view v-if="formError" class="form-error" role="alert" aria-live="assertive" @tap="clearError">
        <text class="form-error-text">{{ formError }}</text>
      </view>

      <view class="group-card">
        <view class="input-field">
          <input
            :value="form.username"
            class="input-control"
            type="number"
            placeholder="学号"
            aria-label="学号"
            :aria-invalid="formError ? 'true' : 'false'"
            @input="onUsernameInput"
            @blur="onUsernameBlur"
          />
        </view>
        <view class="input-field">
          <input
            :value="form.code"
            class="input-control"
            placeholder="邮箱验证码"
            aria-label="邮箱验证码"
            :aria-invalid="formError ? 'true' : 'false'"
            @input="onCodeInput"
          />
          <text
            class="code-action"
            :class="{ disabled: !codeActionEnabled }"
            role="button"
            :aria-label="codeActionLabel"
            :aria-disabled="codeActionEnabled ? 'false' : 'true'"
            :aria-busy="sendingCode ? 'true' : 'false'"
            @tap="sendCode"
          >{{ codeButtonText }}</text>
        </view>
        <view v-if="usernameValid" class="email-hint">验证码将发送至 {{ deriveCampusEmail(form.username.trim()) }}</view>
      </view>

      <view
        class="primary-action"
        :class="{ disabled: !submitEnabled }"
        role="button"
        aria-label="认证"
        :aria-disabled="submitEnabled ? 'false' : 'true'"
        :aria-busy="isBusy ? 'true' : 'false'"
        @tap="submit"
      >
        <text class="primary-action-text">{{ primaryText }}</text>
      </view>

      <view class="form-note-wrap">
        <text class="form-note">{{ NOTE_SUBTITLE }}</text>
      </view>

      <view class="bottom-note">
        <IconSvg name="lock" :size="24" :color="COLOR_MAP['text-tertiary']" />
        <text class="note-text">{{ NOTE_PRIVACY }}</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 身份认证独立页：
 * 学号 + 邮箱验证码两字段表单；发码冷却由 authStore 持有（跨进出页面持久，前端不辅助绕过 60s 冷却）。
 * 认证成功（bindEmail 落库）→ Toast → 返回原页；若进入本页前记录了待办（requireAuth 守卫），
 * 原页 onShow 经 authStore.consumePending() 续接（如重新打开写评价表单）。
 * 未完成认证离开本页（Header 返回 / 手势返回）→ 清除待办，避免过期动作误执行。
 */
import { ref, computed } from 'vue'
import { onUnload } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import Header from '@/components/AppHeader.vue'
import IconSvg from '@/components/IconSvg.vue'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import { sendEmailCode, deriveCampusEmail } from '@/api/user'
import { COLOR_MAP } from '@/theme/tokens'

const authStore = useAuthStore()
const userStore = useUserStore()
// 冷却必须用 storeToRefs 保持响应性（跨页面持有，重进页面续接剩余秒数）
const { codeCooldown } = storeToRefs(authStore)

// ---- 认证表单状态 ----
const form = ref({ username: '', code: '' })
const formError = ref('')
function setError(msg: string) { formError.value = msg }
function clearError() { formError.value = '' }

/** 文案常量（spec 契约基线） */
const NOTE_SUBTITLE = '验证码将发送至你的校园邮箱，完成身份认证后即可使用评价等功能'
const NOTE_PRIVACY = '仅用于核验本校校园身份，认证后将与当前微信账号绑定，不会用于其他用途'

/** 学号弱校验口径：去除首尾空白后须为非空纯数字（不限定位数） */
const usernameValid = computed(() => /^\d+$/.test(form.value.username.trim()))
/** 发码钮可用性：学号合法 && 非冷却 && 无发送在途 */
const codeActionEnabled = computed(() => usernameValid.value && codeCooldown.value === 0 && !sendingCode.value)
/** 认证钮可用性：学号合法非空 && 验证码非空 && 无请求在途 */
const submitEnabled = computed(() => usernameValid.value && form.value.code.trim() !== '' && !isBusy.value)

const isBusy = ref(false)
const sendingCode = ref(false)
const primaryText = computed(() => (isBusy.value ? '认证中…' : '认证'))
/** 发码钮文案：发送在途 / 倒计时 / 常态三分支 */
const codeButtonText = computed(() =>
  sendingCode.value ? '发送中…' : codeCooldown.value > 0 ? `${codeCooldown.value}s 后重发` : '获取验证码',
)
const codeActionLabel = computed(() =>
  sendingCode.value ? '发送中' : codeCooldown.value > 0 ? `${codeCooldown.value}s 后重发验证码` : '获取验证码',
)

/** 学号 @input 净化：仅保留数字并去空白，保证「纯数字」判定可靠（粘贴含空格/小数点也会被净化） */
function onUsernameInput(e: any) {
  form.value.username = String(e.detail?.value ?? '').replace(/\D/g, '')
  clearError()
}
/** 学号失焦浅提示：空 / 含非数字才提示原因，不发起任何请求 */
function onUsernameBlur() {
  const v = form.value.username.trim()
  if (!v) setError('请输入学号')
  else if (!/^\d+$/.test(v)) setError('学号需为纯数字')
}
function onCodeInput(e: any) {
  form.value.code = String(e.detail?.value ?? '')
  clearError()
}

async function sendCode() {
  // 前置状态锁：不满足可用性时静默返回（按钮已置灰，不产生错误提示）
  if (!codeActionEnabled.value) return
  const username = form.value.username.trim()
  clearError()
  sendingCode.value = true
  try {
    await sendEmailCode(username)
    uni.showToast({ title: '验证码已发送', icon: 'success' })
    authStore.startCooldown()
  } catch (e: any) { setError(e.message || '验证码发送失败') } finally { sendingCode.value = false }
}

/** 认证成功标记：区分「完成认证返回」与「中途放弃」（决定 onUnload 是否清待办） */
let verified = false

async function submit() {
  // 前置状态锁：任一条件不满足（字段空/学号非法/在途）静默返回
  if (!submitEnabled.value) return
  clearError()
  isBusy.value = true
  try {
    await userStore.verifyEmail(form.value.code.trim())
    verified = true
    uni.showToast({ title: '认证成功', icon: 'success' })
    // 返回原页：待办由原页 onShow 经 consumePending 续接
    setTimeout(() => uni.navigateBack(), 600)
  } catch (e: any) { setError(e.message || '认证失败') } finally { isBusy.value = false }
}

/** 未完成认证即离开（Header 返回）——与手势返回同语义，onUnload 统一清待办 */
function leaveWithoutVerify() {
  uni.navigateBack()
}

onUnload(() => {
  if (!verified) authStore.clearPending()
})
</script>

<style scoped>
.auth-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; background: var(--bg-page); }
.scroll-wrap { flex: 1; min-height: 0; overflow-y: auto; padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-md) + var(--spacing-lg)); box-sizing: border-box; }

.form-error { margin-bottom: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md); background: var(--color-error-soft); border-radius: var(--radius-card); }
.form-error-text { font-size: var(--font-aux); color: var(--color-error); font-weight: var(--weight-semibold); }

/* 输入项：与全站表单同款浅底无边框字段 */
.group-card { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.input-field { min-height: 92rpx; display: flex; align-items: center; gap: var(--spacing-sm); padding: 0 var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); box-sizing: border-box; }
.input-control { flex: 1; height: 90rpx; font-size: var(--font-small); color: var(--text-primary); min-width: 0; }
.code-action { flex-shrink: 0; min-width: 154rpx; height: 90rpx; padding: 0 0 0 var(--spacing-sm); display: flex; align-items: center; justify-content: flex-end; color: var(--color-primary-text); font-size: var(--font-small); font-weight: var(--weight-semibold); white-space: nowrap; }
.code-action.disabled { color: var(--text-tertiary); }
.email-hint { padding: 0 var(--spacing-xs); font-size: var(--font-aux); line-height: 1.5; color: var(--text-tertiary); }

/* 主按钮：与全站主操作同款（radius-btn + shadow-bar-primary） */
.primary-action { height: 92rpx; margin-top: var(--spacing-lg); border-radius: var(--radius-btn); background: var(--color-primary); box-shadow: var(--shadow-bar-primary); display: flex; align-items: center; justify-content: center; transition: opacity var(--duration-fast) var(--ease-out); }
.primary-action.disabled { opacity: 0.58; }
.primary-action-text { color: var(--color-on-primary); font-size: var(--font-subtitle); font-weight: var(--weight-bold); }

.form-note-wrap { margin-top: var(--spacing-md); text-align: center; }
.form-note { font-size: var(--font-aux); line-height: 1.5; color: var(--text-tertiary); }

/* 底部安全说明：锁图标 + 简短文案（认证即绑定，不公开传播） */
.bottom-note { display: flex; align-items: center; justify-content: center; gap: var(--spacing-xs); margin-top: var(--spacing-md); }
.note-text { font-size: var(--font-aux); color: var(--text-tertiary); }
</style>
