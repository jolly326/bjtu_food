# dish — 菜品本体（一道菜）

**用途**：回答「这道菜是什么」——名称 / 描述 / 图片 / 价格。位置在 [`dish_location`](./dish_location.md)，向量在 [`dish_embedding`](./dish_embedding.md)。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 菜品 ID |
| `name` | VARCHAR(64) | NO | `''` | KEY | 菜名；**允许重复**（不同档口的同名菜是两道菜） |
| `description` | TEXT | YES | `NULL` | | **档案富文本**（名称 + 口味 + 场景 + 特点），语义匹配的输入 |
| `image` | VARCHAR(255) | YES | `NULL` | | 菜品大图（相对路径，**单张**） |
| `price` | INT | YES | `NULL` | | 售价（**分**）；同一道菜的所有位置共用；`NULL` = 未录入 |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 创建时间 |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间 |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_dish_name` | `name` | 普通 | 名称模糊搜索（非唯一） |

## 关键设计

### 本体 / 位置 / 向量三层

- `dish`：菜品是什么（名称、描述、图、价格）；
- `dish_location`：这道菜在哪些食堂 / 档口售卖（可多条，价格与图文共享）；
- `dish_embedding`：`description` 的多模型向量。

### `name` 允许重复

不同档口的同名菜是**两道不同的菜**（做法、口味、图可能不同），各自一行 `dish`；同一道菜挂多个位置时只建一行 `dish` + 多条 `dish_location`。

### `description` 是语义匹配的输入

- 由「名称 + 口味 + 场景 + 特点」组成一句话，例如：`辣子鸡 川味小炒 重辣 下饭 够味 解馋`；
- **只写菜名 = 召回不准**（「想吃辣的」匹配不到「辣子鸡」）；
- `description` 变更 ⇒ 保存时 upsert 当前模型向量到 `dish_embedding`。

### 不设的列

| 列 | 归属 |
|---|---|
| `canteen_id` / `stall_id` / `activate` | [`dish_location`](./dish_location.md)（一道菜多位置、各自在售） |
| `embedding` / `embedding_model` | [`dish_embedding`](./dish_embedding.md)（多模型并存） |
| `like_count` / `tags` / `meal` | 不做点赞 / 口味交给描述与向量 / 全天售卖 |

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| 1→n | `dish_location.dish_id` | 该菜品的售卖位置 | **`ON DELETE CASCADE`** |
| 1→n | `dish_embedding.dish_id` | 该菜品的语义向量 | **`ON DELETE CASCADE`** |
| 1→n | `feedback.dish_id` | 该菜品的文本反馈 | **`ON DELETE CASCADE`** |

## 注意事项

- `image` 存**相对路径**，出参经路径工具转绝对 URL。
- 学生端只返回「至少有一个在售位置」的菜品；卡片按位置展开。
- **删除**：物理删除菜品，位置 / 向量 / 反馈随外键级联清理。
