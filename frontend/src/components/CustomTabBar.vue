<template>
  <view class="custom-tab-bar">
    <view
      v-for="(item, index) in tabs"
      :key="index"
      class="tab-item"
      :class="{ active: current === item.page }"
      @tap="switchTab(item.page)"
    >
      <text class="tab-icon">{{ item.icon }}</text>
      <text class="tab-text">{{ item.text }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { EMOJI } from '@/utils/emoji'

const props = defineProps<{
  current: string
}>()

// 红线 §4.9③：MVP 统一用 emoji 占位，不引入 iconfont SVG
// task-06：二期扩展为 4 Tab（首页/发现/动态/我的）
const tabs = [
  { text: '首页', page: '/pages/home/index', icon: EMOJI.home },
  { text: '发现', page: '/pages/find/index', icon: EMOJI.search },
  { text: '动态', page: '/pages/community/index', icon: EMOJI.review },
  { text: '我的', page: '/pages/profile/index', icon: EMOJI.profile },
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
  height: var(--tabbar-height);
  padding-bottom: env(safe-area-inset-bottom);
  background: var(--blur-bg-solid);
  border-top: 1rpx solid var(--glass-highlight);
  box-shadow: var(--shadow-bar);
  z-index: 100;
}
@supports ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .custom-tab-bar {
    background: var(--blur-bg);
    backdrop-filter: blur(var(--blur-radius)) saturate(180%);
    -webkit-backdrop-filter: blur(var(--blur-radius)) saturate(180%);
  }
}
.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  transition: transform var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.tab-item:active {
  transform: scale(var(--press-scale));
}
.tab-icon {
  font-size: 40rpx;
  line-height: 1;
  opacity: 0.55;
  transition: opacity 0.2s var(--ease-out), transform 0.12s var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.tab-item.active .tab-icon {
  opacity: 1;
  transform: scale(1.05);
}
.tab-text {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  transition: color 0.2s var(--ease-out);
}
.tab-item.active .tab-text {
  color: var(--color-primary);
  font-weight: 600;
}
</style>
