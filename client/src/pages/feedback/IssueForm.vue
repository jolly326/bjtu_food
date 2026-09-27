<template>
  <!-- 意见反馈 · 单表单字段区（2026-09-27 改版）：
       ① 反馈类型（必填，竖排单选，选中项左侧橙色勾）
       ② 具体描述（必填，≤1000 字、超 800 显示计数，占位文案随类型切换）
       ③ 上传截图（选填，最多 1 张，虚线框）
       ④ 小字提示（提交内容由维护者查看）
       表单自身不带卡片壳（白卡由页面 .q-card 提供，与详情页写评价抽屉同构）。 -->
  <view class="fb-form">
    <!-- ① 反馈类型：竖排单选。整行可点，命中区 ≥88rpx -->
    <view class="field">
      <text class="field-label">反馈类型<text class="req">*</text></text>
      <view class="type-list" role="radiogroup" aria-label="反馈类型">
        <view
          v-for="t in FEEDBACK_TYPES"
          :key="t.value"
          class="type-row"
          :class="{ 'type-row--on': model.type === t.value }"
          role="radio"
          :aria-checked="model.type === t.value ? 'true' : 'false'"
          :aria-label="t.label"
          hover-class="type-row-pressed"
          hover-stay-time="80"
          @tap="onPick(t.value)"
        >
          <!-- 选中标记：左侧橙色勾（纯图形；选中语义由 .type-row--on 与 aria-checked 表达） -->
          <view class="type-check" :class="{ on: model.type === t.value }" aria-hidden="true">
            <IconSvg
              v-if="model.type === t.value"
              name="check"
              :size="24"
              :color="COLOR_MAP['text-white']"
            />
          </view>
          <view class="type-text">
            <text class="type-label">{{ t.label }}</text>
            <text v-if="t.hint" class="type-hint">{{ t.hint }}</text>
          </view>
        </view>
      </view>
      <text v-if="errors['form.type']" class="field-error">{{ errors['form.type'] }}</text>
    </view>

    <!-- ② 具体描述（非纠错类型）：上限 600 字、字数**常显在标题行右上角**；占位随选中类型切换。
         类型 = 「菜品信息纠错」时本块不渲染（改由页面侧的预填纠错表单承载，用户口径 v2）。 -->
    <view v-if="!isCorrection" class="field">
      <view class="field-head">
        <text class="field-label">具体描述<text class="req">*</text></text>
        <text class="counter">{{ model.content.length }}/{{ CONTENT_MAX }}</text>
      </view>
      <textarea
        id="f-form-content"
        :value="model.content"
        class="content-input"
        :class="{ 'input-error': errors['form.content'] }"
        :placeholder="placeholder"
        :maxlength="CONTENT_MAX"
        :auto-height="true"
        :cursor-spacing="40"
        :adjust-position="true"
        @input="onTextInput"
      />
      <text v-if="errors['form.content']" class="field-error">{{ errors['form.content'] }}</text>
    </view>

    <!-- ③ 上传截图（非纠错类型；选填，最多 1 张）：单图虚线框形态；破图走统一 ImagePlaceholder -->
    <view v-if="!isCorrection" class="field">
      <text class="field-label">上传截图</text>
      <ImagePicker
        single
        :model-value="model.images"
        :max="1"
        :disabled="submitting"
        @update:model-value="onImagesChange"
      />
    </view>

    <!-- ④ 小字提示（只读，不参与交互） -->
    <text class="form-note">提交内容将由项目维护者查看</text>
  </view>
</template>

<script setup lang="ts">
/** IssueForm（feedback 包内私有）：意见反馈页单表单字段区（类型 + 描述 + 截图 + 提示） */
import { computed } from 'vue'
import ImagePicker from '@/components/ImagePicker.vue'
import IconSvg from '@/components/IconSvg.vue'
import { COLOR_MAP } from '@/theme/tokens'
import { FEEDBACK_TYPES, type FeedbackType } from '@/types/feedback'

/** 描述上限（与编排层同源常量；值 = 用户口径 v2 的 600 字） */
const CONTENT_MAX = 600

/** 类型 = 「菜品信息纠错」⇒ 字段区换成预填纠错表单（描述与截图本组件不渲染） */
const isCorrection = computed(() => props.model.type === 'error')

const props = defineProps<{
  model: { type: FeedbackType | ''; content: string; images: string[] }
  errors: Record<string, string>
  /** 提交中：禁选截图（与写评价抽屉同口径） */
  submitting?: boolean
  /** 描述框占位（随选中类型切换，由编排层按 FEEDBACK_TYPES 派生；未选类型时给通用文案） */
  placeholder: string
}>()
const emit = defineEmits<{
  (e: 'clear', key: string): void
  (e: 'pick', value: FeedbackType): void
}>()

function onPick(value: FeedbackType) {
  emit('pick', value)
  emit('clear', 'form.type')
}

/**
 * textarea @input 回调。
 * 平台例外：uni input 事件对象由运行时透传，模板侧类型为 `Event`（无 `detail` 声明），
 * 故形参取 `Event` 并在读取处做一次结构化收窄，避免 `any` 逃逸（同包内既有写法）。
 */
function onTextInput(e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  props.model.content = detail?.value ?? ''
  emit('clear', 'form.content')
}

function onImagesChange(urls: string[]) {
  props.model.images = urls
}
</script>

<style scoped lang="scss">
/* 字段级样式（.field / .field-label / .req / .field-error / .content-input / .input-error）统一来自共享 partial */
@use './form-shared';

/* ===== ① 反馈类型（竖排单选） ===== */
.type-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}
.type-row {
  /* 命中区：整行可点（行高 ≥88rpx 由 padding 撑起，满足 44pt 下限） */
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-tag);
  background: var(--bg-soft);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
  transition: background var(--duration-fast) var(--ease-out);
}
.type-row-pressed {
  opacity: 0.7;
}
/* 选中行：白卡浮起 + 极轻阴影（与全站「选中/强调」语言一致） */
.type-row--on {
  background: var(--bg-card);
  box-shadow: var(--shadow-warm);
}
/* 选中标记：圆形描边占位 → 选中填主色 + 白勾（纯图形，对读屏隐藏） */
.type-check {
  flex: none;
  width: 40rpx;
  height: 40rpx;
  margin-top: 2rpx;
  border-radius: var(--radius-circle);
  border: 2rpx solid var(--border-bold);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
}
.type-check.on {
  border-color: var(--color-primary);
  background: var(--color-primary);
}
.type-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3xs);
}
.type-label {
  font-size: var(--font-body);
  font-weight: var(--weight-medium);
  color: var(--text-body);
  line-height: 1.3;
}
.type-row--on .type-label {
  font-weight: var(--weight-semibold);
  color: var(--text-title);
}
.type-hint {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  line-height: 1.4;
}

/* 字段标题行：左标题 + 右计数（计数与标题同基线，不占额外高度） */
.field-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--spacing-sm);
}
/* 字数计数：常显在标题行**右上角**（等宽数字避免跳动） */
.counter {
  flex: none;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

/* ===== ④ 小字提示 ===== */
.form-note {
  display: block;
  margin-top: var(--spacing-md);
  font-size: var(--font-tiny);
  color: var(--text-tertiary);
  line-height: 1.5;
}
</style>
