<script setup lang="ts">
/**
 * 结果区（docs/ui/client/菜品首页.md `DishGrid`）。
 *
 * 1 列竖向滚动结果卡；一道菜一张卡，以 `dish.id` 为稳定键，卡内并列该菜全部在售位置。
 * 描述展开态与反馈表单展开态由父级全局唯一控制，同一时间仅一张卡展开。
 */
import { computed, nextTick, ref, watch } from 'vue'

import type { DishVO } from '@/api'
import DishResultCard from './DishResultCard.vue'

defineOptions({ options: { virtualHost: true } })

const props = defineProps<{
  dishes: DishVO[]
  conditioned: boolean
  expandedId: number | null
  feedbackOpenId: number | null
}>()

const emit = defineEmits<{
  (e: 'open', dishId: number): void
  (e: 'feedback-open', dishId: number | null): void
  (e: 'clear'): void
}>()

/** 空态由结果集与条件态派生：none 正常 / plain 有条件 0 命中 / catalog 全量无菜 */
const emptyKind = computed<'none' | 'plain' | 'catalog'>(() => {
  if (props.dishes.length > 0) return 'none'
  return props.conditioned ? 'plain' : 'catalog'
})

/** 结果集版本：菜品行变化时递增，用于新列表淡入 */
const fadeKey = computed(() => props.dishes.map((dish) => dish.id).join(','))

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
        <view
          class="grid-empty__btn"
          hover-class="grid-empty__btn--press"
          aria-role="button"
          aria-label="看全部菜品"
          @tap="emit('clear')"
        >看全部菜品</view>
      </view>
    </view>

    <view v-else :key="fadeKey" class="grid">
      <view
        v-for="dish in dishes"
        :id="`dish-result-${dish.id}`"
        :key="dish.id"
        class="grid__cell"
      >
        <DishResultCard
          :dish="dish"
          :expanded="expandedId === dish.id"
          :feedback-open="feedbackOpenId === dish.id"
          @open="emit('open', $event)"
          @feedback-open="emit('feedback-open', $event)"
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
  animation: motion-fade var(--duration-base) var(--ease-out);
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
  background: var(--bg-card);
  font-size: var(--font-subtitle);
  color: var(--brand);
}

.grid-empty__btn--press {
  opacity: 0.7;
}
</style>
