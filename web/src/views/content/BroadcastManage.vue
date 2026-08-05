<script setup lang="ts">
/**
 * BroadcastManage：广播管理（信息管理聚合页"广播"tab 内容组件）。
 * 首页滚动通知条：增删改、启停、排序。
 */
import { ref, computed, onMounted } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import DataTable from '@/components/DataTable.vue'
import FormDialog from '@/components/FormDialog.vue'
import { Plus, Delete } from '@element-plus/icons-vue'
import {
  listBroadcasts, createBroadcast, updateBroadcast, deleteBroadcast, BROADCAST_TYPE_LABEL,
} from '@/api/broadcast'

const toast = useToastStore()
const confirm = useConfirmStore()

const list = ref<any[]>([])
const loading = ref(false)
const error = ref('')
const searchQuery = ref('')

const typeFilter = ref('')
const statusFilter = ref('')

const typeOptions = [
  { label: '全部类型', value: '' },
  { label: '通知', value: 'NOTICE' },
  { label: '活动', value: 'ACTIVITY' },
  { label: '菜品', value: 'DISH' },
  { label: '外链', value: 'URL' },
  { label: '无跳转', value: 'NONE' },
]
const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '展示中', value: 'enabled' },
  { label: '已停用', value: 'disabled' },
]

const filtered = computed(() => {
  let result = list.value
  if (typeFilter.value) result = result.filter(b => (b.broadcastType || 'NOTICE') === typeFilter.value)
  if (statusFilter.value) result = result.filter(b => (b.status || 'enabled') === statusFilter.value)
  const q = searchQuery.value.trim().toLowerCase()
  if (q) result = result.filter(b =>
    (b.title || '').toLowerCase().includes(q) ||
    (b.content || '').toLowerCase().includes(q),
  )
  return result
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    list.value = await listBroadcasts()
  } catch (e: any) {
    error.value = e.message || '加载广播列表失败'
    list.value = []
  } finally {
    loading.value = false
  }
}
onMounted(load)

// ===== 新增/编辑 =====
const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  title: '', content: '', broadcastType: 'NOTICE', targetUrl: '',
  sortOrder: 0, status: 'enabled' as 'enabled' | 'disabled',
})
const formErrors = ref<Record<string, string>>({})

function openAdd() {
  editingId.value = null
  form.value = { title: '', content: '', broadcastType: 'NOTICE', targetUrl: '', sortOrder: 0, status: 'enabled' }
  formErrors.value = {}
  showModal.value = true
}
function openEdit(b: any) {
  editingId.value = Number(b.id)
  form.value = {
    title: b.title || '',
    content: b.content || '',
    broadcastType: b.broadcastType || 'NOTICE',
    targetUrl: b.targetUrl || '',
    sortOrder: b.sortOrder ?? 0,
    status: b.status === 'disabled' ? 'disabled' : 'enabled',
  }
  formErrors.value = {}
  showModal.value = true
}
function validate() {
  const errs: Record<string, string> = {}
  if (!form.value.title.trim()) errs.title = '标题不能为空'
  if (!form.value.content.trim()) errs.content = '内容不能为空'
  if (form.value.broadcastType === 'URL' && !/^https?:\/\//.test(form.value.targetUrl.trim())) {
    errs.targetUrl = '外链类型需填写 https:// 开头的链接'
  }
  formErrors.value = errs
  return Object.keys(errs).length === 0
}
async function submit() {
  if (!validate()) return
  try {
    if (editingId.value !== null) {
      await updateBroadcast(editingId.value, { ...form.value })
      toast.success('广播已更新')
    } else {
      await createBroadcast({ ...form.value })
      toast.success('广播已添加')
    }
    showModal.value = false
    await load()
  } catch (e: any) {
    toast.error(e.message || '保存失败')
  }
}
async function handleDelete(b: any) {
  if (!await confirm.confirm(`确定删除广播「${b.title}」？`)) return
  try {
    await deleteBroadcast(Number(b.id))
    toast.success('广播已删除')
    await load()
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  }
}
function typeLabel(t: string): string {
  return BROADCAST_TYPE_LABEL[t] || t || '—'
}

// ===== 行内状态快捷切换（展示/停用） =====
const switchId = ref<number | null>(null)
async function toggleStatus(b: any, active: boolean) {
  const next = active ? 'enabled' : 'disabled'
  switchId.value = Number(b.id)
  try {
    await updateBroadcast(Number(b.id), { status: next })
    toast.success(`广播「${b.title}」已${active ? '展示' : '停用'}`)
    await load()
  } catch (e: any) {
    toast.error(e.message || '状态更新失败')
  } finally {
    switchId.value = null
  }
}
</script>

