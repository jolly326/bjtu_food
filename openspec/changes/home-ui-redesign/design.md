## Context

现状（来自 client/src 实际代码）：
- 全站无原生 tabBar，home / community / profile 均经 `navigateTo` 进入；代码注释中虽有 `CustomTabBar` 字样，但并无该组件实例。
- 首页头部 `AppHeader` 变体 `home` 渲染 `user-chip`（头像）+ `home-search`（搜索框），背景为 `--color-primary`（朱砂红）。
- 首页 `pages/home/index.vue` 结构：骨架屏 → `BroadcastBar` → `UniversalGrid` → `FilterBar`（横滑品类卡）→ `HomeFeed`（瀑布流，按 `selectedKey` 品类过滤）。
- `stores/dish.ts` 已持有 `canteenList`（来自 `canteenApi.getCanteenList()`），食堂数据可用。
- 全局 UI 红线（project_spec §4.9）：`@tap`、按压 `scale(var(--press-scale))`、颜色走 token（禁裸 hex）、`prefers-reduced-motion` 降级、`IconSvg` 图标。

## Goals

1. 新增常驻底部菜单栏，区分「首页 / 社区 / 我的」。
2. 首页头部头像框 → 筛选按钮；点击弹出红色食堂筛选下拉，过滤下方内容区。
3. 移除广播条、万能区、横滑品类卡；首页筛选维度由品类改为食堂。

## Non-Goals

- 不改动 community / profile 页头部与内容（仅为其挂底部菜单栏）。
- 不新增收藏/品类横滑等被移除能力的替代功能。
- 不引入原生 tabBar（见决策 D1）。

## Decisions

- **D1 — 自定义 TabBar + reLaunch（非原生 tabBar）**
  采用新增 `components/TabBar.vue` 自定义组件，点击 `uni.reLaunch` 到目标主根页（重置栈、避免叠加历史），active 态由 `getCurrentPages()` 路由路径推导。
  *替代方案*：原生 `pages.json` tabBar。但原生 tabBar 要求 home/community/profile 为 tab 页，会破坏现有大量 `navigateTo` 调用方（详情/活动/反馈等），且受原生样式限制，与项目「无原生 tabBar」约定相悖。因此选自定义方案，非破坏性。

- **D2 — 红色下拉复用 `--color-primary` token**
  下拉面板与 header 共用同一红色块（同背景、零间隙、紧贴 header 底部），背景统一用设计 token `--color-primary`（与 header 同源，亮/暗模式均无缝），**禁止裸 hex**，以符合 UI 红线。下拉为绝对定位面板 + 全屏遮罩（承接外部点击关闭），非底部 sheet。

- **D3 — 筛选模型：品类 → 食堂**
  移除 `FilterBar` 品类过滤；`HomeFeed` 改为消费单一 `selectedCanteenId`（`null`=全部）。`stores/dish.ts` 新增 `fetchDishesByCanteen(canteenId)`（或扩展 `fetchFilterDishes` 支持 `type:'canteen'`）。若后端菜品列表未支持 `canteenId` 参数，需在 `server` 的 dish 列表接口补充该查询参数（走后端契约，需技术负责人确认）。

- **D4 — 头像框移除与入口收敛**
  头部 home 变体去除 `user-chip`/`goProfile` 接线；个人页访问统一收敛到底部菜单栏「我的」项。保留 `AppHeader` 其他变体（detail/profile）的 avatar 能力不受影响。

- **D5 — 组件清理**
  从首页卸载 `BroadcastBar` / `UniversalGrid` / `FilterBar` 并删除其文件；`types/filter-tab.ts`（`FilterTab` 类型）若无其他引用一并删除。`BroadcastBar.vue` `UniversalGrid.vue` `FilterBar.vue` 经检索仅被 home 引用，可安全删除。骨架屏对应 `sk-broadcast / sk-universal / sk-filter` 同步移除。

## Risks

- **R1 后端 `canteenId` 过滤可能缺失**：`/dishes` 若不接受 `canteenId`，需后端改动（影响 `server` 与契约）。缓解：先确认接口，必要时在 tasks 中单列后端任务。
- **R2 reLaunch 重置滚动位置**：切换 tab 会丢失该页滚动位置（tab 常规行为，可接受）。
- **R3 食堂列表加载时机**：进入首页时 `canteenList` 可能为空，下拉需展示加载态或先懒加载。缓解：首页 `onShow` 确保 `ensureCanteens()`。
- **R4 视觉衔接缝隙**：红色面板与 header 间出现 1px 间隙或不同红色。缓解：面板与 header 同源 token、绝对定位紧贴、消除 margin/border。

## Migration & Rollback

- 纯前端为主，属新增 + 删除，无数据迁移。回滚：git revert 相关文件即可恢复旧首页与无菜单栏状态。
- 若涉及后端 `canteenId` 参数，按 `server/src/main/resources/db` 无改动（仅接口查询参数），回滚对应 controller/mapper 即可。
