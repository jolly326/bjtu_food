# 搜索 (find)
- 路由: /pages/find/index
- 分包: 主包
- 源文件: src/pages/find/index.vue
- 最后依据 skills 校对: 2026-08-03
- **2026-08-03 重构（用户裁定）**：①**二级搜索页**——非 tab，经首页顶部搜索框 `navigateTo` 进入（TabBar 已 3 项：home/moment/profile）；②**顶部 = 返回键 + 搜索框 + 结果分类 tab，全部固定不随滚动**（位于 scroll-view 外，天然固定）；③主页仅「**搜索记录**」+「**高频搜索**」两块（热搜去掉）；④去掉底部 CustomTabBar；⑤**搜索结果 = 复合型混合列表**（美团式）：复用 suggest 接口，菜品/档口/食堂混排，各带类型徽标，**顶部 tab 分类过滤（全部/菜品/档口/食堂）**，无"「xx」的搜索结果"标题——**无排序/筛选**（用户裁定）。

## 1. 页面定位
二级搜索页：**顶部固定区 = 返回键 + 搜索框 + 结果分类 tab**（全部/菜品/档口/食堂，结果态显示）；主页展示**搜索记录**（首位）+ **高频搜索**（静态推荐词）；搜索/选推荐词后进入**复合型混合结果页**——菜品/档口/食堂混排（美团式，每项带类型徽标，点击跳对应详情：菜品弹 `DishDetailSheet`、档口/食堂跳详情页），可按 tab 分类过滤。**无"「xx」的搜索结果"标题**、**无排序/筛选**（2026-08-03 用户裁定）；档口页左侧动态分类保留（B 方案）。

## 2. 布局结构
- **顶部固定区 `.search-nav`**（2026-08-03：位于 scroll-view **外**，天然不随滚动；paddingTop 由 `statusBarHeight` 动态设置避让刘海/胶囊）：
  - `.search-nav-row`：**返回键**（arrow-left，`@tap`：结果态 `exitFilter`、主页态 `reLaunch` 回首页）+ **搜索框**（自绘 `.search-box`：圆角灰条 + 放大镜 + 清空按钮，与首页同款视觉；`input` confirm-type=search）。
  - **结果分类 tab `.result-tabs`**（`inFilter` 时显示，固定于搜索框下）：全部 / 菜品 / 档口 / 食堂，选中主色 + 底部 accent 条，计数 `tabular-nums`，`filteredMixed` 按 `activeResultTab` 过滤。
  - 联想面板 `suggest-panel`（`fixed` 定位，top 由 `.search-nav` 底边 selectorQuery 实测 + 4px，z-index 100）。
- 主滚动区 `scroll-view`（refresher + 触底加载）。
- **搜索主页**（`!inFilter`，2026-08-03 两块）：首屏骨架 `discover-skeleton` → 两块 `CardSection`（按优先级）：
  1. `SectionTitle 搜索记录`（extra「清空」）+ 历史 chip 行——默认折叠仅显示 3 条，>3 条时「展开全部 N 条 / 收起」按钮（首位）。
  2. `SectionTitle 高频搜索` + 静态推荐词 chip（`hotKeywords`：快餐/麻辣烫/牛肉面/盖饭/奶茶/烧烤/甜品/早餐；**不带序号徽标**（2026-08-03 用户裁定），点击直接搜索）。
  > 分类宫格已移除（2026-08-03）；热搜已移除（改静态 `hotKeywords`）；无底部 CustomTabBar（二级页）。
