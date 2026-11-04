<script setup lang="ts">
/**
 * 结果区（docs/ui/client/菜品首页.md `DishGrid`）。
 *
 * 1 列竖向滚动结果卡；每个 locations 项渲染一张卡，以 offeringId 为稳定键。
 * 描述全文展开由 expandedId 单点控制，展开后用 scroll-into-view 把该卡带入可视区。
 */
import { computed, nextTick, ref, watch } from 'vue'

import type { DishLocationVO, DishVO } from '@/api'
import DishResultCard from './DishResultCard.vue'

defineOptions({ options: { virtualHost: true } })

type CardItem = { dish: DishVO; loc: DishLocationVO; offeringId: number }

const props = defineProps<{
  dishes: DishVO[]
  expandedId: number | null
  filterLabel: string
  /** 空态类型：none 正常 / filtered 有约束 0 命中 / plain 无约束 0 命中 / catalog 全量无菜 */
  emptyKind: 'none' | 'filtered' | 'plain' | 'catalog'
}>()

const emit = defineEmits<{
  (e: 'open', offeringId: number): void
  (e: 'collapse'): void
  (e: 'feedback', dishId: number): void
  (e: 'pick', canteenName: string): void
  (e: 'clear'): void
  (e: 'relax'): void
}>()

/** 结果集版本：submittedQuery 变化时递增，用于交叉淡入 */
const fadeKey = computed(() => props.dishes.map((d) => d.id).join(','))

const cards = computed<CardItem[]>(() =>
  props.dishes.flatMap((dish) =>
    dish.locations.map((loc) => ({ dish, loc, offeringId: loc.offeringId })),
  ),
)

const scrollIntoView = ref('')
watch(
  () => props.expandedId,
  async (id) => {
    if (id === null) {
      scrollIntoView.value = ''
      return
    }
    await nextTick()
    scrollIntoView.value = `dish-result-${id}`
  },
)
</script>

<template>
  <scroll-view class="grid-scroll" scroll-y :scroll-into-view="scrollIntoView" scroll-with-animation>
    <view v-if="emptyKind !== 'none'" class="grid-empty">
      <text v-if="emptyKind === 'filtered'" class="grid-empty__text">
        「{{ filterLabel }}」暂时没有符合的菜
      </text>
      <text v-else-if="emptyKind === 'plain'" class="grid-empty__text">没找到符合的菜，换个说法试试</text>
      <text v-else class="grid-empty__text">还没有菜品上架</text>

      <view v-if="emptyKind === 'filtered'" class="grid-empty__actions">
        <view class="grid-empty__btn" hover-class="grid-empty__btn--press" @tap="emit('relax')">去掉该条件</view>
        <view class="grid-empty__btn" hover-class="grid-empty__btn--press" @tap="emit('clear')">看全部菜品</view>
      </view>
      <view v-else-if="emptyKind === 'plain'" class="grid-empty__actions">
        <view class="grid-empty__btn" hover-class="grid-empty__btn--press" @tap="emit('clear')">看全部菜品</view>
      </view>
    </view>

    <view v-else :key="fadeKey" class="grid">
      <view
        v-for="card in cards"
        :id="`dish-result-${card.offeringId}`"
        :key="card.offeringId"
        class="grid__cell"
      >
        <DishResultCard
          :dish="card.dish"
          :loc="card.loc"
          :expanded="expandedId === card.offeringId"
          @open="emit('open', $event)"
          @feedback="emit('feedback', $event)"
          @pick="emit('pick', $event)"
        />
      </view>
    </view>
  </scroll-view>
</template>

<style lang="scss" scoped>
.grid-scroll {
  flex: 1;
  min-height: 0;
  height: 0;
}

.grid {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  box-sizing: border-box;
  padding: var(--spacing-lg) var(--page-gutter);
  padding-bottom: var(--dock-reserve);
  padding-bottom: calc(var(--dock-reserve) + env(safe-area-inset-bottom));
  animation: grid-in var(--duration-base) var(--ease-out);
}

@keyframes grid-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

.grid__cell {
  width: 100%;
}

.grid-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--spacing-md);
  padding: var(--spacing-2xl) var(--page-gutter);
}

.grid-empty__text {
  font-size: var(--font-body);
  color: var(--text-body);
  text-align: center;
}

.grid-empty__actions {
  display: flex;
  gap: var(--spacing-sm);
}

.grid-empty__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: var(--control-height);
  padding: 0 var(--spacing-xl);
  border: var(--border-width) solid var(--brand);
  border-radius: var(--radius-pill);
  background: var(--brand);
  font-size: var(--font-subtitle);
  color: var(--on-brand);
}

.grid-empty__btn--press {
  opacity: 0.7;
}
</style>
