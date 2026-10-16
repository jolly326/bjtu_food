# feedback — 意见反馈

**用途**：用户提交的**意见反馈**（分类由 `feedback_option` 字典表达）。**只存提交事实**（append-only）。
**对应实体**：`com.bjtufood.feedback.entity.Feedback`

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 反馈 ID |
| `user_id` | BIGINT | NO | — | KEY, **FK**→`user(id)` | 提交人（**恒为真实用户 ID**；提交入口一律要求登录态） |
| `option_id` | BIGINT | NO | — | KEY, **FK**→`feedback_option(id)` | 反馈类型字典项；提交时校验命中**启用项** |
| `content` | VARCHAR(1024) | NO | `''` | | 反馈正文；服务端 ≤1000 字，纯空白 → `400` |
| `images` | VARCHAR(1024) | YES | NULL | | 配图 JSON 数组（COS 绝对地址），≤3 张 |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | KEY | 提交时间 |

> 本表**无状态列、无 `updated_at`** —— 「是否已处置 + 处置文案 + 处置人」外置在 [moderation_task](./moderation_task.md)（多态键 `('feedback', type_id)`）。

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `idx_feedback_user` | `user_id` | 普通 | 按提交人查（同时是外键 `fk_feedback_user` 所需的索引） |
| `idx_feedback_option` | `option_id` | 普通 | 按类型查（同时是外键 `fk_feedback_option` 所需的索引） |
| `idx_feedback_created` | `created_at` | 普通 | 管理端列表排序（`ORDER BY created_at DESC`） |
| `fk_feedback_user` | `user_id` | **外键** | `REFERENCES user(id)`（**无级联**） |
| `fk_feedback_option` | `option_id` | **外键** | `REFERENCES feedback_option(id)`（**无级联**） |

## 关键设计

### 只存提交事实，「处置状态 + 处置文案」外置
提交只在事务内 INSERT 一行提交事实 + 一行 `moderation_task`（`status='pending'`）。因此：

- 提交行**永不因处置而被改写** —— 「用户提交了什么」与「管理员怎么处置的」各有其表，历史不可互相污染；
- 管理端列表按处置状态筛选走 `moderation_task`（`EXISTS` 相关子查询，命中唯一索引 `(type, type_id)`）；
- 三类待处置总数 = `moderation_task` **单表 `COUNT`**（见 [moderation_task](./moderation_task.md)）。

> 任务行缺失（数据不一致）时管理端出参的处置字段保持 `null`，**不伪造「未处置」**。

### `user_id` 恒为真实用户，且带外键
提交入口 `POST /feedback` **一律要求登录态**（静默登录的游客已有 `userId`），故 `user_id` 恒指向真实 `user` 行；`user` 行**永不物理删除**（注销为匿名化）⇒ 级联无意义，外键 `fk_feedback_user` 只作引用完整性兜底。

### `option_id` 锚在字典 ID，改名免费
类型文案的真源是 [`feedback_option`](./feedback_option.md)：改 `name` 全站生效（含历史反馈的中文翻译），零数据迁移；新增类型免发版（端上拉 `GET /feedback-options` 渲染）。

字典项被本表引用时**禁止删除**（应用层计数），下线一律用 `status='off'`。

### 归属用户恒存在，回执恒可达
处置完成后向提交人投递站内通知：`user_id` 恒有效 ⇒ **恒投递**，不依赖邮箱认证级。

## 关联

| 方向 | 目标表 | 关系 |
|---|---|---|
| N→1 | `user.id` | 提交人 —— **外键 `fk_feedback_user`（无级联）** |
| N→1 | `feedback_option.id` | 反馈类型 —— **外键 `fk_feedback_option`（无级联）**：字典有独立生命周期，被引用项不得删除 |
| 1—1 | `moderation_task`（`type='feedback'`） | 处置状态机 —— **多态键 `(type, type_id)`，无外键**，唯一索引保证 1:1 |
| — | `notification` | 处置后投递回执（应用层，与 [moderation_task](./moderation_task.md) 无结构关联） |

## 注意事项

- `content` 为 `NOT NULL DEFAULT ''` —— 正文是意见反馈的**唯一表达载体**，必填（`type` 只表达分类）。
- `images` 与全站 UGC 配图同档 **≤3 张**，地址须为经 `POST /upload/cloud-image` 转存的 COS 绝对 URL；序列化后超 1024 → `400`，禁止静默截断。
- 库内当前 **0 行**（直连生产库核对）。
- 接口契约见 [api/client/feedback.md](../api/client/feedback.md)；管理端处置见 [api/web/feedback.md](../api/web/feedback.md)。
