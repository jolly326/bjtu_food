<template>
  <view class="section-title" :class="{ 'no-margin': noMargin }">
    <text class="section-text">{{ title }}</text>
    <!-- 右侧附加信息：经具名 slot 承载（纯文本计数如「N 条」、或可点件如 find 页「清空」）。
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
  /** 是否去掉左右外边距（用于已自带 padding 的容器内部） */
  noMargin?: boolean
}>(), {
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
.section-text {
  /* Apple Design Typography：分区标题加大（h2 级）并加重（800），强化信息层级（无竖线纯文本） */
  font-size: var(--font-h2);
  font-weight: var(--weight-heavy);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h2);
  flex: 1;
  min-width: 0;
}
/* 右侧附加内容（`#extra` 槽）的排版由消费方自持：本组件不再内置 `.section-extra`
   （随 `extraText` prop 一并移除 —— UI 统一 Loop Round 17） */
</style>
