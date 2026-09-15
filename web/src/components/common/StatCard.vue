<script setup lang="ts">
/**
 * StatCard：唯一统计卡（UI-01 统一契约）。
 * - props: label / value / icon? / sub? / tone / interactive?
 * - slots: icon（自定义图标）、action（右上角动作区）、default（附加内容，置于 sub 之后）
 * - event: click（interactive 时整卡可点，键盘 Tab+Enter 直达、focus-visible 焦点环、按压缩放）
 * - 取值（frontend-ui-consolidation.md UI-01）：圆角 --radius-card；边框 1px --border-light；
 *   padding space-5；数值 font-3xl + weight-semibold + tabular-nums + --tracking-tight。
 * - 注（2026-09-15 PM 拍板）：装饰性入场动画（原 stack 卡片 keyframes 入场）与 delay prop 已全端删除。
 * - 注（2026-09-15 本轮）：`variant="inline"`（图标 + 数值横排，工作台指标卡专用）与仅其消费的 `size`
 *   随工作台一并删除——全站唯一消费方为菜品详情，走默认 stack 变体，故不再保留单变体分支。
 */
import { type Component, computed } from 'vue'

const props = withDefaults(
  defineProps<{
    label?: string
    value?: string | number
    icon?: Component
    sub?: string
    tone?: 'default' | 'primary' | 'success' | 'warning' | 'danger' | 'star'
    /** 可点交互：hover 抬升 + 按压缩放 + focus-visible + 键盘触发 click */
    interactive?: boolean
  }>(),
  { label: '', value: '', tone: 'default', interactive: false },
)

const emit = defineEmits<{ click: [] }>()

const classes = computed(() => [
  `tone-${props.tone}`,
  { interactive: props.interactive },
])

function onClick() {
  if (props.interactive) emit('click')
}
</script>

<template>
  <component
    :is="interactive ? 'button' : 'div'"
    class="stat-card"
    :class="classes"
    :type="interactive ? 'button' : undefined"
    v-press="interactive"
    @click="onClick"
    @keyup.enter="onClick"
  >
    <div class="sc-top">
      <span class="sc-label">{{ label }}</span>
      <span v-if="$slots.action" class="sc-top-action"><slot name="action" /></span>
      <span v-if="icon || $slots.icon" class="sc-icon">
        <slot name="icon"><el-icon><component :is="icon" /></el-icon></slot>
      </span>
    </div>
    <div class="sc-value">{{ value }}</div>
    <div v-if="sub" class="sc-sub">{{ sub }}</div>
    <slot />
  </component>
</template>

<style scoped>
.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-5);
  box-sizing: border-box;
  min-width: 0;
  text-align: left;
  font: inherit;
  color: inherit;
}

.sc-top { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); }
.sc-top-action { margin-right: auto; }
.sc-icon { width: 18px; height: 18px; color: var(--text-light); display: inline-flex; }

.sc-label { font-size: var(--font-sm); color: var(--text-muted); font-weight: var(--weight-medium); }
.sc-value {
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  font-size: var(--font-3xl);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
  /* 等宽数字：统计数字对齐（菜单价格牌感） */
  font-variant-numeric: tabular-nums;
}
.sc-sub { font-size: var(--font-sm); color: var(--text-secondary); }

/* ===== interactive：hover 抬升 / 按压 / 焦点环（§4.4 / §4.9） ===== */
.stat-card.interactive {
  cursor: pointer;
  transition: transform 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out), border-color 0.2s var(--ease-out);
}
@media (hover: hover) {
  .stat-card.interactive:hover { box-shadow: var(--card-hover-shadow); border-color: var(--border-strong); }
}
.stat-card.interactive:active { transform: scale(var(--press-scale)); }
/* 焦点环走 shared.css 全局 button:focus-visible（--focus-ring），此处不再重复描边避免双环 */

/* interactive 时为 <button>：清除默认按钮观感 */
button.stat-card { border: 1px solid var(--border-light); width: 100%; }

/* ===== tone 数值色走 token ===== */
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

@media (prefers-reduced-motion: reduce) {
  .stat-card.interactive { transition: none; transform: none; }
}
</style>
