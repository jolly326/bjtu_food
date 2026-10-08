<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import BaseModal from '@/components/BaseModal.vue'
import { changePassword, disableMfa, enableMfa, setupMfa, type MfaSetupResult } from '@/api/auth'
import { setSession, getUsername } from '@/api/session'

/**
 * 「账号安全」弹窗：改密 + 动态口令（MFA）绑定 / 停用。
 *
 * <p><b>口径真源</b>：[ui/web/登录页.md](../../../docs/ui/web/登录页.md)（登录第二步与绑定页）、
 * [api/web/auth.md](../../../docs/api/web/auth.md)（端点与字段）、
 * [secur/web/防爆破与限流.md](../../../docs/secur/web/防爆破与限流.md)（口令策略与 MFA）。
 *
 * <p>🔴 <b>口令 / 动态口令只存在于本组件内存态</b>：不写 `sessionStorage`、不进日志、不落构建产物。
 * 强度校验的服务端是唯一门；此处只做「不合规就本地拦住」的体验优化。
 */
const props = defineProps<{ open: boolean; mfaEnabled: boolean }>()
const emit = defineEmits<{ close: []; 'mfa-changed': [enabled: boolean] }>()

// ==================== 改密 ====================
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const changingPassword = ref(false)

/** 与服务端口径一致：≥12 位且含大写/小写/数字/符号中至少三类（本地提示用，服务端仍会校验） */
const MIN_PASSWORD_LENGTH = 12
const REQUIRED_CHARACTER_CLASSES = 3

const passwordHint = computed(() => {
  const value = newPassword.value
  if (!value) return ''
  const classes = [/[A-Z]/, /[a-z]/, /\d/, /[^A-Za-z0-9]/].filter((re) => re.test(value)).length
  if (value.length < MIN_PASSWORD_LENGTH)
    return `至少 ${MIN_PASSWORD_LENGTH} 位（当前 ${value.length} 位）`
  if (classes < REQUIRED_CHARACTER_CLASSES)
    return `需含大写、小写、数字、符号中的至少 ${REQUIRED_CHARACTER_CLASSES} 类`
  return ''
})

function resetPasswordForm(): void {
  oldPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
}

// ==================== 动态口令 ====================
const bindingOpen = ref(false)
const disablingOpen = ref(false)
const settingUp = ref(false)
const setupResult = ref<MfaSetupResult | null>(null)
const bindCode = ref('')
const billing = ref(false)
/** 绑定成功后一次性下发的恢复码（关闭即无处再取） */
const recoveryCodes = ref<string[]>([])

const disablePassword = ref('')
const disableCode = ref('')
const disabling = ref(false)

async function startBind(): Promise<void> {
  settingUp.value = true
  try {
    setupResult.value = await setupMfa()
    bindCode.value = ''
    recoveryCodes.value = []
    bindingOpen.value = true
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '初始化失败')
  } finally {
    settingUp.value = false
  }
}

async function confirmBind(): Promise<void> {
  if (!setupResult.value) return
  if (!bindCode.value.trim()) {
    ElMessage.error('请输入认证器动态口令')
    return
  }
  billing.value = true
  try {
    const res = await enableMfa(setupResult.value.secret, bindCode.value.trim())
    recoveryCodes.value = res.recoveryCodes ?? []
    emit('mfa-changed', true)
    ElMessage.success('动态口令已启用')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '绑定失败')
  } finally {
    billing.value = false
  }
}

function finishBind(): void {
  bindingOpen.value = false
  setupResult.value = null
  bindCode.value = ''
  // 恢复码在关闭弹窗后即无处可查 —— 用户须当场抄录，故不做「静默清空」以外的承诺
  recoveryCodes.value = []
}

async function confirmDisable(): Promise<void> {
  if (!disablePassword.value || !disableCode.value.trim()) {
    ElMessage.error('请填写当前密码与动态口令')
    return
  }
  disabling.value = true
  try {
    await disableMfa(disablePassword.value, disableCode.value.trim())
    emit('mfa-changed', false)
    ElMessage.success('动态口令已停用')
    disablingOpen.value = false
    disablePassword.value = ''
    disableCode.value = ''
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '停用失败')
  } finally {
    disabling.value = false
  }
}

// ==================== 提交改密 ====================
async function submitPassword(): Promise<void> {
  if (!oldPassword.value || !newPassword.value || !confirmPassword.value) {
    ElMessage.error('请完整填写三项')
    return
  }
  if (passwordHint.value) {
    ElMessage.error(passwordHint.value)
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    ElMessage.error('两次输入的新密码不一致')
    return
  }
  changingPassword.value = true
  try {
    const res = await changePassword(oldPassword.value, newPassword.value)
    // 🔴 改密使既有 token 全部失效：服务端回一枚新 token，本端立即换用，避免当场被踢
    if (res.token) setSession(res.token, res.username ?? getUsername() ?? '')
    ElMessage.success('密码已修改，其他设备的登录已失效')
    resetPasswordForm()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '修改失败')
  } finally {
    changingPassword.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (!open) {
      resetPasswordForm()
      bindingOpen.value = false
      disablingOpen.value = false
      recoveryCodes.value = []
      setupResult.value = null
    }
  },
)
</script>

