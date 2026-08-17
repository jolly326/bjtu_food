<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";
import { useThemeStore } from "@/stores/theme";
import { WX_CLOUD_ENV } from "@/api/config";
onLaunch(() => {
  // 恢复深色模式偏好（本地持久化）
  useThemeStore().init();
  // 初始化微信云开发/云托管环境（小程序端 callContainer 调用依赖；H5 等平台跳过）
  // #ifdef MP-WEIXIN
  const wxApi: any = (globalThis as any).wx;
  if (wxApi && wxApi.cloud) {
    wxApi.cloud.init({ env: WX_CLOUD_ENV, traceUser: true });
  }
  // #endif
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
  /* ========== 浅色模式（值见 src/theme/tokens.ts 单一事实源） ==========
     2026-08-16 拍板：品牌主色由珊瑚橙 → 朱砂红 #9B2A1D（呼应食堂暖色场景） */
  /* 品牌主色（朱砂红 vermilion；全站统一朱砂红色系，浅色=#9B2A1D） */
  --color-primary: #9B2A1D;
  --color-primary-dark: #7A1F14;
  /* 主色上的文字（按钮/强调）：珊瑚橙底配白字（#9B2A1D 与白字对比 ~3:1，按钮用白字） */
  --color-on-primary: #FFFFFF;
  /* 导航激活态（强调色点缀）：浅色=珊瑚橙 */
  --color-on-tab: #9B2A1D;
  /* 主色浅底（图标软底/选中标签底），已收敛 primary-bg/primary-soft2 */
  --color-primary-soft: #E8D0C4;
  /* 主色表面（header/home-top 大面积品牌色块，珊瑚橙统一） */
  --color-primary-surface: #9B2A1D;
  --color-on-primary-surface: #FFFFFF;
  /* 强调色（热卖/热搜/新品统一走 accent，已收敛 color-hot） */
  --color-accent: #C45A3C;
  --color-accent-soft: #E8D0C4;
  --color-gradient: linear-gradient(135deg, #9B2A1D 0%, #C45A3C 58%, #E8D0C4 100%);
  /* 语义色（error/success/warning/price/star/like 深浅对称） */
  --color-error: #FF3B30;
  --color-error-soft: #FFECEB;
  --color-success: #10B981;
  --color-success-soft: #ECFDF5;
  --color-warning: #F5A623;
  --color-warning-soft: #FFF8E1;
  /* 价格红：珊瑚橙加深（区别于 primary 与 error） */
  --color-price: #C45A3C;
  --color-star: #F5A623;
  /* 空心星颜色（浅暖灰，避免评分低时大量空星显黑） */
  --color-star-empty: #E5E5EA;
  --color-like: #B53B2C;
  --color-like-soft: #F6E3E0;
  /* 文字（四档层级，tertiary 提对比至 ~3:1） */
  --text-white: #FFFFFF;
  --text-white-secondary: rgba(255, 255, 255, 0.85);
  --text-primary: #1D1A18;
  --text-secondary: #6E6964;
  --text-tertiary: #8F8A84;
  --text-quaternary: #ABA59E;
  /* 背景 */
  --bg-page: #F7F5F2;
  --bg-card: #FFFFFF;
  --bg-input: #F7F5F2;
  --bg-soft: #EDE9E5;
  --bg-placeholder: #F0ECE8;
  /* 边框（已收敛 border-light → border-color） */
  --border-color: #E8E3DE;
  --border-bold: #CBC5BE;
  /* 万能卡片语义色（首页两列：最新活动=冷蓝、反馈菜品=青绿，深浅两套对称） */
  --bg-cell-activity: #E8F1FB;
  --color-cell-activity: #2F6FED;
  --bg-cell-feedback: #E7F7F2;
  --color-cell-feedback: #12B886;
  /* 圆角 */
  --radius-tag: 16px;
  --radius-card: 16px;
  --radius-modal: 24px;
  --radius-btn: 16px;
  --radius-icon: 12px;
  --radius-pill: 16px;
  /* 底部弹层/提交栏圆角顶边（与 --radius-modal 同值，意见反馈页提交栏等引用） */
  --radius-sheet: 24px;
  /* 间距（4pt 基准栅格；2xs=半格，供星标/徽标等紧凑布局，避免裸 4rpx） */
  --spacing-2xs: 4rpx;
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
  --font-display: 72rpx;
  /* 图标尺寸 */
  --icon-sm: 28rpx;
  --icon-lg: 48rpx;
  /* 阴影（材质 / 深度；卡片阴影 2026-08-12 拍板：0 2px 8px rgba(0,0,0,0.04)） */
  --shadow-card: 0 2px 8px rgba(0, 0, 0, 0.04);
  --shadow-card-soft: 0 8rpx 32rpx rgba(0, 0, 0, 0.06);
  --shadow-modal: 0 18rpx 54rpx rgba(0, 0, 0, 0.18);
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
  --shadow-bar-primary: 0 12rpx 28rpx rgba(155, 42, 29, 0.28);
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

/* ========== 深色模式（手动开关） ==========
   原理：页面根节点挂 .theme-dark class → 命中此选择器的深色 token 覆盖，
   CSS 变量沿后代继承，全站即时切换，无需逐组件改动。
   值参考 Apple 深色材质（灰黑底 + 提亮主色 + 低饱和文字）。 */
.theme-dark {
  /* ========== 深色模式（值见 src/theme/tokens.ts 单一事实源） ==========
     2026-08-16 拍板：主色随浅色由珊瑚橙 → 朱砂红，深色提亮 #C45A3C 深底醒目 */
  /* 品牌主色：朱砂红色相提亮（全站统一朱砂红色系；#C45A3C 深底醒目，白字 AA） */
  --color-primary: #C45A3C;
  --color-primary-dark: #A8482E;
  /* 主色上的文字（按钮/强调）：珊瑚橙底配白字 */
  --color-on-primary: #FFFFFF;
  /* 导航激活态（TabBar 图标+文字统一）：深色=珊瑚橙提亮 */
  --color-on-tab: #C45A3C;
  /* 主色浅底（深色=低明度暖褐，已收敛 primary-bg/primary-soft2） */
  --color-primary-soft: #3A211C;
  /* 主色表面（header/home-top 大面积：深浅统一珊瑚橙提亮，产品决策） */
  --color-primary-surface: #A8482E;
  --color-on-primary-surface: #F5EFEC;
  /* 强调色（已收敛 color-hot） */
  --color-accent: #C45A3C;
  --color-accent-soft: #3A241F;
  /* 语义色（error/success/warning/price/star/like 深浅对称） */
  --color-error: #FF6B61;
  --color-error-soft: #3A2321;
  --color-success: #34D399;
  --color-success-soft: #16302A;
  --color-warning: #F5B83D;
  --color-warning-soft: #382D1B;
  /* 价格红：深色提亮暖橙（区别于 error，此前与 error 同值） */
  --color-price: #E8A07E;
  --color-star: #FFC24B;
  --color-star-empty: #3A3632;
  --color-like: #D9695A;
  --color-like-soft: #3A211C;
  /* 文字（四档层级，tertiary 提亮至 ~3:1） */
  --text-white: #FFFFFF;
  --text-white-secondary: rgba(255, 255, 255, 0.85);
  --text-primary: #F4F0EC;
  --text-secondary: #B8B0A8;
  --text-tertiary: #8E887F;
  --text-quaternary: #6F6960;
  /* 背景（灰黑底，apple-design §12 深色材质） */
  --bg-page: #161310;
  --bg-card: #201D1A;
  --bg-input: #2C2823;
  --bg-soft: #2C2823;
  --bg-placeholder: #28231E;
  /* 边框（已收敛 border-light → border-color） */
  --border-color: #2E2A27;
  --border-bold: #3D3935;
  /* 万能卡片语义色（深色：低明度冷蓝/青绿底 + 提亮文字，对比 ≥4.5:1） */
  --bg-cell-activity: #16263A;
  --color-cell-activity: #7FA8E8;
  --bg-cell-feedback: #0F2E26;
  --color-cell-feedback: #4CCF9A;
  /* 深色渐变（珊瑚橙系，与浅色一致） */
  --color-gradient: linear-gradient(135deg, #A8482E 0%, #C45A3C 58%, #E8D0C4 100%);
  --shadow-card: 0 4rpx 16rpx rgba(0, 0, 0, 0.4);
  --shadow-card-soft: 0 8rpx 32rpx rgba(0, 0, 0, 0.4);
  --shadow-modal: 0 18rpx 54rpx rgba(0, 0, 0, 0.6);
  --blur-bg: rgba(28, 28, 28, 0.72);
  --blur-bg-solid: rgba(31, 31, 31, 0.92);
  --glass-highlight: rgba(255, 255, 255, 0.08);
  --glass-highlight-soft: rgba(255, 255, 255, 0.05);
  --shadow-bar: 0 -4rpx 20rpx rgba(0, 0, 0, 0.4);
  --shadow-bar-soft: 0 -4rpx 12rpx rgba(0, 0, 0, 0.4);
  --shadow-bar-primary: 0 12rpx 28rpx rgba(0, 0, 0, 0.5);
}

/* 全局盒模型重置：防止 padding 叠加到 width 造成 scroll-view 内卡片溢出屏幕右侧 */
page, view, scroll-view, text, image { box-sizing: border-box; }

/* H5 端根变量（微信小程序以 page 为准，此处仅供 H5/Webview 兜底） */
:root {
  /* ========== 浅色模式（H5 回退，值见 src/theme/tokens.ts 单一事实源） ==========
     2026-08-16 拍板：品牌主色由珊瑚橙 → 朱砂红 #9B2A1D */
  /* 品牌主色（珊瑚橙色 apricot；全站统一珊瑚橙色系，浅色=#9B2A1D） */
  --color-primary: #9B2A1D;
  --color-primary-dark: #7A1F14;
  /* 主色上的文字（按钮/强调）：珊瑚橙底配白字 */
  --color-on-primary: #FFFFFF;
  /* 导航激活态（强调色点缀）：浅色=珊瑚橙 */
  --color-on-tab: #9B2A1D;
  /* 主色浅底（已收敛 primary-bg/primary-soft2） */
  --color-primary-soft: #E8D0C4;
  /* 主色表面（header/home-top 大面积品牌色块，珊瑚橙统一） */
  --color-primary-surface: #9B2A1D;
  --color-on-primary-surface: #FFFFFF;
  /* 强调色（已收敛 color-hot） */
  --color-accent: #C45A3C;
  --color-accent-soft: #E8D0C4;
  --color-gradient: linear-gradient(135deg, #9B2A1D 0%, #C45A3C 58%, #E8D0C4 100%);
  /* 语义色（error/success/warning/price/star/like 深浅对称） */
  --color-error: #FF3B30;
  --color-error-soft: #FFECEB;
  --color-success: #10B981;
  --color-success-soft: #ECFDF5;
  --color-warning: #F5A623;
  --color-warning-soft: #FFF8E1;
  /* 价格红：珊瑚橙加深（区别于 primary 与 error） */
  --color-price: #C45A3C;
  --color-star: #F5A623;
  /* 空心星颜色（浅暖灰，避免评分低时大量空星显黑） */
  --color-star-empty: #E5E5EA;
  --color-like: #B53B2C;
  --color-like-soft: #F6E3E0;
  /* 文字（四档层级） */
  --text-white: #FFFFFF;
  --text-white-secondary: rgba(255, 255, 255, 0.85);
  --text-primary: #1D1A18;
  --text-secondary: #6E6964;
  --text-tertiary: #8F8A84;
  --text-quaternary: #ABA59E;
  /* 背景 */
  --bg-page: #F7F5F2;
  --bg-card: #FFFFFF;
  --bg-input: #F7F5F2;
  --bg-soft: #EDE9E5;
  --bg-placeholder: #F0ECE8;
  /* 边框（已收敛 border-light → border-color） */
  --border-color: #E8E3DE;
  --border-bold: #CBC5BE;
  --radius-tag: 16px;
  --radius-card: 16px;
  --radius-modal: 24px;
  --radius-btn: 16px;
  --radius-icon: 12px;
  --radius-pill: 16px;
  --radius-sheet: 24px;
  --spacing-2xs: 4rpx;
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
  --font-display: 72rpx;
  --icon-sm: 28rpx;
  --icon-lg: 48rpx;
  --shadow-card: 0 2px 8px rgba(0, 0, 0, 0.04);
  --shadow-card-soft: 0 8rpx 32rpx rgba(0, 0, 0, 0.06);
  --shadow-modal: 0 18rpx 54rpx rgba(0, 0, 0, 0.18);
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
  --shadow-bar-primary: 0 12rpx 28rpx rgba(155, 42, 29, 0.28);
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
/* ========== 真机按压反馈（微信 hover-class 用，WXSS 下 :active 无效） ==========
   微信小程序 view 不支持 :active 伪类，真机按压缩放需用 hover-class="pressed"。
   此全局类供各可点击 view 的 hover-class 复用（含 CustomTabBar/InteractBar/Rating 等）。 */
.pressed {
  transform: scale(var(--press-scale)) !important;
  transition: transform var(--duration-fast) var(--ease-out);
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
