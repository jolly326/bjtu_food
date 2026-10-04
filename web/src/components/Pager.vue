<script setup lang="ts">
/**
 * 分页器（[列表页模板 §一](../../../docs/ui/web/列表页模板.md)）：共 N 条 + 上一页 / 下一页 + 第 X / Y 页。
 *
 * <p>`total = 0` 时不渲染；`prev` / `next` 由视图的 `prevPage` / `nextPage` 承接（来自 `usePagedList`）。
 * 样式取自全局 `styles/shared.css` 的 `.pager*` 系列，跨组件复用。
 */
withDefaults(
  defineProps<{
    total?: number
    page?: number
    pageCount?: number
  }>(),
  { total: 0, page: 1, pageCount: 1 },
)
defineEmits<{ prev: []; next: [] }>()
</script>

<template>
  <div v-if="total > 0" class="pager">
    <span class="pager-total">共 {{ total }} 条</span>
    <div class="pager-actions">
      <button class="btn-secondary" type="button" :disabled="page <= 1" @click="$emit('prev')">上一页</button>
      <span class="pager-page">第 {{ page }} / {{ pageCount }} 页</span>
      <button class="btn-secondary" type="button" :disabled="page >= pageCount" @click="$emit('next')">下一页</button>
    </div>
  </div>
</template>
