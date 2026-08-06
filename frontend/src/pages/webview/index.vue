<template>
  <!-- web-view 原生组件：在小程序内加载公众号文章 / 外部 H5 -->
  <web-view v-if="url" :src="url" />
  <view v-else class="wv-empty">
    <text class="wv-tip">链接无效</text>
  </view>
</template>

<script setup lang="ts">
/**
 * WebView 通用页（Banner/广播 URL 跳转，用于打开微信公众号文章）
 *
 * 用法：
 *   uni.navigateTo({ url: `/pages/webview/index?url=${encodeURIComponent('https://mp.weixin.qq.com/s/xxx')}` })
 *
 * 注意（上线必读）：
 *   1. 生产环境需在微信公众平台「开发管理-开发设置-业务域名」添加并配置
 *      mp.weixin.qq.com 等要打开的域名（需部署校验文件，域名需 ICP 备案）。
 *   2. 开发阶段在开发者工具勾选「不校验合法域名」即可本地预览公众号文章。
 *   3. web-view 为原生组件，会覆盖整页；页面内无法叠加自定义 UI。
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const url = ref('')

onLoad((options) => {
  if (options?.url) url.value = decodeURIComponent(options.url)
})
</script>

<style scoped>
.wv-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--bg-page);
}
.wv-tip {
  font-size: var(--font-body);
  color: var(--text-tertiary);
}
</style>
