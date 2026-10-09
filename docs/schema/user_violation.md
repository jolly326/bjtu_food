# user_violation — 学生账号违规与处置留痕

**用途**：记录学生账号**每次违规来源与对应处置动作**（**只追加**）。
**对应实体**：`com.bjtufood.auth.entity.UserViolation`。
**业务口径**：[secur/client/滥用与内容防护.md](../secur/client/滥用与内容防护.md) §4。

> **为什么独立成表**：违规累积处置是**系统自动执行**的动作（不是某个管理员点的）—— 若不单独留痕，「某账号为什么被限言 / 被封」将无从复盘，而封禁直接影响学生使用，必须能回答「依据是什么、什么时候处置的」。
> **只追加**：不提供修改 / 删除入口。
> **不含原文**：`detail` 只记来源与线索，不存放违规内容全文（避免把违规文本二次留存到另一张表里）。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 自增 ID |
| `user_id` | BIGINT | NO | — | | 归属用户（`user.id`） |
| `source` | VARCHAR(32) | NO | — | | 来源：`moderation`（机审 `risky` 命中）/ `report`（举报成立） |
| `detail` | VARCHAR(255) | YES | NULL | | 违规线索（脱敏摘要，不含原文全文） |
| `action` | VARCHAR(32) | NO | — | | 本次触发的处置：`counted` / `warn` / `mute_24h` / `mute_7d` / `ban` |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 发生时间（**DB 时钟写入**，应用不写、不填充） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_user_violation_user` | `user_id`, `created_at` | 普通 | 按用户查违规时间线 |

## 关键设计

### 处置档位判定
累计次数（`user.violation_count`，含本次）落到哪一档，`action` 即写哪一档：

| 累计次数 | `action` | 效果 |
|---|---|---|
| 1 ~ 2 | `counted` | 仅累计，不改账号状态 |
| 3 ~ 5 | `warn` | 账号标记 + 警告 |
| 6 ~ 8 | `mute_24h` | `user.muted_until` = 现在 + 24 小时 |
| 9 ~ 11 | `mute_7d` | `user.muted_until` = 现在 + 7 天 |
| ≥ 12 | `ban` | `user.status='disabled'` + 吊销该 userId 全部 token |

- **计数与账号列同事务**：`user.violation_count` / `user.muted_until` / `user.status` 的写入与本次留痕在同一事务内，且与「启停 / 解绑 / 注销」共用同一把 userId 锁（避免自动封禁与人工启用交错）。
- **每次处置动作必推告警**：`counted` 之外的档位一律推送安全告警（防「静默封号」）。
- **已注销账号不再处置**（其内容已匿名化归属，继续升级无意义），也不产生本表记录。

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| n→1 | `user_violation.user_id` → `user.id` | 违规主体 | 无外键（`user` 行永不物理删除） |

## 注意事项

- `action` 是**本次触发的动作**，不是账号当前状态；账号当前是否限言看 `user.muted_until` 是否在未来。
- 本表不进任何管理端常规列表查询路径；排查时按 `user_id` 直查。
