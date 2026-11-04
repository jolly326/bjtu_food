<script setup lang="ts">
/**
 * 结果区（docs/ui/client/菜品首页.md `DishGrid`）。
 *
 * 1 列竖向滚动结果卡；一道菜的每个在售位置渲染一张卡，以 `dish_location.id` 为稳定键。
 */
import { computed, nextTick, ref, watch } from 'vue'

import type { DishVO } from '@/api'
import DishResultCard from './DishResultCard.vue'

defineOptions({ options: { virtualHost: true } })

const props = defineProps<{
  dishes: DishVO[]
  expandedId: number | null
  /** 空态类型：none 正常 / plain 有条件 0 命中 / catalog 全量无菜 */
  emptyKind: 'none' | 'plain' | 'catalog'
}>()

const emit = defineEmits<{
  (e: 'open', locationId: number): void
  (e: 'feedback', dishId: number): void
  (e: 'clear'): void
}>()

/** 菜品 × 位置摊平：每个在售位置一张卡 */
const cards = computed(() =>
  props.dishes.flatMap((dish) => dish.locations.map((loc) => ({ dish, loc }))),
)

/** 结果集版本：结果行变化时递增，用于交叉淡入 */
const fadeKey = computed(() => cards.value.map((card) => card.loc.id).join(','))

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
      <text v-if="emptyKind === 'plain'" class="grid-empty__text">没找到，换个说法</text>
      <text v-else class="grid-empty__text">还没有菜品上架</text>

      <view v-if="emptyKind === 'plain'" class="grid-empty__actions">
        <view class="grid-empty__btn" hover-class="grid-empty__btn--press" @tap="emit('clear')">看全部菜品</view>
      </view>
    </view>

    <view v-else :key="fadeKey" class="grid">
      <view
        v-for="card in cards"
        :id="`dish-result-${card.loc.id}`"
        :key="card.loc.id"
        class="grid__cell"
      >
        <DishResultCard
          :dish="card.dish"
          :loc="card.loc"
          :expanded="expandedId === card.loc.id"
          @open="emit('open', $event)"
          @feedback="emit('feedback', $event)"
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
