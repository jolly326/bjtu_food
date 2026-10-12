# moderation_task — 管理端处置任务

**用途**：三类用户提交（意见反馈 / 评价举报 / 菜品问题反馈）共用的**处置状态机**与**处置文案真源**。用户提交表只存提交事实，「是否已处置 + 结论 + 文案 + 处置人 + 处置时间」全部落在本表。
**对应实体**：`com.bjtufood.moderation.entity.ModerationTask`

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 处置任务 ID |
| `type` | VARCHAR(16) | NO | — | KEY | 工单类型：`feedback` / `report` / `correction` |
| `type_id` | BIGINT | NO | — | **UNI**（与 `type` 复合） | 工单 ID（对应工单表主键；**多态引用，无外键**） |
| `status` | VARCHAR(16) | NO | `'pending'` | KEY | `pending` 待处置 / `handled` 已处置 |
| `outcome` | VARCHAR(16) | YES | NULL | | 处置结论：`handled` 通过 / `rejected` 不采纳；`status='handled'` 时**必填**，`pending` 为 NULL |
| `reply_title` | VARCHAR(128) | YES | NULL | | 回复标题（处置时写入；站内回执**同源快照**） |
| `reply_content` | VARCHAR(1024) | YES | NULL | | 处置文案（**回复内容与不采纳原因同列**，≤600 字） |
| `handled_by` | BIGINT | YES | NULL | KEY, **FK**→`admin(id)` | 处置人；`pending` 为 NULL |
| `handled_at` | DATETIME | YES | NULL | | 处置时间 |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 提交时间（与工单行**同一事务**预建） |

> **无 `updated_at`** —— 处置是**一次性终态动作**（`pending → handled`，不可改写），没有「再次更新」的语义，故无更新时间列。

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `uk_task_type_target` | `type`, `type_id` | **唯一** | 多态挂靠键：保证「一张工单恰一行任务」= **1:1** |
| `idx_task_status_created` | `status`, `created_at` | 复合 | 按状态筛（`status` 前缀）：**待处置数的单表 `COUNT`**（`WHERE type=? AND status='pending'`）与处置状态筛选 |
| `fk_task_handled_by` | `handled_by` | **外键** | `REFERENCES admin(id)`（**无级联**）；同时是外键所需的索引 |

> **不另建单列 `status` 索引** —— `idx_task_status_created` 的前缀即 `status`，前缀可用即无需重复索引。
> 列表排序取自各工单表自己的 `created_at`（见 [feedback](./feedback.md) / [report](./report.md) / [correction](./correction.md)），本表的 `created_at` 只作提交时刻留痕。

## 关键设计

### 1:1 挂靠：提交时同事务预建 `pending` 行
| 时机 | 动作 | 事务 |
|---|---|---|
| **提交** | `INSERT` 工单行 + `INSERT` 本表行（`status='pending'`，其余处置列 NULL） | **同一事务**（任一步失败一并回滚） |
| **处置** | `UPDATE` 本表行（`status` / `outcome` / `reply_title` / `reply_content` / `handled_by` / `handled_at`）+ `INSERT` 站内回执快照 | **同一事务** |

因此「未处置」是**显式行**而不是「某列的空值」：

- 管理端列表的「待处置」筛选只需 `EXISTS (…) AND status='pending'`，**走唯一索引**，无需工单表反查；
- 三类工单的**待处置总数 = 本表单表 `COUNT`**（看板口径，见 [api/web/dashboard.md](../api/web/dashboard.md)）；
- 「有工单无任务行」不会出现 —— 提交事务保证两者同生共死。

### 多态键 `(type, type_id)`：**不设外键**（刻意）
目标是三张不同的表（`feedback` / `report` / `correction`），单列无法指向多个父表；因此**引用完整性由提交事务保证**（工单行与任务行同时写入），而非由外键保证。

> 归属表达的唯一入口是本键：**不给工单表加 `task_id`**（那会让提交事实表反向依赖处置层），也不建 ORM 关联（管理端列表先查工单分页，再按 `type_id` 集合批量取任务行组装）。

