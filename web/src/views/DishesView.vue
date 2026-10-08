<script setup lang="ts">
/**
 * A3 菜品管理（页面规格见 [菜品管理.md](../../../docs/ui/web/菜品管理.md)）。
 *
 * <p>要点：分页（页码 + 共 N 条）+ 六态；表格**只放基础检索列**（名称 / 分类 / 现价 / 状态 / 更新时间）——
 * 封面与配图（含大图预览）、原价、归属、描述、属性、评分、浏览量与**全部操作**
 * （编辑 / 复制 / 上下架 / 删除）都在**详情抽屉**内。
 *
 * <p>列表行是**瘦身 VO**（不带 `description` / `images` / `attributes`）而 `PUT` 是整体替换
 * ⇒ 详情与编辑都必须先用 `GET /admin/dishes/{id}` 取全字段，否则保存会把这些字段当成「未传 / 空」而清空。
 *
 * <p>**菜品种类字典**（系统维度取值，`dish.meal_type_id` 的取值域）的维护入口挂在本页分类下拉旁
 * （分类抽屉），不单设菜单项 —— 与 [web/categories.md](../../../docs/api/web/categories.md) 的入口口径一致。
 */
import ActiveFilters from '@/components/ActiveFilters.vue'
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
import { canDelete, canWrite } from '@/utils/permissions'
import { formatDateTime } from '@/utils/datetime'
import { fail } from '@/utils/error'
import {
  copyDish,
  createDish,
  deleteDish,
  getDish,
  listDishes,
  updateDish,
  updateDishStatus,
} from '@/api/dishes'
import { listCanteens } from '@/api/canteens'
import { listStalls } from '@/api/stalls'
import { listDimensions, listValues } from '@/api/dimensions'
import { createCategory, listCategories, renameCategory, sortCategories } from '@/api/categories'
import type {
  CanteenAdminVO,
  DishAdminListItemVO,
  DishAdminVO,
  DishCategoryAdminVO,
  DishDimensionAdminVO,
  DishListParams,
  DishSaveReq,
  DishValueAdminVO,
  OnOffStatus,
  StallAdminVO,
} from '@/types/common'
import { fenToYuan, formatYuan, yuanToFen } from '@/utils/money'
import { usePagedList } from '@/composables/usePagedList'
import { useReorder } from '@/composables/useReorder'
import BaseDrawer from '@/components/BaseDrawer.vue'
import BaseModal from '@/components/BaseModal.vue'
import ImagePreview from '@/components/ImagePreview.vue'
import ImageUpload, { type ImageItem } from '@/components/ImageUpload.vue'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StateBox from '@/components/StateBox.vue'
import StatusTag from '@/components/StatusTag.vue'
import { resolveImageUrl } from '@/utils/image'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import { useRowAction } from '@/composables/useRowAction'

const canteens = ref<CanteenAdminVO[]>([])
const stalls = ref<StallAdminVO[]>([])
const dimensions = ref<DishDimensionAdminVO[]>([])
/** 描述属性维度（**排除系统维度「菜品种类」**）：属性子表单只渲染这些维度 */
const attributeDimensions = computed(() => dimensions.value.filter((d) => !d.system))
/** A6 菜品种类字典：分类**筛选下拉**与编辑表单分类下拉、以及「分类中文名」展示的真源 */
const categories = ref<DishCategoryAdminVO[]>([])
/**
 * 取值字典（按维度 ID 分组）—— A4 落地后**属性的候选真源**。
 *
 * <p>表单用 `input` + `datalist`：既给出字典建议，又保留「**自由输入新值**」能力
 * （服务端在同维度内未命中即**自动登记**为新取值，见 A4 的「灵活取值」）。
 */
const valuesByDimensionId = ref<Record<string, DishValueAdminVO[]>>({})

/* ==================== 列表 ==================== */
const fKeyword = ref('')
const fCanteenId = ref(0)
const fStallId = ref(0)
const fMealTypeId = ref(0)
const fStatus = ref<OnOffStatus | ''>('')

function params(): DishListParams {
  return {
    keyword: fKeyword.value || undefined,
    canteenId: fCanteenId.value || undefined,
    stallId: fStallId.value || undefined,
    mealTypeId: fMealTypeId.value || undefined,
    status: fStatus.value || undefined,
  }
}

