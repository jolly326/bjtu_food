# 我的发布 (my-publish)
- 路由: /pages/profile/my-publish
- 分包: 主包（profile 子目录）
- 源文件: src/pages/profile/my-publish.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
查看当前用户发布的菜品与档口·食堂：两个 tab（菜品 / 档口·食堂），列表项带图、信息、退回原因与审核状态徽标；菜品可进编辑，底部「发布新菜品 / 提交档口·食堂」入口。

## 2. 布局结构
- 顶部：`Header title="我的发布" showBack`（含状态栏占位）。
- Tab 行：`.tabs`（菜品 / 档口·食堂，激活态主色 + 下划线，下划线动画 `--ease-out`）。
- 滚动区 `scroll-view`（refresher）。
- 列表：`.publish-item`（图 + 信息 + `StatusBadge`）：
  - 菜品：图(dish 兜底)/名称/¥价格·tags/退回原因。
  - 档口·食堂：图(home/dish 兜底)/类型+名称/location(IconSvg location)/退回原因。
- 空态：`EmptyState`（各 tab 独立文案，图标+文字）。
- 底部操作（仅菜品 tab）：`AppButton` 发布新菜品 + 提交档口·食堂(outline)。

## 3. 核心组件与用法
- `StatusBadge`：审核状态徽标（`pending`/`approved`/`rejected`，图标+文字）。
- `EmptyState`：空态。
- `AppButton`：发布/提交入口（主 + outline 次级）。
- `IconSvg`：`dish`(菜品/档口图兜底)、`home`(食堂图兜底)、`location`(位置)、`arrow`(未直接用)。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--bg-soft`(图占位)、`--text-primary/secondary/tertiary`。
- 强调：`--color-primary`(激活 tab/下划线)、`--color-error`(退回原因，图标+文字)。
- 圆角/阴影：`--radius-card`、`--radius-icon`(图)、`--shadow-card`。
- 间距：`--spacing-md/sm/lg`（4/8pt 节奏）。
- 字号：`--font-body`、`--font-caption`、`--font-aux`、`--font-tiny`(原因)。
- 动效：`--press-scale`(`.tab:active`/`.publish-item:active`)、`--ease-out`(tab 切换下划线)。
- 布局：scroll-wrap `padding: var(--spacing-md) 0`（非 tab 页）。

## 5. 交互与动效
- tab 切换 `switchTab` 重载对应列表；菜品项点击 `goEditDish` → publish-dish?id。
- 底部按钮跳 publish-dish / submit-stall。
- 下拉刷新。
- tab 激活下划线 `scale` 动画用 `--ease-out`（非按压强调，<300ms）。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ dish/home/location 均 IconSvg；图破兜底用 dish（仅菜品/档口语义，合规）/ home（食堂中性）；无 emoji。
- ②金额 api 层：✅ 价格以「元」展示（¥{{ item.price }} 已由 api 层转元），页面无 `/100`。
- ③WaterfallList 禁 slot：➖ 用自定义 `.publish-item` 列表（非瀑布流），不适用。
- ④三态齐备：✅ 各 tab 有 EmptyState；加载态用 loading 标志（无独立骨架，列表较短）；正常态。
- ⑤Sheet 规范：➖ 无底部 Sheet，不适用。
- ⑥按压 0.97：✅ `.tab:active`/`.publish-item:active` 均 `scale(var(--press-scale))`。
- ⑦颜色 token：✅ 无裸 hex（退回原因 `--color-error`；激活 `--color-primary`）。
- ⑧SectionTitle：➖ 列表页无分区标题，合规。
- ⑨底部避让：➖ 非 tab 页、无 fixed 底栏（操作按钮在滚动内），合规。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：退回原因 --color-error 暗色对比待实测(§8⑥)；价格数字建议 tabular-nums(§8⑮)
- 计划调整：采纳你的建议

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | tab/列表项/底部按钮 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | `.tab:active`/`.publish-item:active scale(var(--press-scale))` + `--press-transition` |
| 3 | 固定栏/导航预留安全区 | 合规 | 无固定底栏（按钮在滚动内）；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | dish/home/location/arrow 均注册 key；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；退回原因 `--color-error` 对比未单独暗色实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 审核状态 StatusBadge 图标+文字；退回原因有图标+文字 |
| 8 | prefers-reduced-motion 处理 | 合规 | tab 下划线可降速/去 transform；无位移动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | tab 下划线 <300ms；press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | 下划线 `--ease-out`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)/弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 下划线 transform/opacity；无 `transition:all` |
| 14 | 可中断动效 | 合规 | 下划线 transition 可中断 |
| 15 | 数字 tabular-nums | 部分 | 价格 ¥ 数字建议 `tabular-nums` 防位移 |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 字号 token；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 主「发布新菜品」+ outline 次级「提交档口·食堂」；无破坏性 |
| 18 | loading/empty/error 三态 | 合规 | 各 tab EmptyState；loading 标志；正常态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 非 tab 页；返回由 Header |
| 21 | 一致性打磨 | 合规 | 按压统一 0.97；StatusBadge 语言统一；tab 下划线同源 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：两 tab 不挤；列表项图+信息不溢出。
- [ ] reduced-motion：tab 下划线无突兀位移。
- [ ] 动态字号：列表项名称/原因不截断。
- [ ] 暗色对比：退回原因 `--color-error`、StatusBadge 文字对比达标。
- [ ] 44pt：tab/列表项/底部按钮 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡。
