<script setup lang="ts">
/**
 * 菜品底部弹窗（组合器）。
 * 结构：DishInfo（菜品信息/点赞） ↔ FeedbackPanel（纠错选项/分页） → ConfirmBubble（轻确认）→ SheetToast（成功提示）。
 * 业务状态由 useDishFeedback 统一管理，定时器由 useTimers 统一清理。
 * 只保留按钮操作，不使用左右横滑；大图不参与切换。
 */
import { computed, ref, watch } from 'vue'

import { useDishFeedback, useTimers } from '@/hooks'

import type { DishVO, IssueOptionOut } from '@/api'
import BaseSheet from '@/components/BaseSheet.vue'
import { SHEET } from '@/constants'
import ConfirmBubble from './ConfirmBubble.vue'
import DishInfo from './DishInfo.vue'
import FeedbackPanel from './FeedbackPanel.vue'
import SheetToast from './SheetToast.vue'


const props = defineProps<{
  modelValue: boolean
  dish: DishVO | null
  options: IssueOptionOut[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'change', payload: { id: number; likeCount: number; liked: boolean }): void
}>()

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const feedbackOpen = ref(false)
const confirmOption = ref<IssueOptionOut | null>(null)
const confirmLeaving = ref(false)
const successVisible = ref(false)
const successLeaving = ref(false)
const popping = ref(false)

const {
  likeBusy,
  likeCooling,
  issueBusy,
  issueCooling,
  toggleLike,
  submitIssue,
  syncFromDish,
} = useDishFeedback({
  dish: props.dish,
  onLikeChange: (payload) => {
    emit('change', payload)
  },
  onIssueSubmit: () => {
    confirmOption.value = null
  },
})

const { setTimer, clearAll } = useTimers()

const feedbackDisabled = computed(
  () => issueBusy.value || issueCooling.value || successVisible.value,
)

function resetFeedbackState(): void {
  feedbackOpen.value = false
  confirmOption.value = null
  confirmLeaving.value = false
  successVisible.value = false
  successLeaving.value = false
}

watch(
  () => [props.modelValue, props.dish] as const,
  ([visible, dish]) => {
    if (!visible || !dish) {
      resetFeedbackState()
      clearAll()
      return
    }
    syncFromDish(dish)
    resetFeedbackState()
  },
  { immediate: true },
)

function openFeedback(): void {
  if (successVisible.value) return
  confirmOption.value = null
  confirmLeaving.value = false
  successLeaving.value = false
  feedbackOpen.value = true
}

function closeFeedback(): void {
  feedbackOpen.value = false
}

function pickOption(option: IssueOptionOut): void {
  if (feedbackDisabled.value || confirmOption.value) return
  // 先按压回弹，再淡入确认气泡（时长见 SHEET.PRESS_MS）
  setTimer(() => {
    confirmOption.value = option
  }, SHEET.PRESS_MS)
}

function cancelConfirm(): void {
  if (issueBusy.value || confirmLeaving.value) return
  confirmLeaving.value = true
  setTimer(() => {
    confirmOption.value = null
    confirmLeaving.value = false
  }, SHEET.CONFIRM_FADE_MS)
}

async function confirmSubmit(): Promise<void> {
  const option = confirmOption.value
  if (!option || issueBusy.value || confirmLeaving.value) return
  const ok = await submitIssue(option.id)
  if (!ok) return
  confirmLeaving.value = false
  confirmOption.value = null
  startSuccess()
}

function startSuccess(): void {
  successVisible.value = true
  successLeaving.value = false
  setTimer(() => {
    successLeaving.value = true
    setTimer(() => {
      successVisible.value = false
      successLeaving.value = false
      resetFeedbackState()
    }, SHEET.SUCCESS_FADE_MS)
  }, SHEET.SUCCESS_HOLD_MS)
}

function playPop(): void {
  popping.value = false
  setTimer(() => {
    popping.value = true
    setTimer(() => {
      popping.value = false
    }, SHEET.POP_MS)
  }, 0)
}

function onToggleLike(): void {
  void toggleLike().then((likedNow) => {
    if (likedNow) playPop()
  })
}
</script>

<template>
  <BaseSheet v-model="open" :mask="false">
    <DishInfo
      :dish="dish"
      :like-busy="likeBusy"
      :like-cooling="likeCooling"
      :popping="popping"
      @toggle-like="onToggleLike"
      @open-feedback="openFeedback"
    />

    <FeedbackPanel
      v-if="feedbackOpen"
      :options="options"
      :disabled="feedbackDisabled"
      @pick-option="pickOption"
      @close-feedback="closeFeedback"
    />
  </BaseSheet>

  <!-- 轻确认气泡：奶米底、细棕描边、奶色遮罩 -->
  <ConfirmBubble
    :visible="confirmOption !== null"
    :leaving="confirmLeaving"
    :busy="issueBusy"
    @submit="confirmSubmit"
    @cancel="cancelConfirm"
  />

  <!-- 成功提示：奶米底、细棕描边、无图标 -->
  <SheetToast
    :visible="successVisible"
    :leaving="successLeaving"
    message="反馈已提交"
  />
</template>
