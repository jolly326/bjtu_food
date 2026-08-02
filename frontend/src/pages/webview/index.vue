<template>
  <view class="page webview-page">
    <Header :title="title" showBack />
    <view class="webview-wrap">
      <web-view v-if="src" :src="src" @error="onError" />
      <view v-else class="webview-fallback">
        <text class="fallback-text">链接无效</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'

const src = ref('')
const title = ref('外部链接')

function onError() {
  // web-view 加载失败（未配置业务域名等）：回落复制链接
  if (src.value) {
    uni.setClipboardData({
      data: src.value,
      success: () => uni.showToast({ title: '已在剪贴板，请到浏览器打开', icon: 'none' }),
    })
  }
}

onLoad((query) => {
  if (query?.src) {
    src.value = decodeURIComponent(query.src as string)
  }
  if (query?.title) {
    title.value = decodeURIComponent(query.title as string)
  }
})
</script>

<style scoped>
.webview-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.webview-wrap { flex: 1; overflow: hidden; }
.webview-fallback { flex: 1; display: flex; align-items: center; justify-content: center; }
.fallback-text { font-size: var(--font-body); color: var(--text-secondary); }
</style>
