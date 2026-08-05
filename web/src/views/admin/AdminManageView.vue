<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdminUserStore } from '@/stores/adminUserStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import FormDialog from '@/components/FormDialog.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import { Plus, Delete } from '@element-plus/icons-vue'

const store = useAdminUserStore()
const toast = useToastStore()
const confirm = useConfirmStore()

const searchQuery = ref('')

const filtered = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return store.list
  return store.list.filter(a => a.username.toLowerCase().includes(q) || (a.nickname || '').toLowerCase().includes(q))
})

// 当前登录管理员 ID（用于禁止操作自己）
const myId = computed(() => {
  const id = localStorage.getItem('adminId')
  return id ? Number(id) : null
})

const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ username: '', nickname: '', password: '', confirmPwd: '' })
const formErrors = ref<Record<string, string>>({})

function openAdd() {
  editingId.value = null
  form.value = { username: '', nickname: '', password: '', confirmPwd: '' }
  formErrors.value = {}
  showModal.value = true
}
function openEdit(id: number) {
  const a = store.list.find(x => Number(x.id) === id)
  if (!a) return
  editingId.value = id
  form.value = { username: a.username, nickname: a.nickname || '', password: '', confirmPwd: '' }
  formErrors.value = {}
  showModal.value = true
}

function validate() {
  const errs: Record<string, string> = {}
  if (!form.value.username.trim()) errs.username = '用户名不能为空'
  if (editingId.value === null && !form.value.password) errs.password = '新增管理员必须设置密码'
  if (form.value.password && form.value.password.length < 6) errs.password = '密码至少 6 位'
  if (form.value.password && form.value.password !== form.value.confirmPwd) errs.confirmPwd = '两次密码不一致'
  formErrors.value = errs
  return Object.keys(errs).length === 0
}

async function handleSubmit() {
  if (!validate()) return
  try {
    if (editingId.value !== null) {
      await store.update(editingId.value, {
        nickname: form.value.nickname.trim() || undefined,
        password: form.value.password || undefined,
      })
      toast.success('管理员已更新')
    } else {
      await store.add({
        username: form.value.username.trim(),
        nickname: form.value.nickname.trim() || undefined,
        password: form.value.password,
      })
      toast.success('管理员已创建')
    }
    showModal.value = false
  } catch (e: any) {
    toast.error(e.message || '保存失败')
  }
}

// ===== 行内状态快捷切换（正常/禁用） =====
const switchId = ref<number | null>(null)
async function toggleStatus(row: any, active: boolean) {
  if (row.status === (active ? 'active' : 'disabled')) return
  switchId.value = Number(row.id)
  try {
    await store.setStatus(Number(row.id), active ? 'active' : 'disabled')
    toast.success(`管理员「${row.nickname || row.username}」已${active ? '启用' : '禁用'}`)
  } catch (e: any) {
    toast.error(e.message || '状态更新失败')
  } finally {
    switchId.value = null
  }
}

async function handleDelete(id: number) {
  const a = store.list.find(x => Number(x.id) === id)
  if (!a) return
  if (myId.value === id) { toast.error('不能删除当前登录账号'); return }
  if (!await confirm.confirm(`确定删除管理员「${a.nickname || a.username}」？此操作不可恢复。`)) return
  try {
    await store.remove(id)
    toast.success('管理员已删除')
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  }
}
</script>

<template>
    <FilterBar v-model="searchQuery">
      <template #actions>
        <button class="btn-primary" v-press @click="openAdd"><el-icon class="btn-plus-icon"><Plus /></el-icon>新增管理员</button>
      </template>
    </FilterBar>

    <DataTable
      :columns="[
        { prop: 'username', label: '用户名', sortable: true },
        { prop: 'nickname', label: '昵称' },
        { prop: 'role', label: '角色', width: '120px', align: 'center' },
        { prop: 'status', label: '状态', width: '110px', align: 'center' },
        { prop: 'createdAt', label: '创建时间', width: '150px', sortable: true, sortValue: (row) => row.created_at },

      ]"
      :rows="filtered"
      :empty-text="filtered.length ? '没有匹配的管理员' : '暂无管理员账号'"
    >
      <template #cell-username="{ row }">
        {{ row.username }}
        <span v-if="myId === Number(row.id)" class="me-tag">我</span>
      </template>
      <template #cell-nickname="{ row }">{{ row.nickname || '-' }}</template>
      <template #cell-role="{ row }">
        <StatusTag :type="row.role === 'super_admin' ? 'warning' : 'info'" :text="row.role === 'super_admin' ? '超级管理员' : '管理员'" />
      </template>
      <template #cell-status="{ row }">
        <div class="status-cell">
          <el-switch
            :model-value="row.status === 'active'"
            :loading="switchId === Number(row.id)"
            :disabled="switchId === Number(row.id) || myId === Number(row.id)"
            @change="(v: any) => toggleStatus(row, !!v)"
          />
          <span class="status-text" :class="row.status === 'active' ? 'on' : 'off'">{{ row.status === 'active' ? '正常' : '已禁用' }}</span>
        </div>
      </template>
      <template #cell-createdAt="{ row }">{{ row.created_at.toLocaleString('zh-CN') }}</template>
      <template #actions="{ row }">
        <button class="link" v-press @click="openEdit(Number(row.id))">编辑</button>
        <button class="link danger" :disabled="myId === Number(row.id)" v-press @click="handleDelete(Number(row.id))">
          <el-icon class="act-ico"><Delete /></el-icon>删除
        </button>
      </template>
    </DataTable>

    <FormDialog :show="showModal" :title="editingId !== null ? '编辑管理员' : '新增管理员'" :width="480" confirm-text="保存" @close="showModal = false" :on-confirm="handleSubmit">
      <div class="modal-form">
        <div class="field"><label>用户名 <span class="required">*</span></label>
          <input v-model="form.username" :disabled="editingId !== null" placeholder="登录用户名" />
          <p v-if="formErrors.username" class="field-error">{{ formErrors.username }}</p>
        </div>
        <div class="field"><label>昵称</label><input v-model="form.nickname" placeholder="昵称（选填）" /></div>
        <div class="field"><label>{{ editingId !== null ? '重置密码（留空不改）' : '初始密码' }} <span v-if="editingId === null" class="required">*</span></label>
          <input v-model="form.password" type="password" placeholder="至少 6 位" />
          <p v-if="formErrors.password" class="field-error">{{ formErrors.password }}</p>
        </div>
        <div class="field" v-if="editingId === null || form.password"><label>确认密码</label>
          <input v-model="form.confirmPwd" type="password" placeholder="再次输入密码" />
          <p v-if="formErrors.confirmPwd" class="field-error">{{ formErrors.confirmPwd }}</p>
        </div>
      </div>
    </FormDialog>
</template>

<style scoped>
.me-tag { display: inline-block; margin-left: var(--space-1); padding: 0 var(--space-1); font-size: var(--font-xs); background: var(--color-primary-bg); color: var(--color-primary); border-radius: var(--radius-sm); }
.link.danger:disabled { color: var(--text-light); cursor: not-allowed; }
/* 行内状态开关 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--text-light); }

.modal-form { display: flex; flex-direction: column; gap: var(--space-3); }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }
.act-ico { width: 13px; height: 13px; vertical-align: -2px; margin-right: 2px; }
</style>
