<template>
  <view class="page verify-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="学号邮箱认证" @back="goBack" />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 已认证态：显示绑定邮箱 + 认证状态 -->
      <view v-if="isVerified" class="verified-card">
        <view class="verified-icon">
          <IconSvg name="check" :size="44" color="var(--color-on-primary)" />
        </view>
        <text class="verified-title">已通过校园认证</text>
        <text class="verified-mail">{{ bindEmail || '已绑定校园邮箱' }}</text>
        <text class="verified-note">已解锁发布 / 评价 / 点赞 / 动态等社区写操作</text>
      </view>

      <!-- 未认证态：认证说明 + 学号邮箱认证表单 -->
      <template v-else>
        <view class="hero">
          <text class="hero-title">认证解锁社区</text>
          <text class="hero-note">完成学号邮箱认证后，即可发布菜品、写评价、点赞、发动态</text>
        </view>

        <!-- 复用认证表单（与 AuthSheet 弹层同一表单逻辑，§2.4） -->
        <view class="form-card">
          <AuthForm :codeCountdown="codeCooldown" @cooldown-change="onCooldownChange" />
        </view>
      </template>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import IconSvg from '@/components/IconSvg.vue'
import AuthForm from '@/components/AuthForm.vue'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'
import { backToHome } from '@/utils/nav'

const theme = useThemeStore()
const userStore = useUserStore()

const isVerified = computed(() => userStore.isVerified())
const bindEmail = computed(() => userStore.userInfo?.bindEmail || '')

// 认证页独立的发码冷却（与 AuthForm 同步，离开页面后重进仍可续接）
const codeCooldown = ref(0)
function onCooldownChange(v: number) {
  codeCooldown.value = v
}

function goBack() {
  backToHome()
}

onLoad(() => {
  // 进入本页时确保静默登录已就绪（游客态才有认证前提）
  userStore.silentLogin()
})
</script>

<style scoped>
.verify-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-xl) + env(safe-area-inset-bottom)); box-sizing: border-box; }

/* ===== 未认证态：hero + 表单卡 ===== */
.hero { padding: var(--spacing-md) var(--spacing-xs) var(--spacing-lg); }
.hero-title { display: block; font-size: var(--font-h2); font-weight: var(--weight-bold); color: var(--text-primary); letter-spacing: var(--tracking-h2); }
.hero-note { display: block; margin-top: var(--spacing-sm); font-size: var(--font-aux); line-height: 1.6; color: var(--text-tertiary); }

.form-card {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: var(--spacing-lg);
}

/* ===== 已认证态：成功卡片 ===== */
.verified-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-lg);
  padding: var(--spacing-xl) var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  text-align: center;
}
.verified-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: var(--color-success);
  display: flex;
  align-items: center;
  justify-content: center;
}
.verified-title { font-size: var(--font-h3); font-weight: var(--weight-bold); color: var(--text-primary); }
.verified-mail { font-size: var(--font-card); font-weight: var(--weight-semibold); color: var(--color-primary); }
.verified-note { font-size: var(--font-aux); line-height: 1.5; color: var(--text-tertiary); }
</style>
