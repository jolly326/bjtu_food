<template>
  <!-- 意见反馈 · 表单字段区（极简无卡片：三个区块各自一块 `.module-wrap`，区块间只靠页面留白分组）：
       ① 反馈类型（必填，竖排单选，选中项左侧橙色勾）
       ② 具体描述（必填，≤600 字、字数常显标题行右上角，占位文案随类型切换）
       ③ 上传截图（选填，≤3 张，与 `UGC_IMAGE_MAX` 同源） -->
  <view class="fb-form">
    <!-- ① 反馈类型：竖排单选。整行可点，命中区 ≥88rpx -->
    <view class="module-wrap">
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
              <AppIcon
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
        <text v-if="errors['form.type']" class="field-error" role="alert">{{ errors['form.type'] }}</text>
      </view>
    </view>

    <!-- ② 具体描述：上限 600 字、字数常显在标题行右上角；占位随选中类型切换 -->
    <view class="module-wrap">
      <view class="field">
        <view class="field-head">
          <text class="field-label">具体描述<text class="req">*</text></text>
          <text class="counter">{{ model.content.length }}/{{ CONTENT_MAX }}</text>
        </view>
        <textarea
          id="f-form-content"
          :value="model.content"
          class="content-input"
          :class="{ focused }"
          aria-label="具体描述"
          :aria-required="true"
          :aria-invalid="errors['form.content'] ? 'true' : 'false'"
          :placeholder="placeholder"
          :maxlength="CONTENT_MAX"
          :auto-height="true"
          :cursor-spacing="40"
          :adjust-position="true"
          @input="onTextInput"
          @focus="focused = true"
          @blur="focused = false"
        />
        <text v-if="errors['form.content']" class="field-error" role="alert">{{ errors['form.content'] }}</text>
        <text v-if="model.type === 'other'" class="field-help">若发现菜品资料有误，请前往对应菜品详情页提交纠错</text>
      </view>
    </view>

    <!-- ③ 上传截图（选填，**≤3 张**，上限取 `UGC_IMAGE_MAX`，与服务端 `FeedbackConst.IMAGE_MAX` 同源）；
         破图走统一 ImagePlaceholder -->
    <view class="module-wrap">
      <view class="field">
        <text class="field-label">上传截图</text>
        <ImagePicker
          ref="imagePickerRef"
          :model-value="model.images"
          :max="IMAGE_MAX"
          :disabled="submitting"
          @update:model-value="onImagesChange"
          @pick="emit('pick-image')"
        />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/** IssueForm（feedback 包内私有）：意见反馈页表单字段区（类型 + 描述 + 截图） */
import ImagePicker from '@/components/ImagePicker.vue'
import AppIcon from '@/components/AppIcon.vue'
import { ref } from 'vue'
import { COLOR_MAP } from '@/theme/tokens'
import { FEEDBACK_TYPES, type FeedbackType } from '@/types/feedback'
import type { PickSource } from '@/components/imagePickSource'
import { UGC_IMAGE_MAX as IMAGE_MAX } from '@/constants/ugc'
import { CONTENT_MAX, type FeedbackFormModel } from './useFeedback'
import type { UgcImageItem } from '@/components/ugcImage'

defineProps<{
  errors: Record<string, string>
  /** 提交中：禁选截图（与写评价抽屉同口径） */
  submitting?: boolean
  /** 描述框占位（随选中类型切换，由编排层按 FEEDBACK_TYPES 派生；未选类型时给通用文案） */
  placeholder: string
}>()

/**
 * 表单值（`v-model` 双向；父级 reactive 为唯一真源）。
 * 本组件**不改 prop 对象本身**：字段变化以整值替换经 `update:model` 回抛，
 * 由父级 `useFeedback.updateForm` 浅合并回 reactive 真源。
 */
const model = defineModel<FeedbackFormModel>('model', { required: true })

