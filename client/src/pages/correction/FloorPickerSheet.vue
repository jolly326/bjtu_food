<template>
  <!--
    FloorPickerSheet（correction 包内私有，R40 新增）—— 楼层**固定字典**底部单选弹层。

    底座复用公共 `BaseSheet`（底部弹层骨架唯一真源：遮罩 / grabber / 下滑关闭 / 安全区 / 焦点回收）；
    本项目 `client/src` 零原生 `<picker>` / `<picker-view>` / `showActionSheet` 先例，禁绕开 BaseSheet。

    **职责边界（UI 稿「组件拆分」R40）**：本组件只做「渲染字典行 + 高亮当前值 + 回抛**存储值**」，
    **不做映射决策**（字典来源与「未命中兜底」判定归表单层 `useCorrection` / `CorrectionForm`）——
    因此 `emit('select')` 回抛的永远是**后端存储值**（`B1` / `1F` …），**汉字不出现在提交路径上**。
  -->
  <BaseSheet :visible="visible" title="选择楼层" @close="emit('close')">
    <view class="fp-list">
      <view
        v-for="opt in options"
        :key="opt.value"
        class="fp-row"
        :class="{ 'fp-row--on': opt.value === value }"
        role="button"
        :aria-label="opt.label"
        :aria-checked="opt.value === value ? 'true' : 'false'"
        hover-class="fp-row--pressed"
        hover-stay-time="80"
        @tap="emit('select', opt.value)"
      >
        <text class="fp-label">{{ opt.label }}</text>
        <IconSvg v-if="opt.value === value" name="check" :size="28" :color="COLOR_MAP['primary-text']" />
      </view>
    </view>
  </BaseSheet>
</template>

<script setup lang="ts">
/**
 * FloorPickerSheet —— 楼层固定字典单选弹层（R40）
 *
 * 只管展示与回抛：汉字仅存在于本组件的**渲染层**；`select` 事件回抛 `options[].value`
 * （后端存储值），由表单层写入 `form.floor`。
 */
import BaseSheet from '@/components/BaseSheet.vue'
import IconSvg from '@/components/IconSvg.vue'
import { COLOR_MAP } from '@/theme/tokens'

/** 字典项：`value` = 后端存储值（提交用）；`label` = 页面展示汉字（仅渲染用） */
interface FloorOption {
  value: string
  label: string
}

withDefaults(defineProps<{
  /** 显隐（父级 `v-if` 懒挂载后由 `visible` 控制开合） */
  visible: boolean
  /** 当前「后端存储值」；未命中字典时为原值（此时无高亮行） */
  value: string
  /**
   * 固定字典（5 项；顺序即展示顺序）。
   * 取 `readonly` 与 `FLOOR_OPTIONS` 的常量声明一致 —— 本组件**只读不写**，
   * 用可变数组类型会诱使消费方传入可变副本，掩盖「字典为端上常量、不应被改」这一事实。
   */
  options: readonly FloorOption[]
}>(), {
  visible: false,
  value: '',
  options: () => [],
})

const emit = defineEmits<{
  /** 关闭（遮罩 / grabber 下滑 / 关闭按钮，由 BaseSheet 统一上抛） */
  (e: 'close'): void
  /** 选中：回抛**存储值**（不回落汉字） */
  (e: 'select', value: string): void
}>()
</script>

<style scoped>
/* 行列表：上下留白 `--spacing-xs`，行间 2rpx 细线分隔（弹层内允许分隔线 —— 卡片内禁横线的约束只管主卡） */
.fp-list { padding: var(--spacing-xs) 0; }
.fp-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  /* 独立可点件：视觉高 = 行高，命中区 ≥ 88rpx（与主卡 picker 单元格同口径） */
  min-height: 88rpx;
  padding: var(--spacing-sm) var(--spacing-lg);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.fp-row--pressed { background: var(--bg-soft); }
/* 选中行：文字切主色文字档（汉字展示层的选中标识；存储值不参与视觉） */
.fp-row--on .fp-label { color: var(--color-primary-text); font-weight: var(--weight-semibold); }
.fp-label { flex: 1; min-width: 0; font-size: var(--font-body); color: var(--text-primary); }
</style>