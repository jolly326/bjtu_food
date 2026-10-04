<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";
import { useUserStore } from "@/stores/user";
import { WX_CLOUD_ENV } from "@/api/config";
import { getWxApi } from "@/utils/device";
onLaunch(() => {
  // 初始化微信云开发/云托管环境（小程序端 callContainer 调用依赖；H5 等平台跳过）
  // #ifdef MP-WEIXIN
  // 平台句柄统一经 utils/device 取（本文件不再直接触碰全局 wx）
  const wxApi = getWxApi();
  if (wxApi && wxApi.cloud) {
    wxApi.cloud.init({ env: WX_CLOUD_ENV, traceUser: true });
  }
  // #endif
  // 微信自动静默登录：打开小程序即登录为游客态（bindEmail 为空）；有 token 则刷新资料
  // 失败（如后端不可达）仅打点，不阻断浏览与菜单栏渲染
  useUserStore().silentLogin().catch(() => {})
});
</script>
<style>
/* ========== 全局设计 Token（Apple Design 风格） ==========
   设计变量**全部**经 @import 引入，本文件不再承载任何变量声明（只留 page 底色、
   var() 派生引用与全局盒模型重置）。两处导入，均为**唯一真源**：
     · 颜色     → theme/generated-colors.css（手工同步 theme/tokens.ts 的 CSS_VARS）
     · 非颜色   → theme/design-tokens.css（圆角 / 间距 / 字号 / 字重 / 字距 / 动效 / 高度 / 层级）
   → 文档对照表：docs/ui/client/设计变量.md
   **改值只改上述两处真源**；生成脚本 `scripts/gen-css-vars.ts` 与 `npm run gen:tokens`
   不在本工作区，generated-colors.css 须**手工同步**（内容与 CSS_VARS 逐键一致）。
   - 因 uni.scss 的 :root 在编译为小程序 WXSS 时被丢弃，真实声明须由 page 承载
     （两个导入文件内部均以 page{…} 声明）；
   - 微信小程序 WXSS 不支持 :root 选择器；
   - 产品仅一种主体颜色、无深色模式切换，不再保留 .theme-dark 令牌块。 */
@import './theme/generated-colors.css';
@import './theme/design-tokens.css';

page {
  /* ===== 全站页面底色（**唯一承载处**）=====
     放在小程序最低层 `page{}`：它天然在所有内容与壁纸层**之下** ⇒ 既兜底防白屏，
     又不会像「页面根 `.page` 的 background」那样把 `z-index: var(--z-page-bg)`（−1）的壁纸层盖住。
     ⚠️ 页面级 / 组件级 SHALL NOT 再声明页面底色（UI 统一 Loop Round 11 根因修复）。 */
  background: var(--bg-page);

  /* ========== 派生颜色引用（var 组合，非色值真源；真源见 tokens.ts） ========== */
  /* 提示/占位文字（MP-004 补齐悬空定义）：与全站 placeholder 语言同源，取三阶末档 */
  --text-hint: var(--text-tertiary);

  /* 全站页底「纱」（`--page-wash`）：**色值已收口到颜色真源**（theme/generated-colors.css，
     源自 tokens.ts 的 COLOR_MAP['page-wash']）—— 本文件不再声明任何色值。
     语义与调参口径（整张壁纸统一压一层、α 是唯一旋钮、由 PageWallpaper 以 background-color 消费）
     见 tokens.ts 中 'page-wash' 的注释。 */

  /* 若未来某页必须让内容从横条背后穿过：
     口径为「壁纸纯底色 #FDEFDB × `--page-wash`（按当时的 α 合成）」。 */

  /* 圆角 / 间距 / 字号 / 字重 / 字距 / 动效 / 高度 / 层级 token 已**集中到**
     theme/design-tokens.css（2026-10-01 UI 规范统一：非颜色变量唯一真源，本文件仅 @import）。 */

}

