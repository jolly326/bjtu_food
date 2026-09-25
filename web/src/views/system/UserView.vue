<script setup lang="ts">
/**
 * UserView：学生账号页（一级入口 /dashboard/system）。
 * 2026-09-15（本轮精简）：原「用户与系统」聚合页（账号 / 操作日志两张分类卡）删除后，
 * 本视图直接作为该路由组件 —— 补页头（H1「学生账号」+ 唯一主操作「刷新」），
 * 页内只读统计行（.stat-inline）删除，数量统一由 DataTable footer「共 N 条」承担。
 * 能力不变：列表 / 状态开关 / 批量启用·禁用 / 行内「行为」聚合弹窗。
 */
import { ref, computed, onMounted } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useUserStore } from '@/stores/userStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { userApi } from '@/api'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import DataTable from '@/components/DataTable.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import StatusTag from '@/components/StatusTag.vue'
import UserActivityModal from '@/components/UserActivityModal.vue'
import { Pointer, RefreshRight } from '@element-plus/icons-vue'

const store = useAdminStore()
const userStore = useUserStore()
const toast = useToastStore()
const confirm = useConfirmStore()

const searchQuery = ref('')
// 用户行为聚合弹窗
const activityUser = ref<any>(null)

// 三态（WEB-108）：进入页面显式刷新，供 DataTable 展示 loading/error
// WEB-02：本页仅依赖「学生用户」一个域，按需加载。
const loading = ref(true)
const error = ref('')
async function refresh() {
  loading.value = true
  error.value = ''
  try {
    await userStore.loadAll()
  } catch (e: any) {
    error.value = e.message || '加载学生列表失败'
  } finally {
    loading.value = false
  }
}
onMounted(refresh)

// 全量用户即学生，列表无需按角色过滤
const students = computed(() => store.users)

// 计数不再页内自算：数量只由 DataTable footer「共 N 条」承担

const statusFilter = ref<string>('')
const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '正常', value: 'active' },
  { label: '已禁用', value: 'disabled' },
]

// 认证状态筛选：选项值仅表达「筛哪一类」，认证态本身由 bindEmail 非空派生
// （出参不含 verified 字段，判据唯一真源 = bindEmail）
const authFilter = ref<string>('')
const authOptions = [
  { label: '全部认证', value: '' },
  { label: '已认证', value: '1' },
  { label: '未认证', value: '0' },
]

/**
 * 游客短标识：由账号 `id` 现算「食客 + ID 尾 4 位」（id 不足 4 位取全量）。
 * spec §7.32：该值是 `id` 的纯派生，不再由接口出参，管理端与小程序端各自现算
 * （规则同服务端建号默认昵称，三处口径一致）。
 */
function guestLabelOf(u: { id?: unknown }): string {
  const id = u?.id
  if (id === null || id === undefined || id === '') return ''
  const s = String(id)
  return `食客${s.length > 4 ? s.slice(-4) : s}`
}

const filteredStudents = computed(() => {
  let list = students.value
  if (statusFilter.value) list = list.filter(u => u.status === statusFilter.value)
  if (authFilter.value !== '') {
    const wantVerified = authFilter.value === '1'
    list = list.filter(u => !!u.bindEmail === wantVerified)
  }
  const q = searchQuery.value
  if (!q) return list
  return list.filter(u =>
    u.username.toLowerCase().includes(q) ||
    (u.nickname || '').toLowerCase().includes(q) ||
    guestLabelOf(u).toLowerCase().includes(q)
  )
})

// 微信绑定展示：仅用布尔标识 wechatBound，不再展示/脱敏 openid 明文（规避隐私泄露，审计 #5）

