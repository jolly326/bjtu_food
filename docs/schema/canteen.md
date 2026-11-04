# canteen — 食堂

**用途**：食堂 —— 运营端录入菜品时的**下拉源**，并在菜品详情 / 推荐出参中作为位置展示。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 食堂 ID |
| `name` | VARCHAR(32) | NO | `''` | | 食堂名称 |
| `activate` | BOOLEAN | NO | `TRUE` | | 食堂状态：`TRUE` 正常 / `FALSE` 已停用 |
| `image` | VARCHAR(255) | YES | NULL | | 封面图（相对路径，出参转绝对 URL；运营端展示用） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间 |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |

> `name` 由唯一键 `uk_canteen_name` 保证唯一。

## 关键设计

- **下拉源**：运营端新增 / 编辑 `dish` 时，食堂从本表下拉选择，避免手输造成同名异构。
- **位置展示**：菜品详情按 `dish_location.canteen_id` 联表带出食堂名；学生端不单独拉取食堂列表。
- 食堂是档口的上级归属（见 [stall.md](./stall.md)）。

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| 1→n | `stall.canteen_id` | 该食堂下的档口 | 见 [stall.md](./stall.md) |
| 1→n | `dish_location.canteen_id` | 该食堂下的菜品位置 | **`ON DELETE RESTRICT`**（有菜品位置引用时不可删除） |

## 注意事项

- 管理端维护入口：运营端「食堂管理」。
