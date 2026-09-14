# 接口设计总览（食在交大 bjtu_food）

> 本文档描述后端 REST 接口契约、认证模型、错误码与数据流转，与实现（`server/src/main/java/com/bjtufood/**`）逐步对齐。
> 供前端（client/web）联调与后续快速上手。
> **权威层级**：以 `docs/project_spec.md` 为唯一权威（spec 为产品宪法）。**若本文档与 spec 有出入，以 spec 为准**（不得据代码现状反向推翻文档，spec §0.4 / §0.5 执行口径）；本文档与实现的差异以 §8「与 project_spec.md 的差异」逐条登记，凡已拍板的下线 / 变更均以 spec 为准收敛，本文档同步修正。

## 1. 通用约定

### 1.1 基础信息
- **Context Path**：`/api`（所有接口实际 URL 前缀为 `/api/xxx`）
- **数据交换**：JSON；图片上传两条链路——UGC 配图走 JSON `{ fileId }`（**单张接口，前端逐张调用**；小程序先 `wx.cloud.uploadFile` 传云开发云存储，见 §4），头像 / 后台菜品图走 `multipart/form-data`
- **统一返回信封** `Result<T>`：
  ```json
  { "code": 200, "message": "ok", "data": { ... } }
  ```
  - `code=200` 成功；`code!=200` 业务/鉴权失败
  - `data` 为实际载荷（`T` 类型）
- **鉴权**：`Authorization: Bearer <JWT>`；游客未带 token 也可访问公开接口，但受限写接口需认证。

### 1.2 认证模型（微信登录 + 邮箱认证）
| 概念 | 说明 |
|---|---|
| 游客态 | 微信静默登录建号，`verified=0`；可浏览公开内容，不可写用户内容（评价 / 评价点赞等 UGC；菜品贡献走公开反馈 `POST /feedback`） |
| 已认证 | 绑定 `@bjtu.edu.cn` 邮箱（验证码）后 `verified=1`，解锁写操作 |
| 角色 | `student`（默认）/ `admin`（**`super_admin` 已移除**，2026-09-14 与 spec §7.10 对齐；`user.role` 仅作账号归属的数据语义，不再作权限分层） |
| 状态 | `active` / `disabled` / `deleted` |

> **管理端鉴权（2026-09-14 与 spec §7.10 对齐，原方案 C 作废）**：`/admin/**` **无登录体系、无 JWT、无角色校验、无 BCrypt**；由 `AdminTokenFilter` 校验请求头 `X-Admin-Token` == 环境变量 `ADMIN_TOKEN`（未配置即 fail-closed 403）。`/auth/admin/login` 端点**不存在**、`SUPER_ADMIN` 分层已移除（详见 §1.2 与 §5 抬头）。

### 1.3 错误码
| code | 含义 | 前端处理 |
|---|---|---|
| 200 | 成功 | — |
| 400 | 参数/业务校验失败（含资源不存在；**2026-09-13 起含 UGC 内容安检违规**——文本 `msgSecCheck` `suggest=risky`、图片 `imgSecCheck` 微信 code `87014`，`message` 给出违规提示） | 提示 `message` |
| 401 | 未登录 / token 失效 | 触发静默登录重试，仍失败则登出 |
| 403 | 无权限（含越权访问管理接口） | 提示「无权限访问该内容」 |
| **4031** | 邮箱未认证（`@RequireVerified` 触发，区别于 403） | 提示「请先完成学号邮箱认证」并弹认证引导 |
| 500 | 服务器异常 | 通用兜底 |

> 错误码仅 `200/400/401/403/4031/500`（404 已收敛入 400，`Result.notFound` 工厂已删除，2026-09）；`4031` 为唯一登记豁免的细分业务码（spec §3 已登记，前端 http.ts 据此分流「未认证」与「无权限」）。

