# email_verification_code — 邮箱验证码

**用途**：学号邮箱认证的验证码。发码写入、核验时原子消费。
**对应实体**：`com.bjtufood.auth.entity.EmailVerificationCode`

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 记录 ID |
| `email` | VARCHAR(128) | NO | `''` | KEY | 目标邮箱（由学号推导 `{学号}@bjtu.edu.cn`） |
| `code_hash` | VARCHAR(128) | NO | `''` | | 验证码的 **BCrypt 散列**（**不存明文**） |
| `purpose` | VARCHAR(32) | NO | `'verify'` | KEY | 用途；当前**仅 `verify`**（认证用途） |
| `expires_at` | DATETIME | YES | NULL | KEY | 过期时间（发码时 = 创建 +10 分钟） |
| `used_at` | DATETIME | YES | NULL | | 消费时间；**NULL = 未使用** |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 发码时间；**同邮箱 60 秒冷却的判据** |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `idx_evc_email` | `email`, `purpose` | 复合 | 同邮箱冷却查询（`ORDER BY created_at DESC LIMIT 1`） |
| `idx_evc_expires` | `expires_at` | 普通 | 定时清理过期记录 |

## 关键设计

### 存散列而非明文
`code_hash` 存 BCrypt 散列 —— 数据库泄露时无法直接得到验证码明文。

⚠️ **代价**：BCrypt **不可反查**，因此核验时无法按码值建索引直查，只能取候选记录**逐条比对**（见下）。

### 核验 = 取候选 + 逐条 BCrypt 比对
因散列不可反查，核验流程为：

```
取最近 20 条（purpose='verify' 且未使用且未过期，按 created_at 倒序）
  → 逐条 BCrypt 比对输入的验证码
  → 命中则原子消费
```

**限 20 条**是性能防护 —— BCrypt 单次约 100ms，无限制会退化为 CPU 密集型攻击面。

### 原子消费：CAS 而非「读后写」
```sql
UPDATE email_verification_code SET used_at = ? WHERE id = ? AND used_at IS NULL
```
**仅当影响行数 = 1 才视为消费成功**。这样同一验证码不会被两个并发请求各用一次。

### 无 `user_id` 列 —— 这是当前设计的核心局限
本表**按 `email` 关联**，不记录「谁申请了这条码」。

后果：核验时**无法按申请者收窄匹配范围**，只能全局比对最近 20 条。叠加「发码端点为匿名 permitAll」，服务端没有任何归属信息可用于限制。

这使安全性完全依赖「枚举不成立」，因此叠加了多道闸：
- IP 限频：**3 次/分 · 10 次/时**（与 [api/client/auth.md](../api/client/auth.md) 的契约一致 —— 以 api 为准）
- 每用户封禁：10 次失败 / 15 分钟（`VerifyCodeAttemptGuard`）

⚠️ 若核验失败率异常（爆破），服务端无法从本表区分攻击者与正常用户。

### 清理策略
- **定时任务**每日 03:00 清理 1 天前已过期的记录（`EmailVerificationCodeCleanupTask`）
- **账号注销时**按 `email` 与 `bind_email` 两批删除（避免残留验证码在他端被消费）

## 关联

| 方向 | 关系 | 说明 |
|---|---|---|
| `user` | **非外键** —— 按 `email` 字符串关联 | 本表无 `user_id` 列 |

## 注意事项

- `expires_at` / `used_at` 允许 NULL，但业务上发码时**必填 `expires_at`**。
- `purpose` 保留为独立维度而非硬编码，当前只有 `verify` 一个取值——为将来可能的「密码重置」等用途预留（届时须重新评估核验逻辑）。
- `code_hash` 长度 128 足以容纳 BCrypt 输出（60 字符）。
- 单条记录的 60 秒冷却基于 `idx_evc_email(email, purpose)` + `ORDER BY created_at DESC LIMIT 1`。

接口契约见 [api/client/auth.md](../api/client/auth.md#post-authemail-code) 与 `#post-authverify-email`。
