<script setup lang="ts">
/**
 * 菜品编辑弹窗（规格见 docs/ui/web/菜品管理.md §3）。
 * 新建（`dish = null`）与编辑同组件；菜品本体 + **售卖点表格（可增删行）**；
 * 保存语义 = 售卖点**整表替换**（带 id 更新、不带 id 新增、未出现删除）。
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ConfirmDeleteCard from '@/components/ConfirmDeleteCard.vue'
import ImageField from '@/components/ImageField.vue'
import { dishApi, upload, type DishBody, type DishOfferingInput, type DishOut } from '@/api'
import type { StallOut } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  /** 编辑对象；`null` = 新建 */
  dish: DishOut | null
  canteens: { id: number; name: string }[]
  stalls: StallOut[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
  remove: [id: number]
}>()

type OfferingRow = {
  id?: number
  canteenId: number | undefined
  stallId: number | null
  priceYuan: string
  activate: boolean
}

const dialog = reactive({
  id: 0,
  name: '',
  description: '',
  imageUrl: '',
})
/** 编辑对象的原图片 URL，用于取消/保存失败时回滚 ImageField 的本地 blob 预览 */
const originalImageUrl = ref('')

const offerings = ref<OfferingRow[]>([])
const saving = ref(false)
const pendingFile = ref<File | null>(null)
const view = ref<'form' | 'delete'>('form')

function blankRow(): OfferingRow {
  return { canteenId: undefined, stallId: null, priceYuan: '', activate: true }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) {
      pendingFile.value = null
      view.value = 'form'
      return
    }
    dialog.id = props.dish?.id ?? 0
    dialog.name = props.dish?.name ?? ''
    dialog.description = props.dish?.description ?? ''
    dialog.imageUrl = props.dish?.imageUrl ?? ''
    originalImageUrl.value = props.dish?.imageUrl ?? ''
    offerings.value = (props.dish?.offerings ?? []).map((offering) => ({
      id: offering.id,
      canteenId: offering.canteenId,
      stallId: offering.stallId,
      priceYuan: offering.price === null ? '' : String(offering.price / 100),
      activate: offering.activate,
    }))
    pendingFile.value = null
    view.value = 'form'
  },
)

function stallsOf(canteenId: number | undefined): StallOut[] {
  return canteenId ? props.stalls.filter((stall) => stall.canteenId === canteenId) : props.stalls
}

function onCanteenChange(row: OfferingRow): void {
  // 换食堂后原档口可能失效 ⇒ 重置选中档口，下拉选项会随 canteenId 响应式重新计算
  row.stallId = null
}

/** 判断当前选中的 stallId 是否仍属于所选食堂，不属于则置空（防御性清理） */
function validateOffering(row: OfferingRow): void {
  if (row.canteenId && row.stallId) {
    const belongs = props.stalls.some((s) => s.id === row.stallId && s.canteenId === row.canteenId)
    if (!belongs) row.stallId = null
  }
}

function addRow(): void {
  offerings.value.push(blankRow())
}

function removeRow(index: number): void {
  offerings.value.splice(index, 1)
}

function offeringBody(row: OfferingRow): DishOfferingInput | null {
  if (!row.canteenId) return null
  const price = row.priceYuan.trim() === '' ? null : Math.round(Number(row.priceYuan) * 100)
  return {
    id: row.id,
    canteenId: row.canteenId,
    stallId: row.stallId ?? null,
    price: price !== null && Number.isFinite(price) ? price : null,
    activate: row.activate,
  }
}

