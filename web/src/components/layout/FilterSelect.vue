<script setup lang="ts">
/**
 * FilterSelect：封装 el-select 的筛选下拉（T-1）。
 * - 禁止页面裸写 el-select（约束）。
 * - props: label / options / clearable(默认true) / width(默认160px，命中≥44px)
 * - v-model 双向；change 事件透传。
 */
interface Option {
  label: string
  value: string | number
}

const props = withDefaults(
  defineProps<{
    modelValue?: string | number
    label?: string
    options?: Option[]
    clearable?: boolean
    width?: number | string
    placeholder?: string
  }>(),
  { modelValue: '', label: '', options: () => [], clearable: true, width: 160, placeholder: '请选择' },
)

const emit = defineEmits<{
  'update:modelValue': [value: string | number]
  change: [value: string | number]
}>()

function onUpdate(v: string | number) {
  emit('update:modelValue', v)
  emit('change', v)
}
</script>

<template>
  <div class="filter-select" :style="{ width: typeof width === 'number' ? width + 'px' : width }">
    <label v-if="label" class="fs-label">{{ label }}</label>
    <el-select
      :model-value="modelValue"
      :clearable="clearable"
      :placeholder="placeholder"
      class="fs-select"
      @update:model-value="onUpdate"
      @change="onUpdate"
    >
      <el-option
        v-for="opt in options"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>
  </div>
</template>

<style scoped>
.filter-select {
  display: inline-flex;
  flex-direction: column;
  gap: var(--space-1);
}
.fs-label { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.fs-select {
  width: 100%;
}
/* 命中区 ≥44px：下拉控件最小高度 */
.fs-select :deep(.el-select__wrapper) {
  min-height: 44px;
  border-radius: var(--radius);
}
</style>