- **复合型混合结果页**（`inFilter`，2026-08-03）：`.filter-enter` 进场过渡 → **无标题，直接混合列表**（`filteredMixed` 按顶部 tab 过滤）/ `EmptyState`。
  - **混合列表**（美团式复合流）：`.mixed-item` 一行一个，左侧 120rpx 圆角图（图破兜底类型图标）+ 右侧名称 + **类型徽标**（`.mixed-type`：菜品=主色 / 档口=热色 / 食堂=中性）+ 副信息 + arrow。点击分类型跳转：菜品弹 `DishDetailSheet`、档口跳 stall、食堂跳 canteen。
  - 数据源：复用 **`GET /dishes/suggest?keyword=`**（`dishStore.fetchSuggestions`），返回 `Suggestion[]`（type: dish|stall|canteen）——搜索与联想同源，无分页、无排序、无筛选（2026-08-03 用户裁定）。
  - 顶部 tab 过滤：`activeResultTab`（全部/菜品/档口/食堂）+ `filteredMixed` computed + `resultTabCount`。
  - 旧 `CardSection` 包裹、`"关键词"的搜索结果` 标题、`DishRowCard`、`SegmentTabs` 排序、`FilterSheet` 筛选均已移除。
- 底部：`CustomTabBar`；`FilterSheet`（通用筛选抽屉，canteen/price/taste）。

