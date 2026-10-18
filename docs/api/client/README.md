# 学生端接口 · 资源目录

> **契约真源**：本文与各资源文档；端上类型由后端 **Pydantic 模型经 FastAPI 导出**（见「契约同步」）。

> 各 feature 文档只描述**业务规则**（为什么、边界在哪），端点签名与字段一律以本目录为准。

## 资源索引

| 文档 | 覆盖端点 | 消费方 |
|---|---|---|
| [auth.md](./auth.md) | `POST /auth/wechat-login` | openid 静默识别 |
| [posters.md](./posters.md) | `GET /posters` | 海报页 |
| [dishes.md](./dishes.md) | `GET /dishes` | 美味页 |
| [canteens.md](./canteens.md) | `GET /canteens` | 食堂筛选 |
| [records.md](./records.md) | `GET /my/records` · `POST /my/records` · `PATCH /my/records/{id}` · `DELETE /my/records/{id}` | 记忆 · 去品尝 · 评分 · 取消品尝 |
| [issues.md](./issues.md) | `GET /dish-issue-options` · `POST /dishes/{id}/issues` | 菜品纠错 |

> 通用约定（信封 / 分页 / 错误码 / 金额 / 时间）→ [`../README.md`](../README.md)
