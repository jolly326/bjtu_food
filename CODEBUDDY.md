# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

「知行食记 / 食在交大」—— 校园美食发现与分享圈（微信小程序 + Spring Boot 后端 + Web 管理后台）。

## 权威文档层级（必读顺序）
1. **`docs/project_spec.md`** —— 最高权威基线（不可违背的红线、跨端边界、命名/错误码/状态机、UI 视觉规范 §4）。**仅技术负责人可修改**；发现冲突须提技术负责人，不得自行绕过或改它。
2. **本文件（`CODEBUDDY.md`）与 `.codebuddy/agents/*.md`** —— 多 Agent 协作流程、角色权限与交接（原 `docs/WORKFLOW.md` 已删除，勿再引用）。
3. 其余 `docs/`（仅此三份）：`architecture.md`(部署/状态管理)、`database.md`(14 表 ER)、`api-design.md`(接口契约/错误码)。原 `ui-design.md`/`testing.md`/`web-ui.md` 已删除——视觉规范唯一权威为 spec §4，勿再引用。
4. **冲突裁决**：一切以 `project_spec.md` 为准，README/架构文档若与其不符以 spec 为准。
5. 多 Agent 模式角色权限在 `.codebuddy/agents/*.md`；本文档面向单实例 CodeBuddy，改动代码前先通读 `project_spec.md`。

## 常用命令
### 后端（server/，Spring Boot 3.2 + Java 21 + Maven + MyBatis-Plus）
- 启动：在 `server/` 执行 `cp .env.example .env` 填变量后 `mvn spring-boot:run`；服务 `http://localhost:8080/api`，文档 `/api/swagger-ui/index.html`。
- 打包：在 `server/` 执行 `mvn clean package`（产出可执行 jar，跳过测试加 `-DskipTests`）。
- 建库建表（唯一权威脚本）：`mysql -u root -p < server/src/main/resources/db/schema.sql`，可选灌数 `mysql -u root -p bjtu_food < server/src/main/resources/db/seed_data.sql`（脚本自包含建库选库）。

### 小程序（client/，uni-app + Vue3 + TS + Pinia）
- 装依赖：在 `client/` 执行 `npm install`。
- 开发（微信）：`npm run dev:mp-weixin`，产物导入微信开发者工具 `client/dist/dev/mp-weixin`。
- 构建：在 `client/` 执行 `npm run build:mp-weixin`；H5 预览 `npm run dev:h5`。
- 类型检查：在 `client/` 执行 `npm run type-check`（即 `vue-tsc --noEmit`），提交前必过。

### 管理后台（web/，Vue3 + Vite + Element Plus）
- 装依赖：在 `web/` 执行 `npm install`（或 README 的 `pnpm install`）。
- 开发：`npm run dev`（`http://localhost:5173`，需 ADMIN 登录）。
- 构建 + 类型检查：`npm run build`（含 `vue-tsc --build`）。
- 校验：`npm run lint`（oxlint + eslint 自动修复）；格式化 `npm run format`（prettier）。

### 测试说明
后端 `mvn test` 仅含 `BjtuFoodApplicationTests` 冒烟用例（无业务单测）；前端无单测脚本，质量靠类型检查 + lint + 真机/模拟器验证（由用户在微信开发者工具完成，agent 不执行真机验证）。

## 高层架构
### 三端定位与数据链路（spec §0.4，强制）
- **小程序 `client/` = 用户端**：业务数据唯一产生源（浏览、菜品贡献、菜品评价、产品反馈）。**社区板块（原动态信息流）已于 2026-09-12 下线删除**。
- **后端 `server/` = 数据服务**：唯一存储与业务规则；小程序与 Web **共用同一套 API 契约**（`/` 用户接口供小程序，`/admin/**` 供 Web）。
- **Web `web/` = 辅助管理工具（非用户端）**：只经 `/admin/**` 读取/管理后端数据（CRUD、UGC 审核、看板、操作日志），不产生业务数据。
- 数据流向：小程序产生数据 → MySQL → Web 经 `/admin/**` 管理 → 小程序即时反映。Web 新增能力必须以小程序已有数据对象为前提（**活动模块除外**：后台录入、小程序消费、web-view 跳公众号文章）。

