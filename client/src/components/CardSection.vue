<template>
  <!-- 纯容器（卡片外壳）：不渲染分区标题，标题由调用方自行渲染 SectionTitle（4 调用点均如此） -->
  <view class="card-section" :class="{ 'is-flush': flush, 'is-bare': bare }">
    <slot />
  </view>
</template>

<script setup lang="ts">
/**
 * CardSection —— 内容块外壳（**全站唯一实现**）
 *
 * 视觉：暖奶米半透 `--module-bg` + `radius-card` + 极淡暖棕 `--module-shadow` + 内距 `--spacing-md`、
 * 外距 `--spacing-md --page-gutter`（纵向卡间距、横向 16pt 页 gutter；首个卡片上边距收紧）。
 *
 * 与全局 `.module-wrap` **同底色同圆角同阴影**（暖奶米半透），二者的区别只在形态职责：
 * `.module-wrap` 是「页面内容块的默认形态」（由页面自行组合、内距 `--spacing-lg`），
 * 本组件是「**需要统一卡壳契约**」的复用外壳 —— 提供 `overflow: hidden` 圆角裁切、
 * 通栏贴边变体与统一外距，消费方无需各写一遍三件套。
 *
 * **`overflow: hidden` 是卡壳契约的一部分**（两种外边距变体同款）：
 * ① 通栏贴顶 / 贴底的子元素被裁进 `--radius-card` 圆角（如 `IdentityCard` 的顶部主色软条纹）；
 * ② 兜住 `flush` 场景下用**负 margin 抵消内距**的子元素（如 `DishReviewSection` 的评价行：
 *    负 margin 恰为 `--spacing-md`，其边界止于 padding box 边 ⇒ 分隔线仍整块通宽、不被切掉）
 *    以及任何意外外溢。
 * 子元素若需真溢出卡壳（阴影 / 浮层锚点），不要在卡内实现 —— 放卡外或改用 `BaseSheet`。
 *
 * ⚠️ **新内容块一律用它或 `.module-wrap`**，不再手写三件套；**SHALL NOT** 回退到纯白底色
 * （纯白块与手绘背景之间会显突兀，见基线 §1.8.2）。
 */
withDefaults(defineProps<{
  /** `true` = 去掉自身外边距（块间距由父级容器统管，如详情页评价区 `.review-section`） */
  flush?: boolean
  /** `true` = 去掉自身内距（`padding: 0`）：给「行自带内距」的通栏列表壳用（如通知列表卡、评价列表卡） */
  bare?: boolean
}>(), {
  flush: false,
  bare: false,
})
</script>

<style scoped>
.card-section {
  /* 暖奶米半透（与全局 `.module-wrap` 同底色）：纯白块与手绘背景之间会显突兀 */
  background: var(--module-bg);
  margin: var(--spacing-md) var(--page-gutter);
  padding: var(--spacing-md);
  border-radius: var(--radius-card);
  box-shadow: var(--module-shadow);
  /* 圆角裁齐 + 外溢兜底（契约见组件 JSDoc）。只裁子元素：卡自身 `--module-shadow`
     画在边框盒之外，不受本属性影响 */
  overflow: hidden;
}
.card-section:first-of-type {
  margin-top: var(--spacing-md);
}
/* flush 变体：外边距归零（写在 `:first-of-type` 之后，同权重时后者生效） */
.card-section.is-flush { margin: 0; }
/* bare 变体：内距归零（行自带内距的通栏列表壳；`overflow: hidden` 圆角裁切契约不变） */
.card-section.is-bare { padding: 0; }
</style>
