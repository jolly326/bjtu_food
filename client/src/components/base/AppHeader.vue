<script setup lang="ts">
/**
 * 全局页头（与 `TabBar` 同级的基础设施；规格见 docs/ui/client/公共组件与形态基线.md §2.1.5）。
 *
 * - 三个 tab 页**统一挂载**：左侧明暗切换（`moon` / `sun`），中间品牌标题，右侧为微信原生胶囊避让；
 * - 自身承担顶部安全区：`padding-top` = 状态栏高（含刘海避让），行高 = 导航栏高（与原生胶囊对齐）；
 * - 🔴 明暗切换是**全局能力**（作用于三个 tab 页），故入口必须三页可达 —— 这也是本组件上提到 base 的原因。
 */
import AppIcon from './AppIcon.vue'
import { navMetrics } from '@/utils/safeArea'
import { toggleTheme, useTheme } from '@/utils/theme'

/** 宿主节点虚拟化：组件根直接参与页面 flex 布局（宿主不打断 flex 链路） */
defineOptions({ options: { virtualHost: true } })

withDefaults(
  defineProps<{
    /** 品牌标题（三页统一，默认「知行食记」） */
    title?: string
  }>(),
  { title: '知行食记' },
)

/** 顶部安全区度量：状态栏高（含刘海避让）+ 胶囊带高 */
const { statusBarHeight, navBarHeight } = navMetrics()
/** 全站主题（明暗切换作用于三个 tab 页） */
const theme = useTheme()
</script>

<template>
  <view class="header" :style="{ paddingTop: statusBarHeight + 'px' }">
    <view class="header__row" :style="{ height: navBarHeight + 'px' }">
      <!-- 左：明暗切换（浅色态显 moon、深色态显 sun，预示点击后的目标模式） -->
      <view class="header__toggle" hover-class="header__toggle--pressed" @tap="toggleTheme">
        <AppIcon v-if="theme === 'dark'" name="sun" :size="44" />
        <AppIcon v-else name="moon" :size="44" />
      </view>
      <!-- 中：标题（水平居中） -->
      <view class="header__title">{{ title }}</view>
      <!-- 右：为微信原生胶囊避让（不改动原生组件） -->
      <view class="header__capsuleSpace" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* Header：独立完整通栏矩形，与下方内容以留白作视觉分隔（不加分割线）；
   下留白收窄（16rpx），把纵向空间让给各页主内容区 */
.header {
  flex: none;
  padding-bottom: var(--spacing-sm);
}

.header__row {
  display: flex;
  align-items: center;
  padding: 0 var(--page-gutter);
}

/* 明暗切换按钮：命中区 ≥ 触达下限；图标左缘与页面基准线对齐 */
.header__toggle {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: var(--tap-target-size);
  height: var(--tap-target-size);
}

.header__toggle--pressed {
  opacity: 0.6;
}

/* 标题：衬线 + 常规字重，水平居中（两侧等宽占位 ⇒ 视觉居中） */
.header__title {
  flex: 1;
  font-family: var(--font-serif);
  font-size: var(--font-title);
  font-weight: var(--weight-regular);
  line-height: 1.2;
  color: var(--text-title);
  text-align: center;
}

/* 右侧占位：与左侧切换按钮等宽，保证标题居于屏中 */
.header__capsuleSpace {
  width: var(--tap-target-size);
}
</style>
