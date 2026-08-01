# 食在交大 · 项目规格说明（project_spec.md）

> **基础规范基线（最高权威）**。所有 agent 与协作者必须服从本文件；冲突时以本文件为准。
> 本文件只规定「不会轻易变、且所有端必须遵守」的基础规范。**功能 / 接口契约 / 页面设计细节见 `tasks/`**；**多 agent 协作流程见 `docs/WORKFLOW.md`**。
> **唯一可修改者：技术负责人**（需求梳理师 + 架构师合并角色）。其余角色不得改动本文件，发现冲突须提技术负责人，不得自行绕过。

---

## 0. 系统总览

### 0.1 角色模型（仅两种）
- `STUDENT`（校内邮箱注册，兼"平鉴官"）：浏览（游客亦可）、发布 / 更新菜品、提交 / 更新档口·食堂、写评价、分享。
- `ADMIN`（系统管理员 / 食堂后勤）：审核 UGC、配 Banner、看板、食堂 / 档口 / 菜品 CRUD + 上架下架、用户 / 管理员管理。
- **无独立 `STALL_OWNER` 角色，亦无 `/stall-owner/**` 路由。**

### 0.2 数据流闭环
1. **浏览**：首页推荐 / 搜索 → 详情 → 评价 / 分享。
2. **贡献（平鉴官）**：提交 → `audit_status=pending` → 后台审核 → `approved` / `rejected`（回写 `reject_reason`，学生**复用原记录**重提回 `pending`）。
3. **运营（后勤）**：后台审 UGC / CRUD / 配 Banner → 小程序即时体现；活动不独立成模块，统一经 Banner 触达。

### 0.3 一致性红线（全局，强制）
- 角色仅 `STUDENT` / `ADMIN`；**禁止** `STALL_OWNER` 或 `/stall-owner/**` 路由；`/admin/**` 仅 `ADMIN`。
- 菜品 / 档口 / 食堂均含独立 `audit_status`(pending/approved/rejected) + `reject_reason`（与上下架 `status` 解耦）。
- Banner 跳转用 `target_type` 枚举（DISH/URL/NONE；`ACTIVITY` 已移除，活动统一经 Banner URL 外链）。
- 实体贡献「下架 / 变更」申请落**独立 `apply` 表**（不复用实体 `audit_status`）。
- 前端 UI 遵循 §4（spring 动效、即时反馈、半透材质、reduced-motion 降级）。

---

## 1. 技术栈
- 后端：Spring Boot 3.2 + Java 21，ORM MyBatis-Plus 3.5.5（BaseMapper + XML，`resources/mapper/*.xml`）；API 文档 **SpringDoc OpenAPI（`/swagger-ui.html` + `/v3/api-docs`），不使用 Knife4j**。
- 小程序端：uni-app + Vue 3 (`<script setup>`) + TypeScript + Pinia，目录 `frontend/`。
- Web 管理端：Vue 3 + Vite + TypeScript + Element Plus + ECharts，目录 `web/`，无 Pinia。
- 数据库：MySQL 8.0，库 `bjtu_food`，utf8mb4；**建表脚本唯一权威：`backend/src/main/resources/db/schema.sql`**（`user.role` 默认 `'student'`）。
- 认证：JWT（7 天），`Authorization: Bearer {token}`；密码 BCrypt。

## 2. 目录结构
- 后端按业务分包：`com.bjtufood.{auth|canteen|dish|review|content|upload|common}`，每模块 `controller/service(+impl)/mapper/entity/dto/` 四层，**禁止跨层调用**（Controller 不得直接调 Mapper）。
- 小程序 `frontend/src/`：`api/`、`types/`、`stores/`、`pages/`（**TabBar 固定 4 页：home / find / moment / profile**；收藏、消息中心、我要贡献进 `profile`，不占 TabBar）、`components/`。
- Web `web/src/`：`api/`(含 `adapter.ts`)、`views/`、`components/`、`router/`。
- 上传图片存 `uploads/images/YYYY/MM/{uuid}.{ext}`，DB 只存相对路径 `/images/...`。