const {
  items,
  total,
  page,
  pageCount,
  firstLoading,
  isEmpty,
  hasData,
  error,
  sessionInvalid,
  reload,
  reloadFirstPage,
  prevPage,
  nextPage,
} = usePagedList<DishAdminListItemVO>((pageNo, pageSize) =>
  listDishes({ page: pageNo, pageSize, ...params() }),
)

const filteredStalls = computed(() =>
  fCanteenId.value ? stalls.value.filter((s) => s.canteenId === fCanteenId.value) : stalls.value,
)

/* ==================== 详情 / 编辑抽屉 ==================== */
/** 抽屉两态：`view` = 详情（只读 + 操作）/ `edit` = 新建或编辑表单 */
type DrawerMode = 'view' | 'edit'

const drawerOpen = ref(false)
const mode = ref<DrawerMode>('view')
/** 详情态的全字段 VO（列表行是瘦身 VO，必须回查详情） */
const detail = ref<DishAdminVO | null>(null)
const detailLoading = ref(false)
const detailError = ref<string | null>(null)
const saving = ref(false)

const form = ref({
  name: '',
  stallId: 0,
  priceYuan: '',
  originalPriceYuan: '',
  mealTypeId: 0,
  description: '',
  /** 组件态 = `{ url }` 对象数组（ImageUpload 契约）；提交时映射回 `images: string[]` */
  images: [] as ImageItem[],
})
const attrSingle = ref<Record<string, string>>({})
const attrMulti = ref<Record<string, string>>({})
/** 进入编辑态时的快照：用于「未保存修改」二次确认（BaseDrawer 的 dirty） */
const snapshot = ref('')

const drawerTitle = computed(() => {
  if (mode.value === 'view') return '菜品详情'
  return detail.value ? '编辑菜品' : '新建菜品'
})

/** 表单快照内容 = 可提交的字段（属性表单另算，见 isDirty） */
function formSnapshot(): string {
  return JSON.stringify({ form: form.value, single: attrSingle.value, multi: attrMulti.value })
}

const dirty = computed(() => mode.value === 'edit' && formSnapshot() !== snapshot.value)

/** 详情展示：描述属性按维度名展开（键 = 维度 ID；未登记维度回落原始键） */
const attributeRows = computed<{ label: string; value: string }[]>(() => {
  const attrs = detail.value?.attributes
  if (!attrs) return []
  const nameOf = (key: string): string =>
    dimensions.value.find((d) => String(d.id) === key)?.name ?? `维度 ${key}`
  return Object.entries(attrs)
    .filter(([, v]) => (Array.isArray(v) ? v.length > 0 : Boolean(v)))
    .map(([key, v]) => ({
      label: nameOf(key),
      value: Array.isArray(v) ? v.join('、') : v,
    }))
})

function resetForm(): void {
  form.value = {
    name: '',
    stallId: stalls.value[0]?.id ?? 0,
    priceYuan: '',
    originalPriceYuan: '',
    mealTypeId: categories.value[0]?.id ?? 0,
    description: '',
    images: [],
  }
  attrSingle.value = {}
  attrMulti.value = {}
  snapshot.value = formSnapshot()
}

/** 详情态 → 编辑态：把已取回的全字段 VO 回填表单 */
function fillFormFrom(d: DishAdminVO): void {
  form.value = {
    name: d.name,
    stallId: d.stallId,
    priceYuan: String(fenToYuan(d.price)),
    originalPriceYuan: d.originalPrice == null ? '' : String(fenToYuan(d.originalPrice)),
    mealTypeId: d.mealTypeId,
    description: d.description,
    // 编辑回显：契约出参 `images: string[]`（有序）映射为组件对象数组，顺序不变 ⇒ 首图仍是封面
    images: d.images.map((url) => ({ url })),
  }
  attrSingle.value = {}
  attrMulti.value = {}
  for (const dim of attributeDimensions.value) {
    const v = d.attributes?.[String(dim.id)]
    if (Array.isArray(v)) attrMulti.value[String(dim.id)] = v.join('、')
    else if (typeof v === 'string') attrSingle.value[String(dim.id)] = v
  }
  snapshot.value = formSnapshot()
}