### 后端分层（包结构 `com.bjtufood.*`）
每业务模块（auth/dish/review/canteen/content/activity/feedback/notify/history/upload/common）严格四层 **controller / service(+impl) / mapper / entity / dto**；**禁止跨层调用**（Controller 不得直调 Mapper）。ORM 用 MyBatis-Plus（`BaseMapper` + `resources/mapper/*.xml`）。API 文档 SpringDoc OpenAPI（非 Knife4j）。统一响应由 `GlobalExceptionHandler` 包装，Controller 不得裸抛。写操作 Service 加 `@Transactional`；评分/点赞计数走 Spring 事件异步维护（`@Async` AFTER_COMMIT），禁止主流程内联重算。

### 认证与鉴权（spec §5.y，强制）
- **废除账号密码/注册**：小程序无登录页/登录按钮/密码体系；微信打开即 `POST /auth/wechat-login`（`code2Session`）静默建号 → **游客态 `verified=false`**（默认已登录）。
- **`verified` 门槛**：UGC 写操作（发菜品/写评价/点赞）改鉴 `verified=true`（邮箱验证码认证 `@bjtu.edu.cn`）；`verified` **不进 JWT**（JWT claims 实况含 `userId`/`role`/`username` 三项，不含 `verified`；后端以 userId 实时查 `user.verified` 判定）。游客入口不置灰，点击弹 `AuthSheet` 认证引导。
- `verified` 缺失异常码 **`4031`**（与 `403` 普通无权限分流）；前端 `http.ts` 据此分别提示。
- **管理后台登录例外（方案 C）**：`/auth/admin/login` 管理员账号密码 + BCrypt + JWT，与小程序微信体系解耦；`/admin/**` 仍仅 `ADMIN`/`SUPER_ADMIN`。
- 角色**仅 `STUDENT`/`ADMIN`**，禁止 `STALL_OWNER` 或 `/stall-owner/**`。

### API 与数据契约（spec §3 / §5.x，强制）
- 统一响应 `{ code, message, data }`，成功 `code=200`；错误码仅 `200/400/401/403/4031/500`，**禁止自定义非标码**（如 1001）。
- 对外 JSON 一律 camelCase；跳转类字段 `targetType/targetId/targetUrl`；分页 `PageResult<T>{ records,total,page,pageSize }`（MP 分页），非分页返回 `List<T>`。
- **金额一律「分」(int/Long)**：分↔元转换只能在 `utils/money`（`fenToYuan/yuanToFen`），**禁止页面/组件裸算**。
- 数据隔离：从 `SecurityUtil.getCurrentUserId()` 取用户，禁止信任前端 userId；UGC `created_by=当前用户`。
- 状态枚举：Dish `status` on/off；Canteen/Stall open/closed；Activity enabled/disabled。评价可见性 `isHidden`(0/1) 非 `isDeleted`。

### 数据库（14 张表，唯一权威 `server/src/main/resources/db/schema.sql`）
- 表（14 张）：user / email_verification_code / canteen / stall / dish / category / review / review_useful / activity / notification / user_feedback / view_log / operation_log / broadcast（兼容保留表，运营广播方案已废弃；`apply_action` 表已于 2026-09-12 随「贡献链路下线」删除）。
- **工作区红线（必遵）**：涉及后端数据库修改**绝不能直连数据库 ALTER**，必须改初始化/种子脚本 `server/src/main/resources/db/`（schema.sql 与 seed_data.sql），保持脚本自包含、可重跑。
- UGC 审核：提交 `audit_status=pending` → 后台 `approved/rejected`（退回必填 `reject_reason` 并回显）；学生编辑**复用原记录**、`reject_reason` 清空；下架/纠错类需求走反馈 `error` 类型（关联菜品），不再有独立 `apply` 表。

