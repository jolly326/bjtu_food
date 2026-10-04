<script setup lang="ts">
/**
 * A1 食堂管理（页面规格见 [列表页模板.md 的 A1 差异节](../../../docs/ui/web/列表页模板.md)）。
 *
 * <p>要点：**不分页**（量级十数条，列表按 `name` 升序）；编辑载体 = **弹窗**（仅 1 个简单控件：名称）；
 * 删除受阻（**其下仍有档口 → `400` 原文透出**）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
import { fail } from '@/utils/error'
import { createCanteen, deleteCanteen, listCanteens, updateCanteen } from '@/api/canteens'
import type { CanteenAdminVO } from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import BaseModal from '@/components/BaseModal.vue'
import ListState from '@/components/ListState.vue'

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<CanteenAdminVO>(() => listCanteens())

const open = ref(false)
const editing = ref<CanteenAdminVO | null>(null)
const saving = ref(false)
const name = ref('')

const title = computed(() => (editing.value ? '编辑食堂' : '新建食堂'))

function openCreate(): void {
  editing.value = null
  name.value = ''
  open.value = true
}

function openEdit(row: CanteenAdminVO): void {
  editing.value = row
  name.value = row.name
  open.value = true
}

async function save(): Promise<void> {
  if (!name.value.trim()) {
    ElMessage.warning('请输入食堂名称')
    return
  }
  saving.value = true
  try {
    if (editing.value) await updateCanteen(editing.value.id, { name: name.value.trim() })
    else await createCanteen({ name: name.value.trim() })
    ElMessage.success(editing.value ? '已保存' : '已新建')
    open.value = false
    await load()
  } catch (e) {
    // 重名 / 空 / 超长 → 后端原文（400）
    fail(e, '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: CanteenAdminVO): Promise<void> {
  const stallHint =
    row.stallCount > 0 ? `该食堂下有 ${row.stallCount} 个档口，删除前请先处理。` : ''
  try {
    await confirmDelete(`确认删除食堂「${row.name}」？${stallHint}`, { title: '删除食堂' })
  } catch {
    return
  }
  try {
    await deleteCanteen(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    // 其下仍有档口 / 食堂不存在 → 后端原文（400 / 4001）
    fail(e, '删除失败')
  }
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>食堂管理</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建食堂</button>
    </div>

    <!-- 六态：① 加载 ⑥ 会话失效 ② 错误 ③ 空 ④ 有数据 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无食堂"
      @retry="load"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>食堂</th>
            <th>档口数</th>
            <th>更新时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.name }}</td>
            <td>{{ row.stallCount }}</td>
            <td class="muted">{{ row.updatedAt }}</td>
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
        <label for="canteen-name">食堂名称</label>
        <input id="canteen-name" class="form-input" v-model="name" placeholder="如 学一食堂" @keyup.enter="save" />
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
.muted {
  color: var(--text-muted);
  font-size: var(--font-sm);
}
</style>
