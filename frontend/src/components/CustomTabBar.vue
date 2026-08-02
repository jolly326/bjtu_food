<template>
  <view class="custom-tab-bar">
    <view class="tab-bar-inner">
      <view
        v-for="item in tabs"
        :key="item.page"
        class="tab-item"
        :class="{ active: current === item.page }"
        @tap="switchTab(item.page)"
      >
        <IconSvg class="tab-icon" :name="item.icon" :size="44" :color="current === item.page ? 'var(--color-primary)' : 'var(--text-tertiary)'" />
        <text class="tab-text">{{ item.text }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import IconSvg from './IconSvg.vue'

const props = defineProps<{
  current: string
}>()

// 4 Tab（首页/发现/动态/我的），统一矢量图标（task-15）
const tabs = [
  { text: '首页', page: '/pages/home/index', icon: 'home' },
  { text: '发现', page: '/pages/find/index', icon: 'search' },
  { text: '动态', page: '/pages/community/index', icon: 'comment' },
  { text: '我的', page: '/pages/profile/index', icon: 'profile' },
]

function switchTab(page: string) {
  if (page === props.current) return
  uni.reLaunch({ url: page })
}
</script>

<style scoped>
.custom-tab-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  /* 整条高度 = 内容高 + 底部安全区，白色背景铺满到屏幕底端（iPhone 小横条区域也铺白，不留缝隙） */
  height: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  box-sizing: border-box;
  background: var(--blur-bg-solid);
  border-top: 1rpx solid var(--glass-highlight);
  box-shadow: var(--shadow-bar);
  z-index: 100;
}
.tab-bar-inner {
  display: flex;
  align-items: center;
  /* 仅内容区承载图标，垂直居中，避开底部安全区 */
  height: var(--tabbar-height);
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
  gap: 4rpx;
  transition: transform var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.tab-item:active {
  transform: scale(var(--press-scale));
}
.tab-icon {
  line-height: 1;
  opacity: 0.55;
  transition: opacity 0.2s var(--ease-out), transform 0.12s var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.tab-item.active .tab-icon {
  opacity: 1;
  transform: scale(var(--tab-active-scale));
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
