<script setup lang="ts">
/**
 * 加载块（规格见 docs/ui/client/公共组件与形态基线.md §2.1.7）。
 *
 * - **非全屏遮罩**：`flex: 1` 占满父级剩余高度并居中 —— 由各页放在自己的内容区里
 *   （美食地图 = 画布区中心），背景透明、不覆盖页面。
 * - **主题跟随全局**：根节点自带 `data-theme`（`useTheme` 模块级单例），
 *   首屏加载时夜间模式同样生效。
 * - 居中「旋转图标 + 无衬线圆体文案」，规格**对齐空态块**：图标 64rpx `muted`（🔴 不上主题色、低透明度），
 *   文案 `--font-h2` 无衬线圆体 regular；图标本体以 `--duration-spin` 匀速旋转（无外围圆环）。
 */
import AppIcon from './AppIcon.vue'
import { useTheme } from '@/theme'

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
      <AppIcon name="arrows-clockwise" :size="64" />
    </view>
    <text class="page-loading__text">{{ text }}</text>
  </view>
</template>

<style lang="scss" scoped>
/* 占满父级剩余高度并居中：与 RetryBlock 同一布局基准；背景透明、不整页覆盖 */
.page-loading {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  min-height: 0;
}

/* 只有图标本体匀速旋转 —— 不叠额外的呼吸 / 缩放动效（基线 §2.1.7） */
.page-loading__icon {
  display: flex;
  margin-bottom: var(--spacing-xs);
  opacity: 0.55;
}

.page-loading__icon :deep(.icon) {
  animation: page-loading-rotate var(--duration-spin) linear infinite;
}

/* 文案与空态标题同字阶（无衬线圆体 --font-h2 + regular + --text-title），保持视觉一致 */
.page-loading__text {
  font-family: var(--font-sans);
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

/* 🔴 尊重 reduced-motion：旋转一并关闭 */
@media (prefers-reduced-motion: reduce) {
  .page-loading__icon :deep(.icon) {
    animation: none;
  }
}
</style>
