<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useUserStore } from '@/stores/userStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import DataTable from '@/components/DataTable.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import StatusTag from '@/components/StatusTag.vue'
import UserActivityModal from '@/components/UserActivityModal.vue'
import { Pointer } from '@element-plus/icons-vue'

const store = useAdminStore()
const userStore = useUserStore()
const toast = useToastStore()
const confirm = useConfirmStore()

const searchQuery = ref('')
// 用户行为聚合弹窗
const activityUser = ref<any>(null)

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

// 认证状态筛选（task-02：微信登录体系落地后的新字段）
const verifiedFilter = ref<string>('')
const verifiedOptions = [
  { label: '全部认证', value: '' },
  { label: '已认证', value: '1' },
  { label: '未认证', value: '0' },
]

const filteredStudents = computed(() => {
  let list = students.value
  if (statusFilter.value) list = list.filter(u => u.status === statusFilter.value)
  if (verifiedFilter.value !== '') list = list.filter(u => Number(u.verified ?? 0) === Number(verifiedFilter.value))
  const q = searchQuery.value
  if (!q) return list
  return list.filter(u =>
    u.username.toLowerCase().includes(q) ||
    (u.nickname || '').toLowerCase().includes(q)
  )
})

// openid 脱敏展示：仅保留尾 4 位（管理端可见绑定关系，不外泄完整 openid）
function maskOpenid(openid?: string): string {
  if (!openid) return ''
  if (openid.length <= 4) return openid
  return `****${openid.slice(-4)}`
}

// ===== 行内状态快捷切换（正常/禁用） =====
const switchId = ref<number | null>(null)
async function toggleStatus(row: any, active: boolean) {
  if (row.status === (active ? 'active' : 'disabled')) return
  // 禁止管理员操作自己（禁用/启用自身会导致无法登录）
  if (userStore.adminId != null && Number(row.id) === userStore.adminId) {
    toast.error('不能操作当前登录的账号')
    return
  }
  switchId.value = Number(row.id)
  try {
    await store.toggleUserStatus(Number(row.id), active ? 'active' : 'disabled')
    toast.success(`学生「${row.nickname || row.username}」已${active ? '启用' : '禁用'}`)
  } catch (e: any) {
    toast.error(e.message || '状态更新失败')
  } finally {
    switchId.value = null
  }
}

// ===== 批量启用/禁用 =====
const selectedIds = ref<number[]>([])
async function batchSetStatus(status: 'active' | 'disabled') {
  if (!selectedIds.value.length) return
  const action = status === 'active' ? '启用' : '禁用'
  // 过滤掉当前登录管理员自身，避免批量封禁把自己踢下线
  const selfId = userStore.adminId
  const targets = students.value.filter(u => selectedIds.value.includes(Number(u.id)) && u.status !== status && (selfId == null || Number(u.id) !== selfId))
  if (!targets.length) {
    toast.error('所选用户中无可操作的账号')
    return
  }
  if (!await confirm.confirm(`确定批量${action} ${targets.length} 名学生？`)) return
  try {
    for (const u of targets) await store.toggleUserStatus(Number(u.id), status)
    toast.success(`已批量${action} ${targets.length} 名学生`)
    selectedIds.value = []
  } catch (e: any) {
    toast.error(e.message || `批量${action}失败`)
  }
}
</script>

<template>
    <!-- 统计与筛选合并为一行，不再单独占卡片空间 -->
    <FilterBar v-model="searchQuery">
      <template #default>
        <FilterSelect v-model="statusFilter" label="状态" :options="statusOptions" :width="150" />
        <FilterSelect v-model="verifiedFilter" label="认证" :options="verifiedOptions" :width="150" />
      </template>
      <template #actions>
        <template v-if="selectedIds.length">
          <button class="btn-secondary" v-press type="button" @click="batchSetStatus('active')">批量启用</button>
          <button class="btn-danger" v-press type="button" @click="batchSetStatus('disabled')">批量禁用（{{ selectedIds.length }}）</button>
        </template>
        <span class="stat-inline">共 {{ stats.total }} · 正常 {{ stats.active }} · 禁用 {{ stats.disabled }}</span>
      </template>
    </FilterBar>

    <DataTable
      selectable
      v-model:selectedIds="selectedIds"
      :columns="[
        { prop: 'avatar', label: '头像', width: '44px', align: 'center' },
        { prop: 'userInfo', label: '用户信息' },
        { prop: 'verified', label: '认证', width: '90px', align: 'center' },
        { prop: 'created', label: '注册时间', width: '130px', sortable: true, sortValue: (row) => row.created_at },
        { prop: 'status', label: '状态', width: '110px', align: 'center' },

      ]"
      :rows="filteredStudents"
      :empty-text="searchQuery ? '没有匹配的学生' : '暂无学生用户'"
    >
      <template #cell-avatar="{ row }">
        <span class="avatar-circle">{{ (row.nickname || row.username)[0] }}</span>
      </template>
      <template #cell-userInfo="{ row }">
        <div class="user-name">{{ row.nickname || row.guestShortId || row.username }}</div>
        <div class="user-meta">
          <span class="user-username">@{{ row.username }}</span>
          <span class="user-sep">·</span>
          <span class="user-date">{{ row.created_at.toLocaleDateString('zh-CN') }} 注册</span>
        </div>
        <!-- 微信登录体系落地后的新字段（task-02）：微信绑定 / 绑定邮箱 -->
        <div v-if="row.openid || row.bindEmail" class="user-meta user-bind">
          <span v-if="row.openid" class="user-wechat" title="微信绑定">微信 {{ maskOpenid(row.openid) }}</span>
          <span v-if="row.openid && row.bindEmail" class="user-sep">·</span>
          <span v-if="row.bindEmail" class="user-email">{{ row.bindEmail }}</span>
        </div>
      </template>
      <template #cell-verified="{ row }">
        <StatusTag :type="Number(row.verified) === 1 ? 'success' : 'gray'" :text="Number(row.verified) === 1 ? '已认证' : '未认证'" />
      </template>
      <template #cell-created="{ row }">{{ row.created_at.toLocaleDateString('zh-CN') }}</template>
      <template #cell-status="{ row }">
        <div class="status-cell">
          <el-switch
            :model-value="row.status === 'active'"
            :loading="switchId === Number(row.id)"
            :disabled="switchId === Number(row.id)"
            @change="(v: any) => toggleStatus(row, !!v)"
          />
          <span class="status-text" :class="row.status === 'active' ? 'on' : 'off'">{{ row.status === 'active' ? '正常' : '已禁用' }}</span>
        </div>
      </template>
      <template #actions="{ row }">
        <button class="btn-secondary" v-press @click="activityUser = row">
          <el-icon class="act-ico"><Pointer /></el-icon>行为
        </button>
      </template>
    </DataTable>

    <UserActivityModal :show="!!activityUser" :user="activityUser" @close="activityUser = null" />
</template>

<style scoped>
/* 统计信息并入筛选条（合并为一，节省空间） */
.stat-inline {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--font-sm);
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.avatar-circle {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--color-primary);
  color: var(--color-on-primary);
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
.user-bind { margin-top: 0; }
.user-wechat { color: var(--color-primary); font-variant-numeric: tabular-nums; }
.user-email { color: var(--text-light); font-variant-numeric: tabular-nums; }

/* .act-ico 已收敛至 shared.css 公共类 */
/* 行内状态开关 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--color-error); }
</style>
