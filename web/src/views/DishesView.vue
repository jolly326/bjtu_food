<script setup lang="ts">
/**
 * A3 菜品管理（页面规格见 [菜品管理.md](../../../docs/ui/web/菜品管理.md)）。
 *
 * <p>要点：分页（页码 + 共 N 条）+ 六态；编辑载体 = **抽屉**（含图片与动态属性子表单 ⇒ 基线 §1.10 判据）；
 * 状态列 **`kind="dish"`**（在售 / 已下架）；上下架走独立端点且**显式传目标状态**；
 * 归属只认 `stallId`（实体下拉，按名 upsert 已移除）；属性值**可直接填写中文**（未命中由服务端登记并替换为 ID）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
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
import { listCategories } from '@/api/categories'
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
import BaseDrawer from '@/components/BaseDrawer.vue'
import BaseModal from '@/components/BaseModal.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'

const canteens = ref<CanteenAdminVO[]>([])
const stalls = ref<StallAdminVO[]>([])
const dimensions = ref<DishDimensionAdminVO[]>([])
/** A6 分类值字典：分类**筛选下拉**与「分类中文名」展示的真源 */
const categories = ref<DishCategoryAdminVO[]>([])
/**
 * 取值字典（按维度 `fieldKey` 分组）—— A4 落地后**属性的候选真源**。
 *
 * <p>表单用 `input` + `datalist`：既给出字典建议，又保留「**自由输入新值**」能力
 * （服务端在同维度内未命中即**自动登记**为新取值，见 A4 的「灵活取值」）。
 */
const valuesByFieldKey = ref<Record<string, DishValueAdminVO[]>>({})

/* ==================== 列表 ==================== */
const fKeyword = ref('')
const fCanteenId = ref(0)
const fStallId = ref(0)
const fMealType = ref('')
const fStatus = ref<OnOffStatus | ''>('')

