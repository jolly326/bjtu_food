<template>
  <!-- 本组件承载**带返回的二级页**顶栏：**左「返回」（文字）+ 居中页面名**。
       与 `AppTitleBand` 的分工（用户裁定：**两个组件、按需显示**，不合并）：
       · 有返回 ⇒ 用本组件：左「返回」文字、标题**居中**（相对导航行真正水平居中）；
       · 无返回 ⇒ 用 `AppTitleBand`：标题**居左**，无返回控件。
       ⚠️ 本组件在 `show-back=false` 时退化为「无返回」形态（标题居左），供 TabBar 主根页使用。
       表面：**恒透明**——背后即 `fixed` 页底壁纸；不得加实底 / 蒙版。
       页头**恒不承载业务操作**：微信原生胶囊占掉右侧约 `--nav-pr` 的宽度，行内余量不足以并存
       「右操作 + 绝对居中标题」⇒ 批量动作落**列表上方动作行**，主操作落**底部固定操作栏**。 -->
  <view
    class="header-wrap"
    :style="{
      paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))',
      '--nav-h': navBarHeight + 'px',
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
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * AppHeader —— 带返回的二级页顶栏（透明 + 左「返回」文字 + 居中页面名）
 *
 * 消费方（8 处）：`profile` / `privacy/DocPage` / `notifications` / `my-reviews` / `feedback` /
 * `correction` / `auth`，以及 `mine`（`show-back=false` ⇒ 标题居左的退化形态）。
 *
 * 表面口径：恒透明（不含任何底色 / 蒙版）——背后即 `fixed` 页底壁纸（UI 文档 §11.1 / 循环 R12）。
 * 无右操作能力：标题与返回不被任何页头控件挤占宽度（主操作落底部固定操作栏、批量动作落列表上方动作行）。
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

/* 顶部度量：一律走跨页统一实现 `useNavMetrics`。
   本组件消费两项：状态栏高（顶部安全区）、导航行高（胶囊所在那一行）。 */
const { statusBarPx: statusBarHeight, navBarHeightPx: navBarHeight } = useNavMetrics()

function handleBack() {
  emit('back')
}
</script>

<style scoped>
/* 恒透明：不再有实心主色底 —— 背后即 `fixed` 页底壁纸；
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
  left: var(--page-gutter);
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  padding-right: var(--spacing-sm);
  -webkit-tap-highlight-color: transparent;
}
.back-area.pressed { opacity: 0.6; }
/* 「返回」文字：与标题**完全同级**（同字号 + 同字重）—— 裁决「各页统一成首页大小」：
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
  /* 裁决「各页统一成首页大小」：字号 `--font-title`(44rpx) + 粗体，与首页「知行食记」同档 */
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
  margin-left: var(--page-gutter);
  max-width: none;
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
}
</style>
