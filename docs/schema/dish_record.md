# dish_record — 饮食记录

**用途**：**一条 = 用户对某道菜的一次饮食记录** —— 承「去品尝」标记的**饮食流水**，是「记忆」的唯一数据源。🔴 **不再是评价分的权威来源** —— 评分真源已迁移到 [dish_rating](./dish_rating.md)（一人一菜一行，删除记忆不撤票）。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 记录 ID |
| `user_id` | BIGINT | NO | 无 | KEY, FK→user | 记录归属用户；**取自登录态，禁止信任前端传入** |
| `dish_id` | BIGINT | NO | 无 | KEY, FK→dish | 所评菜品；**必填、无默认值** |
| `wanted_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 点「去品尝」的时间 —— **决定该记录在「记忆」中的日期 / 月份归属** |
| `reaction` | TINYINT | YES | NULL | | 🔴 **已废弃（历史快照）**：评分真源迁移至 [dish_rating](./dish_rating.md) 后本列**不再读写**；存量值仅作历史留痕，当前评分一律查评分表 |
| `reacted_at` | DATETIME | YES | NULL | | 🔴 **已废弃**（随 `reaction` 一并停写） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_record_user_wanted` | `user_id`, `wanted_at` | 复合 | **「记忆」**（`WHERE user_id=? ORDER BY wanted_at DESC`，正好覆盖按月份 / 日期取数） |
| `idx_record_dish_reaction` | `dish_id`, `reaction` | 复合 | 🔴 随评分迁移**已废弃**（历史索引，聚合不再使用） |
| `idx_record_user_dish` | `user_id`, `dish_id`, `reacted_at` | 复合 | 🔴 随评分迁移**已废弃**（历史索引，一人一票去重由 `dish_rating` 唯一键天然承担） |
| `fk_record_user` | `user_id` | **外键** | `REFERENCES user(id)`，无级联 |
| `fk_record_dish` | `dish_id` | **外键** | `REFERENCES dish(id)`，**`ON DELETE CASCADE`** |

## 关键设计

### 一行 = 一次饮食记录；**一人一菜可多行**

| 项 | 口径 |
|---|---|
| 产生 | 在[美味](../func/client/A-浏览与发现/A2-美味.md)卡片上点「去品尝」⇒ **INSERT 一行** |
| 评分 | 在「记忆」卡片上评分 ⇒ **写 [dish_rating](./dish_rating.md)**（upsert / 删行），🔴 本表评分列停写 |
| 改分 | 🔴 **可就地反复修改**：改 / 清除都发生在评分表（每次同事务重算缓存列），本表不动 |
| 再次品尝 | 可**再次点「去品尝」** ⇒ **新增一行**（新的 `wanted_at`）；旧行**保留**（历史留痕）；若此前评过该菜，新记录**直接带出当前评分**（评分绑定「用户 × 菜品」，不随流水重置） |
| 因此 | 同一用户对同一菜品**可以有 N 行**（N = 去品尝次数）；**群体评价与流水无关**（见 [dish_rating](./dish_rating.md)） |

> **待补评判据**：该用户该菜**在 `dish_rating` 中无行** —— 用户点过「去品尝」但还没给结论。

### 唯一性由应用层保证（无 DB 唯一索引）

- **约束内容**：同一用户对同一菜品，**至多一条未评分记录**（`reaction IS NULL`）—— 否则「记忆」会出现同一道菜的多张未评分卡片。
- **为何不在 DB 层**：MySQL 5.7 **无部分索引**，而「已完成记录允许重复、未完成记录必须唯一」是**条件唯一**，无法用普通唯一键表达。
- **实现**：Service 层在 INSERT 前判 `EXISTS (user_id, dish_id, reaction IS NULL)`，命中即直接复用该行（幂等），不新增。

### 评分聚合口径（已迁移）

🔴 聚合真源与完整口径见 **[dish_rating](./dish_rating.md)**（一人一菜一行）与 [dish.md §评价分缓存列](./dish.md)。要点：**删除记忆不撤票**（删本表不动评分）；「清除评价」= 删评分行（整体撤票）。

## 关联与级联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| N→1 | `user.id` | 记录归属用户 | 无级联（外键 `fk_record_user`）；`user` 行永不物理删除 |
| N→1 | `dish.id` | 所评菜品 | **`ON DELETE CASCADE`** —— 菜品删除时其饮食记录随之清理（菜品不存在则评价无意义） |

## 注意事项

- **本表无 `updated_at`** —— 记录只有「标记 → 评分」两类写，`wanted_at` / `reacted_at` 已完整表达时间线；改分复用 `reacted_at`，不另设「最后修改」列。
- `reacted_at` 与 `reaction` **同批写入**（同一 UPDATE），不单独改其一。
- 划走的卡片（跳过）**不落库** —— 跳过只是浏览行为，落库会让推荐权重被曝光数据污染。
- **删除记录 = 物理删行**（未评分 / 已评分都可删）：删除已评分行会改变群体评价 ⇒ 🔴 **必须同事务重算** `dish.avg_reaction` / `reaction_count`（删掉最新一条后回退到该用户上一条有效评分）。
