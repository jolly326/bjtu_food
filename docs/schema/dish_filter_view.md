# dish_filter_view — 首页筛选视图

**用途**：client **首页筛选栏**（`GET /dishes/views` 下发的那排 tab）的**视图本体** —— 文案 / 显隐 + **筛选种类**（后台可维护、免发版、零代码管理）。
**对应实体**：`com.bjtufood.dish.entity.DishFilterView`（**已实现**）
**状态**：已落地 —— 表 + 7 条种子 + 目录 + `/admin/dish-views` 四个端点（列表 / 新建 / 修改 / 删除）。设计真源见 [A6 首页筛选视图管理](../func/web/A-主数据维护/A6-首页筛选视图管理.md)。

> 表名 **`dish_filter_view`**：避免裸 `view` 被误读为"数据库视图"。
> **本表是筛选视图的唯一真源** —— tab 的文案与筛选种类都存这里，代码里**不含**任何视图常量或视图键。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 视图 ID（端上回传 `view=<id>`） |
| `name` | VARCHAR(32) | NO | `''` | | tab 文案（如「面食粉类」；**可改**） |
| `category_id` | BIGINT | YES | NULL | **FK** | 该 tab 筛选的**菜品种类**（值域 = `dish_category.id`，**外键 `fk_filter_view_category`**，无级联）；**`NULL` = 「为你推荐」**（不按种类筛） |
| `enabled` | TINYINT | NO | 1 | | 是否在 client 首页出现（0 = tab 隐藏） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（管理端列表出参 `updatedAt`） |

## 索引

| 索引名 | 列 | 类型 |
|---|---|---|
| `PRIMARY` | `id` | 主键 |
| `fk_filter_view_category` | `category_id` | **外键** |

> `fk_filter_view_category` = `REFERENCES dish_category(id)`（**无级联**）。
> **无排序列** —— tab 顺序一律按 `id` 升序（端上下发顺序与管理端列表顺序同一口径）。

## 关键设计

### 筛选由 `category_id` 驱动（零代码管理）

- `category_id` 非 NULL ⇒ 该 tab = 该种类下的在售菜品（后端按种类筛，走 `dish.idx_dish_category`）；
- `category_id = NULL` ⇒ 「为你推荐」：不按种类筛，取**全量在售菜品**；
- **排序口径不入库** —— `GET /dishes` 的排序统一为**会话种子伪随机序**（见 [api/client/dishes.md](../api/client/dishes.md)），tab 之间不区分排序口径；
- 取值域由外键 `fk_filter_view_category` 与**服务端校验**共同保证（必须是已存在的 `dish_category.id`）；
- 端的可见性判据与首屏落点都由数据决定：`GET /dishes/views` 只下发 **`enabled = 1`** 且**匹配数不为 0** 的视图；顺序 = `id` 升序；`GET /dishes` 不传 `view` → **取首个启用视图**。

### 端上契约：视图 ID

`GET /dishes/views` 出参 `[{ id, name }]`；端上把 `id` 原样回传为 `GET /dishes?view=<id>`。

### 种子构成（7 行）

| 行 | `name` | `category_id` |
|---|---|---|
| 1 | 为你推荐 | `NULL` |
| 2~7 | 6 个种类 tab | 与 [dish_category](./dish_category.md) 的 6 条种子**一一对应** |

### 删除与停用的边界

- **不允许停用 / 删除「最后一个启用的视图」**（应用层拦截，`400`）—— 否则端上筛选栏为空、无落地页；
- 其余视图**可删**（删除即从下发集合移除，属后台自助操作）。

### 「不筛种类」位（`category_id IS NULL`）的边界
- 本表**无唯一约束**（文案可重复，视图由 `id` 标识），故「不筛种类」在库层**不保证唯一** —— 当前库内**恰 1 行**（种子「为你推荐」）；
- 端上判据是「取**首个启用**视图」，多行「不筛种类」不会报错但语义模糊；
- **触发条件**：若运营要新增第二个「全量」tab，须先定义它与首个的关系（改文案？合并？显式区分口径？），再决定是否加唯一性约束。

## 关联

| 方向 | 目标 | 关系 |
|---|---|---|
| — | [dish](./dish.md)（**只读统计**） | 匹配数 `matchedCount`（`category_id` 非空时按种类计数） |
| — | [dish_category](./dish_category.md)（菜品种类） | `category_id` 引用其 `id`（**外键 `fk_filter_view_category`**，无级联） |

## 注意事项

- 本表已落地（表 + 7 条种子）；实体 `DishFilterView` / Mapper / 目录 / `/admin/dish-views` 四个端点均已实现。
- **写后须失效 `GET /dishes/views` 缓存**：该出参走 `@Cacheable`（TTL 2 分钟），写后显式失效，做到保存即生效。
- `category_id` 的取数与计数走 `dish.idx_dish_category`，**无全表扫描项**：视图的可配面只有 `category_id` 一列（无字段 / 操作符条件语言，无可配排序口径）。