## 3. API 基础规范
- 统一响应：`{ code: number, message: string, data: T }`；成功 `code=200`；异常由 `GlobalExceptionHandler` 统一包装，Controller 不得裸抛。
- 错误码：`200` 成功 / `400` 参数 / `401` 未登录 / `403` 无权限 / `500` 服务器错误；**禁止自定义非标错误码**（如 1001/600）。
- 认证：JWT 经 `JwtAuthFilter`；白名单：`/auth/login|register|email-code|password/reset`、`GET /dishes/**`、`GET /canteens/**`；学生写操作需 `STUDENT`；`/admin/**` 仅 `ADMIN`。
- 分页：`PageResult<T>{ records, total, page, pageSize }`，用 MP 分页插件；单页非分页接口返回 `List<T>`。
- 金额：存储与传输一律「分」（int/Long）；分↔元转换必须在 api 层统一（`utils/money` 的 `fenToYuan`/`yuanToFen`），**禁止页面/组件层裸算**；前端统一展示已为元的 `price`（不得再在模板 `/100`）。
- 数据隔离：`dish.created_by=当前用户`，学生仅读写自己提交；从 `SecurityUtil.getCurrentUserId()` 取用户，禁止信任前端 userId。
- **接口契约 / 状态机 / 字段命名裁决（UGC 审核、Banner、Dish、Review、User、喜欢语义、学生 UGC 路径等）见 `tasks/CONTRACT_IMPACT.md`，新增接口须先在其登记再实现。**

## 4. UI 设计规范（Apple Design 风格）

### 4.1 适用范围与八原则
- 适用端：微信小程序（uni-app）、Web 管理后台（Vue3 + Element Plus）。
- 八原则：Purpose / Agency / Responsibility / Familiarity / Flexibility / Simplicity / Craft / Delight；流体交互四要素：即时响应、1:1 直接操控、可中断、速度 / 动量接力。

### 4.2 视觉 Token（基线）
- 主色：管理端侧栏深红 `#6B1010`；小程序按钮统一 `AppButton`（primary/outline/text）。
- 圆角：卡片 `16px`；底部弹层 `20px 20px 0 0`。材质模糊 `blur(20px) saturate(180%)`；按下缩放 `0.97`；弹层阴影 `0 -8px 30px rgba(0,0,0,0.12)`。
- 小程序自研组件（新页面必须复用）：`ImageSwiper/DishCard/WaterfallList/Rating/TagLabel/CardSection/EmptyState/AppButton/CustomTabBar/MomentCard/SearchBar/StatusBadge/UsefulButton/ImageFallback/SectionTitle/StallCardSingle/ImageUploader/RelatedPickerSheet`；**`CategoryTabs`、`Loading` 组件已于清理提交 f9560c6 删除**——加载态改内联骨架屏（不再依赖 Loading 组件），分类切换由 `SegmentTabs`/筛选条替代，文档 `docs/mini-app-ui.md` §0.2 已校准。
- 管理端：Element Plus + 自封装 `DataTable/FormDialog/ConfirmDialog/StatusTag/ImageUpload`；**`SearchInput` 组件已于清理提交 f9560c6 删除**，管理端搜索统一用 `el-input`，文档 `docs/web-ui.md` §七已校准。
- **小程序图标统一使用 SVG 矢量图标**（本地 `frontend/src/assets/icons` 优先，缺失从阿里云矢量库 Iconfont 经 MCP 拉取）：搜索=ic-search、位置=ic-location、喜欢/收藏=ic-heart、有用/点赞=ic-thumb、热门=ic-fire、限时=ic-clock、猜你喜欢=ic-lightbulb、分享=ic-share、评价=ic-comment、发布=ic-plus、举报=ic-report（完整映射见 `docs/mini-app-ui.md` §0.5 图标映射表）。语义唯一：ic-heart=喜欢（不与点赞混用）、ic-thumb=有用/点赞。**禁止 emoji 字符充当图标**。

