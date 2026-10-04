<template>
  <view class="type-picker">
    <!-- 进页第一屏：未选类型时不渲染任何表单（「不选类型不得进入表单」） -->
    <text class="tip">请选择问题类型</text>
    <view
      v-for="opt in PROBLEM_TYPES"
      :key="opt.value"
      class="opt"
      :class="{ 'opt--on': opt.value === modelValue, 'opt--disabled': !!disabled }"
      role="button"
      :aria-label="`${opt.label}，${opt.hint}`"
      :aria-checked="opt.value === modelValue ? 'true' : 'false'"
      hover-class="opt--pressed"
      hover-stay-time="80"
      @tap="onPick(opt.value)"
    >
      <view class="texts">
        <text class="label">{{ opt.label }}</text>
        <text class="hint">{{ opt.hint }}</text>
      </view>
      <!-- 选中标识：右侧对勾（与楼层弹层同口径 —— 「已选」不靠底色单独表达，
           色觉障碍 / 低亮度下仍可辨） -->
      <IconSvg v-if="opt.value === modelValue" name="check" :size="28" :color="COLOR_MAP['primary-text']" />
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * TypePicker —— 菜品问题反馈的**类型选择**（进页第一屏）。
 *
 * <p>**为什么必须先选类型**：`field`（信息有误）与 `gone`（已经下架）的**表单与字段完全不同**
 * —— 前者要拉详情预填七个改动项，后者一键提交、无需任何字段。
 * 若合并成一个表单，用户会在错误的表单里填一堆用不上的东西
 * （UI 稿：**不选类型不得进入表单** —— 避免进错表单再回退）。
 *
 * <p>**只两项**：「其他问题」MVP 暂不做（评审问题 1 决议 5.2 · 决定 4）。
 */
import IconSvg from '@/components/IconSvg.vue'
import { COLOR_MAP } from '@/theme/tokens'
import { PROBLEM_TYPES } from './problemTypes'
import type { DishProblemType } from '@/types/feedback'

const props = defineProps<{
  /** 当前选中类型（v-model 双向；空串 = **尚未选择** —— 进页第一屏状态） */
  modelValue: DishProblemType | ''
  /** 提交中禁用切换（避免提交途中换表单，载荷与界面错位） */
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: DishProblemType): void
}>()

/** 提交中忽略点按（切类型只在空闲态生效，避免提交途中换表单） */
function onPick(value: DishProblemType) {
  if (props.disabled) return
  emit('update:modelValue', value)
}
</script>

<style scoped>
.type-picker { padding: 0 var(--spacing-md); }

.tip {
  display: block;
  padding: var(--spacing-md) 0 var(--spacing-sm);
  font-size: var(--font-body);
  color: var(--text-primary);
  font-weight: var(--weight-semibold);
}

/* 类型卡（单选行）：整卡可点，命中区 ≥ 88rpx（独立可点件基线） */
.opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  min-height: var(--tap-target-size);
  padding: var(--spacing-sm) var(--spacing-md);
  margin-bottom: var(--spacing-sm);
  background: var(--bg-card);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.opt--pressed { background: var(--bg-soft); }
/* 选中态（形态基线）：浅橙底 + 主色描边（描边加粗不位移 —— 已含 border 占位） */
.opt--on { border-width: 2rpx; border-color: var(--color-primary); background: var(--color-primary-soft); }
.opt--disabled { opacity: 0.6; }

.texts { display: flex; flex-direction: column; gap: var(--spacing-2xs); flex: 1; min-width: 0; }
.label { font-size: var(--font-body); color: var(--text-primary); }
.opt--on .label { color: var(--color-primary-text); font-weight: var(--weight-semibold); }
.hint { font-size: var(--font-caption); color: var(--text-secondary); }
</style>
