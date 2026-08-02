# 小程序 UI 设计文档（mini-app-ui）

本目录为「食在交大」uni-app 微信小程序（学生端）的**逐页 UI 设计文档**。每个页面一份 Markdown，便于产品/设计直接编辑，开发者读取本目录即可同步 UI 设计决策与整改意图。

- **用途**：每份 `<pagekey>.md` 记录该页面当前的真实 UI 结构、所用组件、设计 Token、交互动效、一致性红线自检，以及留给产品/设计的「待定 / 可编辑的设计方案」区块。
- **协作方式**：产品/设计可编辑任意文档，提出或记录 UI 改动意向；开发者据此实现或对齐。文档内容基于 `frontend/src` 下各 `.vue` 当前代码，非凭空臆测。
- **配套规范源**：图标映射表见 `docs/mini-app-ui/icons.md`（权威 `ic-*` 注册表，原 `mini-app-ui.md` §0.5 已迁移）；各页面设计见本目录对应 `<pagekey>.md`；设计 Token / 一致性红线（rpx Token 清单、按压 scale、禁 emoji 等）见 `docs/project_spec.md` §4 与 §4.9。

## 目录（15 页）

> 与 `frontend/src/pages.json` 严格一致。**主包 7 + 分包 `pages-detail` 4 + 分包 `pages-user` 4 = 15 页**。
> 被移除页面（历史遗留 doc，不再有路由）：`contact`（已并入 feedback）、`notify`（消息并入「我的」）、`my-publish` / `my-submissions`（合并进 `messages-services`）、`review-list`（评价改详情内联）、`webview`（已移除，外链改复制链接，见 task-07）、`dish` 独立页（已改为底部弹层 `DishDetailSheet`，见 task-10）。

### 主包（7）
| 文件 | 页面 | 一句话用途 |
|---|---|---|
| [home.md](home.md) | 首页 | 轮播 + 广播条 + 食堂入口 + 热门菜品瀑布流 |
| [find.md](find.md) | 发现 | 搜索/历史/分类/热搜 + 多维筛选结果页 |
| [profile.md](profile.md) | 我的 | 未登录态登录注册 / 已登录态用户卡 + 菜单（含消息区块） |
| [community.md](community.md) | 动态 | 最新动态单流 + 悬浮发布 FAB |
| [settings.md](settings.md) | 设置 | 通知/通用/账号 分组设置 |
| [feedback.md](feedback.md) | 意见反馈 | 类型 + 内容 + 联系方式，底部提交 |
| [messages-services.md](messages-services.md) | 我的发布与贡献 | 「我的发布」+「我的贡献」唯一聚合页（吸收 my-publish / my-submissions） |

> 注：`webview`（外部链接）页已移除（task-07），Banner/广播外链改「复制链接 + toast」，不再有独立 web-view 容器页（相关设计文档已随页面一并删除）。

### 分包 pages-detail（4）
| 文件 | 页面 | 一句话用途 |
|---|---|---|
| [canteen.md](canteen.md) | 食堂详情 | 图集 + 食堂信息 + 档口列表 + 评价 |
| [moment.md](moment.md) | 动态详情 | 正文/九宫格/评论/举报 + 评论输入栏 |
| [stall.md](stall.md) | 档口详情 | 图集 + 档口信息 + 全部菜品 + 评价 |
| [review.md](review.md) | 发表评价 | 评分 + 内容 + 图片，吸底提交 |

> 注：`dish` 菜品详情已不再独立页（task-10），改为底部弹层组件 `DishDetailSheet`（经各入口组件打开），不占独立路由。设计内容见 [dish.md](dish.md)（顶部已标注 ⚠️ 已弹层化）。

### 分包 pages-user（4）
| 文件 | 页面 | 一句话用途 |
|---|---|---|
| [publish-moment.md](publish-moment.md) | 发布动态 | 发动态主入口：正文 + 关联对象 + 图片，底部提交栏 |
| [my-moments.md](my-moments.md) | 我的动态 | 全部/审核中/已退回 分段 + 列表 |
| [publish-dish.md](publish-dish.md) | 发布菜品 | 基本/食堂档口/标签/图片/描述 表单（经「我要贡献」弹层进入） |
| [submit-stall.md](submit-stall.md) | 提交档口·食堂 | 档口或食堂信息提交表单（经「我要贡献」弹层进入） |