### 1.4 分页约定
- 分页参数：`page`（从 1 起）、`pageSize`
- **上限由 `PageUtil.normalize` 统一约束**（pageSize 超上限被截断，防一次性全表加载）
- 分页返回结构统一为 **`PageResult<T>{ records, total, page, pageSize }`**（spec §3 / §5.x 契约形态）；`page` / `pageSize` 为经 `PageUtil.normalize` 归一化后的**实际生效值**（2026-09-14 补齐，见 spec §7.11 第 2 条）。
- **`list` 为过渡期历史字段**（只读派生自 `records`，二者恒等值，`PageResult.getList()` 无 setter，序列化恒同值）：前端 `api` 层以 `recordsOf()` 兼容 `records` / `list` 双形态，消费方以 `records` 为准；待全部消费方切到 `records` 后再移除 `list`（收敛动作另行排期，见 spec §7.11 第 2 条）。
- **注记（2026-09 核实，2026-09-14 更新）**：此前记载的 `PageResult{ list, total }`（仅两字段、无 `page/pageSize`）形态**已过期作废**——所有经 `common/result/PageResult` 归一的接口（`GET /my/reviews`、`/admin/feedbacks*`、`/admin/dishes`、`/admin/users`、`/admin/reviews` 等）现均为上述 4 参（+ `list` 派生）统一形态。

---

## 2. 公开接口（无需登录）

### 2.1 食堂与档口（CanteenController）
| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| GET | `/canteens` | `lat`/`lng`（可选，兼容保留） | `List<CanteenInfoVO>` | 全部食堂（筛选属性字典，**无 open/closed 过滤**——`status` 列已随 2026-09-14 Q-119 去实体化删除）；`lat`/`lng` 为兼容保留参数，距离由端上本地 Haversine 计算，服务端不再按距离排序 |
| GET | `/canteens/all` | — | `List<食堂含档口树>` | 一次性渲染食堂+档口 |

### 2.2 菜品（DishController）
| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| GET | `/dishes/hot-search` | — | `List<HotSearchVO>` | 热搜 TOP10 |
| GET | `/dishes` | `DishQueryReq`（keyword/canteenId/stallId/categoryId/tag/minPrice/maxPrice/spiceLevel/sortBy/sortOrder/page/pageSize/excludeIds） | `PageResult<DishVO>` | 菜品分页搜索/筛选/排序（keyword 同时命中 name 与 alias 别名；`categoryId` 保留为**后台归类查询维度，端上不传**——端上无品类筛选入口，2026-09-14 Q-117） |
| GET | `/dishes/{id}` | `id` | `DishDetailVO` | 详情（登录时含 hasReviewed） |

> **2026-09-14 端上零消费接口下线（spec §7.10 第 1 条）**：`GET /dishes/hot`、`GET /dishes/new`、`GET /dishes/promotions`、`GET /dishes/rising`、`GET /dishes/recommend` 已从 `DishController` 整体删除（端上零消费，连带 service / mapper / 缓存清理）。保留：`GET /dishes`（首页瀑布流与筛选）、`GET /dishes/{id}`、`POST /dishes/{id}/view`（§3.2）、`GET /dishes/hot-search`（首页热搜在用）。菜品促销价与划线原价字段（`promo_price`/`original_price`）保留不变。

### 2.3 评价（ReviewController）
| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| GET | `/reviews` | `dishId`/`stallId`/`canteenId`（三选一，至少传其一）/page/pageSize/sort(latest/useful，**默认 useful 按有用数置顶**，2026-09-14 §7.14 第 2 条) | `PageResult<ReviewVO>` | 评价列表（仅未隐藏且机检通过；本人评价除外，后端过滤） |
| GET | `/dishes/{dishId}/reviews` | page/pageSize/sort | `PageResult<ReviewVO>` | 菜品评价（同上口径） |

