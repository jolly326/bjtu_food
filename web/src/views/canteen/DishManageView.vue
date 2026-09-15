<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { useDishStore } from '@/stores/dishStore'
import { useCanteenStore } from '@/stores/canteenStore'
import { useStallStore } from '@/stores/stallStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { dishApi, categoryApi } from '@/api'
import type { CategoryItem } from '@/api/category'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import DataTable from '@/components/DataTable.vue'
import DishFormDialog from '@/components/DishFormDialog.vue'
import { Plus, Star, Delete } from '@element-plus/icons-vue'

const store = useAdminStore()
const dishStore = useDishStore()
const canteenStore = useCanteenStore()
const stallStore = useStallStore()
const toast = useToastStore()
const confirm = useConfirmStore()
const router = useRouter()
const route = useRoute()

/**
 * 关键词初值来源：反馈详情「关联菜品」跳转携带 `?tab=dish&q=<菜名>`（FeedbackView.goDishEdit）。
 * 直接读取 route.query.q 作为初值，避免运营主路径在最后一跳断裂（管理员无需手敲菜名）。
 */
function queryKeyword(): string {
  const q = route.query.q
  return typeof q === 'string' ? q : ''
}
const searchQuery = ref(queryKeyword())

/**
 * 同组件内 query 变化响应：本页由 ContentManageView 以 v-if 承载，从反馈页二次跳转时
 * 若组件实例未重建则不会重跑 setup → 必须 watch route.query.q 才能重新筛选。
 * 仅在值确实变化时同步，避免用户手动修改关键词后被同值 query 回写覆盖。
 */
watch(
  () => route.query.q,
  (q) => {
    const next = typeof q === 'string' ? q : ''
    if (next !== searchQuery.value) searchQuery.value = next
  },
)

const statusFilter = ref<string>('')
const typeFilter = ref<string>('')
const categoryFilter = ref<string | number>('')

// ===== 食堂 / 档口 / 品类筛选（食堂与档口是菜品的「筛选条件」，非独立实体页） =====
const canteenFilter = ref<string | number>('')
const stallFilter = ref<string | number>('')

const canteenFilterOptions = computed(() => [
  { label: '全部食堂', value: '' },
  ...store.canteens.map(c => ({ label: c.name, value: Number(c.id) })),
])
/** 档口选项随所选食堂联动过滤（未选食堂时不启用档口筛选，也不展示选项） */
const stallFilterOptions = computed(() => {
  if (!canteenFilter.value) return []
  return store.stalls
    .filter(s => Number(s.canteen_id) === Number(canteenFilter.value))
    .map(s => ({ label: s.name, value: Number(s.id) }))
})
const stallFilterDisabled = computed(() => !canteenFilter.value)

/** 品类筛选（WEB-06）：取 /admin/categories 全量字典；未加载完成时仅「全部品类」 */
const categories = ref<CategoryItem[]>([])
const categoryFilterOptions = computed(() => [
  { label: '全部品类', value: '' },
  ...categories.value.map(c => ({ label: c.name, value: Number(c.id) })),
])
const categoryNameOf = (categoryId?: number): string =>
  categories.value.find(c => Number(c.id) === Number(categoryId))?.name || '—'

/** 切换食堂：静默清空档口筛选（防跨食堂脏筛选） */
function onCanteenFilterChange() {
  stallFilter.value = ''
}
// 边界兜底：食堂被清空时（外部重置）同步清掉档口筛选
watch(canteenFilter, (v) => { if (!v) stallFilter.value = '' })

/** 由档口 id 反查其所属食堂 id（菜品只存 stall_id，食堂需两级反查；仅筛选链路使用） */
function canteenIdOfStall(stallId: number | bigint): number {
  const s = store.stalls.find(x => Number(x.id) === Number(stallId))
  return s ? Number(s.canteen_id) : 0
}

