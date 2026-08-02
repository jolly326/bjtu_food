# 发现 (find)
- 路由: /pages/find/index
- 分包: 主包
- 源文件: src/pages/find/index.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
搜索与发现页：未筛选时展示历史搜索、分类宫格、本周热搜；输入/选分类/筛选后进入多维筛选结果页（排序滑块 + 筛选 Sheet + 瀑布流）。

## 2. 布局结构
- 顶部：`Header`（标题随模式切换：`发现` / 筛选时品类名）。
- 搜索框 `SearchBar`（input-mode + 联想 debounce 300ms），下方固定（sticky），联想面板 `suggest-panel`（`fixed` 定位，top 由 selectorQuery 实测，z-index 100）。
- 主滚动区 `scroll-view`（refresher + 触底加载）。
- **发现主页**（`!inFilter`）：首屏骨架 `discover-skeleton` → 三块 `CardSection`：
  1. `SectionTitle 历史搜索`（extra「清空」）+ 历史 chip 行。
  2. `SectionTitle 分类` + 4 列宫格 `category-grid`（IconSvg 分类图标）。
  3. `SectionTitle 本周热搜` + 热搜列表（排名 + 配图 + 词 + 热度/关联数）。
- **筛选结果页**（`inFilter`）：筛选控制条（返回 + `SegmentTabs` 排序 + `filter-trigger`）→ `CardSection`(`SectionTitle` 品类名 + 筛选摘要 + `WaterfallList` / `EmptyState` + footer)。
- 底部：`CustomTabBar`；`FilterSheet`（通用筛选抽屉，canteen/price/taste）。

