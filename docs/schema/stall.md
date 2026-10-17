# stall — 档口

**用途**：档口 —— 菜品的位置归属；竖版海报独立存于 [`poster`](./poster.md) 表（一档口可多张，FK → 本表）。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 档口 ID |
| `canteen_id` | BIGINT | NO | 无 | KEY, FK→canteen | 所属食堂；**必填、无默认值**（不用 `0` 占位） |
| `name` | VARCHAR(32) | NO | `''` | | 档口名称 |
| `floor` | VARCHAR(8) | YES | NULL | | 楼层（受控字典，值即汉字：`负一层`…`四层`） |
| `window_no` | VARCHAR(16) | YES | NULL | | 窗口号 |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（管理端列表出参） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_stall_canteen` | `canteen_id` | 普通 | 按食堂取档口（同时是外键所需索引） |
| `fk_stall_canteen` | `canteen_id` | **外键** | `REFERENCES canteen(id)`，**无级联** |

## 关键设计

> 海报已从本表拆出，独立为 [`poster`](./poster.md) 表 —— 结构、排序、上下架与出图口径一律见该文档。

### `floor` 是受控字典（**取值清单**）

| 取值 |
|---|
| `负一层` ｜ `一层` ｜ `二层` ｜ `三层` ｜ `四层` |

- **不在库内建字典表** —— 低频封闭枚举，没有也**不设**字典端点；
- 🔴 **清单是「两端常量」**：管理端下拉的选项来自**前端固定常量**（见 [`ui/web/档口管理.md`](../ui/web/档口管理.md)），服务端按**同一清单**校验入参 ⇒ 两边必须**逐字一致**；
- 新增 / 改名需「**先迁移数据、再改两端**」—— 顺序不能反（先改代码会出现旧值无法保存）。

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| N→1 | `canteen.id` | 所属食堂 | 无级联（外键 `fk_stall_canteen`） |
| 1→n | `poster.stall_id` | 该档口的海报 | **`ON DELETE CASCADE`**（海报无下游，删档口即清空，见 [poster.md](./poster.md)） |
| 1→n | `dish.stall_id` | 该档口的菜品 | **无外键**（档口有独立维护语义，见 [dish.md](./dish.md)） |

## 注意事项

- `name` 在**同一食堂下**的唯一性由**应用层**保证（无 DB 唯一索引）。
- 管理端维护入口：运营端「档口管理」（档口本体）；海报的增删改、上下架与排序在**「海报管理」**（见 [poster.md](./poster.md)）。
- ⏳ `floor` / `window_no` 是否保留待定：它们在**学生端零消费**（位置由海报图承载），保留理由是**运营端列表的位置展示**。
