# review — 评价

**用途**：用户对菜品的评价。公开列表、我的评价、评分聚合均基于本表。
**对应实体**：`com.bjtufood.review.entity.Review`

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 评价 ID |
| `user_id` | BIGINT | NO | 0 | KEY | 评价者；取自 JWT，**禁止信任前端传入** |
| `dish_id` | BIGINT | NO | 0 | KEY, FK→dish | 所属菜品 |
| `rating` | INT | NO | 0 | | 评分 **1~5** |
| `content` | VARCHAR(512) | YES | NULL | | 评价文字；可只打星不写字（底层允许 NULL，出参空串兜底） |
| `images` | VARCHAR(1024) | YES | NULL | | 配图 JSON 数组（COS 绝对地址），≤3 张 |
| `is_hidden` | TINYINT | NO | 0 | | `0` 可见 / `1` 被管理员隐藏 |
| `hidden_note` | VARCHAR(200) | YES | NULL | | 隐藏附注（管理员**可选**填写，随回执下发给作者）；`is_hidden = 0` 时为 NULL |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 发表时间；🔴 **重复提交保留原值**（覆盖不改时间 ⇒ 评价位置固定，见下「关键设计」） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `uk_review_user_dish` | `user_id`, `dish_id` | **唯一** | **一人一菜一条评价** |
| `idx_review_dish_visible` | `dish_id`, `is_hidden`, `created_at` | 复合 | **菜品详情评价列表**（`WHERE dish_id=? AND is_hidden=0 ORDER BY created_at DESC`，无 filesort） |
| `idx_review_user_visible` | `user_id`, `is_hidden`, `created_at` | 复合 | **「我的评价」**（`WHERE user_id=? AND is_hidden=0 ORDER BY created_at DESC`） |

> 索引设计说明：单列 `idx_review_user` 已由复合索引 `idx_review_user_visible` 取代（前缀即 `user_id`）；`idx_review_dish` **保留** —— 外键 `fk_review_dish`（`review.dish_id → dish.id`）要求该列上存在索引，不可删除。
> **本表无 `updated_at`** —— 「管理端列表统一带 `updatedAt`」口径的**明确例外**：`created_at` **只表达「首次发表时间」**，覆盖更新**不刷新**它（见下「关键设计」）。

## 关键设计

### 唯一键决定「重复提交 = 覆盖」
`uk_review_user_dish` 使「同一用户对同一菜品」只能存在一行。因此重复提交走 **UPDATE 而非 INSERT**：

| 字段 | 覆盖时的行为 |
|---|---|
| `rating` / `content` / `images` | 整体重写 |
| `created_at` | **保留原值** —— 🔴 首次评价时间即固定位置，反复修改**无法把自己刷到列表顶部** |
| `is_hidden` | **重置为 0** —— 重新评价即恢复可见 |

**次数不限**（一人一菜只有一票，改多少次也只是一票）。端上不区分首评 / 重评。

> 🔴 **不设提交限频**：防「反复重提刷位置」由「`created_at` 保留原值」承担 —— 改评价不改变 `created_at`，故在时间倒序下位置不动，不需要额外的 24h 限频（限频反而会阻断正当的「菜换做法了，改一下」）。

并发兜底：应用层先 `selectCount` 判断首评，但两个并发请求可能同时判定为「首评」，由唯一键拦截后者 → 返回「您已评价过该菜品」。

### `is_hidden` 的语义
- 唯一可见性判据，**客户端可见性 = `is_hidden = 0`**
- 公开列表与「我的评价」**同口径** —— 被隐藏的评价**对作者本人也不返回**
- 不设「已下架」态；管理员可事后隐藏（举报 → 处置通道）
- **隐藏 / 删除会投递站内回执**（登录级）；隐藏可附 `hidden_note`（可选文案）；回执失败**不阻塞**处置

### 评分聚合口径
**仅统计 `is_hidden = 0` 的评价**，结果写入 `dish.avg_rating` / `dish.rating_count` 两个**缓存列**（见 [dish.md](./dish.md#派生与缓存列)）。

## 关联与级联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| N→1 | `dish.id` | 所属菜品 | **`ON DELETE CASCADE`**（外键名 `fk_review_dish`） |
| N→1 | `user.id` | 评价者 | 无外键 |

**菜品删除的级联是两级防护**：

1. **应用层**：`DishDeletedEvent` → `ReviewDishCascadeListener` 在**菜品删除事务内同步**删除评价
2. **数据库**：外键 `ON DELETE CASCADE` 兜底

两级方向一致（都删），正常路径下外键命中 0 行；只有应用层失效时外键才生效。

## 注意事项

- **无 `dish_id` 之外的菜品快照** —— 菜品改名后，评价列表里的菜名随 JOIN 实时变化，不保留历史名称。
- **物理删除**：用户删除评价即物理删行（无投票关联数据需清理）。
- `images` 存 `VARCHAR` 而非 `JSON`：与 `dish.images` 一致；出参经 `ImageUrlUtil` 转绝对地址。

接口契约见 [api/client/dishes.md](../api/client/dishes.md#get-dishesidreviews)（公开列表）、`POST /dishes/{id}/reviews`（提交）、[api/client/reviews.md](../api/client/reviews.md#delete-reviewsid)（删除）、[api/client/my.md](../api/client/my.md#get-myreviews)（我的评价）。