/** 行内动作并发保护：提交中该行按钮 `:disabled`（列表页模板 §1.3「并发保护」） */
const { busyId, isBusy, runRowAction } = useRowAction()

async function openDetail(row: DishAdminListItemVO): Promise<void> {
  drawerOpen.value = true
  mode.value = 'view'
  detail.value = null
  detailError.value = null
  detailLoading.value = true
  busyId.value = row.id
  try {
    detail.value = await getDish(row.id)
    fillFormFrom(detail.value)
  } catch (e) {
    detailError.value = e instanceof Error ? e.message : '加载菜品详情失败'
  } finally {
    detailLoading.value = false
    busyId.value = null
  }
}

function openCreate(): void {
  drawerOpen.value = true
  detail.value = null
  mode.value = 'edit'
  resetForm()
}

function startEdit(): void {
  if (detail.value) fillFormFrom(detail.value)
  mode.value = 'edit'
}

/** 编辑态「取消」：有历史行则退回查看态，新建则关抽屉 */
function cancelEdit(): void {
  if (detail.value) {
    fillFormFrom(detail.value)
    mode.value = 'view'
  } else {
    drawerOpen.value = false
  }
}

function buildAttributes(): Record<string, string | string[]> {
  const out: Record<string, string | string[]> = {}
  for (const d of attributeDimensions.value) {
    // 键 = 描述维度 ID 字符串（`dish.attributes` JSON 键形态）；系统维度（菜品种类）只走 mealTypeId
    const key = String(d.id)
    if (d.valueType === 'single') {
      const v = (attrSingle.value[key] ?? '').trim()
      if (v) out[key] = v
    } else {
      const arr = (attrMulti.value[key] ?? '')
        .split(/[、,]/)
        .map((s) => s.trim())
        .filter(Boolean)
      if (arr.length) out[key] = arr
    }
  }
  return out
}

async function save(): Promise<void> {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入菜品名称')
    return
  }
  if (!form.value.stallId) {
    ElMessage.warning('请选择所属档口')
    return
  }
  if (!form.value.images.length) {
    ElMessage.warning('请至少上传一张封面图')
    return
  }
  if (!form.value.mealTypeId) {
    ElMessage.warning('请选择菜品分类')
    return
  }
  const price = yuanToFen(form.value.priceYuan)
  if (price <= 0) {
    ElMessage.warning('现价须大于 0')
    return
  }
  const originalPrice = form.value.originalPriceYuan
    ? yuanToFen(form.value.originalPriceYuan)
    : null
  if (originalPrice != null && originalPrice <= price) {
    ElMessage.warning('原价须大于现价')
    return
  }
  const req: DishSaveReq = {
    name: form.value.name.trim(),
    stallId: form.value.stallId,
    price,
    originalPrice,
    mealTypeId: form.value.mealTypeId,
    description: form.value.description,
    // 提交映射：按数组顺序回 `images: string[]`（首图即封面，后端 / client 契约零变更）
    images: form.value.images.map((img) => img.url),
    attributes: buildAttributes(),
  }
  saving.value = true
  try {
    if (detail.value) {
      await updateDish(detail.value.id, req)
      ElMessage.success('已保存')
      await reload()
      // 回填详情：`GET` 详情是唯一全字段来源，保存后重新取一次保证抽屉不停在旧快照
      detail.value = await getDish(detail.value.id)
      fillFormFrom(detail.value)
      mode.value = 'view'
    } else {
      await createDish(req)
      ElMessage.success('已新建')
      await reload()
      drawerOpen.value = false
    }
  } catch (e) {
    fail(e, '保存失败')
  } finally {
    saving.value = false
  }
}

/* ==================== 复制 ==================== */
const copyOpen = ref(false)
const copyName = ref('')
const copying = ref(false)

function openCopy(): void {
  const d = detail.value
  if (!d) return
  copyName.value = `${d.name}（副本）`
  copyOpen.value = true
}

