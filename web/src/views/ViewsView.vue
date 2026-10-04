<script setup lang="ts">
/**
 * A6 首页筛选视图管理（页面规格见 [A6-首页筛选视图管理](../../../docs/web/A-主数据维护/A6-首页筛选视图管理.md)）。
 *
 * <p>要点：一个 tab = `key` + 文案 + 顺序 + 启停 + **默认标记** + **筛选标准（条件构建器）** + 排序口径。
 * 条件由**字段白名单**组装（后台不可写 SQL）：字段决定可选操作符，操作符决定取值控件。
 * <b>保存前可预览</b>匹配数与抽样菜名 —— 只看数字很难发现「分类键写错导致命中 0 条」。
 * 分类值（`dish.meal_type` 的取值域）由自由输入自动登记，此处只保留**抽屉级**的重命名 / 合并 / 删除。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fail } from '@/utils/error'
import { useReorder } from '@/composables/useReorder'
import {
  createView,
  deleteView,
  listViews,
  previewView,
  setDefaultView,
  sortViews,
  updateView,
} from '@/api/views'
import {
  createCategory,
  deleteCategory,
  listCategories,
  mergeCategory,
  renameCategory,
} from '@/api/categories'
import { listStalls } from '@/api/stalls'
import { listCanteens } from '@/api/canteens'
import type {
  CanteenAdminVO,
  DishCategoryAdminVO,
  DishViewAdminVO,
  DishViewCondition,
  DishViewSaveReq,
  StallAdminVO,
} from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import BaseDrawer from '@/components/BaseDrawer.vue'
import BaseModal from '@/components/BaseModal.vue'
import StateBox from '@/components/StateBox.vue'

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<DishViewAdminVO>(() => listViews())

/** 分类值 / 档口 / 食堂：条件构建器的取值来源 */
const categories = ref<DishCategoryAdminVO[]>([])
const stalls = ref<StallAdminVO[]>([])
const canteens = ref<CanteenAdminVO[]>([])

/* ==================== 字段白名单（与服务端 DishViewConditions 逐项一致） ==================== */
interface FieldMeta {
  field: string
  label: string
  ops: { op: string; label: string }[]
}
const FIELDS: FieldMeta[] = [
  { field: 'mealType', label: '菜品分类', ops: [{ op: '=', label: '等于' }, { op: 'in', label: '属于（多选）' }] },
  { field: 'discount', label: '有折扣', ops: [{ op: 'isTrue', label: '是' }] },
  {
    field: 'price',
    label: '现价（分）',
    ops: [{ op: 'between', label: '区间' }, { op: '>=', label: '不小于' }, { op: '<=', label: '不大于' }],
  },
  { field: 'stallId', label: '档口', ops: [{ op: '=', label: '等于' }, { op: 'in', label: '属于（多选）' }] },
  { field: 'canteenId', label: '食堂', ops: [{ op: '=', label: '等于' }, { op: 'in', label: '属于（多选）' }] },
  { field: 'avgRating', label: '均分', ops: [{ op: '>=', label: '不小于' }] },
  { field: 'createdAt', label: '上新', ops: [{ op: 'withinDays', label: '近 N 天' }] },
]

const SORT_KINDS = [
  { value: 'heat', label: '热度' },
  { value: 'priceAsc', label: '价格升序' },
  { value: 'priceDesc', label: '价格降序' },
  { value: 'discountDesc', label: '折扣力度' },
  { value: 'ratingDesc', label: '均分' },
  { value: 'newest', label: '上新时间' },
  { value: 'random', label: '随机（推荐流）' },
]

const fieldLabel = (field: string): string => FIELDS.find((f) => f.field === field)?.label ?? field
const opsOf = (field: string) => FIELDS.find((f) => f.field === field)?.ops ?? []
const opLabel = (field: string, op: string): string =>
  opsOf(field).find((o) => o.op === op)?.label ?? op
const sortLabel = (kind: string): string => SORT_KINDS.find((s) => s.value === kind)?.label ?? kind

