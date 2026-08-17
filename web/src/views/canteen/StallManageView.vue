<script setup lang="ts">
/**
 * StallManageView：全局档口管理（信息管理 → 业务信息分组）。
 * 与食堂详情页内档口管理互补：这里是跨食堂全局视角（按食堂筛选 + 搜索），增改删弹窗直达。
 */
import { ref, computed } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import DataTable from '@/components/DataTable.vue'
import FormDialog from '@/components/FormDialog.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import { Plus, Delete } from '@element-plus/icons-vue'

const store = useAdminStore()
const toast = useToastStore()
const confirm = useConfirmStore()

const searchQuery = ref('')
const canteenFilter = ref('')

const canteenOptions = computed(() =>
  store.canteens.map(c => ({ label: c.name, value: Number(c.id) })),
)
const canteenNameOf = (id: number | bigint) => {
  const c = store.canteens.find(x => Number(x.id) === Number(id))
  return c?.name || `食堂${id}`
}

const filtered = computed(() => {
  // 按创建顺序展示（无需手动排序）
  let list = [...store.stalls].sort((a, b) => Number(a.id) - Number(b.id))
  if (canteenFilter.value) list = list.filter(s => String(s.canteen_id) === canteenFilter.value)
  const q = searchQuery.value.trim().toLowerCase()
  if (q) list = list.filter(s => (s.name || '').toLowerCase().includes(q) || (s.description || '').toLowerCase().includes(q))
  return list
})

function dishCount(stallId: number | bigint): number {
  return store.dishes.filter(d => Number(d.stall_id) === Number(stallId)).length
}

// ===== 新增/编辑弹窗 =====
const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ name: '', description: '', image: '', canteen_id: '', status: 'active' })
const formErrors = ref<Record<string, string>>({})

function openAdd() {
  editingId.value = null
  form.value = { name: '', description: '', image: '', canteen_id: canteenFilter.value || '', status: 'active' }
  formErrors.value = {}
  showModal.value = true
}
function openEdit(id: number) {
  const s = store.stalls.find(x => Number(x.id) === id)
  if (!s) return
  editingId.value = id
  form.value = {
    name: s.name,
    description: s.description || '',
    image: s.image || '',
    canteen_id: String(s.canteen_id),
    status: s.status as 'active' | 'inactive',
  }
  formErrors.value = {}
  showModal.value = true
}
function validate(): boolean {
  const e: Record<string, string> = {}
  if (!form.value.name.trim()) e.name = '请输入档口名称'
  if (!form.value.canteen_id) e.canteen_id = '请选择所属食堂'
  formErrors.value = e
  return Object.keys(e).length === 0
}
async function handleSubmit() {
  if (!validate()) return
  try {
    if (editingId.value !== null) {
      await store.updateStall(editingId.value, {
        name: form.value.name.trim(),
        description: form.value.description.trim(),
        image: form.value.image,
        status: form.value.status,
      })
      toast.success('档口已更新')
    } else {
      await store.addStall({
        canteen_id: Number(form.value.canteen_id) as unknown as bigint,
        name: form.value.name.trim(),
        description: form.value.description.trim(),
        image: form.value.image,
        status: form.value.status,
        sort_order: 0,
      })
      toast.success('档口已添加')
    }
    showModal.value = false
  } catch (e: any) {
    toast.error(e.message || '保存失败')
  }
}
async function handleDelete(id: number) {
  const s = store.stalls.find(x => Number(x.id) === id)
  if (!await confirm.confirm(`确定删除档口「${s?.name || ''}」？其下菜品也将被删除。`)) return
  try {
    await store.deleteStall(id)
    toast.success('档口已删除')
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  }
}

