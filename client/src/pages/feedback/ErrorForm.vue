<template>
  <!-- 信息不对（关联菜品 + 多选哪里不对 + 正确信息 + 作证）；弹层/互斥由父页统一处理 -->
  <view>
    <view class="field" id="f-dish">
      <text class="field-label">关联菜品<text class="req">*</text></text>

      <!-- 已选中：菜品摘要卡 -->
      <view v-if="model.dish" class="dish-linked">
        <image class="dish-thumb" :src="model.dish.image || ''" mode="aspectFill" />
        <view class="dish-info">
          <text class="dish-name">{{ model.dish.name }}</text>
          <text class="dish-meta">{{ dishMeta }}</text>
        </view>
        <view
          class="dish-change"
          hover-class="pressed"
          hover-stay-time="80"
          role="button"
          aria-label="换一个"
          @tap="emit('reset-dish')"
        ><text class="dish-change-text">换一个</text></view>
      </view>

      <!-- 未选中：点击打开底部搜索弹窗 -->
      <view
        v-else
        class="picker-row dish-picker-row"
        hover-class="pressed"
        hover-stay-time="80"
        role="button"
        aria-label="搜索选择菜品"
        @tap="emit('open-dish')"
      >
        <IconSvg name="search-fill" :size="30" color="var(--color-primary)" />
        <text class="picker-value placeholder">搜索选择菜品</text>
        <IconSvg name="arrow" :size="26" color="var(--text-tertiary)" />
      </view>

      <text v-if="errors['error.dish']" class="field-error">{{ errors['error.dish'] }}</text>
    </view>

    <!-- 哪里不对（每项一行：左侧选项 + 右侧编辑区，不嵌套） -->
    <view class="field" id="f-point">
      <text class="field-label">哪里不对？<text class="req">*</text></text>
      <view class="point-list">
        <view
          v-for="c in points"
          :key="c.key"
          class="point-row"
          :class="{ focused: focusKey === c.key }"
        >
          <!-- 左侧：选项（点击选中/取消，互斥/预填逻辑在父页 togglePoint） -->
          <view
            class="point-option"
            :class="{ active: model.points.includes(c.key) }"
            hover-class="pressed"
            hover-stay-time="80"
            role="checkbox"
            :aria-checked="model.points.includes(c.key)"
            :aria-label="c.label"
            @tap="emit('toggle', c.key)"
          >
            <view class="point-option-icon">
              <IconSvg :name="c.icon" :size="28" :color="model.points.includes(c.key) ? 'var(--color-primary)' : 'var(--text-tertiary)'" />
            </view>
            <text class="point-option-text">{{ c.label }}</text>
            <IconSvg v-if="model.points.includes(c.key)" name="check" :size="24" color="var(--color-primary)" />
          </view>

          <!-- 右侧：编辑区（选中后出现；正确信息直接写入响应式 form.error.correctValues） -->
          <view v-if="model.points.includes(c.key)" class="point-edit" @tap.stop>
            <input
              v-model="model.correctValues[c.key]"
              class="edit-input"
              :class="{ 'input-error': errors[`error.correct.${c.key}`] }"
              :placeholder="`${c.editPlaceholder}`"
              maxlength="200"
              :cursor-spacing="40"
              :adjust-position="true"
              @input="emit('clear', `error.correct.${c.key}`)"
              @focus="focusKey = c.key"
              @blur="focusKey = ''"
            />
            <text v-if="errors[`error.correct.${c.key}`]" class="field-error">{{ errors[`error.correct.${c.key}`] }}</text>
          </view>
        </view>
      </view>
      <text v-if="errors['error.points']" class="field-error">{{ errors['error.points'] }}</text>
    </view>

    <!-- 作证（选填）：文本 + 配图，仅选中问题后显示 -->
    <view v-if="model.points.length" class="evidence-box">
      <text class="evidence-title">作证</text>
      <view class="field">
        <text class="field-label">文本</text>
        <textarea
          v-model="model.evidenceText"
          class="content-input content-input-sm"
          placeholder="补充说明，比如实际情况是啥"
          maxlength="500"
          :auto-height="true"
          :cursor-spacing="40"
          :adjust-position="true"
        />
      </view>
      <!-- 配图（选填 ≤3 张）：统一 ImagePicker（安检上传），随 model.images 交给父页提交；
           标签统一「配图」（评审 m2）；提交中禁选（评审 m1） -->
      <view class="field">
        <text class="field-label">配图</text>
        <ImagePicker v-model="model.images" :max="3" :disabled="submitting" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Dish } from '@/types/dish'
