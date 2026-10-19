<script setup lang="ts">
/**
 * 图标唯一实现（口径见 docs/ui/client/公共组件与形态基线.md §1.8、资源清单 docs/ui/client/图标资源.md）。
 * 资源 = **下载的 Lucide 官方 SVG**（lucide-static · ISC），几何**不自行绘制**。
 * 微信 `<image>` 支持 SVG 文件但不支持运行时换色 ⇒ 由 `scripts/gen-icons.mjs` 按色调 × 主题预生成着色变体，
 * 本组件随当前主题取对应组（浅色组 / 深色组）。
 */
import { computed } from 'vue'

import { iconSrc, type IconTone } from '@/theme/icons.generated'
import { useTheme } from '@/utils/theme'

/** 宿主节点虚拟化：组件根（image）直接参与宿主 flex 布局 */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    name: string
    size?: number
    /** 色调（= 预生成的着色变体），取值见 icons.generated.ts */
    tone?: IconTone
  }>(),
  { size: 28, tone: 'default' },
)

const theme = useTheme()

const src = computed(() => iconSrc(props.name, props.tone, theme.value === 'dark'))
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
