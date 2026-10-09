# client/my — 当前用户的自有资源

**归属**：`ReviewController`（我的评价）· `NotificationController`（站内消息）
**通用约定**见 [api/README.md](../README.md)

> 覆盖 `/my/*` 下全部端点。这些资源**一律按 JWT 中的当前用户过滤**，端上不传任何用户标识。

---

## GET /my/reviews

**鉴权**：🔐 认证 ｜ **用途**：当前用户的全部有效评价

### Query 参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 20 |
| `dishId` | number | 否 | **按菜品过滤**（**「我的评价」页**按菜筛本人评价；**详情页不调用本端点** —— 详情页无「已评价」判定，见 [A3 菜品详情](../../func/client/A-浏览与发现/A3-菜品详情.md)） |

> 用户身份从 JWT 取，**端上不传 userId**（数据隔离红线）。

### 响应 `data` = `PageResult<MyReviewVO>`

`MyReviewVO` 共 **7 字段**：

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 评价 ID |
| `dishId` | number | 否 | 关联菜品 ID |
| `dishName` | string | 否 | 关联菜品名（联表补齐） |
| `rating` | number | 否 | 评分 1~5 |
| `content` | string | 否 | 正文（空串兜底） |
| `images` | string[] | 否 | 配图绝对 URL，≤3 张；恒为数组 |
| `createdAt` | string | 否 | 发表时间 |

> **本人视角不重复下发** `userId` / `userNickname` / `userAvatar`（三者恒等于本人，零信息）。
> 与公开视角 `ReviewVO`（8 字段，见 [dishes.md](./dishes.md#get-dishesidreviews)）**MUST 是两个类型**，端上不得复用单一 interface。

### 备注
- **排序唯一 = 时间倒序**，与公开列表同口径。
- 仅返回 `is_hidden = 0` 的评价 —— 被管理员隐藏的评价**对作者本人也不返回**。
- **不新增专用 `exists` 端点**：详情页按「**无写前判定**」设计（[A3 菜品详情](../../func/client/A-浏览与发现/A3-菜品详情.md)），**不查**「我是否已评价该菜」；本端点的 `dishId` 仅用于**「我的评价」页**按菜筛选。

### 错误码
| code | 条件 |
|---|---|
| **401** | 未登录 / token 无效 |
| **4031** | 邮箱未认证（本端点为**认证级** UGC 能力） |
| 500 | 服务器异常 |

---

## GET /my/notifications

**鉴权**：🔑 需登录（游客态亦可）｜ **用途**：站内消息列表

### Query 参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `isRead` | boolean | 否 | `true` 仅已读 / `false` 仅未读；**不传 = 全部**（端上当前恒不传） |
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 20 |

### 响应 `data` = `PageResult<NotificationVO>`

`NotificationVO` 共 **5 字段**（含 `id`；`type` / `related_id` **不出参** —— 与 [schema/notification.md](../../schema/notification.md) 一致）：

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 通知 ID |
| `title` | string | 否 | 通知标题 |
| `content` | string | 否 | 正文（处理回执内容） |
| `isRead` | boolean | 否 | `true` 已读 / `false` 未读 |
| `createdAt` | string | 否 | 生成时间 |

> **排序 = 时间倒序**。
> **出参字段**：`id` / `title` / `content` / `isRead` / `createdAt`；通知卡不按类型分支、不做类型相关跳转。
> **登录级能力**：游客与已认证用户同权可用；游客提交的反馈 / 举报 / 菜品问题反馈同样收到回执。

---

## GET /my/notifications/unread-count

**鉴权**：🔑 需登录 ｜ **用途**：未读总数（驱动红点）

### 请求参数
无。

### 响应 `data` = `UnreadCountVO`
| 字段 | 类型 | 说明 |
|---|---|---|
| `count` | number | 未读通知总数 |

> **轻量性能端点** —— 避免为驱动一个红点而拉回整页 `records`。
> 端上由 store 统一持有，做 in-flight 合并 + 短 TTL 去重；本地已读操作后**强制刷新**。

---

## PUT /my/notifications/{id}/read

**鉴权**：🔑 需登录 ｜ **用途**：单条标记已读

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | **是** | 通知 ID；无请求体 |

### 响应 `data`
`null`（成功即 `code=200`）

### 错误码
| code | 条件 |
|---|---|
| **200** | **幂等成功** —— 通知**不存在或非本人**均**静默成功**（不暴露他人通知存在性，端上无需分支） |
| 401 | 未登录 / token 无效 |

---

## PUT /my/notifications/read-all

**鉴权**：🔑 需登录 ｜ **用途**：全部标记已读（幂等）

### 请求参数
无参数、无请求体。

### 响应 `data`
`null`（成功即 `code=200`，**幂等**：重复调用结果一致）

### 备注
**路径风格**：`read-all` 为**动作式子路径**（非资源化的 `PUT /my/notifications/read-status`）—— 与同族的 `/{id}/read` 同构，该端点为幂等批量动作、无请求体，动作式命名歧义更小。
