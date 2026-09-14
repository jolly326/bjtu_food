<script setup lang="ts">
/**
 * StarRating：评分星级统一呈现（P3-17，消除 4 处 `'★'.repeat()` 内联重复实现）。
 *
 * 视觉与既有实现完全一致：
 *  - `max`（默认 5）> 0：实心星 + 空位星（实心 `--color-star`、空位 `--border-strong`，字距 1px）；
 *  - `max` = 0：仅渲染实心星（用户活动弹窗列表用，颜色走 `--color-warning` 变体）。
 *
 * 纯展示组件（无交互），`aria-label` 输出「5 分满分，x 分」供读屏识别。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 当前评分（0 – max） */
    value: number
    /** 满分；0 = 只显示已得实心星（不显示空位） */
    max?: number
    /** 颜色变体：star = 金色星（默认，评价列表/详情）；warning = 徽标黄（用户活动弹窗） */
    tone?: 'star' | 'warning'
  }>(),
  { max: 5, tone: 'star' },
)

/** 归一化数值：负数/NaN 归 0，超出 max 时截断，避免 `repeat` 抛 RangeError */
const safeValue = computed(() => Math.max(0, Math.min(Number(props.value) || 0, props.max || Number(props.value) || 0)))
const filled = computed(() => '★'.repeat(safeValue.value))
/** 空位星数：max=0（只显示实心）时为空 */
const empty = computed(() => (props.max > 0 ? '★'.repeat(Math.max(0, props.max - safeValue.value)) : ''))
</script>

<template>
  <span
    class="star-rating"
    :class="tone === 'warning' ? 'tone-warning' : 'tone-star'"
    role="img"
    :aria-label="max > 0 ? `${max} 分满分，${value} 分` : `${value} 星`"
  >
    <span class="star-on">{{ filled }}</span><span v-if="empty" class="star-off">{{ empty }}</span>
  </span>
</template>

<style scoped>
.star-rating { letter-spacing: 1px; }
.star-on { color: var(--color-star); }
.star-off { color: var(--border-strong); }
/* 用户活动弹窗列表：仅实心星 + 徽标黄变体（视觉与既有 .ua-stars 一致） */
.tone-warning .star-on { color: var(--color-warning); }
</style>
