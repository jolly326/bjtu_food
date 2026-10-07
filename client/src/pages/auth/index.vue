<template>
  <view class="page auth-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下） -->
    <PageWallpaper fixed />
    <Header title="身份认证" @back="leaveWithoutVerify" />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 极简无卡片：**不设白色大表单卡**，表单区与说明区各自一块 `.module-wrap`，
           区块之间只靠页面留白（容器 flex gap）分组，不靠分割线。
           表单区 =（学号 → 动态邮箱提示 → 验证码 → 错误行 → 认证按钮）；
           说明区 =（功能说明 → 隐私锁说明）。 -->
      <view class="auth-modules">
        <view class="module-wrap">
        <!-- 学号：透明底 + 仅底部 1rpx 细线（与意见反馈页同款）；聚焦 → 底线切主色 -->
        <view class="input-field" :class="{ 'input-field--focus': focusField === 'username' }">
          <input
            :value="form.username"
            class="input-control"
            type="number"
            placeholder="学号"
            placeholder-class="input-placeholder"
            aria-label="学号"
            :aria-invalid="formError ? 'true' : 'false'"
            @input="onUsernameInput"
            @focus="focusField = 'username'"
            @blur="onUsernameBlur"
          />
        </view>

        <!-- 动态邮箱提示：学号合法后才出现，紧贴学号框下沿 -->
        <view v-if="usernameValid" class="email-hint">验证码将发送至 {{ deriveCampusEmail(form.username.trim()) }}</view>

        <!-- 邮箱验证码：仅数字、≤6 位；行内「获取验证码」**描边胶囊**（轻控件，非色块） -->
        <view class="input-field input-field--code" :class="{ 'input-field--focus': focusField === 'code' }">
          <input
            :value="form.code"
            class="input-control"
            type="number"
            maxlength="6"
            placeholder="邮箱验证码"
            placeholder-class="input-placeholder"
            aria-label="邮箱验证码"
            :aria-invalid="formError ? 'true' : 'false'"
            @input="onCodeInput"
            @focus="focusField = 'code'"
            @blur="focusField = ''"
          />
          <!-- 按压反馈：`<text>` 不支持 `hover-class` ⇒ 胶囊本体改用 `<view>` 承载（视觉与热区不变） -->
          <view
            class="code-action"
            :class="{ disabled: !codeActionEnabled }"
            role="button"
            :aria-label="codeActionLabel"
            :aria-disabled="codeActionEnabled ? 'false' : 'true'"
            :aria-busy="sendingCode ? 'true' : 'false'"
            hover-class="pressed"
            @tap="sendCode"
          >
            <text class="code-action-text">{{ codeButtonText }}</text>
          </view>
        </view>

        <!-- 表单错误行：`role=alert` 即时播报；**无红色底块**，仅浅红文字 + alert 图标；点击即清。
             限频退避时本行实时走秒展示「发送太频繁，N 秒后再试」。 -->
        <view v-if="formErrorText" class="form-error" role="alert" aria-live="assertive" @tap="clearError">
          <AppIcon name="alert" :size="24" :color="COLOR_MAP.error" class="form-error-icon" />
          <text class="form-error-text">{{ formErrorText }}</text>
        </view>

        <!-- 认证主按钮：全站统一 `AppButton`（24rpx 圆角 / 主色实底 / 禁用置灰）。
             外层包裹只承担上间距 —— `AppButton` 自带内联 `margin: 0`，会盖掉 class 上的 margin。 -->
        <view class="action-wrap">
          <AppButton
            :text="primaryText"
            type="primary"
            :disabled="!submitEnabled"
            :loading="isBusy"
            @press="submit"
          />
        </view>
        </view>

        <!-- 两行说明（**独立第二块模块**，与表单区同级）：功能说明 → 隐私锁说明 -->
        <view class="module-wrap">
          <view class="form-note">{{ NOTE_SUBTITLE }}</view>
          <view class="bottom-note">
            <AppIcon name="lock" :size="24" :color="COLOR_MAP['text-tertiary']" />
            <text class="note-text">{{ NOTE_PRIVACY }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 身份认证独立页：
 * 学号 + 邮箱验证码两字段表单；发码冷却由 authStore 持有（跨进出页面持久，前端不辅助绕过 60s 冷却）。
 * 认证成功（bindEmail 落库）→ Toast（**含资料完善引导**：可到「我的主页」完善昵称头像）→ 返回原页；
 * 若进入本页前记录了待办（requireAuth 守卫），原页 onShow 经 authStore.consumePending() 续接（如重新打开写评价表单）。
 * 未完成认证离开本页（Header 返回 / 手势返回）→ 清除待办，避免过期动作误执行。
 */
import { ref, computed } from 'vue'
import { onUnload } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import Header from '@/components/AppHeader.vue'
import AppButton from '@/components/AppButton.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import AppIcon from '@/components/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import { sendEmailCode, deriveCampusEmail } from '@/api/user'
import { errorMessage, toastInfo, toastSuccess } from '@/utils/error'
import { useRateLimitCooldown } from '@/composables/useRateLimitCooldown'
import { backToHome } from '@/utils/back'
import { COLOR_MAP } from '@/theme/tokens'
import { isValidStudentNo } from '@/utils/validate'

const authStore = useAuthStore()
const userStore = useUserStore()
// 冷却必须用 storeToRefs 保持响应性（跨页面持有，重进页面续接剩余秒数）
const { codeCooldown } = storeToRefs(authStore)

// 发码限频退避（P2-7）：被后端限频时不再当作不可恢复失败 ——
// 发码钮进禁用档（退避秒数走秒），倒计时文案复用 `.form-error` 行实时展示，结束自动恢复
const { cooldownSeconds: rateLimitSeconds, cooling: rateLimited, handleError: handleRateLimit } =
  useRateLimitCooldown()

// ---- 认证表单状态 ----
const form = ref({ username: '', code: '' })
const formError = ref('')
function setError(msg: string) { formError.value = msg }
function clearError() { formError.value = '' }

/** 错误行展示文案：限频退避优先（倒计时实时走秒，结束自动消失），其余用已置错误文案 */
const formErrorText = computed(() =>
  rateLimited() ? `发送太频繁，${rateLimitSeconds.value} 秒后再试` : formError.value,
)

/** 当前聚焦字段（驱动底线高亮）：空串 = 无聚焦 */
const focusField = ref('')

/** 文案常量（契约基线） */
const NOTE_SUBTITLE = '验证码将发送至你的校园邮箱，完成身份认证后即可使用评价等功能'
const NOTE_PRIVACY = '仅用于核验本校校园身份，认证后将与当前微信账号绑定，不会用于其他用途'

/** 学号弱校验口径：去除首尾空白后须为非空纯数字（不限定位数） */
const usernameValid = computed(() => isValidStudentNo(form.value.username))
/** 发码钮可用性：学号合法 && 非冷却 && 非限频退避 && 无发送在途 */
const codeActionEnabled = computed(
  () => usernameValid.value && codeCooldown.value === 0 && !rateLimited() && !sendingCode.value,
)
/** 认证钮可用性：学号合法非空 && 验证码非空 && 无请求在途 */
const submitEnabled = computed(() => usernameValid.value && form.value.code.trim() !== '' && !isBusy.value)

const isBusy = ref(false)
const sendingCode = ref(false)
const primaryText = computed(() => (isBusy.value ? '认证中…' : '认证'))
/** 发码钮文案：发送在途 / 冷却倒计时 / 限频退避 / 常态四分支 */
const codeButtonText = computed(() => {
  if (sendingCode.value) return '发送中…'
  if (codeCooldown.value > 0) return `${codeCooldown.value}s后重发`
  if (rateLimited()) return `${rateLimitSeconds.value}s后重发`
  return '获取验证码'
})
const codeActionLabel = computed(() =>
  sendingCode.value ? '发送中' : codeCooldown.value > 0 ? `${codeCooldown.value}s后重发验证码` : '获取验证码',
)

/** uni input 事件：结构类型收窄 detail.value（平台事件对象未纳入项目 TS 类型） */
type InputLike = { detail?: { value?: string } }

/** 学号 @input 净化：仅保留数字并去空白，保证「纯数字」判定可靠（粘贴含空格/小数点也会被净化） */
function onUsernameInput(e: Event) {
  form.value.username = String((e as unknown as InputLike).detail?.value ?? '').replace(/\D/g, '')
  clearError()
}
/** 学号失焦浅提示：空 / 含非数字才提示原因，不发起任何请求 */
function onUsernameBlur() {
  focusField.value = ''
  const v = form.value.username.trim()
  if (!v) setError('请输入学号')
  else if (!isValidStudentNo(v)) setError('学号需为纯数字')
}
/** 验证码 @input 净化：仅保留数字并截断 6 位（与 `maxlength=6` 双重兜底） */
function onCodeInput(e: Event) {
  form.value.code = String((e as unknown as InputLike).detail?.value ?? '').replace(/\D/g, '').slice(0, 6)
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
    toastSuccess('验证码已发送')
    authStore.startCooldown()
  } catch (e) {
    // 限频：进倒计时退避，倒计时文案复用 `.form-error` 行实时走秒；其余错误给通用文案
    if (!handleRateLimit(e)) setError(errorMessage(e, '验证码发送失败'))
  } finally { sendingCode.value = false }
}