### 前端架构要点
- **小程序 `client/src`**：`api/`(含 `http.ts`、`shared.ts`)、`types/`、`stores/`(Pinia: user/dish/theme/location/notify/review)、`pages/`(主包 home/mine/find + 分包 detail/me/activity)、`components/`、`theme/tokens.ts`、`uni.scss`、`assets/icons`。
  - `http.ts`：401 先静默登录重试一次，仍失败 `handleUnauthorized`（清 token+Toast+重登，并发去重），**不用事件总线**；403/4031 分级提示。
  - 图片：小程序走微信云存储 `cloud://`（仅头像/菜品图）；UGC 评价/反馈全量纯文本、无图片上传（`ImageUploader` 已下线）；图标统一 `<IconSvg>`（本地 `assets/icons`，语义唯一 ic-heart=喜欢、ic-thumb=有用/点赞、无收藏）。
- **Web `web/src`**：`api/`(含 `adapter.ts` 做 snake_case→camelCase 映射，**禁止视图层直处字段名**)、`views/`、`components/`、`router/`、`api/dashboard.ts`；登录首屏 `/dashboard`（工作台=待办+数据总览，**非 ECharts 看板**）。
- 两前端**无共享代码**，各自独立 `api/` 层；字段命名约定靠 `project_spec.md` §5.x 对齐。

### UI 实现红线（spec §4.9，BLOCKER 级，改 client 必查）
- 小程序可点元素事件统一 **`@tap`**（禁 `@click`）。
- 按压反馈统一走 `background: var(--color-bg-soft)`（或 `opacity` 微降），**废止 `transform: scale` 按压**（mp-weixin 下 scale 按压易致卡片边缘溢出/裁剪，`client/` 统一 bg-soft 按压语言；适用范围 = 小程序端，`web/` 的 `scale(var(--press-scale))` 为登记豁免）；**grep 限 `client/` 应 0 处裸 `scale(...)`**（非按压强调须量化独立 token 并登记 `uni.scss`）。原小程序侧 `--press-scale` token 作废。
- 颜色全走语义 token（`var(--color-*)`），**禁裸 hex**（原生 API 不接受 `var()` 的常量须集中在 `uni.scss` 注释登记）。
- 图标统一走 `IconSvg`，禁 emoji/文本/`content:'+'` 当图标；`IconSvg` 须有中性 `empty` 占位键，缺失键渲染 `empty` 而非语义图标（`ImageFallback` 等破图兜底同样改 `name="empty"`）。
- 含固定底栏页面 `.scroll-wrap` 必须 `padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom))`，禁内容被遮挡。
- 瀑布流 `WaterfallList` 内部直渲染 `DishCard`，**禁向子组件具名 slot 分发**（uni-app 编译 mp-weixin 后同名 slot 塌缩成空白，阻断级 bug）。
- 底部 Sheet 统一下拉关闭手势（阈值 ~120px）+ `prefers-reduced-motion` 降级；分区标题复用 `SectionTitle`。