### 单一文案列：不设 `reject_reason`
处置文案全部走 `reply_content`（`reply_title` 作短标题）：**不采纳时把原因写进 `reply_content`**。

- 不采纳必须写清原因 ⇒ `outcome='rejected'` 时 `reply_content` **必填**（纯空白 → `400`），不静默降级为缺省文案；
- 留空时按（类型 + 结论）补缺省文案 ⇒ 回执**始终可读**，不存在空回执。

### `outcome` 必须显式落列
「通过 / 不采纳」必须**可从行本身判定**：处置文案只有一列（原因并入其中），故结论独立落 `outcome` —— `handled` 通过 ｜ `rejected` 不采纳；`status='handled'` 时**必填**（应用层校验），`pending` 行恒 NULL。

### `status` 与 `outcome` 的冗余是**有意的**
`status` 可由 `outcome` 派生（`outcome IS NULL` ⇔ `pending`，非 NULL ⇔ `handled`），两列并存属**登记在案的冗余**（见 [README §派生数据与反范式清单](./README.md)）：

- **为何保留 `status`**：待处置计数（看板）与按状态筛选都以它为索引前缀（`idx_task_status_created`），是唯一高频访问路径；
- **结论真源是 `outcome`**：`status` 只是它的可索引投影；
- **写入纪律**：两列由**同一处置动作**在同一事务内写入，不接受单独改一列（否则出现「pending 却有结论」这类不自洽行）。

### `handled_by` 记录处置人，且**必须有读方**
`handled_by` 引用 `admin.id`，管理端处置列表 / 抽屉以 `handledByName` 出参呈现「处理人」—— 否则该列只写不读，违背字段可达性口径（见 [README §管理端字段可达性](./README.md)）。

### 无删除路径 ⇒ 不存在孤儿
三张工单表**都没有删除入口**：`feedback` / `correction` 无删除能力；评价被删时 `report` 行**保留**（`review_id` 置 NULL）。故任务行不会指向不存在的目标，**无需定时清扫任务**。

> **防将来误改**：若日后给任一张工单表引入删除能力，**必须同步删除其任务行**（或一并引入清扫任务），否则本表即成孤儿来源。

## 关联

| 方向 | 目标 | 关系 |
|---|---|---|
| N→1 | `admin.id` | 处置人 —— **外键 `fk_task_handled_by`（无级联）**：`admin` 行由数据库与自助端点维护，不做物理删除 |
| 1—1 | `feedback.id`（`type='feedback'`） | **多态键，无外键**（唯一索引 `uk_task_type_target` 保证 1:1） |
| 1—1 | `report.id`（`type='report'`） | 同上 |
| 1—1 | `correction.id`（`type='correction'`） | 同上 |
| — | `notification` | 处置时投递回执快照（同一事务写入，**无结构关联**） |

## 注意事项

- **与 `notification` 无结构关联**：通知是**用户侧消息通道**（消息中心 / 未读徽标 / `is_read`），触发源**多域**（三类工单的处置回执 / 评价处置回执 / 将来的系统公告），本表只是其中一部分来源。两份文案是**同句快照**、**有意不要求同步**：管理端回显读本表，站内消息读 `notification`。
  > 不给通知加来源键（当前零消费方），也不给本表加 `notification_id`（避免消息表反向依赖业务表）；将来端上要做「点消息跳回我的提交」时，再加 `notification.source_type` + `source_id`（**指向工单表**，不指向本表）。
- **处置不可重复**：`status != 'pending'` 再处置 → `400`（防结论被静默改写 + 回执重复投递）。故 `outcome` / `reply_*` / `handled_*` 一经写入即不可变。
- 库内当前 **2 行**（直连生产库核对）：`report/7` 与 `correction/2`，均 `pending`。
- 管理端处置入参 / 出参见 [api/web/feedback.md](../api/web/feedback.md)、[api/web/reports.md](../api/web/reports.md)、[api/web/corrections.md](../api/web/corrections.md)。
