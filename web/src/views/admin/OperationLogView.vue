<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { usePageStore } from '@/stores/pageStore'
import DataTable from '@/components/DataTable.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import { Document } from '@element-plus/icons-vue'
import type { OperationLogVO } from '@/api/operationLog'

const page = usePageStore()
page.setPage({
  breadcrumbs: [{ label: '系统审计' }, { label: '操作日志' }],
  showSearch: true,
  searchPlaceholder: '搜索操作人/动作/IP...',
})

/**
 * 操作日志（task-10 W4）：只读查询，无任何写操作/删除按钮。
 * 过滤：adminId / action / targetType / startAt / endAt（前端 SearchInput 模糊匹配本地结果）。
 */

const loading = ref(false)
const error = ref('')
const rows = ref<OperationLogVO[]>([])

// 动作筛选预设（对齐 ARCH OperationLogConst.ACTION_*）
const actionOptions = [
  { value: '', label: '全部动作' },
  { value: 'audit_approve', label: '审核通过' },
  { value: 'audit_reject', label: '审核退回' },
  { value: 'moment_hide', label: '动态下架' },
  { value: 'moment_delete', label: '动态删除' },
  { value: 'feedback_handle', label: '反馈处理' },
]
const targetTypeOptions = [
  { value: '', label: '全部对象' },
  { value: 'moment', label: '动态' },
  { value: 'dish', label: '菜品' },
  { value: 'stall', label: '档口' },
  { value: 'canteen', label: '食堂' },
  { value: 'feedback', label: '反馈' },
  { value: 'review', label: '评价' },
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
  try {
    const { operationLogApi } = await import('@/api')
    const res = await operationLogApi.listOperationLogs({
      action: activeAction.value || undefined,
      targetType: activeTarget.value || undefined,
      pageSize: 200,
    })
    rows.value = res.list
  } catch (e: any) {
    error.value = e.message || '加载操作日志失败'
    rows.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadList)

async function onActionChange() { await loadList() }
async function onTargetChange() { await loadList() }

// SearchInput 本地模糊过滤（操作人/动作/IP）
const filtered = computed(() => {
  const q = page.searchQuery.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter(
    r => (r.adminNickname || '').toLowerCase().includes(q)
      || (r.action || '').toLowerCase().includes(q)
      || (r.ip || '').toLowerCase().includes(q),
  )
})

function fmtTime(v: string): string {
  if (!v) return '—'
  const d = new Date(v)
  return isNaN(d.getTime()) ? v : d.toLocaleString('zh-CN')
}
</script>

<template>
  <PageContainer>
    <PageHeader title="操作日志" :count="rows.length" subtitle="仅记录管理员写操作，只读" />

    <FilterBar>
      <template #default>
        <FilterSelect v-model="activeAction" label="动作" :options="actionOptions" :width="160" @change="onActionChange" />
        <FilterSelect v-model="activeTarget" label="对象" :options="targetTypeOptions" :width="160" @change="onTargetChange" />
      </template>
    </FilterBar>

    <DataTable
      :columns="[
        { prop: 'admin', label: '操作人', width: '140px' },
        { prop: 'action', label: '动作', width: '120px' },
        { prop: 'target', label: '操作对象', width: '140px' },
        { prop: 'ip', label: '来源 IP', width: '150px' },
        { prop: 'time', label: '操作时间', width: '180px' },
      ]"
      :rows="filtered"
      :loading="loading"
      :error="error"
      :search-filter="true"
      empty-text="暂无操作日志"
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
      <el-icon><Document /></el-icon> 操作日志由系统自动记录（AOP 埋点），仅供查询，不可修改或删除。
    </div>
  </PageContainer>
</template>

<style scoped>
.muted { color: var(--text-light); }
.ip { font-family: var(--font-mono, monospace); font-size: var(--font-sm); color: var(--text-secondary); }
.read-only-tip {
  margin-top: var(--space-4); display: flex; align-items: center; gap: var(--space-2);
  font-size: var(--font-sm); color: var(--text-muted);
  background: var(--bg-soft); padding: var(--space-3) var(--space-4); border-radius: var(--radius);
}
.read-only-tip .el-icon { width: 16px; height: 16px; opacity: .6; }
</style>
