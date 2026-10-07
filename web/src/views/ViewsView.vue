<script setup lang="ts">
/**
 * A6 首页筛选视图管理（页面规格见 [首页筛选视图.md](../../../docs/ui/web/首页筛选视图.md)）。
 *
 * <p>视图是**纯数据**：文案 / 顺序 / 启停 / **筛选条件** / **排序口径**全部落库，
 * 本页支持**新建 / 编辑 / 启用停用 / 删除 / 拖拽排序**（全部免发版）。
 * 条件与排序口径受后端白名单约束（见 [api/web/views.md](../../../docs/api/web/views.md)），
 * 端上只负责把它们组装成结构化载荷；字段 / 操作符 / 取值形态的最终校验以服务端 `400` 原文为准。
 *
 * <p>表格**只放基础列**（tab 文案 / 条件摘要 / 匹配 / 状态 / 更新时间 + 拖拽手柄）；
 * 顺序位与**全部操作**都在**详情抽屉**内。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { formatDateTime } from '@/utils/datetime'
import { fail } from '@/utils/error'
import { confirmDelete } from '@/utils/confirm'
import { useReorder } from '@/composables/useReorder'
import { createView, deleteView, listViews, sortViews, updateView } from '@/api/views'
import { listCategories } from '@/api/categories'
import { listCanteens } from '@/api/canteens'
import { listStalls } from '@/api/stalls'
import type {
  CanteenAdminVO,
  DishCategoryAdminVO,
  DishSortKind,
  DishViewAdminVO,
  DishViewCondition,
  DishViewField,
  StallAdminVO,
} from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import BaseDrawer from '@/components/BaseDrawer.vue'
import ListState from '@/components/ListState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useRowAction } from '@/composables/useRowAction'

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<DishViewAdminVO>(() => listViews())

/* ==================== 条件字段 / 操作符元数据（与后端白名单同构） ==================== */

/** 取值控件形态：无值 / 数值 / 区间 / 天数 / 实体下拉 */
type ValueKind = 'valueless' | 'number' | 'range' | 'days' | 'dishKind' | 'stall' | 'canteen'

interface FieldMeta {
  label: string
  ops: string[]
  kind: ValueKind
}

const FIELD_META: Record<DishViewField, FieldMeta> = {
  mealTypeId: { label: '种类', ops: ['=', 'in'], kind: 'dishKind' },
  discount: { label: '有折扣', ops: ['isTrue'], kind: 'valueless' },
  price: { label: '现价（分）', ops: ['between', '>=', '<='], kind: 'range' },
  stallId: { label: '档口', ops: ['=', 'in'], kind: 'stall' },
  canteenId: { label: '食堂', ops: ['=', 'in'], kind: 'canteen' },
  avgRating: { label: '均分', ops: ['>='], kind: 'number' },
  createdAt: { label: '上新', ops: ['withinDays'], kind: 'days' },
}

const FIELD_OPTIONS = (Object.keys(FIELD_META) as DishViewField[]).map((field) => ({
  value: field,
  label: FIELD_META[field].label,
}))

const SORT_KIND_OPTIONS: { value: DishSortKind; label: string }[] = [
  { value: 'random', label: '会话种子随机' },
  { value: 'priceAsc', label: '现价升序' },
  { value: 'priceDesc', label: '现价降序' },
  { value: 'discountDesc', label: '折扣力度' },
  { value: 'ratingDesc', label: '均分降序' },
  { value: 'newest', label: '上新倒序' },
]

const OP_LABEL: Record<string, string> = {
  '=': '等于',
  in: '属于',
  isTrue: '成立',
  between: '区间',
  '>=': '不小于',
  '<=': '不大于',
  withinDays: '近 N 天',
}

/** 编辑态的一行条件（UI 形态；提交时收敛为契约的 `{ field, op, value? , values? }`） */
interface ConditionRow {
  field: DishViewField | ''
  op: string
  /** 单值（`=` / `>=` / `<=` / `withinDays`；`in` 时不用） */
  value: string
  /** 多值（`in`） */
  values: string[]
  /** 区间下界 / 上界（`between`） */
  low: string
  high: string
}

