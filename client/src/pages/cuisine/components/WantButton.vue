<script setup lang="ts">
/**
 * 「加入记忆」按钮（单向写入，不提供取消）。
 *
 * - 取消操作应在「记忆」Tab 页从已有记录中删除，此处只负责加入。
 * - **形态**：左侧本子图标 + 右侧文案「加入记忆」，胶囊描边式；
 *   宽度由内容撑起（不设最小宽）、高 ≥ `--tap-target-size`(88rpx)，
 *   左右内边距 `--spacing-lg`、图文间距 `--spacing-xs`。
 * - **配色**：低饱和暖棕边框（`--want-btn-border-color`）+ 卡片底色填充（`--bg-card`）+ 深色文字（`--text-title`）。
 * - **禁用**：`loading`（请求中）或 `disabled`（占位卡）时禁用。
 * - 点击会触发 `@press` 事件，调用方负责调用 `recordApi.want`。
 */
import AppIcon from '@/components/base/AppIcon.vue'

/** 宿主节点虚拟化：组件根直接参与卡片布局 */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    loading?: boolean
    disabled?: boolean
  }>(),
  { loading: false, disabled: false },
)

const emit = defineEmits<{ (e: 'press'): void }>()

function onTap() {
  if (!props.loading && !props.disabled) {
    emit('press')
  }
}
</script>

<template>
  <view
    class="want"
    :class="{ 'is-disabled': props.loading || props.disabled }"
    @tap="onTap"
  >
    <AppIcon name="notebook" :size="30" tone="default" />
    <text class="want__label">加入记忆</text>
  </view>
</template>

<style lang="scss" scoped>
.want {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  box-sizing: border-box;
  min-height: var(--tap-target-size);
  /* 宽度由内容撑起，卡内居中；横向内边距给出舒展呼吸位 */
  padding: var(--spacing-sm) var(--spacing-lg);
  /* 胶囊描边：低饱和暖棕边框、圆角加大、描边减细 */
  border: var(--want-btn-border) solid var(--want-btn-border-color);
  border-radius: var(--want-btn-radius);
  background: var(--bg-card);
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