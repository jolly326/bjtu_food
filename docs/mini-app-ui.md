# 食在交大小程序端 UI 设计方案（定稿）

小程序端（frontend/src，uni-app）全部核心页面的最终 UI 设计方案，经用户与 UI-UX 设计师讨论定样。

- 执行依据：`tasks/task-13-miniapp-ui-design.md`（含逐页整改清单 + 验收门禁）
- 最高红线：`project_spec.md` §4（尤其 §4.9 小程序 slot 红线、§3 金额 api 层统一）

---

## 0. 设计基线

### 0.1 实际页面目录
- **TabBar**：`home/index.vue`、`find/index.vue`、`community/index.vue`、`profile/index.vue`
- **详情**（pages-detail/）：`dish.vue`、`canteen.vue`、`stall.vue`、`moment.vue`、`review.vue`、`review-list.vue`、`contact.vue`
- **社区**：`publish-moment/index.vue`、`my-moments/index.vue`
- **我的子页**（profile/）：`publish-dish.vue`、`submit-stall.vue`、`my-publish.vue`、`my-submissions.vue`；另有 `settings/index.vue`、`feedback/index.vue`
- **工具**：`webview/index.vue`

### 0.2 基础组件（可复用 / 改造）
AppButton / CardSection / CategoryTabs / CustomTabBar / DishCard / EmptyState / header / ImageFallback / ImageSwiper / Loading / MomentCard / Rating / SearchBar / StallCard / StatsCard / StatusBadge / TagLabel / UsefulButton / WaterfallList。

### 0.3 全局视觉 Token
- 圆角：卡片 `--radius-card`(16px)、底弹层 `--radius-modal`(20px 20px 0 0)、标签 `--radius-tag`、按钮 `--radius-btn`。
- 按下缩放：`--press-scale`(≈0.97)；入场：`enter-up` 配合 `--enter-i` 轻量位移（≤8rpx）。
- 半透材质：CustomTabBar/底栏用 `backdrop-filter: blur() saturate(180%)` + 降级纯色。
- 字体：tracking 随字号（`-0.01em`~`-0.02em` 大标题），系统字体优先。
- 分区标题装饰：列表/分区标题统一左侧竖向 accent 条（如 `--accent-bar` 4rpx×28rpx 品牌色），全端一致（首页食堂入口/热门菜品已有，其余页面补齐）。
- 情感语义唯一：喜欢=ic-heart 图标、有用=ic-thumb 图标（均引用 `assets/icons` 下 SVG，经统一图标组件渲染，见 0.5）。

### 0.4 全局统一约束
- 列表/详情页必须齐备 Loading / EmptyState / 正常三态。
- 禁止 `wx:for` 内具名 slot 分发（§4.9）；列表 `v-for` 用稳定 id 作 `:key`，禁用 `index`。
- 详情页底部操作栏避让须用统一 `--action-bar-height` 变量联动。
- 金额分↔元转换仅在 api 层（`fenToYuan`/`yuanToFen`），页面/组件禁止裸算。
- 分区标题统一使用左侧竖线装饰（accent bar），全端一致；首页食堂入口/热门菜品已有，其余页面（find/community/profile/canteen/stall/dish/review-list 等）的分区标题须补齐同款竖线。

### 0.5 图标资源规范
- **统一来源（矢量图标）**：所有功能图标与情感图标（喜欢 ❤️、有用 👍 等）一律使用 **SVG 矢量图标**，来源优先级：① 本地 `Assets`（项目内已有图标优先复用）② 阿里云矢量库（Iconfont）通过 MCP 拉取下载。
- **禁止 emoji 字符当图标**：参照 ui-ux-pro-max 优先级 4（Style Selection）红线「SVG icons (no emoji)」「Emoji as icons」为反模式；当前以 Unicode emoji 充当图标/语义的实现须改为矢量图标。
- **视觉一致性（参考 apple-design + ui-ux-pro-max）**：图标统一线性/面性风格、2px 描边、24px 网格、圆角端点一致；语义清晰、克制，不做无意义装饰。
- **落地约束**：图标随下载落统一图标目录（如 `frontend/src/assets/icons`）经统一图标组件/字体类引用；新语义图标先登记后使用。`frontend/src/assets/icons` 已有 26 个线性 SVG（含本次经 Iconfont MCP 补充的 ic-lightbulb），其余为手写线性风格，统一 `fill:currentColor`、2px 描边、24px 网格、圆角端点一致。

**图标映射表（emoji → 语义 → 图标文件）**

