<template>
  <view class="waterfall-grid" :class="{ single }">
    <!--
      瀑布流 / 单列列表（方案 F，2026-07-31 重写；2026 增强支持档口单列）
      ------------------------------------------------------------
      红线 §4.9（小程序 slot 坑）：禁止在父组件用 <template #card> 向本组件
      具名 slot 分发——uni-app 编译 mp-weixin 后，父组件 N 个同名 slot 片段
      无法正确映射，子组件不消费该 slot 时整块空白不渲染（V1/V2 阻断级 bug）。
      因此本组件**内部直接渲染**卡片，父级仅通过事件上抛：
        <WaterfallList :list @card-click="goToDetail" />          // 双列菜品
        <WaterfallList :list single type="stall" @stall-click />  // 单列档口
      禁止任何具名 slot 分发。
    -->
    <template v-if="single">
      <!-- 单列模式：档口卡流（canteen 详情页重构，task-14 W4） -->
      <view
        v-for="(item, i) in list"
        :key="stallKey(item, i)"
        class="waterfall-item-single enter-up"
        :style="{ '--enter-i': i }"
      >
        <StallCardSingle :stall="toStallItem(item)" @select="onStallClick" />
      </view>
    </template>

    <!-- 双列瀑布流：奇偶分列（右列绝不空） -->
    <template v-else>
      <view class="waterfall-col waterfall-col-left">
        <view
          v-for="(entry, i) in splitList.left"
          :key="entry.key"
          class="waterfall-item enter-up"
          :style="{ '--enter-i': i }"
        >
          <DishCard :dish="entry.item" @select="onCardClick" />
        </view>
      </view>
      <view class="waterfall-col waterfall-col-right">
        <view
          v-for="(entry, i) in splitList.right"
          :key="entry.key"
          class="waterfall-item enter-up"
          :style="{ '--enter-i': i }"
        >
          <DishCard :dish="entry.item" @select="onCardClick" />
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DishCard from './DishCard.vue'
import StallCardSingle from './StallCardSingle.vue'
import type { Dish } from '@/types/dish'
import type { StallCardItem } from './stall-card-item'

const props = withDefaults(defineProps<{
  list: (Dish | any)[]
  itemKey?: string
  /** 单列模式（档口卡流）；默认 false = 双列菜品瀑布流 */
  single?: boolean
  /** 单列模式下卡片类型，当前仅支持 'stall' */
  type?: 'stall'
}>(), {
  list: () => [],
  itemKey: 'id',
  single: false,
  type: 'stall',
})

const emit = defineEmits<{
  cardClick: [dish: Dish]
  stallClick: [stall: StallCardItem]
}>()

/** 瀑布流按图片原始比例排列（不再为错落刻意拉伸图片高度）；列分配保持奇偶分列 */
const splitList = computed(() => {
  const left: { item: Dish; key: string }[] = []
  const right: { item: Dish; key: string }[] = []
  props.list.forEach((item, idx) => {
    const rawKey = (item as Record<string, any>)?.[props.itemKey]
    const key = (rawKey !== undefined && rawKey !== null && rawKey !== '')
      ? `wf-${rawKey}-${idx}`
      : `wf-idx-${idx}`
    const entry = { item: item as Dish, key }
    if (idx % 2 === 0) left.push(entry)
    else right.push(entry)
  })
  return { left, right }
})

function stallKey(item: any, idx: number): string {
  const raw = item?.[props.itemKey]
  return (raw !== undefined && raw !== null && raw !== '') ? `st-${raw}` : `st-idx-${idx}`
}

/** 把任意档口对象归一为 StallCardItem（透传位置/菜品数/人均/招牌菜，todo：档口卡位置缺失） */
function toStallItem(item: any): StallCardItem {
  return {
    id: Number(item?.id || 0),
    name: item?.name || '',
    image: item?.image || item?.images?.[0] || '',
    description: item?.description || '',
    rating: item?.rating ?? item?.avgRating ?? 0,
    avgRating: item?.avgRating != null ? Number(item.avgRating) : undefined,
    dishCount: item?.dishCount != null ? Number(item.dishCount) : undefined,
    perCapita: item?.perCapita != null ? Number(item.perCapita) : undefined,
    location: item?.location || '',
    meta: item?.meta || '',
    tags: item?.tags || [],
    topDishes: Array.isArray(item?.topDishes) ? item.topDishes : [],
  }
}

function onCardClick(dish: Dish) {
  emit('cardClick', dish)
}

function onStallClick(stall: StallCardItem) {
  emit('stallClick', stall)
}
</script>

<style>
.waterfall-grid {
  width: 100%;
  box-sizing: border-box;
  padding-bottom: var(--spacing-lg);
  display: flex;
  gap: var(--spacing-md);
}
.waterfall-col {
  flex: 1 1 0;
  width: 0;
  min-width: 0;
  box-sizing: border-box;
}
.waterfall-item {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  margin-bottom: var(--spacing-md);
}
.waterfall-item:last-child { margin-bottom: 0; }
/* 单列模式：档口卡依次纵向堆叠，span 整宽（覆盖 .waterfall-grid 的 flex 行布局为纵向列）。
   卡间间距用 gap=md（与社区列表一致），容器 padding 提供左右边距（卡片居中不溢出）。
   注意：不能给 item 同时设 width:100% 和左右 margin，否则横向溢出导致左右不对称。 */
.waterfall-grid.single {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
}
.waterfall-grid.single .waterfall-item-single {
  width: 100%;
  box-sizing: border-box;
}
</style>
