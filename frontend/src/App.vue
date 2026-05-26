<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";
onLaunch(() => {
  console.log("App Launch");
});
onShow(() => {
  console.log("App Show");
});
onHide(() => {
  console.log("App Hide");
});
</script>
<style>
/* ========== 页面基础壳 ========== */
.page {
  min-height: 100vh;
  background: var(--bg-page);
}

/* ========== 全局色系变量 ========== */
page {
  /* 主色系 — 砖红色 (设计文档: #8B3A2B) */
  --color-primary: #8B3A2B;
  --color-primary-light: #A0522D;
  --color-primary-dark: #6B1010;
  --color-primary-bg: #fef0ef;
  --color-gradient: linear-gradient(135deg, #8B3A2B, #A0522D);

  /* 辅助色/强调色 (价格、热卖标签) */
  --color-accent: #E67E22;
  --color-star: #F5A623;

  /* 状态色 */
  --color-success: #27AE60;
  --color-warning: #F39C12;
  --color-error: #E74C3C;

  /* 文字色 */
  --text-primary: #1A1A1A;
  --text-secondary: #666666;
  --text-tertiary: #999999;
  --text-white: #FFFFFF;
  --text-white-secondary: rgba(255, 255, 255, 0.85);

  /* 背景色 */
  --bg-page: #F8F8F8;
  --bg-card: #FFFFFF;

  /* 边框色 */
  --border-color: #EEEEEE;

  /* 价格高亮色 (使用辅助色) */
  --color-price: #E67E22;

  /* 间距 */
  --spacing-xs: 8rpx;
  --spacing-sm: 16rpx;
  --spacing-md: 24rpx;
  --spacing-lg: 32rpx;
  --spacing-xl: 48rpx;

  /* 圆角 */
  --radius-card: 16rpx;
  --radius-btn: 48rpx;
  --radius-icon: 8rpx;

  /* 阴影 */
  --shadow-card: 0 4rpx 12rpx rgba(0, 0, 0, 0.05);
  --shadow-hover: 0 8rpx 24rpx rgba(0, 0, 0, 0.1);

  /* 字体大小 */
  --font-h1: 44rpx;
  --font-h2: 36rpx;
  --font-body: 28rpx;
  --font-aux: 24rpx;
  --font-tiny: 22rpx;

  /* 图标尺寸 */
  --icon-sm: 32rpx;
  --icon-md: 40rpx;
  --icon-lg: 48rpx;

  /* 底部导航栏 */
  --tabbar-height: 100rpx;
}
</style>
