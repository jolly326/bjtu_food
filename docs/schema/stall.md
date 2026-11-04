# stall — 档口

**用途**：档口 —— 运营端录入售卖点时的**下拉源**，承载楼层 / 窗口号等位置信息。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 档口 ID |
| `canteen_id` | BIGINT | **YES** | `NULL` | KEY, FK→canteen | 所属食堂；**可空** —— 允许「未归位」档口 |
| `name` | VARCHAR(32) | NO | `''` | | 档口名称 |
| `activate` | BOOLEAN | NO | `TRUE` | | 档口状态：`TRUE` 正常 / `FALSE` 已停用 |
| `floor` | VARCHAR(8) | YES | NULL | | 楼层（受控字典，值即汉字：`负一层`…`四层`） |
| `window_no` | VARCHAR(16) | YES | NULL | | 窗口号 |
| `image` | VARCHAR(255) | YES | NULL | | 封面图（相对路径，出参转绝对 URL；运营端展示用） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间 |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_stall_canteen` | `canteen_id` | 普通 | 按食堂取档口（同时是外键所需索引） |
| `fk_stall_canteen` | `canteen_id` | **外键** | `REFERENCES canteen(id)`，**无级联** |

## 关键设计

> 档口删除后其售卖点保留（`dish_offering.stall_id` 置空），不构成删除阻力。

### `floor` 是受控字典（取值清单）

| 取值 |
|---|
| `负一层` ｜ `一层` ｜ `二层` ｜ `三层` ｜ `四层` |

- **不在库内建字典表** —— 低频封闭枚举，不设字典端点；
- 🔴 **清单是「两端常量」**：管理端下拉的选项来自**前端固定常量**（见 [`ui/web/档口管理.md`](../ui/web/档口管理.md)），服务端按**同一清单**校验入参 ⇒ 两边必须**逐字一致**；
- 新增 / 改名需「先迁移数据、再改两端」—— 顺序不能反（先改代码会出现旧值无法保存）。

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| N→1 | `canteen.id` | 所属食堂 | 无级联（外键 `fk_stall_canteen`） |
| 1→n | `dish_offering.stall_id` | 该档口的售卖点 | **`ON DELETE SET NULL`**（档口删除后售卖点保留、档口置空） |

## 注意事项

- `name` 在**同一食堂下**的唯一性由**应用层**保证（无 DB 唯一索引）。
- **未归位档口**：`canteen_id` 为空的档口允许存在，运营端列表标注「未归位」并可后续归位。
- 管理端维护入口：运营端「档口管理」。
- **封面图** `image`：运营端展示用（相对路径入库、出参绝对 URL）；学生端不消费。
- `floor`：菜品详情「位置」展示（食堂 · 楼层 · 档口）的来源。
- **一次性清库口径**：先删子表 `feedback` / `dish_offering` / `dish`，再删无引用的 `stall`，最后删无引用的 `canteen`（`dish_offering.canteen_id` 为 `RESTRICT`，须在售卖点删尽后删）。
