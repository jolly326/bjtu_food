<template>
  <!-- 本组件承载**带返回的二级页**顶栏：**左「返回」（文字）+ 居中页面名**（2026-09-27 决议）。
       与 `AppTitleBand` 的分工（用户裁定：**两个组件、按需显示**，不合并）：
       · 有返回 ⇒ 用本组件：左「返回」文字、标题**居中**（相对导航行真正水平居中）；
       · 无返回 ⇒ 用 `AppTitleBand`：标题**居左**，无返回控件、无右操作。
       ⚠️ 本组件在 `show-back=false` 时退化为「无返回」形态（标题居左），供 TabBar 主根页使用。
       表面：**恒透明**（2026-09-27 结构性决议）——背后即 `fixed` 页底壁纸；不得加实底 / 蒙版。
       右操作：默认插槽（如通知页「全部已读」），自动避让微信右上角原生胶囊（`navPadRight`）。 -->
  <view
    class="header-wrap"
    :style="{
      paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))',
      '--nav-h': navBarHeight + 'px',
      '--nav-pr': navPadRight,
    }"
  >
    <view class="nav" :class="{ 'nav--with-back': showBack }" :style="{ height: navBarHeight + 'px' }">
      <!-- 左：返回（**文字「返回」**，替换原箭头 icon；触达区撑满导航行高 ≥88rpx） -->
      <view
        v-if="showBack"
        class="back-area"
        role="button"
        aria-label="返回"
        hover-class="pressed"
        @tap="handleBack"
      >
        <text class="back-text">返回</text>
      </view>

      <!-- 标题：有返回 ⇒ 绝对居中；无返回 ⇒ 靠左（与 `AppTitleBand` 同位置） -->
      <text class="title" :class="{ 'title--left': !showBack }">{{ title }}</text>

      <!-- 右：页面级操作（可选），右边界 = 胶囊避让量。
           同时提供 `action` **具名插槽**：消费方以 `<template #action>` 传入右侧操作
           （如通知页「全部已读」胶囊）—— 只有默认插槽时该内容会被静默丢弃（Round 31 修复）。 -->
      <view class="nav-actions"><slot /><slot name="action" /></view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * AppHeader —— 带返回的二级页顶栏（透明 + 左「返回」文字 + 居中页面名）
 *
 * 消费方（7 处）：`profile` / `privacy/DocPage` / `notifications`（带右操作槽） / `my-reviews` /
 * `feedback` / `auth`，以及 `mine`（`show-back=false` ⇒ 标题居左的退化形态）。
 *
 * 表面口径：恒透明（不含任何底色 / 蒙版）——背后即 `fixed` 页底壁纸（UI 文档 §11.1 / 循环 R12）。
 */
import { useNavMetrics } from '@/utils/useNavMetrics'

withDefaults(defineProps<{
  title?: string
  /**
   * 是否显示返回（渲染为文字「返回」）；从首页头像 navigateTo 进入二级页时传 true，TabBar 直入时传 false。
   * ⚠️ `false` ⇒ 标题改为**居左**（与 `AppTitleBand` 一致），不再是「无控件的居中标题」。
   */
  showBack?: boolean
}>(), {
  title: '',
  showBack: true,
})

const emit = defineEmits<{
  (e: 'back'): void
}>()

/* 顶部度量：一律走跨页统一实现 `useNavMetrics`（UI 统一 Loop Round 7 收口）。
   本组件消费三项：状态栏高（顶部安全区）、导航行高（胶囊所在行）、右侧胶囊避让量（右操作槽用）。 */
const { statusBarPx: statusBarHeight, navBarHeightPx: navBarHeight, navPadRight } = useNavMetrics()

function handleBack() {
  emit('back')
}
</script>

<style scoped>
/* 恒透明（2026-09-27 结构性决议）：不再有实心主色底 —— 背后即 `fixed` 页底壁纸；
   保留 sticky + `--z-header` 与底部留白（留白 = 全站 header 总高基准 `--spacing-sm`）。 */
.header-wrap {
  width: 100%;
  box-sizing: border-box;
  background: transparent;
  border-bottom: none;
  position: sticky;
  top: 0;
  z-index: var(--z-header);
  padding-bottom: var(--spacing-sm);
}

/* ===== 导航行：左「返回」 + 标题 + 右操作 ===== */
.nav {
  display: flex;
  align-items: center;
  position: relative;
  box-sizing: border-box;
}
/* 返回：绝对定位在左，`top/bottom: 0` 撑满行高 ⇒ 触达区 = 导航行高（≥88rpx，Apple 44pt 下限） */
.back-area {
  position: absolute;
  left: var(--spacing-md);
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  padding-right: var(--spacing-sm);
  -webkit-tap-highlight-color: transparent;
}
.back-area.pressed { opacity: 0.6; }
/* 「返回」文字：与标题**完全同级**（同字号 + 同字重）—— 2026-09-27 裁决「各页统一成首页大小」：
   字号 `--font-title`(44rpx) + 粗体，与 `AppTitleBand` / 首页「知行食记」一致。 */
.back-text {
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
  color: var(--text-primary);
}
/* 标题（有返回 ⇒ 居中）：相对导航行真正水平居中，不受左侧返回宽度影响 */
.title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  text-align: center;
  /* 2026-09-27 裁决「各页统一成首页大小」：字号 `--font-title`(44rpx) + 粗体，与首页「知行食记」同档 */
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
  color: var(--text-primary);
  max-width: 56%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 标题（无返回 ⇒ 居左）：与 `AppTitleBand` 的位置 / 字号 / 字重一致（44rpx 粗体大号） */
.title--left {
  position: static;
  left: auto;
  transform: none;
  text-align: left;
  margin-left: var(--spacing-md);
  max-width: none;
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
}
/* 右操作槽：右边界避开微信原生胶囊（`--nav-pr` = useNavMetrics 的胶囊避让量） */
.nav-actions {
  position: absolute;
  right: var(--nav-pr, 180rpx);
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
}
</style>
