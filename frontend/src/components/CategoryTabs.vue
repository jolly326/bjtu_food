<template>
  <view class="category-tabs" :class="{ scrollable }">
    <scroll-view scroll-x class="tabs-scroll" :show-scrollbar="false">
      <view class="tabs-inner">
        <view
          v-for="(tab, index) in tabs"
          :key="tab.key"
          class="tab-item"
          :class="{ active: tab.key === modelValue }"
          :style="tab.key === pressedKey ? pressStyle : {}"
          @touchstart="pressedKey = tab.key"
          @touchend="pressedKey = null"
          @touchcancel="pressedKey = null"
          @mousedown="pressedKey = tab.key"
          @mouseup="pressedKey = null"
          @mouseleave="pressedKey = null"
          @tap="select(tab.key)"
        >
          <text class="tab-text">{{ tab.label }}</text>
          <view v-if="tab.key === modelValue" class="tab-indicator" />
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

export interface CategoryTab {
  key: string
  label: string
}

const props = withDefaults(defineProps<{
  modelValue: string
  tabs: CategoryTab[]
  scrollable?: boolean
}>(), {
  scrollable: false,
})

const emit = defineEmits<{
  'update:modelValue': [key: string]
  change: [key: string]
}>()

function select(key: string) {
  if (key === props.modelValue) return
  emit('update:modelValue', key)
  emit('change', key)
}

const pressedKey = ref<string | null>(null)
const pressStyle = { transform: 'scale(0.97)', transition: 'transform 0.12s ease' }

const _ = computed(() => props.tabs)
</script>

<style scoped>
.category-tabs {
  width: 100%;
}
.tabs-scroll {
  width: 100%;
  white-space: nowrap;
}
.tabs-inner {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  padding: 0 var(--spacing-md);
}
.tab-item {
  position: relative;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  padding: var(--spacing-sm) 0;
  /* §4.3 常规 UI：spring 1.0/0.3 的等效过渡（非手势态切换） */
  transition: transform 0.2s cubic-bezier(0.32, 0.72, 0, 1), opacity 0.2s;
}
.tab-text {
  font-size: var(--font-body);
  color: var(--text-secondary);
  font-weight: 500;
  line-height: 1.2;
  transition: color 0.2s, font-weight 0.2s;
}
.tab-item.active .tab-text {
  color: var(--color-primary);
  font-weight: 700;
}
.tab-indicator {
  position: absolute;
  bottom: 0;
  width: 40rpx;
  height: 6rpx;
  border-radius: 6rpx;
  background: var(--color-primary);
  /* 入场 spring 1.0/0.3（无过冲） */
  animation: tabIn 0.3s cubic-bezier(0.32, 0.72, 0, 1);
}
@keyframes tabIn {
  from { transform: scaleX(0.2); opacity: 0; }
  to { transform: scaleX(1); opacity: 1; }
}
</style>