async function submitCopy(): Promise<void> {
  const d = detail.value
  if (!d) return
  if (!copyName.value.trim()) {
    ElMessage.warning('请输入新菜名')
    return
  }
  copying.value = true
  try {
    await copyDish(d.id, copyName.value.trim())
    ElMessage.success('已复制（副本默认下架，确认内容后再上架）')
    copyOpen.value = false
    drawerOpen.value = false
    await reload()
  } catch (e) {
    fail(e, '复制失败')
  } finally {
    copying.value = false
  }
}

/* ==================== 上下架 / 删除 ==================== */
function toggle(): Promise<void> {
  const d = detail.value
  if (!d) return Promise.resolve()
  const next: OnOffStatus = d.status === 'on' ? 'off' : 'on'
  return runRowAction({
    id: d.id,
    action: () => updateDishStatus(d.id, next),
    successMessage: next === 'on' ? '已上架' : '已下架',
    // 详情抽屉停在旧快照上会显示过期状态 ⇒ 刷新列表后重拉详情并回填表单
    refresh: async () => {
      await reload()
      detail.value = await getDish(d.id)
      fillFormFrom(detail.value)
    },
  })
}

function remove(): Promise<void> {
  const d = detail.value
  if (!d) return Promise.resolve()
  return runRowAction({
    id: d.id,
    action: async () => {
      // 影响面必须给出（评价数取详情出参 `ratingCount`）
      await confirmDelete(
        `确认删除菜品「${d.name}」？将一并删除该菜品的至少 ${d.ratingCount} 条评价，且不可恢复。`,
        { title: '删除菜品' },
      )
      await deleteDish(d.id)
    },
    successMessage: '已删除',
    failMessage: '删除失败',
    refresh: reload,
    closeDrawer: () => {
      drawerOpen.value = false
      detail.value = null
    },
  })
}

/* ==================== 图片大图预览 ==================== */
const previewOpen = ref(false)
const previewImages = ref<string[]>([])
const previewIndex = ref(0)

function openPreview(images: string[], index: number): void {
  if (!images || images.length === 0) return
  previewImages.value = images
  previewIndex.value = index
  previewOpen.value = true
}

/* ==================== 分类管理（抽屉：分类下拉旁入口，菜品种类字典） ==================== */
const catOpen = ref(false)
const newCatLabel = ref('')
const editingCatId = ref<number | null>(null)
const editingCatLabel = ref('')

async function refreshCategories(): Promise<void> {
  categories.value = await listCategories()
}

function openCategories(): void {
  catOpen.value = true
  refreshCategories().catch(() => undefined)
}

async function addCategory(): Promise<void> {
  const label = newCatLabel.value.trim()
  if (!label) {
    ElMessage.warning('请填写分类名')
    return
  }
  try {
    await createCategory(label)
    newCatLabel.value = ''
    ElMessage.success('已登记')
    await refreshCategories()
  } catch (e) {
    // 名非法 / 同维度重名 → 后端原文（400）
    fail(e, '登记失败')
  }
}

function startRenameCategory(row: DishCategoryAdminVO): void {
  editingCatId.value = row.id
  editingCatLabel.value = row.label
}

async function commitRenameCategory(row: DishCategoryAdminVO): Promise<void> {
  const label = editingCatLabel.value.trim()
  if (!label) return
  try {
    await renameCategory(row.id, label)
    editingCatId.value = null
    ElMessage.success('已改名')
    await refreshCategories()
  } catch (e) {
    fail(e, '改名失败')
  }
}

/** 种类取值拖拽排序（整体提交全量行） */
const { onDragStart: onCatDragStart, onDrop: onCatDrop } = useReorder(
  categories,
  sortCategories,
  refreshCategories,
)

function reset(): void {
  fKeyword.value = ''
  fCanteenId.value = 0
  fStallId.value = 0
  fMealTypeId.value = 0
  fStatus.value = ''
  reloadFirstPage()
}

/* ---- 已生效筛选条件回显（`ActiveFilters`）----
   下拉筛选项存的是 id，直接回显会让管理员看到「食堂 = 3」这类无意义值 ⇒
   先按下拉选项的 label 映射为名称，映射不到时回落 `#id`（仍比裸 id 可辨认）。 */
const canteenLabel = (id: number): string =>
  canteens.value.find((c) => c.id === id)?.name ?? `#${id}`
