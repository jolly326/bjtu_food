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
| GET | `/canteens` | — | `List<CanteenInfoVO>` | 全部食堂（筛选属性字典，**无 open/closed 过滤**——`status` 列已随 2026-09-14 Q-119 去实体化删除）；**不接收 `lat`/`lng` 参数**（2026-09-15 DOC-05/CT-01 删「兼容保留」描述）：坐标随食堂 VO 返回，距离由端上本地 Haversine 计算，服务端不做距离排序 |
| GET | `/canteens/all` | — | `List<食堂含档口树>` | 一次性渲染食堂+档口 |

### 2.2 菜品（DishController）
| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| GET | `/dishes/hot-search` | — | `List<HotSearchVO>` | 热搜 TOP10 |
| GET | `/dishes` | `DishQueryReq`（keyword/canteenId/stallId/tag/minPrice/maxPrice/spiceLevel/sortBy/sortOrder/page/pageSize） | `PageResult<DishVO>` | 菜品分页搜索/筛选/排序（keyword 同时命中 name 与 alias 别名） |
| GET | `/dishes/{id}` | `id` | `DishDetailVO` | 详情（登录时含 hasReviewed） |

> **2026-09-14 端上零消费接口下线（spec §7.10 第 1 条）**：`GET /dishes/hot`、`GET /dishes/new`、`GET /dishes/promotions`、`GET /dishes/rising`、`GET /dishes/recommend` 已从 `DishController` 整体删除（端上零消费，连带 service / mapper / 缓存清理）。保留：`GET /dishes`（首页瀑布流与筛选）、`GET /dishes/{id}`、`POST /dishes/{id}/view`（§3.2）、`GET /dishes/hot-search`（首页热搜在用）。菜品促销价与划线原价字段（`promo_price`/`original_price`）保留不变。

### 2.3 评价（ReviewController）
| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| GET | `/reviews` | `dishId`/`stallId`/`canteenId`（**可同时传，按 `stallId` > `canteenId` > `dishId` 优先取一，不报 400**（2026-09-15 CT-04 修订「三选一」口径）；至少传其一）/page/pageSize/sort(latest/useful，**默认 useful 按有用数置顶**，2026-09-14 §7.14 第 2 条) | `PageResult<ReviewVO>` | 评价列表（仅未隐藏且机检通过；本人评价除外，后端过滤） |

> **`ReviewVO` 字段（2026-09-13 随 UGC 配图恢复扩充）**：新增 `images`（字符串数组，≤3 项 COS URL，无图返回空数组）与 `secState`（**三态** `pass`/`review`/`rejected`——`review`=机检待人工复核、`rejected`=人工驳回，两者均对非作者不可见；列表 / 详情接口仅返回 `sec_state='pass'` 或本人评价，后端过滤，前端不兜底）。历史注记（2026-09 契约清理）「`isWithImage` 参数已不存在」维持有效：`isWithImage` 筛选参数不恢复，配图随评价正文整体展示。

### 2.5 内容/品类（公开）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/images/**` | 静态图片资源 |

> `GET /broadcasts`、`GET /activities` 及管理端 `/admin/broadcasts`、`/admin/activities` 系列接口已于 2026-09-13 随活动/公告（broadcast）全链路下线删除，接口与实体均不存在（spec §0.5 已登记）。

---

## 3. 需登录接口（学生端）

