<template>
  <view class="custom-tab-bar">
    <view class="tab-bar-inner">
      <view
        v-for="(item, index) in tabs"
        :key="index"
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
  bottom: 0;
  left: 0;
  right: 0;
  /* 外层仅负责定位 + 底部安全区内边距（透明，不铺白） */
  padding-bottom: env(safe-area-inset-bottom);
  z-index: 100;
}
/* 白色卡片背景层：固定内容高度，承载图标并居中，安全区在其下方透出页面 */
.tab-bar-inner {
  display: flex;
  height: var(--tabbar-height);
  background: var(--blur-bg-solid);
  border-top: 1rpx solid var(--glass-highlight);
  box-shadow: var(--shadow-bar);
}
@supports ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .tab-bar-inner {
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
