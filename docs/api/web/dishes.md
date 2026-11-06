# 菜品（运营端）

> 真源：端点 / 入参 / 出参 / 错误码。页面形态见 [`docs/ui/web/菜品管理.md`](../../ui/web/菜品管理.md)，业务规则见 [`docs/func/web/A-主数据维护/A3-菜品管理.md`](../../func/web/A-主数据维护/A3-菜品管理.md)。

**模型**：菜品本体（名称 / 描述 / 图 / 价格）+ N 个位置（食堂 / 楼层（出参派生） / 档口 / 在售）；位置随菜品整表替换。

## `GET /admin/dishes` — 分页列表

| 参数 | 类型 | 说明 |
|---|---|---|
| `canteenId` | number? | 按位置食堂过滤 |
| `stallId` | number? | 按位置档口过滤 |
| `activate` | boolean? | 位置在售状态过滤 |
| `name` | string? | 名称模糊匹配 |
| `page` / `pageSize` | number? | 分页 |

**出参 `data: { records: DishOut[], total: number }`**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 菜品 ID |
| `name` | string | 菜名 |
| `description` | string | 描述（无为空串） |
| `imageUrl` | string? | 绝对 URL |
| `price` | number? | 售价（分），位置共用 |
| `locations` | `DishLocationOut[]` | 位置列表 |
| `updatedAt` | string | 更新时间 |

**`DishLocationOut`**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 位置 ID |
| `canteenId` / `canteenName` | number / string | 食堂 |
| `stallId` / `stallName` | number? / string | 档口；无档口 `stallName` 为空串 |
| `floor` | string | 楼层（取档口） |
| `activate` | boolean | 该位置是否在售 |

## `POST /admin/dishes` — 新建

## `PUT /admin/dishes/{id}` — 修改

**入参 `DishSaveIn`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | 是 | 菜名；允许与其他菜品同名 |
| `description` | string? | 否 | 变更时重算当前模型向量 |
| `imageUrl` | string? | 否 | 绝对 URL；`null` = 清除 |
| `price` | number? | 否 | 售价（分）；`null` = 价格待定 |
| `locations` | `DishLocationIn[]` | 否 | 位置列表；**整表替换**（带 `id` 保留该 `id`，未出现的位置删除） |

**`DishLocationIn`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number? | 否 | 带 `id` 更新、不带 `id` 新增、未出现删除 |
| `canteenId` | number | 是 | 食堂 |
| `stallId` | number? | 否 | 档口；非空时须属于 `canteenId` |
| `activate` | boolean | 否 | 缺省 `true` |

**错误码**

| 码 | 场景 |
|---|---|
| `400` | 名称为空 / 同一菜品位置重复（同食堂同档口）/ 档口不属于所选食堂 |
| `4001` | 菜品 / 食堂 / 档口不存在 |
| `401` | 身份无效 |

## `DELETE /admin/dishes/{id}` — 删除

物理删除菜品；位置 / 向量 / 反馈随外键 `ON DELETE CASCADE` 一并清除。返回 `4001` 表示菜品不存在。
