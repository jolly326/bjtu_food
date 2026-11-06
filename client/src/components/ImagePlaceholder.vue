<script setup lang="ts">
/**
 * 图片占位（口径见 docs/ui/client/菜品首页.md）。
 * 场景：图片缺失 / 加载失败 / 破图。
 */
import AppIcon from './AppIcon.vue'

/** 宿主节点虚拟化：组件根直接参与宿主布局 */
defineOptions({ options: { virtualHost: true } })

withDefaults(defineProps<{ size?: number }>(), { size: 64 })
</script>

<template>
  <view class="ph">
    <AppIcon name="image" :size="size" />
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
</style>