/** 认证成功标记：区分「完成认证返回」与「中途放弃」（决定 onUnload 是否清待办） */
let verified = false
/** 成功返回定时器句柄：离开页面时清理，避免返回打到已销毁页 */
let navTimer: ReturnType<typeof setTimeout> | null = null

async function submit() {
  // 前置状态锁：任一条件不满足（字段空/学号非法/在途）静默返回
  if (!submitEnabled.value) return
  clearError()
  isBusy.value = true
  try {
    await userStore.verifyEmail(form.value.code.trim())
    verified = true
    // 资料层引导（登录 / 资料两层分离口径）：认证是低频动作，成功即提示一句「可完善昵称头像」，
    // 落点 = 「我的主页」身份卡的「编辑个人信息」入口（该入口仅认证态渲染）；不跳转、不打断续接。
    // 用 `toastInfo`（icon:'none'）：`toastSuccess` 走 icon:'success'，长文案会被截断。
    toastInfo('认证成功，可到「我的主页」完善昵称头像')
    // 返回原页：待办由原页 onShow 经 consumePending 续接
    if (navTimer) clearTimeout(navTimer)
    navTimer = setTimeout(backToHome, 600)
  } catch (e) { setError(errorMessage(e, '认证失败')) } finally { isBusy.value = false }
}