### 3.1 认证与账号（AuthController / UserController）
| 方法 | 路径 | 认证 | 参数 | 说明 |
|---|---|---|---|---|
| POST | `/auth/wechat-login` | 公开 | `{ code }` | 微信静默登录，新 openid 自动建号（verified=0），返回 token |
| POST | `/auth/email-code` | **公开**（2026-09-15 DOC-06 对齐 `SecurityConfig` 白名单；同邮箱 60s 限频 + 同 IP 每分钟 ≤3 次/每小时 ≤10 次） | `{ username, email(可空，传学号自动推导 {username}@bjtu.edu.cn), purpose }` | 发学号邮箱验证码（6 位 10 分钟有效） |
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
| POST | `/reviews/{id}/useful` | — | 「有用」切换（一人一票）。**准入 = 需学号邮箱认证 `verified=true`**（`@RequireVerified`，未认证返回 **`4031`** 并弹 `AuthSheet` 认证引导，入口不置灰）；**仅此一条，不受 §7.5 的 openid 双约束**（spec §7.23 第 2 条 / §7.8 第 3 条。*原「仅需登录」表述已于 2026-09-15 作废，为文档对齐代码*） |
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
| POST | `/feedback` | 公开 | 提交反馈（游客可；含举报/纠错/推荐菜品；**2026-09-13 起请求体增可选 `images`：字符串数组 ≤3 项 COS URL**，游客提交同样可带图；文本过 `msgSecCheck` v2 `scene=2`，`risky` 拦 400、`review` 落 `sec_state='review'`）。**`type` 写入白名单（2026-09-15 蓝图 v1 真源，spec §7.23 第 3 条）= `suggestion` / `add` / `error` / `report`**：`suggestion` 建议 / 问题（端上二级 `sub=idea` / `sub=problem`，**「系统 bug」归 `problem`，不升一级类型**）、`add` 新增菜品、`error` 纠错与申请下架（`relatedType=dish`）、`report` 举报（`relatedType=review`，必填 `relatedId`）；**`bug` / `other` 为历史遗留枚举位、无生产者、禁止新增**，非法值 400（仅查询白名单保留以筛存量） |

> 「我的反馈」接口 `GET /feedback/my` 已随反馈中心下线删除（2026-09-07）；进度追踪后续另做。

---

## 4. 图片上传与内容安检（2026-09-13 重构：UGC 配图链路 + 合规要求）

### 4.1 接口

| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| POST | `/upload/images` | JSON `{ fileId: string }`（**单张**，小程序 `wx.cloud.uploadFile` 产生的云存储 fileID，形如 `cloud://env.bucket/path`） | `{ url: string }`（该张 COS 永久 URL） | **UGC 配图上传（2026-09-13 新增；单张契约）**：后端经 tcb `batchdownloadfile` 从云开发云存储拉取 → `imgSecCheck` 送检（违规 code `87014` 返回 400）→ 转存 COS。多张配图由前端**逐张调用**本接口（每张独立送检转存，**单张失败该张返回 400、前端提示后跳过，不中断其余图片**）。需登录（游客亦可，无 `verified` 门槛）；评价 / 反馈提交前先逐张调本接口取 COS URL |
| POST | `/upload/image` | `file`（multipart，jpg/jpeg/png/webp） | `{ url, relativeUrl }` | **保留**：web 管理端菜品图 / H5 回退链路，不承载 UGC 配图（小程序端头像走微信云存储 `cloud://` 直存不经此端点）。**已纳入 `AdminTokenFilter` 口令守卫（2026-09-15 B4）**：不在 `permitAll` 白名单，`X-Admin-Token` 校验通过后置 `ROLE_ADMIN` 授权放行，口令缺失/无效 403 |

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
| GET | `/admin/dishes` | 菜品列表（分页 `PageResult<DishAdminVO>`，4 参统一形态 `{records,total,page,pageSize}`）。**2026-09-15 蓝图 v1（spec §7.23 第 4 条）：菜品无独立审核**——列表**不再返回 / 不再展示审核态**（Web 侧删除审核列与 `reject_reason` 回显） |
| POST | `/admin/dishes` | 新增菜品（**录入即生效**，`audit_status=approved`）。**食堂 / 档口随菜品 upsert（2026-09-15 蓝图 v1 原则 1）**，见下方契约 |
| PUT | `/admin/dishes/{id}` | 编辑（同上；`audit_status` 恒写 `approved`，后台无审核入口）。食堂 / 档口按名 upsert 同新增 |
| DELETE | `/admin/dishes/{id}` | 删除（级联清评价） |

**食堂 / 档口随菜品 upsert 契约（2026-09-15 蓝图 v1 原则 1，spec §7.23）**

