<script setup lang="ts">
/**
 * CategoryManage：首页品类滚轮分类管理（首页配置 → 分类）。
 * 增删改 / 启停 / 排序，小程序首页品类滚轮即时反映。
 */
import { ref, computed, onMounted } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import FilterBar from '@/components/layout/FilterBar.vue'
import DataTable from '@/components/DataTable.vue'
import FormDialog from '@/components/FormDialog.vue'
import { getAll, create, update, toggleStatus, remove, type CategoryItem } from '@/api/category'
import { Plus, Delete } from '@element-plus/icons-vue'

const toast = useToastStore()
const confirm = useConfirmStore()

const loading = ref(false)
const error = ref('')
const rows = ref<CategoryItem[]>([])

const searchQuery = ref('')
const filtered = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter(c => (c.name || '').toLowerCase().includes(q) || (c.code || '').toLowerCase().includes(q))
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await getAll()
  } catch (e: any) {
    error.value = e.message || '加载分类失败'
  } finally {
    loading.value = false
  }
}
onMounted(load)

// ===== 增改弹窗 =====
const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ code: '', name: '', sortOrder: 0 })
const formErrors = ref<Record<string, string>>({})

const CODE_RE = /^[a-z][a-z0-9_]{1,30}$/

function openAdd() {
  editingId.value = null
  form.value = { code: '', name: '', sortOrder: rows.value.length + 1 }
  formErrors.value = {}
  showModal.value = true
}
function openEdit(id: number) {
  const c = rows.value.find(x => x.id === id)
  if (!c) return
  editingId.value = id
  form.value = { code: c.code || '', name: c.name, sortOrder: c.sortOrder }
  formErrors.value = {}
  showModal.value = true
}
async function handleSubmit() {
  const e: Record<string, string> = {}
  if (!form.value.code.trim()) e.code = '请输入品类 code'
  else if (!CODE_RE.test(form.value.code.trim())) e.code = 'code 需为小写字母开头，仅含小写字母/数字/下划线'
  if (!form.value.name.trim()) e.name = '请输入品类名称'
  formErrors.value = e
  if (Object.keys(e).length) return
  try {
    if (editingId.value !== null) {
      await update(editingId.value, {
        code: form.value.code.trim(),
        name: form.value.name.trim(),
        sortOrder: Number(form.value.sortOrder) || 0,
      })
      toast.success('品类已更新')
    } else {
      await create({
        code: form.value.code.trim(),
        name: form.value.name.trim(),
        sortOrder: Number(form.value.sortOrder) || 0,
      })
      toast.success('品类已添加')
    }
    showModal.value = false
    await load()
  } catch (err: any) {
    toast.error(err.message || '保存失败')
  }
}
// ===== 行内状态快捷切换（启用/停用） =====
const switchId = ref<number | null>(null)
async function toggleStatusRow(c: CategoryItem, active: boolean) {
  const next = active ? 'enabled' : 'disabled'
  if (c.status === next) return
  switchId.value = c.id
  try {
    await toggleStatus(c.id, next)
    toast.success(next === 'enabled' ? '品类已启用' : '品类已停用')
    await load()
  } catch (err: any) {
    toast.error(err.message || '操作失败')
  } finally {
    switchId.value = null
  }
}
async function handleDelete(c: CategoryItem) {
  if (!await confirm.confirm(`确定删除品类「${c.name}」？`)) return
  try {
    await remove(c.id)
    toast.success('品类已删除')
    await load()
  } catch (err: any) {
    toast.error(err.message || '删除失败')
  }
}
</script>

<template>
  <FilterBar v-model="searchQuery">
    <template #actions>
      <button class="btn-primary" v-press @click="openAdd"><el-icon class="btn-plus-icon"><Plus /></el-icon>新增品类</button>
    </template>
  </FilterBar>

  <DataTable
    :columns="[
      { prop: 'code', label: 'code', width: '140px' },
      { prop: 'name', label: '品类名称', sortable: true },
      { prop: 'sort', label: '排序', width: '80px', align: 'center', sortable: true, sortValue: (row) => row.sortOrder },
      { prop: 'status', label: '状态', width: '110px', align: 'center' },
    ]"
    :rows="filtered"
    :loading="loading"
    :error="error"
    empty-text="暂无品类"
  >
    <template #cell-code="{ row }"><code class="cell-code">{{ row.code }}</code></template>
    <template #cell-name="{ row }"><span class="cell-title">{{ row.name }}</span></template>
    <template #cell-sort="{ row }">{{ row.sortOrder }}</template>
    <template #cell-status="{ row }">
      <div class="status-cell">
        <el-switch
          :model-value="row.status === 'enabled'"
          :loading="switchId === row.id"
          :disabled="switchId === row.id"
          @change="(v: any) => toggleStatusRow(row, !!v)"
        />
        <span class="status-text" :class="row.status === 'enabled' ? 'on' : 'off'">{{ row.status === 'enabled' ? '启用' : '停用' }}</span>
      </div>
    </template>
    <template #actions="{ row }">
      <button class="link" v-press @click="openEdit(row.id)">编辑</button>
      <button class="link danger" v-press @click="handleDelete(row)">
        <el-icon class="act-ico"><Delete /></el-icon>删除
      </button>
    </template>
  </DataTable>

  <FormDialog :show="showModal" :title="editingId !== null ? '编辑品类' : '新增品类'" confirm-text="保存" :on-confirm="handleSubmit" @close="showModal = false">
    <div class="modal-form">
      <div class="field">
        <label>品类 code <span class="required">*</span></label>
        <input v-model="form.code" placeholder="小写字母/数字/下划线，如 noodle / rice / home" />
        <p v-if="formErrors.code" class="field-error">{{ formErrors.code }}</p>
      </div>
      <div class="field">
        <label>品类名称 <span class="required">*</span></label>
        <input v-model="form.name" placeholder="如：面食 / 盖饭 / 家常菜" />
        <p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p>
      </div>
      <div class="field">
        <label>排序（越小越靠前）</label>
        <input v-model.number="form.sortOrder" type="number" />
      </div>
    </div>
  </FormDialog>
</template>

<style scoped>
.cell-title { font-weight: var(--weight-semibold); color: var(--text-primary); }
.cell-code { font-family: var(--font-mono, ui-monospace, SFMono-Regular, Consolas, monospace); font-size: var(--font-xs); color: var(--text-muted); background: var(--bg-subtle); padding: 2px 6px; border-radius: 4px; }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }
/* .act-ico 已收敛至 shared.css 公共类 */
/* 行内状态开关 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--text-light); }
</style>
