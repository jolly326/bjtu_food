# 档口详情 (stall)
- 路由: /pages/pages-detail/stall
- 分包: pages-detail 分包（实际 src 路径 pages/pages-detail/stall.vue）
- 源文件: src/pages/pages-detail/stall.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
档口详情：图集 + 档口信息（位置/星级/简介）合并卡 + 全部菜品（>6 折叠展开）+ 用户评价预览（前 3 条）；弱化「反馈信息有误」入口（ApplySheet）。由 dishStore.navParams(stallName/canteen) 定位。

## 2. 布局结构
- 顶部：`Header :title="stallDetail?.name||档口" showBack`（含状态栏占位）。
- 滚动区 `scroll-view`（refresher）。
- 三态：
  1. 加载中：`.stall-skeleton`（swiper/info/3×dish 骨架，shimmer）。
  2. 正常：`ImageSwiper` → `CardSection` 档口信息(SectionTitle+位置+星级+简介，extra 弱化反馈链接) → `CardSection` 全部菜品(SectionTitle(数量) + `StallDishRow` 列表，>6 折叠遮罩+展开) / `EmptyState` → `CardSection` 用户评价(SectionTitle(总数)+前 3 ReviewItem+查看全部)。
  3. 失败：`EmptyState text="档口信息加载失败" :retry`。
- 弱化「反馈信息有误」入口：`apply-link`（底部小文字链接，opacity 弱化）。
- `ApplySheet`（entity-type STALL）。

## 3. 核心组件与用法
- `ImageSwiper`：档口图集（图破兜底，中性语境建议 `empty`，见 §8④）。
- `StallDishRow`：菜品行（点击 → dish 详情，按压 `--press-scale`）。
- `ReviewItem`：评价项（前 3）。
- `SectionTitle` / `CardSection`：分区标题与卡片（accent 条）。
- `EmptyState`：无菜品 / 失败重试。
- `ApplySheet`：反馈/申请关闭弹层（spring 0.8/0.3 + ic-close + reduced-motion）。
- `IconSvg`：`location`(位置)、`star-filled`(评分)、`arrow`(查看全部/反馈链接)——均注册 key。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--text-primary/secondary/tertiary`。
- 强调：`--color-primary`(位置图标/查看全部链接/展开)、`--color-star`(评分星)、`--text-tertiary`(弱化反馈链接)。
- 圆角/阴影：`--radius-card`、`--shadow-card`。
- 间距：`--spacing-md/sm`（4/8pt 节奏）。
- 字号：`--font-h3`(档口名)、`--font-caption`(位置/简介/星级)、`--font-aux`(反馈链接/查看全部)、`--font-body`(评分值)。
- 动效：菜品行按下 `StallDishRow` 内部 `--press-scale`；骨架 shimmer；折叠遮罩 `-webkit-mask-image` 渐隐（opacity 渐变，非 layout 动画）。
- 布局：scroll-wrap `padding: var(--spacing-md) 0 0`（CardSection 自带左右 margin，避免 scroll-view 内边距不稳溢出）。

## 5. 交互与动效
- 全部菜品 >6 默认折叠（`max-height` 截断 + 渐隐遮罩），点击「查看全部菜品（N）」展开。
- 评价「查看全部」→ review-list?stallId。
- 弱化反馈链接 `openApply`(requireAuth) → ApplySheet。
- 下拉刷新；骨架 shimmer（reduced-motion 关动画）。
- ApplySheet：spring 0.8/0.3 + ic-close + 下拉关闭手势 + reduced-motion 交叉淡入。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：⚠️ location/star-filled/arrow 合规；ImageSwiper 内部图破兜底（中性语境应 `empty`，以组件实现为准，建议整改见 §8④）；无 emoji。
- ②金额 api 层：✅ 菜品行价格由 StallDishRow 展示已转元，页面无 `/100`。
- ③WaterfallList 禁 slot：➖ 用 StallDishRow 列表（非瀑布流），不适用。
- ④三态齐备：✅ 骨架 / EmptyState(retry, 含无菜品) / 正常态。
- ⑤Sheet 规范：✅ ApplySheet 套通用弹层（spring 0.8/0.3 + ic-close + 下拉关闭 + reduced-motion）。
- ⑥按压 0.97：✅ 菜品行内部 `scale(var(--press-scale))`；反馈链接用 opacity 弱化（合规）。
- ⑦颜色 token：✅ 无裸 hex。
- ⑧SectionTitle：✅ 档口信息/全部菜品/用户评价均 SectionTitle（noMargin 适配卡片内边距）。
- ⑨底部避让：➖ 无底部固定操作栏，scroll-wrap 无 tabbar 需求，合规。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：全部菜品折叠阈值（当前>6 折叠）是否合适
- 计划调整：我认为可以采用美团那种，评论和菜品分成两个水平方向的menubar进行切换，这样也可以少一个全部评价的页面跳转

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | 菜品行/查看全部/反馈链接/展开 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | 菜品行内部 `scale(var(--press-scale))` + `--press-transition` |
| 3 | 固定栏/导航预留安全区 | 合规 | 无固定底栏；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 待整改 | ImageSwiper 图破兜底按一致性规则①应 `empty`（组件层，页级建议标注）；location/star-filled/arrow 合规 |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；弱化反馈链接 `--text-tertiary` 对比建议验证（见 §8⑯） |
| 7 | 不靠颜色 alone 传意 | 合规 | 菜品行/链接有图标+文字；评分 star 图标+数字 |
| 8 | prefers-reduced-motion 处理 | 合规 | ApplySheet 降级交叉淡入；骨架 shimmer 关动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | Sheet enter~300ms、exit 短；press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | Sheet `--ease-drawer`/`--ease-out`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | Sheet 自底部；无 scale(0) |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 骨架 shimmer/遮罩 opacity；无 `transition:all` |
| 14 | 可中断动效 | 合规 | ApplySheet 手势可中断 |
| 15 | 数字 tabular-nums | 部分 | 评分值/菜品价格/评价数建议 `tabular-nums` |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 部分 | 弱化链接 `--font-aux`(22rpx) 仅辅助文字；主文 ≥32rpx；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 反馈入口弱化为小文字链接（非大板块，符合信息架构） |
| 18 | loading/empty/error 三态 | 合规 | 骨架 / EmptyState(retry) / 正常态；菜品区空有 EmptyState |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 非 tab 页；返回由 Header |
| 21 | 一致性打磨 | 合规 | 信息架构合并卡；按压统一 0.97；Sheet 规范同源；折叠遮罩用 mask 渐隐（非 layout 动画） |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：图集/菜品行不溢出；折叠遮罩完整。
- [ ] reduced-motion：ApplySheet 交叉淡入；骨架 shimmer 停。
- [ ] 动态字号：档口名/简介/菜品名不截断。
- [ ] 暗色对比：弱化反馈链接 `--text-tertiary` 对比（建议改 `--text-secondary`）；菜品行文字达标。
- [ ] 44pt：菜品行/查看全部/反馈链接 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡。
