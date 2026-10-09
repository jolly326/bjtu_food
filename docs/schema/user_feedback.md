# user_feedback — 用户反馈与举报

**用途**：用户提交的反馈。**举报（`type='report'`）也存本表**，通过 `related_type` / `related_id` 关联被举报对象。
**对应实体**：`com.bjtufood.feedback.entity.Feedback`

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 反馈 ID |
| `user_id` | BIGINT | NO | 0 | KEY | 提交人；**游客为 0**（等价于「无身份」），后台显示为游客 |
| `type` | VARCHAR(32) | NO | `'suggestion'` | | 反馈类型；**学生端写入白名单仅三类**（见下） |
| `sub` | VARCHAR(16) | YES | NULL | | 二级分类：仅历史 `type='suggestion'` 存量可能为 `idea` / `problem`；其余行为空 |
| `sub_reason_id` | BIGINT | YES | NULL | | **举报原因 ID**（`type='report'` 时 = `report_reason.id`）；纯反馈为空 |
| `content` | VARCHAR(1024) | NO | `''` | | 反馈正文；举报场景**可空**（结论以 `sub_reason_id` 为准） |
| `images` | VARCHAR(1024) | YES | NULL | | 配图 JSON 数组（COS 绝对地址），≤3 张 |
| `status` | VARCHAR(32) | NO | `'pending'` | KEY | `pending` 待处理 / `handled` 已处理 |
| `reply` | VARCHAR(1024) | YES | NULL | | 管理员回复；学生端通知会展示它。**`handled` 时可选**（无则回执用固定文案），`rejected` 时作为「处理说明」下发 |
| `reject_reason` | VARCHAR(200) | YES | NULL | | 不采纳原因；`status='handled'` 且结论为不采纳时**必填** |
| `related_type` | VARCHAR(32) | YES | NULL | | 关联对象类型（如 `review`）；**纯反馈为空** |
| `related_id` | BIGINT | YES | NULL | | 关联对象 ID；**纯反馈为空** |
| `handled_at` | DATETIME | YES | NULL | | 处理时间 |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `idx_feedback_user` | `user_id` | 普通 | 按用户查（归属迁移） |
| `idx_feedback_status_created` | `status`, `created_at` | 复合 | **管理端筛选**（`WHERE status=? ORDER BY created_at` 无 filesort） |

> 索引设计说明：**单列 `idx_feedback_status` 已由复合索引 `idx_feedback_status_created` 取代**（前缀即 `status`）。

## 关键设计

### 一表承载两类数据
| 类型 | `type` | `sub` / `sub_reason_id` | `related_type/id` | `content` |
|---|---|---|---|---|
| 纯反馈 | `bug` / `suggestion` / `other` | `sub` 仅历史 `suggestion` 存量可能非空；`sub_reason_id` 恒空 | 空 | **必填** |
| 举报 | `report` | `sub_reason_id` = 举报原因 ID；`sub` 恒空 | `review` + 评价 ID | 可空 |

**合表的理由**：两者处理流程相同（提交 → 管理员处理 → 回执），分离会重复整套处理逻辑。

### 写入类型白名单仅三类
学生端 `POST /feedback` 的**写入类型**只允许 `bug` / `suggestion` / `other`。

非白名单类型（`issue` / `add` / `error` / `report`）→ **400 拒绝，不静默落库**。其中 `report` 改由独立端点 `POST /reviews/{id}/report` 写入（见 [api/client/reviews.md](../api/client/reviews.md)）。

### 存量口径（现行数据只含 4 类）

本表**只承载「意见反馈」与「举报」两类业务**：菜品录入归 [A3](../func/web/A-主数据维护/A3-菜品管理.md)，菜品信息有误归 [B4](../func/web/B-UGC治理/B4-菜品问题反馈管理.md)（独立表 `dish_correction`）—— 混入本表只会让「意见反馈」列表混进已在别处处置的业务。

现行数据分布（2026-10-07 直连库核对，共 4 行）：

| `type` | 行数 | 说明 |
|---|---|---|
| `suggestion` | 1 | 产品功能建议（可写） |
| `bug` | 1 | 程序功能 Bug（可写） |
| `other` | 1 | 其他相关问题（可写） |
| `report` | 1 | 举报（`POST /reviews/{id}/report` 写入，**必带 `sub_reason_id`**） |

**提交链路侧的不变量**（保证这两种类型不会再被生产出来）：
- `POST /feedback` 的类型白名单 = `FeedbackConst.WRITABLE_TYPES`（`bug` / `suggestion` / `other`），其余一律 `400`；
- `POST /reviews/{id}/report` 写 `type='report'`，且 `sub_reason_id` 必须命中 `report_reason` 的**启用**项（`ReportReasonService#isSubmittable`）；
- 菜品纠错走 `POST /dishes/{id}/correction`，落 **`dish_correction` 表**，不经本表。

> 服务端 `FeedbackConst.QUERY_TYPES` 仍**保留**历史类型值：它只服务「按历史类型筛选」的兼容（老环境 / 备份库导入的数据不会让接口 `400`），不代表这些类型仍可写入。

### 游客以 `user_id = 0` 表示
**不设外键、不用 NULL**（列 `NOT NULL DEFAULT 0`），后台统一显示为「游客」。

> 与 `dish_correction.user_id` **口径一致**：匿名提交以 `0` 表示。

### 有归属用户即可收回执
处理完成后向提交人投递站内通知，判据为 **`user_id > 0`**（**有归属用户即投递**，非邮箱认证级）：静默登录的游客**有 userId，同样收到**；**匿名提交（`user_id = 0`）无归属，不投递**。

**回执文案按 `type` 分流**：纯反馈用「反馈已处理 / 反馈未采纳」，举报用「**举报已受理 / 举报不予处理**」（受理时说明对评价的处置，如"已隐藏该评价"）——见 [B2](../func/web/B-UGC治理/B2-意见反馈管理.md) / [B3](../func/web/B-UGC治理/B3-举报管理.md)。

## 关联

| 方向 | 目标表 | 关系 |
|---|---|---|
| N→1 | `user.id` | 提交人（**无外键**：`user` 行永不物理删除） |
| — | `notification` | 处理后投递回执（由应用层事件维护） |

**不设外键**：账号注销为匿名化（行保留），无级联需求。

## 注意事项

- **`content` 虽 `NOT NULL` 但举报场景可为空串** —— 举报的结论在 `sub_reason_id`（结构化原因），文本仅补充。
- `reject_reason` 与 `reply` 的区别：前者是**不采纳原因**（`rejected` 时必填），后者是**处理回复**（`handled` 时**可空**，通知中展示）。二者独立。
- 无 `handler_id` 列 —— 管理端为单管理员，操作人身份不记录。

接口契约见 [api/client/feedback.md](../api/client/feedback.md)（纯反馈）与 [api/client/reviews.md](../api/client/reviews.md#post-reviewsidreport)（举报）。
