<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { changePassword } from '@/api/auth'
import { ElMessage } from 'element-plus'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const force = computed(() => route.query.force === '1')
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const err = ref('')

async function onSubmit(): Promise<void> {
  err.value = ''
  if (!oldPassword.value || !newPassword.value || !confirmPassword.value) {
    err.value = '请填写完整'
    return
  }
  if (newPassword.value.length < 6 || newPassword.value.length > 64) {
    err.value = '新密码需 6~64 字符'
    return
  }
  if (newPassword.value === oldPassword.value) {
    err.value = '新密码不能与旧密码相同'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    err.value = '两次输入不一致'
    return
  }
  submitting.value = true
  try {
    await changePassword({ oldPassword: oldPassword.value, newPassword: newPassword.value })
    ElMessage.success('密码修改成功')
    if (auth.admin) auth.admin.mustChangePassword = false
    if (force.value) router.replace('/dashboard')
    else router.back()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '修改失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>修改密码</h2>
    </div>
    <div class="card form-card">
      <p class="force-tip" v-if="force">系统检测到您是首次登录，请先修改密码后再继续使用。</p>
      <form @submit.prevent="onSubmit">
        <div class="field">
          <label>原密码</label>
          <input class="form-input" type="password" v-model="oldPassword" autocomplete="off" />
        </div>
        <div class="field">
          <label>新密码（6~64 字符）</label>
          <input class="form-input" type="password" v-model="newPassword" autocomplete="off" />
        </div>
        <div class="field">
          <label>确认新密码</label>
          <input class="form-input" type="password" v-model="confirmPassword" autocomplete="off" />
        </div>
        <p class="field-error" v-if="err">{{ err }}</p>
        <div class="modal-actions">
          <button class="btn-primary" type="submit" :disabled="submitting" v-press>
            {{ submitting ? '提交中…' : '提交' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.form-card {
  max-width: 480px;
  padding: var(--space-7);
}
.force-tip {
  color: var(--color-warning);
  margin: 0 0 var(--space-5);
}
.field {
  margin-bottom: var(--space-4);
}
.field .form-input {
  max-width: none;
}
.modal-actions {
  border: none;
  padding: 0;
  margin-top: var(--space-2);
}
</style>
