# dish_attribute_dimension — 菜品描述属性维度

**用途**：定义菜品可有哪些描述维度（如「食材」「饮食属性」「口味」「冷热」）。**表驱动，新增维度免 ALTER、免发版。** 取值见同库 [dish_attribute_value](./dish_attribute_value.md)。
**对应实体**：`com.bjtufood.dish.entity.DishAttributeDimension`

> 本表只管**描述维度**（与菜品 m:n，取值写入 `dish.attributes`）。菜品「种类」是**另一实体**（1:1，[dish_category](./dish_category.md)），不进本表。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 维度 ID；**唯一锚点**（`dish.attributes` JSON 的键 + 纠错快照键） |
| `name` | VARCHAR(32) | NO | `''` | | 维度中文名（端上直接渲染，如「原料」；**可改，改名免费**） |
| `value_type` | VARCHAR(16) | NO | `'single'` | | `single` 单值 / `multi` 多值 |
| `sort_order` | INT | NO | 0 | | 展示顺序（升序） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（管理端列表出参 `updatedAt`） |

## 索引

| 索引名 | 列 | 类型 |
|---|---|---|
| `PRIMARY` | `id` | 主键 |

> 本表**无 `created_at`**；`updated_at` 只用于管理端列表展示「最近改动」。

## 关键设计

### 表驱动 = 新增维度零发版
新增一个描述维度的完整流程：向本表 **INSERT 一行**（`name` / `value_type` / `sort_order`）——**免 ALTER、免发版、端上零改代码**。

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

## 注意事项

- `sort_order` 决定 `DishDetailVO.attributes[]` 的**返回顺序**，端上按序渲染。
- 维度被菜品引用时**禁止删除**（应用层统计）。
- `value_type` 由 `single` ↔ `multi` 切换时，须**迁移该维度下菜品的数据形状**（由管理端触发，一次性事务）。

接口契约见 [api/client/dishes.md](../api/client/dishes.md) 与 [api/web/dimensions.md](../api/web/dimensions.md)。
