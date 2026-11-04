<script setup lang="ts">
/**
 * 结果条（docs/ui/client/菜品首页.md `ResultBar`）。
 *
 * 常驻主行说明「当前在看什么」；按需撑出一行横向滚动的约束胶囊与取值，
 * 不设下拉、不弹层、不遮挡结果区。
 */
import { computed, ref } from 'vue'

import { RESULT } from '@/constants'
import type { Constraint } from '@/utils/constraints'
import { budgetConstraint, canteenConstraint } from '@/utils/constraints'

defineOptions({ options: { virtualHost: true } })

const props = defineProps<{
  /** 已提交的 query（空 = 无条件结果集） */
  submittedQuery: string
  /** 当前结果卡数（在售位置数） */
  count: number
  /** 去重后的菜名数 */
  dishCount: number
  /** 已生效约束 */
  constraints: Constraint[]
  /** 可选食堂名（全量集 locations 去重） */
  canteenOptions: string[]
  /** 输入框文本与已提交 query 不一致 */
  stale: boolean
}>()

const emit = defineEmits<{
  (e: 'pick', constraint: Constraint): void
  (e: 'remove', constraint: Constraint): void
  (e: 'clear'): void
}>()

const panel = ref<'canteen' | 'budget' | null>(null)

const conditioned = computed(() => props.submittedQuery.length > 0)
const title = computed(() =>
  conditioned.value ? `「${props.submittedQuery}」` : '今天吃什么',
)
const countText = computed(() =>
  conditioned.value
    ? `找到 ${props.count} 处 · 按相关度`
    : `共 ${props.count} 处 · ${props.dishCount} 道`,
)

/** 已选在前，其余取值在后；同一行横向滚动，不设「更多」 */
const budgetOptions = computed(() =>
  RESULT.BUDGET_OPTIONS.map((yuan) => {
    const c = budgetConstraint(yuan)
    return { c, on: props.constraints.some((x) => x.value === c.value) }
  }),
)
const canteenOptions = computed(() =>
  props.canteenOptions.map((name) => {
    const c = canteenConstraint(name)
    return { c, on: props.constraints.some((x) => x.value === c.value) }
  }),
)

const extraVisible = computed(() => panel.value !== null || props.constraints.length > 0)
/** 已生效约束已由胶囊呈现，取值行只列其余可选值，避免同名重复 */
const pickedOptions = computed(() => {
  const all = panel.value === 'budget' ? budgetOptions.value : panel.value === 'canteen' ? canteenOptions.value : []
  return all.filter((opt) => !opt.on)
})

function togglePanel(kind: 'canteen' | 'budget'): void {
  panel.value = panel.value === kind ? null : kind
}

function onPick(c: Constraint): void {
  emit('pick', c)
  panel.value = null
}
</script>

<template>
  <view class="bar" :class="{ 'bar--stale': stale }">
    <view class="bar__main">
      <text class="bar__title">{{ stale ? '结果待更新 · 按回车搜索' : title }}</text>
      <view class="bar__aside">
        <text class="bar__count">{{ countText }}</text>
        <text class="bar__entry" hover-class="bar__entry--press" @tap="togglePanel('canteen')">食堂</text>
        <text class="bar__entry" hover-class="bar__entry--press" @tap="togglePanel('budget')">预算</text>
        <text v-if="conditioned" class="bar__entry" hover-class="bar__entry--press" @tap="emit('clear')">清空</text>
      </view>
    </view>

    <view v-if="extraVisible" class="bar__extra">
      <scroll-view class="bar__scroll" scroll-x :show-scrollbar="false">
        <view class="bar__row">
          <view
            v-for="c in constraints"
            :key="`on-${c.value}`"
            class="bar__chip"
            hover-class="bar__chip--press"
            @tap="emit('remove', c)"
          >
            <text>{{ c.label }}</text>
            <text class="bar__chipX">×</text>
          </view>
          <view
            v-for="opt in pickedOptions"
            :key="`opt-${opt.c.value}`"
            class="bar__value"
            :class="{ 'bar__value--on': opt.on }"
            hover-class="bar__value--press"
            @tap="onPick(opt.c)"
          >
            <text>{{ opt.c.label }}</text>
          </view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.bar {
  box-sizing: border-box;
  padding: var(--spacing-xs) var(--page-gutter);
  background: var(--bg-page);
}

.bar--stale {
  opacity: 0.6;
}

.bar__main {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--spacing-sm);
  min-height: var(--control-height-sm);
}

.bar__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--font-subtitle);
  color: var(--text-title);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bar__aside {
  display: flex;
  flex-shrink: 0;
  align-items: baseline;
  gap: var(--spacing-sm);
}

.bar__count {
  font-size: var(--font-caption);
  color: var(--text-body);
  white-space: nowrap;
}

.bar__entry {
  position: relative;
  font-size: var(--font-caption);
  color: var(--text-body);
  white-space: nowrap;
}

/* 纯文字入口视觉尺寸小，命中区纵向补足到触达下限 */
.bar__entry::after {
  position: absolute;
  top: calc((var(--tap-target-size) - var(--control-height-sm)) / -2);
  right: calc(var(--spacing-2xs) * -1);
  left: calc(var(--spacing-2xs) * -1);
  height: var(--tap-target-size);
  content: '';
}

.bar__entry--press {
  opacity: 0.6;
}

.bar__extra {
  overflow: hidden;
  margin-top: var(--spacing-xs);
  animation: bar-in var(--duration-base) var(--ease-out);
}

@keyframes bar-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

.bar__scroll {
  width: 100%;
  white-space: nowrap;
}

.bar__row {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding-bottom: var(--spacing-xs);
}

.bar__chip {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2xs);
  box-sizing: border-box;
  height: var(--control-height-sm);
  padding: 0 var(--spacing-sm);
  border: var(--border-width) solid var(--brand);
  border-radius: var(--radius-pill);
  background: var(--brand);
  font-size: var(--font-caption);
  color: var(--on-brand);
}

.bar__chip--press {
  opacity: 0.7;
}

.bar__chipX {
  color: var(--on-brand);
}

.bar__value {
  display: inline-flex;
  align-items: center;
  box-sizing: border-box;
  height: var(--control-height-sm);
  padding: 0 var(--spacing-xs);
  font-size: var(--font-caption);
  color: var(--text-body);
}

.bar__value--on {
  color: var(--text-disabled);
}

.bar__value--press {
  opacity: 0.6;
}
</style>
