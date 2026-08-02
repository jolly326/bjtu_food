# 意见反馈 (feedback)
- 路由: /pages/feedback/index
- 分包: 主包
- 源文件: src/pages/feedback/index.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
提交意见反馈：选择类型（功能建议/内容纠错/其他）+ 内容（≤1000）+ 选填联系方式，底部提交；登录后提交至后端。

## 2. 布局结构
- 顶部：`Header title="意见反馈" showBack`（含状态栏占位）。
- 滚动区 `scroll-view`。
- 三个 `CardSection`（title 由 CardSection 渲染，内部含 accent 风格标题）：
  1. `CardSection title="反馈类型"`：类型 chip 行（suggestion/error/other，选中高亮）。
  2. `CardSection title="反馈内容"`：`textarea`（maxlength 1000, auto-height）+ 字数计数。
  3. `CardSection title="联系方式（选填）"`：`input` 邮箱/微信。
- 底部提交栏 `.submit-bar`（fixed）：`AppButton text="提交反馈"`（loading 态，单主 CTA）。

## 3. 核心组件与用法
- `CardSection`：三个分区卡片（`title` 属性渲染带 accent 条标题）。
- `AppButton`：底部提交主按钮（disabled/loading 态清晰）。
- `IconSvg`：本页未直接使用（无图标区块；chip 文字为主）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--bg-soft`、`--text-primary/secondary/tertiary`。
- 强调：`--color-primary`(选中 chip 文字/底)、`--color-primary-soft`(选中 chip 底)。
- 圆角/阴影：`--radius-card`、`--radius-tag`、`--shadow-card`、`--shadow-bar-soft`(submit-bar)、`--radius-btn`。
- 间距：`--spacing-md/sm/xs/xl`（4/8pt 节奏）。
- 字号：`--font-body`、`--font-aux`。
- 动效：`--press-scale`(`.type-chip:active`)、`--press-transition`。
- 布局：`--action-bar-height` + `env(safe-area-inset-bottom)`（scroll-wrap 避让）；submit-bar 同安全区。

## 5. 交互与动效
- 类型 chip 单选切换（底色+文字高亮，图标+语义可选）；内容超 1000 禁提交（字数计数即时反馈）。
- 提交校验登录 → `submitFeedback` → toast「提交成功」→ navigateBack。
- 底部 AppButton 自带按压；chip 按下 scale 0.97。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ 本页无图标需求；无 emoji。
- ②金额 api 层：✅ 无金额。
- ③WaterfallList 禁 slot：➖ 无列表，不适用。
- ④三态齐备：⚠️ 表单页，无空态需求；提交 loading 即加载态。可接受。
- ⑤Sheet 规范：➖ 无底部 Sheet，不适用。
- ⑥按压 0.97：✅ `.type-chip:active scale(var(--press-scale))`；AppButton 内部同。
- ⑦颜色 token：✅ 无裸 hex（chip 选中用 `--color-primary`/`--color-primary-soft`）。
- ⑧SectionTitle：✅ 分区标题走 `CardSection title=`（内部渲染 accent 条标题，等价于 SectionTitle）；原手写 `.section-title` 已整改。
- ⑨底部避让：✅ 提交栏与 scroll-wrap 用 `--action-bar-height` + 安全区。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：暗色模式对比度待实测(§8⑥)；字数计数(如 120/1000)与 textarea 字号边界建议 tabular-nums 与 ≥32rpx 复核(§8⑮⑯)；textarea/input 缺独立可见 label，建议让区块标题承担 label 语义(§8⑲)
- 计划调整：采纳你的建议

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | chip/提交按钮 ≥44pt；卡片间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | `.type-chip:active scale(var(--press-scale))`；AppButton 内部 0.97 |
| 3 | 固定栏/导航预留安全区 | 合规 | submit-bar `--action-bar-height` + `env(safe-area-inset-bottom)`；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | 无 emoji；无图标需求 |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；未单独暗色实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 选中 chip 有底色+文字；字数达限有文字提示 |
| 8 | prefers-reduced-motion 处理 | 合规 | 无位移动画；chip 按压即时缩放 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | press 120ms；无高频动画 |
| 11 | 自定义缓动，禁 ease-in | 合规 | 无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)/弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | 无 keyframes 重播 |
| 15 | 数字 tabular-nums | 部分 | 字数计数（如 120/1000）建议 `tabular-nums` 防位移 |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | textarea `--font-body`(28rpx) 边界建议复核 ≥32rpx；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 唯一主 CTA「提交反馈」；无破坏性 |
| 18 | loading/empty/error 三态 | 部分 | 提交 loading 即加载态；表单页无空态（合理）；字数/登录校验即错误态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | 部分 | 分区标题走 CardSection（可见）；textarea/input 无独立可见 label，靠区块标题+placeholder；建议标题即 label 语义 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 表单页无底部导航 |
| 21 | 一致性打磨 | 合规 | 按压统一 0.97；提交栏材质同源 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：三区块不溢出；chip 行不挤。
- [ ] reduced-motion：无位移动画。
- [ ] 动态字号：textarea/标题不截断。
- [ ] 暗色对比：chip 选中 `--color-primary` 底 + 白字 ≥4.5:1。
- [ ] 44pt：chip / 提交按钮 ≥44pt。
- [ ] 安全区：submit-bar 不被 home indicator 遮挡。
