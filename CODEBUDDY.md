# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

「知行食记 / 食在交大」—— 校园美食发现与分享圈（微信小程序 + Spring Boot 后端 + Web 管理后台）。

## 权威文档层级（必读顺序）
1. **`docs/project_spec.md`** —— 最高权威基线（不可违背的红线、跨端边界、命名/错误码/状态机、UI 视觉规范 §4）。**仅技术负责人可修改**；发现冲突须提技术负责人，不得自行绕过或改它。
2. **本文件（`CODEBUDDY.md`）与 `.codebuddy/agents/*.md`** —— 多 Agent 协作流程、角色权限与交接（原 `docs/WORKFLOW.md` 已删除，勿再引用）。
3. 其余 `docs/`（仅此三份）：`architecture.md`(部署/状态管理)、`database.md`(12 表 ER)、`api-design.md`(接口契约/错误码)。原 `ui-design.md`/`testing.md`/`web-ui.md` 已删除——视觉规范唯一权威为 spec §4，勿再引用。
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
- 开发：`npm run dev`（`http://localhost:5173`；管理端无登录体系，接口经 `X-Admin-Token` 口令把关）。
- 构建 + 类型检查：`npm run build`（含 `vue-tsc --build`）。
- 校验：`npm run lint`（oxlint + eslint 自动修复）；格式化 `npm run format`（prettier）。

### 测试说明
后端 `mvn test` 含 4 个用例：`BjtuFoodApplicationTests`（contextLoads，需数据库）、`WechatServiceTest`、`ContentSecurityServiceTest`、`SmokeApiTest`（MockMvc 六链路接口冒烟，不依赖数据库，可用 `mvn -q -Dtest=SmokeApiTest test` 离线运行）；前端无单测脚本，质量靠类型检查 + lint + 真机/模拟器验证（由用户在微信开发者工具完成，agent 不执行真机验证）。

## 高层架构
### 三端定位与数据链路（spec §0.4，强制）
- **小程序 `client/` = 用户端**：业务数据唯一产生源（浏览、菜品评价、产品反馈——含新增菜品/纠错等反馈诉求；学生端无菜品写接口，菜品由管理员录入）。**社区板块（原动态信息流）已于 2026-09-12 下线删除**。
- **后端 `server/` = 数据服务**：唯一存储与业务规则；小程序与 Web **共用同一套 API 契约**（`/` 用户接口供小程序，`/admin/**` 供 Web）。
- **Web `web/` = 辅助管理工具（非用户端）**：只经 `/admin/**` 读取/管理后端数据（CRUD、UGC 审核、看板、操作日志），不产生业务数据。
- 数据流向：小程序产生数据 → MySQL → Web 经 `/admin/**` 管理 → 小程序即时反映。Web 新增能力必须以小程序已有数据对象为前提（原「活动模块」例外已随 2026-09-13 活动全链路下线作废）。

### 后端分层（包结构 `com.bjtufood.*`）
每业务模块（auth/dish/review/canteen/content/feedback/notify/history/upload/common）严格四层 **controller / service(+impl) / mapper / entity / dto**；**禁止跨层调用**（Controller 不得直调 Mapper）。ORM 用 MyBatis-Plus（`BaseMapper` + `resources/mapper/*.xml`）。API 文档 SpringDoc OpenAPI（非 Knife4j）。统一响应由 `GlobalExceptionHandler` 包装，Controller 不得裸抛。写操作 Service 加 `@Transactional`；评分/点赞计数走 Spring 事件异步维护（`@Async` AFTER_COMMIT），禁止主流程内联重算。

