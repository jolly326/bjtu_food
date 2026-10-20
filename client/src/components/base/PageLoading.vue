<script setup lang="ts">
/**
 * 加载块（规格见 docs/ui/client/公共组件与形态基线.md §2.1.7）。
 *
 * - **非全屏遮罩**：`flex: 1` 占满父级剩余高度并居中 —— 由各页放在自己的内容区里
 *   （风物 = 海报区中心 / 美味 = 卡片栈中心 / 记忆 = 内容区中心），背景透明、不覆盖页面。
 * - **主题跟随全局**：根节点自带 `data-theme`（`useTheme` 模块级单例），
 *   即使挂在 TabPage 之外（首屏加载）夜间模式同样生效。
 * - 居中「旋转图标 + 衬线文案」，规格**对齐空态块**：图标 64rpx `muted`（🔴 不上主题色、低透明度），
 *   文案 `--font-h2` 衬线 regular；仅图标本体以 `--duration-spin` 匀速旋转（无外围圆环）。
 */
import AppIcon from './AppIcon.vue'
import { useTheme } from '@/utils/theme'

defineOptions({ options: { virtualHost: true } })

const theme = useTheme()

withDefaults(
  defineProps<{
    /** 加载提示文案，默认「加载中」 */
    text?: string
  }>(),
  { text: '加载中' },
)
</script>

<template>
  <view class="page-loading" :data-theme="theme">
    <view class="page-loading__icon">
      <AppIcon name="refresh-cw" :size="64" tone="muted" />
    </view>
    <text class="page-loading__text">{{ text }}</text>
  </view>
</template>

<style lang="scss" scoped>
/* 占满父级剩余高度并居中：与 RetryBlock / EmptyBlock 同一布局基准；背景透明、不整页覆盖 */
.page-loading {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  min-height: 0;
}

/* 仅图标本体旋转：无圆环、无描边、无底色；色调用 muted（🔴 不上主题色，与空态图标同语言） */
.page-loading__icon {
  display: flex;
  margin-bottom: var(--spacing-xs);
  opacity: 0.55;
  animation: page-loading-rotate var(--duration-spin) linear infinite;
}

/* 文案与空态标题同字阶（衬线 --font-h2 + regular + --text-title），保持视觉一致 */
.page-loading__text {
  font-family: var(--font-serif);
  font-size: var(--font-h2);
  font-weight: var(--weight-regular);
  line-height: 1.2;
  color: var(--text-title);
  text-align: center;
}

@keyframes page-loading-rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
