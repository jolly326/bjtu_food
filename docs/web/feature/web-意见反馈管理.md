# 意见反馈管理（B-09）

> 所属端：**管理后台（Web）** ｜ 鉴权：🔑 管理员
> 返回：[管理后台功能总览](./README.md)

## 介绍

处理学生在 A-10 提交的**纯意见反馈**（`type` ∈ `bug` / `suggestion` / `other`）。

**与相邻功能的边界**：
- **举报**（`type=report`）归 B-10，**纠错**归 B-11 —— 三者同存 `user_feedback` 表，靠 `type` 区分，**本功能只处理纯反馈三类**，不展示举报 / 纠错。
- 反馈是**意见表达**，没有「写回菜品」动作，处置方式只有**回复**与**退回**。

**处置动作**：
- `handled`（通过 / 已处理，缺省）：填**回复**；
- `rejected`（不采纳 / 退回）：填**回复 + 不采纳原因**（原因必填，1~200 字）。

**回复必填**（红线级口径）：学生收到的站内通知会展示该回复，**空回复等于空通知**，故回复为 `null` / 纯空白一律 `400`。

**回执投递**：处理完成后向提交人投递站内通知（学生端 A-11）。**仅当提交人是「可归属用户」时投递**——游客（匿名提交，`userId` 为 `null`）与未邮箱认证账号**不投递**（反馈通道刻意允许匿名，不保留可回执身份）。投递失败**不影响**处理结果（异常不外抛）。

**重复处理保护**：仅 `status='pending'` 可处理；重复提交返回 `400`（幂等）。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/feedbacks` | 🔑 | 纯反馈列表（分页 + 筛选；**仅** `type` ∈ bug/suggestion/other） |
| PUT | `/admin/feedbacks/{id}` | 🔑 | 处理反馈（回复 / 退回）；非 pending 返回 `400`；不存在返回 `4001` |

### 请求参数（`GET /admin/feedbacks`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 20 |
| `status` | string | 否 | `pending` / `handled`；不传 = 全部 |
| `type` | string | 否 | `bug` / `suggestion` / `other`；不传 = 三类全部 |
| `keyword` | string | 否 | 按反馈内容或回复模糊匹配 |

### 请求 · `PUT /admin/feedbacks/{id}`（`FeedbackHandleReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `reply` | string | 是 | 处理回复（必填，1~1000 字；纯空白视为未填写 → `400`） |
| `outcome` | string | 否 | 处理结论：`handled`（缺省）/ `rejected` |
| `rejectReason` | string | 条件必填 | 不采纳原因（1~200 字）；`outcome=rejected` 时**必填** |

## 字段

### 响应 · `FeedbackAdminVO`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 反馈 ID |
| `type` | string | 反馈类型：`bug` / `suggestion` / `other` |
| `userId` | number \| null | 提交人 ID（**游客为 `null`**） |
| `userNickname` | string \| null | 提交人昵称（游客为 `null`） |
| `content` | string | 反馈内容 |
| `images` | string[] | 配图绝对 URL 数组（无图空数组，≤3 张） |
| `status` | string | `pending` / `handled` |
| `outcome` | string \| null | 处理结论（由 `status` + `rejectReason` 派生）：`handled` / `rejected`；未处理为 `null` |
| `reply` | string \| null | 处理回复（未处理为 `null`） |
| `rejectReason` | string \| null | 不采纳原因（非 rejected 为 `null`） |
| `createdAt` | string | 提交时间 |
| `handledAt` | string \| null | 处理时间（未处理为 `null`） |

### 错误码

| 码 | 场景 |
|---|---|
| `400` | 回复为空 / 超长；`outcome` 非法；`rejected` 缺原因或原因超长；反馈已处理（非 pending） |
| `4001` | 反馈不存在 |

## 数据（读写）

| 表 | 读写什么 | 中文解释 |
|---|---|---|
| `user_feedback` | 查（限定 type）、写 `status` / `reply` / `reject_reason` / `handled_at` | 反馈本体 |
| `user` | 读 `nickname` 回填提交人 | 提交人 |
| `notification` | 写入回执（仅可归属用户） | 处理回执（学生端 A-11） |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

- **列表按 `type` 收窄**：现有管理端把反馈与举报混在同一列表；本功能**只处理纯反馈三类**，举报迁至 B-10。
- **新增 `images` 出参**：现有管理端列表不下发配图（管理员无法核对截图）。
