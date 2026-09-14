<template>
  <view class="section-title" :class="{ 'no-margin': noMargin }" @tap="$emit('tap')">
    <text class="section-text">{{ title }}</text>
    <!-- 右侧附加信息：优先文案 prop（extraText，免具名 slot 跨组件分发），其次具名 slot（复杂内容用） -->
    <text v-if="extraText" class="section-extra">{{ extraText }}</text>
    <slot name="extra" />
  </view>
</template>

<script setup lang="ts">
/**
 * 分区标题（全局统一组件，task-13 §0.3/§0.4）
 * 全站分区/模块标题为无竖线纯文本标题，层级由字号/字重承担，
 * 不再渲染左侧品牌色竖条（旧 bar 装饰已移除）。
 *
 * 右侧附加信息两种承载方式：
 * - `extraText`（推荐，纯文本计数/单位）：**不经具名 slot**，避免「组件 → 共享组件」跨层具名 slot
 *   在 mp-weixin 下的分发风险（uni-app 对同名 slot 有塌缩历史，见 §4.9 瀑布流红线）；
 * - `#extra` 具名 slot（保留给需要可点/富内容右位的页面，如 find 页「清空」）。
 */
withDefaults(defineProps<{
  /** 标题文案 */
  title: string
  /** 是否去掉左右外边距（用于已自带 padding 的容器内部） */
  noMargin?: boolean
  /** 右侧附加纯文本（如评价数）；与具名 slot 二选一，同时给时两者都渲染 */
  extraText?: string
}>(), {
  noMargin: false,
  extraText: '',
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
/* 右侧附加纯文本：小字 + 三级灰（次级信息，不与标题争层级） */
.section-extra {
  flex-shrink: 0;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}
</style>
