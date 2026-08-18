<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import DataTable from '@/components/DataTable.vue'
import DishFormDialog from '@/components/DishFormDialog.vue'
import { Plus, Star, Delete } from '@element-plus/icons-vue'

const store = useAdminStore()
const toast = useToastStore()
const confirm = useConfirmStore()

const searchQuery = ref('')

const statusFilter = ref<string>('')
const typeFilter = ref<string>('')

const rows = computed(() => {
  let list = store.dishes
  if (statusFilter.value) list = list.filter(r => r.status === statusFilter.value)
  if (typeFilter.value === 'discount') list = list.filter(r => !!r.promoPrice)
  else if (typeFilter.value === 'normal') list = list.filter(r => !r.promoPrice)
  const q = searchQuery.value.trim().toLowerCase()
  if (q) list = list.filter(r => (r.name || '').toLowerCase().includes(q))
  return list
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '在售', value: 'active' },
  { label: '已下架', value: 'inactive' },
]
const typeOptions = [
  { label: '全部类型', value: '' },
  { label: '折扣菜品', value: 'discount' },
  { label: '常规菜品', value: 'normal' },
]

function dishImage(row: any): string {
  return (row.image || '').split('|||')[0] || ''
}
function stallName(stallId: number | bigint): string {
  const s = store.stalls.find(s => Number(s.id) === Number(stallId))
  return s?.name || `档口${stallId}`
}
// ===== 菜品新增/编辑（弹窗直达，不再跳档口详情） =====
const dishModal = ref(false)
const editingDishId = ref<number | null>(null)

const dishStallOptions = computed(() => {
  return store.stalls.map(s => {
    const c = store.canteens.find(c => Number(c.id) === Number(s.canteen_id))
    return { label: c ? `${s.name}（${c.name}）` : s.name, value: Number(s.id) }
  })
})

function openAddDish() {
  editingDishId.value = null
  dishModal.value = true
}
function openEditDish(row: any) {
  editingDishId.value = Number(row.id)
  dishModal.value = true
}
function onDishSaved() {
  editingDishId.value = null
}

async function handleDelete(row: any) {
  if (!await confirm.confirm(`确定删除菜品「${row.name}」？删除后不可恢复。`)) return
  try {
    await store.deleteDish(Number(row.id))
    toast.success('菜品已删除')
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  }
}

function formatPrice(row: any): string {
  if (row.promoPrice) return `¥${row.promoPrice.toFixed(2)}`
  return `¥${row.price}`
}

// ===== 行内状态快捷切换（上架/下架，无需进弹窗） =====
const switchId = ref<number | null>(null)
async function toggleStatus(row: any, active: boolean) {
  const next = active ? 'active' : 'inactive'
  switchId.value = Number(row.id)
  try {
    await store.updateDish(Number(row.id), { status: next })
    toast.success(`「${row.name}」已${active ? '上架' : '下架'}`)
  } catch (e: any) {
    toast.error(e.message || '状态更新失败')
  } finally {
    switchId.value = null
  }
}

// ===== 批量上架/下架/删除 =====
const selectedIds = ref<number[]>([])

async function batchSetStatus(status: 'active' | 'inactive') {
  if (!selectedIds.value.length) return
  const label = status === 'active' ? '上架' : '下架'
  if (!await confirm.confirm(`确定批量${label} ${selectedIds.value.length} 个菜品？`)) return
  try {
    for (const id of selectedIds.value) await store.updateDish(id, { status })
    toast.success(`已批量${label} ${selectedIds.value.length} 个菜品`)
    selectedIds.value = []
  } catch (e: any) {
    toast.error(e.message || `批量${label}失败`)
  }
}

async function batchDelete() {
  if (!selectedIds.value.length) return
  if (!await confirm.confirm(`确定批量删除 ${selectedIds.value.length} 个菜品？删除后不可恢复。`)) return
  try {
    for (const id of selectedIds.value) await store.deleteDish(id)
    toast.success(`已删除 ${selectedIds.value.length} 个菜品`)
    selectedIds.value = []
  } catch (e: any) {
    toast.error(e.message || '批量删除失败')
  }
}
</script>

