# 学生端接口 · 食单

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/README.md`](../README.md)。

## `GET /dishes`

🔑 需登录 —— 美食地图的全量在售菜品（端上随机散布、可平移缩放）。

| 项 | 口径 |
|---|---|
| 入参 | 无（不做食堂筛选 / 分页） |
| 出参 | `DishVO[]` —— **全量在售菜品**（地图全量铺放，不做分页） |
| 过滤 | 仅 `status = 'on'`（在售） |
| 排序 | 服务端**不排序**（随机散布由端上完成）；可按任意顺序下发 |
| 错误码 | `401` 未登录 |

**`DishVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 菜品 ID |
| `name` | string | 否 | 菜品名称 |
| `price` | number | 否 | 现价，**单位「分」** |
| `imageUrl` | string | **是** | 菜品大图绝对 URL；`null` = 该菜未配图 |
| `likeCount` | number | 否 | **群体点赞数** = `COUNT(dish_rating.liked = 1)` |
| `liked` | boolean | 否 | 当前用户是否已赞（取登录态，禁止前端传入） |
| `canteenName` | string | 否 | 食堂名（位置） |
| `stallName` | string | 否 | 档口名（位置） |

> 列定义见 [`schema/dish.md`](../../schema/dish.md) · 业务规则见 [`func/client/A1`](../../func/client/A-浏览与发现/A1-美食地图.md) · 点赞口径见 [`func/client/B1`](../../func/client/B-轻量反馈/B1-点赞反馈.md)。

## `POST /dishes/{id}/likes`

🔑 需登录 —— **两态点赞 toggle**（抖音式）：已赞 ⇒ 取消，未赞 ⇒ 点赞。

| 项 | 口径 |
|---|---|
| 入参 | 无（`{id}` 路径参数） |
| 行为 | 该行不存在 ⇒ INSERT（`liked = 1`）；已存在 ⇒ 置 `liked = 0`（取消）；同事务重算 `dish.like_count` |
| 出参 | `{ id, likeCount, liked }` —— `likeCount` 为该菜**最新点赞数**，`liked` 为当前用户**操作后**状态 |
| 归属 | 只能操作本人点赞；用户取自登录态，**禁止前端传入** |
| 频控 | 限频（见 [`secur/S-03`](../../secur/S-03-限频与接口防护.md)） |
| 错误码 | `4001` 菜品不存在 / 已下架 ｜ `429` 命中限频 |

> 点赞真源见 [`schema/dish_rating.md`](../../schema/dish_rating.md) · 业务规则见 [`func/client/B1`](../../func/client/B-轻量反馈/B1-点赞反馈.md)。
