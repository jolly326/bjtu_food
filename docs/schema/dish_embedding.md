# dish_embedding — 菜品语义向量

**用途**：`dish.description` 的预计算向量。派生数据独立成表：`dish` 只存业务事实，且同一道菜可并存多个模型版本的向量。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `dish_id` | BIGINT | NO | 无 | PK, FK→dish | 所属菜品 |
| `model` | VARCHAR(32) | NO | 无 | PK | 向量模型标识（如 `local-hash-bigram-v1`） |
| `vector` | JSON | NO | 无 | | 1024 维向量 |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间 |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `dish_id`, `model` | 主键 | 一个菜品同一模型只存一份 |
| `fk_embedding_dish` | `dish_id` | 外键 | `REFERENCES dish(id)`，**`ON DELETE CASCADE`** |

## 关键设计

### 为什么独立成表

- `vector` 由 `description + model` 派生，放在 `dish` 里会让业务表承载派生数据（违反 BCNF）；
- 独立后一个菜品可并存多模型向量：切换 / 灰度模型时不需要清空旧列，回滚只需切 `model`。

### 检索只信任当前模型

- 读取时按 `model = current_model()` 取行；
- `EMBEDDING_PROVIDER=local`：缺失 / 不符时按当前模型**实时补算**（零成本）；
- `EMBEDDING_PROVIDER=http`：缺失 / 不符的行**跳过不参与召回**，由 `server/scripts/backfill_embeddings.py` 回填；
- 在售行缓存按模型分键（`student:dishes:on_sale:{model}`）。

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| N→1 | `dish.id` | 所属菜品 | **`ON DELETE CASCADE`**（删菜即清向量） |

## 注意事项

- 保存 `description` 时 upsert 当前模型行（`(dish_id, model)` 冲突则更新 `vector`）。
- 旧模型行保留，便于回滚与对比；不做自动清理。