<template>
  <div>
    <FilterBar v-model="searchQuery">
      <template #default>
        <FilterSelect v-model="typeFilter" label="类型" :options="typeOptions" :width="140" />
        <FilterSelect v-model="statusFilter" label="状态" :options="statusOptions" :width="140" />
      </template>
      <template #actions>
        <button class="btn-primary" v-press @click="openAdd"><el-icon class="btn-plus-icon"><Plus /></el-icon>新增广播</button>
      </template>
    </FilterBar>

    <DataTable
      :columns="[
        { prop: 'title', label: '标题', sortable: true },
        { prop: 'type', label: '类型', width: '90px', align: 'center' },
        { prop: 'content', label: '内容', ellipsis: true },
        { prop: 'sort', label: '排序', width: '80px', align: 'center', sortable: true, sortValue: (row) => row.sortOrder },
        { prop: 'status', label: '状态', width: '110px', align: 'center' },

      ]"
      :rows="filtered"
      :loading="loading"
      :error="error"
      empty-text="暂无广播，点击新增">
      <template #cell-title="{ row }"><span class="cell-title">{{ row.title }}</span></template>
      <template #cell-type="{ row }"><span class="tt-tag">{{ typeLabel(row.broadcastType) }}</span></template>
      <template #cell-content="{ row }"><span class="cell-sub" :title="row.content">{{ row.content }}</span></template>
      <template #cell-sort="{ row }">{{ row.sortOrder }}</template>
      <template #cell-status="{ row }">
        <div class="status-cell">
          <el-switch
            :model-value="(row.status || 'enabled') === 'enabled'"
            :loading="switchId === Number(row.id)"
            :disabled="switchId === Number(row.id)"
            @change="(v: any) => toggleStatus(row, !!v)"
          />
          <span class="status-text" :class="(row.status || 'enabled') === 'enabled' ? 'on' : 'off'">{{ (row.status || 'enabled') === 'enabled' ? '展示中' : '已停用' }}</span>
        </div>
      </template>
      <template #actions="{ row }">
        <button class="link" v-press @click="openEdit(row)">编辑</button>
        <button class="link danger" v-press @click="handleDelete(row)">
          <el-icon class="act-ico"><Delete /></el-icon>删除
        </button>
      </template>
    </DataTable>

    <FormDialog :show="showModal" :title="editingId !== null ? '编辑广播' : '新增广播'" :width="520" confirm-text="保存" @close="showModal = false" :on-confirm="submit">
      <div class="bf-form">
        <div class="field"><label>标题 <span class="required">*</span></label>
          <input v-model="form.title" placeholder="如：今日推荐" />
          <p v-if="formErrors.title" class="field-error">{{ formErrors.title }}</p>
        </div>
        <div class="field"><label>内容 <span class="required">*</span></label>
          <textarea v-model="form.content" rows="2" placeholder="首页滚动展示的正文" />
          <p v-if="formErrors.content" class="field-error">{{ formErrors.content }}</p>
        </div>
        <div class="bf-row">
          <div class="field flex-1"><label>类型</label>
            <select v-model="form.broadcastType">
              <option value="NOTICE">通知</option>
              <option value="ACTIVITY">活动</option>
              <option value="DISH">菜品</option>
              <option value="URL">外链</option>
              <option value="NONE">无跳转</option>
            </select>
          </div>
          <div class="field flex-1"><label>排序（越小越靠前）</label>
            <input v-model.number="form.sortOrder" type="number" min="0" />
          </div>
        </div>
        <div v-if="form.broadcastType === 'URL'" class="field"><label>跳转链接</label>
          <input v-model="form.targetUrl" placeholder="https://mp.weixin.qq.com/..." />
          <p v-if="formErrors.targetUrl" class="field-error">{{ formErrors.targetUrl }}</p>
        </div>
        <div class="field"><label>状态</label>
          <select v-model="form.status">
            <option value="enabled">展示</option>
            <option value="disabled">停用</option>
          </select>
        </div>
      </div>
    </FormDialog>
  </div>
</template>

<style scoped>
.bf-form { display: flex; flex-direction: column; gap: var(--space-3); }
.bf-row { display: flex; gap: var(--space-3); }
.flex-1 { flex: 1; }
.required { color: var(--color-error); }
.field-error { font-size: var(--font-sm); color: var(--color-error); margin-top: var(--space-1); }
.cell-title { font-weight: var(--weight-semibold); color: var(--text-primary); }
.cell-sub { font-size: var(--font-sm); color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 320px; display: inline-block; vertical-align: middle; }
.tt-tag { display: inline-block; padding: 0 var(--space-2); border-radius: var(--radius-sm); background: var(--bg-gray); color: var(--text-secondary); font-size: var(--font-xs); }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }
.act-ico { width: 13px; height: 13px; vertical-align: -2px; margin-right: 2px; }
/* 行内状态开关 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--text-light); }
</style>
