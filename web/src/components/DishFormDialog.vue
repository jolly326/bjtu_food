<script setup lang="ts">
/**
 * DishFormDialog：菜品新增/编辑弹窗（食堂详情页 + 全局菜品管理页共用）。
 * 提交走 adminStore.addDish / updateDish（api 层 dishToApi 自动转分）。
 * 价格/原价/促销价均以「元」编辑。
 */
import { ref, watch } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import FormDialog from '@/components/FormDialog.vue'
import ImageUpload from '@/components/ImageUpload.vue'

const props = withDefaults(
  defineProps<{
    show: boolean
    /** 编辑中的菜品 id（null = 新增） */
    editingId?: number | null
    /** 新增时默认选中的档口 */
    defaultStallId?: number | null
    /** 档口下拉选项 [{label, value}] */
    stallOptions: { label: string; value: number }[]
  }>(),
  { editingId: null, defaultStallId: null },
)

const emit = defineEmits<{ close: []; saved: [] }>()

const store = useAdminStore()
const toast = useToastStore()

const SPICE_OPTIONS = [
  { label: '不辣', value: 0 },
  { label: '微辣', value: 1 },
  { label: '中辣', value: 2 },
  { label: '重辣', value: 3 },
]
const PORTION_OPTIONS = [
  { label: '小份', value: 0 },
  { label: '中份', value: 1 },
  { label: '大份', value: 2 },
]
const PERIOD_OPTIONS = ['早餐', '午餐', '晚餐', '夜宵']

const form = ref({
  name: '',
  price: 0,
  originalPrice: 0,
  promoPrice: 0,
  stallId: '' as string | number,
  image: '',
  description: '',
  tags: '',
  status: 'active' as 'active' | 'inactive',
  spiceLevel: 0,
  portion: 0,
  servePeriod: '' as string,
  limited: 0,
})
const formErrors = ref<Record<string, string>>({})
const submitting = ref(false)

watch(
  () => props.show,
  (v) => {
    if (!v) return
    submitting.value = false
    formErrors.value = {}
    if (props.editingId != null) {
      const d = store.dishes.find(x => Number(x.id) === Number(props.editingId))
      if (d) {
        form.value = {
          name: d.name,
          price: Number(d.price) || 0,
          originalPrice: d.originalPrice ? Number(d.originalPrice) : 0,
          promoPrice: d.promoPrice ? Number(d.promoPrice) : 0,
          stallId: String(d.stall_id ?? ''),
          image: d.image || '',
          description: d.description || '',
          tags: d.tags || '',
          status: d.status as 'active' | 'inactive',
          spiceLevel: d.spiceLevel ?? 0,
          portion: d.portion ?? 0,
          servePeriod: d.servePeriod || '',
          limited: d.limited ?? 0,
        }
      }
    } else {
      form.value = {
        name: '', price: 0, originalPrice: 0, promoPrice: 0,
        stallId: props.defaultStallId != null ? String(props.defaultStallId) : '',
        image: '', description: '', tags: '', status: 'active',
        spiceLevel: 0, portion: 0, servePeriod: '', limited: 0,
      }
    }
  },
)

function validate() {
  const errs: Record<string, string> = {}
  if (!form.value.name.trim()) errs.name = '菜品名称不能为空'
  if (!form.value.price || Number(form.value.price) <= 0) errs.price = '价格必须大于 0'
  if (!form.value.stallId) errs.stallId = '请选择所属档口'
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
  const arr = (form.value.tags || '').split(',').map(t => t.trim()).filter(Boolean)
  const i = arr.indexOf(tag)
  if (i === -1) arr.push(tag)
  else arr.splice(i, 1)
  form.value.tags = arr.join(',')
}

function togglePeriod(p: string) {
  const arr = (form.value.servePeriod || '').split(',').map(t => t.trim()).filter(Boolean)
  const i = arr.indexOf(p)
  if (i === -1) arr.push(p)
  else arr.splice(i, 1)
  form.value.servePeriod = arr.join(',')
}

async function submit() {
  if (!validate()) return
  submitting.value = true
  const payload: any = {
    name: form.value.name.trim(),
    price: Number(form.value.price),
    stall_id: Number(form.value.stallId),
    image: form.value.image,
    description: form.value.description,
    tags: form.value.tags,
    status: form.value.status,
    spiceLevel: Number(form.value.spiceLevel) || 0,
    portion: Number(form.value.portion) || 0,
    servePeriod: form.value.servePeriod,
    limited: Number(form.value.limited) || 0,
  }
  if (Number(form.value.originalPrice) > 0) payload.originalPrice = Number(form.value.originalPrice)
  if (Number(form.value.promoPrice) > 0) payload.promoPrice = Number(form.value.promoPrice)
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
    toast.error(e.message || '保存失败')
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
      <div class="df-row">
        <div class="field flex-1"><label>菜品名称 <span class="required">*</span></label>
          <input v-model="form.name" placeholder="如：鱼香肉丝" />
          <p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p>
        </div>
        <div class="field" style="width: 180px"><label>所属档口 <span class="required">*</span></label>
          <select v-model="form.stallId">
            <option value="">选择档口</option>
            <option v-for="s in stallOptions" :key="s.value" :value="s.value">{{ s.label }}</option>
          </select>
          <p v-if="formErrors.stallId" class="field-error">{{ formErrors.stallId }}</p>
        </div>
      </div>

      <div class="df-row">
        <div class="field flex-1"><label>售价（元） <span class="required">*</span></label>
          <input v-model.number="form.price" type="number" min="0" step="0.5" />
          <p v-if="formErrors.price" class="field-error">{{ formErrors.price }}</p>
        </div>
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
        <div class="field flex-1"><label>分量</label>
          <select v-model.number="form.portion">
            <option v-for="p in PORTION_OPTIONS" :key="p.value" :value="p.value">{{ p.label }}</option>
          </select>
        </div>
        <div class="field flex-1"><label>限量</label>
          <select v-model.number="form.limited">
            <option :value="0">不限量</option>
            <option :value="1">限量</option>
          </select>
        </div>
      </div>

      <div class="field"><label>供应时段</label>
        <div class="tag-group">
          <button v-for="p in PERIOD_OPTIONS" :key="p" type="button"
            class="tag-opt" :class="{ on: (form.servePeriod || '').split(',').includes(p) }"
            @click="togglePeriod(p)">{{ p }}</button>
        </div>
      </div>

      <div class="field"><label>标签（点击切换）</label>
        <div class="tag-group">
          <button v-for="t in ['招牌', '新品', '实惠', '热销']" :key="t" type="button"
            class="tag-opt" :class="{ on: (form.tags || '').split(',').includes(t) }"
            @click="toggleTag(t)">{{ t }}</button>
        </div>
      </div>

      <div class="field"><label>描述</label>
        <textarea v-model="form.description" rows="2" placeholder="菜品描述"></textarea>
      </div>

      <div class="df-row">
        <div class="field flex-1"><label>状态</label>
          <select v-model="form.status">
            <option value="active">在售</option>
            <option value="inactive">已下架</option>
          </select>
        </div>
      </div>

      <div class="field"><label>图片</label>
        <ImageUpload v-model="form.image" :max="3" />
      </div>
    </div>
  </FormDialog>
</template>

<style scoped>
.df-form { display: flex; flex-direction: column; gap: var(--space-3); }
.df-row { display: flex; gap: var(--space-3); }
.df-row .field { margin-bottom: 0; }
.flex-1 { flex: 1; }
.required { color: var(--color-error); }
.field-error { font-size: var(--font-sm); color: var(--color-error); margin-top: var(--space-1); }
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
