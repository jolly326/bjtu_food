<template>
  <!--
    CorrectionForm（correction 包内私有，页面根组件）——「锚定只读 + 基础信息 + 动态属性 + 图片 + 提交」。

    结构（自上而下，UI 稿「卡片结构」六段；卡片内**不使用分割横线**，靠间距 / 分组区分）：
      ① 菜品锚定行（纯文本、只读，无点击无箭头，**页首无提示条**）→ ② 基础信息（下划线轻量输入）
      → ③ 属性编辑区（`AttributeGroup` ×N）→ ④ 图片（≤3）→ ⑤ 提交说明 → ⑥ 提交按钮。
    表单值由父级 `useCorrection` 持有（唯一真源），本组件只做渲染与就地写回（**不改结构、不换算金额**）。
  -->
  <view class="q-card">
    <!-- ① 菜品锚定行（纯文本 · 只读）：无标题、无底色、无卡片背景；预填未就绪时仅显示载入文案。
         「正在纠错」标题与「菜品固定不可切换」小字已按 UI 稿移除（禁再补回）。 -->
    <view class="anchor">
      <text v-if="detailLoading" class="anchor-loading">正在载入菜品信息…</text>
      <view v-else class="anchor-line">
        <text class="anchor-name">{{ dishName || '——' }}</text>
        <text v-if="dishLocation" class="anchor-sep">｜</text>
        <text v-if="dishLocation" class="anchor-loc">{{ dishLocation }}</text>
      </view>
    </view>

    <!-- 预填未就绪时只留锚定行（不渲染半截表单，避免误改 / 误提交） -->
    <template v-if="!detailLoading">
      <!-- ② 基础信息表单（标签在左 + 下划线下框，禁全包围矩形框；**无「原价」行**；**仅字段名，不放附加说明小字**）
           **R40 双列并排**：菜品名称跨整行；售价｜食堂名称、楼层｜档口名称各占一行两列。
           实现用 flex 两列（`.cell` 各 `flex: 1 1 0` + `min-width: 0`），**不用 CSS grid**
           （本项目 client/src 零 grid 先例，见 UI 稿禁止项 12）。 -->
      <view class="form">
<!-- 行 1：菜品名称（跨整行；名称文字长，拆分后每列宽度不足） -->
        <view class="row" id="f-c-name">
          <view class="cell cell--full">
            <text class="row-label">菜品名称<text class="row-req">*</text></text>
            <!-- 底线状态（聚焦切主色 / 校验错切错误色）挂在**承载底线的容器**上，不在 input 自身 -->
            <view
              class="row-field"
              :class="{
                'row-field--focus': focused === 'name',
                'row-field--error': !!errors['form.name'],
              }"
            >
              <input
                class="row-input"
                type="text"
                :value="model.name"
                placeholder="如：宫保鸡丁"
                placeholder-class="row-ph"
                :maxlength="64"
                :cursor-spacing="40"
                :adjust-position="true"
                @input="onFieldInput('name', $event)"
                @focus="focused = 'name'"
                @blur="focused = ''"
              />
            </view>
            <text v-if="errors['form.name']" class="row-error">{{ errors['form.name'] }}</text>
          </view>
        </view>
<!-- 行 2（双列）：售价 ｜ 食堂名称 -->
        <view class="row row--pair">
          <view class="cell" id="f-c-price">
            <text class="row-label row-label--narrow">售价<text class="row-req">*</text></text>
            <view
              class="row-field"
              :class="{
                'row-field--focus': focused === 'price',
                'row-field--error': !!errors['form.price'],
              }"
            >
              <input
                class="row-input"
                type="digit"
                :value="model.price"
                placeholder="如 12.5"
                placeholder-class="row-ph"
                :maxlength="9"
                :cursor-spacing="40"
                :adjust-position="true"
                @input="onFieldInput('price', $event)"
                @focus="focused = 'price'"
                @blur="focused = ''"
              />
              <text class="row-unit">元</text>
            </view>
            <text v-if="errors['form.price']" class="row-error row-error--narrow">{{ errors['form.price'] }}</text>
          </view>
          <view class="cell" id="f-c-canteenName">
            <text class="row-label row-label--narrow">食堂名称<text class="row-req">*</text></text>
            <view
              class="row-field"
              :class="{
                'row-field--focus': focused === 'canteenName',
                'row-field--error': !!errors['form.canteenName'],
              }"
            >
              <input
                class="row-input"
                type="text"
                :value="model.canteenName"
                placeholder="如：学一食堂"
                placeholder-class="row-ph"
                :maxlength="64"
                :cursor-spacing="40"
                :adjust-position="true"
                @input="onFieldInput('canteenName', $event)"
                @focus="focused = 'canteenName'"
                @blur="focused = ''"
              />
            </view>
            <text v-if="errors['form.canteenName']" class="row-error row-error--narrow">{{ errors['form.canteenName'] }}</text>
          </view>
        </view>
