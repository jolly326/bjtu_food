# 接口设计总览（食在交大 bjtu_food）

> 本文档以**当前代码为准**（`server/src/main/java/com/bjtufood/**`），描述后端全部 REST 接口契约、认证模型、错误码与数据流转。
> 供前端（client/web）联调与后续快速上手。若与 `project_spec.md` 有出入，以本文档（代码）为准。

## 1. 通用约定

### 1.1 基础信息
- **Context Path**：`/api`（所有接口实际 URL 前缀为 `/api/xxx`）
- **数据交换**：JSON；上传用 `multipart/form-data`
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
| 游客态 | 微信静默登录建号，`verified=0`；可浏览公开内容，不可写用户内容（评价 / 菜品贡献等 UGC） |
| 已认证 | 绑定 `@bjtu.edu.cn` 邮箱（验证码）后 `verified=1`，解锁写操作 |
| 角色 | `student`（默认）/ `admin` / `super_admin` |
| 状态 | `active` / `disabled` / `deleted` |

### 1.3 错误码
| code | 含义 | 前端处理 |
|---|---|---|
| 200 | 成功 | — |
| 400 | 参数/业务校验失败（含资源不存在） | 提示 `message` |
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
| GET | `/dishes/promotions` | — | `List<DishVO>` | 限时活动 TOP4（`FIND_IN_SET('promotion', tags)`） |
| GET | `/dishes/hot-search` | — | `List<HotSearchVO>` | 热搜 TOP10 |
| GET | `/dishes/rising` | — | `List<DishVO>` | 新晋黑马 TOP10 |
| GET | `/dishes` | `DishQueryReq`（keyword/canteenId/stallId/categoryId/tag/minPrice/maxPrice/spiceLevel/sortBy/sortOrder/page/pageSize/excludeIds） | `IPage<DishVO>` | 菜品分页搜索/筛选/排序 |
| GET | `/dishes/recommend` | `page`/`pageSize`/`excludeIds` | `IPage<DishVO>` | 猜你喜欢（基于浏览足迹个性化） |
| GET | `/dishes/{id}` | `id` | `DishDetailVO` | 详情（登录时含 isFavorited/hasReviewed） |

### 2.3 评价（ReviewController）
| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| GET | `/reviews` | `dishId`/`stallId`/`canteenId`（三选一，至少传其一）/page/pageSize/sort(latest/useful) | `PageResult<ReviewVO>` | 评价列表（仅未隐藏） |
| GET | `/dishes/{dishId}/reviews` | page/pageSize/sort | `PageResult<ReviewVO>` | 菜品评价 |

> 评价全量纯文本（UGC 图片已下线），`isWithImage` 参数已不存在（2026-09 契约清理）。

### 2.5 内容/活动/品类（公开）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/categories` | 首页品类滚轮（enabled，sort_order 升序） |
| GET | `/broadcasts` | 首页广播 ticker（enabled） |
| GET | `/activities` | 最新活动（公众号文章卡片；返回**裸 List**，前端需按数组解析） |
| GET | `/images/**` | 静态图片资源 |

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

### 3.2 菜品发布（邮箱认证）
| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| POST | `/dishes` | `DishPublishReq` | 学生发布菜品（created_by=当前用户，pending） |
| PUT | `/dishes/{id}` | `DishPublishReq` | 编辑重提（仅本人） |
| DELETE | `/dishes/{id}` | — | 删本人菜品 |
| POST | `/dishes/{id}/view` | — | 浏览量+1（同时记录浏览足迹） |

> `DishPublishReq` 校验：`name≤64`、`price 0~999900`（分）、`description≤512`、`tags≤128`。

### 3.3 评价（邮箱认证）
| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| POST | `/reviews` | `ReviewReq{dishId,rating,content}` | 提交评价（每菜一人一评，纯文本无图） |
| DELETE | `/reviews/{id}` | — | 删本人评价（级联清理 useful） |
| POST | `/reviews/{id}/useful` | — | 「有用」切换（一人一票） |
| GET | `/my/reviews` | page/pageSize | 我的评价（`@RequireVerified` + `@PreAuthorize("hasRole('STUDENT')")`，返回 **`PageResult{list,total}`**） |

> 评价不支持修改（`PUT /reviews/{id}` 与契约路径 `DELETE /my/reviews/{id}` 均不存在，2026-09 契约清理）；改评 = 删除后重提（一人一菜一评由 `uk_review_user_dish` 保证）。
>
> **`GET /my/reviews` 契约注记（2026-09-13 核实，AUD-BE-06 / AUD-BE-07）**：① 返回形态为 `PageResult{list, total}`（**非** IPage 的 `{records,total,page,pageSize}`，见 §1.4 注记），前端经 `recordsOf()` 双形态兼容；② 除 `@RequireVerified`（切面按 `user.verified` 实时判定）外，另挂方法级 `@PreAuthorize("hasRole('STUDENT')")` 纵深防御——小程序端用户默认 `STUDENT` 角色，不影响正常调用；该双重校验口径与文档描述一致（`ReviewController.java:70-80`）。