import IconSvg from '@/components/IconSvg.vue'
import ImagePicker from '@/components/ImagePicker.vue'

/** ErrorForm（feedback 包内私有）：「信息不对」字段区（关联菜品 + 纠错选项 + 作证文本/配图） */
const props = defineProps<{
  model: {
    dish: Dish | null
    points: string[]
    correctValues: Record<string, string>
    evidenceText: string
    images: string[]
  }
  /** 纠错选项定义（key/label/icon/editPlaceholder），与父页提交/校验共用同一数组源 */
  points: { key: string; label: string; icon: string; editPlaceholder: string }[]
  errors: Record<string, string>
  /** 提交中：禁选配图（评审 m1，与 ReviewComposer 一致） */
  submitting?: boolean
}>()
const emit = defineEmits<{
  (e: 'open-dish'): void
  (e: 'reset-dish'): void
  (e: 'toggle', key: string): void
  (e: 'clear', key: string): void
}>()

/** 当前聚焦的纠错行 key（focus 时左侧选项加主色左边条，组件内状态；切类型随卸载复位） */
const focusKey = ref('')

const dishMeta = computed(() => {
  const d = props.model.dish
  if (!d) return ''
  const parts = [d.canteen, d.stallName].filter(Boolean)
  if (d.price > 0) parts.push(`¥${d.price}`)
  return parts.join(' · ') || '菜品'
})
</script>

<style scoped lang="scss">
/* 字段级样式（.field / .field-label / .req / .field-error / .content-input / .input-error /
   .picker-row / .picker-value）统一来自共享 partial */
@use './form-shared';

/* 已选菜品摘要卡 */
.dish-linked {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm);
  background: var(--bg-soft);
  border-radius: var(--radius-card);
}
.dish-thumb {
  width: 96rpx;
  height: 96rpx;
  border-radius: var(--radius-icon);
  background: var(--bg-placeholder);
  flex-shrink: 0;
}
.dish-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.dish-name { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); }
.dish-meta { font-size: var(--font-tiny); color: var(--text-tertiary); }
.dish-change {
  flex-shrink: 0;
  padding: var(--spacing-xs) var(--spacing-sm);
  background: var(--bg-card);
  border-radius: var(--radius-pill);
  -webkit-tap-highlight-color: transparent;
}
.dish-change-text { font-size: var(--font-aux); color: var(--text-secondary); }

/* 哪里不对行：左侧选项胶囊 + 右侧编辑区 */
.point-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.point-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding-left: var(--spacing-xs);
  border-left: 6rpx solid transparent;
  border-radius: var(--radius-tag);
  box-sizing: border-box;
}
.point-row.focused {
  border-left-color: var(--color-primary);
  background: var(--bg-card);
}
.point-option {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  min-height: 76rpx;
  padding: 0 var(--spacing-md);
  background: var(--bg-card);
  border: 2rpx solid var(--border-color);
  border-radius: var(--radius-pill);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.point-option.active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
}
.point-option-icon {
  width: 48rpx;
  height: 48rpx;
  flex-shrink: 0;
  border-radius: var(--radius-circle);
  background: var(--bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.point-option-text { font-size: var(--font-small); color: var(--text-tertiary); font-weight: var(--weight-regular); white-space: nowrap; }
.point-option.active .point-option-text { font-size: var(--font-body); color: var(--color-primary); font-weight: var(--weight-semibold); }
.point-edit { flex: 1; min-width: 0; }
.edit-input {
  width: 100%;
  height: 68rpx;
  background: var(--bg-input);
  border-radius: var(--radius-icon);
  padding: 0 var(--spacing-md);
  font-size: var(--font-small);
  color: var(--text-primary);
  box-sizing: border-box;
  border: 2rpx solid var(--color-primary);
}
.edit-input.input-error { border-color: var(--color-error); }

/* 作证区 */
.evidence-box {
  margin-top: var(--spacing-lg);
  padding: var(--spacing-md);
  background: var(--bg-soft);
  border-radius: var(--radius-card);
}
.evidence-title {
  display: block;
  font-size: var(--font-tiny);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
  margin-bottom: var(--spacing-sm);
}
</style>
