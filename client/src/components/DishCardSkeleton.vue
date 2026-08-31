<template>
  <!-- 首屏骨架占位（global-ui-polish / ui-feed-loading）：与 DishCard 同宽高，shimmer 走全局 .skeleton 工具类 -->
  <view class="dish-skeleton">
    <view class="sk-image skeleton" />
    <view class="sk-body">
      <view class="sk-line sk-w70 skeleton" />
      <view class="sk-line sk-w40 skeleton" />
      <view class="sk-line sk-w90 skeleton" />
      <view class="sk-row">
        <view class="sk-chip skeleton" />
        <view class="sk-price skeleton" />
      </view>
    </view>
  </view>
</template>

<style scoped>
.dish-skeleton {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}
.sk-image { width: 100%; height: 200rpx; }
.sk-body { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-sm); }
.sk-line { height: 24rpx; border-radius: var(--radius-card); }
.sk-w70 { width: 70%; }
.sk-w40 { width: 40%; }
.sk-w90 { width: 90%; }
.sk-row { display: flex; align-items: center; justify-content: space-between; margin-top: var(--spacing-xs); }
.sk-chip { width: 96rpx; height: 32rpx; border-radius: var(--radius-pill); }
.sk-price { width: 96rpx; height: 32rpx; border-radius: var(--radius-card); }
</style>
