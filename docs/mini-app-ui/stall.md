# 档口详情 (stall)
- 路由: /pages/pages-detail/stall
- 分包: pages-detail 分包（实际 src 路径 pages/pages-detail/stall.vue）
- 源文件: src/pages/pages-detail/stall.vue
- 最后依据 skills 校对: 2026-08-03
- **2026-08-03 用户裁定（本轮精简）**：① 菜品卡只保留 名称 + 价格 + 标徽(tags) + 评分，**图片不放星级徽标、去掉简介/热度**（详情进菜品弹层看）；② 评价 tab 直接放评价列表，**去掉「用户评价」SectionTitle**；③ 档口 tab 直接补充 hero 缺失信息（位置/评分/营业时间/简介），**去掉「档口介绍」SectionTitle**。
- ⚠️ 本次重写（产品新要求，最终形态）：**tabBar 紧跟 hero 卡片下方（文档流，不沉底）**（菜品 / 评价 / 档口；数量为 tab 旁小字）；菜品 tab = **左侧一列筛选 + 右侧一列菜品矩形卡片**（美团外卖式，一行一个、左图右信息）；**默认停在「菜品」tab**；hero 卡片**去除「档口信息」大标题**（名称 +「信息有误？」/ 位置 / 标徽 / 评分 / 一句话简介）。详见 §2 与 §7。
- 历史变更说明：①早前"tab 不沉底"计划（旧 §2 变更①）经两次反复后**最终定为：紧跟 hero 卡下方、文档流、不沉底**（用户 2026-08-02 再次确认"tabbar 应该紧跟着 hero 卡片下方"）；②菜品右侧由双列瀑布流改为**单列卡片**（用户："右侧是一列菜品卡片（注意是一列，矩形卡片，一行只有一个）"）。本文档以最新裁定为准。

## 1. 页面定位
档口详情：图集横幅 + 档口信息 hero 卡（**无「档口信息」大标题**：名称 +「信息有误？」/ 位置 / 标徽(tags) / 评分 / 一句话简介）+ **紧跟 hero 卡下方的三段 tabBar（文档流，不沉底）** + 随 tab 切换的内容区。
- **tab1 菜品**：左侧一列分类（**从菜品实际 tags 动态派生**，无"全部"）+ 右侧**按分类分组的菜品卡片**（美团外卖式：每组标题+该类单列矩形卡，一行一个、左图右信息；点左类→右侧锚点跳转到该组开头）。
- **tab2 评价**：内联评价列表（含本人可删）。
- **tab3 档口**：档口完整介绍（位置/评分/营业时间/简介分区）。

由 dishStore.navParams(stallName/canteen) 定位。弱化「信息有误？」入口（ApplySheet）。默认选中「菜品」tab。

## 2. 布局结构
页面纵向结构（自上而下，关键设计变更点）：

```
Header :title="stallDetail?.name || '档口'" showBack
scroll-view（refresher）
 ├─ 加载骨架 .stall-skeleton（swiper/info/3×dish 骨架，shimmer）
 └─ 正常态（stallDetail）：
     ├─ ImageSwiper                     ← 横幅：档口图集
     ├─ CardSection 档口信息卡（hero）  ← 名称 +「信息有误？」/ 位置 / 标徽(tags) / 评分 / 一句话简介（无"档口信息"大标题）
     ├─ tabBar（紧跟 hero 卡下方，文档流不沉底）← 菜品(计数) / 评价(计数) / 档口
     └─ 内容区（随 tab 切换，v-if 互斥，销毁重建不残留）：
         ├─ 菜品 tab：左一列分类(cat-sidebar，动态派生) + 右按类分组菜品卡片（美团外卖式：组标题+单列卡，点左类锚点跳组开头）
         ├─ 评价 tab：CardSection 内联 ReviewItem 列表 / EmptyState
         └─ 档口介绍 tab：CardSection 单卡内有序分区（位置/评分/营业时间/简介）
     └─ CardSection 关联动态（低优先级，置底轻量区块；task-12.6）
 └─ 失败态 EmptyState text="档口信息加载失败" :retry
```

