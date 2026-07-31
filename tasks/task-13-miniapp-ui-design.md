# 小程序端 UI 定样总册（task-13）

> **定位**：小程序端「讨论定样」正式收口。链路：UI-UX 设计师出逐页方案 → 小程序开发工程师评审可行性 → 技术负责人裁定并落此册 + 在 `project_spec.md` §4 加指针。
> **权威顺序**：`project_spec.md` §4（最高红线，尤其 §4.9）> 本册 > 代码现状。
> **过程稿**（已用完）：`C:\Users\jolly\AppData\Local\Temp\bjtu_mp_ui_proposal.md`、`bjtu_mp_dev_review.md` —— 阅后删除，不入库。

## 0. 协作与验收
- 设计决策权：UI-UX 设计师；可行性评审：小程序开发工程师；规范落定：技术负责人（唯一可改 spec）。
- 验收：质量把控工程师对照本册 + `spec §4` 做门禁，P0 项 0 残留方过关。
- **用户逐页决议来源**：用户与 UI-UX 设计师的逐页 UI 决议（广播多类型 / 食堂去营业态 / find 热搜配图+筛选 tab 升级 / 登录注册重构 / dish 评价入口右上角+筛选迁移且评价互动为爱心「喜欢」/ canteen 单列档口重构（类似档口详情：上介绍下各档口）/ stall 圆角方图 / 图标统一走 iconfont 或本地 Assets 矢量图标等）见 `docs/ui-design-discussion.md` 对应页面小节（§2.1/§2.2/§2.3/§2.6 及 §0.5 图标规范），本册逐页清单已同步。

## 1. UI 通用原则（28 条 · 落点裁定）

**已升格 spec 红线（强制）**
- `spec §4.9` 禁止 wx:for 内具名 slot 分发（故障=整块空白不渲染，非塌缩）。
- `spec §3` 金额分↔元转换只能在 api 层（`utils/money` 的 `fenToYuan/yuanToFen`），页面/组件禁止裸算。
- 三态强制、AppButton 类型白名单、表单页 scroll-view、emoji 登记前置、未生效设置禁虚假控制、关联对象走正式 API —— 建议升 §4 红线，本册先引用，技术负责人后续择机并入 spec。

**本册承载（组件/页面级细则，T1–T28）**
| 编号 | 原则 | 引用 spec |
|---|---|---|
| T1 | 首页首屏顺序：广播→搜索→分类→推荐→瀑布流 | §4.2 |
| T2 | 首页横滑卡（热门/限时/猜你喜欢）抽组件，禁页面内裸实现 | §4.8 |
| T3 | 搜索页：分类胶囊 + 排序胶囊 + 结果，单页不拆 Tab | §4.1 |
| T4 | WaterfallList 复用，禁再传具名 slot | §4.9 |
| T5 | 详情页 SegmentTabs 抽组件（tab 数据驱动，禁 v-if 链） | §4.8 |
| T6 | 档口详情：CanteenCard + StallTab 抽组件 | §4.8 |
| T7 | 评价入口用 AppButton，禁裸 button | §4.21 |
| T8 | 申请下架/变更走 ApplySheet 底部弹层组件 | §4.4 |
| T9 | 社区列表 MomentCard + 浮动发布按钮（FAB 缩放 0.97） | §4.8 |
| T10 | 动态详情：InteractBar（❤️/👍/💬/分享）+ CommentItem + ReportModal | §4.2 |
| T11 | 发布动态：ImageUploader + TagLabel（标签体系待后端接口） | §4.2 |
| T12 | 我的页：SettingGroup/SettingCell 抽象，分组（贡献/账户/其他） | §4.8 |
| T13 | 设置/反馈/注销/账号：SettingGroup + FeedbackForm + 二次确认 | §4.22 |
| T14 | 消息页顶部统计 StatsRow（待后端接口） | §4.8 |
| T15 | 表单页根容器强制 scroll-view（禁页面级滚动丢状态） | §4.22 |
| T16 | 破坏性操作（删除/注销）走二次确认 + 显式可达，禁误触 | §4.1 |
| T17 | 评价 Item（ReviewItem）抽组件，复用于详情/全部评价/审核 | §4.8 |
| T18 | 全部评价页顶部 FilterSheet（评分/排序） | §4.4 |
| T19 | 发表评价：Rating + 文本域 + ImageUploader | §4.2 |
| T20 | 关联对象选择（档口/食堂）走正式 API 禁伪造 id | §5.x |
| T21 | 我的发布/投稿：PublishItem + SubmissionItem（复用卡片） | §4.8 |
| T22 | 负向信息弱化（下架/审核中灰弱化，非红删） | §4.1 |
| T23 | Sheet 抽通用组件（圆角 20px、spring 0.8/0.3、手势中断） | §4.4 |
| T24 | action-bar 用 CSS 变量（--action-bar-h），禁硬编码 100rpx | §4.2 |
| T25 | v-for 禁用 index 作 key（用业务 id） | §4.8 |
| T26 | 档口图片限制（原 5→3 待拍板，先按现有实现） | §4.2 |
| T27 | 情感语义唯一（喜欢=❤️ 图标 / 有用=👍 图标），图标统一从 iconfont 或本地 Assets 矢量引入，新增语义先登记；禁止 Unicode emoji 字符当图标 | §4.2 / §0.5 |
| T28 | 未生效设置（如消息免打扰未实现）必须标注或去除，禁虚假控制 | §4.1 |
| T29 | 图标统一矢量（iconfont/本地 Assets），线性风格、2px 描边、24px 网格一致；禁止 emoji 字符当图标（ui-ux-pro-max P4 红线） | §0.5 |

