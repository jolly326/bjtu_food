<script setup lang="ts">
/**
 * RenameEntityDialog：食堂 / 档口「改名」小弹窗（Q-113/Q-115）。
 *
 * 背景：食堂与档口已从「业务实体」降级为**菜品筛选属性字典**，生命周期只有「按名 upsert（随菜品）/ 改名」，
 * 不保留删除（后端 DELETE 端点同步下线）。本弹窗是属性字典唯一的编辑入口，
 * 由菜品表单「所属食堂 / 所属档口」字段旁的「改名」链接触发，复用既有 api/store 改名链路。
 *
 * 交互（PR-13「最少点击」）：
 *  - 打开即聚焦名称输入框，回车即提交（无需先点按钮）；
 *  - 名称无变化时直接关闭，不发请求（避免无意义的写与日志）。
 */
import { ref, watch, nextTick } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import FormDialog from '@/components/FormDialog.vue'

const props = withDefaults(
  defineProps<{
    show: boolean
    /** 字典类型：决定标题文案与走哪条改名 API */
    kind: 'canteen' | 'stall'
    /** 目标 id（null = 未指定，弹窗不渲染提交逻辑） */
    targetId: number | null
    /** 当前名称（回填） */
    currentName?: string
  }>(),
  { currentName: '' },
)

const emit = defineEmits<{ close: []; renamed: [payload: { id: number; name: string }] }>()

const store = useAdminStore()
const toast = useToastStore()

const name = ref('')
const error = ref('')
const submitting = ref(false)
const inputRef = ref<HTMLInputElement | null>(null)

const kindLabel = () => (props.kind === 'canteen' ? '食堂' : '档口')

watch(
  () => props.show,
  async (v) => {
    if (!v) return
    // 每次打开回填当前名称并聚焦，回车即可提交
    name.value = props.currentName || ''
    error.value = ''
    submitting.value = false
    await nextTick()
    inputRef.value?.focus()
    inputRef.value?.select()
  },
)

async function submit() {
  const next = name.value.trim()
  if (!next) { error.value = `${kindLabel()}名称不能为空`; return }
  if (props.targetId == null) { emit('close'); return }
  // 名称未变：直接关闭，不产生无意义请求
  if (next === (props.currentName || '')) { emit('close'); return }
  submitting.value = true
  try {
    if (props.kind === 'canteen') await store.updateCanteen(props.targetId, { name: next })
    else await store.updateStall(props.targetId, { name: next })
    toast.success(`${kindLabel()}已改名`)
    emit('renamed', { id: props.targetId, name: next })
    emit('close')
  } catch (e: any) {
    // 失败保持打开 + 保留已输入名称（重名等），文案以后端 message 优先
    toast.error(e?.message || '改名失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <FormDialog
    :show="show"
    :title="`${kindLabel()}改名`"
    :width="420"
    confirm-text="保存"
    :confirm-loading="submitting"
    :on-confirm="submit"
    @close="emit('close')"
  >
    <div class="field">
      <label>{{ kindLabel() }}名称 <span class="required">*</span></label>
      <input
        ref="inputRef"
        v-model="name"
        :placeholder="`如：${kind === 'canteen' ? '一食堂' : '面食窗口'}`"
        @keydown.enter.prevent="submit"
      />
      <p v-if="error" class="field-error">{{ error }}</p>
      <p class="field-hint">该名称作为菜品筛选条件展示，改名即时生效</p>
    </div>
  </FormDialog>
</template>

<style scoped>
.field { margin-bottom: 0; }
.required { color: var(--color-error); }
.field-error { font-size: var(--font-sm); color: var(--color-error); margin-top: var(--space-1); }
.field-hint { font-size: var(--font-xs); color: var(--text-light); margin-top: var(--space-1); }
</style>
