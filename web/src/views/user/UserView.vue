<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { usePageStore } from '@/stores/pageStore'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import StatCard from '@/components/common/StatCard.vue'

const store = useAdminStore()
const toast = useToastStore()
const confirm = useConfirmStore()
const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: '用户管理' }], showSearch: true, searchPlaceholder: '搜索学生用户名或昵称...' })

const students = computed(() => store.users.filter(u => u.role !== 'admin'))

const stats = computed(() => ({
  total: students.value.length,
  active: students.value.filter(u => u.status === 'active').length,
  disabled: students.value.filter(u => u.status === 'disabled').length,
}))

const statusFilter = ref<string>('')
const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '正常', value: 'active' },
  { label: '已禁用', value: 'disabled' },
]

const searchQuery = computed(() => page.searchQuery.trim().toLowerCase())
const filteredStudents = computed(() => {
  let list = students.value
  if (statusFilter.value) list = list.filter(u => u.status === statusFilter.value)
  const q = searchQuery.value
  if (!q) return list
  return list.filter(u =>
    u.username.toLowerCase().includes(q) ||
    (u.nickname || '').toLowerCase().includes(q)
  )
})

async function handleToggle(id: number) {
  const u = store.users.find(u => Number(u.id) === id)
  if (!u) return
  const action = u.status === 'active' ? '禁用' : '启用'
  if (!await confirm.confirm(`确定${action}学生「${u.nickname || u.username}」？`)) return
  try {
    await store.toggleUserStatus(id)
    toast.success(`学生已${action}`)
  } catch (e: any) {
    toast.error(e.message || `${action}失败`)
  }
}
</script>

<template>
  <PageContainer>
    <PageHeader title="用户管理" :count="filteredStudents.length">
      <template #extra>
        <div class="stats-row">
          <StatCard label="学生总数" :value="stats.total" tone="default" :delay="0" />
          <StatCard label="正常" :value="stats.active" tone="success" :delay="40" />
          <StatCard label="已禁用" :value="stats.disabled" tone="danger" :delay="80" />
        </div>
      </template>
    </PageHeader>

    <FilterBar>
      <template #default>
        <FilterSelect v-model="statusFilter" label="状态" :options="statusOptions" :width="150" />
      </template>
    </FilterBar>

    <DataTable
      :columns="[
        { prop: 'avatar', label: '头像', width: '44px', align: 'center' },
        { prop: 'userInfo', label: '用户信息' },
        { prop: 'status', label: '状态', width: '120px', align: 'center' },
        { prop: 'actions', label: '操作', width: '120px', align: 'center' },
      ]"
      :rows="filteredStudents"
      :empty-text="searchQuery ? '没有匹配的学生' : '暂无学生用户'"
    >
      <template #cell-avatar="{ row }">
        <span class="avatar-circle">{{ (row.nickname || row.username)[0] }}</span>
      </template>
      <template #cell-userInfo="{ row }">
        <div class="user-name">{{ row.nickname || row.username }}</div>
        <div class="user-meta">
          <span class="user-username">@{{ row.username }}</span>
          <span class="user-sep">·</span>
          <span class="user-date">{{ row.created_at.toLocaleDateString('zh-CN') }} 注册</span>
        </div>
      </template>
      <template #cell-status="{ row }">
        <StatusTag :type="row.status === 'active' ? 'success' : 'danger'" :text="row.status === 'active' ? '正常' : '已禁用'" />
      </template>
      <template #actions="{ row }">
        <button
          class="action-btn"
          :class="row.status === 'active' ? 'action-disable' : 'action-enable'"
          v-press
          @click="handleToggle(Number(row.id))"
        >
          {{ row.status === 'active' ? '禁用' : '启用' }}
        </button>
      </template>
    </DataTable>
  </PageContainer>
</template>

<style scoped>
/* ===== 数据概览 ===== */
.stats-row {
  display: flex;
  gap: var(--space-3);
}
@media (max-width: 767px) {
  .stats-row { flex-wrap: wrap; }
}

.avatar-circle {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-light) 100%);
  color: var(--text-white);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: var(--weight-semibold);
  flex-shrink: 0;
}

/* ===== 用户信息列 ===== */
.user-name {
  font-size: var(--font-md);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  line-height: 1.4;
}
.user-meta {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  margin-top: var(--space-1);
  font-size: var(--font-xs);
  color: var(--text-muted);
}
.user-username { color: var(--text-muted); }
.user-sep { color: var(--border-soft); }
.user-date { color: var(--text-light); }

.action-btn {
  padding: var(--space-1) var(--space-4);
  border-radius: var(--radius-sm);
  font-size: var(--font-xs);
  font-weight: var(--weight-medium);
  cursor: pointer;
  transition: background 0.2s var(--ease-out), color 0.2s var(--ease-out), border-color 0.2s var(--ease-out), transform 160ms var(--ease-out);
  border: 1px solid;
}
.action-btn:active { transform: scale(var(--press-scale)); }
.action-disable { border-color: var(--color-warning); color: var(--color-warning); background: var(--bg-card); }
.action-disable:hover { background: var(--color-warning-bg); }
.action-enable { border-color: var(--color-success); color: var(--color-success); background: var(--bg-card); }
.action-enable:hover { background: var(--color-success-bg); }
</style>
