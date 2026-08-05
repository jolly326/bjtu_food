<script setup lang="ts">
/**
 * StatCard：统计卡（T-1）。
 * - props: label / value / icon? / sub? / tone('default'|'primary'|'success'|'warning'|'danger')
 * - 数值色走 token，禁止硬编码。
 * - 入场 scale(0.95)+opacity:0，spring 1.0/0.3；支持 stagger 延迟（delay 属性）。
 */
import { type Component, type PropType } from 'vue'

const props = withDefaults(
  defineProps<{
    label?: string
    value?: string | number
    icon?: Component
    sub?: string
    tone?: 'default' | 'primary' | 'success' | 'warning' | 'danger' | 'star'
    delay?: number
  }>(),
  { label: '', value: '', tone: 'default', delay: 0 },
)
</script>

<template>
  <div
    class="stat-card"
    :class="`tone-${tone}`"
    :style="{ animationDelay: delay + 'ms' }"
  >
    <div class="sc-top">
      <span class="sc-label">{{ label }}</span>
      <span v-if="icon" class="sc-icon"><el-icon><component :is="icon" /></el-icon></span>
    </div>
    <div class="sc-value">{{ value }}</div>
    <div v-if="sub" class="sc-sub">{{ sub }}</div>
  </div>
</template>

<style scoped>
.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  animation: sc-enter 0.3s var(--ease-out) both;
}
.sc-top { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); }
.sc-label { font-size: var(--font-sm); color: var(--text-muted); font-weight: var(--weight-medium); }
.sc-icon { width: 18px; height: 18px; color: var(--text-light); display: inline-flex; }
.sc-value {
  font-size: var(--font-3xl);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
  /* 等宽数字：统计数字对齐（菜单价格牌感） */
  font-variant-numeric: tabular-nums;
}
.sc-sub { font-size: var(--font-sm); color: var(--text-secondary); }

/* tone 数值色走 token */
.tone-default .sc-value { color: var(--text-primary); }
.tone-primary .sc-value { color: var(--color-primary); }
.tone-primary .sc-icon { color: var(--color-primary); }
.tone-success .sc-value { color: var(--color-success); }
.tone-success .sc-icon { color: var(--color-success); }
.tone-warning .sc-value { color: var(--color-warning); }
.tone-warning .sc-icon { color: var(--color-warning); }
.tone-danger .sc-value { color: var(--color-error); }
.tone-danger .sc-icon { color: var(--color-error); }
.tone-star .sc-value { color: var(--color-star); }
.tone-star .sc-icon { color: var(--color-star); }

@keyframes sc-enter {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}
@media (prefers-reduced-motion: reduce) {
  .stat-card { animation: none; }
}
</style>
