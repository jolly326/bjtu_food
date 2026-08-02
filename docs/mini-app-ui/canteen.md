# 食堂详情 (canteen)
- 路由: /pages/pages-detail/canteen
- 分包: pages-detail 分包（实际 src 路径 pages/pages-detail/canteen.vue）
- 源文件: src/pages/pages-detail/canteen.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
食堂详情：图集 + 食堂信息（位置/简介，无评分）合并卡 + 档口列表（单列 WaterfallList）+ 用户评价预览（前 3 条），并提供弱化的「反馈信息有误」入口（ApplySheet）。

## 2. 布局结构
- 顶部：`Header :title="canteenName||食堂详情" showBack`（含状态栏占位）。
- 滚动区 `scroll-view`（refresher）。
- 三态：
  1. 加载中：`.canteen-skeleton`（swiper/info/3×stall 骨架，shimmer）。
  2. 正常：`ImageSwiper` → `CardSection` 食堂信息(SectionTitle+位置+简介，extra 弱化反馈链接) → `CardSection` 档口列表(SectionTitle(数量) + `WaterfallList single type="stall"`) / `EmptyState`(无档口可重试) → `CardSection` 用户评价(SectionTitle(总数) + 前 3 `ReviewItem` + 「查看全部评价」)。
  3. 失败：`EmptyState text="食堂信息加载失败" :retry`。
- 弱化「反馈信息有误」入口：`apply-link`（底部小文字链接，opacity 弱化，非大板块）。
- `ApplySheet`（entity-type CANTEEN，反馈/申请调整）。

## 3. 核心组件与用法
- `ImageSwiper`：食堂图集轮播（图破兜底，中性语境建议 `empty`，见 §8④）。
- `WaterfallList :list single type="stall" @stall-click`：档口单列卡（内部 StallCardSingle，禁 slot）。
- `ReviewItem`：评价项（前 3 条）。
- `SectionTitle` / `CardSection`：分区标题与卡片（accent 条）。
- `EmptyState`：无档口 / 失败重试。
- `ApplySheet`：反馈信息有误弹层（spring 0.8/0.3 + ic-close + reduced-motion）。
- `IconSvg`：`location`(位置，注册 key)、`arrow`(查看全部/反馈链接，注册 key)、`empty`/`star` 未直接用（ImageSwiper 内部兜底）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--text-primary/secondary/tertiary`。
- 强调：`--color-primary`(位置图标/查看全部链接)、`--text-tertiary`(弱化反馈链接)。
- 圆角/阴影：`--radius-card`、`--shadow-card`。
- 间距：`--spacing-md/sm`（4/8pt 节奏）。
- 字号：`--font-h3`(食堂名)、`--font-caption`(位置/简介)、`--font-aux`(反馈链接/查看全部)。
- 动效：档口卡按下由 WaterfallList/StallCardSingle 内部 `--press-scale`；骨架 shimmer 动画。
- 布局：scroll-wrap `padding: var(--spacing-md) 0 0`（CardSection 自带左右 margin，避免 scroll-view 内边距不稳溢出）。

## 5. 交互与动效
- 档口卡点击 `goToStall`（经 dishStore.navParams 传 stallName/canteen）→ stall 详情。
- 评价「查看全部」→ review-list?canteenId。
- 弱化反馈链接 `openApply`（requireAuth）→ ApplySheet。
- 下拉刷新；骨架屏 shimmer（仅 opacity/transform，非 transition:all）。
- ApplySheet：spring 0.8/0.3（抽屉曲线）+ ic-close + 下拉关闭手势（仅向下、阈值~120px）+ reduced-motion 交叉淡入（去 transform，保留 opacity）。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：⚠️ location/arrow 合规；ImageSwiper 内部图破兜底（中性语境应 `empty`，以组件实现为准，建议整改见 §8④）；无 emoji。
- ②金额 api 层：✅ 食堂/档口无金额裸算（档口卡人均由组件展示已转元）。
- ③WaterfallList 禁 slot：✅ 仅 `:list single type="stall"` + `@stall-click`，未传 slot。
- ④三态齐备：✅ 骨架 / EmptyState(retry, 含无档口可重试) / 正常态。
- ⑤Sheet 规范：✅ ApplySheet 套通用弹层（spring 0.8/0.3 + ic-close + 下拉关闭 + reduced-motion）。
- ⑥按压 0.97：✅ 档口卡内部 `scale(var(--press-scale))`；反馈链接用 opacity 弱化（非按压强调，合规）。
- ⑦颜色 token：✅ 无裸 hex。
- ⑧SectionTitle：✅ 食堂信息/档口列表/用户评价均 SectionTitle（`noMargin` 适配卡片内边距）。
- ⑨底部避让：➖ 无底部固定栏（无操作栏），scroll-wrap 无 tabbar 需求，合规。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：食堂信息是否补评分/人均（当前已移除评分，仅档口卡含 avgRating）
- 计划调整：是的可以补充

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | 档口卡/查看全部/反馈链接 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | 档口卡内部 `scale(var(--press-scale))` + `--press-transition` |
| 3 | 固定栏/导航预留安全区 | 合规 | 无固定底栏；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 待整改 | ImageSwiper 图破兜底按一致性规则①应 `empty`，若组件用 `home`/`dish` 须改（属组件层，页级建议标注）；location/arrow 合规 |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；弱化反馈链接 `--text-tertiary` 对比建议验证（见 §8⑯） |
| 7 | 不靠颜色 alone 传意 | 合规 | 档口卡/链接有图标+文字；无纯色状态 |
| 8 | prefers-reduced-motion 处理 | 合规 | ApplySheet 降级交叉淡入；骨架 shimmer 在 reduced-motion 关动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | Sheet enter~300ms、exit 更短；press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | Sheet `--ease-drawer`/`--ease-out`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | Sheet 自底部；无 scale(0) |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 骨架 shimmer 用 opacity/transform；无 `transition:all` |
| 14 | 可中断动效 | 合规 | ApplySheet 手势可中断；transition 可重定向 |
| 15 | 数字 tabular-nums | 部分 | 档口卡人均/评价数建议 `tabular-nums` |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 部分 | 弱化链接 `--font-aux`(22rpx) 低于 32rpx 建议值，建议复核或仅作辅助文字；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 反馈入口弱化为小文字链接（非破坏性传播，非大板块，符合信息架构） |
| 18 | loading/empty/error 三态 | 合规 | 骨架 / EmptyState(retry) / 正常态；评价区空有 EmptyState |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 非 tab 页；返回由 Header |
| 21 | 一致性打磨 | 合规 | 信息架构合规（合并卡、弱化反馈）；按压统一 0.97；Sheet 规范同源 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：图集/档口单列卡不溢出；查看全部不被遮挡。
- [ ] reduced-motion：ApplySheet 交叉淡入；骨架 shimmer 停。
- [ ] 动态字号：食堂名/简介不截断。
- [ ] 暗色对比：弱化反馈链接 `--text-tertiary` 对比（建议改 `--text-secondary` 或仅辅助）；档口卡文字达标。
- [ ] 44pt：档口卡/查看全部/反馈链接 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡。
