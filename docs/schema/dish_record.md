# dish_record — 饮食记录

**用途**：**一条 = 用户对某道菜的一次饮食记录** —— 承「去品尝」标记与三态评分。是「记忆」的唯一数据源，也是 `dish.avg_reaction` 的权威来源。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 记录 ID |
| `user_id` | BIGINT | NO | 无 | KEY, FK→user | 记录归属用户；**取自登录态，禁止信任前端传入** |
| `dish_id` | BIGINT | NO | 无 | KEY, FK→dish | 所评菜品；**必填、无默认值** |
| `wanted_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 点「去品尝」的时间 —— **决定该记录在「记忆」中的日期 / 月份归属** |
| `reaction` | TINYINT | YES | NULL | | **三态反应**：`5` = 喜欢 ｜ `3` = 一般 ｜ `1` = 不喜欢；**`NULL` = 尚未评分**（列注释同此口径） |
| `reacted_at` | DATETIME | YES | NULL | | 评分时间；`reaction` 为 NULL 时恒 NULL；**服务端取 DB 时钟写入**（与 `wanted_at` 同一基准） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_record_user_wanted` | `user_id`, `wanted_at` | 复合 | **「记忆」**（`WHERE user_id=? ORDER BY wanted_at DESC`，正好覆盖按月份 / 日期取数） |
| `idx_record_dish_reaction` | `dish_id`, `reaction` | 复合 | **评价分聚合**（`WHERE dish_id=? AND reaction IS NOT NULL`） |
| `idx_record_user_dish` | `user_id`, `dish_id`, `reacted_at` | 复合 | **一人一菜一票**：定位同用户同菜的全部评分行，取 `reacted_at` 最新一行为有效票 |
| `fk_record_user` | `user_id` | **外键** | `REFERENCES user(id)`，无级联 |
| `fk_record_dish` | `dish_id` | **外键** | `REFERENCES dish(id)`，**`ON DELETE CASCADE`** |

## 关键设计

### 一行 = 一次饮食记录；**一人一菜可多行**

| 项 | 口径 |
|---|---|
| 产生 | 在[美味](../func/client/A-浏览与发现/A2-美味.md)卡片上点「去品尝」⇒ **INSERT 一行**（`reaction = NULL`） |
| 评分 | 在「记忆」卡片上评分 ⇒ **UPDATE 该行**（`reaction` + `reacted_at`） |
| 改分 | 🔴 **可就地反复修改**：`5` / `3` / `1` / `null`（清除）任意次改写同一行，每次同事务重算缓存列 |
| 再次品尝 | 已评分的菜可**再次点「去品尝」** ⇒ **新增一行**（新的 `wanted_at`、`reaction` 重新为空）。旧行**保留**（历史留痕） |
| 因此 | 同一用户对同一菜品**可以有 N 行**（N = 去品尝次数），各自带自己的 `wanted_at` 与评分；**群体评价只取其中最新一次有效评分** |

> **`reaction IS NULL`（未评分）** 是「记忆」里**待补评**的判据 —— 用户点过「去品尝」但还没给结论。

### 唯一性由应用层保证（无 DB 唯一索引）

- **约束内容**：同一用户对同一菜品，**至多一条未评分记录**（`reaction IS NULL`）—— 否则「记忆」会出现同一道菜的多张未评分卡片。
- **为何不在 DB 层**：MySQL 5.7 **无部分索引**，而「已完成记录允许重复、未完成记录必须唯一」是**条件唯一**，无法用普通唯一键表达。
- **实现**：Service 层在 INSERT 前判 `EXISTS (user_id, dish_id, reaction IS NULL)`，命中即直接复用该行（幂等），不新增。

### 评分聚合口径（一人一菜一票）

`dish.avg_reaction` / `reaction_count` 按 **`(user_id, dish_id)` 去重后各投一票**：

| 项 | 口径 |
|---|---|
| 有效票 | 同一用户同一菜品**只取最新一次有效评分** —— `reaction IS NOT NULL` 的行中 `reacted_at` 最大者，并列取 `id` 最大者 |
| 不计票 | `reaction IS NULL`（未评分 / 已清除）的行 |
| 清除评价 | 把最新那条清成 `NULL` ⇒ 该用户该菜**回退到上一条有效评分**参与聚合（不是整体撤票） |
| `reaction_count` | **投票用户数**（去重人数），非记录条数 |
| 实现 | `NOT EXISTS` 排除「同用户同菜更晚有效行」（MySQL 5.7 无窗口函数）；聚合索引 `idx_record_user_dish` |

口径与对账纪律见 [dish.md §评价分缓存列](./dish.md)。

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
