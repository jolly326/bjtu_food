<script setup lang="ts">
/**
 * 图片占位（口径见 docs/ui/client/公共组件与形态基线.md §1.9）。
 * 场景：图片缺失 / 加载失败 / 破图；宿主自带底色时用 bare。
 */
import AppIcon from './AppIcon.vue'

withDefaults(
  defineProps<{
    size?: number
    bare?: boolean
  }>(),
  { size: 64, bare: false },
)
</script>

<template>
  <view class="ph" :class="{ 'ph--bare': bare }">
    <AppIcon name="image-broken" :size="size" tone="muted" />
  </view>
</template>

<style lang="scss" scoped>
.ph {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background: var(--bg-placeholder);
}

.ph--bare {
  background: transparent;
}
</style>
