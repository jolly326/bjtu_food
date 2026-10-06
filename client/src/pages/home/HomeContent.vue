<template>
  <view class="feed-wrap">
    <!-- 三态判断序固定 **失败 > 在途 > 空 > 内容**（有错不显示空）：
         · 失败（且无数据）→ 公共 `RetryBlock`（P3-03）：整块 @tap 上抛 retry 由页面走重拉路径，
           替代静默空态 / 「还没录菜品」误导文案（失败 ≠ 加载中 ≠ 无内容）；
         · 在途（首屏 / 切大类拉取，`LOADING_KEY_HOME` 在途）→ 全局 `.list-foot` 文字行；
         · 空（成功但 0 条）→ 公共 `EmptyState`：**不给 CTA**（同屏已有食堂 / 大类控件，属「已有可达入口」）。 -->
    <RetryBlock v-if="loadFailed" @retry="emit('retry')" />

    <!-- 在途只给文字行、不给骨架屏（禁的是伪内容与抖动，不是文字）：静默 = 慢网白屏 -->
    <view v-else-if="loading" class="list-foot">
      <text class="list-foot-text">加载中…</text>
    </view>

    <!-- 零数据空态（显式、禁静默）：成功返回 0 条时网格区不再整块空白 -->
    <EmptyState
      v-else-if="!list.length"
      icon="dish"
      :icon-size="48"
      title="暂无菜品"
      desc="换个食堂或大类看看"
    />

    <template v-else>
      <view class="waterfall-grid">
        <!-- 双列瀑布流：奇偶分列（右列绝不空）；WaterfallList 已内联合并到此，减少一层组件嵌套 -->
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

      <!-- 触底态（MP-05）：到达保留页数上限给出说明；加载更多在途给出在途提示。
           两者均为全局 `.list-foot` 用法（与首屏在途文字行同一实现）。 -->
      <view v-if="pageLimited" class="list-foot">
        <text class="list-foot-text">已展示前 {{ maxDishes }} 个结果，切换大类可查看更多</text>
      </view>
      <view v-else-if="loadingMore" class="list-foot">
        <text class="list-foot-text">正在加载更多…</text>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DishCard from './DishCard.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useDishStore, LOADING_KEY_HOME, HOME_MAX_PAGES } from '@/stores/dish'
import { HOME_PAGE_SIZE } from '@/constants/paging'
import type { DishListItem } from '@/types/dish'
import { dishDetailUrl } from '@/utils/routes'

const emit = defineEmits<{
  /** 列表加载失败后点击重试：上抛页面按与首屏同一条重拉路径重拉 */
  (e: 'retry'): void
}>()

const dishStore = useDishStore()

/** 列表数据源（`store.homeList`）：空态判定与瀑布流分列共用 */
const list = computed(() => dishStore.homeList)

/**
 * 列表首屏 / 切大类在途：只订阅 `LOADING_KEY_HOME`（触底加载更多是另一个 key，不遮挡已有列表）。
 * 在途只给文字行（全局 `.list-foot`），不给骨架屏。
 */
const loading = computed(() => dishStore.isLoading(LOADING_KEY_HOME))
/** 触底加载更多在途 */
const loadingMore = computed(() => dishStore.homeLoadingMore)
/** 触达保留页数上限：给出「已展示前 N 个」说明，避免静默截断 */
const pageLimited = computed(() => dishStore.homePageLimited)
const maxDishes = HOME_MAX_PAGES * HOME_PAGE_SIZE

/** 列表最近一次请求失败且当前无数据：渲染错误重试块，失败 ≠ 无数据（失败优先于在途与空态） */
const loadFailed = computed(() => dishStore.homeError && list.value.length === 0)

/** 双列分列：奇偶分列（右列绝不空）。卡片图为**固定 3:2 容器**，故无需按图片比例做列内等高平衡。
 *  key 仅由稳定业务主键 `id` 构成（`DishListItem.id: number` 为必填）——**不附加列内序号 idx**，
 *  避免加载更多时列内序号重排导致 key 变化、已渲染卡片整列重建（闪烁 / 掉帧）。 */
const splitList = computed(() => {
  const left: { item: DishListItem; key: string }[] = []
  const right: { item: DishListItem; key: string }[] = []
  list.value.forEach((item, idx) => {
    const entry = { item, key: `wf-${item.id}` }
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
  padding: 0 var(--page-gutter);
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

/* 文字行（在途 / 加载更多 / 已封顶）样式全部来自全局 `.list-foot` / `.list-foot-text`
   （App.vue 唯一实现），页面不再保留私有副本 */
</style>