### 4.3 动效系统（Motion）
| 交互 | Damping | Response |
| --- | --- | --- |
| 常规 UI | 1.0 | 0.3–0.4 |
| 抽屉 / Sheet | 0.8 | 0.3 |
| 旋转 / 翻动 | 0.8 | 0.4 |
| 位置重排 | 1.0 | 0.4 |
- 默认全站 `damping 1.0`；仅手势带动量时加回弹 `0.8`。可中断：永远从当前屏幕呈现值起步。Web 用 Motion/Framer Motion：`1.0≈bounce 0`、`0.8≈bounce 0.2`。

### 4.4 交互反馈
- 点按：按下即时 `scale(0.97)`；命中区 +~10px 滞回，可按住拖离取消。
- 抽屉 / Sheet：spring `0.8/0.3`，手势可中断、按速度符号决定提交 / 回弹（阈值 ~50%）。
- 弹窗：锚定触发源，进出同路径、缓动镜像对称。
- 滚动橡皮筋：`(over·dim·k)/(dim+k·|over|)`，`k≈0.55`。

### 4.5 材质与层级
- 半透导航 / 工具条 / 抽屉：`backdrop-filter: blur(20px) saturate(180%)` + 半透底；材质权重编码层级（结构区更重更暗，交互元素更轻更亮）；不叠两层轻透面。

### 4.6 字体排版
- tracking 随字号（大标题 `-0.02em`，正文 `0`）；leading 反比（大标题 ~1.05，正文 ~1.5）；系统字体优先；`rem`/`em` 随用户字号缩放。

### 4.7 可达性与降级
- `prefers-reduced-motion: reduce` → 交叉淡入、去弹性过冲；`prefers-reduced-transparency` → 去模糊；`prefers-contrast: more` → 近实底 + 边框。
- 小程序：无 Pointer Events，用 touch + 自记速度历史；`backdrop-filter` 真机部分支持降级纯色半透 + 阴影；动画只用 `transform`/`opacity`。

### 4.8 组件级约定
- 卡片 tap `scale(0.97)`、入场 spring `1.0/0.3`；TabBar spring `1.0/0.3`；抽屉 / Sheet §4.4 `0.8/0.3` + 手势中断；列表 / 瀑布流滚动橡皮筋；Toast 四态同帧触发；列表页三态（Loading/EmptyState/正常）。

