<script setup lang="ts">
/**
 * DishFormDialog：菜品新增/编辑弹窗（后台菜品管理页唯一入口）。
 * 提交走 adminStore.addDish / updateDish（api 层 dishToApi 自动转分）。
 * 价格 / 原价均以「元」编辑（§7.26：原价高于现价时端上划线；原 `promoPrice` 已删除）。
 *
 * 归属区（§7.23 第 1 条，2026-09-15 蓝图 v1）：**食堂 → 档口两级联动 + 直接输入新名称**
 *  - 下拉选既有字典值；也支持直接输入新名称，提交时随菜品保存，后端按名 upsert 自动建档
 *    （同名不重复建档；独立新增端点 POST /admin/canteens|stalls 已删除，孪生创建弹窗一并下线）；
 *  - 未选食堂时档口 disabled（可见不可交互，非隐藏）；切换食堂静默清空已选档口，防跨食堂脏数据；
 *  - 编辑态由 dish.stall_id 回填档口、按名回填食堂；
 *  - 提交契约（DishAdminReq）：既有档口传 stallId；新档口传 stallName + canteenName
 *    （canteenName 仅在 stallName 触发新建档口时被后端消费，故两者总是成对提供）；
 *  - 名称「其他/其它/无/未知」为后端空值语义（不建档），前端同步拦截（对齐 EMPTY_NAME_VALUES）；
 *  - 改名（属性字典唯一编辑动作）保留：行内「改名」入口 → RenameEntityDialog。
 */
import { ref, watch, computed } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useDishStore } from '@/stores/dishStore'
import { useCanteenStore } from '@/stores/canteenStore'
import { useStallStore } from '@/stores/stallStore'
import { useMealTypeStore } from '@/stores/mealTypeStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import {
  parseCsv,
  formatCsv,
  DIET_TYPE_OPTIONS,
  SERVE_TEMP_OPTIONS,
  INGREDIENT_OPTIONS,
  FLAVOR_TAG_OPTIONS,
} from '@/constants'
import FormDialog from '@/components/FormDialog.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import RenameEntityDialog from '@/components/RenameEntityDialog.vue'

const props = withDefaults(
  defineProps<{
    show: boolean
    /** 编辑中的菜品 id（null = 新增） */
    editingId?: number | null
    /** 新增时默认选中的档口（其食堂自动级联反查） */
    defaultStallId?: number | null
  }>(),
  { editingId: null, defaultStallId: null },
)

const emit = defineEmits<{ close: []; saved: [] }>()

const store = useAdminStore()
const dishStore = useDishStore()
const canteenStore = useCanteenStore()
const stallStore = useStallStore()
const mealTypeStore = useMealTypeStore()
const toast = useToastStore()
const confirm = useConfirmStore()

/** 后端 upsert 空值语义名称（DishServiceImpl.EMPTY_NAME_VALUES 同源）：视为未传、不建档 */
const UPSET_EMPTY_NAMES = ['其他', '其它', '无', '未知']

const form = ref({
  name: '',
  price: 0,
  originalPrice: 0,
  /** 所属食堂：number = 既有食堂 id；string = 直接输入的新名称（后端按名 upsert） */
  canteenValue: '' as string | number,
  /** 所属档口：number = 既有档口 id；string = 直接输入的新名称（后端按名 upsert） */
  stallValue: '' as string | number,
  image: '',
  description: '',
  status: 'active' as 'active' | 'inactive',
  /** 描述四维（§7.28）：单选维（荤素 / 冷热）存机器值；多值维（主料 / 口味）存 CSV 机器值 */
  dietType: '',
  ingredients: '',
  flavorTags: '',
  serveTemp: '',
  /** 菜品大类（§7.34）：单值枚举键，选项完全由后端字典下发（仅可选择、禁自由输入） */
  mealType: '',
})
const formErrors = ref<Record<string, string>>({})
const submitting = ref(false)
/** 归属区错误补充提示（档口失效等失败恢复路径） */
const stallHint = ref('')

// WEB-02：打开弹窗时若归属字典尚未加载（如直接深链进入），兜底拉一次（静默）
// 大类字典（§7.34）同属该兜底链路：失败只影响大类字段（字段内可重试），不阻塞弹窗其它内容
watch(
  () => props.show,
  (v) => {
    if (!v) return
    if (!store.canteens.length) canteenStore.loadAll().catch(() => {})
    if (!store.stalls.length) stallStore.loadAll().catch(() => {})
    mealTypeStore.ensureLoaded().catch(() => {})
  },
)

