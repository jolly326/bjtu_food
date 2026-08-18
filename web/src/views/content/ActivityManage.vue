<script setup lang="ts">
/**
 * ActivityManage：活动管理（信息管理聚合页"活动"tab 内容组件）。
 * 最新活动（公众号文章卡片）：增删改、启停、排序；小程序「最新活动」页展示。
 */
import { ref, computed, onMounted } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import DataTable from '@/components/DataTable.vue'
import FormDialog from '@/components/FormDialog.vue'
import { Plus, Delete } from '@element-plus/icons-vue'
import { listActivities, createActivity, updateActivity, deleteActivity } from '@/api/activity'

const toast = useToastStore()
const confirm = useConfirmStore()

const list = ref<any[]>([])
const loading = ref(false)
const error = ref('')
const searchQuery = ref('')
const statusFilter = ref('')

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '展示中', value: 'enabled' },
  { label: '已停用', value: 'disabled' },
]

const filtered = computed(() => {
  let result = list.value
  if (statusFilter.value) result = result.filter(a => (a.status || 'enabled') === statusFilter.value)
  const q = searchQuery.value.trim().toLowerCase()
  if (q) result = result.filter(a =>
    (a.title || '').toLowerCase().includes(q) ||
    (a.description || '').toLowerCase().includes(q),
  )
  return result
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listActivities({ page: 1, pageSize: 200 })
    list.value = res.list
  } catch (e: any) {
    error.value = e.message || '加载活动列表失败'
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
  title: '', description: '', image: '', articleUrl: '',
  sortOrder: 0, status: 'enabled' as 'enabled' | 'disabled',
})
const formErrors = ref<Record<string, string>>({})

function openAdd() {
  editingId.value = null
  form.value = { title: '', description: '', image: '', articleUrl: '', sortOrder: 0, status: 'enabled' }
  formErrors.value = {}
  showModal.value = true
}
function openEdit(a: any) {
  editingId.value = Number(a.id)
  form.value = {
    title: a.title || '',
    description: a.description || '',
    image: a.image || '',
    articleUrl: a.articleUrl || '',
    sortOrder: a.sortOrder ?? 0,
    status: a.status === 'disabled' ? 'disabled' : 'enabled',
  }
  formErrors.value = {}
  showModal.value = true
}
function validate() {
  const errs: Record<string, string> = {}
  if (!form.value.title.trim()) errs.title = '标题不能为空'
  if (form.value.articleUrl && !/^https?:\/\//.test(form.value.articleUrl.trim())) {
    errs.articleUrl = '文章链接需为 https:// 开头（公众号文章 mp.weixin.qq.com）'
  }
  if (form.value.image && !/^https?:\/\//.test(form.value.image.trim())) {
    errs.image = '封面图需为 https:// 开头的图片链接'
  }
  formErrors.value = errs
  return Object.keys(errs).length === 0
}
async function submit() {
  if (!validate()) return
  try {
    if (editingId.value !== null) {
      await updateActivity(editingId.value, { ...form.value })
      toast.success('活动已更新')
    } else {
      await createActivity({ ...form.value })
      toast.success('活动已添加')
    }
    showModal.value = false
    await load()
  } catch (e: any) {
    toast.error(e.message || '保存失败')
  }
}
async function handleDelete(a: any) {
  if (!await confirm.confirm(`确定删除活动「${a.title}」？`)) return
  try {
    await deleteActivity(Number(a.id))
    toast.success('活动已删除')
    await load()
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  }
}

// ===== 行内状态快捷切换（展示/停用） =====
const switchId = ref<number | null>(null)
async function toggleStatus(a: any, active: boolean) {
  const next = active ? 'enabled' : 'disabled'
  switchId.value = Number(a.id)
  try {
    await updateActivity(Number(a.id), { status: next })
    toast.success(`活动「${a.title}」已${active ? '展示' : '停用'}`)
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
        <FilterSelect v-model="statusFilter" label="状态" :options="statusOptions" :width="140" />
      </template>
      <template #actions>
        <button class="btn-primary" v-press @click="openAdd"><el-icon class="btn-plus-icon"><Plus /></el-icon>新增活动</button>
      </template>
    </FilterBar>

    <DataTable
      :columns="[
        { prop: 'title', label: '标题', sortable: true },
        { prop: 'url', label: '公众号文章', ellipsis: true },
        { prop: 'sort', label: '排序', width: '80px', align: 'center', sortable: true, sortValue: (row) => row.sortOrder },
        { prop: 'status', label: '状态', width: '110px', align: 'center' },
      ]"
      :rows="filtered"
      :loading="loading"
      :error="error"
      empty-text="暂无活动，点击新增">
      <template #cell-title="{ row }"><span class="cell-title">{{ row.title }}</span></template>
      <template #cell-url="{ row }">
        <span v-if="row.articleUrl" class="cell-sub" :title="row.articleUrl">{{ row.articleUrl }}</span>
        <span v-else class="cell-sub muted">未配置链接</span>
      </template>
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

    <FormDialog :show="showModal" :title="editingId !== null ? '编辑活动' : '新增活动'" :width="560" confirm-text="保存" @close="showModal = false" :on-confirm="submit">
      <div class="bf-form">
        <div class="field"><label>标题 <span class="required">*</span></label>
          <input v-model="form.title" placeholder="如：开学季食堂焕新：全新菜单抢先看" />
          <p v-if="formErrors.title" class="field-error">{{ formErrors.title }}</p>
        </div>
        <div class="field"><label>摘要（卡片副文案）</label>
          <textarea v-model="form.description" rows="2" placeholder="小程序活动卡片的副标题文案" />
        </div>
        <div class="field"><label>封面图链接</label>
          <input v-model="form.image" placeholder="https://...（公众号文章封面，可留空）" />
          <p v-if="formErrors.image" class="field-error">{{ formErrors.image }}</p>
        </div>
        <div class="field"><label>公众号文章链接</label>
          <input v-model="form.articleUrl" placeholder="https://mp.weixin.qq.com/s/..." />
          <p v-if="formErrors.articleUrl" class="field-error">{{ formErrors.articleUrl }}</p>
          <p class="field-tip">小程序点击活动卡片将用 web-view 打开该公众号文章；链接必须是 https:// 开头。</p>
        </div>
        <div class="bf-row">
          <div class="field flex-1"><label>排序（越小越靠前）</label>
            <input v-model.number="form.sortOrder" type="number" min="0" />
          </div>
          <div class="field flex-1"><label>状态</label>
            <select v-model="form.status">
              <option value="enabled">展示</option>
              <option value="disabled">停用</option>
            </select>
          </div>
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
.field-tip { font-size: var(--font-xs); color: var(--text-muted); margin-top: var(--space-1); line-height: 1.5; }
.cell-title { font-weight: var(--weight-semibold); color: var(--text-primary); }
.cell-sub { font-size: var(--font-sm); color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 320px; display: inline-block; vertical-align: middle; }
.cell-sub.muted { color: var(--text-muted); }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--text-light); }
</style>
