# canteen — 食堂

**用途**：档口的上级归属；**管理端维护的主数据实体**（管理侧设计见 [A1 食堂管理](../func/web/A-主数据维护/A1-食堂管理.md)）。
**对应实体**：`com.bjtufood.canteen.entity.Canteen`

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 食堂 ID |
| `name` | VARCHAR(64) | NO | `''` | | 食堂名；经菜品位置行出参；**全站唯一**（应用层校验） |
| `images` | VARCHAR(1024) | YES | NULL | | 食堂图片 URL 列表 JSON；经管理端出参 `images`（转绝对 URL 数组），**A1 详情抽屉内可编辑**（有序、首图作封面、≤5 张；序列化后 ≤1024，超长 `400`） |
| `location` | VARCHAR(128) | YES | NULL | | 食堂位置；经管理端出参 `location`，**A1 详情抽屉内可编辑**（≤128 字，空串 = 清空） |
| `description` | VARCHAR(512) | YES | NULL | | 食堂描述；经管理端出参 `description`，**A1 详情抽屉内可编辑**（≤512 字，空串 = 清空） |
| `sort_order` | INT | NO | 0 | | **排序位（管理端食堂列表的排序键）**：`CanteenService#listAllForAdmin` 按其升序 → `updated_at` 降序；**A1 详情抽屉内作为普通数值字段维护**（本页不是拖拽排序菜单） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（管理端列表出参 `updatedAt`） |

## 索引

| 索引名 | 列 | 类型 |
|---|---|---|
| `PRIMARY` | `id` | 主键 |

> `name` **不加唯一索引** —— 唯一性由**应用层**保证（新增 / 改名时查重）。

## 关键设计

### 管理端维护的实体
- 管理端可**新增 / 改名 / 删除**（`POST` / `PUT` / `DELETE /admin/canteens`）。
- **删除约束**：其下仍有档口时禁止删除，避免孤儿档口。
- **名称唯一**由应用层校验；重名不重复建档。

### 位置表达
位置表达统一用 **食堂名 · 楼层 · 档口名** 三元组；服务端**不出参坐标**、端上**不算距离**、**不申请定位权限**。

### 无公开字典端点
食堂**没有公开的只读字典端点**；端上通过菜品详情获得食堂名，不做独立查询。

## 关联

| 方向 | 目标表 | 关系 |
|---|---|---|
| 1→n | `stall.canteen_id` | 食堂下的档口 |
| — | `dish` | **无直接关联** —— `dish` 只连 `stall_id`，经 `stall` 间接得到食堂 |

## 注意事项

- 本表**无 `created_at`**：食堂是管理端维护的字典型主数据，「创建时间」无业务消费方，`updated_at` 已承载「最近改动」展示。
- `images` / `location` / `description` **无公开（client）消费方**，仅随管理端出参下发；`sort_order` 是**内部排序位**（管理端食堂列表排序键）—— 四者均在 **A1 详情抽屉**内可查看、可编辑（写入面与空值语义见 [api/web/stalls.md](../api/web/stalls.md)）。
- `canteen` **不依赖** `review`：档口均分由 controller 编排层聚合，包级依赖图为无环（见 `ArchTests` 护栏）。

接口契约：`canteenName` 经 `DishDetailVO` / `DishListItemVO` 出参（见 [api/client/dishes.md](../api/client/dishes.md)）。
