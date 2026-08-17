<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import FormDialog from '@/components/FormDialog.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import DataTable from '@/components/DataTable.vue'
import { Plus, House, Delete } from '@element-plus/icons-vue'

const router = useRouter()
const store = useAdminStore()
const toast = useToastStore()
const confirm = useConfirmStore()

const searchQuery = ref('')

const showModal = ref(false)
const editingId = ref<number | null>(null)
const statusFilter = ref<string>('')

const filtered = computed(() => {
  // 按创建顺序展示（无需手动排序）
  let list = [...store.canteens].sort((a, b) => Number(a.id) - Number(b.id))
  if (statusFilter.value) list = list.filter(c => (c.status || 'active') === statusFilter.value)
  const q = searchQuery.value.trim().toLowerCase()
  if (q) list = list.filter(c => c.name.toLowerCase().includes(q) || (c.location || '').toLowerCase().includes(q))
  return list
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '上架', value: 'active' },
  { label: '下架', value: 'inactive' },
]

const form = ref({ name: '', location: '', description: '', image: '', sort_order: 0, status: 'active' as 'active' | 'inactive' })
const formErrors = ref<Record<string, string>>({})

function getFirstImage(img: string): string {
  return img.split('|||')[0] || img
}
function stallCount(canteenId: number | bigint): number {
  return store.stalls.filter(s => Number(s.canteen_id) === Number(canteenId)).length
}
function dishCount(canteenId: number | bigint): number {
  const stallIds = new Set(store.stalls.filter(s => Number(s.canteen_id) === Number(canteenId)).map(s => Number(s.id)))
  return store.dishes.filter(d => stallIds.has(Number(d.stall_id))).length
}

function validate() {
  const errs: Record<string, string> = {}
  if (!form.value.name.trim()) errs.name = '食堂名称不能为空'
  if (!form.value.location.trim()) errs.location = '位置不能为空'
  formErrors.value = errs
  return Object.keys(errs).length === 0
}

function openAdd() {
  editingId.value = null
  form.value = { name: '', location: '', description: '', image: '', sort_order: 0, status: 'active' }
  formErrors.value = {}
  showModal.value = true
}

function openEdit(id: number) {
  const c = store.canteens.find(x => Number(x.id) === id)
  if (!c) return
  editingId.value = id
  form.value = {
    name: c.name,
    location: c.location || '',
    description: c.description || '',
    image: c.image || '',
    sort_order: c.sort_order,
    status: c.status as 'active' | 'inactive',
  }
  formErrors.value = {}
  showModal.value = true
}

function handleSubmit() {
  if (!validate()) return
  try {
    if (editingId.value !== null) {
      store.updateCanteen(editingId.value, { ...form.value })
      toast.success('食堂已更新')
    } else {
      store.addCanteen({ ...form.value })
      toast.success('食堂已添加')
    }
    showModal.value = false
  } catch (e: any) {
    toast.error(e.message || '保存失败')
  }
}

async function handleDelete(id: number) {
  const c = store.canteens.find(x => Number(x.id) === id)
  if (!await confirm.confirm(`确定删除食堂「${c?.name || ''}」？`)) return
  try {
    await store.deleteCanteen(id)
    toast.success('食堂已删除')
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  }
}

function enterCanteen(id: number) { router.push(`/dashboard/canteens/${id}`) }
// 整行点击直达食堂详情（操作列已 stop 冒泡）
function onRowClick(row: any) { enterCanteen(Number(row.id)) }

// ===== 行内状态快捷切换（上架/下架） =====
const switchId = ref<number | null>(null)
async function toggleStatus(row: any, active: boolean) {
  const next = active ? 'active' : 'inactive'
  switchId.value = Number(row.id)
  try {
    await store.updateCanteen(Number(row.id), { status: next })
    toast.success(`食堂「${row.name}」已${active ? '上架' : '下架'}`)
  } catch (e: any) {
    toast.error(e.message || '状态更新失败')
  } finally {
    switchId.value = null
  }
}

// ===== 批量上架/下架 =====
const selectedIds = ref<number[]>([])
async function batchSetStatus(status: 'active' | 'inactive') {
  if (!selectedIds.value.length) return
  const label = status === 'active' ? '上架' : '下架'
  if (!await confirm.confirm(`确定批量${label} ${selectedIds.value.length} 个食堂？`)) return
  try {
    for (const id of selectedIds.value) await store.updateCanteen(id, { status })
    toast.success(`已批量${label} ${selectedIds.value.length} 个食堂`)
    selectedIds.value = []
  } catch (e: any) {
    toast.error(e.message || `批量${label}失败`)
  }
}
</script>