> 前提不变：食堂 / 档口是**菜品属性字典**（无独立管理页、无删除，仅「新增 / 改名 / 列表查看」，见 §5.5）。本契约只解决「录入菜品时字典里还没有这个食堂 / 档口」的问题。

- `DishAdminReq` 新增可选字段：**`canteenName`**（食堂名）、**`stallName`**（档口名）；既有的 **`stallId`** 保留。
- 服务端解析顺序（同一事务内，`@Transactional`）：
  1. 传了 `stallId` 且该档口存在 → **沿用现状**（原校验「档口不存在」保留）；
  2. 未传 `stallId`（或为 `null`）而传了 `canteenName` / `stallName` → **按名 upsert**：
     - `canteen`：按 `name` 精确查 `canteen`；不存在则 **INSERT**（`sort_order` 默认 0，`created_by` 置空）后取回 `id`；
     - `stall`：按 `canteen_id` + `name` 查 `stall`；不存在则 **INSERT**（带 `canteen_id`）后取回 `id`；
     - 名称入参须 trim 后非空且长度 ≤64，非法 → `400`；
  3. 三者均未传 / 均无法解析 → **`400`「请指定所属档口」**（现有「档口不存在」文案按此细化）。
- upsert 落在 **Service 层**（`DishServiceImpl`），**禁止 Controller 直调 Mapper**；**随菜品 upsert 是字典项新增的唯一入口**（原 `/admin/canteens`、`/admin/stalls` 独立 POST 已随 2026-09-15 CT-02 删除，两字典端点仅剩 GET + PUT）。
- 返回：`DishAdminVO` / `DishVO` 的 `canteenName` / `stallName` / `canteenId` / `stallId` 语义不变。
- **验收**：新增菜品时填入一个字典中不存在的食堂名与档口名 → 保存成功且 `canteen` / `stall` 各新增 1 条，菜品归属正确；重复提交同名 → **不重复建档**（命中既有字典项）。

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
| GET | `/admin/feedbacks*` | 反馈列表（回复；**VO 含 `images`/`secState`，详情展示配图 ≤3 张**）。**2026-09-15 蓝图 v1（spec §7.23 第 5 条）：反馈是全项目唯一有待处理态的运营对象**，`status=pending/handled` 按 `type` 筛选（`suggestion`/`add`/`error`/`report`，历史 `bug`/`other` 可筛存量） |
| PUT | `/admin/feedbacks/{id}` | **处理反馈（唯一运营闭环）**：见下方契约 |

**反馈处理契约（2026-09-15 蓝图 v1 第 5 条，spec §7.23；2026-09-15 CT-03 以代码为准修订）**

- 结论二分（请求字段 **`outcome`**），管理端在弹窗中显式选择：
  - **采纳 / 已处理**（`outcome='handled'`，缺省值）→ `status='handled'`；
  - **不采纳 / 退回**（`outcome='rejected'`）→ `status='handled'`（反馈无「退回重提」链路，终态唯一），**`reject_reason` 必填**（1~200 字，纯空白视为未填写 → `400`），说明不采纳原因，**随回执一并向提交人展示**。
- 入参：**`{ reply: string, outcome: "handled"|"rejected", rejectReason?: string }`**——**`reply` 恒必填**（1~1000 字，纯空白视为未填写 → `400`；§7.16 第 2 条口径不变）；`outcome='rejected'` 时 `rejectReason` 必填，`outcome='handled'` 时不消费 `rejectReason`（保持 NULL）。**原 `{ reply?, rejectReason?, rejected? }` 旧描述作废（2026-09-15 CT-03）**。
- 回执投递不变：已认证提交人收站内通知 `feedback_handle`（含回执正文；不采纳时含不采纳原因），游客不投递、不阻塞（§7.8 第 4 条 / §0.1 匿名心智）。
- 落库：`user_feedback.reject_reason` 列**已落地**（`schema.sql` 幂等块 `add_feedback_reject_reason`），结论差异由 `reject_reason` 是否非空承载，无「待拍板」遗留。

