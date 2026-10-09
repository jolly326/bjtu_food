# web/dishes — 菜品主数据管理

**归属**：`DishAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 管理端是菜品主数据的**唯一写入源**（学生端无菜品写接口）。位置（食堂 · 楼层 · 档口名）由档口**派生**（只选 `stallId`）；种类（`mealTypeId`，存 `dish.meal_type_id`）值域来自**系统维度（菜品种类）** 的取值 —— 见 [web/categories.md](./categories.md)；描述属性维度与取值来自 [web/dimensions.md](./dimensions.md)。

---

## GET /admin/dishes

**用途**：菜品列表（**分页**）

### 请求参数
| 名称 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` | number | 否 | 1 | 页码 |
| `pageSize` | number | 否 | 20 | 每页条数；上限 **50** |
| `stallId` | number | 否 | — | 按档口筛选 |
| `canteenId` | number | 否 | — | 按食堂筛选（经档口间接） |
| `mealTypeId` | number | 否 | — | 按种类筛选（系统维度取值 ID，见 [web/categories.md](./categories.md)） |
| `status` | string | 否 | — | 按上架状态筛选（`on` / `off`；不传 = 全部，**含已下架**）；非法值 `400` |
| `keyword` | string | 否 | — | 关键词（菜名 / 档口名 / 食堂名，与 client 搜索同口径） |

**排序**：`updatedAt DESC`（最近维护在前）。

### 响应 `data` = `AdminPageResult<DishAdminListItemVO>`

列表行（**瘦身**）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 菜品 ID |
| `stallId` | number | 所属档口 ID（行内跳转用） |
| `stallName` / `canteenName` | string | 档口 / 食堂名（联表带出） |
| `name` | string | 菜品名称 |
| `price` / `originalPrice` | number \| null | 现价（分）/ 原价（分） |
| `coverImage` | string | **封面图**（取 `images` 首图的派生值；无图为空串） |
| `mealTypeId` / `mealTypeLabel` | number / string | 种类取值 ID / 种类中文名 |
| `status` | string | `on` / `off` |
| `avgRating` | number \| null | 均分（零评价为 `null`） |
| `ratingCount` | number | 评价数（**列表即删除确认的影响面来源**） |
| `recentViewCount` | number | **近 30 天浏览量**（`dish_view_log` 滚动窗口；非历史累计。窗口内无浏览为 `0`） |
| `updatedAt` | string | 更新时间（排序依据） |

> 列表**不下发** `description` / `images[]` / `attributes` / `createdAt` / `viewCount` —— 列表 UI 不消费。
> **`viewCount`（历史累计）不出参**；浏览量由 `recentViewCount`（**近 30 天窗口**）下发 —— 两者语义不同、不可互相替代，详见 [`schema/dish_view_log.md`](../../schema/dish_view_log.md) §2.4。

---

## GET /admin/dishes/{id}

**用途**：单条详情（**编辑回填全字段**）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 菜品 ID |

### 响应 `data` = `DishAdminVO`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 菜品 ID |
| `stallId` | number | 所属档口 ID |
| `name` | string | 菜品名称 |
| `price` / `originalPrice` | number \| null | 现价（分）/ 原价（分，可空） |
| `description` | string | 描述（无为空串） |
| `images` | string[] | 图片**绝对 URL** 数组（有序、首图作封面；恒为数组） |
| `mealTypeId` / `mealTypeLabel` | number / string | 种类取值 ID / 种类中文名 |
| `attributes` | object | 描述属性（键 = **描述维度** ID，值 = **中文**，服务端由取值 ID 翻译；**不含菜品种类**） |
| `status` | string | `on` / `off` |
| `avgRating` / `ratingCount` | number \| null / number | 均分 / 评价数（只读展示） |
| `createdAt` / `updatedAt` | string | 时间（`yyyy-MM-dd HH:mm:ss`） |

> **不返回 `viewCount`**（历史累计浏览量）。浏览量由 `dish_view_log` 的 **30 天滚动窗口**承载，仅在**列表行**下发 `recentViewCount`（见上表），**详情不返回**。
> `stallName` / `canteenName` **只在列表回带**：详情的归属编辑是**档口下拉**（选项取自 [web/stalls.md](./stalls.md)）。

---

## POST /admin/dishes

**用途**：新增（录入即生效，公开可见性只看 `status`；新增默认 `on`）

