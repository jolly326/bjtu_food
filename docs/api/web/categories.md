# web/categories — 菜品种类取值管理（系统维度取值的别名面）

**归属**：`DishCategoryAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 菜品「种类」（`dish.meal_type_id` 存取值 `id`）的**取值域** —— 即**系统维度（菜品种类）**（`dish_attribute_dimension.system = 1`）下 [dish_attribute_value](../../schema/dish_attribute_value.md) 的行。管理端只保留**登记**、**重命名**与**排序**；数据锚在 `id`，改名免费。首页筛选视图**引用**它、不派生它（见 [web/views.md](./views.md)）。
>
> **本文件是 `/admin/dish-categories` 这个路径的契约**：该路径是**系统维度取值的别名面**（比「某个固定维度下的取值」短、且不要求调用方先知道维度 ID）。同一批行也可经 [web/dimensions.md](./dimensions.md) 的「取值」端点读写（系统维度行的取值子资源）——两条路径操作**同一批数据**，不是两套字典。
>
> **维护入口**：**A3 菜品管理**页头的「管理分类」抽屉（分类下拉旁），**不单设菜单项** —— 种类取值是菜品录入的从属字典，独立成页会让「登记种类」多一跳。

---

## GET /admin/dish-categories

**用途**：种类取值列表

### 请求参数
无（**不分页**）。

### 响应 `data` = `DishCategoryAdminVO[]`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 取值 ID（= `dish.meal_type_id` 的存储值） |
| `label` | string | 种类中文名（如「面食粉类」） |
| `order` | number | 组内展示顺序 |
| `dishCount` | number | **引用该种类的菜品数**（`dish.meal_type_id = id` 的菜品数） |
| `updatedAt` | string | 更新时间（`yyyy-MM-dd HH:mm:ss`） |

**排序**：按 `order` 升序（同序按 `id` 升序）。

---

## POST /admin/dish-categories

**用途**：**登记新种类取值**

### 请求体
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `label` | string | **是** | 种类中文名（1~32 字；**同维度下唯一**，应用层校验） |

> 取值的**机器键（ID）由后端生成**，管理员只填中文 `label`；顺序一律由拖拽维护（不传 `order`）。

### 响应 `data` = `DishCategoryAdminVO`
新建的种类取值。

---

## PUT /admin/dish-categories/{id}

**用途**：重命名

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 取值 ID |

### 请求体 `DishCategoryRenameReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `label` | string | **是** | 种类中文名（1~32 字；**同维度下唯一**，应用层校验） |

### 响应 `data`
`null`。

---

## PUT /admin/dish-categories/sort

**用途**：拖拽排序（**整体提交全量行**）

### 请求体
`{ "items": [{ "id": number, "order": number }] }`（**必须覆盖该维度现有全部取值行**）

### 响应 `data`
`null`。

> 提交协议与其余 `PUT .../sort` **逐字一致**（见 [api/README〈拖拽排序提交〉](../README.md)）：`items` 为空 / 含未知 `id` / 含重复 `id` / 同一 `order` 重复 / **缺行** → 一律 `400`「排序提交非法」，服务端整体替换 `order`。
> **排序键与展示**：列表按 `order` 升序（同序时按 `id` 升序）；A3 的「管理分类」抽屉内提供行首手柄拖拽。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `label` 为空 / 超 32 字 / 同维度重名；**排序提交非法** |
| `4001` | 取值不存在（或不属于系统维度） |
| `401` | 未带 / token 失效 |
| `500` | 系统维度（菜品种类）未初始化 —— 建库未完成，非调用方问题 |

### 备注
- **数据锚在 `id` + 自由命名 + 仅重命名**：`dish.meal_type_id` 存取值 ID，`label` 改名免费、零菜品迁移。
- **无删除端点**：种类取值是 `dish.meal_type_id` 的锚点，被菜品引用时删除会留下悬空 ID；下线一律「改名 + 把菜品改到别的种类」（若要删除未被引用的取值，走 [web/dimensions.md](./dimensions.md) 的取值删除端点）。
- **入口**：种类取值维护入口挂在 A3 菜品录入的分类下拉旁。
