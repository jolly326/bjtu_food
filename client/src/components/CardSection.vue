<template>
  <!-- 纯容器（卡片外壳）：不渲染分区标题，标题由调用方自行渲染 SectionTitle（4 调用点均如此） -->
  <view class="card-section" :class="{ 'is-flush': flush }">
    <slot />
  </view>
</template>

<script setup lang="ts">
/**
 * CardSection —— 卡片外壳（**全站唯一实现**）
 *
 * 视觉：`bg-card` + `radius-card` + `shadow-card` + 内距 `--spacing-md`、
 * 外距 `--spacing-md --page-gutter`（纵向 12pt 卡间距、横向 16pt 页 gutter；
 * 首个卡片上边距收紧为 `--spacing-md`）。
 * ⚠️ **新卡片一律用它**，不再手写三件套。
 */
withDefaults(defineProps<{
  /** `true` = 去掉自身外边距（块间距由父级容器统管，如详情页评价区 `.review-section`） */
  flush?: boolean
}>(), {
  flush: false,
})
</script>

<style scoped>
.card-section {
  background: var(--bg-card);
  margin: var(--spacing-md) var(--page-gutter);
  padding: var(--spacing-md);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.card-section:first-of-type {
  margin-top: var(--spacing-md);
}
/* flush 变体：外边距归零（写在 `:first-of-type` 之后，同权重时后者生效） */
.card-section.is-flush { margin: 0; }
</style>
