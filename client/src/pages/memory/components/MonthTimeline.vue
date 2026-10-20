<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

/**
 * 年月横向导航（Julienne 时间流；规格见 docs/ui/client/公共组件与形态基线.md §2.2 MonthTimeline）。
 * 每项上下层级：**年份小字在上、月份大号衬线在下**；全部月份 `--font-hero`(64rpx) 同字号，
 * 选中仅加深颜色（年份 + 月份），其余浅灰弱化；**连续月份轴**（空月份保留节点）。
 * 外层 scroll-view 永远可滑；滑动结束后选中月自动居中吸附。
 * 🔴 **月份数组为空 ⇒ 组件整体不渲染**（年月导航只呈现真实记录覆盖的月份轴，见 docs/ui/client/公共组件与形态基线.md §2.2）。
 */
/** 宿主节点虚拟化：组件根直接参与页面布局 */
defineOptions({ options: { virtualHost: true } })

const props = defineProps<{
  months: string[]
  modelValue: string
}>()

const emit = defineEmits<{ (e: 'pick', month: string): void }>()

const CN_MONTHS = [
  '一月', '二月', '三月', '四月', '五月', '六月',
  '七月', '八月', '九月', '十月', '十一月', '十二月',
]

function monthLabel(month: string): string {
  return CN_MONTHS[Number(month.slice(5, 7)) - 1] ?? ''
}

function yearOf(month: string): string {
  return month.slice(0, 4)
}

function onTap(month: string) {
  if (month !== props.modelValue) emit('pick', month)
}

/* 选中月自动居中：跟踪当前 scrollLeft，滑完吸附到视口中心 */
const uid = Math.random().toString(36).slice(2, 8)
const svId = `mt-sv-${uid}`
const itemId = (m: string) => `mt-i-${uid}-${m.replace(/-/g, '')}`
const scrollLeft = ref(0)
const lastScrollLeft = ref(0)

function onScroll(e: { detail: { scrollLeft: number } }) {
  lastScrollLeft.value = e.detail.scrollLeft
}

function centerSelected() {
  if (!props.months.length) return
  nextTick(() => {
    uni.createSelectorQuery()
      .select(`#${svId}`)
      .boundingClientRect((svRaw) => {
        const sv = Array.isArray(svRaw) ? svRaw[0] : svRaw
        if (!sv || sv.left == null || sv.width == null) return
        const svLeft = sv.left
        const svWidth = sv.width
        uni.createSelectorQuery()
          .select(`#${itemId(props.modelValue)}`)
          .boundingClientRect((itemRaw) => {
            const item = Array.isArray(itemRaw) ? itemRaw[0] : itemRaw
            if (!item || item.left == null || item.width == null) return
            const itemLeft = item.left
            const itemWidth = item.width
            const contentOffset = (itemLeft - svLeft) + lastScrollLeft.value
            const target = contentOffset + itemWidth / 2 - svWidth / 2
            scrollLeft.value = Math.max(0, target)
          })
          .exec()
      })
      .exec()
  })
}

watch(() => props.modelValue, centerSelected)
watch(() => props.months, centerSelected, { immediate: true })
</script>

<template>
  <scroll-view
    v-if="months.length"
    :id="svId"
    class="timeline"
    scroll-x
    enhanced
    :show-scrollbar="false"
    :scroll-left="scrollLeft"
    scroll-with-animation
    @scroll="onScroll"
  >
    <view class="timeline__inner">
      <view
        v-for="month in months"
        :key="month"
        :id="itemId(month)"
        class="timeline__item"
        :class="{ 'timeline__item--on': month === modelValue }"
        hover-class="timeline__item--pressed"
        @tap="onTap(month)"
      >
        <text class="timeline__year">{{ yearOf(month) }}</text>
        <text class="timeline__month">{{ monthLabel(month) }}</text>
      </view>
    </view>
  </scroll-view>
</template>

<style lang="scss" scoped>
.timeline {
  width: 100%;
  white-space: nowrap;
  /* 作为时间流区 flex 容器的一子项：保持自身内容高度，不被压缩 */
  flex: none;
}

.timeline__inner {
  display: inline-flex;
  align-items: flex-end;
  gap: var(--spacing-lg);
  padding: var(--page-gutter);
}

/* 每项上下层级：年份小字在上、月份大号衬线在下；整项命中 ≥ 触达下限 */
.timeline__item {
  display: inline-flex;
  flex: none;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--spacing-2xs);
  white-space: nowrap;
  box-sizing: border-box;
  min-height: var(--tap-target-size);
  justify-content: center;
}

.timeline__item--pressed {
  opacity: 0.6;
}

/* 未选：浅灰淡化（年份 + 月份同弱化；当前月实色、历史月低透明度） */
.timeline__year {
  font-size: var(--font-small);
  letter-spacing: 0.02em;
  color: var(--text-placeholder);
  opacity: 0.5;
}

.timeline__month {
  font-family: var(--font-serif);
  font-size: var(--font-hero);
  font-weight: var(--weight-regular);
  letter-spacing: 0.02em;
  color: var(--text-placeholder);
  opacity: 0.5;
}

/* 当前月：仅加深颜色（字号与未选一致，均为 --font-hero） */
.timeline__item--on .timeline__month {
  color: var(--text-title);
  opacity: 1;
}

.timeline__item--on .timeline__year {
  color: var(--text-tertiary);
  opacity: 1;
}
</style>
