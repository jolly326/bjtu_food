# 我的提交 (my-submissions)

> ⚠️ 此页路由已在页面清单评议中移除（my-publish/my-submissions 并入 messages-services；notify 消息并入「我的」；review-list 取消独立跳转，评价内联于详情）。本文件为遗留设计文档。

- 路由: /pages/profile/my-submissions（已移除）
- 分包: 主包（profile 子目录）（已移除）
- 源文件: src/pages/profile/my-submissions.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
查看当前用户的实体申请与动态提交记录：tab（实体 / 动态），实体项展示标题、类型·动作、审核状态徽标与下架标记；动态项可进详情。

## 2. 布局结构
- 顶部：`Header title="我的提交" showBack`（含状态栏占位）。
- Tab 行：`.tabs`（实体 / 动态，激活主色+下划线，下划线 `--ease-out`）。
- 滚动区 `scroll-view`（refresher）。
- 列表：`.sub-item`（标题 + 类型·动作 + `StatusBadge` + 下架标记/箭头）：
  - 实体：entityType(菜品/档口/食堂) · action(新增/下架/变更)；approved+off 显示「已下架」(lock 图标)。
  - 动态：标题 + 状态徽标 + 右箭头（点击进 moment 详情）。
- 空态：`EmptyState`（各 tab 独立文案，图标+文字）。

## 3. 核心组件与用法
- `StatusBadge`：审核状态徽标（图标+文字）。
- `EmptyState`：空态。
- `IconSvg`：`lock`(已下架，注册 key，size 22，color `var(--text-tertiary)`)、`arrow`(动态项右箭头，注册 key，size 28，color `var(--text-tertiary)`)。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--text-primary/secondary/tertiary`。
- 强调：`--color-primary`(激活 tab/下划线)。
- 圆角/阴影：`--radius-card`、`--shadow-card`。
- 间距：`--spacing-xs/sm/md`（4/8pt 节奏）。
- 字号：`--font-body`、`--font-caption`、`--font-aux`、`--font-tiny`(下架标)。
- 动效：`--press-scale`(`.tab:active`/`.sub-item:active`)、`--ease-out`。
- 布局：scroll-wrap `padding: var(--spacing-md) 0`；tabs `padding: var(--spacing-md) var(--spacing-md) 0`。

## 5. 交互与动效
- tab 切换 `switchTab`（仅切显示，数据一次性加载后 computed 过滤）。
- 动态项点击 `goMoment` → moment 详情；实体项无跳转（申请记录）。
- `onShow` 加载；下拉刷新。
- tab 下划线动画 `--ease-out`（非按压强调，<300ms）；`.tab`/`.sub-item` press `transition: transform 120ms var(--ease-out)`。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ lock/arrow 走 IconSvg；无 emoji。
- ②金额 api 层：✅ 无金额。
- ③WaterfallList 禁 slot：➖ 自绘列表，不适用。
- ④三态齐备：✅ 各 tab EmptyState；加载标志 loading；正常态。
- ⑤Sheet 规范：➖ 无底部 Sheet，不适用。
- ⑥按压 0.97：✅ `.tab:active`/`.sub-item:active` `scale(var(--press-scale))`。
- ⑦颜色 token：✅ 无裸 hex。
- ⑧SectionTitle：➖ 列表页无分区标题，合规。
- ⑨底部避让：➖ 非 tab 页、无固定底栏，合规。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：~~按压过渡 120ms→160ms 及补 reduced-motion 降级块~~（已整改：过渡改 160ms ease-out 并加 `@media (prefers-reduced-motion)`，见 §8②）
- 计划调整：采纳你的建议

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥ 44×44px，相邻间距 ≥ 8px | 合规 | tab/列表项 ≥44px；间距 `var(--spacing-sm)`(8pt) |
| 2 | 按压反馈 100ms 内：:active scale(0.97)，transition transform 160ms ease-out | 合规 | 已整改：过渡改 `transform 160ms var(--ease-out)` 并加 `@media (prefers-reduced-motion)` 降级；`.tab:active`/`.sub-item:active scale(var(--press-scale))` |
| 3 | 固定底栏预留 env(safe-area-inset-bottom) | 合规 | 无固定底栏；Header 含状态栏占位，无需 inset-bottom |
| 4 | 禁用 emoji 作图标：统一 IconSvg 已注册 key | 合规 | lock/arrow 均 IconSvg 注册 key；无 emoji |
| 5 | 仅用语义 token（var(--…)），组件内不写裸 hex | 合规 | 全 `var(--…)`，无裸 hex |
| 6 | 正文对比度 ≥ 4.5:1，亮/暗双模式 | 部分 | token 满足；未单独暗色实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 下架标 lock 图标+文字；状态 StatusBadge 图标+文字 |
| 8 | 处理 prefers-reduced-motion | 部分 | 无位移动画；下划线为 transform，但缺显式 `@media (prefers-reduced-motion)` 降级块 |
| 9 | hover 动效门控 @media (hover:hover) and (pointer:fine) | 合规 | 触控 `:active`/touch，无 hover 依赖 |
| 10 | UI 微交互 < 300ms，exit 比 enter 短，高频不动画 | 合规 | tab 下划线/按压 <300ms；非高频 |
| 11 | 自定义缓动，禁 ease-in | 合规 | 下划线 `--ease-out`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)/弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | 下划线 transition 可中断 |
| 15 | 数字 tabular-nums | ➖ | 无数字计数 |
| 16 | 正文 ≥ 16px(32rpx)，行高 1.5–1.75，无横向滚动 | 合规 | 字号 token(≥32rpx)；纵向滚动，无横向溢出 |
| 17 | 每屏一个主 CTA | 合规 | 记录查看页，无主 CTA/破坏性操作 |
| 18 | loading/empty/error 态齐全 | 合规 | loading 标志；各 tab EmptyState；正常态 |
| 19 | 表单无障碍 label/校验/input type | ➖ | 无表单 |
| 20 | 导航一致，底部 ≤5 项 | ➖ | 非 tab 页；返回由 Header |
| 21 | 一致性打磨 disabled/focus-visible/stagger | 合规 | 按压统一 0.97；StatusBadge 语言统一；tab 下划线同源 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：两 tab 不挤；列表项不溢出。
- [ ] reduced-motion：tab 下划线无突兀位移。
- [ ] 动态字号：列表项标题/类型·动作不截断。
- [ ] 暗色对比：下架标 `--text-tertiary` 上文字、StatusBadge 对比达标。
- [ ] 44px：tab/列表项 ≥44px。
- [ ] 安全区：Header 不被刘海遮挡。
