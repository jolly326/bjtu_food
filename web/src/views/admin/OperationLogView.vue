<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import DataTable from '@/components/DataTable.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import { Document } from '@element-plus/icons-vue'
import type { OperationLogVO } from '@/api/operationLog'

const searchQuery = ref('')

/**
 * 操作日志（task-10 W4）：只读查询，无任何写操作/删除按钮。
 * 过滤：adminId / action / targetType / startAt / endAt（前端 SearchInput 模糊匹配本地结果）。
 */

const loading = ref(false)
const error = ref('')
const rows = ref<OperationLogVO[]>([])

// ===== 受控分页（后端已分页，total 来自后端；pageSize ≤ 100 不触碰后端上限） =====
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

// 请求竞态守卫：连续输入/切筛选会并发请求，仅接受最新一次的结果，丢弃过期响应（防数据错乱）
let reqToken = 0

async function reloadFromFirstPage() {
  page.value = 1
  await loadList()
}
function onPageChange() {
  loadList()
}

// 动作筛选预设（对齐 ARCH OperationLogConst.ACTION_*）
const actionOptions = [
  { value: '', label: '全部动作' },
  { value: 'audit_approve', label: '审核通过' },
  { value: 'audit_reject', label: '审核退回' },
  { value: 'apply_approve', label: 'UGC通过' },
  { value: 'apply_reject', label: 'UGC退回' },
  { value: 'moment_hide', label: '动态下架' },
  { value: 'moment_delete', label: '动态删除' },
  { value: 'review_hide', label: '评价隐藏' },
  { value: 'review_delete', label: '评价删除' },
  { value: 'dish_delete', label: '菜品删除' },
  { value: 'feedback_handle', label: '反馈处理' },
  { value: 'account_delete', label: '账号删除' },
]
const targetTypeOptions = [
  { value: '', label: '全部对象' },
  { value: 'moment', label: '动态' },
  { value: 'dish', label: '菜品' },
  { value: 'stall', label: '档口' },
  { value: 'canteen', label: '食堂' },
  { value: 'feedback', label: '反馈' },
  { value: 'review', label: '评价' },
  { value: 'apply', label: 'UGC申请' },
  { value: 'user', label: '用户' },
]
const activeAction = ref('')
const activeTarget = ref('')

function actionText(a: string): string {
  const found = actionOptions.find(o => o.value === a)
  return found ? found.label : a
}
function targetText(t: string): string {
  const found = targetTypeOptions.find(o => o.value === t)
  return found ? found.label : t
}

async function loadList() {
  loading.value = true
  error.value = ''
  const token = ++reqToken
  try {
    const { operationLogApi } = await import('@/api')
    const res = await operationLogApi.listOperationLogs({
      action: activeAction.value || undefined,
      keyword: searchQuery.value.trim() || undefined,
      targetType: activeTarget.value || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    if (token !== reqToken) return // 已有更新的请求发出，丢弃过期响应
    rows.value = res.list
    total.value = res.total
  } catch (e: any) {
    if (token !== reqToken) return
    error.value = e.message || '加载操作日志失败'
    rows.value = []
    total.value = 0
  } finally {
    if (token === reqToken) loading.value = false
  }
}

onMounted(loadList)

async function onActionChange() { await reloadFromFirstPage() }
async function onTargetChange() { await reloadFromFirstPage() }

// 关键词检索已改为服务端 keyword 过滤（后端按 action/targetType 模糊），
// 翻页/改筛选会重新请求后端对应页，不再本地截断当前页子集。
const filtered = computed(() => rows.value)

// 关键词变化（输入或清空）→ 回到第 1 页重新拉取对应页（受控分页）
// 加 300ms 防抖，避免连续输入每个 keystroke 都发请求（去重），并 await 确保完成。
let searchDebounce: ReturnType<typeof setTimeout> | undefined
watch(searchQuery, () => {
  clearTimeout(searchDebounce)
  searchDebounce = setTimeout(() => { reloadFromFirstPage() }, 300)
})
// 卸载时清理防抖定时器，避免组件销毁后回调仍触发（M4：定时器泄漏修复）
onBeforeUnmount(() => clearTimeout(searchDebounce))

function fmtTime(v: string): string {
  if (!v) return '—'
  const d = new Date(v)
  return isNaN(d.getTime()) ? v : d.toLocaleString('zh-CN')
}
</script>

<template>
    <FilterBar v-model="searchQuery">
      <template #default>
        <FilterSelect v-model="activeAction" label="动作" :options="actionOptions" :width="160" @change="onActionChange" />
        <FilterSelect v-model="activeTarget" label="对象" :options="targetTypeOptions" :width="160" @change="onTargetChange" />
      </template>
    </FilterBar>

    <DataTable
      server-mode
      :server-total="total"
      v-model:server-page="page"
      v-model:server-page-size="pageSize"
      @page-change="onPageChange"
      :columns="[
        { prop: 'admin', label: '操作人', width: '140px' },
        { prop: 'action', label: '动作', width: '120px' },
        { prop: 'target', label: '操作对象', ellipsis: true, width: '140px' },
        { prop: 'ip', label: '来源 IP', width: '150px' },
        { prop: 'time', label: '操作时间', width: '180px', sortable: true, sortValue: (row) => row.createdAt },
      ]"
      :rows="filtered"
      :loading="loading"
      :error="error" empty-text="暂无操作日志"
    >
      <template #cell-admin="{ row }">{{ row.adminNickname || ('管理员#' + row.adminId) }}</template>
      <template #cell-action="{ row }"><StatusTag type="info" :text="actionText(row.action)" /></template>
      <template #cell-target="{ row }">
        <span v-if="row.targetType">{{ targetText(row.targetType) }}#{{ row.targetId }}</span>
        <span v-else class="muted">—</span>
      </template>
      <template #cell-ip="{ row }"><span class="ip">{{ row.ip || '—' }}</span></template>
      <template #cell-time="{ row }">{{ fmtTime(row.createdAt) }}</template>
    </DataTable>

    <div class="read-only-tip">
      <el-icon><Document /></el-icon>
      <span>仅记录管理员写操作，系统自动生成</span>
      <span class="read-only-badge">只读</span>
    </div>
</template>

<style scoped>
.muted { color: var(--text-light); }
.ip { font-family: var(--font-mono, monospace); font-size: var(--font-sm); color: var(--text-secondary); }
.read-only-tip {
  margin-top: var(--space-4); display: flex; align-items: center; gap: var(--space-2);
  font-size: var(--font-sm); color: var(--text-secondary);
  background: var(--color-primary-bg); border: 1px solid color-mix(in srgb, var(--color-primary) 25%, transparent);
  padding: var(--space-3) var(--space-4); border-radius: var(--radius);
}
.read-only-tip .el-icon { width: 16px; height: 16px; opacity: .7; color: var(--color-primary); }
.read-only-badge {
  margin-left: auto; flex-shrink: 0;
  padding: 1px var(--space-2); border-radius: var(--radius-pill);
  background: var(--bg-card); color: var(--text-secondary);
  border: 1px solid var(--border-color); font-size: var(--font-xs); font-weight: var(--weight-medium);
}
</style>
