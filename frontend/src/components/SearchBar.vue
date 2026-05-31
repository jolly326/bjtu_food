<template>
  <view class="search-bar" :style="customStyle">

    <!-- 点击模式（首页：点击跳转搜索页） -->
    <template v-if="!inputMode">
      <view class="search-icon">
        <image src="/static/icons/search.svg" class="icon-img" />
      </view>
      <text class="search-placeholder" @tap="handleTap">{{ placeholder }}</text>
    </template>

    <!-- 输入模式（发现页：就地搜索） -->
    <template v-else>
      <view class="search-icon">
        <image src="/static/icons/search.svg" class="icon-img" />
      </view>
      <input
        :value="modelValue"
        class="search-input"
        :placeholder="placeholder"
        confirm-type="search"
        @input="handleInput"
        @confirm="handleConfirm"
      />
      <text v-if="modelValue" class="clear-btn" @tap="handleClear">✕</text>
    </template>

  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  placeholder?: string
  margin?: string
  inputMode?: boolean
  modelValue?: string
}>(), {
  placeholder: '搜索菜品或档口...',
  margin: 'var(--spacing-md)',
  inputMode: false,
  modelValue: '',
})

const emit = defineEmits<{
  tap: []
  'update:modelValue': [value: string]
  search: [value: string]
}>()

const customStyle = computed(() => ({
  margin: props.margin,
}))

function handleTap() {
  emit('tap')
}

function handleInput(e: any) {
  emit('update:modelValue', e.detail.value)
}

function handleConfirm(e: any) {
  emit('search', e.detail.value)
}

function handleClear() {
  emit('update:modelValue', '')
}
</script>

<style scoped>
.search-bar {
  display: flex;
  align-items: center;
  background: var(--bg-card);
  border-radius: var(--radius-btn);
  padding: 20rpx var(--spacing-lg);
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
  border: 2rpx solid var(--border-color);
}
.search-icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.icon-img {
  width: 32rpx;
  height: 32rpx;
  margin-right: 20rpx;
}
.search-placeholder {
  font-size: var(--font-body);
  color: var(--text-tertiary);
  flex: 1;
}
.search-input {
  flex: 1;
  font-size: var(--font-body);
  color: var(--text-primary);
  background: transparent;
  border: none;
  outline: none;
}
.clear-btn {
  font-size: var(--font-body);
  color: var(--text-tertiary);
  padding: 0 8rpx;
  flex-shrink: 0;
}
</style>
