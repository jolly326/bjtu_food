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
