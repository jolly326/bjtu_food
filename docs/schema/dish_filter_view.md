# dish_filter_view — 首页筛选视图

**用途**：client **首页筛选栏**（`GET /dishes/views` 下发的那排 tab）的**视图本体** —— 文案 / 顺序 / 显隐 + **筛选条件** + **排序口径**（全部后台可维护、免发版、零代码管理）。
**对应实体**：`com.bjtufood.dish.entity.DishFilterView`（**已实现**）
**状态**：已落地 —— 表 + 7 条种子 + 视图目录 + `/admin/dish-views` 五个端点（列表 / 新建 / 改文案启停改条件 / 排序 / 删除）。设计真源见 [A6 首页筛选视图管理](../func/web/A-主数据维护/A6-首页筛选视图管理.md)。

> 表名 **`dish_filter_view`**：避免裸 `view` 被误读为"数据库视图"。
> **本表是筛选视图的唯一真源** —— tab 的筛选条件与排序口径都存这里，代码里**不含**任何视图常量或视图键。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 视图 ID（端上回传 `view=<id>`；管理端拖拽排序载荷 `SortItemsReq` 亦用它） |
| `label` | VARCHAR(32) | NO | `''` | | tab 文案（如「面食粉类」；**可改**） |
| `conditions` | JSON | NO | — | | 筛选条件数组（AND 组合；`[]` = 不筛选，即「为你推荐」）；字段 / 操作符 / 取值三层白名单见 [A6](../func/web/A-主数据维护/A6-首页筛选视图管理.md) |
| `sort_kind` | VARCHAR(16) | NO | — | | 排序口径（白名单：`random` / `priceAsc` / `priceDesc` / `discountDesc` / `ratingDesc` / `newest`） |
| `sort_order` | INT | NO | 0 | | 展示顺序（升序） |
| `enabled` | TINYINT | NO | 1 | | 是否在 client 首页出现（0 = tab 隐藏） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（管理端列表出参 `updatedAt`） |

## 索引

| 索引名 | 列 | 类型 |
|---|---|---|
| `PRIMARY` | `id` | 主键 |
| `idx_view_order` | `sort_order` | 普通 |

> JSON 列**无默认值**（MySQL 5.7 不允许 JSON 列带 `DEFAULT`）⇒ INSERT 必须显式写 `conditions`；「不筛选」写 `[]` 而不写 `NULL`（列 `NOT NULL`）。

## 关键设计

### 条件与排序口径入库（零代码管理）

- **筛选条件**（`conditions`）与**排序口径**（`sort_kind`）都是**本表数据**，由管理端「新建视图 / 编辑视图」写入；
- 取值必须是**白名单内的字段 + 操作符**（字段 / 操作符 / 排序口径三层白名单，唯一真源 = `DishViewConditions`），落库前即在服务端校验 ⇒ **不可配 SQL / 表达式 / 函数**；
- 端的可见性判据与首屏落点都由数据决定：`GET /dishes/views` 只下发 **`enabled = 1`** 且**匹配数不为 0** 的视图；顺序 = `sort_order` 升序（同序按 `id` 升序）；`GET /dishes` 不传 `view` → **取首个启用视图**。

### 端上契约：视图 ID

`GET /dishes/views` 出参 `[{ id, label }]`；端上把 `id` 原样回传为 `GET /dishes?view=<id>`。

### 删除与停用的边界

- **不允许停用 / 删除「最后一个启用的视图」**（应用层拦截，`400`）—— 否则端上筛选栏为空、无落地页；
- 其余视图**可删**（删除即从下发集合移除，属后台自助操作）。

## 关联

| 方向 | 目标 | 关系 |
|---|---|---|
| — | [dish](./dish.md)（**只读统计**） | 匹配数 `matchedCount` |
| — | [dish_attribute_value](./dish_attribute_value.md)（系统维度取值） | 种类条件的取值 ID（`conditions` 内为 JSON 标量，**非外键**） |

> 条件的 `mealTypeId` 值引用**系统维度（菜品种类）**下 [dish_attribute_value](./dish_attribute_value.md) 的 `id`。

## 注意事项

- 本表已落地（表 + 7 条种子）；实体 `DishFilterView` / Mapper / 目录 `DishViewCatalog` / 条件引擎 `DishViewConditions` / `/admin/dish-views` 五个端点均已实现。
- **写后须失效 `GET /dishes/views` 缓存**：该出参走 `@Cacheable`（TTL 2 分钟），写后显式失效，做到保存即生效。
- 条件字段中 `stallId` / `canteenId` / `price` / `avgRating` / `createdAt` 若无索引，取数会退化为全表扫描 + filesort（与 `%keyword%` 搜索同类），属已知取舍；`mealTypeId` 走 `dish.idx_dish_meal_type`。
- **列改名上线顺序**：线上库（TDSQL-C 5.7 内核）须**先执行** ``ALTER TABLE dish_filter_view CHANGE `order` sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序';``（该内核不支持 `RENAME COLUMN` 语法），**再部署**读取 `sort_order` 的后端版本；管理端 API 出参字段仍为 `order`，前端零改动。
