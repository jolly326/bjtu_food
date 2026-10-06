<script setup lang="ts">
/**
 * A4 菜品属性维度与取值（页面规格见 [属性维度与取值.md](../../../docs/ui/web/属性维度与取值.md)）。
 *
 * <p>要点：维度列表**不分页**（按 `order` 升序）+ **拖拽排序**（提交**全量行**，非法提交 → `400`）；
 * 编辑维度 = **弹窗**（字段键 / 名称 / 取值类型）；**取值管理 = 抽屉**（`.table--compact` 紧凑表格）；
 * 删除确认**必须含影响面**（其下 N 个取值、M 个菜品）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
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
import BaseModal from '@/components/BaseModal.vue'
import BaseDrawer from '@/components/BaseDrawer.vue'
import StateBox from '@/components/StateBox.vue'
import ListState from '@/components/ListState.vue'

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<DishDimensionAdminVO>(() => listDimensions())

/* ==================== 维度：新增 / 编辑 ==================== */
const dimOpen = ref(false)
const editing = ref<DishDimensionAdminVO | null>(null)
const saving = ref(false)
const form = ref<DishDimensionSaveReq>({ fieldKey: '', name: '', valueType: 'single' })

const dimTitle = computed(() => (editing.value ? '编辑维度' : '新建维度'))

function openCreate(): void {
  editing.value = null
  form.value = { fieldKey: '', name: '', valueType: 'single' }
  dimOpen.value = true
}

function openEdit(row: DishDimensionAdminVO): void {
  editing.value = row
  form.value = { fieldKey: row.fieldKey, name: row.name, valueType: row.valueType }
  dimOpen.value = true
}

