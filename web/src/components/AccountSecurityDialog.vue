<script setup lang="ts">
/**
 * 「账号安全」弹窗：**修改密码**（唯一能力）。
 *
 * <p>口径真源：[api/web/auth.md](../../../docs/api/web/auth.md)（端点与字段）·
 * [secur/web/防爆破与限流.md](../../../docs/secur/web/防爆破与限流.md)（口令策略）；
 * 表单 / 按钮 / 反馈规格见 [公共组件与形态基线.md](../../../docs/ui/web/公共组件与形态基线.md)。
 *
 * <p>🔴 <b>口令只存在于本组件内存态</b>：不写 `sessionStorage`、不进日志、不落构建产物。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import BaseModal from '@/components/BaseModal.vue'
import { changePassword } from '@/api/auth'
import { getUsername, setSession } from '@/api/session'
import { fail } from '@/utils/error'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

/** 三项输入（内存态） */
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
/** 提交中 —— 主按钮 `:disabled`，防重复提交 */
const changingPassword = ref(false)

/** 口令强度提示（实时；与服务端 `PasswordPolicy` 同口径：≥12 位且至少三类字符） */
const passwordHint = computed(() => {
  const v = newPassword.value
  if (!v) return ''
  const classes =
    (/[A-Z]/.test(v) ? 1 : 0) +
    (/[a-z]/.test(v) ? 1 : 0) +
    (/[0-9]/.test(v) ? 1 : 0) +
    (/[^A-Za-z0-9]/.test(v) ? 1 : 0)
  if (v.length < 12) return '还差 ' + (12 - v.length) + ' 位'
  if (classes < 3) return '需包含大写、小写、数字、符号中的至少三类'
  return ''
})

function resetPasswordForm(): void {
  oldPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
}

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
    fail(e, '修改失败')
  } finally {
    changingPassword.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (!open) resetPasswordForm()
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

    <template #actions>
      <button class="btn-secondary" type="button" @click="emit('close')">关闭</button>
    </template>
  </BaseModal>
</template>