/** 未完成认证即离开（Header 返回）——onUnload 统一清待办；无返回栈时 reLaunch 首页兜底 */
function leaveWithoutVerify() {
  backToHome()
}

onUnload(() => {
  if (navTimer) clearTimeout(navTimer)
  if (!verified) authStore.clearPending()
})
</script>

<style scoped>
/* 页面根不带底色：底色下沉到全局 `page{}`，否则会盖住负层级壁纸层 */
.auth-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; }
.auth-page { min-height: 0; }
/* `min-height: 0` 必需：全局 `.page` 兜底写了 `min-height: 100vh / 100dvh`，而移动端
   `100vh`（最大视口）通常 **大于** `100dvh`（当前视口）；二者同时存在时 min 胜出
   ⇒ 页根比可视区高出一截 ⇒ **页面本身**多出一段可滚区（内容并未超屏也会滚）。
   自带滚动容器的页根必须把 min-height 归零，把高度交给 `height: 100dvh` + 内部 scroll-view。 */
/* 底部 = 呼吸位 + `env(safe-area-inset-bottom)`：表单卡是滚动区最后一块，
   无安全区时会被 Home Indicator 压住（同 `my-reviews` 的 `.scroll-wrap` 写法） */
.scroll-wrap { flex: 1; min-height: 0; padding: var(--spacing-lg) var(--page-gutter) calc(var(--spacing-md) + var(--spacing-lg) + env(safe-area-inset-bottom)); box-sizing: border-box; }

