# 运营端接口 · 菜品

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/README.md`](../README.md)。
> 列定义见 [`schema/dish.md`](../../schema/dish.md)。

## `GET /admin/dishes`

| 项 | 口径 |
|---|---|
| 入参 | `stallId?` · `canteenId?` · `status?`（`on` / `off`）· `page` / `pageSize`（默认 20，上限 50） |
| 出参 | `AdminPageResult<DishAdminVO>` |
| 排序 | 按 `id` 升序 |

**`DishAdminVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 菜品 ID |
| `stallId` | number | 否 | 所属档口 |
| `stallName` | string | 否 | 档口名 |
| `canteenName` | string | 否 | 食堂名（位置链路展示） |
| `name` | string | 否 | 菜品名称 |
| `price` | number | 否 | 现价，**单位「分」** |
| `imageUrl` | string | **是** | 菜品大图绝对 URL；`null` = 未上传 |
| `status` | string | 否 | `on` 在售 / `off` 已下架 |
| `avgReaction` | number | **是** | 平均评价度（**只读**缓存列）；`null` = 零评分 |
| `reactionCount` | number | 否 | 评分条数（**只读**缓存列） |
| `updatedAt` | string | 否 | 更新时间 |

## `POST /admin/dishes`

| 项 | 口径 |
|---|---|
| 入参 | `{ stallId, name, price, imageUrl?, status? }` —— `status` 缺省 `on` |
| 出参 | `DishAdminVO` |
| 错误码 | `400` `stallId` / `name` / `price` 缺失或非法 ｜ 同档口下同名已存在 ｜ `4001` 档口不存在 |

## `PUT /admin/dishes/{id}`

| 项 | 口径 |
|---|---|
| 入参 | 同 `POST`（`imageUrl` 传 `null` = 清除图片） |
| 出参 | `DishAdminVO` |
| 错误码 | `400` 同档口下同名已存在 ｜ `4001` 菜品不存在 |

## `DELETE /admin/dishes/{id}`

| 项 | 口径 |
|---|---|
| 行为 | 物理删除 |
| 🔴 级联 | **连带清理其饮食记录**（外键 `ON DELETE CASCADE`）与纠错记录 —— 删前须确认 |
| 错误码 | `4001` 菜品不存在 |

> **`avgReaction` / `reactionCount` 是派生缓存列** —— 由用户评分驱动重算，**不可通过本接口直接修改**（见 [`schema/dish.md`](../../schema/dish.md) 的派生清单）。
> **下架**（`status = 'off'`）对公开接口**等价于不存在**；**无「售罄 / 今日供应」状态**。
