# 系统通知（处理回执）（A-11）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔐 认证**（需学号邮箱认证）
> 返回：[功能总览](./README.md)

## 介绍

接收**管理员处理反馈/举报、菜品纠错后的回执**（仅已认证用户）。服务端内部以**类型键**区分来源（`feedback_handle`=反馈处理回执 / `correction_handle`=菜品信息纠错回执，采纳/拒绝均投递），但**类型键不出参**——通知卡只渲染「标题 + 正文 + 时间 + 未读态」，**不按类型分支、不做类型相关跳转**（将来要做「按类型跳转」须先由 UI 文档定义交互，再以 `targetType` / `targetId` 形式扩字段）。管理员处理反馈或菜品纠错时投递，正文含回复内容（不采纳时含不采纳原因）。游客提交的反馈/纠错不投递通知（不阻塞处理）。进页拉取通知列表（默认全部，可筛未读），点开单条即标记已读，支持一键「全部已读」（幂等，无载荷）；无通知时展示空态。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/my/notifications` | 🔐 认证（`@RequireVerified` + `hasRole('STUDENT')`） | 我的通知列表（倒序，支持 `isRead` 过滤） |
| GET | `/my/notifications/unread-count` | 同上 | 未读总数（驱动红点）；轻量性能端点，避免为红点拉回整页 `records` |
| PUT | `/my/notifications/{id}/read` | 同上 | 单条标记已读（带归属校验） |
| PUT | `/my/notifications/read-all` | 同上 | 全部已读（**幂等**） |

> **路径风格登记（2026-09-29 决策）**：`read-all` 为**动作式路径**（非资源化的 `PUT /my/notifications/read-status`）——
> 项目既有惯例允许动作式子路径（如同族的 `/{id}/read`），且该端点是**幂等批量动作、无请求体**，
> 动作式命名表达能力更强、歧义更小。**保持现状**。

## 字段

### 请求

| 接口 | 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|---|
| `GET /my/notifications` | `isRead` | boolean | 否 | 已读过滤：`true`=仅已读 / `false`=仅未读；**不传 = 全部**（端上当前恒不传） |
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

`NotificationVO` 单行字段：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 通知 ID |
| `title` | string | 通知标题 |
| `content` | string | 通知正文（回执内容；不采纳时含不采纳原因） |
| `isRead` | boolean | 是否已读：`true`=已读 / `false`=未读 |
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
| `data` | null | 无载荷；成功即 `code=200`（幂等，重复调用结果一致） |

### 错误码

| code | 含义 | 中文解释 |
|---|---|---|
| 4031 | 邮箱未认证 | 跳转身份认证页 |
| 403 | 归属不符 | 只能操作自己的通知 |
| **4001** | 通知不存在 | 资源不存在 |

## 数据（读写）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `notification` | SELECT / UPDATE `is_read` | 通知列表、红点计数、单条与全部已读 |
| `notification` | INSERT（由反馈处理 / 纠错处理触发） | 管理员处理反馈时写入 `feedback_handle` 回执、采纳/拒绝菜品纠错时写入 `correction_handle` 回执（标题「菜品信息更新」）；游客提交不投递 |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

**无（文档与代码一致）**
