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

**不删行**的原因：`review` / `feedback` / `report` / `correction` 等历史内容保留并经 JOIN 取昵称，物理删除会让这些内容失去归属。

### 无 `password` / `unionid` / `role` / `last_login_at` 列
均已随能力退役移除（微信体系无密码；角色收敛为单一学生态）。

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| 1→n | `review.user_id` | 本人的评价 | **外键 `fk_review_user`（无级联）** |
| 1→n | `feedback.user_id` | 本人的意见反馈 | **外键 `fk_feedback_user`（无级联 —— `user` 行永不物理删除，外键是纯引用完整性兜底）** |
| 1→n | `report.user_id` | 本人的举报 | **外键 `fk_report_user`（无级联 —— 同上）** |
| 1→n | `correction.user_id` | 本人的菜品问题反馈 | **外键 `fk_correction_user`（无级联 —— 同上）** |
| 1→n | `notification.user_id` | 站内消息 | **`ON DELETE CASCADE`**（外键兜底；应用层注销时已主动硬删） |
| — | `email_verification_code` | 按 `email` 关联 | **非外键** —— 该表无 `user_id` 列 |

接口契约见 [api/client/auth.md](../api/client/auth.md)；昵称 / 头像修改见 `PUT /auth/profile`。

## 注意事项

- `openid` 唯一索引**允许 NULL**（MySQL 唯一索引不约束 NULL），这是匿名化后同一微信能重新建号的前提；`wx_id` 注销后写入 `deleted_{id}`（非 NULL），既不占用任何真实账号标识，也不让唯一键悬空。
- 建号为**两步写入**（INSERT 占位 `name` → 拿到自增 id → UPDATE 回填正式 `name`），两步在**同一事务**内；回填前判等 `name` 是否仍为占位值，避免覆盖已有值。
- **库表实况 25 行**（直连生产库核对）。
