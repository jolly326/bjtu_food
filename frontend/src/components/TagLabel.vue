<template>
  <text class="tag-label" :class="typeClass">{{ text }}</text>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  text: string
  type?: 'featured' | 'recommend' | 'default'
}>(), {
  type: 'default',
})

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
  background: #FDE8E8;
  color: #C0392B;
}
/* 招牌菜 — 砖红系（品牌色） */
.tag-featured {
  background: #FEF0EF;
  color: #8B3A2B;
}
/* 新品 — 绿色系（新鲜感） */
.tag-new {
  background: #E8F8F0;
  color: #27AE60;
}
/* 热卖 — 橙色系（热卖氛围） */
.tag-hot {
  background: #FEF3E0;
  color: #E67E22;
}
/* 默认 */
.tag-default {
  background: #F0F0F0;
  color: #666666;
}
</style>
