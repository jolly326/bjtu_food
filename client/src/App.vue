<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";
import { useUserStore } from "@/stores/user";
import { WX_CLOUD_ENV } from "@/api/config";
onLaunch(() => {
  // 初始化微信云开发/云托管环境（小程序端 callContainer 调用依赖；H5 等平台跳过）
  // #ifdef MP-WEIXIN
  const wxApi: any = (globalThis as any).wx;
  if (wxApi && wxApi.cloud) {
    wxApi.cloud.init({ env: WX_CLOUD_ENV, traceUser: true });
  }
  // #endif
  // 微信自动静默登录（§5.y）：打开小程序即登录为游客态（verified=false）；有 token 则刷新资料
  // 失败（如后端不可达）仅打点，不阻断浏览与菜单栏渲染
  useUserStore().silentLogin().catch(() => {})
});
</script>
<style>
/* ========== 全局设计 Token（Apple Design 风格） ==========
   设计 Token 值以 theme/tokens.ts 为单一事实源（仅 IconSvg 色值等原生属性例外见该文件）。
   此处（App 全局样式）为 WXSS 变量声明面，且**仅保留 page 一条规则**：
   - 因 uni.scss 的 :root 在编译为小程序 WXSS 时被丢弃，真实声明须落在 App.vue；
   - 微信小程序 WXSS 不支持 :root 选择器，故必须以 page 承载；
   - 交付目标仅微信小程序（H5 构建脚本已移除），不再保留 :root 兜底块；
   - 产品仅一种主体颜色、无深色模式切换，不再保留 .theme-dark 令牌块。 */