/**
 * 「他人已修改」轻提示基线（Q-112 ①，PR-13 最简实现）：
 * 打开编辑弹窗时记录该菜品当前的 updated_at 作为基线；保存前仅比对该基线是否与
 * store 中当前行的 updated_at 一致（不一致说明期间被改过），给一次可「继续保存 / 取消」的**非阻塞**提示。
 * 不引入版本号 / 乐观锁 / ETag 等并发控制机制。
 */
const editBaselineUpdatedAt = ref<string>('')
function updatedAtKey(v: unknown): string {
  return v ? new Date(v as any).getTime().toString() : ''
}

// 改名（Q-113/Q-115）：属性字典唯一的编辑动作，无删除
const renameKind = ref<'canteen' | 'stall'>('canteen')
const renameId = ref<number | null>(null)
const renameName = ref('')
const renameModal = ref(false)

function openRename(kind: 'canteen' | 'stall', id: number | string, name: string) {
  renameKind.value = kind
  renameId.value = Number(id)
  renameName.value = name
  renameModal.value = true
}

// ===== 级联数据源 =====
const canteenOptions = computed(() =>
  store.canteens.map(c => ({ label: c.name, value: Number(c.id) })),
)
/** 档口选项：严格按所选食堂的 canteen_id 过滤（防脏数据）；食堂为手输新名称时无既有选项，仅允许手输 */
const stallOptions = computed(() => {
  if (!form.value.canteenValue || typeof form.value.canteenValue !== 'number') return []
  return store.stalls
    .filter(s => Number(s.canteen_id) === Number(form.value.canteenValue))
    .map(s => ({ label: s.name, value: Number(s.id) }))
})
const stallDisabled = computed(() => !form.value.canteenValue)
const stallPlaceholder = computed(() => (form.value.canteenValue ? '选择或输入新档口名' : '请先选择食堂'))

// ===== 菜品大类（§7.34）：选项仅来自后端字典，端上零硬编码中文 =====
/** 字典选项（单一真源 = 后端 `GET /dishes/meal-types`，端上零硬编码中文） */
const mealTypeDictOptions = computed(() =>
  mealTypeStore.list.map(t => ({ label: t.label, value: t.key })),
)
/**
 * 下拉选项 = 字典项 +「当前值兜底项」（与列表筛选 `DishManageView.mealTypeFilterOptions` 的 extras 同口径）。
 * 成因：字典**只含当前有在售菜品**的大类，而管理端菜品含已下架 → 编辑某下架菜品时其大类可能不在字典集合内，
 * 此时无匹配 option，el-select 会直接显示裸枚举键、看着像「没选中」（列表/详情已有 labelOf 兜底，表单此前没有）。
 * 标签走 store.labelOf（字典命中用后端标签，未覆盖回落键本身），仅用于回显，提交值不变（保存正确性不受影响）。
 */
const mealTypeOptions = computed(() => {
  const opts = mealTypeDictOptions.value
  const cur = form.value.mealType
  if (!cur || opts.some(o => o.value === cur)) return opts
  return [...opts, { label: mealTypeStore.labelOf(cur), value: cur }]
})
/** 选项不可用（加载中 / 加载失败）时给出明确占位文案，避免「空下拉」被误读为无数据 */
const mealTypePlaceholder = computed(() => {
  if (mealTypeStore.loading) return '大类加载中…'
  if (mealTypeStore.error) return '大类选项加载失败'
  return '请选择菜品大类'
})
/**
 * 字典「加载完成且成功、但为空」——库中尚无任何在售菜品，大类选项不存在（唯一放行空选的态）。
 * 与「加载中 / 加载失败」区分：后两者有恢复路径（等待 / 重试），必须继续按必填拦截。
 * 判定口径取**字典本身**（mealTypeDictOptions），不取含「当前值兜底项」的 mealTypeOptions，
 * 否则字典为空时会被回显兜底项掩盖（误判为已有选项、必填/hint 口径失真）。
 */
