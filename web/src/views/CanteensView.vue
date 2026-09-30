<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listCanteens,
  createCanteen,
  updateCanteen,
  toggleCanteenStatus,
  deleteCanteen,
} from '@/api/canteens'
import type { CanteenVO, CanteenSaveReq } from '@/types/common'
import StatusTag from '@/components/StatusTag.vue'
import BaseModal from '@/components/BaseModal.vue'
import StateBox from '@/components/StateBox.vue'

const list = ref<CanteenVO[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const open = ref(false)
const editing = ref<CanteenVO | null>(null)
const form = ref<CanteenSaveReq>({ name: '', description: '', sortOrder: 0 })
const saving = ref(false)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    list.value = await listCanteens()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  form.value = { name: '', description: '', sortOrder: 0 }
  open.value = true
}
function openEdit(r: CanteenVO): void {
  editing.value = r
  form.value = { name: r.name, description: r.description, sortOrder: r.sortOrder }
  open.value = true
}
async function save(): Promise<void> {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  saving.value = true
  try {
    if (editing.value) await updateCanteen(editing.value.id, form.value)
    else await createCanteen(form.value)
    ElMessage.success('已保存')
    open.value = false
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}
async function toggle(r: CanteenVO): Promise<void> {
  try {
    await toggleCanteenStatus(r.id)
    ElMessage.success('已更新')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}
async function remove(r: CanteenVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除食堂「${r.name}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteCanteen(r.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>食堂管理</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建食堂</button>
    </div>
    <StateBox v-if="loading" status="loading" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="load" />
    <div v-else class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>名称</th>
            <th>描述</th>
            <th>排序</th>
            <th>状态</th>
            <th>档口数</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.id">
            <td>{{ r.name }}</td>
            <td class="ellipsis">{{ r.description || '—' }}</td>
            <td>{{ r.sortOrder }}</td>
            <td><StatusTag :status="r.status" kind="onoff" /></td>
            <td>{{ r.stallCount }}</td>
            <td class="actions">
              <button class="link" type="button" @click="openEdit(r)">编辑</button>
              <button class="link" type="button" @click="toggle(r)">
                {{ r.status === 'on' ? '停用' : '启用' }}
              </button>
              <button class="link danger" type="button" @click="remove(r)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="6"><StateBox status="empty" /></td>
          </tr>
        </tbody>
      </table>
    </div>

    <BaseModal :title="editing ? '编辑食堂' : '新建食堂'" :open="open" @close="open = false">
      <div class="field">
        <label>名称</label>
        <input class="form-input" v-model="form.name" placeholder="食堂名称" />
      </div>
      <div class="field">
        <label>描述</label>
        <textarea class="form-textarea" v-model="form.description" rows="3" />
      </div>
      <div class="field">
        <label>排序</label>
        <input class="form-input" type="number" v-model.number="form.sortOrder" />
      </div>
      <template #actions>
        <button class="btn-ghost" type="button" @click="open = false">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" v-press @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>
