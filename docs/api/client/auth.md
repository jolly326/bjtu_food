# 学生端接口 · 身份

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/README.md`](../CONVENTIONS.md)。

## `POST /auth/wechat-login`

🔓 公开 —— openid **静默识别**。

| 项 | 口径 |
|---|---|
| 用途 | 小程序冷启动时以 `wx.login` 的 code 换取服务端凭证；服务端据此建 / 取 `user` 行 |
| 入参 | `{ code }` —— 微信登录凭据；缺失 / 无效 ⇒ `400` |
| 出参 | `{ token }` —— 服务端签发的访问凭证 |
| 端上 | 把 `token` 存入本地并在后续请求的 `Authorization` 头携带；**端上不持有、不传递 openid** |
| 副作用 | 首次调用 **INSERT** 一行 `user`（`openid` 唯一），此后 **SELECT** |
| 错误码 | `400` code 无效 / 换取失败 ｜ `429` 命中限频 |

> 列定义见 [`schema/user.md`](../../schema/user.md) · 业务规则见 [`func/client/C1`](../../func/client/C-用户身份/C1-openid静默识别.md)。
> **本端点是全站唯一的身份入口** —— 无注册、无登录页、无认证、无注销。
