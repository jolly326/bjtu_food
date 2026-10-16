<script setup lang="ts">
/**
 * 图标唯一实现（口径见 docs/ui/client/公共组件与形态基线.md §1.8、资源清单 docs/ui/client/图标资源.md）。
 * 资源 = **下载的 Lucide 官方 SVG**（lucide-static · ISC），几何**不自行绘制**。
 * 微信 `<image>` 支持 SVG 文件但不支持运行时换色 ⇒ 由 `scripts/gen-icons.mjs` 按色调预生成着色变体。
 */
import { computed } from 'vue'

import { iconSrc, type IconTone } from '@/theme/icons.generated'

const props = withDefaults(
  defineProps<{
    name: string
    size?: number
    /** 色调（= 预生成的着色变体），取值见 icons.generated.ts */
    tone?: IconTone
  }>(),
  { size: 28, tone: 'default' },
)

const src = computed(() => iconSrc(props.name, props.tone))
</script>

<template>
  <image class="icon" :src="src" :style="{ width: size + 'rpx', height: size + 'rpx' }" mode="aspectFit" />
</template>

<style lang="scss" scoped>
.icon {
  display: block;
  flex-shrink: 0;
}
</style>
