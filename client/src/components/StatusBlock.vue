<script setup lang="ts">
/**
 * 状态块骨架（加载中 / 加载失败 / 其它整块状态共用）。
 *
 * 统一口径（见 docs/ui/client/公共组件与形态基线.md §2.1.3 / §2.1.7）：
 * - `flex: 1` 占满父级剩余高度并**居中**，背景透明、不整页覆盖；
 * - 图标 56rpx + 文案 `--font-h3` / `--text-title`，加载与失败**同一规格**；
 * - `spinning` 时仅图标本体以 `--duration-spin` 匀速旋转（无外围圆环）；
 * - `pressable` 时整块可点（用于「加载失败 → 点击重试」）。
 */
import AppIcon from './AppIcon.vue'

/** 宿主节点虚拟化：组件根直接参与页面 flex 布局 */
defineOptions({ options: { virtualHost: true } })

withDefaults(
  defineProps<{
    name: string
    text: string
    /** 图标边长（rpx），默认 56（落在宿主盒对齐例外档） */
    size?: number
    /** 图标旋转（加载态） */
    spinning?: boolean
    /** 图标降低不透明度（加载态的弱化表现） */
    dim?: boolean
    /** 整块可点 */
    pressable?: boolean
    /** 无障碍标签（可点块的语义） */
    ariaLabel?: string
    /** 主题标记（首屏状态块需要自带，避免夜间模式闪浅色） */
    theme?: string
  }>(),
  {
    size: 56,
    spinning: false,
    dim: false,
    pressable: false,
    ariaLabel: '',
    theme: '',
  },
)

const emit = defineEmits<{ (e: 'press'): void }>()
</script>

<template>
  <view
    class="status"
    :data-theme="theme || undefined"
    :hover-class="pressable ? 'status--pressed' : 'none'"
    :aria-role="pressable ? 'button' : undefined"
    :aria-label="ariaLabel || undefined"
    @tap="pressable && emit('press')"
  >
    <view
      class="status__icon"
      :class="{ 'status__icon--spin': spinning, 'status__icon--dim': dim }"
    >
      <AppIcon :name="name" :size="size" />
    </view>
    <text class="status__text">{{ text }}</text>
  </view>
</template>

<style lang="scss" scoped>
.status {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  gap: var(--spacing-xs);
  min-height: 0;
  padding: var(--spacing-xl) var(--spacing-lg);
}

.status--pressed {
  opacity: 0.7;
}

.status__icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.status__icon--dim {
  opacity: 0.55;
}

.status__icon--spin :deep(.icon) {
  animation: status-rotate var(--duration-spin) linear infinite;
}

.status__text {
  font-family: var(--font-sans);
  font-size: var(--font-h3);
  font-weight: var(--weight-regular);
  line-height: 1.2;
  color: var(--text-title);
  text-align: center;
}

@keyframes status-rotate {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

/* 🔴 尊重 reduced-motion：旋转关闭 */
@media (prefers-reduced-motion: reduce) {
  .status__icon--spin :deep(.icon) {
    animation: none;
  }
}
</style>