> **`ReviewVO` 字段（2026-09-13 随 UGC 配图恢复扩充）**：新增 `images`（字符串数组，≤3 项 COS URL，无图返回空数组）与 `secState`（**三态** `pass`/`review`/`rejected`——`review`=机检待人工复核、`rejected`=人工驳回，两者均对非作者不可见；列表 / 详情接口仅返回 `sec_state='pass'` 或本人评价，后端过滤，前端不兜底）。历史注记（2026-09 契约清理）「`isWithImage` 参数已不存在」维持有效：`isWithImage` 筛选参数不恢复，配图随评价正文整体展示。

### 2.5 内容/品类（公开）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/categories` | 品类列表（enabled，sort_order 升序）。**2026-09-14 Q-117 定型（spec §7.22 第 1 条）：保留为后台菜品归类用途**——端上品类死链路已删除（`api/category.ts`、品类滚轮 UI 与本接口的端上调用均已清理），不再作为端上筛选数据源、端上零消费 |
| GET | `/images/**` | 静态图片资源 |

> `GET /broadcasts`、`GET /activities` 及管理端 `/admin/broadcasts`、`/admin/activities` 系列接口已于 2026-09-13 随活动/公告（broadcast）全链路下线删除，接口与实体均不存在（spec §0.5 已登记）。

---

## 3. 需登录接口（学生端）

### 3.1 认证与账号（AuthController / UserController）
| 方法 | 路径 | 认证 | 参数 | 说明 |
|---|---|---|---|---|
| POST | `/auth/wechat-login` | 公开 | `{ code }` | 微信静默登录，新 openid 自动建号（verified=0），返回 token |
| POST | `/auth/email-code` | 登录 | `{ username, email(可空，传学号自动推导 {username}@bjtu.edu.cn), purpose }` | 发学号邮箱验证码（同邮箱 60s 限频 + 同 IP 每分钟 ≤3 次/每小时 ≤10 次，P3/BE-105；6 位 10 分钟有效） |
| POST | `/auth/verify-email` | 登录 | `{ code }` | 验证码认证，绑定邮箱，verified→1，返回新 token |
| GET | `/auth/profile` | 登录 | — | 用户资料（**不含 openid**） |
| PUT | `/auth/profile` | 登录 | `{ nickname, avatar }` | 更新资料（avatar 仅允许站内 `/images/`、`/uploads/`、`cloud://`） |
| DELETE | `/auth/account` | 登录 | — | **注销账号（匿名化，非物理删除）**：nickname→'已注销用户'、openid/unionid→NULL（解绑，允许重新登录建新游客号）、status→'deleted'；评价/反馈保留但去身份化；token 立即失效（TokenBlacklist token+userId 双维度）；幂等（重复调用 400「账号已注销」） |

> `PUT /auth/password` **已于 2026-09-14 删除（spec §7.21 第 3 条 Q-108，用户确认）**：端点、`AuthController` / `AuthServiceImpl.changePassword` 方法与 `PasswordChangeReq` DTO 均已移除——学生端与管理端均无密码体系，本文档旧版记载作废，不得据此推导该端点存在。

### 3.2 菜品埋点（登录即可，非写接口）
| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| POST | `/dishes/{id}/view` | — | 浏览量+1（同时记录浏览足迹）。**去重口径（2026-09-14，spec §7.14 第 1 条）**：同一用户对同一菜品每个自然日（Asia/Shanghai）只计 1 次——当日已有 `view_log` 记录则幂等返回成功，不自增 `view_count`、不重复写足迹 |

> **学生端菜品写接口已于 2026-09-13 全部下线**：`POST /dishes`（学生发布）、`PUT /dishes/{id}`（编辑重提）、`DELETE /dishes/{id}`（删本人菜品）三者均已从 `DishController` / `DishService` / `DishServiceImpl` 删除，接口不存在，客户端零消费（`api/dish.ts` 的 `deleteDish`、详情页长按删除链路同步移除）。`DishPublishReq` DTO 已随之删除。菜品由管理员经 `/admin/dishes/**` 录入，学生新增菜品需求走反馈 `add` 类型（`POST /feedback`）由后台处理。
>
> 本表 `POST /dishes/{id}/view` 为浏览埋点，保留；全部 `GET /dishes*` 只读接口保留。

