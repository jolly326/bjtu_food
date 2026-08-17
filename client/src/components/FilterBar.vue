<template>
  <view class="filter-bar">
    <scroll-view
      class="filter-scroll"
      scroll-x
      :scroll-left="scrollLeft"
      :scroll-with-animation="true"
      show-scrollbar="false"
    >
      <view class="filter-track">
        <view
          v-for="tab in tabs"
          :key="tab.key"
          :id="`fb-${tab.key}`"
          class="filter-item"
          :class="{ active: tab.key === selectedKey }"
          @tap="onSelect(tab)"
        >
          <text class="filter-label">{{ tab.label }}</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { FilterTab } from './filter-tab'

const props = defineProps<{
  /** 扁平平铺的筛选维度（单级，无二级菜单）：美食类型等 */
  tabs: FilterTab[]
  /** 当前选中项的 key */
  modelValue: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', key: string): void
  (e: 'change', tab: FilterTab): void
}>()

const selectedKey = ref(props.modelValue)
const scrollLeft = ref(0)

watch(
  () => props.modelValue,
  (v) => {
    selectedKey.value = v
    scrollToCenter(v)
  },
)

function currentTab(): FilterTab {
  return props.tabs.find((t) => t.key === selectedKey.value) ?? props.tabs[0]
}

/** 选中项平滑滚动到容器水平中点 */
function scrollToCenter(key: string) {
  nextTick(() => {
    const query = uni.createSelectorQuery()
    query
      .select('.filter-scroll')
      .boundingClientRect((container: any) => {
        query
          .select(`#fb-${key}`)
          .boundingClientRect((item: any) => {
            if (!container || !item) return
            const target = item.left - container.left - (container.width / 2 - item.width / 2)
            scrollLeft.value = Math.max(0, target)
          })
          .exec()
      })
      .exec()
  })
}

function onSelect(tab: FilterTab) {
  selectedKey.value = tab.key
  scrollToCenter(tab.key)
  emit('update:modelValue', tab.key)
  emit('change', tab)
}

// 首屏挂载后把默认选中项滚到居中
watch(
  () => props.tabs,
  () => scrollToCenter(selectedKey.value),
  { immediate: true },
)
</script>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  align-items: center;
  margin: 16rpx 24rpx 8rpx;
}

.filter-scroll {
  flex: 1;
  white-space: nowrap;
}

.filter-track {
  display: inline-flex;
  align-items: center;
  /* 首尾项也可滚动居中：两端补半容器宽，抵消 scroll-left 钳死为 0 的贴边问题 */
  padding: 0 calc(50% - 70rpx);
}

.filter-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 64rpx;
  padding: 0 28rpx;
  margin: 0 8rpx;
  transition: color 0.2s ease;

  .filter-label {
    font-size: 28rpx;
    color: #8a8278;
    font-weight: 500;
  }

  /* 选中：文字变朱砂红，底部一条红线，无背景填充 */
  &.active {
    .filter-label {
      color: #c0392b;
      font-weight: 600;
    }

    &::after {
      content: '';
      position: absolute;
      left: 50%;
      bottom: 6rpx;
      transform: translateX(-50%);
      width: 40rpx;
      height: 4rpx;
      border-radius: 2rpx;
      background: #c0392b;
    }
  }
}
</style>
