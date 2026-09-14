<script setup lang="ts">
/**
 * StatCard：唯一统计卡（UI-01 统一契约）。
 * - props: label / value / icon? / sub? / tone / variant('stack'|'inline') / size('md'|'lg') / interactive?
 * - slots: icon（自定义图标）、action（行尾动作区）、default（附加内容，stack 态置于 sub 之后）
 * - event: click（interactive 时整卡可点，键盘 Tab+Enter 直达、focus-visible 焦点环、按压缩放）
 * - 取值（frontend-ui-consolidation.md UI-01）：圆角 --radius-card；边框 1px --border-light；
 *   padding lg=space-5/space-6、md=space-4/space-5；图标容器 48/40px + --radius-md + --color-primary-bg；
 *   数值 font-4xl/font-2xl + weight-bold + tabular-nums + --tracking-tight。
 * - 注（2026-09-15 PM 拍板）：装饰性入场动画（原 stack 卡片 keyframes 入场）与 delay prop 已全端删除。
 */
import { type Component, computed } from 'vue'

const props = withDefaults(
  defineProps<{
    label?: string
    value?: string | number
    icon?: Component
    sub?: string
    tone?: 'default' | 'primary' | 'success' | 'warning' | 'danger' | 'star'
    /** stack = 标签在上、数值在下；inline = 图标 + 数值横排（工作台指标卡） */
    variant?: 'stack' | 'inline'
    /** md = 常规卡片；lg = 大号（待办卡），控制 padding / 图标容器 / 数值字号 */
    size?: 'md' | 'lg'
    /** 可点交互：hover 抬升 + 按压缩放 + focus-visible + 键盘触发 click */
    interactive?: boolean
  }>(),
  { label: '', value: '', tone: 'default', variant: 'stack', size: 'md', interactive: false },
)

const emit = defineEmits<{ click: [] }>()

const classes = computed(() => [
  `tone-${props.tone}`,
  `variant-${props.variant}`,
  `size-${props.size}`,
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
    <!-- inline：图标 + 数值横排 + 行尾动作 -->
    <template v-if="variant === 'inline'">
      <span class="sc-icon-box" :class="size === 'lg' ? 'icon-lg' : 'icon-md'">
        <slot name="icon"><el-icon v-if="icon"><component :is="icon" /></el-icon></slot>
      </span>
      <span class="sc-body">
        <span class="sc-value">{{ value }}</span>
        <span class="sc-label">{{ label }}</span>
      </span>
      <span v-if="$slots.action" class="sc-action"><slot name="action" /></span>
    </template>

    <!-- stack：标签 + 图标在上、数值居中、sub/附加内容在下 -->
    <template v-else>
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
    </template>
  </component>
</template>

<style scoped>
.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  display: flex;
  box-sizing: border-box;
  min-width: 0;
  text-align: left;
  font: inherit;
  color: inherit;
}

/* ===== variant: stack（默认，DishDetailView 等） ===== */
.variant-stack {
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-5);
}
.sc-top { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); }
.sc-top-action { margin-right: auto; }
.sc-icon { width: 18px; height: 18px; color: var(--text-light); display: inline-flex; }

/* ===== variant: inline（工作台待办/指标卡） ===== */
.variant-inline {
  align-items: center;
  gap: var(--space-4);
  user-select: none;
}
.variant-inline.size-lg { padding: var(--space-5) var(--space-6); }
.variant-inline.size-md { padding: var(--space-4) var(--space-5); }
.sc-icon-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  color: var(--color-primary);
  background: var(--color-primary-bg);
}
.sc-icon-box.icon-lg { width: 48px; height: 48px; }
.sc-icon-box.icon-md { width: 40px; height: 40px; }
.sc-icon-box .el-icon { font-size: var(--stat-icon-lg); }
.sc-icon-box.icon-md .el-icon { font-size: var(--stat-icon-md); }
.sc-body { flex: 1; min-width: 0; }
.variant-inline .sc-value { display: block; }
.variant-inline .sc-label { display: block; }
.sc-action {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--font-sm);
  font-weight: var(--weight-medium);
  color: var(--color-primary);
  flex-shrink: 0;
}
.variant-inline .tone-success .sc-action,
.variant-inline.tone-success .sc-action { color: var(--color-success); }

/* ===== 字号 / 字重（tabular-nums 数值对齐） ===== */
.sc-label { font-size: var(--font-sm); color: var(--text-muted); font-weight: var(--weight-medium); }
.variant-inline .sc-label { margin-top: var(--space-1); font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.variant-inline.size-lg .sc-label { font-size: var(--font-md); color: var(--text-secondary); }
.sc-value {
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  font-size: var(--font-3xl);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
  /* 等宽数字：统计数字对齐（菜单价格牌感） */
  font-variant-numeric: tabular-nums;
}
.variant-inline .sc-value {
  font-weight: var(--weight-bold);
  line-height: 1.1;
}
.variant-inline.size-lg .sc-value { font-size: var(--font-4xl); }
.variant-inline.size-md .sc-value { font-size: var(--font-2xl); }
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

/* ===== tone 数值色走 token（图标容器随语义色联动，工作台 done 态图标转绿） ===== */
.tone-default .sc-value { color: var(--text-primary); }
.tone-primary .sc-value { color: var(--color-primary); }
.tone-primary .sc-icon, .tone-primary .sc-icon-box { color: var(--color-primary); }
.tone-success .sc-value { color: var(--color-success); }
.tone-success .sc-icon, .tone-success .sc-icon-box { color: var(--color-success); }
.tone-success .sc-icon-box { background: var(--color-success-bg); }
.tone-warning .sc-value { color: var(--color-warning); }
.tone-warning .sc-icon, .tone-warning .sc-icon-box { color: var(--color-warning); }
.tone-danger .sc-value { color: var(--color-error); }
.tone-danger .sc-icon, .tone-danger .sc-icon-box { color: var(--color-error); }
.tone-star .sc-value { color: var(--color-star); }
.tone-star .sc-icon, .tone-star .sc-icon-box { color: var(--color-star); }

@media (prefers-reduced-motion: reduce) {
  .stat-card.interactive { transition: none; transform: none; }
}
</style>
