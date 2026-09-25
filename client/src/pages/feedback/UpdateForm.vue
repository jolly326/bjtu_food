<template>
  <!-- 我要更新信息（update 模式字段区，纯表单、无文字说明输入框）：
       菜品选择 → 详情预填（名称/价格/食堂名/档口/口味标签/食材/图片），用户只改差异项。
       食堂名 / 档口均为 line-input 自由文本（无字典 / 无 picker）。 -->
  <view>
    <!-- 步骤一：菜品选择（未选 → 选择行；已选 → 菜品行 + 重选） -->
    <view class="field">
      <text class="field-label">要更新哪道菜<text class="req">*</text></text>
      <view
        v-if="!model.dish"
        id="f-up-dish"
        class="picker-row dish-row"
        :class="{ 'input-error': errors['update.dish'] }"
        role="button"
        aria-label="搜索并选择要更新的菜品"
        hover-class="row-pressed"
        hover-stay-time="80"
        @tap="emit('open-dish')"
      >
        <text class="picker-value placeholder">搜菜名选一道</text>
        <IconSvg name="arrow" :size="24" :color="COLOR_MAP['text-tertiary']" />
      </view>
      <view v-else class="dish-card">
        <view class="dish-card-main">
          <text class="dish-card-name">{{ model.dish.name }}</text>
          <text class="dish-card-sub">{{ dishLocation || '——' }}</text>
          <text v-if="detailLoading" class="dish-card-loading">正在载入菜品信息…</text>
        </view>
        <view
          class="dish-reset"
          role="button"
          aria-label="重选菜品"
          hover-class="row-pressed"
          hover-stay-time="80"
          @tap="emit('reset-dish')"
        ><text class="dish-reset-text">重选</text></view>
      </view>
      <text v-if="errors['update.dish']" class="field-error">{{ errors['update.dish'] }}</text>
    </view>

    <!-- 步骤二：预填表单（选定后展示；预填值来自详情，用户只改差异项） -->
    <template v-if="model.dish && !detailLoading">
      <!-- 名称（预填） -->
      <view class="field">
        <text class="field-label">名称<text class="req">*</text></text>
        <input
          id="f-up-name"
          :value="model.name"
          class="line-input"
          :class="{ 'input-error': errors['update.name'] }"
          placeholder="菜品名称"
          maxlength="64"
          :cursor-spacing="40"
          :adjust-position="true"
          @input="onNameInput"
        />
        <text v-if="errors['update.name']" class="field-error">{{ errors['update.name'] }}</text>
      </view>

      <!-- 价格（预填 = price/100 元展示，提交由 API 层口径转分） -->
      <view class="field">
        <text class="field-label">价格（元）<text class="req">*</text></text>
        <input
          id="f-up-price"
          :value="model.price"
          class="line-input line-input--price"
          :class="{ 'input-error': errors['update.price'] }"
          placeholder="如 12.5"
          type="digit"
          maxlength="9"
          :cursor-spacing="40"
          :adjust-position="true"
          @input="onPriceInput"
        />
        <text v-if="errors['update.price']" class="field-error">{{ errors['update.price'] }}</text>
      </view>

      <!-- 食堂名（line-input 自由文本，预填详情 canteenName） -->
      <view class="field">
        <text class="field-label">食堂名<text class="req">*</text></text>
        <input
          id="f-up-canteenName"
          :value="model.canteenName"
          class="line-input"
          :class="{ 'input-error': errors['update.canteenName'] }"
          placeholder="如：一食堂"
          maxlength="64"
          :cursor-spacing="40"
          :adjust-position="true"
          @input="onCanteenInput"
        />
        <text v-if="errors['update.canteenName']" class="field-error">{{ errors['update.canteenName'] }}</text>
      </view>

      <!-- 档口（line-input 自由文本，预填详情 stallName） -->
      <view class="field">
        <text class="field-label">档口<text class="req">*</text></text>
        <input
          id="f-up-stallName"
          :value="model.stallName"
          class="line-input"
          :class="{ 'input-error': errors['update.stallName'] }"
          placeholder="如：麻辣香锅"
          maxlength="64"
          :cursor-spacing="40"
          :adjust-position="true"
          @input="onStallInput"
        />
        <text v-if="errors['update.stallName']" class="field-error">{{ errors['update.stallName'] }}</text>
      </view>

      <!-- 口味标签 chips（预填 flavorTags，可增删，自由输入添加） -->
      <view class="field">
        <text class="field-label">口味标签</text>
        <view v-if="model.flavorTags.length" class="chips">
          <view v-for="(t, i) in model.flavorTags" :key="`flavor-${i}-${t}`" class="chip">
            <text class="chip-text">{{ attrLabel('flavorTags', t) }}</text>
            <view
              class="chip-x"
              role="button"
              :aria-label="`删除口味标签 ${attrLabel('flavorTags', t)}`"
              hover-class="row-pressed"
              hover-stay-time="80"
              @tap="removeChip('flavorTags', i)"
            >
              <IconSvg name="close" :size="18" :color="COLOR_MAP['text-tertiary']" />
            </view>
          </view>
        </view>
        <view class="chip-add-row">
          <input
            v-model="flavorDraft"
            class="chip-input"
            placeholder="加个口味标签，如：微辣"
            maxlength="10"
            confirm-type="done"
            :cursor-spacing="40"
            :adjust-position="true"
            @confirm="addChip('flavorTags')"
          />
          <view
            class="chip-add"
            role="button"
            aria-label="添加口味标签"
            hover-class="row-pressed"
            hover-stay-time="80"
            @tap="addChip('flavorTags')"
          ><text class="chip-add-text">添加</text></view>
        </view>
      </view>

      <!-- 食材 chips（预填 ingredients，可增删，自由输入添加） -->
      <view class="field">
        <text class="field-label">食材</text>
        <view v-if="model.ingredients.length" class="chips">
          <view v-for="(t, i) in model.ingredients" :key="`ing-${i}-${t}`" class="chip">
            <text class="chip-text">{{ attrLabel('ingredients', t) }}</text>
            <view
              class="chip-x"
              role="button"
              :aria-label="`删除食材 ${attrLabel('ingredients', t)}`"
              hover-class="row-pressed"
              hover-stay-time="80"
              @tap="removeChip('ingredients', i)"
            >
              <IconSvg name="close" :size="18" :color="COLOR_MAP['text-tertiary']" />
            </view>
          </view>
        </view>
        <view class="chip-add-row">
          <input
            v-model="ingredientDraft"
            class="chip-input"
            placeholder="加个食材，如：土豆"
            maxlength="10"
            confirm-type="done"
            :cursor-spacing="40"
            :adjust-position="true"
            @confirm="addChip('ingredients')"
          />
          <view
            class="chip-add"
            role="button"
            aria-label="添加食材"
            hover-class="row-pressed"
            hover-stay-time="80"
            @tap="addChip('ingredients')"
          ><text class="chip-add-text">添加</text></view>
        </view>
      </view>

      <!-- 图片（预填菜品现有图，可增删；ImagePicker 安检上传，提交中禁选） -->
      <view class="field">
        <text class="field-label">图片</text>
        <ImagePicker :model-value="model.images" :max="9" :disabled="submitting" @update:model-value="onImagesChange" />
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
/**
 * UpdateForm（feedback 包内私有）：「我要更新信息」预填表单字段区。
 * chips 展示机器值 → 中文由四维字典（stores/dish-attribute）翻译，未命中回落原始值（端上零硬编码映射表）。
 */
