<template>
  <view class="feed-wrap">
    <FilterBar
      :tabs="typeTabs"
      :model-value="selectedKey"
      @change="onFilterChange"
    />

    <template v-if="dishStore.filterList.length > 0">
      <WaterfallList :list="dishStore.filterList" @card-click="goToDetail" />

      <view v-if="dishStore.filterLoadingMore" class="list-footer loading">
        <view class="footer-spinner" />
        <text class="footer-text">加载中…</text>
      </view>
      <view v-else-if="dishStore.filterFinished" class="list-footer finished">
        <text class="footer-text">— 已经到底啦 —</text>
      </view>
    </template>

    <view v-else class="home-empty">
      <IconSvg name="empty" :size="120" color="var(--text-tertiary)" />
      <text class="empty-tip">{{ loadFailed ? '加载失败' : '暂时没有内容' }}</text>
      <text class="empty-sub">{{ loadFailed ? '网络异常或后端未启动，下拉刷新后重试' : '下拉刷新，或确认后端已启动、网络可访问后重试' }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import FilterBar from '@/components/FilterBar.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import IconSvg from '@/components/IconSvg.vue'
import { useDishStore } from '@/stores/dish'
import type { FilterTab } from '@/components/filter-tab'

const dishStore = useDishStore()

defineProps<{
  /** 瀑布流加载失败（首屏网络异常），用于空态文案降级 */
  loadFailed?: boolean
}>()

/** 扁平平铺的美食类型筛选维度（单级横滑，无二级菜单） */
const typeTabs: FilterTab[] = [
  { key: 'noodle', label: '面食', type: 'tag', payload: 'noodle' },
  { key: 'rice', label: '米饭', type: 'tag', payload: 'rice' },
  { key: 'spicy', label: '辣味', type: 'tag', payload: 'spicy' },
  { key: 'signature', label: '招牌菜', type: 'tag', payload: 'signature' },
  { key: 'vegetarian', label: '素食', type: 'tag', payload: 'vegetarian' },
  { key: 'western', label: '西餐', type: 'tag', payload: 'western' },
  { key: 'halal', label: '清真', type: 'tag', payload: 'halal' },
  { key: 'recommended', label: '必吃', type: 'tag', payload: 'recommended' },
]

/** 当前选中类型 key，默认首项（面食） */
const selectedKey = typeTabs[0].key

function onFilterChange(tab: FilterTab) {
  dishStore.fetchFilterDishes(tab, true)
}

/** 菜品卡片点击 → 独立详情页（pages-detail/dish） */
function goToDetail(dish: { id: number }) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}
</script>

<style scoped lang="scss">
.feed-wrap {
  padding: 0 var(--spacing-md);
  box-sizing: border-box;
}

.list-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-md) 0;
  gap: var(--spacing-xs);

  .footer-spinner {
    width: 28rpx;
    height: 28rpx;
    border: 3rpx solid var(--border-color);
    border-top-color: var(--color-primary);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }

  .footer-text {
    font-size: var(--font-body);
    color: var(--text-tertiary);
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.home-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--spacing-xl) var(--spacing-md);

  .empty-tip {
    margin-top: var(--spacing-sm);
    font-size: var(--font-subheading);
    color: var(--text-primary);
  }

  .empty-sub {
    margin-top: var(--spacing-xs);
    font-size: var(--font-body);
    color: var(--text-tertiary);
    text-align: center;
  }
}
</style>
