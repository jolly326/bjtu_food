# 接口设计总览（食在交大 bjtu_food）

> 本文档以**当前代码为准**（`server/src/main/java/com/bjtufood/**`），描述后端全部 REST 接口契约、认证模型、错误码与数据流转。
> 供前端（client/web）联调与后续快速上手。若与 `project_spec.md` 有出入，以本文档（代码）为准。

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
| 角色 | `student`（默认）/ `admin` / `super_admin` |
| 状态 | `active` / `disabled` / `deleted` |

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
- 分页返回结构为 MyBatis-Plus `IPage` 序列化：`{ records: [], total, page, pageSize, ... }`
- **注记（2026-09 核实）**：部分接口经 `common/result/PageResult` 归一，形态为 **`PageResult{ list, total }`**（字段名 `list` 而非 `records`，且不含 `page/pageSize`）——如 `GET /my/reviews`（`ReviewController.java:79` `PageResult.of(result.getRecords(), result.getTotal())`）、`/admin/feedbacks*`、`/admin/dishes` 等。前端 `api` 层以 `recordsOf()` 双形态兼容（`records` / `list` 二选一），消费方不直接假设字段名。

---

## 2. 公开接口（无需登录）

### 2.1 食堂与档口（CanteenController）
| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| GET | `/canteens` | `lat`/`lng`（可选） | `List<CanteenInfoVO>` | open 食堂；带经纬度按距离升序 |
| GET | `/canteens/all` | — | `List<食堂含档口树>` | 一次性渲染食堂+档口 |

### 2.2 菜品（DishController）
| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| GET | `/dishes/hot` | `lat`/`lng`/`limit`(可选) | `List<DishVO>` | 热门菜品 |
| GET | `/dishes/new` | — | `List<DishVO>` | 今日上新 TOP8 |
| GET | `/dishes/promotions` | — | `List<DishVO>` | 限时促销 TOP4（`FIND_IN_SET('promotion', tags)`；指菜品促销标签，与已下线的 activity 活动模块无关） |
| GET | `/dishes/hot-search` | — | `List<HotSearchVO>` | 热搜 TOP10 |
| GET | `/dishes/rising` | — | `List<DishVO>` | 新晋黑马 TOP10 |
| GET | `/dishes` | `DishQueryReq`（keyword/canteenId/stallId/categoryId/tag/minPrice/maxPrice/spiceLevel/sortBy/sortOrder/page/pageSize/excludeIds） | `IPage<DishVO>` | 菜品分页搜索/筛选/排序（keyword 同时命中 name 与 alias 别名） |
| GET | `/dishes/recommend` | `page`/`pageSize`/`excludeIds` | `IPage<DishVO>` | 猜你喜欢（基于浏览足迹个性化） |
| GET | `/dishes/{id}` | `id` | `DishDetailVO` | 详情（登录时含 hasReviewed） |

### 2.3 评价（ReviewController）
| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| GET | `/reviews` | `dishId`/`stallId`/`canteenId`（三选一，至少传其一）/page/pageSize/sort(latest/useful) | `PageResult<ReviewVO>` | 评价列表（仅未隐藏） |
| GET | `/dishes/{dishId}/reviews` | page/pageSize/sort | `PageResult<ReviewVO>` | 菜品评价 |

> **`ReviewVO` 字段（2026-09-13 随 UGC 配图恢复扩充）**：新增 `images`（字符串数组，≤3 项 COS URL，无图返回空数组）与 `secState`（**三态** `pass`/`review`/`rejected`——`review`=机检待人工复核、`rejected`=人工驳回，两者均对非作者不可见；列表 / 详情接口仅返回 `sec_state='pass'` 或本人评价，后端过滤，前端不兜底）。历史注记（2026-09 契约清理）「`isWithImage` 参数已不存在」维持有效：`isWithImage` 筛选参数不恢复，配图随评价正文整体展示。

### 2.5 内容/品类（公开）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/categories` | 首页品类滚轮（enabled，sort_order 升序） |
| GET | `/images/**` | 静态图片资源 |

> `GET /broadcasts`、`GET /activities` 及管理端 `/admin/broadcasts`、`/admin/activities` 系列接口已于 2026-09-13 随活动/公告（broadcast）全链路下线删除，接口与实体均不存在（spec §0.5 已登记）。

---

## 3. 需登录接口（学生端）

