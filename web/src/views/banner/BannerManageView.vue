<script setup lang="ts">
import { ref, computed } from 'vue'
import { useBannerStore } from '@/stores/bannerStore'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import FormDialog from '@/components/FormDialog.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import { Plus, CaretTop, CaretBottom, Delete } from '@element-plus/icons-vue'

const store = useBannerStore()
const admin = useAdminStore()
const toast = useToastStore()
const confirm = useConfirmStore()
const searchQuery = ref('')

const showModal = ref(false)
const editingId = ref<number | null>(null)
const dishes = computed(() => admin.dishes)
const statusFilter = ref<string>('')

const TARGET_TYPES: { value: 'DISH' | 'URL' | 'NONE'; label: string; hint: string }[] = [
  { value: 'DISH', label: '跳转菜品', hint: '需选择菜品' },
  { value: 'URL', label: '跳转链接', hint: '需填写外部链接' },
  { value: 'NONE', label: '不跳转', hint: '纯展示' },
]

const form = ref({
  title: '', image: '', target_type: 'DISH' as 'DISH' | 'URL' | 'NONE',
  target_id: '' as string | number, target_url: '', sort_order: 1, status: 'active' as 'active' | 'inactive',
})
const formErrors = ref<Record<string, string>>({})

const targetTypeOptions = TARGET_TYPES.map(t => ({ label: t.label, value: t.value }))
const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '轮播中', value: 'active' },
  { label: '已停用', value: 'inactive' },
]

const filtered = computed(() => {
  let list = store.sortedList
  if (statusFilter.value) list = list.filter(b => (b.status || 'active') === statusFilter.value)
  const q = searchQuery.value.trim().toLowerCase()
  if (q) list = list.filter(b => b.title.toLowerCase().includes(q))
  return list
})

function onTargetTypeChange() {
  form.value.target_id = ''
  form.value.target_url = ''
}