三态（加载骨架 / 正常 / 失败）齐备。

### 关键设计变更点（2026-08-02 用户裁定，最终形态）
1. **【hero 卡片去「档口信息」大标题】**：移除 `SectionTitle title="档口信息"`，直接展示：名称（`--font-h3` 700 + `--tracking-h3`）+ **「信息有误？」**（名称行最右弱链接，`--font-aux` `--text-tertiary`）→ 位置（location 图标 + 文本独立一行）→ **标徽（tags 徽章行）**（如 招牌/清真；后端 `StallDetail.tags` 未返回时由菜品 tags 派生高频前 3）→ 评分（star-filled + 数值）→ 一句话简介（2 行截断）。已落地 `stall.vue`。
2. **【tabBar 紧跟 hero 卡下方（不沉底，最终形态）】**：三段 tab（菜品 / 评价 / 档口）置于**文档流**——紧跟 hero 信息卡之后、内容区之前（`margin: var(--spacing-sm) var(--spacing-md) 0`，`--bg-card` 卡片样式 + `--shadow-bar-soft`）；**数量为 tab 标签旁小字**（`.tab-count`，`--font-aux` + `tabular-nums`）。`scroll-wrap` 不再需要底部 `--action-bar-height` + 安全区避让。早前"沉底/不沉底"反复，以本条为最终裁定。
3. **【菜品 tab = 左侧一列筛选 + 右侧一列菜品卡片（美团外卖式）】**：左侧竖向分类侧栏（`cat-sidebar`，纵向一列「全部/面食/米饭/小吃/…」）+ 右侧**单列菜品卡片列表**（`.dish-row-list`）：**一行一个矩形卡片**，左 160rpx 正方形圆角图（图破兜底 `dish` 占位 + 评分徽标叠加）+ 右侧名称/价格/标签/热度·简介。非双列瀑布流。
4. **【默认停在「菜品」tab】**：进入页默认 `activeTab = 'dishes'`。

> 代码现状：变更 1/2/3/4 均已在 `stall.vue` 落地（hero 无大标题 + 标徽行 + tabBar 紧跟 hero 不沉底 + 数量小字 + 左分类右单列卡片 + 默认菜品）。

