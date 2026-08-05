<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";
onLaunch(() => {
  // 预留：登录态恢复等全局初始化
});
</script>
<style>
/* ========== 全局设计 Token（Apple Design 风格） ==========
   关键修复：uni.scss 的 :root 变量块在编译为小程序 WXSS 时被丢弃，
   导致全部 CSS 变量解析为空、页面裸文本。此处定义在 App 全局样式中，
   作用到小程序 page 根与 H5 :root，确保各平台 token 全部生效。
   增量补齐组件实际引用但此前缺失的 --color-accent / --color-primary-bg /
   --font-headline / --icon-lg / --radius-tag / --font-small 等。 */
/* =========================================================================
   设计 Token：拆分为「page」与「:root」两条独立规则。
   原因：微信小程序 WXSS 不支持 :root 选择器，若把二者写成同一选择器组，
   部分 WXSS 编译/运行环境会把整条规则丢弃，导致全部 CSS 变量解析为空 ——
   页面透明、卡片无底色无阴影、主色不显示（即「全白」）。
   拆成两条独立规则后：微信以 page 为准，H5 以 :root 为准，互不牵连。
   ========================================================================= */
page {
  /* 品牌主色（交大红系） */
  --color-primary: #8B3A2B;
  --color-primary-dark: #6B1010;
  --color-primary-soft: #FEF0EF;
  --color-primary-bg: #FBEDEB;
  --color-accent: #E67E22;
  --color-gradient: linear-gradient(135deg, #8B3A2B 0%, #C95C3F 58%, #E67E22 100%);
  --color-error: #E54D42;
  --color-success: #10B981;
  --color-warning: #F5A623;
  --color-price: #C0392B;
  --color-star: #FFB400;
  /* 空心星颜色（浅暖灰，避免评分低时大量空星显黑） */
  --color-star-empty: #E8E0D8;
  /* 语义状态色（浅底 + 对应前景，供 badge/tag 使用） */
  --color-success-soft: #ecfdf5;
  --color-error-soft: #fef2f2;
  --color-warning-soft: #fff8e1;
  --color-accent-soft: #fff3e0;
  --color-primary-soft2: #fde8e8;
  --color-hot-soft: #fef3e0;
  --color-hot: #E67E22;
  --color-like: #ff6b6b;
  --color-like-soft: #fff5f5;
  --border-light: #eee;
  /* 文字 */
  --text-white: #FFFFFF;
  --text-white-secondary: rgba(255, 255, 255, 0.85);
  --text-primary: #1C1917;
  --text-secondary: #6B625B;
  --text-tertiary: #A89E96;
  --text-quaternary: #aaa19a;
  /* 背景 */
  --bg-page: #F6F4EF;
  --bg-card: #FFFFFF;
  --bg-soft: #F1ECE6;
  --bg-placeholder: #f0f0f0;
  /* 边框 */
  --border-color: #ECE6E0;
  --border-bold: #C9BFB6;
  /* 圆角 */
  --radius-tag: 999rpx;
  --radius-card: 16px;
  --radius-modal: 24px;
  --radius-btn: 16px;
  --radius-icon: 12px;
  /* 间距（4pt 基准栅格） */
  --spacing-xs: 8rpx;
  --spacing-sm: 16rpx;
  --spacing-md: 24rpx;
  --spacing-lg: 32rpx;
  --spacing-xl: 48rpx;
  /* 字体（尺寸梯度） */
  --font-tiny: 20rpx;
  --font-aux: 22rpx;
  --font-small: 24rpx;
  --font-body: 28rpx;
  --font-caption: 30rpx;
  --font-subtitle: 32rpx;
  --font-card: 32rpx;
  --font-headline: 44rpx;
  --font-h3: 36rpx;
  --font-h2: 40rpx;
  --font-h1: 48rpx;
  /* 图标尺寸 */
  --icon-sm: 28rpx;
  --icon-lg: 48rpx;
  /* 阴影（材质 / 深度） */
  --shadow-card: 0 4rpx 16rpx rgba(56, 42, 34, 0.08);
  --shadow-card-soft: 0 8rpx 32rpx rgba(56, 42, 34, 0.08);
  --shadow-modal: 0 18rpx 54rpx rgba(56, 42, 34, 0.18);
  /* 半透材质（小程序真机 backdrop-filter 降级） */
  --blur-radius: 20px;
  --blur-bg: rgba(255, 255, 255, 0.72);
  --blur-bg-solid: rgba(255, 255, 255, 0.92);
  /* 玻璃/材质高光边（Apple §12 顶部光线） */
  --glass-highlight: rgba(255, 255, 255, 0.5);
  --glass-highlight-soft: rgba(255, 255, 255, 0.22);
  /* 暗化遮罩（图片叠加层 / 弹窗 scrim，禁止裸 rgba） */
  --overlay-dark-strong: rgba(0, 0, 0, 0.6);
  --overlay-dark-deep: rgba(0, 0, 0, 0.65);
  --overlay-dark-mid: rgba(0, 0, 0, 0.5);
  --overlay-dark-soft: rgba(0, 0, 0, 0.15);
  --overlay-dark-faint: rgba(0, 0, 0, 0.06);
  --overlay-scrim: rgba(0, 0, 0, 0.4);
  /* 卡片/底栏阴影（替代裸 shadow rgba） */
  --shadow-bar: 0 -4rpx 20rpx rgba(56, 42, 34, 0.08);
  --shadow-bar-soft: 0 -4rpx 12rpx rgba(0, 0, 0, 0.06);
  --shadow-bar-primary: 0 12rpx 28rpx rgba(139, 58, 43, 0.22);
  /* 主色半透（header 材质 / hero 阴影，避免裸 rgba） */
  --color-primary-glass: rgba(139, 58, 43, 0.82);
  --color-primary-alpha: rgba(139, 58, 43, 0.22);
  /* 长条删除按钮（图片移除）暗底白字 */
  --badge-dark-bg: rgba(0, 0, 0, 0.5);
  --badge-dark-text: var(--text-white);
  /* 浅色文字半透（hero 副标题等） */
  --text-white-soft: rgba(255, 255, 255, 0.84);
  --text-white-faint: rgba(255, 255, 255, 0.18);
  --text-white-edge: rgba(255, 255, 255, 0.24);
  /* 动效：按压 + 缓动曲线（emil-design-eng / Apple §1/§4） */
  --press-scale: 0.97;
  --press-transition: transform 0.12s ease;
  /* 动效时长（统一，避免散落 0.12s/0.15s/0.2s/0.3s） */
  --duration-fast: 120ms;
  --duration-base: 200ms;
  --duration-slow: 300ms;
  --duration-drawer: 400ms;
  --ease-out: cubic-bezier(0.23, 1, 0.32, 1);
  --ease-in-out: cubic-bezier(0.77, 0, 0.175, 1);
  --ease-drawer: cubic-bezier(0.32, 0.72, 0, 1);
  /* 选中态图标放大（量化 CustomTabBar 强调缩放，避免裸 scale） */
  --tab-active-scale: 1.05;
  /* 字距梯度（typo scale，标题负字距收紧、正文不收紧） */
  --tracking-h1: -0.02em;
  --tracking-h2: -0.02em;
  --tracking-h3: -0.01em;
  --tracking-body: 0;
  /* 字重梯度（统一，收敛裸 font-weight） */
  --weight-regular: 400;
  --weight-medium: 500;
  --weight-semibold: 600;
  --weight-bold: 700;
  --weight-heavy: 800;
  /* 布局 */
  --tabbar-height: 100rpx;
  /* 详情/表单页底部固定操作栏统一高度（§4.9 / T24，详情 action-bar / review 提交栏 / contact 提交栏同源避让） */
  --action-bar-height: 120rpx;
}

/* 全局盒模型重置：防止 padding 叠加到 width 造成 scroll-view 内卡片溢出屏幕右侧 */
page, view, scroll-view, text, image { box-sizing: border-box; }

/* H5 端根变量（微信小程序以 page 为准，此处仅供 H5/Webview 兜底） */
:root {
  /* 品牌主色（交大红系） */
  --color-primary: #8B3A2B;
  --color-primary-dark: #6B1010;
  --color-primary-soft: #FEF0EF;
  --color-primary-bg: #FBEDEB;
  --color-accent: #E67E22;
  --color-gradient: linear-gradient(135deg, #8B3A2B 0%, #C95C3F 58%, #E67E22 100%);
  --color-error: #E54D42;
  --color-success: #10B981;
  --color-warning: #F5A623;
  --color-price: #C0392B;
  --color-star: #FFB400;
  /* 空心星颜色（浅暖灰，避免评分低时大量空星显黑） */
  --color-star-empty: #E8E0D8;
  --color-success-soft: #ecfdf5;
  --color-error-soft: #fef2f2;
  --color-warning-soft: #fff8e1;
  --color-accent-soft: #fff3e0;
  --color-primary-soft2: #fde8e8;
  --color-hot-soft: #fef3e0;
  --color-hot: #E67E22;
  --color-like: #ff6b6b;
  --color-like-soft: #fff5f5;
  --border-light: #eee;
  --text-white: #FFFFFF;
  --text-white-secondary: rgba(255, 255, 255, 0.85);
  --text-primary: #1C1917;
  --text-secondary: #6B625B;
  --text-tertiary: #A89E96;
  --text-quaternary: #aaa19a;
  --bg-page: #F6F4EF;
  --bg-card: #FFFFFF;
  --bg-soft: #F1ECE6;
  --bg-placeholder: #f0f0f0;
  --border-color: #ECE6E0;
  --border-bold: #C9BFB6;
  --radius-tag: 999rpx;
  --radius-card: 16px;
  --radius-modal: 24px;
  --radius-btn: 16px;
  --radius-icon: 12px;
  --spacing-xs: 8rpx;
  --spacing-sm: 16rpx;
  --spacing-md: 24rpx;
  --spacing-lg: 32rpx;
  --spacing-xl: 48rpx;
  --font-tiny: 20rpx;
  --font-aux: 22rpx;
  --font-small: 24rpx;
  --font-body: 28rpx;
  --font-caption: 30rpx;
  --font-subtitle: 32rpx;
  --font-card: 32rpx;
  --font-headline: 44rpx;
  --font-h3: 36rpx;
  --font-h2: 40rpx;
  --font-h1: 48rpx;
  --icon-sm: 28rpx;
  --icon-lg: 48rpx;
  --shadow-card: 0 4rpx 16rpx rgba(56, 42, 34, 0.08);
  --shadow-card-soft: 0 8rpx 32rpx rgba(56, 42, 34, 0.08);
  --shadow-modal: 0 18rpx 54rpx rgba(56, 42, 34, 0.18);
  --blur-radius: 20px;
  --blur-bg: rgba(255, 255, 255, 0.72);
  --blur-bg-solid: rgba(255, 255, 255, 0.92);
  --glass-highlight: rgba(255, 255, 255, 0.5);
  --glass-highlight-soft: rgba(255, 255, 255, 0.22);
  --overlay-dark-strong: rgba(0, 0, 0, 0.6);
  --overlay-dark-deep: rgba(0, 0, 0, 0.65);
  --overlay-dark-mid: rgba(0, 0, 0, 0.5);
  --overlay-dark-soft: rgba(0, 0, 0, 0.15);
  --overlay-dark-faint: rgba(0, 0, 0, 0.06);
  --overlay-scrim: rgba(0, 0, 0, 0.4);
  --shadow-bar: 0 -4rpx 20rpx rgba(56, 42, 34, 0.08);
  --shadow-bar-soft: 0 -4rpx 12rpx rgba(0, 0, 0, 0.06);
  --shadow-bar-primary: 0 12rpx 28rpx rgba(139, 58, 43, 0.22);
  --color-primary-glass: rgba(139, 58, 43, 0.82);
  --color-primary-alpha: rgba(139, 58, 43, 0.22);
  --badge-dark-bg: rgba(0, 0, 0, 0.5);
  --badge-dark-text: var(--text-white);
  --text-white-soft: rgba(255, 255, 255, 0.84);
  --text-white-faint: rgba(255, 255, 255, 0.18);
  --text-white-edge: rgba(255, 255, 255, 0.24);
  --press-scale: 0.97;
  --press-transition: transform 0.12s ease;
  /* 动效时长（统一，避免散落 0.12s/0.15s/0.2s/0.3s） */
  --duration-fast: 120ms;
  --duration-base: 200ms;
  --duration-slow: 300ms;
  --duration-drawer: 400ms;
  --ease-out: cubic-bezier(0.23, 1, 0.32, 1);
  --ease-in-out: cubic-bezier(0.77, 0, 0.175, 1);
  --ease-drawer: cubic-bezier(0.32, 0.72, 0, 1);
  /* 选中态图标放大（量化 CustomTabBar 强调缩放，避免裸 scale） */
  --tab-active-scale: 1.05;
  /* 字距梯度（typo scale，标题负字距收紧、正文不收紧） */
  --tracking-h1: -0.02em;
  --tracking-h2: -0.02em;
  --tracking-h3: -0.01em;
  --tracking-body: 0;
  --tabbar-height: 100rpx;
  /* 详情/表单页底部固定操作栏统一高度（§4.9 / T24，详情 action-bar / review 提交栏 / contact 提交栏同源避让） */
  --action-bar-height: 120rpx;
}

/* ========== 页面基础壳 ========== */
.page {
  min-height: 100vh;
  background: var(--bg-page);
}

/* 主滚动区底部安全留白，避免内容被固定底栏遮挡 */
.scroll-wrap {
  padding-bottom: calc(var(--tabbar-height) + 24rpx + env(safe-area-inset-bottom));
}

/* ========== 半透材质工具类（Apple Design §12 材质与深度） ========== */
.glass {
  background: var(--blur-bg-solid);
  box-shadow: var(--shadow-card);
}
@supports ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .glass {
    background: var(--blur-bg);
    backdrop-filter: blur(var(--blur-radius)) saturate(180%);
    -webkit-backdrop-filter: blur(var(--blur-radius)) saturate(180%);
  }
}

/* ========== 按压反馈（emil-design-eng / Apple §1） ==========
   按下瞬间缩放到 0.97，松手回弹；仅 transform，合成层友好。 */
.press {
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.press:active {
  transform: scale(var(--press-scale));
}

/* ========== 进场动画（红线 §4.9②：MVP 真机仅简单 CSS 过渡，位移 ≤0） ==========
   降级为纯 opacity 交叉淡入，不做位移 / 复杂 keyframe。 */
@keyframes enterFade {
  from { opacity: 0; }
  to { opacity: 1; }
}
.enter-up {
  animation: enterFade 0.2s ease both;
  animation-delay: calc(var(--enter-i, 0) * 40ms);
}

/* ========== 骨架屏（加载占位，shimmer 流光） ========== */
@keyframes shimmer {
  0% { background-position: -150% 0; }
  100% { background-position: 150% 0; }
}
.skeleton {
  background: linear-gradient(90deg, var(--bg-soft) 25%, var(--border-color) 37%, var(--bg-soft) 63%);
  background-size: 400% 100%;
  animation: shimmer 1.4s ease infinite;
  border-radius: var(--radius-card);
}

/* ========== 减少动态效果（Apple §14） ========== */
@media (prefers-reduced-motion: reduce) {
  .dish-card,
  .app-btn,
  .tab-item,
  .swiper-slide,
  .enter-up,
  .press,
  .pressed,
  .filter-enter,
  .m-action,
  .fab {
    transition: opacity 0.2s ease !important;
    animation: none !important;
  }
  .dish-card,
  .app-btn,
  .press,
  .pressed,
  .m-action,
  .fab {
    transform: none !important;
  }
  @keyframes tabIn { from, to { transform: none; opacity: 1; } }
  .skeleton-icon,
  .skeleton-line,
  .skeleton-dish-img,
  .skeleton-dish-name,
  .skeleton-dish-price,
  .skeleton {
    animation: none !important;
  }
}

/* ========== 减少透明度（材质降级为更实） ========== */
@media (prefers-reduced-transparency: reduce) {
  .glass {
    background: var(--bg-card);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}
</style>
