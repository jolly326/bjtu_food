<template>
  <view class="feed-wrap">
    <LoadingHint v-if="dishStore.filterInitialLoading && dishStore.filterList.length === 0" />

    <template v-else-if="dishStore.filterList.length > 0">
      <!-- 双列瀑布流：奇偶分列（右列绝不空）；原 WaterfallList 已内联合并到此，减少一层组件嵌套 -->
      <view class="waterfall-grid">
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
    </template>

    <!-- 空态 / 失败态统一复用 EmptyState（global-ui-polish / ui-feed-loading） -->
    <EmptyState
      v-else
      icon="empty"
      :text="waterfallFailed ? '加载失败' : '暂时没有内容'"
      :desc="waterfallFailed ? '网络异常，请稍后重试' : '下拉刷新，或检查网络后重试'"
      :retry="waterfallFailed"
      @retry="emit('retry')"
    />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DishCard from './DishCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingHint from '@/components/LoadingHint.vue'
import { useDishStore } from '@/stores/dish'
import type { Dish } from '@/types/dish'

const props = defineProps<{
  /** 瀑布流加载失败（首屏网络异常），用于空态文案降级 */
  loadFailed?: boolean
}>()

const emit = defineEmits<{
  (e: 'retry'): void
}>()

const dishStore = useDishStore()

/** 空态失败判定：广播/万能区（loadFailed）或瀑布流自身（filterLoadFailed）任一失败即展示失败态，避免「moments 成功但瀑布流失败」被误判为「没有内容」 */
const waterfallFailed = computed(() => props.loadFailed || dishStore.filterLoadFailed)

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
  uni.navigateTo({ url: `/pages/dish/index?id=${dish.id}` })
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
