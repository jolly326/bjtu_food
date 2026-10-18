<script setup lang="ts">
/**
 * 年月横向导航（Julienne 时间流；规格见 docs/ui/client/公共组件与形态基线.md §2.2 MonthTimeline）。
 * 选中项 = 「2026 十月」（年份 + 月份，加深加大）；未选项 = 「九月」（弱化为低透明度浅灰）。
 * 只渲染有记录的月份；横向滑动切换。
 */
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
        <text v-if="month === modelValue" class="timeline__year">{{ yearOf(month) }}</text>
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
  align-items: baseline;
  gap: var(--spacing-lg);
  padding: var(--spacing-xs) var(--page-gutter) var(--spacing-sm);
}

.timeline__item {
  display: inline-flex;
  align-items: baseline;
  gap: var(--spacing-2xs);
}

.timeline__item--pressed {
  opacity: 0.6;
}

.timeline__year {
  font-size: var(--font-small);
  letter-spacing: 0.02em;
  color: var(--text-placeholder);
}

.timeline__month {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-regular);
  letter-spacing: 0.02em;
  color: var(--text-placeholder);
  opacity: 0.65;
}

.timeline__item--on .timeline__month {
  font-size: var(--font-h3);
  font-weight: var(--weight-medium);
  letter-spacing: 0.01em;
  color: var(--text-title);
  opacity: 1;
}

.timeline__item--on .timeline__year {
  font-weight: var(--weight-medium);
}
</style>
