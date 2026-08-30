<template>
  <view class="wv-page">
    <!-- 自定义返回条：web-view 为原生组件会覆盖整页，故用 webview-styles 把网页内容下移，
         返回条置于其上（始终可见），避免用户卡在外部网页无法返回 -->
    <view class="wv-bar" :style="{ height: (statusBarHeight + navBarHeight) + 'px', paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))' }">
      <view class="wv-back" @tap="back" role="button" aria-label="返回">
        <IconSvg name="arrow-left" :size="'22px'" color="var(--text-white)" />
      </view>
      <text class="wv-title">网页</text>
    </view>
    <web-view
      v-if="url"
      :src="url"
      :webview-styles="webviewStyles"
      @message="onMessage"
    />
    <view v-else class="wv-empty">
      <text class="wv-tip">链接无效</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * WebView 通用页（活动/广播 URL 跳转，用于打开微信公众号文章）
 *
 * 用法：
 *   uni.navigateTo({ url: `/pages/standalone/webview/index?url=${encodeURIComponent('https://mp.weixin.qq.com/s/xxx')}` })
 *
 * 注意（上线必读）：
 *   1. 生产环境需在微信公众平台「开发管理-开发设置-业务域名」添加并配置
 *      mp.weixin.qq.com 等要打开的域名（需部署校验文件，域名需 ICP 备案）。
 *   2. 开发阶段在开发者工具勾选「不校验合法域名」即可本地预览公众号文章。
 *   3. web-view 为原生组件，会覆盖整页；此处用 webview-styles.top 把网页内容下移到返回条之下，
 *      返回条浮于其上，保证刘海不被遮挡且返回键始终可见。
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import IconSvg from '@/components/IconSvg.vue'

const url = ref('')
const statusBarHeight = ref(20)
const navBarHeight = ref(56)

// 兼容老基础库：getWindowInfo 不存在时回退 getSystemInfoSync
function getStatusBarHeight(): number {
  // @ts-ignore
  if (typeof wx === 'undefined') return 20
  // @ts-ignore
  const win = (wx.getWindowInfo ? wx.getWindowInfo() : (wx.getSystemInfoSync ? wx.getSystemInfoSync() : null))
  return (win && win.statusBarHeight) || 20
}

onLoad((options) => {
  // 健壮性/安全：① decodeURIComponent 对畸形 % 编码（如 %E0%A4%A）会抛 URIError 导致白屏，需 try-catch；
  // ② 仅放行 http/https 协议，避免加载任意/危险协议（H5 端无微信业务域名兜底）。
  if (options?.url) {
    let decoded = ''
    try {
      decoded = decodeURIComponent(options.url)
    } catch {
      // 非法编码：置空走「链接无效」分支，而非白屏
    }
    if (/^https?:\/\//i.test(decoded.trim())) {
      url.value = decoded.trim()
    }
  }
  const sb = getStatusBarHeight()
  statusBarHeight.value = sb
  // @ts-ignore - 微信胶囊按钮位置，用于对齐返回条高度
  const mb = (typeof wx !== 'undefined' && wx.getMenuButtonBoundingClientRect) ? wx.getMenuButtonBoundingClientRect() : null
  if (mb && mb.height) navBarHeight.value = (mb.top - sb) * 2 + mb.height
  webviewStyles.value = {
    top: `${sb + navBarHeight.value}px`,
    progressbar: { color: '#9B2A1D' },
  }
})

// web-view 内容顶部留出「状态栏 + 返回条」高度，避免被刘海/状态栏遮挡
const webviewStyles = ref({
  top: '64px',
  progressbar: { color: '#9B2A1D' },
})

function back() {
  // @ts-ignore
  const pages = (typeof getCurrentPages === 'function') ? getCurrentPages() : []
  if (pages.length > 1) uni.navigateBack()
  else uni.reLaunch({ url: '/pages/home/index' })
}

function onMessage(e: any) {
  // 网页可通过 wx.miniProgram.postMessage 回传，预留
}
</script>

<style scoped>
.wv-page { position: relative; width: 100%; height: 100vh; background: var(--bg-page); }
.wv-bar {
  position: fixed;
  left: 0; right: 0; top: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  background: var(--color-primary);
  border-bottom: none;
}
.wv-back {
  width: 44px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-tap-highlight-color: transparent;
}
.wv-title {
  font-size: var(--font-h3);
  font-weight: var(--weight-bold);
  color: var(--text-white);
}
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