/** 条件摘要（列表列，供一眼看出该 tab 在筛什么） */
function summarize(conditions: DishViewCondition[]): string {
  if (!conditions?.length) return '全部菜品'
  return conditions
    .map((c) => {
      if (c.op === 'isTrue') return `${fieldLabel(c.field)}：是`
      if (c.op === 'between') return `${fieldLabel(c.field)} ${c.values?.[0]} ~ ${c.values?.[1]}`
      if (c.op === 'in') {
        const names = (c.values ?? []).map((v) => labelOf(c.field, v))
        return `${fieldLabel(c.field)} 属于 {${names.join('、')}}`
      }
      if (c.op === 'withinDays') return `${fieldLabel(c.field)} 近 ${c.value} 天`
      return `${fieldLabel(c.field)} ${opLabel(c.field, c.op)} ${labelOf(c.field, c.value ?? '')}`
    })
    .join(' 且 ')
}

/** 键 → 展示名（分类用中文名；档口 / 食堂用名称；其余原样） */
function labelOf(field: string, key: string): string {
  if (field === 'mealType') return categories.value.find((c) => c.key === key)?.label ?? key
  if (field === 'stallId') return stalls.value.find((s) => String(s.id) === key)?.name ?? key
  if (field === 'canteenId') return canteens.value.find((c) => String(c.id) === key)?.name ?? key
  return key
}

/** 条件可选值（`mealType` / `stallId` / `canteenId` 的 `=` / `in` 控件用） */
function optionsOf(field: string): { value: string; label: string }[] {
  if (field === 'mealType') return categories.value.map((c) => ({ value: c.key, label: c.label }))
  if (field === 'stallId') {
    return stalls.value.map((s) => ({ value: String(s.id), label: `${s.canteenName} · ${s.name}` }))
  }
  if (field === 'canteenId') return canteens.value.map((c) => ({ value: String(c.id), label: c.name }))
  return []
}

/* ==================== 新建 / 编辑（抽屉） ==================== */
const open = ref(false)
const editing = ref<DishViewAdminVO | null>(null)
const saving = ref(false)
const form = ref<{
  key: string
  label: string
  sortKind: string
  enabled: boolean
  conditions: DishViewCondition[]
}>({ key: '', label: '', sortKind: 'heat', enabled: true, conditions: [] })

const title = computed(() => (editing.value ? '编辑筛选视图' : '新建筛选视图'))

const previewCount = ref<number | null>(null)
const previewSamples = ref<string[]>([])
const previewing = ref(false)

function blankCondition(): DishViewCondition {
  // 默认给一行「菜品分类 = 」，管理员改字段时 onFieldChange 会重置操作符与取值
  const meta = FIELDS[0]
  return {
    field: meta?.field ?? 'mealType',
    op: meta?.ops[0]?.op ?? '=',
    value: '',
    values: [],
  }
}

function openCreate(): void {
  editing.value = null
  form.value = { key: '', label: '', sortKind: 'heat', enabled: true, conditions: [] }
  resetPreview()
  open.value = true
}

function openEdit(row: DishViewAdminVO): void {
  editing.value = row
  form.value = {
    key: row.key,
    label: row.label,
    sortKind: row.sortKind,
    enabled: row.enabled,
    conditions: (row.conditions ?? []).map((c) => ({ ...c, values: c.values ? [...c.values] : [] })),
  }
  resetPreview()
  open.value = true
}

function resetPreview(): void {
  previewCount.value = null
  previewSamples.value = []
}

function addCondition(): void {
  form.value.conditions.push(blankCondition())
  resetPreview()
}

function removeCondition(index: number): void {
  form.value.conditions.splice(index, 1)
  resetPreview()
}

/** 切字段：操作符与取值形态随之重置（避免留下「旧字段的取值」这种脏条件） */
function onFieldChange(condition: DishViewCondition): void {
  const ops = opsOf(condition.field)
  condition.op = ops[0]?.op ?? ''
  condition.value = ''
  condition.values = []
  resetPreview()
}

function onOpChange(condition: DishViewCondition): void {
  condition.value = ''
  condition.values = []
  resetPreview()
}

