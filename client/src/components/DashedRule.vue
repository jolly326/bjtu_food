<script setup lang="ts">
/**
 * 短横线虚线分隔（口径见 docs/ui/client/公共组件与形态基线.md）。
 * 线 16rpx + 空 16rpx，上下各留 --spacing-sm；信息页 / 纠错页共用的中线，保证切换时位置一致。
 */
defineOptions({ options: { virtualHost: true } })
</script>

<template>
  <view class="dashed-rule" aria-hidden="true" />
</template>

<style lang="scss" scoped>
.dashed-rule {
  height: var(--spacing-2xs);
  margin: var(--spacing-sm) 0;
  background-image: repeating-linear-gradient(
    to right,
    var(--sheet-divider) 0 var(--spacing-sm),
    transparent var(--spacing-sm) var(--spacing-lg)
  );
}
</style>
