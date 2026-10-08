<template>
  <!-- 举报底部弹层（对齐 BaseSheet 统一骨架：遮罩/grabber/下滑关闭/安全区）：
       原因为**底部弹出的单选列表**（字典端点实时拉取，端上零硬编码），不再填写文本；
       选中原因后点「提交举报」——原因 ID 作为 reasonId 上送（content 可空）。 -->
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
          :key="r.id"
          class="rp-option"
          :class="{ on: selected === r.id }"
          role="radio"
          :aria-checked="selected === r.id ? 'true' : 'false'"
          :aria-label="r.label"
          @tap="selected = r.id"
        >
          <!-- 选中标记：橙色勾（纯图形；选中语义由 aria-checked 表达） -->
          <view class="rp-check" aria-hidden="true">
            <AppIcon v-if="selected === r.id" name="check" :size="24" :color="COLOR_MAP['primary']" />
          </view>
          <text class="rp-option-text">{{ r.label }}</text>
        </view>
      </view>
      <!-- 字典加载失败：轻提示 + 关闭重开重拉（低频动作，不引入重试块） -->
      <text v-else class="rp-empty">{{ reasonsFailed ? '举报原因加载失败，请关闭后重试' : '加载中…' }}</text>

      <!-- 提交：走公共 `ContentButton`（内容宽胶囊唯一实现）。
           未选原因 = 禁用档（灰底灰字）；提交中 = 在途档（转环 + 忽略点击，防重复提交）。 -->
      <ContentButton
        class="rp-submit"
        :text="submitting ? '提交中…' : '提交举报'"
        :disabled="!selected"
        :loading="submitting"
        @press="onSubmit"
      />
    </view>
  </BaseSheet>
</template>

<script setup lang="ts">
/**
 * 举报底部弹层（页包内私有组件）：**原因单选**（底部弹层形态，对齐 BaseSheet 统一骨架）。
 * 选项来自后端字典 `GET /report-reasons`（PUB，打开时实时拉取，端上零硬编码）；
 * 选中原因 ID 经 `submit` 事件上抛（`useReport` 以 reasonId 上送，content 可空）。
 */
import { ref, watch } from 'vue'
import BaseSheet from '@/components/BaseSheet.vue'
import ContentButton from '@/components/ContentButton.vue'
import AppIcon from '@/components/AppIcon.vue'
import { COLOR_MAP } from '@/theme/tokens'
import { listReportReasons, type ReportReason } from '@/api/feedback'

const props = defineProps<{
  open: boolean
  submitting?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'submit', reasonId: number): void
}>()

const reasons = ref<ReportReason[]>([])
const reasonsFailed = ref(false)
const selected = ref(0)

/** 打开时实时拉取原因字典（公开端点，游客可用）；失败静默降级为提示行 */
watch(
  () => props.open,
  (v) => {
    if (!v) return
    selected.value = 0
    reasonsFailed.value = false
    listReportReasons()
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
/* 原因单选列表：整行热区（≥44pt）+ **左侧橙色勾**表达选中（不只靠底色，便于色觉障碍与读屏辨认） */
.rp-list {
  display: flex;
  flex-direction: column;
  /* 选项之间靠留白分组，不用分割线（团队规范） */
  gap: var(--spacing-sm);
}
.rp-option {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  min-height: var(--tap-target-size);
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-btn);
  /* 弹层内控件底走浅白半透档（与属性弹层 / 纠错表单字段行同源），不用冷灰 */
  background: var(--module-input-bg);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
  transition: background-color var(--duration-fast) var(--ease-out);
}
.rp-option.on {
  background: var(--color-primary-soft);
  box-shadow: inset var(--spacing-2xs) 0 0 var(--color-primary);
}
/* 选中标记：橙色勾（纯图形，选中语义同时由 aria-checked 表达） */
.rp-check {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--spacing-lg);
  height: var(--spacing-lg);
}
.rp-option-text {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  color: var(--text-body);
}
.rp-option.on .rp-option-text {
  color: var(--color-primary);
  font-weight: var(--weight-semibold);
}
/* 字典加载中 / 失败：次级浅灰提示行 */
.rp-empty {
  display: block;
  font-size: var(--font-small);
  color: var(--text-tertiary);
  padding: var(--spacing-md) 0;
  text-align: center;
}
/* 提交钮：底色 / 圆角 / 触达 / 禁用 / 在途各档全部由公共 `ContentButton` 承担。
   弹层内为**整幅主操作** ⇒ 覆盖内容宽为通栏（`ContentButton` 默认 inline-flex），
   并与上方选项列表拉开一组间距（40rpx 规范档）。 */
.rp-submit {
  display: flex;
  width: 100%;
  margin-top: var(--spacing-module);
}
</style>