### 3.3 评价（邮箱认证）
| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| POST | `/reviews` | `ReviewReq{dishId,rating,content,images?}` | 提交评价（每菜一人一评；`images` 可选字符串数组，≤3 项 COS URL） |
| DELETE | `/reviews/{id}` | — | 删本人评价（级联清理 useful） |
| POST | `/reviews/{id}/useful` | — | 「有用」切换（一人一票） |
| GET | `/my/reviews` | page/pageSize | 我的评价（`@RequireVerified` + `@PreAuthorize("hasRole('STUDENT')")`，返回 `PageResult<ReviewVO>` 4 参统一形态） |

> 评价不支持修改（`PUT /reviews/{id}` 与契约路径 `DELETE /my/reviews/{id}` 均不存在，2026-09 契约清理）；改评 = 删除后重提（一人一菜一评由 `uk_review_user_dish` 保证）。
>
> **安检（2026-09-13）**：提交时文本过 `msgSecCheck` v2（`scene=2` 评价场景）；`suggest=pass` 正常落库，`suggest=review` 正常落库且 `sec_state='review'`（对非作者不可见，进管理后台复核队列），`suggest=risky` 返回 `400` 拦截。配图须先经 `POST /upload/images` 逐张安检转存（单张接口，违规该张 400、前端跳过不中断），再把返回的 COS URL 随 `images` 提交。
>
> **`GET /my/reviews` 契约注记（2026-09-13 核实，AUD-BE-06 / AUD-BE-07；2026-09-14 更新）**：① 返回形态已随 `PageResult` 4 参统一化收敛（spec §7.11 第 2 条）——现经 `PageResult.of(records, total, page, pageSize)` 返回 **`{ records, total, page, pageSize }`**（`page`/`pageSize` 为归一化实际生效值），原「`PageResult{list, total}` 两参形态」注记作废；前端 `recordsOf()` 双形态兼容兜底暂予保留（收敛另行排期）。② 除 `@RequireVerified`（切面按 `user.verified` 实时判定）外，另挂方法级 `@PreAuthorize("hasRole('STUDENT')")` 纵深防御——小程序端用户默认 `STUDENT` 角色，不影响正常调用；该双重校验口径与文档描述一致（`ReviewController.java:70-80`）。

### 3.5 通知（需邮箱认证 `@RequireVerified`）
> 通知 `type`：`feedback_handle`（反馈/举报处理结果回执，仅已认证提交人可收到）；`dish_audit`（菜品审核结果）为**仅存量兼容**类型——2026-09-14 实体审核链路删除（spec §7.21 第 2 条 Q-107）后不再产生新通知，历史数据保留可读。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/my/notifications` | 消息列表（isRead/page/pageSize） |
| GET | `/my/notifications/unread-count` | 未读总数（红点） |
| PUT | `/my/notifications/{id}/read` | 单条已读 |
| PUT | `/my/notifications/read-all` | 全部已读（**2026-09-14 恢复**） |

> **全部已读 `PUT /my/notifications/read-all`（2026-09-14 恢复该能力，此前契约清理时曾被移除；依据 `project_spec.md` §7.18 第 2 条）**：
> ① 鉴权：需登录（同 `/my/notifications` 系列口径——`@RequireVerified` 按 `user.verified` 实时判定 + 方法级 `hasRole('STUDENT')`）；
> ② 语义：一次性将该用户**全部未读**通知置为已读，**幂等**——无未读时返回 0，不报错；重复调用结果一致；
> ③ 返回：`Result<Integer>`，`data` = 本次置为已读的条数（无未读时为 `0`）。

### 3.6 反馈
| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/feedback` | 公开 | 提交反馈（游客可；含举报/纠错/推荐菜品；**2026-09-13 起请求体增可选 `images`：字符串数组 ≤3 项 COS URL**，游客提交同样可带图；文本过 `msgSecCheck` v2 `scene=2`，`risky` 拦 400、`review` 落 `sec_state='review'`） |