## 2. 跨页红线违规整改（P0/P1）

| # | 文件:行 | 违规 | 正确改法 | 优先级 |
|---|---|---|---|---|
| V1 | `find/index.vue:163-167` | §4.9 slot 空白 | 删 `<template #card>`，改 `<WaterfallList :list @card-click="goToDetail">` | **P0** |
| V2 | `pages-detail/canteen.vue:41-45` | §4.9 slot 空白 | 同上 | **P0** |
| V3 | `publish-moment/index.vue` 档口 id 伪造 | §5.x 关联 API | `RelatedPickerSheet` 改调正式档口列表 API，禁硬编码 id | **P0** |
| V4 | 多页缺 EmptyState（find/community/notify/评价/投稿） | 三态强制 | 列表接口补 `EmptyState` | P1 |
| V5 | 多页 AppButton `type` 错（review gradient/moment plain/find 裸 button） | T7/§4.21 | 对齐白名单 primary/outline/text/danger/gradient | P1 |
| V6 | 表单页缺 scroll-view（publish-moment/publish-dish/submit-stall/review/feedback/settings） | T15/§4.22 | 根容器改 scroll-view | P1 |
| V7 | 多页 `v-for` 用 `index` 作 key | T25 | 改用业务 id | P1 |

## 3. 逐页整改清单

> 列：页面 / 现状（文件:行）/ 整改 / 验收标准 / 优先级 / 工作量

**TabBar 主站**
- **home** / 广播 `v-if="true"` 写死、横向滚动条、合并 `:style` 块；食堂卡含营业态 / 广播接真实数据并按**广播类型分发**（预留多种广播，不写死社区）、根容器 750rpx、合并 style 块；**食堂卡不显示营业状态** / 真机无横向滚动、无 `v-if="true"`、广播多类型可扩展 / P1 / S
- **find** / `#card` slot 空白（V1）、缺 EmptyState、搜索无节流、分类宫格写「发现」字、热搜无配图、筛选 tab 普通 / 修 V1、补 EmptyState、搜索防抖；**分类宫格去「发现」字**、**每条热搜菜品左侧配图**、**筛选 tab 视觉重设计（精致有辨识度）** / 菜品区正常、热搜配图、筛选 tab 升级 / **P0**+P1 / S
- **community** / MomentCard 裸实现、缺 EmptyState、FAB 缩放 / 抽 MomentCard、补空态、FAB scale(0.97) / 列表正常、FAB 有反馈 / P1 / M
- **profile** / SettingGroup 未抽象、设页无 scroll-view、**登录注册界面太奇怪** / 抽 SettingGroup/SettingCell、设页改 scroll-view；**登录注册界面重构（AuthForm）** / 分组清晰、可滚动、登录注册合理 / P1 / M（重构 L）

