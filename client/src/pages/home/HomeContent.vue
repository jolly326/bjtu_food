<template>
  <view class="feed-wrap">
    <!-- 加载失败重试块（MP-012，P3-03 上提为公共组件）：筛选流失败且无数据时替代静默空态/
         「还没录菜品」误导文案；整块 @tap 上抛 retry 由页面走重拉路径。
         失败态**优先于**加载态与空态：失败 ≠ 加载中 ≠ 无内容 -->
    <RetryBlock v-if="loadFailed" @retry="emit('retry')" />

    <!-- spec §4.8 不设加载骨架/加载指示：loading 期间本区块不渲染任何内容，保持空白 -->
    <template v-else-if="!loading">
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

      <!-- 触底态（MP-05）：到达保留页数上限给出说明并保留「清除筛选」脱困动作，
           不再无限 concat；加载中给出在途提示（filterLoadingMore 此前全仓零消费） -->
      <view v-if="pageLimited" class="feed-foot">
        <text class="feed-foot-text">已展示前 {{ maxDishes }} 个结果，缩小筛选范围可查看更多</text>
      </view>
      <view v-else-if="loadingMore" class="feed-foot">
        <text class="feed-foot-text">正在加载更多…</text>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DishCard from './DishCard.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import {
  useDishStore,
  LOADING_KEY_FILTER,
  FILTER_PAGE_SIZE,
  FILTER_MAX_PAGES,
} from '@/stores/dish'
import type { Dish } from '@/types/dish'
import { dishDetailUrl } from '@/utils/routes'

const emit = defineEmits<{
  /** 筛选流加载失败后点击重试（MP-012）：上抛页面走与下拉刷新同一条重拉路径 */
  (e: 'retry'): void
}>()

const dishStore = useDishStore()

/**
 * 筛选流首屏 / 切筛选在途（MP-01）：此前 store 未把筛选流纳入 withLoading，
 * 请求期间无在途态可消费，内容区只能空白，而贡献卡又抢先渲染 → 「加载中」被读成「没内容」。
 * 只订阅 LOADING_KEY_FILTER（触底加载更多是另一个 key，不遮挡已有列表）。
 */
const loading = computed(() => dishStore.isLoading(LOADING_KEY_FILTER))
/** 触底加载更多在途（MP-05 顺带让此前全仓零消费的 filterLoadingMore 有出口） */
const loadingMore = computed(() => dishStore.filterLoadingMore)
/** 触达保留页数上限（MP-05）：给出「已展示前 N 个」说明，避免静默截断 */
const pageLimited = computed(() => dishStore.filterPageLimited)
const maxDishes = FILTER_MAX_PAGES * FILTER_PAGE_SIZE

/** 筛选流最近一次请求失败且当前无数据（MP-012）：渲染错误重试块，失败 ≠ 无数据 */
const loadFailed = computed(() => dishStore.filterError && dishStore.filterList.length === 0)

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

/* 失败态块已上提为公共组件 components/RetryBlock.vue（P3-03），样式随之收敛，此处不再保留副本 */

/* 触底 / 加载更多提示（MP-05）：居中次级灰小字，不抢内容焦点 */
.feed-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 var(--spacing-md) var(--spacing-lg);
}
.feed-foot-text {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  text-align: center;
}
</style>
