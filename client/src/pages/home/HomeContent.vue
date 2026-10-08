<template>
  <view class="feed-wrap">
    <!-- 三态判断序固定 **失败 > 在途 > 空 > 内容**（有错不显示空）：
         · 失败（且无数据）→ 公共 `RetryBlock`（P3-03）：整块 @tap 上抛 retry 由页面走重拉路径，
           替代静默空态 / 「还没录菜品」误导文案（失败 ≠ 加载中 ≠ 无内容）；
         · 在途（首屏拉取，`LOADING_KEY_HOME` 在途）→ 全局 `.list-foot` 文字行；
         · 空（成功但 0 条）→ 公共 `EmptyState`：**不给 CTA**（同屏已有大类标签栏，属「已有可达入口」⇒ 按 F#2 不给 CTA）。 -->
    <!-- 切视图失败行：`homeError` 且列表非空（旧列表留在屏上）⇒ 顶部一行失败信号 ——
         整条可点，点击走切视图同一条重拉路径（`fetchHomeDishes(true, true)`）。
         列表为空的失败走下方 `RetryBlock`，两者判据互斥。 -->
    <view
      v-if="swapFailed"
      class="home-error-bar"
      role="button"
      aria-label="切换失败，点击重试"
      hover-class="pressed"
      @tap="emit('retry', true)"
    >
      <text class="home-error-bar-text">切换失败 · 点击重试</text>
    </view>

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
      desc="换个大类看看"
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

      <!-- 触底三态（基线 §1.17「列表底部反馈」）：切视图在途 > 封顶 > 到底 > 加载更多在途。
           均为全局 `.list-foot` 用法（与首屏在途文字行同一实现）。
           · 切视图在途：旧列表保留在屏，仅列表末尾追加「正在切换…」（不清空列表）；
           · 封顶：到达保留页数上限给出说明（不静默截断），优先级高于「到底」；
           · 到底：短页封口（`finished`）⇒「没有更多了」，替代反复上滑无信号。 -->
      <view v-if="swapping" class="list-foot">
        <text class="list-foot-text">正在切换…</text>
      </view>
      <view v-else-if="pageLimited" class="list-foot">
        <text class="list-foot-text">已展示前 {{ maxDishes }} 个结果，切换大类可查看更多</text>
      </view>
      <view v-else-if="finished" class="list-foot">
        <text class="list-foot-text">没有更多了</text>
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
import { useDishStore, LOADING_KEY_HOME, LOADING_KEY_HOME_SWAP, HOME_MAX_PAGES } from '@/stores/dish'
import { HOME_PAGE_SIZE } from '@/constants/paging'
import type { DishListItem } from '@/types/dish'
import { dishDetailUrl } from '@/utils/routes'

const emit = defineEmits<{
  /** 列表加载失败后点击重试：上抛页面按既有重拉路径重拉；`keepList=true` = 切视图失败行（不清空旧列表） */
  (e: 'retry', keepList?: boolean): void
}>()

const dishStore = useDishStore()

/** 列表数据源（`store.homeList`）：空态判定与瀑布流分列共用 */
const list = computed(() => dishStore.homeList)

/**
 * 列表首屏在途：只订阅 `LOADING_KEY_HOME`（触底加载更多是另一个 key，不遮挡已有列表）。
 * 在途只给文字行（全局 `.list-foot`），不给骨架屏。
 */
const loading = computed(() => dishStore.isLoading(LOADING_KEY_HOME))
/** 切视图在途（`LOADING_KEY_HOME_SWAP`）：旧列表保留在屏，列表末尾追加一行「正在切换…」 */
const swapping = computed(() => dishStore.isLoading(LOADING_KEY_HOME_SWAP))
/** 触底加载更多在途 */
const loadingMore = computed(() => dishStore.homeLoadingMore)
/** 触达保留页数上限：给出「已展示前 N 个」说明，避免静默截断（优先级高于「到底」） */
const pageLimited = computed(() => dishStore.homePageLimited)
/** 真正到底（短页封口）：列表末尾「没有更多了」，给用户停止上滑的信号 */
const finished = computed(() => dishStore.homeFinished)
const maxDishes = HOME_MAX_PAGES * HOME_PAGE_SIZE

/**
 * 失败两支（判据互斥，判序「失败 > 在途 > 空 > 内容」）：
 * · 列表为空且失败 → 整块 `RetryBlock`（失败 ≠ 无数据）；
 * · 列表非空且失败（切视图失败，旧列表留在屏上）→ 顶部失败行 `.home-error-bar`（唯一失败信号）。
 */
const loadFailed = computed(() => dishStore.homeError && list.value.length === 0)
const swapFailed = computed(() => dishStore.homeError && list.value.length > 0)

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

/* ===== 切视图失败行（P1-1）：--bg-soft 凹陷面 + 卡圆角，整条可点重试 =====
   视觉与 RetryBlock 同一凹陷面语言（但为单行轻量形态，仅用于「列表仍留在屏上」的失败场景）；
   按压走全局 `.pressed`（整块档 opacity 0.7）。 */
.home-error-bar {
  margin-top: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-card);
  background: var(--module-bg);
  text-align: center;
  -webkit-tap-highlight-color: transparent;
}
.home-error-bar-text {
  font-size: var(--font-small);
  font-weight: var(--weight-medium);
  color: var(--text-secondary);
}

/* 失败态块已上提为公共组件 components/RetryBlock.vue（P3-03），样式随之收敛，此处不再保留副本 */

/* 文字行（在途 / 切换中 / 加载更多 / 到底 / 已封顶）样式全部来自全局 `.list-foot` / `.list-foot-text`
   （App.vue 唯一实现），页面不再保留私有副本 */
</style>