| 原 emoji | 语义 | 图标文件 | 来源 |
|---|---|---|---|
| 🔍 | 搜索 | ic-search.svg | 本地 |
| 📍 | 位置 | ic-location.svg | 本地 |
| ❤️ | 喜欢/收藏 | ic-heart.svg | 本地 |
| 👍 | 有用/点赞 | ic-thumb.svg | 本地 |
| 🔥 | 热门 | ic-fire.svg | 本地 |
| ⏰ | 限时 | ic-clock.svg | 本地 |
| 💡 | 猜你喜欢 | ic-lightbulb.svg | Iconfont MCP（本次新增）|
| 📤 | 分享 | ic-share.svg | 本地 |
| 💬 | 评价/评论 | ic-comment.svg | 本地 |
| ➕ | 发布 | ic-plus.svg | 本地 |
| ⚠️ | 举报 | ic-report.svg | 本地 |

> 新增语义图标须先在此表登记，再将 SVG 下载至 `assets/icons`，禁止 emoji 字符当图标。

---

# 一、TabBar 页面

## 1.1 home（首页 / 推荐流） `pages/home/index.vue`
**定位**：App 门面与默认落地页，让用户一进入即触达 Banner 活动 / 食堂入口 / 热门菜品，1 次点击进任意食堂或菜品详情。

**信息架构（自上而下）**
1. Header「食在交大」
2. Banner 轮播（按 `target_type` 分发跳转；无数据整块隐藏）
3. 广播通知条（细长 ticker：**仅 通知图标 + 文本内容，无「查看全部」；内容每秒上下滚动轮换**，按广播类型分发跳转，预留多种广播信息，不写死社区）
4. 食堂入口（横滑卡片：图 + 名，**不含营业状态**）
5. 热门菜品（双列瀑布流 + 无限加载；**移除右侧「上拉加载更多」提示，加载态仅以底部 footer / 无感呈现**）
6. CustomTabBar

**关键交互**：Banner 点击按 `targetType` 分发（DISH/URL→webview/复制链接/NONE）；食堂卡/菜品卡 tap 直接导航；下拉刷新 + 触底加载更多。

**组件拆分**：复用 `WaterfallList`、`CustomTabBar`、`EmptyState`、`Loading`、`Header`；横滑食堂卡抽 `CanteenCard`；广播条按 `v-if="broadcastList.length"` 接真实数据。

**视觉规范**：圆角/阴影/材质 ✓；按下 `scale(0.97)` ✓；层次清晰、留白一致、可读性良好。

---

## 1.2 find（发现 / 搜索 / 筛选） `pages/find/index.vue`
**定位**：主动探索与精准检索，通过搜索、分类、热搜、多维筛选快速定位菜品。

**信息架构**
- 顶部：Header + 内联搜索框（联想 debounce 300ms，**搜索框宽度放宽至接近内容区满宽，左右留白与首页一致**）
- 主区（非筛选态）：历史搜索 / 分类宫格（**去除「分类」标题文字，8 个网格直接置于搜索框下方**） / 本周热搜（**每条左侧配图，图片圆角正方形；增大每条上下高度并补充更多信息如热度值/关联数**）
- 筛选结果态：排序条 + Waterfall + 触底 footer
- 筛选 Sheet（食堂/价格/口味）

**关键交互**：搜索 input debounce → 联想；confirm → 进筛选态；筛选 Sheet spring 0.8/0.3 遮罩点击关闭。

**组件拆分**：复用 `WaterfallList`、`SearchBar`、`EmptyState`、`DishCard`、`CustomTabBar`、`Header`；新增 `FilterSheet`、`HotSearchList`、`HistoryChips`。

**视觉规范要点**
- 列表的筛选 tab / 分段选择器（SegmentTabs）视觉须精致、有辨识度（重设计，非普通文字 tab）。
- 图标均来自 §0.5 映射表；Sheet `cubic-bezier(0.32,0.72,0,1)` 0.3s ✓。
- 结果列表禁止具名 slot 分发（§4.9）；搜索框一律 `SearchBar`；筛选 Sheet 须抽 `FilterSheet`。

---

## 1.3 community（动态 / 广场） `pages/community/index.vue`
**定位**：UGC 社区广场，浏览动态流并一键发布。

**信息架构**：Header「动态」+ 两 Tab（最新/推荐）→ MomentCard 流 + 触底 footer → 悬浮发布 FAB（ic-plus）。

**关键交互**：Tab 切换重载；下拉刷新；触底无限加载；FAB tap → publish-moment。

**组件拆分**：复用 `MomentCard`、`EmptyState`、`CustomTabBar`、`Header`、`Loading`；分段 Tab 抽 `SegmentTabs`；FAB 按下统一 `scale(0.97)`。

