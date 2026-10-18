<script setup lang="ts">
/**
 * 年月横向导航（Julienne 时间流；规格见 docs/ui/client/公共组件与形态基线.md §2.2 MonthTimeline）。
 * 每项上下层级：**年份小字在上、月份大号衬线在下**；当前月深色，其余浅灰。
 * 只渲染有记录的月份；横向滑动切换。
 */
/** 宿主节点虚拟化：组件根直接参与页面布局 */
defineOptions({ options: { virtualHost: true } })

const props = defineProps<{
  months: string[]
  modelValue: string
}>()

const emit = defineEmits<{ (e: 'pick', month: string): void }>()

const CN_MONTHS = [
  '一月',
  '二月',
  '三月',
  '四月',
  '五月',
  '六月',
  '七月',
  '八月',
  '九月',
  '十月',
  '十一月',
  '十二月',
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
</script>

<template>
  <scroll-view class="timeline" scroll-x enhanced :show-scrollbar="false">
    <view class="timeline__inner">
      <view
        v-for="month in months"
        :key="month"
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
}

.timeline__inner {
  display: inline-flex;
  align-items: flex-end;
  gap: var(--spacing-lg);
  padding: var(--spacing-xs) var(--page-gutter) var(--spacing-sm);
}

/* 每项上下层级：年份小字在上、月份大号衬线在下；整项命中 ≥ 触达下限 */
.timeline__item {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--spacing-3xs);
  min-height: var(--tap-target-size);
  justify-content: center;
}

.timeline__item--pressed {
  opacity: 0.6;
}

/* 未选：浅灰（年份 + 月份同弱化） */
.timeline__year {
  font-size: var(--font-small);
  letter-spacing: 0.02em;
  color: var(--text-placeholder);
  opacity: 0.65;
}

.timeline__month {
  font-family: var(--font-serif);
  font-size: var(--font-subtitle);
  font-weight: var(--weight-regular);
  letter-spacing: 0.02em;
  color: var(--text-placeholder);
  opacity: 0.65;
}

/* 当前月：月份大号衬线深色，年份提档 */
.timeline__item--on .timeline__month {
  font-size: var(--font-title);
  color: var(--text-title);
  opacity: 1;
}

.timeline__item--on .timeline__year {
  color: var(--text-tertiary);
  opacity: 1;
}
</style>
