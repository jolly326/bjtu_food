<script setup lang="ts">
/**
 * 功能网格按钮 · 日月切换（页面私有）。
 *
 * 作为美食地图的一个**网格单元**随画布滑动；仅滑入可视区时可点（命中由画布全局手势判定，
 * 不在此自挂 tap）。视觉形态区别于菜品磁贴：直接以 plump 图标落在背景上（无圆角矩形框），
 * 既是装饰点缀、也是交互功能网格。点击切换日 / 夜主题（页面根视图 `data-theme` 随 `useTheme` 响应）。
 */
import AppIcon from '@/components/AppIcon.vue'
import { useTheme } from '@/theme'

const theme = useTheme()
</script>

<template>
  <view class="totem">
    <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" :size="200" />
  </view>
</template>

<style lang="scss" scoped>
.totem {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* Q 弹反馈由外层 .map__tile 的 hover 承担；此处仅承载图标 */
}
</style>
