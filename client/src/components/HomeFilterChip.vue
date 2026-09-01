<template>
  <!-- 筛选行：左侧两个按钮（全部食堂 / 全部价格），最右侧筛选图标常驻（暂不挂任何跳转） -->
  <view
    class="home-filter-row"
    :style="{ '--capsule-h': capsuleHeight + 'px' }"
  >
    <!-- 左组：食堂 + 价格 两颗胶囊（独占剩余空间、可收缩，长文案以 … 省略） -->
    <view class="filter-chips">
      <!-- 食堂切换（左1）：仅展开时红底；收起后文案回显所选食堂名 -->
      <view
        class="filter-chip"
        :class="{ active: filterOpen }"
        @tap="$emit('filter')"
        role="button"
        :aria-label="filterLabel"
      >
        <IconSvg class="chip-icon" name="dish" :size="'18px'" :color="filterOpen ? 'var(--color-on-primary)' : 'var(--text-secondary)'" />
        <text class="filter-chip-text">{{ filterLabel }}</text>
        <IconSvg class="chip-icon" name="arrow-down" :size="'16px'" :color="filterOpen ? 'var(--color-on-primary)' : 'var(--text-tertiary)'" />
      </view>

      <!-- 价格切换（左2）：仅展开时红底；收起后文案回显所选区间（元） -->
      <view
        class="filter-chip price-chip"
        :class="{ active: priceOpen }"
        @tap="$emit('price')"
        role="button"
        :aria-label="`价格：${priceLabel}`"
      >
        <IconSvg class="chip-icon" name="price" :size="'18px'" :color="priceOpen ? 'var(--color-on-primary)' : 'var(--text-secondary)'" />
        <text class="filter-chip-text">{{ priceLabel }}</text>
        <IconSvg class="chip-icon" name="arrow-down" :size="'16px'" :color="priceOpen ? 'var(--color-on-primary)' : 'var(--text-tertiary)'" />
      </view>
    </view>

    <!-- 筛选图标：恒在最右（左组 flex:1 吃掉全部剩余空间，且本按钮不收缩）；按产品要求暂不挂跳转 -->
    <view
      class="filter-icon-btn"
      role="button"
      aria-label="详细筛选"
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
  /** 价格胶囊文案：未选「全部价格」，已选回显区间（元，由父级用 fenToYuan 换算后传入） */
  priceLabel?: string
  /** 食堂下拉是否展开（仅展开时红底白字；收起后仅文案回显选中值） */
  filterOpen?: boolean
  /** 价格下拉是否展开（仅展开时红底白字；收起后仅文案回显选中值） */
  priceOpen?: boolean
}>(), {
  selectedCanteen: '',
  capsuleHeight: 36,
  priceLabel: '全部价格',
  filterOpen: false,
  priceOpen: false,
})

const emit = defineEmits<{
  (e: 'filter'): void
  (e: 'price'): void
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
/* 筛选行：左侧两颗胶囊（食堂/价格），筛选图标常驻最右 */
.home-filter-row {
  display: flex;
  align-items: center;
  /* ⚠️ flex:1 不可移除：本组件被 .filter-bar / .find-filter-row（均为 display:flex）包裹，
     作为 flex item 默认 flex:0 1 auto → 宽度只按内容收缩、不撑满父级，
     此时 .filter-icon-btn 的 margin-left:auto 没有任何剩余空间可分配，
     筛选图标会紧贴价格胶囊右侧而不是靠右。必须撑满，auto 外边距才生效。 */
  flex: 1;
  min-width: 0;
  padding: 0;
  box-sizing: border-box;
  gap: var(--spacing-sm);
}
/* 左组（食堂 + 价格）：独占全部剩余空间（flex:1），把右侧筛选图标顶到最右。
   组内两颗胶囊 flex:1 平均分左区空间，超长食堂名仅在其内部省略（min-width:0），
   既均分又不挤出右侧图标。 */
.filter-chips {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  gap: var(--spacing-sm);
}
.filter-chip {
  /* ⚠️ flex:1 均分左区：两胶囊各占一半剩余空间，平均分配左侧区域。
     min-width:0 保证长食堂名只在其内部省略，不会把右侧筛选图标挤出屏幕。 */
  flex: 1;
  min-width: 0;
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
/* 价格胶囊：与食堂胶囊同规则（基类已覆盖），保留类名供未来单独定制 */
.price-chip { background: var(--bg-card); }
/* 胶囊内图标不参与收缩，让省略号只截文字，避免图标被压扁 */
.chip-icon { flex-shrink: 0; }
.filter-chip:active { transform: scale(var(--press-scale)); }
/* 展开态：主色底白字（收起后即恢复白底，选中值只由文案回显） */
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