### 5.5 基础数据维护
| 方法 | 路径 | 说明 |
|---|---|---|
| GET/PUT | `/admin/canteens`、`/admin/stalls` | 食堂/档口**筛选属性字典**——能力仅「列表查看 / 改名（编辑）」，**无 DELETE、无独立 POST**（2026-09-15 CT-02：独立新增端点已删；**新增仅随菜品录入按名 upsert 建档**，spec §7.23 原则 1，见 §5.2）；无 `status`/`auditStatus`/`rejectReason` 字段（Q-119 已 DROP） |
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

### 6.3 审核流（2026-09-15 蓝图 v1 校准，与 spec §7.8 / §7.21 / §7.23 对齐）
```
菜品：管理员经 /admin/dishes 录入/编辑 → audit_status=approved 且**直接生效**（管理员即权威，spec §7.8 第 1 条）；
      ★2026-09-15 蓝图 v1（§7.23 第 4 条）：菜品无独立审核——dish.audit_status / dish.reject_reason 退役为历史列，
        后台无审核入口（列表无审核列、详情无审核态与退回原因回显），客户端不出现「菜品审核」概念；
        学生端无菜品写接口（POST/PUT/DELETE /dishes 已于 2026-09-13 下线）
评价：先发后审、无 audit_status——机检 pass 即公开（is_hidden=0 AND sec_state='pass'）；sec_state='review' 进人工复核队列（PUT /admin/reviews/{id}/sec-state）；risky 提交即 400 拦截
反馈/举报（唯一运营闭环）：POST /feedback 公开提交（type ∈ suggestion/add/error/report）
     → 管理员 PUT /admin/feedbacks/{id} 处理：reply 恒必填；不采纳·退回（outcome=rejected）另 rejectReason 必填
     → status=handled + 站内通知回执（游客不投递）
     → 处理动作即实际的录入/修改/下架菜品（不得「一键转菜品」，须人工编辑确认，spec §7.13 第 1 条）
食堂/档口：无审核流；作为菜品属性字典随菜品 upsert（不存在即自动建档，spec §7.23 原则 1）
```

> 食堂/档口为筛选属性字典，无 `status`/`audit_status`/`reject_reason`（2026-09-14 Q-113 / Q-119 去实体化），不参与审核流。

---

## 7. 前端对接说明

- **base URL**：小程序经 `wx.cloud.callContainer` 访问（云托管），`context-path=/api`；web 管理端走公网/内网代理。
- **图片**：`relativeUrl` 用于存库（头像 / 后台菜品图），展示时前端拼完整前缀；**UGC 配图为 COS 完整 URL**（`review.images` / `user_feedback.images`，直接展示，无需拼前缀）。
- **分页**：统一读 `records`/`total`（`page`/`pageSize` 为经 `PageUtil.normalize` 归一化的实际生效值；原 `/activities` 裸数组例外已随该接口下线删除，2026-09-13）。
- **403 分级**：`code=403`（无权限）与 `code=4031`（未认证邮箱）前端需区分处理。

---

## 8. 差异登记（裁决以 project_spec.md 与最新拍板为准；代码侧差异列为待对齐项）

> **⚠️ 裁决口径（本节唯一有效，2026-09-15 修订，原「以代码为准」表述作废）**：本节是**待对齐项登记册**，不是裁决依据。
> - **规范 / 契约冲突一律以 `project_spec.md` 与最新拍板决议为准**（spec §0.4 / §0.5 执行口径、PR-02 硬原则）；**禁止据代码现状反向推翻文档**。
> - 「实际代码」列仅用于**标记代码尚未对齐文档的缺口**，对应行的处置方向恒为**改代码**（除非该差异已由新的用户拍板推翻文档，此时应先改文档再改代码）。
> - 任何人**不得**援引本节「代码与文档不一致」作为按代码实现的依据。