**详情页**
- **dish** / SegmentTabs 用 v-if 链、评价入口裸 button、**评价入口不在评价卡片右上角、详情卡片右上角有筛选、评价互动误用「有用」** / 抽 SegmentTabs（数据驱动）；**评价进入按钮移到用户评价卡片右上角**；**去除详情卡片右上角筛选，迁移到 review-list**；**评价互动改爱心「喜欢」图标（SVG，非 emoji）** / 切换顺滑、入口在右上角、筛选在 review-list、评价区为爱心喜欢 / P1 / M
- **canteen** / `#card` slot 空白（V2）、StallTab 裸、**结构需重构** / 修 V2；**重构：与档口详情同构——上食堂介绍与信息、下各个档口单列卡片流（不直接显示菜品）**，抽 StallCard 单列版 / 食堂介绍+单列档口流渲染 / **P0**+P1 / L（重构）
- **stall** / 字段散落、缺结构化、**菜品图圆形** / 抽 CardSection 分组；**菜品图片改圆角正方形（非圆形）** / 信息层次清晰、菜品图圆角方 / P1 / M
- **moment** / InteractBar/CommentItem/ReportModal 裸 / 抽组件 / 交互统一 / P1 / M
- **review（发表评价）** / Rating+文本域+ImageUploader 裸 / 抽组件、根 scroll-view / 表单可滚 / P1 / M
- **review-list（全部评价）** / FilterSheet 无、ReviewItem 裸、**缺从 dish 迁移来的筛选、评价互动误用「有用」** / 抽 FilterSheet+ReviewItem；**承接 dish 迁移的筛选（评分/排序）**；**ReviewItem 互动同步为爱心「喜欢」图标（与 dish 共用，SVG）** / 可筛选、评价区为爱心喜欢 / P1 / M
- **contact** / 仅 `makePhoneCall`，缺视觉层 / 补 CardSection + 图标 / 信息完整 / P2 / S

**社区发布/我的**
- **publish-moment** / 档口 id 伪造（V3）、ImageUploader 裸、缺 scroll-view / 修 V3、抽 ImageUploader、scroll-view / 提交真实档口 / **P0**+P1 / M
- **my-moments** / 复用 MomentCard 未抽 / 复用 MomentCard / 一致 / P2 / S
- **profile/publish-dish** / 表单裸、缺 scroll-view / 抽表单 + scroll-view / 可滚 / P1 / M
- **profile/submit-stall** / 同上 / 同上 / P1 / M
- **profile/my-publish** / PublishItem 裸 / 抽 PublishItem / 一致 / P2 / S
- **profile/my-submissions** / SubmissionItem 裸 / 抽 SubmissionItem / 一致 / P2 / S
- **settings** / 无 scroll-view、免打扰虚假控制 / scroll-view + 去/标虚假项 / 可滚、无虚假控制 / P1 / S
- **feedback** / FeedbackForm 裸 / 抽 FeedbackForm + scroll-view / 表单规范 / P1 / S

**工具**
- **webview** / 仅 `web-view` 容器 / 加 loading + 失败兜底 / 外链体验完整 / P2 / S

## 4. 需技术负责人 / 后端裁定项
- **标签体系**（dish/canteen/stall 标签）：前端按 `task-05` 实现，待后端建表 + 接口；技术负责人已在 `CONTRACT_IMPACT` 登记缺口。
- **后端接口缺口**（需在 `CONTRACT_IMPACT` 登记后方可实现）：档口列表 API（RelatedPickerSheet 用）、canteen detail、stall detail、review-list、contact、feedback、settings、profile 子页数据。
- **档口图片数量**：提案建议 5→3，待用户拍板，先按现有。

## 5. 验收门禁（质量把控依据）
1. **P0 零残留**：V1（find `#card`）、V2（canteen `#card`）、V3（档口 id 伪造）必须修复且真机验证。
2. 全端列表三态（Loading/EmptyState/正常）齐备。
3. AppButton `type` 全端对齐白名单，无裸 `<button>` 承担主操作。
4. 表单页根容器均为 scroll-view。
5. 无 `v-for :key="index"`、无 `v-if="true"`、无硬编码 action-bar 高度、无虚假控制项。