### 4.9 小程序 MVP 红线（布局 / 动效 / 图标 / 组件渲染）
- **布局（750rpx 视口）**：根容器视为 750rpx；横向用 `flex` + `flex-wrap`/`flex:1`/`min-width:0` 防溢出；图片 / 卡片 `width:100%` + `box-sizing:border-box`；禁止横向滚动条；长文本 `-webkit-line-clamp` 截断。每页须通过「真机 750rpx 无横向滚动 / 无裁切」。
- **动效（从简）**：仅 uni-app `<transition>`（位移 ≤8rpx）与简单 CSS `transition`（opacity/transform 轻量）；禁止 `@keyframes` 长动画、大位移、`scale>1` 入场；手势 Sheet / 抽屉仍走 §4.4，入场不做复杂 keyframe。
- **图标（SVG 矢量）**：按 §4.2 映射（本地 `assets/icons` 优先 + Iconfont 兜底）；新增语义须登记图标名并将 SVG 下载至 `frontend/src/assets/icons`，禁止 emoji 字符当图标，不得私自引入未登记图标。语义唯一：ic-heart=喜欢、ic-thumb=有用/点赞，互不混用。
- **组件渲染（禁止 wx:for 内具名 slot 分发）**：小程序多列 / 瀑布流组件**禁止**在父组件用 `<template #x>` 向子组件同名 `<slot name="x">` 分发——uni-app 编译 mp-weixin 后父组件 N 个同名 slot 片段无法正确映射，子组件不消费该 slot 时整块**空白不渲染**（实测 `WaterfallList`：find/canteen 残留 `#card` 调用导致菜品区整块空白，阻断级 bug，2026-07-31）。`WaterfallList` 已内部 `import DishCard` 直接渲染，**禁止再向其传具名 slot**，统一 `<WaterfallList :list @card-click="goToDetail"/>` 经事件上抛父级。
- **小程序页面级 / 组件级 UI 设计细则（三态强制、AppButton 类型白名单、表单页 scroll-view 强制、emoji 登记前置、未生效设置禁虚假控制、金额 api 层统一、关联对象走正式 API、负向操作弱化、Sheet/SegmentTabs/ReviewItem/FeedbackForm 等组件抽取契约等 28 条）见 `tasks/task-13-miniapp-ui-design.md`，由小程序开发工程师按优先级落地；本文件仅定最高红线。**
- **UI 全量审计红线（2026-08-02 补充，BLOCKER 级，违反即阻断）**：以下规则自 2026-08-02 全量审计结果提炼，**与上方四条红线同属强制，新增/整改页面不得回退**：
  - **固定底栏避让**：任何含固定底栏（`submit-bar` / `comment-bar` / `action-bar`）的页面，其 `.scroll-wrap` 必须加 `padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom))`，**禁止**内容被底栏遮挡（BLOCKER 级）。
  - **事件绑定统一 `@tap`**：小程序内所有可点元素事件绑定统一用 `@tap`，**禁止**混用 `@click`（uni-app 编译 mp-weixin 时 `@click` 行为与 `@tap` 不一致，易致命中区/手势异常）。
  - **按压缩放统一 `var(--press-scale)`（非按压强调 scale 须量化 token）**：可点元素按下反馈一律 `transform: scale(var(--press-scale))`，`--press-scale` 固定 `0.97`，**禁止**任何裸 `scale(0.9/0.95/0.97/0.985/0.99)` 数值散落。适用范围覆盖**所有交互元素**：`.pressed` 类、`@tap` 触发元素的 `:active`、`.sheet-option`、`.cell`、action icon 等一律不得写裸 scale 值。**grep 全仓应 0 处裸 `scale(...)`**（`pages/profile/index.vue` 的注释说明除外，仅注释、非样式规则），整改后须复验此 grep-zero 期望不破。⚠️ **裸 scale 红线须区分「按压 scale」与「非按压强调 scale」**：按压一律 `scale(var(--press-scale))`；**非按压强调 scale（如 tab 选中放大高亮 `scale(1.05)`）须量化为独立 token（如 `--tab-active-scale`）并在 `frontend/src/uni.scss` 登记**，方不作为 grep-zero 违规——未登记的非按压 `scale(...)` 仍计入 grep-zero 违规。
  - **图标统一走 `IconSvg`**：所有功能 / 情感图标一律经 `<IconSvg name="…" />` 渲染 `frontend/src/assets/icons` 下 SVG，**禁止**手写 `<text>+</text>`、`content: '+'`、`✦` 等文本 / Unicode 字符当图标（与 §4.2 / §4.9 emoji 红线同源强化）。⚠️ **`IconSvg` 必须注册中性 `empty` 占位键，缺失/未注册键禁止静默回退到语义图标**：`IconSvg` 内部**不得**采用 `ICONS[name] || ICONS.dish` 这类「未命中键静默落到语义图标（如 `dish` 碗）」的回退写法——拼写错误 / 未注册键（如 `name="empty"`）会无声渲染成菜品碗，造成「空状态显示菜品碗」这类静默语义 bug。须注册专用 `empty` 中性占位键（不可见/中性占位 SVG），缺失键渲染该占位键而非语义图标；**`IconSvg` 现已在 dev 环境（`import.meta.env?.DEV`）对未知 `name` 触发 `console.warn`（仍暂回退 `dish` 以保渲染，但告警已落地）**，便于及时发现拼写/注册遗漏。⚠️ **审计须 diff 字符串字面量 icon 与 `ICONS` keys，防未注册键漏网**：凡以**字符串字面量**向 `SettingCell` / `CustomTabBar` / `ContributeSheet` / `AppButton` 等组件传入 `icon`/`name` 属性（而非动态键），审计时须与该组件实际读取的 `ICONS` 注册键做 diff，确认每个字面量均已注册；未注册键（如第八轮 `profile/index.vue:58` 的 `folder` 未注册、静默成碗）即便 dev `console.warn` 也不得放过，须登记整改——`console.warn` 仅辅助发现、不替代静态 diff 核查。⚠️ **中性占位必须为 `empty`（非 `dish`），且覆盖「IconSvg 回退目标」与「任何硬编码 ImageFallback / 破图占位」两处**：① `IconSvg` 的回退目标（含 dev 告警后的兜底落点）必须落在 `empty` 中性占位键，**不得**保留 `dish` 语义图标在中性占位语境的残留；② `ImageFallback.vue` 等全局图片裂图兜底组件的模板**硬编码**占位（如 `name="dish"`）一律改为 `name="empty"`——破图 / 空态语境禁止用语义图标（碗 `dish`）冒充中性占位（头像 / 档口 / 评价图加载失败全显示成碗属静默语义 bug，且该类硬编码不触发未注册告警，是第九轮新发现的全局兜底组件高危盲区）。⚠️ **审计须 grep 模板 `name="dish"` / `name="empty"` 逐文件核对中性语境**：凡模板出现 `name="empty"` 须确认确为中性占位语义；凡出现 `name="dish"` 须确认是「菜品 / 档口图语义」而非破图 / 空态占位冒充——两处（IconSvg 回退目标 + ImageFallback 等硬编码兜底）须同时落 `empty`，方算 IconSvg 红线收口。
  - **底部抽屉 / 弹窗规范**：`ReportModal` / `ContributeSheet` / `ApplySheet` / `FilterSheet` / share-sheet 等底部抽屉须含 `env(safe-area-inset-bottom)` 安全区避让，进出场缓动 `cubic-bezier(0.32,0.72,0,1) 0.3s`，并对 `prefers-reduced-motion: reduce` 交叉淡入降级（去弹性过冲）。
  - **`<swiper indicator-active-color>` / `<swiper indicator-color>` 裸 hex 为例外**：该原生属性（含激活态 `indicator-active-color` 与非激活态 `indicator-color`）不支持 `var()`，允许写裸 hex，但**须在 `frontend/src/uni.scss` 注释登记**（注明对应 token 名，便于全局改色时同步），不作为红线违规。
  - **图片添加统一用 `ImageUploader` / `IconSvg`**：新增图片入口一律走全局 `ImageUploader` 组件或 `IconSvg name="plus"` 触发，**禁止**在页面内联复制「+ 添加图片」逻辑 / 裸加号文本。以下两类为**已登记合法例外**（非违规，不强制替换）：① **单图头像上传**（`pages/profile/index.vue` 头像；`pages/pages-detail/review.vue` 评价单图）——单图场景；② **受 `canSubmit` 门控的「延迟上传」流程**（先存临时路径、提交时才逐个上传）——内联 `uni.chooseImage` 可避免破坏提交校验时序。完整多图流页面（`publish-dish` / `submit-stall`）仍须强制走 `ImageUploader`。
  - **分区标题复用 `SectionTitle`**：所有分区 / 区块标题一律渲染 `<SectionTitle title="…" />`；`CardSection` 内部**不另起**一套标题语言（不得手写 `.section-head`+`.section-title` 竖条 / 纯文字标题模拟 accent 条），表单内字段级 label 属字段语义允许纯 text。
  - **颜色全走语义 token（禁裸 hex）**：所有颜色（含限时 / 促销 / 热门等标签底色、`IconSvg` 的 `color` 属性、文字色、边框色、背景色）必须引用语义 token（如 `var(--color-hot)` / `var(--color-promo)` / `var(--color-primary)` 等），**禁止**在模板 / 组件样式中写裸 hex（如 `#FF6B6B` / `#FFB400`）。原生 API 不接受 `var()` 的颜色例外（如 `<swiper indicator-active-color>`、`uni.showModal` 的 `confirmColor` 等）**必须集中在 `frontend/src/uni.scss` 注释登记**（注明对应 token 名与用途，便于全局改色时同步）；且该常量须路由经过注册常量（如 swiper 指示点色统一经 `SWIPER_INDICATOR_ACTIVE_COLOR` 引用），**禁止在页面内联写裸 hex**——即裸 hex 只能出现在 `uni.scss` 的登记处，业务代码一律引用注册常量，登记后方不作为红线违规。
  - **底部 Sheet 统一下拉关闭手势 + reduced-motion 降级**：所有 bottom-sheet（`ApplySheet` / `ContributeSheet` / `NicknameSheet` / `FilterSheet` / `RelatedPickerSheet` 等）必须统一支持下拉关闭手势——仅向下拖拽、阈值约 `120px`、松手超过阈值 `emit('close')` 否则回弹；并须对 `prefers-reduced-motion: reduce` 做降级（去弹性过冲、交叉淡入）。**禁止**个别 sheet 仅支持 mask 点击关闭、缺失下拉手势或降级（与 §4.4 Sheet 弹簧 + 手势中断同源强化）。
  - **审计时先查本段已登记例外清单，确认未登记才计违规**：上述各红线中凡标注「已登记合法例外」「为例外」「登记后不作为红线违规」之处，须以本段（§4.9 UI 全量审计红线）逐条登记的例外为准；审计 / 复验时发现疑似违规，**先查本段已登记例外清单，确认确未登记才计为违规**。未在本段登记的裸 hex / 内联逻辑等一律按红线违规处理。
