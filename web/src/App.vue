<script setup lang="ts">
// 根组件：路由出口 + 全局图片放大预览覆盖层（任意 <img v-zoomimg> 触发）
import ImageZoomOverlay from '@/components/ImageZoomOverlay.vue'
</script>

<template>
  <RouterView />
  <ImageZoomOverlay />
</template>
