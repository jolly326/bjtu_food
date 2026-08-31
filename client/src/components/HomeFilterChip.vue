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
      <IconSvg name="filter" :size="'18px'" color="var(--color-on-primary)" />
      <text class="filter-chip-text">{{ filterLabel }}</text>
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
}>(), {
  selectedCanteen: '',
  capsuleHeight: 32,
})

const emit = defineEmits<{
  (e: 'filter'): void
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
  padding: var(--spacing-xs) var(--spacing-lg) 0;
  box-sizing: border-box;
}
.filter-chip {
  flex-shrink: 0;
  /* 行独占一行，自然宽度；仅在极端超长食堂名（约 > 一屏宽）时才省略，解决此前 46% 截断看不全的问题 */
  max-width: calc(100vw - var(--spacing-lg) * 2);
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: var(--capsule-h, 32px);
  padding: 0 var(--spacing-md);
  /* 红色头部上的半透明白底胶囊，保证可见性（避免裸 hex，使用 rgba 叠白） */
  background: rgba(255, 255, 255, 0.18);
  border-radius: var(--radius-pill);
  -webkit-tap-highlight-color: transparent;
  transition: var(--press-transition);
}
.filter-chip:active { transform: scale(var(--press-scale)); }
.filter-chip-text {
  font-size: var(--font-body);
  color: var(--color-on-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
</style>
