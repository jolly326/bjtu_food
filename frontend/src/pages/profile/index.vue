<template>
  <view class="page profile-page">
    <Header title="我的" />

    <!-- ===== 未登录状态：登录表单 ===== -->
    <template v-if="!isLoggedIn">
      <view class="login-section">
        <!-- 学号输入 -->
        <view class="form-group">
          <view class="input-wrap" :class="{ focused: focusField === 'studentId' }">
            <text class="input-icon">👤</text>
            <input
              v-model="form.studentId"
              class="form-input"
              placeholder="学号（如 20240001）"
              type="number"
              @focus="focusField = 'studentId'"
              @blur="focusField = ''"
            />
          </view>
        </view>

        <!-- 验证码输入 -->
        <view class="form-group">
          <view class="input-wrap code-wrap" :class="{ focused: focusField === 'code' }">
            <text class="input-icon">🔐</text>
            <input
              v-model="form.code"
              class="form-input code-input"
              placeholder="验证码"
              type="number"
              @focus="focusField = 'code'"
              @blur="focusField = ''"
            />
            <text
              class="get-code-btn"
              :class="{ disabled: codeCountdown > 0 }"
              @tap="handleGetCode"
            >
              {{ codeCountdown > 0 ? `${codeCountdown}秒后重试` : '获取验证码' }}
            </text>
          </view>
        </view>

        <!-- 登录按钮 -->
        <AppButton text="登 录" margin="0 0 var(--spacing-md)" @tap="handleLogin" />

        <!-- 提示文字 -->
        <view class="login-tips">
          <text class="tip-text">首次登录将自动创建账号</text>
          <text class="tip-text">验证码将发送至 {{ form.studentId }}@bjtu.edu.cn</text>
        </view>
      </view>
    </template>

    <!-- ===== 已登录状态：用户信息 ===== -->
    <template v-else>
      <!-- 用户信息卡片 -->
      <CardSection>
        <view class="user-info">
          <view class="avatar-wrap">
            <image v-if="userInfo?.avatar" :src="userInfo.avatar" class="avatar" />
            <view v-else class="avatar-placeholder">👤</view>
          </view>
          <text class="nickname">{{ userInfo?.nickname || '未知用户' }}</text>
        </view>
      </CardSection>

      <!-- 退出登录 -->
      <view class="logout-wrap">
        <AppButton text="退出登录" margin="var(--spacing-md) 0" @tap="handleLogout" />
      </view>
    </template>

    <CustomTabBar current="/pages/profile/index" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import Header from '@/components/header.vue'
import CardSection from '@/components/CardSection.vue'
import AppButton from '@/components/AppButton.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useUserStore } from '@/stores/user'
import type { UserInfo } from '@/types/user'

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo as unknown as UserInfo | null)
const isLoggedIn = computed(() => userStore.isLoggedIn())

const focusField = ref('')
const codeCountdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const form = ref({
  studentId: '',
  code: '',
})

function startCountdown() {
  codeCountdown.value = 60
  countdownTimer = setInterval(() => {
    codeCountdown.value--
    if (codeCountdown.value <= 0) {
      if (countdownTimer) clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

function handleGetCode() {
  if (codeCountdown.value > 0 || !form.value.studentId) return
  uni.showToast({ title: '验证码已发送', icon: 'success' })
  startCountdown()
}

async function handleLogin() {
  if (!form.value.studentId || !form.value.code) {
    uni.showToast({ title: '请填写学号和验证码', icon: 'none' })
    return
  }
  try {
    await userStore.login(form.value.code)
    uni.showToast({ title: '登录成功', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '登录失败', icon: 'none' })
  }
}

function handleLogout() {
  uni.showModal({
    title: '提示',
    content: '确定退出登录？',
    success: (res) => {
      if (res.confirm) {
        userStore.logout()
      }
    },
  })
}
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(var(--tabbar-height) + 30rpx + env(safe-area-inset-bottom));
}

/* ===== 登录表单区域 ===== */
.login-section {
  padding: 60rpx 30rpx 0;
}
.logout-wrap {
  padding: 0 30rpx;
}

/* ===== Logo ===== */
.logo-area {
  text-align: center;
  margin-bottom: 48rpx;
}
.logo {
  font-size: 48rpx;
  font-weight: 600;
  color: var(--color-primary);
}

/* ===== 表单项 ===== */
.form-group {
  margin-bottom: var(--spacing-md);
}
.input-wrap {
  display: flex;
  align-items: center;
  background: var(--bg-page);
  border-radius: var(--radius-card);
  height: 88rpx;
  padding: 0 24rpx;
  border: 2rpx solid transparent;
  transition: border-color 0.2s;
}
.input-wrap.focused {
  border-color: var(--color-primary);
}
.code-wrap {
  padding-right: 0;
}
.input-icon {
  font-size: 32rpx;
  color: var(--text-tertiary);
  margin-right: var(--spacing-sm);
  flex-shrink: 0;
}
.form-input {
  flex: 1;
  height: 100%;
  font-size: var(--font-body);
  color: var(--text-primary);
  background: transparent;
  border: none;
  outline: none;
}
.code-input {
  margin-right: 8rpx;
}
.get-code-btn {
  font-size: 26rpx;
  color: var(--color-primary);
  padding: 0 24rpx;
  flex-shrink: 0;
  white-space: nowrap;
}
.get-code-btn.disabled {
  color: var(--text-tertiary);
}



/* ===== 提示文字 ===== */
.login-tips {
  text-align: center;
  margin-top: var(--spacing-md);
}
.tip-text {
  display: block;
  font-size: 24rpx;
  color: var(--text-tertiary);
  line-height: 1.6;
}

/* ===== 已登录：用户信息卡片 ===== */
.user-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}
.avatar-wrap {
  flex-shrink: 0;
}
.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: var(--border-color);
}
.avatar-placeholder {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: var(--border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56rpx;
}
.nickname {
  font-size: 32rpx;
  font-weight: 500;
  color: var(--text-primary);
}


</style>
