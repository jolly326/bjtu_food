<script setup lang="ts">
/**
 * DataTable：通用数据表格（§4.2 自封装组件）。
 * - 三态齐全：loading / error / empty / 正常
 * - 列定义驱动（prop / label / width / slot），单元格用 #cell-{prop} 槽自定义
 * - 可选 selectable 多选（#selection 槽或内置复选）
 * - 表头排序：列加 sortable:true 点击循环 升序→降序→取消
 * - 自动分页：行数超过 pageSize 时显示分页栏（共 N 条 / 每页条数 / 页码）
 * - 圆角/阴影走 Token（§4.2）
 */
export interface DataTableColumn {
  prop: string
  label: string
  width?: string | number
  align?: 'left' | 'center' | 'right'
  /** 长文本列：默认单行截断（需 title 悬停），保持行高统一 */
  ellipsis?: boolean
  /** 可排序列：点击表头循环 升序→降序→取消 */
  sortable?: boolean
  /** 自定义排序取值（默认取 row[prop]；派生列如「档口/菜品」用此计算） */
  sortValue?: (row: any) => number | string | null | undefined
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
    /** 整行可点击（row-click 生效）：显示 pointer 光标 + 行尾箭头引导 */
    rowClickable?: boolean
    /** 空态主图标（组件名，如 Document） */
    emptyIcon?: any
    /** 操作列宽度（px 或字符串），默认 160px */
    actionsWidth?: string
    /** 是否启用分页（默认开启；行数小于每页条数时自动隐藏分页栏） */
    pagination?: boolean
    /** 每页条数选项 */
    pageSizes?: number[]
    defaultPageSize?: number
    /**
     * 服务端受控分页模式：开启后组件不自行 slice，rows 即当前页数据，
     * 分页栏的 total / 当前页 / 每页条数由外部（serverTotal / v-model:serverPage / v-model:serverPageSize）控制，
     * 翻页触发 page-change 事件交由父组件重新请求对应页。用于后端单页上限 100 的真分页场景。
     */
    serverMode?: boolean
    /** serverMode 下后端返回的总条数 */
    serverTotal?: number
    /** serverMode 下当前页码（v-model，1 起） */
    serverPage?: number
    /** serverMode 下每页条数（v-model，≤ 后端上限 100） */
    serverPageSize?: number
  }>(),
  {
    loading: false, error: '', emptyText: '暂无数据', selectable: false, rowKey: 'id',
    selectedIds: () => [], rowClickable: false, emptyIcon: null, actionsWidth: '160px',
    pagination: true, pageSizes: () => [10, 20, 50, 100], defaultPageSize: 10,
    serverMode: false, serverTotal: 0, serverPage: 1, serverPageSize: 20,
  },
)

import { ref, computed, watch } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'

