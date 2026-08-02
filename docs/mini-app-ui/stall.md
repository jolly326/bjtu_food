# 档口详情 (stall)
- 路由: /pages/pages-detail/stall
- 分包: pages-detail 分包（实际 src 路径 pages/pages-detail/stall.vue）
- 源文件: src/pages/pages-detail/stall.vue
- 最后依据 skills 校对: 2026-08-02
- ⚠️ 本次重写（产品新要求）：tab 栏改为**不沉底**、置于档口信息卡下方；菜品 tab = **左侧竖排分类栏 + 右侧瀑布流**（美团外卖式）；**默认停在「菜品」tab**。详见 §2 与 §7。

## 1. 页面定位
档口详情：图集横幅 + 档口信息卡（hero，含名字/位置/简介/评分）+ **信息卡下方的三段 tab 栏（不沉底）** + 随 tab 切换的内容区。
- **tab1 菜品**：左侧竖排分类栏（全部/面食/米饭/小吃/…）+ 右侧菜品瀑布流（美团外卖式分类索引联动）。
- **tab2 评价**：内联评价列表（含本人可删）。
- **tab3 档口介绍**：档口完整介绍（位置/评分/营业时间/简介分区）。

由 dishStore.navParams(stallName/canteen) 定位。弱化「反馈信息有误」入口（ApplySheet）。默认选中「菜品」tab。

## 2. 布局结构
页面纵向结构（自上而下，关键设计变更点）：

```
Header :title="stallDetail?.name || '档口'" showBack
scroll-view（refresher）
 ├─ 加载骨架 .stall-skeleton（swiper/info/3×dish 骨架，shimmer）
 └─ 正常态（stallDetail）：
     ├─ ImageSwiper                     ← 横幅：档口图集
     ├─ CardSection 档口信息卡（hero）  ← 名字/位置/简介/评分 + 弱化反馈链接
     ├─ tab 栏（三段）【变更①：不沉底】← 紧跟信息卡下方，菜品(6)/评价(0)/档口介绍
     └─ 内容区（随 tab 切换，v-show 互斥）：
         ├─ 菜品 tab：左竖排分类栏(cat-sidebar) + 右 WaterfallList 瀑布流【变更②】
         ├─ 评价 tab：CardSection 内联 ReviewItem 列表 / EmptyState
         └─ 档口介绍 tab：CardSection 单卡内有序分区（位置/评分/营业时间/简介）
     └─ CardSection 关联动态（低优先级，置底轻量区块；task-12.6）
 └─ 失败态 EmptyState text="档口信息加载失败" :retry
```

三态（加载骨架 / 正常 / 失败）齐备。

### 关键设计变更点（产品新要求）
1. **【变更① · tab 栏不沉底】**：原实现为 `position: fixed; bottom: 0` 的底部固定 tabBar（`.detail-tabs`）；本次改为**普通文档流内块级 tab 栏**，位于 `ImageSwiper` + 档口信息卡**之后**、内容区**之前**。不再占用底部安全区、不遮挡滚动内容、不随内容滚动（随外层 scroll-view 自然滚动，贴信息卡下缘）。
2. **【变更② · 菜品 tab = 左侧竖排分类栏 + 右侧瀑布流】**：保留/沿用美团外卖式左侧竖向分类侧栏（`cat-sidebar`，纵向一列「全部/面食/米饭/小吃/…」），右侧 `WaterfallList` 瀑布流随分类点选联动过滤。**不是**横向标签条，也**不是**沉底 tab。
3. **【变更③ · 默认停在「菜品」tab】**：进入页默认 `activeTab = 'dishes'`，无需用户手动切 tab 即可看到菜品分类索引 + 瀑布流。

> 代码现状说明：变更②（左侧分类栏）与变更③（默认菜品）当前代码已落地（`stall.vue` 中 `cat-sidebar`/`activeCategory`/`activeTab='dishes'`）；变更①（tab 不沉底）为本次设计目标，当前代码仍是底部固定 `detail-tabs`，需按 §7 调整。

