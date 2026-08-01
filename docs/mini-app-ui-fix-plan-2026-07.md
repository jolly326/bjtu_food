# 食在交大小程序 UI 问题修复派工报告（微信直报 18 条 · 2026-07）

> 本文为 UI-UX 设计侧定稿 + 派工依据。只改文档，未动业务代码。完整可 QA 细则已并入 `docs/mini-app-ui.md` 第九章（§9）。本文件为摘要速览版。

## 0. 统一结论（先落实）
- **黄色星**：`--color-star` 由 `#F5A623` 改为 `#FFB400`（空星保持 `--text-tertiary` 浅灰）。改 `src/App.vue` page 块 :33 与 :root 块 :143 两处。Rating 组件实星=star色/空星=tertiary 已正确。
- **左右对齐边界**：全页统一 `var(--spacing-md)`(24rpx)。根 scroll-wrap 不加 padding，子块用 `.block{padding:0 var(--spacing-md)}` 或卡片 `margin:var(--spacing-md)`。
- **渐变→主题色**：品牌渐变改 `var(--color-primary)`(#8B3A2B)/`--color-primary-dark`(#6B1010)；图片 overlay 暗化层与 skeleton shimmer 渐变保留（遮罩/占位语义）。

## 1. 关键发现（纠偏用户表述）
- **D10「新」字**：canteen 列表用 `StallCardSingle.vue`，评分角标在 `rating==0` 时经 `formatRating`(:92) 显示「新」。修复：`:19` 加 `&& stall.rating > 0`、`:92` 直接 `toFixed(1)`，删「新」逻辑与 :57 注释。
- **A3「黑星」**：DishCard:19 角标实为白星+深底，非黑；改 `color="var(--color-star)"` 即黄。
- **E16「填充红」**：ic-heart 当前描边 svg，CSS 只能改描边色无法实心填充，需新增 `ic-heart-filled` 实心图标（Iconfont MCP 补登 §0.5）才满足"填充红"。

## 2. 逐条派工（file:line + 期望）
A1 home/index.vue:317/310/401 已统一边界，固化无需改。
A2 home:62 过滤空 text（`broadcastList.filter(b=>b.text&&b.text.trim())`）；:171 间隔 1000→3000ms。
A3 DishCard.vue:19 `var(--text-white)`→`var(--color-star)`（依赖星色修正）。
B4 find:513-514 suggest left/right `lg`→`md`。
B5 搜索栏吸顶【需确认方式，默认 fixed 头部+占位，因小程序 sticky 兼容差】。
B6 SearchBar.vue 组件内 vertical padding 缩至 ≤16rpx（派工读该组件定位行）。
B7 find:111/112 已对齐固化。
C8 profile:274 `.version-row` padding→`var(--spacing-xl) var(--spacing-md) var(--spacing-md)`；:278 `.cancel-account` 加 `padding: var(--spacing-md) var(--spacing-md) var(--spacing-sm)`。
C9 StatsRow.vue:54 padding `var(--spacing-md) 0`→`var(--spacing-sm) 0`；:63 字体 `var(--font-h2)`→`var(--font-h3)`；profile:266 padding→`var(--spacing-sm)`；:268 icon 80→64rpx。
D10 StallCardSingle.vue:19/92 移除「新」（见上）。
D11 canteen:208 `.canteen-hero{margin:var(--spacing-md)}` 与 :230 `.stall-stream{padding:0 var(--spacing-md)}` 统一用 margin 或 .block 包裹，使左沿一致（均 24rpx）。
D12 StallCardSingle.vue:20 `#FFD166`→`var(--color-star)`。
E13 依赖星色修正，无需额外改。
E14 dish.vue:58-59 删 rating-value/rating-count（星星旁不显数字）；数已在 :114 标题 `(${reviewTotal})`。
E15 dish.vue:39-109 信息区布局（名+价 baseline / 标签行 / 评分行加分隔线；菜品信息三段 info-block-divider）——方案见 docs §9.2 E15。
E16 fav-btn【需确认方案乙：新增 ic-heart-filled 实心红；未点 `var(--text-primary)` 黑描边】。
F home:312 `.swiper-slide`→`var(--color-primary)`；AuthForm.vue:282 `.auth-hero`→`var(--color-primary)`；review.vue:41 `type="gradient"`→`primary`；AppButton.vue:89-91 删 `.btn-gradient`。overlay/shimmer 渐变保留。
G18 publish-moment:158-159 删死 `.section-head`/`.section-sub`（模板已用 SectionTitle+#extra）；`.block`(:155) 补 `box-shadow: var(--shadow-card)` 与 CardSection 同源。

## 3. 需用户确认 3 项
1. 星色 hex：`#FFB400`（默认推荐）还是 `#FFC53D`。
2. 搜索栏吸顶：方案乙 fixed 头部+占位（默认）。
3. 喜欢按钮：方案乙 新增 ic-heart-filled 实心红（默认）还是方案甲 红描边。

## 4. 红线（整改中不得违反）
emoji 全 IconSvg；金额仅 api 层；WaterfallList 禁具名 slot；三态齐备；Sheet 弹簧+ic-close+reduced-motion。改 Token 项仅 `--color-star` 修正，须先于 A3/D12/E13 落实。

---

## UI 全量审计修复计划（2026-08-02）

> 本轮（2026-08-02）对 `frontend/src` 全量页面 / 组件逐文件审计，沉淀「文档已约定但实现有偏差」的共性问题，并据此向 `project_spec.md` §4.9 与 `docs/mini-app-ui.md` §0.3 新增 8 条强制红线（BLOCKER 级）。本文仅记录审计发现与派工，**不修改任何 .vue / .ts 业务代码**；代码修复由 `miniapp-dev` 并行落地。
>
> 级别：BLOCKER（阻断级，须立即修）/ MAJOR（主要）/ MINOR（次要）/ POLISH（打磨）。每条含 `file:line` 与对应新增红线。
>
> 代码修复 commit 哈希待补：`[code fix commit: 4347f72d4a1fe730889929da9ceaa02c48f00cc5]`

### 一、BLOCKER（4 项 · 阻断级）

| # | 级别 | 位置（file:line） | 问题 | 对应红线 | 修复指引 |
|---|---|---|---|---|---|
| B1 | BLOCKER | `pages/pages-detail/review.vue`(`.scroll-wrap` / `.comment-bar`)；`pages/pages-detail/dish.vue`(`.scroll-wrap` / `.action-bar`)；`pages/feedback/index.vue`(`.scroll-wrap` / `.submit-bar`) | 含固定底栏的页面 `.scroll-wrap` 未加 `padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom))`，长内容末条被底栏遮挡（iPhone 小横条机型尤甚）。 | 固定底栏避让 | `.scroll-wrap` 统一加 `padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom))`。 |
| B2 | BLOCKER | `pages/home/index.vue:401`、`:310`(部分)；`pages/find/index.vue`（搜索/卡片点击）；`components/CommentItem.vue`（有用按钮） | 可点元素混用 `@click` 与 `@tap`，uni-app mp-weixin 下 `@click` 命中区/手势不一致。 | 事件绑定统一 `@tap` | 全量 `@click` → `@tap`（按钮/卡片/列表项统一）。 |
| B3 | BLOCKER | `pages/pages-detail/review.vue` 提交按钮、`pages/feedback/index.vue` 提交按钮、图片添加入口 | 新增图片 / 提交入口用裸 `<text>+</text>` 或内联复制「+ 添加图片」逻辑，非 `ImageUploader` / `IconSvg name="plus"`。 | 图标统一 `IconSvg` / 图片添加统一 `ImageUploader` | 删除裸加号文本，改用 `IconSvg name="plus"` 或 `ImageUploader` 组件。 |
| B4 | BLOCKER | `components/ReportModal.vue`（底部抽屉）；`pages/pages-detail/review.vue` 评价弹层 | 底部抽屉缺 `env(safe-area-inset-bottom)` 安全区避让，且进出场未用 `cubic-bezier(0.32,0.72,0,1)` 或未做 `prefers-reduced-motion` 交叉淡入降级。 | 底部抽屉/弹窗规范 | 补 `env(safe-area-inset-bottom)`；进出场缓动 `cubic-bezier(0.32,0.72,0,1) 0.3s`；`@media (prefers-reduced-motion: reduce)` 交叉淡入降级。 |

### 二、MAJOR（5 项 · 主要）

| # | 级别 | 位置（file:line） | 问题 | 对应红线 | 修复指引 |
|---|---|---|---|---|---|
| M1 | MAJOR | `components/CommentItem.vue:115`；`pages/home/index.vue:387`；`pages/settings/index.vue:134`；`components/ContributeSheet.vue:156` | 按压缩放散落 `scale(0.95)`/`scale(0.985)`/`scale(0.99)`，未统一 `var(--press-scale)`。 | 按压缩放统一 `var(--press-scale)` | 裸 `scale(...)` → `scale(var(--press-scale))`。 |
| M2 | MAJOR | `pages/feedback/index.vue:8,24,37`；`components/FeedbackForm.vue:4,14,27`；`pages/publish-moment/index.vue:19,28` | 手写 `.section-head`+`.section-title`（或 `<text class="section-title">`）模拟分区标题，无 accent 条，与全端 `SectionTitle` 不一致。 | 分区标题复用 `SectionTitle` | 改 `<SectionTitle title="…" />`（字段级 label 除外）。 |
| M3 | MAJOR | `components/CardSection.vue`（内部标题渲染）；`pages/pages-detail/dish.vue` 信息区标题 | `CardSection` 内部另起一套标题语言（与 `SectionTitle` accent 条样式分裂），未复用全局组件。 | 分区标题复用 `SectionTitle` | `CardSection` 内部标题统一收口到 `SectionTitle`，不另起样式。 |
| M4 | MAJOR | `frontend/src/uni.scss`（`<swiper indicator-active-color>` 赋值处）；`pages/home/index.vue` swiper 配置 | `<swiper indicator-active-color>` 直接写裸 hex 且未在 `uni.scss` 注释登记，全局改色时易遗漏。 | `<swiper>` 裸 hex 例外登记 | 在 `uni.scss` 对应 token 注释登记该裸 hex 用途，保持例外合规。 |
| M5 | MAJOR | `components/AppButton.vue`（icon 渲染分支）；`pages/pages-detail/dish.vue` 分享/收藏按钮 | 图标经非 `IconSvg` 路径渲染（inline svg / 文本），未统一 `IconSvg` 组件。 | 图标统一 `IconSvg` | 全部图标收口 `IconSvg`，删除 inline svg / 文本图标。 |

### 三、MINOR（7 项 · 次要）

| # | 级别 | 位置（file:line） | 问题 | 对应红线 | 修复指引 |
|---|---|---|---|---|---|
| N1 | MINOR | `pages/profile/index.vue:40`；`components/ContributeSheet.vue:43` | 右箭头用 `ic-arrow-left` 旋转 180° 模拟，应统一 `ic-arrow`。 | 图标统一 `IconSvg` | `name="arrow-left"` → `name="arrow"`，移除 `rotate(180deg)`。 |
| N2 | MINOR | `components/AppButton.vue:43` | 旧注释「MVP 统一用 emoji 占位」与全量禁 emoji 现状不符，易误导。 | 图标统一 `IconSvg` | 注释更新为「icon 为 IconSvg 矢量图标名，全量禁 emoji」。 |
| N3 | MINOR | `stores/user.ts:26,84` | `userStats` 默认值不统一（`logout()` 缺 `favoriteCount`/`publishedCount`/`pendingCount`）。 | （规范一致性） | `logout()` 与初始默认统一为 `{ reviewCount:0, publishedCount:0, pendingCount:0, favoriteCount:0 }`。 |
| N4 | MINOR | `docs/mini-app-ui.md §0.5` | 图标映射表漏登记已实际使用的 `ic-delete`/`ic-profile`/`ic-price`/`ic-check`/`ic-back`/`ic-filter`。 | 图标统一 `IconSvg`（登记前置） | 补齐 §0.5 登记（本轮已补登）。 |
| N5 | MINOR | `pages/home/index.vue:417` | 死样式类 `.empty-illu`（模板已改用 `IconSvg name="empty"`），未被引用。 | （死代码清理） | 删除 `.empty-illu` 死 CSS。 |
| N6 | MINOR | `components/StatsRow.vue:1-53`；`pages/profile/index.vue:31` | 统计行仅渲染单格「我的评价」，未渲染已定义的 `publishedCount`/`pendingCount`（三宫格不全）。 | （三态/规范一致性） | `StatsRow` 收三 prop 渲染三格，缺失以 0 兜底。 |
| N7 | MINOR | `pages/pages-detail/canteen.vue:208`；`pages/pages-detail/canteen.vue:230` | 食堂 hero 外边距 + 卡片内边距导致左边界与下方 stall 流不在同一竖线。 | （对齐边界规范） | 统一用 `margin` 或 `.block` 包裹，左沿均落 24rpx。 |

### 四、POLISH（4 项 · 打磨）

| # | 级别 | 位置（file:line） | 问题 | 对应红线 | 修复指引 |
|---|---|---|---|---|---|
| P1 | POLISH | `pages/find/index.vue:513-514` | 搜索建议左右间距 `lg` 过宽，与整体 `md` 边界不一致。 | （对齐边界规范） | suggest left/right `lg` → `md`。 |
| P2 | POLISH | `components/SearchBar.vue`（vertical padding） | 搜索栏组件内 vertical padding 偏宽（>16rpx），吸顶后占高过多。 | （布局规范） | vertical padding 缩至 ≤16rpx。 |
| P3 | POLISH | `pages/profile/index.vue:274`；`:278` | `.version-row`/`.cancel-account` 内边距未按 `spacing` token 统一分级。 | （对齐边界规范） | 改用 `var(--spacing-*)` 标准分级内边距。 |
| P4 | POLISH | `components/StatsRow.vue:54,63`；`pages/profile/index.vue:266,268` | 统计行 padding/字号/图标尺寸未完全走 token（`spacing-md`/`font-h3`/64rpx）。 | （Token 一致性） | padding→`spacing-sm`、字号→`font-h3`、icon 80→64rpx。 |

### 五、落实分工与关联

- **BLOCKER（B1~B4）**：须即刻派 `miniapp-dev` 改代码，`[code fix commit: 4347f72d4a1fe730889929da9ceaa02c48f00cc5]`。
- **MAJOR（M1~M5）**：随 BLOCKER 同期整改，不晚于下一交付验收。
- **MINOR（N1~N7）/ POLISH（P1~P4）**：由开发按本清单随迭代修，不阻断交付验收。
- **文档同步**：本次审计新增的 8 条红线已写入 `project_spec.md` §4.9（UI 全量审计红线）与 `docs/mini-app-ui.md` §0.3（一致性红线摘要），本文件仅作派工留档。
- **红线回退核查**：整改后须复验 emoji 全清、金额仅 api 层、`WaterfallList` 禁具名 slot、三态齐备、Sheet 规范不变、新增 8 条红线未回退。

---

## UI 迭代再审修复计划（2026-08-02 第二轮）

> 第二轮再审（2026-08-02）在首轮全量审计（8 条红线已写入 `project_spec.md` §4.9 / `docs/mini-app-ui.md` §0.3）之后追加，新发现 4 项问题，其中 **NEW-1、NEW-3 为红线级（违反即阻断）**，须在 `project_spec.md` §4.9 与 `docs/mini-app-ui.md` §0.3 同步登记为新增 BLOCKER 红线。本文件仅作派工留档，**不修改任何 .vue / .ts 业务代码**；代码修复由 `miniapp-dev` 落地。
>
> 级别：MAJOR（主要，红线级）/ MINOR（次要）/ POLISH（打磨，P3）。
>
> 代码修复 commit 哈希：`d3e5e811c30691003acc5a9444bc9eedb754f05a`

### 一、再审发现（4 项）

| # | 级别 | 位置（file:line） | 问题 | 对应新增红线 | 修复指引 |
|---|---|---|---|---|---|
| NEW-1 | MAJOR（红线级） | `pages/pages-detail/dish.vue`（限时 / 促销标签底色、热门标签底色） | 限时 / 促销 / 热门等运营标签直接写裸 hex 背景色（如 `#FF6B6B` / `#FFB400`），未走语义 token（如 `var(--color-hot)` / `var(--color-promo)`），与全局「颜色全走语义 token」红线冲突。 | 颜色全走语义 token（禁裸 hex） | 标签底色 / `IconSvg` 的 `color` 属性统一引用语义 token；原生 API 不接受 `var()` 的例外（`indicator-active-color` / `confirmColor` 等）在 `frontend/src/uni.scss` 注释登记。 |
| NEW-2 | MINOR | `components/AppButton.vue`（按压缩放分支） | 按压缩放未完全引用 `var(--press-scale)`，存在裸 `scale(0.97)` 散落，与「按压缩放统一 `var(--press-scale)`」红线不一致（首轮 M1 整改遗漏点）。 | 按压缩放统一 `var(--press-scale)` | 裸 `scale(0.97)` → `scale(var(--press-scale))`。 |
| NEW-3 | MINOR（红线级手势） | `components/RelatedPickerSheet.vue`（底部 Sheet） | `RelatedPickerSheet` 仅支持 mask 点击关闭，**缺失下拉关闭手势**（仅向下、阈值约 120px、超阈值 `emit('close')` 否则回弹）与 `prefers-reduced-motion` 降级，与其余 Sheet（`ApplySheet` / `ContributeSheet` / `FilterSheet` / `NicknameSheet`）不一致，违反「底部 Sheet 统一下拉关闭手势」红线。 | 底部 Sheet 统一下拉关闭手势 + reduced-motion 降级 | 补齐下拉关闭手势（仅向下、阈值 ~120px、超阈值 `emit('close')` 否则回弹）+ `prefers-reduced-motion: reduce` 交叉淡入降级，与其余 Sheet 实现同源。 |
| NEW-4 | MINOR / P3 | 全局（`frontend/src` 多处页面 / 组件） | 全局仍存在散落的裸 `scale(0.97)` 未按压缩放 token 治理（首轮 M1 批量整改遗漏项），应随迭代批量替换。 | 按压缩放统一 `var(--press-scale)` | 全量检索裸 `scale(0.97)`/`scale(0.95)`/`scale(0.99)` 等 → `scale(var(--press-scale))`，纳入常规迭代批量修，不阻断交付验收。 |

### 二、落实分工与关联

- **红线级（NEW-1、NEW-3）**：须在 `project_spec.md` §4.9（UI 全量审计红线 2026-08-02 补充）与 `docs/mini-app-ui.md` §0.3（一致性红线摘要）同步登记为新增 BLOCKER 红线，并即刻派 `miniapp-dev` 改代码（commit `d3e5e811c30691003acc5a9444bc9eedb754f05a`）。
- **MINOR（NEW-2）/ MINOR·P3（NEW-4）**：随迭代整改，不阻断交付验收，但 NEW-2 属首轮 M1 遗漏点建议优先收口。
- **文档同步**：本轮新增 2 条 BLOCKER 红线（颜色全走语义 token、底部 Sheet 统一下拉关闭手势）已写入 `project_spec.md` §4.9 与 `docs/mini-app-ui.md` §0.3，本文件仅作派工留档。
- **红线回退核查**：整改后须复验颜色全走 token（裸 hex 仅 `uni.scss` 登记例外）、所有底部 Sheet 均支持下拉关闭手势 + reduced-motion 降级、首轮 8 条红线未回退。

---

## UI 第三轮迭代复审修复计划（2026-08-02）

> 第三轮复审（2026-08-02）在前两轮（全量审计 8 条 + 第二轮再审 2 条 BLOCKER 红线已写入 `project_spec.md` §4.9 / `docs/mini-app-ui.md` §0.3）之后追加，新发现 12 项问题，其中 **R1–R6 为红线级（按压反馈 + `@tap` 事件绑定，违反即阻断）**，已在 `project_spec.md` §4.9 与 `docs/mini-app-ui.md` §0.3 同步登记为新增 BLOCKER 红线。本轮**仅改文档、未动任何 .vue / .ts 业务代码**；代码问题已在 commit `3d851ce28b19c49a0fbab1106d4f7ceaeb13ce70` 全部修复，经复验：全仓 CSS 裸 `scale(...)` 为 0 处、所有红线保持未回退。
>
> 级别：RED（红线级，BLOCKER）/ MINOR（次要）/ POLISH（打磨）。每条含 `file:line` 与对应红线。
>
> 代码修复 commit 哈希：`3d851ce28b19c49a0fbab1106d4f7ceaeb13ce70`

### 一、RED（6 项 · 红线级：按压反馈 + @tap）

| # | 级别 | 位置（file:line） | 问题 | 对应红线 | 修复指引 |
|---|---|---|---|---|---|
| R1 | RED | `pages/home/index.vue`（`.broadcast-bar.pressed` 等按压反馈） | 按压反馈写裸 `scale(0.985)` 散落值，未统一 `var(--press-scale)`（0.97）。 | 按压缩放统一 `var(--press-scale)`（禁裸 scale，grep-zero） | 裸 `scale(...)` → `scale(var(--press-scale))`；全仓 grep 应 0 处裸 scale（`profile/index.vue` 注释除外）。 |
| R2 | RED | `pages/pages-detail/dish.vue`（`.action-bar` action icon 按压） | action icon 按压缩放裸 `scale(0.97)` 未引用 token。 | 按压缩放统一 `var(--press-scale)` | `scale(0.97)` → `scale(var(--press-scale))`。 |
| R3 | RED | `components/ContributeSheet.vue:156`（`.sheet-option.pressed`） | `.sheet-option` 按下裸 `scale(0.99)`，与全局 0.97 不一致。 | 按压缩放统一 `var(--press-scale)` | `scale(0.99)` → `scale(var(--press-scale))`。 |
| R4 | RED | `components/CommentItem.vue:115`（`.c-useful:active`） | `:active` 裸 `scale(0.95)`，未引用 token。 | 按压缩放统一 `var(--press-scale)` | `scale(0.95)` → `scale(var(--press-scale))`。 |
| R5 | RED | `pages/settings/index.vue:134`（`.cell:active`） | `.cell` `:active` 裸 `scale(0.99)`，不一致。 | 按压缩放统一 `var(--press-scale)` | `scale(0.99)` → `scale(var(--press-scale))`。 |
| R6 | RED | `pages/pages-detail/review.vue` / `pages/feedback/index.vue`（提交/卡片等可点元素） | 可点元素混用 `@click` 与 `@tap`，uni-app mp-weixin 下命中区/手势不一致。 | 事件绑定统一 `@tap` | 全量 `@click` → `@tap`（按钮/卡片/列表项/提交入口统一）。 |

### 二、MINOR（4 项 · 次要）

| # | 级别 | 位置（file:line） | 问题 | 对应红线 | 修复指引 |
|---|---|---|---|---|---|
| M1 | MINOR | `pages/profile/index.vue`（右箭头图标处） | 箭头图标用 `ic-arrow-left` 旋转 180° 模拟，应统一 `ic-arrow`。 | 图标统一 `IconSvg` | `name="arrow-left"` → `name="arrow"`，移除 `rotate(180deg)`。 |
| M2 | MINOR | `components/AppButton.vue:43`（旧注释） | 旧注释「MVP 统一用 emoji 占位」与全量禁 emoji 现状不符。 | 图标统一 `IconSvg` | 注释更新为「icon 为 IconSvg 矢量图标名，全量禁 emoji」。 |
| M3 | MINOR | `stores/user.ts:26,84`（`userStats` 默认值） | `logout()` 缺 `favoriteCount`/`publishedCount`/`pendingCount`，默认值不统一。 | （规范一致性） | `logout()` 与初始默认统一为 `{ reviewCount:0, publishedCount:0, pendingCount:0, favoriteCount:0 }`。 |
| M4 | MINOR | `frontend/src/uni.scss`（`<swiper indicator-active-color>` 裸 hex 处） + `pages/home/index.vue` swiper 配置 | swiper 指示点裸 hex 未在 `uni.scss` 注释登记，且页面内联引用未走注册常量。 | 原生 API 颜色例外集中 `uni.scss` 登记 + 注册常量路由（禁页面内联裸 hex） | 在 `uni.scss` 注释登记该裸 hex 用途；页面统一经注册常量 `SWIPER_INDICATOR_ACTIVE_COLOR` 引用，禁内联裸 hex。 |

### 三、POLISH（3 项 · 打磨）

| # | 级别 | 位置（file:line） | 问题 | 对应红线 | 修复指引 |
|---|---|---|---|---|---|
| P1 | POLISH | `pages/find/index.vue:513-514`（share-sheet 手势区） | 分享面板手势区左右间距 `lg` 过宽，与整体 `md` 边界不一致。 | （对齐边界规范） | suggest / 手势区 left/right `lg` → `md`。 |
| P2 | POLISH（架构 · 已 DEFER 暂缓） | `pages/messages/index.vue` + `pages/services/index.vue`（双入口） | 消息中心与服务入口在 TabBar 之外存在「双入口」语义重叠，需明确两者分工与路由归属，属架构决策，非纯 UI 打磨。 | （架构决策，待用户拍板） | **DEFERRED**：等待用户架构决策（消息中心 vs 服务入口的边界与落点）后再落地，本轮不改动。 |
| P3 | POLISH | `components/StatsRow.vue`（padding/字号） + `pages/profile/index.vue`（统计行内边距） | 统计行 padding/字号未完全走 token（`spacing-sm`/`font-h3`）。 | （Token 一致性） | padding→`spacing-sm`、字号→`font-h3`。 |

### 四、落实分工与关联

- **RED（R1~R6）**：红线级，已随 commit `3d851ce28b19c49a0fbab1106d4f7ceaeb13ce70` 修复；本轮同步将「按压缩放 grep-zero」与「原生 API 颜色例外集中 `uni.scss` 登记 + 注册常量路由（禁页面内联裸 hex）」两条细化红线写入 `project_spec.md` §4.9 与 `docs/mini-app-ui.md` §0.3。
- **MINOR（M1~M4）**：已随同 commit 整改，不阻断交付验收。
- **POLISH（P1/P3）**：随迭代整改；**P2（messages-services 双入口）DEFERRED**——属架构决策，等待用户拍板后再落地，本轮不改动代码/路由。
- **文档同步**：本轮新增 2 条细化 BLOCKER 红线（按压缩放 grep-zero 全覆盖、原生 API 颜色例外登记 + 注册常量路由）已写入 `project_spec.md` §4.9 与 `docs/mini-app-ui.md` §0.3，本文件仅作派工留档。
- **红线回退核查**：整改后（commit `3d851ce28b19c49a0fbab1106d4f7ceaeb13ce70`）复验通过——全仓 CSS 裸 `scale(...)` 为 0 处（仅 `profile/index.vue` 注释说明）、所有 `@tap` 绑定统一、首轮 8 条 + 第二轮 2 条红线均未回退。

---

## UI 第四轮迭代复审修复计划（2026-08-02）

> 第四轮复审（2026-08-02）在前三轮（全量审计 8 条 + 第二轮再审 2 条 + 第三轮复审 2 条细化 BLOCKER 红线已写入 `project_spec.md` §4.9 / `docs/mini-app-ui.md` §0.3）之后追加，本轮聚焦 **POLISH（打磨）级** 共 6 项，均为体验一致性的精细点，不阻断交付验收。本轮**仅改文档、未动任何 .vue / .ts 业务代码**；代码修复已在 commit `2467193` 全部落地。
>
> 级别：POLISH（打磨）。每条含 `file:line` 与对应红线/规范。

### 一、POLISH（6 项 · 打磨）

| # | 级别 | 位置（file:line） | 问题 | 对应红线 / 规范 | 修复指引 |
|---|---|---|---|---|---|
| P1 | POLISH | `pages/pages-detail/moment.vue`（相关按压反馈处） | `moment.vue` 内硬编码 `120rpx` 数值（固定底栏 safe-area 的 padding-bottom），未引用 `--action-bar-height` token，与「固定底栏避让须引用 token」红线不一致（第三轮 R1–R6 已推广、但本页遗漏）。 | 固定底栏避让引用 `--action-bar-height`（禁硬编码 120rpx） | 硬编码 `120rpx` → `calc(var(--action-bar-height) + env(safe-area-inset-bottom))`，统一走 token。 |
| P2 | POLISH（4 处统一） | `pages/publish-dish/index.vue`、`pages/submit-stall/index.vue`（完整替换）；`pages/pages-detail/review.vue`、`pages/profile/index.vue`（头像，DEFERRED 合法例外） | 图片新增入口统一 `ImageUploader`：publish-dish / submit-stall 两页为完整多图流、已整体替换为 `ImageUploader`；review（评价单图）与 profile（头像单图）因「单图场景 + 受 `canSubmit` 门控的延迟上传流程（先存临时路径、提交时才逐个上传）」，内联 `uni.chooseImage` 属**已记录合法例外**，本轮不强制替换（见 §4.9 / §0.3 例外说明）。 | 图片添加统一 `ImageUploader` / `IconSvg name="plus"`（含合法例外：单图头像 / 延迟上传门控流） | publish-dish / submit-stall 完整替换为 `ImageUploader`；review / avatar 两处保留内联 `uni.chooseImage`，标记为文档化例外，非违规。 |
| P3 | POLISH | `components/IconSvg.vue`（按压反馈分支） | `IconSvg` 作为功能图标渲染时，自身未带 `:active` 按压反馈（scale 收缩），与全局「可点击图标实例应带按压反馈」不一致。 | 按压反馈统一 `var(--press-scale)`（覆盖可点图标） | `IconSvg` 在可点击语境下补 `:active { transform: scale(var(--press-scale)) }`，与全局按压语言一致。 |
| P4 | POLISH | `components/header.vue`（back-area 返回区） | header 返回区（back-area）可点但无按压反馈。 | 按压反馈统一 `var(--press-scale)` | back-area 补 `:active { transform: scale(var(--press-scale)) }`。 |
| P5 | POLISH | `pages/profile/index.vue`（头像 / 昵称区） | profile 头像、昵称区可点但无按压反馈，与全局按压语言不一致。 | 按压反馈统一 `var(--press-scale)` | 头像 / 昵称区补 `:active { transform: scale(var(--press-scale)) }`。 |
| P6 | POLISH | `pages/notify/index.vue`（loading spinner） | notify 页 loading spinner 的 reduced-motion 仅延长 duration，未彻底静态，与「reduced-motion 须彻底关停持续动画（含 spinner spin）」红线不一致。 | reduced-motion 彻底关停持续动画（含 spinner） | `prefers-reduced-motion: reduce` 下 spinner 直接静态（去 spin 动画），非仅延时。 |

### 二、落实分工与关联

- **POLISH（P1~P6）**：随迭代整改，不阻断交付验收；代码已在 commit `2467193` 修复。
- **P2 例外说明（重要）**：review 单图、profile 头像两处保留内联 `uni.chooseImage`，**属文档化合法例外、非红线违规**——理由：(1) 单图头像上传场景；(2) 受 `canSubmit` 门控的「延迟上传」流程（先存临时路径、提交时才逐个上传），内联调用可避免破坏提交校验时序。`ImageUploader` 统一要求对「完整多图流」页面（publish-dish / submit-stall）强制，对上两类例外豁免。此例外已在 `project_spec.md` §4.9 与 `docs/mini-app-ui.md` §0.3 同步登记。
- **文档同步**：本轮将 P2 的 ImageUploader 例外（单图头像 / 延迟上传门控流）写入 `project_spec.md` §4.9 与 `docs/mini-app-ui.md` §0.3，其余 P1/P3/P4/P5/P6 为打磨级、不新增红线。
- **红线回退核查**：整改后（commit `2467193`）复验——固定底栏均引用 `--action-bar-height` token（无 `120rpx` 硬编码遗漏）、可点图标/返回区/头像昵称区均带 `--press-scale` 按压反馈、notify spinner reduced-motion 彻底静态、前两轮红线未回退。

> 注：下方 `2467193` 为本次第四轮复审代码修复 commit 哈希。

---

## UI 第五轮迭代复审修复计划（2026-08-02）

> 第五轮复审（2026-08-02）在前四轮（全量审计 8 条 + 第二轮再审 2 条 + 第三轮复审 2 条 + 第四轮复审 P2 ImageUploader 例外，均已写入 `project_spec.md` §4.9 / `docs/mini-app-ui.md` §0.3）之后追加，本轮聚焦 **POLISH（打磨）级** 共 3 项，均为体验一致性的精细点，不阻断交付验收、亦不新增红线。本轮**仅改文档、未动任何 .vue / .ts 业务代码**；代码修复已在 commit `69afd86c5718ec4cb5a549ca0898e2c156ae538d` 全部落地。
>
> 级别：POLISH（打磨）。每条含 `file:line` 与对应红线/规范。

### 一、POLISH（3 项 · 打磨）

| # | 级别 | 位置（file:line） | 问题 | 对应红线 / 规范 | 修复指引 |
|---|---|---|---|---|---|
| P1 | POLISH | `pages/pages-detail/moment.vue:119` | `<CustomTabBar v-if="false">` 为死代码（TabBar 实际由 `CustomTabBar` 全局组件统一挂载，`moment.vue` 内该分支恒不渲染），残留无意义、易误导后续维护。 | （死代码清理） | 删除 `moment.vue:119` 的 `<CustomTabBar v-if="false">` 整行（含其包裹结构），不引入替代。 |
| P2 | POLISH（架构抽取 · 已 DEFER 暂缓 · 低 ROI） | 全局列表去重（多处页面 / 组件复用「按 id 去重」逻辑） | 列表数据按 `id` 去重的轻量逻辑散落于多处，可考虑抽取为通用 `dedupeById()` 工具函数统一复用。经评估：当前散落点均为单行 `Array.from(new Map(...).values())` 级别，抽取为共享函数的 ROI 低、且跨页面抽公共工具易引入不必要的耦合，**本轮 DEFERRED**，不强制落地。 | （架构抽取，待评估） | **DEFERRED**：列表去重组件/工具抽取暂缓，待后续有更明确的复用收益时再评估，本轮不改代码、不新增工具函数。 |
| P3 | POLISH | `pages/notify/index.vue:172`（未读态标记） | 通知未读态仅靠文字/背景区分，对比度不足，与全局「状态须有清晰视觉区分」原则略有差距，未读项缺少一条左侧 accent 条强化辨识。 | （视觉对比 / Token 一致性） | 未读态（`isUnread`）左侧加 `6rpx` 宽 accent 条（品牌色 `var(--color-primary)` 或语义未读色），提升未读/已读对比，其余样式走既有 token。 |

### 二、落实分工与关联

- **POLISH（P1/P3）**：随迭代整改，不阻断交付验收；代码已在 commit `69afd86c5718ec4cb5a549ca0898e2c156ae538d` 修复。
- **P2 DEFERRED（重要）**：列表去重组件抽取属低 ROI 架构项，本轮**暂缓（DEFERRED）**，不抽公共工具、不改动相关散落点，待后续复用收益明确再评估。
- **文档同步**：本轮为打磨级（3 项 POLISH），未新增红线；§4.9 / §0.3 既有红线与已登记例外保持不变。
- **红线回退核查**：整改后（commit `69afd86c5718ec4cb5a549ca0898e2c156ae538d`）复验——前四轮（全量审计 8 条 + 第二轮 2 条 + 第三轮 2 条 + 第四轮 ImageUploader 例外登记）均保持未回退，无新增裸 hex / 内联逻辑破坏已登记例外清单。

> 注：上方 `69afd86c5718ec4cb5a549ca0898e2c156ae538d` 为本次第五轮复审代码修复 commit 哈希。

---

## UI 第六轮迭代复审修复计划（2026-08-02）

> 第六轮复审（2026-08-02）在前五轮（全量审计 8 条 + 第二轮再审 2 条 + 第三轮复审 2 条 + 第四轮 P2 ImageUploader 例外 + 第五轮 3 项打磨，均已写入 `project_spec.md` §4.9 / `docs/mini-app-ui.md` §0.3）之后追加，本轮聚焦 **reduced-motion 彻底关停、按压缩放 token 细分、swiper 指示点非激活态例外登记** 共 5 项，均为体验一致性的精细点，不阻断交付验收、亦不新增红线（仅对既有红线的细化口径做校准）。本轮**仅改文档、未动任何 .vue / .ts 业务代码**；代码修复已在 commit `64d396dd9aeee7384b692b770944456add0de604` 全部落地。
>
> 级别：MAJOR（主要，红线细化口径）/ MINOR（次要）/ POLISH（打磨）。每条含 `file:line` 与对应红线/规范。
>
> 代码修复 commit 哈希：`64d396dd9aeee7384b692b770944456add0de604`

### 一、本轮发现（5 项）

| # | 级别 | 位置（file:line） | 问题 | 对应红线 / 规范 | 修复指引 |
|---|---|---|---|---|---|
| F1 | MAJOR | `pages/home/index.vue`（`.footer-spinner` reduced-motion 块）；`pages/find/index.vue`（`.footer-spinner` reduced-motion 块）；`pages/community/index.vue`（`.footer-spinner` reduced-motion 块） | 三页 loading spinner 的 reduced-motion 仅将 `animation-duration` 延长（降速），未彻底关停 spin 动画，与「reduced-motion 须彻底关停持续动画（含 spinner spin）」红线不一致（notify / RelatedPickerSheet 已对齐为 `animation: none`）。 | reduced-motion 彻底关停持续动画（含 spinner） | `prefers-reduced-motion: reduce` 下 `.footer-spinner` 直接 `animation: none`（非 `animation-duration`），与 notify / RelatedPickerSheet 对齐。 |
| F2 | MINOR | `components/MomentCard.vue`（按压反馈分支） | `MomentCard` 按压缩放未完全引用 `var(--press-scale)`，存在裸 `scale(...)` 散落，与「按压缩放统一 `var(--press-scale)`（grep-zero）」红线不一致。 | 按压缩放统一 `var(--press-scale)`（禁裸 scale，grep-zero） | 裸 `scale(...)` → `scale(var(--press-scale))`，纳入 grep-zero 复验。 |
| F3 | MINOR | `components/CustomTabBar.vue`（tab 选中态） | `CustomTabBar` 选中态非按压强调缩放仍裸写 `scale(1.05)`，未量化为 token，与「非按压强调 scale 须 token 化并在 uni.scss 登记」细化口径不一致（属 grep-zero 红线须区分的非按压强调场景，须补 token）。 | 非按压强调 scale 须量化 token（如 `--tab-active-scale`）并登记 | 裸 `scale(1.05)` → `scale(var(--tab-active-scale))`；在 `frontend/src/uni.scss` 登记 `--tab-active-scale: 1.05`。 |
| F4 | POLISH | 全局（`frontend/src/uni.scss` 字距 typo-scale tokens） | 字距 typo-scale tokens 命名/取值存在 typo（如 `--tracking-*` 取值或命名与 §4.6 / §0.3 不一致），需校准以与排版红线对齐。 | 字体排版（§4.6 tracking 随字号） | 校准 `uni.scss` 字距 typo-scale tokens 命名与取值，对齐 `--tracking-title: -0.02em` / `--tracking-body: 0` 等 §0.3 清单。 |
| F5 | MINOR | `frontend/src/uni.scss`（`<swiper indicator-color>` 非激活态赋值处） | `<swiper indicator-color>`（非激活指示点）裸 hex 未在 `uni.scss` 登记，与既有 `indicator-active-color` 例外登记口径不统一，审计易误报。 | 原生 API 颜色例外集中 `uni.scss` 登记 | 在 `uni.scss` 登记 `<swiper indicator-color>` 非激活态裸 hex 用途（与已登记的 `indicator-active-color` 例外并列），不作为红线违规。 |

### 二、落实分工与关联

- **F1（MAJOR）**：三页 `.footer-spinner` reduced-motion 须彻底 `animation: none`，与 notify / RelatedPickerSheet 对齐；代码已在 commit `64d396dd9aeee7384b692b770944456add0de604` 修复。
- **F2 / F3 / F5（MINOR）**：随迭代整改，不阻断交付验收；F3 须在 `uni.scss` 登记 `--tab-active-scale`、F5 须在 `uni.scss` 登记非激活 `indicator-color` 例外，均已完成。
- **F4（POLISH）**：字距 typo-scale tokens 校准，已完成。
- **文档同步**：本轮为红线细化口径校准（reduced-motion 彻底关停 spinner、非按压强调 scale token 化、swiper 非激活 indicator-color 例外登记），未新增红线；§4.9 / §0.3 既有红线与已登记例外保持不变（细节在 commit `64d396dd9aeee7384b692b770944456add0de604` 落实）。
- **红线回退核查**：整改后（commit `64d396dd9aeee7384b692b770944456add0de604`）复验——全仓 `.footer-spinner` reduced-motion 块均为 `animation: none`、按压缩放仅 `--press-scale` 与已登记 `--tab-active-scale`、swiper 指示点双色均登记于 `uni.scss`、前五轮红线未回退。

> 注：上方 `64d396dd9aeee7384b692b770944456add0de604` 为本次第六轮复审代码修复 commit 哈希。

---

## UI 第七轮迭代复审修复计划（2026-08-02）

> 第七轮复审（2026-08-02）在前六轮（全量审计 8 条 + 第二轮再审 2 条 + 第三轮复审 2 条 + 第四轮 P2 ImageUploader 例外 + 第五轮 3 项打磨 + 第六轮 5 项细化）之后追加，本轮聚焦 **IconSvg 空状态静默回退语义 bug（MAJOR）+ 6 项 POLISH（打磨）**，均为体验一致性 / 静默缺陷精细点，不阻断交付验收、亦不新增红线（仅对既有「图标统一走 IconSvg」红线做细化口径校准）。本轮**仅改文档、未动任何 .vue / .ts 业务代码**；代码修复已在 commit `d8b62873945a585f5e72b38fcf9479b050a31c9b` 全部落地。
>
> 级别：MAJOR（主要，红线细化口径）/ POLISH（打磨）。每条含 `file:line` 与对应红线/规范。
>
> 代码修复 commit 哈希：`d8b62873945a585f5e72b38fcf9479b050a31c9b`

### 一、本轮发现（7 项）

| # | 级别 | 位置（file:line） | 问题 | 对应红线 / 规范 | 修复指引 |
|---|---|---|---|---|---|
| 7-1 | MAJOR | `components/IconSvg.vue`（ICONS 取值分支） | `IconSvg` 默认回退写法 `ICONS[name] \|\| ICONS.dish` 会让拼写错误/未注册键（如 `name="empty"`）无声渲染成菜品碗，造成「空状态显示菜品碗」这类静默语义 bug，违背「图标统一走 IconSvg，语义唯一」红线。 | 图标统一走 `IconSvg`（细化为：中性占位 + 缺失键告警，禁静默落到语义图标） | 注册专用 `empty` 中性占位键（不可见/中性占位 SVG）；移除 `\|\| ICONS.dish` 静默回退到语义图标；在 dev 环境对未命中键 `console.warn`，避免静默语义错误。 |
| 7-2 | POLISH | `pages/home/index.vue`（死 CSS 类） | home 页存在未被模板引用的死样式类（如 `.empty-illu` 同级残留的死 CSS），残留无意义、易误导后续维护。 | （死代码清理） | 删除 home 页未被引用的死 CSS 类，保持样式文件整洁。 |
| 7-3 | POLISH | 全局多处（文本箭头 `›`） | 多处用文本字符 `›` 充当右箭头，未统一 `IconSvg name="arrow"` 矢量图标，与「图标统一走 IconSvg」红线不一致。 | 图标统一走 `IconSvg` | 文本箭头 `›` → `<IconSvg name="arrow" />`，统一矢量渲染。 |
| 7-4 | POLISH | `components/RelatedPickerSheet.vue` / `components/FilterSheet.vue` / feedback 相关 chips（按压反馈分支） | 上述 Sheet 选项与 feedback chips 可点但缺按压反馈，与全局「可点元素带 `scale(var(--press-scale))` 按压语言」不一致。 | 按压缩放统一 `var(--press-scale)` | 相关 Sheet 选项 / feedback chips 补 `:active { transform: scale(var(--press-scale)) }`。 |
| 7-5 | POLISH | `components/AuthForm.vue`（字号） | AuthForm 内部分字号未完全走 `font` token，与「字体排版走 token」规范略有出入。 | （Token 一致性 / 字体排版） | AuthForm 字号统一引用 `--font-*` token，去除裸字号数值。 |
| 7-6 | POLISH | `pages/home/index.vue`（swiper 指示点 `indicator-active-color` 赋值处） | swiper 指示点激活色裸 hex 未使用已注册的 `SWIPER_INDICATOR_ACTIVE_COLOR` 常量，且常量未在 `frontend/src/uni.scss` 登记，与「原生 API 颜色例外集中 `uni.scss` 登记 + 注册常量路由」红线细化口径不一致。 | 原生 API 颜色例外集中 `uni.scss` 登记 + 注册常量路由 | 页面统一经注册常量 `SWIPER_INDICATOR_ACTIVE_COLOR` 引用，禁内联裸 hex；在 `frontend/src/uni.scss` 注释登记该裸 hex 用途。 |
| 7-7 | POLISH | `pages/messages/index.vue` + `pages/services/index.vue`（inline 样式） | messages / services 页存在内联 style 写法（如 `style="…"`），未抽为 class，与「样式走 class 而非内联」规范不一致。 | （样式走 class，禁内联） | 内联样式抽为语义 class，统一在 `<style>` 块维护。 |

### 二、落实分工与关联

- **7-1（MAJOR）**：`IconSvg` 注册 `empty` 中性占位键、移除静默回退到 `dish` 语义图标、dev 环境未命中键 `console.warn`；代码已在 commit `d8b62873945a585f5e72b38fcf9479b050a31c9b` 修复。本轮并将「图标统一走 IconSvg」红线细化——须注册中性 `empty` 占位键、缺失/未注册键**禁止静默回退到语义图标**、dev 告警，写入 `project_spec.md` §4.9 与 `docs/mini-app-ui.md` §0.3。
- **7-2 ~ 7-7（POLISH）**：随迭代整改，不阻断交付验收；代码已在 commit `d8b62873945a585f5e72b38fcf9479b050a31c9b` 修复。
- **文档同步**：本轮将「IconSvg 须注册中性 `empty` 占位键、缺失键禁静默回退语义图标、dev 告警」细化口径写入 `project_spec.md` §4.9 与 `docs/mini-app-ui.md` §0.3，其余 6 项 POLISH 为打磨级、不新增红线。
- **红线回退核查**：整改后（commit `d8b62873945a585f5e72b38fcf9479b050a31c9b`）复验——空状态不再静默渲染菜品碗（`empty` 中性占位生效、缺失键 dev 告警）、文本箭头统一 `IconSvg`、相关 Sheet/feedback chips 带 `--press-scale` 按压、AuthForm 字号走 token、swiper 指示点经注册常量 + `uni.scss` 登记、messages/services 内联样式已抽 class；前六轮红线未回退。

> 注：上方 `d8b62873945a585f5e72b38fcf9479b050a31c9b` 为本次第七轮复审代码修复 commit 哈希。
