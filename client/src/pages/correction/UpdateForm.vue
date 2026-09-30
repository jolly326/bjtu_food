<template>
  <!-- 菜品纠错字段区（**进页即按 dishId 预绑定，表单内不允许切换菜品**）：
       菜品行（只读）→ 预填表单（名称 / 价格 / 食堂名 / 档口 / 描述属性 / 图片）→ 用户只改错的地方。
       食堂名 / 档口均为 line-input 自由文本（无字典 / 无 picker）；
       描述属性按**动态属性模型**渲染（有候选值 = 点选 chips，无候选值 = 自由文本 chips）。 -->
  <view>
    <!-- 绑定菜品（只读展示）：名称 + 「食堂 · 档口」 -->
    <view class="field">
      <text class="field-label">要更新哪道菜</text>
      <view class="dish-card">
        <view class="dish-card-main">
          <text class="dish-card-name">{{ dishName || '——' }}</text>
          <text class="dish-card-sub">{{ dishLocation || '——' }}</text>
          <text v-if="detailLoading" class="dish-card-loading">正在载入菜品信息…</text>
        </view>
      </view>
    </view>

    <!-- 预填表单（预填完成后展示；用户只改差异项） -->
    <template v-if="!detailLoading">
      <!-- 名称（预填） -->
      <view class="field">
        <text class="field-label">名称<text class="req">*</text></text>
        <input
          id="f-c-name"
          :value="model.name"
          class="line-input"
          :class="{ 'input-error': errors['form.name'] }"
          placeholder="菜品名称"
          maxlength="64"
          :cursor-spacing="40"
          :adjust-position="true"
          @input="onNameInput"
        />
        <text v-if="errors['form.name']" class="field-error">{{ errors['form.name'] }}</text>
      </view>

      <!-- 价格（预填 = 详情现价，元；提交由 API 层口径转分） -->
      <view class="field">
        <text class="field-label">价格（元）<text class="req">*</text></text>
        <input
          id="f-c-price"
          :value="model.price"
          class="line-input line-input--price"
          :class="{ 'input-error': errors['form.price'] }"
          placeholder="如 12.5"
          type="digit"
          maxlength="9"
          :cursor-spacing="40"
          :adjust-position="true"
          @input="onPriceInput"
        />
        <text v-if="errors['form.price']" class="field-error">{{ errors['form.price'] }}</text>
      </view>

      <!-- 食堂名（line-input 自由文本，预填详情 canteenName） -->
      <view class="field">
        <text class="field-label">食堂名<text class="req">*</text></text>
        <input
          id="f-c-canteenName"
          :value="model.canteenName"
          class="line-input"
          :class="{ 'input-error': errors['form.canteenName'] }"
          placeholder="如：一食堂"
          maxlength="64"
          :cursor-spacing="40"
          :adjust-position="true"
          @input="onCanteenInput"
        />
        <text v-if="errors['form.canteenName']" class="field-error">{{ errors['form.canteenName'] }}</text>
      </view>

      <!-- 档口（line-input 自由文本，预填详情 stallName） -->
      <view class="field">
        <text class="field-label">档口<text class="req">*</text></text>
        <input
          id="f-c-stallName"
          :value="model.stallName"
          class="line-input"
          :class="{ 'input-error': errors['form.stallName'] }"
          placeholder="如：麻辣香锅"
          maxlength="64"
          :cursor-spacing="40"
          :adjust-position="true"
          @input="onStallInput"
        />
        <text v-if="errors['form.stallName']" class="field-error">{{ errors['form.stallName'] }}</text>
      </view>

      <!-- 描述属性（动态维度）：候选值点选 chips + **恒可自由输入新值**（候选仅为提示） -->
      <view v-for="ed in model.attributes" :key="ed.fieldKey" class="field">
        <text class="field-label">{{ ed.name }}</text>
        <!-- 参考候选：点选 chips（single = 单选替换；multi = 多选切换） -->
        <view v-if="ed.options.length" class="chips" role="group" :aria-label="ed.name">
          <view
            v-for="opt in ed.options"
            :key="opt"
            class="chip"
            :class="{ 'chip--off': !ed.selected.includes(opt) }"
            role="button"
            :aria-label="`${ed.name} ${opt}`"
            :aria-pressed="ed.selected.includes(opt) ? 'true' : 'false'"
            hover-class="row-pressed"
            hover-stay-time="80"
            @tap="toggleOption(ed, opt)"
          >
            <text class="chip-text">{{ opt }}</text>
          </view>
        </view>
        <!-- 已选「候选之外」的自定义值：单独展示、可删除 -->
        <view v-if="customValues(ed).length" class="chips">
          <view v-for="(v, j) in customValues(ed)" :key="`${ed.fieldKey}-c-${j}-${v}`" class="chip">
            <text class="chip-text">{{ v }}</text>
            <view
              class="chip-x"
              role="button"
              :aria-label="`删除${ed.name} ${v}`"
              hover-class="row-pressed"
              hover-stay-time="80"
              @tap="removeValue(ed, v)"
            >
              <IconSvg name="close" :size="18" :color="COLOR_MAP['text-tertiary']" />
            </view>
          </view>
        </view>
        <!-- 自由输入：随时可加候选之外的新值（single 维填入即替换，multi 维追加） -->
        <view class="chip-add-row">
          <input
            v-model="drafts[ed.fieldKey]"
            class="chip-input"
            :placeholder="`加个${ed.name}`"
            maxlength="10"
            confirm-type="done"
            :cursor-spacing="40"
            :adjust-position="true"
            @confirm="addValue(ed)"
          />
          <view
            class="chip-add"
            role="button"
            :aria-label="`添加${ed.name}`"
            hover-class="row-pressed"
            hover-stay-time="80"
            @tap="addValue(ed)"
          ><text class="chip-add-text">添加</text></view>
        </view>
      </view>

      <!-- 图片（预填菜品首图，可增删，**≤3 张**；ImagePicker 安检上传，提交中禁选） -->
      <view class="field">
        <text class="field-label">图片</text>
        <ImagePicker :model-value="model.images" :max="UGC_IMAGE_MAX" :disabled="submitting" @update:model-value="onImagesChange" />
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { UGC_IMAGE_MAX } from '@/constants/ugc'
/**
 * UpdateForm（correction 包内私有）：菜品纠错预填表单字段区。
 *
 * 描述属性按**动态属性模型**（值即中文）渲染：维度由后端下发（端上零硬编码维度名）；
 * 候选值仅作提示（点选 chips，`single` 单选 / `multi` 多选），**恒可自由输入候选之外的新值**。
 */
