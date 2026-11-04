# 学生端接口 · 反馈

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/CONVENTIONS.md`](../CONVENTIONS.md)。
> 列定义见 [`schema/feedback.md`](../../schema/feedback.md)。

## `POST /feedback`

🔑 需携带静默身份 `student_token` —— 提交文本反馈（产品级或菜品级）。

| 项 | 口径 |
|---|---|
| 入参 | `{ type: 'product' \| 'dish', dishId?: number, content: string }` |
| 校验 | `type = 'dish'` ⇒ `dishId` 必填；`type = 'product'` ⇒ 忽略 / 拒绝 `dishId`；`content` 1–500 字 |
| 内容安全 | 🔴 提交前走微信 `msgSecCheck`，命中违规拒绝 |
| 出参 | `null` |
| 限频 | 见 [`secur/S-03`](../../secur/S-03-限频与接口防护.md) |
| 错误码 | `400` 参数非法 / 正文为空或超长 / 内容安全未通过 ｜ `4001` 菜品不存在或无在售售卖点 ｜ `401` 身份无效 ｜ `429` 命中限频 |

**入参示例**

```json
{ "type": "dish", "dishId": 12, "content": "这道菜在东区比西区贵了 3 块" }
```

```json
{ "type": "product", "content": "搜索框希望支持语音输入" }
```

## 业务规则

- 提交人 `user_id` 取自服务端身份上下文，**禁止前端传入**；
- 不返回回执、不提供查询入口；
- 正文为用户自由文本，**必须过内容安全**（见 [`schema/feedback.md`](../../schema/feedback.md)）。