### 3.5 通知（需邮箱认证 `@RequireVerified`）
> 通知 `type`：`dish_audit`（菜品审核结果）、`feedback_handle`（反馈/举报处理结果回执，仅已认证提交人可收到）。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/my/notifications` | 消息列表（isRead/page/pageSize） |
| GET | `/my/notifications/unread-count` | 未读总数（红点） |
| PUT | `/my/notifications/{id}/read` | 单条已读 |

> 全部已读接口 `PUT /my/notifications/read-all` 不存在（2026-09 契约清理）；已读仅单条操作。

### 3.6 反馈
| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/feedback` | 公开 | 提交反馈（游客可；含举报/纠错/推荐菜品，纯文本免图） |

> 「我的反馈」接口 `GET /feedback/my` 已随反馈中心下线删除（2026-09-07）；进度追踪后续另做。

---

## 4. 图片上传（需登录）

| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| POST | `/upload/image` | `file`（multipart，jpg/jpeg/png/webp） | `{ url, relativeUrl }` | 上传头像/菜品图（UGC 评价/反馈已全量纯文本，不再传图） |

- 校验：扩展名白名单 + magic number + UUID 重命名 + 失败清理
- 返回 `data.url`（完整访问）、`data.relativeUrl`（数据库保存的相对路径）

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
| GET | `/admin/reviews` | 评价审核列表（isHidden/userId 过滤） |
| PUT | `/admin/reviews/{id}/hide` | 隐藏评价 |
| DELETE | `/admin/reviews/{id}` | 删评价（清理 useful 孤儿） |
| GET | `/admin/feedbacks*` | 反馈审核（回复） |

### 5.5 基础数据维护
| 方法 | 路径 | 说明 |
|---|---|---|
| CRUD | `/admin/canteens`、`/admin/stalls` | 食堂/档口 |
| CRUD | `/admin/categories` | 品类（sortOrder 非数字返回 400） |
| CRUD | `/admin/broadcasts` | 广播 |
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
- **图片**：`relativeUrl` 用于存库，展示时前端拼完整前缀。
- **分页**：统一读 `records`/`total`；`/activities` 例外返回裸数组。
- **403 分级**：`code=403`（无权限）与 `code=4031`（未认证邮箱）前端需区分处理。

---

## 8. 与 project_spec.md 的差异（以代码为准）

| 项 | spec 描述 | 实际代码 | 建议 |
|---|---|---|---|
| 页面数量 | 9 页（旧） | 11 页（pages.json：主包 3 + 分包 detail/me/activity，共 11 页） | 已对齐（2026-09 spec §2.1 已校准为 11） |
| 4031 错误码 | 禁止非标码（例外豁免制） | 使用 4031 细分 | 已在 spec §3 登记豁免（2026-08-19） |
| view_log | 要求唯一键+upsert | 无唯一键，应用层 upsert | 已实现写入，唯一键可选增强 |
| `GET /my/reviews` 分页形态 | IPage `{records,total,...}`（§1.4 通用） | `PageResult{list,total}`（`ReviewController.java:79`） | 已在 §1.4 / §3.3 加注，前端 `recordsOf()` 双形态兼容 |
| `GET /my/reviews` 权限 | 仅 `@RequireVerified` | 额外 `@PreAuthorize("hasRole('STUDENT')")`（`ReviewController.java:71`） | 已在 §3.3 加注（不影响小程序，默认 STUDENT） |

---

## 9. 已知技术债 / 建议
- `BroadcastAdminController` 用 `@RequestBody Broadcast` 直收无 `@Valid`/枚举校验 → 建议补 DTO+校验
- 验证码限频无 IP 维度 → 建议补 IP 维度 + 单日总量限制
- 前端裸 hex（webview progressbar、find confirmColor）→ 建议登记 `uni.scss` token
- `SecurityConfig` 白名单残留 `/lists/share/**`（美食清单模块已移除，无对应 Controller）→ 建议清理白名单条目
- `NotificationController` 直调 `NotificationMapper`（分页/已读/未读计数在 Controller 内完成）→ 违反「Controller 不得直调 Mapper」分层红线（spec §2），建议下沉至 `NotificationService`
- ~~通知接口用 `hasRole('STUDENT')` 而非 verified 口径~~ → **已解决**（`@RequireVerified` 切面已补齐，2026-09 核实）
