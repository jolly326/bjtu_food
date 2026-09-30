<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listStalls,
  createStall,
  updateStall,
  toggleStallStatus,
  deleteStall,
} from '@/api/stalls'
import { listCanteens } from '@/api/canteens'
import type { StallVO, StallSaveReq, CanteenVO } from '@/types/common'
import StatusTag from '@/components/StatusTag.vue'
import BaseModal from '@/components/BaseModal.vue'
import StateBox from '@/components/StateBox.vue'

const list = ref<StallVO[]>([])
const canteens = ref<CanteenVO[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const open = ref(false)
const editing = ref<StallVO | null>(null)
const form = ref<StallSaveReq>({ canteenId: 0, name: '', floor: '', windowNo: '', sortOrder: 0 })
const saving = ref(false)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    list.value = await listStalls()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  form.value = { canteenId: canteens.value[0]?.id ?? 0, name: '', floor: '', windowNo: '', sortOrder: 0 }
  open.value = true
}
function openEdit(r: StallVO): void {
  editing.value = r
  form.value = {
    canteenId: r.canteenId,
    name: r.name,
    floor: r.floor,
    windowNo: r.windowNo,
    sortOrder: r.sortOrder,
  }
  open.value = true
}
async function save(): Promise<void> {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  if (!form.value.canteenId) {
    ElMessage.warning('请选择所属食堂')
    return
  }
  saving.value = true
  try {
    if (editing.value) await updateStall(editing.value.id, form.value)
    else await createStall(form.value)
    ElMessage.success('已保存')
    open.value = false
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}
async function toggle(r: StallVO): Promise<void> {
  try {
    await toggleStallStatus(r.id)
    ElMessage.success('已更新')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}
async function remove(r: StallVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除档口「${r.name}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteStall(r.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(async () => {
  try {
    canteens.value = await listCanteens()
  } catch {
    /* 保留空，列表可后补 */
  }
  load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>档口管理</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建档口</button>
    </div>
    <StateBox v-if="loading" status="loading" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="load" />
    <div v-else class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>所属食堂</th>
            <th>档口</th>
            <th>楼层</th>
            <th>窗口号</th>
            <th>排序</th>
            <th>状态</th>
            <th>菜品数</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.id">
            <td>{{ r.canteenName }}</td>
            <td>{{ r.name }}</td>
            <td>{{ r.floor || '—' }}</td>
            <td>{{ r.windowNo || '—' }}</td>
            <td>{{ r.sortOrder }}</td>
            <td><StatusTag :status="r.status" kind="onoff" /></td>
            <td>{{ r.dishCount }}</td>
            <td class="actions">
              <button class="link" type="button" @click="openEdit(r)">编辑</button>
              <button class="link" type="button" @click="toggle(r)">
                {{ r.status === 'on' ? '停用' : '启用' }}
              </button>
              <button class="link danger" type="button" @click="remove(r)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="8"><StateBox status="empty" /></td>
          </tr>
        </tbody>
      </table>
    </div>

    <BaseModal :title="editing ? '编辑档口' : '新建档口'" :open="open" @close="open = false">
      <div class="field">
        <label>所属食堂</label>
        <select class="form-input" v-model.number="form.canteenId">
          <option :value="0" disabled>请选择</option>
          <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </div>
      <div class="field">
        <label>名称</label>
        <input class="form-input" v-model="form.name" placeholder="档口名称" />
      </div>
      <div class="field">
        <label>楼层</label>
        <input class="form-input" v-model="form.floor" placeholder="如 1F / 二层" />
      </div>
      <div class="field">
        <label>窗口号</label>
        <input class="form-input" v-model="form.windowNo" placeholder="如 12 号" />
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
