<script setup lang="ts">
/**
 * 纠错反馈页（DishSheet 滑动区第 2 页）—— Sheet 内三态闭环，无独立弹窗。
 *
 * pick：虚线上面 3 个选项（纯文字，无底无边框），下面「‹返回 / 下一页›」
 * confirm：上面只留选中项（居中），下面「取消 / 确认提交」
 * success：上面「✓ 反馈已收到」，下面居中「关闭」
 *
 * 🔴 虚线全程固定、上下两块高度固定 ⇒ 状态切换时虚线位置不动。
 * 🔴 成功为单向收尾，不提供返回选择页的入口。
 */
import { computed, ref, watch } from 'vue'

import AppIcon from '@/components/AppIcon.vue'
import DashedRule from '@/components/DashedRule.vue'
import type { IssueOptionOut } from '@/api'
import { SHEET } from '@/constants'

export type FeedbackStage = 'pick' | 'confirm' | 'success'

const props = withDefaults(
  defineProps<{
    options: IssueOptionOut[]
    stage: FeedbackStage
    selected: IssueOptionOut | null
    /** 提交中：确认按钮不再响应 */
    busy: boolean
  }>(),
  {
    options: () => [],
    stage: 'pick',
    selected: null,
    busy: false,
  },
)

const emit = defineEmits<{
  (e: 'pick', option: IssueOptionOut): void
  (e: 'cancel'): void
  (e: 'confirm'): void
  /** 返回：滑回菜品信息页（不关闭 Sheet）；success 态「关闭」也走此事件 */
  (e: 'back'): void
}>()

const FEEDBACK_PAGE_SIZE = SHEET.FEEDBACK_PAGE_SIZE

const feedbackPage = ref(0)
const pageCount = computed(() =>
  Math.max(1, Math.ceil(props.options.length / FEEDBACK_PAGE_SIZE)),
)
const optionPages = computed<IssueOptionOut[][]>(() => {
  if (!props.options.length) return [[]]
  const pages: IssueOptionOut[][] = []
  for (let index = 0; index < props.options.length; index += FEEDBACK_PAGE_SIZE) {
    pages.push(props.options.slice(index, index + FEEDBACK_PAGE_SIZE))
  }
  return pages
})
const currentPage = computed(() => optionPages.value[feedbackPage.value] ?? [])
const canNext = computed(() => feedbackPage.value < pageCount.value - 1)
const leftNavLabel = computed(() => (feedbackPage.value === 0 ? '返回' : '上一页'))

/** 回到选择页时页码重置 */
watch(
  () => props.stage,
  (stage) => {
    if (stage === 'pick') feedbackPage.value = 0
  },
)

function onPick(option: IssueOptionOut): void {
  if (props.busy) return
  emit('pick', option)
}

function goPrev(): void {
  if (props.busy) return
  if (feedbackPage.value === 0) {
    // 第一页「返回」⇒ 滑回菜品信息页
    emit('back')
    return
  }
  feedbackPage.value -= 1
}

function goNext(): void {
  if (props.busy || !canNext.value) return
  feedbackPage.value += 1
}

function onConfirm(): void {
  if (props.busy) return
  emit('confirm')
}
</script>

<template>
  <view class="feedback">
    <!-- 虚线上方：选择 / 提示区（高度固定） -->
    <view :key="`top-${stage}`" class="feedback__top">
      <view v-if="stage === 'pick'" class="feedback__options">
        <view
          v-for="option in currentPage"
          :key="option.id"
          class="feedback__option"
          hover-class="feedback__option--pressed"
          aria-role="button"
          :aria-label="option.name"
          @tap="onPick(option)"
        >
          <text class="feedback__label">{{ option.name }}</text>
        </view>
      </view>

      <view v-else-if="stage === 'confirm' && selected" class="feedback__picked">
        <text class="feedback__label">{{ selected.name }}</text>
      </view>

      <view v-else class="feedback__success">
        <AppIcon name="check" :size="32" />
        <text class="feedback__success-text">反馈已收到</text>
      </view>
    </view>

    <!-- 虚线：全程固定 -->
    <DashedRule />

    <!-- 虚线下方：操作区（高度固定） -->
    <view :key="`bottom-${stage}`" class="feedback__bottom">
      <template v-if="stage === 'pick'">
        <view
          class="nav-item"
          hover-class="nav-item--pressed"
          aria-role="button"
          :aria-label="leftNavLabel"
          @tap="goPrev"
        >
          <view class="nav-arrow nav-arrow--left">
            <AppIcon name="chevron" :size="24" />
          </view>
          <text class="nav-text">{{ leftNavLabel }}</text>
        </view>
        <view
          class="nav-item"
          :class="{ 'nav-item--disabled': !canNext }"
          hover-class="nav-item--pressed"
          aria-role="button"
          aria-label="下一页"
          @tap="goNext"
        >
          <text class="nav-text">下一页</text>
          <view class="nav-arrow">
            <AppIcon name="chevron" :size="24" />
          </view>
        </view>
      </template>

      <template v-else-if="stage === 'confirm'">
        <view
          class="nav-item"
          hover-class="nav-item--pressed"
          aria-role="button"
          aria-label="取消"
          @tap="emit('cancel')"
        >
          <text class="nav-text">取消</text>
        </view>
        <view
          class="nav-item"
          :class="{ 'nav-item--disabled': busy }"
          hover-class="nav-item--pressed"
          aria-role="button"
          aria-label="确认提交"
          @tap="onConfirm"
        >
          <text class="nav-text">确认提交</text>
        </view>
      </template>

      <view v-else class="feedback__close-wrap">
        <view
          class="nav-item"
          hover-class="nav-item--pressed"
          aria-role="button"
          aria-label="关闭"
          @tap="emit('back')"
        >
          <text class="nav-text">关闭</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.feedback {
  display: block;
}

/* 上方块：min-height 与信息页同值 ⇒ 虚线同一位置 */
.feedback__top {
  display: flex;
  align-items: center;
  min-height: var(--sheet-block-top);
  animation: fb-fade var(--duration-base) var(--ease-out);
}

.feedback__options {
  display: flex;
  flex: 1;
  align-items: center;
  gap: var(--spacing-sm);
}

/* 选项：纯文字，无底色无边框 */
.feedback__option {
  display: flex;
  flex: 1 1 0;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: var(--tap-target-size);
}

.feedback__option--pressed {
  opacity: 0.55;
}

.feedback__label {
  overflow: hidden;
  color: var(--text-body);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 已选中：纯文字居中 */
.feedback__picked {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: var(--tap-target-size);
}

/* 成功态：对勾 + 文案 */
.feedback__success {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
}

.feedback__success-text {
  color: var(--text-title);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  line-height: 1.3;
}

/* 虚线：位置固定，由 DashedRule 承载 */

/* 下方区：高度 = 信息页底行 */
.feedback__bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: var(--sheet-block-bottom);
  animation: fb-fade var(--duration-base) var(--ease-out);
}

.feedback__close-wrap {
  display: flex;
  flex: 1;
  justify-content: center;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
  min-height: var(--tap-target-size);
  padding: 0 var(--spacing-2xs);
}

.nav-item--pressed {
  opacity: 0.55;
}

.nav-item--disabled {
  opacity: 0.45;
}

.nav-text {
  color: var(--text-body);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  line-height: 1.3;
}

/* 箭头本体向上，旋转成左右 */
.nav-arrow {
  display: flex;
  transform: rotate(90deg);
}

.nav-arrow--left {
  transform: rotate(-90deg);
}

@keyframes fb-fade {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .feedback__top,
  .feedback__bottom {
    animation: none;
  }
}
</style>
