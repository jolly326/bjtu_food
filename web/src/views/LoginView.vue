<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { setSession } from '@/api/session'

/**
 * 管理员登录页（TD-23）。
 *
 * <p><b>口径真源</b>：[ui/web/登录页.md](../../../docs/ui/web/登录页.md)（页面构成 / 交互与状态）、
 * [api/web/auth.md](../../../docs/api/web/auth.md)（端点与字段）、
 * [UI 基线 §1.7](../../../docs/ui/web/公共组件与形态基线.md)（表单校验与提交中去重）。
 *
 * <p>🔴 **前端不持有口令**：口令只存在于本组件的内存态，仅在 `submit()` 那一次请求体中出现，
 * 成功后只留 token（写入 `sessionStorage`），口令不落任何存储、不进构建产物。
 */
const router = useRouter()

/** 账号（内存态） */
const username = ref('')
/** 密码（内存态，🔴 不得写入任何 storage / 日志） */
const password = ref('')
/** 字段级校验错误 —— 提交时校验、改动即清除（UI 基线 §1.7：SHALL NOT 边输边报错） */
const usernameError = ref('')
const passwordError = ref('')
/** 提交中 —— 主按钮 `:disabled`，防重复提交（§1.7「提交中去重」） */
const submitting = ref(false)

const usernameInput = ref<HTMLInputElement>()

onMounted(async () => {
  // 初始：账号字段自动聚焦（两字段空）
  await nextTick()
  usernameInput.value?.focus()
})

/** 回车等同提交（`<form @submit.prevent>` 承接两个输入框的 Enter） */
async function submit(): Promise<void> {
  usernameError.value = ''
  passwordError.value = ''
  if (!username.value.trim()) usernameError.value = '请输入账号'
  if (!password.value) passwordError.value = '请输入密码'
  if (usernameError.value || passwordError.value) return

  submitting.value = true
  try {
    const res = await login(username.value.trim(), password.value)
    // 成功：token 存 sessionStorage → 跳 /dashboard
    setSession(res.token, res.username)
    await router.replace('/dashboard')
  } catch (e) {
    // 失败：`ElMessage.error(message)` —— 服务端统一为「账号或密码错误」，
    // 不区分账号不存在 / 密码错误（防用户名枚举，见 api/web/auth.md）。
    ElMessage.error(e instanceof Error ? e.message : '登录失败')
    password.value = ''
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <!-- 登录页在管理骨架（侧栏 + 内容区）之外：全屏居中，不含侧栏、页头与导航 -->
  <div class="login-page">
    <form class="card login-card" novalidate @submit.prevent="submit">
      <div class="brand">
        <div class="brand-logo">食</div>
        <div class="brand-text">
          <div class="brand-name">食在交大</div>
          <div class="brand-sub">管理后台</div>
        </div>
      </div>

      <div class="form">
        <div class="field">
          <label for="login-username">账号</label>
          <input
            id="login-username"
            ref="usernameInput"
            v-model="username"
            class="form-input"
            type="text"
            autocomplete="username"
            :aria-invalid="usernameError ? 'true' : undefined"
            :aria-describedby="usernameError ? 'login-username-error' : undefined"
            @input="usernameError = ''"
          />
          <p v-if="usernameError" id="login-username-error" class="field-error">
            {{ usernameError }}
          </p>
        </div>

        <div class="field">
          <label for="login-password">密码</label>
          <input
            id="login-password"
            v-model="password"
            class="form-input"
            type="password"
            autocomplete="current-password"
            :aria-invalid="passwordError ? 'true' : undefined"
            :aria-describedby="passwordError ? 'login-password-error' : undefined"
            @input="passwordError = ''"
          />
          <p v-if="passwordError" id="login-password-error" class="field-error">
            {{ passwordError }}
          </p>
        </div>

        <button class="btn-primary submit" type="submit" :disabled="submitting">
          {{ submitting ? '登录中…' : '登录' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
  box-sizing: border-box;
  background: var(--bg-page);
}

.login-card {
  width: 360px;
  padding: var(--space-6);
  box-sizing: border-box;
}

/* 品牌区 —— 与侧栏品牌区同语汇（见 AdminLayout.vue / 登录页 §页面构成） */
.brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
}
.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: var(--radius);
  background: var(--color-primary);
  color: var(--color-on-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-lg);
  font-weight: var(--weight-semibold);
}
.brand-name {
  font-size: var(--font-lg);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.brand-sub {
  font-size: var(--font-xs);
  color: var(--text-muted);
}

/* 字段间距纵向 --space-4（由表单容器 gap 承担，不写进公共类） */
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

/* 唯一主操作：登录 */
.submit {
  width: 100%;
  margin-top: var(--space-2);
}
</style>
