# 运营端接口 · 资源目录

> **契约真源**：本文与各资源文档；端上类型由后端 **Pydantic 模型经 FastAPI 导出**（见「契约同步」）。

> 各 feature 文档只描述**业务规则**（为什么、边界在哪），端点签名与字段一律以本目录为准。

## 资源索引

| 文档 | 覆盖端点 |
|---|---|
| [auth.md](./auth.md) | 登录与 `/admin/**` 的 `Authorization: Bearer` 鉴权 |
| [canteens.md](./canteens.md) | `/admin/canteens*` |
| [stalls.md](./stalls.md) | `/admin/stalls*` |
| [posters.md](./posters.md) | `/admin/posters*` |
| [dishes.md](./dishes.md) | `/admin/dishes*` |
| [records.md](./records.md) | `/admin/dish-records*`（评分数据，仅读） |
| [issues.md](./issues.md) | `/admin/dish-issues*` · `/admin/dish-issue-options*` |
| [images.md](./images.md) | `POST /admin/images` |

> 通用约定（信封 / 分页 / 错误码 / 金额 / 时间）→ [`../README.md`](../README.md)
