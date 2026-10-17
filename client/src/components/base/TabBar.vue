<script setup lang="ts">
/**
 * 底部**胶囊 Tab**（自绘；口径见 docs/ui/client/公共组件与形态基线.md §2.1.1）。
 * 五项：Discover / Recipes / Add / Ask / Settings —— 悬浮胶囊（**横向撑满、五项等分**，不贴屏边、非沉底固定菜单栏）；
 * `pages.json` 的 `tabBar.custom = true` 隐藏原生栏，本组件顶替。
 *
 * 形态：每项 = 图标在上、文字在下；**选中态把整个选项铺成胶囊**（不是给图标加底色块）。
 * 内外胶囊**同为全圆**且间距同源：外胶囊 `padding/gap` 与内胶囊距边缘、距相邻项恒为 `--spacing-xs`。
 */
import AppIcon from './AppIcon.vue'

defineProps<{ active: 'discover' | 'recipes' | 'add' | 'ask' | 'settings' }>()

const ITEMS = [
  { key: 'discover', path: '/pages/discover/index', icon: 'compass', label: 'Discover' },
  { key: 'recipes', path: '/pages/recipes/index', icon: 'book', label: 'Recipes' },
  { key: 'add', path: '/pages/add/index', icon: 'plus', label: 'Add' },
  { key: 'ask', path: '/pages/ask/index', icon: 'message-circle', label: 'Ask' },
  { key: 'settings', path: '/pages/settings/index', icon: 'settings', label: 'Settings' },
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
        <AppIcon
          :name="item.icon"
          :size="44"
          :tone="item.key === active ? 'primary' : 'default'"
        />
        <text class="tabbar__label" :class="{ 'tabbar__label--on': item.key === active }">
          {{ item.label }}
        </text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 悬浮层：贴底 + 左右各留 --spacing-md，胶囊横向撑满（不贴屏边）；空白区点击穿透 */
.tabbar {
  position: fixed;
  right: 0;
  bottom: calc(var(--tabbar-gap) + env(safe-area-inset-bottom));
  left: 0;
  z-index: var(--z-tabbar);
  padding: 0 var(--spacing-md);
  pointer-events: none;
}

/* 外胶囊：全圆；全向内边距 + 项间 gap 同值 ⇒ 内胶囊与边缘、与相邻项同距 */
.tabbar__capsule {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  box-sizing: border-box;
  height: var(--tabbar-height);
  padding: var(--spacing-xs);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  box-shadow: var(--module-shadow);
  pointer-events: auto;
}

/* 单项：三项等分 ⇒ 宽松不挤压；图标在上、文字在下，整体水平居中 */
.tabbar__item {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-2xs);
  height: 100%;
  /* 与外胶囊同为全圆 —— 选中时铺成内胶囊 */
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
