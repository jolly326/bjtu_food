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
  font-size: 22rpx;
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
  line-height: 1.4;
}
/* 必吃推荐 */
.tag-recommend {
  background: var(--color-error);
  color: var(--text-white);
}
/* 招牌菜 (浅色版) */
.tag-featured {
  background: var(--color-primary-bg);
  color: var(--color-primary);
}
/* 新品 */
.tag-new {
  background: var(--color-accent);
  color: var(--text-white);
}
/* 热卖 */
.tag-hot {
  background: var(--color-warning);
  color: var(--text-white);
}
/* 默认 */
.tag-default {
  background: var(--bg-page);
  color: var(--color-primary);
}
</style>