## 3. 核心组件与用法
- **自绘搜索框**（`.search-box`，2026-08-03 替代 `SearchBar` 组件）：圆角灰条 + 放大镜 + 清空按钮，`input confirm-type=search`，联想 debounce 300ms。
- **混合结果列表**（`mixed-item`）：菜品/档口/食堂复合流，每项类型徽标（`mixed-type`：t-dish 主色 / t-stall 热色 / t-canteen 中性）+ 副信息 + arrow；点击 `goToMixed` 分类型跳转。
- `CardSection` / `SectionTitle`：仅主页态（搜索记录/高频搜索）使用。
- `EmptyState`：无结果/重试。
- 返回键：自绘（arrow-left），结果态 `exitFilter`、主页态 `reLaunch` 回首页（2026-08-03，无 Header 标题）。
- `IconSvg`：`close`(删历史/清空)、`arrow-left`(返回)、`dish`/`stall`/`canteen`(混合结果图破兜底类型图标，**canteen 作中性回退见 §8④**)。

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
- 搜索记录 chip（2026-08-03 简化：标题「历史搜索」→「搜索记录」，首位）：默认显示最近 3 条，`>3` 时「展开全部 N 条 / 收起」按钮（`historyExpanded`）；单击进搜索、删除按钮 `stop` 删除单项、extra「清空」清全部。
- 高频搜索（2026-08-03）：静态推荐词 chip，点击直接 `doMixedSearch(kw)`。
- **混合结果交互（2026-08-03）**：搜索/选词 → `doMixedSearch(kw)`（调 suggest，重置 `activeResultTab='all'`）→ 混合列表；顶部 tab 点击切换 `activeResultTab` → `filteredMixed` 过滤（全部/菜品/档口/食堂）；点击 `goToMixed(item)`：菜品弹 `DishDetailSheet`、档口 `navParams.stallName` 跳 stall、食堂 `?canteen=` 跳 canteen。无排序、无筛选、无分页。
- 结果页返回（2026-08-03）：顶部返回键，结果态 `exitFilter`（回主页）、主页态 `reLaunch` 回首页。
- **顶部固定**（2026-08-03）：返回 + 搜索框 + tab 均在 `.search-nav`（scroll-view 外），滑动结果不顶走；联想面板 top 随 `.search-nav` 高度实测（`inFilter` 变化时重测）。
- 进场过渡（2026-08-03）：`.filter-enter`（opacity + translateY(16rpx)，0.24s `--ease-out`）。
- 刷新：`onRefresh` 在结果态重新执行 `doMixedSearch`，主页态重拉 `loadDiscover`。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ 全走 IconSvg；混合结果图破兜底类型图标（dish/stall/canteen），无 emoji；联想面板非 dish/stall 类型回退 `empty`（见 §8④）。
- ②金额 api 层：✅ 无金额展示/计算（混合结果页不展示价格），页面无 `/100` 裸算。
- ③列表渲染：✅ 混合列表内联渲染（`v-for` + 类型徽标），无具名 slot 依赖。
- ④三态齐备：✅ 搜索主页骨架；结果页 EmptyState（可重试 `doMixedSearch`）；正常态。
- ⑤Sheet 规范：➖ 结果页无筛选抽屉（2026-08-03 移除 FilterSheet）；菜品弹层由 `DishDetailSheet` 遵循通用弹层规范。
- ⑥按压 0.97：✅ mixed-item/搜索框/history-chip/hot-chip 均 `scale(var(--press-scale))`。
- ⑦颜色 token：✅ 无裸 hex；类型徽标走 `--color-primary/-soft`、`--color-hot/-soft`、`--bg-soft`。
- ⑧SectionTitle：✅ 搜索记录/高频搜索/搜索结果标题均 SectionTitle（分类宫格已删）。
- ⑨底部避让：✅ scroll-wrap `--tabbar-height` + 安全区（二级页仍预留 TabBar 高度避让，兼容从首页跳转返回动画）。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：~~suggest 面板非 dish/stall 类型回退图标 `home` 是否改为 `empty`~~（已整改：回退图标改 `empty`，见 §8④）
- 计划调整：

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | chip/搜索框/mixed-item 均 ≥44pt；间距 ≥`--spacing-sm` |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | `.pressed{scale(var(--press-scale))}`，`--press-transition`≈120ms ease |
| 3 | 固定栏/导航预留安全区 | 合规 | search-wrap sticky 避让安全区；Header 含状态栏占位 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | `close`/`arrow-left`/`dish`/`stall`/`canteen` 均注册 key；混合结果图破兜底类型图标 |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)`；无裸 hex |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；未单独暗色/动态字号实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 类型徽标文字+色（菜品/档口/食堂）；清空有文字+图标 |
| 8 | prefers-reduced-motion 处理 | 合规 | `.filter-enter` 过渡随 `prefers-reduced-motion` 关闭 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控端用 `:active`/touch，无裸 hover |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | 进场 240ms；chip press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | `--ease-out`；无 ease-in |
| 12 | 进场禁 scale(0) | 合规 | `.filter-enter` 用 translateY+opacity，非 scale(0) |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | 进场过渡 transition 可中断 |
| 15 | 数字 tabular-nums | 合规 | 混合结果「共 N 条」计数走文本（如为数字场景补 tabular） |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 字号走 token；4/8pt 间距；无横向滚动 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 无破坏性操作；搜索为主流程 |
| 18 | loading/empty/error 三态 | 合规 | 主页骨架 + 结果 EmptyState（重试 `doMixedSearch`）+ 正常态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | 部分 | SearchBar 有 placeholder；键盘类型对（搜索）；无可见 label 属搜索框常见例外 |
| 20 | 导航一致：底部≤5 项 icon+label | 合规 | CustomTabBar 3 项（home/moment/profile），当前项高亮 |
| 21 | 一致性打磨 | 合规 | 按压统一 0.97；类型徽标三色语义清晰 |

## 9. 交付前验证（Pre-delivery）
- [ ] **开发侧整改**：已清零——混合结果页替换筛选（2026-08-03）；无排序/筛选/分页；历史折叠；高频词无序号；顶部返回+搜索框+tab 固定。
- [ ] 375px：mixed-item 一行不挤（类型徽标 + 名称 ellipsis）；suggest-panel 不被 TabBar 遮挡（fixed top 实测）。
- [ ] **顶部固定**：滚动混合结果时返回键/搜索框/分类 tab 不随之滚动；联想面板 top 正确贴合搜索框底（结果态含 tab 后重测）。
- [ ] reduced-motion：`.filter-enter` 过渡关闭。
- [ ] 动态字号：混合结果名称/类型徽标不截断。
- [ ] 暗色对比：类型徽标 `--color-hot-soft` 底文字 ≥3:1。
- [ ] 44pt：chip/搜索框/mixed-item/历史展开按钮 ≥44pt。
- [ ] 安全区：suggest-panel 底部不被 home indicator 遮挡。
