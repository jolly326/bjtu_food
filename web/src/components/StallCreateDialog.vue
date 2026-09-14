<script setup lang="ts">
/**
 * StallCreateDialog：内联新建档口字典项小弹窗（叠层，宽 480，confirmText「创建并选中」）。
 * 归属：docs/loop/design/dish-entry-flow.md §1.3(A) / §3.2。
 * 2026-09-14 Q-113/Q-115：档口是**菜品筛选属性字典**，非业务实体，生命周期只有「新增 / 改名」；
 * 营业时间 / 营业状态 / 实体审核字段均已下线，本弹窗不出现。
 * 食堂（预选菜品表单当前食堂）+ 档口名必填；楼层 / 窗口号 / 位置 / 图片 / 描述选填。
 * 食堂字段旁提供「+ 新建食堂」文字链，复用 CanteenCreateDialog（避免两套实现）。
 */
import { ref, watch, computed } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { createStallInline } from '@/components/EntityCreateDialogs'
import FormDialog from '@/components/FormDialog.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import CanteenCreateDialog from '@/components/CanteenCreateDialog.vue'

const props = withDefaults(
  defineProps<{
    show: boolean
    /** 预选食堂（菜品表单当前已选食堂；无则空必填） */
    defaultCanteenId?: number | null
  }>(),
  { defaultCanteenId: null },
)
const emit = defineEmits<{ close: []; created: [payload: { id: number; canteenId: number; name: string }] }>()

const store = useAdminStore()
const toast = useToastStore()

const form = ref({ canteenId: '' as string | number, name: '', floor: '', windowNo: '', location: '', image: '', description: '' })
const formErrors = ref<Record<string, string>>({})
const submitting = ref(false)
const canteenModal = ref(false)

const canteenOptions = computed(() =>
  store.canteens.map(c => ({ label: c.name, value: Number(c.id) })),
)
const canteenName = computed(() => {
  const hit = store.canteens.find(c => Number(c.id) === Number(form.value.canteenId))
  return hit?.name || ''
})

watch(
  () => props.show,
  (v) => {
    if (!v) return
    form.value = {
      canteenId: props.defaultCanteenId != null ? Number(props.defaultCanteenId) : '',
      name: '', floor: '', windowNo: '', location: '', image: '', description: '',
    }
    formErrors.value = {}
    submitting.value = false
    canteenModal.value = false
  },
)

/** 弹窗内「+ 新建食堂」入口（叠层再上一层） */
function onCanteenCreated(payload: { id: number; name: string }) {
  form.value.canteenId = payload.id
  formErrors.value.canteenId = ''
}

async function submit() {
  const errs: Record<string, string> = {}
  if (!form.value.canteenId) errs.canteenId = '请选择所属食堂'
  if (!form.value.name.trim()) errs.name = '档口名称不能为空'
  formErrors.value = errs
  if (Object.keys(errs).length) return
  submitting.value = true
  try {
    const created = await createStallInline({ ...form.value, canteenId: Number(form.value.canteenId) })
    toast.success('档口已创建')
    emit('created', { ...created, canteenId: Number(form.value.canteenId) })
    emit('close')
  } catch (e: any) {
    // 失败：弹窗不关闭 + 已填数据保留（重名 / 食堂被并发删除等，文案以后端 message 优先）
    toast.error(e?.message || '保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <FormDialog
    :show="show"
    title="新增档口"
    :width="480"
    confirm-text="创建并选中"
    :confirm-loading="submitting"
    :on-confirm="submit"
    @close="emit('close')"
  >
    <p class="dict-hint">档口字典项，作为菜品筛选项使用（可随时改名）</p>
    <div class="field">
      <label>所属食堂 <span class="required">*</span></label>
      <div class="canteen-row">
        <select v-model="form.canteenId" class="canteen-select">
          <option value="">选择食堂</option>
          <option v-for="c in canteenOptions" :key="String(c.value)" :value="c.value">{{ c.label }}</option>
        </select>
        <button class="link" v-press type="button" @click="canteenModal = true">+ 新建食堂</button>
      </div>
      <p class="field-hint">当前：{{ canteenName || '未选择' }}</p>
      <p v-if="formErrors.canteenId" class="field-error">{{ formErrors.canteenId }}</p>
    </div>

    <div class="field mt">
      <label>档口名称 <span class="required">*</span></label>
      <input v-model="form.name" placeholder="如：川湘风味" />
      <p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p>
    </div>

    <div class="row mt">
      <div class="field flex-1"><label>楼层（选填）</label>
        <input v-model="form.floor" placeholder="如 1F" />
      </div>
      <div class="field flex-1"><label>窗口号（选填）</label>
        <input v-model="form.windowNo" placeholder="如 12" />
      </div>
    </div>

    <div class="field mt">
      <label>位置（选填）</label>
      <input v-model="form.location" placeholder="如 一层东侧" />
    </div>

    <div class="field mt">
      <label>图片（选填，至多 3 张）</label>
      <ImageUpload v-model="form.image" :max="3" />
    </div>

    <div class="field mt">
      <label>描述（选填）</label>
      <textarea v-model="form.description" rows="2" placeholder="档口描述" />
    </div>

    <!-- 内联新建食堂（再叠一层，创建后回填到本弹窗的食堂字段） -->
    <CanteenCreateDialog :show="canteenModal" @close="canteenModal = false" @created="onCanteenCreated" />
  </FormDialog>
</template>

<style scoped>
.field { margin-bottom: 0; }
.mt { margin-top: var(--space-3); }
.dict-hint { margin: 0 0 var(--space-3); font-size: var(--font-xs); color: var(--text-light); }
.row { display: flex; gap: var(--space-3); }
.flex-1 { flex: 1; }
.canteen-row { display: flex; align-items: center; gap: var(--space-2); }
.canteen-select { flex: 1; min-width: 0; }
.required { color: var(--color-error); }
.field-error { font-size: var(--font-sm); color: var(--color-error); margin-top: var(--space-1); }
.field-hint { font-size: var(--font-xs); color: var(--text-light); margin-top: var(--space-1); }
</style>
