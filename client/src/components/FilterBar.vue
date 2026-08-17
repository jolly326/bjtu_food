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
          v-for="tab in visibleTabs"
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
    <view
      v-if="mode === 'type'"
      class="filter-back"
      @tap="backToRoot"
    >
      <text class="filter-back-text">‹</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { TAG_MAP } from '@/api/dish'
import type { FilterTab } from './filter-tab'

const emit = defineEmits<{
  (e: 'change', tab: FilterTab): void
}>()

const ROOT_TABS: FilterTab[] = [
  { key: 'recommend', label: '推荐', type: 'recommend' },
  { key: 'type', label: '美食类型', type: 'tag' },
]

const TYPE_TABS: FilterTab[] = [
  { key: 'noodle', label: '面食', type: 'tag', payload: 'noodle' },
  { key: 'rice', label: '米饭', type: 'tag', payload: 'rice' },
  { key: 'spicy', label: '辣味', type: 'tag', payload: 'spicy' },
  { key: 'signature', label: '招牌菜', type: 'tag', payload: 'signature' },
  { key: 'vegetarian', label: '素食', type: 'tag', payload: 'vegetarian' },
  { key: 'western', label: '西餐', type: 'tag', payload: 'western' },
]

const mode = ref<'root' | 'type'>('root')
const selectedKey = ref<string>('recommend')
const scrollLeft = ref(0)

const visibleTabs = computed(() => (mode.value === 'root' ? ROOT_TABS : TYPE_TABS))

function currentTab(): FilterTab {
  const list = visibleTabs.value
  return list.find((t) => t.key === selectedKey.value) ?? list[0]
}

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
  if (tab.key === 'type' && mode.value === 'root') {
    mode.value = 'type'
    selectedKey.value = TYPE_TABS[0].key
    scrollToCenter(selectedKey.value)
    emit('change', currentTab())
    return
  }
  selectedKey.value = tab.key
  scrollToCenter(tab.key)
  emit('change', tab)
}

function backToRoot() {
  mode.value = 'root'
  selectedKey.value = 'recommend'
  scrollToCenter('recommend')
  emit('change', currentTab())
}

watch(mode, () => scrollToCenter(selectedKey.value))
</script>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 16rpx;
  margin: 16rpx 24rpx 8rpx;
  padding: 8rpx 8rpx;
  box-shadow: 0 4rpx 16rpx rgba(43, 43, 43, 0.06);
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
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 64rpx;
  padding: 0 28rpx;
  margin: 0 8rpx;
  border-radius: 32rpx;
  transition: background 0.25s ease, color 0.25s ease;

  .filter-label {
    font-size: 28rpx;
    color: #8a8278;
    font-weight: 500;
  }

  &.active {
    background: linear-gradient(135deg, #c0392b, #a93226);

    .filter-label {
      color: #fff;
      font-weight: 600;
    }
  }
}

.filter-back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 64rpx;
  margin-left: 8rpx;

  .filter-back-text {
    font-size: 36rpx;
    color: #c0392b;
    line-height: 1;
  }
}
</style>
