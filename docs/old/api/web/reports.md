# web/reports — 举报管理

**归属**：`ReportAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 处理学生提交的**评价举报**（独立资源 `report`，与意见反馈 [web/feedback.md](./feedback.md) **各自成表、各自成端点**）。
> 举报**无正文**：结论以结构化原因（`optionId`）为准；处置状态与处置文案挂靠 [`moderation_task`](../../schema/moderation_task.md)。
> 受理举报可**联动隐藏**被举报评价（[web/reviews.md](./reviews.md) 提供「恢复显示」作为反向动作）。

---

## GET /admin/reports

**用途**：举报列表（分页）

### 请求参数
| 名称 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` / `pageSize` | number | 否 | 1 / 20 | 页码 / 每页条数（上限 **50**） |
| `status` | string | 否 | — | `pending` 待处置 / `handled` 已处置；**不传 = 全部**；非法值 `400` |
| `userId` | number | 否 | — | 按举报人筛选 |

**排序**：`createdAt DESC`。

> `status` **以处置任务表为判据**（`moderation_task` 的 `type='report'` 行 + `status`），不由举报表自身列表达。

### 响应 `data` = `AdminPageResult<ReportAdminVO>`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 举报 ID |
| `userId` | number | 举报人 ID（**恒为真实用户 ID** —— 列带外键 `fk_report_user → user(id)`） |
| `userName` | string | 举报人昵称（联表带出；注销账号显示「已注销用户」） |
| `optionId` | number | **举报原因 ID**（`report_option.id`） |
| `optionName` | string | 举报原因中文名（由 `report_option` 字典实时翻译，**停用原因仍可译出**） |
| `reviewId` | number \| null | 被举报评价 ID；**评价已删除为 `null`** |
| `reviewDeleted` | boolean | **被举报评价是否已删除**（`reviewId` 为 `null` 即 `true`；此时摘要与可见性均为 `null`） |
| `reviewContent` | string \| null | 被举报评价正文**摘要**（截断，完整内容去评价管理看；评价已删除为 `null`） |
| `reviewDishName` | string \| null | 被举报评价所属菜品名（评价已删除为 `null`） |
| `reviewHidden` | boolean \| null | 被举报评价当前是否已隐藏（决定「同时隐藏」复选是否置灰；评价已删除为 `null`） |
| `status` | string | `pending` / `handled` |
| `outcome` | string \| null | 处置结论：`handled` 受理 / `rejected` 不予处理；**未处置为 `null`** |
| `replyTitle` | string \| null | 处置回执短标题（未处置为 `null`） |
| `replyContent` | string \| null | 处置文案 / 不采纳原因（未处置为 `null`；单一列） |
| `handledByName` | string \| null | 处置人登录名（未处置 / 账号已删除为 `null`） |
| `createdAt` | string | 提交时间（`yyyy-MM-dd HH:mm:ss`） |
| `handledAt` | string \| null | 处置时间（未处置为 `null`） |

> **被举报评价已删除**（`reviewDeleted = true`）：举报行**保留**、举报人 / 原因 / 处置状态 / 处置文案**完整**，管理端据此显示「被举报评价已删除」—— 摘要在列表以 `null` 下发，不伪造内容。

---

## PUT /admin/reports/{id}

**用途**：**处置**（受理 / 不予处理；受理可联动隐藏被举报评价）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 举报 ID |

### 请求体 `ModerationHandleReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `replyTitle` | string | 否 | 回执短标题（**≤40 字**）；留空按（类型 + 结论）用缺省文案 |
| `replyContent` | string | 条件 | 处置文案（**≤600 字**）。`outcome=handled` 时可空（缺省文案）；**`outcome=rejected` 时必填** —— 即为**不采纳原因**，纯空白 → `400` |
| `outcome` | string | 否 | `handled` 受理（**缺省**）｜ `rejected` 不予处理；非法值 → `400` |
| `hideReview` | boolean | 否 | **是否联动隐藏被举报评价**（缺省 `false`）；**仅受理时消费**，未受理忽略。**受理举报通常应传 `true`** |

**联动口径**：
- `handled` + `hideReview=true` → 隐藏被举报评价（**幂等**：评价已隐藏 / 已删除 / 举报目标已删除时**静默成功**）；
- `handled` + `hideReview=false` → 只记录「已受理」，不动评价；
- `rejected` → `hideReview` **不消费**。

**回执正文组装**：受理且**本次确实隐藏了评价**时，回执正文写明「已隐藏该评价」（管理员未填文案时为「已隐藏该评价」，已填则附于其后）—— 用户看不到那条评价了，不说明即为「凭空消失」。

### 响应 `data`
`null`。

**处置动作**（同一事务）：更新 `moderation_task` 行（`status='handled'` / `outcome` / `reply_title` / `reply_content` / `handled_by` / `handled_at`）+ 投递站内回执（`reply_title` / `reply_content` 快照）。

> **已处置再处理 → `400`「该记录已处理」**（防结论被静默改写 + 回执重复投递）。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `outcome` 非法；**`rejected` 时 `replyContent` 缺失 / 纯空白**；`replyTitle` >40 字；`replyContent` >600 字；`hideReview` 取值非布尔；`status` 非法值；**已处置再处理** |
| `4001` | 举报不存在 |
| `401` | 未带 / token 失效 |

### 备注
- **`outcome` 是任务表的物理列**（`status='handled'` 时必填）—— 「受理 / 不予处理」不靠文案推断。
- **文案只有一列**：不采纳原因并入 `replyContent`，无独立的「拒绝理由」字段。
- **回执文案按结论分流**：受理 → 「举报已受理」，不予处理 → 「举报未采纳」（正文即管理员填写的处置文案 / 不采纳原因）；投递判据 = 举报人 `userId` 有效（提交恒带登录态 ⇒ **恒投递**），投递与处置同一事务。
- **联动隐藏是「一次性动作」而非状态**：恢复显示走 [web/reviews.md](./reviews.md) 的「恢复显示」。
- **不做批量处置**（一期不做，留作演进）。
- 举报人可重复提交的约束（同一用户对同一评价**不重复计数**）见 [client/reviews.md](../client/reviews.md#post-reviewsidreport)。
