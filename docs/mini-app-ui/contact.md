# 联系开发者 (contact)

> ⚠️ 此页路由 pages-detail/contact 已在代码合并中删除（contact 仅复用 FeedbackForm，无独立内容），本文件为遗留文档。实际反馈入口为 feedback 页。

- 路由: /pages/pages-detail/contact（已删除）
- 分包: pages-detail 分包（实际 src 路径 pages/pages-detail/contact.vue）
- 源文件: src/pages/pages-detail/contact.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
联系开发者：复用 `FeedbackForm` 组件的反馈表单页（类型/内容/联系方式 + 提交），作为「意见反馈」的开发者联系入口。

## 2. 布局结构
- 顶部：`Header title="联系开发者" showBack`（含状态栏占位）。
- 主体：`<FeedbackForm />`（居中表单，min-height:100vh + `--bg-page`）。
- 无独立滚动区/底栏（FeedbackForm 内部自管理提交栏）。

## 3. 核心组件与用法
- `FeedbackForm`：反馈表单组件（内部含 `SectionTitle` 三处：反馈类型/内容/联系方式，及 AppButton 提交；可见 label、字数计数、校对见 feedback.md）。
- `Header`：自定义导航栏。
- `IconSvg`：由 FeedbackForm 内部使用（close/plus 等注册 key），本页不直接引用。

## 4. 设计 Token 使用
- 背景：`--bg-page`（页面根）。
- （其余 token 由 `FeedbackForm` 内部使用，参见 feedback.md / FeedbackForm.vue。）
- 布局：`.page { min-height:100vh; background: var(--bg-page) }`（dvh 等价：小程序用 100vh + Header 占位）。

## 5. 交互与动效
- 全部交互委托 `FeedbackForm`：类型选择、内容输入、联系方式、提交（登录校验 + submitFeedback + toast + navigateBack）。
- 按压/动效由 FeedbackForm 内部组件（AppButton/type-chip）实现 `--press-scale`（即时反馈）。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ FeedbackForm 内部图标均 IconSvg（close/plus 等注册 key）；无 emoji。
- ②金额 api 层：✅ 无金额。
- ③WaterfallList 禁 slot：➖ 无列表，不适用。
- ④三态齐备：➖ 表单页（FeedbackForm 内部提交 loading），无独立空态需求，合规。
- ⑤Sheet 规范：➖ 无底部 Sheet，不适用。
- ⑥按压 0.97：✅ FeedbackForm 内部 chip/AppButton 用 `--press-scale`。
- ⑦颜色 token：✅ 无裸 hex（FeedbackForm 内部使用语义 token）。
- ⑧SectionTitle：✅ FeedbackForm 内部三处标题走 `SectionTitle`（accent 条，已整改非手写）。
- ⑨底部避让：⚠️ 本页 `.page` 仅 `min-height:100vh`，未显式处理 iPhone 安全区/底部栏；因 FeedbackForm 自带提交栏且页面非 tab 页，实际由 FeedbackForm 内部 `.submit-bar` 用 `--action-bar-height` + 安全区。本页壳无额外底栏，合规。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：是否与「意见反馈」(feedback) 合并为同一页，减少重复入口
- 计划调整：是的采用你的建议

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | 由 FeedbackForm 内部 chip/提交按钮 ≥44pt；本页壳无独立元素 |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | FeedbackForm 内部 chip/AppButton 用 `--press-scale`（即时 `--press-transition`） |
| 3 | 固定栏/导航预留安全区 | 合规 | Header 含状态栏；提交栏安全区由 FeedbackForm 内部 `--action-bar-height`+`env(safe-area-inset-bottom)` 处理 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | FeedbackForm 内 close/plus 等注册 key；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 由 FeedbackForm 内部 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | 由 FeedbackForm 内部 token；未单独暗色实测（见 feedback.md §8⑥） |
| 7 | 不靠颜色 alone 传意 | 合规 | 由 FeedbackForm：类型 chip 底色+文字；字数/校验有文字 |
| 8 | prefers-reduced-motion 处理 | 合规 | 由 FeedbackForm 内部无位移动画；无 scale(0) |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | FeedbackForm 内部 press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | 无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)/弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | 无 keyframes 重播 |
| 15 | 数字 tabular-nums | 部分 | 由 FeedbackForm 字数计数建议 `tabular-nums` |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 部分 | 由 FeedbackForm 内部 `--font-body`(28rpx) 边界建议复核 ≥32rpx |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 唯一主 CTA「提交反馈」；无破坏性 |
| 18 | loading/empty/error 三态 | 部分 | 提交 loading 即状态；表单无空态（合理）；字数/登录校验即错误态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | 部分 | 由 FeedbackForm 内部 SectionTitle 作 label；建议标题即 label 语义（见 feedback.md §8⑲） |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 表单页无底部导航 |
| 21 | 一致性打磨 | 合规 | 与 feedback 页同源；按压统一 0.97 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：FeedbackForm 三区块不溢出（由 FeedbackForm 验证）。
- [ ] reduced-motion：无位移动画。
- [ ] 动态字号：表单文字不截断。
- [ ] 暗色对比：chip 选中/提交按钮文字对比达标（由 FeedbackForm 验证）。
- [ ] 44pt：chip/提交按钮 ≥44pt（由 FeedbackForm 验证）。
- [ ] 安全区：Header 不被刘海遮挡；提交栏不被 home indicator 遮挡（由 FeedbackForm 验证）。
