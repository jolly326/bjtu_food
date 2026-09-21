<script setup lang="ts">
/**
 * StatusTag：状态标签（§4.2 自封装组件）。
 * 统一 green / gray / red / warning 四态，复用既有色彩 Token。
 */
withDefaults(
  defineProps<{
    type?: 'success' | 'info' | 'warning' | 'danger' | 'gray'
    text?: string
  }>(),
  { type: 'info', text: '' },
)
</script>

<template>
  <span class="tag" :class="`tag-${type}`">{{ text }}</span>
</template>

<style scoped>
.tag {
  display: inline-flex;
  align-items: center;
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-pill);
  font-size: var(--font-xs);
  font-weight: var(--weight-medium);
  line-height: var(--leading-base);
  white-space: nowrap;
}
.tag-success { background: var(--color-success-bg); color: var(--color-success); }
.tag-gray { background: var(--bg-gray); color: var(--text-muted); }
.tag-warning { background: var(--color-warning-bg); color: var(--color-warning); }
.tag-danger { background: var(--color-error-bg); color: var(--color-error); }
/* info 态：浅底 + 主色文字 → 文字走「文字档」（填充底/边框才用 --color-primary），
   否则深色主题下文字色 2.90:1、浅色主题 4.48:1 均不达 4.5:1（C1） */
.tag-info { background: var(--color-primary-bg); color: var(--color-primary-text); }
</style>