function validate() {
  const errs: Record<string, string> = {}
  if (!form.value.title.trim()) errs.title = 'Banner 标题不能为空'
  if (!form.value.image) errs.image = '请上传 Banner 图片'
  if (form.value.target_type === 'DISH') {
    if (!form.value.target_id) errs.target_id = '请选择关联菜品'
  }
  if (form.value.target_type === 'URL') {
    if (!form.value.target_url.trim()) errs.target_url = '请填写外部链接'
    else if (!/^https?:\/\//i.test(form.value.target_url.trim())) errs.target_url = '链接须以 http(s):// 开头'
  }
  formErrors.value = errs
  return Object.keys(errs).length === 0
}

function openAdd() {
  editingId.value = null
  form.value = { title: '', image: '', target_type: 'DISH', target_id: '', target_url: '', sort_order: store.maxSortOrder + 1, status: 'active' }
  formErrors.value = {}
  showModal.value = true
}
function openEdit(id: number) {
  const b = store.list.find(x => Number(x.id) === id)
  if (!b) return
  editingId.value = id
  form.value = {
    title: b.title, image: b.image || '',
    target_type: (b.target_type as any) || 'NONE',
    target_id: String(b.target_id ?? ''), target_url: b.target_url || '',
    sort_order: b.sort_order, status: b.status as 'active' | 'inactive',
  }
  formErrors.value = {}
  showModal.value = true
}

async function handleSubmit() {
  if (!validate()) return
  const payload: any = {
    title: form.value.title.trim(),
    image: form.value.image,
    target_type: form.value.target_type,
    sort_order: Number(form.value.sort_order) || 0,
    status: form.value.status,
  }
  if (form.value.target_type === 'DISH') {
    payload.target_id = Number(form.value.target_id)
  } else if (form.value.target_type === 'URL') {
    payload.target_url = form.value.target_url.trim()
  }
  try {
    if (editingId.value !== null) await store.update(editingId.value, payload)
    else await store.add(payload)
    toast.success(editingId.value !== null ? 'Banner 已更新' : 'Banner 已添加')
    showModal.value = false
  } catch (e: any) {
    toast.error(e.message || '保存失败')
  }
}

async function handleDelete(id: number) {
  if (!await confirm.confirm('确定删除该 Banner？')) return
  try { await store.remove(id); toast.success('Banner 已删除') } catch (e: any) { toast.error(e.message || '删除失败') }
}

async function moveOrder(id: number, dir: -1 | 1) {
  const list = store.sortedList
  const idx = list.findIndex(b => Number(b.id) === id)
  const cur = list[idx]
  const swap = list[idx + dir]
  if (!cur || !swap) return
  try {
    const a = Number(cur.sort_order)
    const b = Number(swap.sort_order)
    await store.update(Number(cur.id), { sort_order: b } as any)
    await store.update(Number(swap.id), { sort_order: a } as any)
  } catch (e: any) { toast.error(e.message || '排序调整失败') }
}

function targetText(b: any): string {
  switch (b.target_type) {
    case 'DISH': return '菜品 #' + b.target_id
    case 'URL': return (b.target_url || '').slice(0, 24)
    default: return '不跳转'
  }
}

// ===== 行内状态快捷切换（轮播/停用） =====
const switchId = ref<number | null>(null)
async function toggleStatusRow(b: any, active: boolean) {
  const next = active ? 'active' : 'inactive'
  switchId.value = Number(b.id)
  try {
    await store.update(Number(b.id), { status: next } as any)
    toast.success(`Banner「${b.title}」已${active ? '启用' : '停用'}`)
  } catch (e: any) {
    toast.error(e.message || '状态更新失败')
  } finally {
    switchId.value = null
  }
}
</script>

<template>
    <FilterBar v-model="searchQuery">
      <template #default>
        <FilterSelect v-model="statusFilter" label="状态" :options="statusOptions" :width="150" />
      </template>
      <template #actions>
        <button class="btn-primary" v-press @click="openAdd"><el-icon class="btn-plus-icon"><Plus /></el-icon>新增 Banner</button>
      </template>
    </FilterBar>

    <DataTable
      :columns="[
        { prop: 'image', label: '图片', width: '120px' },
        { prop: 'title', label: '标题', sortable: true },
        { prop: 'target', label: '跳转', ellipsis: true },
        { prop: 'sort', label: '排序', width: '80px', align: 'center', sortable: true, sortValue: (row: any) => row.sort_order },
        { prop: 'status', label: '状态', width: '110px', align: 'center' },

      ]"
      :rows="filtered"
      empty-text="暂无 Banner，点击上方按钮添加">
      <template #cell-image="{ row }">
        <img v-if="row.image" :src="row.image" :alt="row.title" class="cell-banner" />
        <span v-else class="cell-banner cell-banner-empty">图</span>
      </template>
      <template #cell-title="{ row }">
        <span class="cell-title">{{ row.title }}</span>
        <span class="tt-tag">{{ (TARGET_TYPES.find(t => t.value === row.target_type) || {}).label || row.target_type }}</span>
      </template>
      <template #cell-target="{ row }"><span class="cell-sub" :title="row.target_url || ''">{{ targetText(row) }}</span></template>
      <template #cell-sort="{ row }">{{ row.sort_order }}</template>
      <template #cell-status="{ row }">
        <div class="status-cell">
          <el-switch
            :model-value="(row.status || 'active') === 'active'"
            :loading="switchId === Number(row.id)"
            :disabled="switchId === Number(row.id)"
            @change="(v: any) => toggleStatusRow(row, !!v)"
          />
          <span class="status-text" :class="(row.status || 'active') === 'active' ? 'on' : 'off'">{{ (row.status || 'active') === 'active' ? '轮播中' : '已停用' }}</span>
        </div>
      </template>
      <template #actions="{ row }">
        <button class="link" v-press @click="moveOrder(Number(row.id), -1)"><el-icon class="sort-svg"><CaretTop /></el-icon>上移</button>
        <button class="link" v-press @click="moveOrder(Number(row.id), 1)"><el-icon class="sort-svg"><CaretBottom /></el-icon>下移</button>
        <button class="link" v-press @click="openEdit(Number(row.id))">编辑</button>
        <button class="link danger" v-press @click="handleDelete(Number(row.id))">
          <el-icon class="act-ico"><Delete /></el-icon>删除
        </button>
      </template>
    </DataTable>

    <FormDialog
      :show="showModal"
      :title="editingId !== null ? '编辑 Banner' : '新增 Banner'"
      :width="520"
      confirm-text="保存"
      @close="showModal = false"
      :on-confirm="handleSubmit"
    >
      <div class="modal-form">
        <div class="field"><label>标题 <span class="required">*</span></label>
          <input v-model="form.title" placeholder="如：开学季优惠" />
          <p v-if="formErrors.title" class="field-error">{{ formErrors.title }}</p>
        </div>
        <div class="field"><label>跳转类型 <span class="required">*</span></label>
          <div class="type-group">
            <button v-for="t in TARGET_TYPES" :key="t.value" type="button"
              class="type-opt" :class="{ on: form.target_type === t.value }" @click="onTargetTypeChange(); form.target_type = t.value">
              {{ t.label }}
            </button>
          </div>
          <p class="type-hint">{{ TARGET_TYPES.find(t => t.value === form.target_type)?.hint }}</p>
        </div>
        <div class="field" v-if="form.target_type === 'DISH'">
          <label>关联菜品 <span class="required">*</span></label>
          <FilterSelect v-model="form.target_id" :options="dishes.map(d => ({ label: d.name, value: Number(d.id) }))" placeholder="请选择菜品" :width="'100%'" />
          <p v-if="formErrors.target_id" class="field-error">{{ formErrors.target_id }}</p>
        </div>
        <div class="field" v-else-if="form.target_type === 'URL'">
          <label>外部链接 <span class="required">*</span></label>
          <input v-model="form.target_url" placeholder="https://..." />
          <p v-if="formErrors.target_url" class="field-error">{{ formErrors.target_url }}</p>
        </div>
        <div class="modal-row">
          <div class="field"><label>排序</label><input v-model.number="form.sort_order" type="number" min="0" /></div>
          <div class="field"><label>状态</label>
            <FilterSelect v-model="form.status" :options="statusOptions.filter(o => o.value !== '')" :width="'100%'" />
          </div>
        </div>
        <div class="field"><label>轮播图片 <span class="required">*</span></label>
          <ImageUpload v-model="form.image" :single="true" />
          <p v-if="formErrors.image" class="field-error">{{ formErrors.image }}</p>
        </div>
      </div>
    </FormDialog>
