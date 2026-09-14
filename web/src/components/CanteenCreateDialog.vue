<script setup lang="ts">
/**
 * CanteenCreateDialog：内联新建食堂字典项小弹窗（叠层，宽 520，confirmText「创建并选中」）。
 * 归属：docs/loop/design/dish-entry-flow.md §1.3(B) / §3.3。
 * 2026-09-14 Q-113/Q-115：食堂是**菜品筛选属性字典**，非业务实体，生命周期只有「新增 / 改名」，
 * 无营业状态 / 营业时间 / 实体审核字段（本弹窗均不出现）。
 * 仅名称必填；位置 / 描述 / 图片选填。成功后 emit created({id, name})，由调用方级联选中。
 */
import { ref, watch } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { createCanteenInline } from '@/components/EntityCreateDialogs'
import FormDialog from '@/components/FormDialog.vue'
import ImageUpload from '@/components/ImageUpload.vue'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ close: []; created: [payload: { id: number; name: string }] }>()

const toast = useToastStore()

const form = ref({ name: '', location: '', description: '', image: '' })
const formErrors = ref<Record<string, string>>({})
const submitting = ref(false)

watch(
  () => props.show,
  (v) => {
    if (!v) return
    // 每次打开重置；失败时弹窗保持打开（表单数据随 ref 保留）
    form.value = { name: '', location: '', description: '', image: '' }
    formErrors.value = {}
    submitting.value = false
  },
)

async function submit() {
  const errs: Record<string, string> = {}
  if (!form.value.name.trim()) errs.name = '食堂名称不能为空'
  formErrors.value = errs
  if (Object.keys(errs).length) return
  submitting.value = true
  try {
    const created = await createCanteenInline(form.value)
    toast.success('食堂已创建')
    emit('created', created)
    emit('close')
  } catch (e: any) {
    // 失败：弹窗不关闭 + 已填数据保留，文案由后端 message 优先，前端兜底
    toast.error(e?.message || '保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <FormDialog
    :show="show"
    title="新增食堂"
    :width="520"
    confirm-text="创建并选中"
    :confirm-loading="submitting"
    :on-confirm="submit"
    @close="emit('close')"
  >
    <p class="dict-hint">食堂字典项，作为菜品筛选项使用（可随时改名）</p>
    <div class="field">
      <label>食堂名称 <span class="required">*</span></label>
      <input v-model="form.name" placeholder="如：一食堂" />
      <p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p>
    </div>

    <div class="field mt">
      <label>位置（选填）</label>
      <input v-model="form.location" placeholder="如：主校区东侧" />
    </div>

    <div class="field mt">
      <label>描述（选填）</label>
      <textarea v-model="form.description" rows="2" placeholder="食堂描述" />
    </div>

    <div class="field mt">
      <label>图片（选填，至多 3 张）</label>
      <ImageUpload v-model="form.image" :max="3" />
    </div>
  </FormDialog>
</template>

<style scoped>
.field { margin-bottom: 0; }
.mt { margin-top: var(--space-3); }
.dict-hint { margin: 0 0 var(--space-3); font-size: var(--font-xs); color: var(--text-light); }
.required { color: var(--color-error); }
.field-error { font-size: var(--font-sm); color: var(--color-error); margin-top: var(--space-1); }
</style>
