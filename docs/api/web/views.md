# web/views — 首页筛选视图管理

**归属**：`DishViewAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 首页筛选 tab 的**全部口径都在这里**：文案 / 顺序 / 显隐 + **筛选条件** + **排序口径**。视图是纯数据（见 [schema/dish_filter_view.md](../../schema/dish_filter_view.md)），新增 / 删除视图均为后台自助操作、**免发版**。
> 种类取值（`/admin/dish-categories`）见 [web/categories.md](./categories.md)。
> 客户端下发契约（`GET /dishes/views` / `GET /dishes?view=`）见 [dishes.md](../client/dishes.md)。

---

## 筛选条件语言（`conditions`）

`conditions` 是**有限语言**的数组，元素形如 `{ field, op, value?, values? }`；**组合关系只有 AND**（一期不支持 OR），`[]` = 不筛选（匹配全部在售菜品）。

**字段白名单**（`field` → 允许的 `op`）：

| `field` | 含义 | 操作符 | 取值形态 |
|---|---|---|---|
| `mealTypeId` | 菜品种类 | `=` / `in` | **取值 ID**（系统维度取值的 `id`，见 [web/categories.md](./categories.md)）；`=` 用 `value`，`in` 用 `values` |
| `discount` | 有折扣 | `isTrue` | **无值**（派生 `original_price > price`） |
| `price` | 现价（分） | `between` / `>=` / `<=` | `between` 用 `values`（恰两项）；其余用 `value` |
| `stallId` | 档口 | `=` / `in` | 档口 ID（[web/stalls.md](./stalls.md)） |
| `canteenId` | 食堂 | `=` / `in` | 食堂 ID（经档口间接：`s.canteen_id`） |
| `avgRating` | 均分 | `>=` | 数值（0~5） |
| `createdAt` | 上新 | `withinDays` | 正整数（近 N 天） |

**排序口径白名单**（`sortKind`，单选）：`random` 会话种子随机 ｜ `priceAsc` / `priceDesc` ｜ `discountDesc` 折扣力度 ｜ `ratingDesc` 均分 ｜ `newest` 上新。

> 🔴 **条件语言不可配 SQL**：字段 / 操作符 / 取值三层白名单 + 参数化绑定（唯一真源 = `DishViewConditions`）；白名单外的字段 / 操作符 / 形态一律 `400`，不静默降级。
> 🔴 **不存在视图键**：视图由 **`id`** 标识，端上回传 `view=<id>`。

---

## GET /admin/dish-views

**用途**：视图列表

### 请求参数
无（**不分页**）。

### 响应 `data` = `DishViewAdminVO[]`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 视图 ID |
| `label` | string | tab 文案 |
| `order` | number | 展示顺序（升序） |
| `enabled` | boolean | 启停 |
| `conditions` | object[] | 筛选条件（结构见上「筛选条件语言」；`[]` = 不筛选） |
| `sortKind` | string | 排序口径（白名单见上） |
| `matchedCount` | number | **当前匹配的在售菜品数**（列表直接展示，兼作「是否生效」的自证） |
| `updatedAt` | string | 更新时间（`yyyy-MM-dd HH:mm:ss`） |

**排序**：按 `order` 升序（同序按 `id` 升序）。

---

## POST /admin/dish-views

**用途**：**新建视图**（新 tab 默认排最后，且**默认启用**）

### 请求体 `DishViewCreateReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `label` | string | **是** | tab 文案（1~32 字，如「面食粉类」） |
| `conditions` | object[] | 否 | 筛选条件（缺省 / `[]` = 不筛选）；结构与白名单见上 |
| `sortKind` | string | **是** | 排序口径（白名单见上） |

> **不含 `order` / `enabled`** —— 顺序由列表拖拽维护，启停由 `PUT` 维护；新建后即为启用状态。

### 响应 `data` = `DishViewAdminVO`
新建的视图。

---

## PUT /admin/dish-views/{id}

**用途**：修改（**文案 / 启停 / 条件 / 排序口径整体替换**）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 视图 ID |

### 请求体 `DishViewUpdateReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `label` | string | **是** | tab 文案（1~32 字） |
| `enabled` | boolean | **是** | 是否在 client 首页出现（停用 = tab 隐藏） |
| `conditions` | object[] | **是** | 筛选条件（`[]` = 不筛选）；结构与白名单见上 |
| `sortKind` | string | **是** | 排序口径（白名单见上） |

> **整体替换语义**：四个字段一次提交，未传 / `null` → `400`（不出现「漏传字段导致旧值残留」）。

### 响应 `data`
`null`。

> **约束**：**不允许停用「最后一个启用的视图」**（否则端上筛选栏为空、无落地页）。

---

## PUT /admin/dish-views/sort

**用途**：**整体提交顺序**（拖拽后一次提交）

### 请求体 `SortItemsReq`
`items`：`[{ id, order }]` 数组，整体替换顺序。**边界（全量行 / 非法提交 → `400`）见 [api/README 的「拖拽排序提交」](../README.md#拖拽排序提交)**。

### 响应 `data`
`null`。

---

## DELETE /admin/dish-views/{id}

**用途**：删除视图（tab 从下发集合移除）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 视图 ID |

### 响应 `data`
`null`。

> **约束**：**不允许删除「最后一个启用的视图」**（与停用同一道护栏 —— 否则端上筛选栏为空）。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `label` 为空或超长；条件字段 / 操作符 / 取值形态不在白名单；`sortKind` 不在白名单；`conditions` / `sortKind` 缺失；**停用或删除「最后一个启用的视图」**；排序提交非法 |
| `4001` | 视图不存在 |
| `401` | 未带 / token 失效 |

### 备注
- **视图全在库**：tab 的筛选 / 排序 / 文案 / 顺序 / 显隐都是 `dish_filter_view` 的行数据，后台一次改完即生效（写后失效 `GET /dishes/views` 缓存）。
- **排序端点用 `/sort`**：`items` 全量提交，服务端整体替换 `order`（与全站拖拽排序口径一致）。
- **条件语言是白名单、不可配 SQL**：字段 / 操作符 / 值三层白名单 + 参数化绑定，封死开放 SQL。
