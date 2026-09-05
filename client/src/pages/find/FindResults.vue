<template>
  <view class="filter-result">
    <!-- 结果态滚动容器：find-page-layout-restructure —— 滚动随结果内容区（FindResults）走，
         不再由 find/index 页根层包裹两态共用滚动。加载/失败/空态仍由 StateView 一次承载 -->
    <scroll-view
      class="results-scroll"
      scroll-y
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="emit('refresh')"
    >
      <StateView
        v-if="loading || failed || items.length === 0"
        :loading="loading"
        :failed="failed"
        :empty="true"
        error-text="加载失败，请重试"
        :empty-text="`没有找到与“${keyword}”相关的结果`"
        :empty-retry="true"
        @retry="emit('retry')"
      />

      <!-- 搜索结果：一行一个菜品（DishResultRow），左图右信息 -->
      <view v-else class="mixed-list" :class="{ single: items.length === 1 }">
        <DishResultRow
          v-for="item in items"
          :key="`${item.type}-${item.id}`"
          :dish="item"
          :keyword="keyword"
          @select="emit('select', $event)"
        />
      </view>

    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import DishResultRow from './DishResultRow.vue'
import StateView from '@/components/StateView.vue'

/** 搜索混合结果项（仅菜品）；与 find 页 MixedResult 结构兼容 */
interface MixedResultItem {
  type: 'dish'
  id?: number
  name: string
  image?: string
  sub?: string
  price?: number
  rating?: number
  ratingCount?: number
  stall?: string
  tags?: string
  tagLabels?: string[]
  promoPrice?: number
  originalPrice?: number
  lat?: number
  lng?: number
  distance?: number
}

const props = defineProps<{
  items: MixedResultItem[]
  loading?: boolean
  failed?: boolean
  keyword?: string
  /** 结果态下拉刷新触发态：由宿主页面维护并复位 */
  refresherTriggered?: boolean
}>()

const emit = defineEmits<{
  (e: 'retry'): void
  (e: 'select', id: number): void
  /** 结果态下拉刷新：宿主页面据此重跑当前搜索/筛选 */
  (e: 'refresh'): void
}>()
</script>

<style scoped>
/* 结果内容区占满宿主（find-body/results-host flex 链），滚动由内部 scroll-view 承担 */
.filter-result {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
}
.results-scroll {
  flex: 1;
  min-height: 0;
  padding-bottom: var(--spacing-lg);
}
/* 搜索结果列表（仅菜品，一行一个，左图右信息） */
.mixed-list { margin: var(--spacing-md); }
/* 2.4 单条结果视觉平衡：仅加大顶部间距使卡片位于上半舒适区，不填充内容 */
.mixed-list.single { margin-top: calc(var(--spacing-md) + var(--spacing-lg)); }
</style>
