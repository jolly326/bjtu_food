# 小程序端 UI 整改实现分解（task-14）

> **来源**：`docs/ui-design-discussion.md`（定稿方案）+ `tasks/task-13-miniapp-ui-design.md`（定样总册 / 验收门禁）。
> **权威顺序**：`project_spec.md` §4（尤其 §4.9 slot 红线、§3 金额 api 层）> `task-13` > 本册 > 代码现状。
> **角色**：本册由技术负责人拆解并派发给「小程序开发工程师」；质量把控工程师对照 `task-13 §5` 做门禁，P0 项 0 残留方过关。
> **工作量**：S=小(0.5d) / M=中(1d) / L=大(≥2d，含重构)。
> **依赖**：依赖 task-13 已定样；部分页面数据依赖后端接口（见 §4 裁定项，缺口已在 `CONTRACT_IMPACT` 登记）。

---

## 执行顺序总览（建议发车顺序）

```
Phase 0 地基（全端前置）
  W1 图标体系与矢量图标落地        [S]
  W2 基础组件抽取（去重）          [L]
Phase 1 P0 红线修复（门禁前置，先清零）
  W3 find 首页 slot 空白 + 升级     [P0+S]
  W4 canteen 重构（单列档口流）     [P0+L]
  W5 publish-moment 档口 id 伪造    [P0+M]
Phase 2 TabBar 主站
  W6 home 广播/食堂卡/横滑          [P1+S]
  W7 community 列表 + FAB           [P1+M]
  W8 profile 设置抽组 + 登录注册重构 [P1+M,含 L 重构]
Phase 3 详情页
  W9  dish 评价入口/筛选迁移/爱心喜欢 [P1+M]
  W10 review-list 承接筛选 + 爱心喜欢 [P1+M]
  W11 stall 结构化 + 圆角方图        [P1+M]
  W12 moment 详情组件化             [P1+M]
Phase 4 发布 / 我的 / 工具
  W13 review 发表评价组件化         [P1+M]
  W14 publish-dish/submit-stall 表单 [P1+M×2]
  W15 my-moments/my-publish/my-submissions 复用 [P2+S×3]
  W16 settings/feedback/contact/webview [P1/S×3 + P2]
收口：task-13 §5 门禁对齐 + 三态/AppButton/scroll-view/key 全端扫尾
```

---

## Phase 0 地基（全端前置，必须最先交付）

### W1 · 图标体系与矢量图标落地  [工作量 S · 优先级 P1（红线 T29/T27）]
**目标**：落实 §0.5 图标规范，所有图标从 emoji 字符迁移到矢量 SVG。
**任务**：
1. 用已配置的 iconfont MCP（`search_icons`，中文关键词）拉取起步图标集到 `frontend/src/assets/icons/`，命名规范如 `ic-heart.svg`/`ic-thumb.svg`/`ic-search.svg`/`ic-broadcast.svg`/`ic-arrow.svg` 等；按功能分组（导航/操作/情感/状态）。
2. 建统一图标组件（如 `IconSvg.vue` 或字体类），全端经该组件/类引用，禁止再散落 Unicode emoji 当图标。
3. 情感语义登记：喜欢=❤️ 图标、有用=👍 图标，二者语义唯一、SVG 渲染。
4. 视觉一致性：线性风格、2px 描边、24px 网格、圆角端点一致（参照 apple-design + ui-ux-pro-max P4）。
**验收**：项目内 0 个 emoji 字符当图标；图标经统一组件引入；图标目录入库。
**关联**：task-13 T27/T29、§0.5。

