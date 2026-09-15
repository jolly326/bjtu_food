<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";
import { useUserStore } from "@/stores/user";
import { WX_CLOUD_ENV } from "@/api/config";
onLaunch(() => {
  // 初始化微信云开发/云托管环境（小程序端 callContainer 调用依赖；H5 等平台跳过）
  // #ifdef MP-WEIXIN
  // 平台例外：wx 句柄为微信运行时对象，未纳入项目 TS 类型（与 http.ts / useDishPage 同款说明）
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
   设计 Token 值以 theme/tokens.ts 为单一事实源。
   UI-03（spec §4.2）：颜色变量块由 scripts/gen-css-vars.ts 从 tokens.ts 的 CSS_VARS
   生成至 theme/generated-colors.css，此处 @import 引入（构建期由 vite/postcss 内联进 app.wxss）。
   **颜色块由 gen:tokens 生成，禁止手工编辑**；改色值只改 tokens.ts 后运行 npm run gen:tokens。
   本文件仅保留非颜色 token（圆角/间距/字号/高度/动效/层级）与 var() 派生引用（不含裸色值）。
   - 因 uni.scss 的 :root 在编译为小程序 WXSS 时被丢弃，真实声明须落在 App.vue；
   - 微信小程序 WXSS 不支持 :root 选择器，故必须以 page 承载；
   - 产品仅一种主体颜色、无深色模式切换，不再保留 .theme-dark 令牌块。 */
@import './theme/generated-colors.css';

page {
  /* ========== 派生颜色引用（var 组合，非色值真源；真源见 tokens.ts） ========== */
  /* 提示/占位文字（MP-004 补齐悬空定义）：与全站 placeholder 语言同源，取三阶末档 */
  --text-hint: var(--text-tertiary);

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
  /* 间距（4pt 基准栅格；2xs=半格，供星标/徽标等紧凑布局，避免裸 4rpx） */
  --spacing-2xs: 4rpx;
  --spacing-xs: 8rpx;
  --spacing-sm: 16rpx;
  --spacing-md: 24rpx;
  --spacing-lg: 32rpx;
  --spacing-xl: 48rpx;
  /* QA-01 修复：补齐悬空 token（空态上留白 padding 引用，缺失致声明被丢弃、空态贴顶） */
  --spacing-2xl: 64rpx;
  /* 字体（尺寸梯度） */
  --font-tiny: 20rpx;
  --font-aux: 22rpx;
  --font-small: 24rpx;
  --font-body: 28rpx;
  --font-caption: 30rpx;
  /* 字号标度（单调升序、同值无别名；32rpx=--font-subtitle，44rpx=--font-title） */
  --font-subtitle: 32rpx;
  --font-h3: 36rpx;
  --font-h2: 40rpx;
  --font-title: 44rpx;
  /* 图标尺寸 */
  --icon-2xl: 64rpx;
  --icon-3xl: 80rpx;
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
  /* 层级标度：统一浮层 z-index，数值越大越靠上，避免互相遮挡 / 点击穿透。
     两段式：页面骨架层（50~999，内容之上、弹层之下）→ 弹层级（2000+）。
     骨架层相对关系保持既有值收编，仅消灭裸值，不改变任何层叠行为。 */
  --z-action-bar: 50;      /* 页面底部固定操作栏（dish action-bar / profile submit-bar 等同语义底栏） */
  --z-detail-bar: 70;      /* 详情页空态/加载承接条（no-dish-bar，固定顶部） */
  --z-detail-nav: 80;      /* 详情页覆盖导航（dish-nav，固定顶部） */
  --z-filter-dropdown: 90; /* 首页/搜索页筛选下拉（FilterBar 食堂下拉遮罩与价格弹层根） */
  --z-tabbar: 100;         /* 自绘底部菜单栏 */
  --z-header: 100;         /* 全站吸顶顶栏（AppHeader） */
  --z-sheet: 2000;        /* 底部半屏弹层（BaseSheet 系列：ListPickerSheet 等选择器，走 BaseSheet 默认 z-token） */
  --z-actionsheet: 4000;  /* 操作菜单/写评价表单弹层（BaseSheet 系列：ActionSheet / ReviewComposer，zToken=--z-actionsheet） */
  --z-modal: 5000;        /* 居中弹窗（ReportModal） */
  --z-auth: 6000;         /* 登录网关，最高层级 */
}

/* 全局盒模型重置：防止 padding 叠加到 width 造成 scroll-view 内卡片溢出屏幕右侧 */
page, view, scroll-view, text, image { box-sizing: border-box; }

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

/* ========== 按压反馈（client-ui-motion 拍板：仅 opacity / bg-soft，禁 transform scale） ==========
   hover-class="pressed" 的全局兜底反馈（TabBar / FilterBar / 反馈表单等引用，UX-004 空引用修复）；
   取值 0.7 对齐既有按压 opacity 语言（DishInfoCard .correct-row.pressed）。
   页面可再以局部 `.xxx.pressed` 覆盖为 bg-soft 底色语言（scoped 选择器特异性更高）。 */
.pressed { opacity: 0.7; }

/* 注：装饰性入场动效（原 .enter-up / enterFade）已于 client-mvp-strip-entrance-anim 剥离，
   MVP 阶段内容一律静态直接呈现，可见性不依赖动画。 */

/* 减少动态效果媒体查询已移除：全站动效已于 client-ui-motion-removal-tokens-consolidation 剥离，无需降级。 */

/* ========== 交互状态工具类（client-ui-comprehensive-upgrade 1.2/1.4/1.6/2.2/4.1） ==========
   统一禁用态与键盘焦点、hover，避免各组件散落重复实现。
   （.glass / .is-loading / .has-error 死工具类已于 UI-03 清理删除。） */
/* 禁用态：弱化 + 禁点 */
.is-disabled { opacity: 0.5; pointer-events: none; filter: grayscale(0.2); }
/* 键盘焦点环（Apple 焦点规范：仅键盘可达时显示，触屏/鼠标不显）。
   fix：文本输入类（input / textarea）聚焦时 SHALL NOT 呈现主色描边——移动端/小程序点击输入框
   即命中 :focus-visible，主色（暖砖红）描边会被读成「红色边框」。输入态由各输入容器自身样式表达
   （如评论栏 .comment-input-box.focused），不复用全局 outline。 */
:focus-visible:not(input):not(textarea) {
  outline: 3rpx solid var(--color-primary);
  outline-offset: 2rpx;
  border-radius: var(--radius-xs);
}
input:focus,
textarea:focus,
input:focus-visible,
textarea:focus-visible {
  outline: none;
}
/* .hoverable 缩放已移除（client-ui-motion-removal-tokens-consolidation）：MVP 仅保留 :active opacity 反馈。 */
/* 宽屏适配（4.1）：主滚动区在宽屏居中限宽，避免内容被无限拉伸（仅 H5/桌面生效；
   原 .app-container 死工具类已于 UI-03 删除） */
@media (min-width: 768px) {
  .scroll-wrap { max-width: 720px; margin: 0 auto; }
}
</style>
