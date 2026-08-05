# 食堂详情 (canteen)
- 路由: /pages/pages-detail/canteen
- 分包: pages-detail 分包（实际 src 路径 pages/pages-detail/canteen.vue）
- 源文件: src/pages/pages-detail/canteen.vue
- 最后依据 skills 校对: 2026-08-02（现状校准：文档对齐代码当前结构——共享区 + 档口单列流 + 关联动态，撤销旧「美团式双 Tab」方案描述）

## 1. 页面定位
食堂详情：顶部共享区（图集 `ImageSwiper` + 食堂信息合并卡）常驻，下方为**档口单列卡片流**（`WaterfallList single type="stall"`）+ 置底**关联动态**轻量区块。**无评价 Tab、无评价区**（评价内联至各档口 `stall` 详情页，`review-list` 独立页已移除）。

> **重要（现状校准）**：早期文档曾描述「美团式分段 Tab（档口/评价）+ 共享区吸顶 + 评价完整列表 + 写评价弱入口」方案，但**该方案未在代码落地**。当前代码为「共享区 → 档口单列流 → 关联动态」的纵向结构，无 `SegmentTabs`、无评价区、无「写评价」入口。本文档已按**代码现状**重写为唯一口径（详见 §7 裁定）。如需恢复评价区或双 Tab，须由技术负责人重排需求级页面清单后再议，本文档不先行保留过期描述。

「信息有误？」入口（ApplySheet）置于食堂信息 hero 卡内右上角（小文字链接，名称同行的最右，无 SectionTitle 大标题）。

## 2. 布局结构
- 顶部：`Header :title="canteenName||食堂详情" showBack`（含状态栏占位）。
- `scroll-view`（refresher）：
  - 加载骨架：`.canteen-skeleton`（swiper/info/stall 骨架，shimmer）。
  - 正常态（`canteenInfo`）：
    1. `ImageSwiper`（食堂图集轮播，图破兜底 `empty`，见 §8④）。
    2. `CardSection` **食堂信息 hero**（**无「食堂信息」大标题**，2026-08-02 用户裁定）：名称（左上，`--font-h3` 700 + `--tracking-h3`）+ **「信息有误？」**（右上弱链接 `--font-aux` `--text-tertiary`，同行最右）；位置（location 图标 + 文本，**独立一行** `--font-small` 500）；介绍（`--font-body`，2 行截断）。
       - **评分 / 人均占位行已移除**（2026-08-02 用户裁定：hero 只需名称/位置/介绍/信息有误四项；档口评分在下方档口卡片中展示）。
    3. `CardSection` 档口列表（**无「档口列表（N）」分区标题**，直接 `WaterfallList single type="stall" @stall-click` 一列矩形卡片：左侧 140rpx 正方形圆角图片 + 右侧名称/标签/评分）——有档口时；无档口 `EmptyState text="该食堂暂无档口" :retry`。
    4. `CardSection` 关联动态（低优先级置底轻量区块；`relatedMoments` 聚合，点击跳 moment 详情；无动态不渲染）。
  - 失败态：`EmptyState text="食堂信息加载失败" :retry`。
- 底部：`ApplySheet`（entity-type CANTEEN）。
- 三态（整体）：加载骨架 / 正常（含档口空态）/ 失败重试，齐备。

