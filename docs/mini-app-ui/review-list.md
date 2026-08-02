# 全部评价 (review-list)
- 路由: /pages/pages-detail/review-list
- 分包: pages-detail 分包（实际 src 路径 pages/pages-detail/review-list.vue）
- 源文件: src/pages/pages-detail/review-list.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
评价列表：按 dishId/stallId/canteenId 展示「全部评价」，或无实体 id 时展示「我的评价」；触底分页加载，本人评价可长按删除。

## 2. 布局结构
- 顶部：`Header :title="headerTitle" showBack`（有实体→「全部评价」，无→「我的评价」；含状态栏占位）。
- 滚动区 `scroll-view`（refresher + 触底 `@scrolltolower` + lower-threshold 80）。
- 三态：
  1. 加载中：`skeleton-list`（3×`.sk-card`）。
  2. 空/失败：`EmptyState :text="loadFailed?'评价加载失败':'暂无评价'" :retry="loadFailed"`（图标+文字）。
  3. 正常：`review-list`（`ReviewItem`，`dishId===0` 时 `:deletable` 允许删除）+ 触底「没有更多了」。
- 无底部固定栏（非表单/详情操作页）。

## 3. 核心组件与用法
- `ReviewItem`：评价项（`@delete` 上抛长按删除，本人可删；star 图标注册 key）。
- `EmptyState`：空态 + 重试。
- `IconSvg`：未直接使用（ReviewItem 内部图标 star 等注册 key）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--text-tertiary`、`--text-primary`。
- 圆角/阴影：`--radius-card`(未直接用，ReviewItem 内部)、`--shadow-card`(未直接用)。
- 间距：`--spacing-sm/md/lg`（4/8pt 节奏）。
- 字号：`--font-aux`(list-end)、`--font-body`(未直接用)。
- 动效：ReviewItem 内部按压 `--press-scale`；无页面级动画。
- 布局：scroll-wrap `padding:0`（非 tab 页，无底部栏避让）。

## 5. 交互与动效
- 三种实体筛选：getReviewsByDish/Stall/Canteen；无实体 `getMyReviews`（requireAuth）。
- 触底 `onLoadMore` 分页（page+1）；下拉刷新。
- 长按本人评价 `onReviewLongPress` → showModal 确认（二次确认，danger 语义）→ `deleteReview`，本地移除。
- 删除为低频手势动作，无高频动画。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ 页内无直接图标；ReviewItem 内部 star 等 IconSvg；无 emoji。
- ②金额 api 层：✅ 无金额。
- ③WaterfallList 禁 slot：➖ 评价列表（非菜品瀑布流），不适用。
- ④三态齐备：✅ 骨架 / EmptyState(retry) / 正常态；触底「没有更多了」完整。
- ⑤Sheet 规范：➖ 无底部 Sheet（删除用 showModal），不适用。
- ⑥按压 0.97：✅ ReviewItem 内部卡片按下 scale 0.97（组件统一）。
- ⑦颜色 token：✅ 无裸 hex。
- ⑧SectionTitle：➖ 列表页无分区标题，合规。
- ⑨底部避让：➖ 非 tab 页、无 fixed 底栏，scroll-wrap `padding:0`，合规。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：暗色模式对比度待实测(§8⑥)；评分值/计数建议 tabular-nums(§8⑮)
- 计划调整：

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | 评价项点击区 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | ReviewItem 内部卡片按下 `scale(var(--press-scale))` + `--press-transition` |
| 3 | 固定栏/导航预留安全区 | 合规 | 无固定底栏；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | 无 emoji；ReviewItem 内 star 等注册 key |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；未单独暗色实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 评分 star 图标+数字；删除为 showModal 文字确认 |
| 8 | prefers-reduced-motion 处理 | 合规 | 无位移动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | press 120ms；无高频动画 |
| 11 | 自定义缓动，禁 ease-in | 合规 | 无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)/弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | 无 keyframes 重播 |
| 15 | 数字 tabular-nums | 部分 | 评分值/计数建议 `tabular-nums` |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 字号 token；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 删除为长按+二次确认（danger 语义隔离）；无主 CTA 抢占 |
| 18 | loading/empty/error 三态 | 合规 | 骨架 / EmptyState(retry) / 正常态；触底「没有更多了」 |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 非 tab 页；返回由 Header |
| 21 | 一致性打磨 | 合规 | 按压统一 0.97；删除二次确认同源 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：评价项不溢出；触底 footer 完整。
- [ ] reduced-motion：无位移动画。
- [ ] 动态字号：评价正文/评分不截断。
- [ ] 暗色对比：评价文字、删除确认 danger 文本对比达标。
- [ ] 44pt：评价项 / 长按删除命中区 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡。