### 3.1 认证与账号（AuthController / UserController）
| 方法 | 路径 | 认证 | 参数 | 说明 |
|---|---|---|---|---|
| POST | `/auth/wechat-login` | 公开 | `{ code }` | 微信静默登录，新 openid 自动建号（verified=0），返回 token |
| POST | `/auth/email-code` | 登录 | `{ username, email(可空，传学号自动推导 {username}@bjtu.edu.cn), purpose }` | 发学号邮箱验证码（60s 限频，6 位 10 分钟有效） |
| POST | `/auth/verify-email` | 登录 | `{ code }` | 验证码认证，绑定邮箱，verified→1，返回新 token |
| GET | `/auth/profile` | 登录 | — | 用户资料（**不含 openid**） |
| PUT | `/auth/profile` | 登录 | `{ nickname, avatar }` | 更新资料（avatar 仅允许站内 `/images/`、`/uploads/`、`cloud://`） |
| PUT | `/auth/password` | 登录 | `{ oldPassword, newPassword }` | 改密（管理员用） |
| DELETE | `/auth/account` | 登录 | — | **注销账号（匿名化，非物理删除）**：nickname→'已注销用户'、openid/unionid→NULL（解绑，允许重新登录建新游客号）、status→'deleted'；评价/反馈保留但去身份化；token 立即失效（TokenBlacklist token+userId 双维度）；幂等（重复调用 400「账号已注销」） |

### 3.2 菜品埋点（登录即可，非写接口）
| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| POST | `/dishes/{id}/view` | — | 浏览量+1（同时记录浏览足迹） |

> **学生端菜品写接口已于 2026-09-13 全部下线**：`POST /dishes`（学生发布）、`PUT /dishes/{id}`（编辑重提）、`DELETE /dishes/{id}`（删本人菜品）三者均已从 `DishController` / `DishService` / `DishServiceImpl` 删除，接口不存在，客户端零消费（`api/dish.ts` 的 `deleteDish`、详情页长按删除链路同步移除）。`DishPublishReq` DTO 已随之删除。菜品由管理员经 `/admin/dishes/**` 录入，学生新增菜品需求走反馈 `add` 类型（`POST /feedback`）由后台处理。
>
> 本表 `POST /dishes/{id}/view` 为浏览埋点，保留；全部 `GET /dishes*` 只读接口保留。

### 3.3 评价（邮箱认证）
| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| POST | `/reviews` | `ReviewReq{dishId,rating,content,images?}` | 提交评价（每菜一人一评；`images` 可选字符串数组，≤3 项 COS URL） |
| DELETE | `/reviews/{id}` | — | 删本人评价（级联清理 useful） |
| POST | `/reviews/{id}/useful` | — | 「有用」切换（一人一票） |
| GET | `/my/reviews` | page/pageSize | 我的评价（`@RequireVerified` + `@PreAuthorize("hasRole('STUDENT')")`，返回 **`PageResult{list,total}`**） |

> 评价不支持修改（`PUT /reviews/{id}` 与契约路径 `DELETE /my/reviews/{id}` 均不存在，2026-09 契约清理）；改评 = 删除后重提（一人一菜一评由 `uk_review_user_dish` 保证）。
>
> **安检（2026-09-13）**：提交时文本过 `msgSecCheck` v2（`scene=2` 评价场景）；`suggest=pass` 正常落库，`suggest=review` 正常落库且 `sec_state='review'`（对非作者不可见，进管理后台复核队列），`suggest=risky` 返回 `400` 拦截。配图须先经 `POST /upload/images` 逐张安检转存（单张接口，违规该张 400、前端跳过不中断），再把返回的 COS URL 随 `images` 提交。
>
> **`GET /my/reviews` 契约注记（2026-09-13 核实，AUD-BE-06 / AUD-BE-07）**：① 返回形态为 `PageResult{list, total}`（**非** IPage 的 `{records,total,page,pageSize}`，见 §1.4 注记），前端经 `recordsOf()` 双形态兼容；② 除 `@RequireVerified`（切面按 `user.verified` 实时判定）外，另挂方法级 `@PreAuthorize("hasRole('STUDENT')")` 纵深防御——小程序端用户默认 `STUDENT` 角色，不影响正常调用；该双重校验口径与文档描述一致（`ReviewController.java:70-80`）。

### 3.5 通知（需邮箱认证 `@RequireVerified`）
> 通知 `type`：`dish_audit`（菜品审核结果）、`feedback_handle`（反馈/举报处理结果回执，仅已认证提交人可收到）。

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

## 5. 管理后台接口（需 ADMIN / SUPER_ADMIN）

> 均需 `Authorization: Bearer <admin token>`，并有方法级 `@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")` 纵深防御。

