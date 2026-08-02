# 首页 (home)
- 路由: /pages/home/index
- 分包: 主包
- 源文件: src/pages/home/index.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
小程序入口首页，聚合 Banner 推荐、滚动广播通知、食堂横滑入口与热门菜品瀑布流，面向游客/学生打开即看、快速分流到详情。

## 2. 布局结构
- 顶部：`Header title="食在交大"`（自定义导航，高度含状态栏占位）。
- 主滚动区 `scroll-view`（scroll-y + 下拉刷新 `refresher-enabled` + 触底 `@scrolltolower`）。
- 区块顺序（均 `enter-up` 错峰淡入，配合 `--enter-i` 实现 30–80ms 错峰）：
  1. Banner 轮播 `swiper`（无数据轻量占位「暂无推荐」）。
  2. 广播通知条 `broadcast-section`（细长 ticker，图标+文本上下轮换）。
  3. 食堂入口 `SectionTitle` + 横滑卡片 `scroll-view scroll-x`。
  4. 热门菜品 `SectionTitle` + `WaterfallList` + 触底 footer（加载中/已经到底）。
- 三态：加载中（`.home-skeleton` 骨架：banner/canteen/grid 三块）· 全空（`isAllEmpty` 时 `EmptyState` 内联：empty 图标+「暂时没有内容/加载失败」+下拉重试提示）· 正常态。
- 底部：`CustomTabBar current="/pages/home/index"`（固定 100rpx + 安全区）。

