<script setup lang="ts">
/**
 * 菜品编辑弹窗（规格见 docs/ui/web/菜品管理.md）。
 * 新建（`dish = null`）与编辑同组件；菜品本体 + **位置表格（可增删行）**；
 * 保存语义 = 位置**整表替换**（带 id 更新、不带 id 新增、未出现删除）。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ConfirmDeleteCard from '@/components/ConfirmDeleteCard.vue'
import ImageField from '@/components/ImageField.vue'
import { dishApi, upload, type DishBody, type DishLocationInput, type DishOut } from '@/api'
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

/** 楼层「全部」哨兵：楼层是前端筛选维度，不随位置落库 */
const FLOOR_ALL = ''

type LocationRow = {
  id?: number
  canteenId: number | undefined
  floor: string
  stallId: number | null
  activate: boolean
}

const dialog = reactive({
  id: 0,
  name: '',
  description: '',
  imageUrl: '',
  priceYuan: '',
})
/** 编辑对象的原图片 URL，用于取消/保存失败时回滚 ImageField 的本地 blob 预览 */
const originalImageUrl = ref('')

const locations = ref<LocationRow[]>([])
const saving = ref(false)
const pendingFile = ref<File | null>(null)
const view = ref<'form' | 'delete'>('form')

function blankRow(): LocationRow {
  return { canteenId: undefined, floor: FLOOR_ALL, stallId: null, activate: true }
}

/** 楼层选项取自档口数据，避免与档口页各维护一份楼层常量 */
const floorOptions = computed(() => {
  const floors = new Set<string>()
  for (const stall of props.stalls) {
    if (stall.floor) floors.add(stall.floor)
  }
  return [...floors]
})

/** 档口下拉按「食堂 + 楼层」两级收窄 */
function stallsOf(row: LocationRow): StallOut[] {
  const list = row.canteenId
    ? props.stalls.filter((stall) => stall.canteenId === row.canteenId)
    : props.stalls
  return row.floor ? list.filter((stall) => stall.floor === row.floor) : list
}

function onCanteenChange(row: LocationRow): void {
  row.stallId = null
  row.floor = FLOOR_ALL
}

function onFloorChange(row: LocationRow): void {
  row.stallId = null
}

function onStallChange(row: LocationRow): void {
  if (!row.stallId) return
  const stall = props.stalls.find((item) => item.id === row.stallId)
  row.floor = stall?.floor || FLOOR_ALL
}

function addRow(): void {
  locations.value.push(blankRow())
}

function removeRow(index: number): void {
  locations.value.splice(index, 1)
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
    dialog.priceYuan = props.dish?.price == null ? '' : String(props.dish.price / 100)
    locations.value = (props.dish?.locations ?? []).map((loc) => ({
      id: loc.id,
      canteenId: loc.canteenId,
      floor: loc.floor || FLOOR_ALL,
      stallId: loc.stallId,
      activate: loc.activate,
    }))
    pendingFile.value = null
    view.value = 'form'
  },
)

