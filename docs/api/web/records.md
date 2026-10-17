# 运营端接口 · 饮食记录（评分数据）

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/README.md`](../README.md)。
> 列定义见 [`schema/dish_record.md`](../../schema/dish_record.md)。

## `GET /admin/dish-records`

评分数据列表（**只读**）。

| 项 | 口径 |
|---|---|
| 入参 | `dishId?` · `reaction?`（`5` / `3` / `1` / **`none`**）· `page` / `pageSize`（默认 20，上限 50） |
| 出参 | `AdminPageResult<DishRecordVO>` |
| 排序 | 按 `wantedAt` 降序 |

**`reaction?` 取值约定**

| 传值 | 含义 |
|---|---|
| `5` / `3` / `1` | 按反应筛选（喜欢 / 一般 / 不喜欢） |
| **`none`** | 🔴 **未评分**（`reaction IS NULL`）—— 用保留字而非空串 / `0`，避免与"不传"混淆 |
| 不传 | 全部（含未评分） |
| 其它值 | `400` |

**`DishRecordVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 记录 ID |
| `dishId` | number | 否 | 菜品 ID |
| `dishName` | string | 否 | 菜品名称 |
| `canteenName` | string | 否 | 食堂名 |
| `stallName` | string | 否 | 档口名 |
| `reaction` | number | **是** | `5` = 喜欢 ｜ `3` = 一般 ｜ `1` = 不喜欢；**`null` = 尚未评分** |
| `wantedAt` | string | 否 | 去品尝时间 |
| `reactedAt` | string | **是** | 评分时间；未评分时 `null` |

> 🔴 **只读** —— 运营端**不修改、不删除**用户记录。菜品的评价分（`avgReaction`）是**派生缓存列**，运营端改不了（见 [`schema/dish.md`](../../schema/dish.md) 的派生清单）。
> 不展示提交人标识（学生端全匿名）。
