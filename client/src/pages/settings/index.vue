<script setup lang="ts">
/**
 * 设置 tab（占位骨架 + 已接入入口）。
 * 「我的食谱」跳转至回味页（records）；其余设置项待定义。
 */
import TabBar from '@/components/base/TabBar.vue'
import AppIcon from '@/components/base/AppIcon.vue'
import { navMetrics } from '@/utils/safeArea'

/** 顶部安全区：避开状态栏 / 刘海与右上角胶囊 */
const topInset = navMetrics().topInset

/** 「我的食谱」：进入回味（records）子页 */
function openRecords() {
  uni.navigateTo({ url: '/pages/records/index' })
}
</script>

<template>
  <view class="page" :style="{ paddingTop: topInset + 'px' }">
    <view class="topbar">
      <text class="topbar__title">设置</text>
    </view>

    <view class="group">
      <view class="row" hover-class="row--pressed" @tap="openRecords">
        <AppIcon name="book" :size="28" tone="default" />
        <text class="row__label">我的食谱</text>
        <AppIcon name="chevron-right" :size="28" tone="default" />
      </view>
    </view>

    <TabBar active="settings" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: 100vh;
  overflow: hidden;
  padding-bottom: calc(
    var(--tabbar-height) + var(--tabbar-gap) + var(--spacing-md) + env(safe-area-inset-bottom)
  );
}

.topbar {
  padding: var(--spacing-md) var(--page-gutter);
}

.topbar__title {
  font-size: var(--font-display);
  font-weight: var(--weight-bold);
  line-height: 1.2;
  color: var(--text-title);
}

.group {
  padding: 0 var(--page-gutter);
}

.row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  height: var(--tap-target-size);
  border-bottom: 1rpx solid var(--border-color);
}

.row--pressed {
  opacity: 0.7;
}

.row__label {
  flex: 1;
  font-size: var(--font-body);
  color: var(--text-title);
}
</style>
