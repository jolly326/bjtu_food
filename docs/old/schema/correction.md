# correction — 菜品问题反馈（类型模型重构后）

**用途**：用户对菜品提出的**问题反馈**，由管理员核实后处置。**只存提交事实（append-only）**。
处置状态/结论/文案/人/时间**外置**在 [moderation_task](./moderation_task.md)（多态键 `(type, type_id)`，1:1 挂靠）。

**对应实体**：`com.bjtufood.correction.entity.Correction`

> 本表**无状态列** —— 「是否已处置 + 结论 + 处置文案 + 处置人」外置在 [moderation_task](./moderation_task.md)（多态键 `('correction', type_id)`）。

## 类型模型（重构后）

删除旧 `type` 二元（`field` / `gone`），按**业务层级**改为两类：

| 新 `type` | 语义 | 目标实体 | `subtype` |
|---|---|---|---|
| **`dish`** | 菜品类纠错：信息有误 + 已下架 | `dish.id` | `info` / `off_shelf` |
| **`stall`** | 档口类纠错：档口关闭 | `stall.id` | 恒 `NULL` |

- **「菜品修改 + 菜品下架」归为一类**（`dish`）：两者都针对菜品本体，管理员处置时回到 [A3 菜品管理](../func/web/A-主数据维护/A3-菜品管理.md)。
- **「档口关闭」归为另一类**（`stall`）：目标是档口，管理员处置时在 [A2 档口管理](../func/web/A-主数据维护/A2-档口管理.md) 核实。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 反馈 ID |
| `type` | VARCHAR(16) | NO | — | KEY | 枚举：`dish` / `stall` |
| `subtype` | VARCHAR(16) | YES | NULL | KEY | `dish` 型下区分：`info`（信息有误）/ `off_shelf`（已下架）；`stall` 型恒 `NULL` |
| `dish_id` | BIGINT | YES | 无 | KEY | `dish` 型目标菜品（**无外键**，删除留痕见「关键设计 §3」）；`stall` 型恒 `NULL` |
| `stall_id` | BIGINT | YES | 无 | KEY | `stall` 型目标档口（无外键，同上）；`dish` 型恒 `NULL` |
| `user_id` | BIGINT | NO | 无 | KEY, **FK**→user | 提交人；必填真实登录用户（`fk_correction_user`，无级联） |
| `name` | VARCHAR(64) | YES | NULL | | 改动后的菜名（仅 `dish`/`info` 有值） |
| `price` | INT | YES | NULL | | 改动后的现价（分）（仅 `dish`/`info` 有值） |
| `canteen_name` | VARCHAR(64) | YES | NULL | | 改动后的食堂名 |
| `stall_name` | VARCHAR(64) | YES | NULL | | 改动后的档口名（仅 `dish` 型有值） |
| `images` | JSON | YES | NULL | | 图片数组（≤3 张，全类型均可选） |
| `attributes` | JSON | YES | NULL | | 描述属性快照（仅 `dish` 型） |
| `floor` | VARCHAR(16) | YES | NULL | | 楼层快照（仅 `dish` 型；实际楼层由 A2 手动写回） |
| `note` | VARCHAR(200) | YES | NULL | | 补充说明（`stall` 型为主要说明，其余为选填） |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 提交时间 |

> 处置信息不在本表：`status` / `reply` / `reject_reason` / `handled_at` 均**外置**于 `moderation_task`（对应 `type='correction'` 的 1:1 任务行）。

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `idx_correction_dish` | `dish_id` | 普通 | 按菜品查反馈 |
| `idx_correction_stall` | `stall_id` | 普通 | 按档口查反馈 |
| `fk_correction_user` | `user_id` | 外键 | `REFERENCES user(id)`（无级联）—— 目标引用中**唯一**的外键 |

> `dish_id` / `stall_id` 不设外键（理由见「关键设计 §3」）；防重复不走唯一索引（见「关键设计 §2」）。

## 关键设计

