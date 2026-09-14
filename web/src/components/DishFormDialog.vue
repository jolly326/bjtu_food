<script setup lang="ts">
/**
 * DishFormDialog：菜品新增/编辑弹窗（后台菜品管理页唯一入口）。
 * 提交走 adminStore.addDish / updateDish（api 层 dishToApi 自动转分）。
 * 价格/原价/促销价均以「元」编辑。
 *
 * 归属区（docs/loop/design/dish-entry-flow.md §1.2）：**食堂 → 档口两级联动**
 *  - 食堂仅用于级联过滤，不进 payload（菜品只提交 stall_id，后端契约不变）；
 *  - 未选食堂时档口 disabled（可见不可交互，非隐藏）；
 *  - 切换食堂静默清空已选档口，防跨食堂脏数据；
 *  - 编辑态由 dish.stall_id 反查档口 → 反查其食堂回显；
 *  - 两控件面板底部固定「+ 新增食堂 / + 新增档口」，叠层小弹窗新建后自动选中，
 *    本弹窗保持打开且已填字段不丢失（§1.3）。
 */
import { ref, watch, computed } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { parseTags, formatTags } from '@/api/adapter'
import { TAG_OPTIONS } from '@/api/tags'
import FormDialog from '@/components/FormDialog.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import InlineSelectPanel from '@/components/InlineSelectPanel.vue'
import CanteenCreateDialog from '@/components/CanteenCreateDialog.vue'
import StallCreateDialog from '@/components/StallCreateDialog.vue'
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
const toast = useToastStore()
const confirm = useConfirmStore()

const SPICE_OPTIONS = [
  { label: '不辣', value: 0 },
  { label: '微辣', value: 1 },
  { label: '中辣', value: 2 },
  { label: '重辣', value: 3 },
]
/** 风味/菜系权威值域（project_spec §7.9；空选项 = 未填，不提交空串） */
const REGION_OPTIONS = ['东北', '川湘', '粤式', '西北', '清真', '其他']

const form = ref({
  name: '',
  price: 0,
  originalPrice: 0,
  promoPrice: 0,
  /** 所属食堂（级联用，不进 payload） */
  canteenId: '' as string | number,
  /** 所属档口（提交为 stall_id） */
  stallId: '' as string | number,
  image: '',
  description: '',
  alias: '',
  tags: '',
  status: 'active' as 'active' | 'inactive',
  spiceLevel: 0,
  region: '' as string,
})
const formErrors = ref<Record<string, string>>({})
const submitting = ref(false)
/** 归属区错误补充提示（档口失效等失败恢复路径，§1.4-3） */
const stallHint = ref('')

const canteenModal = ref(false)
const stallModal = ref(false)

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
/** 档口选项：严格按所选食堂的 canteen_id 过滤（§1.2 防脏数据） */
const stallOptions = computed(() => {
  if (!form.value.canteenId) return []
  return store.stalls
    .filter(s => Number(s.canteen_id) === Number(form.value.canteenId))
    .map(s => ({ label: s.name, value: Number(s.id) }))
})
const stallPlaceholder = computed(() => (form.value.canteenId ? '选择档口' : '请先选择食堂'))
/** 该食堂下无档口时的面板内联提示（§1.2） */
const stallEmptyText = computed(() =>
  form.value.canteenId ? '该食堂暂无档口，点下方新增' : '请先选择食堂',
)

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
    canteenModal.value = false
    stallModal.value = false
    if (props.editingId != null) {
      const d = store.dishes.find(x => Number(x.id) === Number(props.editingId))
      if (d) {
        // 记录编辑基线（打开时的 updated_at），保存前据此做一次轻量「他人已修改」提示
        editBaselineUpdatedAt.value = updatedAtKey(d.updated_at)
        form.value = {
          name: d.name,
          price: Number(d.price) || 0,
          originalPrice: d.originalPrice ? Number(d.originalPrice) : 0,
          promoPrice: d.promoPrice ? Number(d.promoPrice) : 0,
          canteenId: canteenIdOfStall(d.stall_id),
          stallId: String(d.stall_id ?? ''),
          image: d.image || '',
          description: d.description || '',
          alias: d.alias || '',
          tags: d.tags || '',
          status: d.status as 'active' | 'inactive',
          spiceLevel: d.spiceLevel ?? 0,
          region: d.region || '',
        }
      }
    } else {
      editBaselineUpdatedAt.value = ''
      const presetStall = props.defaultStallId != null ? String(props.defaultStallId) : ''
      form.value = {
        name: '', price: 0, originalPrice: 0, promoPrice: 0,
        canteenId: canteenIdOfStall(presetStall),
        stallId: presetStall,
        image: '', description: '', alias: '', tags: '', status: 'active',
        spiceLevel: 0, region: '',
      }
    }
  },
)

