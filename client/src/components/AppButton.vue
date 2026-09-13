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
    <IconSvg v-if="icon" :name="icon" :size="30" :color="iconColor" class="btn-icon" />
    <text class="btn-text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import IconSvg from './IconSvg.vue'

const props = withDefaults(defineProps<{
  text: string
  icon?: string
  type?: 'primary' | 'danger' | 'outline'
  disabled?: boolean
  loading?: boolean
  width?: string
  margin?: string
}>(), {
  type: 'primary',
  disabled: false,
  loading: false,
  width: '100%',
  margin: '0',
  icon: '',
})

// 自定义事件禁用原生事件名（tap/click）：否则 uni-app 编译 mp-weixin 时父组件
// 监听被当作原生 bindxxx，emit 参数会丢失（同 DishCard 坑，见其注释）。
const emit = defineEmits<{
  press: []
}>()

// icon 为 IconSvg 矢量图标名（通过 btnIcon slot 或文本渲染），全量禁 emoji（红线 §4.9③）。
const btnType = computed(() => `btn-${props.type}`)

/** 图标色与文字同源：实底型（primary/danger）用 on-primary 白字；outline 型文字为主色（.btn-outline .btn-text），图标须同色，避免白底白图标 */
const iconColor = computed(() => (props.type === 'outline' ? 'var(--color-primary)' : 'var(--color-on-primary)'))

const btnStyle = computed(() => ({
  width: props.width,
  margin: props.margin,
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
.btn-icon {
  flex-shrink: 0;
  margin-right: var(--spacing-xs);
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
.btn-danger {
  background: var(--color-error);
}
.btn-outline {
  background: transparent;
  border: 2rpx solid var(--color-primary);
}
.btn-outline .btn-text {
  color: var(--color-primary);
}
.app-btn.loading {
  opacity: 0.6;
  pointer-events: none;
}
</style>
