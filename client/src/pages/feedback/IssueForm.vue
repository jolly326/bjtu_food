<template>
  <!-- 我要反馈问题（issue 模式字段区）：正文 + 配图；样式复用包内共享 partial -->
  <view>
    <view class="field">
      <text class="field-label">想说啥<text class="req">*</text></text>
      <textarea
        id="f-issue-text"
        :value="model.text"
        class="content-input"
        :class="{ 'input-error': errors['issue.text'] }"
        placeholder="想说啥？比如：三食堂的麻辣香锅涨价了 / 排队太久了"
        maxlength="1000"
        :auto-height="true"
        :cursor-spacing="40"
        :adjust-position="true"
        @input="onTextInput"
      />
      <text v-if="model.text.length > 800" class="counter">{{ model.text.length }}/1000</text>
      <text v-if="errors['issue.text']" class="field-error">{{ errors['issue.text'] }}</text>
    </view>

    <!-- 配图（选填 ≤3 张）：统一 ImagePicker（安检上传），随 model.images 交给父页提交；提交中禁选（评审 m1） -->
    <view class="field">
      <text class="field-label">配图</text>
      <ImagePicker :model-value="model.images" :max="3" :disabled="submitting" @update:model-value="onImagesChange" />
    </view>
  </view>
</template>

<script setup lang="ts">
/** IssueForm（feedback 包内私有）：「我要反馈问题」字段区（正文 + 配图） */
import ImagePicker from '@/components/ImagePicker.vue'

const props = defineProps<{
  model: { text: string; images: string[] }
  errors: Record<string, string>
  /** 提交中：禁选配图（评审 m1，与 ReviewComposer 一致） */
  submitting?: boolean
}>()
const emit = defineEmits<{ (e: 'clear', key: string): void }>()

/**
 * textarea @input 回调。
 * 平台例外：uni input 事件对象由运行时透传，模板侧类型为 `Event`（无 `detail` 声明），
 * 故形参取 `Event` 并在读取处做一次结构化收窄，避免 `any` 逃逸（同 useFeedback 位置自定义输入说明）。
 */
function onTextInput(e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  props.model.text = detail?.value ?? ''
  emit('clear', 'issue.text')
}

function onImagesChange(urls: string[]) {
  props.model.images = urls
}
</script>

<style scoped lang="scss">
/* 字段级样式（.field / .field-label / .req / .field-error / .content-input / .input-error）统一来自共享 partial */
@use './form-shared';

.counter { display: block; text-align: right; font-size: var(--font-aux); color: var(--text-tertiary); margin-top: var(--spacing-xs); font-variant-numeric: tabular-nums; }
</style>
