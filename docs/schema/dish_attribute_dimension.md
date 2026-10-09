# dish_attribute_dimension — 菜品描述属性维度

**用途**：定义菜品可有哪些描述维度（如「菜品种类」「原料」「口味」「冷热」）。**表驱动，新增维度免 ALTER、免发版。** 取值见同库 [dish_attribute_value](./dish_attribute_value.md)。
**对应实体**：`com.bjtufood.dish.entity.DishAttributeDimension`

> 维度分两类：**系统维度**（`system = 1`，至多一行，当前 = 「菜品种类」，取值被 `dish.meal_type_id` 引用）与**描述维度**（`system = 0`，取值写入 `dish.attributes`）。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 维度 ID；**唯一锚点**（`dish.attributes` JSON 的键 + 纠错快照键） |
| `name` | VARCHAR(32) | NO | `''` | | 维度中文名（端上直接渲染，如「原料」；**可改，改名免费**） |
| `value_type` | VARCHAR(16) | NO | `'single'` | | `single` 单值 / `multi` 多值 |
| `system` | TINYINT | NO | 0 | | **1 = 系统维度**（当前仅「菜品种类」一行）；0 = 普通描述维度 |
| `sort_order` | INT | NO | 0 | | 展示顺序（升序） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（管理端列表出参 `updatedAt`） |

## 索引

| 索引名 | 列 | 类型 |
|---|---|---|
| `PRIMARY` | `id` | 主键 |

> 本表**无 `created_at`**；`updated_at` 只用于管理端列表展示「最近改动」。
> **「至多一行为 `system = 1`」是数据不变量**：系统维度行由建库维护、应用层**不提供新增 / 删除入口**（管理端只能改其 `name`），故不存在第二条写入路径。建库后用 `SELECT COUNT(*) FROM dish_attribute_dimension WHERE system = 1` 核验（应为 `1`）。

## 关键设计

### 系统维度：菜品种类（`system = 1`）

「菜品种类」是一个**真正的属性维度**，只是取值不由 `dish.attributes` 承载：

| 项 | 口径 |
|---|---|
| 取值域 | 该维度下的 [dish_attribute_value](./dish_attribute_value.md) 行（套餐盖饭 / 家常小炒 / 面食粉类 / 香锅干锅 / 风味小吃 / 汤饮甜品） |
| 存储 | `dish.meal_type_id` 存取值 `id`（**独立列 + 索引**）；**不写入 `dish.attributes`**（同一事实不进两处） |
| `value_type` | 恒为 `single`（一道菜恰属一个种类，互斥、必填） |
| 保护规则 | **不可删**、**不可改 `value_type`**；其取值**被菜品引用时不可删**；菜品保存时校验「种类取值属于该系统维度」 |
| 管理入口 | 管理端「分类」抽屉（`/admin/dish-categories`，系统维度取值的**别名面**，见 [web/categories.md](../api/web/categories.md)）与 A4 取值管理均可读写同一批行 |

> **独立列是唯一同时满足「语义 + 性能」的形态**：
> - **语义**：种类需**互斥 + 必填**（一道菜恰属一种、后台录入必填）；普通描述维度不具备该约束（`value_type` 可 `multi`、取值可空），塞进 JSON 会丢掉「必填 / 互斥」这一事实。
> - **性能**：JSON 内的值无法建索引（首页按种类取数会退化为全表扫描），且 MySQL 生成列不能引用其它表。
>
> 为避免「同一事实两处」，系统维度**不出现**在任何菜品的 `attributes` 中，写入 `attributes` 时携带系统维度 ID → `400`。

### 表驱动 = 新增维度零发版
新增一个描述维度的完整流程：向本表 **INSERT 一行**（`name` / `value_type` / `sort_order`，`system = 0`）——**免 ALTER、免发版、端上零改代码**。

### 数据锚在 ID，`dish.attributes` 的键 = 维度 ID
- `dish.attributes` JSON 对象的**键 = 维度 `id`**（JSON 键为字符串形态的十进制 ID），**仅描述维度**；
- 后端据此拼装 `DishDetailVO.attributes[]`（出参 `dimensionId`），并解析反馈 / 保存提交的 `attributes`；
- 维度 `name` 只是展示文案：改 `name` 只改一行、**全站生效**（含详情展示与管理端列表），**零菜品迁移**。

### 取值在 `dish_attribute_value` 表
- 本表**只定义维度**（中文名、单/多选、顺序）；
- **取值是 `dish_attribute_value` 的行**（`label` + `id`）；
- **描述维度的取值存进 `dish.attributes`**（存取值 `id`），出参时服务端翻译成中文 ⇒ 取值可集中改名（见 [dish_attribute_value.md](./dish_attribute_value.md)）。

## 关联

| 方向 | 目标表 | 关系 |
|---|---|---|
| 1→n | `dish_attribute_value.dimension_id` | 该维度下的取值 |
| — | `dish.attributes`（JSON 键 = 描述维度的 ID） | 菜品（**非外键**，JSON 内的键无法用外键约束） |
| — | `dish.meal_type_id`（**非外键**，存系统维度取值 `id`） | 菜品种类的取数锚点 |

## 注意事项

- `sort_order` 决定 `DishDetailVO.attributes[]` 的**返回顺序**，端上按序渲染。
- 描述维度被菜品引用时**禁止删除**（应用层统计）；**系统维度恒禁止删除**。
- `value_type` 由 `single` ↔ `multi` 切换时，须**迁移该维度下菜品的数据形状**（由管理端触发，一次性事务）；**系统维度不支持切换**。

接口契约见 [api/client/dishes.md](../api/client/dishes.md) 与 [api/web/dimensions.md](../api/web/dimensions.md)。
