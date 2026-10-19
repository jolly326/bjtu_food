<script setup lang="ts">
/**
 * 卡片长按操作浮层（规格见 docs/ui/web/公共组件与形态基线.md §2.3）。
 * 底部抽屉 + 纯文本行（编辑 / 删除 / 取消），无图标、无红色警示 —— 对齐学生端记忆页长按浮层口径。
 * 删除仍由视图侧走 ElMessageBox 二次确认；被禁用的删除行置灰 + 原因说明。
 * 样式全部收口在 theme.css（.mask--sheet + .sheet*），本组件零 scoped 样式。
 */
import { nextTick, ref, watch } from 'vue'

const props = defineProps<{
  modelValue: boolean
  /** 浮层标题（行名，弱化展示；空则不渲染标题行） */
  title?: string
  /** 删除行禁用（如纠错选项被引用） */
  removeDisabled?: boolean
  /** 删除禁用原因（删除行下方弱化说明） */
  removeHint?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  edit: []
  remove: []
}>()

const firstRow = ref<HTMLButtonElement | null>(null)

/** 打开后焦点落入首行，键盘用户可直接操作 */
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) nextTick(() => firstRow.value?.focus())
  },
)

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.stopPropagation()
    emit('update:modelValue', false)
  }
}
</script>

<template>
  <div v-if="modelValue" class="mask mask--sheet" @click.self="emit('update:modelValue', false)">
    <div class="sheet" role="menu" :aria-label="title || '卡片操作'" @keydown="onKeydown">
      <p v-if="title" class="sheet__title">{{ title }}</p>
      <button ref="firstRow" type="button" class="sheet__row" role="menuitem" @click="emit('edit')">
        编辑
      </button>
      <button
        type="button"
        class="sheet__row"
        role="menuitem"
        :disabled="removeDisabled"
        @click="emit('remove')"
      >
        删除
      </button>
      <p v-if="removeDisabled && removeHint" class="sheet__hint">{{ removeHint }}</p>
      <button
        type="button"
        class="sheet__row sheet__row--muted"
        @click="emit('update:modelValue', false)"
      >
        取消
      </button>
    </div>
  </div>
</template>
