# 食堂详情 (canteen)
- 路由: /pages/pages-detail/canteen
- 分包: pages-detail 分包（实际 src 路径 pages/pages-detail/canteen.vue）
- 源文件: src/pages/pages-detail/canteen.vue
- 最后依据 skills 校对: 2026-08-02（布局重构：引入顶部共享区 + 美团式双 Tab 切换）

## 1. 页面定位
食堂详情：顶部共享区（图集 + 食堂信息合并卡）常驻，下方用**美团式分段 Tab** 在「档口」与「评价」两个水平区域间切换——档口列表与用户评价不再纵向堆叠在同一长滚动里，而是各自成为独立可滚动的内容面板，从而减少一次「查看全部评价」的页面跳转（评价预览即完整列表，前 3 条截断逻辑移除）。

弱化「反馈信息有误」入口（ApplySheet）仍置于共享信息卡内。

## 2. 布局结构
- 顶部：`Header :title="canteenName||食堂详情" showBack`（含状态栏占位，背景随图集做 translucency 处理，见 §4）。
- 共享区（图集 + 信息卡，**不随 Tab 滚动**，吸顶于 Header 之下）：
  - `ImageSwiper`（食堂图集轮播）。
  - `CardSection` 食堂信息（SectionTitle + 位置 + 简介，extra 弱化反馈链接）。
- 分段 Tab 栏 `SegmentTabs`（档口 / 评价，**sticky 吸顶**，位于共享区底部、内容面板之上）：
  - 档口：`WaterfallList single type="stall"`，或 `EmptyState text="暂无档口" :retry`。
  - 评价：`ReviewItem` 完整列表（不再截断前 3），空态 `EmptyState text="暂无评价"`；保留「写评价」弱化入口（可选，见 §7）。
- 三态（整体）：
  1. 加载中：`.canteen-skeleton`（swiper/info/tab/面板骨架，shimmer）。
  2. 正常：共享区 → SegmentTabs → 当前面板内容。
  3. 失败：`EmptyState text="食堂信息加载失败" :retry`（吸顶于共享区下，Tab 禁用）。
- 弱化「反馈信息有误」入口：`apply-link`（信息卡内小文字链接，opacity 弱化）。
- `ApplySheet`（entity-type CANTEEN）。

