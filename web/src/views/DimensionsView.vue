<script setup lang="ts">
/**
 * A4 菜品属性维度与取值（页面规格见 [属性维度与取值.md](../../../docs/ui/web/属性维度与取值.md)）。
 *
 * <p>要点：维度列表**不分页**（按 `order` 升序）+ **拖拽排序**（提交**全量行**，非法提交 → `400`）；
 * 表格**只放基础列**（维度 / 取值类型 / 取值数 / 关联菜品 / 更新时间 + 拖拽手柄）；
 * **全部操作**（管理取值 / 编辑 / 删除）都在**详情抽屉**内；抽屉三态：
 * `view`（只读 + 操作）/ `edit`（名称 + 取值类型）/ `values`（取值管理，紧凑表格 + 拖拽排序）。
 *
 * <p>**系统维度**（`system = true`，菜品种类）：名称旁标「系统」；取值类型置灰、删除置灰
 * （取值被 `dish.meal_type_id` 引用）；取值仍可登记 / 改名 / 排序（与 A6 分类抽屉同一批行）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
import { formatDateTime } from '@/utils/datetime'
import { fail } from '@/utils/error'
import { useReorder } from '@/composables/useReorder'
import {
  createDimension,
  createValue,
  deleteDimension,
  deleteValue,
  listDimensions,
  listValues,
  sortDimensions,
  sortValues,
  updateDimension,
  updateValue,
} from '@/api/dimensions'
import type { DishDimensionAdminVO, DishDimensionSaveReq, DishValueAdminVO } from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import BaseDrawer from '@/components/BaseDrawer.vue'
import StateBox from '@/components/StateBox.vue'
import ListState from '@/components/ListState.vue'
import { useRowAction } from '@/composables/useRowAction'

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<DishDimensionAdminVO>(() => listDimensions())

/* ==================== 详情 / 编辑 / 取值（同一抽屉三态） ==================== */
type DrawerMode = 'view' | 'edit' | 'values'

const drawerOpen = ref(false)
const mode = ref<DrawerMode>('view')
const current = ref<DishDimensionAdminVO | null>(null)
const saving = ref(false)

const form = ref<DishDimensionSaveReq>({ name: '', valueType: 'single' })
/** 进入编辑态时的快照：用于「未保存修改」二次确认（BaseDrawer 的 dirty） */
const snapshot = ref('')

const drawerTitle = computed(() => {
  if (mode.value === 'values') return `取值管理 · ${current.value?.name ?? ''}`
  if (mode.value === 'edit') return current.value ? '编辑维度' : '新建维度'
  return '维度详情'
})
const dirty = computed(() => mode.value === 'edit' && JSON.stringify(form.value) !== snapshot.value)

/** 行内 / 抽屉动作并发保护：提交中该行（或抽屉当前行）动作置灰（列表页模板 §1.3「并发保护」） */
const { busyId, isBusy, runRowAction } = useRowAction()

/** 系统维度（菜品种类）：取值类型不可改、维度不可删 —— 端上置灰，服务端另有 400 兜底 */
const systemDimension = computed(() => Boolean(current.value?.system))

function openDetail(row: DishDimensionAdminVO): void {
  current.value = row
  form.value = { name: row.name, valueType: row.valueType }
  snapshot.value = JSON.stringify(form.value)
  mode.value = 'view'
  drawerOpen.value = true
}

function openCreate(): void {
  current.value = null
  form.value = { name: '', valueType: 'single' }
  snapshot.value = JSON.stringify(form.value)
  mode.value = 'edit'
  drawerOpen.value = true
}

function startEdit(): void {
  if (current.value) {
    form.value = { name: current.value.name, valueType: current.value.valueType }
  }
  snapshot.value = JSON.stringify(form.value)
  mode.value = 'edit'
}

function cancelEdit(): void {
  if (current.value) {
    form.value = { name: current.value.name, valueType: current.value.valueType }
    snapshot.value = JSON.stringify(form.value)
    mode.value = 'view'
  } else {
    drawerOpen.value = false
  }
}