> 注：分包页面实际路径为 `src/pages/pages-detail/`（root = `"pages/pages-detail/"`）与 `src/pages/pages-user/`（root = `"pages/pages-user/"`），本目录文档按 `pagekey` 命名，便于检索。

## 依据的设计 Skills

本目录所有页面文档的 §8「Skill 合规自检」统一以如下 9 个 UI 设计 Skills 为权威依据，逐页对照其规则得出「合规 / 部分 / 待整改 / ➖不适用」结论。

| Skill | 一句话用途 |
|---|---|
| **ui-ux-pro-max** | UI/UX 设计情报库（风格 / 配色 / 字体 / 产品 / 颜色 / 排版），提供 84 风格、192 配色、74 字偶、98 产品类型等可检索数据库。 |
| **apple-design** | Apple 人机界面（HIG）理念：触控目标 ≥44pt、按压即时反馈 scale(0.97)、spring 动效、安全区、prefers-reduced-motion 降级，落地到小程序可执行的动效与材质规范。 |
| **review-animations** | 动效评审标准（STANDARDS.md）：micro-interaction <300ms、可中断、transform/opacity 优先、非色相唯一语义、tabular-nums 等量化验收口径。 |
| **emil-design-eng** | Emil Kowalski 的 UI 打磨哲学：不可见细节、组件手感、动画决策，用于提升微交互质感（按下反馈、入场、层级）。 |
| **improve-animations** | 动效审计方法论（AUDIT.md）：以资深动效顾问视角扫描代码库，产出优先级清单与自包含实施方案。 |
| **design-system** | 三层 Token 架构（primitive→semantic→component）+ 组件规格 + 状态变体，约束本项目 CSS 变量与语义化颜色/间距/圆角。 |
| **brand** | 品牌一致性（visual-identity + consistency-checklist）：图标语义契约、禁用 emoji、品牌色与占位规范，对应本项目一致性红线 ①。 |
| **ui-styling** | 现代可访问 UI 构建：语义 token、对比度 ≥4.5:1、非色相单一含义、表单可访问性，约束本项目颜色与表单规范。 |
| **banner-design** | 横幅/英雄区/社交卡视觉方向，用于首页轮播、活动横幅等图集类模块的视觉决策参考。 |

## 每页 Skill 合规自检栏（§8）说明

- 每份页面文档的 **§8「Skill 合规自检（UI 设计 skills）」** 是一张固定 **21 项**的检查表，作为该页 UI 的**逐页验收红线**，由上述 9 个 Skills 共同推导而来（每项均有可核查的代码位置 `file:line`）。
- 表格三列固定为：`# 检查项` · `结论` · `说明`。结论取值：
  - **合规**：该页已实现且符合 skill 规则；
  - **部分**：实现了但存在不完整/边缘瑕疵（如某交互缺按压反馈），说明中给出具体位置与期望；
  - **待整改**：明确违反 skill 或一致性红线（如中性占位误用 `home` 而非 `empty`、对比度低于 4.5:1），说明中给出 file:line 与推荐改法；
  - **➖**：该项对该页不适用（如纯静态容器页无按压目标）。