function emptyRow(): ConditionRow {
  const field: DishViewField = 'mealTypeId'
  return { field, op: FIELD_META[field].ops[0] ?? '', value: '', values: [], low: '', high: '' }
}

/** 字段 / 操作符候选（随行内已选字段联动） */
function opsOf(row: ConditionRow): string[] {
  return row.field ? FIELD_META[row.field].ops : []
}

function kindOf(row: ConditionRow): ValueKind | null {
  return row.field ? FIELD_META[row.field].kind : null
}

/* ==================== 实体候选（种类 / 档口 / 食堂下拉的数据源） ==================== */
const categories = ref<DishCategoryAdminVO[]>([])
const stalls = ref<StallAdminVO[]>([])
const canteens = ref<CanteenAdminVO[]>([])

const kindOptions = computed(() =>
  categories.value.map((c) => ({ value: String(c.id), label: c.label })),
)
const stallOptions = computed(() =>
  stalls.value.map((s) => ({ value: String(s.id), label: `${s.canteenName} · ${s.name}` })),
)
const canteenOptions = computed(() =>
  canteens.value.map((c) => ({ value: String(c.id), label: c.name })),
)

function optionsOf(row: ConditionRow): { value: string; label: string }[] {
  switch (kindOf(row)) {
    case 'dishKind':
      return kindOptions.value
    case 'stall':
      return stallOptions.value
    case 'canteen':
      return canteenOptions.value
    default:
      return []
  }
}

/** ID 类取值的中文名（条件摘要与详情展示用；查不到回落 `#id`） */
function labelOfValue(field: DishViewField | '', raw: string): string {
  const options = optionsOf({ ...emptyRow(), field })
  const hit = options.find((o) => o.value === raw)
  return hit ? hit.label : `#${raw}`
}

/** 条件摘要（列表列 / 详情与只读展示共用；端上只做「可读化」，不改变语义） */
function describeCondition(c: DishViewCondition): string {
  const meta = FIELD_META[c.field as DishViewField]
  const name = meta ? meta.label : c.field
  switch (c.op) {
    case 'isTrue':
      return name
    case 'in': {
      const labels = (c.values ?? []).map((v) => labelOfValue(c.field, v))
      return `${name} ∈ {${labels.join('、')}}`
    }
    case 'between': {
      const values = c.values ?? []
      return `${name} ${values[0] ?? ''}~${values[1] ?? ''}`
    }
    case 'withinDays':
      return `${name} 近 ${c.value ?? ''} 天`
    case '>=':
    case '<=':
      return `${name} ${c.op === '>=' ? '≥' : '≤'} ${c.value ?? ''}`
    default:
      return `${name} = ${isIdField(c.field) ? labelOfValue(c.field, c.value ?? '') : (c.value ?? '')}`
  }
}

/** 取值是实体 ID（摘要展示时换成中文名）的字段 */
function isIdField(field: DishViewField | ''): boolean {
  return field === 'mealTypeId' || field === 'stallId' || field === 'canteenId'
}

function describeConditions(conditions: DishViewCondition[]): string {
  if (!conditions || conditions.length === 0) return '不筛选'
  return conditions.map(describeCondition).join(' 且 ')
}

/** 契约条件 → 编辑行 */
function toRow(c: DishViewCondition): ConditionRow {
  const row = emptyRow()
  row.field = c.field
  row.op = c.op
  if (c.op === 'in') row.values = [...(c.values ?? [])]
  else if (c.op === 'between') {
    row.low = c.values?.[0] ?? ''
    row.high = c.values?.[1] ?? ''
  } else row.value = c.value ?? ''
  return row
}

/** 编辑行 → 契约条件（`isTrue` 无值；`in` 用 values；`between` 用 values 两项） */
function fromRow(row: ConditionRow): DishViewCondition {
  const field = row.field as DishViewField
  if (row.op === 'isTrue') return { field, op: row.op }
  if (row.op === 'in') return { field, op: row.op, values: [...row.values] }
  if (row.op === 'between') return { field, op: row.op, values: [row.low, row.high] }
  return { field, op: row.op, value: row.value }
}

/* ==================== 抽屉三态之外的两态：查看 / 编辑 ==================== */
type DrawerMode = 'view' | 'edit'

