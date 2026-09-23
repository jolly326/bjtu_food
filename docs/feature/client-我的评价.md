# 我的评价（A-10）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔐 认证**（需学号邮箱认证）
> 返回：[功能总览](./README.md)

## 介绍

查看自己发过的**全部评价**——包括被管理员隐藏的（会标「已被隐藏」），并支持删除自己的评价。列表分页加载（上滑加载更多）；点「删除」→ 二次确认 → 调 `DELETE /reviews/{id}`。

## UI

> 📐 页面 UI 设计稿已拆出 → [client-我的评价.md（docs/ui）](../ui/client-我的评价.md)（**UI 口径以该文件为唯一真源**）

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/my/reviews` | **🔐 认证**（`@RequireVerified` + 方法级 `hasRole('STUDENT')`） | 我的评价列表；**本人视角不过滤 `is_hidden`**（自己的隐藏评价也可见）。**可选 `dishId` 过滤**（按菜品过滤本人评价） |

## 字段

### 请求（query）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 20 |
| `dishId` | number | 否 | **按菜品过滤**（菜品详情页判定「我是否已评价」时传）；不传 = 全部 |
| （鉴权） | — | — | 用户身份从 JWT 取，**端上不传 userId**（数据隔离红线） |

### 响应（`data` = `PageResult<MyReviewVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | MyReviewVO[] | 当前页评价行数组 |
| `total` | number | 本人评价总条数 |
| `page` | number | 实际生效页码（归一化后） |
| `pageSize` | number | 实际生效每页条数（归一化后） |

> **`page` / `pageSize` 归一化规则（R11）**：回传经 `PageUtil.normalize` 归一化后的**实际生效值**（`page < 1 → 1`；`pageSize < 1 → 10`；`pageSize > 100 → 100`）—— 入参越界 / 超限时端上据此得知服务端真正用了什么。

### 响应 · `MyReviewVO` 单行字段（**本人视角，11 字段**）

> **双视角命名（R9）**：本接口返回 **`MyReviewVO`（本人视角，11 字段）** = 公开视角 `ReviewVO` 的 8 字段 + 本表后 3 个（`dishId` / `dishName` / `isHidden`）。两视角 **MUST 是两个类型**，端上 **SHALL NOT** 用单一 `ReviewVO` interface 复用。

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 评价 ID |
| `userId` | number | 评价者用户 ID（本人） |
| `dishId` | number | 关联菜品 ID |
| `dishName` | string | **关联菜品名称**（后端联表补齐；本页必展示，用于辨识是哪道菜） |
| `userNickname` | string | 评价者昵称 |
| `userAvatar` | string | 评价者头像 URL |
| `rating` | number | 评分（1~5 星） |
| `content` | string | 评价文字内容 |
| `images` | string[] | 评价配图 URL 数组（≤3 张 COS 地址，无图空数组） |
| `createdAt` | string | 评价发表时间。**端上 SHALL NOT 改名**，统一用 `createdAt` |
| `isHidden` | number | **是否被管理员隐藏：0=正常 / 1=已隐藏**——本接口本人视角**必然返回**，端上据此标注「已被隐藏」 |

## 数据（读取）

| 表 | 读什么 | 中文解释 |
|---|---|---|
| `review` | 本人全部评价（**不过滤 `is_hidden`**） | 评价本体（含被隐藏的） |
| `dish` | `name` | 联表补齐菜品名，供「我的评价」列表展示 |
| `user` | `nickname`、`avatar` | 联表补齐昵称与头像 |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

**无（文档与代码一致）**
