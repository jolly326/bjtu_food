<template>
  <view class="app-btn" :class="[btnType, { disabled }]" :style="{ width, margin }" @tap="handleTap">
    <text class="btn-text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  text: string
  type?: 'primary' | 'danger' | 'gradient'
  disabled?: boolean
  width?: string
  margin?: string
}>(), {
  type: 'primary',
  disabled: false,
  width: '100%',
  margin: '0',
})

const emit = defineEmits<{
  tap: []
}>()

const btnType = computed(() => `btn-${props.type}`)

function handleTap() {
  if (props.disabled) return
  emit('tap')
}
</script>

<style scoped>
.app-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: 48rpx;
  box-sizing: border-box;
}
.app-btn.disabled {
  opacity: 0.4;
}
.btn-text {
  font-size: 32rpx;
  font-weight: 500;
  color: var(--text-white);
}
.btn-primary {
  background: var(--color-primary);
}
.btn-danger {
  background: var(--color-error);
}
.btn-gradient {
  background: var(--color-gradient);
}
</style>
