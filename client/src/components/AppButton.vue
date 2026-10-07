<template>
  <view
    class="app-btn"
    :class="[btnType, { 'is-disabled': disabled, loading }]"
    :style="btnStyle"
    :aria-label="text"
    :aria-busy="loading ? 'true' : 'false'"
    :aria-disabled="disabled ? 'true' : 'false'"
    role="button"
    tabindex="0"
    :hover-class="disabled || loading ? 'none' : 'pressed'"
    @tap="handleTap"
  >
    <view v-if="loading" class="btn-spinner" aria-hidden="true" />
    <text class="btn-text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  text: string
  /** 按钮型（当前仅实底主色一种；danger/outline 变体不提供） */
  type?: 'primary'
  disabled?: boolean
  loading?: boolean
}>(), {
  type: 'primary',
  disabled: false,
  loading: false,
})

// 自定义事件禁用原生事件名（tap/click）：否则 uni-app 编译 mp-weixin 时父组件
// 监听被当作原生 bindxxx，emit 参数会丢失（同 DishCard 坑，见其注释）。
const emit = defineEmits<{
  press: []
}>()

const btnType = computed(() => `btn-${props.type}`)

const btnStyle = computed(() => ({
  width: '100%',
  margin: '0',
}))

function handleTap() {
  if (props.disabled || props.loading) return
  emit('press')
}
</script>

<style scoped>
.app-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--tap-target-size);
  border-radius: var(--radius-btn);
  box-sizing: border-box;
  gap: var(--spacing-xs);
}
/* 文案：主色底上的白字（禁用档下由下方 is-disabled 规则转为三阶末档灰字） */
.btn-text {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-medium);
  color: var(--color-on-primary);
}
.btn-primary {
  background: var(--color-primary);
}
/* 禁用档（主色实底）：灰底 + 灰字（**不再降透明** —— opacity 只表在途 busy）；
   禁点由全局 .is-disabled 的 pointer-events: none 承担 */
.app-btn.is-disabled {
  background: var(--bg-input);
}
.app-btn.is-disabled .btn-text {
  color: var(--text-tertiary);
}
.app-btn.loading {
  opacity: 0.6;
  pointer-events: none;
}
/* 在途旋转环：文字左侧 28rpx（环样式与 0.8s 节奏复用 RetryBlock 的环语言）。
   环底 = --text-white-edge（主色实底上的半透白描边档）；顶弧 = --text-white（实底白字同源）；
   reduced-motion 降级由 App.vue 全局块覆盖。 */
.btn-spinner {
  flex: none;
  width: 28rpx;
  height: 28rpx;
  border: 3rpx solid var(--text-white-edge);
  border-top-color: var(--text-white);
  border-radius: var(--radius-circle);
  animation: btn-spin 0.8s linear infinite;
}
@keyframes btn-spin {
  to { transform: rotate(360deg); }
}
/* 按压反馈：主色实底 CTA 档 ⇒ 0.85（与 EmptyState / RetryBlock 双 CTA / SearchBar 搜索钮同档） */
.app-btn.pressed {
  opacity: 0.85;
}
</style>
