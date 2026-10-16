# 学生端接口 · 菜品纠错

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定（信封 / 分页 / 错误码 / 金额 / 时间）见 [`api/README.md`](../README.md)。

## 端点

### `GET /dish-issue-options`

选项字典（🔑 需登录，只读）。

- **出参**：`[{ id, name }]` —— 按 `id` 升序；端上零硬编码、不排序。

### `POST /dishes/{id}/issues`

提交菜品纠错（🔑 需登录）。

- **入参**：`{ optionId }` —— **只有一个字段**；缺少 / 不存在 ⇒ `400`
- **归属**：提交人取自登录态，**禁止前端传入**
- **去重**：同一人 + 同一菜 + 同一选项的**未处理**记录幂等复用，不新增
- **出参**：`data: null`
- **错误码**：`400` 参数非法 ｜ `429` 命中限频

> 列定义见 [`schema/dish_issue.md`](../../schema/dish_issue.md) · 业务规则见 [`func/client/B3`](../../func/client/B-轻量反馈/B3-菜品纠错.md) · 管理端见 [`api/web/issues.md`](../web/issues.md)。