page {
  /* ========== 浅色模式（值见 src/theme/tokens.ts 单一事实源） ==========
     2026-09-05 拍板：主色先定暖砖红（tab-pages-visual-unify），tab-pages-visual-refine-2 提亮为 #C45549 */
  /* 品牌主色（提亮暖砖红 terracotta；全站统一暖砖红色系） */
  --color-primary: #C45549;
  /* 主色上的文字（按钮/强调）：暖砖红底配白字（#C45549 与白字对比 ~3:1，按钮用白字） */
  --color-on-primary: #FFFFFF;
  /* 主色浅底（图标软底/选中标签底），已收敛 primary-bg/primary-soft2 */
  --color-primary-soft: #F8E8E5;
  /* 主色表面（header/home-top 大面积品牌色块，珊瑚橙统一） */
  --color-on-primary-surface: #FFFFFF;
  /* 强调色（热卖/热搜/新品统一走 accent，已收敛 color-hot） */
  --color-accent: #C45A3C;
  --color-accent-soft: #F5E6E3;
  /* 语义色（error/success/warning/price/star/like 深浅对称） */
  --color-error: #FF3B30;
  --color-error-soft: #FFECEB;
  --color-warning: #F5A623;
  --color-warning-soft: #FFF8E1;
  /* 价格红：并入主色体系（价格用主色） */
  --color-price: #C45549;
  --color-star: #F5A623;
  /* 空心星颜色（浅暖灰，避免评分低时大量空星显黑） */
  --color-star-empty: #E5E5EA;
  --color-like: #9E3B2E;
  --color-like-soft: #F6E3E0;
  /* 文字（三阶层级：一级 #262626 / 二级 #595959 / 三级 #999999） */
  --text-white: #FFFFFF;
  --text-primary: #262626;
  --text-secondary: #595959;
  --text-tertiary: #999999;
  /* 背景 */
  --bg-page: #F7F3EF;
  --bg-card: #FFFFFF;
  --bg-input: #F7F5F2;
  --bg-soft: #EDE9E5;
  --bg-placeholder: #F0ECE8;
  /* 边框（已收敛 border-light → border-color） */
  --border-color: #E8E3DE;
  --border-bold: #CBC5BE;
  /* 万能卡片语义色（首页两列：最新活动=冷蓝、反馈菜品=青绿，深浅两套对称）。
     2026-08-17 加深浅色底：原 #E8F1FB/#E7F7F2 在白页上接近白色卡片，无法与广播条等白色组件区分；
     加深到中低明度冷色底，保证一眼可辨的彩色卡片，同时保持图标/文字对比 ≥4.5:1。 */
  /* 卡片描边（万能两卡与通用卡片边界，低对比强化边界；语义卡用各自色淡描边） */
  --border-card: rgba(0, 0, 0, 0.12);
  /* 圆角 */
  /* 圆角标度（单位统一 rpx，与 --spacing-* 同单位；none/circle 为形状修饰，非量级） */
  --radius-none: 0;
  --radius-xs: 16rpx;
  --radius-tag: 16rpx;
  --radius-card: 32rpx;
  --radius-modal: 48rpx;
  --radius-btn: 16rpx;
  --radius-icon: 24rpx;
  /* 全圆胶囊（搜索框/筛选 chip/进度条/小标签）；原 16px 名实不符，已修正为全圆角 */
  --radius-pill: 999rpx;
  /* 正圆（头像 / 圆点 / 指示器） */
  --radius-circle: 50%;
  /* 底部弹层/提交栏圆角顶边（与 --radius-modal 同值，意见反馈页提交栏等引用） */
  --radius-sheet: 48rpx;
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
  --font-label: 26rpx;
  --font-body: 28rpx;
  --font-caption: 30rpx;
  /* 字号标度（单调升序、同值无别名；32rpx=--font-subtitle，44rpx=--font-title） */
  --font-subtitle: 32rpx;
  --font-h3: 36rpx;
  --font-h2: 40rpx;
  --font-title: 44rpx;
  /* 图标尺寸 */
  /* 占位/空态图标字号（.placeholder-icon / .empty-icon 的字形尺寸，非文本排版） */
  --icon-xl: 56rpx;
  --icon-2xl: 64rpx;
  --icon-3xl: 80rpx;
  --icon-4xl: 120rpx;
  /* 阴影（材质 / 深度；卡片阴影为中性淡投影 rgba(0,0,0,0.04)，不随主色；顶栏品牌阴影见 --shadow-bar-primary） */
  --shadow-card: 0 2px 12px rgba(0, 0, 0, 0.04);
  --shadow-card-soft: 0 8rpx 32rpx rgba(0, 0, 0, 0.06);
  --shadow-modal: 0 18rpx 54rpx rgba(0, 0, 0, 0.18);
  /* 半透材质（小程序真机 backdrop-filter 降级） */
  /* 玻璃/材质高光边（Apple §12 顶部光线） */
  /* 暗化遮罩（图片叠加层 / 弹窗 scrim，禁止裸 rgba） */
  --overlay-dark-strong: rgba(0, 0, 0, 0.6);
  --overlay-dark-soft: rgba(0, 0, 0, 0.15);
  --overlay-dark-faint: rgba(0, 0, 0, 0.06);
  --overlay-scrim: rgba(0, 0, 0, 0.4);
  /* 卡片/底栏阴影（替代裸 shadow rgba） */
  --shadow-bar: 0 -4rpx 20rpx rgba(56, 42, 34, 0.08);
  --shadow-bar-soft: 0 -4rpx 12rpx rgba(0, 0, 0, 0.06);
  --shadow-bar-primary: 0 12rpx 28rpx rgba(196, 85, 73, 0.28);
  /* 浮起/按下阴影（global-ui-polish：替代裸 rgba 阴影） */
  --shadow-float: 0 6rpx 16rpx rgba(0, 0, 0, 0.12);
  /* 长条删除按钮（图片移除）暗底白字 */
  --badge-dark-bg: rgba(0, 0, 0, 0.5);
  --badge-dark-text: var(--text-white);
  /* 浅色文字半透（hero 副标题等） */
  /* 动效时长（统一，避免散落 0.12s/0.15s/0.2s/0.3s） */
  --duration-fast: 120ms;
  --duration-base: 200ms;
  --duration-slow: 300ms;
  --ease-out: cubic-bezier(0.23, 1, 0.32, 1);
  /* 字距梯度（typo scale，标题负字距收紧、正文不收紧） */
  --tracking-h2: -0.02em;
  --tracking-h3: -0.01em;
  /* 字重梯度（统一，收敛裸 font-weight） */
  --weight-regular: 400;
  --weight-medium: 500;
  --weight-semibold: 600;
  --weight-bold: 700;
  --weight-heavy: 800;
  /* 布局：主滚动区底部安全留白（.scroll-wrap 消费；命名沿用历史 tabbar 高度，非字面 TabBar） */
  --tabbar-height: 100rpx;
  /* 详情/表单页底部固定操作栏统一高度（§4.9 / T24，详情 action-bar / review 提交栏 / contact 提交栏同源避让） */
  --action-bar-height: 120rpx;
  /* 层级标度：统一浮层 z-index，数值越大越靠上，避免互相遮挡 / 点击穿透 */
  --z-sheet: 2000;        /* 底部半屏弹层（ApplySheet / RelatedPickerSheet） */
  --z-sheet-mask: 1990;   /* 上述弹层遮罩 */
  --z-actionsheet: 4000;  /* 操作菜单（ReviewActionSheet / MomentActionSheet） */
  --z-modal: 5000;        /* 居中弹窗（ReportModal） */
  --z-auth: 6000;         /* 登录网关，最高层级 */
}