- **21 项覆盖维度**：触控目标尺寸与间距、按压即时反馈 scale(0.97)、安全区/小横条避让、无 emoji（IconSvg 仅）、语义化颜色 token（禁裸 hex）、对比度 ≥4.5:1、非色相单一语义、prefers-reduced-motion 降级、hover 门控、微交互 <300ms、自定义缓动（禁用 ease-in）、禁 scale(0) 入场、仅 transform/opacity 动画、动画可中断、数字 tabular-nums、正文 ≥16px/32rpx、单一主 CTA、加载/空/错误三态、表单可访问性、导航一致性、一致性打磨（SectionTitle/Sheet/底栏变量）。
- **一致性红线交叉**：第 ④ 项（图标）与品牌 Skill、一致性红线 ① 强绑定；第 ⑦ 项（语义 token）与 design-system、一致性红线 ⑥/⑦ 强绑定；第 ⑨/⑯ 项与 apple-design 安全区、ui-styling 对比度强绑定。任一「待整改」均视为阻断级，需整改后复测。
- **Native API 例外**：第 ⑤ 项中 `swiper` 指示器色、`uni.showModal` 的 `confirmColor` 因不接受 `var()`，已在 `uni.scss` 登记，计为**合规**，不计违规（见下方「Native API 例外」）。

## 设计规范速查

### 一致性红线（来自 project_spec §4.9 / 各页文档 §0.3、§0.4）

> 以下为强约束，凡页面文档「一致性红线自检」一节均逐条对照。

1. **图标全走 IconSvg**：所有功能/情感图标统一经 `<IconSvg name="…" />` 渲染 `frontend/src/assets/icons` 下 SVG；**全量禁用 emoji**（Unicode emoji、`emoji.ts` 一律禁止）。缺失图标经 Iconfont MCP 补齐。
   - 图标语义契约：喜欢=`heart`、有用/点赞=`thumb`、评分=`star`（三者互不混用）。
   - 中性占位必须为 `empty`（非 `dish`）：图破/空态语境禁止用 `dish` 冒充中性占位（首页食堂卡、find 联想、ImageFallback、about 等用 `empty`；仅 DishCard 菜品图占位可用 `dish`）。
2. **金额仅 api 层转换**：分↔元由 `utils/money` 的 `fenToYuan`/`yuanToFen` 处理，页面/组件禁止裸算（`/100`）；模板直接展示已为「元」的 `price`。
3. **WaterfallList 禁具名 slot**：组件内部已直接渲染 `DishCard`/`StallCardSingle`，父级只经 `@card-click`/`@stall-click` 事件上抛，**禁止向其传具名 slot**。
4. **三态齐备**：列表/详情页必须有 ①加载中（骨架屏/内联态）②空态（`EmptyState`）③正常态；三者缺一即阻断级缺陷。
5. **Sheet 规范**：所有 bottom-sheet（`ApplySheet`/`ContributeSheet`/`NicknameSheet`/`FilterSheet`/`RelatedPickerSheet`/分享面板等）统一 spring `0.8/0.3`（抽屉缓动 `cubic-bezier(0.32,0.72,0,1)`）+ `ic-close` 关闭 + 下拉关闭手势（仅向下、阈值~120px）+ `prefers-reduced-motion` 降级交叉淡入。
6. **按压缩放统一值**：可点元素按下一律 `transform: scale(var(--press-scale))`，`--press-scale` 固定 `0.97`；禁止 `scale(0.95/0.985/0.99)` 等非 0.97 值。非按压强调（如 CustomTabBar 选中放大）须量化独立 token（如 `--tab-active-scale: 1.05`）并在 `uni.scss` 登记。
7. **颜色全走语义 token**：禁止裸 hex（含标签底色、IconSvg `color`、文字/边框/背景）。原生 API 不接受 `var()` 的例外（`swiper` 的 `indicator-active-color`/`indicator-color`、`uni.showModal` 的 `confirmColor`）须在 `uni.scss` 注释登记，登记后不作为违规。
8. **分区标题统一 SectionTitle**：任何分区/区块标题须渲染 `<SectionTitle title="…" />`，禁止手写 `.section-title`/`.section-head` 无 accent 条标题。表单内「区块小标题」可用 `SectionTitle`（卡片内用 `noMargin`）；字段行内 label 属字段语义，允许纯 text。
9. **底部固定栏避让统一变量**：详情/表单页底部操作栏统一用 `--action-bar-height`（120rpx）联动，避免被 iPhone 小横条遮挡（含 `env(safe-area-inset-bottom)` 安全区）。

### 核心设计 Token（来自 App.vue 的 `page`/`root` 块，rpx 为主）

