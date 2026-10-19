<script setup lang="ts">
/**
 * Tab 页外壳（三 tab 页统一挂载；规格见 docs/ui/client/公共组件与形态基线.md §2.1.x）。
 *
 * 统一承担：全局页头 `AppHeader`、页面根 flex 骨架、底部 `TabBar` 与 TabBar 让位内距；
 * 页面只填两个插槽——`hero`（行 2：问候 / 标题 + 日期 + 筛选 / 标题 + 日期）与默认插槽
 * （行 3：海报区 / 菜品卡片栈 / 记忆列表），二者在三个 tab 页处于同一屏幕位置。
 */
import AppHeader from './AppHeader.vue'
import TabBar from './TabBar.vue'
import { useTheme } from '@/utils/theme'

/** 宿主节点虚拟化：组件根直接参与页面 flex 布局（宿主不虚拟化会打断 flex 链路） */
defineOptions({ options: { virtualHost: true } })

withDefaults(
  defineProps<{
    /** 当前激活的 tab（驱动 TabBar 选中态） */
    active: 'scenery' | 'cuisine' | 'memory'
  }>(),
  {},
)

/** 全站主题（明暗切换作用于三个 tab 页） */
const theme = useTheme()
</script>

<template>
  <view class="page" :data-theme="theme">
    <AppHeader />
    <slot name="hero" />
    <view class="page__main">
      <slot />
    </view>
    <TabBar :active="active" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: 100vh;
  overflow: hidden;
  /* TabBar 让位：统一由页面根承担（含安全区）；内容区（含滚动区）不得重复让位 */
  padding-bottom: calc(
    var(--tabbar-height) + var(--tabbar-gap) + var(--spacing-md) + env(safe-area-inset-bottom)
  );
}

/* 内容区：占满页头与 TabBar 之间的纵向空间；作为行 3 插槽容器，
   内部由页面自行排布（flex: 1 子项撑满） */
.page__main {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}
</style>
