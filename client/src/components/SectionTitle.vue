<template>
  <view class="section-title" :class="{ 'no-margin': noMargin }">
    <!-- 左 = 标题块：标题 + 可选计数紧邻（UI 统一 Loop Round 24）。
         计数**与标题同色**（不是灰字附属）、字号小半号、等宽数字 ⇒ 读作「评价 12」一体；
         不再用「评价（12）」括号式（括号会让数字显得次要）。 -->
    <view class="section-head">
      <text class="section-text">{{ title }}</text>
      <text v-if="count !== null" class="section-count">{{ count }}</text>
    </view>
    <!-- 右侧附加信息：经具名 slot 承载（可点件如评价卡「写评价」轻量入口、find 页「清空」）。
         ⚠️ UI 统一 Loop Round 17：原 `extraText` 文案 prop 全仓零传入，按「零消费即删」移除；
         消费方均为本组件的**直接**使用方（不涉及跨层具名 slot 分发），故 slot 方案无塌缩风险。 -->
    <slot name="extra" />
  </view>
</template>

<script setup lang="ts">
/**
 * 分区标题（全局统一组件，task-13 §0.3/§0.4）
 * 全站分区/模块标题为无竖线纯文本标题，层级由字号/字重承担，
 * 不再渲染左侧品牌色竖条（旧 bar 装饰已移除）。
 *
 * 右侧附加信息经 `#extra` 具名 slot 承载（如评价数、find 页「清空」）。
 */
withDefaults(defineProps<{
  /** 标题文案 */
  title: string
  /**
   * 标题右侧计数（可选）：渲染为「标题 N」，**与标题同色**、字号小半号、等宽数字。
   * 传 `null`（默认）不渲染 —— 用于「在途 / 失败时不显示数字，避免 0 值误导」的场景。
   */
  count?: number | null
  /** 是否去掉左右外边距（用于已自带 padding 的容器内部） */
  noMargin?: boolean
}>(), {
  count: null,
  noMargin: false,
})
</script>

<style scoped>
.section-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 0;
  margin-bottom: var(--spacing-sm);
  box-sizing: border-box;
}
/* 左侧标题块：增长位放在**块**上（不是标题文字上），保证计数紧跟标题、不跑到右端 */
.section-head {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: var(--spacing-xs);
}
.section-text {
  /* Apple Design Typography：分区标题加大（h2 级）并加重（800），强化信息层级（无竖线纯文本） */
  font-size: var(--font-h2);
  font-weight: var(--weight-heavy);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h2);
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 计数（Round 24）：与标题**同色**（禁止灰字 —— 灰字会让总数读作附属信息）、小半号（h2 40 → h3 36）、
   等宽数字；与标题 `baseline` 对齐（40/36rpx 共基线，视觉一体） */
.section-count {
  flex: 0 0 auto;
  font-size: var(--font-h3);
  font-weight: var(--weight-heavy);
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}
/* 右侧附加内容（`#extra` 槽）的排版由消费方自持：本组件不再内置 `.section-extra`
   （随 `extraText` prop 一并移除 —— UI 统一 Loop Round 17） */
</style>
