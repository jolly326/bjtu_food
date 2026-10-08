<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listAlerts } from '@/api/alerts'
import { usePagedList } from '@/composables/usePagedList'
import ActiveFilters from '@/components/ActiveFilters.vue'
import BaseDrawer from '@/components/BaseDrawer.vue'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import type { AlertSeverity, SecurityAlertVO } from '@/types/common'

/**
 * 安全告警面板。
 *
 * <p><b>口径真源</b>：[ui/web/安全告警.md](../../../docs/ui/web/安全告警.md)（页面构成 / 交互）、
 * [api/web/alerts.md](../../../docs/api/web/alerts.md)。
 *
 * <p><b>为什么需要它</b>：群消息会被刷走、无法按类型回看 —— 本页把「这台机器上到底发生过什么」
 * 变成可查询、可筛选的事实。
 *
 * <p><b>只读</b>：记录只追加，页面**不提供**任何写入口（`viewer` 同样可查看）。
 */
const TYPE_OPTIONS = [
  { key: 'LOGIN_SUCCESS', label: '登录成功' },
  { key: 'LOGIN_LOCKOUT', label: '登录失败达阈值' },
  { key: 'MFA_ENABLED', label: '动态口令启用' },
  { key: 'MFA_DISABLED', label: '动态口令停用' },
  { key: 'PASSWORD_CHANGED', label: '口令修改' },
  { key: 'AUDIT_WRITE_FAILURE', label: '审计写入失败' },
  { key: 'VIOLATION_PENALTY', label: '违规累积处置' },
  { key: 'CRAWL_DETECTED', label: '爬取检测' },
] as const

/** 级别下拉（顺序按严重度递减：先看严重的） */
const SEVERITY_OPTIONS: { key: AlertSeverity; label: string }[] = [
  { key: 'critical', label: '严重' },
  { key: 'warn', label: '警告' },
  { key: 'info', label: '提示' },
]

const fType = ref('')
const fSeverity = ref<AlertSeverity | ''>('')

const {
  items,
  total,
  page,
  pageCount,
  firstLoading,
  sessionInvalid,
  error,
  isEmpty,
  hasData,
  reload,
  reloadFirstPage,
  prevPage,
  nextPage,
} = usePagedList<SecurityAlertVO>((p, pageSize) =>
  listAlerts({
    page: p,
    pageSize,
    alertType: fType.value || undefined,
    severity: fSeverity.value || undefined,
  }),
)

/** 类型标签：优先用后端下发的快照，取不到时回落本地键表（未知键原样显示，不隐藏） */
function typeLabel(row: SecurityAlertVO | null): string {
  if (!row) return ''
  if (row.alertTypeLabel) return row.alertTypeLabel
  return TYPE_OPTIONS.find((t) => t.key === row.alertType)?.label ?? row.alertType
}

/** 详情抽屉（只读） */
const open = ref(false)
const current = ref<SecurityAlertVO | null>(null)

function openDetail(row: SecurityAlertVO): void {
  current.value = row
  open.value = true
}

/** 已生效筛选条件（供 `ActiveFilters` 回显） */
const activeFilters = computed(() => {
  const out: { key: string; label: string; value: string }[] = []
  if (fType.value) {
    out.push({
      key: 'alertType',
      label: '类型',
      value: TYPE_OPTIONS.find((t) => t.key === fType.value)?.label ?? fType.value,
    })
  }
  if (fSeverity.value) {
    out.push({
      key: 'severity',
      label: '级别',
      value: SEVERITY_OPTIONS.find((s) => s.key === fSeverity.value)?.label ?? fSeverity.value,
    })
  }
  return out
})

/** 清除单个筛选条件并重查 */
function clearFilter(key: string): void {
  if (key === 'alertType') fType.value = ''
  if (key === 'severity') fSeverity.value = ''
  reloadFirstPage()
}

function reset(): void {
  fType.value = ''
  fSeverity.value = ''
  reloadFirstPage()
}

onMounted(() => reloadFirstPage())
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>安全告警</h2></div>

    <div class="card filters">
      <select class="form-input" v-model="fType" @change="reloadFirstPage">
        <option value="">全部类型</option>
        <option v-for="t in TYPE_OPTIONS" :key="t.key" :value="t.key">{{ t.label }}</option>
      </select>
      <select class="form-input" v-model="fSeverity" @change="reloadFirstPage">
        <option value="">全部级别</option>
        <option v-for="s in SEVERITY_OPTIONS" :key="s.key" :value="s.key">{{ s.label }}</option>
      </select>
      <button class="btn-primary" type="button" v-press @click="reloadFirstPage">查询</button>
      <button class="btn-secondary" type="button" @click="reset">重置</button>
      <ActiveFilters :items="activeFilters" @remove="clearFilter" @clear="reset" />
    </div>

    <!-- 六态：① 加载 ② 错误 ③ 空 ④ 有数据 ⑤ 分页 ⑥ 会话失效 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无告警记录"
      @retry="reload"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>时间</th>
            <th>级别</th>
            <th>类型</th>
            <th>标题</th>
            <th>来源 IP</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td class="muted">{{ row.createdAt }}</td>
            <td>
              <span :class="['sev', `sev-${row.severity}`]">{{ row.severityLabel }}</span>
            </td>
            <td>{{ typeLabel(row) }}</td>
            <td>{{ row.title }}</td>
            <td class="muted">{{ row.sourceIp || '—' }}</td>
            <td class="actions">
              <button class="link" type="button" @click="openDetail(row)">详情</button>
            </td>
          </tr>
        </tbody>
      </table>

      <Pager
        :total="total"
        :page="page"
        :page-count="pageCount"
        @prev="prevPage"
        @next="nextPage"
      />
    </div>

    <!-- 详情抽屉（只读）：明细全文 + 全部字段 -->
    <BaseDrawer title="告警详情" :open="open" @close="open = false">
      <div class="ctx">
        <div class="ctx-label">
          <span :class="['sev', `sev-${current?.severity}`]">{{ current?.severityLabel }}</span>
          {{ typeLabel(current) }}
        </div>
        <div class="ctx-body">{{ current?.title }}</div>
      </div>

      <div class="detail-group">
        <div class="detail-group-title">明细</div>
        <div class="ctx">
          <div class="ctx-body">{{ current?.detail || '（无明细）' }}</div>
        </div>
      </div>

      <div class="detail-group">
        <div class="detail-group-title">记录信息</div>
        <div class="detail-meta detail-meta--grid">
          <DetailMetaRow k="发生时间">{{ current?.createdAt || '—' }}</DetailMetaRow>
          <DetailMetaRow k="来源 IP">{{ current?.sourceIp || '—' }}</DetailMetaRow>
          <DetailMetaRow k="类型键">{{ current?.alertType || '—' }}</DetailMetaRow>
          <DetailMetaRow k="级别键">{{ current?.severity || '—' }}</DetailMetaRow>
        </div>
      </div>
    </BaseDrawer>
  </div>
</template>

<style scoped>
/* 级别标识：只读语义色，不承载交互 */
.sev {
  display: inline-block;
  padding: 2px var(--space-2);
  border-radius: var(--radius-xs);
  font-size: var(--font-xs);
  font-weight: var(--weight-medium);
  white-space: nowrap;
}
.sev-critical {
  background: var(--color-danger-soft);
  color: var(--color-error);
}
.sev-warn {
  background: var(--bg-soft);
  color: var(--color-warning);
}
.sev-info {
  background: var(--bg-soft);
  color: var(--text-muted);
}
</style>