const stallLabel = (id: number): string => stalls.value.find((s) => s.id === id)?.name ?? `#${id}`
const categoryLabel = (id: number): string =>
  categories.value.find((c) => c.id === id)?.label ?? `#${id}`

const activeFilters = computed(() => {
  const out: { key: string; label: string; value: string }[] = []
  if (fKeyword.value.trim())
    out.push({ key: 'keyword', label: '菜品名', value: fKeyword.value.trim() })
  if (fCanteenId.value)
    out.push({ key: 'canteen', label: '食堂', value: canteenLabel(fCanteenId.value) })
  if (fStallId.value) out.push({ key: 'stall', label: '档口', value: stallLabel(fStallId.value) })
  if (fMealTypeId.value)
    out.push({ key: 'category', label: '分类', value: categoryLabel(fMealTypeId.value) })
  if (fStatus.value)
    out.push({
      key: 'status',
      label: '状态',
      value: fStatus.value === 'on' ? '在售' : '已下架',
    })
  return out
})

/** 清除单个筛选条件并重查 */
function clearFilter(key: string): void {
  if (key === 'keyword') fKeyword.value = ''
  if (key === 'canteen') fCanteenId.value = 0
  if (key === 'stall') fStallId.value = 0
  if (key === 'category') fMealTypeId.value = 0
  if (key === 'status') fStatus.value = ''
  reloadFirstPage()
}

