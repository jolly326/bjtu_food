# 菜品（学生端）

> 真源：端点 / 入参 / 出参 / 错误码。页面形态见 [`docs/ui/client/菜品首页.md`](../../ui/client/菜品首页.md)，业务流程见 [`docs/func/client/A-浏览与发现/A1-搜索找菜.md`](../../func/client/A-浏览与发现/A1-搜索找菜.md)。

## `GET /dishes` — 全量在售菜品

**鉴权**：🔑 匿名（静默 `student_token`）｜**限频**：读接口账户级

| 项 | 口径 |
|---|---|
| 过滤 | 只返回**至少有一个在售位置**的菜品；`locations` 只含 `activate = true` 的位置 |
| 缓存 | `ETag` + `Cache-Control: private, max-age=30`；`If-None-Match` 命中返回 `304` |
| 排序 | 菜品 `id` 升序，位置 `id` 升序（端上按菜名做一次稳定序） |

**出参 `data: DishVO[]`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 菜品 ID |
| `name` | string | 否 | 菜名；不同档口同名是不同 `id` |
| `imageUrl` | string | **是** | 菜品图（绝对 URL）；`null` = 无图 |
| `description` | string | 否 | 档案富文本（无描述为空串） |
| `price` | number | **是** | 售价（**分**），同一菜品多位置共用；`null` = 未录入 |
| `locations` | `DishLocationVO[]` | 否 | 该菜品的在售位置，恒为数组 |

**`DishLocationVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 位置 ID（学生端卡片身份） |
| `canteenName` | string | 否 | 食堂名 |
| `stallName` | string | 否 | 档口名；无档口为 `''` ⇒ 展示「暂无」 |
| `floor` | string | 否 | 楼层（取档口）；缺失为 `''` ⇒ 展示「暂无」 |

> 一道菜挂 N 个在售位置 ⇒ 端上渲染 N 张卡：图 / 描述 / 价格相同，`食堂 · 楼层 · 档口` 不同。

## `POST /dishes/recommend` — 一句话匹配

**鉴权**：🔑 匿名｜**限频**：推荐接口账户级

**入参**：`{ query: string }`，≤200 字。

**出参 `data: { dishes: DishVO[] }`**（字段同上）

| 环节 | 口径 |
|---|---|
| 硬约束 | 完整菜名 / 食堂名 / 价格上限（`N 元` / `N 块`，不超过）；按位置过滤后按菜品聚合 |
| 排序 | 车型向量余弦降序（一道菜共享向量），同分保持服务端原始顺序；端上不二次排序 |
| 条数 | 未开精排最多 30 条、开精排最多 10 条；不设相似度下限 |
| 精排 | `SYSTEM1_ENABLED=true` 时对 top-30 rerank 取前 10；失败退回向量排序 |

**错误码**

| 码 | 场景 |
|---|---|
| `400` | 查询为空 / 超过 200 字 |
| `401` | 身份无效 |
| `429` | 命中限频（带 `Retry-After`） |