### 认证与鉴权（spec §5.y，强制）
- **废除账号密码/注册**：小程序无登录页/登录按钮/密码体系；微信打开即 `POST /auth/wechat-login`（`code2Session`）静默建号 → **游客态 `verified=false`**（默认已登录）。
- **`verified` 门槛**：UGC 写操作（写评价/评价点赞/删本人评价，即评价类 UGC）改鉴 `verified=true`（邮箱验证码认证 `@bjtu.edu.cn`）；**学生端无菜品写接口**（`POST`/`PUT`/`DELETE /dishes` 已于 2026-09-13 全部下线，菜品由管理员录入）；`verified` **不进 JWT**（JWT claims 实况含 `userId`/`role`/`username` 三项，不含 `verified`；后端以 userId 实时查 `user.verified` 判定）。游客入口不置灰，点击弹 `AuthSheet` 认证引导。
- `verified` 缺失异常码 **`4031`**（与 `403` 普通无权限分流）；前端 `http.ts` 据此分别提示。
- **管理后台登录例外（方案 C）**：`/auth/admin/login` 管理员账号密码 + BCrypt + JWT，与小程序微信体系解耦；`/admin/**` 仍仅 `ADMIN`/`SUPER_ADMIN`。
- 角色**仅 `STUDENT`/`ADMIN`**，禁止 `STALL_OWNER` 或 `/stall-owner/**`。

### API 与数据契约（spec §3 / §5.x，强制）
- 统一响应 `{ code, message, data }`，成功 `code=200`；错误码仅 `200/400/401/403/4031/500`，**禁止自定义非标码**（如 1001）。
- 对外 JSON 一律 camelCase；跳转类字段 `targetType/targetId/targetUrl`；分页 `PageResult<T>{ records,total,page,pageSize }`（MP 分页），非分页返回 `List<T>`。
- **金额一律「分」(int/Long)**：分↔元转换只能在 `utils/money`（`fenToYuan/yuanToFen`），**禁止页面/组件裸算**。
- 数据隔离：从 `SecurityUtil.getCurrentUserId()` 取用户，禁止信任前端 userId；UGC `created_by=当前用户`。
- 状态枚举：Dish `status` on/off；Canteen/Stall open/closed。评价可见性 `isHidden`(0/1) 非 `isDeleted`（原 Activity/Broadcast `enabled/disabled` 枚举已随下线移除）。

### 数据库（11 张表，唯一权威 `server/src/main/resources/db/schema.sql`）
- 表（11 张）：user / email_verification_code / canteen / stall / dish / review / review_useful / notification / user_feedback / view_log / operation_log（`broadcast`/`activity` 两表已于 2026-09-13 随活动/公告全链路下线删除；`category` 表已于 2026-09-15 随品类维度整链删除（端上零呈现、仅 Web 自用的不可见第三维度），基线 14→12→11；`apply_action` 表已于 2026-09-12 随「贡献链路下线」删除）。
- **工作区红线（必遵）**：涉及后端数据库修改**绝不能直连数据库 ALTER**，必须改初始化/种子脚本 `server/src/main/resources/db/`（schema.sql 与 seed_data.sql），保持脚本自包含、可重跑。
- 菜品无独立审核：`dish.audit_status` 退役列已于 2026-09-15 全量删除（含索引与常量），公开查询仅按 `status='on'` 过滤；菜品由管理员录入（`/admin/dishes`）即生效。**学生端无菜品写接口**（`POST`/`PUT`/`DELETE /dishes` 已于 2026-09-13 全部下线）；下架/纠错类需求走反馈 `error` 类型（关联菜品），新增菜品走 `add`，不再有独立 `apply` 表。
- **UGC 配图列（2026-09-13，以列扩展落地、不加表，基线 11 张）**：`review.images` / `user_feedback.images`（JSON 数组 ≤3 项 COS URL）；`review.sec_state` / `user_feedback.sec_state` 已于 2026-09-15 随「取消人工复核」全链退役；改库必须改 `server/src/main/resources/db/schema.sql`（幂等 ALTER），禁止直连库。

