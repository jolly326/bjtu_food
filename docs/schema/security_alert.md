# security_alert — 安全告警记录

**用途**：每一条安全告警的**可查询留痕**（**只追加**）—— 实时推送（飞书机器人群消息）解决「立刻知道」，本表解决「事后回看」。
**对应实体**：`com.bjtufood.common.alert.entity.SecurityAlert`；写入点 `com.bjtufood.common.alert.SecurityAlertRecorder`。
**安全令径**：[secur/web/审计与告警.md](../secur/web/审计与告警.md)；类型与级别见 `com.bjtufood.common.alert.AlertType`。

> **为什么必须落库**：群消息会被刷走、无法按类型回看 —— 复盘时只能靠翻聊天记录，而「这台机器上到底发生过什么」应是可查询、可筛选的事实。
> **只追加**：不提供修改 / 删除入令。
> **不含敏感值**：标题与明细由调用方传脱敏文本，口令 / token / 密钥一律不入表。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 告警记录 ID |
| `alert_type` | VARCHAR(32) | NO | — | | 类型键（枚举名，如 `LOGIN_LOCKOUT`） |
| `alert_type_label` | VARCHAR(16) | NO | `''` | | 类型中文标签（**写入时快照**） |
| `severity` | VARCHAR(8) | NO | — | | 级别键：`info` / `warn` / `critical` |
| `severity_label` | VARCHAR(16) | NO | `''` | | 级别中文标签（**写入时快照**） |
| `title` | VARCHAR(128) | NO | — | | 告警标题 |
| `detail` | VARCHAR(1024) | YES | NULL | | 告警明细（脱敏，按列宽截断） |
| `source_ip` | VARCHAR(64) | YES | NULL | | 来源 IP（调用线程无 Web 上下文时为 NULL） |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 发生时间（**DB 时钟写入**，应用不写、不填充） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_security_alert_time` | `created_at` | 普通 | 面板默认「按时间倒序」翻页 |
| `idx_security_alert_type` | `alert_type`, `created_at` | 普通 | 按类型筛选 + 时间排序 |

## 关键设计

### 标签作为快照落库，而不是展示时反查枚举
`alert_type_label` / `severity_label` 在写入时就存中文。原因：枚举常量会重命名，而**告警记录必须永久可读** —— 展示时反查枚举意味着「改一次代码，历史记录集体换文案甚至变空」。

### 级别由类型决定
同一类型的事件严重度固定（见 `AlertType`）：调用方只传类型，级别由枚举给出 —— 让每个调用点自选级别，级别必然漂移（同类事件时高时低，反而失去筛选价值）。

### 落库与推送都在告警线程池内
调用线程只做「取当前请求 IP + 组装行对象」；写库与推送都在单线程守护线程池里完成，业务请求不被 DB 往返与 3 秒 webhook 超时拖慢。
🔴 **IP 必须在调用线程取** —— 告警线程没有 Web 上下文，异步后再取只会得到 NULL。

### 降级：写不进去也不能影响业务，更不能递归
落库失败只记 ERROR，**不再推送告警**（推送本身会再写一行，形成递归）。告警是旁路，绝不因「记录写不进去」而让业务失败。

### 排序令径
面板按 `created_at DESC, id DESC`：同一秒内产生的多条告警需要稳定行序，仅按时间排序会让页面刷新时行序抖动。

## 关联

无外键：表中 `source_ip` 与业务实体无关；触发主体（管理员 / 学生账号）以标题与明细内的脱敏文本表述，不引外键 —— 告警必须在账号行已不存在时仍然可读（如「账号注销」告警）。

## 注意事项

- **只追加**：不提供 `UPDATE` / `DELETE` 入令。
- **保留期与归档**：见 [secur/web/审计与告警.md](../secur/web/审计与告警.md)（与审计日志同令径）。
- 本表**不进任何业务出参**；仅由管理端「安全告警」面板分页读取（[api/web/alerts.md](../api/web/alerts.md)）。