/** 选择食堂：切换时静默清空档口并重算选项（§1.2 / 流程 C） */
function onCanteenChange() {
  form.value.stallId = ''
  formErrors.value.stallId = ''
  stallHint.value = ''
}

/** 新建食堂成功：追加选项 → 自动选中 → 档口 enable 并清空（§1.3(B) / 验收 11） */
function onCanteenCreated(payload: { id: number; name: string }) {
  form.value.canteenId = payload.id
  form.value.stallId = ''
  formErrors.value.canteenId = ''
  formErrors.value.stallId = ''
  stallHint.value = ''
}

/** 新建档口成功：追加选项 → 自动选中 → 食堂级联到其所属食堂（§1.3(A) / 验收 9、10） */
function onStallCreated(payload: { id: number; canteenId: number; name: string }) {
  form.value.canteenId = payload.canteenId
  form.value.stallId = payload.id
  formErrors.value.canteenId = ''
  formErrors.value.stallId = ''
  stallHint.value = ''
}

function validate() {
  const errs: Record<string, string> = {}
  if (!form.value.name.trim()) errs.name = '菜品名称不能为空'
  if (!form.value.price || Number(form.value.price) <= 0) errs.price = '价格必须大于 0'
  if (!form.value.canteenId) errs.canteenId = '请选择所属食堂'
  if (!form.value.stallId) errs.stallId = '请选择所属档口'
  // 产品定型：菜品首图必填（无图不录入 / 不上架）
  if (!form.value.image) errs.image = '请至少上传 1 张菜品图'
  if (Number(form.value.originalPrice) < 0) errs.originalPrice = '原价不能为负'
  if (Number(form.value.promoPrice) < 0) errs.promoPrice = '促销价不能为负'
  if (Number(form.value.promoPrice) > 0) {
    const base = Number(form.value.originalPrice) > 0 ? Number(form.value.originalPrice) : Number(form.value.price)
    if (Number(form.value.promoPrice) >= base) errs.promoPrice = '促销价须低于原价/常规价'
  }
  formErrors.value = errs
  return Object.keys(errs).length === 0
}

