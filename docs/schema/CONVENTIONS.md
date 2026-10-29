# schema — 数据库设计

**设计真源**：本目录（`schema/*.md`）—— **DDL 结构以各表文档的「列定义 / 索引」为准**
**建库方式**：按各表文档的列定义**手工建表**；本仓库**不维护 `schema.sql` 初始化脚本**
**引擎**：InnoDB ｜ **字符集**：utf8mb4 / utf8mb4_general_ci ｜ **MySQL**：8.0（本地与 TDSQL-C 生产同版本）

> 本目录是**库结构的唯一真源** —— 表 / 列 / 类型 / 约束 / 索引 / 表间关系均以各表文档为准。
> **变更流程**：先改对应表文档 → 再在数据库执行 `ALTER` / `CREATE` → 两者必须一致。

## 表清单（8 张）

| 表 | 用途 | 文档 |
|---|---|---|
| `canteen` | 食堂 | [canteen.md](./canteen.md) |
| `stall` | 档口（菜品位置归属） | [stall.md](./stall.md) |
| `dish` | 菜品（含点赞数缓存列） | [dish.md](./dish.md) |
| `dish_rating` | **菜品点赞（真源）**：一人一菜一行 | [dish_rating.md](./dish_rating.md) |
| `dish_issue` | 菜品纠错提交（只含选项） | [dish_issue.md](./dish_issue.md) |
| `dish_issue_option` | 菜品纠错选项字典 | [dish_issue_option.md](./dish_issue_option.md) |
| `user` | 学生（openid 静默识别） | [user.md](./user.md) |
| `admin` | 运营端账号 | [admin.md](./admin.md) |

## 实体关系

```
canteen 1 ── n stall ─┬─ n dish ─── n dish_rating ──── n 1 user（点赞真源，一人一菜一行）
                      └──────── n dish_issue ───── n 1 user
                                 └── n 1 dish_issue_option
admin（独立账号体系，与 user 无关）
```

## 命名与类型约定

| 对象 | 规则 | 例 |
|---|---|---|
| 表名 | 小写下划线，**单数** | `dish_rating` |
| 列名 | 小写下划线 | `like_count` |
| 索引 | `idx_<表>_<用途>` / `uk_<表>_<列>` | `uk_rating_user_dish` |
| 状态列 | 存 **BOOLEAN** | `activate = TRUE` |
| 金额 | `INT`，**单位「分」** | 12.00 元存 `1200` |
| 点赞 | `liked` `TINYINT(1)`（`1` = 已赞，`0` = 已取消） | 见 [dish_rating.md](./dish_rating.md) |
| 图片 | `VARCHAR` 存**相对路径**（单图一列） | 出参转绝对 URL |
| 时间 | `DATETIME`，`NOT NULL DEFAULT CURRENT_TIMESTAMP` | 时区 Asia/Shanghai |
| 更新时间 | `ON UPDATE CURRENT_TIMESTAMP` | 免应用层维护 |

## 时间戳写入来源：唯一来源 = **DB 时钟**

`created_at` / `updated_at` 一律由 MySQL 写入 —— INSERT 走 `DEFAULT CURRENT_TIMESTAMP`，UPDATE 走 `ON UPDATE CURRENT_TIMESTAMP`。**应用层不写这两列**，也不用 ORM 自动填充。

**无库默认值的列**同样取 **DB 时钟**：`dish_issue.handled_at` 由服务端在同事务内写 `NOW()`（不用应用本地时间 —— 否则容器时区与库时区不一致时，同一行会出现两种时区的时间）。`dish_rating.updated_at` 由 `ON UPDATE CURRENT_TIMESTAMP` 自动维护，或由服务端在点赞/取消事务内显式写入。

> 语义边界：`ON UPDATE` 只在**行确实变化**时刷新 ⇒ 幂等写不刷新 `updated_at`。

## 派生数据与反范式清单

> **判据**：本库以 **3NF 为基线**，下列各处是**登记在案的有意反范式**。每处必须写明**权威来源**与**对账手段** ——「有冗余、无对账」不成立。

| 载体 | 冗余 / 派生内容 | 权威来源 | 维护与对账 | 风险 |
|---|---|---|---|---|
| `dish.like_count` | 点赞数缓存列（物化） | [`dish_rating`](./dish_rating.md)（一人一菜一行） | 提交后同事务重算；每日全量对账兜底 | 🔴 **唯一兜底是对账任务**：直连库改点赞数据**必须触发重算**，否则缓存停旧值、用户直接看到错误点赞数且系统无告警 |

## 库级约束的边界（由应用层承担的部分）

> 本库不依赖 `CHECK`，**字典类命名去重 / 值域**约束统一由应用层承担 —— 下列约束**不在 DB 层**。
> 实体间 N→1 引用一律在 DB 层建外键（清单见各表文档「关联 / 索引」）：删档口受 `fk_dish_stall` 的 `RESTRICT` 保护 —— 有菜品引用时不可删。

| 边界 | 清单 | 为何不在 DB 层 |
|---|---|---|
| **由应用层保证的唯一性** | `canteen.name`；`stall.name`（同食堂下）；`dish.name`（同档口下） | 字典类命名去重 |
| **可空列不作哨兵** | `dish_issue.handled_at = NULL` = 未处理；未赞 = **`dish_rating` 无行或 `liked = 0`**（非 NULL 哨兵） | 用 `NULL` / 缺行表达「尚未发生」 |
| **枚举值域** | `dish.activate`（`TRUE` / `FALSE`） | 布尔值，应用层校验 |
| **受控字典不在库内** | `stall.floor`（值即汉字：`负一层`…`四层`） | 低频封闭枚举；新增 / 改名需「先迁移数据、再改两端」 |

## 变更流程

1. **先改本目录对应表文档**（列 / 索引 / 约束）—— 文档是唯一真源
2. **再在数据库执行**对应 `ALTER` / `CREATE`（**直连远程库**，显式使用 `server/.env.production` 的 `DATABASE_URL`；`server/.env.development` 连本地开发库）
3. 两边必须一致；发现不一致时**以数据库实况为准并立即回写文档**
4. 若涉及出参字段 → 同步 [`api/`](../api/CONVENTIONS.md)
5. 🔴 **改数据必须带派生列重算**：任何影响 `dish_rating` 的直连库写（`INSERT` / `UPDATE` / `DELETE`），**必须同时重算受影响菜品的 `dish.like_count`**
