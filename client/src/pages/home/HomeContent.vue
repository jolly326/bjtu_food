<template>
  <view class="feed-wrap">
    <view class="waterfall-grid">
      <!-- 双列瀑布流：奇偶分列（右列绝不空）；原 WaterfallList 已内联合并到此，减少一层组件嵌套 -->
      <view class="waterfall-col waterfall-col-left">
        <view v-for="entry in splitList.left" :key="entry.key" class="waterfall-item">
          <DishCard :dish="entry.item" @select="goToDetail" />
        </view>
      </view>
      <view class="waterfall-col waterfall-col-right">
        <view v-for="entry in splitList.right" :key="entry.key" class="waterfall-item">
          <DishCard :dish="entry.item" @select="goToDetail" />
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DishCard from './DishCard.vue'
import { useDishStore } from '@/stores/dish'
import type { Dish } from '@/types/dish'
import { dishDetailUrl } from '@/utils/routes'

const dishStore = useDishStore()

/** 瀑布流按图片原始比例排列（不再为错落刻意拉伸图片高度）；列分配保持奇偶分列。
 *  key 仅由稳定业务主键 id 构成（id 唯一），不附加列内序号 idx，
 *  避免加载更多时列内序号重排导致 key 变化、已渲染卡片整列重建（闪烁/掉帧）。 */
const splitList = computed(() => {
  const left: { item: Dish; key: string }[] = []
  const right: { item: Dish; key: string }[] = []
  dishStore.filterList.forEach((item, idx) => {
    const rawKey = item.id
    const key =
      rawKey !== undefined && rawKey !== null
        ? `wf-${rawKey}`
        : `wf-idx-${idx}`
    const entry = { item, key }
    if (idx % 2 === 0) left.push(entry)
    else right.push(entry)
  })
  return { left, right }
})

/** 菜品卡片点击 → 独立详情页（pages/detail/dish） */
function goToDetail(dish: { id: number }) {
  uni.navigateTo({ url: dishDetailUrl(dish.id) })
}
</script>

<style scoped lang="scss">
.feed-wrap {
  padding: 0 var(--spacing-md);
  box-sizing: border-box;
}

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
</style>