// ===== 行内状态快捷切换（正常/禁用） =====
const switchId = ref<number | null>(null)
async function toggleStatus(row: any, active: boolean) {
  if (row.status === (active ? 'active' : 'disabled')) return
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

// ===== 批量启用/禁用（WEB-105：循环期间抑制逐条 reload——纯接口调用并统计成败，结束后统一刷新一次） =====
const selectedIds = ref<number[]>([])
const batchRunning = ref(false)
async function batchSetStatus(status: 'active' | 'disabled') {
  if (!selectedIds.value.length || batchRunning.value) return
  const action = status === 'active' ? '启用' : '禁用'
  // 后台无操作人身份（§7.10 A），且全量用户即学生，不存在误封管理员的风险
  const targets = students.value.filter(u => selectedIds.value.includes(Number(u.id)) && u.status !== status)
  if (!targets.length) {
    toast.error('所选用户中无可操作的账号')
    return
  }
  if (!await confirm.confirm(`确定批量${action} ${targets.length} 名学生？`)) return
  batchRunning.value = true
  let okCount = 0
  const failedIds: number[] = []
  // 单条失败不中断批次：记录后继续执行下一条
  for (const u of targets) {
    try {
      await userApi.toggleUserStatusById(Number(u.id), status)
      okCount++
    } catch {
      failedIds.push(Number(u.id))
    }
  }
  // 结束后统一刷新一次（替代逐条 N 次全量 reload）
  await refresh()
  batchRunning.value = false
  const failCount = failedIds.length
  if (failCount === 0) {
    toast.success(`已批量${action} ${okCount} 名学生`)
    selectedIds.value = []
  } else {
    // 部分失败：汇总成败计数，仅保留失败项便于重试
    toast.error(`批量${action}完成：成功 ${okCount} 条，失败 ${failCount} 条`)
    selectedIds.value = failedIds
  }
}
</script>

<template>
  <PageContainer>
    <!-- 页头：页面身份 + 唯一主操作（刷新）；数量只由表格 footer 承担，不与筛选条争位 -->
    <PageHeader title="学生账号">
      <template #actions>
        <button class="btn-secondary" v-press type="button" :disabled="loading" @click="refresh">
          <el-icon class="act-ico"><RefreshRight /></el-icon>刷新
        </button>
      </template>
    </PageHeader>

    <FilterBar v-model="searchQuery">
      <template #default>
        <FilterSelect v-model="statusFilter" label="状态" :options="statusOptions" :width="150" />
        <FilterSelect v-model="authFilter" label="认证" :options="authOptions" :width="150" />
      </template>
      <template #actions>
        <template v-if="selectedIds.length">
          <button class="btn-secondary" v-press type="button" :disabled="batchRunning" @click="batchSetStatus('active')">批量启用</button>
          <button class="btn-danger" v-press type="button" :disabled="batchRunning" @click="batchSetStatus('disabled')">批量禁用（{{ selectedIds.length }}）</button>
        </template>
      </template>
    </FilterBar>

    <DataTable
      selectable
      v-model:selectedIds="selectedIds"
      :columns="[
        { prop: 'avatar', label: '头像', width: '44px', align: 'center' },
        { prop: 'userInfo', label: '用户信息' },
        { prop: 'authState', label: '认证', width: '90px', align: 'center' },
        { prop: 'created', label: '注册时间', width: '130px', sortable: true, sortValue: (row) => row.created_at },
        { prop: 'status', label: '状态', width: '110px', align: 'center' },
      ]"
      :rows="filteredStudents"
      :loading="loading"
      :error="error"
      :empty-text="searchQuery ? '没有匹配的学生' : '暂无学生用户'"
    >
      <template #cell-avatar="{ row }">
        <span class="avatar-circle">{{ (row.nickname || row.username)[0] }}</span>
      </template>
      <template #cell-userInfo="{ row }">
        <div class="user-name">{{ row.nickname || guestLabelOf(row) || row.username }}</div>
        <div class="user-meta">
          <span class="user-username">@{{ row.username }}</span>
        </div>
        <!-- 微信登录体系落地后的新字段（task-02）：微信绑定 / 绑定邮箱 -->
        <div v-if="row.wechatBound || row.bindEmail" class="user-meta user-bind">
          <span v-if="row.wechatBound" class="user-wechat" title="微信绑定">已绑定微信</span>
          <span v-if="row.wechatBound && row.bindEmail" class="user-sep">·</span>
          <span v-if="row.bindEmail" class="user-email">{{ row.bindEmail }}</span>
        </div>
      </template>
      <template #cell-authState="{ row }">
        <!-- 认证态派生自 bindEmail 非空（唯一判据）；出参已无 verified 字段 -->
        <StatusTag :type="row.bindEmail ? 'success' : 'gray'" :text="row.bindEmail ? '已认证' : '未认证'" />
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
        <button class="link" v-press @click="activityUser = row">
          <el-icon class="act-ico"><Pointer /></el-icon>行为
        </button>
      </template>
    </DataTable>

    <UserActivityModal :show="!!activityUser" :user="activityUser" @close="activityUser = null" />
  </PageContainer>
</template>

<style scoped>
/* 计数展示统一交 DataTable footer（共 N 条），本页不再持有统计样式 */

.avatar-circle {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--color-primary);
  color: var(--color-on-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-base);
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
.user-bind { margin-top: 0; }
.user-wechat { color: var(--text-muted); font-variant-numeric: tabular-nums; }
.user-email { color: var(--text-light); font-variant-numeric: tabular-nums; }

/* .act-ico 已收敛至 shared.css 公共类 */
/* 行内状态开关 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--color-error); }
</style>
