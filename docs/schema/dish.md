# dish — 菜品

**用途**：菜品主体 —— 美食地图磁贴的取数来源（大图 / 名称 / 位置 / 点赞数）。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 菜品 ID |
| `canteen_id` | BIGINT | NO | 无 | KEY, FK→canteen | 所属食堂；**必填** —— 最小定位信息，学生端据此能找到地方（`ON DELETE RESTRICT`） |
| `stall_id` | BIGINT | **YES** | `NULL` | KEY, FK→stall | 所属档口；**可空** —— 档口未知 / 暂不指定时留空（`ON DELETE SET NULL`） |
| `floor` | VARCHAR(8) | **YES** | `NULL` | | 楼层（受控字典：`负一层｜一层｜二层｜三层｜四层`，与 `stall.floor` 同字典）；无档口时也能带楼层 |
| `name` | VARCHAR(64) | NO | `''` | | 菜品名称 |
| `image` | VARCHAR(255) | YES | NULL | | **菜品大图**（相对路径，**单张**） |
| `activate` | BOOLEAN | NO | `TRUE` | KEY | 上架状态：`TRUE` 在售 / `FALSE` 已下架 |
| `like_count` | INT | NO | 0 | | **点赞数缓存列** = `COUNT(dish_rating.liked = 1)`（群体热度） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（管理端列表出参） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_dish_canteen` | `canteen_id` | 普通 | 按食堂取菜品（管理端）与位置联表 |
| `idx_dish_stall` | `stall_id` | 普通 | 按档口取菜品（管理端） |
| `idx_dish_activate` | `activate` | 普通 | 公开列表「只查在售」的过滤（`WHERE activate = TRUE`） |
| `uq_dish_canteen_name` | `(canteen_id, name)` | 唯一 | **同一食堂下菜品名唯一**（替换失效的「同一档口下唯一」） |

## 关键设计

### 点赞数缓存列（物化）

**`like_count` 是缓存列，不是权威数据。**

| 项 | 说明 |
|---|---|
| 口径 | 🔴 **聚合真源 = [dish_rating](./dish_rating.md)（一人一菜一行）** —— `like_count` = `COUNT(liked = TRUE)` |
| 维护方 | 点赞 / 取消后**同事务同步重算** |
| 零点赞 | `like_count = 0`（不兜底成非零值，避免污染排序 / 展示） |

🔴 **对账纪律**：在线重算只在**应用写路径**（点赞 / 取消）触发；**任何直连库改或删 `dish_rating` 的操作都必须同时重算受影响菜品** —— 否则用户会在磁贴 / 弹窗上直接看到错误点赞数，而系统不报错、无告警。

### 不设的列（零消费即删）

| 列 | 为何不设 |
|---|---|
| `description` | 无菜品详情页，详情仅在底部弹窗承载，不落库长描述 |
| `attributes` | 无详情页、无属性筛选；描述维度体系整体不引入 |
| `category_id` | 筛选维度只有**食堂**（运营端），学生端不做种类筛选 |
| `images`（多图） | 磁贴只需一张大图 |
| `original_price` | 无折扣 / 划线价需求 |
| `avg_reaction` / 风味评级相关列 | 不落库 —— 群体指标统一走 `dish.like_count`（点赞数），不另存风味评级 |

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| N→1 | `canteen.id` | 所属食堂（**必填**） | **`ON DELETE RESTRICT`**（有菜品引用的食堂不能删除） |
| N→1 | `stall.id` | 所属档口（**可空**） | **`ON DELETE SET NULL`**（档口删除后菜品保留、档口置空） |
| 1→n | `dish_rating.dish_id` | 该菜品的点赞（真源） | 见 [dish_rating.md](./dish_rating.md) |

## 注意事项

- `image` 存**相对路径**，出参经路径工具转绝对 URL。
- `activate = FALSE`（下架）对公开接口**等价于不存在**。
- `name` 在**同一食堂下**唯一（唯一索引 `uq_dish_canteen_name`）。
- 🔴 **归属一致性**：`stall_id` 非空且其档口有 `canteen_id` 时，`dish.canteen_id` 必须等于该档口的 `canteen_id`（应用层校验，违者 `400`）—— 杜绝「A 食堂的档口挂到 B 食堂的菜」。
- 楼层取值优先级：出参 `floor = COALESCE(dish.floor, stall.floor)`，缺失时为空串。
- 无「售罄 / 今日供应」状态（一期不做）。
