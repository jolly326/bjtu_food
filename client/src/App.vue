<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";
import { useThemeStore } from "@/stores/theme";
import { useUserStore } from "@/stores/user";
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
  // 微信自动静默登录（§5.y）：打开小程序即登录为游客态（verified=false）；有 token 则刷新资料
  // 失败（如后端不可达）仅打点，不阻断浏览与菜单栏渲染
  useUserStore().silentLogin().catch(() => {})
});
</script>
<style>
/* ========== 全局设计 Token（Apple Design 风格） ==========
   设计 Token 值以 theme/tokens.ts 为单一事实源（仅 IconSvg 色值等原生属性例外见该文件）。
   此处（App 全局样式）为 WXSS 变量声明面：因 uni.scss 的 :root 在编译为小程序 WXSS 时被丢弃，
   真实声明须落在 App.vue 的 page / .theme-dark / :root 三条规则中，确保各平台 token 全部生效。
   增量补齐组件实际引用但此前缺失的 --color-accent / --color-primary-soft /
   --font-title / --icon-lg / --radius-tag / --font-small 等。 */
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
  --bg-page: #F1ECE6;
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
  --bg-cell-activity: #CFE3FA;
  --color-cell-activity: #1E5FCE;
  --bg-cell-feedback: #C4ECDD;
  --color-cell-feedback: #0E9E6E;
  /* 卡片描边（万能两卡与通用卡片边界，低对比强化边界；语义卡用各自色淡描边） */
  --border-card: rgba(0, 0, 0, 0.12);
  --border-cell-activity: rgba(30, 95, 206, 0.28);
  --border-cell-feedback: rgba(14, 158, 110, 0.28);
  /* 圆角 */
  /* 圆角标度（单位统一 rpx，与 --spacing-* 同单位；none/circle 为形状修饰，非量级） */
  --radius-none: 0;
  --radius-xs: 16rpx;
  --radius-tag: 32rpx;
  --radius-card: 32rpx;
  --radius-modal: 48rpx;
  --radius-btn: 32rpx;
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
  --font-h1: 48rpx;
  --font-display: 72rpx;
  /* 图标尺寸 */
  --icon-sm: 28rpx;
  --icon-lg: 48rpx;
  /* 占位/空态图标字号（.placeholder-icon / .empty-icon 的字形尺寸，非文本排版） */
  --icon-xl: 56rpx;
  --icon-2xl: 64rpx;
  --icon-3xl: 80rpx;
  --icon-4xl: 120rpx;
  /* 阴影（材质 / 深度；卡片阴影 2026-08-12 拍板：0 2px 8px rgba(0,0,0,0.04)） */
  --shadow-card: 0 2px 12px rgba(0, 0, 0, 0.08);
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
  /* 浮起/按下阴影（global-ui-polish：替代裸 rgba 阴影） */
  --shadow-float: 0 6rpx 16rpx rgba(0, 0, 0, 0.12);
  --shadow-press: 0 1rpx 3rpx rgba(0, 0, 0, 0.12);
  /* 长条删除按钮（图片移除）暗底白字 */
  --badge-dark-bg: rgba(0, 0, 0, 0.5);
  --badge-dark-text: var(--text-white);
  /* 浅色文字半透（hero 副标题等） */
  --text-white-soft: rgba(255, 255, 255, 0.84);
  --text-white-faint: rgba(255, 255, 255, 0.18);
  --text-white-edge: rgba(255, 255, 255, 0.24);
  /* 动效时长（统一，避免散落 0.12s/0.15s/0.2s/0.3s） */
  --duration-fast: 120ms;
  --duration-base: 200ms;
  --duration-slow: 300ms;
  --ease-out: cubic-bezier(0.23, 1, 0.32, 1);
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
  /* 卡片描边（深色：低对比强化边界；语义卡用各自色淡描边） */
  --border-card: rgba(255, 255, 255, 0.14);
  --border-cell-activity: rgba(127, 168, 232, 0.28);
  --border-cell-feedback: rgba(76, 207, 154, 0.28);
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
  /* 浮起/按下阴影（global-ui-polish：深色分支，提升不透明度以适配深底） */
  --shadow-float: 0 6rpx 16rpx rgba(0, 0, 0, 0.5);
  --shadow-press: 0 1rpx 3rpx rgba(0, 0, 0, 0.4);
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
  --bg-page: #F1ECE6;
  --bg-card: #FFFFFF;
  --bg-input: #F7F5F2;
  --bg-soft: #EDE9E5;
  --bg-placeholder: #F0ECE8;
  /* 边框（已收敛 border-light → border-color） */
  --border-color: #E8E3DE;
  --border-bold: #CBC5BE;
  /* 万能卡片语义色（H5 兜底：与 page 块一致，活动=冷蓝、反馈=青绿） */
  --bg-cell-activity: #CFE3FA;
  --color-cell-activity: #1E5FCE;
  --bg-cell-feedback: #C4ECDD;
  --color-cell-feedback: #0E9E6E;
  --border-cell-activity: rgba(30, 95, 206, 0.22);
  --border-cell-feedback: rgba(14, 158, 110, 0.22);
  --radius-none: 0;
  --radius-xs: 16rpx;
  --radius-tag: 32rpx;
  --radius-card: 32rpx;
  --radius-modal: 48rpx;
  --radius-btn: 32rpx;
  --radius-icon: 24rpx;
  --radius-pill: 999rpx;
  --radius-circle: 50%;
  --radius-sheet: 48rpx;
  --spacing-2xs: 4rpx;
  --spacing-xs: 8rpx;
  --spacing-sm: 16rpx;
  --spacing-md: 24rpx;
  --spacing-lg: 32rpx;
  --spacing-xl: 48rpx;
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
  --font-h1: 48rpx;
  --font-display: 72rpx;
  --icon-sm: 28rpx;
  --icon-lg: 48rpx;
  /* 占位/空态图标字号（.placeholder-icon / .empty-icon 的字形尺寸，非文本排版） */
  --icon-xl: 56rpx;
  --icon-2xl: 64rpx;
  --icon-3xl: 80rpx;
  --icon-4xl: 120rpx;
  --shadow-card: 0 2px 12px rgba(0, 0, 0, 0.08);
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
  /* 动效时长（统一，避免散落 0.12s/0.15s/0.2s/0.3s） */
  --duration-fast: 120ms;
  --duration-base: 200ms;
  --duration-slow: 300ms;
  --ease-out: cubic-bezier(0.23, 1, 0.32, 1);
  /* 字距梯度（typo scale，标题负字距收紧、正文不收紧） */
  --tracking-h1: -0.02em;
  --tracking-h2: -0.02em;
  --tracking-h3: -0.01em;
  --tracking-body: 0;
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

/* ========== 交互状态通用令牌（client-ui-comprehensive-upgrade 1.1） ==========
   加载态遮罩底色（骨架/禁用态复用）、禁用态弱化文字色（复用四档文字末档）。
   微信小程序以 page 为准、H5 以 :root 为准，此处三者统一声明。 */
page { --state-loading: rgba(0, 0, 0, 0.04); --state-disabled: var(--text-quaternary); }
.theme-dark { --state-loading: rgba(255, 255, 255, 0.06); --state-disabled: var(--text-quaternary); --scale-hover: 1.02; }
:root { --state-loading: rgba(0, 0, 0, 0.04); --state-disabled: var(--text-quaternary); --scale-hover: 1.02; }

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

/* ========== 骨架屏（加载占位，静态灰块，无 shimmer 流光） ========== */
.skeleton {
  background: var(--bg-soft);
  border-radius: var(--radius-card);
}

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
/* 加载态：叠加微光遮罩并禁交互（配合 .skeleton 或独立用于整块） */
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
