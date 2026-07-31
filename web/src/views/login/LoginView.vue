<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const userStore = useUserStore()
const username = ref('')
const password = ref('')
const error = ref('')

async function handleLogin() {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  const result = await userStore.login(username.value, password.value)
  if (result.success) {
    router.push('/dashboard/feedbacks')
  } else {
    error.value = result.error || '登录失败'
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h2>食在交大管理系统</h2>
      <p class="login-desc">请输入管理员账号和密码登录</p>
      <input v-model="username" placeholder="用户名" type="text" @input="error=''" />
      <input v-model="password" placeholder="密码" type="password" @keyup.enter="handleLogin" @input="error=''" />
      <p v-if="error" class="login-error">{{ error }}</p>
      <button v-press @click="handleLogin">登 录</button>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
}
.login-card {
  background: var(--bg-card);
  padding: var(--space-10) var(--space-8) var(--space-8);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-pop);
  width: 380px;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  align-items: center;
}
.login-card h2 {
  text-align: center;
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-2xl);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-tight);
}
.login-desc {
  color: var(--text-muted);
  font-size: var(--font-sm);
  margin: 0 0 var(--space-1);
}
.login-card input {
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  font-size: var(--font-base);
  outline: none;
  box-sizing: border-box;
  transition: border-color .2s var(--ease-out), box-shadow .2s var(--ease-out);
}
.login-card input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 15%, transparent);
}
.login-error {
  color: var(--color-error);
  font-size: var(--font-sm);
  margin: calc(-1 * var(--space-2)) 0;
}
.login-card button {
  width: 100%;
  padding: var(--space-3);
  background: var(--color-primary);
  color: var(--text-white);
  border: none;
  border-radius: var(--radius);
  font-size: var(--font-md);
  cursor: pointer;
  font-weight: var(--weight-medium);
  transition: background .2s var(--ease-out), transform 160ms var(--ease-out);
}
.login-card button:hover {
  background: var(--color-primary-light);
}
.login-card button:active {
  transform: scale(var(--press-scale));
}
</style>