const drawerOpen = ref(false)
const mode = ref<DrawerMode>('view')
const current = ref<DishViewAdminVO | null>(null)
const saving = ref(false)
/** 进入编辑态时的快照：用于「未保存修改」二次确认（BaseDrawer 的 dirty） */
const snapshot = ref('')

const form = ref<{ label: string; sortKind: DishSortKind; conditions: ConditionRow[] }>({
  label: '',
  sortKind: 'random',
  conditions: [],
})

const drawerTitle = computed(() => {
  if (mode.value === 'view') return '视图详情'
  return current.value ? '编辑视图' : '新建视图'
})
const dirty = computed(() => mode.value === 'edit' && formSnapshot() !== snapshot.value)

function formSnapshot(): string {
  return JSON.stringify(form.value)
}

/** 行内 / 抽屉动作并发保护：提交中该行（或抽屉当前行）动作置灰（列表页模板 §1.3「并发保护」） */
const { isBusy, runRowAction } = useRowAction()

function openDetail(row: DishViewAdminVO): void {
  current.value = row
  mode.value = 'view'
  drawerOpen.value = true
}

function openCreate(): void {
  current.value = null
  form.value = { label: '', sortKind: 'random', conditions: [] }
  snapshot.value = formSnapshot()
  mode.value = 'edit'
  drawerOpen.value = true
}

function startEdit(): void {
  const row = current.value
  if (!row) return
  form.value = {
    label: row.label,
    sortKind: row.sortKind,
    conditions: row.conditions.map(toRow),
  }
  snapshot.value = formSnapshot()
  mode.value = 'edit'
}

function cancelEdit(): void {
  if (current.value) {
    mode.value = 'view'
  } else {
    drawerOpen.value = false
  }
}

/* ==================== 条件编辑 ==================== */

function addCondition(): void {
  form.value.conditions.push(emptyRow())
}

function removeCondition(index: number): void {
  form.value.conditions.splice(index, 1)
}

/** 字段切换时重置取值形态与操作符（避免「换字段后操作符不属该字段」的非法载荷） */
function onFieldChange(row: ConditionRow): void {
  const ops = opsOf(row)
  row.op = ops[0] ?? ''
  row.value = ''
  row.values = []
  row.low = ''
  row.high = ''
}

/** 操作符切换时清掉不适用的取值（提交前仍以服务端白名单为准） */
function onOpChange(row: ConditionRow): void {
  row.value = ''
  row.values = []
  row.low = ''
  row.high = ''
}

/* ==================== 保存 / 启停 / 删除 ==================== */

function buildConditions(): DishViewCondition[] {
  return form.value.conditions.filter((row) => row.field).map(fromRow)
}

async function save(): Promise<void> {
  const label = form.value.label.trim()
  if (!label) {
    ElMessage.warning('请填写 tab 文案')
    return
  }
  if (label.length > 32) {
    ElMessage.warning('tab 文案最多 32 字')
    return
  }
  if (!form.value.sortKind) {
    ElMessage.warning('请选择排序口径')
    return
  }
  saving.value = true
  const conditions = buildConditions()
  try {
    if (current.value) {
      // 四字段整体替换：`enabled` 原样回传（本端点不表达「启停」，启停走独立动作）
      await updateView(current.value.id, {
        label,
        enabled: current.value.enabled,
        conditions,
        sortKind: form.value.sortKind,
      })
      ElMessage.success('已保存（客户端即时生效）')
      await load()
      const refreshed = items.value.find((r) => r.id === current.value?.id)
      if (refreshed) {
        current.value = refreshed
        mode.value = 'view'
      }
    } else {
      await createView({ label, conditions, sortKind: form.value.sortKind })
      ElMessage.success('已新建（默认启用）')
      await load()
      drawerOpen.value = false
    }
  } catch (e) {
    // 文案为空 / 超长、条件或排序口径不在白名单 → 后端原文
    fail(e, '保存失败')
  } finally {
    saving.value = false
  }
}

/** 刷新后按 id 回填抽屉数据（抽屉展示的不是该行时才不动，避免串数据） */
function syncCurrent(id: number): void {
  if (current.value?.id !== id) return
  current.value = items.value.find((r) => r.id === id) ?? current.value
}

