<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const submitting = ref(false)

async function onSubmit(): Promise<void> {
  if (!username.value || !password.value) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  submitting.value = true
  try {
    await auth.login(username.value, password.value)
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.replace(redirect)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '登录失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-shell">
    <div class="login-card card">
      <h1>食在交大</h1>
      <p class="sub">管理后台 · 管理员登录</p>
      <form @submit.prevent="onSubmit">
        <div class="field">
          <label>账号</label>
          <input
            class="form-input"
            v-model="username"
            autocomplete="username"
            placeholder="管理员账号"
          />
        </div>
        <div class="field">
          <label>密码</label>
          <input
            class="form-input"
            type="password"
            v-model="password"
            autocomplete="current-password"
            placeholder="密码"
          />
        </div>
        <button class="btn-primary login-btn" type="submit" :disabled="submitting" v-press>
          {{ submitting ? '登录中…' : '登 录' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-shell {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-app, #f5f6f8);
}
.login-card {
  width: 360px;
  max-width: 92vw;
  padding: var(--space-8) var(--space-7);
  text-align: center;
}
.login-card h1 {
  font-size: var(--font-2xl);
  margin: 0 0 var(--space-1);
}
.sub {
  color: var(--text-muted);
  margin: 0 0 var(--space-6);
}
.field {
  text-align: left;
  margin-bottom: var(--space-4);
}
.field .form-input {
  max-width: none;
}
.login-btn {
  width: 100%;
  margin-top: var(--space-2);
  padding: var(--space-3);
}
</style>