const mealTypeDictEmpty = computed(() =>
  !mealTypeStore.loading && !mealTypeStore.error && !mealTypeDictOptions.value.length,
)
/** 字典为空时说明成因与当下可行动作，避免「下拉点不开、又不知为何」 */
const mealTypeHint = computed(() =>
  mealTypeDictEmpty.value
    ? '暂无可选大类：选项随「有在售菜品」的大类出现，本次可先保存、稍后补选'
    : '按菜名与做法形态选择（不看主料与口味）',
)
function reloadMealTypes() {
  mealTypeStore.loadAll().catch(() => {})
}

/** 由档口 id 反查其所属食堂 id（编辑回显 / defaultStallId 预选共用） */
function canteenIdOfStall(stallId: number | string | bigint | null | undefined): number | '' {
  if (stallId === '' || stallId === null || stallId === undefined) return ''
  const s = store.stalls.find(x => Number(x.id) === Number(stallId))
  return s ? Number(s.canteen_id) : ''
}

watch(
  () => props.show,
  (v) => {
    if (!v) return
    submitting.value = false
    formErrors.value = {}
    stallHint.value = ''
    if (props.editingId != null) {
      const d = store.dishes.find(x => Number(x.id) === Number(props.editingId))
      if (d) {
        // 记录编辑基线（打开时的 updated_at），保存前据此做一次轻量「他人已修改」提示
        editBaselineUpdatedAt.value = updatedAtKey(d.updated_at)
        form.value = {
          name: d.name,
          price: Number(d.price) || 0,
          originalPrice: d.originalPrice ? Number(d.originalPrice) : 0,
          canteenValue: canteenIdOfStall(d.stall_id) || d.canteenName || '',
          stallValue: Number(d.stall_id ?? 0) || d.stallName || '',
          image: d.image || '',
          description: d.description || '',
          status: d.status as 'active' | 'inactive',
          dietType: d.dietType || '',
          ingredients: d.ingredients || '',
          flavorTags: d.flavorTags || '',
          serveTemp: d.serveTemp || '',
          // 编辑态回填大类（§7.34）：原样取 DishAdminVO 的枚举键（不做 key → 中文 反查）
          mealType: d.mealType || '',
        }
      }
    } else {
      editBaselineUpdatedAt.value = ''
      const presetStall = props.defaultStallId != null ? String(props.defaultStallId) : ''
      form.value = {
        name: '', price: 0, originalPrice: 0,
        canteenValue: canteenIdOfStall(presetStall),
        stallValue: presetStall,
        image: '', description: '', status: 'active',
        dietType: '', ingredients: '', flavorTags: '', serveTemp: '',
        mealType: '',
      }
    }
  },
)

/** 选择食堂：切换时静默清空档口并重算选项（防跨食堂脏数据；手输新名同样触发清空） */
function onCanteenChange() {
  form.value.stallValue = ''
  formErrors.value.stallValue = ''
  stallHint.value = ''
}

function validate() {
  const errs: Record<string, string> = {}
  if (!form.value.name.trim()) errs.name = '菜品名称不能为空'
  if (!form.value.price || Number(form.value.price) <= 0) errs.price = '价格必须大于 0'
  // 归属必填（§7.23 第 1 条）：既有选择（数字 id）或直接输入新名称二选一
  if (!form.value.canteenValue) errs.canteenValue = '请选择或输入所属食堂'
  else if (typeof form.value.canteenValue === 'string' && UPSET_EMPTY_NAMES.includes(form.value.canteenValue.trim())) {
    errs.canteenValue = '该名称为空值语义，请输入实际食堂名称'
  }
  if (!form.value.stallValue) errs.stallValue = '请选择或输入所属档口'
  else if (typeof form.value.stallValue === 'string' && UPSET_EMPTY_NAMES.includes(form.value.stallValue.trim())) {
    errs.stallValue = '该名称为空值语义，请输入实际档口名称'
  }
  if (typeof form.value.canteenValue === 'string' && typeof form.value.stallValue === 'number') {
    errs.stallValue = '新食堂暂无既有档口，请直接输入新档口名称'
  }
  // 菜品大类必选（§7.34）：口径为「每个菜品恰属一个大类（单值互斥、全量覆盖）」，
  // 后台是菜品唯一录入源 → 不选会让新菜品落在 NULL，破坏该不变量，故必填（不选即拦截保存）。
  // 唯一例外：**字典加载完成且成功、但为空**（库中尚无在售菜品）时无从选择，若仍强制必填则
  // 「菜品 → 大类」互相依赖成死锁（一道菜都录不进来）——该态放行保存（大类留空），就绪后补选。
  // 加载中 / 加载失败**不属于**该例外（各有恢复路径），仍按必填拦截（此时保存请求本身也到不了后端）。
  if (!form.value.mealType && !mealTypeDictEmpty.value) errs.mealType = '请选择菜品大类'
  // 产品定型：菜品首图必填（无图不录入 / 不上架）
  if (!form.value.image) errs.image = '请至少上传 1 张菜品图'
  if (Number(form.value.originalPrice) < 0) errs.originalPrice = '原价不能为负'
  // 原价（§7.26）：判据 originalPrice > price 才划线；≤ 现价划不出折扣，提示纠正
  if (Number(form.value.originalPrice) > 0 && Number(form.value.originalPrice) <= Number(form.value.price)) {
    errs.originalPrice = '原价须高于现价（否则不显示划线）'
  }
  formErrors.value = errs
  return Object.keys(errs).length === 0
}

