<script setup lang="ts">
/**
 * A2 档口管理（页面规格见 [列表页模板.md 的 A2 差异节](../../../docs/web/ui/列表页模板.md)）。
 *
 * <p>要点：**不分页**；可选 `canteenId` 筛选；列表按「食堂 → 档口名」；编辑载体 = **弹窗**（4 个简单控件）；
 * 楼层为**下拉（楼层字典，值即汉字）**；删除受阻（其下仍有菜品 → `400` 原文透出）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fail } from '@/utils/error'
import { createStall, deleteStall, listStalls, updateStall } from '@/api/stalls'
import { listCanteens } from '@/api/canteens'
import type { CanteenAdminVO, StallAdminVO, StallSaveReq } from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import { FLOOR_OPTIONS } from '@/utils/floorDict'
import BaseModal from '@/components/BaseModal.vue'
import StateBox from '@/components/StateBox.vue'

/** 筛选：所属食堂（不传 = 全部） */
const filterCanteenId = ref<number | ''>('')
const canteens = ref<CanteenAdminVO[]>([])

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<StallAdminVO>(() =>
    listStalls(filterCanteenId.value === '' ? undefined : Number(filterCanteenId.value)),
  )

const open = ref(false)
const editing = ref<StallAdminVO | null>(null)
const saving = ref(false)
const form = ref<StallSaveReq>({ canteenId: 0, name: '', floor: '', windowNo: '' })

const title = computed(() => (editing.value ? '编辑档口' : '新建档口'))

function openCreate(): void {
  editing.value = null
  form.value = { canteenId: canteens.value[0]?.id ?? 0, name: '', floor: '', windowNo: '' }
  open.value = true
}

function openEdit(row: StallAdminVO): void {
  editing.value = row
  form.value = {
    canteenId: row.canteenId,
    name: row.name,
    floor: row.floor,
    windowNo: row.windowNo,
  }
  open.value = true
}

async function save(): Promise<void> {
  if (!form.value.canteenId) {
    ElMessage.warning('请选择所属食堂')
    return
  }
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入档口名称')
    return
  }
  saving.value = true
  try {
    if (editing.value) await updateStall(editing.value.id, form.value)
    else await createStall(form.value)
    ElMessage.success(editing.value ? '已保存' : '已新建')
    open.value = false
    await load()
  } catch (e) {
    // 同食堂下重名 / 楼层不在字典 / 食堂不存在 → 原文透出（后端 message）
    fail(e, '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: StallAdminVO): Promise<void> {
  const dishHint = row.dishCount > 0 ? `该档口下有 ${row.dishCount} 个菜品，删除前请先处理。` : ''
  try {
    await ElMessageBox.confirm(
      `确认删除档口「${row.name}」？${dishHint}`,
      '删除档口',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteStall(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    // 其下仍有菜品 / 档口不存在 → 后端原文（400 / 4001）
    fail(e, '删除失败')
  }
}

onMounted(async () => {
  // 依赖数据（食堂下拉）与列表并行：依赖失败不阻塞列表
  listCanteens()
    .then((rows) => (canteens.value = rows))
    .catch(() => undefined)
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>档口管理</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建档口</button>
    </div>

    <div class="card filters">
      <select class="form-input" v-model="filterCanteenId" @change="load">
        <option value="">全部食堂</option>
        <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <button class="btn-primary" type="button" v-press @click="load">查询</button>
      <button
        class="btn-secondary"
        type="button"
        @click="
          () => {
            filterCanteenId = ''
            load()
          }
        "
      >
        重置
      </button>
    </div>

    <!-- 六态：① 加载 ⑥ 会话失效 ② 错误 ③ 空 ④ 有数据 -->
    <StateBox v-if="firstLoading" status="loading" />
    <StateBox v-else-if="sessionInvalid" status="session" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="load" />
    <StateBox v-else-if="isEmpty" status="empty" message="暂无档口" />
    <div v-else-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>所属食堂</th>
            <th>档口</th>
            <th>楼层</th>
            <th>窗口号</th>
            <th>菜品数</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.canteenName || '—' }}</td>
            <td>{{ row.name }}</td>
            <td>{{ row.floor || '—' }}</td>
            <td>{{ row.windowNo || '—' }}</td>
            <td>{{ row.dishCount }}</td>
            <td class="actions">
              <button class="link" type="button" @click="openEdit(row)">编辑</button>
              <button class="link danger" type="button" @click="remove(row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <BaseModal :title="title" :open="open" @close="open = false">
      <div class="field">
        <label>所属食堂</label>
        <select class="form-input" v-model.number="form.canteenId">
          <option :value="0" disabled>请选择</option>
          <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </div>
      <div class="field">
        <label>档口名称</label>
        <input class="form-input" v-model="form.name" placeholder="如 嘉园奶茶" />
      </div>
      <div class="field">
        <label>楼层</label>
        <select class="form-input" v-model="form.floor">
          <option value="">未填写</option>
          <option v-for="f in FLOOR_OPTIONS" :key="f" :value="f">{{ f }}</option>
        </select>
      </div>
      <div class="field">
        <label>窗口号</label>
        <input class="form-input" v-model="form.windowNo" placeholder="如 3号窗口" />
      </div>
      <template #actions>
        <button class="btn-secondary" type="button" @click="open = false">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" v-press @click="save">
          {{ saving ? '保存中…' : '保存' }}
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
  width: 160px;
}
</style>
