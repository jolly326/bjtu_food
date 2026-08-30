# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

「知行食记 / 食在交大」—— 校园美食发现与分享社区（微信小程序 + Spring Boot 后端 + Web 管理后台）。

## 权威文档层级（必读顺序）
1. **`docs/project_spec.md`** —— 最高权威基线（不可违背的红线、跨端边界、命名/错误码/状态机）。**仅技术负责人可修改**；发现冲突须提技术负责人，不得自行绕过或改它。
2. **`docs/WORKFLOW.md`** —— 多 Agent 协作流程与角色交接（需求→定样→拆 task→开发→质量门禁→经验回流）。
3. 其余 `docs/`：`architecture.md`(部署/状态管理)、`database.md`(18 表 ER)、`api-design.md`(接口契约/错误码)、`ui-design.md`(视觉规范)、`testing.md`、`web-ui.md`。
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
- **小程序 `client/` = 用户端**：业务数据唯一产生源（浏览、UGC 提交、评价、动态、反馈）。
- **后端 `server/` = 数据服务**：唯一存储与业务规则；小程序与 Web **共用同一套 API 契约**（`/` 用户接口供小程序，`/admin/**` 供 Web）。
- **Web `web/` = 辅助管理工具（非用户端）**：只经 `/admin/**` 读取/管理后端数据（CRUD、UGC 审核、看板、操作日志），不产生业务数据。
- 数据流向：小程序产生数据 → MySQL → Web 经 `/admin/**` 管理 → 小程序即时反映。Web 新增能力必须以小程序已有数据对象为前提（**活动模块除外**：后台录入、小程序消费、web-view 跳公众号文章）。

### 后端分层（包结构 `com.bjtufood.*`）
每业务模块（auth/dish/review/moment/canteen/content/activity/apply/feedback/notify/history/upload/common）严格四层 **controller / service(+impl) / mapper / entity / dto**；**禁止跨层调用**（Controller 不得直调 Mapper）。ORM 用 MyBatis-Plus（`BaseMapper` + `resources/mapper/*.xml`）。API 文档 SpringDoc OpenAPI（非 Knife4j）。统一响应由 `GlobalExceptionHandler` 包装，Controller 不得裸抛。写操作 Service 加 `@Transactional`；评分/点赞计数走 Spring 事件异步维护（`@Async` AFTER_COMMIT），禁止主流程内联重算。

### 认证与鉴权（spec §5.y，强制）
- **废除账号密码/注册**：小程序无登录页/登录按钮/密码体系；微信打开即 `POST /auth/wechat-login`（`code2Session`）静默建号 → **游客态 `verified=false`**（默认已登录）。
- **`verified` 门槛**：社区写操作（发菜品/评价/动态/评论/点赞）改鉴 `verified=true`（邮箱验证码认证 `@bjtu.edu.cn`）；`verified` **不进 JWT**（JWT 仅含 userId），后端按 `user.verified` 实时判定。游客入口不置灰，点击弹 `AuthSheet` 认证引导。
- `verified` 缺失异常码 **`4031`**（与 `403` 普通无权限分流）；前端 `http.ts` 据此分别提示。
- **管理后台登录例外（方案 C）**：`/auth/admin/login` 管理员账号密码 + BCrypt + JWT，与小程序微信体系解耦；`/admin/**` 仍仅 `ADMIN`/`SUPER_ADMIN`。
- 角色**仅 `STUDENT`/`ADMIN`**，禁止 `STALL_OWNER` 或 `/stall-owner/**`。

### API 与数据契约（spec §3 / §5.x，强制）
- 统一响应 `{ code, message, data }`，成功 `code=200`；错误码仅 `200/400/401/403/4031/500`，**禁止自定义非标码**（如 1001）。
- 对外 JSON 一律 camelCase；跳转类字段 `targetType/targetId/targetUrl`；分页 `PageResult<T>{ records,total,page,pageSize }`（MP 分页），非分页返回 `List<T>`。
- **金额一律「分」(int/Long)**：分↔元转换只能在 `utils/money`（`fenToYuan/yuanToFen`），**禁止页面/组件裸算**。
- 数据隔离：从 `SecurityUtil.getCurrentUserId()` 取用户，禁止信任前端 userId；UGC `created_by=当前用户`。
- 状态枚举：Dish `status` on/off；Canteen/Stall open/closed；Activity enabled/disabled。评价可见性 `isHidden`(0/1) 非 `isDeleted`。

