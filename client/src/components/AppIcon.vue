<script setup lang="ts">
/**
 * 图标唯一实现（口径见 docs/ui/client/公共组件与形态基线.md §1.8、资源清单 docs/ui/client/图标资源.md）。
 * 资源 = **Streamline Plump（Solid）中性棕实心 SVG**（CC BY 4.0），几何**不自行绘制**。
 * 填充色以显式 `fill`（`#8C7B6B`）落盘，微信 `<image>` 直接渲染，不做运行时换色。
 */
import { computed } from 'vue'

import { iconSrc } from '@/theme/icons'

/** 宿主节点虚拟化：组件根（image）直接参与宿主 flex 布局 */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    name: string
    size?: number
  }>(),
  { size: 28 },
)

const src = computed(() => iconSrc(props.name))
</script>

<template>
  <image
    class="icon"
    :src="src"
    :style="{ width: size + 'rpx', height: size + 'rpx' }"
    mode="aspectFit"
    aria-hidden="true"
  />
</template>

<style lang="scss" scoped>
.icon {
  display: block;
  flex-shrink: 0;
}
</style>