### W2 · 基础组件抽取（去重）  [工作量 L · 优先级 P1]
**目标**：抽 §六 列出的共用组件，消除各页裸实现。
**任务**（每个组件独立 commit，数据驱动 + 可复用）：
- `ImageUploader`（publish-dish/submit-stall/review/publish-moment 图片网格）
- `SegmentTabs`（community/my-moments/my-publish/my-submissions 分段，数据驱动 tab，禁 v-if 链，T5）
- `ReviewItem`（dish/review-list 评价卡共用，T17，互动=爱心喜欢）
- `ApplySheet`（dish/profile 申请下架/纠错底部弹层，T8，圆角 20px spring 0.8/0.3，T23）
- `FeedbackForm`（contact/feedback 共用 + 统一类型枚举，T13）
- `SettingGroup`+`SettingCell`（settings/profile 菜单，T12）
- `RelatedPickerSheet`（publish-moment，须走真实档口搜索 API，T20）
- `SubmissionItem`/`PublishItem`（my-publish/my-submissions，T21）
- `CanteenCard`（home 横滑）/ `StallCard` 单列版（canteen，§2.2）
- `FilterSheet`（find 筛选，T18）
- `InteractBar`/`CommentItem`/`ReportModal`（moment，T10）
- `AuthForm`（profile，登录注册重构）/ `ContributeSheet`（profile 贡献入口）
- `StatsRow`（profile 统计）
**验收**：上述组件在 `frontend/src/components` 可见、被对应页面引用；无页面内裸重复实现；`WaterfallList`/`SearchBar`/`CardSection`/`Loading`/`UsefulButton` 充分复用且不传具名 slot（T4）。
**关联**：task-13 §六、T4/T5/T8/T10/T12/T13/T17/T18/T20/T21/T23。

---

## Phase 1 P0 红线修复（门禁前置，先清零）

### W3 · find 发现页 slot 空白 + 升级  [工作量 P0+S]
**文件**：`pages/find/index.vue`
**任务**：
1. 修 V1：删 `<template #card>`，改 `<WaterfallList :list @card-click="goToDetail">`（§4.9，禁具名 slot）。
2. 搜索 input debounce 300ms + 联想。
3. 分类宫格**不写「发现」二字**；本周热搜**每条左侧配图**（图标/缩略图）。
4. 筛选 tab / 分段选择器（SegmentTabs）视觉**重设计**，精致有辨识度（非普通文字 tab）。
5. 补 EmptyState（V4）；搜索框一律 `SearchBar`。
**验收**：真机无 `#card` 空白；热搜配图；筛选 tab 精致化；三态齐备。
**关联**：task-13 V1、T1/T3/T4、§1.2。

### W4 · canteen 食堂详情重构  [工作量 P0+L（重构）]
**文件**：`pages/pages-detail/canteen.vue`
**任务**：
1. 修 V2：删 `<template #card>`，`WaterfallList` 单列模式 + `@card-click="goToStall"`（§4.9）。
2. **重构信息架构**：① 食堂介绍与信息区块（图+名+简介+基础信息）② 各个档口**单列卡片流**（不直接显示菜品），与档口详情（stall.vue）同构。
3. 抽 `StallCard` 单列版（档口图+名+简介+评分/标签）。
4. 禁向 `WaterfallList` 传具名 slot。
**验收**：食堂介绍+单列档口流渲染正常；档口卡 tap→stall.vue；真机无 slot 空白。
**关联**：task-13 V2、§2.2、用户决议（类似档口详情）。

### W5 · publish-moment 档口 id 伪造  [工作量 P0+M]
**文件**：`pages/publish-moment/index.vue`
**任务**：
1. 修 V3：`RelatedPickerSheet` 改调**正式档口列表 API**（返回真实 `stallId`），禁硬编码/伪造 id（T20/§5.x）。
2. 抽 `ImageUploader`（≤9，T11）；根容器改 `scroll-view`（T15/§4.22）。
3. 关联对象卡 + Sheet 内搜索须用 `SearchBar`。
**验收**：提交真实档口 id；图片网格组件化；页面可滚。
**关联**：task-13 V3、T11/T15/T20、§3.1。

---

## Phase 2 TabBar 主站

