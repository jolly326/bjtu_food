# dish — 菜品

**用途**：菜品主体 —— 美食地图磁贴的取数来源（大图 / 名称 / 位置 / 点赞数）。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 菜品 ID |
| `stall_id` | BIGINT | NO | 无 | KEY | 所属档口；**必填、无默认值**（磁贴位置联表用） |
| `name` | VARCHAR(64) | NO | `''` | | 菜品名称 |
| `image` | VARCHAR(255) | YES | NULL | | **菜品大图**（相对路径，**单张**） |
| `status` | VARCHAR(32) | NO | `'on'` | KEY | 上架状态：`on` 在售 / `off` 已下架 |
| `like_count` | INT | NO | 0 | | **点赞数缓存列** = `COUNT(dish_rating.liked = 1)`（群体热度） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（管理端列表出参） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_dish_stall` | `stall_id` | 普通 | 按档口取菜品（管理端）与位置联表 |
| `idx_dish_status` | `status` | 普通 | 公开列表「只查在售」的过滤（`WHERE status='on'`） |

## 关键设计

### 点赞数缓存列（物化）

**`like_count` 是缓存列，不是权威数据。**

| 项 | 说明 |
|---|---|
| 口径 | 🔴 **聚合真源 = [dish_rating](./dish_rating.md)（一人一菜一行）** —— `like_count` = `COUNT(liked = 1)` |
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
| N→1 | `stall.id` | 所属档口 | **无外键** |
| 1→n | `dish_rating.dish_id` | 该菜品的点赞（真源） | 见 [dish_rating.md](./dish_rating.md) |

## 注意事项

- `image` 存**相对路径**，出参经路径工具转绝对 URL。
- `status = 'off'`（下架）对公开接口**等价于不存在**。
- `name` 在**同一档口下**的唯一性由**应用层**保证（无 DB 唯一索引）。
- 无「售罄 / 今日供应」状态（一期不做）。
