# 消息中心 (notify)

> ⚠️ 此页路由已在页面清单评议中移除（my-publish/my-submissions 并入 messages-services；notify 消息并入「我的」；review-list 取消独立跳转，评价内联于详情）。本文件为遗留设计文档。

- 路由: /pages/notify/index（已移除）
- 分包: 主包（已移除）
- 源文件: src/pages/notify/index.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
通知列表：动态审核/菜品审核/评论/有用等消息，未读高亮 + 未读点，支持单条标已读与「全部标为已读」，按类型跳对应详情。

## 2. 布局结构
- 顶部：`Header title="消息中心" showBack`（含状态栏占位）。
- 操作行：有数据时显示「全部标为已读」`read-all`（右对齐，文本按钮）。
- 滚动区 `scroll-view`（refresher + 触底 `@scrolltolower`）。
- 三态：
  1. 加载中：`skeleton-list`（3 张 `.sk-card`）。
  2. 空态：`EmptyState text="暂无消息" icon="broadcast"`（loadFailed 时带重试）。
  3. 正常：`notify-list`（每条 `notify-item`，未读 `.unread` 浅底+左侧主色边+未读点）。
- 触底 footer（spinner / 已经到底）。

## 3. 核心组件与用法
- `EmptyState`：空态 + 重试（图标+文字）。
- `IconSvg`：`typeIcon(n.type)` → comment/dish/thumb/broadcast（消息类型徽标，均注册 key）；无图按类型映射。
- 原生能力：相对时间 `relativeTime`（数字+单位，建议 tabular-nums）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--bg-soft`(未用)、`--text-primary/secondary/tertiary`。
- 状态色：`--color-primary`(已读链接/未读左边框)、`--color-primary-soft2`(未读卡片底)、`--color-error`(未读点)、`--color-warning-soft`(audit 徽标底)、`--color-primary-soft`(comment)、`--color-like-soft`(useful)。
- 圆角/阴影：`--radius-card`、`--shadow-card`、`--radius-tag`(未读点)。
- 间距：`--spacing-sm/md/lg`（4/8pt 节奏）。
- 字号：`--font-body`、`--font-aux`。
- 动效：`--press-scale`(`.notify-item:active`)。
- 布局：scroll-wrap `padding:0`（非 tab 页）。

## 5. 交互与动效
- 点击条目：未读则乐观标已读并 `readNotification`；按 type + relatedId 跳 moment/dish 详情，无关联 toast 提示。
- 「全部标为已读」批量 `readAllNotifications` + 刷新未读角标。
- 触底无限加载；下拉刷新。
- `prefers-reduced-motion`：footer-spinner 关动画。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ comment/dish/thumb/broadcast 均 IconSvg；无 emoji。
- ②金额 api 层：✅ 无金额。
- ③WaterfallList 禁 slot：➖ 无瀑布流，不适用。
- ④三态齐备：✅ 骨架 / EmptyState(retry) / 正常态；触底 footer 完整。
- ⑤Sheet 规范：➖ 无底部 Sheet，不适用。
- ⑥按压 0.97：✅ `.notify-item:active scale(var(--press-scale))`。
- ⑦颜色 token：✅ 状态浅底用 `--color-*-soft` 语义 token；无裸 hex。
- ⑧SectionTitle：➖ 列表页无分区标题，合规。
- ⑨底部避让：➖ 非 tab 页、无底部栏，合规。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：未读态是否需配合「已读/未读」icon 区分（当前用左色边+点+底色，已含非色线索）
- 计划调整：采纳你的建议

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | 条目点击区 ≥44pt；条目间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | `.notify-item:active scale(var(--press-scale))` + `--press-transition` |
| 3 | 固定栏/导航预留安全区 | 合规 | 无固定底栏；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | comment/dish/thumb/broadcast 均注册 key；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)`；状态色走 `--color-*-soft` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；未读 soft 底与文字对比未单独暗色实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 未读左色边+点+底色+「全部标为已读」文字；类型有图标+文字 |
| 8 | prefers-reduced-motion 处理 | 合规 | footer-spinner 关动画；无位移动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | press 120ms；无高频动画 |
| 11 | 自定义缓动，禁 ease-in | 合规 | 无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)/弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | 无 keyframes 重播 |
| 15 | 数字 tabular-nums | 部分 | 相对时间（如「3分钟前」）含数字，建议 `tabular-nums` 防位移 |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 字号 token；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 「全部标为已读」为次要文本动作；无破坏性 |
| 18 | loading/empty/error 三态 | 合规 | 骨架 / EmptyState(retry) / 正常态 + footer |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 非 tab 页；返回由 Header |
| 21 | 一致性打磨 | 合规 | 未读态非色线索齐备；按压统一 0.97；类型图标同线宽 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：消息条目不溢出；未读左边框完整显示。
- [ ] reduced-motion：footer spinner 停转。
- [ ] 动态字号：条目正文/时间不截断。
- [ ] 暗色对比：未读 soft 底（primary-soft2）上文字 ≥4.5:1；未读点 `--color-error` ≥3:1。
- [ ] 44pt：条目 / 「全部标为已读」 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡。
