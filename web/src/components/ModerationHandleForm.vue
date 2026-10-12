<script setup lang="ts">
/**
 * 工单处置表单（[反馈与举报.md](../../../docs/ui/web/反馈与举报.md) §二 —— 意见反馈与举报
 * 两个处置抽屉**共用同一组三输入**）：
 *
 * - `outcome`：处理结论单选（两枚 `.tag-option` 按钮组，对齐「放行绿 < 驳回黄」梯度）；
 * - `replyTitle`：回执短标题（可选，≤40 字；留空按（类型 + 结论）用缺省文案）；
 * - `replyContent`：处置文案（≤600 字；`outcome=rejected` 时必填 ——
 *   即为不采纳原因，**提交前由调用方校验**，服务端对纯空白返回 400）。
 *
 * 举报页额外渲染「同时隐藏被举报评价」复选（`showHideReview`）：仅 `outcome=handled` 时可用；
 * 被举报评价已隐藏时置灰锁定（联动隐藏是幂等的一次性动作，不做无意义的重复隐藏）。
 */
import { useId } from 'vue'
import type { ModerationOutcome } from '@/types/common'

const props = defineProps<{
  /** 结论两枚选项的文案（反馈 = 「通过 / 已处理」「不采纳 / 退回」；举报 = 「受理」「不予处理」） */
  handledLabel: string
  rejectedLabel: string
  /** 回执接收方称谓（反馈 = 提交人；举报 = 举报人），用于占位提示 */
  recipient: string
  /** 渲染「同时隐藏被举报评价」复选（仅举报页传 true） */
  showHideReview?: boolean
  /** 被举报评价已隐藏 ⇒ 复选置灰且恒为 true（不做无意义的重复隐藏） */
  hideLocked?: boolean
}>()

const outcome = defineModel<ModerationOutcome>('outcome', { required: true })
const replyTitle = defineModel<string>('replyTitle', { required: true })
const replyContent = defineModel<string>('replyContent', { required: true })
const hideReview = defineModel<boolean>('hideReview')

/** 同页两个抽屉不会同时打开，但表单控件 id 走 useId 保证全局唯一（`<label for>` 成对） */
const titleId = useId()
const contentId = useId()
const outcomeLabelId = useId()
</script>

<template>
  <div class="field">
    <label :id="outcomeLabelId">处理结论</label>
    <div class="tag-options" role="radiogroup" :aria-labelledby="outcomeLabelId">
      <button
        class="tag-option"
        type="button"
        role="radio"
        :aria-checked="outcome === 'handled'"
        :class="{ active: outcome === 'handled' }"
        @click="outcome = 'handled'"
      >
        {{ props.handledLabel }}
      </button>
      <button
        class="tag-option"
        type="button"
        role="radio"
        :aria-checked="outcome === 'rejected'"
        :class="{ active: outcome === 'rejected' }"
        @click="outcome = 'rejected'"
      >
        {{ props.rejectedLabel }}
      </button>
    </div>
  </div>

  <!-- 举报专用：联动隐藏是一次性动作而非状态，故只在受理分支出现 -->
  <div v-if="props.showHideReview && outcome === 'handled'" class="field">
    <label class="check-line">
      <input
        type="checkbox"
        :checked="hideReview"
        :disabled="hideLocked"
        @change="hideReview = ($event.target as HTMLInputElement).checked"
      />
      <span>同时隐藏被举报评价{{ hideLocked ? '（该评价已隐藏）' : '（默认勾选）' }}</span>
    </label>
  </div>

  <div class="field">
    <label :for="titleId">回执标题（可选，≤40 字）</label>
    <input :id="titleId" v-model="replyTitle" class="form-input" maxlength="40" />
  </div>

  <div class="field">
    <label :for="contentId">
      {{ outcome === 'rejected' ? '不采纳原因（必填，≤600 字）' : '处置文案（可选，≤600 字）' }}
    </label>
    <textarea
      :id="contentId"
      v-model="replyContent"
      class="form-textarea"
      rows="4"
      maxlength="600"
      :placeholder="`将随站内回执下发给${props.recipient}；留空则回执用缺省文案`"
    />
    <div class="hint">{{ replyContent.length }} / 600</div>
  </div>
</template>
