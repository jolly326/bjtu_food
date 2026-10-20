# schema — 数据库设计

**设计真源**：本目录（`schema/*.md`）—— **DDL 结构以各表文档的「列定义 / 索引」为准**
**建库方式**：按各表文档的列定义**手工建表**；本仓库**不维护 `schema.sql` 初始化脚本**
**引擎**：InnoDB ｜ **字符集**：utf8mb4 / utf8mb4_general_ci ｜ **生产**：MySQL 5.7（TDSQL-C）

> 本目录是**库结构的唯一真源** —— 表 / 列 / 类型 / 约束 / 索引 / 表间关系均以各表文档为准。
> **变更流程**：先改对应表文档 → 再在数据库执行 `ALTER` / `CREATE` → 两者必须一致。

## 表清单（9 张）

| 表 | 用途 | 文档 |
|---|---|---|
| `canteen` | 食堂（美味页筛选项） | [canteen.md](./canteen.md) |
| `stall` | 档口（菜品位置归属） | [stall.md](./stall.md) |
| `poster` | 海报（运营内容，**档口绑定可选**，独立上下架与固定序） | [poster.md](./poster.md) |
| `dish` | 菜品（含评价分缓存列） | [dish.md](./dish.md) |
| `dish_rating` | **菜品评分（口碑真源）**：一人一菜一行，与流水解耦 | [dish_rating.md](./dish_rating.md) |
| `dish_record` | 饮食记录（「去品尝」流水；评分已迁出） | [dish_record.md](./dish_record.md) |
| `dish_issue` | 菜品纠错提交（只含选项） | [dish_issue.md](./dish_issue.md) |
| `dish_issue_option` | 菜品纠错选项字典 | [dish_issue_option.md](./dish_issue_option.md) |
| `user` | 学生（openid 静默识别） | [user.md](./user.md) |
| `admin` | 运营端账号 | [admin.md](./admin.md) |

## 实体关系

```
canteen 1 ── n stall ─┬─ 0..n poster（档口可选，删档口 SET NULL）
                      ├─ n dish ─┬─ n dish_record ── n 1 user（流水；引用即禁删）
                      │          └─ n dish_rating ──── n 1 user（口碑真源，一人一菜一行）
                      └──────── n dish_issue ───── n 1 user
                                 └── n 1 dish_issue_option
admin（独立账号体系，与 user 无关）
```

## 命名与类型约定

| 对象 | 规则 | 例 |
|---|---|---|
| 表名 | 小写下划线，**单数** | `dish_record` |
| 列名 | 小写下划线 | `avg_reaction` |
| 索引 | `idx_<表>_<用途>` / `uk_<表>_<列>` | `idx_record_user_wanted` |
| 状态列 | 存**可读字符串**而非 TINYINT | `status = 'on'` |
| 金额 | `INT`，**单位「分」** | 12.00 元存 `1200` |
| 评分 | 原始反应 `TINYINT`（`5` / `3` / `1`）；展示值 `DECIMAL(3,1)` | 见 [dish_rating.md](./dish_rating.md) |
| 图片 | `VARCHAR` 存**相对路径**（单图一列） | 出参转绝对 URL |
| 时间 | `DATETIME`，`NOT NULL DEFAULT CURRENT_TIMESTAMP` | 时区 Asia/Shanghai |
| 更新时间 | `ON UPDATE CURRENT_TIMESTAMP` | 免应用层维护 |

## 时间戳写入来源：唯一来源 = **DB 时钟**

`created_at` / `updated_at` 一律由 MySQL 写入 —— INSERT 走 `DEFAULT CURRENT_TIMESTAMP`，UPDATE 走 `ON UPDATE CURRENT_TIMESTAMP`。**应用层不写这两列**，也不用 ORM 自动填充。

**无库默认值的列**同样取 **DB 时钟**：`dish_rating.reacted_at`、`dish_issue.handled_at` 由服务端在同事务内写 `NOW()`（不用应用本地时间 —— 否则容器时区与库时区不一致时，同一行会出现两种时区的时间）。

> 语义边界：`ON UPDATE` 只在**行确实变化**时刷新 ⇒ 幂等写不刷新 `updated_at`。

## 派生数据与反范式清单

> **判据**：本库以 **3NF 为基线**，下列各处是**登记在案的有意反范式**。每处必须写明**权威来源**与**对账手段** ——「有冗余、无对账」不成立。

| 载体 | 冗余 / 派生内容 | 权威来源 | 维护与对账 | 风险 |
|---|---|---|---|---|
| `dish.avg_reaction` / `reaction_count` | 评分聚合缓存（物化列） | [`dish_rating`](./dish_rating.md)（一人一菜一行） | 提交后重算；每日全量对账兜底 | 🔴 **唯一兜底是对账任务**：直连库改评分数据**必须触发重算**，否则缓存停旧值、用户直接看到错误评价分且系统无告警 |

## 库级约束的边界（由应用层承担的部分）

> MySQL 5.7 对 `CHECK` 只解析、不生效，且本库刻意少用外键与唯一索引 —— 下列约束**不在 DB 层**。

| 边界 | 清单 | 为何不在 DB 层 |
|---|---|---|
| **无外键的引用关系** | `dish.stall_id` | 档口是管理端维护的实体，有独立的合并 / 清理语义 |
| **由应用层保证的唯一性** | 同一用户对同一菜品的**未评分记录**至多一条（`dish_record`）；同一用户 + 同一菜品 + 同一选项的**未处理记录**至多一条（`dish_issue`）；`canteen.name`；`stall.name`（同食堂下）；`dish.name`（同档口下） | 条件唯一（MySQL 5.7 无部分索引）/ 字典类命名去重 |
| **可空列不作哨兵** | `dish_issue.handled_at = NULL` = 未处理；评分「未评」= **`dish_rating` 无行**（非 NULL 哨兵） | 用 `NULL` / 缺行表达「尚未发生」 |
| **枚举值域** | `dish.status` · `poster.status`（`on` / `off`） | 存可读字符串是既定口径；脏值由应用层拦截 |
| **受控字典不在库内** | `stall.floor`（值即汉字：`负一层`…`四层`） | 低频封闭枚举；新增 / 改名需「先迁移数据、再改两端」 |

## 变更流程

1. **先改本目录对应表文档**（列 / 索引 / 约束）—— 文档是唯一真源
2. **再在数据库执行**对应 `ALTER` / `CREATE`（**直连远程库**，凭据取 `server/.env`）
3. 两边必须一致；发现不一致时**以数据库实况为准并立即回写文档**
4. 若涉及出参字段 → 同步 [`api/`](../api/README.md)
5. 🔴 **改数据必须带派生列重算**：任何影响 `dish_rating` 的直连库写（`INSERT` / `UPDATE` / `DELETE`），**必须同时重算受影响菜品的 `dish.avg_reaction` / `reaction_count`**