<!-- 行 3（双列）：楼层（**字典单选单元格**，非 input） ｜ 档口名称 -->
        <view class="row row--pair">
          <view class="cell" id="f-c-floor">
            <text class="row-label row-label--narrow">楼层<text class="row-req">*</text></text>
            <!-- picker 单元格：汉字文案 + 右箭头；独立可点件（高 88rpx），**无 input、不可键入**（UI 稿禁止项 10）。
                 底线切错误色：仅必填校验失败时；本控件无「聚焦态」——弹层开合由 BaseSheet 表达。 -->
            <view
              class="row-field row-field--picker"
              :class="{ 'row-field--error': !!errors['form.floor'] }"
              role="button"
              :aria-label="`楼层，当前 ${floorLabel}`"
              hover-class="row-field--picker-pressed"
              hover-stay-time="80"
              @tap="openFloorPicker"
            >
              <text class="row-picker-text" :class="{ 'row-picker-text--ph': !model.floor }">{{ floorLabel }}</text>
              <IconSvg name="arrow-down" :size="24" :color="COLOR_MAP['text-tertiary']" />
            </view>
            <text v-if="errors['form.floor']" class="row-error row-error--narrow">{{ errors['form.floor'] }}</text>
          </view>
          <view class="cell" id="f-c-stallName">
            <text class="row-label row-label--narrow">档口名称<text class="row-req">*</text></text>
            <view
              class="row-field"
              :class="{
                'row-field--focus': focused === 'stallName',
                'row-field--error': !!errors['form.stallName'],
              }"
            >
              <input
                class="row-input"
                type="text"
                :value="model.stallName"
                placeholder="如：麻辣香锅"
                placeholder-class="row-ph"
                :maxlength="64"
                :cursor-spacing="40"
                :adjust-position="true"
                @input="onFieldInput('stallName', $event)"
                @focus="focused = 'stallName'"
                @blur="focused = ''"
              />
            </view>
            <text v-if="errors['form.stallName']" class="row-error row-error--narrow">{{ errors['form.stallName'] }}</text>
          </view>
        </view>
      </view>

      <!-- 楼层受控字典单选弹层（底座 = 公共 BaseSheet，回抛所选汉字） -->
      <FloorPickerSheet
        :visible="floorPickerOpen"
        :value="model.floor"
        :options="FLOOR_OPTIONS"
        @close="floorPickerOpen = false"
        @select="onFloorSelect"
      />

      <!-- ③ 属性编辑区（维度由后端下发：端上零硬编码维度名；候选仅作提示）
           `:first` 由 index 显式下发：首组去上边距 —— 组间距不依赖跨组件 `.ag:first-child`（mp-weixin 不可靠） -->
      <view v-if="model.attributes.length" class="attrs">
        <AttributeGroup
          v-for="(ed, idx) in model.attributes"
          :key="ed.fieldKey"
          :field-key="ed.fieldKey"
          :name="ed.name"
          :value-type="ed.valueType"
          :selected="ed.selected"
          :candidates="ed.candidates"
          :first="idx === 0"
          @change="onAttributeChange"
        />
      </view>

      <!-- ④ 图片（选填，**≤5 张** —— 纠正常需「菜品 + 价签 + 档口牌」多张佐证，宽于评价 / 反馈的 3 张；
           上传 / 预览 / 删除 / 破图占位由公共 ImagePicker 承担） -->
      <view class="img-block">
        <text class="img-title">补充实拍图片（选填，最多 {{ CORRECTION_IMAGE_MAX }} 张）</text>
        <ImagePicker
          ref="imagePickerRef"
          :model-value="model.images"
          :max="CORRECTION_IMAGE_MAX"
          :disabled="!!submitting"
          @update:model-value="onImagesChange"
          @pick="emit('pick-image')"
        />
      </view>

      <!-- ⑤ 提交说明（不暗示提交即生效）+ ⑥ 提交按钮（随内容滚动，非固定底栏） -->
      <text class="submit-note">提交后人工审核，审核通过更新菜品信息</text>
      <view class="submit-area" @tap="onSubmitTap">
        <AppButton
          :text="submitting ? '提交中…' : '提交菜品问题反馈'"
          :disabled="!canSubmit"
          :loading="!!submitting"
          @press="emit('submit')"
        />
      </view>
      <!-- 失败原因（橙字）：页面底部提示，**已填内容完整保留** -->
      <text v-if="submitError" class="submit-error">{{ submitError }}</text>
    </template>
  </view>
