<script setup lang="ts">
/**
 * 纠错反馈面板（DishSheet 私有子组件）。
 * 选项分页（每页条数见 `SHEET.FEEDBACK_PAGE_SIZE`）在此内部自治（页码 / 上一页下一页 / 末页置灰）；
 * 第 1 页点「返回」= 回主信息块（emit closeFeedback 由组合器处理）。
 * 提交中 / 冷却 / 成功提示期间整体禁用（disabled prop）。
 */
import { computed, ref, watch } from 'vue'

import type { IssueOptionOut } from '@/api'
import { SHEET } from '@/constants'
import SheetPill from './SheetPill.vue'

const props = withDefaults(
  defineProps<{
    options: IssueOptionOut[]
    /** 提交中 / 冷却 / 成功提示期间禁用全部交互 */
    disabled: boolean
  }>(),
  {
    options: () => [],
    disabled: false,
  },
)

const emit = defineEmits<{
  (e: 'pickOption', option: IssueOptionOut): void
  (e: 'closeFeedback'): void
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
const canNext = computed(() => feedbackPage.value < pageCount.value - 1)
const leftNavLabel = computed(() => (feedbackPage.value === 0 ? '返回' : '上一页'))

/** 选项字典变化时，页码越界回退到最后一页 */
watch(
  () => props.options.length,
  () => {
    if (feedbackPage.value > pageCount.value - 1) {
      feedbackPage.value = Math.max(0, pageCount.value - 1)
    }
  },
)

/** 面板重新打开时回到第一页（由父级通过 key 重建或调用 reset；此处用 options 引用变化兜底） */
function resetPage(): void {
  feedbackPage.value = 0
}
defineExpose({ resetPage })

function goPrev(): void {
  if (props.disabled) return
  if (feedbackPage.value === 0) {
    emit('closeFeedback')
    return
  }
  feedbackPage.value -= 1
}

function goNext(): void {
  if (props.disabled || !canNext.value) return
  feedbackPage.value += 1
}
</script>

<template>
  <view class="feedback-pager">
    <view
      class="feedback-pager__strip"
      :style="{ transform: `translateX(-${feedbackPage * 100}%)` }"
    >
      <view v-for="(page, index) in optionPages" :key="index" class="feedback-page">
        <SheetPill
          v-for="option in page"
          :key="option.id"
          :disabled="disabled"
          :aria-label="option.name"
          @press="emit('pickOption', option)"
        >
          {{ option.name }}
        </SheetPill>
        <view
          v-for="n in FEEDBACK_PAGE_SIZE - page.length"
          :key="`spacer-${n}`"
          class="feedback-spacer"
          aria-hidden="true"
        />
      </view>
    </view>

    <view class="dashed-rule" aria-hidden="true" />

    <view class="feedback-nav">
      <SheetPill :disabled="disabled" :aria-label="leftNavLabel" @press="goPrev">
        {{ leftNavLabel }}
      </SheetPill>
      <SheetPill :disabled="disabled || !canNext" aria-label="下一页" @press="goNext">
        下一页
      </SheetPill>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 反馈分页：只切选项行，导航固定 */
.feedback-pager {
  overflow: hidden;
}

.feedback-pager__strip {
  display: flex;
  width: 100%;
  transform: translateX(0);
  transition: transform var(--duration-base) var(--ease-out);
}

.feedback-page {
  display: flex;
  flex: 0 0 100%;
  gap: var(--spacing-xs);
  min-width: 0;
}

.feedback-spacer {
  flex: 1 1 0;
  min-width: 0;
}

.feedback-nav {
  display: flex;
  gap: var(--spacing-sm);
}

.dashed-rule {
  height: 1rpx;
  margin: var(--spacing-sm) 0;
  background-image: repeating-linear-gradient(
    to right,
    var(--sheet-divider) 0,
    var(--sheet-divider) var(--spacing-xs),
    transparent var(--spacing-xs),
    transparent var(--spacing-sm)
  );
}
</style>
