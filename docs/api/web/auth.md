# 运营端接口 · 登录与鉴权

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/README.md`](../README.md)。

运营端采用**独立管理员账号 + 账密登录**，与学生的 `user` 体系**完全隔离**。

## `POST /admin/sessions`

🔓 公开 —— 登录（创建会话），换取运营端 JWT。

| 项 | 口径 |
|---|---|
| 入参 | `{ username, password }` |
| 出参 | `{ token, username }` |
| 后续请求 | 带 `Authorization: Bearer <token>`（**独立 secret 签名**，与学生端凭证不通用） |
| 错误码 | `400` 参数缺失 ｜ **`401` 用户名或密码错误** ｜ `429` 命中限频（防爆破） |

> 前端**不持有口令**（登录后仅持 token）；口令以 BCrypt 哈希比对，列定义见 [`schema/admin.md`](../../schema/admin.md)。
> **无注册、无自助改密端点** —— 账号行由数据库维护。
> 登出由**前端清除 token** 完成，无服务端吊销端点。

## 鉴权约定

`/admin/**` 下除 `POST /admin/sessions` 外**全部需要** `Authorization: Bearer <token>`；缺失 / 失效 ⇒ `401`（前端清 token 跳登录页）。

> 运营端**无角色分级**（单管理员运营）—— 不设按角色的入口隐藏。