<template>
  <BaseModal title="账号安全" :open="open" @close="emit('close')">
    <section class="block">
      <h4 class="block-title">修改密码</h4>
      <div class="field">
        <label for="sec-old">当前密码</label>
        <input
          id="sec-old"
          v-model="oldPassword"
          class="form-input"
          type="password"
          autocomplete="current-password"
        />
      </div>
      <div class="field">
        <label for="sec-new">新密码</label>
        <input
          id="sec-new"
          v-model="newPassword"
          class="form-input"
          type="password"
          autocomplete="new-password"
        />
        <p class="field-hint">至少 12 位，且含大写、小写、数字、符号中的至少三类。</p>
      </div>
      <div class="field">
        <label for="sec-confirm">确认新密码</label>
        <input
          id="sec-confirm"
          v-model="confirmPassword"
          class="form-input"
          type="password"
          autocomplete="new-password"
        />
      </div>
      <button
        class="btn-primary"
        type="button"
        :disabled="changingPassword"
        @click="submitPassword"
      >
        {{ changingPassword ? '提交中…' : '修改密码' }}
      </button>
      <p class="field-hint">修改后其他设备的登录会立即失效；本端自动续用新凭证。</p>
    </section>

    <section class="block">
      <h4 class="block-title">动态口令（MFA）</h4>
      <p class="field-hint">
        当前状态：<strong>{{ mfaEnabled ? '已启用' : '未启用' }}</strong
        >。 启用后登录需两步（账号密码 + 认证器口令），口令泄露也拿不到凭证。
      </p>
      <button
        v-if="!mfaEnabled"
        class="btn-primary"
        type="button"
        :disabled="settingUp"
        @click="startBind"
      >
        {{ settingUp ? '准备中…' : '绑定动态口令' }}
      </button>
      <button v-else class="btn-secondary" type="button" @click="disablingOpen = true">
        停用动态口令
      </button>
    </section>

    <template #actions>
      <button class="btn-secondary" type="button" @click="emit('close')">关闭</button>
    </template>
  </BaseModal>

  <!-- 绑定：先录入密钥，再以认证器口令确认；成功后一次性展示恢复码 -->
  <BaseModal title="绑定动态口令" :open="bindingOpen" @close="finishBind">
    <template v-if="recoveryCodes.length === 0">
      <p class="field-hint">用认证器 App 扫描或手动录入下面的密钥，然后填写 App 当前显示的口令。</p>
      <div class="field">
        <label for="mfa-secret">密钥（Base32）</label>
        <input
          id="mfa-secret"
          class="form-input"
          type="text"
          readonly
          :value="setupResult?.secret ?? ''"
        />
      </div>
      <div class="field">
        <label for="mfa-uri">otpauth 链接</label>
        <input
          id="mfa-uri"
          class="form-input"
          type="text"
          readonly
          :value="setupResult?.otpauthUri ?? ''"
        />
      </div>
      <div class="field">
        <label for="mfa-bind-code">认证器口令</label>
        <input
          id="mfa-bind-code"
          v-model="bindCode"
          class="form-input"
          type="text"
          inputmode="numeric"
          autocomplete="one-time-code"
        />
      </div>
      <p class="field-hint">绑定未确认前不会改动账号；中途放弃不影响现有登录方式。</p>
    </template>
    <template v-else>
      <p class="field-hint">
        🔴 以下恢复码<b>只显示这一次</b>（服务端仅存哈希，关闭后再也查不到）。
        请立即抄录并离线保存；设备丢失时用它登录，用一枚作废一枚。
      </p>
      <ul class="codes">
        <li v-for="code in recoveryCodes" :key="code">{{ code }}</li>
      </ul>
    </template>

    <template #actions>
      <template v-if="recoveryCodes.length === 0">
        <button class="btn-secondary" type="button" @click="finishBind">取消</button>
        <button class="btn-primary" type="button" :disabled="billing" @click="confirmBind">
          {{ billing ? '确认中…' : '确认绑定' }}
        </button>
      </template>
      <button v-else class="btn-primary" type="button" @click="finishBind">我已抄录</button>
    </template>
  </BaseModal>

  <!-- 停用：须同时提供口令与动态口令（防 token 盗用方顺手摘掉第二因子） -->
  <BaseModal title="停用动态口令" :open="disablingOpen" @close="disablingOpen = false">
    <p class="field-hint">停用会降低账号防护，需同时验证当前密码与动态口令。</p>
    <div class="field">
      <label for="mfa-off-pwd">当前密码</label>
      <input
        id="mfa-off-pwd"
        v-model="disablePassword"
        class="form-input"
        type="password"
        autocomplete="current-password"
      />
    </div>
    <div class="field">
      <label for="mfa-off-code">动态口令 / 恢复码</label>
      <input
        id="mfa-off-code"
        v-model="disableCode"
        class="form-input"
        type="text"
        autocomplete="one-time-code"
      />
    </div>
    <template #actions>
      <button class="btn-secondary" type="button" @click="disablingOpen = false">取消</button>
      <button class="btn-primary" type="button" :disabled="disabling" @click="confirmDisable">
        {{ disabling ? '提交中…' : '确认停用' }}
      </button>
    </template>
  </BaseModal>
</template>

<style scoped>
.block {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding-bottom: var(--space-5);
  margin-bottom: var(--space-5);
  border-bottom: 1px solid var(--border-light);
}
.block:last-of-type {
  padding-bottom: 0;
  margin-bottom: 0;
  border-bottom: none;
}
.block-title {
  margin: 0;
  font-size: var(--font-base);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}
.field label {
  font-size: var(--font-xs);
  color: var(--text-secondary);
}
.field-hint {
  margin: 0;
  font-size: var(--font-xs);
  color: var(--text-muted);
  line-height: 1.5;
}
.codes {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  list-style: none;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-2);
  background: var(--bg-hover);
  border-radius: var(--radius);
  font-family: var(--font-numeric);
  letter-spacing: 0.06em;
}
</style>
