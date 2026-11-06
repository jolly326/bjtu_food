# feedback — 用户文本反馈

**用途**：学生提交的**文本反馈**，分为两类 —— **产品反馈**（针对小程序本身）与**菜品反馈**（针对某道菜）。单表以 `type` 判别。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 反馈 ID |
| `type` | VARCHAR(8) | NO | 无 | KEY | 反馈类型：`product` 产品反馈 ｜ `dish` 菜品反馈 |
| `dish_id` | BIGINT | **YES** | `NULL` | KEY, FK→dish | 被反馈的菜品；`type = product` 时为 `NULL`，`type = dish` 时必填 |
| `user_id` | BIGINT | NO | 无 | KEY, FK→user | 提交人；**取自静默身份，禁止信任前端传入** |
| `content` | TEXT | NO | 无 | | 反馈正文（纯文本） |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | KEY | 提交时间 |
| `handled_at` | DATETIME | YES | `NULL` | | 运营标记已处理的时间；`NULL` = 未处理 |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `idx_feedback_type_handled_created` | `type`, `handled_at`, `created_at` | 复合 | 运营列表（按类型查待办，默认 `handled_at IS NULL ORDER BY created_at DESC`） |
| `idx_feedback_dish` | `dish_id` | 普通 | 按菜品聚合查看菜品反馈 |
| `fk_feedback_dish` | `dish_id` | 外键 | `REFERENCES dish(id)`，**`ON DELETE CASCADE`** |
| `fk_feedback_user` | `user_id` | 外键 | `REFERENCES user(id)`，无级联 |

## 关键设计

### 单表 + `type` 判别

- `type = product` ⇒ `dish_id` 为 `NULL`（反馈针对整个小程序）；
- `type = dish` ⇒ `dish_id` 必填（反馈针对某道菜）；
- 单表比分两表少一套接口与索引，运营列表按 `type` 过滤即可。

### 文本内容必须过内容安全

- 本表 `content` 是**用户自由文本**；
- 🔴 提交前必须走**微信内容安全检测**（`msgSecCheck`），命中违规直接拒绝并返回 `400`；
- 该检测是文本 UGC 的硬门槛，不可省略。

### 提交人必填

- 小程序无登录界面，但启动时 `wx.login` 静默换 openid ⇒ **首次自动建 `user` 行**，此后所有反馈都带 `user_id`；
- `user_id` 取自服务端身份上下文，禁止前端传入。

### 处置外置为单时间戳

- 唯一的运营侧状态 = `handled_at`（`NULL` / 非 NULL），等价于「待办 / 已办」；
- 不建状态机、不存处置文案、不回执用户。

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| N→1 | `dish.id` | 被反馈的菜品（`type = dish` 时非空） | **`ON DELETE CASCADE`**（删菜即清空菜品反馈） |
| N→1 | `user.id` | 提交人 | 无级联（`user` 行永不物理删除） |

## 注意事项

- **产品反馈** `dish_id` 为 `NULL`；写入时服务端按 `type` 校验，非法组合返回 `400`。
- **正文长度**：建议上限 500 字，超长截断或拒绝（按接口层约定）。
- 管理端可达：类型 / 关联菜品 / 提交人 / 正文 / 提交时间 / 处理时间，均可只读呈现。
- 保留策略：默认只查未处理；已处理行超过阈值后再加清理任务。