### 已拍板关键决策（避免回退）
- **权威口径（2026-09-12 校正）**：`docs/project_spec.md` 为唯一权威；**代码只在 UI 实现层提供指导，不得据代码反向推翻文档**（文档已同步的部分，冲突时改代码不改文档）。开发交付以「静态错误清零」为准，**编译 / 构建 / 真机运行由用户执行**。
- **产品聚焦四条主线（2026-09-12 拍板，最高优先级）**：① 菜品信息展示；② 搜索与查找（`find` 二级页）；③ 用户 UGC —— **评价类**（菜品评价，唯一评价形态）；④ 用户 UGC —— **反馈 / 贡献类**（意见反馈 + 举报 / 纠错 / 申请下架 / 推荐 / 新增菜品 + 菜品贡献）。**不属于这四条的一律不投入**；恢复已下线能力须重新拍板。
- **UGC 全谱系 = 评价 + 反馈 / 贡献**（③ + ④），是菜品信息迭代与程序优化的输入源；**反馈类 UGC 与评价同等重要，不得弱化**——它是实时发现菜品信息错误与程序问题、驱动信息更新与优化的主通道。本次下线的是「社区 / 动态」社交广场形态，**不是下线 UGC**。
- **社区板块（原动态信息流）已下线并全量删除干净（2026-09-12）**：小程序四个页面与发布流程分包、对应 api / types / 组件 / composable、后端模块与两端接口、Web 管理页、相关库表**全部删除，代码与文档均不留残留字样**；恢复靠 git 历史。评价不再「同步到社区」。
- TabBar **固定 home / mine 两页**；「我的」页宫格 **2×2**（最新活动 / 意见反馈 / 系统通知 / 我的评价），「我发布的」已删。
- UGC 唯一发布路径 = 菜品详情底栏「写评价」；无独立发布页、无「关联对象」表单。
- 收藏功能全量移除（无入口/字段/图标）；喜欢语义仅 `ic-heart`。
- 食堂/档口降级为菜品属性（`dish.canteen`/`dish.stall`），无独立路由。
- 搜索为二级页 `find`（非 tab），属核心板块，不得降级或移除。
- 活动为独立模块（后台录入、列表页、`web-view` 跳公众号文章；`web-view` 仅活动用）；**当前 `FEATURE_GATES.activity=false` 暂缓开放，非核心板块**。
- 认证走 `AuthSheet` 弹层（无独立认证页）；反馈页 `POST /feedback` 公开、不收集联系方式、匿名提交。
- 通知异步写（`@Async`+有界线程池，不引 MQ）；推荐/热门用 Caffeine 60s TTL + 写失效；报表导出已移除。
- **贡献链路下线（2026-09-12）**：`apply_action` 表与 `/my/apply`、`/admin/apply*`、`/my/submissions` 接口全量删除；贡献类（新增菜品 / 推荐 / 纠错 / 申请下架）统一走反馈 `add`/`error` 类型，由管理员在 Web 后台据反馈手工录入/修正菜品闭环，无独立申请链路。
- **UGC 图片全量下线（2026-09-12）**：评价 `images` 与评价图展示、反馈 `images` 与三表单上传入口全部移除；`ImageUploader` 因无消费方删除；全项目 UGC 仅剩纯文本，图片上传仅保留头像与后台录菜品。
- **我的评价（2026-09-12）**：我的页宫格 1×3 → 2×2，新增「我的评价」格（`requireAuth`）与 `pages/me/my-reviews` 列表页（本人删除 + 空态双口径），复用 `DELETE /reviews/{id}`。
- **反馈回执（2026-09-12）**：反馈/举报提交成功给处理预期文案（游客明确「无法单独通知你」）；管理员标记处理后，已认证提交人于系统通知收到 `feedback_handle` 回执（游客不投递不阻塞）。
- **按压语言改用 bg-soft（2026-09-12 拍板；适用范围 = 小程序端）**：废止 `scale(0.97)` 按压（小程序侧 `--press-scale` token 作废）；mp-weixin 下 `transform: scale` 按压易致卡片边缘溢出/裁剪，`client/` 统一 `background: var(--color-bg-soft)`/`opacity` 作按压反馈；`grep` 范围限 `client/` 应 0 处裸 `scale(...)`。**Web 端登记豁免**：DOM 端无 mp-weixin 溢出问题，`web/` 的 `scale(var(--press-scale))` 维持使用，不受本条约束。

## 改动前注意
- 任何 spec 冲突先问技术负责人，**不要改 `project_spec.md`**（除非你就是技术负责人角色）。
- 后端加表/改字段 → 改 `server/src/main/resources/db/schema.sql`（及 seed），不要直连库。
- 新增接口先确认 `api-design.md` 契约与错误码，复用统一响应与分页结构。
- 小程序改动后用 `npm run type-check` 校验；改样式/组件须过 §4.9 UI 红线 grep 自检（裸 scale / 裸 hex / `@click`）。