### 1. 两类的语义（`type` + `subtype`）

- `subtype='info'`（信息有误）：`name/price/canteen_name/stall_name/attributes/floor/images` 承载改动项快照；管理端在 A3 菜品管理修改菜品。
- `subtype='off_shelf'`（已下架）：一键提交（`note`/`images` 选填）；管理端在 A3 菜品管理按需下架（可逆），不删除菜品，不级联清评价。
- `type='stall'`（档口关闭）：一键提交，`note` 承载主要说明（「搬去哪 / 关多久」）、`images` 选填；管理端在 A2 档口管理核实并迁移菜品挂靠。

### 2. 防重复提交（应用层去重，维持现状）

- `subtype='info'`：**不限次**重复提交（每次可为不同改动）——按 `(type, dish_id)` 的全表唯一索引会挡住第二位用户、并压平管理端「N 人反馈」聚合，**不采用**。
- `subtype='off_shelf'`（一键型）：同一用户对同一菜品**只计一次** —— 应用层先 `exists` 后 `insert`（键 `(user_id, dish_id)`，重复提交返回成功、不重复计数），管理端「N 人反馈已下架」聚合建立在此去重之上。
- `type='stall'`（一键型）：同款去重，键 `(user_id, stall_id)`。
- **是否升级为 DB 唯一索引**（MySQL 5.7 无部分索引，需生成列/冗余列才能表达「仅一键型限定」）：见 [待决事项清单 §2](../todo.md)。

### 3. 外键与存量迁移

- 外键只有 `fk_correction_user`（`user_id` → `user.id`，无级联）。
- `dish_id` / `stall_id` **不设外键**（普通 `KEY`，与 `feedback` / `report` 的目标列同口径）：目标行被物理删除后**反馈行必须保留**（历史痕迹，目标名以 `name` / `stall_name` 快照留痕）—— 若设 `RESTRICT` / `NO ACTION`，删除会被反馈行**直接阻断**（与 A3 菜品删除、A2 档口删除能力冲突，「删除后行保留」在 FK 下不可能成立）；`ON DELETE SET NULL` 又会抹掉「反馈的是哪一行」。故引用完整性由提交时的目标校验保证。
- 存量：`type='field'` → `type='dish'`, `subtype='info'`；`type='gone'` → `type='dish'`, `subtype='off_shelf'`。只需改 `type`/`subtype` 两列，`dish_id` / `note` / `images` 等原值保留。

## 处置方向（按 `type` + `subtype`）

| `type` + `subtype` | 管理员处置 |
|---|---|
| `dish`/`info` | 在 A3 菜品管理修改菜品资料，标记已处理 |
| `dish`/`off_shelf` | 在 A3 菜品管理下架（可逆），标记已处理 |
| `stall` | 核实档口；确已关闭 → 按 `note` 迁移 `Dish.stall_id` 到真实档口（A2），或标记「待确认」；标记已处理 |

## 关联

| 方向 | 目标表 | 关系 |
|---|---|---|
| N→1 | `dish.id` | 被反馈菜品 —— **逻辑引用（无外键，删除后留痕，见 §3）** |
| N→1 | `stall.id` | 被反馈档口 —— **逻辑引用（无外键，同上）** |
| N→1 | `user.id` | 提交人 —— **外键 `fk_correction_user`（无级联）** |
| 1—1 | `moderation_task`（`type='correction'`） | 处置状态机 —— **多态键 `(type, type_id)`，无外键**（下一步见 [待决事项清单 §7](../todo.md)） |

## 注意事项

- `attributes` 快照键 = 维度 ID，值 = 用户提交的中文原文；不参与写回菜品。
- `floor` 快照仅作记录；实际楼层由 A2 档口管理手动写回 `stall.floor`。
- `stall` 型的 `dish_id` 恒 `NULL`；`dish` 型的 `stall_id` 恒 `NULL`。
- `images` / `note` 全类型均可选（≤3 张；`note` ≤200 字），不设 `NOT NULL`。