async function onSave(): Promise<void> {
  if (!dialog.name.trim()) {
    ElMessage.warning('菜品名称不能为空')
    return
  }
  const price = dialog.priceYuan.trim() === '' ? null : Math.round(Number(dialog.priceYuan) * 100)
  if (price !== null && (!Number.isFinite(price) || price < 0)) {
    ElMessage.warning('价格不能为负')
    return
  }

  const payload: DishLocationInput[] = []
  for (const row of locations.value) {
    if (!row.canteenId) {
      ElMessage.warning('请选择位置的食堂')
      return
    }
    if (row.stallId) {
      const stall = props.stalls.find((item) => item.id === row.stallId)
      if (!stall || stall.canteenId !== row.canteenId) {
        ElMessage.warning('该档口不属于所选食堂')
        return
      }
    }
    payload.push({
      id: row.id,
      canteenId: row.canteenId,
      stallId: row.stallId ?? null,
      activate: row.activate,
    })
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
      price,
      locations: payload,
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
  <Teleport defer to="#overlay-root">
    <div v-if="modelValue" class="mask" @click.self="emit('update:modelValue', false)">
      <div class="dialog dialog--wide" role="dialog" aria-modal="true" aria-label="菜品编辑">
        <ConfirmDeleteCard
          v-if="view === 'delete'"
          message="删除后不可恢复。位置、向量与反馈将一并清除。"
          @cancel="view = 'form'"
          @confirm="confirmDelete"
        />
        <template v-else>
          <h3 class="dialog__title">{{ dialog.id ? '编辑菜品' : '新建菜品' }}</h3>

          <div class="dialog__body">
            <div class="edit-grid">
              <div class="edit-field">
                <label class="dialog__label" for="dish-name">名称</label>
                <input id="dish-name" v-model="dialog.name" class="dialog__input" type="text" />
              </div>

              <div class="edit-field">
                <label class="dialog__label" for="dish-price">价格（元）</label>
                <input
                  id="dish-price"
                  v-model="dialog.priceYuan"
                  class="dialog__input"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="留空 = 价格待定"
                />
              </div>

              <div class="edit-field">
                <label class="dialog__label" for="dish-desc">描述</label>
                <textarea
                  id="dish-desc"
                  v-model="dialog.description"
                  class="dialog__input dialog__textarea"
                  placeholder="口味 / 场景 / 特点"
                />
              </div>

              <div class="edit-field">
                <label class="dialog__label">菜品图片</label>
                <ImageField
                  v-model="dialog.imageUrl"
                  label="菜品图片"
                  @pending="pendingFile = $event"
                />
              </div>

              <div class="edit-field edit-field--wide">
                <div class="offering-head">
                  <span class="dialog__label">售卖位置</span>
                  <button class="btn btn--sm" type="button" @click="addRow">添加位置</button>
                </div>

                <div
                  v-for="(row, index) in locations"
                  :key="row.id ?? `new-${index}`"
                  class="offering-edit"
                >
                  <select
                    v-model="row.canteenId"
                    class="dialog__input"
                    aria-label="食堂"
                    @change="onCanteenChange(row)"
                  >
                    <option :value="undefined">选择食堂</option>
                    <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
                  </select>
                  <select
                    v-model="row.floor"
                    class="dialog__input"
                    aria-label="楼层"
                    @change="onFloorChange(row)"
                  >
                    <option :value="FLOOR_ALL">全部楼层</option>
                    <option v-for="f in floorOptions" :key="f" :value="f">{{ f }}</option>
                  </select>
                  <select
                    v-model="row.stallId"
                    class="dialog__input"
                    aria-label="档口"
                    @change="onStallChange(row)"
                  >
                    <option :value="null">不指定档口</option>
                    <option v-for="s in stallsOf(row)" :key="s.id" :value="s.id">
                      {{ s.name }}
                    </option>
                  </select>
                  <select v-model="row.activate" class="dialog__input" aria-label="在售状态">
                    <option :value="true">在售</option>
                    <option :value="false">已下架</option>
                  </select>
                  <button
                    class="btn btn--sm offering-edit__remove"
                    type="button"
                    @click="removeRow(index)"
                  >
                    删除本行
                  </button>
                </div>
              </div>
            </div>
          </div>

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
  </Teleport>
</template>

<style scoped>
/* 字段栅格由 theme.css 的 .edit-grid 统一承载，本页只定义售卖位置区 */
.offering-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--web-gap-tight);
  margin-top: var(--web-gap-tight);
}

.offering-head .dialog__label {
  margin: 0;
}

.offering-edit {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.9fr) minmax(0, 1.2fr) minmax(0, 0.9fr) auto;
  align-items: center;
  gap: var(--web-gap-tight);
  margin-top: var(--web-gap-tight);
  padding: var(--web-gap-tight);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--web-radius-sm);
  background: var(--el-fill-color-lighter);
}

/* 字段栅格在 theme.css 的 ≤767 档收为单列；本页只收售卖位置行 */
@media (max-width: 767px) {
  .offering-edit {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .offering-edit__remove {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .offering-edit {
    grid-template-columns: 1fr;
  }

  .offering-edit__remove {
    grid-column: auto;
  }
}
</style>