</template>

<script setup lang="ts">
/**
 * CorrectionForm —— 菜品问题反馈页「信息有误」型的表单区（渲染 + 校验错误呈现 + 提交触发）
 *
 * 职责边界：本组件**不持有业务状态**（表单值 / 校验 / patch 组装 / 提交全在 `useCorrection`），
 * 只负责「六段结构」的渲染与字段级写回（`props.model` 就地写回，父级 reactive 为唯一真源）。
 * 图标走 `IconSvg`、图片占位走 `ImagePlaceholder`（经 `ImagePicker` 间接消费）、
 * 事件统一 `@tap`、按压用 hover-class 透明度微降（禁 `transform: scale`）、颜色全语义 token。
 */
import { ref, computed } from 'vue'
import AppButton from '@/components/AppButton.vue'
import IconSvg from '@/components/IconSvg.vue'
import ImagePicker from '@/components/ImagePicker.vue'
import AttributeGroup from './AttributeGroup.vue'
import FloorPickerSheet from './FloorPickerSheet.vue'
import { COLOR_MAP } from '@/theme/tokens'
import { CORRECTION_IMAGE_MAX } from '@/constants/ugc'
import { toastInfo } from '@/utils/error'
import { FLOOR_OPTIONS } from './useCorrection'
import type { CorrectionFormModel } from './useCorrection'
import type { PickSource } from '@/components/imagePickSource'

/**
 * 基础信息字段键（R40 起**逐字段显式渲染**，不再由 `FIELDS` 配置 `v-for` 驱动）。
 *
 * 原因：R40 引入**双列并排**（菜品名称跨整行 + 两行双列），行列结构不再等长，
 * 配置式 `v-for` 无法表达「跨整行 / 双列」混合布局 —— 改为按 UI 稿逐行显式书写，
 * 换来布局与文档逐行可对照（**字段顺序、占位、maxlength、单位后缀一律以 UI 稿为准**）。
 */
type FieldKey = 'name' | 'price' | 'canteenName' | 'stallName'

const props = defineProps<{
  /** 表单值（预填详情；用户只改动其中的错误项）——父级 reactive 持有唯一真源 */
  model: CorrectionFormModel
  /** 锚定菜品名（只读展示） */
  dishName: string
  /** 锚定位置「食堂 · 楼层 · 档口」（只读展示） */
  dishLocation: string
  /** 详情拉取中（预填未就绪：不渲染表单，只留锚定卡） */
  detailLoading: boolean
  /** 字段级错误（键 = `form.<字段名>`） */
  errors: Record<string, string>
  /** 提交中：禁选配图、提交区忽略二次点击 */
  submitting?: boolean
  /** 是否可提交（必备项齐全 **且** 有改动）——由 `useCorrection` 判定 */
  canSubmit: boolean
  /** 置灰态点击提示文案（缺哪项 / 无改动 / 限频倒计时） */
  gateHint: string
  /** 提交失败原因（页面底部橙字；空 = 无） */
  submitError: string
}>()

const emit = defineEmits<{
  /** 字段变化：清掉该字段的行内错误（并撤下过期的失败提示） */
  (e: 'clear', key: string): void
  /** 提交（仅在可提交时触发；置灰态由外层热区 toast 兜底） */
  (e: 'submit'): void
  /**
   * 请求选择**配图来源**（拍照 / 相册）——由页面根级弹层承接。
   * <p>本组件位于 scroll-view 内，不能自带 fixed 弹层（层级会被压扁/裁剪）。
   */
  (e: 'pick-image'): void
}>()

/**
 * ImagePicker 的 startPick 中转（拉起选图 → 压缩校验 → 安检上传）。
 * <p>来源弹层在页面根级，故经宿主页选中来源后再透传下来。
 */
