# 运营端接口 · 菜品

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/README.md`](../CONVENTIONS.md)。
> 列定义见 [`schema/dish.md`](../../schema/dish.md)。

## `GET /admin/dishes`

| 项 | 口径 |
|---|---|
| 入参 | `stallId?` · `canteenId?` · `noStall?`（`true` = 只看**未指定档口**的菜品）· `activate?`（`true` 在售 / `false` 已下架）· `name?`（**名称模糊匹配**，1–64 字）· `page` / `pageSize`（默认 20，上限 50） |
| 出参 | `AdminPageResult<DishAdminVO>` |
| 排序 | 按 `id` 升序 |

**`DishAdminVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 菜品 ID |
| `canteenId` | number | 否 | 所属食堂（**必填**，最小定位信息） |
| `canteenName` | string | 否 | 食堂名（位置链路展示） |
| `stallId` | number | **是** | 所属档口；`null` = 档口未知 / 暂不指定 |
| `stallName` | string | 否 | 档口名；缺失为 `''`（列表占位展示「暂无」） |
| `floor` | string | **是** | 楼层（受控字典：负一层…四层）；缺失为 `''`（列表占位展示「暂无」） |
| `name` | string | 否 | 菜品名称 |
| `imageUrl` | string | **是** | 菜品大图绝对 URL；`null` = 未上传 |
| `activate` | boolean | 否 | 菜品状态：`true` 在售 / `false` 已下架 |
| `likeCount` | number | 否 | **点赞数**（**只读**缓存列）；= `COUNT(dish_rating.liked = TRUE)` |
| `updatedAt` | string | 否 | 更新时间 |

## `POST /admin/dishes`

| 项 | 口径 |
|---|---|
| 入参 | `{ canteenId, name, stallId?, floor?, imageUrl?, activate? }` —— `canteenId` / `name` 必填；`stallId` / `floor` 缺省为「未指定」；`activate` 缺省 `true` |
| 出参 | `DishAdminVO` |
| 错误码 | `400` `canteenId` / `name` 缺失或非法 ｜ 同食堂下同名已存在 ｜ 档口与食堂归属不一致 ｜ `4001` 食堂不存在 ｜ `4001` 档口不存在 |

## `PUT /admin/dishes/{id}`

| 项 | 口径 |
|---|---|
| 入参 | 同 `POST`（`imageUrl` 传 `null` = 清除图片；`stallId` 传 `null` = 清空档口） |
| 出参 | `DishAdminVO` |
| 错误码 | `400` 同食堂下同名已存在 ｜ 档口与食堂归属不一致 ｜ `4001` 菜品不存在 |

## `DELETE /admin/dishes/{id}`

| 项 | 口径 |
|---|---|
| 行为 | 物理删除 |
| 🔴 **引用即禁删** | 存在**学生点赞**（`dish_rating`）或**纠错**（`dish_issue`）⇒ `400`「该菜品已有学生点赞或纠错记录，无法删除，请改用『下架』」—— 学生数据不可随菜品蒸发；`ON DELETE CASCADE` 仅作兜底，正常路径不可达 |
| 错误码 | `400` 存在引用 ｜ `4001` 菜品不存在 |

> **`likeCount` 是派生缓存列** —— 由用户点赞驱动重算，**不可通过本接口直接修改**（见 [`schema/dish.md`](../../schema/dish.md) 的派生清单）。
> **下架**（`activate = false`）对公开接口**等价于不存在**；**无「售罄 / 今日供应」状态**。