/** `in` 多选（`<select multiple>` 绑 `values`） */
function onMultiChange(condition: DishViewCondition, event: Event): void {
  const target = event.target as HTMLSelectElement
  condition.values = Array.from(target.selectedOptions).map((o) => o.value)
  resetPreview()
}

async function runPreview(): Promise<void> {
  previewing.value = true
  try {
    const res = await previewView(form.value as DishViewSaveReq)
    previewCount.value = res.matchedCount
    previewSamples.value = res.sampleNames
  } catch (e) {
    // 条件不在白名单 → 后端原文（400）
    fail(e, '预览失败')
  } finally {
    previewing.value = false
  }
}

async function save(): Promise<void> {
  if (!form.value.key.trim() || !form.value.label.trim()) {
    ElMessage.warning('请填写视图键与 tab 文案')
    return
  }
  // 空取值行会带出一堆语义不明的条件 —— 提交前拦掉，错误定位到行
  for (const [i, c] of form.value.conditions.entries()) {
    if (c.op === 'isTrue') continue
    if (c.op === 'between' && (!c.values?.[0] || !c.values?.[1])) {
      ElMessage.warning(`第 ${i + 1} 行条件缺少取值`)
      return
    }
    if (c.op === 'in' && !c.values?.length) {
      ElMessage.warning(`第 ${i + 1} 行条件未选择取值`)
      return
    }
    if (c.op !== 'between' && c.op !== 'in' && !c.value) {
      ElMessage.warning(`第 ${i + 1} 行条件缺少取值`)
      return
    }
  }
  saving.value = true
  try {
    const req = form.value as DishViewSaveReq
    if (editing.value) await updateView(editing.value.id, req)
    else await createView(req)
    ElMessage.success(editing.value ? '已保存（保存即生效）' : '已新建（如需默认请点「设为默认」）')
    open.value = false
    await load()
  } catch (e) {
    // 键重名 / 改键 / 停用默认视图 / 条件不在白名单 → 后端原文
    fail(e, '保存失败')
  } finally {
    saving.value = false
  }
}