- **Web 管理端 UI 细则 / 页面模板（三栏布局、T1/T2/T3 模板、统一组件 `PageContainer/PageSection/...`、视觉刷新）见 `tasks/task-12-miniapp-web-scope.md`，由 web-dev 落地；本文件仅定基础 token 与红线。**

## 5. 开发约束
- **平台定位**：仅美食信息公示与点评，**不涉及下单 / 支付 / 外卖**；无售罄 / 库存概念；浏览对游客开放；列表默认按热度降序，可切评分 / 价格，长列表无限滚动，无结果显空状态。
- 命名：Java PascalCase、字段 / 方法 camelCase；DB snake_case（MP 自动驼峰）；前端 TS camelCase，Web 经 `api/adapter.ts` 转换，禁止 View 层直接处理字段名。
- 所有 API 响应含 `code/message/data`；前端 `http.ts` 判定 `code!==200` 抛异常，页面 try-catch，Store fetch 失败置空数组不向上抛。
- Controller 入参 DTO + `@Validated`；Service 写操作 `@Transactional`；评分 / 点赞计数走 Spring 事件异步维护，禁止主流程内联重算。
- 内容审核流：学生提交 `audit_status=pending` → 管理员 `approved/rejected`（退回必填 `reject_reason` 并回显）；小程序仅展示 `approved` 且上架 / 营业中；评价 `is_hidden` 控制可见性；Web「菜品审核」「评价审核」为独立模块。学生编辑重提**复用原记录**、`reject_reason` 清空。下架 / 变更申请落独立 `apply` 表（见 §0.3）。
- 注册仅限 `@bjtu.edu.cn` + 验证码；评价一人一菜一条（`uk_review_user_dish`）、点赞一人一票（`uk_useful_user_review`），业务代码须与唯一键一致。
- 小程序请求超时 8s、管理端 5s；API 基地址集中 `api/config.ts` 的 `API_BASE_URL`，禁止硬编码 URL。

