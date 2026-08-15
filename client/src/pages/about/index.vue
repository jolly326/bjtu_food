<template>
  <view class="page about-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="关于我们" @back="backToHome" />
    <scroll-view class="scroll-wrap" scroll-y :scroll-with-animation="!reduceMotion">
      <view class="hero">
        <view class="logo">
          <IconSvg name="comment" :size="64" color="var(--color-primary)" />
        </view>
        <text class="app-name">知行食记</text>
        <text class="app-version">版本 1.0.0</text>
      </view>

      <view class="card">
        <text class="card-title">关于产品</text>
        <text class="card-text">「知行食记」是北京交通大学专属的美食发现社区，致力于帮助同学快速找到食堂里值得一试的好菜，分享真实评价，让每一餐都更有选择。</text>
      </view>

      <view class="card">
        <text class="card-title">核心功能</text>
        <view class="feature"><text class="dot" />菜品发现与评分分布</view>
        <view class="feature"><text class="dot" />真实评价与动态分享</view>
        <view class="feature"><text class="dot" />食堂与档口一站式检索</view>
        <view class="feature"><text class="dot" />校园活动与运营通知</view>
      </view>

      <view class="card">
        <text class="card-title">联系我们</text>
        <view class="contact-row">
          <text class="contact-label">团队</text>
          <text class="contact-value">知行食记开发组</text>
        </view>
        <view class="contact-row">
          <text class="contact-label">邮箱</text>
          <text class="contact-value">support@bjtufood.cn</text>
        </view>
      </view>

      <view class="footer-note">北京交通大学 · 校园美食社区</view>
      <view style="height: calc(var(--spacing-lg) + env(safe-area-inset-bottom))" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import IconSvg from '@/components/IconSvg.vue'

const theme = useThemeStore()

const reduceMotion = ref(false)
if (typeof window !== 'undefined') {
  reduceMotion.value = !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg-page);
}
.scroll-wrap {
  height: calc(100vh - var(--header-h, 88rpx));
  box-sizing: border-box;
}
.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: calc(var(--spacing-xl) + 20rpx) var(--spacing-md) var(--spacing-lg);
}
.logo {
  width: 120rpx;
  height: 120rpx;
  border-radius: 32rpx;
  background: var(--color-primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.app-name {
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
  color: var(--text-primary);
}
.app-version {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}

.card {
  margin: 0 var(--spacing-md) var(--spacing-md);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}
.card-title {
  font-size: var(--font-card);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.card-text {
  font-size: var(--font-body);
  color: var(--text-secondary);
  line-height: 1.7;
}
.feature {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-body);
  color: var(--text-secondary);
}
.dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: var(--color-primary);
  flex-shrink: 0;
}
.contact-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}
.contact-label {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  width: 80rpx;
  flex-shrink: 0;
}
.contact-value {
  font-size: var(--font-body);
  color: var(--text-primary);
}
.footer-note {
  text-align: center;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  padding: var(--spacing-md) 0;
}
</style>