/* ==================== 设默认 / 删除 / 拖拽排序 ==================== */
async function makeDefault(row: DishViewAdminVO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认把「${row.label}」设为默认视图？端上不带 view 参数时将落到它（原默认自动取消），且它**不可删除、不可停用**。`,
      '设为默认',
      { type: 'warning', confirmButtonText: '设默认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await setDefaultView(row.id)
    ElMessage.success('已设为默认')
    await load()
  } catch (e) {
    fail(e)
  }
}

async function remove(row: DishViewAdminVO): Promise<void> {
  const hint = row.isDefault
    ? '默认视图不可删除（请先把默认切到别的视图）。'
    : `确认删除视图「${row.label}」？删除后首页筛选栏不再出现该 tab。`
  try {
    await ElMessageBox.confirm(hint, '删除视图', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteView(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    fail(e, '删除失败')
  }
}

const { dragIndex, onDragStart, onDrop } = useReorder(items, sortViews, load)

/* ==================== 分类值管理（抽屉：重命名 / 合并 / 删除） ==================== */
const catOpen = ref(false)
const catLoading = ref(false)
const newCatKey = ref('')
const newCatLabel = ref('')
const renameId = ref<number | null>(null)
const renameLabel = ref('')
const mergeFromId = ref<number | null>(null)
const mergeToId = ref<number | null>(null)

async function openCategories(): Promise<void> {
  catOpen.value = true
  await loadCategories()
}

async function loadCategories(): Promise<void> {
  catLoading.value = true
  try {
    categories.value = await listCategories()
  } catch (e) {
    fail(e, '分类值加载失败')
  } finally {
    catLoading.value = false
  }
}

async function addCategory(): Promise<void> {
  if (!newCatKey.value.trim() || !newCatLabel.value.trim()) {
    ElMessage.warning('请填写分类键与分类名')
    return
  }
  try {
    await createCategory(newCatKey.value.trim(), newCatLabel.value.trim())
    newCatKey.value = ''
    newCatLabel.value = ''
    ElMessage.success('已登记')
    await loadCategories()
  } catch (e) {
    fail(e, '登记失败')
  }
}

function startRename(row: DishCategoryAdminVO): void {
  renameId.value = row.id
  renameLabel.value = row.label
}

async function commitRename(row: DishCategoryAdminVO): Promise<void> {
  if (!renameLabel.value.trim()) return
  try {
    await renameCategory(row.id, renameLabel.value.trim())
    renameId.value = null
    ElMessage.success('已改名（全站菜品分类同步生效）')
    await loadCategories()
    await load()
  } catch (e) {
    fail(e, '改名失败')
  }
}

async function commitMerge(): Promise<void> {
  if (!mergeFromId.value || !mergeToId.value) {
    ElMessage.warning('请选择源分类与目标分类')
    return
  }
  const from = categories.value.find((c) => c.id === mergeFromId.value)
  const to = categories.value.find((c) => c.id === mergeToId.value)
  try {
    await ElMessageBox.confirm(
      `确认把「${from?.label}」的**全部菜品**改归「${to?.label}」，并删除「${from?.label}」？此操作会改写菜品数据，不可撤销。`,
      '合并分类值',
      { type: 'warning', confirmButtonText: '合并', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await mergeCategory(mergeFromId.value, mergeToId.value)
    mergeFromId.value = null
    mergeToId.value = null
    ElMessage.success('已合并')
    await loadCategories()
    await load()
  } catch (e) {
    fail(e, '合并失败')
  }
}

async function removeCategory(row: DishCategoryAdminVO): Promise<void> {
  const hint =
    row.dishCount > 0
      ? `该分类下有 ${row.dishCount} 个菜品，**不能删除**（请先合并到其它分类）。`
      : `确认删除分类「${row.label}」？`
  try {
    await ElMessageBox.confirm(hint, '删除分类值', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteCategory(row.id)
    ElMessage.success('已删除')
    await loadCategories()
  } catch (e) {
    fail(e, '删除失败')
  }
}

onMounted(async () => {
  // 取值来源（分类值 / 档口 / 食堂）失败不阻塞列表
  try {
    const [cs, ss, cs2] = await Promise.all([listCategories(), listStalls(), listCanteens()])
    categories.value = cs
    stalls.value = ss
    canteens.value = cs2
  } catch {
    /* 条件构建器的下拉可能为空，列表仍可用 */
  }
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>首页筛选视图</h2>
      <div class="head-actions">
        <button class="btn-secondary" type="button" @click="openCategories">管理分类值</button>
        <button class="btn-primary" type="button" v-press @click="openCreate">新建视图</button>
      </div>
    </div>

    <!-- 六态：① 加载 ② 错误 ③ 空 ④ 有数据 ⑤ 排序 ⑥ 会话失效 -->
    <StateBox v-if="firstLoading" status="loading" />
    <StateBox v-else-if="sessionInvalid" status="session" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="load" />
    <StateBox v-else-if="isEmpty" status="empty" message="暂无筛选视图" />
    <div v-else-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th class="drag-col"></th>
            <th>tab 文案</th>
            <th>视图键</th>
            <th>筛选标准</th>
            <th>排序</th>
            <th>匹配</th>
            <th>状态</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(row, index) in items"
            :key="row.id"
            draggable="true"
            @dragstart="onDragStart(index)"
            @dragover.prevent
            @drop="onDrop(index)"
          >
            <td class="drag-col" title="拖拽排序">⋮⋮</td>
            <td>
              {{ row.label }}
              <span v-if="row.isDefault" class="tag badge">默认</span>
            </td>
            <td><code>{{ row.key }}</code></td>
            <td class="ellipsis">{{ summarize(row.conditions) }}</td>
            <td>{{ sortLabel(row.sortKind) }}</td>
            <td class="num">{{ row.matchedCount }}</td>
            <td>
              <span :class="row.enabled ? 'tag badge-on' : 'tag badge'">
                {{ row.enabled ? '启用' : '停用' }}
              </span>
            </td>
            <td class="actions">
              <button class="link" type="button" @click="openEdit(row)">编辑</button>
              <button v-if="!row.isDefault" class="link" type="button" @click="makeDefault(row)">
                设为默认
              </button>
              <button class="link danger" type="button" @click="remove(row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="foot-note">
        下发规则：**启用中**且**匹配数不为 0** 的视图才会出现在首页筛选栏；**默认视图恒下发**（不可删除、不可停用）。
      </p>
    </div>

    <!-- 编辑抽屉：文案 / 键 / 条件构建器 / 排序 / 启停 / 预览 -->
    <BaseDrawer :title="title" :open="open" width="720px" @close="open = false">
      <div class="field">
        <label>tab 文案</label>
        <input class="form-input" v-model="form.label" placeholder="如 面食粉类" />
      </div>
      <div class="field">
        <label>视图键</label>
        <input
          class="form-input"
          v-model="form.key"
          :disabled="!!editing"
          placeholder="小写字母 / 数字 / -（如 noodle）"
        />
        <div class="hint">端上回传 `view=&lt;key&gt;`；**在用后不可修改**（改键会让端上缓存的视图全部对不上）</div>
      </div>

      <div class="field">
        <label>筛选标准（AND 组合；留空 = 全部菜品）</label>
        <div v-for="(c, i) in form.conditions" :key="i" class="cond-row">
          <select class="form-input" v-model="c.field" @change="onFieldChange(c)">
            <option v-for="f in FIELDS" :key="f.field" :value="f.field">{{ f.label }}</option>
          </select>
          <select class="form-input op" v-model="c.op" @change="onOpChange(c)">
            <option v-for="o in opsOf(c.field)" :key="o.op" :value="o.op">{{ o.label }}</option>
          </select>

          <!-- 取值控件随「字段 + 操作符」变化 -->
          <template v-if="c.op === 'isTrue'">
            <span class="no-value">（无需取值）</span>
          </template>
          <template v-else-if="c.op === 'between'">
            <input class="form-input num-input" type="number" v-model="c.values![0]" placeholder="下界" />
            <span class="tilde">~</span>
            <input class="form-input num-input" type="number" v-model="c.values![1]" placeholder="上界" />
          </template>
          <template v-else-if="c.op === 'in'">
            <select class="form-input grow" multiple size="3" @change="onMultiChange(c, $event)">
              <option v-for="o in optionsOf(c.field)" :key="o.value" :value="o.value" :selected="c.values?.includes(o.value)">
                {{ o.label }}
              </option>
            </select>
          </template>
          <template v-else-if="optionsOf(c.field).length">
            <select class="form-input grow" v-model="c.value">
              <option value="">请选择</option>
              <option v-for="o in optionsOf(c.field)" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </template>
          <template v-else>
            <input
              class="form-input grow"
              :type="c.field === 'avgRating' || c.field === 'createdAt' || c.field === 'price' ? 'number' : 'text'"
              v-model="c.value"
              :placeholder="c.field === 'createdAt' ? '天数（如 7）' : '取值'"
            />
          </template>

          <button class="link danger" type="button" @click="removeCondition(i)">移除</button>
        </div>
        <div class="cond-actions">
          <button class="btn-secondary" type="button" @click="addCondition">＋ 添加条件</button>
          <button class="btn-secondary" type="button" :disabled="previewing" @click="runPreview">
            {{ previewing ? '试算中…' : '预览匹配数' }}
          </button>
        </div>
        <div v-if="previewCount !== null" class="preview">
          当前条件匹配 <b>{{ previewCount }}</b> 个在售菜品
          <span v-if="previewSamples.length">（示例：{{ previewSamples.join('、') }}）</span>
        </div>
      </div>

      <div class="field">
        <label>排序口径</label>
        <select class="form-input" v-model="form.sortKind">
          <option v-for="s in SORT_KINDS" :key="s.value" :value="s.value">{{ s.label }}</option>
        </select>
      </div>
      <div class="field">
        <label class="check-line">
          <input type="checkbox" v-model="form.enabled" />
          <span>在首页筛选栏展示（默认视图不可停用）</span>
        </label>
      </div>

      <template #actions>
        <button class="btn-secondary" type="button" @click="open = false">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" v-press @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </BaseDrawer>

    <!-- 分类值管理抽屉：登记 / 改名 / 合并 / 删除 -->
    <BaseDrawer title="分类值管理" :open="catOpen" @close="catOpen = false">
      <div class="add-row">
        <input class="form-input" v-model="newCatKey" placeholder="分类键（英文小写）" />
        <input class="form-input" v-model="newCatLabel" placeholder="分类名（如 面食粉类）" />
        <button class="btn-primary" type="button" v-press @click="addCategory">登记</button>
      </div>

      <StateBox v-if="catLoading" status="loading" />
      <StateBox v-else-if="!categories.length" status="empty" message="暂无分类值" />
      <table v-else class="table table--compact">
        <thead>
          <tr>
            <th>分类名</th>
            <th>分类键</th>
            <th>菜品数</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in categories" :key="c.id">
            <td>
              <input
                v-if="renameId === c.id"
                class="form-input inline"
                v-model="renameLabel"
                @keyup.enter="commitRename(c)"
              />
              <span v-else>{{ c.label }}</span>
            </td>
            <td><code>{{ c.key }}</code></td>
            <td class="num">{{ c.dishCount }}</td>
            <td class="actions">
              <template v-if="renameId === c.id">
                <button class="link" type="button" @click="commitRename(c)">保存</button>
                <button class="link" type="button" @click="renameId = null">取消</button>
              </template>
              <template v-else>
                <button class="link" type="button" @click="startRename(c)">改名</button>
                <button class="link danger" type="button" @click="removeCategory(c)">删除</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="merge-block">
        <div class="dim-title">合并同义分类（唯一手段：把源分类的菜品改指目标后删除源分类）</div>
        <div class="add-row">
          <select class="form-input" v-model.number="mergeFromId">
            <option :value="null">源分类</option>
            <option v-for="c in categories" :key="c.id" :value="c.id">
              {{ c.label }}（{{ c.dishCount }} 个菜品）
            </option>
          </select>
          <select class="form-input" v-model.number="mergeToId">
            <option :value="null">目标分类</option>
            <option v-for="c in categories" :key="`to-${c.id}`" :value="c.id">{{ c.label }}</option>
          </select>
          <button class="btn-secondary" type="button" @click="commitMerge">合并</button>
        </div>
      </div>
    </BaseDrawer>
  </div>
</template>

<style scoped>
.head-actions {
  display: flex;
  gap: var(--space-3);
}
.drag-col {
  width: 28px;
  color: var(--text-muted);
  cursor: grab;
  text-align: center;
}
.num {
  font-variant-numeric: tabular-nums;
}
.tag.badge {
  background: var(--bg-gray);
  color: var(--text-secondary);
}
.tag.badge-on {
  background: var(--color-success-bg);
  color: var(--color-success);
}
.tag {
  display: inline-block;
  margin-left: var(--space-1);
  padding: 1px var(--space-2);
  border-radius: var(--radius-pill);
  font-size: var(--font-xs);
}
.hint {
  margin-top: var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-xs);
}
.foot-note {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  color: var(--text-muted);
  font-size: var(--font-xs);
  border-top: 1px solid var(--table-border);
}
.cond-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}
.cond-row .op {
  width: 130px;
}
.cond-row .grow {
  flex: 1;
}
.cond-row .num-input {
  width: 90px;
}
.tilde,
.no-value {
  color: var(--text-muted);
  font-size: var(--font-sm);
}
.cond-actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-2);
}
.preview {
  margin-top: var(--space-3);
  padding: var(--space-3);
  background: var(--bg-soft);
  border-radius: var(--radius);
  color: var(--text-secondary);
  font-size: var(--font-sm);
}
.add-row {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}
.add-row .form-input {
  flex: 1;
}
.form-input.inline {
  width: 100%;
}
.merge-block {
  margin-top: var(--space-5);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border-light);
}
.dim-title {
  font-size: var(--font-sm);
  color: var(--text-muted);
  margin-bottom: var(--space-2);
}
.check-line {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: var(--weight-regular);
}
</style>