| 项 | spec 描述 | 实际代码（待对齐项） | 处置 |
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
| **评价「有用」点赞准入**（2026-09-15 蓝图 v1，spec §7.23 第 2 条） | 需 `verified=true`（未认证 `4031` + 弹 `AuthSheet`） | 代码**已符合**：`ReviewController.java:128` `@RequireVerified` | **代码不动，文档对齐代码**；原「仅需登录」表述（§7.8 第 3 条 / §7.19 第 4 条 / 本文档 §3.3）已同步作废 |
| **反馈处理 `reject_reason`**（2026-09-15 蓝图 v1，spec §7.23 第 5 条） | 结论「不采纳 / 退回」时 `rejectReason` 必填，随回执展示 | 代码**已实现并对齐**：`FeedbackHandleReq.outcome/rejectReason` 校验、`schema.sql` 幂等补列 `add_feedback_reject_reason`、`FeedbackServiceImpl` 结论派生与回执文案均已落地（见 §5.4） | 已对齐（线上生效待部署） |
| **食堂 / 档口随菜品 upsert**（2026-09-15 蓝图 v1，spec §7.23 原则 1） | `POST`/`PUT /admin/dishes` 支持按 `canteenName` / `stallName` upsert | 代码**已实现并对齐**：`DishAdminReq` 增 `stallName` / `canteenName`，`DishServiceImpl` 在 Service 层按名解析 / 建档（同名复用，新建档口时食堂名必有效） | 已对齐 |
| **菜品无独立审核**（2026-09-15 蓝图 v1，spec §7.23 第 4 条） | `dish.audit_status` / `dish.reject_reason` 退役：后台无审核入口、端上无「菜品审核」概念；管理员录入 / 编辑即 `approved` 并生效 | 后端**已符合**（`DishServiceImpl` 新增 / 编辑均写 `AUDIT_APPROVED`，`/admin/audit/**` 已删）；**Web / 小程序已对齐**（`DishManageView` 审核列与 `DishDetailView` 审核态回显已删；小程序 `AuditStatus` 已无引用） | **收尾**：存量 `audit_status` 由 `normalize_dish_audit_status.sql` 一次性归一（用户执行） |
| **管理端无密码体系**（2026-09-15 蓝图 v1，spec §7.23） | 无账号 / 无密码 / 无 BCrypt 登录校验 / 无 `SUPER_ADMIN`；`user.password` 为历史兼容列 | **已完全符合**（2026-09-15 优化 Loop：`DataInitializer` 已整体删除，种子数据以 `db/seed_data.sql` 为唯一基线；`/admin/**` 走 `AdminTokenFilter`）；BCrypt 仅保留于 `email_verification_code.code_hash` | 已对齐（`SecurityConfig` / `SwaggerConfig` 等处历史注释为待清理残留） |

---

## 9. 已知技术债 / 建议
- ~~`BroadcastAdminController` 用 `@RequestBody Broadcast` 直收无 `@Valid`/枚举校验~~ → 已随 2026-09-13 活动/公告全链路下线删除（该 Controller 不存在）
- ~~验证码限频无 IP 维度~~ → **已解决**（P3/BE-105：`/auth/email-code` 接入 `IpRateLimiter`，同 IP 每分钟 ≤3 次、每小时 ≤10 次，2026-09-14 核实）
- ~~前端裸 hex（find confirmColor）~~ → **已解决**（已收敛为 `client/src/theme/tokens.ts` 登记常量 `MODAL_CONFIRM_DANGER_COLOR` / `MODAL_CONFIRM_PRIMARY_COLOR`，满足 spec §4.9 裸 hex 红线；原 webview progressbar 例外已随 `web-view` 退出小程序移除）
- ~~`SecurityConfig` 白名单残留 `/lists/share/**`~~ → **已解决**（白名单已清理，`PUBLIC_ANY_METHOD` / `PUBLIC_GET_PREFIXES` 无美食清单残留条目，2026-09-14 核实）
- ~~`NotificationController` 直调 `NotificationMapper`（分页/已读/未读计数在 Controller 内完成）→ 违反「Controller 不得直调 Mapper」分层红线（spec §2）~~ → **已解决**（P3/ARCH-008：查询/计数/已读逻辑下沉 `NotificationService`，Controller 不再注入 Mapper，2026-09 核实）
- ~~通知接口用 `hasRole('STUDENT')` 而非 verified 口径~~ → **已解决**（`@RequireVerified` 切面已补齐，2026-09 核实）