## 3. 核心组件与用法
- `SearchBar`：搜索输入与联想。
- `SegmentTabs`：滑块式排序（热度/评分/价格，`light` 模式），spring 1.0/0.3。
- `FilterSheet`：底部筛选抽屉（spring 0.8/0.3 + 手势中断 + 遮罩关闭 + reduced-motion 降级）。
- `WaterfallList`：结果瀑布流（`:list` + `@card-click`）。
- `CardSection` / `SectionTitle`：分区卡片与标题。
- `EmptyState`：无结果/重试。
- `IconSvg`：`close`(删历史)、`arrow-left`(返回)、`filter`、`fire`(热搜/排序)、`star`(排序)、`price`(排序)、`dish`/`stall`/`home`(suggest 映射，**home 作中性回退见 §8④**)；分类图标走独立矢量（noodle/rice/…/dish 兜底）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--bg-soft`、`--text-primary/secondary/tertiary`。
- 强调色：`--color-primary`(返回/选中)、`--color-price`(热搜排名/热度/火图标)、`--color-error`(清除筛选)。
- 圆角/阴影：`--radius-card`、`--radius-tag`、`--shadow-card`、`--shadow-modal`(suggest-panel)。
- 间距：`--spacing-sm/md/lg/xl`（4/8pt 节奏）。
- 字号：`--font-body`、`--font-aux`、`--font-caption`。
- 动效：`--press-scale`（history-chip/category-cell/hotsearch-item/suggest-item/filter-trigger 均 `:active`/`.pressed scale(0.97)`）、`--ease-out`。
- 布局：`--tabbar-height` + `env(safe-area-inset-bottom)`；`search-wrap` sticky。

## 5. 交互与动效
- 搜索联想：输入 debounce 300ms 拉取 `fetchSuggestions`，面板 `fixed` 避免 scroll-view 裁剪；点击建议按 type 跳 dish/canteen/stall。
- 历史 chip：单击进筛选、删除按钮 `stop` 删除单项、extra「清空」清全部。
- 分类宫格：点击进入对应品类筛选结果。
- 排序 `SegmentTabs` 滑块 spring；`filter-trigger` 打开 `FilterSheet`；摘要区「清除筛选」。
- 筛选结果触底无限加载（footer spinner / 到底）；`prefers-reduced-motion` footer-spinner 关动画。
- FilterSheet：spring `0.8/0.3`（抽屉曲线 cubic-bezier(.32,.72,0,1)）+ ic-close + 下拉关闭手势 + reduced-motion 交叉淡入；抽屉从底部进入，exit 比 enter 短（~60–70%）。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ 全走 IconSvg；分类图标映射独立矢量（noodle/rice/…/dish 兜底），无 emoji。suggest 回退用 `home`，建议改 `empty`（见 §8④）。
- ②金额 api 层：✅ 筛选价格以「元」由 FilterSheet 传入，页面无 `/100` 裸算。
- ③WaterfallList 禁 slot：✅ 仅 `:list` + `@card-click`。
- ④三态齐备：✅ 发现主页骨架；筛选结果 EmptyState（可重试）；正常态。
- ⑤Sheet 规范：✅ `FilterSheet` 套通用弹层（spring 0.8/0.3 + 手势中断 + 遮罩 + reduced-motion 降级）。
- ⑥按压 0.97：✅ 多处 `scale(var(--press-scale))`；FilterSheet 内部同规范。
- ⑦颜色 token：✅ 无裸 hex；`--color-price` 用于热搜，「火」图标 `color="var(--color-price)"`。
- ⑧SectionTitle：✅ 历史/分类/本周热搜/筛选品类名均 SectionTitle。
- ⑨底部避让：✅ scroll-wrap `--tabbar-height` + 安全区。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：~~suggest 面板非 dish/stall 类型回退图标 `home` 是否改为 `empty`~~（已整改：回退图标改 `empty`，见 §8④）
- 计划调整：

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | chip/宫格/热搜项/筛选触发均 ≥44pt；间距 ≥`--spacing-sm` |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | 多项 `.pressed{scale(var(--press-scale))}`，`--press-transition`≈120ms ease |
| 3 | 固定栏/导航预留安全区 | 合规 | 底部 TabBar + search-wrap sticky 均避让安全区；Header 含状态栏占位 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | 已整改：回退图标改 `empty`；其余 `close`/`arrow-left`/`filter`/`fire`/`star`/`price`/`dish`/`stall` 均注册 key |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)`；无裸 hex |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；未单独暗色/动态字号实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 热搜排名有数字+配图；清空/清除有文字+图标 |
| 8 | prefers-reduced-motion 处理 | 合规 | footer-spinner 关动画；FilterSheet 降级交叉淡入 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控端用 `:active`/touch，无裸 hover |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | sheet 抽屉 enter~300ms、exit 更短；chip press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | segment/sheet 用 `--ease-out`/`--ease-drawer`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | FilterSheet 自底部（含遮罩居中上下文），非 scale(0)；无 popover |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | FilterSheet 手势可中断（拖拽重定向当前值）；transition 可中断 |
| 15 | 数字 tabular-nums | 部分 | 热搜热度/排名为数字，建议 `tabular-nums` 防位移 |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 字号走 token；4/8pt 间距；无横向滚动 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 无破坏性操作；筛选为主流程 |
| 18 | loading/empty/error 三态 | 合规 | 主页骨架 + 结果 EmptyState + footer |
| 19 | 表单无障碍（label/必填/校验/键盘） | 部分 | SearchBar 有 placeholder；键盘类型对（搜索）；但作为独立表单页无可见 label，搜索框属常见例外 |
| 20 | 导航一致：底部≤5 项 icon+label | 合规 | CustomTabBar ≤5，当前项高亮 |
| 21 | 一致性打磨 | 合规 | 按压统一 0.97；宫格/列表动效语言一致；无 disabled 态 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：分类宫格 4 列不挤；suggest-panel 不被 TabBar 遮挡（fixed top 实测）。
- [ ] reduced-motion：FilterSheet 交叉淡入、footer spinner 停。
- [ ] 动态字号：热搜列表/宫格文字不截断。
- [ ] 暗色对比：热搜「火」图标 `--color-price` 在暗面 ≥3:1。
- [ ] 44pt：chip/宫格/筛选触发 ≥44pt。
- [ ] 安全区：suggest-panel 底部、TabBar 不被 home indicator 遮挡。
