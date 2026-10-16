<script setup lang="ts">
/**
 * 底部**胶囊 Tab**（自绘；口径见 docs/ui/client/公共组件与形态基线.md §2.1.1）。
 * 三项：橱窗 / 美食 / 回味 —— 悬浮胶囊（不占满宽、**非沉底固定菜单栏**）；
 * `pages.json` 的 `tabBar.custom = true` 隐藏原生栏，本组件顶替。
 * 每项：图标**在上**、文字**在下**（图标大于文字），两者水平居中对齐；选中 = `primary` 色调 + 主色文字 + 柔光底。
 */
import AppIcon from './AppIcon.vue'

defineProps<{ active: 'poster' | 'dish' | 'records' }>()

const ITEMS = [
  { key: 'poster', path: '/pages/poster/index', icon: 'image', label: '橱窗' },
  { key: 'dish', path: '/pages/dish/index', icon: 'dish', label: '美食' },
  { key: 'records', path: '/pages/records/index', icon: 'book', label: '回味' },
] as const

function go(path: string) {
  uni.switchTab({ url: path })
}
</script>

<template>
  <view class="tabbar">
    <view class="tabbar__capsule">
      <view
        v-for="item in ITEMS"
        :key="item.key"
        class="tabbar__item"
        :class="{ 'tabbar__item--on': item.key === active }"
        hover-class="tabbar__item--pressed"
        @tap="go(item.path)"
      >
        <AppIcon :name="item.icon" :size="48" :tone="item.key === active ? 'primary' : 'default'" />
        <text class="tabbar__label" :class="{ 'tabbar__label--on': item.key === active }">{{ item.label }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 悬浮层：只负责「贴底 + 水平居中」，空白区点击穿透 */
.tabbar {
  position: fixed;
  right: 0;
  bottom: calc(var(--tabbar-gap) + env(safe-area-inset-bottom));
  left: 0;
  z-index: var(--z-tabbar);
  display: flex;
  justify-content: center;
  pointer-events: none;
}

/* 胶囊本体：左右留白更宽，避免项与项相互挤压 */
.tabbar__capsule {
  display: flex;
  align-items: center;
  height: var(--tabbar-height);
  padding: 0 var(--spacing-sm);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-pill);
  background: var(--module-bg);
  box-shadow: var(--shadow-bar);
  pointer-events: auto;
}

/* 单项：图标在上、文字在下，整体水平居中 */
.tabbar__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-2xs);
  height: 100%;
  padding: 0 var(--spacing-md);
  border-radius: var(--radius-pill);
}

.tabbar__item--on {
  background: var(--color-primary-soft);
}

.tabbar__item--pressed {
  opacity: 0.6;
}

.tabbar__label {
  font-size: var(--font-tiny);
  line-height: 1;
  color: var(--text-tertiary);
}

.tabbar__label--on {
  color: var(--color-primary-text);
  font-weight: var(--weight-semibold);
}
</style>
