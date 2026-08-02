# 动态 (community)
- 路由: /pages/community/index
- 分包: 主包
- 源文件: src/pages/community/index.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
校园美食动态流（最新单流，无分段 Tab）。展示 `MomentCard` 列表、触底加载，右下悬浮发布 FAB 直达发布页。

## 2. 布局结构
- 顶部：`Header title="动态"`（含状态栏占位）。
- 滚动区 `scroll-view`（refresher + 触底 `@scrolltolower`）。
- 三态：
  1. 加载中：`skeleton-list`（3 张 `.sk-card` 骨架 shimmer）。
  2. 空态：`EmptyState text="还没有动态，快去发布第一条吧" icon="comment"` + action「发布第一条动态」(plus)。
  3. 正常：`moment-list`（纵向 `MomentCard`，`:key=m.id` 稳定 id）。
- 触底 footer（加载中 spinner / 已经到底）。
- 悬浮发布：`fab`（fixed 右下，按下 `scale(0.97)`），避让 TabBar + 安全区。
- 底部：`CustomTabBar`。

## 3. 核心组件与用法
- `MomentCard`：动态卡片（头像兜底 user、关联对象跳转、审核态等），`@tap`/`@go-related` 上抛。
- `EmptyState`：空态 + 发布 action（图标+文字）。
- `IconSvg`：`plus`(FAB，注册 key)、`empty` 未直接用（MomentCard 内部用 user 兜底）。
- `CustomTabBar`：主包 tab。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--text-tertiary`、`--text-secondary`、`--text-primary`。
- 强调：`--color-primary`(FAB 底)、`--text-white`(FAB 图标)。
- 阴影：`--shadow-bar-primary`(FAB)。
- 间距：`--spacing-md/lg/xs`（4/8pt 节奏）。
- 字号：`--font-aux`(footer)。
- 动效：`--press-scale`(`.fab.pressed`)。
- 布局：`--tabbar-height` + `env(safe-area-inset-bottom)`（FAB bottom 避让 tabbar + 安全区）；scroll-wrap 同。

## 5. 交互与动效
- 单流：始终按 latest 倒序拉取（无推荐 Tab，已决议）。
- 触底无限加载；下拉刷新。
- FAB 点击 `goPublish` → publish-moment；按下 `scale(0.97)`（即时 `--press-transition`）。
- 卡片点击进 moment 详情；关联对象按 relatedType 跳 dish/stall。
- `prefers-reduced-motion`：footer-spinner 关动画。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ plus 走 IconSvg；MomentCard 内部 user 等亦 IconSvg；无 emoji。
- ②金额 api 层：✅ 无金额。
- ③WaterfallList 禁 slot：➖ 本页无瀑布流（用 MomentCard 列表），不适用。
- ④三态齐备：✅ 骨架 / EmptyState(action) / 正常态；触底 footer 完整。
- ⑤Sheet 规范：➖ 本页无底部 Sheet（FAB 为导航按钮），不适用。
- ⑥按压 0.97：✅ `.fab.pressed scale(var(--press-scale))`；MomentCard 内部同规范。
- ⑦颜色 token：✅ 无裸 hex。
- ⑧SectionTitle：➖ 本页为单流列表，无分区标题（列表无需 accent 条）；合规。
- ⑨底部避让：✅ FAB 与 scroll-wrap 均避让 tabbar + 安全区。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：暗色模式对比度待实测验证(§8⑥)；若后续加计数建议数字用 tabular-nums 防位移(§8⑮)
- 计划调整：

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | FAB 直径 ≥44pt；卡片点击区 ≥44pt；列表间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | `.fab.pressed scale(var(--press-scale))` + `--press-transition`≈120ms |
| 3 | 固定栏/导航预留安全区 | 合规 | FAB 避让 TabBar + `env(safe-area-inset-bottom)`；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | plus 注册 key；MomentCard 内 user 等注册 key；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；未单独暗色实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 空态图标+文字；FAB 有图标+语义 |
| 8 | prefers-reduced-motion 处理 | 合规 | footer-spinner 关动画；FAB 无位移动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch，无裸 hover |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | FAB press 120ms；无高频动画 |
| 11 | 自定义缓动，禁 ease-in | 合规 | 无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)；卡片进出无弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | 无 keyframes 重播；FAB 用 transition |
| 15 | 数字 tabular-nums | 部分 | 无独立计数；若后续加计数建议 tabular-nums |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 字号 token；4/8pt 间距；无横向滚动 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | FAB 为唯一主 CTA（发布）；无破坏性操作 |
| 18 | loading/empty/error 三态 | 合规 | 骨架 / EmptyState(action) / 正常态 + footer |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | 合规 | CustomTabBar ≤5，当前项高亮 |
| 21 | 一致性打磨 | 合规 | FAB/卡片按压统一 0.97；空态图标语义一致 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：动态卡片不溢出；FAB 不与 TabBar 重叠。
- [ ] reduced-motion：footer spinner 停转。
- [ ] 动态字号：卡片正文/时间不截断。
- [ ] 暗色对比：FAB `--color-primary` 底 + 白图标 ≥3:1；空态文字 ≥4.5:1。
- [ ] 44pt：FAB / 卡片点击区 ≥44pt。
- [ ] 安全区：FAB 与 TabBar 不被 home indicator 遮挡。
