# dish_rating —— 菜品评分（口碑真源）

> **一人一菜一行**：与 [dish_record](./dish_record.md)（饮食流水）**解耦**。
> 评分 / 改分 / 清除都发生在本表；**删除记忆不撤票**（删 `dish_record` 不动本表）。
> 聚合 `dish.avg_reaction` / `reaction_count` **只扫本表**——一人一菜一行 ⇒ 天然去重，
> 无需「NOT EXISTS 取最新」的旧口径（见 [dish.md §评价分缓存列](./dish.md)）。

## 字段

| 列 | 类型 | 空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | 自增 | PK | |
| `user_id` | BIGINT | NO | | FK→`user.id` CASCADE | 评分人 |
| `dish_id` | BIGINT | NO | | FK→`dish.id` CASCADE + INDEX | 被评菜品 |
| `reaction` | SMALLINT | NO | | | `5` 喜欢 ｜ `3` 一般 ｜ `1` 不喜欢（🔴 本表无 NULL——清除评价 = 删行，即整体撤票） |
| `reacted_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 最近评分时间（DB 时钟写入） |

## 约束 / 索引

| 名 | 定义 | 用途 |
|---|---|---|
| `uq_rating_user_dish` | `UNIQUE (user_id, dish_id)` | **一人一菜一票**：改分 = 覆盖本行，永不产生第二票 |
| `ix_rating_dish` | `INDEX (dish_id)` | 聚合按菜品扫描 |

## 生命周期

| 动作 | 行为 |
|---|---|
| 评分 / 改分 | upsert 本行（`reacted_at` = DB 时钟）+ 同事务重算缓存列 |
| 清除评价 | 删本行（整体撤票）+ 重算 |
| **删除记忆** | 🔴 **不动本表** —— 口碑贡献独立于私人流水存在，`avg_reaction` 不变 |
| 删除菜品 | 记忆引用即禁删 ⇒ 本表行不会因删菜蒸发；`CASCADE` 仅兜底 |

## 语义对照（旧 → 新）

| 旧（评分存在记录行上） | 新（评分独立成表） |
|---|---|
| 删除已评分记录 ⇒ 撤票 + 重算 | **删除记忆 ⇒ 评分保留、口碑不变** |
| 聚合需 NOT EXISTS 去重取最新 | 天然一人一票 |
| 「清除评价」= 置 `reaction = NULL` | 「清除评价」= 删行（整体撤票） |
