# admin_audit_log — 管理端操作审计日志

**用途**：`/admin/**` 全部**写操作**的留痕（**只追加**）—— 管理端是全站唯一具备不可逆破坏力（物理删菜品 + 级联删评价）的入口，「谁、在什么时间、对哪个资源、用什么方法、结果如何」必须可追溯、可举证。
**对应实体**：`com.bjtufood.common.audit.entity.AdminAuditLog`；写入点 `com.bjtufood.common.audit.AdminAuditRecorder`。
**安全口径**：[secur/web/审计与告警.md](../secur/web/审计与告警.md)。

> **只追加**：不提供修改 / 删除入口。
> **不记录请求体**：请求体可能含口令类字段，一律不入审计表。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 自增 ID |
| `admin_id` | BIGINT | NO | — | | 操作人（`admin_account.id`） |
| `http_method` | VARCHAR(8) | NO | — | | 仅写操作落库：`POST` / `PUT` / `DELETE` / `PATCH` |
| `path` | VARCHAR(255) | NO | — | | 应用内路径（已剥离 context-path），如 `/admin/dishes/12` |
| `target_id` | VARCHAR(64) | YES | NULL | | 自路径提取的目标资源 ID（纯数字段；无则 NULL） |
| `result` | VARCHAR(32) | NO | — | | `success` / `fail:<HTTP 状态码>` |
| `ip` | VARCHAR(64) | YES | NULL | | 来源 IP |
| `ua` | VARCHAR(255) | YES | NULL | | User-Agent（按列宽截断） |
| `before_value` | TEXT | YES | NULL | | 变更前对象快照（JSON，脱敏）；删除类只有本侧 |
| `after_value` | TEXT | YES | NULL | | 变更后对象快照（JSON，脱敏）；状态跃迁类才有 |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 发生时间（**DB 时钟写入**，应用不写、不填充） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_admin_audit_log_admin_time` | `admin_id`, `created_at` | 普通 | 按操作人查时间线 |
| `idx_admin_audit_log_time` | `created_at` | 普通 | 按时间范围检索 / 归档 |

## 关键设计

### 覆盖范围由「路径 + 方法」判定
落库条件 = `path` 命中 `/admin/**` **且** `http_method` 为写方法。新增写端点**自动纳入覆盖**，不存在「忘了挂审计」的漏挂面（无需逐方法标注解）。

### 只落写操作
读请求不落库 —— 管理端读多写极少，逐请求落库会把审计表刷爆并使信号淹没在噪声里。

### 审计失败不阻塞业务
写入失败回滚与否不改变业务结果：失败记 ERROR **并推送安全告警**（留痕缺口必须可见，绝不静默）。

### 保留期
**1 年**；超期归档（归档动作本身亦须留痕）。

### 变更前后值快照（`before_value` / `after_value`）
只记「谁在什么时间调了哪个端点」不足以回答**误删后如何重建** —— 快照补的正是「那条记录原来长什么样」。
登记方式是 Service 在改动前调用 `AuditSnapshot.before(对象)`、改动后调用 `AuditSnapshot.after(对象)`，
由请求结束时的写入点（`AdminAuthFilter`）取走并落库（**取走即清除**，不跨请求残留）。

| 覆盖 | 说明 |
|---|---|
| 删除类（全部 `DELETE` 端点） | 只有 `before_value`（删除后无对象）；菜品 / 评价 / 账号处置 / 主数据删除均覆盖 |
| 状态跃迁类（评价隐藏 / 恢复、账号启停、解绑认证邮箱、账号注销） | `before_value` 与 `after_value` 双侧 |
| 对象只记**现状字段** | 不记请求体（请求体可能含口令类字段，一律不入审计表） |
| 序列化失败 | 快照缺失但**留痕仍在**，记 ERROR（审计是旁路，不得把业务请求打成 500） |

## 关联

无外键：`admin_id` 引用 `admin_account.id`，但管理端账号行不物理删除（停用为状态变更），约束无实际收益。

## 注意事项

- **禁止**为审计表提供 `UPDATE` / `DELETE` 入口（只追加是这张表的全部价值）。
- 审计表**不进**任何管理端 UI 的常规列表查询路径（读放大 → 不参与业务出参）；排查时按 `admin_id` / 时间范围直查。