const imagePickerRef = ref<{ startPick: (source: PickSource) => void } | null>(null)
function startPick(source: PickSource) {
  imagePickerRef.value?.startPick(source)
}
defineExpose({ startPick })

/** 聚焦字段键（聚焦时底线切主色；空 = 无聚焦） */
const focused = ref('')

/** 楼层字典弹层开合（底座 = 公共 BaseSheet，懒挂载由 `visible` 驱动） */
const floorPickerOpen = ref(false)

/**
 * 楼层单元格展示文案：`form.floor` 即**汉字**（值即显示值）；空串 ⇒ 占位「请选择楼层」。
 */
const floorLabel = computed(() => props.model.floor || '请选择楼层')

/** 打开楼层字典弹层（提交中禁开，避免与提交态交互打架） */
function openFloorPicker() {
  if (props.submitting) return
  floorPickerOpen.value = true
}

/**
 * 选中字典项：写入所选**汉字**（即存储值）并撤下该字段的过期错误提示。
 * 与文本字段口径一致 —— 用户一动内容就撤下上一次提交失败提示。
 */
function onFloorSelect(value: string) {
  props.model.floor = value
  floorPickerOpen.value = false
  emit('clear', 'form.floor')
}

/**
 * input @input 回调（平台例外：uni input 事件对象由运行时透传，形参取 `Event` 后结构化收窄，避免 `any` 逃逸）。
 */
function onFieldInput(key: FieldKey, e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  props.model[key] = detail?.value ?? ''
  emit('clear', `form.${key}`)
}

/**
 * 属性维度选区变化：父级模型就地写回（patch 组装由 `useCorrection` 与基线比对完成）。
 * 同时回抛 `clear`（键取 `form.attributes.<fieldKey>`）——**与文本字段口径一致**：用户一动内容就
 * 撤下上一次的提交失败橙字（属性区无字段级错误，故该键在 `fieldErrors` 中恒为空、仅起撤提示作用）。
 */
function onAttributeChange(fieldKey: string, selected: string[]) {
  const ed = props.model.attributes.find((x) => x.fieldKey === fieldKey)
  if (ed) ed.selected = selected
  emit('clear', `form.attributes.${fieldKey}`)
}

/** 图片增删：同上，就地写回 + 回抛 `clear`（撤下过期的失败提示） */
function onImagesChange(urls: string[]) {
  props.model.images = urls
  emit('clear', 'form.images')
}

/**
 * 提交区外热区：`AppButton` 在 disabled 态 `pointer-events: none`，点击落到本热区 ⇒ 兜底提示；
 * 可提交时由 `AppButton` 自身 emit('press')（此处直接返回，避免一次点击提交两次）。
 */
function onSubmitTap() {
  if (props.submitting || props.canSubmit) return
  toastInfo(props.gateHint || '还不能提交')
}
</script>

<style scoped lang="scss">
/* 下划线字段行样式来自共享 partial（本包内同源） */
@use './field-shared' as field;
/* ===== 主卡片（圆角 16rpx + 浅暖米色细描边 + 柔和卡阴影；一枚大卡承载全部表单） ===== */
.q-card {
  margin: var(--spacing-md) var(--spacing-md) 0;
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border: 2rpx solid var(--border-color);
  border-radius: var(--radius-btn);
  box-shadow: var(--shadow-card);
  box-sizing: border-box;
}

/* ===== ① 菜品锚定行（纯文本 · 只读：**无底色 / 无卡片背景 / 无标题与辅助小字**，无点击、无箭头）
   页首即本行，与下方表单的间距由 `.form` 的 margin-top 承担 ⇒ 本行自身不加 margin-top。 */
.anchor-line { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--spacing-2xs); }
.anchor-name { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); }
.anchor-sep { font-size: var(--font-aux); color: var(--text-placeholder); }
.anchor-loc { font-size: var(--font-aux); color: var(--text-secondary); }
/* 预填未就绪态：仅渲染这一行载入文案（表单不渲染，防误改误提交） */
.anchor-loading { font-size: var(--font-aux); color: var(--text-tertiary); }

/* ===== ② 基础信息（下划线轻量输入 · R40 双列并排） ===== */
.form { margin-top: var(--spacing-lg); }
/* 每行 = 一个 `.row`；R40 起 `.row` 内部装 1（跨整行）或 2（双列）个 `.cell`。
   **不用 CSS grid**（本项目 client/src 零 grid 先例，UI 稿禁止项 12）—— 双列靠 flex 均分。 */