> 「我的反馈」接口 `GET /feedback/my` 已随反馈中心下线删除（2026-09-07）；进度追踪后续另做。

---

## 4. 图片上传与内容安检（2026-09-13 重构：UGC 配图链路 + 合规要求）

### 4.1 接口

| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| POST | `/upload/images` | JSON `{ fileId: string }`（**单张**，小程序 `wx.cloud.uploadFile` 产生的云存储 fileID，形如 `cloud://env.bucket/path`） | `{ url: string }`（该张 COS 永久 URL） | **UGC 配图上传（2026-09-13 新增；单张契约）**：后端经 tcb `batchdownloadfile` 从云开发云存储拉取 → `imgSecCheck` 送检（违规 code `87014` 返回 400）→ 转存 COS。多张配图由前端**逐张调用**本接口（每张独立送检转存，**单张失败该张返回 400、前端提示后跳过，不中断其余图片**）。需登录（游客亦可，无 `verified` 门槛）；评价 / 反馈提交前先逐张调本接口取 COS URL |
| POST | `/upload/image` | `file`（multipart，jpg/jpeg/png/webp） | `{ url, relativeUrl }` | **保留**：用户头像、后台菜品图等既有链路，不承载 UGC 配图 |

- `/upload/images` 单张校验：fileId 属本小程序云环境（`cloud://` 前缀）+ 扩展名白名单 + 大小 ≤1MB（`imgSecCheck` 硬限制）+ `imgSecCheck` 通过才转存；任一不通过该张返回 400 与明确提示（违规 / 文件获取失败 / 存储未配置），不影响其他张。
- **≤3 张总量约束不在本接口**：由评价 / 反馈提交载荷校验兜底（`ReviewReq.images` / 反馈 `images` ≤3 项，服务端对最终载荷再校验张数与 URL 域名白名单）。
- 返回 `data.url` 为该张 COS 完整 URL，前端收集后随评价 / 反馈 `images` 提交；数据库（`review.images` / `user_feedback.images`) 直接存 COS URL。

### 4.2 微信内容安检合规要求（全 UGC 强制，登记于本节统一维护）

| 项 | 约定 |
|---|---|
| 文本安检 | `msgSecCheck` **v2**：入参 `openid`（提交人 openid，服务端据 userId 取）+ `scene` + `version=2`；后端封装为统一安检入口（`ContentSecurityService`），评价 / 反馈提交与昵称更新均不得绕过 |
| scene 映射 | **昵称=1；评价/反馈=2**（后续新增 UGC 形态须在本表登记 scene 值） |
| suggest 三态 | `pass` 放行；`review` 落 `sec_state='review'` 进人工复核（不拦截提交）；`risky` 返回 `400` 拦截 |
| 图片安检 | `imgSecCheck`：违规（微信 code `87014`）返回 `400` 拦截 |
| access_token | 统一使用微信 **`stable_token`** 并缓存（刷新互斥、不走过期即弃的普通 token） |
| 状态字段 | `review.sec_state` / `user_feedback.sec_state`（**三态** `pass`/`review`/`rejected`，默认 `pass`；`review`=机检待人工复核、`rejected`=人工复核驳回，两者均对非作者不可见）；管理后台 `PUT /admin/reviews/{id}/sec-state` 复核放行（→`pass`）/ 驳回（→`rejected`） |
| 错误码 | 安检违规一律 `400`，不新增错误码（错误码仅 `200/400/401/403/4031/500`） |

---

## 5. 管理后台接口（管理端口令保护）

> **鉴权口径（2026-09-14 与 spec §7.10 对齐，原「需 ADMIN / SUPER_ADMIN + JWT + 方法级 `@PreAuthorize`」描述作废）**：`/admin/**` **无登录体系、无 JWT、无角色校验**——`SecurityConfig` 放行后由 `AdminTokenFilter` 校验请求头 `X-Admin-Token` == 环境变量 `ADMIN_TOKEN`（未配置即 fail-closed 403；web 端 `VITE_ADMIN_TOKEN` 与之同值）。`SUPER_ADMIN` 角色已移除，`/auth/admin/login` 端点不存在。

