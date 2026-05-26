<template>
  <view class="custom-tab-bar">
    <view
      v-for="(item, index) in tabs"
      :key="index"
      class="tab-item"
      :class="{ active: current === item.page }"
      @tap="switchTab(item.page)"
    >
      <image class="tab-icon" :src="current === item.page ? item.activeIcon : item.icon" />
      <text class="tab-text">{{ item.text }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
const props = defineProps<{
  current: string
}>()

const tabs = [
  { text: '首页', page: '/pages/home/index', icon: '/static/icons/Homepage.svg', activeIcon: '/static/icons/Homepage-active.svg' },
  { text: '发现', page: '/pages/find/index', icon: '/static/icons/search.svg', activeIcon: '/static/icons/search-active.svg' },
  { text: '收藏', page: '/pages/favorite/index', icon: '/static/icons/like.svg', activeIcon: '/static/icons/like-active.svg' },
  { text: '我的', page: '/pages/profile/index', icon: '/static/icons/user.svg', activeIcon: '/static/icons/user-active.svg' },
]

function switchTab(page: string) {
  if (page === props.current) return
  uni.reLaunch({ url: page })
}
</script>

<style scoped>
.custom-tab-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  background: var(--bg-card);
  box-shadow: 0 -4rpx 12rpx rgba(0, 0, 0, 0.06);
  padding:10rpx 0 env(safe-area-inset-bottom);
  z-index: 100;
  height: 100rpx;
}
.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
}
.tab-icon {
  width: 48rpx;
  height: 48rpx;
}
.tab-text {
  font-size: 24rpx;
  color: var(--text-tertiary);
}
.tab-item.active .tab-text {
  color: var(--color-primary);
}
</style>
