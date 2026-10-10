# web/views — 首页筛选视图管理

**归属**：`DishViewAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 首页筛选 tab 的**全部口径都在这里**：文案 + **筛选种类** + 显隐。视图是纯数据（见 [schema/dish_filter_view.md](../../schema/dish_filter_view.md)），新增 / 删除视图均为后台自助操作、**免发版**。
> 菜品种类字典（`/admin/dish-categories`）见 [web/categories.md](./categories.md)。
> 客户端下发契约（`GET /dishes/views` / `GET /dishes?view=`）见 [dishes.md](../client/dishes.md)。

---

## 视图语义（`categoryId`）

| `categoryId` | 含义 | 端上表现 |
|---|---|---|
| `null` | **「为你推荐」** | 不按种类筛，取全量在售菜品 |
| 非 `null` | 该种类 tab | 值域 = `dish_category.id`（见 [web/categories.md](./categories.md)）；后端按该种类筛在售菜品 |

- **排序口径不入库**：`GET /dishes` 的排序统一为**会话种子伪随机序**（见 [dishes.md](../client/dishes.md)），tab 之间无排序差异；
- 🔴 **不存在视图键**：视图由 **`id`** 标识，端上回传 `view=<id>`；
- 🔴 **筛选面只有种类一维**：视图不持有字段 / 操作符条件语言，无可配 SQL 面。

> **种子构成（7 行）**：1 个「为你推荐」（`categoryId = null`）+ 6 个种类 tab（与 `dish_category` 的 6 条种子一一对应）。
> **下发判据**：`GET /dishes/views` 只下发 `enabled = true` 且**匹配数不为 0** 的视图；`GET /dishes` 不传 `view` ⇒ 取首个启用视图。

---

## GET /admin/dish-views

**用途**：视图列表

### 请求参数
无（**不分页**）。

### 响应 `data` = `DishViewAdminVO[]`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 视图 ID |
| `name` | string | tab 文案 |
| `categoryId` | number \| null | 筛选种类 ID（`null` = 为你推荐） |
| `enabled` | boolean | 启停 |
| `matchedCount` | number | **当前匹配的在售菜品数**（列表直接展示，兼作「是否生效」的自证） |
| `updatedAt` | string | 更新时间（`yyyy-MM-dd HH:mm:ss`） |

**排序**：按 `id` 升序（字典不设排序位）。

---

## POST /admin/dish-views

**用途**：**新建视图**（默认**启用**，`id` 自增 ⇒ 排在末尾）

### 请求体 `DishViewCreateReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | **是** | tab 文案（1~32 字，如「面食粉类」） |
| `categoryId` | number \| null | 否 | 筛选种类 ID（须为已存在的 `dish_category.id`）；缺省 / `null` = 「为你推荐」（不按种类筛） |

> **不含 `enabled` / 顺序** —— 新建后即为启用状态；启停由 `PUT` 维护，顺序由 `id` 决定。

### 响应 `data` = `DishViewAdminVO`
新建的视图。

---

## PUT /admin/dish-views/{id}

**用途**：修改（**文案 / 筛选种类 / 启停整体替换**）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 视图 ID |

### 请求体 `DishViewUpdateReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | **是** | tab 文案（1~32 字） |
| `categoryId` | number \| null | 否 | 筛选种类 ID；`null` / 缺省 = 「为你推荐」（不按种类筛）；非空须命中已存在的种类 |
| `enabled` | boolean | **是** | 是否在 client 首页出现（停用 = tab 隐藏） |

> **整体替换语义**：三个字段一次提交；`name` / `enabled` 缺失或 `null` → `400`（不出现「漏传字段导致旧值残留」）。

### 响应 `data`
`null`。

> **约束**：**不允许停用「最后一个启用的视图」**（否则端上筛选栏为空、无落地页）。

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
| `400` | `name` 为空或超长；`categoryId` 非 `null` 且不是已存在的种类 ID；**停用或删除「最后一个启用的视图」** |
| `4001` | 视图不存在 |
| `401` | 未带 / token 失效 |

### 备注
- **视图全在库**：tab 的文案 / 筛选种类 / 显隐都是 `dish_filter_view` 的行数据，后台一次改完即生效（写后失效 `GET /dishes/views` 缓存）。
- **字典不设排序位**：视图列表与端上下发顺序一律按 `id` 升序。
- **筛选面收窄为「按种类」**：`categoryId` 是唯一的筛选字段 ⇒ 不存在「条件配错筛出空列表」的形态；是否生效由 `matchedCount` 自证。
