# notification — 站内通知

**用途**：向用户投递处理回执（**反馈 / 举报 / 菜品问题反馈 / 评价**被管理员处理后的结果）。**仅站内，不做任何推送。**
**对应实体**：`com.bjtufood.notification.entity.Notification`

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 通知 ID |
| `user_id` | BIGINT | NO | 0 | KEY, FK→user | 接收人 |
| `title` | VARCHAR(128) | NO | `''` | | 通知标题（如「反馈已处理」「菜品信息更新」） |
| `content` | VARCHAR(1024) | NO | `''` | | 通知正文 = **固定前缀（≤40 字）+ `reply` / `rejectReason` 全文**（`reply ≤600` ⇒ 构造后必 ≤ 1024）——**恒非空**（空回执用固定文案，出参不做 null 兜底）；**SHALL NOT 截断** |
| `is_read` | TINYINT | NO | 0 | | `0` 未读 / `1` 已读 |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 生成时间（列表按此倒序） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `idx_notification_user_created` | `user_id`, `created_at` | 复合 | 列表排序（`WHERE user_id=? ORDER BY created_at DESC`，无 filesort） |
| `idx_notification_user_read` | `user_id`, `is_read` | 复合 | 未读计数（`WHERE user_id=? AND is_read=0`） |
| `fk_notification_user` | `user_id` | **外键** | `ON DELETE CASCADE` |

> 索引设计说明：单列 `idx_notification_user` **保留** —— 外键 `fk_notification_user`（`ON DELETE CASCADE`）要求 `user_id` 上存在索引，不可删除（复合索引不能替代外键所需的索引）。

## 关键设计

### 出参 5 个字段（含 `id`）
出参 = `id` / `title` / `content` / `isRead` / `createdAt`（共 5 个）；端上从不读取类型，通知卡只渲染「标题 + 正文 + 时间 + 未读态」，**不按类型分支、不做类型相关跳转**。

将来若要做「按类型点击跳转」，须先由 UI 文档定义交互，再新增 `type` + `related_id` 列（命名以本文件为准）。

### 登录级能力，非认证级
投递判据是 **`user_id` 有效**（提交反馈 / 举报 / 菜品问题反馈时带登录态），**不要求邮箱认证**。

游客提交的反馈同样收到回执 —— 邮箱认证只约束 UGC 写操作，不约束消息中心。

### 账号注销时硬删
通知是**账号维度的过程性数据**：注销后账号不可再进入、无任何读取方，保留即孤儿数据。

因此 `UserAccountClosedEvent` → `NotificationAccountCleanListener` 在注销事务内**物理删除**该用户全部通知。这与 `review` / `user_feedback` 的「保留并匿名化」口径**刻意不同**。

### 异步投递，不阻塞主流程
`NotificationServiceImpl#notify` 标 `@Async`：通知写入失败**不影响**业务处理结果（反馈已被处理、菜品问题反馈已被采纳）。

⚠️ 因此投递失败**只能通过日志发现**，调用方无法感知。

## 关联与级联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| N→1 | `user.id` | 接收人 | **`ON DELETE CASCADE`**（外键名 `fk_notification_user`） |

**外键兜底**：正常注销路径下应用层已主动硬删，外键命中 0 行；只有应用层失效时才由外键生效。

> `user` 行实际**永不物理删除**（注销为匿名化），故该外键主要防「误删 user 行」这类未来改动。

## 注意事项

- **无推送通道** —— 产品定型明确「通知仅站内通知中心，不做任何推送」。订阅消息属演进预留项，需重新拍板。
- `is_read` 用 `TINYINT` 而非 `VARCHAR` —— 与 `dish` / `user` 的字符串枚举风格不同，**保持现状**（换档需迁移既有数据）。
- 未读计数 `WHERE user_id=? AND is_read=0` 可走 `idx_notification_user_read(user_id, is_read)`。

接口契约见 [api/client/my.md](../api/client/my.md)。