## 3. 核心组件与用法
- `Header`：页面标题栏。
- `WaterfallList :list @card-click="goToDetail"`：双列瀑布流，禁具名 slot，事件上抛。
- `CustomTabBar`：主包 tab 栏（icon+label ≤5 项，当前项高亮）。
- `SectionTitle title="食堂入口" / "热门菜品"`：分区标题（带 accent 条）。
- `IconSvg`：`empty`（全空态，已注册）、`broadcast`（广播图标，已注册）、`home`（食堂无图占位，**一致性瑕疵见 §8④**）、swiper 内无图标。
- 原生 `swiper` / `swiper-item`：Banner 轮播（autoplay/circular/indicator-dots）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--text-white`、`--text-secondary`、`--text-tertiary`。
- 圆角/阴影：`--radius-card`、`--shadow-card`、`--blur-*`(未直接用，banner overlay 用 `--overlay-dark-strong/soft`)。
- 间距：`--spacing-sm/md/lg/xl`（4/8pt 栅格节奏）。
- 字号：`--font-h2`(swiper title)、`--font-body`(subtitle)、`--font-aux`(ticker/footer)、`--font-caption`(canteen name)。
- 动效：`--press-scale`(`.canteen-card.pressed`/`.broadcast-bar.pressed` 均 `scale(var(--press-scale))`)、`--ease-out`(ticker 动画)、`--press-transition`。
- 布局：`--tabbar-height` + `env(safe-area-inset-bottom)`（scroll-wrap 底部留白）。
- 字距：`--tracking-h2`(swiper title，标题负字距)。
- Native 例外：`SWIPER_INDICATOR_ACTIVE_COLOR` / `SWIPER_INDICATOR_COLOR`（`src/constants/ui.ts` 常量，对应语义色，已在 uni.scss 登记，不计裸 hex 违规）。

## 5. 交互与动效
- 卡片按下：touchstart/mousedown 置 `pressed` → `.pressed { transform: scale(var(--press-scale)) }`（canteen-card、broadcast-bar），press 反馈即时、`transition: var(--press-transition)` ≈120ms ease，不位移布局。
- Banner 点击 `handleBannerTap` 按 `targetType`（DISH→dish 详情（经 `DishDetailSheet` 弹层）；URL→**复制链接 + toast**（webview 页已移除，见 task-07）；其他→canteen/stall/community 分发）。
- 广播条：每 3s 轮换一条，`broadcast-up` 上滑入场；`prefers-reduced-motion` 降级为 `broadcast-fade` 纯淡入（保留 opacity 去 transform）。
- 下拉刷新、热门列表触底无限加载（footer spinner 转圈 + 「已经到底啦」）；`prefers-reduced-motion` 下 footer-spinner 关动画。
- 进场：区块经 `enter-up` 错峰交叉淡入，仅 transform+opacity，无 `scale(0)`、无 `transition:all`。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：⚠️ `empty`/`broadcast` 合规；`home` 用于食堂无图占位，按一致性规则①中性占位应为 `empty`（DishCard 之外禁止用 `home`/`dish` 冒充中性占位），建议改为 `empty`。
- ②金额 api 层：✅ 首页无金额裸算（热门菜品价格由 WaterfallList 内部已转元展示）。
- ③WaterfallList 禁 slot：✅ 仅 `:list` + `@card-click`，未传 slot。
- ④三态齐备：✅ 骨架 / EmptyState / 正常态均覆盖；banner 无数据有轻量占位。
- ⑤Sheet 规范：➖ 首页无底部 Sheet（广播/轮播非弹层），不适用。
- ⑥按压 0.97：✅ 两处 `.pressed` 均用 `scale(var(--press-scale))`，值固定 0.97。
- ⑦颜色 token：✅ 无裸 hex；swiper indicator 用已登记常量。
- ⑧SectionTitle：✅ 「食堂入口」「热门菜品」均用 SectionTitle。
- ⑨底部避让：✅ scroll-wrap 用 `--tabbar-height` + 安全区。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：~~食堂无图占位图标 `home` 是否按一致性规则改为 `empty`~~（已整改：占位改用 `empty`，见 §8④）
- 计划调整：

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | TabBar/卡片/广播条点击区均 ≥44pt；卡片间距 `--spacing-sm`(16rpx≈8pt) 起 |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | `.pressed{transform:scale(var(--press-scale))}` + `--press-transition:transform .12s ease` |
| 3 | 固定栏/导航预留安全区 | 合规 | CustomTabBar + scroll-wrap 用 `--tabbar-height` + `env(safe-area-inset-bottom)`；Header 含状态栏占位 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | 已整改：占位改用 `empty`；其余 `empty`/`broadcast` 合规且为已注册 key |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全部 `var(--…)`；swiper indicator 用已登记常量（native 例外） |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 体系满足语义；但首页未单独跑暗色/动态字号实测，依赖全局 token，建议交付前验证 |
| 7 | 不靠颜色 alone 传意 | 合规 | 空态/加载有图标+文字；无纯色状态传达 |
| 8 | prefers-reduced-motion 处理 | 合规 | ticker/footer-spinner 降级交叉淡入、关动画；保留 opacity |
| 9 | hover 门控 @media(hover:hover) | 合规 | 小程序触控交互用 `:active`/touch 事件，无裸 `:hover` 误触 |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | press 120ms；ticker 依 3s 轮换非高频动画；无高频动作动画 |
| 11 | 自定义缓动，禁 ease-in | 合规 | ticker/进场用 `--ease-out`(cubic-bezier(.23,1,.32,1))；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | `enter-up` 用 translateY+opacity（非 scale(0)）；无 popover |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 动效均 transform/opacity；无 `transition:all` |
| 14 | 可中断动效 | 合规 | 进场/反馈用 transition 可中断；无 keyframes 重播风险 |
| 15 | 数字 tabular-nums | 部分 | 价格由 WaterfallList 内部展示，本页无独立计数；若价格/计数建议 `tabular-nums` 防位移（组件层落实） |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 字号走 `--font-body`(28rpx) 起；间距 4/8pt 栅格；无横向滚动 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 首页以浏览分流为主，无破坏性操作；Banner 点击为信息入口 |
| 18 | loading/empty/error 三态 | 合规 | 骨架/EmptyState/正常态 + banner 轻量占位 + 下拉重试 |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 本页无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | 合规 | CustomTabBar ≤5 项，当前项高亮；固定元素 z-index 刻度统一 |
| 21 | 一致性打磨（图标/动效/stagger/disabled/focus） | 合规 | 进场 stagger 30–80ms 不挡交互；按压统一 0.97；无 disabled 态需处理 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px 视口：食堂横滑入口不溢出；瀑布流双列均 width>0（真机 devtools 实测）。
- [ ] reduced-motion：广播条降级为纯淡入、footer spinner 停转，无位移跳动。
- [ ] 动态字号（最大系统字）：标题/正文不重叠、卡片不被挤压。
- [ ] 暗色对比：首页表面/文字对比 ≥4.5:1（主用 token，独立暗色验证）。
- [ ] 44pt 触控：Banner/食堂卡/广播条/TabBar 命中区均 ≥44pt。
- [ ] 安全区：TabBar 与底部内容不被 home indicator 遮挡。
