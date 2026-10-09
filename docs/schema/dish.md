# dish — 菜品

**用途**：菜品主体，列表 / 详情 / 菜品问题反馈 / 评价关联的载体
**对应实体**：`com.bjtufood.dish.entity.Dish`
**结构真源**：本文档「列定义 / 索引」节（即 `CREATE TABLE dish` 的等价描述）

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 菜品 ID |
| `stall_id` | BIGINT | NO | 0 | KEY | 所属档口 ID（0 = 尚未归入档口） |
| `name` | VARCHAR(64) | NO | `''` | | 菜品名称 |
| `price` | INT | NO | 0 | | **现价，单位「分」，已含折扣**；唯一价格数据源 |
| `original_price` | INT | YES | NULL | | **原价，单位「分」**；`original_price > price` 即为有折扣，NULL = 无折扣 |
| `description` | VARCHAR(512) | YES | NULL | | 菜品描述 |
| `images` | VARCHAR(1024) | YES | NULL | | 多图 JSON 数组（相对路径）；**首图作封面** |
| `attributes` | JSON | YES | NULL | | 描述属性对象：键 = **描述维度 ID**（[dish_attribute_dimension](./dish_attribute_dimension.md) 的 `id`，JSON 内为字符串形态），值 = **取值 ID**（`single` 为数字 / `multi` 为数字数组）；出参时服务端翻译为中文；仅含该菜实际拥有的维度，**不含系统维度**。**仅详情展示用、不参与列表筛选**（筛选由 `meal_type_id` 单值承担）|
| `meal_type_id` | BIGINT | YES | NULL | KEY | 菜品**种类 ID**（**单值**，值域 = **系统维度（菜品种类）** 下的取值 [dish_attribute_value](./dish_attribute_value.md) 的 `id`）；后台录入必填，公开接口不出参 |
| `status` | VARCHAR(32) | NO | `'on'` | KEY | 上架状态：`on` 在售 / `off` 已下架 |
| `view_count` | INT | NO | 0 | | **只读历史列**：不写入、不出参；浏览量由 [`dish_view_log`](./dish_view_log.md) 明细日志承载 |
| `avg_rating` | DECIMAL(3,2) | YES | NULL | | **缓存列**（见下）；**零评价为 NULL**，公开出参兜底下发 `5.0` |
| `rating_count` | INT | NO | 0 | | **缓存列**，评价数；**不出公开出参**（保持列表 8 字段 / 详情 11 字段，可信度由「评价区按星级筛选」承载） |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 创建时间 |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（**唯一来源 = DB 时钟**，见 [README §时间戳写入来源](./README.md)；任意列变化即刷新 —— 含 `avg_rating` / `rating_count` 重算 ⇒ 管理端列表按本列排序时会被评价活动重排） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_dish_stall` | `stall_id` | 普通 | 按档口查菜品（档口管理页） |
| `idx_dish_meal_type` | `meal_type_id` | 普通 | 按种类取数（首页「种类」视图筛选、管理端种类筛选与引用计数） |
| `idx_dish_status` | `status` | 普通 | 公开列表「只查在售」的过滤（`WHERE status='on'`）|

> **关于浏览量与排序**：**本表无「热度排序」** —— 7 个视图走**会话伪随机序**（各视图的排序口径存 [`dish_filter_view.sort_kind`](./dish_filter_view.md)），各排序分支一律以 `d.id` 作决胜键保证全序稳定。
> `idx_dish_status` 只服务 `status` 过滤 —— 浏览量由 `dish_view_log` 的 30 天窗口承载、评分由 `avg_rating` / `rating_count` 缓存列承载，排序与筛选均不读这三列，故索引不涉及它们。浏览量由 [`dish_view_log.md`](./dish_view_log.md) 的 **30 天滚动窗口明细**承载，仅供管理端展示、**不参与排序**。
> 三路 `%keyword%` 模糊匹配同样无法用索引（前缀通配）。二者是各自独立的性能约束，见各 feature 文档的登记。
> **首页主列表 / 猜你喜欢的 `CRC32` 会话伪随机序**同样是不可索引的计算表达式排序（`EXPLAIN` 实测 `type=ALL` + `Using filesort`）——按 `dish` 当前规模（**25 行**）判为**已核实的已知取舍**，触发条件与候选方案见 [README「已知取舍」](../README.md)。

## 派生与缓存列

**`avg_rating` / `rating_count` 是缓存列，不是权威数据。**

| 项 | 说明 |
|---|---|
| 口径 | 仅统计 `is_hidden = 0` 的评价 |
| 维护方 | `ReviewSubmittedEvent` → `RatingUpdateListener`（`AFTER_COMMIT`，**提交后同步重算**）；`DishRatingReconcileTask` 每日对账兜底 |
| 一致性 | **提交后本人视角强一致**——重算在提交响应返回前完成，提交者重拉详情即读到新值 |
| 为何缓存 | 列表页需对每行展示均分，逐行实时聚合不可接受 |
| 零评价 | **`avg_rating` 为 NULL 而非 0**，用于区分「无评价」与「均分 0」两种不同事实 |

⚠️ **修改这两列前必须确认重算链路**——直接写入会与重算链路互相覆盖。

### 🔴 零评价时 `avg_rating` 为 NULL（展示兜底只在出参层）

| 项 | 口径 |
|---|---|
| **存储** | 零评价 ⇒ **NULL**（权威事实，保持「无评价」与「均分 0」可区分） |
| **公开出参** | 展示兜底为 `5.0`、**不落库**（契约见 [api/client/dishes.md](../api/client/dishes.md)） |
| **排序** | 评分**不参与排序**（7 个视图走会话伪随机序），故兜底值无排序消费方 |

> ⚠️ **为什么必须分离**：若把 `5.0` 落库或直接送进排序公式，零评价菜品将凭空获得 `5.0 × 20 = 100` 分热度 —— 冷启动阶段大量菜品零评价会使**首页几乎全部由「无人吃过的菜」占据**，且与真实评价排序混淆。**展示兜底值只存在于出参，不存在于存储与排序。**

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| N→1 | `stall.id` | 所属档口（食堂经 stall 间接关联） | 无外键，`stall_id = 0` 为未归入档口 |
| 1→n | `review.dish_id` | 该菜品的评价列表 | **ON DELETE CASCADE** —— 菜品删除时级联清理其评价（应用层 `ReviewDishCascadeListener` 先删，外键兜底） |

> `dish_correction.dish_id` **不设外键**：菜品删除后反馈记录**有意保留**（它是「用户反馈过什么」的历史痕迹，非菜品附属数据）。

## 不出参的内部列

`meal_type_id` / `view_count` / `status` —— 端上零消费，按「零消费即删」不出现在任何公开接口响应中。

需要这些值的后台管理端（`DishAdminVO`）走 `/admin/**`，其字段集与公开接口不同。

## 注意事项

- **`images` 存 VARCHAR 而非 JSON**：相对路径数组以 JSON 字符串存入 VARCHAR(1024)；出参经 `ImageUrlUtil` 转绝对 URL（`DishListItemVO.coverImage` 为其**派生值**——取首图）。
- **`attributes` 的键 = 描述维度 ID**（JSON 内为字符串形态的十进制 `dish_attribute_dimension.id`，`system = 0`）。新增描述维度只需向该表插一行，**免 ALTER、免发版**。
- **`attributes` 引用取值字典**（[dish_attribute_value](./dish_attribute_value.md)）：值为**取值 ID**，出参翻译为中文；编辑候选来自取值字典。**系统维度（菜品种类）不得出现在 `attributes`** —— 提交时携带系统维度 ID → `400`。
- **`meal_type_id` 的值域来自系统维度（菜品种类）下的取值**（[dish_attribute_value](./dish_attribute_value.md)，`system = 1` 的维度），**不是数据库枚举**；被首页视图（[A6](../func/web/A-主数据维护/A6-首页筛选视图管理.md)）以 `mealTypeId ∈ {…}` 条件引用（条件值 = 取值 ID）。
- **`original_price` 促销价字段不属本表**：`price` 是唯一价格数据源，不存在第三个价格字段。
