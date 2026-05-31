<template>
  <view class="app-btn" :class="[btnType, { disabled, loading }]" :style="{ width, margin }" @tap="handleTap">
    <image v-if="icon" :src="icon" class="btn-icon" />
    <text class="btn-text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  text: string
  icon?: string
  type?: 'primary' | 'danger' | 'gradient' | 'outline'
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

const btnType = computed(() => `btn-${props.type}`)

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
.btn-icon {
  width: 32rpx;
  height: 32rpx;
  margin-right: 8rpx;
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
.btn-gradient {
  background: var(--color-gradient);
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
