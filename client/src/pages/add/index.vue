<script setup lang="ts">
/**
 * 添加 tab（占位；功能与接口待定义，见 docs/ui/client 规划）。
 * 内容未定义前先给可导航、可运行的占位骨架，保证 5 项 TabBar 完整可用。
 */
import TabBar from '@/components/base/TabBar.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import { navMetrics } from '@/utils/safeArea'

/** 顶部安全区：避开状态栏 / 刘海与右上角胶囊 */
const topInset = navMetrics().topInset
</script>

<template>
  <view class="page" :style="{ paddingTop: topInset + 'px' }">
    <view class="topbar">
      <text class="topbar__title">添加</text>
    </view>

    <EmptyState title="添加功能建设中" subtitle="敬请期待" />

    <TabBar active="add" />
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
</style>
