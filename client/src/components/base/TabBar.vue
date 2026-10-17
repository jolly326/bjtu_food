<script setup lang="ts">
/**
 * 底部**胶囊 Tab**（自绘；口径见 docs/ui/client/公共组件与形态基线.md §2.1.1）。
 * 三项：发现 / 美食 / 记忆 —— 悬浮胶囊（**横向撑满、三项等分**，不贴屏边、非沉底固定菜单栏）；
 * `pages.json` 的 `tabBar.custom = true` 隐藏原生栏，本组件顶替。
 *
 * 图标与文案一一对应：发现=compass、美食=dish、记忆=clock。
 * 形态：**一个外环胶囊包裹三项**（仅 `1rpx` 浅灰描边，**无底色 / 无阴影**，悬浮于页面底色之上）；
 * 每项内层自带胶囊，选中项内层 `--color-primary-soft` 浅橙填充 + 主色图标 / 文字，未选无填充。
 */
import AppIcon from './AppIcon.vue'

defineProps<{ active: 'discover' | 'recipes' | 'records' }>()

const ITEMS = [
  { key: 'discover', path: '/pages/discover/index', icon: 'compass', label: '发现' },
  { key: 'recipes', path: '/pages/recipes/index', icon: 'dish', label: '美食' },
  { key: 'records', path: '/pages/records/index', icon: 'clock', label: '记忆' },
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
        <view class="tabbar__inner">
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
  padding: var(--spacing-xs) var(--spacing-md);
  pointer-events: none;
}

/* 外胶囊：整行唯一外环 —— 仅 1rpx 浅灰描边，**无底色 / 无阴影**（悬浮不沉底）；全向内边距 + 项间 gap 同值 */
.tabbar__capsule {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  box-sizing: border-box;
  height: var(--tabbar-height);
  padding: var(--spacing-xs);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-pill);
  pointer-events: auto;
}

/* 项：无描边无底色，flex:1 等分、横向居中 */
.tabbar__item {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: 100%;
  border-radius: var(--radius-pill);
}

.tabbar__inner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  width: 100%;
  height: 100%;
  border-radius: var(--radius-pill);
  transition: background-color var(--duration-base) var(--ease-out);
}

.tabbar__item--on .tabbar__inner {
  background: var(--color-primary-soft);
}

.tabbar__item--pressed {
  opacity: 0.6;
}

.tabbar__label {
  font-size: var(--font-tiny);
  line-height: 1;
  color: var(--color-brown-deep);
}

.tabbar__label--on {
  color: var(--color-primary-text);
  font-weight: var(--weight-semibold);
}
</style>
