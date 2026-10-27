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
| `likeCount` | number | 否 | **点赞数**（**只读**缓存列）；= `COUNT(dish_rating.liked = 1)` |
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
| 🔴 **引用即禁删** | 存在**学生点赞**（`dish_rating`）或**纠错**（`dish_issue`）⇒ `400`「该菜品已有学生点赞或纠错记录，无法删除，请改用『下架』」—— 学生数据不可随菜品蒸发；`ON DELETE CASCADE` 仅作兜底，正常路径不可达 |
| 错误码 | `400` 存在引用 ｜ `4001` 菜品不存在 |

> **`likeCount` 是派生缓存列** —— 由用户点赞驱动重算，**不可通过本接口直接修改**（见 [`schema/dish.md`](../../schema/dish.md) 的派生清单）。
> **下架**（`status = 'off'`）对公开接口**等价于不存在**；**无「售罄 / 今日供应」状态**。
