# dish — 菜品（本体）

**用途**：菜品本体 —— 学生端「输入一句话 → 匹配菜品」的语义取数来源，也是运营端菜品管理的主对象。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 菜品 ID |
| `name` | VARCHAR(64) | NO | `''` | UK | 菜品名称；**全库唯一** —— 同一道菜在多个食堂售卖 = 一行 `dish` + 多条 `dish_offering` |
| `description` | TEXT | YES | `NULL` | | **档案富文本**（名称 + 口味 + 场景 + 特点），语义匹配的输入 |
| `image` | VARCHAR(255) | YES | `NULL` | | 菜品大图（相对路径，**单张**） |
| `embedding` | JSON | YES | `NULL` | | `description` 的语义向量（1024 维），保存时预计算 |
| `embedding_model` | VARCHAR(32) | YES | `NULL` | | 生成向量的模型标识，便于重算与版本追溯 |
| `activate` | BOOLEAN | NO | `TRUE` | KEY | 上架状态：`TRUE` 在售 / `FALSE` 已下架 |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 创建时间 |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间 |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `uk_dish_name` | `name` | **唯一** | 菜品名全库唯一，天然去重「同一道菜多食堂」 |
| `idx_dish_activate` | `activate` | 普通 | 公开列表「只查在售」的过滤 |

## 关键设计

### 本体与售卖分离

- `dish` 只回答「这道菜**是什么**」：名称、描述、图片、向量。
- 在哪卖、多少钱由 [`dish_offering`](./dish_offering.md) 承载 —— **一菜多食堂 / 多档口 = 1 行 `dish` + N 行 `dish_offering`**。

### `description` 是语义匹配的输入

- 由「名称 + 口味 + 场景 + 特点」组成一句话，例如：`辣子鸡 川味小炒 重辣 下饭 够味 解馋`；
- **只写菜名 = 召回不准**（「想吃辣的」匹配不到「辣子鸡」）；
- `description` 变更 ⇒ **保存时重算 `embedding`**（应用层触发），并把 `embedding_model` 一并更新。

### 不设的列

| 列 | 为何不设 |
|---|---|
| `canteen_id` / `stall_id` / `floor` | 属于「在哪卖」，下沉到 [`dish_offering`](./dish_offering.md)（一菜多售卖点，单列装不下） |
| `price` | 价格随食堂不同，下沉到 [`dish_offering`](./dish_offering.md) |
| `like_count` | 不做点赞 |
| `tags` | 口味交给 `description` + 向量，不维护人工标签 |
| `meal` | 所有菜品全天售卖，不分时段 |

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| 1→n | `dish_offering.dish_id` | 该菜品的售卖点 | **`ON DELETE CASCADE`**（删菜即清空售卖点） |
| 1→n | `feedback.dish_id` | 该菜品的文本反馈 | **`ON DELETE CASCADE`**（删菜即清空菜品反馈） |

## 注意事项

- `image` 存**相对路径**，出参经路径工具转绝对 URL。
- `activate = FALSE`（下架）对公开接口**等价于不存在**。
- **删除**：管理员可物理删除菜品；`dish_offering` 与 `feedback` 因外键 `ON DELETE CASCADE` 自动清理。
- **归属一致性**：`dish_offering.stall_id` 非空时，其档口 `stall.canteen_id` 必须等于该行的 `canteen_id`（应用层校验，违者 `400`）。
