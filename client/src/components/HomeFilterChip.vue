<template>
  <!-- 独立筛选组件：仅负责「筛选 chip」的展示与交互，与 AppHeader 解耦。
       选中食堂名由 selectedCanteen prop 决定，点击冒泡 filter 事件交由父级控制下拉显隐。 -->
  <view
    class="home-filter-row"
    :style="{ '--capsule-h': capsuleHeight + 'px' }"
  >
    <view
      class="filter-chip"
      @tap="$emit('filter')"
      role="button"
      :aria-label="filterLabel"
    >
      <IconSvg name="filter" :size="'18px'" color="var(--text-secondary)" />
      <text class="filter-chip-text">{{ filterLabel }}</text>
    </view>

    <!-- 排序胶囊（问题一：行 2 与筛选胶囊并排，点击弹出排序 Sheet） -->
    <view
      class="filter-chip sort-chip"
      @tap="$emit('sort')"
      role="button"
      :aria-label="`排序：${sortLabel}`"
    >
      <IconSvg name="arrow-down" :size="'18px'" color="var(--text-secondary)" />
      <text class="filter-chip-text">{{ sortLabel }}</text>
    </view>

    <!-- 价格胶囊（D13：点击弹出价格 Sheet；选中态文字转主色，底仍 --bg-soft） -->
    <view
      class="filter-chip sort-chip"
      :class="{ 'price-active': priceActive }"
      @tap="$emit('price')"
      role="button"
      :aria-label="`价格：${priceLabel}`"
    >
      <IconSvg name="price" :size="'18px'" :color="priceActive ? 'var(--color-primary)' : 'var(--text-secondary)'" />
      <text class="filter-chip-text">{{ priceLabel }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import IconSvg from './IconSvg.vue'

const props = withDefaults(defineProps<{
  /** 当前选中食堂名（空 = 全部），超长以 ... 省略 */
  selectedCanteen?: string
  /** 胶囊高度（px），用于对齐原生胶囊/搜索框高度；缺省回退 32px */
  capsuleHeight?: number
  /** 当前排序项文案（如「排序 · 最新」），由父级按 HomeSortKey 映射 */
  sortLabel?: string
  /** 价格胶囊文案（选中时显示区间，如「价格 · 0-20」） */
  priceLabel?: string
  /** 价格是否已生效（选中态文字转主色） */
  priceActive?: boolean
}>(), {
  selectedCanteen: '',
  capsuleHeight: 32,
  sortLabel: '排序 · 最新',
  priceLabel: '价格',
  priceActive: false,
})

const emit = defineEmits<{
  (e: 'filter'): void
  (e: 'sort'): void
  (e: 'price'): void
}>()

/** 筛选 chip 文案：选中食堂显示「筛选 · 食堂名」，未选仅「筛选」 */
const filterLabel = computed(() =>
  props.selectedCanteen ? `筛选 · ${props.selectedCanteen}` : '筛选',
)

// 仅用于透传 --capsule-h 变量（与 AppHeader 同一取值口径），无独立副作用
const capsuleHeight = ref(props.capsuleHeight)
onMounted(() => {
  capsuleHeight.value = props.capsuleHeight
})
</script>

<style scoped>
/* 筛选行：左对齐，给搜索框与底边留出呼吸感；自然宽度，超长食堂名仍可完整显示（极长者才省略） */
.home-filter-row {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 0;
  box-sizing: border-box;
}
.filter-chip {
  flex-shrink: 0;
  /* 行独占一行，自然宽度；仅在极端超长食堂名（约 > 一屏宽）时才省略，解决此前 46% 截断看不全的问题 */
  max-width: calc(100vw - var(--spacing-lg) * 2);
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: var(--capsule-h, 32px);
  padding: 0 var(--spacing-md);
  /* 白底筛选条上的浅灰胶囊（与 find 页 filter chip 一致），避免裸 hex */
  background: var(--bg-soft);
  border-radius: var(--radius-pill);
  -webkit-tap-highlight-color: transparent;
  transition: var(--press-transition);
}
/* 无障碍：胶囊高度仅 32px（< 44px 热区），用透明 ::after 纵向扩展命中区域至 ≈44px（global-ui-polish / ui-touch-a11y）；仅纵向避免横向与相邻胶囊重叠 */
.filter-chip::after {
  content: '';
  position: absolute;
  top: -12rpx;
  bottom: -12rpx;
  left: 0;
  right: 0;
}
/* 排序胶囊：与筛选胶囊并排，允许收缩并省略，避免长食堂名把排序挤出屏幕 */
.sort-chip { min-width: 0; flex-shrink: 1; max-width: 50%; background: var(--bg-soft); }
.filter-chip:active { transform: scale(var(--press-scale)); }
/* 价格胶囊选中态（D13）：文字转主色，底仍 --bg-soft；图标色由内联 :color 控制 */
.filter-chip.price-active .filter-chip-text { color: var(--color-primary); }
.filter-chip-text {
  font-size: var(--font-body);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
</style>
