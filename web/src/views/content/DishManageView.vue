<script setup lang="ts">
/**
 * DishManageView：菜品管理（扁平两层结构）。
 *
 * 结构（本轮 UI 收敛，无层层嵌套）：
 *   PageContainer → 页头（H1「菜品」+ 右[新增菜品]）→ 主体（筛选条 + 列表）
 *
 * 本轮收敛：
 *  - 页头承载主操作「新增菜品」（原在筛选条右侧，与筛选混在一起）；
 *  - 删除 `stat-inline` 统计（共 N · 在售 X · 已下架 Y）——数量只在列表 footer 出现一次；
 *  - 删除行内「详情」链接：整行点击即进详情（唯一入口），操作列只留 编辑 / 删除；
 *  - 列合并：所属食堂 + 所属档口 → 位置（食堂 · 档口），少一列视觉噪音；
 *  - 顶部统计/搜索与筛选保持一行。
 *
 * 菜品大类（2026-09-21 §7.34 / change home-ui-refresh）：新增「大类」筛选 + 展示列。
 * 筛选项 / 展示文案均取自后端字典 `GET /dishes/meal-types`（端上零硬编码中文）；
 * 筛选为**前端本地过滤**（与食堂 / 档口 / 状态 / 关键词一致——`GET /admin/dishes` 只有
 * page / pageSize 两个参数，未擅自扩展服务端接口）。
 *
 * 深链兼容（本站旧书签 / 各页跳转）：
 *  - `?q=<菜名>`：反馈页「关联菜品 →」跳转预填搜索（FeedbackView.goDishEdit）；
 *  - `?tab=dish`：旧「信息管理」tab 参数，本页无多视图，读取时忽略（不报错、不改写 URL）。
 */
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { useDishStore } from '@/stores/dishStore'
import { useCanteenStore } from '@/stores/canteenStore'
import { useStallStore } from '@/stores/stallStore'
import { useMealTypeStore } from '@/stores/mealTypeStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { dishApi } from '@/api'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import DataTable from '@/components/DataTable.vue'
import DishFormDialog from '@/components/DishFormDialog.vue'
import { Plus, Star, Delete } from '@element-plus/icons-vue'

const store = useAdminStore()
const dishStore = useDishStore()
const canteenStore = useCanteenStore()
const stallStore = useStallStore()
const mealTypeStore = useMealTypeStore()
const toast = useToastStore()
const confirm = useConfirmStore()
const router = useRouter()
const route = useRoute()

/**
 * 关键词初值来源：反馈详情「关联菜品」跳转携带 `?q=<菜名>`（FeedbackView.goDishEdit）。
 * 直接读取 route.query.q 作为初值，避免运营主路径在最后一跳断裂（管理员无需手敲菜名）。
 */
function queryKeyword(): string {
  const q = route.query.q
  return typeof q === 'string' ? q : ''
}
const searchQuery = ref(queryKeyword())

/**
 * 同组件内 query 变化响应：从反馈页二次跳转时若组件实例未重建则不会重跑 setup
 * → 必须 watch route.query.q 才能重新筛选。
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

// ===== 食堂 / 档口筛选（食堂与档口是菜品的「筛选条件」，非独立实体页） =====
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

// ===== 菜品大类筛选（§7.34）=====
/**
 * 说明（现状如实登记）：`GET /admin/dishes` 只有 page / pageSize 两个参数，本页全部筛选
 * （食堂 / 档口 / 状态 / 关键词）均为**前端本地过滤**——大类筛选沿用同一风格（不擅自扩展服务端接口）。
 */
const mealTypeFilter = ref<string>('')

/**
 * 大类筛选项：文案与顺序来自后端字典（单一真源，端上零硬编码中文）。
 * 字典只含「当前有在售菜品」的大类，而管理端列表**含已下架**菜品 →
 * 追加「列表中实际出现、字典未覆盖」的枚举键（标签回落键本身，原样透出），
 * 否则该类菜品筛不出来（出现死数据 / 漏筛）。
 */