/** 维度保存后按 id 回填最新行（列表已 reload），避免详情停在旧快照上 */
function syncCurrent(id: number): void {
  const refreshed = items.value.find((r) => r.id === id)
  if (!refreshed || current.value?.id !== id) return
  current.value = refreshed
  form.value = { name: refreshed.name, valueType: refreshed.valueType }
  snapshot.value = JSON.stringify(form.value)
}

async function saveDimension(): Promise<void> {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写维度名称')
    return
  }
  saving.value = true
  try {
    if (current.value) {
      await updateDimension(current.value.id, form.value)
      ElMessage.success('已保存')
      await load()
      syncCurrent(current.value.id)
      mode.value = 'view'
    } else {
      await createDimension(form.value)
      ElMessage.success('已新建')
      await load()
      drawerOpen.value = false
    }
  } catch (e) {
    // 名称重名 / 取值类型切换失败 → 后端原文（400）
    fail(e, '保存失败')
  } finally {
    saving.value = false
  }
}

function removeDimension(row: DishDimensionAdminVO): Promise<void> {
  return runRowAction({
    id: row.id,
    action: async () => {
      await confirmDelete(
        `确认删除维度「${row.name}」？其下 ${row.valueCount} 个取值、${row.dishCount} 个菜品正在使用；被引用时将无法删除。`,
        {
          title: '删除维度',
        },
      )
      await deleteDimension(row.id)
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

/* ==================== 维度：拖拽排序（提交全量行） ==================== */
const { onDragStart, onDrop } = useReorder(items, sortDimensions, load)

/* ==================== 取值：抽屉内管理 ==================== */
const values = ref<DishValueAdminVO[]>([])
const valuesLoading = ref(false)
const valuesError = ref<string | null>(null)
const newLabel = ref('')
const editingValueId = ref<number | null>(null)
const editingLabel = ref('')
/** 取值表（抽屉内）行内动作并发保护：提交中该行按钮 `:disabled`（与列表行同范式） */
const busyValueId = ref<number | null>(null)

/** 取值管理态 → 查看态（抽屉留在维度详情上，不回列表） */
function backToView(): void {
  mode.value = 'view'
}

async function openValues(): Promise<void> {
  if (!current.value) return
  mode.value = 'values'
  busyId.value = current.value.id
  try {
    await loadValues()
  } finally {
    busyId.value = null
  }
}

async function loadValues(): Promise<void> {
  const dim = current.value
  if (!dim) return
  valuesLoading.value = true
  valuesError.value = null
  try {
    values.value = await listValues(dim.id)
  } catch (e) {
    valuesError.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    valuesLoading.value = false
  }
}

/** 取值增删改后同步刷新维度列表（取值数 / 关联菜品数变了） */
async function refreshAll(): Promise<void> {
  await loadValues()
  await load()
  if (current.value) syncCurrent(current.value.id)
}

async function addValue(): Promise<void> {
  const dim = current.value
  const label = newLabel.value.trim()
  if (!dim || !label) {
    ElMessage.warning('请输入取值名称')
    return
  }
  try {
    await createValue(dim.id, { label })
    newLabel.value = ''
    ElMessage.success('已新增')
    // 抽屉保持打开（连续录入）；列表与计数同源刷新
    await refreshAll()
  } catch (e) {
    // 同维度下重名 → 后端原文（400）
    fail(e, '新增失败')
  }
}

function startRename(row: DishValueAdminVO): void {
  editingValueId.value = row.id
  editingLabel.value = row.label
}

async function commitRename(row: DishValueAdminVO): Promise<void> {
  const dim = current.value
  const label = editingLabel.value.trim()
  if (!dim || !label) return
  busyValueId.value = row.id
  try {
    await updateValue(dim.id, row.id, { label })
    editingValueId.value = null
    ElMessage.success('已改名')
    await refreshAll()
  } catch (e) {
    fail(e, '改名失败')
  } finally {
    busyValueId.value = null
  }
}

async function removeValue(row: DishValueAdminVO): Promise<void> {
  const dim = current.value
  if (!dim) return
  try {
    await confirmDelete(`确认删除取值「${row.label}」？被菜品引用时将无法删除。`, {
      title: '删除取值',
    })
  } catch {
    return
  }
  busyValueId.value = row.id
  try {
    await deleteValue(dim.id, row.id)
    ElMessage.success('已删除')
    await refreshAll()
  } catch (e) {
    fail(e, '删除失败')
  } finally {
    busyValueId.value = null
  }
}

/* 取值拖拽排序（提交全量行，复用 useReorder） */
const { onDragStart: onValueDragStart, onDrop: onValueDrop } = useReorder(
  values,
  (req) => sortValues(current.value!.id, req),
  loadValues,
)

onMounted(() => load())
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>属性维度与取值</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建维度</button>
    </div>

    <!-- 四态：加载 / 会话失效 / 错误 / 空 / 有数据 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无属性维度"
      @retry="load"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th class="drag-col"></th>
            <th>维度</th>
            <th>取值类型</th>
            <th>取值数</th>
            <th>关联菜品</th>
            <th>更新时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in items" :key="row.id" @dragover.prevent @drop="onDrop(index)">
            <td class="drag-col"><DragHandle @dragstart="onDragStart(index)" /></td>
            <td>
              {{ row.name }}
              <span v-if="row.system" class="sys-tag">系统</span>
            </td>
            <td>{{ row.valueType === 'single' ? '单选' : '多选' }}</td>
            <td class="num">{{ row.valueCount }}</td>
            <td class="num">{{ row.dishCount }}</td>
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
        顺序在行首手柄上拖拽调整；取值在「详情 → 管理取值」内维护（新增 / 改名 / 删除 / 拖拽排序）。
      </p>
    </div>

    <!-- 详情 / 编辑 / 取值（同一抽屉三态） -->
    <BaseDrawer :title="drawerTitle" :open="drawerOpen" :dirty="dirty" @close="drawerOpen = false">
      <!-- ① 查看态 -->
      <template v-if="mode === 'view'">
        <div class="detail-meta">
          <div class="meta-row">
            <span class="meta-key">维度 ID</span>
            <span class="meta-val num">
              #{{ current?.id }}
              <span class="muted">（= 菜品 attributes 的键）</span>
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-key">维度名</span>
            <span class="meta-val">
              {{ current?.name }}
              <span v-if="current?.system" class="sys-tag">系统</span>
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-key">取值类型</span>
            <span class="meta-val">
              {{ current?.valueType === 'single' ? '单选' : '多选' }}
              <span v-if="current?.system" class="muted">（系统维度恒单值，不可改）</span>
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-key">顺序</span>
            <span class="meta-val num">
              {{ current?.order }}
              <span class="muted">（升序；在列表行首手柄上拖拽调整）</span>
            </span>
          </div>
          <DetailMetaRow k="取值数" num>{{ current?.valueCount }}</DetailMetaRow>
          <div class="meta-row">
            <span class="meta-key">关联菜品</span>
            <span class="meta-val num">
              {{ current?.dishCount }}
              <span class="muted">（被引用时不可删除维度）</span>
            </span>
          </div>
          <DetailMetaRow k="更新时间">{{ formatDateTime(current?.updatedAt) }}</DetailMetaRow>
        </div>
      </template>

      <!-- ② 编辑态（新建 / 编辑共用；2 个简单控件） -->
      <template v-else-if="mode === 'edit'">
        <div class="field">
          <label for="dim-name">维度名称</label>
          <input
            id="dim-name"
            class="form-input"
            v-model="form.name"
            placeholder="如 口味 / 食材"
          />
        </div>
        <div class="field">
          <label for="dim-value-type">取值类型</label>
          <select
            id="dim-value-type"
            class="form-input"
            v-model="form.valueType"
            :disabled="systemDimension"
          >
            <option value="single">单选</option>
            <option value="multi">多选</option>
          </select>
          <div class="hint">
            {{
              systemDimension
                ? '系统维度（菜品种类）恒单值：取值类型不可修改'
                : '切换取值类型会自动迁移该维度下菜品的数据形状'
            }}
          </div>
        </div>
      </template>

      <!-- ③ 取值管理态（紧凑表格 + 拖拽排序） -->
      <template v-else>
        <div class="add-row">
          <input
            class="form-input"
            v-model="newLabel"
            placeholder="新增取值名称（≤32 字）"
            @keyup.enter="addValue"
          />
          <button class="btn-primary" type="button" v-press @click="addValue">新增</button>
        </div>

        <StateBox v-if="valuesLoading" status="loading" />
        <StateBox
          v-else-if="valuesError"
          status="error"
          :message="valuesError"
          @retry="loadValues"
        />
        <StateBox v-else-if="!values.length" status="empty" message="暂无取值" />
        <!-- 紧凑表格包 `.table-wrap` ⇒ 窄屏获得横向滚动兜底 -->
        <div v-else class="table-wrap">
          <table class="table table--compact">
            <thead>
              <tr>
                <th class="drag-col"></th>
                <th>取值</th>
                <th>引用菜品</th>
                <th>更新时间</th>
                <th class="actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(v, index) in values"
                :key="v.id"
                @dragover.prevent
                @drop="onValueDrop(index)"
              >
                <td class="drag-col"><DragHandle @dragstart="onValueDragStart(index)" /></td>
                <td>
                  <input
                    v-if="editingValueId === v.id"
                    class="form-input inline"
                    v-model="editingLabel"
                    @keyup.enter="commitRename(v)"
                    @keyup.esc="editingValueId = null"
                  />
                  <span v-else>{{ v.label }}</span>
                </td>
                <td class="num">{{ v.dishCount }}</td>
                <td class="muted">{{ formatDateTime(v.updatedAt) }}</td>
                <td class="actions">
                  <template v-if="editingValueId === v.id">
                    <button
                      class="link"
                      type="button"
                      :disabled="busyValueId === v.id"
                      @click="commitRename(v)"
                    >
                      保存
                    </button>
                    <button
                      class="link"
                      type="button"
                      :disabled="busyValueId === v.id"
                      @click="editingValueId = null"
                    >
                      取消
                    </button>
                  </template>
                  <template v-else>
                    <button
                      class="link"
                      type="button"
                      :disabled="busyValueId === v.id"
                      @click="startRename(v)"
                    >
                      改名
                    </button>
                    <button
                      class="link danger"
                      type="button"
                      :disabled="busyValueId === v.id"
                      @click="removeValue(v)"
                    >
                      删除
                    </button>
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <template #actions>
        <template v-if="mode === 'view'">
          <button class="link" type="button" :disabled="isBusy(current?.id)" @click="openValues">
            管理取值
          </button>
          <button class="link" type="button" :disabled="isBusy(current?.id)" @click="startEdit">
            编辑
          </button>
          <button
            class="link danger"
            type="button"
            :disabled="isBusy(current?.id) || systemDimension"
            :title="systemDimension ? '系统维度：取值被菜品种类引用，不可删除' : undefined"
            @click="current && removeDimension(current)"
          >
            删除
          </button>
        </template>
        <template v-else-if="mode === 'edit'">
          <button class="btn-secondary" type="button" @click="cancelEdit">取消</button>
          <button
            class="btn-primary"
            type="button"
            :disabled="saving"
            v-press
            @click="saveDimension"
          >
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </template>
        <template v-else>
          <button class="btn-secondary" type="button" @click="backToView">返回维度详情</button>
        </template>
      </template>
    </BaseDrawer>
  </div>
</template>

<style scoped>
.sys-tag {
  margin-left: var(--space-2);
  padding: 0 var(--space-1);
  border-radius: var(--radius-pill);
  background: var(--bg-gray);
  color: var(--text-secondary);
  font-size: var(--font-xs);
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
