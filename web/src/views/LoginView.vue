<script setup lang="ts">
/**
 * 登录页（设计稿 docs/ui/web/登录页.md；业务规则 docs/func/web/C1）。
 * 无注册 / 无找回密码 / 无验证码；token 存 sessionStorage。
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  if (!form.username.trim() || !form.password) {
    error.value = '请输入账号和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await session.login(form.username.trim(), form.password)
    router.push({ name: 'canteens' })
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <form class="login__card" @submit.prevent="onSubmit">
      <h2 class="login__title">知行食记 · 运营端</h2>

      <label class="login__label" for="username">账号</label>
      <input
        id="username"
        v-model="form.username"
        class="login__input"
        type="text"
        placeholder="账号"
        autocomplete="username"
      />

      <label class="login__label" for="password">密码</label>
      <input
        id="password"
        v-model="form.password"
        class="login__input"
        type="password"
        placeholder="密码"
        autocomplete="current-password"
      />

      <p v-if="error" class="login__error">{{ error }}</p>

      <button class="login__submit" type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登录' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
}

.login__card {
  display: flex;
  flex-direction: column;
  width: 400px;
  padding: 32px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
}

.login__title {
  margin: 0 0 24px;
  font-size: 18px;
  text-align: center;
  color: var(--el-text-color-primary);
}

.login__label {
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.login__input {
  height: 36px;
  margin-bottom: 16px;
  padding: 0 12px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  outline: none;
}

.login__input:focus {
  border-color: var(--el-color-primary);
}

.login__error {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-color-danger);
}

.login__submit {
  height: 36px;
  border: none;
  border-radius: var(--el-border-radius-base);
  background: var(--el-color-primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

.login__submit:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>
