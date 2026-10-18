# 学生端接口 · 推荐菜品

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/README.md`](../README.md)。

## `GET /dishes`

🔑 需登录 —— 推荐页的扑克卡片序列。

| 项 | 口径 |
|---|---|
| 入参 | `canteenId`（可选，食堂筛选）· `pageSize` |
| 出参 | `PageResult<DishCardVO>` |
| 排序 | **加权随机** —— 权重取自 `avg_reaction`（评价越高越易出现）；零评分菜按**基线权重**参与 |
| 过滤 | 仅 `status = 'on'`（在售）的菜品 |
| 错误码 | `400` 参数非法（`canteenId` 不存在） |

**`DishCardVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 菜品 ID |
| `name` | string | 否 | 菜品名称 |
| `price` | number | 否 | 现价，**单位「分」** |
| `imageUrl` | string | **是** | 菜品大图绝对 URL；`null` = 该菜未配图 |
| `flavorLevel` | string | **是** | **群体风味评级**字母：`S` / `A` / `B` / `C` / `D`；**`null` = 待评**（评分条数 < 3）—— 卡片左上字母 + 右上风味评级据此渲染 |
| `wanted` | boolean | 否 | 当前用户对该菜**是否已有未评分记录**（决定「去品尝」按钮是否为已标记态） |
| `canteenName` | string | 否 | 食堂名（位置） |
| `stallName` | string | 否 | 档口名（位置） |

> ⚠️ **分页与随机**：每次请求按加权随机**重掷**，不保证跨页不重不漏 —— 扑克卡片是「随便看看」场景，端上不作全量遍历假设。
> 列定义见 [`schema/dish.md`](../../schema/dish.md) · 业务规则见 [`func/client/A2`](../../func/client/A-浏览与发现/A2-推荐菜品.md) · 评分口径见 [`func/client/B2`](../../func/client/B-轻量反馈/B2-评分反馈.md)。