## 3. 核心组件与用法
- `ImageSwiper`：档口图集横幅（图破兜底，中性语境建议 `empty`）。
- `CardSection` / `SectionTitle`：档口信息 hero 卡、评价/介绍内容卡的分区标题（accent 条；卡片内 `noMargin`）。
- **tab 栏**：三段式（菜品/评价/档口介绍），置于信息卡下方；选中项 `--color-primary` 高亮 + 底部 accent 条 + 数字 `tabular-nums`。
- **左侧分类栏组件**（菜品 tab）：竖向 `cat-sidebar` 一列，项含「全部」+ 该档口实际命中品类（派生自 `DISH_CATEGORIES` ∩ 菜品 tags）；选中态主色底、非选中 `--bg-soft`；按压 `--press-scale`。
- `WaterfallList`：菜品瀑布流（双列，禁用具名 slot，父级只 `@card-click` 上抛）；空态用 `EmptyState text="该分类暂无菜品"`。
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
- 字号：`--font-h3`(档口名)、`--font-caption`(位置/简介)、`--font-body`(评分值/tab 文字)、`--font-aux`(分类标签/反馈链接/查看全部)。
- 动效：分类项/tab 按下 `--press-scale` + `--press-transition`；骨架 shimmer；瀑布流卡片入场沿用 WaterfallList 内部。
- 布局：scroll-wrap `padding: var(--spacing-md) 0 0`（CardSection 自带左右 margin）；**底部不再需要 `--action-bar-height` + 安全区避让**（因 tab 已不沉底）。

## 5. 交互与动效
- **tab 切换**：点 tab 切换三段内容（`v-show` 互斥）；选中项高亮 + accent 条 + `tabular-nums` 计数；切换微交互 <300ms。
- **分类点选联动**：点左侧分类项 → 右侧瀑布流联动过滤为该类菜品（美团外卖式索引）；`activeCategory` 高亮；无命中分类显示 `EmptyState`。
- **按压反馈**：分类项、tab、菜品卡、关联动态均 `scale(var(--press-scale))`；反馈链接用 opacity 弱化（合规）。
- 评价「查看全部」不再跳转（改内联三段切换）；本人评价可删（`uni.showModal` 二次确认）。
- 下拉刷新；骨架 shimmer（reduced-motion 关动画）。
- ApplySheet / DishDetailSheet：spring 0.8/0.3 + ic-close + 下拉关闭手势 + reduced-motion 交叉淡入。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：⚠️ location/star-filled/clock/arrow 合规；ImageSwiper 图破兜底（中性语境应 `empty`，建议整改见 §7）；无 emoji。
- ②金额 api 层：✅ 菜品价格由 DishCard 展示已转元，页面无 `/100`。
- ③WaterfallList 禁 slot：✅ 只经 `@card-click` 上抛，不传具名 slot。
- ④三态齐备：✅ 骨架 / EmptyState(retry) / 正常态；菜品空态有 EmptyState。
- ⑤Sheet 规范：✅ ApplySheet/DishDetailSheet 套通用弹层（spring 0.8/0.3 + ic-close + 下拉关闭 + reduced-motion）。
- ⑥按压 0.97：✅ 分类项/tab/菜品卡/关联动态 `scale(var(--press-scale))`；反馈链接 opacity 弱化（合规）。
- ⑦颜色 token：✅ 无裸 hex。
- ⑧SectionTitle：✅ 档口信息/用户评价/档口介绍均 SectionTitle（`noMargin` 适配卡片内边距）。
- ⑨底部避让：⚠️ **变更①后 tab 不沉底，不再需要底部固定栏**。当前代码仍有 `position: fixed` 的 `detail-tabs` 需改为文档流内块级（见 §7）；`scroll-wrap` 底部 `--action-bar-height` 避让在 tab 上移后应移除/核减。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。⚠️ 本节为**当前与代码的差异清单**，开发者据此调整实现。

- **【变更① · tab 不沉底（待代码调整）】**：将底部固定 `detail-tabs`（`position:fixed;bottom:0`）改为**信息卡下方、内容区之前的文档流内块级 tab 栏**。同步：`scroll-wrap` 去掉底部 `--action-bar-height` + 安全区避让（或核减），`--shadow-bar-soft`/`border-top` 可移作分隔线。默认仍 `activeTab='dishes'`（变更③）。
- **【变更② · 左侧竖排分类栏（已落地）】**：保留美团外卖式 `cat-sidebar`（竖向一列「全部/面食/米饭/小吃/…」）+ 右侧 `WaterfallList` 联动；非横向标签条、非沉底 tab。
- **【变更③ · 默认「菜品」tab（已落地）】**：`activeTab = ref('dishes')`，进入即菜品分类索引 + 瀑布流。
- 待讨论项：菜品分类项是否需随右侧瀑布流滚动自动高亮（目前为点选联动，如需「滚到哪类高亮哪类」可加双向联动）。
- 待讨论项：全部菜品折叠阈值（旧版 >6 折叠）在「全部」分类下是否还需保留折叠遮罩（瀑布流双列场景折叠遮罩适用性需复核）。
- 待整改项：ImageSwiper 图破兜底按一致性规则①应为中性占位 `empty`（组件层，页级建议标注）。