import { ref, computed } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import ImagePicker from '@/components/ImagePicker.vue'
import { COLOR_MAP } from '@/theme/tokens'
import { useDishAttributeStore } from '@/stores/dish-attribute'
import type { DishListItem } from '@/types/dish'

const props = defineProps<{
  /** 选定菜品（列表行；选择器行展示 + 提交路径 dishId 由父页持有） */
  model: {
    dish: DishListItem | null
    name: string
    price: string
    canteenName: string
    stallName: string
    flavorTags: string[]
    ingredients: string[]
    images: string[]
  }
  /** 详情拉取中（预填未就绪，表单暂不展示） */
  detailLoading: boolean
  errors: Record<string, string>
  /** 提交中：禁选配图 */
  submitting?: boolean
}>()

const emit = defineEmits<{
  (e: 'clear', key: string): void
  (e: 'open-dish'): void
  (e: 'reset-dish'): void
}>()

const dishAttr = useDishAttributeStore()

/** chips 展示文案：机器值 → 字典中文；未命中（用户自由输入项）回落原始值 */
function attrLabel(field: 'flavorTags' | 'ingredients', value: string): string {
  return dishAttr.labelOf(field, value) || value
}

const dishLocation = computed(() =>
  [props.model.dish?.canteen, props.model.dish?.stallName].filter(Boolean).join(' · '),
)

/**
 * input @input 回调（平台例外：uni input 事件对象由运行时透传，形参取 `Event` 并结构化收窄，避免 `any` 逃逸）。
 */
function onNameInput(e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  props.model.name = detail?.value ?? ''
  emit('clear', 'update.name')
}

function onPriceInput(e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  props.model.price = detail?.value ?? ''
  emit('clear', 'update.price')
}

function onCanteenInput(e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  props.model.canteenName = detail?.value ?? ''
  emit('clear', 'update.canteenName')
}

function onStallInput(e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  props.model.stallName = detail?.value ?? ''
  emit('clear', 'update.stallName')
}

function onImagesChange(urls: string[]) {
  props.model.images = urls
}

/* ===== chips 编辑（增删；自由输入添加，去重 + 最多 12 项） ===== */
const MAX_CHIPS = 12
const flavorDraft = ref('')
const ingredientDraft = ref('')

function addChip(field: 'flavorTags' | 'ingredients') {
  const draft = field === 'flavorTags' ? flavorDraft : ingredientDraft
  const value = draft.value.trim()
  if (!value) return
  const list = props.model[field]
  if (list.length >= MAX_CHIPS) {
    uni.showToast({ title: `最多 ${MAX_CHIPS} 项`, icon: 'none' })
    return
  }
  if (!list.some((t) => t === value)) list.push(value)
  draft.value = ''
}

function removeChip(field: 'flavorTags' | 'ingredients', index: number) {
  props.model[field].splice(index, 1)
}
</script>

<style scoped lang="scss">
/* 字段级样式（.field / .field-label / .req / .field-error / .picker-row / .picker-value / .input-error）统一来自共享 partial */
@use './form-shared';

/* 按压反馈：统一 hover-class + opacity（与全站按压语言一致） */
.row-pressed { opacity: 0.7; }

/* ===== 菜品选择行 / 已选菜品卡 ===== */
.dish-row { border: 2rpx solid transparent; }
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
.dish-reset {
  position: relative;
  flex: 0 0 auto;
  padding: var(--spacing-2xs) var(--spacing-sm);
  -webkit-tap-highlight-color: transparent;
}
.dish-reset-text { font-size: var(--font-aux); color: var(--color-primary-text); font-weight: var(--weight-medium); }

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

/* ===== chips（预填 + 增删；贴纸观感 = 主色浅底 + 深主色文字，与全站 chip 语言同档） ===== */
.chips { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); margin-bottom: var(--spacing-xs); }
.chip {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
  padding: var(--spacing-2xs) var(--spacing-sm);
  background: var(--color-primary-soft);
  border-radius: var(--radius-pill);
}
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