### 5.z 已拍板架构决策（强制）
- **D-A** 通知异步写 `notification` 用 `@Async` + 有界线程池，不引 MQ。
- **D-B** `view_log` 加唯一键 `uk_view_user_target`，`record()` 改 upsert。
- **D-C** 报表导出返回 CSV 文件流，不引 Apache POI。
- **D-D** 推荐 / 热门 / 广场用 Caffeine 短 TTL 缓存(60s) + 写失效；`recommendDishes()` 改 SQL 分页。
- **D-E** schema 漂移治理：启动时 fail-fast 校验或 CI 步骤。
- **Q1** 不建成就 / 等级 / 成长体系（无 `achievement`/`user_achievement`）。
- **Q2** 不置顶 / 话题 / 精选运营干预，社区排序不干预。
- **Q4** 必须交付：①`DELETE /my/account` 账号注销 + 级联清理 ②社区举报复用 `user_feedback`(`related_type`/`related_id`)，不新建举报表 ③删除本人记录（动态 / 菜品 / 评价）④关联动态双向跳转。
- **Q5** 不碰关注 / 粉丝流，不建用户关系表。

### 5.x 三端一致性红线（强制，违反即阻断级缺陷）
- **字段命名**：对外 JSON 一律 camelCase；`favoriteCount` 为收藏量唯一字段名（禁止 `collectCount`）；`targetType`(枚举) 为 Banner 跳转唯一字段名；评价状态 `isHidden`(0/1) 非 `isDeleted`；Web `snake_case` 仅允许 `api/adapter.ts` 内部，禁止进入 `types/` 或视图层。
- **错误码统一**：成功 200 / 参数 400 / 未登录 401 / 无权限 403 / 服务器 500；**401 统一处理**：小程序已 `uni.$emit('auth:unauthorized')` + 清 token + Toast；**web `http.ts` 须补齐 401 拦截**（清 `localStorage.token` + 跳转登录）。
- **喜欢 / 收藏单一概念**：禁止 `like`/`favorite` 双体系、禁止 `like_count`、禁止 `/likes`；原 `/favorites` 端点与表已彻底删除，对外统一 `favorite` 语义 + `DishVO.favoriteCount`。
- **状态枚举**：Banner `status` enabled/disabled、`targetType` DISH/URL/NONE；Dish `status` on/off；Canteen/Stall `status` open/closed；Web 内部 `active/inactive` 须经 adapter 映射回后端枚举。
- **User 无 stall**：`UserVO` 不含 `stallId`；web `userToLegacy` 的 `stall_id` 映射须删除。
- **学生 UGC 路径**：提交档口 / 食堂仅 `POST /my/stalls`（STUDENT），发布菜品仅 `POST /dishes` 系列；严禁 `/stall-owner/**`。
- **分页结构**：列表接口统一 `PageResult<T>{ records, total, page, pageSize }`；单页非分页返回 `List<T>`。
- **整改影响面清单（谁改什么）见 `tasks/CONTRACT_IMPACT.md`，本文件不再重复。**

## 6. 协作纪律
- 本文件为**唯一权威基础规范**；功能 / 接口 / 页面设计细节以 `tasks/` 为准；多 agent 协作流程与交接物见 `docs/WORKFLOW.md`。
- **仅技术负责人可修改本文件**；其余角色（后端 / 小程序 / Web / 质量把控工程师）发现与 `tasks/` 或代码冲突时，须提技术负责人裁定，不得自行绕过或改本文件。
- 踩坑经验回流：实测证伪的方案（如 §4.9 组件渲染红线）由技术负责人提炼进本文件红线。
