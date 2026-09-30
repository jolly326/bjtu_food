<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listDishes,
  createDish,
  updateDish,
  toggleDishStatus,
  deleteDish,
} from '@/api/dishes'
import { listCanteens } from '@/api/canteens'
import { listStalls } from '@/api/stalls'
import { listDimensions } from '@/api/dimensions'
import type {
  DishAdminVO,
  DishSaveReq,
  DishListParams,
  CanteenVO,
  StallVO,
  DishDimensionVO,
  OnOffStatus,
} from '@/types/common'
import { fenToYuan, yuanToFen, formatYuan } from '@/utils/money'
import { usePagedList } from '@/composables/usePagedList'
import StatusTag from '@/components/StatusTag.vue'
import BaseModal from '@/components/BaseModal.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import StateBox from '@/components/StateBox.vue'

const canteens = ref<CanteenVO[]>([])
const stalls = ref<StallVO[]>([])
const dimensions = ref<DishDimensionVO[]>([])

// 筛选
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

const { items, loading, finished, error, load } = usePagedList<DishAdminVO>(
  (page, pageSize) => listDishes({ page, pageSize, ...params() }),
  20,
)

// 弹窗
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
function openEdit(r: DishAdminVO): void {
  editing.value = r
  form.value = {
    name: r.name,
    stallId: r.stallId,
    priceYuan: String(fenToYuan(r.price)),
    originalPriceYuan: r.originalPrice == null ? '' : String(fenToYuan(r.originalPrice)),
    mealType: r.mealType,
    description: r.description,
    images: [...r.images],
  }
  attrSingle.value = {}
  attrMulti.value = {}
  for (const d of dimensions.value) {
    const v = r.attributes?.[d.fieldKey]
    if (Array.isArray(v)) attrMulti.value[d.fieldKey] = v.join(', ')
    else if (typeof v === 'string') attrSingle.value[d.fieldKey] = v
  }
  open.value = true
}
function buildAttributes(): Record<string, string | string[]> {
  const out: Record<string, string | string[]> = {}
  for (const d of dimensions.value) {
    if (d.valueType === 'single') {
      const v = (attrSingle.value[d.fieldKey] ?? '').trim()
      if (v) out[d.fieldKey] = v
    } else {
      const arr = (attrMulti.value[d.fieldKey] ?? '')
        .split(',')
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
  const price = yuanToFen(form.value.priceYuan)
  if (price <= 0) {
    ElMessage.warning('现价须大于 0')
    return
  }
  const originalPrice = form.value.originalPriceYuan
    ? yuanToFen(form.value.originalPriceYuan)
    : null
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
    ElMessage.success('已保存')
    open.value = false
    load(true)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}
async function toggle(r: DishAdminVO): Promise<void> {
  try {
    await toggleDishStatus(r.id)
    ElMessage.success('已更新')
    load(true)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}
async function remove(r: DishAdminVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除菜品「${r.name}」？其全部评价将一并删除。`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteDish(r.id)
    ElMessage.success('已删除')
    load(true)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}
function search(): void {
  load(true)
}
function reset(): void {
  fKeyword.value = ''
  fCanteenId.value = 0
  fStallId.value = 0
  fMealType.value = ''
  fStatus.value = ''
  load(true)
}
const filteredStalls = (): StallVO[] =>
  fCanteenId.value ? stalls.value.filter((s) => s.canteenId === fCanteenId.value) : stalls.value

onMounted(async () => {
  try {
    ;[canteens.value, stalls.value, dimensions.value] = await Promise.all([
      listCanteens(),
      listStalls(),
      listDimensions(),
    ])
  } catch {
    /* 依赖数据加载失败时列表仍可展示 */
  }
  load(true)
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>菜品管理</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建菜品</button>
    </div>

    <div class="card filters">
      <input class="form-input" v-model="fKeyword" placeholder="菜品名" @keyup.enter="search" />
      <select class="form-input" v-model.number="fCanteenId" @change="search">
        <option :value="0">全部食堂</option>
        <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <select class="form-input" v-model.number="fStallId" @change="search">
        <option :value="0">全部档口</option>
        <option v-for="s in filteredStalls()" :key="s.id" :value="s.id">{{ s.name }}</option>
      </select>
      <input class="form-input" v-model="fMealType" placeholder="大类" @keyup.enter="search" />
      <select class="form-input" v-model="fStatus" @change="search">
        <option value="">全部状态</option>
        <option value="on">在售</option>
        <option value="off">下架</option>
      </select>
      <button class="btn-primary" type="button" v-press @click="search">查询</button>
      <button class="btn-ghost" type="button" @click="reset">重置</button>
    </div>

    <StateBox v-if="loading && !items.length" status="loading" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="search" />
    <div v-else class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>封面</th>
            <th>名称</th>
            <th>现价</th>
            <th>原价</th>
            <th>食堂 / 档口</th>
            <th>大类</th>
            <th>状态</th>
            <th>评分</th>
            <th>浏览</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in items" :key="r.id">
            <td>
              <img v-if="r.images[0]" :src="r.images[0]" class="mini-cover" alt="" />
              <span v-else class="muted">无图</span>
            </td>
            <td>{{ r.name }}</td>
            <td>{{ formatYuan(r.price) }}</td>
            <td>{{ r.originalPrice == null ? '—' : formatYuan(r.originalPrice) }}</td>
            <td>{{ r.canteenName }} / {{ r.stallName }}</td>
            <td>{{ r.mealType || '—' }}</td>
            <td><StatusTag :status="r.status" kind="onoff" /></td>
            <td>{{ r.avgRating ?? '—' }}<span v-if="r.ratingCount" class="muted">（{{ r.ratingCount }}）</span></td>
            <td>{{ r.viewCount }}</td>
            <td class="actions">
              <button class="link" type="button" @click="openEdit(r)">编辑</button>
              <button class="link" type="button" @click="toggle(r)">
                {{ r.status === 'on' ? '下架' : '上架' }}
              </button>
              <button class="link danger" type="button" @click="remove(r)">删除</button>
            </td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="10"><StateBox status="empty" /></td>
          </tr>
        </tbody>
      </table>
      <div class="pager">
        <span v-if="loading" class="muted">加载中…</span>
        <button v-else-if="!finished" class="btn-ghost" type="button" @click="load()">加载更多</button>
        <span v-else-if="items.length" class="muted">已全部加载</span>
      </div>
    </div>

    <BaseModal
      :title="editing ? '编辑菜品' : '新建菜品'"
      :open="open"
      @close="open = false"
    >
      <div class="field">
        <label>名称</label>
        <input class="form-input" v-model="form.name" placeholder="菜品名称" />
      </div>
      <div class="field">
        <label>所属档口</label>
        <select class="form-input" v-model.number="form.stallId">
          <option :value="0" disabled>请选择</option>
          <option v-for="s in stalls" :key="s.id" :value="s.id">
            {{ s.canteenName }} · {{ s.name }}
          </option>
        </select>
      </div>
      <div class="row2">
        <div class="field">
          <label>现价（元）</label>
          <input class="form-input" type="number" step="0.01" v-model="form.priceYuan" />
        </div>
        <div class="field">
          <label>原价（元，可空）</label>
          <input class="form-input" type="number" step="0.01" v-model="form.originalPriceYuan" />
        </div>
      </div>
      <div class="field">
        <label>菜品大类</label>
        <input class="form-input" v-model="form.mealType" placeholder="如 主食 / 汤品" />
      </div>
      <div class="field">
        <label>描述</label>
        <textarea class="form-textarea" v-model="form.description" rows="2" />
      </div>
      <div class="field">
        <label>配图（首图为封面，必填，≤5 张）</label>
        <ImageUpload v-model="form.images" :max="5" />
      </div>
      <div v-if="dimensions.length" class="dim-block">
        <div class="dim-title">描述属性</div>
        <div class="field" v-for="d in dimensions" :key="d.id">
          <label>{{ d.name }}</label>
          <input
            v-if="d.valueType === 'single'"
            class="form-input"
            v-model="attrSingle[d.fieldKey]"
          />
          <input
            v-else
            class="form-input"
            v-model="attrMulti[d.fieldKey]"
            placeholder="多个值用逗号分隔"
          />
        </div>
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
.mini-cover {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-sm);
  object-fit: cover;
  display: block;
}
.muted {
  color: var(--text-muted);
  font-size: var(--font-sm);
}
.pager {
  padding: var(--space-4);
  text-align: center;
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
