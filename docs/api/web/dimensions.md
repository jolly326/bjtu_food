# web/dimensions — 菜品属性「维度与取值」管理

**归属**：`DishAttributeAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 维护菜品属性的**维度**（`dish_attribute_dimension`）与**取值字典**（`dish_attribute_value`）。菜品存的是**取值 ID**，出参由服务端翻译成中文 ⇒ **客户端契约不变**。
>
> **系统维度**（`system = true`，当前 = 「菜品种类」）：其取值即**菜品种类字典**，被 `dish.meal_type_id` 引用（别名面契约见 [web/categories.md](./categories.md)）。该行**取值类型不可改、不可删**；其取值被菜品引用时同样不可删（引用判据 = `dish.meal_type_id = 取值 ID`）。

---

## GET /admin/dish-dimensions

**用途**：维度列表

### 请求参数
无（**不分页**）。

### 响应 `data` = `DishDimensionAdminVO[]`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 维度 ID（`dish.attributes` JSON 的键；系统维度为菜品种类锚点） |
| `name` | string | 维度中文名 |
| `valueType` | string | `single` / `multi`（系统维度恒为 `single`） |
| `system` | boolean | **是否系统维度**（`true` = 菜品种类：取值类型不可改、维度不可删；且其取值不写入 `attributes`） |
| `order` | number | 展示顺序 |
| `dishCount` | number | 使用该维度的菜品数（删除前判断） |
| `valueCount` | number | 该维度下的**取值数**（删除确认文案用） |
| `updatedAt` | string | 更新时间（`yyyy-MM-dd HH:mm:ss`） |

**排序**：按 `order` 升序。

---

## POST /admin/dish-dimensions

**用途**：新增维度（默认**排最后**）

### 请求体 `DishDimensionSaveReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | **是** | 维度中文名（1~32 字，如「原料」「口味」） |
| `valueType` | string | **是** | `single` 单值 / `multi` 多值 |

> **不含 `order`** —— 展示顺序由列表拖拽维护（`PUT /admin/dish-dimensions/sort`）。

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
> **系统维度**：只允许改 `name`；提交与现状不同的 `valueType` → `400`（菜品种类恒单值）。

---

## DELETE /admin/dish-dimensions/{dimensionId}

**用途**：删除（被菜品引用时返回 `400`）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `dimensionId` | number | 是 | 维度 ID |

### 响应 `data`
`null`。

> **系统维度恒不可删** → `400`（其取值被 `dish.meal_type_id` 引用，删除即产生悬空 ID）。

---

## PUT /admin/dish-dimensions/sort

**用途**：**维度排序**（拖拽后整体提交）

### 请求体 `SortItemsReq`
`items`：`[{ id, order }]` 数组，整体替换顺序。**边界（全量行 / 非法提交 → `400`）见 [api/README 的「拖拽排序提交」](../README.md#拖拽排序提交)**。

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
| `id` | number | 取值 ID（描述属性经 `dish.attributes` 引用；系统维度取值经 `dish.meal_type_id` 引用） |
| `label` | string | 取值中文名 |
| `order` | number | 组内展示顺序 |
| `dishCount` | number | 引用该取值的菜品数（删除前判断；系统维度按 `dish.meal_type_id` 统计） |
| `updatedAt` | string | 更新时间（`yyyy-MM-dd HH:mm:ss`） |

**排序**：按 `order` 升序。

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
| `label` | string | **是** | 取值中文名（1~32 字；**同维度下唯一**） |

> 取值的**机器键（ID）由后端生成**，管理员只填中文 `label`；**不含 `order`**（组内顺序由拖拽维护）。

### 响应 `data` = `DishValueAdminVO`
新建的取值。

---

## PUT /admin/dish-dimensions/{dimensionId}/values/{valueId}

**用途**：修改取值（`label`）

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

## PUT /admin/dish-dimensions/{dimensionId}/values/sort

**用途**：**取值排序**（拖拽后整体提交）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `dimensionId` | number | 是 | 维度 ID |

### 请求体 `SortItemsReq`
`items`：`[{ id, order }]` 数组，整体替换顺序。**边界见 [api/README 的「拖拽排序提交」](../README.md#拖拽排序提交)**。

### 响应 `data`
`null`。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `name` / `label` 非法或重名；`valueType` 非法；**系统维度改取值类型 / 删除**；删除时维度 / 取值仍被引用；排序提交非法 |
| `4001` | 维度 / 取值不存在 |
| `401` | 未带 / token 失效 |

### 备注
- **名称全部可改、且免费**：维度名、取值名都只是展示，数据锚在**维度 ID / 取值 ID** ⇒ 改名**不影响任何菜品数据**。
- **灵活取值**：管理端录入菜品时可直接输入新值 → 自动登记进取值字典；反馈提交的新值仅当反馈被**采纳**时按中文查字典（命中即用；未命中自动新建）。
- **删除约束**：维度 / 取值**被菜品引用时禁止删除**（`400`），防止静默丢数据。
- **排序一律「拖拽 + 批量端点」**：一次原子提交比逐条填 `order` 更不易出错；`order` 仍在 VO 回显，但不从保存请求传入。
- **`dish.attributes` 用 JSON 列**（存取值 ID），不引入关联表；若将来需要「按属性筛选菜品」，再迁移为关联表（演进预留）。
