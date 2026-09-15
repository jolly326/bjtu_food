<template>
  <text class="tag-label" :class="typeClass">{{ text }}</text>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 标签 chip（全站唯一）。取色两口径：
 * - variant="auto"（默认）：按文案语义自动取色（必吃推荐 / 招牌菜 / 热卖 / 默认）；
 * - variant="plain"：统一「主色软底 + 主色字 + 中等字重」chip，尺寸不变；
 *   供搜索结果标签行等「多标签需视觉齐平、不按语义分色」的场景使用。
 */
const props = withDefaults(
  defineProps<{
    text: string
    variant?: 'auto' | 'plain'
  }>(),
  { variant: 'auto' },
)

const typeClass = computed(() => {
  if (props.variant === 'plain') return 'tag-plain'
  if (props.text === '必吃推荐') return 'tag-recommend'
  if (props.text === '招牌菜') return 'tag-featured'
  if (props.text === '热卖') return 'tag-hot'
  return 'tag-default'
})
</script>

<style scoped>
/* 小标签：小元素档圆角（16rpx）+ 收紧内边距（tab-pages-visual-unify，缩小尺寸不抢主信息） */
.tag-label {
  display: inline-block;
  font-size: var(--font-tiny);
  padding: var(--spacing-2xs) var(--spacing-xs);
  border-radius: var(--radius-tag);
  line-height: 1.4;
}
/* 必吃推荐 — 红色系（推荐强调） */
.tag-recommend {
  background: var(--color-primary-soft);
  color: var(--color-price);
}
/* 招牌菜 — 砖红系（品牌色） */
.tag-featured {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}
/* 热卖 — 橙色系（热卖氛围，统一走 accent） */
.tag-hot {
  background: var(--color-accent-soft);
  color: var(--color-accent);
}
/* 默认 */
.tag-default {
  background: var(--bg-placeholder);
  color: var(--text-secondary);
}
/* plain：统一主色软底 chip（尺寸/圆角/内边距与 auto 一致，仅取色与字重固定） */
.tag-plain {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: var(--weight-medium);
}
</style>
