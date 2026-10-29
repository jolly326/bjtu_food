# dish_rating —— 菜品点赞（真源）

> **一人一菜一行**：点赞真源。
> 点赞 / 取消都发生在本表；群体点赞数 = `dish.like_count`（聚合 `COUNT(liked = 1)`）。

## 字段

| 列 | 类型 | 空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | 自增 | PK | |
| `user_id` | BIGINT | NO | | FK→`user.id` CASCADE | 点赞人 |
| `dish_id` | BIGINT | NO | | FK→`dish.id` CASCADE + INDEX | 被赞菜品 |
| `liked` | BOOLEAN | NO | `TRUE` | | **点赞态**：`TRUE` = 已赞 ｜ `FALSE` = 已取消（保留行，聚合只数 `liked = TRUE`） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 最近操作时间（DB 时钟写入） |

## 约束 / 索引

| 名 | 定义 | 用途 |
|---|---|---|
| `uq_rating_user_dish` | `UNIQUE (user_id, dish_id)` | **一人一菜一行**：改赞 = 覆盖本行 `liked`，永不产生第二行 |
| `ix_rating_dish` | `INDEX (dish_id, liked)` | 聚合按菜品 + 点赞态扫描（覆盖点赞数聚合查询） |

## 生命周期

| 动作 | 行为 |
|---|---|
| 点赞 | upsert 本行（`liked = TRUE`，`updated_at` = DB 时钟）+ 同事务重算 `dish.like_count` |
| 取消点赞 | 置 `liked = FALSE` + 同事务重算 `dish.like_count`（保留行，便于再次点赞） |
| 删除菜品 | 纠错引用即禁删 ⇒ 本表行不会因删菜蒸发；`CASCADE` 仅兜底 |

## 聚合口径

🔴 聚合真源与完整口径见 **[dish.md §点赞数缓存列](./dish.md)**。要点：`dish.like_count` = `COUNT(liked = 1)`，一人一菜一行天然去重。
