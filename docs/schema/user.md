# user — 用户账号

**用途**：小程序用户账号。游客与已认证用户共用一张表，用 `email` 是否为空区分认证态。
**对应实体**：`com.bjtufood.auth.entity.User`

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 用户 ID |
| `wx_id` | VARCHAR(64) | NO | `''` | **UNI** | 账号标识，三种取值形态：微信建号为 `wx_<openid 尾 16 位>`；注销后为 `deleted_{id}`；种子数据为学号 |
| `name` | VARCHAR(64) | NO | `''` | | 昵称；游客默认「食客 + ID 尾 4 位」 |
| `avatar` | VARCHAR(512) | YES | NULL | | 头像地址（站内相对路径，或经内容安检 + COS 转存的绝对地址）|
| `status` | VARCHAR(32) | NO | `'active'` | | `active` 正常 / `disabled` 禁用 / `deleted` 已注销 |
| `openid` | VARCHAR(64) | YES | NULL | **UNI** | 微信 openid；**登录取号依据**，注销后置 NULL 解绑 |
| `email` | VARCHAR(128) | YES | NULL | | 已认证绑定的校园邮箱；**认证态的唯一判据**（非空即已认证）；**同一邮箱至多一行非空**由应用层保证（本列无唯一索引）|
| `violation_count` | INT | NO | `0` | | 累计违规次数（机审 `risky` 命中 + 举报成立）；达阈值触发梯度处置 |
| `muted_until` | DATETIME | YES | NULL | | 限言到期时刻；**处于未来 = 限言中**（写端点 `403`，读不受限），到期自动恢复 |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `uk_user_wx_id` | `wx_id` | 唯一 | 账号标识唯一性 |
| `uk_user_openid` | `openid` | 唯一 | 微信登录取号；**NULL 不参与唯一索引** |

## 关键设计

### `email` 是认证态的唯一判据
- **不进 JWT** —— 后端按本列非空实时查库判定（`AuthStateUtil`），保证解绑后立即生效
- 非空 ⇔ 已认证，可写 UGC；空 ⇔ 游客态
- 端上 `isVerified()` 由它单点派生

### 注销 = 匿名化，非物理删除
`status='deleted'` 时：

| 列 | 变为 | 目的 |
|---|---|---|
| `name` | `'已注销用户'` | 历史内容展示时自动匿名化 |
| `wx_id` | `'deleted_{id}'` | 释放唯一键，且不含任何个人信息 |
| `openid` / `email` / `avatar` | `NULL` | 解绑，允许同一微信重新建号 |

**不删行**的原因：`review` / `user_feedback` 等历史内容保留并经 JOIN 取昵称，物理删除会让这些内容失去归属。

### 无 `password` / `unionid` / `role` / `last_login_at` 列
均已随能力退役移除（微信体系无密码；角色收敛为单一学生态）。

### 违规累积用独立列而不是 `status` 复用
`violation_count` 是「已发生多少次违规」的累计量，`muted_until` 是「临时不能发言」的**有期限状态**，二者都**不能**用 `status` 表达：
- 用 `status='disabled'` 表达限言 ⇒ 到期后无法自动恢复，且会连**读**一起挡掉；
- 累计次数必须单调递增，与账号当前形态无关。

处置梯度（口径见 [secur/client/滥用与内容防护.md](../secur/client/滥用与内容防护.md)）：累计 3 次警告 → 6 次限言 24 小时 → 9 次限言 7 天 → 12 次封禁（写 `status='disabled'` 并吊销该 userId 全部 token）。
每次处置产生一条安全告警（webhook 推送，**不落库**），防止静默封号。

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| 1→n | `review.user_id` | 本人的评价 | 无外键（`user` 行永不删除） |
| 1→n | `user_feedback.user_id` | 本人的反馈 | **`FOREIGN KEY (user_id) REFERENCES user(id)`**（无级联 —— `user` 行永不物理删除，外键是纯引用完整性兜底） |
| 1→n | `dish_correction.user_id` | 本人的菜品问题反馈 | 无外键 |
| 1→n | `notification.user_id` | 站内消息 | **`ON DELETE CASCADE`**（外键兜底；应用层注销时已主动硬删） |
| — | `email_verification_code` | 按 `email` 关联 | **非外键** —— 该表无 `user_id` 列 |

接口契约见 [api/client/auth.md](../api/client/auth.md)；昵称 / 头像修改见 `PUT /auth/profile`。

## 注意事项

- `openid` 唯一索引**允许 NULL**（MySQL 唯一索引不约束 NULL），这是匿名化后同一微信能重新建号的前提；`wx_id` 注销后写入 `deleted_{id}`（非 NULL），既不占用任何真实账号标识，也不让唯一键悬空。
- 建号为**两步写入**（INSERT 占位 `name` → 拿到自增 id → UPDATE 回填正式 `name`），两步在**同一事务**内；回填前判等 `name` 是否仍为占位值，避免覆盖已有值。
- **库表实况 30 行**；其中 id 1–5 为种子数据（`wx_id` 为学号 / 账号名，名下共 40 条评价 + 4 条反馈），与生产账号同表同构。