### 5.1 工作台（DashboardController）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/dashboard` | 工作台总览（`range=week/month/all` → 7/30/90 天，默认 week）。**2026-09-14 Q-106 收口（spec §7.21 第 1 条）**：不再计算前端不消费的图表与趋势字段（`hotCanteens`/`hotDishes`/`viewTrend`/`reviewTrend`，原 `viewTrend` 命名与语义不符一并修正），不再执行全表菜品聚合；仅返回待办（含明细 5 条）+ 规模指标 + 近期操作（10 条），各统计项独立容错 |

> 原 `GET /admin/stats/**`（统计：热门排行/趋势）为**幽灵端点**（全仓零实现，2026-09-14 核实）：现 `StatsController` 不暴露任何 HTTP 端点（无 `@RequestMapping`/`@GetMapping`），仅作为 `DashboardController` 的统计逻辑复用载体，该端点**不新建**。

### 5.2 菜品管理（DishAdminController）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/dishes` | 菜品列表（分页 `PageResult<DishAdminVO>`，4 参统一形态 `{records,total,page,pageSize}`） |
| POST | `/admin/dishes` | 新增菜品（默认 approved） |
| PUT | `/admin/dishes/{id}` | 编辑 |
| DELETE | `/admin/dishes/{id}` | 删除（级联清评价） |

### 5.3 用户管理（UserAdminController）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/users` | 用户列表（role/status 过滤，`PageResult<UserVO>` 4 参统一形态） |
| PUT | `/admin/users/{id}/status` | 启停用户 |

> **已删除端点（2026-09-14 与 spec §7.10 对齐）**：`PUT /admin/users/{id}/role`（改角色，无实现；`SUPER_ADMIN` 已移除）与 `GET/POST/PUT/DELETE /admin/admins/*`（管理员账号管理，Controller 不存在；管理后台「账号设置」入口已删除，spec §7.10 第 4 条）均**不存在**，勿按旧版记载对接。

