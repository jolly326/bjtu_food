# dish_attribute_value — 菜品描述属性取值

**用途**：某个**描述维度**下的可选取值（**取值字典**），经 `dish.attributes` 引用其 `id`。管理侧设计见 [A4 菜品属性维度与取值管理](../func/web/A-主数据维护/A4-菜品属性维度管理.md)。
**对应实体**：`com.bjtufood.dish.entity.DishAttributeValue`（已实现）

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 取值 ID（**菜品 `attributes` 引用的就是它**） |
| `dimension_id` | BIGINT | NO | 0 | KEY | 所属维度（`dish_attribute_dimension.id`） |
| `name` | VARCHAR(32) | NO | `''` | | 取值中文名（展示用；**可改，改名免费**） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（管理端列表出参 `updatedAt`） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `idx_value_dimension` | `dimension_id` | 普通 | 按维度查取值 |

> `name` **不加唯一索引** —— 「同维度下唯一」由**应用层**保证。

## 关键设计

### 名称可改 = 数据锚在 ID
取值在 `dish.attributes` 中存的是**取值的 `id`**（不是中文），故改 `name` 只改一行、**全站生效**（如「牛肉」→「牛」）。

### 时间列与顺序
本表**无 `created_at`**；`updated_at` 只用于管理端列表展示「最近改动」。
**无排序列** ⇒ 同维度下的取值（端上与管理端「管理取值」抽屉）一律按 `id` 升序排列。

### 删除约束
取值**被菜品引用时禁止删除**（应用层统计 `dishCount`，即 `dish.attributes` 中该取值 ID 的出现次数，`> 0` 即拦截），避免菜品属性读到悬空 ID。

### 取值登记入口
两处共用同一登记逻辑：
1. 管理端录入菜品时**输入新值 → 自动登记**；
2. **反馈采纳**时按中文查字典，未命中**自动新建**（用户提交菜品问题反馈本身**不写库**）。

## 关联

| 方向 | 目标表 | 关系 |
|---|---|---|
| N→1 | `dish_attribute_dimension.id` | 所属维度 |
| N→n | `dish.attributes`（JSON 内引用 `id`） | 描述属性（**非外键**） |

> **不设外键** —— 与全库约定一致：JSON 内的引用无法用外键约束，关系由应用层维护。

## 注记

- 实体 `DishAttributeValue` / Mapper / 管理端点均已实现；表形态见上方列定义（`id` / `dimension_id` / `name` / `updated_at`，无 `created_at`、无 `value_key`、无排序列）。
- **存量数据迁移**：`migrate_attribute_values_to_id` 将 `dish.attributes` 中文值按维度登记为本表行并就地替换为取值 ID（一次性护栏 + 转换前留档 `dish_attributes_backup_20261003`）。
