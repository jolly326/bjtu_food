<script setup lang="ts">
/**
 * 登录页（设计稿 docs/ui/web/登录页.md；业务规则 docs/func/web/C1）。
 * 无注册 / 无找回密码 / 无验证码；token 存 sessionStorage。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

/** 已登录时直接进首屏 */
onMounted(() => {
  if (session.isAuthenticated) router.replace({ name: 'canteens' })
})

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

/* 登录卡：极浅灰白卡面 + 双层极弱阴影，无实线描边；窄屏自适应 */
.login__card {
  display: flex;
  flex-direction: column;
  width: min(400px, 92vw);
  padding: 32px;
  border-radius: 20px;
  background: var(--web-bg-card);
  box-shadow: var(--el-box-shadow-elevated);
}

.login__title {
  margin: 0 0 24px;
  font-family: var(--web-font-serif);
  font-size: 20px;
  font-weight: 500;
  text-align: center;
  color: var(--el-text-color-primary);
}

.login__label {
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.login__input {
  height: 38px;
  margin-bottom: 16px;
  padding: 0 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s ease;
}

.login__input:focus {
  border-color: var(--el-color-primary-light-5);
}

.login__error {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-color-danger);
}

/* 主操作：胶囊按钮（圆角 = 半高） */
.login__submit {
  height: 40px;
  border: none;
  border-radius: var(--web-radius-pill);
  background: var(--el-color-primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.login__submit:hover {
  opacity: 0.9;
}

.login__submit:active {
  opacity: 0.75;
}

.login__submit:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>
