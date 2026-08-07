<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const userStore = useUserStore()
const username = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

async function handleLogin() {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    const result = await userStore.login(username.value, password.value)
    if (result.success) {
      router.replace('/dashboard')
    } else {
      error.value = result.error || '登录失败'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-brand">
      <h1 class="brand-name">知行食记</h1>
      <p class="brand-slogan">校园美食信息管理后台</p>
    </div>

    <div class="login-card">
      <div class="card-head">
        <h2>管理员登录</h2>
        <p class="login-desc">请输入管理员账号和密码</p>
      </div>
      <label class="field">
        <span class="field-label">用户名</span>
        <input v-model="username" placeholder="请输入用户名" type="text" autocomplete="username" @input="error=''" />
      </label>
      <label class="field">
        <span class="field-label">密码</span>
        <input v-model="password" placeholder="请输入密码" type="password" autocomplete="current-password" @keyup.enter="handleLogin" @input="error=''" />
      </label>
      <p v-if="error" class="login-error" role="alert">{{ error }}</p>
      <button class="login-btn" v-press :disabled="submitting" type="button" @click="handleLogin">
        {{ submitting ? '登录中…' : '登 录' }}
      </button>
      <p class="login-hint">
        默认管理员：<code>admin</code> / <code>123456</code>（首次登录后请修改）
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-8);
  padding: var(--space-6);
  box-sizing: border-box;
  /* 白灰黑：纯背景，无渐变 */
  background: var(--bg-page);
}

/* 品牌区：纯文字品牌，无图标、无渐变 */
.login-brand { display: flex; flex-direction: column; align-items: center; gap: var(--space-2); }
.brand-name { margin: 0; color: var(--text-primary); font-size: 26px; font-weight: var(--weight-bold); letter-spacing: 0.06em; }
.brand-slogan { margin: 0; color: var(--text-secondary); font-size: var(--font-base); letter-spacing: 0.08em; }

/* 登录卡片 */
.login-card {
  width: 380px;
  max-width: 100%;
  background: var(--bg-card);
  padding: var(--space-8);
  border-radius: var(--radius-modal);
  box-shadow: 0 24px 60px color-mix(in srgb, #000 18%, transparent);
  border: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  animation: card-in 0.34s var(--ease-out) both;
}
@keyframes card-in {
  from { opacity: 0; transform: translateY(14px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
@media (prefers-reduced-motion: reduce) { .login-card { animation: none; } }

.card-head { text-align: center; margin-bottom: var(--space-2); }
.card-head h2 { margin: 0; color: var(--text-primary); font-size: var(--font-2xl); font-weight: var(--weight-semibold); letter-spacing: var(--tracking-tight); }
.login-desc { color: var(--text-muted); font-size: var(--font-sm); margin: var(--space-1) 0 0; }

.field { display: flex; flex-direction: column; gap: var(--space-2); }
.field-label { font-size: var(--font-sm); font-weight: var(--weight-medium); color: var(--text-secondary); }
.field input {
  width: 100%;
  padding: var(--space-2) var(--space-3);   /* 与后台 .field input 一致 */
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-size: var(--font-base);
  outline: none;
  box-sizing: border-box;
  background: var(--bg-card);
  transition: border-color .2s var(--ease-out), box-shadow .2s var(--ease-out);
}
.field input:focus {
  border-color: var(--color-primary);
  box-shadow: var(--focus-ring);
}
.login-error {
  color: var(--color-error);
  font-size: var(--font-sm);
  margin: 0;
}
.login-hint {
  margin: var(--space-2) 0 0;
  text-align: center;
  font-size: var(--font-xs);
  color: var(--text-muted);
  line-height: 1.6;
}
.login-hint code {
  padding: 0 4px;
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: var(--font-xs);
}
.login-btn {
  width: 100%;
  padding: var(--space-3);
  background: var(--color-primary);   /* 与后台 .btn-primary 一致的纯色 */
  color: var(--color-on-primary);
  border: none;
  border-radius: var(--radius);
  font-size: var(--font-md);
  cursor: pointer;
  font-weight: var(--weight-medium);
  transition: background .2s var(--ease-out), transform 160ms var(--ease-out), box-shadow .2s var(--ease-out);
}
.login-btn:hover { background: var(--color-primary-light); box-shadow: 0 8px 20px color-mix(in srgb, #000 35%, transparent); }
.login-btn:active { transform: scale(var(--press-scale)); }
.login-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.login-btn:focus-visible { outline: none; box-shadow: var(--focus-ring); }

@media (max-width: 480px) {
  .login-card { padding: var(--space-6); }
}
</style>