const emit = defineEmits<{
  'row-click': [row: any]
  'update:selectedIds': [ids: number[]]
  /** serverMode 下翻页/改每页条数时触发，父组件据此重新请求对应页 */
  'page-change': [page: number, pageSize: number]
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

// ===== 表头排序 =====
const sortState = ref<{ prop: string; order: 'asc' | 'desc' } | null>(null)
function compareValues(va: any, vb: any): number {
  if (va == null && vb == null) return 0
  if (va == null) return 1
  if (vb == null) return -1
  // 数值优先
  if (typeof va === 'number' || typeof vb === 'number') return Number(va) - Number(vb)
  const na = Number(va)
  const nb = Number(vb)
  if (va !== '' && vb !== '' && !isNaN(na) && !isNaN(nb)) return na - nb
  // 日期其次
  const da = Date.parse(va)
  const db = Date.parse(vb)
  if (!isNaN(da) && !isNaN(db)) return da - db
  // 字符串兜底
  return String(va).localeCompare(String(vb), 'zh-Hans-CN')
}
function toggleSort(prop: string) {
  if (!sortState.value || sortState.value.prop !== prop) {
    sortState.value = { prop, order: 'asc' }
  } else if (sortState.value.order === 'asc') {
    sortState.value = { prop, order: 'desc' }
  } else {
    sortState.value = null
  }
}

// ===== 排序 + 分页 =====
const page = ref(1)
const pageSize = ref(props.defaultPageSize)
const sortedRows = computed(() => {
  const s = sortState.value
  if (!s) return props.rows
  const col = props.columns.find(c => c.prop === s.prop)
  const getVal = col?.sortValue ?? ((row: any) => row?.[s.prop])
  return [...props.rows].sort((a, b) => {
    const r = compareValues(getVal(a), getVal(b))
    return s.order === 'asc' ? r : -r
  })
})
// serverMode：rows 即当前页数据，组件不 slice；total / 页码由外部控制
const displayRows = computed(() => {
  if (props.serverMode) {
    return { rows: sortedRows.value, total: props.serverTotal }
  }
  if (!props.pagination) return { rows: sortedRows.value, total: sortedRows.value.length }
  const total = sortedRows.value.length
  const maxPage = Math.max(1, Math.ceil(total / pageSize.value))
  if (page.value > maxPage) page.value = maxPage
  const start = (page.value - 1) * pageSize.value
  return { rows: sortedRows.value.slice(start, start + pageSize.value), total }
})
// 数据变化（筛选/加载）后页码越界自动修正
watch(
  () => [props.rows.length, props.defaultPageSize],
  () => {
    const total = sortedRows.value.length
    const maxPage = Math.max(1, Math.ceil(total / pageSize.value))
    if (page.value > maxPage) page.value = maxPage
  },
)
// 分页开关关闭时回到第 1 页
watch(
  () => props.pagination,
  (v) => { if (!v) page.value = 1 },
)
// ===== serverMode 分页栏绑定（与父组件双向同步） =====
const sPage = computed({
  get: () => props.serverPage,
  set: (v: number) => emit('update:serverPage', v),
})
const sPageSize = computed({
  get: () => props.serverPageSize,
  set: (v: number) => emit('update:serverPageSize', v),
})
function onServerPageChange(p: number, ps: number) {
  emit('update:serverPage', p)
  emit('update:serverPageSize', ps)
  emit('page-change', p, ps)
}
</script>

<template>
  <div class="table-wrap">
    <!-- 三态 -->
    <div v-if="loading" class="state-box"><span class="spin" />加载中…</div>
    <div v-else-if="error" class="state-box state-err">{{ error }}，请刷新页面重试</div>
    <!-- 空态：大图标 + 文案 + 可选 CTA（视觉引导，不靠一行小字） -->
    <div v-else-if="!rows.length" class="empty-state">
      <div v-if="emptyIcon" class="empty-icon"><el-icon><component :is="emptyIcon" /></el-icon></div>
      <div class="empty-text">{{ emptyText }}</div>
      <div v-if="$slots.emptyAction" class="empty-action">
        <slot name="emptyAction" />
      </div>
    </div>

    <template v-else>
      <table class="table">
        <thead>
          <tr>
            <th v-if="selectable" style="width: 48px" class="col-select">
              <input
                type="checkbox"
                :checked="selectedIds.length === displayRows.rows.length && displayRows.rows.length > 0"
                @change="
                  emit('update:selectedIds', ($event.target as HTMLInputElement).checked ? displayRows.rows.map(rowKeyValue) : [])
                "
              />
            </th>
            <th
              v-for="col in columns"
              :key="col.prop"
              :style="{ width: col.width, textAlign: col.align || 'left' }"
              :class="{ 'th-sortable': col.sortable }"
            >
              <button v-if="col.sortable" type="button" class="th-sort" @click="toggleSort(col.prop)">
                {{ col.label }}
                <span class="sort-arrow" :class="{ on: sortState?.prop === col.prop }">
                  <template v-if="sortState?.prop === col.prop">{{ sortState.order === 'asc' ? '▲' : '▼' }}</template>
                  <template v-else>↕</template>
                </span>
              </button>
              <template v-else>{{ col.label }}</template>
            </th>
            <th v-if="$slots.actions" :style="{ width: actionsWidth }" class="col-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in displayRows.rows"
            :key="rowKeyValue(row)"
            :class="{ 'row-sel': selectable && isSelected(row) }"
            :role="rowClickable ? 'button' : undefined"
            :tabindex="rowClickable ? 0 : undefined"
            :style="rowClickable ? { cursor: 'pointer' } : undefined"
            @click="onRowClick(row)"
            @keydown.enter.prevent="onRowClick(row)"
            @keydown.space.prevent="onRowClick(row)"
          >
            <td v-if="selectable" class="col-select" @click.stop>
              <input type="checkbox" :checked="isSelected(row)" @change="toggleSelect(row)" />
            </td>
            <td
              v-for="col in columns"
              :key="col.prop"
              :class="{ 'cell-ellipsis': col.ellipsis && !$slots[`cell-${col.prop}`] }"
              :style="{ textAlign: col.align || 'left' }"
              :title="col.ellipsis && !$slots[`cell-${col.prop}`] ? String(row[col.prop] ?? '') : undefined"
            >
              <slot :name="`cell-${col.prop}`" :row="row">{{ row[col.prop] }}</slot>
            </td>
            <!-- 可点击行：右侧箭头引导 -->
            <td v-if="rowClickable" class="row-arrow-cell" @click.stop>
              <el-icon class="row-arrow"><ArrowRight /></el-icon>
            </td>
            <td v-if="$slots.actions" class="actions" @click.stop>
              <slot name="actions" :row="row" />
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页栏：共 N 条 + 每页条数 + 页码 -->
      <!-- serverMode：受控分页，绑定外部 sPage/sPageSize，翻页触发 page-change -->
      <div v-if="pagination && serverMode && serverTotal > 0 && serverTotal > serverPageSize" class="table-footer">
        <div class="tf-count">共 <b>{{ serverTotal }}</b> 条</div>
        <div class="tf-pager">
          <el-pagination
            layout="sizes, prev, pager, next"
            :total="serverTotal"
            :page-sizes="pageSizes"
            v-model:current-page="sPage"
            v-model:page-size="sPageSize"
            background
            @change="onServerPageChange"
          />
        </div>
      </div>
      <div v-else-if="pagination && serverMode && serverTotal > 0" class="table-footer table-footer-min">
        <div class="tf-count">共 <b>{{ serverTotal }}</b> 条</div>
      </div>
      <!-- 非 serverMode：本地分页（数据已全部加载） -->
      <div v-else-if="pagination && displayRows.total > pageSize" class="table-footer">
        <div class="tf-count">共 <b>{{ displayRows.total }}</b> 条</div>
        <div class="tf-pager">
          <el-pagination
            layout="sizes, prev, pager, next"
            :total="displayRows.total"
            :page-sizes="pageSizes"
            v-model:current-page="page"
            v-model:page-size="pageSize"
            background
          />
        </div>
      </div>
      <div v-else-if="pagination && !serverMode" class="table-footer table-footer-min">
        <div class="tf-count">共 <b>{{ displayRows.total }}</b> 条</div>
      </div>
    </template>
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
.table thead {
  position: sticky;
  top: 0;
  z-index: 2;
}
.table th {
  text-align: left;
  padding: var(--table-th-padding);
  background: var(--table-header-bg);
  color: var(--text-secondary);
  font-weight: var(--weight-medium);
  border-bottom: 1px solid var(--border-light);
  white-space: nowrap;
}
.table td {
  padding: var(--table-td-padding);
  border-bottom: 1px solid var(--table-border);
  color: var(--text-primary);
  /* 等宽数字（菜单价格牌感：数字列对齐） */
  font-variant-numeric: tabular-nums;
}
/* 可排序表头 */
.th-sortable {
  padding: 0 !important;
}
.th-sort {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  width: 100%;
  height: 100%;
  padding: var(--table-th-padding);
  border: none;
  background: none;
  font: inherit;
  color: inherit;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.15s var(--ease-out);
}
.th-sort:hover { color: var(--color-primary); background: var(--table-row-hover); }
.th-sort:focus-visible { outline: none; box-shadow: var(--focus-ring); }
.sort-arrow {
  font-size: 10px;
  color: var(--text-light);
  opacity: .55;
  transition: color 0.15s var(--ease-out), opacity 0.15s var(--ease-out);
}
.sort-arrow.on { color: var(--color-primary); opacity: 1; }
/* 长文本列默认单行截断（列加 ellipsis:true 启用），保持行高统一 */
.cell-ellipsis {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.table tbody tr {
  transition: background 0.15s var(--ease-out);
  /* 不再全局 pointer：仅可整行点击的页面自行处理，避免误导 */
}
.table tbody tr:hover {
  background: var(--table-row-hover);
}
.table tbody tr.row-sel {
  background: var(--table-row-active);
}
.table tbody tr:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}
/* 末行分隔线柔化：数据密度更高，视觉更干净 */
.table tbody tr:last-child td { border-bottom-color: transparent; }
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
  /* 三态高度稳定：加载/错误态不塌陷 */
  min-height: 240px;
  box-sizing: border-box;
}
.state-err {
  color: var(--color-error);
}
/* ===== 空态（视觉引导：图标 + 文案 + CTA） ===== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: var(--space-10) var(--space-4);
  text-align: center;
  /* 空态高度稳定：数据为空时不塌陷 */
  min-height: 240px;
  box-sizing: border-box;
}
.empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: var(--radius-md);
  background: var(--bg-soft);
  color: var(--text-light);
  margin-bottom: var(--space-1);
}
.empty-icon .el-icon { width: 28px; height: 28px; }
.empty-text { font-size: var(--font-base); color: var(--text-secondary); }
.empty-action { margin-top: var(--space-2); }
/* ===== 可点击行箭头引导 ===== */
.row-arrow-cell { width: 40px; text-align: center; }
.row-arrow { width: 16px; height: 16px; color: var(--text-light); opacity: 0; transition: opacity 0.15s var(--ease-out), color 0.15s var(--ease-out); }
.table tbody tr:hover .row-arrow { opacity: 1; color: var(--color-primary); }
/* ===== 分页栏 ===== */
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--border-light);
  flex-wrap: wrap;
}
.table-footer-min { justify-content: flex-end; }
.tf-count { font-size: var(--font-sm); color: var(--text-secondary); font-variant-numeric: tabular-nums; }
.tf-count b { color: var(--color-primary); font-weight: var(--weight-semibold); margin: 0 2px; }
.tf-pager :deep(.el-pagination) { justify-content: flex-end; }
.tf-pager :deep(.el-pagination.is-background .el-pager li) { border-radius: var(--radius-sm); }
.tf-pager :deep(.el-pagination__sizes .el-select__wrapper) { min-height: 28px; }
.tf-pager :deep(.el-pagination__total) { display: none; }
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
