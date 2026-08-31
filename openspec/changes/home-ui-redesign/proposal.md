## Why

当前小程序没有常驻底部导航：home / community / profile 三页都靠 `navigateTo` 进入，用户无法一键在三个主区之间切换。同时首页信息过载——顶部头像框冗余（个人页可由底部入口进入）、首屏堆叠「广播条 + 最新活动/反馈菜品万能区 + 横滑品类卡」，用户缺少按食堂（canteen）快速筛选菜品的能力。本次重做首页交互骨架与全局导航，让三个主区清晰可切、首页聚焦「按食堂筛选」。

## What Changes

- **新增底部菜单栏（TabBar）**：在 home / community / profile 三个主根页常驻底部菜单栏，含「首页 / 社区 / 我的」三项，当前路由决定高亮态。
- **首页头部改造**：去掉搜索框左侧的头像框（`user-chip`），改为「筛选」按钮（点击 `@tap` 触发）。
- **新增食堂筛选下拉**：点击筛选按钮向下弹出红色背景面板（`--color-primary-surface`，与 header 同一红色块、视觉衔接无间隙），容器内列出各食堂选项，选中后筛选下方内容区（瀑布流）。
- **首页去除冗余区块**：移除「广播条 `BroadcastBar`」「最新活动/反馈菜品万能区 `UniversalGrid`」「横向滑动品类卡 `FilterBar`」。
- **首页筛选维度变更**：内容区由「按品类横滑」改为「按食堂筛选」（默认全部），由新下拉驱动。
- 删除上述不再使用的组件文件与类型（`BroadcastBar.vue` / `UniversalGrid.vue` / `FilterBar.vue` / `types/filter-tab.ts`，需先确认无其他引用）。

## Capabilities

### New Capabilities
- `tab-bar`: 全局底部菜单栏，区分「首页 / 社区 / 我的」三个主区，并定义其导航与视觉规范。
- `home-filter`: 首页头部筛选按钮 + 红色食堂筛选下拉 + 内容区按食堂过滤，并移除广播/万能区/横滑品类卡。

### Modified Capabilities
- （无既有 spec；本仓库此前未使用 `openspec/specs/`，均为新增能力）

## Impact

- **client/src**：`App.vue`（挂载 TabBar）、`components/AppHeader.vue`（home 变体去头像、加筛选按钮）、`pages/home/index.vue`（接筛选事件、去广播/万能/横滑、接食堂筛选）、`components/HomeFeed.vue`（按食堂过滤）、`stores/dish.ts`（食堂筛选能力）、`components/TabBar.vue`（新增）、食堂筛选下拉组件（新增）。
- **删除**：`components/BroadcastBar.vue`、`components/UniversalGrid.vue`、`components/FilterBar.vue`、`types/filter-tab.ts`（如无其他引用）。
- **后端**：菜品列表接口需支持 `canteenId` 查询参数（假设 `/dishes` 已支持；若未支持需在 `server` 增加该参数）。涉及改动须走后端契约，由技术负责人确认。
- **规范约束**：所有改动须过 UI 红线（§4.9）：用 `@tap`、按压 `scale(var(--press-scale))`、颜色走 token（红色用 `--color-primary-surface` 而非裸 hex）、`prefers-reduced-motion` 降级、图标走 `IconSvg`。