const rows = computed(() => {
  let list = store.dishes
  if (statusFilter.value) list = list.filter(r => r.status === statusFilter.value)
  if (typeFilter.value === 'discount') list = list.filter(r => !!r.promoPrice)
  else if (typeFilter.value === 'normal') list = list.filter(r => !r.promoPrice)
  if (categoryFilter.value) list = list.filter(r => Number(r.categoryId) === Number(categoryFilter.value))
  // 档口优先（更精确）；仅选食堂时按其下全部档口过滤
  if (stallFilter.value) list = list.filter(r => Number(r.stall_id) === Number(stallFilter.value))
  else if (canteenFilter.value) list = list.filter(r => canteenIdOfStall(r.stall_id) === Number(canteenFilter.value))
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

/**
 * 行内统计（T4）：口径为**全量**而非当前页/当前筛选——
 * store.dishes 来自 dishApi.getAll()（按页循环拉取的全量聚合，WEB-104），故可直接计数；
 * 已下架 = 非 active（与列表「非 active 即已下架」的展示口径严格一致，两者相加恒等于总数）。
 * PR-13：不为取数新增接口。
 */
const stats = computed(() => {
  const total = store.dishes.length
  const active = store.dishes.filter(d => d.status === 'active').length
  return { total, active, inactive: total - active }
})

// ===== 三态（WEB-02：store 不再顶层自动加载，进页显式加载本页所需域并暴露 loading/error） =====
const loading = ref(true)
const error = ref('')
async function refresh() {
  loading.value = true
  error.value = ''
  // 本页域：菜品（列表）+ 食堂/档口（筛选字典）+ 品类（WEB-06 品类列/筛选）
  // 域间独立容错（WEB-09 口径）：字典域失败不拖垮菜品主列表
  const results = await Promise.allSettled([
    dishStore.loadAll(),
    canteenStore.loadAll(),
    stallStore.loadAll(),
    categoryApi.getAll().then((list: CategoryItem[]) => { categories.value = list }),
  ])
  const firstRejected = results.find(r => r.status === 'rejected') as PromiseRejectedResult | undefined
  if (firstRejected) error.value = firstRejected.reason?.message || '加载菜品列表失败'
  loading.value = false
}
onMounted(refresh)

// ===== 进入菜品详情（列表行点击 = 唯一详情入口；编辑仍走弹窗） =====
function onRowClick(row: any) {
  router.push(`/dashboard/content/dishes/${Number(row.id)}`)
}

function dishImage(row: any): string {
  return (row.image || '').split('|||')[0] || ''
}

// ===== 菜品新增/编辑（弹窗直达；归属选择在 DishFormDialog 内，支持直接输入新名称由后端按名 upsert） =====
const dishModal = ref(false)
const editingDishId = ref<number | null>(null)

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

/**
 * 删除影响说明（Q-112 ②）：删除菜品将连带使其评价不可见。
 * 列表接口已带 rating_count，能取到具体条数就带上；取不到则用通用文案（不为取数新增接口，PR-13）。
 */
function deleteImpactText(row: any): string {
  const n = Number(row?.rating_count ?? 0)
  const reviewLine = n > 0
    ? `该菜品下的 ${n} 条评价将一并删除、不可恢复。`
    : '该菜品的评价将一并删除、不可恢复。'
  return `确定删除菜品「${row.name}」？删除后不可恢复。${reviewLine}`
}

async function handleDelete(row: any) {
  if (!await confirm.confirm(deleteImpactText(row))) return
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

// ===== 批量上架/下架/删除（WEB-105：循环期间抑制逐条 reload——纯接口调用并统计成败，结束后统一刷新一次） =====
const selectedIds = ref<number[]>([])
const batchRunning = ref(false)

/**
 * 批量执行通用编排：循环纯接口调用（不逐条触发 store 内部 loadAll），
 * 单条失败不中断批次；结束后统一刷新菜品域一次并汇总「成功 N 条，失败 M 条」。
 */
async function runBatch(
  label: string,
  ids: number[],
  exec: (id: number) => Promise<void>,
  successText: (n: number) => string,
) {
  batchRunning.value = true
  let okCount = 0
  const failedIds: number[] = []
  for (const id of ids) {
    try {
      await exec(id)
      okCount++
    } catch {
      failedIds.push(id)
    }
  }
  // 结束后统一刷新一次（替代逐条 N 次全量 reload）
  try {
    await dishStore.loadAll()
  } catch { /* 刷新失败由页面既有数据兜底，不吞掉批量结果提示 */ }
  batchRunning.value = false
  const failCount = failedIds.length
  if (failCount === 0) {
    toast.success(successText(okCount))
    selectedIds.value = []
  } else {
    toast.error(`批量${label}完成：成功 ${okCount} 条，失败 ${failCount} 条`)
    // 部分失败：仅保留失败项便于重试
    selectedIds.value = failedIds
  }
}

async function batchSetStatus(status: 'active' | 'inactive') {
  if (!selectedIds.value.length || batchRunning.value) return
  const label = status === 'active' ? '上架' : '下架'
  if (!await confirm.confirm(`确定批量${label} ${selectedIds.value.length} 个菜品？`)) return
  await runBatch(label, [...selectedIds.value], id => dishApi.updateById(id, { status }),
    n => `已批量${label} ${n} 个菜品`)
}

async function batchDelete() {
  if (!selectedIds.value.length || batchRunning.value) return
  // 删除影响说明（Q-112 ②）：汇总所选菜品的评价条数（列表已有 rating_count），取不到则通用文案
  const reviewTotal = store.dishes
    .filter(d => selectedIds.value.includes(Number(d.id)))
    .reduce((sum, d) => sum + Number(d.rating_count ?? 0), 0)
  const reviewLine = reviewTotal > 0
    ? `所选菜品下的 ${reviewTotal} 条评价将一并删除、不可恢复。`
    : '所选菜品的评价将一并删除、不可恢复。'
  if (!await confirm.confirm(`确定批量删除 ${selectedIds.value.length} 个菜品？删除后不可恢复。${reviewLine}`)) return
  await runBatch('删除', [...selectedIds.value], id => dishApi.deleteById(id),
    n => `已删除 ${n} 个菜品`)
}
</script>

<template>
    <FilterBar v-model="searchQuery">
      <template #default>
        <FilterSelect
          v-model="canteenFilter"
          label="食堂"
          :options="canteenFilterOptions"
          :width="170"
          @change="onCanteenFilterChange"
        />
        <FilterSelect
          v-model="stallFilter"
          label="档口"
          :options="stallFilterOptions"
          :width="170"
          :disabled="stallFilterDisabled"
          :placeholder="stallFilterDisabled ? '请先选食堂' : '全部档口'"
        />
        <FilterSelect v-model="categoryFilter" label="品类" :options="categoryFilterOptions" :width="150" />
        <FilterSelect v-model="statusFilter" label="状态" :options="statusOptions" :width="150" />
        <FilterSelect v-model="typeFilter" label="类型" :options="typeOptions" :width="150" />
      </template>
      <template #actions>
        <template v-if="selectedIds.length">
          <button class="btn-secondary" v-press type="button" :disabled="batchRunning" @click="batchSetStatus('active')">批量上架</button>
          <button class="btn-secondary" v-press type="button" :disabled="batchRunning" @click="batchSetStatus('inactive')">批量下架（{{ selectedIds.length }}）</button>
          <button class="btn-danger" v-press type="button" :disabled="batchRunning" @click="batchDelete">批量删除</button>
        </template>
        <!-- 全量统计（加载/失败态不展示，避免用 0 冒充真实计数） -->
        <span v-if="!loading && !error" class="stat-inline">共 {{ stats.total }} · 在售 {{ stats.active }} · 已下架 {{ stats.inactive }}</span>
        <button class="btn-primary" v-press @click="openAddDish">
          <el-icon class="btn-plus-icon"><Plus /></el-icon>新增菜品
        </button>
      </template>
    </FilterBar>

    <DataTable
      selectable
      row-clickable
      v-model:selectedIds="selectedIds"
      @row-click="onRowClick"
      :columns="[
        { prop: 'image', label: '图片', width: '72px' },
        { prop: 'name', label: '菜品名称', sortable: true },
        { prop: 'canteenName', label: '所属食堂', width: '140px' },
        { prop: 'stallName', label: '所属档口' },
        { prop: 'category', label: '品类', width: '110px' },
        { prop: 'price', label: '价格', width: '120px', align: 'center', sortable: true },
        { prop: 'rating', label: '评分', width: '80px', align: 'center', sortable: true },
        { prop: 'status', label: '状态', width: '110px', align: 'center' },

      ]"
      :rows="rows"
      :loading="loading"
      :error="error"
      actions-width="230px"
      empty-text="暂无菜品">
      <template #cell-image="{ row }">
        <img v-if="dishImage(row)" :src="dishImage(row)" :alt="row.name" class="cell-thumb" loading="lazy" decoding="async" />
        <span v-else class="cell-thumb cell-thumb-empty">图</span>
      </template>
      <template #cell-name="{ row }">
        <span class="cell-title" :title="row.name">{{ row.name }}</span>
        <span v-if="row.promoPrice" class="promo-flag">折扣</span>
      </template>
      <!-- 所属食堂/档口：DishAdminVO 联表直读（§7.23 第 1 条），不再经 store 反查 -->
      <template #cell-canteenName="{ row }">
        <span class="cell-sub" :title="row.canteenName">{{ row.canteenName || '—' }}</span>
      </template>
      <template #cell-stallName="{ row }">
        <span class="cell-sub" :title="row.stallName">{{ row.stallName || '—' }}</span>
      </template>
      <template #cell-category="{ row }">
        <span class="cell-sub">{{ categoryNameOf(row.categoryId) }}</span>
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
        <button class="link" v-press @click="onRowClick(row)">详情</button>
        <button class="link muted-link" v-press @click="openEditDish(row)">编辑</button>
        <button class="link danger" v-press @click="handleDelete(row)">
          <el-icon class="act-ico"><Delete /></el-icon>删除
        </button>
      </template>
    </DataTable>

    <DishFormDialog
      :show="dishModal"
      :editing-id="editingDishId"
      :default-stall-id="null"
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
/* 次级操作链接（编辑）：弱化于「详情」主链接，保持操作列层级清晰 */
.muted-link { color: var(--text-secondary); }
/* 行内状态开关 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--text-light); }
/* .act-ico 已收敛至 shared.css 公共类 */
</style>