.row { display: flex; flex-wrap: wrap; align-items: center; padding: var(--spacing-xs) 0 var(--spacing-sm); }
/* 双列行：两 `.cell` 等宽均分（`flex: 1 1 0` + `min-width: 0` 允许内部 input 收缩而不撑破列宽） */
.row--pair { gap: var(--spacing-lg); }
.cell { flex: 1 1 0; min-width: 0; }
/* 跨整行单元格：不参与均分（`flex: 1 1 100%` 独占本行） */
.cell--full { flex: 1 1 100%; }
/* 单元格内纵向排布：标签 / 输入 / 错误小字依次换行（错误小字换行到下一行而非挤在同行右侧） */
.cell > .row-label,
.cell > .row-field,
.cell > .row-error { display: block; width: 100%; }
.cell > .row-field { display: flex; }
.row-label {
  flex: none;
  /* 跨整行行标签宽 160rpx（4 字标签 + 必填星） */
  width: var(--form-label-width);
  font-size: var(--font-aux);
  font-weight: var(--weight-medium);
  color: var(--text-secondary);
}
/* 双列行标签窄档 96rpx（列宽 ≈307rpx，扣标签后余 ≈190rpx 供输入；4 字标签会换行，故窄档） */
.row-label--narrow { width: 96rpx; }
.row-req { color: var(--color-error); margin-left: var(--spacing-2xs); font-weight: var(--weight-heavy); }
.row-field {
  flex: 1 1 auto;
  min-width: 0;
  @include field.underline;
}
/* 聚焦：底线切主色（主色 = 唯一强调色，不用描边框） */
.row-field--focus { border-bottom-color: var(--color-primary); }
/* 校验错误：底线切错误色（与行内错误小字同源；错误态规则置于聚焦之后 ⇒ 错误优先可见） */
.row-field--error { border-bottom-color: var(--color-error); }
/* 楼层 picker 单元格（R40）：与同行 input 同高同底线形态，但**不可键入**（无 input、无焦点态）。
   下划线形态 + 整格可点 ⇒ 88rpx 同时满足视觉与触达（独立可点件，不适用属性 chip 的 67rpx 例外）。 */
.row-field--picker {
  justify-content: space-between;
  -webkit-tap-highlight-color: transparent;
}
.row-field--picker-pressed { background: var(--bg-soft); }
.row-picker-text {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 空值占位（灰字，与 `.row-ph` 同档；`text` 元素不消费 placeholder-class，故显式给类） */
.row-picker-text--ph { color: var(--text-placeholder); }
.row-input {
  flex: 1 1 auto;
  min-width: 0;
  height: 100%;
  font-size: var(--font-body);
  color: var(--text-primary);
  /* 等宽数字：售价等数字输入不因字宽抖动（对文本行无害） */
  font-variant-numeric: tabular-nums;
}
.row-ph { color: var(--text-placeholder); }
.row-unit { flex: none; font-size: var(--font-tiny); color: var(--text-tertiary); }
/* 校验错误：行内橙（错误色 token）小字，显示在**所属单元格内**、与该单元格输入左边界对齐
   （跨整行 160rpx / 双列 96rpx 两种缩进，见 UI 稿 §3 交互口径） */
.row-error {
  margin-top: var(--spacing-2xs);
  padding-left: var(--form-label-width);
  font-size: var(--font-tiny);
  color: var(--color-error);
}
.row-error--narrow { padding-left: 96rpx; }

/* ===== ③ 属性编辑区（每个维度一组 AttributeGroup；组成员间距由组件内 `.ag` 承担） ===== */
.attrs { margin-top: var(--spacing-lg); }

/* ===== ④ 图片区 ===== */
.img-block { margin-top: var(--spacing-lg); }
.img-title { display: block; margin-bottom: var(--spacing-sm); font-size: var(--font-aux); font-weight: var(--weight-medium); color: var(--text-secondary); }

/* ===== ⑤⑥ 提交说明 + 提交按钮（随内容滚动）+ 失败提示 ===== */
.submit-note {
  display: block;
  margin-top: var(--spacing-lg);
  font-size: var(--font-tiny);
  line-height: 1.5;
  color: var(--text-tertiary);
}
.submit-area { margin-top: var(--spacing-sm); }
.submit-error {
  display: block;
  margin-top: var(--spacing-sm);
  font-size: var(--font-tiny);
  line-height: 1.5;
  color: var(--color-error);
}
</style>
