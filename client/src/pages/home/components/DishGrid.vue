<script setup lang="ts">
/**
 * 菜品图标网格（docs/ui/client/公共组件与形态基线.md §2.2 `DishGrid`）。
 *
 * 3 列竖向滚动；内联展开卡插在被点磁贴所在行的下方（整行），
 * 网格其余部分保持可滚动 / 可点选，不出现遮罩或弹窗。
 */
import { computed, nextTick, ref, watch } from 'vue'

import type { DishVO } from '@/api'
import DishExpandCard from './DishExpandCard.vue'
import DishTile from './DishTile.vue'

defineOptions({ options: { virtualHost: true } })

const props = defineProps<{
  dishes: DishVO[]
  empty: boolean
  expandedId: number | null
}>()

const emit = defineEmits<{
  (e: 'open', dish: DishVO): void
  (e: 'clear'): void
  (e: 'collapse'): void
  (e: 'feedback'): void
}>()

type Cell =
  | { type: 'tile'; dish: DishVO }
  | { type: 'card'; dish: DishVO }

const cells = computed<Cell[]>(() => {
  const list = props.dishes
  const result: Cell[] = []

  if (props.expandedId === null) {
    list.forEach((dish) => result.push({ type: 'tile', dish }))
    return result
  }

  const index = list.findIndex((dish) => dish.id === props.expandedId)
  if (index < 0) {
    list.forEach((dish) => result.push({ type: 'tile', dish }))
    return result
  }

  // 插到被点磁贴所在行的末尾，避免整行出现空位
  const rowEnd = Math.min(Math.ceil((index + 1) / 3) * 3, list.length)
  list.slice(0, rowEnd).forEach((dish) => result.push({ type: 'tile', dish }))
  result.push({ type: 'card', dish: list[index] })
  list.slice(rowEnd).forEach((dish) => result.push({ type: 'tile', dish }))
  return result
})

const scrollIntoView = ref('')

watch(
  () => props.expandedId,
  async (id) => {
    if (id === null) {
      scrollIntoView.value = ''
      return
    }
    await nextTick()
    scrollIntoView.value = `dish-expand-${id}`
  },
)
</script>

<template>
  <scroll-view
    class="grid-scroll"
    scroll-y
    :scroll-into-view="scrollIntoView"
    scroll-with-animation
  >
    <view v-if="empty" class="grid-empty">
      <text class="grid-empty__text">没找到符合的菜，换个说法试试</text>
      <view class="grid-empty__btn" hover-class="grid-empty__btn--press" @tap="emit('clear')">
        清空搜索
      </view>
    </view>

    <view v-else class="grid">
      <template v-for="cell in cells" :key="cell.type === 'card' ? `card-${cell.dish.id}` : `tile-${cell.dish.id}`">
        <view
          v-if="cell.type === 'card'"
          :id="`dish-expand-${cell.dish.id}`"
          class="grid__cell grid__cell--full"
        >
          <DishExpandCard
            :dish="cell.dish"
            @collapse="emit('collapse')"
            @feedback="emit('feedback')"
          />
        </view>
        <view v-else class="grid__cell">
          <DishTile :dish="cell.dish" @open="emit('open', $event)" />
        </view>
      </template>
    </view>
  </scroll-view>
</template>

<style lang="scss" scoped>
.grid-scroll {
  flex: 1;
  min-height: 0;
  height: 0;
}

/* 留白写在滚动内容内层：scroll-view 自身的内边距不参与滚动裁剪，会让内容顶到页头。
   底部按 `--dock-reserve` 让空，末行才能滚到悬浮搜索胶囊之上，不被遮挡。
   底部内距分两层声明：先给纯变量兜底，再叠加安全区 —— 后者失效时前者仍生效。 */
.grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  padding: var(--spacing-lg) var(--page-gutter);
  padding-bottom: var(--dock-reserve);
  padding-bottom: calc(var(--dock-reserve) + env(safe-area-inset-bottom, 0px));
}

.grid__cell {
  width: calc((100% - var(--spacing-md) * 2) / 3);
  margin-bottom: var(--spacing-md);
}

.grid__cell--full {
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

.grid-empty__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: var(--control-height);
  padding: 0 var(--spacing-xl);
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-pill);
  background: var(--pill-bg);
  font-size: var(--font-subtitle);
  color: var(--text-title);
}

.grid-empty__btn--press {
  opacity: 0.7;
}
</style>