/** 多值维（主料 / 口味）chips 切换：CSV 读入 → 切换 → CSV 写回（写侧统一出口） */
function toggleMulti(key: 'ingredients' | 'flavorTags', value: string) {
  const arr = parseCsv(form.value[key])
  const i = arr.indexOf(value)
  if (i === -1) arr.push(value)
  else arr.splice(i, 1)
  form.value[key] = formatCsv(arr)
}
function isMultiOn(key: 'ingredients' | 'flavorTags', value: string): boolean {
  return parseCsv(form.value[key]).includes(value)
}

/** 归属 payload（§7.23 第 1 条 DishAdminReq 契约）：stallName 有效时优先；canteenName 随新档口成对提供 */
function ownershipPayload(): { stallId?: number; stallName?: string; canteenName?: string } {
  const { canteenValue, stallValue } = form.value
  const canteenNameOf = (v: string | number): string => {
    if (typeof v === 'number') {
      return store.canteens.find(c => Number(c.id) === Number(v))?.name || ''
    }
    return v.trim()
  }
  if (typeof stallValue === 'number') {
    // 既有档口：只传 stallId（食堂由档口派生，后端不消费 canteenName）
    return { stallId: stallValue }
  }
  // 新档口：stallName + canteenName 成对（既有食堂传其名称 → 后端按名复用；新食堂传输入名 → 自动建档）
  return { stallName: stallValue.trim(), canteenName: canteenNameOf(canteenValue) }
}