async function onSave(): Promise<void> {
  if (!dialog.name.trim()) {
    ElMessage.warning('菜品名称不能为空')
    return
  }
  // 保存前防御性校验：确保每个售卖点的 stallId 属于所选食堂
  for (const row of offerings.value) {
    validateOffering(row)
  }
  const payload: DishOfferingInput[] = []
  for (const row of offerings.value) {
    const body = offeringBody(row)
    if (!body) {
      ElMessage.warning('售卖点需选择食堂')
      return
    }
    if (row.stallId) {
      const stall = props.stalls.find((item) => item.id === row.stallId)
      if (!stall || stall.canteenId !== row.canteenId) {
        ElMessage.warning('售卖点所属食堂不匹配')
        return
      }
    }
    payload.push(body)
  }

  saving.value = true
  try {
    let imageUrl = dialog.imageUrl || null
    if (pendingFile.value) {
      imageUrl = await upload(pendingFile.value)
      pendingFile.value = null
    }
    const body: DishBody = {
      name: dialog.name.trim(),
      description: dialog.description || null,
      imageUrl,
      offerings: payload,
    }
    if (dialog.id) await dishApi.update(dialog.id, body)
    else await dishApi.create(body)
    ElMessage.success('已保存')
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

/** 取消：回滚图片预览到原服务器 URL（释放本地 blob）并关闭弹窗 */
function onCancel(): void {
  dialog.imageUrl = originalImageUrl.value
  emit('update:modelValue', false)
}

function confirmDelete(): void {
  view.value = 'form'
  emit('remove', dialog.id)
}
</script>

<template>
  <div v-if="modelValue" class="mask" @click.self="emit('update:modelValue', false)">
    <div class="dialog">
      <ConfirmDeleteCard
        v-if="view === 'delete'"
        message="删除后不可恢复，该菜品的售卖点与菜品反馈将一并清除。"
        @cancel="view = 'form'"
        @confirm="confirmDelete"
      />
      <template v-else>
        <h3 class="dialog__title">{{ dialog.id ? '编辑菜品' : '新建菜品' }}</h3>

        <label class="dialog__label" for="dish-name">名称</label>
        <input id="dish-name" v-model="dialog.name" class="dialog__input" type="text" />

        <label class="dialog__label" for="dish-desc">描述</label>
        <textarea
          id="dish-desc"
          v-model="dialog.description"
          class="dialog__input dialog__textarea"
          placeholder="名称 + 口味 + 场景 + 特点"
        />

        <label class="dialog__label">菜品图片</label>
        <ImageField v-model="dialog.imageUrl" label="菜品图片" @pending="pendingFile = $event" />

        <div class="offering-head">
          <span class="dialog__label">售卖点</span>
          <button class="btn btn--sm" type="button" @click="addRow">+ 添加售卖点</button>
        </div>

        <div v-for="(row, index) in offerings" :key="index" class="offering-edit">
          <div class="offering-edit__grid">
            <select v-model="row.canteenId" class="dialog__input" @change="onCanteenChange(row)">
              <option :value="undefined">选择食堂</option>
              <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
            <select v-model="row.stallId" class="dialog__input">
              <option :value="null">不指定档口</option>
              <option v-for="s in stallsOf(row.canteenId)" :key="s.id" :value="s.id">
                {{ s.name }}
              </option>
            </select>
            <input
              v-model="row.priceYuan"
              class="dialog__input"
              type="number"
              min="0"
              step="0.01"
              placeholder="价格（元）"
            />
            <select v-model="row.activate" class="dialog__input">
              <option :value="true">在售</option>
              <option :value="false">已下架</option>
            </select>
          </div>
          <button class="btn btn--sm offering-edit__remove" type="button" @click="removeRow(index)">
            删除本行
          </button>
        </div>

        <p class="dialog__hint">楼层随档口带出；不指定档口时楼层显示「—」。价格留空 = 价格待定。</p>

        <template
          v-if="
            offerings.some(
              (r) =>
                r.canteenId &&
                r.stallId &&
                !props.stalls.some((s) => s.id === r.stallId && s.canteenId === r.canteenId),
            )
          "
        >
          <p
            class="dialog__hint"
            style="color: var(--el-color-danger); font-size: 12px; margin-top: 6px"
          >
            部分售卖点的档口与所属食堂不匹配，已自动清空，请重新选择。
          </p>
        </template>

        <footer class="dialog__foot">
          <button v-if="dialog.id" class="btn btn--danger" @click="view = 'delete'">删除</button>
          <span class="dialog__spacer" />
          <button class="btn btn--outline" @click="onCancel">取消</button>
          <button class="btn btn--primary" :disabled="saving" @click="onSave">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </footer>
      </template>
    </div>
  </div>
</template>

<style scoped>
.dialog__textarea {
  min-height: 72px;
  padding: 8px 10px;
  resize: vertical;
}

.offering-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

.offering-edit {
  margin-top: 8px;
  padding: 8px;
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-lighter);
}

.offering-edit__grid {
  display: grid;
  grid-template-columns: 1.2fr 1.2fr 1fr 0.8fr;
  gap: 8px;
}

.offering-edit__remove {
  margin-top: 6px;
}

@media (max-width: 640px) {
  .offering-edit__grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