/* ===== 极简无卡片：表单区 / 说明区各一块 `.module-wrap`（暖奶米半透底 + 圆角 + 极淡暖棕阴影，
       规格由全局类承担），模块之间只靠页面留白分组 ===== */
.auth-modules {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

/* ===== 输入项：浅白半透底 + 圆角，**无边框无线**（团队规范 §3.2 表单输入语言；
       聚焦 → 底色加深一档，替代原先的「底线切主色」（底色是模块内唯一的输入边界线索，
       低对比度底线 1.18:1 对低视力不可辨）===== */
.input-field {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  min-height: var(--tap-target-size);
  padding: 0 var(--spacing-md);
  background: var(--module-input-bg);
  border: none;
  border-radius: var(--radius-icon);
  box-sizing: border-box;
  transition: background-color var(--duration-fast) var(--ease-out);
}
.input-field--focus { background: var(--bg-soft); }
.input-control { flex: 1; min-width: 0; height: var(--tap-target-size); font-size: var(--font-body); color: var(--text-primary); }
.input-placeholder { color: var(--text-tertiary); }
/* 验证码行：与上一输入项（或动态提示）拉开一组间距 */
.input-field--code { margin-top: var(--spacing-lg); }

/* 动态邮箱提示：学号合法才出现，紧贴学号框下沿 */
.email-hint { margin-top: var(--spacing-xs); font-size: var(--font-aux); line-height: 1.5; color: var(--text-tertiary); }

/* ===== 行内「获取验证码」：描边胶囊（轻控件，非厚重色块） ===== */
.code-action {
  position: relative;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 64rpx;
  padding: 0 var(--spacing-md);
  border: 1rpx solid var(--color-primary-text);
  border-radius: var(--radius-pill);
  color: var(--color-primary-text);
  white-space: nowrap;
  -webkit-tap-highlight-color: transparent;
}
/* 胶囊文案：字号 / 字重挂在本节点上（颜色随胶囊状态继承） */
.code-action-text { font-size: var(--font-small); font-weight: var(--weight-medium); }
/* 命中区经 ::after **仅纵向**扩至 ≥88rpx（a11y 44pt 下限；视觉尺寸不变） */
.code-action::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: var(--tap-target-size);
  transform: translateY(-50%);
}
/* 禁用档（描边 / 文字类）：中性描边 + 文字三阶末档 + 禁点（**不降透明**，opacity 只表在途 busy） */
.code-action.disabled { color: var(--text-tertiary); border-color: var(--border-color); pointer-events: none; }

/* ===== 表单错误行：**无红色底块**，仅浅红文字 + alert 图标 ===== */
.form-error { display: flex; align-items: center; gap: var(--spacing-2xs); margin-top: var(--spacing-xs); }
.form-error-icon { flex: none; width: 24rpx; height: 24rpx; }
.form-error-text { flex: 1; min-width: 0; font-size: var(--font-aux); line-height: 1.5; color: var(--color-error); }

/* ===== 主按钮上间距（按钮本体由 `AppButton` 承担全站统一形态） ===== */
.action-wrap { margin-top: var(--spacing-xl); }

/* ===== 两行说明：功能说明 → 隐私锁说明 ===== */
.form-note { display: block; margin-top: var(--spacing-lg); font-size: var(--font-aux); line-height: 1.5; color: var(--text-tertiary); }
.bottom-note { display: flex; align-items: flex-start; gap: var(--spacing-xs); margin-top: var(--spacing-sm); }
.note-text { flex: 1; min-width: 0; font-size: var(--font-aux); line-height: 1.5; color: var(--text-tertiary); }
</style>
