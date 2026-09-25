<template>
  <!-- 举报底部弹层（对齐 BaseSheet 统一骨架：遮罩/grabber/下滑关闭/安全区）：
       原因为**底部弹出的单选列表**（字典端点实时拉取，端上零硬编码），不再填写文本；
       选中原因后点「提交举报」——原因机器值作为 sub 上送（content 可空）。 -->
  <BaseSheet
    :visible="open"
    title="举报评价"
    closable
    z-token="--z-actionsheet"
    @close="emit('update:open', false)"
  >
    <view class="rp-body">
      <!-- 处理承诺（48 小时内处理，纯人工，无自动动作） -->
      <text class="rp-note">请选择举报原因，举报将在 48 小时内处理</text>

      <!-- 原因单选列表（字典下发，展示顺序 = 后端 order） -->
      <view v-if="reasons.length" class="rp-list">
        <view
          v-for="r in reasons"
          :key="r.value"
          class="rp-option"
          :class="{ on: selected === r.value }"
          role="radio"
          :aria-checked="selected === r.value ? 'true' : 'false'"
          :aria-label="r.label"
          @tap="selected = r.value"
        >
          <text class="rp-option-text">{{ r.label }}</text>
        </view>
      </view>
      <!-- 字典加载失败：轻提示 + 关闭重开重拉（低频动作，不引入重试块） -->
      <text v-else class="rp-empty">{{ reasonsFailed ? '举报原因加载失败，请关闭后重试' : '加载中…' }}</text>

      <!-- 提交：选中原因后可用；提交中禁用 -->
      <view
        class="rp-submit"
        :class="{ disabled: !selected || submitting }"
        role="button"
        :aria-label="'提交举报'"
        :aria-disabled="!selected || submitting ? 'true' : 'false'"
        @tap="onSubmit"
      >
        <text class="rp-submit-text">{{ submitting ? '提交中…' : '提交举报' }}</text>
      </view>
    </view>
  </BaseSheet>
</template>

<script setup lang="ts">
/**
 * 举报底部弹层（页包内私有组件）：**原因单选**（底部弹层形态，对齐 BaseSheet 统一骨架）。
 * 选项来自后端字典 `GET /feedback/report-reasons`（PUB，打开时实时拉取，端上零硬编码——PR-12）；
 * 选中值经 `submit` 事件上抛（`useReport` 以 sub 上送，content 可空）。
 */
import { ref, watch } from 'vue'
import BaseSheet from '@/components/BaseSheet.vue'
import { getReportReasons, type ReportReason } from '@/api/feedback'

const props = defineProps<{
  open: boolean
  submitting?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'submit', reasonValue: string): void
}>()

const reasons = ref<ReportReason[]>([])
const reasonsFailed = ref(false)
const selected = ref('')

/** 打开时实时拉取原因字典（公开端点，游客可用）；失败静默降级为提示行 */
watch(
  () => props.open,
  (v) => {
    if (!v) return
    selected.value = ''
    reasonsFailed.value = false
    getReportReasons()
      .then((rows) => {
        reasons.value = rows
        reasonsFailed.value = rows.length === 0
      })
      .catch(() => {
        reasons.value = []
        reasonsFailed.value = true
      })
  },
)

function onSubmit() {
  if (!selected.value || props.submitting) return
  emit('submit', selected.value)
}
</script>

<style scoped>
.rp-body {
  padding-bottom: var(--spacing-xs);
}
/* 处理承诺：次级浅灰小字 */
.rp-note {
  display: block;
  font-size: var(--font-small);
  color: var(--text-tertiary);
  padding-bottom: var(--spacing-sm);
}
/* 原因单选列表：整行热区，选中态主色文字 + 浅底 */
.rp-list {
  display: block;
}
.rp-option {
  padding: var(--spacing-sm) var(--spacing-sm);
  border-radius: 12rpx;
  margin-bottom: var(--spacing-2xs);
  background: var(--bg-soft);
}
.rp-option.on {
  background: var(--color-primary-soft);
}
.rp-option-text {
  font-size: var(--font-body);
  color: var(--text-body);
}
.rp-option.on .rp-option-text {
  color: var(--color-primary);
  font-weight: 600;
}
/* 字典加载中 / 失败：次级浅灰提示行 */
.rp-empty {
  display: block;
  font-size: var(--font-small);
  color: var(--text-tertiary);
  padding: var(--spacing-md) 0;
  text-align: center;
}
/* 提交钮：主色实底（对齐全站主操作语言）；未选原因 / 提交中禁用 */
.rp-submit {
  margin-top: var(--spacing-sm);
  padding: var(--spacing-sm) 0;
  border-radius: 12rpx;
  background: var(--color-primary);
  text-align: center;
}
.rp-submit.disabled {
  opacity: 0.5;
}
.rp-submit-text {
  color: #ffffff;
  font-size: var(--font-body);
  font-weight: 600;
}
</style>
