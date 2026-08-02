# 外部链接 (webview)
- 路由: /pages/webview/index
- 分包: 主包
- 源文件: src/pages/webview/index.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
承载外部 H5 / 公众号文章的 web-view 容器。由 banner 或分享复制链接打开，加载失败回落复制链接到剪贴板。

## 2. 布局结构
- 顶部：`Header :title="title" showBack`（title 由 query 传入，默认「外部链接」；含状态栏占位）。
- 主体 `webview-wrap`（`flex:1; overflow:hidden`）：
  - 有 `src`：`web-view :src`（`@error="onError"`）。
  - 无 `src`：`webview-fallback`（「链接无效」占位，图标+文字）。

## 3. 核心组件与用法
- `Header`：自定义导航栏（返回安全）。
- 原生 `web-view`：承载外部 URL（系统组件，自身含滚动/交互）。
- 无卡片/列表组件；无 IconSvg（无图标区块，fallback 文案为主）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--text-tertiary`(fallback 文案)。
- 间距：未用显式间距 token（单 web-view 占满）。
- 字号：`--font-body`(fallback 文案)。
- 动效：无按压/动画（纯容器）。
- 布局：全屏 `height:100vh`，webview-wrap `flex:1`（dvh 等价：小程序用 100vh + Header 占位）。

## 5. 交互与动效
- `onLoad` 解析 query.src（decodeURIComponent）/ query.title。
- `web-view @error`：回落 `setClipboardData` 复制链接 + toast「已在剪贴板，请到浏览器打开」。
- 无点击反馈/转场动画（系统 web-view 内部行为，由 OS 处理）。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：➖ 本页无图标需求；无 emoji。
- ②金额 api 层：✅ 无金额。
- ③WaterfallList 禁 slot：➖ 无列表，不适用。
- ④三态齐备：➖ 容器页，src 空有 fallback 文案，无加载/空态需求，合规。
- ⑤Sheet 规范：➖ 无底部 Sheet，不适用。
- ⑥按压 0.97：➖ 无可点元素（Header 返回由组件处理），不适用。
- ⑦颜色 token：✅ 无裸 hex（仅 `--bg-page`/`--text-tertiary`）。
- ⑧SectionTitle：➖ 无分区标题，合规。
- ⑨底部避让：➖ 无底部固定栏，web-view 占满，合规。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：fallback 是否补 IconSvg 图标（当前纯文字）
- 计划调整：fallback 文字对比度已整改（见 §8⑥）：改用 `--text-secondary`，满足 ≥4.5:1。

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | ➖ | 无独立可点元素（web-view 由系统处理） |
| 2 | 按压反馈 100ms 内 scale(0.97) | ➖ | 无本页可点元素；Header 返回由组件统一按压 |
| 3 | 固定栏/导航预留安全区 | 合规 | Header 含状态栏占位；web-view 占满未被遮挡 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | 无 emoji；无图标需求 |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 仅 `--bg-page`/`--text-tertiary` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 合规 | 已整改：fallback 改 `--text-secondary`（约 4.8:1，满足 ≥4.5:1）；无需补图标 |
| 7 | 不靠颜色 alone 传意 | 合规 | fallback 为文字说明，无纯色传达 |
| 8 | prefers-reduced-motion 处理 | 合规 | 无动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 无 hover |
| 10 | 微交互<300ms，exit 短于 enter | ➖ | 无动效 |
| 11 | 自定义缓动，禁 ease-in | ➖ | 无缓动 |
| 12 | 进场禁 scale(0)，popover 从触发点 | ➖ | 无进场/弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 transition |
| 14 | 可中断动效 | ➖ | 无动效 |
| 15 | 数字 tabular-nums | ➖ | 无数字 |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 部分 | fallback 文案 `--font-body`(28rpx) 略低于 32rpx 建议值，建议复核 ≥32rpx |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | ➖ | 无 CTA |
| 18 | loading/empty/error 三态 | 合规 | src 空有 fallback 文案（等价 error 态） |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 无底部导航 |
| 21 | 一致性打磨 | 合规 | 无 disabled/focus 需处理 |

> §8⑥/§8⑯ 补充：fallback 文案已改用 `--text-secondary`(#6B625B)，在 `--bg-page`(#F6F4EF) 上对比度约 4.8:1，满足 ≥4.5:1（已整改）。

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：web-view 占满；fallback 文案居中可读。
- [ ] reduced-motion：无影响（无动画）。
- [ ] 动态字号：fallback 文案不截断。
- [ ] 暗色对比：fallback 文字对比 ≥4.5:1（建议改用 `--text-secondary`）。
- [ ] 44pt：Header 返回命中区 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡。