### W6 · home 首页  [工作量 P1+S]
**文件**：`pages/home/index.vue`
**任务**：
1. 广播条接真实数据，按**广播类型分发跳转**（预留多种广播类型，不写死社区）；`v-if="broadcastList.length"` 无数据整块隐藏；去 `v-if="true"` 写死。
2. 食堂入口横滑卡抽 `CanteenCard`，**不含营业状态**。
3. Banner 按 `targetType` 分发（DISH/URL→webview/复制链接/NONE）。
4. 合并散落 `:style` 块；根容器 750rpx；真机无横向滚动条。
5. 复用 `WaterfallList`/`CustomTabBar`/`EmptyState`/`Loading`/`Header`；热门菜品双列瀑布流+无限加载。
**验收**：广播多类型可扩展；食堂卡无营业态；真机无横向滚动/无写死。
**关联**：task-13 T1/T2、§1.1、用户决议（广播多类型/食堂去营业态）。

### W7 · community 动态广场  [工作量 P1+M]
**文件**：`pages/community/index.vue`
**任务**：
1. 抽 `MomentCard`（消除裸实现）；两 Tab（最新/推荐）用 `SegmentTabs` 数据驱动。
2. 补 EmptyState（V4）；FAB 按下统一 `scale(0.97)`；下拉刷新 + 触底无限加载。
**验收**：列表正常、FAB 有反馈、Tab 切换顺滑。
**关联**：task-13 T9、§1.3。

### W8 · profile 我的 + 登录注册重构  [工作量 P1+M（含 L 重构）]
**文件**：`pages/profile/index.vue` + `settings/index.vue`
**任务**：
1. 抽 `SettingGroup`/`SettingCell`、`MenuItem`、`StatsRow`、`ContributeSheet`、`ApplySheet`。
2. **登录注册界面重构**：新 `AuthForm`（登录方式切换、发送验证码 60s 倒计时、内联错误就近展示）；现版不合理处重写。
3. 设页根容器改 `scroll-view`（T15）；不向用户展示数据库内部 id。
4. settings 去除/标注未生效的虚假控制（如免打扰，T28）。
**验收**：分组清晰、可滚动、登录注册合理、无虚假控制。
**关联**：task-13 T12/T13/T28、§1.4、用户决议（登录注册重构）。

---

## Phase 3 详情页

### W9 · dish 菜品详情  [工作量 P1+M]
**文件**：`pages/pages-detail/dish.vue`
**任务**：
1. 抽 `SegmentTabs`（数据驱动，禁 v-if 链，T5）。
2. **评价进入按钮移到「用户评价」卡片右上角**（AppButton，禁裸 button，T7）。
3. **去除详情卡片右上角筛选**，该能力迁移到 review-list（W10 承接）。
4. **评价互动改爱心「喜欢」图标（SVG，非 emoji）**，乐观更新。
5. 底部 action-bar 用统一 `--action-bar-height` 变量（T24）；详情卡片长按（仅作者）删除。
**验收**：切换顺滑、入口在右上角、筛选在 review-list、评价区为爱心喜欢。
**关联**：task-13 §2.1、T5/T7/T17/T24、用户决议（评价入口/互动）。

### W10 · review-list 全部评价  [工作量 P1+M]
**文件**：`pages/pages-detail/review-list.vue`
**任务**：
1. 抽 `FilterSheet`（评分/排序）+ `ReviewItem`（与 dish 共用）。
2. **承接 dish 迁来的筛选（评分/排序）**。
3. **ReviewItem 互动同步为爱心「喜欢」图标（SVG）**，乐观更新；长按删本人。
4. 齐备 Loading 态，超 50 条分页。
**验收**：可筛选、评价区为爱心喜欢、三态齐备。
**关联**：task-13 §2.6、T17/T18、用户决议。

### W11 · stall 档口详情  [工作量 P1+M]
**文件**：`pages/pages-detail/stall.vue`
**任务**：
1. 信息抽 `CardSection` 分组，消除字段散落。
2. **菜品图片改圆角正方形（非圆形）**；分隔线用 `--border-color`。
3. 抽 `StallDishRow`；齐备 Loading/EmptyState 三态。
**验收**：信息层次清晰、菜品图圆角方、三态齐备。
**关联**：task-13 §2.3、T4、用户决议（圆角方图）。

