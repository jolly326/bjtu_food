<template>
  <view class="section-title" :class="{ 'no-margin': noMargin }" @tap="$emit('tap')">
    <text class="section-text">{{ title }}</text>
    <slot name="extra" />
  </view>
</template>

<script setup lang="ts">
/**
 * 分区标题（全局统一组件，task-13 §0.3/§0.4）
 * moment-detail-publish-ux：全站分区/模块标题为无竖线纯文本标题，层级由字号/字重承担，
 * 不再渲染左侧品牌色竖条（旧 bar 装饰已移除）。
 */
withDefaults(defineProps<{
  /** 标题文案 */
  title: string
  /** 是否去掉左右外边距（用于已自带 padding 的容器内部） */
  noMargin?: boolean
}>(), {
  noMargin: false,
})

defineEmits<{
  /** 点击标题时触发（用于跳转到列表详情等） */
  (e: 'tap'): void
}>()
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
</style>
