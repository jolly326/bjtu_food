<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')

function handleLogin() {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  localStorage.setItem('token', 'logged_in')
  localStorage.setItem('username', username.value)
  router.push('/dashboard')
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
      </div>
      <h2>食在交大管理系统</h2>
      <p class="login-desc">请输入管理员账号和密码登录</p>
      <input v-model="username" placeholder="用户名" type="text" @input="error=''" />
      <input v-model="password" placeholder="密码" type="password" @keyup.enter="handleLogin" @input="error=''" />
      <p v-if="error" class="login-error">{{ error }}</p>
      <button @click="handleLogin">登 录</button>
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
  background: #fff;
  padding: 40px 36px 32px;
  border-radius: var(--radius-lg);
  box-shadow: 0 8px 40px rgba(0,0,0,.2);
  width: 380px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: center;
}
.login-logo {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}
.login-card h2 {
  text-align: center;
  margin: 0;
  color: var(--text-primary);
  font-size: 20px;
  font-weight: 600;
}
.login-desc {
  color: var(--text-muted);
  font-size: 13px;
  margin: 0 0 2px;
}
.login-card input {
  width: 100%;
  padding: 11px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
  transition: border-color .2s;
}
.login-card input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 15%, transparent);
}
.login-error {
  color: var(--color-error);
  font-size: 13px;
  margin: -4px 0;
}
.login-card button {
  width: 100%;
  padding: 11px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius);
  font-size: 15px;
  cursor: pointer;
  font-weight: 500;
  transition: background .2s;
}
.login-card button:hover {
  background: var(--color-primary-light);
}
</style>
