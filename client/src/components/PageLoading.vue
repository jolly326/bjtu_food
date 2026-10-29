<script setup lang="ts">
/**
 * 加载块（规格见 docs/ui/client/公共组件与形态基线.md §2.1.7）。
 *
 * 与「加载失败」共用 `StatusBlock` 骨架 ⇒ 图标尺寸 / 文案字阶 / 居中位置完全一致；
 * 加载态差异只有两点：图标弱化（55% 不透明度）+ 匀速旋转。
 */
import StatusBlock from './StatusBlock.vue'
import { useTheme } from '@/theme'

defineOptions({ options: { virtualHost: true } })

const theme = useTheme()

withDefaults(
  defineProps<{
    /** 加载提示文案，默认「加载中」 */
    text?: string
  }>(),
  { text: '加载中' },
)
</script>

<template>
  <StatusBlock name="loading" :text="text" :theme="theme" spinning dim />
</template>