function params(): DishListParams {
  return {
    keyword: fKeyword.value || undefined,
    canteenId: fCanteenId.value || undefined,
    stallId: fStallId.value || undefined,
    mealType: fMealType.value || undefined,
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

/* ==================== 新增 / 编辑（抽屉） ==================== */
const open = ref(false)
const editing = ref<DishAdminVO | null>(null)
const saving = ref(false)
const form = ref({
  name: '',
  stallId: 0,
  priceYuan: '',
  originalPriceYuan: '',
  mealType: '',
  description: '',
  images: [] as string[],
})
const attrSingle = ref<Record<string, string>>({})
const attrMulti = ref<Record<string, string>>({})

const drawerTitle = computed(() => (editing.value ? '编辑菜品' : '新建菜品'))

function resetForm(): void {
  form.value = {
    name: '',
    stallId: stalls.value[0]?.id ?? 0,
    priceYuan: '',
    originalPriceYuan: '',
    mealType: '',
    description: '',
    images: [],
  }
  attrSingle.value = {}
  attrMulti.value = {}
}

function openCreate(): void {
  editing.value = null
  resetForm()
  open.value = true
}

async function openEdit(row: DishAdminListItemVO): Promise<void> {
  // A3：列表行是**瘦身 VO**（不带 description / images / attributes），而 `PUT` 是**整体替换**
  // ⇒ 必须先用详情端点取全字段回填；否则保存会把这些字段当成「未传 / 空」而清空（不可逆）。
  try {
    const d = await getDish(row.id)
    editing.value = d
    form.value = {
      name: d.name,
      stallId: d.stallId,
      priceYuan: String(fenToYuan(d.price)),
      originalPriceYuan: d.originalPrice == null ? '' : String(fenToYuan(d.originalPrice)),
      mealType: d.mealType,
      description: d.description,
      images: [...d.images],
    }
    attrSingle.value = {}
    attrMulti.value = {}
    for (const dim of dimensions.value) {
      const v = d.attributes?.[dim.fieldKey]
      if (Array.isArray(v)) attrMulti.value[dim.fieldKey] = v.join('、')
      else if (typeof v === 'string') attrSingle.value[dim.fieldKey] = v
    }
    open.value = true
  } catch (e) {
    fail(e, '加载菜品详情失败')
  }
}

function buildAttributes(): Record<string, string | string[]> {
  const out: Record<string, string | string[]> = {}
  for (const d of dimensions.value) {
    if (d.valueType === 'single') {
      const v = (attrSingle.value[d.fieldKey] ?? '').trim()
      if (v) out[d.fieldKey] = v
    } else {
      const arr = (attrMulti.value[d.fieldKey] ?? '')
        .split(/[、,]/)
        .map((s) => s.trim())
        .filter(Boolean)
      if (arr.length) out[d.fieldKey] = arr
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
  if (!form.value.mealType.trim()) {
    ElMessage.warning('请填写菜品分类')
    return
  }
  const price = yuanToFen(form.value.priceYuan)
  if (price <= 0) {
    ElMessage.warning('现价须大于 0')
    return
  }
  const originalPrice = form.value.originalPriceYuan ? yuanToFen(form.value.originalPriceYuan) : null
  if (originalPrice != null && originalPrice <= price) {
    ElMessage.warning('原价须大于现价')
    return
  }
  const req: DishSaveReq = {
    name: form.value.name.trim(),
    stallId: form.value.stallId,
    price,
    originalPrice,
    mealType: form.value.mealType.trim(),
    description: form.value.description,
    images: form.value.images,
    attributes: buildAttributes(),
  }
  saving.value = true
  try {
    if (editing.value) await updateDish(editing.value.id, req)
    else await createDish(req)
    ElMessage.success(editing.value ? '已保存' : '已新建')
    open.value = false
    await reload()
  } catch (e) {
    fail(e, '保存失败')
  } finally {
    saving.value = false
  }
}

/* ==================== 复制 ==================== */
const copyOpen = ref(false)
// 复制的源就是**列表行**（复制只用到 id 与 name，无需取详情）
const copySource = ref<DishAdminListItemVO | null>(null)
const copyName = ref('')
const copying = ref(false)

function openCopy(row: DishAdminListItemVO): void {
  copySource.value = row
  copyName.value = `${row.name}（副本）`
  copyOpen.value = true
}

async function submitCopy(): Promise<void> {
  const src = copySource.value
  if (!src) return
  if (!copyName.value.trim()) {
    ElMessage.warning('请输入新菜名')
    return
  }
  copying.value = true
  try {
    await copyDish(src.id, copyName.value.trim())
    ElMessage.success('已复制（副本默认下架，确认内容后再上架）')
    copyOpen.value = false
    await reload()
  } catch (e) {
    fail(e, '复制失败')
  } finally {
    copying.value = false
  }
}

/* ==================== 上下架 / 删除 ==================== */
async function toggle(row: DishAdminListItemVO): Promise<void> {
  const next: OnOffStatus = row.status === 'on' ? 'off' : 'on'
  try {
    await updateDishStatus(row.id, next)
    ElMessage.success(next === 'on' ? '已上架' : '已下架')
    await reload()
  } catch (e) {
    fail(e)
  }
}

async function remove(row: DishAdminListItemVO): Promise<void> {
  try {
    await confirmDelete(`确认删除菜品「${row.name}」？其**全部评价将一并删除**且不可恢复。`, {
      title: '删除菜品',
    })
  } catch {
    return
  }
  try {
    await deleteDish(row.id)
    ElMessage.success('已删除')
    await reload()
  } catch (e) {
    fail(e, '删除失败')
  }
}

function reset(): void {
  fKeyword.value = ''
  fCanteenId.value = 0
  fStallId.value = 0
  fMealType.value = ''
  fStatus.value = ''
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
    // 各维度的取值字典：逐个拉取后按 fieldKey 归组（供 datalist 建议）
    const grouped = await Promise.all(
      ds.map(async (d) => [d.fieldKey, await listValues(d.id)] as const),
    )
    valuesByFieldKey.value = Object.fromEntries(grouped)
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
      <button class="btn-primary" type="button" v-press @click="openCreate">新建菜品</button>
    </div>

    <div class="card filters">
      <input class="form-input" v-model="fKeyword" placeholder="菜品名" @keyup.enter="reloadFirstPage" />
      <select class="form-input" v-model.number="fCanteenId" @change="reloadFirstPage">
        <option :value="0">全部食堂</option>
        <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <select class="form-input" v-model.number="fStallId" @change="reloadFirstPage">
        <option :value="0">全部档口</option>
        <option v-for="s in filteredStalls" :key="s.id" :value="s.id">{{ s.name }}</option>
      </select>
      <!-- A6 分类值字典驱动的下拉：筛选值 = 分类键（管理员从下拉选取合法键） -->
      <select class="form-input" v-model="fMealType" @change="reloadFirstPage">
        <option value="">全部分类</option>
        <option v-for="c in categories" :key="c.key" :value="c.key">{{ c.label }}</option>
      </select>
      <select class="form-input" v-model="fStatus" @change="reloadFirstPage">
        <option value="">全部状态</option>
        <option value="on">在售</option>
        <option value="off">已下架</option>
      </select>
      <button class="btn-primary" type="button" v-press @click="reloadFirstPage">查询</button>
      <button class="btn-secondary" type="button" @click="reset">重置</button>
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
            <th>封面</th>
            <th>名称</th>
            <th>现价</th>
            <th>原价</th>
            <th>食堂 / 档口</th>
            <th>分类</th>
            <th>状态</th>
            <th>评分</th>
            <th title="近 30 天浏览量（滚动窗口统计，非历史累计）">近 30 天浏览</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>
              <img v-if="row.coverImage" :src="row.coverImage" class="mini-cover" alt="" />
              <span v-else class="muted">无图</span>
            </td>
            <td>{{ row.name }}</td>
            <td class="num">{{ formatYuan(row.price) }}</td>
            <td class="num">{{ row.originalPrice == null ? '—' : formatYuan(row.originalPrice) }}</td>
            <td>{{ row.canteenName }} / {{ row.stallName }}</td>
            <td>{{ row.mealTypeLabel || row.mealType || '—' }}</td>
            <td><StatusTag :status="row.status" kind="dish" /></td>
            <td class="num">
              {{ row.avgRating ?? '—' }}
              <span v-if="row.ratingCount" class="muted">（{{ row.ratingCount }}）</span>
            </td>
            <td class="num">
              <!-- 无浏览记录时后端补 0（非「—」）⇒ 显式区分「0 次」与「无数据」 -->
              {{ row.recentViewCount ?? 0 }}
            </td>
            <td class="actions">
              <button class="link" type="button" @click="openEdit(row)">编辑</button>
              <button class="link" type="button" @click="openCopy(row)">复制</button>
              <button class="link" type="button" @click="toggle(row)">
                {{ row.status === 'on' ? '下架' : '上架' }}
              </button>
              <button class="link danger" type="button" @click="remove(row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>

      <Pager :total="total" :page="page" :page-count="pageCount" @prev="prevPage" @next="nextPage" />
    </div>

    <!-- 编辑：含图片 + 动态属性子表单 ⇒ 抽屉 -->
    <BaseDrawer :title="drawerTitle" :open="open" @close="open = false">
      <div class="field">
        <label for="dish-name">菜品名称</label>
        <input id="dish-name" class="form-input" v-model="form.name" placeholder="菜品名称" />
      </div>
      <div class="field">
        <label for="dish-stall">所属档口</label>
        <select id="dish-stall" class="form-input" v-model.number="form.stallId">
          <option :value="0" disabled>请选择</option>
          <option v-for="s in stalls" :key="s.id" :value="s.id">{{ s.canteenName }} · {{ s.name }}</option>
        </select>
      </div>
      <div class="row2">
        <div class="field">
          <label for="dish-price">现价（元）</label>
          <input id="dish-price" class="form-input" type="number" step="0.01" v-model="form.priceYuan" />
        </div>
        <div class="field">
          <label for="dish-original-price">原价（元，可空）</label>
          <input id="dish-original-price" class="form-input" type="number" step="0.01" v-model="form.originalPriceYuan" />
        </div>
      </div>
      <div class="field">
        <label for="dish-meal-type">菜品分类</label>
        <input id="dish-meal-type" class="form-input" v-model="form.mealType" placeholder="如 主食 / 饮品；可填新分类，保存时自动登记" />
      </div>
      <div class="field">
        <label for="dish-description">描述</label>
        <textarea id="dish-description" class="form-textarea" v-model="form.description" rows="2" />
      </div>
      <div class="field">
        <label>配图（首图为封面，必填，≤5 张）</label>
        <ImageUpload v-model="form.images" :max="5" :aria-label="'配图'" />
      </div>
      <div v-if="dimensions.length" class="dim-block">
        <div class="dim-title">描述属性（下拉给出字典建议；填新值保存时自动登记）</div>
        <div class="field" v-for="d in dimensions" :key="d.id">
          <label>{{ d.name }}{{ d.valueType === 'multi' ? '（多值）' : '' }}</label>
          <!-- 字典建议 + 自由录入：value 为中文，服务端同维度内未命中即自动登记为新取值。
               v-model 必须是成员表达式 ⇒ 单值 / 多值分列两个输入（不用三元写法）。 -->
          <input
            v-if="d.valueType === 'single'"
            class="form-input"
            :list="`dim-${d.fieldKey}`"
            placeholder="选择或输入新值"
            v-model="attrSingle[d.fieldKey]"
          />
          <input
            v-else
            class="form-input"
            :list="`dim-${d.fieldKey}`"
            placeholder="多个值用顿号分隔"
            v-model="attrMulti[d.fieldKey]"
          />
          <datalist :id="`dim-${d.fieldKey}`">
            <option v-for="v in valuesByFieldKey[d.fieldKey] ?? []" :key="v.id" :value="v.label" />
          </datalist>
        </div>
      </div>
      <template #actions>
        <button class="btn-secondary" type="button" @click="open = false">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" v-press @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </BaseDrawer>

    <!-- 复制：只填新菜名（其余字段复制源菜品，副本默认下架） -->
    <BaseModal title="复制菜品" :open="copyOpen" @close="copyOpen = false">
      <div class="field">
        <label for="copy-name">新菜品名称</label>
        <input id="copy-name" class="form-input" v-model="copyName" @keyup.enter="submitCopy" />
      </div>
      <p class="hint">其余字段（价格 / 分类 / 属性 / 图片）全部复制源菜品；**副本默认下架**，确认内容后再上架。</p>
      <template #actions>
        <button class="btn-secondary" type="button" @click="copyOpen = false">取消</button>
        <button class="btn-primary" type="button" :disabled="copying" v-press @click="submitCopy">
          {{ copying ? '复制中…' : '复制' }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding: var(--space-4);
  margin-bottom: var(--space-4);
}
.filters .form-input {
  width: 150px;
}
.mini-cover {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-sm);
  object-fit: cover;
  display: block;
}
.num {
  font-variant-numeric: tabular-nums;
}
.muted {
  color: var(--text-muted);
  font-size: var(--font-sm);
}
.hint {
  color: var(--text-muted);
  font-size: var(--font-sm);
  margin: 0;
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
</style>
