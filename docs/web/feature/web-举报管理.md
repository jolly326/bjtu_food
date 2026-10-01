# 举报管理（B-10）

> 所属端：**管理后台（Web）** ｜ 鉴权：🔑 管理员
> 返回：[管理后台功能总览](./README.md)
>
> ⚠️ 本管理后台（Web 后台）已软冻结，待后期整体重构移除（详见 [管理后台功能总览](./README.md) 顶部声明）。

## 介绍

处理学生在 A-08 提交的**评价举报**（存于 `user_feedback`，`type='report'`，关联对象为评价）。

**与 B-09 的差异（为何拆开）**：反馈是「意见表达」，处置方式是**回复**；举报是「对具体内容的指控」，处置方式是**先处置被举报对象、再回复举报人**。两者语义与后续动作都不同，混在一个列表会让管理员漏掉真正的处置动作。

**处置动作（本功能的核心）**：
- `hideReview`（隐藏被举报评价）：调 B-08 的隐藏能力，使该评价对所有人不可见（含作者本人）；
- `dismiss`（不予处理）：被举报评价保持原样。

**回复必填**：无论处置结论如何，都需填写回复（举报人将收到站内通知）；纯空白 `400`。`dismiss` 时**不需要**不采纳原因（举报本就是「已阅知」性质，处置结论由 `action` 表达）。

**回执投递**：与 B-09 同口径——**仅**提交人为「可归属用户」（非游客、已邮箱认证）时投递；投递失败不影响处理结果。

**重复处理**：仅 `status='pending'` 可处理；重复提交返回 `400`。

**举报原因**：值域由服务端字典 `GET /report-reasons` 下发（`value` / `label`），管理端**零硬编码中文**；列表展示用 `reasonLabel`（中文），入参校验用 `reason`（机器值）。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/reports` | 🔑 | 举报列表（分页 + 筛选；**仅** `type=report`） |
| PUT | `/admin/reports/{id}` | 🔑 | 处理举报（处置被举报评价 + 回复举报人）；非 pending 返回 `400`；不存在返回 `4001` |

### 请求参数（`GET /admin/reports`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 20 |
| `status` | string | 否 | `pending` / `handled`；不传 = 全部 |
| `reason` | string | 否 | 按举报原因筛选（值域 = `GET /report-reasons`） |

### 请求 · `PUT /admin/reports/{id}`（`ReportHandleReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `action` | string | 是 | 处置动作：`hideReview` / `dismiss` |
| `reply` | string | 是 | 回复举报人（必填，1~1000 字；纯空白 → `400`） |

## 字段

### 响应 · `ReportAdminVO`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 举报 ID |
| `reason` | string | 举报原因机器值 |
| `reasonLabel` | string | 举报原因中文标签（由服务端字典翻译，**端上零硬编码**） |
| `reviewId` | number | 被举报评价 ID |
| `reviewContent` | string | 被举报评价内容（**评价已删除为 `null`**） |
| `reviewDishName` | string \| null | 被举报评价所属菜品名 |
| `reviewHidden` | boolean | 被举报评价当前是否已隐藏（评价已删除为 `false`） |
| `userId` | number \| null | 举报人 ID（**游客为 `null`**） |
| `userNickname` | string \| null | 举报人昵称 |
| `content` | string | 举报补充说明（可空，无为空串） |
| `images` | string[] | 佐证配图绝对 URL 数组（无图空数组，≤3 张） |
| `status` | string | `pending` / `handled` |
| `action` | string \| null | 处置动作：`hideReview` / `dismiss`；未处理为 `null` |
| `reply` | string \| null | 回复（未处理为 `null`） |
| `createdAt` | string | 举报时间 |
| `handledAt` | string \| null | 处理时间 |

### 错误码

| 码 | 场景 |
|---|---|
| `400` | `action` 非法或缺失；回复为空 / 超长；举报已处理（非 pending） |
| `4001` | 举报不存在；**被举报评价已被删除**（选 `hideReview` 时） |

## 数据（读写）

| 表 | 读写什么 | 中文解释 |
|---|---|---|
| `user_feedback` | 查（限定 `type='report'`）、写 `status` / `reply` / `handled_at` | 举报记录 |
| `review` | 读被举报内容、按需写 `is_hidden` | 被举报评价（B-08 能力） |
| `dish` | 读 `name` 回填所属菜品 | 被举报评价的菜品 |
| `user` | 读 `nickname` 回填举报人 | 举报人 |
| `notification` | 写入回执（仅可归属用户） | 处理回执（学生端 A-11） |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

- 举报评价独立管理于 `/admin/reports`，处置动作由明确的 `action` 表达（处置被举报对象）。
- **新增 `action` 语义**：现有实现只能「回复」，无法在处理时直接处置被举报评价（需管理员手动切到评价页操作，易漏）。
- **新增 `reviewHidden` / `reviewDishName` 出参**：现有列表不回带被举报对象的状态，管理员无法判断「是否已处置过」。