const emit = defineEmits<{
  (e: 'clear', key: string): void
  (e: 'pick', value: FeedbackType): void
  /**
   * 请求选择**配图来源**（拍照 / 相册）。
   * <p>命名带 `-image` 后缀是必须的：本组件已有 `pick` 事件承载「选反馈类型」，
   * 复用同名会让宿主页两个语义互相顶掉（已实际发生过一次，故登记于此）。
   */
  (e: 'pick-image'): void
}>()

/**
 * ImagePicker 的 startPick 中转（拉起选图 → 本地压缩校验 → 只留本地临时路径；上云与机审在提交时）。
 * <p>来源弹层必须在页面根级挂载（本组件位于 scroll-view 内，fixed 层级会被裁剪），
 * 故宿主页拿到来源后再经此透传下去。
 */
const imagePickerRef = ref<{ startPick: (source: PickSource) => void } | null>(null)
function startPick(source: PickSource) {
  imagePickerRef.value?.startPick(source)
}
defineExpose({ startPick })

/** 描述框聚焦态（iOS 焦点反馈：底线高亮主色） */
const focused = ref(false)

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
  model.value = { ...model.value, content: detail?.value ?? '' }
  emit('clear', 'form.content')
}

/** 截图增删：整值替换经 `update:model` 回抛（禁就地改 prop 对象） */
function onImagesChange(items: UgcImageItem[]) {
  model.value = { ...model.value, images: items }
}
</script>

<style scoped lang="scss">
/* 字段级样式（.field / .field-label / .req / .field-error / .content-input / .input-error）统一来自共享 partial */
@use './form-shared';

/* 表单容器：**flex column + gap** 承担「模块之间只靠留白分组」（小程序 WXSS 不支持 `+` 兄弟选择器，
   故用 gap 而非相邻兄弟外边距）；左右 gutter 与上下留白也在此承担，页面不再有大白卡壳。 */
.fb-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  padding: var(--spacing-md) var(--page-gutter) 0;
}

/* ===== ① 反馈类型（竖排单选；整行可点，选项间**不用分割线**，靠模块内留白分组） ===== */
.type-list {
  display: flex;
  flex-direction: column;
  /* 选项之间用上下内边距分组，替代模块内分割线 */
  gap: var(--spacing-md);
}
.type-row {
  /* 命中区：整行可点（min-height ≥88rpx 命中下限）；不套独立灰色背景块 */
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  min-height: var(--tap-target-size);
  padding: 0;
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
  transition: background var(--duration-fast) var(--ease-out);
}
.type-row-pressed {
  /* iOS 按压质感：瞬时浅灰底，松手恢复，无永久底色（不用 opacity 以免压暗文字）。
     底色取全站按压语言 `--bg-soft`（与「我的」页用户卡 / 通知行 / 搜索胶囊同源），不用裸 rgba。 */
  background: var(--bg-soft);
}
/* 选中行：不套灰卡 / 不浮起；仅由左侧橙色勾（.type-check.on）区分选中态 */
/* 选中标记：1rpx 浅灰细描边空心圆 → 选中填主色 + 白勾（纯图形，对读屏隐藏） */
.type-check {
  flex: none;
  width: 40rpx;
  height: 40rpx;
  margin-top: 2rpx;
  border-radius: var(--radius-circle);
  border: 1rpx solid var(--border-color);
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
  font-weight: var(--weight-regular);
  color: var(--text-body);
  line-height: 1.3;
}
/* 选中态仅靠左侧 .type-check.on 区分；label 始终常规字重、不加粗（杜绝权重跳动） */
.type-hint {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  line-height: 1.4;
}

/* ===== ② 描述：标题行（左标题 + 右计数） ===== */
.field-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--spacing-sm);
}
/* 字数计数：常显在标题行右上角（等宽数字避免跳动） */
.counter {
  flex: none;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}
/* 描述框辅助说明：仅 other 类型展示的菜品问题反馈引导 */
.field-help {
  display: block;
  margin-top: var(--spacing-xs);
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  line-height: 1.4;
}
/* 描述框聚焦态：底线切换为品牌主色高亮（新增 iOS 焦点反馈） */
.content-input.focused {
  border-bottom-color: var(--color-primary);
}
</style>
