<script setup lang="ts">
/**
 * 底部**胶囊 Tab**（自绘；口径见 docs/ui/client/公共组件与形态基线.md §2.1.1）。
 * 三项：风物 / 美味 / 记忆 —— **全宽通栏容器**底置（`bottom: 0` + 安全区内距），
 * 胶囊在容器内**垂直居中**，**宽 = 海报卡宽、左缘与海报卡左缘对齐**（非沉底全宽菜单栏）。
 * `pages.json` 的 `tabBar.custom = true` 隐藏原生栏，本组件顶替。
 *
 * 图标与文案一一对应：风物=image、美味=dish、记忆=notebook（Julienne 语汇：图像 / 餐具 / 手账）。
 * 形态：**一个外环胶囊包裹三项**（仅 `1rpx` 浅灰描边，**无底色 / 无阴影**）；
 * 每项内层自带胶囊，选中项内层 `--color-primary-soft` 浅橙填充 + 主色图标 / 文字，未选无填充。
 * 图标与文字视觉尺寸配平（40rpx 线性图标 + 26rpx 衬线标签）；三项在胶囊内水平均分、垂直居中。
 */
import AppIcon from './AppIcon.vue'

/** 宿主节点虚拟化：组件根直接参与页面布局（宿主不打断布局链路） */
defineOptions({ options: { virtualHost: true } })

defineProps<{ active: 'scenery' | 'cuisine' | 'memory' }>()

const ITEMS = [
  { key: 'scenery', path: '/pages/scenery/index', icon: 'image', label: '风物' },
  { key: 'cuisine', path: '/pages/cuisine/index', icon: 'dish', label: '美味' },
  { key: 'memory', path: '/pages/memory/index', icon: 'notebook', label: '记忆' },
] as const

function go(path: string) {
  uni.switchTab({ url: path })
}
</script>

<template>
  <view class="tabbar">
    <!-- 容器内容区：高 = 胶囊高 + 上下呼吸位；胶囊在其中垂直居中 -->
    <view class="tabbar__slot">
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
              :size="40"
              :tone="item.key === active ? 'primary' : 'default'"
            />
            <text class="tabbar__label" :class="{ 'tabbar__label--on': item.key === active }">
              {{ item.label }}
            </text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 全宽通栏容器：底置，底抵屏幕有效可视区底部（不含系统手势横条）；空白区点击穿透 */
.tabbar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: var(--z-tabbar);
  padding-bottom: env(safe-area-inset-bottom);
  pointer-events: none;
}

/* 容器内容区：高 = --tabbar-height + --tabbar-gap + --spacing-md（页面让位同值）；
   胶囊在其中**水平 + 垂直居中** */
.tabbar__slot {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: calc(var(--tabbar-height) + var(--tabbar-gap) + var(--spacing-md));
  pointer-events: none;
}

/* 外胶囊：整行唯一外环 —— 仅 1rpx 浅灰描边，**无底色 / 无阴影**；
   宽 = 海报卡宽（屏宽 − 左 gutter × 2 − 露出 24rpx），**在容器内水平居中** */
.tabbar__capsule {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  box-sizing: border-box;
  width: calc(100vw - var(--page-gutter) * 2 - var(--spacing-md));
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

/* 标签：Julienne 文艺衬线 + 放大字号（与 40rpx 图标视觉配平） */
.tabbar__label {
  font-family: var(--font-serif);
  font-size: var(--font-body);
  line-height: 1;
  color: var(--color-brown-deep);
}

.tabbar__label--on {
  color: var(--color-primary-text);
  font-weight: var(--weight-semibold);
}
</style>
