# 运营端接口 · 食堂

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/README.md`](../README.md)。
> 列定义见 [`schema/canteen.md`](../../schema/canteen.md)。

## `GET /admin/canteens`

| 项 | 口径 |
|---|---|
| 入参 | `page` / `pageSize`（默认 20，上限 50） |
| 出参 | `AdminPageResult<CanteenVO>` |
| 排序 | 按 `id` 升序 |

**`CanteenVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 食堂 ID |
| `name` | string | 否 | 食堂名称 |
| `stallCount` | number | 否 | 下属档口数（只读统计） |
| `updatedAt` | string | 否 | 更新时间 |

## `POST /admin/canteens`

| 项 | 口径 |
|---|---|
| 入参 | `{ name }` |
| 出参 | `CanteenVO` |
| 错误码 | `400` `name` 为空 / **同名已存在** |

## `PUT /admin/canteens/{id}`

| 项 | 口径 |
|---|---|
| 入参 | `{ name }` |
| 出参 | `CanteenVO` |
| 错误码 | `400` 同名已存在 ｜ `4001` 食堂不存在 |

## `DELETE /admin/canteens/{id}`

| 项 | 口径 |
|---|---|
| 行为 | 物理删除 |
| 🔴 边界 | **该食堂下仍有档口时拒绝** |
| 错误码 | `400` 存在下属档口 ｜ `4001` 食堂不存在 |
