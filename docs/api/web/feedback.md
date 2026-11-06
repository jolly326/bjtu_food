# 运营端接口 · 反馈

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/CONVENTIONS.md`](../CONVENTIONS.md)。
> 列定义见 [`schema/feedback.md`](../../schema/feedback.md)。

## `GET /admin/feedback`

| 项 | 口径 |
|---|---|
| 入参 | `type?`（`product` / `dish`）· `handled?`（`true` 已处理 / `false` 未处理）· `page` / `pageSize`（默认 20，上限 50） |
| 出参 | `AdminPageResult<FeedbackAdminVO>` |
| 排序 | 未处理优先，其次 `created_at` 降序 |

**`FeedbackAdminVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 反馈 ID |
| `type` | string | 否 | `product` / `dish` |
| `dishId` | number | **是** | 关联菜品；产品反馈为 `null` |
| `dishName` | string | 否 | 菜品名；产品反馈为 `''` |
| `content` | string | 否 | 反馈正文 |
| `createdAt` | string | 否 | 提交时间 |
| `handledAt` | string | **是** | 处理时间；`null` = 未处理 |

## `PATCH /admin/feedback/{id}/handled`

| 项 | 口径 |
|---|---|
| 入参 | 无 |
| 行为 | 置 `handled_at` 为当前时间；已处理则保持原值（幂等） |
| 出参 | `FeedbackAdminVO` |
| 错误码 | `4001` 反馈不存在 |

> 运营端只做「查看 + 标记已处理」，**不改反馈正文**，也不向学生回执。