const mealTypeFilterOptions = computed(() => {
  const covered = new Set(mealTypeStore.list.map(t => t.key))
  const extras = Array.from(new Set(
    store.dishes.map(d => d.mealType).filter((key): key is string => !!key && !covered.has(key)),
  )).sort()
  return [
    { label: '全部大类', value: '' },
    ...mealTypeStore.list.map(t => ({ label: t.label, value: t.key })),
    ...extras.map(key => ({ label: key, value: key })),
  ]
})

const rows = computed(() => {
  let list = store.dishes
  if (mealTypeFilter.value) list = list.filter(r => r.mealType === mealTypeFilter.value)
  if (statusFilter.value) list = list.filter(r => r.status === statusFilter.value)
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


// ===== 三态（WEB-02：store 不再顶层自动加载，进页显式加载本页所需域并暴露 loading/error） =====
const loading = ref(true)
const error = ref('')
async function refresh() {
  loading.value = true
  error.value = ''
  // 本页域：菜品（列表）+ 食堂/档口（筛选字典）
  // 大类字典（§7.34）为「筛选选项 + 标签映射」附属域：**不并入下方 allSettled**——
  // 其失败只会让筛选项 / 展示退化为原始枚举键，不应把已加载成功的菜品主列表打成页面级错误（WEB-09 口径）。
  mealTypeStore.ensureLoaded().catch(() => {})
  // 域间独立容错（WEB-09 口径）：字典域失败不拖垮菜品主列表
  const results = await Promise.allSettled([
    dishStore.loadAll(),
    canteenStore.loadAll(),
    stallStore.loadAll(),
  ])
  const firstRejected = results.find(r => r.status === 'rejected') as PromiseRejectedResult | undefined
  if (firstRejected) error.value = firstRejected.reason?.message || '加载菜品列表失败'
  loading.value = false
}
onMounted(refresh)

// ===== 进入菜品详情（整行点击 = 唯一详情入口；编辑走弹窗） =====
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

/** 展示值恒取现价 price（§7.26：禁止双源切换，无促销价） */
function formatPrice(row: any): string {
  return `¥${Number(row.price ?? 0).toFixed(2)}`
}

/** 有折扣（§7.26）：原价有值且高于现价 → 端上原价划线 */
function hasPromo(row: any): boolean {
  return row.originalPrice != null && Number(row.originalPrice) > Number(row.price)
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
    : `所选菜品的评价将一并删除、不可恢复。`
  if (!await confirm.confirm(`确定批量删除 ${selectedIds.value.length} 个菜品？删除后不可恢复。${reviewLine}`)) return
  await runBatch('删除', [...selectedIds.value], id => dishApi.deleteById(id),
    n => `已删除 ${n} 个菜品`)
}
</script>

<template>
  <PageContainer>
    <PageHeader title="菜品">
      <template #actions>
        <button class="btn-primary" v-press type="button" @click="openAddDish">
          <el-icon class="btn-plus-icon"><Plus /></el-icon>新增菜品
        </button>
      </template>
    </PageHeader>

    <FilterBar v-model="searchQuery">
      <template #default>
        <FilterSelect v-model="mealTypeFilter" label="大类" :options="mealTypeFilterOptions" :width="150" />
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
        <FilterSelect v-model="statusFilter" label="状态" :options="statusOptions" :width="150" />
      </template>
      <template #actions>
        <!-- 批量动作仅在选中时出现（数量只在此处出现一次） -->
        <template v-if="selectedIds.length">
          <span class="sel-count">已选 {{ selectedIds.length }} 项</span>
          <button class="btn-secondary" v-press type="button" :disabled="batchRunning" @click="batchSetStatus('active')">批量上架</button>
          <button class="btn-secondary" v-press type="button" :disabled="batchRunning" @click="batchSetStatus('inactive')">批量下架</button>
          <button class="btn-danger" v-press type="button" :disabled="batchRunning" @click="batchDelete">批量删除</button>
        </template>
      </template>
    </FilterBar>

    <DataTable
      selectable
      row-clickable
      v-model:selectedIds="selectedIds"
      @row-click="onRowClick"
      :columns="[
        { prop: 'image', label: '图片', width: '72px', align: 'center' },
        { prop: 'name', label: '菜品名称', sortable: true },
        { prop: 'mealType', label: '大类', width: '104px' },
        { prop: 'location', label: '位置' },
        { prop: 'price', label: '价格', width: '120px', align: 'center', sortable: true },
        { prop: 'rating', label: '评分', width: '80px', align: 'center', sortable: true },
        { prop: 'status', label: '状态', width: '110px', align: 'center' },
      ]"
      :rows="rows"
      :loading="loading"
      :error="error"
      actions-width="140px"
      empty-text="暂无菜品">
      <template #cell-image="{ row }">
        <img v-if="dishImage(row)" :src="dishImage(row)" :alt="row.name" class="cell-thumb" loading="lazy" decoding="async" />
        <span v-else class="cell-thumb cell-thumb-empty">图</span>
      </template>
      <template #cell-name="{ row }">
        <span class="cell-title" :title="row.name">{{ row.name }}</span>
      </template>
      <!-- 大类（§7.34）：纯文字展示（分类维度不加彩标签，避免与状态语义抢色）；文案取自后端字典 -->
      <template #cell-mealType="{ row }">
        <span v-if="row.mealType" class="cell-sub" :title="mealTypeStore.labelOf(row.mealType)">
          {{ mealTypeStore.labelOf(row.mealType) }}
        </span>
        <span v-else class="text-muted">—</span>
      </template>
      <!-- 位置：所属食堂 · 所属档口（DishAdminVO 联表直读，§7.23 第 1 条；两列合并为一列降噪） -->
      <template #cell-location="{ row }">
        <span class="cell-sub" :title="`${row.canteenName || '—'} · ${row.stallName || '—'}`">
          {{ row.canteenName || '—' }} · {{ row.stallName || '—' }}
        </span>
      </template>
      <template #cell-price="{ row }">
        <span class="price-cell">{{ formatPrice(row) }}</span>
        <span v-if="hasPromo(row)" class="origin">¥{{ Number(row.originalPrice).toFixed(2) }}</span>
        <span v-if="hasPromo(row)" class="promo-flag">折扣</span>
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
      <!-- 操作：详情统一走整行点击（不再重复一个「详情」链接） -->
      <template #actions="{ row }">
        <button class="link" v-press type="button" @click="openEditDish(row)">编辑</button>
        <button class="link danger" v-press type="button" @click="handleDelete(row)">
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
  </PageContainer>
</template>

<style scoped>
.cell-thumb { width: 52px; height: 40px; border-radius: var(--radius-sm); object-fit: cover; display: inline-block; vertical-align: middle; background: var(--bg-soft); }
.cell-thumb-empty { display: inline-flex; align-items: center; justify-content: center; font-size: var(--font-xs); color: var(--text-light); }
.cell-title { font-weight: var(--weight-semibold); color: var(--text-primary); }
.cell-sub { font-size: var(--font-sm); color: var(--text-secondary); }
.promo-flag { margin-left: var(--space-2); background: var(--color-error); color: var(--text-white); font-size: var(--font-xs); padding: 0 var(--space-2); border-radius: var(--radius-sm); vertical-align: 1px; }
.price-cell { color: var(--color-price); font-weight: var(--weight-bold); }
.origin { color: var(--text-light); text-decoration: line-through; font-size: var(--font-xs); margin-left: var(--space-1); }
.rating { display: inline-flex; align-items: center; gap: 2px; font-size: var(--font-sm); color: var(--color-star); font-weight: var(--weight-medium); }
.star { width: 13px; height: 13px; }
.text-muted { color: var(--text-light); }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }
/* 选中计数（批量动作区只出现一次数量） */
.sel-count { font-size: var(--font-sm); color: var(--text-secondary); white-space: nowrap; font-variant-numeric: tabular-nums; }
/* 行内状态开关 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--text-light); }
/* .act-ico 已收敛至 shared.css 公共类 */
</style>
