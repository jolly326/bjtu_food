<script setup lang="ts">
/**
 * 图标唯一实现（口径见 docs/ui/client/菜品首页.md，键表与署名见 src/theme/icons.ts）。
 * 资源 = **Streamline Plump（Solid）中性灰实心 SVG**（CC BY 4.0），几何**不自行绘制**。
 * 填充色以显式 `fill`（`#6B7280`）落盘，微信 `<image>` 直接渲染；主色实底上用 `inverse` 反白。
 */
import { computed } from 'vue'

import { iconSrc } from '@/theme/icons'

/** 宿主节点虚拟化：组件根（image）直接参与宿主 flex 布局 */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    name: string
    size?: number
    /** 主色实底上反白显示 */
    inverse?: boolean
  }>(),
  { size: 28, inverse: false },
)

const src = computed(() => iconSrc(props.name))
const style = computed(() => ({
  width: props.size + 'rpx',
  height: props.size + 'rpx',
  filter: props.inverse ? 'brightness(0) invert(1)' : undefined,
}))
</script>

<template>
  <image
    class="icon"
    :src="src"
    :style="style"
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
