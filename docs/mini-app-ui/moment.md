# 动态详情 (moment)
- 路由: /pages/pages-detail/moment
- 分包: pages-detail 分包（实际 src 路径 pages/pages-detail/moment.vue）
- 源文件: src/pages/pages-detail/moment.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
动态详情：发布者 + 正文全文 + 九宫格大图 + 关联对象卡 + 退回原因/编辑重提（作者）+ 互动区（有用/评论/举报）+ 评论列表（折叠展开）+ 底部评论输入栏。

## 2. 布局结构
- 顶部：`Header title="动态详情" showBack`（含状态栏占位）。
- 滚动区 `scroll-view`（refresher）。
- 三态：
  1. 加载中：`.skeleton`（3×`.sk-block`）。
  2. 空/失败：`EmptyState text="动态加载失败或不存在" :retry`（图标+文字）。
  3. 正常：`.m-head`(头像+昵称+时间+审核态徽标) → `.m-content`(正文) → `MomentImageGrid`(九宫格) → `.related-card`(关联对象) → `.reject-box`(退回原因+编辑重提，作者) → `InteractBar`(有用/评论/举报) → `.comment-section`(评论列表+展开)。
- 底部 `.comment-bar`（fixed）：`comment-input` + `comment-send`(comment 图标，≥44pt 命中区)。
- `ReportModal`：举报弹层（spring 0.8/0.3 + ic-close + reduced-motion）。

## 3. 核心组件与用法
- `MomentImageGrid`：九宫格大图（图破兜底 user）。
- `InteractBar`：互动栏（useful/comment/report 事件上抛，图标+文字/计数）。
- `CommentItem`：评论项（回复/删除）。
- `ReportModal`：举报弹层（共享组件，spring 0.8/0.3）。
- `EmptyState`：加载失败/评论空态。
- `IconSvg`：`user`(头像兜底)、`arrow`(关联卡)、`edit`(编辑重提)、`comment`(评论发送/空态)、`related-card` 内 `relatedIconName`(dish/list，均注册 key)。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--text-primary/secondary/tertiary`、`--text-white`(发送/编辑图标)。
- 强调/状态：`--color-primary`(编辑重提底/展开链接)、`--color-error`(退回 box/删除确认)、`--color-warning`/`--color-error`(审核态 soft 底)、`--color-warning-soft`/`--color-error-soft`。
- 圆角/阴影：`--radius-card`、`--radius-tag`(审核徽标/编辑按钮)、`--radius-btn`(评论输入)、`--shadow-card`(关联卡)、`--shadow-bar-soft`(评论栏)。
- 间距：`--spacing-md/sm/xs/lg`（4/8pt 节奏）。
- 字号：`--font-body`、`--font-caption`、`--font-aux`、`--font-tiny`(审核文字)。
- 动效：`--press-scale`(`.related-card:active`)、`--press-transition`。
- 布局：`--action-bar-height` + `env(safe-area-inset-bottom)`（scroll-wrap 与 comment-bar 避让）。

## 5. 交互与动效
- 有用：乐观切换 `usefulActive` + count（图标+数字），调 `toggleUseful` 校正。
- 关联卡点击 `goRelated` → dish/stall 详情。
- 退回态（作者）：显示原因 + 「编辑重提」→ publish-moment?id。
- 评论：底部输入 send / 楼中楼回复 / 长按删（仅本人）。
- 举报：`openReport`(requireAuth) → ReportModal 提交（type=report 走 submitFeedback）。
- 评论 >5 条折叠「共 N 条，点击展开」。
- ReportModal：spring 0.8/0.3 + ic-close + reduced-motion 交叉淡入；exit 短于 enter。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ user/arrow/edit/comment/dish/list 均 IconSvg；无 emoji。
- ②金额 api 层：✅ 无金额。
- ③WaterfallList 禁 slot：➖ 无瀑布流，不适用。
- ④三态齐备：✅ 骨架 / EmptyState(retry, 评论空态) / 正常态。
- ⑤Sheet 规范：✅ ReportModal 套通用弹层（spring 0.8/0.3 + ic-close + reduced-motion）。
- ⑥按压 0.97：✅ `.related-card:active scale(var(--press-scale))`；InteractBar/CommentItem/comment-send 内部同规范。
- ⑦颜色 token：✅ 无裸 hex（审核/退回用 `--color-*-soft`；编辑底 `--color-primary`）。
- ⑧SectionTitle：➖ 详情页无分区标题（评论区用纯 text 标题，属字段语义，允许），合规。
- ⑨底部避让：✅ comment-bar 与 scroll-wrap 用 `--action-bar-height` + 安全区。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：审核/退回 soft 底文字暗色对比待实测(§8⑥)；点赞/评论计数与时间建议 tabular-nums(§8⑮)；评论单行输入缺独立可见 label(§8⑲)
- 计划调整：采纳你的建议

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | InteractBar 项/关联卡/评论发送/评论项 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | `.related-card:active`/InteractBar/CommentItem/comment-send 内部 `scale(var(--press-scale))` |
| 3 | 固定栏/导航预留安全区 | 合规 | comment-bar `--action-bar-height` + `env(safe-area-inset-bottom)`；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | user/arrow/edit/comment/dish/list 均注册 key；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)`；状态色走 `--color-*-soft` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；审核/退回 soft 底文字对比未单独暗色实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 审核态徽标图标+文字；退回 box 有图标+文字；有用有图标+计数 |
| 8 | prefers-reduced-motion 处理 | 合规 | ReportModal 降级交叉淡入；无位移动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | Sheet enter~300ms、exit 短；press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | Sheet `--ease-drawer`/`--ease-out`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)；Sheet 自底部 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | ReportModal 手势可中断；transition 可重定向 |
| 15 | 数字 tabular-nums | 部分 | 有用计数/评论计数/时间建议 `tabular-nums` 防位移 |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 字号 token；4/8pt 间距；九宫格无横向滚动 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 举报/删除为次级或确认动作（删除 confirm）；无主 CTA 抢占 |
| 18 | loading/empty/error 三态 | 合规 | 骨架 / EmptyState(retry, 评论空态) / 正常态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | 部分 | 评论输入为单行输入，有 placeholder；键盘类型对应；但无独立可见 label（即时通讯式输入框常见例外） |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 详情页无 tab 栏；返回由 Header |
| 21 | 一致性打磨 | 合规 | 审核/退回 soft 底语言统一；按压统一 0.97；ReportModal 同源 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：九宫格不溢出；评论栏不被遮挡；退回 box 不挤。
- [ ] reduced-motion：ReportModal 交叉淡入。
- [ ] 动态字号：正文/评论不截断。
- [ ] 暗色对比：审核/退回 soft 底文字、InteractBar 图标对比达标。
- [ ] 44pt：InteractBar/关联卡/评论发送 ≥44pt。
- [ ] 安全区：comment-bar 不被 home indicator 遮挡。