function toggleTag(tag: string) {
  // tags 统一 CSV 格式（WEB-101）：读走 parseTags（兼容历史 JSON 脏数据），写走 formatTags
  const arr = parseTags(form.value.tags)
  const i = arr.indexOf(tag)
  if (i === -1) arr.push(tag)
  else arr.splice(i, 1)
  form.value.tags = formatTags(arr)
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
    stall_id: Number(form.value.stallId),
    image: form.value.image,
    description: form.value.description,
    // 搜索别名：后端 DishAdminReq.alias（逗号分隔，trim 后总长 ≤255）。显式传串（含空串=清空别名）
    alias: form.value.alias.trim(),
    tags: formatTags(parseTags(form.value.tags)),
    status: form.value.status,
    spiceLevel: Number(form.value.spiceLevel) || 0,
    region: form.value.region,
  }
  // 折扣清空契约（WEB-102）：留空时显式携带 null（而非省略字段），确保编辑可撤销已有原价/促销价
  // （api 层 dishToApi 0 → 分、null 直传）
  payload.originalPrice = Number(form.value.originalPrice) > 0 ? Number(form.value.originalPrice) : null
  payload.promoPrice = Number(form.value.promoPrice) > 0 ? Number(form.value.promoPrice) : null
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
    // 失败恢复路径（§1.4-3）：弹窗保留、表单数据保留；若归属档口已失效则清空重选并刷新选项
    toast.error(e?.message || '保存失败')
    const sid = Number(form.value.stallId)
    if (sid && !store.stalls.some(s => Number(s.id) === sid)) {
      form.value.stallId = ''
      formErrors.value = { ...formErrors.value, stallId: '所选档口已失效，请重新选择' }
      stallHint.value = '所选档口已失效，请重新选择'
      store.loadAll().catch(() => {})
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
        <div class="field flex-1"><label>所属食堂 <span class="required">*</span></label>
          <InlineSelectPanel
            v-model="form.canteenId"
            :options="canteenOptions"
            placeholder="选择食堂"
            add-text="新增食堂字典项"
            empty-text="暂无食堂，点下方新增"
            renamable
            @update:model-value="onCanteenChange"
            @add="canteenModal = true"
            @rename="(opt) => openRename('canteen', opt.value, opt.label)"
          />
          <p v-if="formErrors.canteenId" class="field-error">{{ formErrors.canteenId }}</p>
        </div>
      </div>

      <!-- 第二行：所属档口（依赖食堂）+ 售价 -->
      <div class="df-row">
        <div class="field flex-1"><label>所属档口 <span class="required">*</span></label>
          <InlineSelectPanel
            v-model="form.stallId"
            :options="stallOptions"
            :placeholder="stallPlaceholder"
            :disabled="!form.canteenId"
            add-text="新增档口字典项"
            :empty-text="stallEmptyText"
            renamable
            @add="stallModal = true"
            @rename="(opt) => openRename('stall', opt.value, opt.label)"
          />
          <p v-if="formErrors.stallId" class="field-error">{{ formErrors.stallId }}</p>
          <p v-else-if="stallHint" class="field-error">{{ stallHint }}</p>
        </div>
        <div class="field flex-1"><label>售价（元） <span class="required">*</span></label>
          <input v-model.number="form.price" type="number" min="0" step="0.5" />
          <p v-if="formErrors.price" class="field-error">{{ formErrors.price }}</p>
        </div>
      </div>

      <div class="df-row">
        <div class="field flex-1"><label>原价（元，选填）</label>
          <input v-model.number="form.originalPrice" type="number" min="0" step="0.5" />
          <p v-if="formErrors.originalPrice" class="field-error">{{ formErrors.originalPrice }}</p>
        </div>
        <div class="field flex-1"><label>促销价（元，选填）</label>
          <input v-model.number="form.promoPrice" type="number" min="0" step="0.5" placeholder="留空=无折扣" />
          <p v-if="formErrors.promoPrice" class="field-error">{{ formErrors.promoPrice }}</p>
        </div>
      </div>

      <div class="df-row">
        <div class="field flex-1"><label>辣度</label>
          <select v-model.number="form.spiceLevel">
            <option v-for="s in SPICE_OPTIONS" :key="s.value" :value="s.value">{{ s.label }}</option>
          </select>
        </div>
        <div class="field flex-1"><label>风味 / 菜系</label>
          <select v-model="form.region">
            <option value="">未填写</option>
            <option v-for="r in REGION_OPTIONS" :key="r" :value="r">{{ r }}</option>
          </select>
        </div>
      </div>

      <div class="field"><label>标签（点击切换）</label>
        <div class="tag-group">
          <button v-for="t in TAG_OPTIONS" :key="t.value" type="button"
            class="tag-opt" :class="{ on: parseTags(form.tags).includes(t.value) }"
            @click="toggleTag(t.value)">{{ t.label }}</button>
        </div>
      </div>

      <div class="field"><label>描述</label>
        <textarea v-model="form.description" rows="2" placeholder="菜品描述"></textarea>
          <p v-if="formErrors.description" class="field-error">{{ formErrors.description }}</p>
      </div>

      <div class="df-row">
        <div class="field flex-1"><label>搜索别名（选填，逗号分隔）</label>
          <input v-model="form.alias" placeholder="如：麻小,小龙虾" />
          <p class="field-hint">学生搜索这些词也能找到本菜品</p>
        </div>
      </div>

      <div class="df-row">
        <div class="field flex-1"><label>状态</label>
          <select v-model="form.status">
            <option value="active">在售</option>
            <option value="inactive">已下架</option>
          </select>
        </div>
      </div>

      <div class="field"><label>图片 <span class="required">*</span></label>
        <ImageUpload v-model="form.image" :max="3" />
        <p v-if="formErrors.image" class="field-error">{{ formErrors.image }}</p>
      </div>
    </div>

    <!-- 叠层小弹窗：新建食堂 / 新建档口（不离开本弹窗，已填字段随 ref 保留） -->
    <CanteenCreateDialog
      :show="canteenModal"
      @close="canteenModal = false"
      @created="onCanteenCreated"
    />
    <StallCreateDialog
      :show="stallModal"
      :default-canteen-id="form.canteenId === '' ? null : Number(form.canteenId)"
      @close="stallModal = false"
      @created="onStallCreated"
    />
    <!-- 改名（属性字典唯一编辑动作；无删除） -->
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
.required { color: var(--color-error); }
.field-error { font-size: var(--font-sm); color: var(--color-error); margin-top: var(--space-1); }
.field-hint { font-size: var(--font-xs); color: var(--text-light); margin-top: var(--space-1); }
.tag-group { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.tag-opt {
  padding: var(--space-1) var(--space-4);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-pill);
  font-size: var(--font-sm);
  cursor: pointer;
  background: var(--bg-card);
  color: var(--text-secondary);
  transition: background 0.2s var(--ease-out), border-color 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 160ms var(--ease-out);
}
.tag-opt.on { background: var(--color-primary-bg); border-color: var(--color-primary); color: var(--color-primary); font-weight: var(--weight-medium); }
.tag-opt:active { transform: scale(var(--press-scale)); }
</style>
