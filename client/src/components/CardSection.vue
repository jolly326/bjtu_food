<template>
  <!-- 纯容器（卡片外壳）：不渲染分区标题，标题由调用方自行渲染 SectionTitle（4 调用点均如此） -->
  <view class="card-section">
    <slot />
  </view>
</template>

<style scoped>
.card-section {
  background: var(--bg-card);
  margin: var(--spacing-sm) var(--spacing-md);
  padding: var(--spacing-md);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.card-section:first-of-type {
  margin-top: var(--spacing-md);
}
</style>