### 数据库（18 张表，唯一权威 `server/src/main/resources/db/schema.sql`）
- 表：user / email_verification_code / canteen / stall / dish / category / review / review_useful / moment / moment_comment / moment_useful / activity / notification / user_feedback / apply_action / view_log / operation_log（broadcast 表保留但运营广播方案已废弃，首页广播由社区动态驱动）。
- **工作区红线（必遵）**：涉及后端数据库修改**绝不能直连数据库 ALTER**，必须改初始化/种子脚本 `server/src/main/resources/db/`（schema.sql 与 seed_data.sql），保持脚本自包含、可重跑。
- UGC 审核：提交 `audit_status=pending` → 后台 `approved/rejected`（退回必填 `reject_reason` 并回显）；学生编辑**复用原记录**、`reject_reason` 清空；下架/变更申请落独立 `apply` 表。

### 前端架构要点
- **小程序 `client/src`**：`api/`(含 `http.ts`、`shared.ts`)、`types/`、`stores/`(Pinia: user/dish/theme/location/notify/review/moment)、`pages/`(主包 home/community/profile/find + 分包 detail/user/standalone)、`components/`、`theme/tokens.ts`、`uni.scss`、`assets/icons`。
  - `http.ts`：401 先静默登录重试一次，仍失败 `handleUnauthorized`（清 token+Toast+重登，并发去重），**不用事件总线**；403/4031 分级提示。
  - 图片：小程序走微信云存储 `cloud://`；上传统一 `ImageUploader`；图标统一 `<IconSvg>`（本地 `assets/icons`，语义唯一 ic-heart=喜欢、ic-thumb=有用/点赞、无收藏）。
- **Web `web/src`**：`api/`(含 `adapter.ts` 做 snake_case→camelCase 映射，**禁止视图层直处字段名**)、`views/`、`components/`、`router/`、`api/dashboard.ts`；登录首屏 `/dashboard`（工作台=待办+数据总览，**非 ECharts 看板**）。
- 两前端**无共享代码**，各自独立 `api/` 层；字段命名约定靠 `project_spec.md` §5.x 对齐。

### UI 实现红线（spec §4.9，BLOCKER 级，改 client 必查）
- 小程序可点元素事件统一 **`@tap`**（禁 `@click`）。
- 按压缩放统一 `transform: scale(var(--press-scale))`（`--press-scale:0.97`），**grep 全仓应 0 处裸 `scale(...)`**（非按压强调须量化独立 token 并登记 `uni.scss`）。
- 颜色全走语义 token（`var(--color-*)`），**禁裸 hex**（原生 API 不接受 `var()` 的常量须集中在 `uni.scss` 注释登记）。
- 图标统一走 `IconSvg`，禁 emoji/文本/`content:'+'` 当图标；`IconSvg` 须有中性 `empty` 占位键，缺失键渲染 `empty` 而非语义图标（`ImageFallback` 等破图兜底同样改 `name="empty"`）。
- 含固定底栏页面 `.scroll-wrap` 必须 `padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom))`，禁内容被遮挡。
- 瀑布流 `WaterfallList` 内部直渲染 `DishCard`，**禁向子组件具名 slot 分发**（uni-app 编译 mp-weixin 后同名 slot 塌缩成空白，阻断级 bug）。
- 底部 Sheet 统一下拉关闭手势（阈值 ~120px）+ `prefers-reduced-motion` 降级；分区标题复用 `SectionTitle`。

### 已拍板关键决策（避免回退）
- 收藏功能全量移除（无入口/字段/图标）；喜欢语义仅 `ic-heart`。
- 食堂/档口降级为菜品属性（`dish.canteen`/`dish.stall`），无独立路由。
- 搜索为二级页 `find`（非 tab）；TabBar 固定 home/community/profile 三页。
- 活动为独立模块（后台录入、列表页、`web-view` 跳公众号文章；`web-view` 仅活动用）。
- 认证走 `AuthSheet` 弹层（无独立认证页）；反馈页 `POST /feedback` 公开、不收集联系方式、匿名提交。
- 通知异步写（`@Async`+有界线程池，不引 MQ）；推荐/热门用 Caffeine 60s TTL + 写失效；报表导出已移除。

## 改动前注意
- 任何 spec 冲突先问技术负责人，**不要改 `project_spec.md`**（除非你就是技术负责人角色）。
- 后端加表/改字段 → 改 `server/src/main/resources/db/schema.sql`（及 seed），不要直连库。
- 新增接口先确认 `api-design.md` 契约与错误码，复用统一响应与分页结构。
- 小程序改动后用 `npm run type-check` 校验；改样式/组件须过 §4.9 UI 红线 grep 自检（裸 scale / 裸 hex / `@click`）。
