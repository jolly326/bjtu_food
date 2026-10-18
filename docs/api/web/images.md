# 运营端接口 · 图片

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/README.md`](../README.md)。

## `POST /admin/images`

🔑 需登录（运营端 JWT）—— 上传图片，创建图片资源。

| 项 | 口径 |
|---|---|
| 入参 | `multipart/form-data`，字段 `file` |
| 出参 | `{ url }` —— COS **绝对 URL**（入库时服务端转**相对路径**存储） |
| 限制 | 类型 `jpg` / `png` / `webp`（按文件魔数判定）；单张 **≤ 5MB**；**不校验宽高比**（与菜品大图共用端点） |
| 用途 | 竖版海报（[posters](./posters.md)，出图规范 3:4）· 菜品大图（[dishes](./dishes.md)） |
| 错误码 | `400` 类型 / 大小不合规 ｜ `401` 未登录 |

> **学生端无上传端点** —— 学生侧无文字与图片 UGC（见 [`func/client/README.md`](../../func/client/README.md) 的「合规与不做的事」）。
> 海报是**竖版 3:4**（见 [`ui/client/档口海报.md`](../../ui/client/档口海报.md)）。
