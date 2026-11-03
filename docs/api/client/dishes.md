# 学生端接口 · 菜品

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/CONVENTIONS.md`](../CONVENTIONS.md)。
> 列定义见 [`schema/dish.md`](../../schema/dish.md) 与 [`schema/dish_offering.md`](../../schema/dish_offering.md)。

## `GET /dishes`

🔑 需携带静默身份 `student_token` —— 全量在售菜品，供学生端图标网格初始渲染。

| 项 | 口径 |
|---|---|
| 入参 | 无（不分页） |
| 出参 | `DishVO[]` —— 全量在售菜品 |
| 过滤 | 仅 `activate = true` |
| 排序 | 服务端不排序（网格顺序由端上决定） |
| 缓存 | 响应带 `ETag` + `Cache-Control: private, max-age=30`；后台写操作后立即失效 |
| 错误码 | `401` 缺少 / 无效身份 token |

**`DishVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 菜品 ID |
| `name` | string | 否 | 菜品名称 |
| `imageUrl` | string | **是** | 菜品大图绝对 URL；`null` = 未配图 |
| `description` | string | 否 | 档案富文本（缺失为 `''`） |
| `locations` | `DishLocationVO[]` | 否 | 售卖点列表，恒为数组 |

**`DishLocationVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `canteenName` | string | 否 | 食堂名 |
| `stallName` | string | 否 | 档口名；无档口为 `''` ⇒ 展示「暂无」 |
| `floor` | string | 否 | 楼层；缺失为 `''` ⇒ 展示「暂无」 |
| `price` | number | **是** | 售价（**分**）；`null` = 未录入 |

> 一菜多售卖点 ⇒ `locations` 多项，内联展开卡逐项展示「食堂 · 楼层 · 档口 · 价格」。

## `POST /dishes/recommend`

🔑 需携带静默身份 `student_token` —— **输入一句话，返回匹配菜品**。

| 项 | 口径 |
|---|---|
| 入参 | `{ query: string }` —— 用户原话，1–200 字 |
| 出参 | `{ dishes: DishVO[] }` —— 命中菜品，按相关度降序 |
| 处理 | ① 抽取硬约束（食堂 / 价格 / 名称）→ ② SQL 精确过滤 → ③ 语义向量召回 → ④（可选）决策模型对 top-K 精排 |
| 空结果 | `dishes = []` |
| 降级 | 模型超时 / 失败 ⇒ `500`，端上展示「稍后再试」重试 |
| 限频 | 见 [`secur/S-03`](../../secur/S-03-限频与接口防护.md) |
| 错误码 | `400` query 缺失 / 超长 ｜ `401` 身份无效 ｜ `429` 命中限频 ｜ `500` 模型异常 |

> 业务规则见 [`func/client/A1`](../../func/client/A-浏览与发现/A1-搜索找菜.md)。「软意图」直接由 `query` 原文计算向量，不做自由文本抽取；硬约束只认食堂 / 价格 / 名称三类。

## 契约摘要

| 端点 | 用途 |
|---|---|
| `GET /dishes` | 初始网格：全量在售菜品 |
| `POST /dishes/recommend` | 一句话匹配：返回命中菜品 |
