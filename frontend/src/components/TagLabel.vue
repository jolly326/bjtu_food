<template>
  <text class="tag-label" :class="typeClass">{{ text }}</text>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  text: string
}>()

const typeClass = computed(() => {
  if (props.text === '必吃推荐') return 'tag-recommend'
  if (props.text === '招牌菜') return 'tag-featured'
  if (props.text === '新品') return 'tag-new'
  if (props.text === '热卖') return 'tag-hot'
  return 'tag-default'
})
</script>

<style scoped>
.tag-label {
  display: inline-block;
  font-size: var(--font-tiny);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-icon);
  line-height: 1.4;
}
/* 必吃推荐 — 红色系（推荐强调） */
.tag-recommend {
  background: var(--color-primary-soft2);
  color: var(--color-price);
}
/* 招牌菜 — 砖红系（品牌色） */
.tag-featured {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}
/* 新品 — 绿色系（新鲜感） */
.tag-new {
  background: var(--color-accent-soft);
  color: var(--color-accent);
}
/* 热卖 — 橙色系（热卖氛围） */
.tag-hot {
  background: var(--color-hot-soft);
  color: var(--color-hot);
}
/* 默认 */
.tag-default {
  background: var(--bg-placeholder);
  color: var(--text-secondary);
}
</style>
