<template>
  <!-- 认证表单已并入本文件（原私有子件合入唯一消费者，公共面收窄）。
       N08 修复：v-show 保持 AuthSheet 常驻挂载（由 BaseSheet 根节点 v-show 承接），关闭弹层不清空发码倒计时，
       冷却由本层单一 codeCooldown 持有并持续递减，重开时续接（前端不辅助绕过 60s 冷却）。
       骨架（遮罩/grabber/下拉关闭/安全区/焦点还原）统一复用 BaseSheet，本层承载认证语义与表单。 -->
  <BaseSheet
    :visible="visible"
    z-token="--z-auth"
    scroll-body
    @close="hide"
  >
    <!-- ===== 学号邮箱认证表单（模板并入自原私有子件） ===== -->
    <view class="auth-shell">
      <view class="form-head">
        <text class="form-title">学号邮箱认证</text>
        <text class="form-note">{{ NOTE_SUBTITLE }}</text>
      </view>

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

      <view class="bottom-note">
        <IconSvg name="lock" :size="24" color="var(--text-tertiary)" />
        <text class="note-text">{{ NOTE_PRIVACY }}</text>
      </view>
    </view>
  </BaseSheet>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import BaseSheet from './BaseSheet.vue'
import IconSvg from './IconSvg.vue'
import { useAuthSheetStore } from '@/stores/auth-sheet'
import { useUserStore } from '@/stores/user'
import { sendEmailCode, deriveCampusEmail } from '@/api/user'

const authSheetStore = useAuthSheetStore()
const userStore = useUserStore()

// 必须用 storeToRefs 保持响应性（直接解构会丢失更新，弹层永不显示）
const { visible } = storeToRefs(authSheetStore)

// ---- 发码冷却（N08）：本层单一持有，弹层 v-show 常驻不清空，重开回填续接 ----
const codeCooldown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

// ---- 认证表单状态（并入自原私有子件） ----
const form = ref({ username: '', code: '' })
const formError = ref('')
function setError(msg: string) { formError.value = msg }
function clearError() { formError.value = '' }

/** 发码请求进行中（防连点重复发码） */
const sendingCode = ref(false)
const isBusy = computed(() => userStore.loading)

/** 文案常量（spec 契约基线，去除斜杠缩写） */
// AUD-PM-18：客户端已无「发布菜品」流程（菜品新增走反馈 add 由后台处理），文案对齐真实解锁能力
const NOTE_SUBTITLE = '验证码将发送至你的校园邮箱，完成认证后即可使用评价、点赞等功能'
const NOTE_PRIVACY = '仅用于核验本校校园身份，认证后将与当前微信账号绑定，不会用于其他用途'

/** 学号弱校验口径：去除首尾空白后须为非空纯数字（不限定位数） */
const usernameValid = computed(() => /^\d+$/.test(form.value.username.trim()))
/** 发码钮可用性：学号合法 && 非冷却 && 无发送在途 */
const codeActionEnabled = computed(() => usernameValid.value && codeCooldown.value === 0 && !sendingCode.value)
/** 认证钮可用性：学号合法非空 && 验证码非空 && 无请求在途 */
const submitEnabled = computed(() => usernameValid.value && form.value.code.trim() !== '' && !isBusy.value)

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

function startCountdown() {
  // 若已有剩余冷却（重开弹层续接），不重置为 60，沿用当前剩余值
  if (codeCooldown.value <= 0) codeCooldown.value = 60
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    codeCooldown.value -= 1
    if (codeCooldown.value <= 0 && countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
  }, 1000)
}

async function sendCode() {
  // 前置状态锁：不满足可用性时静默返回（按钮已置灰，不产生错误提示）
  if (!codeActionEnabled.value) return
  const username = form.value.username.trim()
  clearError()
  sendingCode.value = true
  try {
    await sendEmailCode(username, 'verify')
    uni.showToast({ title: '验证码已发送', icon: 'success' })
    startCountdown()
  } catch (e: any) { setError(e.message || '验证码发送失败') } finally { sendingCode.value = false }
}

async function submit() {
  // 前置状态锁：任一条件不满足（字段空/学号非法/在途）静默返回
  if (!submitEnabled.value) return
  clearError()
  try {
    await userStore.verifyEmail(form.value.code.trim())
    uni.showToast({ title: '认证成功', icon: 'success' })
  } catch (e: any) { setError(e.message || '认证失败') }
}

onUnmounted(() => { if (countdownTimer) clearInterval(countdownTimer) })

// 用户主动关闭（点击遮罩 / 下滑 / 关闭钮）未完成认证：清除待办，避免过期动作在后续认证成功后误执行
function hide() {
  authSheetStore.clearPending()
  authSheetStore.hide()
}

// 认证成功（verified=true）后关闭弹层并执行认证前记录的待办（跳转到目标功能，§5.y）
watch(
  () => userStore.isVerified(),
  (v) => {
    if (v) authSheetStore.runPending()
  },
)
</script>

<style scoped>
/* 认证弹层（表单 scoped 样式并入本文件） */
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
.code-action { flex-shrink: 0; min-width: 154rpx; height: 90rpx; padding: 0 0 0 var(--spacing-sm); display: flex; align-items: center; justify-content: flex-end; color: var(--color-primary); font-size: var(--font-small); font-weight: var(--weight-semibold); white-space: nowrap; }
.code-action.disabled { color: var(--text-tertiary); }
.email-hint { padding: 0 var(--spacing-xs); font-size: var(--font-aux); line-height: 1.5; color: var(--text-tertiary); }

/* 主按钮：与全站主操作同款（radius-btn + shadow-bar-primary + 按压缩放） */
.primary-action { height: 92rpx; margin-top: var(--spacing-lg); border-radius: var(--radius-btn); background: var(--color-primary); box-shadow: var(--shadow-bar-primary); display: flex; align-items: center; justify-content: center; transition: opacity var(--duration-fast) var(--ease-out); }
.primary-action.disabled { opacity: 0.58; }
.primary-action-text { color: var(--color-on-primary); font-size: var(--font-subtitle); font-weight: var(--weight-bold); }

/* 底部安全说明：锁图标 + 简短文案（认证即绑定，不公开传播） */
.bottom-note { display: flex; align-items: center; justify-content: center; gap: var(--spacing-xs); margin-top: var(--spacing-md); }
.note-text { font-size: var(--font-aux); color: var(--text-tertiary); }
</style>