function toggle(row: DishViewAdminVO): Promise<void> {
  const next = !row.enabled
  return runRowAction({
    id: row.id,
    action: () =>
      updateView(row.id, {
        label: row.label,
        enabled: next,
        conditions: row.conditions,
        sortKind: row.sortKind,
      }),
    successMessage: next ? '已启用' : '已停用',
    refresh: load,
    syncAfterRefresh: syncCurrent,
  })
}

function remove(row: DishViewAdminVO): Promise<void> {
  return runRowAction({
    id: row.id,
    action: async () => {
      await confirmDelete(`确认删除视图「${row.label}」？该 tab 将从首页筛选栏移除。`, {
        title: '删除视图',
      })
      await deleteView(row.id)
    },
    successMessage: '已删除',
    failMessage: '删除失败',
    refresh: load,
    closeDrawer: () => {
      if (current.value?.id !== row.id) return
      drawerOpen.value = false
      current.value = null
    },
  })
}

/* ==================== 拖拽排序（提交全量行） ==================== */
const { onDragStart, onDrop } = useReorder(items, sortViews, load)

onMounted(async () => {
  // 条件编辑的下拉候选：失败不阻塞列表（条件仍可手填 ID）
  try {
    const [cs, ss, kinds] = await Promise.all([listCanteens(), listStalls(), listCategories()])
    canteens.value = cs
    stalls.value = ss
    categories.value = kinds
  } catch {
    /* 列表仍可展示 */
  }
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>首页筛选视图</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建视图</button>
    </div>

    <!-- 四态：加载 / 会话失效 / 错误 / 空 / 有数据 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无筛选视图"
      @retry="load"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th class="drag-col"></th>
            <th>tab 文案</th>
            <th>条件</th>
            <th>匹配</th>
            <th>状态</th>
            <th>更新时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in items" :key="row.id" @dragover.prevent @drop="onDrop(index)">
            <td class="drag-col"><DragHandle @dragstart="onDragStart(index)" /></td>
            <td>{{ row.label }}</td>
            <td class="muted">{{ describeConditions(row.conditions) }}</td>
            <td class="num">
              {{ row.matchedCount }}
              <span v-if="row.matchedCount === 0" class="no-match">无匹配，客户端不显示</span>
            </td>
            <td><StatusTag :status="row.enabled ? 'on' : 'off'" kind="onoff" /></td>
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
        下发规则：启用中且匹配数不为 0 的视图才会出现在首页筛选栏；顺序在行首手柄上拖拽调整。
      </p>
    </div>

    <!-- 详情 / 编辑抽屉（同一抽屉两态） -->
    <BaseDrawer :title="drawerTitle" :open="drawerOpen" :dirty="dirty" @close="drawerOpen = false">
      <!-- ① 查看态 -->
      <template v-if="mode === 'view' && current">
        <div class="detail-meta">
          <DetailMetaRow k="视图 ID" num>#{{ current.id }}</DetailMetaRow>
          <DetailMetaRow k="tab 文案">{{ current.label }}</DetailMetaRow>
          <DetailMetaRow k="筛选条件">{{ describeConditions(current.conditions) }}</DetailMetaRow>
          <DetailMetaRow k="排序口径">{{
            SORT_KIND_OPTIONS.find((o) => o.value === current?.sortKind)?.label ?? current.sortKind
          }}</DetailMetaRow>
          <div class="meta-row">
            <span class="meta-key">顺序</span>
            <span class="meta-val num">
              {{ current.order }}
              <span class="muted">（升序；在列表行首手柄上拖拽调整）</span>
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-key">状态</span>
            <span class="meta-val">
              <StatusTag :status="current.enabled ? 'on' : 'off'" kind="onoff" />
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-key">匹配菜品</span>
            <span class="meta-val num">
              {{ current.matchedCount }}
              <span class="muted">（为 0 时客户端不显示该 tab）</span>
            </span>
          </div>
          <DetailMetaRow k="更新时间">{{ formatDateTime(current.updatedAt) }}</DetailMetaRow>
        </div>
      </template>

      <!-- ② 新建 / 编辑态 -->
      <template v-else>
        <div class="field">
          <label for="view-label">tab 文案</label>
          <input
            id="view-label"
            class="form-input"
            v-model="form.label"
            maxlength="32"
            placeholder="如 面食粉类"
          />
          <div class="hint">1~32 字；保存后客户端该 tab 文案即时更新（免发版）</div>
        </div>

        <div class="field">
          <label for="view-sort">排序口径</label>
          <select id="view-sort" class="form-input" v-model="form.sortKind">
            <option v-for="o in SORT_KIND_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </option>
          </select>
          <div class="hint">「会话种子随机」下客户端每次会话内顺序稳定、重进小程序重洗</div>
        </div>

        <div class="cond-block">
          <div class="cond-title">筛选条件（多条之间为「且」；不添加条件 = 不筛选）</div>
          <div v-if="!form.conditions.length" class="muted cond-empty">暂无条件</div>
          <div v-for="(row, index) in form.conditions" :key="index" class="cond-row">
            <select class="form-input" v-model="row.field" @change="onFieldChange(row)">
              <option v-for="o in FIELD_OPTIONS" :key="o.value" :value="o.value">
                {{ o.label }}
              </option>
            </select>
            <select class="form-input" v-model="row.op" @change="onOpChange(row)">
              <option v-for="op in opsOf(row)" :key="op" :value="op">
                {{ OP_LABEL[op] ?? op }}
              </option>
            </select>

            <!-- 取值控件随字段类型切换：无值 / 数值 / 区间 / 天数 / 实体下拉（单选或 in 多选） -->
            <span v-if="kindOf(row) === 'valueless'" class="muted cond-fixed">无需取值</span>

            <template v-else-if="kindOf(row) === 'range'">
              <input class="form-input" v-model="row.low" placeholder="下界" />
              <input class="form-input" v-model="row.high" placeholder="上界" />
            </template>

            <template v-else-if="kindOf(row) === 'number' || kindOf(row) === 'days'">
              <input
                class="form-input"
                v-model="row.value"
                :placeholder="kindOf(row) === 'days' ? '天数' : '数值'"
              />
            </template>

            <template v-else-if="row.op === 'in'">
              <select class="form-input" multiple v-model="row.values">
                <option v-for="o in optionsOf(row)" :key="o.value" :value="o.value">
                  {{ o.label }}
                </option>
              </select>
            </template>

            <template v-else>
              <select class="form-input" v-model="row.value">
                <option value="" disabled>请选择</option>
                <option v-for="o in optionsOf(row)" :key="o.value" :value="o.value">
                  {{ o.label }}
                </option>
              </select>
            </template>

            <button class="link danger" type="button" @click="removeCondition(index)">删除</button>
          </div>
          <button class="btn-secondary" type="button" @click="addCondition">＋ 添加条件</button>
        </div>
      </template>

      <template #actions>
        <template v-if="mode === 'view' && current">
          <button class="link" type="button" :disabled="isBusy(current.id)" @click="startEdit">
            编辑
          </button>
          <button
            class="link"
            type="button"
            :disabled="isBusy(current.id)"
            @click="toggle(current)"
          >
            {{ current.enabled ? '停用' : '启用' }}
          </button>
          <button
            class="link danger"
            type="button"
            :disabled="isBusy(current.id)"
            @click="remove(current)"
          >
            删除
          </button>
        </template>
        <template v-else>
          <button class="btn-secondary" type="button" @click="cancelEdit">取消</button>
          <button class="btn-primary" type="button" :disabled="saving" v-press @click="save">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </template>
      </template>
    </BaseDrawer>
  </div>
</template>

<style scoped>
.no-match {
  margin-left: var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-xs);
}
.cond-block {
  border-top: 1px dashed var(--border-light);
  margin-top: var(--space-2);
  padding-top: var(--space-2);
}
.cond-title {
  font-size: var(--font-sm);
  color: var(--text-muted);
  margin-bottom: var(--space-2);
}
.cond-empty {
  margin-bottom: var(--space-2);
}
.cond-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}
.cond-row .form-input {
  width: 150px;
}
.cond-fixed {
  width: 150px;
}
</style>
