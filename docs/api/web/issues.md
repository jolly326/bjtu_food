# 运营端接口 · 菜品纠错

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/README.md`](../README.md)。
> 列定义见 [`schema/dish_issue.md`](../../schema/dish_issue.md) · [`schema/dish_issue_option.md`](../../schema/dish_issue_option.md) · 业务规则见 [`func/client/B2`](../../func/client/B-轻量反馈/B2-菜品纠错.md)。

## 纠错提交

### `GET /admin/dish-issues`

| 项 | 口径 |
|---|---|
| 入参 | `handled?`（`false` 未处理 / `true` 已处理 / **缺省 = 全部不过滤**）· `dishId?` · `page` / `pageSize`（默认 20，上限 50） |
| 出参 | `AdminPageResult<DishIssueVO>` |
| 排序 | **该菜当前待处理纠错条数降序** → `createdAt` 降序（被多人反馈的菜顶到最前） |

**`DishIssueVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 提交 ID |
| `dishId` | number | 否 | 被反馈菜品 |
| `dishName` | string | 否 | 菜品名称 |
| `canteenName` | string | 否 | 食堂名 |
| `stallName` | string | 否 | 档口名 |
| `optionName` | string | 否 | 错误类型中文（字典翻译） |
| `createdAt` | string | 否 | 提交时间 |
| `handledAt` | string | **是** | 标记已处理的时间；`null` = 未处理 |
| `dishFeedbackCount` | number | 否 | 该菜**当前待处理**纠错条数（服务端聚合；列表排序与严重度提示用） |

### `PATCH /admin/dish-issues/{id}`

| 项 | 口径 |
|---|---|
| 入参 | `{ handled }` —— `true` 写 `handledAt`；`false` 清空（**撤销误标**） |
| 出参 | `DishIssueVO` |
| 错误码 | `400` 参数非法 ｜ `4001` 记录不存在 |

> 🔴 **运营端不修改菜品** —— 本接口只标记「看过了」；菜品信息的更正走 [`PUT /admin/dishes/{id}`](./dishes.md)（**手动**）。
> **提交行永不删除**（见 [`schema/dish_issue.md`](../../schema/dish_issue.md)）。
> **无回执**：不给提交用户任何通知。

## 选项字典

### `GET /admin/dish-issue-options`

| 项 | 口径 |
|---|---|
| 入参 | 无 |
| 出参 | `[{ id, name, refCount, updatedAt }]` —— 按 `id` 升序 |

### `POST /admin/dish-issue-options`

| 项 | 口径 |
|---|---|
| 入参 | `{ name }` |
| 出参 | 同上单条 |
| 错误码 | `400` `name` 为空 |

### `PUT /admin/dish-issue-options/{id}`

| 项 | 口径 |
|---|---|
| 入参 | `{ name }` —— **改名全站生效**（含历史记录的中文翻译） |
| 出参 | 同上单条 |
| 错误码 | `4001` 选项不存在 |

### `DELETE /admin/dish-issue-options/{id}`

| 项 | 口径 |
|---|---|
| 行为 | 物理删除 |
| 🔴 边界 | **已被提交记录引用时拒绝**（被引用即禁删） |
| 错误码 | `400` 存在引用 ｜ `4001` 选项不存在 |

> **本字典无启停位** —— 选项集合即有效集合（见 [`schema/dish_issue_option.md`](../../schema/dish_issue_option.md)）。