async function submit() {
  if (!validate()) return
  // 「他人已修改」轻提示（Q-112 ①）：仅编辑态、仅提示不阻塞——比对编辑基线与当前行 updated_at，
  // 不一致时给「继续保存 / 取消」二选一；取消则退回弹窗保留已填内容（不重载、不丢输入）。
  if (props.editingId != null && editBaselineUpdatedAt.value) {
    const cur = store.dishes.find(x => Number(x.id) === Number(props.editingId))
    const curKey = updatedAtKey(cur?.updated_at)
    if (curKey && curKey !== editBaselineUpdatedAt.value) {
      const goOn = await confirm.confirm('该菜品在你编辑期间已被修改，继续保存将覆盖对方的改动。是否继续保存？')
      if (!goOn) return
      // 用户确认继续：以当前行最新时间为新基线，避免重复提示
      editBaselineUpdatedAt.value = curKey
    }
  }
  submitting.value = true
  const payload: any = {
    name: form.value.name.trim(),
    price: Number(form.value.price),
    ...ownershipPayload(),
    image: form.value.image,
    description: form.value.description,
    status: form.value.status,
    // 描述四维（§7.28）：单选维传机器值 / 空串；多值维传 CSV（空串 = 清空该维）
    dietType: form.value.dietType,
    ingredients: formatCsv(parseCsv(form.value.ingredients)),
    flavorTags: formatCsv(parseCsv(form.value.flavorTags)),
    serveTemp: form.value.serveTemp,
    // 菜品大类（§7.34）：枚举键原样提交（api 层透传，非法值由后端白名单 400）
    mealType: form.value.mealType,
  }
  // 折扣清空契约（WEB-102）：留空时显式携带 null（而非省略字段），确保编辑可撤销已有原价
  // （api 层 dishToApi 0 → 分、null 直传）
  payload.originalPrice = Number(form.value.originalPrice) > 0 ? Number(form.value.originalPrice) : null
  try {
    if (props.editingId != null) {
      await store.updateDish(Number(props.editingId), payload)
      toast.success('菜品已更新')
    } else {
      await store.addDish(payload)
      toast.success('菜品已添加')
    }
    emit('saved')
    emit('close')
  } catch (e: any) {
    // 失败恢复路径：弹窗保留、表单数据保留；若归属档口已失效则清空重选并刷新归属字典
    toast.error(e?.message || '保存失败')
    const sid = Number(form.value.stallValue)
    if (sid && !store.stalls.some(s => Number(s.id) === sid)) {
      form.value.stallValue = ''
      formErrors.value = { ...formErrors.value, stallValue: '所选档口已失效，请重新选择' }
      stallHint.value = '所选档口已失效，请重新选择'
      dishStore.loadAll().catch(() => {})
      canteenStore.loadAll().catch(() => {})
      stallStore.loadAll().catch(() => {})
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <FormDialog
    :show="show"
    :title="editingId != null ? '编辑菜品' : '新增菜品'"
    :width="640"
    confirm-text="保存"
    :confirm-loading="submitting"
    @close="emit('close')"
    :on-confirm="submit"
  >
    <div class="df-form">
      <!-- 第一行：菜品名称 + 所属食堂（归属是录菜第一步心智，保持在弹窗上半屏） -->
      <div class="df-row">
        <div class="field flex-1"><label>菜品名称 <span class="required">*</span></label>
          <input v-model="form.name" placeholder="如：鱼香肉丝" />
          <p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p>
        </div>
        <div class="field flex-1">
          <div class="label-row">
            <label>所属食堂 <span class="required">*</span></label>
            <button
              v-if="typeof form.canteenValue === 'number'"
              class="link rename-link" v-press type="button"
              @click="openRename('canteen', form.canteenValue, store.canteens.find(c => Number(c.id) === Number(form.canteenValue))?.name || '')"
            >改名</button>
          </div>
          <el-select
            v-model="form.canteenValue"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入新食堂名"
            class="w-full"
            @update:model-value="onCanteenChange"
          >
            <el-option v-for="c in canteenOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
          <p class="field-hint">下拉选既有食堂，或直接输入新名称（保存时自动建档）</p>
          <p v-if="formErrors.canteenValue" class="field-error">{{ formErrors.canteenValue }}</p>
        </div>
      </div>

      <!-- 第二行：所属档口（依赖食堂）+ 售价 -->
      <div class="df-row">
        <div class="field flex-1">
          <div class="label-row">
            <label>所属档口 <span class="required">*</span></label>
            <button
              v-if="typeof form.stallValue === 'number'"
              class="link rename-link" v-press type="button"
              @click="openRename('stall', form.stallValue, store.stalls.find(s => Number(s.id) === Number(form.stallValue))?.name || '')"
            >改名</button>
          </div>
          <el-select
            v-model="form.stallValue"
            filterable
            allow-create
            default-first-option
            :placeholder="stallPlaceholder"
            :disabled="stallDisabled"
            class="w-full"
          >
            <el-option v-for="s in stallOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <p class="field-hint">下拉选既有档口，或直接输入新名称（保存时自动建档）</p>
          <p v-if="formErrors.stallValue" class="field-error">{{ formErrors.stallValue }}</p>
          <p v-else-if="stallHint" class="field-error">{{ stallHint }}</p>
        </div>
        <div class="field flex-1"><label>售价（元） <span class="required">*</span></label>
          <input v-model.number="form.price" type="number" min="0" step="0.5" />
          <p v-if="formErrors.price" class="field-error">{{ formErrors.price }}</p>
        </div>
      </div>

      <div class="df-row">
        <div class="field flex-1"><label>原价（元，选填）</label>
          <input v-model.number="form.originalPrice" type="number" min="0" step="0.5" placeholder="高于现价时端上划线" />
          <p v-if="formErrors.originalPrice" class="field-error">{{ formErrors.originalPrice }}</p>
        </div>
        <div class="field flex-1"><label>状态</label>
          <select v-model="form.status">
            <option value="active">在售</option>
            <option value="inactive">已下架</option>
          </select>
        </div>
      </div>

      <!--
        菜品大类（§7.34）：单值互斥的分类维度（**不属于**下方描述四维）。
        选项完全来自后端字典 GET /dishes/meal-types —— 仅可选择、不可自由输入，端上零硬编码中文标签。
      -->
      <div class="df-row">
        <div class="field flex-1">
          <label>菜品大类 <span class="required">*</span></label>
          <el-select
            v-model="form.mealType"
            :placeholder="mealTypePlaceholder"
            :loading="mealTypeStore.loading"
            :disabled="!mealTypeOptions.length"
            :no-data-text="mealTypeStore.loading ? '加载中…' : '暂无可选大类'"
            class="w-full"
          >
            <el-option v-for="o in mealTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <p class="field-hint">{{ mealTypeHint }}</p>
          <p v-if="mealTypeStore.error" class="field-error">
            大类选项加载失败，<button class="link retry-link" v-press type="button" @click="reloadMealTypes">重试</button>
          </p>
          <p v-else-if="formErrors.mealType" class="field-error">{{ formErrors.mealType }}</p>
        </div>
      </div>

      <!-- 描述四维（§7.28）：荤素 / 冷热单选，主料 / 口味多选（机器值经 constants 字典映射） -->
      <div class="df-row">
        <div class="field flex-1"><label>荤素</label>
          <select v-model="form.dietType">
            <option v-for="o in DIET_TYPE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
        </div>
        <div class="field flex-1"><label>冷热</label>
          <select v-model="form.serveTemp">
            <option v-for="o in SERVE_TEMP_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
        </div>
      </div>

      <div class="field"><label>主料（点击切换，可多选）</label>
        <div class="chip-group">
          <button v-for="o in INGREDIENT_OPTIONS" :key="o.value" type="button"
            class="chip-opt" :class="{ on: isMultiOn('ingredients', o.value) }"
            @click="toggleMulti('ingredients', o.value)">{{ o.label }}</button>
        </div>
      </div>

      <div class="field"><label>口味（点击切换，可多选）</label>
        <div class="chip-group">
          <button v-for="o in FLAVOR_TAG_OPTIONS" :key="o.value" type="button"
            class="chip-opt" :class="{ on: isMultiOn('flavorTags', o.value) }"
            @click="toggleMulti('flavorTags', o.value)">{{ o.label }}</button>
        </div>
      </div>

      <div class="field"><label>描述</label>
        <textarea v-model="form.description" rows="2" placeholder="菜品描述"></textarea>
          <p v-if="formErrors.description" class="field-error">{{ formErrors.description }}</p>
      </div>

      <div class="field"><label>图片 <span class="required">*</span></label>
        <ImageUpload v-model="form.image" :max="3" />
        <p v-if="formErrors.image" class="field-error">{{ formErrors.image }}</p>
      </div>
    </div>

    <!-- 改名（属性字典唯一编辑动作；新增由后端按名 upsert，无删除） -->
    <RenameEntityDialog
      :show="renameModal"
      :kind="renameKind"
      :target-id="renameId"
      :current-name="renameName"
      @close="renameModal = false"
    />
  </FormDialog>
</template>

<style scoped>
.df-form { display: flex; flex-direction: column; gap: var(--space-3); }
.df-row { display: flex; gap: var(--space-3); }
.df-row .field { margin-bottom: 0; }
.flex-1 { flex: 1; min-width: 0; }
/* 字段标签行：标签 + 行内「改名」入口右对齐 */
.label-row { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); }
.rename-link { font-size: var(--font-xs); }
.required { color: var(--color-error); }
.field-error { font-size: var(--font-sm); color: var(--color-error); margin-top: var(--space-1); }
.field-hint { font-size: var(--font-xs); color: var(--text-light); margin-top: var(--space-1); }
/* 大类字典加载失败时的字段内重试入口（样式沿用全站 .link，仅补与前置文案的间距） */
.retry-link { margin-left: var(--space-1); }
.chip-group { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.chip-opt {
  padding: var(--space-1) var(--space-4);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-pill);
  font-size: var(--font-sm);
  cursor: pointer;
  background: var(--bg-card);
  color: var(--text-secondary);
  transition: background 0.2s var(--ease-out), border-color 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 160ms var(--ease-out);
}
/* 选中态：浅底 + 主色边框，但**文字走「文字档」**（C1：填充档作文字时深色主题仅 2.90:1） */
.chip-opt.on { background: var(--color-primary-bg); border-color: var(--color-primary); color: var(--color-primary-text); font-weight: var(--weight-medium); }
.chip-opt:active { transform: scale(var(--press-scale)); }
.w-full { width: 100%; }
</style>