onMounted(async () => {
  // 依赖数据（下拉）失败不阻塞列表
  try {
    const [cs, ss, ds, cats] = await Promise.all([
      listCanteens(),
      listStalls(),
      listDimensions(),
      listCategories(),
    ])
    canteens.value = cs
    stalls.value = ss
    dimensions.value = ds
    categories.value = cats
    // 各维度的取值字典：逐个拉取后按维度 ID 归组（供 datalist 建议）
    const grouped = await Promise.all(
      ds.map(async (d) => [String(d.id), await listValues(d.id)] as const),
    )
    valuesByDimensionId.value = Object.fromEntries(grouped)
  } catch {
    /* 列表仍可展示 */
  }
  await reloadFirstPage()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>菜品管理</h2>
      <button class="btn-secondary" type="button" v-press @click="openCategories">管理分类</button>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建菜品</button>
    </div>

    <div class="card filters">
      <input
        class="form-input"
        v-model="fKeyword"
        placeholder="菜品名"
        @keyup.enter="reloadFirstPage"
      />
      <select class="form-input" v-model.number="fCanteenId" @change="reloadFirstPage">
        <option :value="0">全部食堂</option>
        <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <select class="form-input" v-model.number="fStallId" @change="reloadFirstPage">
        <option :value="0">全部档口</option>
        <option v-for="s in filteredStalls" :key="s.id" :value="s.id">{{ s.name }}</option>
      </select>
      <!-- A6 种类字典驱动的下拉：筛选值 = 种类取值 ID -->
      <select class="form-input" v-model.number="fMealTypeId" @change="reloadFirstPage">
        <option :value="0">全部分类</option>
        <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.label }}</option>
      </select>
      <select class="form-input" v-model="fStatus" @change="reloadFirstPage">
        <option value="">全部状态</option>
        <option value="on">在售</option>
        <option value="off">已下架</option>
      </select>
      <button class="btn-primary" type="button" v-press @click="reloadFirstPage">查询</button>
      <button class="btn-secondary" type="button" @click="reset">重置</button>
      <!-- 已生效筛选条件回显：让管理员一眼看出当前结果被什么条件筛出（可点单个清除） -->
      <ActiveFilters :items="activeFilters" @remove="clearFilter" @clear="reset" />
    </div>

    <!-- 六态：① 加载 ② 错误 ③ 空 ④ 有数据 ⑤ 分页 ⑥ 会话失效 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无菜品"
      @retry="reload"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>菜品</th>
            <th>分类</th>
            <th>现价</th>
            <th>状态</th>
            <th>更新时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.name }}</td>
            <td>{{ row.mealTypeLabel || '—' }}</td>
            <td class="num">{{ formatYuan(row.price) }}</td>
            <td><StatusTag :status="row.status" kind="dish" /></td>
            <td class="muted">{{ formatDateTime(row.updatedAt) }}</td>
            <td class="actions">
              <button
                class="link"
                type="button"
                :disabled="isBusy(row.id)"
                @click="openDetail(row)"
              >
                详情
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <p class="foot-note">
        「名称 / 分类 / 现价 /
        状态」是快速检索列；配图（可点开大图）、原价、归属食堂档口、描述、描述属性、
        评分、浏览量与编辑 / 复制 / 上下架 / 删除都在行内「详情」内。
      </p>

      <Pager
        :total="total"
        :page="page"
        :page-count="pageCount"
        @prev="prevPage"
        @next="nextPage"
      />
    </div>

    <!-- 详情 / 编辑抽屉（同一抽屉两态） -->
    <BaseDrawer :title="drawerTitle" :open="drawerOpen" :dirty="dirty" @close="drawerOpen = false">
      <StateBox v-if="detailLoading" status="loading" />
      <StateBox v-else-if="detailError" status="error" :message="detailError" />

      <!-- ① 查看态 -->
      <template v-else-if="mode === 'view' && detail">
        <div class="field">
          <label id="dish-images-label">
            配图 · {{ detail.images.length }} 张（首图为封面，点击看大图）
          </label>
          <div
            v-if="detail.images.length"
            class="detail-thumbs"
            role="group"
            aria-labelledby="dish-images-label"
          >
            <button
              v-for="(img, i) in detail.images"
              :key="i"
              class="thumb"
              type="button"
              :aria-label="`查看第 ${i + 1} 张配图`"
              @click="openPreview(detail.images, i)"
            >
              <img :src="resolveImageUrl(img)" alt="" />
            </button>
          </div>
          <div v-else class="muted">暂无配图</div>
        </div>

        <!-- 分组：核心指标（评分 / 价格并列强调）→ 基本信息（两栏）→ 描述与属性（整行） -->
        <div class="detail-group">
          <div class="detail-group-title">核心指标</div>
          <div class="detail-meta detail-meta--grid">
            <DetailMetaRow k="评分" emphasis>
              {{ detail.avgRating ?? '—' }}
              <span class="muted">（{{ detail.ratingCount }} 条）</span>
            </DetailMetaRow>
            <DetailMetaRow k="现价" num emphasis>{{ formatYuan(detail.price) }}</DetailMetaRow>
          </div>
        </div>

        <div class="detail-group">
          <div class="detail-group-title">基本信息</div>
          <div class="detail-meta detail-meta--grid">
            <DetailMetaRow k="名称">{{ detail.name }}</DetailMetaRow>
            <DetailMetaRow k="菜品 ID" num>#{{ detail.id }}</DetailMetaRow>
            <div class="meta-row">
              <span class="meta-key">分类</span>
              <span class="meta-val">
                {{ detail.mealTypeLabel || '—'
                }}<span class="muted"> #{{ detail.mealTypeId }}</span>
              </span>
            </div>
            <div class="meta-row">
              <span class="meta-key">状态</span>
              <span class="meta-val"><StatusTag :status="detail.status" kind="dish" /></span>
            </div>
            <div class="meta-row">
              <span class="meta-key">归属</span>
              <span class="meta-val">
                {{ detail.canteenName }} / {{ detail.stallName
                }}<span class="muted"> #{{ detail.stallId }}</span>
              </span>
            </div>
            <DetailMetaRow k="原价" num>
              {{ detail.originalPrice == null ? '—（无折扣）' : formatYuan(detail.originalPrice) }}
            </DetailMetaRow>
          </div>
        </div>

        <div class="detail-group">
          <div class="detail-group-title">描述与属性</div>
          <div class="detail-meta">
            <DetailMetaRow k="描述">{{ detail.description || '—' }}</DetailMetaRow>
            <div class="meta-row">
              <span class="meta-key">描述属性</span>
              <span class="meta-val">
                <template v-if="attributeRows.length">
                  <span v-for="attr in attributeRows" :key="attr.label" class="attr-line">
                    {{ attr.label }}：{{ attr.value }}
                  </span>
                </template>
                <span v-else class="muted">未设置</span>
              </span>
            </div>
          </div>
        </div>

        <div class="detail-group">
          <div class="detail-group-title">时间</div>
          <div class="detail-meta detail-meta--grid">
            <DetailMetaRow k="创建时间">{{ formatDateTime(detail.createdAt) }}</DetailMetaRow>
            <DetailMetaRow k="更新时间">{{ formatDateTime(detail.updatedAt) }}</DetailMetaRow>
          </div>
        </div>
      </template>

      <!-- ② 编辑态（新建 / 编辑共用） -->
      <template v-else>
        <div class="field">
          <label for="dish-name">菜品名称</label>
          <input id="dish-name" class="form-input" v-model="form.name" placeholder="菜品名称" />
        </div>
        <div class="field">
          <label for="dish-stall">所属档口</label>
          <select id="dish-stall" class="form-input" v-model.number="form.stallId">
            <option :value="0" disabled>请选择</option>
            <option v-for="s in stalls" :key="s.id" :value="s.id">
              {{ s.canteenName }} · {{ s.name }}
            </option>
          </select>
        </div>
        <div class="row2">
          <div class="field">
            <label for="dish-price">现价（元）</label>
            <input
              id="dish-price"
              class="form-input"
              type="number"
              step="0.01"
              v-model="form.priceYuan"
            />
          </div>
          <div class="field">
            <label for="dish-original-price">原价（元，可空）</label>
            <input
              id="dish-original-price"
              class="form-input"
              type="number"
              step="0.01"
              v-model="form.originalPriceYuan"
            />
          </div>
        </div>
        <div class="field">
          <label for="dish-meal-type">菜品分类</label>
          <select id="dish-meal-type" class="form-input" v-model.number="form.mealTypeId">
            <option :value="0" disabled>请选择分类</option>
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.label }}</option>
          </select>
          <div class="hint">
            菜品种类在页头「管理分类」内维护（登记 / 改名 / 拖拽排序；改名免费）
          </div>
        </div>
        <div class="field">
          <label for="dish-description">描述</label>
          <textarea
            id="dish-description"
            class="form-textarea"
            v-model="form.description"
            rows="2"
          />
        </div>
        <div class="field">
          <label>配图（首图为封面，必填，≤5 张）</label>
          <ImageUpload v-model="form.images" :max="5" :aria-label="'配图'" />
        </div>
        <div v-if="attributeDimensions.length" class="dim-block">
          <div class="dim-title">描述属性（下拉给出字典建议；填新值保存时自动登记）</div>
          <div class="field" v-for="d in attributeDimensions" :key="d.id">
            <label>{{ d.name }}{{ d.valueType === 'multi' ? '（多值）' : '' }}</label>
            <!-- 字典建议 + 自由录入：value 为中文，服务端同维度内未命中即自动登记为新取值；
                 键 = 维度 ID 字符串（`dish.attributes` JSON 键形态）。
                 v-model 必须是成员表达式 ⇒ 单值 / 多值分列两个输入（不用三元写法）。 -->
            <input
              v-if="d.valueType === 'single'"
              class="form-input"
              :list="`dim-${d.id}`"
              placeholder="选择或输入新值"
              v-model="attrSingle[String(d.id)]"
            />
            <input
              v-else
              class="form-input"
              :list="`dim-${d.id}`"
              placeholder="多个值用顿号分隔"
              v-model="attrMulti[String(d.id)]"
            />
            <datalist :id="`dim-${d.id}`">
              <option
                v-for="v in valuesByDimensionId[String(d.id)] ?? []"
                :key="v.id"
                :value="v.label"
              />
            </datalist>
          </div>
        </div>
      </template>

      <template #actions>
        <template v-if="mode === 'view' && canWrite() && detail">
          <button
            v-if="canWrite()"
            class="link"
            type="button"
            :disabled="isBusy(detail.id)"
            @click="startEdit"
          >
            编辑
          </button>
          <button
            v-if="canWrite()"
            class="link"
            type="button"
            :disabled="isBusy(detail.id)"
            @click="openCopy"
          >
            复制
          </button>
          <button
            v-if="canWrite()"
            class="link"
            type="button"
            :disabled="isBusy(detail.id)"
            @click="toggle"
          >
            {{ detail.status === 'on' ? '下架' : '上架' }}
          </button>
          <button
            v-if="canDelete()"
            class="link danger"
            type="button"
            :disabled="isBusy(detail.id)"
            @click="remove"
          >
            删除
          </button>
        </template>
        <template v-else-if="mode === 'edit'">
          <button class="btn-secondary" type="button" @click="cancelEdit">取消</button>
          <button class="btn-primary" type="button" :disabled="saving" v-press @click="save">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </template>
      </template>
    </BaseDrawer>

    <!-- 复制：只填新菜名（其余字段复制源菜品，副本默认下架） -->
    <BaseModal title="复制菜品" :open="copyOpen" @close="copyOpen = false">
      <div class="field">
        <label for="copy-name">新菜品名称</label>
        <input id="copy-name" class="form-input" v-model="copyName" @keyup.enter="submitCopy" />
      </div>
      <p class="hint">
        其余字段（价格 / 分类 / 属性 / 图片）全部复制源菜品；副本默认下架，确认内容后再上架。
      </p>
      <template #actions>
        <button class="btn-secondary" type="button" @click="copyOpen = false">取消</button>
        <button class="btn-primary" type="button" :disabled="copying" v-press @click="submitCopy">
          {{ copying ? '复制中…' : '复制' }}
        </button>
      </template>
    </BaseModal>

    <!-- 分类管理（抽屉）：种类字典的登记 / 改名 / 拖拽排序（取值 ID 由后端生成） -->
    <BaseDrawer title="菜品分类" :open="catOpen" @close="catOpen = false">
      <div class="add-row">
        <input
          class="form-input"
          v-model="newCatLabel"
          placeholder="分类名（≤32 字）"
          @keyup.enter="addCategory"
        />
        <button class="btn-primary" type="button" v-press @click="addCategory">登记</button>
      </div>
      <StateBox v-if="!categories.length" status="empty" message="暂无分类" />
      <div v-else class="table-wrap">
        <table class="table table--compact">
          <thead>
            <tr>
              <th class="drag-col"></th>
              <th>分类名</th>
              <th>引用菜品</th>
              <th>更新时间</th>
              <th class="actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(row, index) in categories"
              :key="row.id"
              @dragover.prevent
              @drop="onCatDrop(index)"
            >
              <td class="drag-col"><DragHandle @dragstart="onCatDragStart(index)" /></td>
              <td>
                <input
                  v-if="editingCatId === row.id"
                  class="form-input inline"
                  v-model="editingCatLabel"
                  @keyup.enter="commitRenameCategory(row)"
                  @keyup.esc="editingCatId = null"
                />
                <span v-else>{{ row.label }}</span>
              </td>
              <td class="num">{{ row.dishCount }}</td>
              <td class="muted">{{ formatDateTime(row.updatedAt) }}</td>
              <td class="actions">
                <template v-if="editingCatId === row.id">
                  <button class="link" type="button" @click="commitRenameCategory(row)">
                    保存
                  </button>
                  <button class="link" type="button" @click="editingCatId = null">取消</button>
                </template>
                <button v-else class="link" type="button" @click="startRenameCategory(row)">
                  改名
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p class="foot-note">
          菜品种类是系统维度取值：改名免费（菜品存的是取值 ID，零迁移）；拖拽排序整体提交全量行。
        </p>
      </div>
    </BaseDrawer>

    <!-- 图片大图预览：挂载即打开 -->
    <ImagePreview
      v-if="previewOpen"
      :images="previewImages"
      :index="previewIndex"
      @close="previewOpen = false"
    />
  </div>
</template>

<style scoped>
/* 筛选区内控件定宽（.filters 容器样式已收敛到全局 shared.css） */
.filters .form-input {
  width: 150px;
}
.row2 {
  display: flex;
  gap: var(--space-4);
}
.row2 .field {
  flex: 1;
}
.dim-block {
  border-top: 1px dashed var(--border-light);
  margin-top: var(--space-2);
  padding-top: var(--space-2);
}
.dim-title {
  font-size: var(--font-sm);
  color: var(--text-muted);
  margin-bottom: var(--space-2);
}
/* 描述属性逐行展示（键：值），超长可断行 */
.attr-line {
  display: block;
}
.add-row {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}
.add-row .form-input {
  flex: 1;
}
.form-input.inline {
  width: 100%;
}
</style>
