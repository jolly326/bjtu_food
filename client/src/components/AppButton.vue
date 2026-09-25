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
    @tap="handleTap"
  >
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
  height: 88rpx;
  border-radius: var(--radius-btn);
  box-sizing: border-box;
  gap: var(--spacing-xs);
}
/* 禁用态：复用全局 .is-disabled 令牌（App.vue：opacity 0.5 + pointer-events:none + 轻灰度），
   不再组件内自设 0.4 弱化档，与全站禁用口径单一来源 */
.btn-text {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-medium);
  color: var(--color-on-primary);
}
.btn-primary {
  background: var(--color-primary);
}
.app-btn.loading {
  opacity: 0.6;
  pointer-events: none;
}
</style>
