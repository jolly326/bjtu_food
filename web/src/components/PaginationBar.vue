<script setup lang="ts">
/**
 * 公共分页条（口径见 docs/ui/web/公共组件与形态基线.md §2.3「分页」）。
 * 视图只传当前页与是否有下一页，翻页事件回抛给 `usePagedList.go`。
 */
defineProps<{ page: number; hasNext: boolean }>()

const emit = defineEmits<{
  (e: 'prev'): void
  (e: 'next'): void
}>()
</script>

<template>
  <div class="pager">
    <button class="btn" :disabled="page <= 1" @click="emit('prev')">上一页</button>
    <span class="pager__cur">{{ page }}</span>
    <button class="btn" :disabled="!hasNext" @click="emit('next')">下一页</button>
  </div>
</template>
