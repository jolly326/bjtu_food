> 实施偏差记录（供技术负责人复核）：
> - **5.2 `types/filter-tab.ts` 未删除**：扩展 `FilterTab`（`canteen` 类型 + `canteenId`）后，`stores/dish.ts` 仍依赖该类型，故保留；Bash/grep 0 引用针对三个已删除的组件文件（`BroadcastBar/UniversalGrid/FilterBar`）。
> - **4.2 后端无需改动**：经核查 `server` 的 `DishQueryReq` 已含 `canteenId`，且 `client` 的 `DishQuery`/`api/dish.ts searchDishesPage` 已映射到 `canteenId`，故仅补充 `CanteenInfo.id` 与 `getCanteenList` 映射即可让前端按食堂过滤。
> - **筛选 chip 文案省略**：`AppHeader` home 变体的 `filter-chip` 限定 `max-width:46%`，超长食堂名由 CSS `text-overflow:ellipsis` 截断，保证右侧搜索框尺寸不被挤压（符合 §4.9 红线与设计更新）。

## 1. 底部菜单栏 TabBar（capability: tab-bar）

- [x] 1.1 新增 `client/src/components/TabBar.vue`：三项目录（首页/社区/我的），使用 `IconSvg`（home/community/profile）+ 文字标签；active 态由 `getCurrentPages()` 路由路径推导；事件用 `@tap`、按压 `scale(var(--press-scale))`、active 用语义 token、支持 `prefers-reduced-motion`。
- [x] 1.2 在 `pages/home`、`pages/community`、`pages/profile` 三个主包页模板内各自挂载 `<TabBar/>`；显隐与高亮由 `stores/route.ts`（`tabVisible` + `showTab()`）控制，仅在 home/community/profile 主根页显示（二级页不挂载即不出现）。验证三主根页可见、二级页不出现。
- [x] 1.3 接 tab 点击 → `uni.reLaunch` 到对应主根页（home/community/profile），验证跳转 + active 高亮正确、原栈重置。
- [x] 1.4 回归：验证既有 `navigateTo` 二级页流程（详情/活动/反馈/编辑等）不受影响，返回后首页 TabBar 正常。

## 2. 首页头部筛选按钮（capability: home-filter）

- [x] 2.1 修改 `components/AppHeader.vue` 的 `home` 变体：移除 `user-chip` 头像框，改为「筛选」chip（`IconSvg` filter 图标 + 当前食堂名，超长省略），`@tap` 向上 emit `filter` 事件；保留 `home-search` 搜索框。
- [x] 2.2 更新 `pages/home/index.vue`：移除 header 的 `avatar`/`goProfile` 接线；监听 `filter` 事件切换下拉显隐。

## 3. 红色食堂筛选下拉（capability: home-filter）

- [x] 3.1 新增食堂筛选下拉组件 `components/CanteenFilter.vue`：面板背景用 `--color-primary` token（与 spec/design D2 一致）、与 header 同红色块且零间隙紧贴；列出 `dishStore.canteenList` 选项 + 「全部」；点击外部遮罩关闭；`prefers-reduced-motion` 降级（仅透明度淡入，无位移过冲）。验证展开/收起与视觉衔接。
- [x] 3.2 选中食堂 → 设置 `selectedCanteenId`（null=全部）并关闭下拉；内容区随之过滤。验证选中 X 仅显示 X、选「全部」恢复全部。

## 4. 内容区按食堂过滤（capability: home-filter）

- [x] 4.1 `stores/dish.ts` 扩展 `fetchFilterDishes`/`loadMoreFilterDishes` 支持 `type:'canteen'`（调用 `searchDishesPage({ canteenId })`）；`HomeFeed` 消费 `filterList`（默认全部）。验证瀑布流随选择更新。
- [x] 4.2 确认后端菜品列表 `canteenId` 查询参数已支持（`DishQueryReq.canteenId` + 前端 `DishQuery`/`searchDishesPage` 映射已存在），补齐 `CanteenInfo.id` 与 `getCanteenList` 映射即可。无需改后端。

## 5. 移除冗余区块与组件清理（capability: home-filter）

- [x] 5.1 从 `pages/home/index.vue` 模板 + 脚本移除 `BroadcastBar`、`UniversalGrid`、`FilterBar` 的使用；同步移除骨架屏 `sk-broadcast / sk-universal / sk-filter`（替换为贴合瀑布流的骨架）。
- [x] 5.2 删除不再使用的组件文件：`components/BroadcastBar.vue`、`components/UniversalGrid.vue`、`components/FilterBar.vue`（已删除）；`types/filter-tab.ts` 因 `stores/dish.ts` 仍依赖而保留（见顶部偏差）。
- [x] 5.3 校验 `npm run type-check` 通过（无悬挂 import / 类型错误）。

## 6. 收尾验证

- [x] 6.1 执行 `npm run type-check`（EXIT=0）与 `npm run build:mp-weixin`（Build complete）；自检 UI 红线：无裸 hex（`filter-chip` 用 `rgba(255,255,255,.18)` 叠白）、无 `@click`（全 `@tap`）、按压 `scale(var(--press-scale))`、图标走 `IconSvg`、减少动效纳入 `.cf-panel`。
