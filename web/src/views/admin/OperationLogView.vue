<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import DataTable from '@/components/DataTable.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import { Document } from '@element-plus/icons-vue'
import { useAsyncGuard } from '@/composables/useAsyncGuard'
import type { OperationLogVO } from '@/api/operationLog'
import {
  OPERATION_ACTION_OPTIONS,
  OPERATION_TARGET_OPTIONS,
  operationActionText,
  operationTargetText,
} from '@/constants'

const searchQuery = ref('')

/**
 * 操作日志（task-10 W4）：只读查询，无任何写操作/删除按钮。
 * 过滤：adminId / action / targetType / startAt / endAt（前端 SearchInput 模糊匹配本地结果）。
 */

// 请求竞态守卫（UI-05 收敛为 useAsyncGuard）：连续输入/切筛选会并发请求，
// 过期响应整份丢弃，防数据错乱；数据赋值在 alive() 校验后进行。
const { loading, error, run } = useAsyncGuard()
const rows = ref<OperationLogVO[]>([])

// ===== 受控分页（后端已分页，total 来自后端；pageSize ≤ 100 不触碰后端上限） =====
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function reloadFromFirstPage() {
  page.value = 1
  await loadList()
}
function onPageChange() {
  loadList()
}

// 动作 / 对象筛选预设与文案映射统一收敛至 constants/index.ts（OPERATION_ACTION_* / OPERATION_TARGET_*）
// ——与后端 OperationLogConst 同源、与工作台「近期操作」共用同一份映射，避免两处各写一套导致口径漂移。
const activeAction = ref('')
const activeTarget = ref('')

// 时间区间筛选（Q-112 ④）：透传既有 startAt / endAt 参数（后端已支持，仅补 UI 入口）。
// el-date-picker 的 value-format 出参为 'YYYY-MM-DD'（含空值时为 [start,end] 或 null 的形态），
// 提交前拼上日界时刻（起 00:00:00 / 止 23:59:59），与后端契约 yyyy-MM-dd HH:mm:ss 对齐（PR-02 口径以后端为准）。
const dateRange = ref<[string, string] | null>(null)

function toStartAt(): string | undefined {
  const s = dateRange.value?.[0]
  return s ? `${s} 00:00:00` : undefined
}
function toEndAt(): string | undefined {
  const e = dateRange.value?.[1]
  return e ? `${e} 23:59:59` : undefined
}

async function loadList() {
  await run(async (alive) => {
    const { operationLogApi } = await import('@/api')
    const res = await operationLogApi.listOperationLogs({
      action: activeAction.value || undefined,
      keyword: searchQuery.value.trim() || undefined,
      targetType: activeTarget.value || undefined,
      startAt: toStartAt(),
      endAt: toEndAt(),
      page: page.value,
      pageSize: pageSize.value,
    })
    if (!alive()) return // 已有更新的请求发出，丢弃过期响应
    rows.value = res.list
    total.value = res.total
  })
}

onMounted(loadList)

async function onActionChange() { await reloadFromFirstPage() }
async function onTargetChange() { await reloadFromFirstPage() }
/** 时间区间变化（选择 / 清空）→ 回第 1 页按区间重新拉取 */
async function onDateRangeChange() { await reloadFromFirstPage() }

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
        <FilterSelect v-model="activeAction" label="动作" :options="OPERATION_ACTION_OPTIONS" :width="160" @change="onActionChange" />
        <FilterSelect v-model="activeTarget" label="对象" :options="OPERATION_TARGET_OPTIONS" :width="160" @change="onTargetChange" />
        <!-- 时间区间（Q-112 ④）：绑定既有 startAt / endAt，仅补 UI 入口，无新增接口 -->
        <div class="date-range-filter">
          <label class="dr-label">时间</label>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            unlink-panels
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            :clearable="true"
            class="dr-picker"
            @change="onDateRangeChange"
          />
        </div>
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
      <template #cell-action="{ row }"><StatusTag type="info" :text="operationActionText(row.action)" /></template>
      <template #cell-target="{ row }">
        <span v-if="row.targetType">{{ operationTargetText(row.targetType) }}#{{ row.targetId }}</span>
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
/* 时间区间筛选：与 FilterSelect 同一视觉语言（label 在左、控件高度 ~36px 对齐） */
.date-range-filter { display: inline-flex; align-items: center; gap: var(--space-2); }
.dr-label { font-size: var(--font-sm); color: var(--text-secondary); font-weight: var(--weight-medium); white-space: nowrap; }
.dr-picker { width: 260px; }
.dr-picker :deep(.el-range-editor) { min-height: 36px; border-radius: var(--radius); }
.muted { color: var(--text-light); }
.ip { font-family: var(--font-numeric, monospace); font-size: var(--font-sm); color: var(--text-secondary); }
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
