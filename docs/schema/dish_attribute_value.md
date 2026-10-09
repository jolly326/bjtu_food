# dish_attribute_value — 菜品描述属性取值

**用途**：某个**维度**下的可选取值（**取值字典**）。描述维度的取值经 `dish.attributes` 引用其 `id`；**系统维度（菜品种类）的取值经 `dish.meal_type_id` 引用其 `id`**。管理侧设计见 [A4 菜品属性维度与取值管理](../func/web/A-主数据维护/A4-菜品属性维度管理.md)。
**对应实体**：`com.bjtufood.dish.entity.DishAttributeValue`（已实现）

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 取值 ID（**菜品 `attributes` / `dish.meal_type_id` 引用的就是它**） |
| `dimension_id` | BIGINT | NO | 0 | KEY | 所属维度（`dish_attribute_dimension.id`；`system = 1` 者为**菜品种类字典**） |
| `label` | VARCHAR(32) | NO | `''` | | 取值中文名（展示用；**可改，改名免费**） |
| `sort_order` | INT | NO | 0 | | 组内展示顺序（升序） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（管理端列表出参 `updatedAt`） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `idx_value_dimension` | `dimension_id` | 普通 | 按维度查取值 |

> `label` **不加唯一索引** —— 「同维度下唯一」由**应用层**保证。

## 关键设计

### 名称可改 = 数据锚在 ID
描述维度的取值在 `dish.attributes` 中存的是**取值的 `id`**、菜品种类在 `dish.meal_type_id` 中存的也是**取值的 `id`**（都不是中文），故改 `label` 只改一行、**全站生效**（如「牛肉」→「牛」、「面食」→「面食粉类」）。

### 系统维度下的取值 = 菜品种类字典
`dimension_id` 指向**系统维度**（`dish_attribute_dimension.system = 1`）的行即**菜品种类字典**：菜品录入时从它下拉选取、存入 `dish.meal_type_id`；取值**被菜品引用时禁止删除**（引用判据见下）。

### 时间列
本表**无 `created_at`**；`updated_at` 只用于管理端列表展示「最近改动」。

### 删除约束
取值**被菜品引用时禁止删除**（应用层统计 `dishCount`，`> 0` 即拦截），避免菜品属性读到悬空 ID。引用判据按所属维度取：

| 所属维度 | 引用判据 |
|---|---|
| 描述维度 | `dish.attributes` 中该取值 ID 的出现次数 |
| 系统维度（菜品种类） | `dish.meal_type_id = 取值 ID` 的菜品数 |

### 取值登记入口
两处共用同一登记逻辑：
1. 管理端录入菜品时**输入新值 → 自动登记**（**仅描述维度**；菜品种类只能从字典选取）；
2. **反馈采纳**时按中文查字典，未命中**自动新建**（用户提交菜品问题反馈本身**不写库**）。

## 关联

| 方向 | 目标表 | 关系 |
|---|---|---|
| N→1 | `dish_attribute_dimension.id` | 所属维度 |
| N→n | `dish.attributes`（JSON 内引用 `id`） | 描述属性（**非外键**） |
| 1→n | `dish.meal_type_id`（**非外键**，存系统维度取值 `id`） | 菜品所属种类 |

> **不设外键** —— 与全库约定一致：JSON 内的引用无法用外键约束，关系由应用层维护。

## 注记

- 实体 `DishAttributeValue` / Mapper / 10 个管理端点均已实现；表形态见上方列定义（`id` / `dimension_id` / `label` / `sort_order` / `updated_at`，无 `created_at`、无 `value_key`）。
- **存量数据迁移**：`migrate_attribute_values_to_id` 将 `dish.attributes` 历史中文值按维度登记为本表行并就地替换为取值 ID（一次性护栏 + 转换前留档 `dish_attributes_backup_20261003`）。
- **列改名上线顺序**：线上库（TDSQL-C 5.7 内核）须**先执行** ``ALTER TABLE dish_attribute_value CHANGE `order` sort_order INT NOT NULL DEFAULT 0 COMMENT '组内展示顺序';``（该内核不支持 `RENAME COLUMN` 语法），**再部署**读取 `sort_order` 的后端版本；管理端 API 出参字段仍为 `order`，前端零改动。
