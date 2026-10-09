# client/reviews — 评价操作与举报

**归属**：`ReviewController`（删除）· `ReportController`（举报）· `FeedbackController`（举报原因字典）
**通用约定**见 [api/README.md](../README.md)

> 评价**列表**与**提交**端点在 [dishes.md](./dishes.md#post-dishesidreviews)；本文只覆盖「按评价 ID 操作」的部分。

---

## DELETE /reviews/{id}

**鉴权**：🔐 认证 ｜ **用途**：删除本人评价

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | **是** | 评价 ID；**无请求体**，归属由 JWT 判定 |

### 响应 `data`
`null`（成功即 `code=200`）

### 错误码
| code | 条件 | 端上处置 |
|---|---|---|
| **4001** | 评价不存在 | Toast 提示，本地不移除该条 |
| 403 | 试图删除他人评价 | Toast（正常不可达：入口已按 `userId` 收口） |
| 4031 | 邮箱未认证 | 跳转身份认证页 |
| 401 | 未登录 | 静默重登并重试一次 |

### 副作用
| 目标 | 变化 | 时机 |
|---|---|---|
| `review` | 物理删除该行 | 事务内 |
| `dish.avg_rating` / `rating_count` | 重算 | **异步 AFTER_COMMIT** |

> 该菜品的评分与评价数随之重算，**不影响他人评价**。二次确认由端上承担（破坏性操作必备）。

---

## GET /report-reasons

**鉴权**：🔓 公开 ｜ **用途**：举报原因字典

### 请求参数
无。

### 响应 `data` = `ReportReason[]`
| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 原因 ID（提交举报时作为 `reasonId` 上送） |
| `label` | string | 否 | 中文标签（弹层直接渲染） |

> **恰好 2 个字段**，按 **`order` 升序**下发（**仅启用项**），端上**零硬编码**、不排序。
> 值域由 `report_reason` 字典驱动（管理端 [A7](../../func/web/A-主数据维护/A7-举报原因管理.md) 维护，新增原因免发版；种子见 [schema/report_reason.md](../../schema/report_reason.md)）。
> **不含 `order` 字段** —— 展示顺序即数组顺序。

---

## POST /reviews/{id}/report

**鉴权**：🔑 需登录 ｜ **用途**：举报违规评价

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | **是** | 被举报的评价 ID |

### 请求体 `ReportReq`
| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `reasonId` | number | **是** | 原因 ID；值域 = `GET /report-reasons` 下发项的 `id`，非法 / 缺失 → 400 |
| `content` | string | 否 | **可空** —— 结论以结构化原因为准，文本仅作补充；≤1000 字，仍过内容安检 |
| `images` | string[] | 否 | 佐证配图，≤3 张；须为经 `POST /upload/cloud-image` 转存的 COS 地址 |

> 请求体**无 `type`、无关联对象字段** —— 被举报对象由路径 `{id}` 表达，类型固定为「评价举报」。
> **不要求填写文本**：举报结论以结构化原因为准，管理员据此直接处置。

### 响应 `data`
`null`（成功即 `code=200`）

### 错误码
| code | 条件 |
|---|---|
| **4001** | 被举报的评价不存在（含已隐藏 / 已删除） |
| 400 | `reasonId` 缺失或非法（非启用项） / 重复举报 / 超频 / 补充文本安检 `risky` |

### 限频（同 IP）
| 维度 | 阈值 |
|---|---|
| 每分钟 | ≤2 条 |
| 每小时 | ≤10 条 |

> 超限返回 400，`message` 含剩余等待秒数。

### 副作用
| 目标 | 变化 |
|---|---|
| `user_feedback` | INSERT（`type='report'`、`sub_reason_id=reasonId`、`related_type='review'`、`related_id={id}`、`status='pending'`；**匿名提交 `user_id = 0`**） |
| `notification` | 管理员处理后异步 INSERT 回执 |

> **有 `userId` 即可收回执**：静默登录的游客**同样收到**处理回执；仅**匿名提交**（无 token，`user_id = 0`）因无接收人不投递。