### W12 · moment 动态详情  [工作量 P1+M]
**文件**：`pages/pages-detail/moment.vue`
**任务**：
1. 抽 `InteractBar`（有用👍/评论💬/举报⚠️，统一 `UsefulButton`）、`CommentItem`、`ReportModal`。
2. 举报弱化为次级入口，不与正向互动同权并列。
3. 图片预览；有用👍 乐观更新；评论回复。
**验收**：交互统一、举报弱化、三态齐备。
**关联**：task-13 T10、§2.4。

---

## Phase 4 发布 / 我的 / 工具

### W13 · review 发表评价  [工作量 P1+M]
**文件**：`pages/pages-detail/review.vue`
**任务**：抽 `CardSection`(评分/内容/图片≤3) + `Rating` + `ImageUploader`（≤3）；根 `scroll-view` 包裹（T15）；AppButton 类型白名单（T7）；提交文案随状态变化。
**验收**：表单可滚、按钮合规。关联：task-13 §2.5、T7/T15。

### W14 · 发布/提交表单  [工作量 P1+M×2]
- **W14a publish-dish**（`pages/profile/publish-dish.vue`）：picker 食堂→联动档口；`TagSelector` 标签来源统一常量；`ImageUploader`(≤9)；scroll-view；金额保持 api 层统一（前端 `¥{{price}}` 不裸算）。关联 §4.1、T15。
- **W14b submit-stall**（`pages/profile/submit-stall.vue`）：类型切换显隐字段；`TypeSwitch`；走 `api/stall.ts` 封装（禁直连 HTTP）；scroll-view；齐备 Loading/提交中态。关联 §4.2、T15/T20。

### W15 · 我的动态/发布/提交  [工作量 P2+S×3]
- **W15a my-moments**（`pages/my-moments/index.vue`）：复用 `MomentCard` + `SegmentTabs`，徽标计数响应式。
- **W15b my-publish**（`pages/profile/my-publish.vue`）：抽 `PublishItem`，移除 `as any` 兜底，齐备 Loading。
- **W15c my-submissions**（`pages/profile/my-submissions.vue`）：抽 `SubmissionItem`，off 态用 `StatusBadge` 扩展状态。
**验收**：一致复用、无裸实现、三态齐备。关联 task-13 §3.2/§4.3/§4.4。

### W16 · settings / feedback / contact / webview  [工作量 P1/S×2 + P2×2]
- **W16a settings**（`settings/index.vue`）：scroll-view + 去/标虚假控制（T28）。
- **W16b feedback**（`feedback/index.vue`）：抽 `FeedbackForm` + scroll-view，与 contact 复用同 `FeedbackForm`/类型枚举。
- **W16c contact**（`pages-detail/contact.vue`）：补 `CardSection` + 图标，走 `submitFeedback` api 层 + 统一类型枚举。
- **W16d webview**（`pages/webview/index.vue`）：加 web-view loading + 失败兜底（复制链接 Toast）。
**验收**：反馈类复用同一表单；无虚假控制；外链体验完整。关联 task-13 §2.7/§4.5/§4.6/§5.1。

---

## 收口 · 全端门禁对齐（task-13 §5）

1. **P0 零残留**：W3(V1)/W4(V2)/W5(V3) 修复且真机验证。
2. 全端列表三态（Loading/EmptyState/正常）齐备（V4）。
3. AppButton `type` 全端对齐白名单，无裸 `<button>` 主操作（V5/T7）。
4. 表单页根容器均为 scroll-view（V6/T15）。
5. 无 `v-for :key="index"`（V7/T25）、无 `v-if="true"`、无硬编码 action-bar 高度（T24）、无虚假控制（T28/V28）。
6. 图标全端矢量（W1/T29），无 emoji 当图标。

---

## 依赖与待裁定（向后端/技术负责人）
- **后端接口缺口**（须 `CONTRACT_IMPACT` 登记后实现）：档口列表 API（W5）、canteen detail、stall detail、review-list、contact、feedback、settings、profile 子页数据。
- **标签体系**：待后端建表+接口（task-05）。
- **档口图片数量**：提案 5→3 待拍板，先按现有（T26）。
- 所有工作量估算以「接口已就绪」为前提；接口缺口由后端并行推进，不阻塞纯 UI 整改（W2/W3/W4 视觉结构部分）。
