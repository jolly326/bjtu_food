<script setup lang="ts">
/**
 * FormField：表单字段包裹（T-1）。
 * - props: label / required? / error?(字段下方) / hint?
 * - 插槽：#default（control）、#label-extra（标签右侧补充）。
 * - 通用 label 排版 + 必填标红 + 错误/提示文本，统一字段下方反馈位置。
 */
withDefaults(
  defineProps<{
    label?: string
    required?: boolean
    error?: string
    hint?: string
  }>(),
  { label: '', required: false, error: '', hint: '' },
)
</script>

<template>
  <div class="form-field" :class="{ 'has-error': !!error }">
    <div v-if="label" class="ff-label-row">
      <label class="ff-label">
        {{ label }}
        <span v-if="required" class="ff-required">*</span>
      </label>
      <div v-if="$slots['label-extra']" class="ff-label-extra">
        <slot name="label-extra" />
      </div>
    </div>
    <div class="ff-control">
      <slot />
    </div>
    <p v-if="error" class="ff-error">{{ error }}</p>
    <p v-else-if="hint" class="ff-hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.form-field { margin-bottom: var(--space-5); }
.ff-label-row { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); margin-bottom: var(--space-2); }
.ff-label { font-size: var(--font-sm); color: var(--text-secondary); font-weight: var(--weight-medium); }
.ff-required { color: var(--color-error); margin-left: 2px; }
.ff-label-extra { display: inline-flex; align-items: center; }
.ff-control { display: block; }
.ff-error { margin: var(--space-1) 0 0; font-size: var(--font-sm); color: var(--color-error); }
.ff-hint { margin: var(--space-1) 0 0; font-size: var(--font-sm); color: var(--text-muted); }
</style>
