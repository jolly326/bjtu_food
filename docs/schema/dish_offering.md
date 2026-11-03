# dish_offering — 菜品售卖点

**用途**：菜品「**在哪里卖、卖多少钱**」的唯一真源。一道菜可在多个食堂 / 档口售卖，各售卖点价格独立。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 售卖点 ID |
| `dish_id` | BIGINT | NO | 无 | KEY, FK→dish | 所属菜品（`ON DELETE CASCADE`） |
| `canteen_id` | BIGINT | NO | 无 | KEY, FK→canteen | 所在食堂（`ON DELETE RESTRICT`） |
| `stall_id` | BIGINT | **YES** | `NULL` | KEY, FK→stall | 所在档口；**可空** —— 学校自营 / 大食堂统一出餐等无具体档口的情况（`ON DELETE SET NULL`） |
| `price` | INT | **YES** | `NULL` | | 该售卖点售价，单位**分**；`NULL` = 未填 |
| `activate` | BOOLEAN | NO | `TRUE` | KEY | 该售卖点是否在售 |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间 |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_offering_dish` | `dish_id` | 普通 | 按菜品取售卖点（菜品详情 / 编辑） |
| `idx_offering_canteen` | `canteen_id` | 普通 | 按食堂取售卖点（位置筛选） |
| `uq_offering_dish_canteen_stall` | `(dish_id, canteen_id, stall_id)` | **唯一** | 同一菜在同一食堂同一档口只允许一条；`stall_id IS NULL` 的唯一性由应用层保证 |
| `fk_offering_dish` | `dish_id` | 外键 | `REFERENCES dish(id)`，**`ON DELETE CASCADE`** |
| `fk_offering_canteen` | `canteen_id` | 外键 | `REFERENCES canteen(id)`，**`ON DELETE RESTRICT`** |
| `fk_offering_stall` | `stall_id` | 外键 | `REFERENCES stall(id)`，**`ON DELETE SET NULL`** |

## 关键设计

### 一菜多售卖点

- 一道菜在东区 ¥12、西区 ¥15、学校自营 ¥10 ⇒ **3 行**，各自价格、各自在售状态；
- 菜品详情与推荐出参按 `dish_id` 聚合，展示「在哪些食堂 / 档口、各多少钱」。

### 无档口是合法状态

- `stall_id = NULL` 表示「属于食堂整体（学校自营 / 大食堂统一出餐）」，不是缺数据；
- 楼层来自 `stall.floor`（选了档口即带出）；无档口时楼层留空，不在本表重复存储。

### 价格随食堂不同

- 价格是本表列，不是 `dish` 列；同一道菜在不同食堂价格可不同；
- 精确价格筛选（如「15 元以内」）在本表 `price` 上做，单位统一为分。

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| N→1 | `dish.id` | 所属菜品 | **`ON DELETE CASCADE`** |
| N→1 | `canteen.id` | 所在食堂 | **`ON DELETE RESTRICT`**（有售卖点引用的食堂不可删除） |
| N→1 | `stall.id` | 所在档口（可空） | **`ON DELETE SET NULL`**（档口删除后售卖点保留、档口置空） |

## 注意事项

- 运营端在**菜品编辑页内**维护该菜的多个售卖点（增删行，每行选食堂 / 档口 / 价格），见 [`ui/web/菜品管理.md`](../ui/web/菜品管理.md)。
- 🔴 **归属一致性**：`stall_id` 非空时，其档口 `stall.canteen_id` 必须等于本行 `canteen_id`（应用层校验，违者 `400`）。
- 删除 `canteen` 时若仍有售卖点引用 ⇒ 被 `RESTRICT` 拦截；删除 `stall` 时售卖点保留、`stall_id` 置空。
- `price = NULL` 表示未录入，价格区间筛选自然跳过该行。