import { ref } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import ImagePicker from '@/components/ImagePicker.vue'
import { COLOR_MAP } from '@/theme/tokens'
import type { AttributeEditor } from './useCorrection'

const props = defineProps<{
  /** 表单值（预填详情；用户只改动其中的错误项） */
  model: {
    name: string
    price: string
    canteenName: string
    stallName: string
    images: string[]
    attributes: AttributeEditor[]
  }
  /** 绑定菜品名（只读展示；进页即由 dishId 预绑定，不可切换） */
  dishName: string
  /** 绑定菜品位置（「食堂 · 档口」，只读展示） */
  dishLocation: string
  /** 详情拉取中（预填未就绪，表单暂不展示） */
  detailLoading: boolean
  errors: Record<string, string>
  /** 提交中：禁选配图 */
  submitting?: boolean
}>()

const emit = defineEmits<{
  (e: 'clear', key: string): void
}>()

/** 自由文本维度的输入草稿（按维度键分组） */
const drafts = ref<Record<string, string>>({})

/** 已选中但不在候选里的自定义值（用于单独展示可删除 chip） */
function customValues(ed: AttributeEditor): string[] {
  return ed.selected.filter((v) => !ed.options.includes(v))
}

/** 候选点选：single 替换、multi 切换 */
function toggleOption(ed: AttributeEditor, value: string) {
  if (ed.valueType === 'single') {
    ed.selected = ed.selected[0] === value ? [] : [value]
    return
  }
  ed.selected = ed.selected.includes(value)
    ? ed.selected.filter((x) => x !== value)
    : [...ed.selected, value]
}

/** 自由输入：single 填入即替换；multi 追加（去重） */
function addValue(ed: AttributeEditor) {
  const value = (drafts.value[ed.fieldKey] || '').trim()
  if (!value) return
  if (ed.valueType === 'single') {
    ed.selected = [value]
  } else if (!ed.selected.includes(value)) {
    ed.selected.push(value)
  }
  drafts.value[ed.fieldKey] = ''
}

