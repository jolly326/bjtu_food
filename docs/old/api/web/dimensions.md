# web/dimensions — 菜品属性「维度与取值」管理

**归属**：`DishAttributeAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 维护菜品属性的**维度**（`dish_attribute_dimension`）与**取值字典**（`dish_attribute_value`）。菜品存的是**取值 ID**，出参由服务端翻译成中文 ⇒ **客户端契约不变**。
>
> 本组端点只管**描述维度**（与菜品 **m:n**，取值存 `dish.attributes`）。菜品「种类」是**另一实体**（与菜品 **1:1**，独立字典表 `dish_category`），走 [web/categories.md](./categories.md)，不出现在本组端点。

---

## GET /admin/dish-dimensions

**用途**：维度列表

### 请求参数
无（**不分页**）。

### 响应 `data` = `DishDimensionAdminVO[]`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 维度 ID（`dish.attributes` JSON 的键） |
| `name` | string | 维度中文名 |
| `valueType` | string | `single` / `multi` |
| `dishCount` | number | 使用该维度的菜品数（删除前判断） |
| `valueCount` | number | 该维度下的**取值数**（删除确认文案用） |
| `updatedAt` | string | 更新时间（`yyyy-MM-dd HH:mm:ss`） |

**排序**：按 `id` 升序（维度顺序即 `DishDetailVO.attributes[]` 的返回顺序，字典不设排序位）。

---

## POST /admin/dish-dimensions

**用途**：新增维度（默认**排最后**）

### 请求体 `DishDimensionSaveReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | **是** | 维度中文名（1~32 字，如「原料」「口味」） |
| `valueType` | string | **是** | `single` 单值 / `multi` 多值 |

### 响应 `data` = `DishDimensionAdminVO`
新建的维度。

---

## PUT /admin/dish-dimensions/{dimensionId}

**用途**：修改（`valueType` 切换自动迁移数据形状）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `dimensionId` | number | 是 | 维度 ID |

### 请求体 `DishDimensionSaveReq`
同 `POST`。

### 响应 `data`
`null`。

> **单 / 多选可切换**：后端在同一事务内自动迁移该维度下菜品的数据形状（单值 `id` ↔ 多值 `[id]`）。

---

## DELETE /admin/dish-dimensions/{dimensionId}

**用途**：删除（被菜品引用时返回 `400`）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `dimensionId` | number | 是 | 维度 ID |

### 响应 `data`
`null`。

---

## GET /admin/dish-dimensions/{dimensionId}/values

**用途**：取值列表

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `dimensionId` | number | 是 | 维度 ID |

### 响应 `data` = `DishValueAdminVO[]`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 取值 ID（经 `dish.attributes` 引用） |
| `name` | string | 取值中文名 |
| `dishCount` | number | 引用该取值的菜品数（删除前判断） |
| `updatedAt` | string | 更新时间（`yyyy-MM-dd HH:mm:ss`） |

**排序**：按 `id` 升序（组内顺序，字典不设排序位）。

---

## POST /admin/dish-dimensions/{dimensionId}/values

**用途**：新增取值（默认**排最后**）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `dimensionId` | number | 是 | 维度 ID |

### 请求体 `DishValueSaveReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | **是** | 取值中文名（1~32 字；**同维度下唯一**） |

> 取值的**机器键（ID）由后端生成**，管理员只填中文 `name`。

### 响应 `data` = `DishValueAdminVO`
新建的取值。

---

## PUT /admin/dish-dimensions/{dimensionId}/values/{valueId}

**用途**：修改取值（`name`）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `dimensionId` | number | 是 | 维度 ID |
| `valueId` | number | 是 | 取值 ID |

### 请求体 `DishValueSaveReq`
同 `POST`。

### 响应 `data`
`null`。

---

## DELETE /admin/dish-dimensions/{dimensionId}/values/{valueId}

**用途**：删除取值（被菜品引用时返回 `400`）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `dimensionId` | number | 是 | 维度 ID |
| `valueId` | number | 是 | 取值 ID |

### 响应 `data`
`null`。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `name` 非法或重名；`valueType` 非法；删除时维度 / 取值仍被引用 |
| `4001` | 维度 / 取值不存在 |
| `401` | 未带 / token 失效 |

### 备注
- **名称全部可改、且免费**：维度名、取值名都只是展示，数据锚在**维度 ID / 取值 ID** ⇒ 改名**不影响任何菜品数据**。
- **灵活取值**：管理端录入菜品时可直接输入新值 → 自动登记进取值字典；反馈提交的新值仅当反馈被**采纳**时按中文查字典（命中即用；未命中自动新建）。
- **删除约束**：维度 / 取值**被菜品引用时禁止删除**（`400`），防止静默丢数据。
- **字典不设排序位**：维度与取值一律按 `id` 升序（无 `order` 入参 / 出参，无排序端点）。
- **`dish.attributes` 用 JSON 列**（存取值 ID），不引入关联表；若将来需要「按属性筛选菜品」，再迁移为关联表（演进预留）。
