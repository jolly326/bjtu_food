# dish_location — 菜品位置

**用途**：一道菜在哪个食堂 / 档口售卖。一道菜可挂多个位置；价格与图文在 [`dish`](./dish.md)，位置只表达「在哪卖」与「是否在售」。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 位置 ID；学生端卡片身份 |
| `dish_id` | BIGINT | NO | 无 | KEY, FK→dish | 所属菜品 |
| `canteen_id` | BIGINT | NO | 无 | KEY, FK→canteen | 食堂 |
| `stall_id` | BIGINT | YES | `NULL` | KEY, FK→stall | 档口；`NULL` = 食堂级位置 |
| `activate` | BOOLEAN | NO | `TRUE` | KEY | 该位置是否在售（位置级独立） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间 |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `uk_location_dish_canteen_stall` | `dish_id`, `canteen_id`, `stall_id` | **唯一** | 同一菜品的同一位置只允许一条；`stall_id` 为 `NULL` 时由应用层判重 |
| `idx_location_dish` | `dish_id` | 普通 | 按菜品取位置 |
| `idx_location_canteen` | `canteen_id` | 普通 | 按食堂筛选 |
| `idx_location_stall` | `stall_id` | 普通 | 按档口筛选 |
| `fk_location_dish` | `dish_id` | 外键 | `REFERENCES dish(id)`，**`ON DELETE CASCADE`** |
| `fk_location_canteen` | `canteen_id` | 外键 | `REFERENCES canteen(id)`，**`ON DELETE RESTRICT`** |
| `fk_location_stall` | `stall_id` | 外键 | `REFERENCES stall(id)`，**`ON DELETE SET NULL`** |

## 关键设计

### 位置不覆写菜品

价格 / 图片 / 描述都在 `dish`：同一道菜在多个位置售卖时完全一致。图或口味不同 ⇒ 是另一道菜（另一行 `dish`），不是同一道菜的另一个位置。

### 在售是位置级的

某个档口停售不影响同一道菜在其他档口售卖；学生端只展开 `activate = TRUE` 的位置。

### `canteen_id` 是有意反范式

`stall_id` 非空时，食堂可由档口推导（`stall.canteen_id`），`canteen_id` 属可推导冗余。保留它换取：

- 查询免一次 join；
- 能表达「食堂级位置」（`stall_id = NULL`）。

代价是必须在应用层保证：`stall_id` 非空时 `stall.canteen_id = canteen_id`，否则 `400`。

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| N→1 | `dish.id` | 所属菜品 | **`ON DELETE CASCADE`**（删菜即清位置） |
| N→1 | `canteen.id` | 所属食堂 | **`ON DELETE RESTRICT`**（有位置引用时不可删食堂） |
| N→1 | `stall.id` | 所属档口 | **`ON DELETE SET NULL`**（删档口后位置保留、档口置空） |

## 注意事项

- 同一 `dish_id + canteen_id + stall_id` 只允许一条（`stall_id` 为 `NULL` 时由应用层按 `dish_id + canteen_id` 判重）。
- 学生端按 `dish.id` 聚合展示，各在售位置在卡内并列；卡片形态见 [`ui/client/菜品首页.md`](../ui/client/菜品首页.md)。
