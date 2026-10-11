# report — 评价举报

**用途**：用户对**评价**提交的举报（原因由 `report_option` 字典表达）。**只存提交事实**（append-only）。
**对应实体**：`com.bjtufood.feedback.entity.Report`

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 举报 ID |
| `user_id` | BIGINT | NO | — | KEY, **FK**→`user(id)` | 举报人（**恒为真实用户 ID**；提交入口一律要求登录态） |
| `review_id` | BIGINT | YES | NULL | KEY, **FK**→`review(id)` | 被举报评价 ID（**`NULL` = 该评价已被删除**） |
| `option_id` | BIGINT | NO | — | KEY, **FK**→`report_option(id)` | 举报原因字典项；提交时校验命中**启用项** |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | KEY | 提交时间 |

> 本表**无 `content` 列** —— 举报的结论是**结构化原因单选**（`option_id`），文本补充不是举报的成立要件。
> 本表**无状态列、无 `updated_at`** —— 处置结果外置在 [moderation_task](./moderation_task.md)（多态键 `('report', type_id)`）。

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `idx_report_user` | `user_id` | 普通 | 按举报人查（同时是外键 `fk_report_user` 所需的索引） |
| `idx_report_review` | `review_id` | 普通 | 按被举报评价查（同时是外键 `fk_report_review` 所需的索引） |
| `idx_report_option` | `option_id` | 普通 | 按原因查（同时是外键 `fk_report_option` 所需的索引） |
| `idx_report_created` | `created_at` | 普通 | 管理端列表排序（`ORDER BY created_at DESC`） |
| `fk_report_user` | `user_id` | **外键** | `REFERENCES user(id)`（**无级联**） |
| `fk_report_review` | `review_id` | **外键** | `REFERENCES review(id)` **`ON DELETE SET NULL`** |
| `fk_report_option` | `option_id` | **外键** | `REFERENCES report_option(id)`（**无级联**） |

## 关键设计

### 🔴 `review_id` 可空 + `ON DELETE SET NULL`：被举报评价被删，举报行保留
| 若设 | 后果 |
|---|---|
| `ON DELETE CASCADE` | 删除一条违规评价会**连带删掉举报记录** —— 举报人诉求与处置结论一并消失 |
| `ON DELETE RESTRICT` | **评价永远删不掉** —— 只要被举报过就被卡住（评价删除是评价治理的常规动作） |
| **`ON DELETE SET NULL`（现行）** | 举报行**保留**、`review_id` 置 `NULL` —— **显式**表达「被举报评价已删除」 |

因此举报人 / 原因 / 处置状态 / 处置文案**全部完整**，管理端据此显示「被举报评价已删除」（评价摘要与可见性一并置空）。

> **提交时 `review_id` 必填**（应用层校验：举报必须有目标）；`NULL` 只会由**评价被删除**这一条路径产生。
> 隐藏（`review.is_hidden=1`）不触发本外键 —— 行仍在、`review_id` 仍在。

### 去重由应用层保证，不建唯一索引
同一登录用户对同一评价的重复举报**不新增行**（应用层查重后 `400`）。

> **不建 `(user_id, review_id)` 唯一索引**：`review_id` 可空，而唯一索引不约束 `NULL` 行 —— 评价被删后同一用户可再次举报其他内容，唯一索引会给出「部分成立」的假保证（真实约束落在应用层，键的语义由「提交时的目标」而非「行当前的值」表达）。

### `user_id` 恒为真实用户，且带外键
提交入口 `POST /reviews/{id}/report` **一律要求登录态**，故 `user_id` 恒指向真实 `user` 行；`user` 行**永不物理删除**（注销为匿名化）⇒ 无级联需求，外键只作引用完整性兜底。

### `option_id` 锚在字典 ID，改名免费
原因文案的真源是 [`report_option`](./report_option.md)：改 `name` 免费、新增原因免发版（端上拉 `GET /report-options`）。字典项被本表引用时**禁止删除**，下线一律用 `status='off'`。

### 只存提交事实，「处置状态 + 处置文案」外置
提交在**同一事务**内 INSERT 举报行 + `moderation_task`（`status='pending'`）；处置时更新任务行并投递站内回执（受理可联动隐藏被举报评价）。举报行本身不被改写。

## 关联

| 方向 | 目标表 | 关系 |
|---|---|---|
| N→1 | `user.id` | 举报人 —— **外键 `fk_report_user`（无级联）** |
| N→1 | `review.id` | 被举报评价 —— **外键 `fk_report_review`（`ON DELETE SET NULL`）** |
| N→1 | `report_option.id` | 举报原因 —— **外键 `fk_report_option`（无级联）** |
| 1—1 | `moderation_task`（`type='report'`） | 处置状态机 —— **多态键 `(type, type_id)`，无外键**，唯一索引保证 1:1 |
| — | `notification` | 处置后投递回执（应用层，与 [moderation_task](./moderation_task.md) 无结构关联） |

## 注意事项

- 库内当前 **1 行**（直连生产库核对）：该行 `review_id = NULL` —— 原举报指向的评价已删除，举报行与处置任务行**均保留**。
- 举报**不收集联系方式**、**不要求邮箱认证**（静默登录的游客即可提交，`user_id` 恒真实 ⇒ 回执恒可达）。
- 接口契约见 [api/client/reviews.md](../api/client/reviews.md)；管理端处置见 [api/web/reports.md](../api/web/reports.md)。