### 请求体 `DishSaveReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `stallId` | number | **是** | 所属档口（必须存在，否则 `400`） |
| `name` | string | **是** | 菜品名称（1~64 字；**不要求唯一**，同名菜品靠档口区分） |
| `price` | number | **是** | **现价**（分，> 0 的整数） |
| `originalPrice` | number | 否 | **原价**（分，可空）；`> price` 时 client 显示划线 |
| `description` | string | 否 | 描述（≤512 字） |
| `images` | string[] | 否 | 图片地址（**1~5 张**、**有序**、**首图作封面**）；经 [`POST /admin/upload`](./upload.md) 上传**原样入库**，出参由服务端统一转绝对 URL |
| `mealTypeId` | number | **是** | **种类取值 ID**（来自 [web/categories.md](./categories.md)；单值）—— 必须属于**系统维度（菜品种类）**，否则 → `400` |
| `attributes` | object | 否 | 描述属性（键 = **描述维度 ID**，值 = **取值 ID** 或**取值中文名** —— 同维度内未命中即**自动登记并替换为 ID**；`400` 用于键不在维度字典 / **键 = 系统维度（菜品种类）** / 值形态非法）；**可空** |

> **不含 `status`** —— 上下架走独立端点。
> `attributes` 未传该维度 = 该菜没有这个属性（详情页**缺项不占位**）。

### 响应 `data` = `DishAdminVO`
新建的菜品。

---

## PUT /admin/dishes/{id}

**用途**：修改（**可编辑字段整体替换**）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 菜品 ID |

### 请求体 `DishSaveReq`
同 `POST`。**`null` 语义逐字段定死**：

| 字段 | 传 `null` / 空 的语义 |
|---|---|
| `name` / `mealTypeId` | 必填，空 → `400` |
| `originalPrice` | `0` / `null` ⇒ **清空** |
| `description` | `null` ⇒ 空串 |
| `images` / `attributes` / `stallId` | `null` ⇒ **不修改**（结构性内容，未带多为「未改动」）；要清空请传空数组 / 空对象 |
| `status` | 本端点**不写** |

> **图片校验**（与新增共用同一校验助手）：张数 `>5` → `400`；序列化后长度 `>1024` → `400`（对齐 `dish.images VARCHAR(1024)`，防静默截断）；**编辑传空集合 = 要求清空 ⇒ 拒绝**（要下架走 `PUT /{id}/status`；传 `null` = 不修改）。

### 响应 `data`
`null`。

---

## POST /admin/dishes/{id}/copy

**用途**：**复制为新菜品**（副本默认 `off` 下架）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 源菜品 ID |

### 请求体 `DishCopyReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | **是** | 新菜品名称（1~64 字）——其余字段**全部复制**源菜品 |

### 响应 `data` = `DishAdminVO`
新建的副本菜品。

---

## PUT /admin/dishes/{id}/status

**用途**：**上下架**（只改 `status`）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 菜品 ID |

### 请求体 `DishStatusReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `status` | string | **是** | `on` 上架 / `off` 下架 |

### 响应 `data`
`null`。

---

## DELETE /admin/dishes/{id}

**用途**：删除（**物理删除**，其评价**级联删除**）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 菜品 ID |

### 响应 `data`
`null`。

> ⚠️ **删除会影响历史**（评价与均分一起消失）⇒ UI 必须**二次确认**并明示「将一并删除该菜品的 **至少 N 条评价**」（N 取列表已有的 `ratingCount`）。
> 反馈记录**保留**（`dish_correction` 不设外键，是「用户反馈过什么」的历史痕迹）。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | 名称为空 / 超长；价格非正整数；`stallId` 不存在；`mealTypeId` 缺失或**不属于系统维度（菜品种类）**；`attributes` 的维度 ID 不在维度字典 / **携带系统维度** / 值形态非法；图片超过 5 张、地址非法或**序列化后超 1024 字**；`status` 非法 |
| `4001` | 菜品不存在（`GET` / `PUT` / `PUT .../status` / `DELETE` / `copy` 目标不存在） |
| `401` | 未带 / token 失效 |

### 备注（接口选型）
- **`PUT` 整体替换而非部分更新**：本页是**表单式**编辑（提交全量字段），整体替换语义明确、不会出现「漏传一个字段导致旧值残留」；上下架是**行内操作**，单独端点比「只传 status」更清晰。
- **列表 / 详情 VO 分离**：列表是「扫视」场景（要小、要快），详情是「编辑回填」场景（要全）；共用 VO 会让列表为 20 行 × 512 字描述 + 属性 Map 买单。
- **全站不用 `PATCH`**；**不引入 HTTP `409`**（业务冲突归 `400` + 明确 `message`）；`DELETE` 目标不存在返回 `4001`。
- **菜品名不唯一**：同名菜品靠档口区分；强制唯一会阻碍「两个档口都卖番茄炒蛋」这类真实情况。
- **`ratingCount` 兼作删除影响提示值**：它是**可见**评价数（`is_hidden = 0`）；删除会一并删掉隐藏评价，实际删除数**不少于**此值，故确认文案用「**至少** N 条评价」口径。