// ===== 行内状态快捷切换（营业/关闭） =====
const switchId = ref<number | null>(null)
async function toggleStatus(row: any, active: boolean) {
  const next = active ? 'active' : 'inactive'
  switchId.value = Number(row.id)
  try {
    await store.updateStall(Number(row.id), { status: next })
    toast.success(`档口「${row.name}」已${active ? '营业' : '关闭'}`)
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
  const label = status === 'active' ? '营业' : '关闭'
  if (!await confirm.confirm(`确定批量${label} ${selectedIds.value.length} 个档口？`)) return
  try {
    for (const id of selectedIds.value) await store.updateStall(id, { status })
    toast.success(`已批量${label} ${selectedIds.value.length} 个档口`)
    selectedIds.value = []
  } catch (e: any) {
    toast.error(e.message || `批量${label}失败`)
  }
}
</script>

<template>
  <FilterBar v-model="searchQuery">
    <template #default>
      <FilterSelect v-model="canteenFilter" label="食堂" :options="[{ label: '全部食堂', value: '' }, ...canteenOptions.map(c => ({ label: c.label, value: String(c.value) }))]" :width="180" :clearable="false" />
    </template>
    <template #actions>
      <template v-if="selectedIds.length">
        <button class="btn-secondary" v-press type="button" @click="batchSetStatus('active')">批量营业</button>
        <button class="btn-secondary" v-press type="button" @click="batchSetStatus('inactive')">批量关闭（{{ selectedIds.length }}）</button>
      </template>
      <button class="btn-primary" v-press @click="openAdd"><el-icon class="btn-plus-icon"><Plus /></el-icon>新增档口</button>
    </template>
  </FilterBar>

  <DataTable
    selectable
    v-model:selectedIds="selectedIds"
    :columns="[
      { prop: 'image', label: '图片', width: '72px' },
      { prop: 'name', label: '档口名称', sortable: true },
      { prop: 'canteen', label: '所属食堂' },
      { prop: 'desc', label: '描述', ellipsis: true },
      { prop: 'stats', label: '菜品数', width: '90px', align: 'center', sortable: true, sortValue: (row) => dishCount(row.id) },
      { prop: 'status', label: '状态', width: '110px', align: 'center' },

    ]"
    :rows="filtered"
    empty-text="暂无档口"
  >
    <template #cell-image="{ row }">
      <img v-if="row.image" :src="row.image.split('|||')[0]" :alt="row.name" class="cell-thumb" loading="lazy" decoding="async" />
      <span v-else class="cell-thumb cell-thumb-empty">图</span>
    </template>
    <template #cell-name="{ row }"><span class="cell-title">{{ row.name }}</span></template>
    <template #cell-canteen="{ row }"><span class="cell-sub">{{ canteenNameOf(row.canteen_id) }}</span></template>
    <template #cell-desc="{ row }"><span class="cell-sub">{{ row.description || '—' }}</span></template>
    <template #cell-stats="{ row }">{{ dishCount(row.id) }}</template>
    <template #cell-status="{ row }">
      <div class="status-cell">
        <el-switch
          :model-value="(row.status || 'active') === 'active'"
          :loading="switchId === Number(row.id)"
          :disabled="switchId === Number(row.id)"
          @change="(v: any) => toggleStatus(row, !!v)"
        />
        <span class="status-text" :class="(row.status || 'active') === 'active' ? 'on' : 'off'">{{ (row.status || 'active') === 'active' ? '营业中' : '已关闭' }}</span>
      </div>
    </template>
    <template #actions="{ row }">
      <button class="link" v-press @click="openEdit(Number(row.id))">编辑</button>
      <button class="link danger" v-press @click="handleDelete(Number(row.id))">
        <el-icon class="act-ico"><Delete /></el-icon>删除
      </button>
    </template>
  </DataTable>

  <FormDialog :show="showModal" :title="editingId !== null ? '编辑档口' : '新增档口'" confirm-text="保存" @close="showModal = false" :on-confirm="handleSubmit">
    <div class="modal-form">
      <div class="field">
        <label>档口名称 <span class="required">*</span></label>
        <input v-model="form.name" placeholder="如：麻辣香锅" />
        <p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p>
      </div>
      <div class="field">
        <label>所属食堂 <span class="required">*</span></label>
        <select v-model="form.canteen_id">
          <option value="" disabled>请选择食堂</option>
          <option v-for="c in store.canteens" :key="Number(c.id)" :value="String(c.id)">{{ c.name }}</option>
        </select>
        <p v-if="formErrors.canteen_id" class="field-error">{{ formErrors.canteen_id }}</p>
      </div>
      <div class="field"><label>描述</label><textarea v-model="form.description" rows="3" placeholder="主营菜品、特色等"></textarea></div>
      <div class="field">
        <label>图片</label>
        <ImageUpload v-model="form.image" :max="3" />
      </div>
      <div class="field">
        <label>状态</label>
        <select v-model="form.status">
          <option value="active">营业中</option>
          <option value="inactive">已关闭</option>
        </select>
      </div>
    </div>
  </FormDialog>
</template>

<style scoped>
.cell-thumb { width: 52px; height: 40px; border-radius: var(--radius-sm); object-fit: cover; display: inline-block; vertical-align: middle; background: var(--bg-soft); }
.cell-thumb-empty { display: inline-flex; align-items: center; justify-content: center; font-size: var(--font-xs); color: var(--text-light); }
.cell-title { font-weight: var(--weight-semibold); color: var(--text-primary); }
.cell-sub { font-size: var(--font-sm); color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 240px; display: inline-block; vertical-align: middle; }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }
/* .act-ico 已收敛至 shared.css 公共类 */
/* 行内状态开关 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--text-light); }
</style>