async function saveDimension(): Promise<void> {
  if (!form.value.fieldKey.trim() || !form.value.name.trim()) {
    ElMessage.warning('请填写字段键与名称')
    return
  }
  saving.value = true
  try {
    if (editing.value) await updateDimension(editing.value.id, form.value)
    else await createDimension(form.value)
    ElMessage.success(editing.value ? '已保存' : '已新建')
    dimOpen.value = false
    await load()
  } catch (e) {
    // 字段键重名 / 改在用字段键 → 后端原文（400）
    fail(e, '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeDimension(row: DishDimensionAdminVO): Promise<void> {
  try {
    await confirmDelete(
      `确认删除维度「${row.name}」？其下 ${row.valueCount} 个取值、${row.dishCount} 个菜品正在使用；被引用时将无法删除。`,
      {
        title: '删除维度',
      },
    )
  } catch {
    return
  }
  try {
    await deleteDimension(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    // 被引用 → 后端原文（400），提示改为「先改菜品，再删除」
    fail(e, '删除失败')
  }
}

/* ==================== 维度：拖拽排序（提交全量行） ==================== */
const { onDragStart, onDrop } = useReorder(items, sortDimensions, load)

/* ==================== 取值：抽屉内管理 ==================== */
const valuesOpen = ref(false)
const currentDim = ref<DishDimensionAdminVO | null>(null)
const values = ref<DishValueAdminVO[]>([])
const valuesLoading = ref(false)
const valuesError = ref<string | null>(null)
const newLabel = ref('')
const editingValueId = ref<number | null>(null)
const editingLabel = ref('')

async function openValues(row: DishDimensionAdminVO): Promise<void> {
  currentDim.value = row
  valuesOpen.value = true
  await loadValues()
}

async function loadValues(): Promise<void> {
  const dim = currentDim.value
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

async function addValue(): Promise<void> {
  const dim = currentDim.value
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
    await loadValues()
    await load()
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
  const dim = currentDim.value
  const label = editingLabel.value.trim()
  if (!dim || !label) return
  try {
    await updateValue(dim.id, row.id, { label })
    editingValueId.value = null
    ElMessage.success('已改名')
    await loadValues()
    await load()
  } catch (e) {
    fail(e, '改名失败')
  }
}

async function removeValue(row: DishValueAdminVO): Promise<void> {
  const dim = currentDim.value
  if (!dim) return
  try {
    await confirmDelete(`确认删除取值「${row.label}」？被菜品引用时将无法删除。`, {
      title: '删除取值',
    })
  } catch {
    return
  }
  try {
    await deleteValue(dim.id, row.id)
    ElMessage.success('已删除')
    await loadValues()
    await load()
  } catch (e) {
    fail(e, '删除失败')
  }
}

/* 取值拖拽排序（提交全量行，复用 useReorder） */
const { onDragStart: onValueDragStart, onDrop: onValueDrop } = useReorder(
  values,
  (req) => sortValues(currentDim.value!.id, req),
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
            <th>字段键</th>
            <th>取值类型</th>
            <th>取值数</th>
            <th>关联菜品</th>
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
            <td>{{ row.name }}</td>
            <td>
              <code>{{ row.fieldKey }}</code>
            </td>
            <td>{{ row.valueType === 'single' ? '单选' : '多选' }}</td>
            <td class="num">{{ row.valueCount }}</td>
            <td class="num">{{ row.dishCount }}</td>
            <td class="actions">
              <button class="link" type="button" @click="openValues(row)">管理取值</button>
              <button class="link" type="button" @click="openEdit(row)">编辑</button>
              <button class="link danger" type="button" @click="removeDimension(row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 维度编辑：3 个简单控件 → 弹窗 -->
    <BaseModal :title="dimTitle" :open="dimOpen" @close="dimOpen = false">
      <div class="field">
        <label for="dim-name">维度名称</label>
        <input id="dim-name" class="form-input" v-model="form.name" placeholder="如 口味 / 食材" />
      </div>
      <div class="field">
        <label for="dim-field-key">字段键</label>
        <input
          id="dim-field-key"
          class="form-input"
          v-model="form.fieldKey"
          :disabled="!!editing"
          placeholder="如 flavor / ingredient"
        />
        <div class="hint">后端索引键，创建后不可修改</div>
      </div>
      <div class="field">
        <label for="dim-value-type">取值类型</label>
        <select id="dim-value-type" class="form-input" v-model="form.valueType">
          <option value="single">单选</option>
          <option value="multi">多选</option>
        </select>
      </div>
      <template #actions>
        <button class="btn-secondary" type="button" @click="dimOpen = false">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" v-press @click="saveDimension">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </BaseModal>

    <!-- 取值管理：抽屉 + 紧凑表格 -->
    <BaseDrawer
      :title="`取值管理 · ${currentDim?.name ?? ''}`"
      :open="valuesOpen"
      @close="valuesOpen = false"
    >
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
      <StateBox v-else-if="valuesError" status="error" :message="valuesError" @retry="loadValues" />
      <StateBox v-else-if="!values.length" status="empty" message="暂无取值" />
      <table v-else class="table table--compact">
        <thead>
          <tr>
            <th class="drag-col"></th>
            <th>取值</th>
            <th>引用菜品</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(v, index) in values"
            :key="v.id"
            draggable="true"
            @dragstart="onValueDragStart(index)"
            @dragover.prevent
            @drop="onValueDrop(index)"
          >
            <td class="drag-col" title="拖拽排序">⋮⋮</td>
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
            <td class="actions">
              <template v-if="editingValueId === v.id">
                <button class="link" type="button" @click="commitRename(v)">保存</button>
                <button class="link" type="button" @click="editingValueId = null">取消</button>
              </template>
              <template v-else>
                <button class="link" type="button" @click="startRename(v)">改名</button>
                <button class="link danger" type="button" @click="removeValue(v)">删除</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </BaseDrawer>
  </div>
</template>

<style scoped>
.drag-col {
  width: 28px;
  color: var(--text-muted);
  cursor: grab;
  user-select: none;
  text-align: center;
}
.num {
  font-variant-numeric: tabular-nums;
}
.hint {
  margin-top: var(--space-1);
  color: var(--text-muted);
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
