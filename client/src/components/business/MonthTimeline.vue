<script setup lang="ts">
/**
 * 月份时间线（规格见 docs/ui/client/公共组件与形态基线.md §2.2 MonthTimeline）。
 * 每项纵向两行：年份 + 月份；选中月放大加粗。只渲染有记录的月份。
 */
const props = defineProps<{
  months: string[]
  modelValue: string
}>()

const emit = defineEmits<{ (e: 'pick', month: string): void }>()

function label(month: string): string {
  return `${Number(month.slice(5, 7))}月`
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
        @tap="onTap(month)"
      >
        <text class="timeline__year">{{ month.slice(0, 4) }}</text>
        <text class="timeline__month">{{ label(month) }}</text>
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
  gap: var(--spacing-lg);
  padding: var(--spacing-xs) var(--page-gutter);
}

.timeline__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-2xs);
}

.timeline__year {
  font-size: var(--font-tiny);
  color: var(--text-tertiary);
}

.timeline__month {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
}

.timeline__item--on .timeline__month {
  font-size: var(--font-h2);
  font-weight: var(--weight-heavy);
  color: var(--text-title);
}

.timeline__item--on .timeline__year {
  color: var(--text-body);
}
</style>
