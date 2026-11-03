# 运营端接口 · 菜品

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/CONVENTIONS.md`](../CONVENTIONS.md)。
> 列定义见 [`schema/dish.md`](../../schema/dish.md) 与 [`schema/dish_offering.md`](../../schema/dish_offering.md)。

## `GET /admin/dishes`

| 项 | 口径 |
|---|---|
| 入参 | `canteenId?` · `stallId?` · `activate?` · `name?`（模糊，1–64 字）· `page` / `pageSize`（默认 20，上限 50） |
| 出参 | `AdminPageResult<DishAdminVO>` |
| 排序 | 按 `id` 升序 |

**`DishAdminVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 菜品 ID |
| `name` | string | 否 | 菜品名称（全库唯一） |
| `description` | string | 否 | 档案富文本（缺失为 `''`） |
| `imageUrl` | string | **是** | 菜品大图绝对 URL；`null` = 未上传 |
| `activate` | boolean | 否 | `true` 在售 / `false` 已下架 |
| `offerings` | `DishOfferingAdminVO[]` | 否 | **售卖点列表，恒为数组** |
| `updatedAt` | string | 否 | 更新时间 |

**`DishOfferingAdminVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 售卖点 ID |
| `canteenId` | number | 否 | 食堂 ID |
| `canteenName` | string | 否 | 食堂名 |
| `stallId` | number | **是** | 档口 ID；`null` = 无具体档口（学校自营 / 大食堂） |
| `stallName` | string | 否 | 档口名；无档口为 `''` |
| `floor` | string | 否 | 楼层（取 `stall.floor`）；无档口为 `''` |
| `price` | number | **是** | 售价（**分**）；`null` = 未录入 |
| `activate` | boolean | 否 | 该售卖点是否在售 |

> 一菜多售卖点 ⇒ `offerings` 多项，运营端菜品列表按「食堂 · 档口 · 价格」逐行展示。

## `POST /admin/dishes`

| 项 | 口径 |
|---|---|
| 入参 | `{ name, description?, imageUrl?, activate?, offerings?: DishOfferingInput[] }` |
| `DishOfferingInput` | `{ canteenId, stallId?, price?, activate? }` |
| 校验 | `name` 必填且全库唯一；`stallId` 非空时其档口 `canteen_id` 必须等于 `canteenId`（否则 `400`） |
| 出参 | `DishAdminVO` |
| 错误码 | `400` name 缺失 / 重名 ｜ 档口与食堂归属不一致 ｜ `4001` 食堂 / 档口不存在 |

## `PUT /admin/dishes/{id}`

| 项 | 口径 |
|---|---|
| 入参 | 同 `POST` |
| `offerings` 语义 | **整表替换**：未出现在列表中的旧售卖点被删除；带 `id` 的更新、不带 `id` 的新增 |
| 出参 | `DishAdminVO` |
| 错误码 | `400` 重名 ｜ 归属不一致 ｜ `4001` 菜品 / 食堂 / 档口不存在 |

## `DELETE /admin/dishes/{id}`

| 项 | 口径 |
|---|---|
| 行为 | 物理删除 |
| 级联 | `dish_offering` 与 `feedback`（菜品级）随外键 `ON DELETE CASCADE` 一并清除（删除前前端二次确认） |
| 错误码 | `4001` 菜品不存在 |

> **嵌入向量由服务端维护**：`description` 变更后自动重算 `embedding`，不接受前端传入。
> **下架**（`activate = false`）对公开接口等价于不存在；**无「售罄 / 今日供应」状态**。
