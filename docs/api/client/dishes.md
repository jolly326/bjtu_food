# 学生端接口 · 菜品

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/CONVENTIONS.md`](../CONVENTIONS.md)。
> 列定义见 [`schema/dish.md`](../../schema/dish.md) 与 [`schema/dish_offering.md`](../../schema/dish_offering.md)。

## `GET /dishes`

🔑 需携带静默身份 `student_token` —— 全量在售菜品，供学生端无条件结果集渲染。

| 项 | 口径 |
|---|---|
| 入参 | 无（不分页） |
| 出参 | `DishVO[]` —— 全量在售菜品 |
| 过滤 | 仅返回至少有一个在售售卖点的菜品（由 `dish_offering.activate = true` 判定） |
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
| `offeringId` | number | 否 | 售卖点 ID（`dish_offering.id`）；端上作结果卡稳定键 |
| `canteenName` | string | 否 | 食堂名 |
| `stallName` | string | 否 | 档口名；无档口为 `''` ⇒ 展示「暂无」 |
| `floor` | string | 否 | 楼层；缺失为 `''` ⇒ 展示「暂无」 |
| `price` | number | **是** | 售价（**分**）；`null` = 未录入 |

> 一菜多售卖点 ⇒ `locations` 多项，端上按每项渲染一张结果卡；`description` 属菜品本体，随各卡展示。

## `POST /dishes/recommend`

🔑 需携带静默身份 `student_token` —— **输入一句话，返回匹配菜品**。

| 项 | 口径 |
|---|---|
| 入参 | `{ query: string }` —— 用户原话，1–200 字 |
| 出参 | `{ dishes: DishVO[] }` —— 命中菜品，按相关度降序 |
| 处理 | ① 抽取硬约束（**完整菜名 / 食堂名 / 价格上限**）→ ② 在售菜品与售卖点精确过滤 → ③ 语义向量余弦排序（取前 30）→ ④（可选）System 1 精排（取前 10） |
| 硬约束 | 完整菜名命中 ⇒ 只返回该菜；食堂名 / `N 元` 命中 ⇒ 只保留符合条件的**售卖点**（`locations` 同步收窄）；三类都未命中 ⇒ 不过滤 |
| 条数 | 未开精排最多 `30` 条、开精排最多 `10` 条；🔴 **不设相似度下限** —— 过滤后的结果全部按相关度降序返回（`dishes = []` 仅出现在硬约束过滤后为空、或向量未回填时） |
| 向量服务 | `EMBEDDING_PROVIDER=local`（本地哈希）或 `http`（OpenAI 兼容）；**只使用 `embedding_model` 与当前模型一致的预计算向量**，不符的行在外部模式下**跳过**（由回填脚本补齐） |
| 空结果 | `dishes = []` |
| 缓存 | 同一 `query` **30 秒内复用结果**（含向量与精排结果）；后台任何菜品 / 食堂 / 档口写操作立即失效 |
| 降级 | **向量服务**超时 / 失败 ⇒ `500`（端上提示稍后再试）；**精排失败 ⇒ 退化为向量排序**，不报错 |
| 限频 | 见 [`secur/S-03`](../../secur/S-03-限频与接口防护.md) |
| 错误码 | `400` query 缺失 / 超长 ｜ `401` 身份无效 ｜ `429` 命中限频 ｜ `500` 模型异常 |

> 业务规则见 [`func/client/A1`](../../func/client/A-浏览与发现/A1-搜索找菜.md)；“软意图”直接由 `query` 原文计算向量，不做自由文本抽取；硬约束只认三类。

## 契约摘要

| 端点 | 用途 |
|---|---|
| `GET /dishes` | 初始网格：全量在售菜品 |
| `POST /dishes/recommend` | 一句话匹配：返回命中菜品 |
