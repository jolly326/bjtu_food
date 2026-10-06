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
 *
 * **`overflow: hidden` 是卡壳契约的一部分**（两种外边距变体同款）：
 * ① 通栏贴顶 / 贴底的子元素被裁进 `--radius-card` 圆角（如 `IdentityCard` 的顶部主色软条纹）；
 * ② 兜住 `flush` 场景下用**负 margin 抵消内距**的子元素（如 `DishReviewSection` 的评价行：
 *    负 margin 恰为 `--spacing-md`，其边界止于 padding box 边 ⇒ 1rpx 分隔线仍整卡通宽、不被切掉）
 *    以及任何意外外溢。
 * 子元素若需真溢出卡壳（阴影 / 浮层锚点），不要在卡内实现 —— 放卡外或改用 `BaseSheet`。
 *
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
  /* 圆角裁齐 + 外溢兜底（契约见组件 JSDoc）。只裁子元素：卡自身 `--shadow-card`
     画在边框盒之外，不受本属性影响 */
  overflow: hidden;
}
.card-section:first-of-type {
  margin-top: var(--spacing-md);
}
/* flush 变体：外边距归零（写在 `:first-of-type` 之后，同权重时后者生效） */
.card-section.is-flush { margin: 0; }
</style>