### 5.1 数据看板与统计
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/dashboard` | 数据看板总览（range=week/month/all → 7/30/90 天） |
| GET | `/admin/stats/**` | 统计（热门排行/趋势） |

### 5.2 菜品管理（DishAdminController）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/dishes` | 菜品列表（**分页 IPage**，`{records,total}`） |
| POST | `/admin/dishes` | 新增菜品（默认 approved） |
| PUT | `/admin/dishes/{id}` | 编辑 |
| DELETE | `/admin/dishes/{id}` | 删除（级联清评价） |

### 5.3 用户与管理员管理
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/users` | 用户列表（role/status 过滤） |
| PUT | `/admin/users/{id}/status` | 启停用户 |
| PUT | `/admin/users/{id}/role` | 改角色 |
| GET/POST/PUT/DELETE | `/admin/admins/*` | 管理员管理（仅 SUPER_ADMIN） |

### 5.4 审核与内容治理
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/reviews` | 评价审核列表（isHidden/userId 过滤；**2026-09-13 起支持 `secState` 过滤，VO 含 `images`/`secState`**） |
| PUT | `/admin/reviews/{id}/sec-state` | **评价安检复核（2026-09-13 新增）**：入参 `{ state: "pass" \| "rejected" }`——放行（落 `sec_state='pass'`，恢复公开展示）/ 驳回（落 `sec_state='rejected'`，持续对非作者不可见，作者侧呈现未过审态）。`review` 态仅由机检写入，本接口不接受（管理端只写人工结论） |
| PUT | `/admin/reviews/{id}/hide` | 隐藏评价 |
| DELETE | `/admin/reviews/{id}` | 删评价（清理 useful 孤儿） |
| GET | `/admin/feedbacks*` | 反馈审核（回复；**VO 含 `images`/`secState`，详情展示配图 ≤3 张**） |

### 5.5 基础数据维护
| 方法 | 路径 | 说明 |
|---|---|---|
| CRUD | `/admin/canteens`、`/admin/stalls` | 食堂/档口 |
| CRUD | `/admin/categories` | 品类（sortOrder 非数字返回 400） |
| GET | `/admin/operation-logs` | 操作日志（只读） |
| GET | `/admin/audit/*` | 待审内容 |

---

## 6. 关键数据流转

### 6.1 评价 → 评分聚合
```
POST /reviews → ReviewSubmittedEvent → RatingUpdateListener(@Async AFTER_COMMIT)
             → recalcAvgRating(dishId) 更新 dish.avg_rating / rating_count
```
- 聚合异步执行，失败仅记 `[ALERT]` 日志不阻塞提交

### 6.2 浏览 → 足迹 → 猜你喜欢
```
GET /dishes/{id} → addViewCount(+1) + recordDishView(userId,dishId) upsert 足迹
GET /dishes/recommend → recentViewedDishIds → 同 stall/tags 加权排序
```

### 6.3 审核流
```
学生提交(UGC) → audit_status=pending → 管理员 approve/reject（reject 写 reject_reason）
Dish/Stall/Canteen：学生写走直接发布（菜品/档口纠错由反馈 error 类型承载），均需审核
```

---

## 7. 前端对接说明

- **base URL**：小程序经 `wx.cloud.callContainer` 访问（云托管），`context-path=/api`；web 管理端走公网/内网代理。
- **图片**：`relativeUrl` 用于存库（头像 / 后台菜品图），展示时前端拼完整前缀；**UGC 配图为 COS 完整 URL**（`review.images` / `user_feedback.images`，直接展示，无需拼前缀）。
- **分页**：统一读 `records`/`total`（原 `/activities` 裸数组例外已随该接口下线删除，2026-09-13）。
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
| `GET /my/reviews` 分页形态 | IPage `{records,total,...}`（§1.4 通用） | `PageResult{list,total}`（`ReviewController.java:79`） | 已在 §1.4 / §3.3 加注，前端 `recordsOf()` 双形态兼容 |
| `GET /my/reviews` 权限 | 仅 `@RequireVerified` | 额外 `@PreAuthorize("hasRole('STUDENT')")`（`ReviewController.java:71`） | 已在 §3.3 加注（不影响小程序，默认 STUDENT） |
| `POST /dishes`（学生发布菜品） | 曾列为 UGC 写路径 | 接口已删除（2026-09-13 下线） | 已在 §3.2 加注，spec §5 已同步 |
| `PUT`/`DELETE /dishes/{id}`（学生编辑·删除本人菜品） | 曾列为学生 UGC 写路径 | 接口已删除（2026-09-13 下线，`DishPublishReq` 一并删除） | 已在 §3.2 加注，spec §0.1/§0.3/§3/§5/§5.y/§5.z 已同步为学生端无菜品写接口 |

---

## 9. 已知技术债 / 建议
- ~~`BroadcastAdminController` 用 `@RequestBody Broadcast` 直收无 `@Valid`/枚举校验~~ → 已随 2026-09-13 活动/公告全链路下线删除（该 Controller 不存在）
- 验证码限频无 IP 维度 → 建议补 IP 维度 + 单日总量限制
- 前端裸 hex（find confirmColor）→ 建议登记 token（原 webview progressbar 例外已随 `web-view` 退出小程序移除）
- `SecurityConfig` 白名单残留 `/lists/share/**`（美食清单模块已移除，无对应 Controller）→ 建议清理白名单条目
- ~~`NotificationController` 直调 `NotificationMapper`（分页/已读/未读计数在 Controller 内完成）→ 违反「Controller 不得直调 Mapper」分层红线（spec §2）~~ → **已解决**（P3/ARCH-008：查询/计数/已读逻辑下沉 `NotificationService`，Controller 不再注入 Mapper，2026-09 核实）
- ~~通知接口用 `hasRole('STUDENT')` 而非 verified 口径~~ → **已解决**（`@RequireVerified` 切面已补齐，2026-09 核实）