## 3. 核心组件与用法
- `ImageSwiper`：食堂图集轮播（图破兜底 `empty`）。
- `WaterfallList :list single type="stall" @stall-click`：档口单列卡（内部 StallCardSingle，禁 slot，只经事件上抛）。
- `CardSection` / `SectionTitle`：仅关联动态保留 `SectionTitle title="关联动态"`；**食堂信息 hero 与档口列表不再用 SectionTitle**（2026-08-02 用户裁定）。
- `EmptyState`：无档口 / 加载失败重试。
- `ApplySheet`：「信息有误？」弹层（spring 0.8/0.3 + ic-close + reduced-motion）。
- `IconSvg`：`location`(位置)、`arrow`(关联动态右箭头)、`empty`(图破兜底)。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--text-primary/secondary/tertiary`。
- 强调：`--color-primary`(位置图标 / 关联动态 arrow)、`--text-tertiary`(弱化反馈链接)。
- 圆角/阴影：`--radius-card`、`--shadow-card`。
- 间距：`--spacing-md/sm/lg`（4/8pt 节奏）。
- 字号（4 级层级阶梯，2026-08-02 重整，见 §7 裁定）：
  - **H1 标题**：食堂名 `--font-h3`(36rpx) 700 + `--tracking-h3` 负字距；
  - **正文/数据**：简介 `--font-body`(28rpx) 400 line-height 1.6；
  - **元数据/列表**：位置 `--font-small`(24rpx) 500 + 24rpx 图标；关联动态 `--font-small`(24rpx)；
  - **弱化/注记**：「信息有误？」`--font-aux`(22rpx) `--text-tertiary`。
- 数字：hero 无评分占位（已移除）；档口卡评分由 `StallCardSingle` 组件内 `.star-num-text` 展示（`tabular-nums` 由组件层落实，见 lists.md 通用列表模式）。
- 动效：档口卡 / 关联动态项按下 `scale(var(--press-scale))`（`.pressed` 类 + touch/mouse 事件）；骨架 shimmer 仅 opacity/transform；reduced-motion 关动画。
- 布局：页面 `flex` 纵向，`scroll-view` 占剩余高度；`scroll-wrap padding: var(--spacing-md) 0 0`（CardSection 自带左右 margin）。

## 5. 交互与动效
- 档口卡点击 `goToStall`（经 dishStore.navParams 传 stallName/canteen）→ stall 详情。
- 关联动态项点击 → moment 详情（按 `m.id`）。
- 「信息有误？」`openApply`(requireAuth) → ApplySheet。
- 下拉刷新：作用于整页（重载食堂 + 档口 + 关联动态）。
- ApplySheet：spring 0.8/0.3 + ic-close + 下拉关闭手势（仅向下、阈值~120px）+ reduced-motion 交叉淡入。
- 骨架 shimmer 在 reduced-motion 下停动画。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ location/arrow 合规；ImageSwiper 图破兜底已用 `empty`（组件层，见 §8④）；无 emoji。
- ②金额 api 层：✅ 无金额裸算（档口卡人均由组件展示已转元）。
- ③WaterfallList 禁 slot：✅ 仅 `:list single type="stall"` + `@stall-click`，未传 slot。
- ④三态齐备：✅ 骨架 / EmptyState(retry, 含无档口/失败) / 正常态。
- ⑤Sheet 规范：✅ ApplySheet 套通用弹层（spring 0.8/0.3 + ic-close + 下拉关闭 + reduced-motion）。
- ⑥按压 0.97：✅ 档口卡内部 `scale(var(--press-scale))`；关联动态项 `.pressed scale(var(--press-scale))`；反馈链接用 opacity 弱化（合规）。
- ⑦颜色 token：✅ 无裸 hex。
- ⑧SectionTitle：✅ 仅关联动态用 SectionTitle；食堂信息 hero 与档口列表**不用**（2026-08-02 用户裁定去大标题，非违规）。
- ⑨底部避让：✅ 无底部固定操作栏；Header 含状态栏；scroll-view 底部留 `env(safe-area-inset-bottom)`。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。

- **裁定（评价区 / 写评价弱入口）**：~~「评价区写评价弱入口」~~ —— **取消**。当前代码为「共享区 + 档口单列流 + 关联动态」结构，**无评价区、无评价 Tab**；评价已内联至各档口 `stall` 详情页（`review-list` 独立页已移除，见 project_spec §2.1）。故原「双 CTA 抢焦点」前提不成立，该待讨论项随旧方案一并撤销。若未来需在食堂详情恢复评价区，采用「完整评价列表 + 弱化写评价入口」时，写评价入口应以信息卡内小文字链接弱化呈现、不占大板块（避免与档口列表抢主焦点），届时再落回本文档。
- **裁定（食堂评分 / 人均占位）**：~~前端聚合占位~~ —— **已移除**（2026-08-02 用户裁定）。hero 卡片只保留名称 / 位置 / 介绍 /「信息有误？」四项；档口评分由下方各档口卡片（`StallCardSingle`）展示，食堂级评分不再占位（`canteenRating`/`canteenPerCapita` computed 已随重构删除）。若未来后端提供 canteen 级评分字段，再议是否回填 hero。
- **裁定（hero 卡片去分区大标题，2026-08-02）**：去掉 `SectionTitle title="食堂信息"` 大标题，直接展示名称 + 「信息有误？」（名称行最右弱链接）+ 位置（独立一行）+ 介绍（2 行截断）；去掉「档口列表（N）」分区标题，档口直接一列矩形卡片（左 140rpx 正方形圆角图 + 右名称/标签/评分）。已落地 `canteen.vue`（template 与 `.info-body/.info-head/.info-location/.feedback-link` 样式）。
- **裁定（字体层级重整，2026-08-02）**：原字号 `36→30→22→20→30` 跳变无序（元数据 30 与正文同级、评分数据 22 反而最小，倒挂）。重整为 **4 级清晰阶梯**：H1 标题 36（`--tracking-h3` 负字距）→ 正文/数据 28（简介 line-height 1.6）→ 元数据/列表 24（位置、关联动态）→ 弱化/注记 22（「信息有误？」、反馈链接）。已落地 `canteen.vue`（`.info-name/.info-location-text/.info-desc-text/.related-moment-text`）。
- 待整改项：无（ImageSwiper 图破兜底已合规，见 §8④）。

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | 档口卡/关联动态项/反馈链接 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | 档口卡内部 + 关联动态项 `.pressed scale(var(--press-scale))` + `--press-transition` |
| 3 | 固定栏/导航预留安全区 | 合规 | Header 含状态栏；scroll-view 留 `safe-area-inset-bottom` |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | ImageSwiper 图破兜底已落 `empty`（`ImageSwiper.vue` `.image-swiper-placeholder` `<IconSvg name="empty">`，组件层）；location/arrow 合规；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；弱化反馈链接 `--text-tertiary` 对比为全局 token 依赖项，交付前统一真机实测（见 §9） |
| 7 | 不靠颜色 alone 传意 | 合规 | 档口卡/链接有图标+文字；无纯色状态 |
| 8 | prefers-reduced-motion 处理 | 合规 | ApplySheet 降级交叉淡入；骨架 shimmer 关动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | Sheet enter~300ms、exit 更短；press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | Sheet `--ease-drawer`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | Sheet 自底部；无 scale(0) |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 骨架/按压仅 transform/opacity；无 `transition:all` |
| 14 | 可中断动效 | 合规 | ApplySheet 手势可中断 |
| 15 | 数字 tabular-nums | 合规 | hero 无评分占位；档口评分/人均数字由 `StallCardSingle.vue` `.star-num-text`/`.stall-meta-text` 展示（tabular 由组件层落实） |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 主文 `--font-body`(28rpx) 起；弱化链接 `--font-aux`(22rpx) 仅辅助文字；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 「信息有误？」弱化为小文字链接（非大板块）；无主 CTA 抢占 |
| 18 | loading/empty/error 三态 | 合规 | 骨架 / EmptyState(retry, 无档口/失败) / 正常态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 非 tab 页；返回由 Header |
| 21 | 一致性打磨 | 合规 | 信息架构（共享区 + 档口单列流 + 关联动态）；按压统一 0.97；Sheet 规范同源 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：图集/档口单列卡不溢出。
- [ ] reduced-motion：ApplySheet 交叉淡入；骨架 shimmer 停。
- [ ] 动态字号：食堂名/简介不截断。
- [ ] 暗色对比：弱化反馈链接 `--text-tertiary`、档口卡文字为全局 token 依赖项，交付前统一真机实测 ≥4.5:1。
- [ ] 44pt：档口卡/关联动态项/反馈链接 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡；scroll-view 底部留 `safe-area-inset-bottom`。
- [ ] 关联动态：无数据不渲染该 CardSection（避免空壳区块）。
