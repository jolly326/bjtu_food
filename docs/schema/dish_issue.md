# dish_issue — 菜品纠错

**用途**：用户提交的**菜品信息纠错** —— 一条 = 一次提交（**只含选项，无文字、无配图**）。运营据此**手动**更新菜品信息。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 提交 ID |
| `dish_id` | BIGINT | NO | 无 | KEY, FK→dish | 被反馈的菜品；**必填、无默认值** |
| `user_id` | BIGINT | NO | 无 | KEY, FK→user | 提交人；**取自登录态，禁止信任前端传入** |
| `option_id` | BIGINT | NO | 无 | KEY, FK→dish_issue_option | 错误类型；提交时校验其**存在性** |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | KEY | 提交时间 |
| `handled_at` | DATETIME | YES | NULL | | 运营**标记已处理**的时间；**NULL = 未处理**（运营待办判据）；**服务端取 DB 时钟写入**（与 `created_at` 同一基准） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_issue_handled_created` | `handled_at`, `created_at` | 复合 | **运营列表**（默认 `WHERE handled_at IS NULL ORDER BY created_at DESC`） |
| `idx_issue_dish` | `dish_id` | 普通 | 按菜品聚合查看（同一菜被反馈过几次） |
| `fk_issue_dish` | `dish_id` | **外键** | `REFERENCES dish(id)`，**`ON DELETE CASCADE`** |
| `fk_issue_user` | `user_id` | **外键** | `REFERENCES user(id)`，无级联 |
| `fk_issue_option` | `option_id` | **外键** | `REFERENCES dish_issue_option(id)`，无级联（字典被引用即禁删） |

## 关键设计

### 只存提交事实；处置外置为**单个时间戳**

- 本表**无处置状态机、无处置文案、无处置人** —— 运营的动作是「去看并改菜品」，不是「写结论」。
- 唯一的运营侧状态 = **`handled_at`**（NULL / 非 NULL）—— 一列即等价于「待办 / 已办」，**不另建任务表**。
- 🔴 **提交后不给用户任何回执**（无站内通知、无状态可查）—— 用户侧看不到处理结果。

### 是否处理**不影响菜品的即时展示**

运营改菜品是**独立动作**；本表只记录「谁在什么时候反馈了什么」。因此可能出现「已处理但菜品未改」—— 属运营流程问题，系统不做一致性约束。

### 去重（应用层）

同一用户 + 同一菜品 + 同一选项，**未处理**（`handled_at IS NULL`）的记录**至多一条**；重复提交幂等复用该行，不新增。

> 为何不在 DB 层：MySQL 5.7 无部分索引，条件唯一无法用普通唯一键表达。

### 保留策略

- 提交行**永不删除** —— 保留「被反馈过几次」的严重度信号与审计（同一问题被多人反复反馈 = 运营一直没改）。
- 运营列表**默认只查未处理** ⇒ 规模 = 待办量，天然受控。
- **触发条件**：**已处理行 > 5 万** 时再加「清理已处理且超过 N 天」的定时任务（**未处理行不可清**）。

## 关联与级联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| N→1 | `dish.id` | 被反馈的菜品 | **`ON DELETE CASCADE`**（菜品删除则纠错记录一并清理） |
| N→1 | `user.id` | 提交人 | 无级联（外键 `fk_issue_user`）；`user` 行永不物理删除 |
| N→1 | `dish_issue_option.id` | 错误类型 | 无级联（字典被引用即禁删） |

## 注意事项

- **无 `updated_at`** —— 唯一的可变列是 `handled_at`，它自带时间语义。
- **无 `content` / 无 `images`** —— 用户侧只有选项 ⇒ **不涉内容安全送检**。
- 管理端可达：菜品 / 提交人 / 错误类型（中文）/ 提交时间 / 处理时间，**均为只读呈现**。