## 3. 核心组件与用法
- `ImageSwiper`：食堂图集轮播（图破兜底，中性语境建议 `empty`，见 §8④）。
- `SegmentTabs`：分段切换控件（档口/评价；下划线指示条 `transform` 平移；按压 `--press-scale`）。
- `WaterfallList :list single type="stall" @stall-click`：档口单列卡（内部 StallCardSingle，禁 slot）。
- `ReviewItem`：评价项（完整列表）。
- `SectionTitle` / `CardSection`：分区标题与卡片（accent 条）。
- `EmptyState`：无档口 / 无评价 / 失败重试。
- `ApplySheet`：反馈信息有误弹层（spring 0.8/0.3 + ic-close + reduced-motion）。
- `IconSvg`：`location`(位置)、`arrow`(反馈链接)、`empty`(图破兜底)；Tab 用文字 + 数量角标，不依赖图标表意（见 §8⑦）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--text-primary/secondary/tertiary`。
- 强调：`--color-primary`(位置图标/反馈链接/SegmentTabs 指示条)、`--text-tertiary`(弱化反馈链接)。
- 圆角/阴影：`--radius-card`、`--shadow-card`。
- 间距：`--spacing-md/sm`（4/8pt 节奏）。
- 字号：`--font-h3`(食堂名)、`--font-caption`(位置/简介)、`--font-aux`(反馈链接/Tab 角标)、`--font-body`(档口数量/评价数，建议 `tabular-nums`)。
- 动效：
  - SegmentTabs 指示条用 `transform: translateX()` + `--ease-out`（非 transition:all）；面板切换用 opacity 交叉淡入（reduced-motion 同）。
  - 档口卡按下 `scale(var(--press-scale))`；骨架 shimmer 仅 opacity/transform。
- 布局：
  - 页面采用 `flex` 纵向：`Header`(固定) → 共享区(固定，不滚动) → `SegmentTabs`(sticky) → 面板(`flex:1` 内部独立 `scroll-view`)。
  - 共享区与 SegmentTabs 共同构成吸顶块，面板滚动不带动上方；`scroll-wrap` 自身不再承担全局 padding（CardSection 自带左右 margin）。

## 5. 交互与动效
- 档口卡点击 `goToStall`（经 dishStore.navParams 传 stallName/canteen）→ stall 详情。
- SegmentTabs 切换：指示条 `translateX` 滑动（~200ms，`--ease-out`）；面板内容交叉淡入；切换保留各面板滚动位置（双 scroll-view 并存，v-show 控制显隐）。
- 弱化反馈链接 `openApply`(requireAuth) → ApplySheet。
- 下拉刷新：作用于当前面板（档口面板刷新档口，评价面板刷新评价）。
- ApplySheet：spring 0.8/0.3 + ic-close + 下拉关闭手势（仅向下、阈值~120px）+ reduced-motion 交叉淡入。
- 骨架 shimmer 在 reduced-motion 下停动画；SegmentTabs 指示条在 reduced-motion 下去 transform 仅 opacity。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：⚠️ location/arrow 合规；ImageSwiper 内部图破兜底（中性语境应 `empty`，以组件实现为准，建议整改见 §8④）；无 emoji；Tab 不靠图标 alone 表意（文字+数量，见 §8⑦）。
- ②金额 api 层：✅ 食堂/档口无金额裸算（档口卡人均由组件展示已转元）。
- ③WaterfallList 禁 slot：✅ 仅 `:list single type="stall"` + `@stall-click`，未传 slot。
- ④三态齐备：✅ 骨架 / EmptyState(retry, 含无档口/无评价/失败) / 正常态。
- ⑤Sheet 规范：✅ ApplySheet 套通用弹层（spring 0.8/0.3 + ic-close + 下拉关闭 + reduced-motion）。
- ⑥按压 0.97：✅ 档口卡内部 `scale(var(--press-scale))`；SegmentTabs 项 `--press-scale`；反馈链接用 opacity 弱化（合规）。
- ⑦颜色 token：✅ 无裸 hex。
- ⑧SectionTitle：✅ 食堂信息区 SectionTitle（档口/评价由 SegmentTabs 承担标题角色，数量以角标呈现）。
- ⑨底部避让：✅ 无底部固定操作栏；Header 含状态栏；吸顶块预留 `env(safe-area-inset-top)` 之下空间，面板 `scroll-view` 底部留 `env(safe-area-inset-bottom)`。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：评价区是否保留「写评价」弱入口（当前默认不展示，避免与档口 Tab 抢 CTA）。
- 计划调整：采用美团式双 Tab（档口/评价）水平切换，评价改为完整列表不再截断前 3；共享区（图集+信息）吸顶常驻。

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | 档口卡/SegmentTabs 项/反馈链接 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | 档口卡 + SegmentTabs 项内部 `scale(var(--press-scale))` + `--press-transition` |
| 3 | 固定栏/导航预留安全区 | 合规 | Header + 吸顶块含状态栏；面板 scroll-view 留 `safe-area-inset-bottom` |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 待整改 | ImageSwiper 图破兜底按一致性规则①应 `empty`（组件层，页级建议标注）；location/arrow 合规 |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；弱化反馈链接 `--text-tertiary` 对比建议验证（见 §8⑯） |
| 7 | 不靠颜色 alone 传意 | 合规 | Tab 文字+数量角标；档口卡/链接有图标+文字；无纯色状态 |
| 8 | prefers-reduced-motion 处理 | 合规 | ApplySheet 降级交叉淡入；骨架 shimmer 关动画；SegmentTabs 指示条去 transform |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | Tab 指示条~200ms；Sheet enter~300ms、exit 更短；press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | Tab `--ease-out`；Sheet `--ease-drawer`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | Sheet 自底部；无 scale(0) |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 指示条 translateX / 面板 opacity；无 `transition:all` |
| 14 | 可中断动效 | 合规 | ApplySheet 手势可中断；Tab 指示条可重定向 |
| 15 | 数字 tabular-nums | 部分 | 档口数量/评价数建议 `tabular-nums` |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 部分 | 弱化链接 `--font-aux`(22rpx) 仅辅助文字；主文 ≥32rpx；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 反馈入口弱化为小文字链接（非破坏性传播，非大板块）；Tab 为中性导航非 CTA |
| 18 | loading/empty/error 三态 | 合规 | 骨架 / EmptyState(retry) / 正常态；档口与评价各自空态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 非 tab 页；返回由 Header；SegmentTabs 为页内分段非全局导航 |
| 21 | 一致性打磨 | 合规 | 信息架构（共享区吸顶 + 双 Tab）；按压统一 0.97；Sheet 规范同源；与 stall 详情双 Tab 方案同源 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：图集/档口单列卡不溢出；SegmentTabs 两项目不挤压；角标不遮挡。
- [ ] 吸顶：滚动面板时共享区 + SegmentTabs 稳定停留，不被 Header 遮挡、不抖动。
- [ ] reduced-motion：ApplySheet 交叉淡入；骨架 shimmer 停；Tab 指示条去 transform。
- [ ] 动态字号：食堂名/简介不截断；Tab 项文字不换行。
- [ ] 暗色对比：弱化反馈链接 `--text-tertiary` 对比（建议改 `--text-secondary` 或仅辅助）；档口卡文字达标。
- [ ] 44pt：档口卡/SegmentTabs 项/反馈链接 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡；面板底部留 `safe-area-inset-bottom`。
- [ ] Tab 切换：档口/评价各自滚动位置保留；下拉刷新作用于当前面板。