### 5.4 审核与内容治理
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/reviews` | 评价审核列表（isHidden/secState/userId/keyword 过滤；**2026-09-13 起支持 `secState` 过滤，VO 含 `images`/`secState`**） |
| PUT | `/admin/reviews/{id}/sec-state` | **评价安检复核（2026-09-13 新增）**：入参 `{ state: "pass" \| "rejected" }`——放行（落 `sec_state='pass'`，恢复公开展示）/ 驳回（落 `sec_state='rejected'`，持续对非作者不可见，作者侧呈现未过审态）。`review` 态仅由机检写入，本接口不接受（管理端只写人工结论） |
| PUT | `/admin/reviews/{id}/hide` | 隐藏评价 |
| DELETE | `/admin/reviews/{id}` | 删评价（清理 useful 孤儿） |
| GET | `/admin/feedbacks*` | 反馈审核（回复；**VO 含 `images`/`secState`，详情展示配图 ≤3 张**） |

### 5.5 基础数据维护
| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT | `/admin/canteens`、`/admin/stalls` | 食堂/档口**筛选属性字典**——能力仅「列表查看 / 新增 / 改名（编辑）」，**无 DELETE**（2026-09-14 Q-115，spec §7.22 第 5 条）；无 `status`/`auditStatus`/`rejectReason` 字段（Q-119 已 DROP） |
| GET/POST/PUT/DELETE | `/admin/categories` | 品类（增删改启停，sortOrder 非数字返回 400）。**2026-09-14 Q-117 定型：仅供后台菜品归类用途**（菜品表单 `categoryId` 归类），端上不呈现 |
| GET | `/admin/operation-logs` | 操作日志（只读） |

> 原 `GET /admin/audit/*`（待审内容）已于 2026-09-14 随实体审核链路删除（spec §7.21 第 2 条 Q-107）：`/admin/audit/**` 三条端点及其专属 Service / VO / DTO 均已移除；菜品审核语义收敛为「管理员录入即 `approved`」（spec §7.8 第 1 条）。

---

## 6. 关键数据流转

### 6.1 评价 → 评分聚合
```
POST /reviews → ReviewSubmittedEvent → RatingUpdateListener(@Async AFTER_COMMIT)
             → recalcAvgRating(dishId) 更新 dish.avg_rating / rating_count
```
- 聚合异步执行，失败仅记 `[ALERT]` 日志不阻塞提交
- **聚合口径（2026-09-14 Q-110，spec §7.21 第 4 条）**：只计入 `is_hidden=0 AND sec_state='pass'` 的评价——被内容安检判为 `review` / `rejected` 的内容完全不进统计；机审结果回写时须触发重算；`rating_count` 与「该菜品可见评价数」同口径（历史存量重算见 spec §8「待运维执行」条目）

### 6.2 浏览 → 足迹
```
GET /dishes/{id} → addViewCount(+1，按用户×菜品×自然日去重) + recordDishView(userId,dishId) upsert 足迹
```

> 原「`GET /dishes/recommend` 猜你喜欢」已随 2026-09-14 端上零消费接口下线删除（spec §7.10 第 1 条）；浏览足迹仅作为 `view_log` 数据留存，无下游消费接口。

### 6.3 审核流（2026-09-14 校准，与 spec §7.8 / §7.21 对齐）
```
菜品：管理员经 /admin/dishes 录入/编辑 → audit_status=approved（管理员即权威，spec §7.8 第 1 条）；学生端无菜品写接口（POST/PUT/DELETE /dishes 已于 2026-09-13 下线）
评价：先发后审、无 audit_status——机检 pass 即公开（is_hidden=0 AND sec_state='pass'）；sec_state='review' 进人工复核队列（PUT /admin/reviews/{id}/sec-state）；risky 提交即 400 拦截
反馈/举报：POST /feedback 公开提交 → 管理员 PUT /admin/feedbacks/{id} 处理（reply 必填）→ 站内通知回执
纠错/下架/新增菜品：反馈类型承载（error/add，关联菜品），无独立申请表
```

> 食堂/档口为筛选属性字典，无 `status`/`audit_status`/`reject_reason`（2026-09-14 Q-113 / Q-119 去实体化），不参与审核流。

---

## 7. 前端对接说明

- **base URL**：小程序经 `wx.cloud.callContainer` 访问（云托管），`context-path=/api`；web 管理端走公网/内网代理。
- **图片**：`relativeUrl` 用于存库（头像 / 后台菜品图），展示时前端拼完整前缀；**UGC 配图为 COS 完整 URL**（`review.images` / `user_feedback.images`，直接展示，无需拼前缀）。
- **分页**：统一读 `records`/`total`（`page`/`pageSize` 为经 `PageUtil.normalize` 归一化的实际生效值；原 `/activities` 裸数组例外已随该接口下线删除，2026-09-13）。
- **403 分级**：`code=403`（无权限）与 `code=4031`（未认证邮箱）前端需区分处理。

---

## 8. 与 project_spec.md 的差异（以代码为准）

| 项 | spec 描述 | 实际代码 | 建议 |
|---|---|---|---|
| **UGC 配图与内容安检（2026-09-13 拍板，QA 门禁契约校准）** | spec §5.a：评价/反馈配图 ≤3 张、msgSecCheck v2/imgSecCheck、`sec_state` 可见性、`POST /upload/images`、`PUT /admin/reviews/{id}/sec-state` | 已落地并对账一致（本文档 §2.3 / §3.3 / §3.6 / §4 / §5.4 契约与实现一致：**单张**上传、前端逐张调用、`sec_state` 三态 `pass/review/rejected`、复核入参 `{ state: "pass"\|"rejected" }`） | 原「评价全量纯文本、反馈纯文本免图」注记已随本拍板作废 |
| **activity / broadcast 全链路下线（2026-09-13）** | spec 曾列 `/activities`、`/broadcasts`、`/admin/activities`、`/admin/broadcasts` 接口与 activity/broadcast 实体 | 接口 / 实体 / 库表 / Web 管理页 / 小程序页面（`pages/activity/` 分包）与「最新活动」宫格全部删除，数据库基线 14 → 12 张表 | 本文档 §2.5 / §5.5 已删除相关行；spec §0.5 已登记下线拍板 |
| 页面数量 | 9 页（spec §2.1，2026-09-13 随 activity 下线由 11 收敛） | 9 页（pages.json：主包 3 + 分包 detail/me，共 9 页） | 已对齐（spec §2.1 与 pages.json 一致） |
| 4031 错误码 | 禁止非标码（例外豁免制） | 使用 4031 细分 | 已在 spec §3 登记豁免（2026-08-19） |
| view_log | 要求唯一键+upsert | 无唯一键，应用层 upsert | 已实现写入，唯一键可选增强 |
| ~~`GET /my/reviews` 分页形态~~ | IPage `{records,total,...}`（§1.4 通用） | **已消除（2026-09-14）**：经 `PageResult.of(...)` 统一为 4 参 `{records,total,page,pageSize}`（+`list` 派生过渡字段） | 原「`PageResult{list,total}` 两参形态」差异不复存在；§1.4 / §3.3 注记已同步更新，前端 `recordsOf()` 兜底暂保留 |
| `GET /my/reviews` 权限 | 仅 `@RequireVerified` | 额外 `@PreAuthorize("hasRole('STUDENT')")`（`ReviewController.java:71`） | 已在 §3.3 加注（不影响小程序，默认 STUDENT） |
| `POST /dishes`（学生发布菜品） | 曾列为 UGC 写路径 | 接口已删除（2026-09-13 下线） | 已在 §3.2 加注，spec §5 已同步 |
| `PUT`/`DELETE /dishes/{id}`（学生编辑·删除本人菜品） | 曾列为学生 UGC 写路径 | 接口已删除（2026-09-13 下线，`DishPublishReq` 一并删除） | 已在 §3.2 加注，spec §0.1/§0.3/§3/§5/§5.y/§5.z 已同步为学生端无菜品写接口 |

---

## 9. 已知技术债 / 建议
- ~~`BroadcastAdminController` 用 `@RequestBody Broadcast` 直收无 `@Valid`/枚举校验~~ → 已随 2026-09-13 活动/公告全链路下线删除（该 Controller 不存在）
- ~~验证码限频无 IP 维度~~ → **已解决**（P3/BE-105：`/auth/email-code` 接入 `IpRateLimiter`，同 IP 每分钟 ≤3 次、每小时 ≤10 次，2026-09-14 核实）
- ~~前端裸 hex（find confirmColor）~~ → **已解决**（已收敛为 `client/src/theme/tokens.ts` 登记常量 `MODAL_CONFIRM_DANGER_COLOR` / `MODAL_CONFIRM_PRIMARY_COLOR`，满足 spec §4.9 裸 hex 红线；原 webview progressbar 例外已随 `web-view` 退出小程序移除）
- ~~`SecurityConfig` 白名单残留 `/lists/share/**`~~ → **已解决**（白名单已清理，`PUBLIC_ANY_METHOD` / `PUBLIC_GET_PREFIXES` 无美食清单残留条目，2026-09-14 核实）
- ~~`NotificationController` 直调 `NotificationMapper`（分页/已读/未读计数在 Controller 内完成）→ 违反「Controller 不得直调 Mapper」分层红线（spec §2）~~ → **已解决**（P3/ARCH-008：查询/计数/已读逻辑下沉 `NotificationService`，Controller 不再注入 Mapper，2026-09 核实）
- ~~通知接口用 `hasRole('STUDENT')` 而非 verified 口径~~ → **已解决**（`@RequireVerified` 切面已补齐，2026-09 核实）