**间距（4pt 栅格）**：`--spacing-xs` 8rpx · `--spacing-sm` 16rpx · `--spacing-md` 24rpx · `--spacing-lg` 32rpx · `--spacing-xl` 48rpx
**圆角**：`--radius-card` 16rpx（卡片）· `--radius-modal` 24rpx（弹层顶）· `--radius-btn` 16rpx · `--radius-tag` 999rpx（标签/胶囊）· `--radius-icon` 12rpx
**字体**：`--font-tiny` 20 · `--font-aux` 22 · `--font-small` 24 · `--font-body` 28 · `--font-caption` 30 · `--font-subtitle/--font-card` 32 · `--font-h3` 36 · `--font-h2` 40 · `--font-h1` 48（单位 rpx）
**颜色（关键）**：`--color-primary` #8B3A2B（品牌深红）· `--color-primary-soft/#2/#bg`（浅底系列）· `--color-accent` #2F7D72 · `--color-price` #C0392B · `--color-star` #FFB400 · `--color-star-empty` #E8E0D8 · `--color-hot` #E67E22（限时/促销）· `--color-like` #ff6b6b · `--color-error` #E54D42 · `--color-success` #10B981 · `--color-warning` #F5A623
**文字**：`--text-primary` #1C1917 · `--text-secondary` #6B625B · `--text-tertiary` #A89E96 · `--text-quaternary` #aaa19a
**背景/边框**：`--bg-page` #F6F4EF · `--bg-card` #FFFFFF · `--bg-soft` #F1ECE6 · `--border-color` #ECE6E0 · `--border-bold` #C9BFB6
**阴影/材质**：`--shadow-card` · `--shadow-modal` · `--shadow-bar`/`--shadow-bar-soft`/`--shadow-bar-primary` · `--blur-bg`（backdrop-filter 半透，真机不支持降级纯色+阴影）· `--overlay-scrim`/`--overlay-dark-*` · `--badge-dark-bg`/`--badge-dark-text`
**语义状态浅底**：`--color-error-soft` · `--color-success-soft` · `--color-warning-soft` · `--color-accent-soft` · `--color-primary-soft2` · `--color-hot-soft` · `--color-like-soft`
**动效**：`--press-scale` 0.97 · `--press-transition` `transform 0.12s ease` · `--ease-out` `cubic-bezier(0.23,1,0.32,1)` · `--ease-in-out` · `--ease-drawer` `cubic-bezier(0.32,0.72,0,1)` · `--tab-active-scale` 1.05 · `--tracking-h1/h2/h3`（-0.02/-0.02/-0.01em，标题负字距；正文 0）
**布局**：`--tabbar-height` 100rpx（主包 tab 页滚动区底部留白）· `--action-bar-height` 120rpx（详情/表单底部操作栏统一高度）· `env(safe-area-inset-bottom)`（iPhone 安全区）
**图标尺寸**：`--icon-sm` 28rpx · `--icon-lg` 48rpx

> 通用工具类（App.vue 全局）：`.page`（min-height:100vh + `--bg-page`）、`.scroll-wrap`（底部安全留白）、`.glass`（半透材质 + backdrop-filter 降级）、`.press`/`:active scale(0.97)`、`.enter-up`（错峰交叉淡入，配合 `--enter-i`）、`.skeleton`（shimmer 流光）、`@media (prefers-reduced-motion)` 与 `(prefers-reduced-transparency)` 降级。

### Native API 例外（已在 uni.scss 登记，不计违规）

- `<swiper indicator-active-color>` / `<swiper indicator-color>`：不接受 `var()`，使用 `SWIPER_INDICATOR_ACTIVE_COLOR` / `SWIPER_INDICATOR_COLOR` 常量（见 `src/constants/ui.ts`）。
- `uni.showModal` 的 `confirmColor`：不接受 `var()`，settings/index.vue 用真实色值 `#e54d42`（对应 `--color-error`）。

---

**文档可自由编辑，修改后的设计方案请直接保存，开发者会读取本目录同步实现。**