/* 全局盒模型重置：防止 padding 叠加到 width 造成 scroll-view 内卡片溢出屏幕右侧 */
page, view, scroll-view, text, image { box-sizing: border-box; }

/* ========== 页面基础壳 ==========
   ⚠️ **页面根不得再有底色**：根层叠上下文中的绘制顺序为
   ① 负层级子层（`PageWallpaper` 的 `z-index: var(--z-page-bg)` = −1）→ ② 流内块背景。
   若 `.page`（页面根）自带不透明底色，它会在 ② 把 ① 的壁纸层**整块盖住** ⇒ 全站表现为「奶黄底、
   壁纸不可见」。故底色下沉到小程序最低层 `page{}`（见上方 token 块内的 `background`）——
   它天然在所有内容与壁纸之下，仍能兜底防白屏。 */
.page {
  /* 页面根兜底高度：**vh + dvh 双声明**（同一属性名，后者在支持 dvh 的环境生效）。
     ⚠️ 为什么必须写 dvh：移动端 H5 地址栏伸缩时 `100vh` = **最大可视高**，
     比真实可视区高 ⇒ 页面根比屏幕高出一截 ⇒ ① 页面自身多出一段可滚区（"多余滚动"）；
     ② 固定底栏被顶到屏幕外/底部露出空白。
     ⚠️ 为什么必须是 `min-height` 而不是 `height`：本类与各页根的 `height: 100vh; height: 100dvh`
     **共存** —— 若这里写死 `min-height: 100vh`，会把页根的 `100dvh` **顶回 100vh**（min 大于 height 时 min 获胜），
     使 dvh 修复静默失效（这正是修复前的状态）。 */
  min-height: 100vh;
  min-height: 100dvh;
}

/* ===== 主滚动区尺寸口径（全站唯一真源，Round 26 复核）=====
   ① `min-height: 0` **必需**：flex 子项默认 `min-height: auto`，不收缩 ⇒ 内容把滚动容器撑高 ⇒
      容器超出页根 ⇒ 页面与滚动区**双层滚动**（多余滚动 + 底部空白）。各页 `.scroll-wrap` 亦各自声明（双保险）。
   ② 底部留白**不再全局兜底**（仅自带自绘 TabBar 的页让出菜单栏）：
      只有自带**自绘 TabBar** 的页（home / mine）需要让出菜单栏，且由页面自身承担（home = 页根 `padding-bottom`、
      mine = 页脚 `padding-bottom`）。全局兜底会让**非 Tab 页**凭空多出 ≈ tabbar(50px) + 安全区(≈34px) 的死留白，
      短内容也被这层 padding 顶出滚动条（"空白滚动区域"根因之一）。 */
.scroll-wrap {
  min-height: 0;
}

/* ========== 按压反馈（仅 opacity / bg-soft，禁 transform scale） ==========
   hover-class="pressed" 的全局兜底反馈（TabBar / 反馈表单 / 列表行等引用，UX-004 空引用修复）；
   取值 0.7 对齐既有按压 opacity 语言（DishInfoCard .correct-link.pressed）。
   页面可再以局部 `.xxx.pressed` 覆盖为 bg-soft 底色语言（scoped 选择器特异性更高）。 */
.pressed { opacity: 0.7; }

/* 注：MVP 阶段内容一律静态直接呈现，可见性不依赖动画（装饰性入场动效不启用）。 */

/* 全站动效统一呈现，不提供「减少动态效果」降级分支。 */

/* ========== 交互状态工具类 ==========
   统一禁用态与键盘焦点、hover，避免各组件散落重复实现。 */
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
/* MVP 仅保留 :active opacity 反馈（无 .hoverable 缩放）。 */
/* 宽屏适配：主滚动区在宽屏居中限宽，避免内容被无限拉伸（仅 H5/桌面生效）。 */
@media (min-width: 768px) {
  .scroll-wrap { max-width: 720px; margin: 0 auto; }
}
</style>
