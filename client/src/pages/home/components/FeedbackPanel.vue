<script setup lang="ts">
/** 文本反馈面板（docs/ui/client/菜品首页.md `FeedbackPanel`）。 */
import { ref, watch } from 'vue'

import { feedbackApi } from '@/api'
import type { FeedbackType } from '@/api'
import { FEEDBACK } from '@/constants'

defineOptions({ options: { virtualHost: true } })

const props = defineProps<{
  modelValue: boolean
  dishId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const type = ref<FeedbackType>('product')
const content = ref('')
const submitting = ref(false)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    type.value = props.dishId ? 'dish' : 'product'
    content.value = ''
    submitting.value = false
  },
)

function close(): void {
  emit('update:modelValue', false)
}

function onInput(event: Event): void {
  const detail = (event as unknown as { detail?: { value?: string } }).detail
  content.value = detail?.value ?? ''
}

async function submit(): Promise<void> {
  const text = content.value.trim()
  if (!text) {
    uni.showToast({ title: '请填写反馈内容', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await feedbackApi.submit({
      type: type.value,
      dishId: type.value === 'dish' ? props.dishId ?? undefined : undefined,
      content: text,
    })
    uni.showToast({ title: '反馈已收到', icon: 'none' })
    close()
  } catch (error) {
    uni.showToast({ title: (error as Error).message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view v-if="modelValue" class="fb">
    <view class="fb__scrim" @tap="close" />
    <view class="fb__panel" @tap.stop>
      <text class="fb__title">意见反馈</text>

      <view class="fb__types">
        <text
          class="fb__type"
          :class="{ 'fb__type--on': type === 'product' }"
          @tap="type = 'product'"
        >产品</text>
        <text
          class="fb__type"
          :class="{ 'fb__type--on': type === 'dish', 'fb__type--off': !dishId }"
          @tap="dishId && (type = 'dish')"
        >菜品</text>
      </view>

      <textarea
        class="fb__input"
        :value="content"
        :maxlength="FEEDBACK.MAX_LEN"
        placeholder="说说你的想法"
        placeholder-class="fb__ph"
        @input="onInput"
      />

      <view class="fb__foot">
        <text class="fb__cancel" @tap="close">取消</text>
        <view class="fb__submit" hover-class="fb__submit--press" @tap="submit">
          {{ submitting ? '提交中' : '提交' }}
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.fb {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: var(--z-sheet);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: motion-fade var(--duration-fast) var(--ease-out) both;
}

.fb__scrim {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: var(--overlay-scrim);
}

.fb__panel {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  box-sizing: border-box;
  width: 86%;
  max-height: 80vh;
  overflow-y: auto;
  padding: var(--spacing-lg);
  border-radius: var(--radius-card);
  background: var(--bg-card);
  animation: motion-rise var(--duration-base) var(--ease-out) both;
}

.fb__title {
  font-size: var(--font-h3);
  color: var(--text-title);
}

.fb__types {
  display: flex;
  gap: var(--spacing-lg);
}

.fb__type {
  padding-bottom: var(--spacing-2xs);
  border-bottom: var(--border-width) solid transparent;
  font-size: var(--font-subtitle);
  color: var(--text-body);
}

.fb__type--on {
  border-bottom-color: var(--brand);
  color: var(--brand);
}

.fb__type--off {
  opacity: 0.4;
}

.fb__input {
  box-sizing: border-box;
  width: 100%;
  height: var(--feedback-input-height);
  padding: var(--spacing-sm);
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-card);
  font-size: var(--font-body);
  color: var(--text-title);
}

.fb__ph {
  color: var(--text-disabled);
}

.fb__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-lg);
}

.fb__cancel {
  padding: var(--spacing-xs) var(--spacing-sm);
  font-size: var(--font-body);
  color: var(--text-body);
}

.fb__submit {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: var(--control-height);
  padding: 0 var(--spacing-xl);
  border-radius: var(--radius-pill);
  background: var(--brand);
  font-size: var(--font-body);
  color: var(--on-brand);
}

.fb__submit--press {
  opacity: 0.7;
}
</style>
