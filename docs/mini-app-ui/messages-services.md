# 消息与服务 (messages-services)
- 路由: /pages/profile/messages-services/index
- 分包: 主包（profile 子目录）
- 源文件: src/pages/profile/messages-services/index.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
个人中心「我的发布 + 我的贡献」汇总页：一级 `SegmentTabs`（我的发布 / 我的贡献），二级 sub-tabs 区分菜品/档口·食堂（发布）与实体/动态（贡献），聚合 my-publish 与 my-submissions 的内容与入口。

> **本页为「我的发布与贡献」唯一聚合页**：页面清单评议已明确 `my-publish`（我的发布）与 `my-submissions`（我的提交）路由移除、其内容/入口并入本页，本页即二者在代码层面的唯一落点（详见 `docs/project_spec.md` §2.1）。页面标题建议同步由「消息与服务」更名为「我的发布与贡献」（与导航语义对齐，见 §7）。

## 2. 布局结构
- 顶部：`Header title="消息与服务" showBack`（含状态栏占位）。
- 一级 `SegmentTabs .seg-wrap`：我的发布 / 我的贡献（滑块 spring 1.0/0.3）。
- 滚动区 `scroll-view`（refresher）。
- **我的发布**（`activeGroup==='publish'`）：sub-tabs（菜品/档口·食堂）+ 列表（同 my-publish 的 `.publish-item` + `StatusBadge` + 空态）+ 底部发布按钮。
- **我的贡献**（`activeGroup==='contribution'`）：sub-tabs（实体/动态）+ 列表（同 my-submissions 的 `.sub-item` + `StatusBadge`/下架锁 + 空态）。
- 末尾 spacer。

## 3. 核心组件与用法
- `SegmentTabs`：一级滑块分段（spring 1.0/0.3，滑块从当前值重定向可中断）。
- `StatusBadge`：审核状态徽标（图标+文字）。
- `EmptyState`：各二级 tab 空态（图标+文字）。
- `AppButton`：发布新菜品 / 提交档口·食堂（主+outline 次级）。
- `IconSvg`：`dish`/`home`(图兜底)、`location`、`lock`(已下架)、`arrow`(动态右箭头)（与 my-publish/my-submissions 一致，均注册 key）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--bg-soft`、`--text-primary/secondary/tertiary`。
- 强调：`--color-primary`(激活 tab/下划线/链接)、`--color-error`(退回原因/必填)。
- 圆角/阴影：`--radius-card`、`--radius-icon`、`--shadow-card`。
- 间距：`--spacing-md/sm/lg/xl`（4/8pt 节奏）。
- 字号：`--font-body`、`--font-caption`、`--font-aux`、`--font-tiny`。
- 动效：`--press-scale`(`.sub-tab:active`/`.publish-item:active`/`.sub-item:active`)、`--ease-out`(SegmentTabs/sub-tab 滑块)。
- 布局：`.seg-wrap margin: var(--spacing-md)`；scroll-wrap `padding: var(--spacing-md) 0`。

## 5. 交互与动效
- 一级 SegmentTabs 切换重载对应 group；二级 sub-tab 切换（发布即时重载，贡献仅切显）。
- 菜品项 → publish-dish 编辑；动态项 → moment 详情。
- 下拉刷新；SegmentTabs/sub-tab 滑块 spring，非按压强调（<300ms，从当前值重定向）。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ dish/home/location/lock/arrow 均 IconSvg；无 emoji。
- ②金额 api 层：✅ 价格以「元」展示（api 层已转），无 `/100`。
- ③WaterfallList 禁 slot：➖ 自绘列表，不适用。
- ④三态齐备：✅ 各二级 tab EmptyState；loading 标志；正常态。
- ⑤Sheet 规范：➖ 无底部 Sheet，不适用。
- ⑥按压 0.97：✅ `.sub-tab`/`.publish-item`/`.sub-item` 均 `scale(var(--press-scale))`。
- ⑦颜色 token：✅ 无裸 hex。
- ⑧SectionTitle：➖ 列表/汇总页无分区标题，合规。
- ⑨底部避让：➖ 非 tab 页、无固定底栏（发布按钮在滚动内），合规。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：标题「消息与服务」是否更名（实际内容为发布/贡献汇总，无消息）
- 计划调整：✅ 已落实 —— 页面清单评议确认本页为「我的发布与贡献」唯一聚合页（吸收 my-publish / my-submissions），标题建议更名「我的发布与贡献」与导航语义对齐；notify 消息已并入「我的」区块，本页不再承载消息。

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | 一级/二级 tab/列表项/按钮 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | `.sub-tab`/`.publish-item`/`.sub-item:active scale(var(--press-scale))` |
| 3 | 固定栏/导航预留安全区 | 合规 | 无固定底栏（按钮在滚动内）；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | dish/home/location/lock/arrow 均注册 key；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；未单独暗色实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | StatusBadge 图标+文字；下架 lock 图标+文字 |
| 8 | prefers-reduced-motion 处理 | 合规 | SegmentTabs/sub-tab 滑块可降速/去 transform；无位移动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | 滑块 <300ms；press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | 滑块 `--ease-out`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)/弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 滑块 transform/opacity；无 `transition:all` |
| 14 | 可中断动效 | 合规 | 滑块 spring 从当前值重定向 |
| 15 | 数字 tabular-nums | 部分 | 价格 ¥ 数字建议 `tabular-nums` |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 字号 token；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 主「发布新菜品」+ outline「提交档口·食堂」；无破坏性 |
| 18 | loading/empty/error 三态 | 合规 | 各二级 tab EmptyState；loading；正常态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 非 tab 页；返回由 Header |
| 21 | 一致性打磨 | 合规 | 与 my-publish/my-submissions 动效/图标语言一致；按压统一 0.97 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：一级/二级 tab 不挤；列表项不溢出。
- [ ] reduced-motion：滑块无突兀位移。
- [ ] 动态字号：列表项/标签不截断。
- [ ] 暗色对比：StatusBadge/下架标文字对比达标。
- [ ] 44pt：tab/列表项/按钮 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡。