</template>

<style scoped>
.cell-banner { width: 96px; height: 40px; border-radius: var(--radius-sm); object-fit: cover; display: inline-block; vertical-align: middle; background: var(--bg-soft); }
.cell-banner-empty { display: inline-flex; align-items: center; justify-content: center; font-size: var(--font-xs); color: var(--text-light); }
.cell-title { font-weight: var(--weight-semibold); color: var(--text-primary); margin-right: var(--space-2); }
.cell-sub { font-size: var(--font-sm); color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 260px; display: inline-block; vertical-align: middle; }
.tt-tag { display: inline-block; padding: 0 var(--space-2); border-radius: var(--radius-sm); background: var(--bg-gray); color: var(--text-secondary); font-size: var(--font-xs); }
.sort-svg { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; }
/* .act-ico 已收敛至 shared.css 公共类 */
/* 行内状态开关 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--text-light); }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }

.modal-form { display: flex; flex-direction: column; gap: var(--space-3); }
.modal-row { display: flex; gap: var(--space-3); }
.modal-row .field { flex: 1; }

.type-group { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.type-opt { padding: var(--space-2) var(--space-4); border: 1px solid var(--border-strong); border-radius: var(--radius-pill); font-size: var(--font-sm); cursor: pointer; background: var(--bg-card); color: var(--text-secondary); transition: background 0.2s var(--ease-out), border-color 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 160ms var(--ease-out); }
.type-opt.on { background: var(--color-primary-bg); border-color: var(--color-primary); color: var(--color-primary); font-weight: var(--weight-medium); }
.type-opt:active { transform: scale(var(--press-scale)); }
.type-hint { font-size: var(--font-xs); color: var(--text-light); margin: var(--space-2) 0 0; }

@media (prefers-reduced-motion: reduce) {
  .banner-card { animation: none; }
}
</style>
