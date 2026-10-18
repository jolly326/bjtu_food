<script setup lang="ts">
/**
 * 「去品尝 / 保存 / 提问」**按钮（规格见 docs/ui/client/公共组件与形态基线.md §2.2）。
 *
 * - **形态**：**胶囊描边式** —— 仅「文案 + 外围边框」，**无图标、无装饰**；圆角 `--radius-btn`
 *   （小幅度柔和圆角，Julienne 纸片风，不用全圆）、高度 `--tap-target-size + --spacing-xs`
 *   （96rpx，加高）、左右留白 `--spacing-xl`；文案居中。
 * - **三种变体**（由 `variant` 控制）：
 *   - `make`：**暖橙描边**（`--color-primary`）+ 内部**深色文字**（`--text-title`）；不随按压/选中变色；
 *   - `save` / `ask`：描边 / 文案恒为 `--border-color` + `--text-tertiary`（灰），次要 CTA。
 * - **文案**：`tasted ? '取消品尝' : '去品尝'`（仅 `make` 变体随 `tasted` 切换文案）；`save`/`ask` 文案固定。
 * - 在途（请求中）仅**禁止点击**，视觉不变。
 * - `disabled`（占位卡用）：只影响可点性，视觉与默认态一致 —— 保证图片未加载时按钮与文案照常可见。
 */
import { computed } from 'vue'

/** 宿主节点虚拟化：组件根直接参与卡片布局 */
defineOptions({ options: { virtualHost: true } })

const BUTTON_LABELS = {
  make: { off: '去品尝', on: '取消品尝' },
  save: '保存',
  ask: '提问',
} as const

const props = withDefaults(
  defineProps<{
    /** 变体：make（主 CTA 暖橙）/ save（灰）/ ask（灰） */
    variant?: 'make' | 'save' | 'ask'
    tasted?: boolean
    loading?: boolean
    disabled?: boolean
  }>(),
  { variant: 'make', tasted: false, loading: false, disabled: false },
)

const emit = defineEmits<{ (e: 'press'): void }>()

function onTap() {
  if (!props.loading && !props.disabled) {
    emit('press')
  }
}

/** 当前按钮应显示的文案 */
const label = computed(() => {
  if (props.variant === 'make') {
    return props.tasted ? BUTTON_LABELS.make.on : BUTTON_LABELS.make.off
  }
  return BUTTON_LABELS[props.variant]
})
</script>

<template>
  <view
    class="want"
    :class="['want--variant-' + props.variant, { 'is-disabled': props.loading || props.disabled }]"
    @tap="onTap"
  >
    <text class="want__label">{{ label }}</text>
  </view>
</template>

<style lang="scss" scoped>
.want {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: var(--want-btn-width);
  height: var(--tap-target-size);
  margin: 0 auto;
  padding: 0 var(--spacing-xl);
  /* 胶囊描边：只有边框 + 充足内留白；小幅度柔和圆角（非全圆） */
  border: var(--want-btn-border) solid var(--border-color);
  border-radius: var(--want-btn-radius);
  background: transparent;
}

/* make：暖橙描边 + 内部深色文字；不随按压/选中变色 */
.want--variant-make {
  border-color: var(--color-primary);
  background: var(--bg-page);
}
.want--variant-make .want__label {
  color: var(--text-title);
}

/* save / ask：次要 CTA —— 恒为灰描边 + 灰文字 */
.want--variant-save,
.want--variant-ask {
  border-color: var(--border-color);
}
.want--variant-save .want__label,
.want--variant-ask .want__label {
  color: var(--text-tertiary);
}

/* 按压反馈：仅透明度微降，不改色、不缩放 */
.want:not(.is-disabled):active {
  opacity: 0.6;
}

.want__label {
  font-size: var(--want-btn-font);
  font-weight: var(--weight-regular);
  color: var(--text-title);
}
</style>
