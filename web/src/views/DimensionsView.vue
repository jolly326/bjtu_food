<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listDimensions,
  createDimension,
  updateDimension,
  deleteDimension,
} from '@/api/dimensions'
import type { DishDimensionVO, DishDimensionSaveReq } from '@/types/common'
import BaseModal from '@/components/BaseModal.vue'
import StateBox from '@/components/StateBox.vue'

const list = ref<DishDimensionVO[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const open = ref(false)
const editing = ref<DishDimensionVO | null>(null)
const form = ref<DishDimensionSaveReq>({ fieldKey: '', name: '', valueType: 'single', order: 0 })
const saving = ref(false)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    list.value = await listDimensions()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}
function openCreate(): void {
  editing.value = null
  form.value = { fieldKey: '', name: '', valueType: 'single', order: 0 }
  open.value = true
}
function openEdit(r: DishDimensionVO): void {
  editing.value = r
  form.value = { fieldKey: r.fieldKey, name: r.name, valueType: r.valueType, order: r.order }
  open.value = true
}
async function save(): Promise<void> {
  if (!form.value.fieldKey.trim() || !form.value.name.trim()) {
    ElMessage.warning('请填写字段键与名称')
    return
  }
  saving.value = true
  try {
    if (editing.value) await updateDimension(editing.value.id, form.value)
    else await createDimension(form.value)
    ElMessage.success('已保存')
    open.value = false
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}
async function remove(r: DishDimensionVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除属性维度「${r.name}」？关联菜品该维度将清空。`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteDimension(r.id)
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
      <h2>菜品属性维度管理</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建维度</button>
    </div>
    <StateBox v-if="loading" status="loading" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="load" />
    <div v-else class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>名称</th>
            <th>字段键</th>
            <th>取值类型</th>
            <th>排序</th>
            <th>已关联菜品</th>
            <th>已用取值</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.id">
            <td>{{ r.name }}</td>
            <td><code>{{ r.fieldKey }}</code></td>
            <td>{{ r.valueType === 'single' ? '单选' : '多选' }}</td>
            <td>{{ r.order }}</td>
            <td>{{ r.dishCount }}</td>
            <td class="used-values">
              <span v-for="v in r.usedValues" :key="v" class="tag tag-gray">{{ v }}</span>
              <span v-if="!r.usedValues.length" class="muted">—</span>
            </td>
            <td class="actions">
              <button class="link" type="button" @click="openEdit(r)">编辑</button>
              <button class="link danger" type="button" @click="remove(r)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="7"><StateBox status="empty" /></td>
          </tr>
        </tbody>
      </table>
    </div>

    <BaseModal :title="editing ? '编辑维度' : '新建维度'" :open="open" @close="open = false">
      <p class="hint">字段键为后端索引键（英文 snake/kebab），创建后不可改；取值在菜品编辑处维护。</p>
      <div class="field">
        <label>名称</label>
        <input class="form-input" v-model="form.name" placeholder="如 辣度 / 口味" />
      </div>
      <div class="field">
        <label>字段键</label>
        <input class="form-input" v-model="form.fieldKey" :disabled="!!editing" placeholder="如 spicy_level" />
      </div>
      <div class="field">
        <label>取值类型</label>
        <select class="form-input" v-model="form.valueType">
          <option value="single">单选</option>
          <option value="multi">多选</option>
        </select>
      </div>
      <div class="field">
        <label>排序</label>
        <input class="form-input" type="number" v-model.number="form.order" />
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

<style scoped>
.used-values {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  max-width: 260px;
}
.muted {
  color: var(--text-muted);
}
.hint {
  color: var(--text-muted);
  font-size: var(--font-sm);
  margin: 0 0 var(--space-3);
}
</style>
