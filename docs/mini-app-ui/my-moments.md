# 我的动态 (my-moments)
- 路由: /pages/my-moments/index
- 分包: 主包
- 源文件: src/pages/my-moments/index.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
当前用户发布的动态管理：分段「全部 / 审核中 / 已退回」，列表展示 `MomentCard`（带审核态），已退回可直达编辑重提。

## 2. 布局结构
- 顶部：`Header title="我的动态" showBack`（含状态栏占位）。
- 分段：`SegmentTabs`（全部/审核中/已退回，滑块 spring 1.0/0.3）。
- 滚动区 `scroll-view`（refresher + 触底）。
- 三态：
  1. 加载中：`skeleton-list`（3 张 `.sk-card`）。
  2. 空态：`EmptyState :text="emptyText"`（按分段变化）icon="comment"。
  3. 正常：`moment-list`（`MomentCard :show-audit="true"`，`:key=m.id`）。
- 无底部 tab 栏（非主包 tab 页）。

## 3. 核心组件与用法
- `SegmentTabs`：滑块式分段（复用 find/三子页同款数据驱动组件，滑块用 spring 非按压强调）。
- `MomentCard`：动态卡（`:show-audit` 显示审核状态徽标：pending/approved/rejected，图标+文字）。
- `EmptyState`：各分段空态文案（图标+文字）。
- `IconSvg`：未直接用（MomentCard 内部 user 等注册 key）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--text-tertiary`、`--text-primary`。
- 间距：`--spacing-sm/md/lg`（4/8pt 节奏）。
- 字号：`--font-aux`、`--font-body`。
- 动效：`--ease-out`（SegmentTabs 滑块；tab 切换非按压）、`--press-scale`（卡片由 MomentCard 内部统一）。
- 布局：scroll-wrap 无 tabbar 避让（非 tab 页），`padding:0`。

## 5. 交互与动效
- 分段切换 `onSegChange` 重拉对应 auditStatus；全部态额外统计 pending/rejected 徽标。
- 卡片点击：已退回→`publish-moment?id=` 编辑；其他→`moment` 详情。关联对象按类型跳。
- 下拉刷新、触底（MomentCard 列表未额外 footer，单页量小）。
- SegmentTabs 滑块 spring 1.0/0.3，从当前值重定向可中断；无 keyframes 重播。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ 页内无直接图标；MomentCard 内部 user 等 IconSvg；无 emoji。
- ②金额 api 层：✅ 无金额。
- ③WaterfallList 禁 slot：➖ 无瀑布流，不适用。
- ④三态齐备：✅ 骨架 / EmptyState（按分段文案）/ 正常态。
- ⑤Sheet 规范：➖ 无底部 Sheet，不适用。
- ⑥按压 0.97：✅ MomentCard 内部卡片按下 scale 0.97（组件统一）；本页 tab 切换非按压强调，SegmentTabs 内部用滑块动画非 scale。
- ⑦颜色 token：✅ 无裸 hex。
- ⑧SectionTitle：➖ 列表页无分区标题，合规。
- ⑨底部避让：➖ 非 tab 页、无底部操作栏，scroll-wrap `padding:0`，合规。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：暗色模式对比度待实测(§8⑥)；若加计数建议 tabular-nums(§8⑮)
- 计划调整：

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | 分段项/卡片点击区 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | 卡片 press 由 MomentCard 统一 `scale(var(--press-scale))` |
| 3 | 固定栏/导航预留安全区 | 合规 | 无固定底栏；Header 含状态栏占位 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | 无 emoji；MomentCard 内 user 等注册 key |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；未单独暗色实测（审核态 soft 底对比建议验证） |
| 7 | 不靠颜色 alone 传意 | 合规 | 审核态徽标图标+文字（pending/approved/rejected） |
| 8 | prefers-reduced-motion 处理 | 合规 | 无位移动画；SegmentTabs 滑块可降速/去 transform |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | SegmentTabs 滑块 <300ms；press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | 滑块 `--ease-out`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)；无弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 滑块 transform/opacity；无 `transition:all` |
| 14 | 可中断动效 | 合规 | 滑块 spring 从当前值重定向 |
| 15 | 数字 tabular-nums | 部分 | 无独立计数；若加建议 tabular-nums |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 字号 token；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 无破坏性操作；列表管理页 |
| 18 | loading/empty/error 三态 | 合规 | 骨架 / EmptyState（按分段）/ 正常态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 非 tab 页；返回由 Header |
| 21 | 一致性打磨 | 合规 | 审核态徽标语言统一；按压 0.97 同源 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：分段三选项不挤；卡片不溢出。
- [ ] reduced-motion：分段滑块无突兀位移。
- [ ] 动态字号：卡片正文/审核标签不截断。
- [ ] 暗色对比：审核态 soft 底（warning/error-soft）文字 ≥3:1。
- [ ] 44pt：分段项/卡片 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡。
