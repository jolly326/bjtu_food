# dish_category — 菜品种类字典

**用途**：菜品「种类」（`dish.category_id` 存本表 `id`）的**取值域** —— 由管理端「分类」抽屉维护，首页筛选视图**引用**它、不派生它。
**对应实体**：`com.bjtufood.dish.entity.DishCategory`
**状态**：已落地 —— 表 + 6 条种子 + `/admin/dish-categories` 三个端点（列表 / 登记 / 重命名）。

> 本表是**菜品种类的唯一真源**：种类名（`name`）只存这里；`dish.category_id` 与 `dish_filter_view.category_id` 引用的都是本表 `id`。
> **与描述属性的区别**：种类与菜品是 **1:1**（互斥、必填、单值列 + 索引）；描述属性（[dish_attribute_dimension](./dish_attribute_dimension.md) / [dish_attribute_value](./dish_attribute_value.md)）与菜品是 **m:n**（可多值、可空、存 `dish.attributes` JSON）。两者是**两种不同关系、两个独立实体**，不共用表、不共用端点。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 种类 ID（`dish.category_id` 与视图 `category_id` 引用的就是它） |
| `name` | VARCHAR(32) | NO | `''` | | 种类中文名（展示用；**可改，改名免费** —— 数据锚在 `id`） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（管理端列表出参 `updatedAt`） |

## 索引

| 索引名 | 列 | 类型 |
|---|---|---|
| `PRIMARY` | `id` | 主键 |

> 本表**无 `created_at`**；`updated_at` 只用于管理端列表展示「最近改动」。
> `name` **不加唯一索引** —— 「同表唯一」由**应用层**保证（与 [dish_attribute_value](./dish_attribute_value.md) 同口径）。
> **无排序列** —— 列表与端上下拉一律按 `id` 升序。

## 关键设计

### 为何是独立实体，而不是「维度」的一个特例

| 项 | 种类（本表） | 描述属性（维度 / 取值） |
|---|---|---|
| 与菜品的关系 | **1:1**（互斥、必填） | **m:n**（可多值、可空） |
| 存储 | `dish.category_id`（独立列 + 索引） | `dish.attributes` JSON（键 = 描述维度 ID） |
| 规则 | 名称可改；被菜品引用时不可删；无删除端点 | 名称可改；被引用时不可删；可增删维度与取值 |
| 单/多值 | 恒单值 | `value_type` 可为 `single` / `multi` |

两种关系不同的实体**各有自己的表与端点**：维度表只定义描述维度（不承载种类），种类字典独立成表 —— 登记 / 改名 / 引用计数各走自己的端点，不存在「同一批字典行两条写路径」的别名面。

### 名称可改 = 数据锚在 ID

`dish.category_id` 存的是本表 `id`（不是中文），故改 `name` 只改一行、**全站生效**（首页 tab 文案除外 —— 视图文案是视图自己的 `name`），零菜品迁移。

### 删除约束与入口

- 种类**被菜品引用时禁止删除**（应用层统计引用它的菜品数，`> 0` 即拦截），避免菜品读到悬空 ID；
- **无删除端点**：种类是 `dish.category_id` 的锚点，下线一律「改名 + 把菜品改到别的种类」；
- **登记入口唯一**：管理端 A3 菜品管理页的「分类」抽屉（列表 / 登记 / 重命名），不单设菜单项 —— 种类是菜品录入的从属字典。

## 关联

| 方向 | 目标 | 关系 |
|---|---|---|
| 1→n | `dish.category_id`（**非外键**，存本表 `id`） | 菜品所属种类 |
| — | `dish_filter_view.category_id`（**非外键**） | 视图按种类取数的取值锚点 |

> 不设外键 —— 与全库约定一致（关系由应用层维护，见 [README §外键与级联](./README.md)）。

## 注意事项

- **端上零消费**：`dish.category_id` 不出公开出参（列表 8 字段 / 详情 11 字段均不含种类），筛选由视图条件在服务端完成；管理端 `DishAdminVO` / `DishAdminListItemVO` / `DishAdminListQuery` 含该字段（录入下拉 + 编辑回填 + 列表筛选）。
- **与看板口径对齐**：看板「种类为空的菜品数」判据 = `dish.category_id IS NULL`。
- 接口契约见 [api/web/categories.md](../api/web/categories.md)；管理侧设计见 [A6 首页筛选视图管理](../func/web/A-主数据维护/A6-首页筛选视图管理.md)。
