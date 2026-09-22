# 系统通知（处理回执）（A-12）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔐 认证**（需学号邮箱认证）
> 返回：[功能总览](./README.md)

## 干什么

接收**管理员处理反馈/举报后的回执**（仅已认证用户）。当前只有一种通知类型 `feedback_handle`：管理员处理反馈时投递，正文含回复内容（不采纳时含不采纳原因）。游客提交的反馈不投递通知（不阻塞处理）。

## UI

> 📐 页面 UI 设计稿已拆出 → [client-系统通知.md（docs/ui）](../ui/client-系统通知.md)（2026-09-22 拆分；**UI 口径以该文件为唯一真源**）

## 操作

1. 进页 → 拉取通知列表（默认全部，可筛未读）→ 未读项有视觉标记。
2. 点开某条 → 调用「单条已读」→ 视觉转为已读。
3. 点「全部已读」→ 一次性把全部未读置为已读（幂等，返回本次置读条数）。
4. 无通知时展示空态。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/my/notifications` | 🔐 认证（`@RequireVerified` + `hasRole('STUDENT')`） | 我的通知列表（倒序，支持 `isRead` 过滤） |
| GET | `/my/notifications/unread-count` | 同上 | 未读总数（驱动红点） |
| PUT | `/my/notifications/{id}/read` | 同上 | 单条标记已读（带归属校验） |
| PUT | `/my/notifications/read-all` | 同上 | 全部已读（**幂等**） |

## 字段

### 请求

| 接口 | 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|---|
| `GET /my/notifications` | `isRead` | number | 否 | 已读过滤：`0`=仅未读 / `1`=仅已读；**不传 = 全部** |
| `GET /my/notifications` | `page` | number | 否 | 页码，默认 1 |
| `GET /my/notifications` | `pageSize` | number | 否 | 每页条数，默认 20 |
| `GET /my/notifications/unread-count` | — | — | — | 无参数 |
| `PUT /my/notifications/{id}/read` | `id` | number | **是** | 通知 ID（路径参数），无请求体 |
| `PUT /my/notifications/read-all` | — | — | — | 无参数、无请求体 |

### 响应 · `GET /my/notifications`（`data` = `PageResult<NotificationVO>`）

**分页壳字段**：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | NotificationVO[] | 当前页通知行数组（端上以它为准） |
| `total` | number | 该用户通知总条数 |
| `page` | number | 服务端归一化后的实际页码 |
| `pageSize` | number | 服务端归一化后的实际每页条数 |
| `list` | NotificationVO[] | 过渡期兼容字段，恒等于 `records`，新代码勿用 |

`NotificationVO` 单行字段：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 通知 ID |
| `type` | string | 通知类型：现仅 `feedback_handle`=反馈处理回执；`dish_audit` 为**仅存量兼容**类型（不再产生新通知，历史数据仍可读）；端上对未知类型按容错处理（不跳转、不崩溃） |
| `title` | string | 通知标题 |
| `content` | string | 通知正文（回执内容；不采纳时含不采纳原因） |
| `relatedId` | number | 关联对象 ID（`feedback_handle` 场景 = **反馈 ID**） |
| `isRead` | number | 是否已读：`0`=未读 / `1`=已读 |
| `createdAt` | string | 通知生成时间 |

### 响应 · `GET /my/notifications/unread-count`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `count` | number | 未读通知总数（端上读它决定是否显示红点） |

### 响应 · `PUT /my/notifications/{id}/read`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `data` | null | 无载荷；成功即 200 |

### 响应 · `PUT /my/notifications/read-all`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `data` | number | **本次被置为已读的条数**；无未读时为 `0`（幂等，重复调用结果一致） |

### 错误码

| code | 含义 | 中文解释 |
|---|---|---|
| 4031 | 邮箱未认证 | 弹 `AuthSheet` 认证引导 |
| 403 / 400 | 归属不符 / 通知不存在 | 只能操作自己的通知 |

## 数据（读写）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `notification` | SELECT / UPDATE `is_read` | 通知列表、红点计数、单条与全部已读 |
| `notification` | INSERT（由反馈处理触发） | 管理员处理反馈时异步写入 `feedback_handle` 回执；游客提交不投递 |
