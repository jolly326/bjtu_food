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
> 代码修复 commit 哈希待补：`[code fix commit: TBD]`

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

- **BLOCKER（B1~B4）**：须即刻派 `miniapp-dev` 改代码，`[code fix commit: TBD]`。
- **MAJOR（M1~M5）**：随 BLOCKER 同期整改，不晚于下一交付验收。
- **MINOR（N1~N7）/ POLISH（P1~P4）**：由开发按本清单随迭代修，不阻断交付验收。
- **文档同步**：本次审计新增的 8 条红线已写入 `project_spec.md` §4.9（UI 全量审计红线）与 `docs/mini-app-ui.md` §0.3（一致性红线摘要），本文件仅作派工留档。
- **红线回退核查**：整改后须复验 emoji 全清、金额仅 api 层、`WaterfallList` 禁具名 slot、三态齐备、Sheet 规范不变、新增 8 条红线未回退。