### 前端架构要点
- **小程序 `client/src`**：`api/`(含 `http.ts`、`shared.ts`)、`types/`、`stores/`(Pinia: user/dish/theme/location/notify/review)、`pages/`(主包 home/mine/find + 分包 detail/me)、`components/`、`theme/tokens.ts`、`uni.scss`、`assets/icons`。
  - `http.ts`：401 先静默登录重试一次，仍失败 `handleUnauthorized`（清 token+Toast+重登，并发去重），**不用事件总线**；403/4031 分级提示。
  - 图片：**UGC 配图（2026-09-13 恢复）**走 `wx.cloud.uploadFile` 传云开发云存储（中转）→ `POST /api/upload/images`（后端 imgSecCheck → COS 转存，返回 COS URL），配图组件（≤3 张、`wx.compressImage` 压缩 ≤1MB、≤750×1334）供评价弹层与反馈表单复用；头像仍走既有单图链路；图标统一 `<IconSvg>`（本地 `assets/icons`，语义唯一 ic-heart=喜欢、ic-thumb=有用/点赞、无收藏）。
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
- **产品定型一页纸（2026-09-13，防跑偏宪法；完整版见 `project_spec.md` §0.0，效力最高）**：
  - **一句话**：交大人的「吃什么不踩雷」——校园菜品信息展示与检索平台，用户反馈经安检与审核回流为高质量信息。
  - **五支柱**：① 信息展示优先（「售罄 / 今日供应」即时状态一期不做）；② 轻社区边界（UGC 仅「评价」一种形态，终局已定；不做动态 / 关注 / 私聊 / 收藏）；③ UGC 通道唯一（菜品共建走反馈表单 → 管理员录入，不恢复学生直建菜卡接口）；④ 合规底线（全部 UGC 过微信内容安检：**仅 `risky` 拦截 400，`pass`/`review` 一律放行，无人工复核**）；⑤ 轻运营（无运营位，Excel 批量导入推二期并预留导入通道抽象）。
  - **平台边界**：游客可浏览 / 搜索一切、评价 / 反馈须 `@bjtu.edu.cn` 认证；通知仅站内通知中心、不做任何推送；热度算法保持「浏览 + 评分聚合」（**无收藏维度——产品无收藏功能，历史文档 / 代码注释中的「收藏」为措辞残留，2026-09-13 勘误登记**）；北极星 = 周活 / 留存。
  - **演进预留（做之前须重新拍板）**：Excel 批量导入 → OCR 菜单识别（同一导入通道）、微信订阅消息推送、「售罄 / 今日供应」即时状态、推荐算法演进。
  - **定型增补（第二轮 PM 问答，2026-09-13）**：「我的反馈列表」**不恢复**（回执通知即闭环）；**账号注销本期落地**（合规硬需求，匿名化 + token 失效，入口在「我的」页底部）；菜品下架 = 客户端**完全不可见、评价保留**；dish.alias **别名搜索**本期落地（搜索命中 name 或 alias）。
  - **详细设计基线**：`docs/product-blueprint.md`（《产品定型总纲 v1.0》）——页面 / 流程 / 数据模型 / 接口 / 内容安全 / 算法 / 版本边界全量固化；**原则以 §0.0 为准、细节以总纲为准**。
  - **裁决规则**：任何新功能 / 改动若与本页冲突，**必须先修订 `project_spec.md` §0.0（重新拍板）再动代码**。