## 3. 核心组件与用法
- `ImageSwiper`：档口图集横幅（图破兜底，中性语境建议 `empty`）。
- `CardSection`：hero 卡（无 SectionTitle 大标题）、评价/介绍内容卡（`SectionTitle` 仅内容卡内用，如「用户评价 (N)」「档口介绍」）。
- **tabBar（紧跟 hero 下方，文档流不沉底）**：三段式（菜品/评价/档口），卡片样式（`--bg-card` + `--shadow-bar-soft`）；选中项 `--color-primary` 高亮 + 底部 accent 条；**数量为 tab 标签旁小字**（`.tab-count`，`--font-aux` + `tabular-nums`）。
- **菜品单列卡片（美团外卖式）**：`.dish-row-card`（左 160rpx 正方形圆角图 + 右信息），行内 `TagLabel` 等价物 `.dish-row-tag`；图破兜底 `dish` 占位 + 评分徽标叠加。
- **左侧分类栏组件**（菜品 tab）：竖向 `cat-sidebar` 一列，项为 `categoryGroups`（**从菜品实际 tags 动态派生**，无"全部"；匹配 DISH_CATEGORIES key 得中文名、未命中用原文；一菜多 tag 进多组）；选中态主色底、非选中 `--bg-soft`；按压 `--press-scale`。
- **右侧分组滚动区**（美团外卖式）：`.dish-group-scroll`（scroll-view + `scroll-into-view` 锚点）；每组 = `.dish-group`（组标题：`.dish-group-label` 分类名 + `.dish-group-count` 数量小字）+ 该类 `.dish-row-card` 单列矩形卡（左 160rpx 正方形 8rpx 圆角图 + 右名称/价格/标签/热度·简介）；空态 `EmptyState text="该档口暂无菜品"`。
- `ReviewItem`：评价项（本人 `userId` 可删）。
- `EmptyState`：无菜品 / 无评价 / 失败重试。
- `ApplySheet`（entity-type STALL）：反馈/申请关闭弹层（spring 0.8/0.3 + ic-close + reduced-motion）。
- `DishDetailSheet`：菜品详情底部弹层（task-10：独立页 → sheet）。
- `IconSvg`：`location`(位置)、`star-filled`(评分)、`clock`(营业时间)、`arrow`(查看全部/反馈链接/关联动态)——均注册 key。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--bg-soft`、`--text-primary/secondary/tertiary`。
- 强调：`--color-primary`(位置图标/查看全部链接/tab 选中/分类选中)、`--color-star`(评分星)、`--text-tertiary`(弱化反馈链接)。
- 圆角/阴影：`--radius-card`、`--radius-tag`(分类项胶囊)、`--shadow-card`。
- 间距：`--spacing-xs/sm/md`（4/8pt 节奏）；tab 栏与信息卡间距 `--spacing-md`。
- 字号：`--font-h3`(档口名 + `--tracking-h3` 负字距)、`--font-small`(位置)、`--font-body`(简介/评分值/tab 文字)、`--font-aux`(「信息有误？」/分类标签/tab 数量小字)。
- 标徽：`--color-primary-soft` 底 + `--color-primary` 文字 + `--radius-tag`（tags 徽章，20rpx）。
- 动效：分类项/tab/菜品卡按下 `--press-scale` + `--press-transition`；骨架 shimmer。
- 布局：scroll-wrap `padding: var(--spacing-md) 0 0` + `padding-bottom: var(--spacing-lg)`（CardSection 自带左右 margin）；**无底部固定栏避让**（tab 不沉底）。

## 5. 交互与动效
- **tab 切换**：点 tab 切换三段内容（`v-if` 互斥销毁重建，不残留；**切前记录整页滚动位置、切后恢复**，`onScroll`/`switchTab`/`scrollTop`，避免回弹顶部）；选中项高亮 + accent 条 + `tabular-nums` 计数；切换微交互 <300ms。
- **分类点选锚点跳转（美团外卖式）**：左侧分类由菜品实际 tags 动态派生（`categoryGroups`，匹配 DISH_CATEGORIES 中文名、未命中用原文；一菜多 tag 进多组）；右侧按分类分组展示（`dish-group`：组标题 + 该类卡片）。点左侧分类 → 右侧 `scroll-into-view` 平滑滚动到该组开头（`scrollToCategory`/`scrollAnchor`，350ms 后重置锚点支持连续点击）；`activeCategory` 高亮。无任何菜品时 `EmptyState text="该档口暂无菜品"`。
- **按压反馈**：分类项、tab、菜品卡、关联动态均 `scale(var(--press-scale))`；反馈链接用 opacity 弱化（合规）。
- 评价「查看全部」不再跳转（改内联三段切换）；本人评价可删（`uni.showModal` 二次确认）。
- 下拉刷新；骨架 shimmer（reduced-motion 关动画）。
- ApplySheet / DishDetailSheet：spring 0.8/0.3 + ic-close + 下拉关闭手势 + reduced-motion 交叉淡入。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ location/star-filled/clock/arrow 合规；ImageSwiper 图破兜底已用 `empty`（组件层，见 §8④）；无 emoji。
- ②金额 api 层：✅ 菜品价格由 DishCard 展示已转元，页面无 `/100`。
- ③列表渲染：✅ 菜品为单列卡片（`.dish-row-card`）内联渲染，无具名 slot 依赖。
- ④三态齐备：✅ 骨架 / EmptyState(retry) / 正常态；菜品空态有 EmptyState。
- ⑤Sheet 规范：✅ ApplySheet/DishDetailSheet 套通用弹层（spring 0.8/0.3 + ic-close + 下拉关闭 + reduced-motion）。
- ⑥按压 0.97：✅ 分类项/tab/菜品卡/关联动态 `scale(var(--press-scale))`；反馈链接 opacity 弱化（合规）。
- ⑦颜色 token：✅ 无裸 hex。
- ⑧SectionTitle：✅ 仅内容卡（用户评价/档口介绍）用 SectionTitle；hero 卡**不用**（2026-08-02 用户裁定去大标题，非违规）。
- ⑨底部避让：✅ 无底部固定栏——tabBar 紧跟 hero 卡下方（文档流，不沉底），scroll-wrap 底部仅 `--spacing-lg`，无 `--action-bar-height`/安全区避让。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。⚠️ 本节为**当前与代码的差异清单**，开发者据此调整实现。

- **【2026-08-02 · hero 去大标题 + 标徽行（已落地）】**：移除「档口信息」SectionTitle；hero 直接展示 名称 +「信息有误？」/ 位置 / 标徽(tags) / 评分 / 一句话简介。标徽数据：优先后端 `StallDetail.tags`（已扩展类型与 api 映射），缺省由菜品 tags 派生高频前 3（`stallTags` computed）。
- **【2026-08-02 · tabBar 紧跟 hero 不沉底 + 数量小字（已落地，最终形态）】**：早前"底部 fixed 沉底"方案**废弃**；tabBar 改为文档流（紧跟 hero 卡下方、内容区之前），卡片样式 + 数量小字（`.tab-count`，`--font-aux` + `tabular-nums`）。scroll-wrap 移除底部 `--action-bar-height` + 安全区避让（tab 不沉底）。
- **【2026-08-02 · 菜品右侧按类分组 + 左侧动态派生（已落地）】**：右区由"过滤单类"改为**按分类分组展示**（美团外卖式）：左侧分类由菜品实际 tags **动态派生**（匹配 DISH_CATEGORIES key 得中文名，未命中用 tags 原文；一个菜多 tag 进多组）；右侧每组 = 分组标题（分类 + 数量小字）+ 该类菜品卡片（`.dish-row-card` 单列矩形，一行一个）；**点左侧分类 → 右侧 `scroll-into-view` 锚点跳转到该组开头**（`scrollToCategory`/`scrollAnchor`）。
- **【2026-08-02 · 圆角改小 + tab 互斥（已落地）】**：菜品卡 `border-radius: 12rpx`（缩略图 8rpx，原 `--radius-card` 16px 过大）；菜品/评价/档口内容区由 `v-show` 改 **`v-if` 互斥**（切 tab 销毁重建，消除菜品残留）。
- **【2026-08-03 · 菜品卡精简（已落地）】**：卡片只保留 **名称 + 价格 + 标徽(tags) + 评分行**；**图片上不放星级徽标**（原 `.dish-row-rating` 图片角标移除）；**去掉简介/热度**（`.dish-row-meta` 删除）——菜品详情进弹层看。
- **【2026-08-03 · 评价/档口 tab 去大标题（已落地）】**：评价 tab 直接放 `ReviewItem` 列表（**无「用户评价 (N)」SectionTitle**）；档口 tab 直接放 hero 缺失信息行（位置/评分/营业时间/简介，**无「档口介绍」SectionTitle**）。
- **【变更③ · 默认「菜品」tab（已落地）】**：`activeTab = ref('dishes')`，进入即菜品分类索引 + 单列卡片。
- **裁定（①菜品分类是否随右侧滚动自动高亮）**：**点选锚点跳转为 MVP 基线**（已落地：`scrollToCategory`/`scrollAnchor`，点左侧→右侧 `scroll-into-view` 到该组开头）。**双向滚动联动（右侧滚动时左栏自动高亮当前分类）列为后续增强**，需为分组监听滚动事件，非本轮范围。
- **裁定（②「全部」分类）**：**不再有「全部」分类**。左侧分类由菜品实际 tags 动态派生（`categoryGroups`，匹配 DISH_CATEGORIES key 得中文名、未命中用原文；一菜多 tag 进多组），全部菜品以"分组标题 + 单列卡片"形式完整展示在右侧滚动区，无需"全部"入口；无任何菜品时 `EmptyState text="该档口暂无菜品"` 兜底。
- 待整改项：ImageSwiper 图破兜底按一致性规则①应为中性占位 `empty` —— **已合规**（`ImageSwiper.vue` `.image-swiper-placeholder` 用 `<IconSvg name="empty">`，组件层已落 `empty`，见 §8④），不再是待整改。

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | 分类项/tab/菜品卡/关联动态 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | 分类项 `.cat-item:active` / tab / 菜品卡 / 关联动态 `scale(var(--press-scale))` + `--press-transition` |
| 3 | 固定栏/导航预留安全区 | 合规 | Header 含状态栏；scroll-view 底部留 `safe-area-inset-bottom`（变更①落地后移除 `--action-bar-height` 避让，见 §7） |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | ImageSwiper 图破兜底已落 `empty`（`ImageSwiper.vue` `.image-swiper-placeholder` `<IconSvg name="empty">`）；location/star-filled/clock/arrow 合规；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；弱化反馈链接 `--text-tertiary` 对比为全局 token 依赖项，交付前统一真机实测（见 §9） |
| 7 | 不靠颜色 alone 传意 | 合规 | 分类选中有主色底 + cat-dot 圆点 + 文字；评分星图标+数字；无纯色状态 |
| 8 | prefers-reduced-motion 处理 | 合规 | ApplySheet/DishDetailSheet 降级交叉淡入；骨架 shimmer 关动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | Sheet enter~300ms、exit 更短；press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | Sheet `--ease-drawer`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | Sheet 自底部；无 scale(0) |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 分类项/tab 过渡均 transform/opacity；无 `transition:all` |
| 14 | 可中断动效 | 合规 | ApplySheet/DishDetailSheet 手势可中断 |
| 15 | 数字 tabular-nums | 合规 | tab 数量 `.tab-count` 已 `tabular-nums`（stall.vue）；菜品价格 `.dish-row-price`、热度 `.dish-row-meta-text` 已 `tabular-nums`；评分值 `.info-rating-text` 为静态小数字 |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 主文 `--font-body`/`--font-caption`；分类标签 `--font-aux`(22rpx) 为辅助标签；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 反馈入口弱化为小文字链接；tab 为页内分段非 CTA；无主 CTA 抢占 |
| 18 | loading/empty/error 三态 | 合规 | 骨架 / EmptyState(retry, 无菜品/无评价/失败) / 正常态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 非 tab 页；返回由 Header；tab 为页内分段非全局导航 |
| 21 | 一致性打磨 | 合规 | 信息架构（左侧分类 + 双列瀑布流 + 三段 tab）；按压统一 0.97；Sheet 规范同源 |

## 9. 交付前验证（Pre-delivery）
- [ ] **开发侧整改**：已清零——hero 去大标题 + 标徽行、tabBar 紧跟 hero 不沉底 + 数量小字、菜品右侧单列卡片、`.tab-count`/价格/热度 `tabular-nums` 均已落地（stall.vue）。
- [ ] 375px：左筛选 + 右单列卡片每行一卡、不重叠、无横向滚动；tabBar 数量小字不挤压。
- [ ] reduced-motion：ApplySheet/DishDetailSheet 交叉淡入；骨架 shimmer 停。
- [ ] 动态字号：档口名/菜品名/分类项/标徽/简介不截断。
- [ ] 暗色对比：弱化「信息有误？」`--text-tertiary`、评分星 `--color-star`、分类选中主色底 + 白字为全局 token 依赖项，交付前统一真机实测。
- [ ] 44pt：分类项/tab/菜品卡/关联动态 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡；无底部固定栏（tab 不沉底，无 home indicator 遮挡问题）。
