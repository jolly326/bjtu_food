<template>
  <!-- 提个想法（提建议 / 报问题，均文本 + 图片）；细分 chips 组件内持有 -->
  <view>
    <view class="sub-row" role="radiogroup" aria-label="细分类型">
      <view
        v-for="s in subs"
        :key="s.value"
        class="sub-chip"
        :class="{ active: model.sub === s.value }"
        hover-class="pressed"
        hover-stay-time="80"
        role="radio"
        :aria-checked="model.sub === s.value"
        :aria-label="s.label"
        @tap="model.sub = s.value"
      >
        <text class="sub-text">{{ s.label }}</text>
      </view>
    </view>

    <view class="field">
      <text class="field-label">想说啥<text class="req">*</text></text>
      <textarea
        id="f-sug-text"
        v-model="model.text"
        class="content-input"
        :class="{ 'input-error': errors['suggestion.text'] }"
        :placeholder="model.sub === 'idea' ? '你的想法，比如：希望加几个素食窗口' : '发生啥了？描述一下'"
        maxlength="1000"
        :auto-height="true"
        :cursor-spacing="40"
        :adjust-position="true"
        @input="emit('clear', 'suggestion.text')"
      />
      <text v-if="model.text.length > 800" class="counter">{{ model.text.length }}/1000</text>
      <text v-if="errors['suggestion.text']" class="field-error">{{ errors['suggestion.text'] }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/** SuggestionForm（feedback 包内私有）：「提个想法」字段区（细分 chips + 正文） */

const props = defineProps<{
  model: { sub: 'idea' | 'problem'; text: string }
  errors: Record<string, string>
}>()
const emit = defineEmits<{ (e: 'clear', key: string): void }>()

const subs = [
  { value: 'idea' as const, label: '提建议' },
  { value: 'problem' as const, label: '报问题' },
]
</script>

<style scoped>
.sub-row { display: flex; gap: var(--spacing-sm); margin-bottom: var(--spacing-md); }
.sub-chip {
  flex: 1;
  min-height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-sm) var(--spacing-lg);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  border: 2rpx solid var(--border-color);
  -webkit-tap-highlight-color: transparent;
}
.sub-chip.active { background: var(--color-primary-soft); border-color: var(--color-primary); }
.sub-text { font-size: var(--font-body); color: var(--text-secondary); font-weight: var(--weight-medium); }
.sub-chip.active .sub-text { color: var(--color-primary); font-weight: var(--weight-semibold); }

.field { margin-bottom: var(--spacing-md); }
.field:last-child { margin-bottom: 0; }
.field-label { display: block; font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--text-secondary); margin-bottom: var(--spacing-xs); }
.req { color: var(--color-error); margin-left: var(--spacing-2xs); font-size: var(--font-small); font-weight: var(--weight-heavy); }
.content-input {
  width: 100%;
  min-height: 300rpx;
  font-size: var(--font-body);
  color: var(--text-primary);
  line-height: 1.6;
  padding: var(--spacing-md);
  background: var(--bg-input);
  border-radius: var(--radius-icon);
  box-sizing: border-box;
  border: 2rpx solid transparent;
}
.input-error { border-color: var(--color-error); }
.field-error { display: block; margin-top: var(--spacing-xs); font-size: var(--font-tiny); color: var(--color-error); }
.counter { display: block; text-align: right; font-size: var(--font-aux); color: var(--text-tertiary); margin-top: var(--spacing-xs); font-variant-numeric: tabular-nums; }
</style>