**视觉规范**：筛选态与非筛选态切换清晰；分段选择器须统一组件（SegmentTabs 规范）。

---

## 1.4 profile（我的 / 登录注册 / 贡献入口） `pages/profile/index.vue`
**定位**：个人中心 + 鉴权入口 + 贡献统一入口。

**信息架构**
- 未登录：Auth Hero + 登录/注册/找回 表单（**登录注册界面需重构，现版不合理**）
- 已登录：用户信息卡 → 统计（**不再独占整屏宽度，改为紧凑内联/小卡片**） → 「我要贡献」入口（**与统计/菜单同宽，不单独占满整屏宽**） → 菜单组 → 版本 → 退出；**整体 UI 重新设计，更紧凑均衡**
- 「我要贡献」Sheet / 申请下架 Sheet / 昵称编辑 Modal

**关键交互**：登录方式切换；发送验证码 60s 倒计时；内联错误就近展示；退出/注销二次确认。

**组件拆分**：复用 `AppButton`、`CustomTabBar`、`Header`、`StatusBadge`、`Loading`；新增 `AuthForm`（重构）、`ContributeSheet`、`ApplySheet`、`MenuItem`、`StatsRow`；菜单组抽 `SettingGroup`/`SettingCell`。

**视觉规范要点**
- 不向用户直接展示数据库内部 id。
- 贡献/申请/关联选择等底部弹层必须抽独立组件。

---

# 二、详情页（pages-detail/）

## 2.1 dish（菜品详情） `pages/pages-detail/dish.vue`
**定位**：单菜品深度查看 + 评价入口。

**信息架构**
- ImageSwiper → 标题 / 价格 / 标签 / 评分行
- CardSection「菜品信息」
- CardSection 评价区（**进入评价详情的入口位于「用户评价」卡片内，而非星级右侧**）
- 底部 action-bar（写评价 / 去档口）

**关键交互**：评价区点击爱心图标（ic-heart）表示「喜欢」，乐观更新（图标取自 iconfont/本地 Assets，非 emoji 字符）；长按菜品名（仅作者）删除。

**组件拆分**：复用 `ImageSwiper`、`CardSection`、`TagLabel`、`UsefulButton`、`EmptyState`、`AppButton`、`Rating`、`Loading`；抽 `ReviewItem`、`ApplySheet`；底部操作栏避让用统一 `--action-bar-height`。

**视觉规范要点**
- **菜品详情卡片右上角的筛选须去除**，该筛选能力迁移至全部评价页（review-list）。
- 详情页底部操作栏避让须用统一变量联动。

---

## 2.2 canteen（食堂详情） `pages/pages-detail/canteen.vue`
**定位**：食堂概况 + 档口浏览。

**信息架构（重构后，与档口详情同构：上介绍信息、下各档口列表）**
1. ① 食堂介绍与信息区块（图 + 名称 + 简介 + 基础信息，**内容更详细：补充营业时间/地址/档口数/综合评分等**）
2. ② 各个档口的单列卡片流（每张卡 = 一个档口：档口图 + 名称 + 简介 + 评分/标签；**档口图上去除「新」角标；卡片设计更详细（补充评分/菜品数/人均/标签等），卡片尺寸保持不变**；点击进档口详情；**不直接显示菜品**）

**关键交互**：档口卡 tap → 档口详情（stall.vue）。

**组件拆分**：复用 `WaterfallList`（单列模式）、`EmptyState`、`CardSection`、`AppButton`、`Header`；档口卡抽 `StallCard` 单列版；禁止向 `WaterfallList` 传具名 slot，统一 `@card-click="goToStall"`。

**视觉规范**：单列档口卡类似美团，层次清晰、留白一致。

---

## 2.3 stall（档口详情） `pages/pages-detail/stall.vue`
**定位**：单档口信息 + 菜品列表。

**信息架构**：Header + ImageSwiper + 信息卡 → 全部菜品列表（行式）→ 弱化的「反馈/申请关闭纠错」。

**关键交互**：菜品行 tap → 详情。

**组件拆分**：复用 `ImageSwiper`、`ImageFallback`、`TagLabel`、`AppButton`、`Header`、`EmptyState`、`Loading`；抽 `StallDishRow`；须齐备 Loading/EmptyState 三态。

**视觉规范要点**
- **菜品图片必须为圆角正方形（非圆形，须修复当前仍为圆形的实现）**。
- 分隔线用 `--border-color`，对比清晰。

---

## 2.4 moment（动态详情） `pages/pages-detail/moment.vue`
**定位**：单条动态全文 + 评论互动。

