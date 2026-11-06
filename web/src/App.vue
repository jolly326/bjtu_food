<script setup lang="ts">
// 根组件：路由出口 + 全局弹窗挂载点 + 图片放大预览覆盖层
import ImageZoomOverlay from '@/components/ImageZoomOverlay.vue'
</script>

<template>
  <RouterView />
  <div id="overlay-root"></div>
  <ImageZoomOverlay />
</template>
