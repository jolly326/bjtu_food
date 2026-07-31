<template>
  <view class="search-bar" :style="customStyle">

    <!-- 点击模式（首页：点击跳转搜索页） -->
    <template v-if="!inputMode">
      <IconSvg name="search" :size="32" color="var(--text-tertiary)" />
      <text class="search-placeholder" @tap="handleTap">{{ placeholder }}</text>
    </template>

    <!-- 输入模式（发现页：就地搜索） -->
    <template v-else>
      <IconSvg name="search" :size="32" color="var(--text-tertiary)" />
      <input
        :value="modelValue"
        class="search-input"
        :placeholder="placeholder"
        confirm-type="search"
        @input="handleInput"
        @confirm="handleConfirm"
      />
      <IconSvg
        v-if="modelValue"
        class="clear-btn"
        name="close"
        :size="28"
        color="var(--text-tertiary)"
        @click="handleClear"
      />
    </template>

  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import IconSvg from './IconSvg.vue'

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
  padding: var(--spacing-sm) var(--spacing-lg);
  box-shadow: 0 2rpx 8rpx var(--overlay-dark-faint);
  border: 2rpx solid var(--border-color);
}
.search-icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  margin-right: var(--spacing-sm);
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
  flex-shrink: 0;
  padding: 0 var(--spacing-xs);
  transition: transform 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.clear-btn:active { transform: scale(0.9); }
</style>