**信息架构**：Header + 发布者（**头像圆角正方形**） + 审核态徽标 → 正文全文 + 九宫格大图 → 关联对象卡 → 互动栏（有用 ic-thumb / 评论 ic-comment / 举报 ic-report）→ 评论区 + 底部评论输入栏。**去除动态详情内的 tab bar / 分段筛选（无意义）**。

**关键交互**：图片预览；有用（ic-thumb）乐观更新；评论回复；举报 Modal。

**组件拆分**：复用 `EmptyState`、`Header`、`Loading`、`ImageSwiper`、`UsefulButton`；抽 `CommentItem`、`InteractBar`、`ReportModal`。

**视觉规范要点**：举报须弱化为次级入口，不得与正向互动同权并列；「有用」统一 `UsefulButton`；发布者头像圆角正方形；**须修复进入动态详情空白问题（数据绑定/空态渲染，确保有内容时正常展示）**。

---

## 2.5 review（发表评价） `pages/pages-detail/review.vue`
**定位**：提交对菜品的评价。

**信息架构**：Header + CardSection(评分) + CardSection(内容) + CardSection(图片≤3) + 提交按钮。

**关键交互**：Rating 打分；textarea 计数；图片 chooseImage(≤3)；提交校验。

**组件拆分**：复用 `CardSection`、`Rating`、`AppButton`、`Header`；抽 `ImageUploader`；根须用 `scroll-view` 包裹全部内容。

**视觉规范要点**：AppButton 类型白名单 primary/outline/text/danger/gradient；多 CardSection 表单页强制 scroll-view；提交按钮文案随状态变化。

---

## 2.6 review-list（全部评价 / 我的评价） `pages/pages-detail/review-list.vue`
**定位**：某菜品全部评价 或 我的评价列表。

**信息架构**：Header + 筛选条（**承接从 dish 迁移来的筛选：评分/排序**） + 列表(ReviewItem) + EmptyState + 触底。

**关键交互**：排序切/带图切 → 重载；点击爱心图标（ic-heart）表示「喜欢」，乐观更新；长按删本人。

**组件拆分**：复用 `UsefulButton`、`EmptyState`、`Header`、`Rating`、`Loading`；抽 `ReviewItem`（与 dish 共用）；须齐备 Loading 态，超 50 条分页。

**视觉规范**：三处评价卡片必须复用同一 `ReviewItem`；评价互动统一为爱心「喜欢」图标（SVG，取自 iconfont/本地 Assets），禁用 emoji 字符。

---

## 2.7 contact（联系开发者） `pages/pages-detail/contact.vue`
**定位**：用户向开发者反馈。

**信息架构**：Header + 反馈类型(picker) + 内容 + 联系方式 + 提交。

**关键交互**：picker 选类型；提交 → 成功态。

**组件拆分**：复用 `AppButton`、`Header`、`CardSection`、`EmptyState`；与 `feedback/index.vue` 抽同一 `FeedbackForm`；须走 `submitFeedback` api 层 + 统一类型枚举。

**视觉规范**：反馈类页须复用同一 `FeedbackForm` 与同一类型枚举；新语义图标先登记后使用。

---

# 三、社区编辑 / 我的动态

## 3.1 publish-moment（发布 / 编辑动态） `pages/publish-moment/index.vue`
**定位**：创作动态（图文 + 可选关联菜品/档口）。

**信息架构**：Header + 正文 textarea + 图片网格(≤9) + 关联对象卡 + 底部提交栏；关联对象 Sheet。

**关键交互**：图片 chooseMedia(≤9)；关联 Sheet 搜菜品/档口；提交校验。

**组件拆分**：复用 `AppButton`、`Header`、`ImageUploader`、`SearchBar`；抽 `RelatedPickerSheet`（关联对象卡与 Sheet 内搜索须用 `SearchBar`）。

**视觉规范**：关联对象选择必须走各自正式搜索 API，禁止借道伪造 id（档口联想须返回真实 `stallId`）。

---

## 3.2 my-moments（我的动态） `pages/my-moments/index.vue`
**定位**：用户自己动态的审核状态管理。

**信息架构**：Header + 分段(全部/审核中/已退回) + 列表(MomentCard show-audit)。

**关键交互**：分段切重载；tap 卡片 → 退回进编辑。

**组件拆分**：复用 `MomentCard`、`EmptyState`、`Header`、`Loading`、`SegmentTabs`；徽标计数须响应式（`ref`/`reactive`）。

---

# 四、我的子页（profile/）

## 4.1 publish-dish（发布 / 编辑菜品） `pages/profile/publish-dish.vue`
**定位**：学生发布/编辑菜品（UGC）。

