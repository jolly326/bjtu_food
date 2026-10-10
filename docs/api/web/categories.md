# web/categories — 菜品种类字典管理

**归属**：`DishCategoryAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 菜品「种类」（`dish.category_id` 存本表 `id`）的**取值域** = [dish_category](../../schema/dish_category.md) 表。管理端只保留**登记**与**重命名**；数据锚在 `id`，改名免费。首页筛选视图按 `category_id` **引用**它、不派生它（见 [web/views.md](./views.md)）。
>
> **本文件是 `/admin/dish-categories` 这个路径的契约**。种类字典与描述属性是**两个独立实体、两套端点**：维度表只承载**描述维度**（与菜品 m:n，取值存 `dish.attributes`，见 [web/dimensions.md](./dimensions.md)），种类与菜品 **1:1**（独立列 `dish.category_id`）。
>
> **维护入口**：**A3 菜品管理**页头的「管理分类」抽屉（分类下拉旁），**不单设菜单项** —— 种类是菜品录入的从属字典，独立成页会让「登记种类」多一跳。

---

## GET /admin/dish-categories

**用途**：种类列表

### 请求参数
无（**不分页**）。

### 响应 `data` = `DishCategoryAdminVO[]`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 种类 ID（= `dish.category_id` 的存储值） |
| `name` | string | 种类中文名（如「面食粉类」） |
| `dishCount` | number | **引用该种类的菜品数**（`dish.category_id = id` 的菜品数） |
| `updatedAt` | string | 更新时间（`yyyy-MM-dd HH:mm:ss`） |

**排序**：按 `id` 升序（字典不设排序位）。

---

## POST /admin/dish-categories

**用途**：**登记新种类**

### 请求体
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | **是** | 种类中文名（1~32 字；**同表唯一**，应用层校验） |

> 种类的**机器键（ID）由后端生成**，管理员只填中文 `name`。

### 响应 `data` = `DishCategoryAdminVO`
新建的种类。

---

## PUT /admin/dish-categories/{id}

**用途**：重命名

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 种类 ID |

### 请求体 `DishCategoryRenameReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | **是** | 种类中文名（1~32 字；**同表唯一**，应用层校验） |

### 响应 `data`
`null`。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `name` 为空 / 超 32 字 / 重名 |
| `4001` | 种类不存在 |
| `401` | 未带 / token 失效 |

### 备注
- **数据锚在 `id` + 自由命名 + 仅重命名**：`dish.category_id` 存种类 ID，`name` 改名免费、零菜品迁移。
- **无删除端点**：种类是 `dish.category_id` 的锚点，被菜品引用时删除会留下悬空 ID；下线一律「改名 + 把菜品改到别的种类」。
- **入口**：种类维护入口挂在 A3 菜品录入的分类下拉旁。
