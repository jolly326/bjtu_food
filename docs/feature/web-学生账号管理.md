# 学生账号管理（B-05）

> 所属板块：**web（管理后台）** ｜ 鉴权：**🔑 口令**（`X-Admin-Token`）
> 返回：[功能总览](./README.md)

## 介绍

**禁用 / 启用**学生账号（禁用后无法登录），并查看某个学生的历史行为（评价与反馈）。管理端**没有角色概念**（全量用户即学生）。进页拉取学生列表（分页）；行内 switch 经确认后禁用 / 启用（禁用后该用户无法登录，且 UGC 写操作被拦截）；点「行为」弹窗按 `userId` 拉该用户的评价与反馈；关键词搜索为**前端本地过滤**（后端不接关键词参数）。

## UI

> 📐 页面 UI 设计稿已拆出 → [web-学生账号管理.md（docs/ui）](../ui/web-学生账号管理.md)（**UI 口径以该文件为唯一真源**）

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/users` | 🔑 | 学生列表（分页，仅支持状态过滤） |
| PUT | `/admin/users/{id}/status` | 🔑 | 启用 / 禁用学生 |
| — | `GET /admin/reviews?userId=` | 🔑 | **行为弹窗数据源（评价）**，消费字段见下「行为弹窗消费的字段」 |
| — | `GET /admin/feedbacks?userId=` | 🔑 | **行为弹窗数据源（反馈）**，消费字段见下「行为弹窗消费的字段」 |

## 字段

### 请求 · `GET /admin/users`（query）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 10（上限 100） |
| `status` | string | 否 | 状态过滤：`active`=正常 / `disabled`=已禁用 / `deleted`=已注销；不传 = 全部 |

> **无 `keyword` 参数**：页面上的关键词搜索是前端本地过滤（后端不接收该参数）。
> **无 `role` 参数**：角色维度不存在（角色仅服务端会话内判定）。

### 响应 · `GET /admin/users`（`data` = `PageResult<UserVO>`）

**分页壳字段**：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | UserVO[] | 当前页学生行数组（端上以它为准） |
| `total` | number | 符合条件的学生总条数 |

### 响应 · `UserVO` 单行字段

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 用户 ID |
| `username` | string | 学号 / 账号（已注销账号为 `deleted_{id}`） |
| `nickname` | string | 昵称（已注销显示「已注销用户」） |
| `avatar` | string \| null | 头像 URL |
| `status` | string | 账号状态：`active`=正常 / `disabled`=已禁用 / `deleted`=已注销 |
| `wechatBound` | boolean | 是否已绑定微信（**仅布尔标识，不返回 openid 明文**，规避隐私泄露） |
| `bindEmail` | string \| null | 已认证绑定的校园邮箱（**非空即已认证**，认证状态唯一真源，管理端与小程序端同口径派生） |
| `createdAt` | string | 注册时间 |

> **无 `role`**（角色仅服务端会话内判定）；**无 `openid`**（隐私）；**无 `guestShortId`**（`id` 的纯派生值不作接口出参，判据不因端而异）——管理端的**关键词过滤与昵称兜底展示由 `id` 现算**同一规则（「食客 + ID 后 4 位」，`UserView` 内局部函数）。

### 请求 · `PUT /admin/users/{id}/status`

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `id` | number | **是** | 用户 ID（路径参数） |
| `status` | string | **是** | 目标状态：`active`=启用 / `disabled`=禁用（端上按当前状态取反后直接传目标值） |

### 响应 · `PUT /admin/users/{id}/status`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `data` | null | 无载荷；成功即 `code=200` |

### 行为弹窗消费的字段

弹窗从 `GET /admin/reviews?userId=` 与 `GET /admin/feedbacks?userId=` 取该生的评价 / 反馈，逐字段含义如下：

**评价记录（`ReviewAdminVO`）**：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 评价 ID |
| `dishId` | number | 关联菜品 ID |
| `dishName` | string | 关联菜品名称（联表补齐） |
| `rating` | number | 评分（1~5 星） |
| `content` | string | 评价正文 |
| `images` | string[] | 评价配图 URL 数组（≤3 张 COS 地址） |
| `createdAt` | string | 评价时间 |
| `isHidden` | number | 是否被隐藏：`0`=正常 / `1`=已隐藏（本弹窗含被隐藏的评价） |

**反馈记录（`FeedbackAdminVO`）**：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 反馈 ID |
| `type` | string | 反馈类型：`issue`=问题反馈（存量历史类型 `suggestion` / `add` / `error` / `bug` / `report` / `other` 仍可能出现于老数据） |
| `sub` | string \| null | 二级分类（仅存量 `suggestion` 有值）：`idea`=想法 / `problem`=问题 |
| `content` | string | 反馈内容 |
| `status` | string | 处理状态：`pending`=待处理 / `handled`=已处理 |
| `createdAt` | string | 提交时间 |
| `handledAt` | string \| null | 处理时间（未处理为 null） |

## 数据（读写）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `user.status` | UPDATE | `active` ↔ `disabled`；禁用后无法登录、UGC 写操作被拦截 |
| `review` | SELECT（按 userId） | 行为弹窗展示评价记录 |
| `user_feedback` | SELECT（按 userId） | 行为弹窗展示反馈记录 |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

**无（文档与代码一致）**

> ⚠️ **系统不采集浏览足迹**（行为日志类数据无读取方，已按数据最小化原则停采）——行为弹窗只展示评价与反馈。
