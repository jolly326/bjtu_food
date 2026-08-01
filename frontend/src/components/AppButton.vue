<template>
  <view
    class="app-btn"
    :class="[btnType, { disabled, loading }]"
    :style="btnStyle"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @mousedown="pressed = true"
    @mouseup="pressed = false"
    @mouseleave="pressed = false"
    @tap="handleTap"
  >
    <text v-if="icon" class="btn-icon-text">{{ icon }}</text>
    <text class="btn-text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

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

const emit = defineEmits<{
  click: []
}>()

// icon 为 IconSvg 矢量图标名（通过 btnIcon slot 或文本渲染），全量禁 emoji（红线 §4.9③）。
const btnType = computed(() => `btn-${props.type}`)

const pressed = ref(false)
const btnStyle = computed(() => ({
  width: props.width,
  margin: props.margin,
  transform: pressed.value ? 'scale(var(--press-scale))' : 'scale(1)',
  transition: 'var(--press-transition)',
}))

function handleTap() {
  if (props.disabled || props.loading) return
  emit('click')
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
.btn-icon-text {
  font-size: 30rpx;
  line-height: 1;
  margin-right: var(--spacing-xs);
}
.app-btn.disabled {
  opacity: 0.4;
}
.btn-text {
  font-size: var(--font-card);
  font-weight: 500;
  color: var(--text-white);
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