<template>
    <FilterBar v-model="searchQuery">
      <template #default>
        <FilterSelect v-model="statusFilter" label="状态" :options="statusOptions" :width="150" />
        <FilterSelect v-model="typeFilter" label="类型" :options="typeOptions" :width="150" />
      </template>
      <template #actions>
        <template v-if="selectedIds.length">
          <button class="btn-secondary" v-press type="button" @click="batchSetStatus('active')">批量上架</button>
          <button class="btn-secondary" v-press type="button" @click="batchSetStatus('inactive')">批量下架（{{ selectedIds.length }}）</button>
          <button class="btn-danger" v-press type="button" @click="batchDelete">批量删除</button>
        </template>
        <button class="btn-primary" v-press @click="openAddDish">
          <el-icon class="btn-plus-icon"><Plus /></el-icon>新增菜品
        </button>
      </template>
    </FilterBar>

    <DataTable
      selectable
      v-model:selectedIds="selectedIds"
      :columns="[
        { prop: 'image', label: '图片', width: '72px' },
        { prop: 'name', label: '菜品名称', sortable: true },
        { prop: 'stall', label: '所属档口' },
        { prop: 'price', label: '价格', width: '120px', align: 'center', sortable: true },
        { prop: 'rating', label: '评分', width: '80px', align: 'center', sortable: true },
        { prop: 'status', label: '状态', width: '110px', align: 'center' },

      ]"
      :rows="rows"
      empty-text="暂无菜品">
      <template #cell-image="{ row }">
        <img v-if="dishImage(row)" :src="dishImage(row)" :alt="row.name" class="cell-thumb" loading="lazy" decoding="async" />
        <span v-else class="cell-thumb cell-thumb-empty">图</span>
      </template>
      <template #cell-name="{ row }">
        <span class="cell-title" :title="row.name">{{ row.name }}</span>
        <span v-if="row.promoPrice" class="promo-flag">折扣</span>
      </template>
      <template #cell-stall="{ row }">
        <span class="cell-sub" :title="stallName(row.stall_id)">{{ stallName(row.stall_id) }}</span>
      </template>
      <template #cell-price="{ row }">
        <span class="price-cell" :class="{ promo: !!row.promoPrice }">{{ formatPrice(row) }}</span>
        <span v-if="row.promoPrice && row.originalPrice" class="origin">¥{{ row.originalPrice.toFixed(2) }}</span>
      </template>
      <template #cell-rating="{ row }">
        <span v-if="row.avg_rating" class="rating"><el-icon class="star"><Star /></el-icon>{{ Number(row.avg_rating).toFixed(1) }}</span>
        <span v-else class="text-muted">—</span>
      </template>
      <template #cell-status="{ row }">
        <div class="status-cell">
          <el-switch
            :model-value="row.status === 'active'"
            :loading="switchId === Number(row.id)"
            :disabled="switchId === Number(row.id)"
            @change="(v: any) => toggleStatus(row, !!v)"
          />
          <span class="status-text" :class="row.status === 'active' ? 'on' : 'off'">{{ row.status === 'active' ? '在售' : '已下架' }}</span>
        </div>
      </template>
      <template #actions="{ row }">
        <button class="link" v-press @click="openEditDish(row)">编辑</button>
        <button class="link danger" v-press @click="handleDelete(row)">
          <el-icon class="act-ico"><Delete /></el-icon>删除
        </button>
      </template>
    </DataTable>

    <DishFormDialog
      :show="dishModal"
      :editing-id="editingDishId"
      :default-stall-id="null"
      :stall-options="dishStallOptions"
      @close="dishModal = false"
      @saved="onDishSaved"
    />
</template>

<style scoped>
.cell-thumb { width: 52px; height: 40px; border-radius: var(--radius-sm); object-fit: cover; display: inline-block; vertical-align: middle; background: var(--bg-soft); }
.cell-thumb-empty { display: inline-flex; align-items: center; justify-content: center; font-size: var(--font-xs); color: var(--text-light); }
.cell-title { font-weight: var(--weight-semibold); color: var(--text-primary); }
.cell-sub { font-size: var(--font-sm); color: var(--text-secondary); }
.promo-flag { margin-left: var(--space-2); background: var(--color-error); color: var(--text-white); font-size: var(--font-xs); padding: 0 var(--space-2); border-radius: var(--radius-sm); vertical-align: 1px; }
.price-cell { color: var(--color-price); font-weight: var(--weight-bold); }
.price-cell.promo { color: var(--color-error); }
.origin { color: var(--text-light); text-decoration: line-through; font-size: var(--font-xs); margin-left: var(--space-1); }
.rating { display: inline-flex; align-items: center; gap: 2px; font-size: var(--font-sm); color: var(--color-star); font-weight: var(--weight-medium); }
.star { width: 13px; height: 13px; }
.text-muted { color: var(--text-light); }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }
/* 行内状态开关 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--text-light); }
/* .act-ico 已收敛至 shared.css 公共类 */
</style>
