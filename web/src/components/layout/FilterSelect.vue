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
  <div class="filter-select">
    <label v-if="label" class="fs-label">{{ label }}</label>
    <el-select
      :model-value="modelValue"
      :clearable="clearable"
      :placeholder="placeholder"
      class="fs-select"
      :style="{ width: typeof width === 'number' ? width + 'px' : width }"
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
/* 单行布局：label 在左、控件在右，整体高度与搜索框/按钮对齐（约 40px） */
.filter-select {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}
.fs-label { font-size: var(--font-sm); color: var(--text-secondary); font-weight: var(--weight-medium); white-space: nowrap; }
.fs-select {
  flex-shrink: 0;
}
/* 下拉高度对齐搜索框/按钮：去掉 44px 强制高度 */
.fs-select :deep(.el-select__wrapper) {
  min-height: 36px;
  border-radius: var(--radius);
}
</style>
