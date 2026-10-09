# web/alerts — 安全告警记录

**归属**：`AdminAlertController` ｜ **功能口径**：[`func/web/C-账号与访问/C3-安全告警.md`](../../func/web/C-账号与访问/C3-安全告警.md) ｜ **安全设计**：[`secur/web/审计与告警.md`](../../secur/web/审计与告警.md)
**通用约定**见 [api/README.md](../README.md)
**鉴权**：需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 回看安全告警记录。**只读**：记录只追加，本资源**没有**写端点 —— 能改告警的人就能抹掉自己的痕迹。
> 记录的产生口径（哪些事件会告警、级别如何定）见 [`secur/web/审计与告警.md`](../../secur/web/审计与告警.md) 与 `AlertType`，本文档不复述。

---

## GET /admin/alerts

**用途**：安全告警记录（分页，按发生时间倒序）

### 请求参数
| 名称 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` / `pageSize` | number | 否 | 1 / 20 | 页码 / 每页条数（上限 **50**） |
| `alertType` | string | 否 | — | 告警类型键（如 `LOGIN_LOCKOUT`）；**不传 = 全部** |
| `severity` | string | 否 | — | 级别键：`info` / `warn` / `critical`；**不传 = 全部** |

**排序**：`createdAt DESC`，同秒按 `id DESC`（同一秒的多条告警需要稳定行序，否则刷新时顺序抖动）。

### 响应 `data` = `AdminPageResult<SecurityAlertVO>`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 告警记录 ID |
| `alertType` | string | 类型键（枚举名） |
| `alertTypeLabel` | string | 类型中文标签（**写入时快照**） |
| `severity` | string | 级别键：`info` 提示 / `warn` 警告 / `critical` 严重 |
| `severityLabel` | string | 级别中文标签（写入时快照） |
| `title` | string | 告警标题 |
| `detail` | string | 明细（脱敏；无明细为空串） |
| `sourceIp` | string | 来源 IP（无 Web 上下文时为空串） |
| `createdAt` | string | 发生时间（`yyyy-MM-dd HH:mm:ss`） |

> **标签字段是快照而非查表**：端上直接展示 `alertTypeLabel` / `severityLabel`，**不反查**枚举 —— 类型常量重命名不会让历史记录失去可读性。端上做类型下拉时可用本地键表，但**未知键必须原样显示**（未知类型本身就是需要看到的信息）。

### 告警类型键（`alertType`）
| 键 | 中文标签 | 级别 | 触发场景 |
|---|---|---|---|
| `LOGIN_SUCCESS` | 登录成功 | `info` | 管理端每次成功登录 |
| `LOGIN_LOCKOUT` | 登录失败达阈值 | `critical` | 同一账号连续失败达锁定阈值（疑似暴力破解） |
| `PASSWORD_CHANGED` | 口令修改 | `warn` | 改密成功（既有 token 全部失效） |
| `AUDIT_WRITE_FAILURE` | 审计写入失败 | `critical` | 审计日志落库失败（留痕缺口） |
| `VIOLATION_PENALTY` | 违规累积处置 | `warn` | 学生账号触发自动限言 / 封禁 |
| `CRAWL_DETECTED` | 爬取检测 | `warn` | 顺序枚举 ID / 批量命中不存在的资源 |

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `page` / `pageSize` 非法（非数字、`< 1` 等） |
| `401` | 未带 / token 失效（含改密后凭证版本失效） |
| `403` | 无（本端点只有读，任何已认证角色均可访问） |