<template>
    <FilterBar v-model="searchQuery">
      <template #default>
        <FilterSelect v-model="statusFilter" label="状态" :options="statusOptions" :width="150" />
      </template>
      <template #actions>
        <template v-if="selectedIds.length">
          <button class="btn-secondary" v-press type="button" @click="batchSetStatus('active')">批量上架</button>
          <button class="btn-secondary" v-press type="button" @click="batchSetStatus('inactive')">批量下架（{{ selectedIds.length }}）</button>
        </template>
        <button class="btn-primary" v-press @click="openAdd"><el-icon class="btn-plus-icon"><Plus /></el-icon>新增食堂</button>
      </template>
    </FilterBar>

    <DataTable
      selectable
      v-model:selectedIds="selectedIds"
      :columns="[
        { prop: 'image', label: '图片', width: '72px' },
        { prop: 'name', label: '食堂名称', sortable: true },
        { prop: 'location', label: '位置', width: '160px' },
        { prop: 'stats', label: '档口 / 菜品', width: '110px', align: 'center', sortable: true, sortValue: (row) => stallCount(row.id) * 1000 + dishCount(row.id) },
        { prop: 'status', label: '状态', width: '110px', align: 'center' },

      ]"
      :rows="filtered"
      empty-text="暂无食堂，点击下方按钮添加"
      :empty-icon="House"
      row-clickable
      @row-click="onRowClick"
    >
      <template #emptyAction>
        <button class="btn-primary" v-press type="button" @click="openAdd"><el-icon class="btn-plus-icon"><Plus /></el-icon>新增食堂</button>
      </template>
      <template #cell-image="{ row }">
        <img v-if="getFirstImage(row.image)" :src="getFirstImage(row.image)" :alt="row.name" class="cell-thumb" loading="lazy" decoding="async" />
        <span v-else class="cell-thumb cell-thumb-empty">图</span>
      </template>
      <template #cell-name="{ row }"><span class="cell-title">{{ row.name }}</span></template>
      <template #cell-location="{ row }">{{ row.location || '—' }}</template>
      <template #cell-stats="{ row }">{{ stallCount(row.id) }} / {{ dishCount(row.id) }}</template>
      <template #cell-status="{ row }">
        <div class="status-cell">
          <el-switch
            :model-value="(row.status || 'active') === 'active'"
            :loading="switchId === Number(row.id)"
            :disabled="switchId === Number(row.id)"
            @change="(v: any) => toggleStatus(row, !!v)"
          />
          <span class="status-text" :class="(row.status || 'active') === 'active' ? 'on' : 'off'">{{ (row.status || 'active') === 'active' ? '上架' : '下架' }}</span>
        </div>
      </template>
      <template #actions="{ row }">
        <button class="link" v-press @click="enterCanteen(Number(row.id))">档口</button>
        <button class="link" v-press @click="openEdit(Number(row.id))">编辑</button>
        <button class="link danger" v-press @click="handleDelete(Number(row.id))">
          <el-icon class="act-ico"><Delete /></el-icon>删除
        </button>
      </template>
    </DataTable>

    <FormDialog
      :show="showModal"
      :title="editingId !== null ? '编辑食堂' : '新增食堂'"
      :width="580"
      confirm-text="保存"
      @close="showModal = false"
      :on-confirm="handleSubmit"
    >
      <div class="modal-form">
        <div class="modal-row">
          <div class="field flex-1"><label>食堂名称 <span class="required">*</span></label><input v-model="form.name" placeholder="输入食堂名称" /><p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p></div>
          <div class="field flex-1"><label>位置 <span class="required">*</span></label><input v-model="form.location" placeholder="输入食堂位置" /><p v-if="formErrors.location" class="field-error">{{ formErrors.location }}</p></div>
        </div>
        <div class="field"><label>描述</label><textarea v-model="form.description" rows="2" placeholder="输入食堂描述"></textarea></div>
        <div class="modal-row">
          <div class="field flex-1"><label>排序</label><input v-model.number="form.sort_order" type="number" min="0" /></div>
          <div class="field flex-1"><label>状态</label>
            <select v-model="form.status">
              <option value="active">上架</option>
              <option value="inactive">下架</option>
            </select>
          </div>
        </div>
        <div class="field">
          <label>图片 <span class="text-muted">（至多 3 张）</span></label>
          <ImageUpload v-model="form.image" :max="3" />
        </div>
      </div>
    </FormDialog>
</template>

<style scoped>
.cell-thumb { width: 52px; height: 40px; border-radius: var(--radius-sm); object-fit: cover; display: inline-block; vertical-align: middle; background: var(--bg-soft); }
.cell-thumb-empty { display: inline-flex; align-items: center; justify-content: center; font-size: var(--font-xs); color: var(--text-light); }
.cell-title { font-weight: var(--weight-semibold); color: var(--text-primary); }
/* 行内状态开关 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--text-light); }
.modal-form { display: flex; flex-direction: column; gap: var(--space-3); }
.modal-row { display: flex; gap: var(--space-3); }
.flex-1 { flex: 1; }
.text-muted { color: var(--text-light); font-size: var(--font-xs); font-weight: var(--weight-regular); }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }
/* .act-ico 已收敛至 shared.css 公共类 */
</style>
