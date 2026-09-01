<template>
  <!-- 筛选行：左=食堂(切换)，右=价格(切换)，最右=筛选图标→详细筛选页(find) -->
  <view
    class="home-filter-row"
    :style="{ '--capsule-h': capsuleHeight + 'px' }"
  >
    <!-- 食堂切换（左侧） -->
    <view
      class="filter-chip"
      :class="{ active: filterActive }"
      @tap="$emit('filter')"
      role="button"
      :aria-label="filterLabel"
    >
      <IconSvg name="dish" :size="'18px'" :color="filterActive ? 'var(--color-on-primary)' : 'var(--text-secondary)'" />
      <text class="filter-chip-text">{{ filterLabel }}</text>
      <IconSvg name="arrow-down" :size="'16px'" :color="filterActive ? 'var(--color-on-primary)' : 'var(--text-tertiary)'" />
    </view>

    <!-- 价格切换（右侧） -->
    <view
      class="filter-chip price-chip"
      :class="{ active: priceActive }"
      @tap="$emit('price')"
      role="button"
      :aria-label="`价格：${priceLabel}`"
    >
      <IconSvg name="price" :size="'18px'" :color="priceActive ? 'var(--color-on-primary)' : 'var(--text-secondary)'" />
      <text class="filter-chip-text">{{ priceLabel }}</text>
      <IconSvg name="arrow-down" :size="'16px'" :color="priceActive ? 'var(--color-on-primary)' : 'var(--text-tertiary)'" />
    </view>

    <!-- 详细筛选图标：点击进入 find 二级筛选页 -->
    <view
      class="filter-icon-btn"
      role="button"
      aria-label="详细筛选"
      @tap="$emit('openFilter')"
    >
      <IconSvg name="filter" :size="'20px'" color="var(--text-secondary)" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import IconSvg from './IconSvg.vue'

const props = withDefaults(defineProps<{
  /** 当前选中食堂名（空 = 全部），超长以 ... 省略 */
  selectedCanteen?: string
  /** 胶囊高度（px），用于对齐原生胶囊/搜索框高度；缺省回退 36px */
  capsuleHeight?: number
  /** 价格胶囊文案（选中时显示区间，如「0-20」） */
  priceLabel?: string
  /** 价格是否已生效（选中态文字转主色） */
  priceActive?: boolean
  /** 食堂筛选是否已激活（展开面板或已选食堂 → 红底白字） */
  filterActive?: boolean
}>(), {
  selectedCanteen: '',
  capsuleHeight: 36,
  priceLabel: '价格',
  priceActive: false,
  filterActive: false,
})

const emit = defineEmits<{
  (e: 'filter'): void
  (e: 'price'): void
  (e: 'openFilter'): void
}>()

/** 筛选 chip 文案：选中食堂显示「食堂名」，未选仅「全部食堂」 */
const filterLabel = computed(() =>
  props.selectedCanteen || '全部食堂',
)

// 仅用于透传 --capsule-h 变量（与 AppHeader 同一取值口径），无独立副作用
const capsuleHeight = ref(props.capsuleHeight)
onMounted(() => {
  capsuleHeight.value = props.capsuleHeight
})
</script>

<style scoped>
/* 筛选行：两端对齐，左=食堂+价格，右=筛选图标 */
.home-filter-row {
  display: flex;
  align-items: center;
  padding: 0;
  box-sizing: border-box;
  gap: var(--spacing-sm);
}
.filter-chip {
  flex-shrink: 0;
  max-width: calc(100vw - var(--spacing-lg) * 2);
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: var(--capsule-h, 36px);
  padding: 0 var(--spacing-md);
  /* 抬起控制件：白底从米色筛选条中浮起 */
  background: var(--bg-card);
  border-radius: var(--radius-pill);
  -webkit-tap-highlight-color: transparent;
  transition: var(--press-transition);
}
/* 无障碍：胶囊高度 < 44px 热区，透明 ::after 扩展命中区域至 ≈44px */
.filter-chip::after {
  content: '';
  position: absolute;
  top: -12rpx;
  bottom: -12rpx;
  left: 0;
  right: 0;
}
/* 价格胶囊允许收缩，避免长文本挤出 */
.price-chip { min-width: 0; flex-shrink: 1; max-width: 45%; background: var(--bg-card); }
.filter-chip:active { transform: scale(var(--press-scale)); }
/* 选中态：主色底白字 */
.filter-chip.active { background: var(--color-primary); }
.filter-chip.active .filter-chip-text { color: var(--color-on-primary); }
.filter-chip-text {
  font-size: var(--font-body);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
/* 右侧筛选图标按钮：透明底，圆形热区 */
.filter-icon-btn {
  flex-shrink: 0;
  margin-left: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: var(--radius-circle);
  -webkit-tap-highlight-color: transparent;
  transition: var(--press-transition);
}
.filter-icon-btn:active { transform: scale(var(--press-scale)); background: var(--bg-soft); }
</style>