- **权威口径（2026-09-12 校正）**：`docs/project_spec.md` 为唯一权威；**代码只在 UI 实现层提供指导，不得据代码反向推翻文档**（文档已同步的部分，冲突时改代码不改文档）。开发交付以「静态错误清零」为准，**编译 / 构建 / 真机运行由用户执行**。
- **学生端菜品写接口全量下线（2026-09-13 拍板，防回退）**：`POST /dishes`（发布）、`PUT /dishes/{id}`（编辑重提）、`DELETE /dishes/{id}`（删本人菜品）三者已从 controller/service/impl 全量删除，DTO `dish/dto/DishPublishReq.java` 一并删除；客户端 `api/dish.ts` 的 `deleteDish`、详情页长按删除链路（`onDishLongPress`/`navTimer`/`onUnload`/`DishInfoCard` 的 `@longpress`）同步移除。**学生端无菜品写接口 = 学生只有评价类 UGC 写能力**；菜品由管理员经 `/admin/dishes/**` 录入，学生菜品需求走反馈 `add` 类型。**保留**：`POST /dishes/{id}/view`（浏览埋点）、全部 `GET /dishes*`、管理端 `/admin/dishes/**` 全部能力。恢复须重新拍板。
- **产品定位（2026-09-13 定稿）**：**校园菜品信息展示与检索平台**——信息展示 + 搜索 + 认证评价 + 反馈驱动的**轻 UGC 信息共建**；不做重社区。菜品 UGC 通道 = **反馈表单（可配图）→ 管理员审阅录入/修改/上下架**；不恢复学生直建菜卡接口（`POST /dishes` 维持已下线）。
- **产品聚焦四条主线（2026-09-12 拍板，最高优先级）**：① 菜品信息展示；② 搜索与查找（`find` 二级页）；③ 用户 UGC —— **评价类**（菜品评价，唯一评价形态，支持配图 ≤3 张）；④ 用户 UGC —— **反馈 / 贡献类**（意见反馈 + 举报 / 纠错 / 申请下架 / 推荐 / 新增菜品等反馈入口，支持配图 ≤3 张；菜品提交经反馈 `add` 由后台录入，学生端无菜品写接口）。**不属于这四条的一律不投入**；恢复已下线能力须重新拍板。
- **UGC 全谱系 = 评价 + 反馈 / 贡献**（③ + ④），是菜品信息迭代与程序优化的输入源；**反馈类 UGC 与评价同等重要，不得弱化**——它是实时发现菜品信息错误与程序问题、驱动信息更新与优化的主通道。本次下线的是「社区 / 动态」社交广场形态，**不是下线 UGC**。
- **社区板块（原动态信息流）已下线并全量删除干净（2026-09-12）**：小程序四个页面与发布流程分包、对应 api / types / 组件 / composable、后端模块与两端接口、Web 管理页、相关库表**全部删除，代码与文档均不留残留字样**；恢复靠 git 历史。评价不再「同步到社区」。
- TabBar **固定 home / mine 两页**；「我的」页宫格为**一行 3 列**（意见反馈 / 系统通知 / 我的评价），「我发布的」「最新活动」已删。
- UGC 唯一发布路径 = 菜品详情底栏「写评价」；无独立发布页、无「关联对象」表单。
- 收藏功能全量移除（无入口/字段/图标）；喜欢语义仅 `ic-heart`。
- 食堂/档口降级为菜品属性（`dish.canteen`/`dish.stall`），无独立路由。
- 搜索为二级页 `find`（非 tab），属核心板块，不得降级或移除。
- **活动 + 公告（broadcast）全链路下线（2026-09-13 拍板，防回退）**：小程序端零消费，`activity`/`broadcast` 的后端接口/实体、管理后台页面、库表全部删除（基线 14→12 张表）；小程序 `pages/activity/` 分包两页与「最新活动」宫格（含 `FEATURE_GATES.activity` 登记项）移除；`web-view` 随之退出小程序。恢复须重新拍板。
- 认证走 `AuthSheet` 弹层（无独立认证页）；反馈页 `POST /feedback` 公开、不收集联系方式、匿名提交。
- 通知异步写（`@Async`+有界线程池，不引 MQ）；推荐/热门用 Caffeine 60s TTL + 写失效；报表导出已移除。
- **贡献链路下线（2026-09-12）**：`apply_action` 表与 `/my/apply`、`/admin/apply*`、`/my/submissions` 接口全量删除；贡献类（新增菜品 / 推荐 / 纠错 / 申请下架）统一走反馈 `add`/`error` 类型，由管理员在 Web 后台据反馈手工录入/修正菜品闭环，无独立申请链路。
- **UGC 配图与微信内容安检（2026-09-13 拍板；2026-09-15 取消人工复核，防回退）**：评价与反馈**支持配图**（各 ≤3 张；前端 `wx.compressImage` 压缩至 ≤1MB、≤750×1334）；**全部 UGC（文本+图片）过微信内容安检**——文本 `msgSecCheck` v2（scene：昵称=1、评价/反馈=2；**仅 `risky` 拦截=400，`pass`/`review` 一律放行**）、图片 `imgSecCheck`（87014 拦截）；access_token 用 stable_token 缓存。图片链路：`wx.cloud.uploadFile` 传云开发云存储（免域名白名单）→ 后端 tcb 拉取 → imgSecCheck → 转存 **COS**（新接口 `POST /api/upload/images` 入参 fileId；multipart `/api/upload/image` 保留）。安检态 `sec_state` 列与人工复核队列已于 2026-09-15 全链退役（`PUT /admin/reviews/{id}/sec-state` 端点删除）；管理端仅保留**事后处置**（`PUT /admin/reviews/{id}/hide`、`DELETE /admin/reviews/{id}`），评价可见性口径收敛为仅 `is_hidden=0`。环境变量新增 `COS_BUCKET/COS_SECRET_ID/COS_SECRET_KEY/COS_REGION`。**此拍板推翻 2026-09「UGC 图片全量下线、无图片入口」口径**——评价/反馈相关走查清单中「无图片入口」类条款作废；链路不绑定云托管，后端可整体迁移独立服务器（届时上传域名走备案域名白名单）。
- ~~UGC 图片全量下线（2026-09-12）~~ → **已被 2026-09-13 拍板取代**（见上条）：当日曾移除评价/反馈 `images` 与上传入口并删除 `ImageUploader`，现随配图恢复重新落地（配图组件供评价弹层与反馈表单复用）；本条保留为演进记录，勿再据「全量下线」口径开发或走查。
- **我的评价（2026-09-12）**：新增「我的评价」格（`requireAuth`）与「我的评价」列表页（路径 `pages/my-reviews`；本人删除 + 空态双口径），复用 `DELETE /reviews/{id}`；2026-09-13 随活动下线宫格由 2×2 收敛回一行 3 列（意见反馈 / 系统通知 / 我的评价）。
- **反馈回执（2026-09-12）**：反馈/举报提交成功给处理预期文案（游客明确「无法单独通知你」）；管理员标记处理后，已认证提交人于系统通知收到 `feedback_handle` 回执（游客不投递不阻塞）。
- **按压语言改用 bg-soft（2026-09-12 拍板；适用范围 = 小程序端）**：废止 `scale(0.97)` 按压（小程序侧 `--press-scale` token 作废）；mp-weixin 下 `transform: scale` 按压易致卡片边缘溢出/裁剪，`client/` 统一 `background: var(--color-bg-soft)`/`opacity` 作按压反馈；`grep` 范围限 `client/` 应 0 处裸 `scale(...)`。**Web 端登记豁免**：DOM 端无 mp-weixin 溢出问题，`web/` 的 `scale(var(--press-scale))` 维持使用，不受本条约束。

## 改动前注意
- 任何 spec 冲突先问技术负责人，**不要改 `project_spec.md`**（除非你就是技术负责人角色）。
- 后端加表/改字段 → 改 `server/src/main/resources/db/schema.sql`（及 seed），不要直连库。
- 新增接口先确认 `api-design.md` 契约与错误码，复用统一响应与分页结构。
- 小程序改动后用 `npm run type-check` 校验；改样式/组件须过 §4.9 UI 红线 grep 自检（裸 scale / 裸 hex / `@click`）。