**信息架构**：Header + CardSection(基本信息/食堂档口 picker/口味标签/图片/描述) + 提交。

**关键交互**：picker 选食堂→联动档口；标签 chips 多选；图片≤9。

**组件拆分**：复用 `CardSection`、`AppButton`、`Header`、`ImageUploader`、`TagSelector`；标签来源统一引用常量；金额展示保持 api 层统一（前端 `¥{{price}}` 已是元，不裸算）。

---

## 4.2 submit-stall（提交档口·食堂） `pages/profile/submit-stall.vue`
**定位**：学生补充档口/食堂。

**信息架构**：Header + 类型切换 + CardSection + 提交。

**关键交互**：类型切换显隐字段；picker 选食堂；图片≤9。

**组件拆分**：复用 `CardSection`、`AppButton`、`Header`、`ImageUploader`、`TypeSwitch`；须走 `api/stall.ts` 封装（禁止直连 HTTP），齐备 Loading/提交中态。

---

## 4.3 my-publish（我的发布） `pages/profile/my-publish.vue`
**定位**：学生已发布菜品/档口·食堂的审核状态总览。

**信息架构**：Header + 分段(菜品/档口·食堂) + 列表 + 底部操作。

**关键交互**：分段切重载；菜品项 tap → 编辑。

**组件拆分**：复用 `StatusBadge`、`EmptyState`、`AppButton`、`Header`、`SegmentTabs`、`PublishItem`；移除 `as any` 兜底，齐备 Loading 态。

---

## 4.4 my-submissions（我的提交） `pages/profile/my-submissions.vue`
**定位**：学生「申请下架/纠错」与动态提交记录聚合查看。

**信息架构**：Header + 分段(实体/动态) + 列表 + EmptyState。

**关键交互**：分段切；动态项 tap → 详情。

**组件拆分**：复用 `StatusBadge`、`EmptyState`、`Header`、`SegmentTabs`、`SubmissionItem`；统一 `SubmissionItem`，off 态用 `StatusBadge` 扩展状态，齐备 Loading 态。

---

## 4.5 settings（设置） `pages/settings/index.vue`
**定位**：应用设置与账号管理。

**信息架构**：Header + 分组(通知/通用/账号) + 版本。

**关键交互**：通知开关；关于/隐私 Modal；清缓存；退出/注销二次确认。

**组件拆分**：复用 `Header`；抽 `SettingGroup`/`SettingCell`（与 profile 菜单统一）。

**视觉规范**：未生效设置（如暂无后端订阅能力的通知开关）须显式标注「（即将推出）」或默认隐藏，禁止虚假控制。

---

## 4.6 feedback（意见反馈） `pages/feedback/index.vue`
**定位**：用户建议/Bug 反馈。

**信息架构**：Header + 类型 chips + 内容 textarea + 联系方式 + 提交栏。

**关键交互**：类型 chip 单选；内容计数；提交校验。

**组件拆分**：复用 `AppButton`、`Header`、`FeedbackForm`、`CardSection`；contact 与 feedback 复用同一 `FeedbackForm` 与 `CardSection`。

---

# 五、工具

## 5.1 webview（外部链接） `pages/webview/index.vue`
**定位**：承载 Banner URL / 公众号文章等外部 H5。

**信息架构**：Header + web-view(src) 或 无效链接 fallback。

**关键交互**：onLoad 取 src/title；加载失败 → 复制链接 Toast。

**组件拆分**：复用 `Header`；加 web-view 加载中 Loading；空 src 也提供「复制链接」入口。

---

# 六、组件拆分总览（去重）

**必须抽的基础组件**
1. `ImageUploader`（publish-dish / submit-stall / review / publish-moment 图片网格）
2. `SegmentTabs`（community / my-moments / my-publish / my-submissions 分段）
3. `ReviewItem`（dish / review-list 评价卡共用）
4. `ApplySheet`（dish / profile 申请下架/纠错）
5. `FeedbackForm`（contact / feedback）
6. `SettingGroup`+`SettingCell`（settings / profile 菜单）
7. `RelatedPickerSheet`（publish-moment，且须走真实档口搜索 API）
8. `SubmissionItem`/`PublishItem`（my-publish / my-submissions）
9. `CanteenCard`（home 横滑）/ `StallCard` 单列版（canteen）
10. `FilterSheet`（find 筛选）
11. `InteractBar` / `CommentItem` / `ReportModal`（moment 详情）
12. `AuthForm` / `ContributeSheet`（profile，AuthForm 重构）
13. `StatsRow`（profile 统计）

**已有组件须充分复用**：`SearchBar`、`CardSection`、`Loading`、`UsefulButton`。