/* 全局盒模型重置：防止 padding 叠加到 width 造成 scroll-view 内卡片溢出屏幕右侧 */
page, view, scroll-view, text, image { box-sizing: border-box; }

/* ========== 交互状态通用令牌（client-ui-comprehensive-upgrade 1.1） ==========
   加载态遮罩底色（.is-loading 遮罩复用）、禁用态弱化文字色（复用四档文字末档）。
   仅由 page 声明（H5 兜底与深色分支已移除）。 */
page { --state-loading: rgba(0, 0, 0, 0.04); --state-disabled: var(--text-tertiary); }

/* ========== 页面基础壳 ========== */
.page {
  min-height: 100vh;
  background: var(--bg-page);
}

/* 主滚动区底部安全留白，避免内容被固定底栏遮挡 */
.scroll-wrap {
  min-height: 0;
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

/* 按压反馈已移除（client-ui-motion-removal-tokens-consolidation）：MVP 仅保留 :active opacity 弱化为交互反馈，不再使用 transform scale / hover-class。 */

/* 注：装饰性入场动效（原 .enter-up / enterFade）已于 client-mvp-strip-entrance-anim 剥离，
   MVP 阶段内容一律静态直接呈现，可见性不依赖动画。 */

/* 减少动态效果媒体查询已移除：全站动效已于 client-ui-motion-removal-tokens-consolidation 剥离，无需降级。 */

/* ========== 减少透明度（材质降级为更实） ========== */
@media (prefers-reduced-transparency: reduce) {
  .glass {
    background: var(--bg-card);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

/* ========== 交互状态工具类（client-ui-comprehensive-upgrade 1.2/1.4/1.6/2.2/4.1） ==========
   统一加载/禁用/错误态与键盘焦点、hover、宽屏容器，避免各组件散落重复实现。 */
/* 加载态：叠加微光遮罩并禁交互（独立用于整块） */
.is-loading { position: relative; pointer-events: none; }
.is-loading::after {
  content: ''; position: absolute; inset: 0; border-radius: inherit;
  background: var(--state-loading);
}
/* 禁用态：弱化 + 禁点（语义文字色复用 --state-disabled） */
.is-disabled { opacity: 0.5; pointer-events: none; filter: grayscale(0.2); }
/* 错误态：内联校验失败提示容器（与 .has-error 文字色配对） */
.has-error { color: var(--color-error); }
/* 键盘焦点环（Apple 焦点规范：仅键盘可达时显示，触屏/鼠标不显） */
:focus-visible {
  outline: 3rpx solid var(--color-primary);
  outline-offset: 2rpx;
  border-radius: var(--radius-xs);
}
/* .hoverable 缩放已移除（client-ui-motion-removal-tokens-consolidation）：MVP 仅保留 :active opacity 反馈。 */
/* 宽屏容器：桌面/平板居中限宽，移动端自然铺满（4.1） */
.app-container { width: 100%; margin: 0 auto; box-sizing: border-box; }
@media (min-width: 768px) {
  .app-container { max-width: 720px; }
  /* 主滚动区在宽屏居中限宽，避免内容被无限拉伸（4.1，仅 H5/桌面生效） */
  .scroll-wrap { max-width: 720px; margin: 0 auto; }
}
</style>