/** 删除某一项（候选或自定义） */
function removeValue(ed: AttributeEditor, value: string) {
  ed.selected = ed.selected.filter((x) => x !== value)
}

/**
 * input @input 回调（平台例外：uni input 事件对象由运行时透传，形参取 `Event` 并结构化收窄，避免 `any` 逃逸）。
 */
function onNameInput(e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  props.model.name = detail?.value ?? ''
  emit('clear', 'form.name')
}

function onPriceInput(e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  props.model.price = detail?.value ?? ''
  emit('clear', 'form.price')
}

function onCanteenInput(e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  props.model.canteenName = detail?.value ?? ''
  emit('clear', 'form.canteenName')
}

function onStallInput(e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  props.model.stallName = detail?.value ?? ''
  emit('clear', 'form.stallName')
}

function onImagesChange(urls: string[]) {
  props.model.images = urls
}
</script>

<style scoped lang="scss">
/* 字段级样式（.field / .field-label / .req / .field-error / .picker-row / .picker-value / .input-error）统一来自共享 partial */
/* 字段级共享样式仍由 feedback 包持有（`_form-shared.scss`）：两页共用同一份、只在**编译期内联**，
   不产生运行时跨分包依赖（改一处两页同步；需彻底解耦时可上提到 src/styles/）。 */
@use '../feedback/form-shared';

/* 按压反馈：统一 hover-class + opacity（与全站按压语言一致） */
.row-pressed { opacity: 0.7; }

/* ===== 绑定菜品卡（只读，无「重选」入口 —— 表单内不允许切换菜品） ===== */
.dish-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  background: var(--bg-input);
  border-radius: var(--radius-icon);
}
.dish-card-main { flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.dish-card-name { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dish-card-sub { font-size: var(--font-tiny); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dish-card-loading { font-size: var(--font-tiny); color: var(--color-primary-text); }

/* ===== 单行输入（名称 / 价格 / 食堂名 / 档口） ===== */
.line-input {
  width: 100%;
  height: 88rpx;
  padding: 0 var(--spacing-md);
  background: var(--bg-input);
  border-radius: var(--radius-icon);
  box-sizing: border-box;
  font-size: var(--font-body);
  color: var(--text-primary);
  border: 2rpx solid transparent;
}
/* .input-error 的 border 覆盖：须与共享 partial 同机制（error 边框在后） */
.line-input.input-error { border-color: var(--color-error); }
.line-input--price { font-variant-numeric: tabular-nums; }

/* ===== chips（描述属性：点选 / 自由文本；贴纸观感 = 主色浅底 + 深主色文字，与全站 chip 语言同档） ===== */
.chips { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); margin-bottom: var(--spacing-xs); }
.chip {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
  padding: var(--spacing-2xs) var(--spacing-sm);
  background: var(--color-primary-soft);
  border-radius: var(--radius-pill);
}
/* 未选中候选值：中性浅底 + 次级灰字（与选中态可辨） */
.chip--off { background: var(--bg-soft); }
.chip--off .chip-text { color: var(--text-secondary); }
.chip-text { font-size: var(--font-aux); color: var(--color-primary-text); font-weight: var(--weight-medium); line-height: 1.4; }
.chip-x {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32rpx;
  height: 32rpx;
  -webkit-tap-highlight-color: transparent;
}
.chip-add-row { display: flex; align-items: center; gap: var(--spacing-xs); }
.chip-input {
  flex: 1 1 auto;
  min-width: 0;
  height: 72rpx;
  padding: 0 var(--spacing-md);
  background: var(--bg-input);
  border-radius: var(--radius-pill);
  box-sizing: border-box;
  font-size: var(--font-aux);
  color: var(--text-primary);
}
.chip-add {
  flex: 0 0 auto;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 var(--spacing-lg);
  background: var(--bg-card);
  border: 2rpx solid var(--border-color);
  border-radius: var(--radius-pill);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.chip-add-text { font-size: var(--font-aux); color: var(--text-secondary); font-weight: var(--weight-medium); }
</style>
