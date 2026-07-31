<script setup lang="ts">
/**
 * DataTable：通用数据表格（§4.2 自封装组件）。
 * - 三态齐全：loading / error / empty / 正常
 * - 列定义驱动（prop / label / width / slot），单元格用 #cell-{prop} 槽自定义
 * - 可选 selectable 多选（#selection 槽或内置复选）
 * - 圆角/阴影走 Token（§4.2）
 */
export interface DataTableColumn {
  prop: string
  label: string
  width?: string | number
  align?: 'left' | 'center' | 'right'
}

const props = withDefaults(
  defineProps<{
    columns: DataTableColumn[]
    rows: any[]
    loading?: boolean
    error?: string
    emptyText?: string
    selectable?: boolean
    rowKey?: string
    selectedIds?: number[]
  }>(),
  { loading: false, error: '', emptyText: '暂无数据', selectable: false, rowKey: 'id', selectedIds: () => [] },
)

const emit = defineEmits<{
  'row-click': [row: any]
  'update:selectedIds': [ids: number[]]
}>()

function rowKeyValue(row: any) {
  return row?.[props.rowKey]
}
function isSelected(row: any) {
  return props.selectedIds.includes(rowKeyValue(row))
}
function toggleSelect(row: any) {
  const key = rowKeyValue(row)
  const set = new Set(props.selectedIds)
  if (set.has(key)) set.delete(key)
  else set.add(key)
  emit('update:selectedIds', [...set])
}
function onRowClick(row: any) {
  emit('row-click', row)
}
</script>

<template>
  <div class="table-wrap">
    <!-- 三态 -->
    <div v-if="loading" class="state-box"><span class="spin" />加载中…</div>
    <div v-else-if="error" class="state-box state-err">{{ error }}</div>
    <div v-else-if="!rows.length" class="state-box">{{ emptyText }}</div>

    <table v-else class="table">
      <thead>
        <tr>
          <th v-if="selectable" style="width: 48px" class="col-select">
            <input
              type="checkbox"
              :checked="selectedIds.length === rows.length && rows.length > 0"
              @change="
                emit('update:selectedIds', ($event.target as HTMLInputElement).checked ? rows.map(rowKeyValue) : [])
              "
            />
          </th>
          <th
            v-for="col in columns"
            :key="col.prop"
            :style="{ width: col.width, textAlign: col.align || 'left' }"
          >
            {{ col.label }}
          </th>
          <th v-if="$slots.actions" style="width: 160px" class="col-actions">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in rows"
          :key="rowKeyValue(row)"
          :class="{ 'row-sel': selectable && isSelected(row) }"
          @click="onRowClick(row)"
        >
          <td v-if="selectable" class="col-select" @click.stop>
            <input type="checkbox" :checked="isSelected(row)" @change="toggleSelect(row)" />
          </td>
          <td
            v-for="col in columns"
            :key="col.prop"
            :style="{ textAlign: col.align || 'left' }"
          >
            <slot :name="`cell-${col.prop}`" :row="row">{{ row[col.prop] }}</slot>
          </td>
          <td v-if="$slots.actions" class="actions" @click.stop>
            <slot name="actions" :row="row" />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.table-wrap {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--border-light);
  overflow-x: auto;
}
.table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-base);
}
.table th {
  text-align: left;
  padding: var(--space-3) var(--space-4);
  background: var(--bg-soft);
  color: var(--text-secondary);
  font-weight: var(--weight-medium);
  border-bottom: 1px solid var(--border-light);
  white-space: nowrap;
}
.table td {
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--border-soft);
  color: var(--text-primary);
}
.table tbody tr {
  transition: background 0.15s var(--ease-out);
  cursor: pointer;
}
.table tbody tr:hover {
  background: var(--bg-hover);
}
.table tbody tr.row-sel {
  background: var(--color-primary-bg);
}
.col-select {
  text-align: center;
}
.col-actions {
  white-space: nowrap;
}
.actions {
  white-space: nowrap;
  display: flex;
  gap: var(--space-2);
}
.state-box {
  text-align: center;
  color: var(--text-light);
  padding: var(--space-10) var(--space-4);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
}
.state-err {
  color: var(--color-error);
}
.spin {
  width: 16px;
  height: 16px;
  border: 2px solid var(--border-color);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .spin {
    animation-duration: 1.4s;
  }
}
</style>
