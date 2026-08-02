# 发表评价 (review)
- 路由: /pages/pages-detail/review
- 分包: pages-detail 分包（实际 src 路径 pages/pages-detail/review.vue）
- 源文件: src/pages/pages-detail/review.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
对菜品发表评价：评分（Rating 组件）+ 评价内容（≤500）+ 图片（≤3），吸底提交；同一用户重复评价由后端 400 冲突提示。

## 2. 布局结构
- 顶部：`Header title="发表评价" showBack`（含状态栏占位）。
- 滚动区 `scroll-view`。
- 三个 `CardSection`（卡片内纯 text 小标题 `.section-label`，字段语义允许）：
  1. 评分：`Rating v-model :readonly=false :show-text :star-size=48`（star 图标，注册 key）。
  2. 评价内容：`textarea`（maxlength 500）+ 字数计数。
  3. 图片（最多3张）：`image-list`（预览 + 移除 close 图标 + 加号上传）。
- 底部 `.submit-bar`（fixed）：`AppButton text="提交评价"`（disabled 当 rating=0 或内容空；loading=uploading，单主 CTA）。

## 3. 核心组件与用法
- `Rating`：星级评分组件（star/star-filled 图标，hover 态在小程序为 touch 按压高亮）。
- `CardSection`：分区卡片。
- `AppButton`：提交主按钮（disabled/loading 态清晰，disabled=opacity 弱化）。
- `IconSvg`：`close`(移除图片，注册 key)、`plus`(上传占位，注册 key)。
- 原生 `uni.chooseImage`：选图（系统控件）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--bg-soft`(输入框/上传底)、`--text-primary/tertiary`。
- 强调：`--badge-dark-bg`/`--badge-dark-text`(移除按钮底/字，暗底白字，语义 token)。
- 边框：`--border-color`(上传虚线框)。
- 圆角：`--radius-card`、`--radius-icon`(图/输入框)、`--shadow-bar-soft`(submit-bar)。
- 间距：`--spacing-md/sm/xs/lg`（4/8pt 节奏）。
- 字号：`--font-body`、`--font-tiny`(计数)。
- 动效：未用显式 press（AppButton 内部 scale；Rating/加号无 scale 强调）。
- 布局：`--action-bar-height` + `env(safe-area-inset-bottom)`（scroll-wrap 与 submit-bar 避让）。

## 5. 交互与动效
- 评分默认 5；内容非空且 rating>0 才可提交（disabled 态明确）。
- 图片选≤3，逐张 `uploadImage` 上传取 URL（单张失败跳过）；提交 `submitReview` → toast → navigateBack。
- 提交按钮 AppButton 自带按压（scale 0.97，即时 `--press-transition`）。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ close/plus 走 IconSvg；Rating 内 star/star-filled 注册 key；无 emoji。
- ②金额 api 层：✅ 无金额。
- ③WaterfallList 禁 slot：➖ 无列表，不适用。
- ④三态齐备：⚠️ 表单页，无空态需求；提交 loading/disabled。可接受。
- ⑤Sheet 规范：➖ 无底部 Sheet（原生 chooseImage），不适用。
- ⑥按压 0.97：⚠️ 加号/图片区未加 `:active scale`；提交 AppButton 内部 `--press-scale`。可接受但非强制（建议补图片区按压反馈）。
- ⑦颜色 token：✅ 无裸 hex（移除按钮 `--badge-dark-bg`/`--badge-dark-text` 语义 token）。
- ⑧SectionTitle：⚠️ 本页三个小标题用纯 text `.section-label`（评分/评价内容/图片），属表单字段语义 label，按 §0.3 细则允许不强制 accent 条；未手写无 accent 的「分区大标题」，合规。
- ⑨底部避让：✅ submit-bar 与 scroll-wrap 用 `--action-bar-height` + 安全区。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：~~图片上传区/加号是否补按压 scale 反馈~~（已整改：image-upload plus-cell 加按压反馈，见 §8②）
- 计划调整：

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | Rating star/图片格/移除/提交按钮 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | 已整改：`.image-upload` plus-cell 加按压反馈；提交 AppButton 内部 0.97 |
| 3 | 固定栏/导航预留安全区 | 合规 | submit-bar `--action-bar-height` + `env(safe-area-inset-bottom)`；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | close/plus/star/star-filled 均注册 key；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)`；移除按钮语义 token |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；disabled 态文字对比未单独暗色实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 评分有 star 图标+数字文字；提交 disabled 有状态+文字 |
| 8 | prefers-reduced-motion 处理 | 合规 | 无位移动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch；Rating 无裸 hover |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | AppButton press 120ms；无高频动画 |
| 11 | 自定义缓动，禁 ease-in | 合规 | 无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)/弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | 无 keyframes 重播 |
| 15 | 数字 tabular-nums | 部分 | 字数计数（如 120/500）建议 `tabular-nums` |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 部分 | textarea/小标题建议 ≥32rpx；当前 `--font-body`(28rpx) 略低，建议复核 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 唯一主 CTA「提交评价」；无破坏性 |
| 18 | loading/empty/error 三态 | 部分 | 提交 loading/disabled 即状态；表单无空态（合理）；评分/内容校验即错误态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | 部分 | 区块小标题 `.section-label` 即字段 label；textarea 有 placeholder；star/图片无独立可见 label，靠区块标题；建议标题即 label 语义 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 表单页无底部导航 |
| 21 | 一致性打磨 | 部分 | AppButton 按压统一；加号/star 缺按压反馈（见 §8②）；disabled=opacity 弱化到位 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：三区块不溢出；图片 3 格整齐。
- [ ] reduced-motion：无位移动画。
- [ ] 动态字号：textarea/标题不截断。
- [ ] 暗色对比：disabled 态文字、移除按钮白字对比达标。
- [ ] 44pt：star/图片格/移除/提交 ≥44pt。
- [ ] 安全区：submit-bar 不被 home indicator 遮挡。
