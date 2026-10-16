# web/feedback — 意见反馈管理

**归属**：`FeedbackAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 处理学生提交的**意见反馈**（独立资源 `feedback`）。举报是独立资源，见 [web/reports.md](./reports.md)。
> 表格本身**只存提交事实**：处置状态与处置文案挂靠 [`moderation_task`](../../schema/moderation_task.md)，列表按类型 / 状态 / 提交人 / 关键词筛选。

---

## GET /admin/feedbacks

**用途**：反馈列表（分页）

### 请求参数
| 名称 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` / `pageSize` | number | 否 | 1 / 20 | 页码 / 每页条数（上限 **50**） |
| `status` | string | 否 | — | `pending` 待处置 / `handled` 已处置；**不传 = 全部**；非法值 `400` |
| `optionId` | number | 否 | — | 按反馈类型（`feedback_option` 字典项）筛选 |
| `userId` | number | 否 | — | 按提交人筛选（用户行为聚合用） |
| `keyword` | string | 否 | — | 关键词（**对反馈正文 或 处置文案**模糊匹配） |

**排序**：`createdAt DESC`。

> `status` **以处置任务表为判据**（`moderation_task` 的 `type='feedback'` 行 + `status`）；`keyword` 的「处置文案」分支同样查任务表。
> **无板块分流参数** —— 举报独立成表、独立端点，本列表只有单一资源。

### 响应 `data` = `AdminPageResult<FeedbackAdminVO>`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 反馈 ID |
| `userId` | number | 提交人 ID（**恒为真实用户 ID** —— 列带外键 `fk_feedback_user → user(id)`） |
| `userName` | string | 提交人昵称（联表带出；注销账号显示「已注销用户」） |
| `optionId` | number | 反馈类型 ID（`feedback_option` 字典项） |
| `optionName` | string | 反馈类型中文名（由 `feedback_option` 字典实时翻译，**停用类型仍可译出**） |
| `content` | string | 反馈正文（空串兜底） |
| `images` | string[] | 配图**绝对 URL** 数组（≤3 张；恒为数组） |
| `status` | string | `pending` / `handled` |
| `outcome` | string \| null | 处置结论：`handled` 通过 / `rejected` 不采纳；**未处置为 `null`** |
| `replyTitle` | string \| null | 处置回执短标题（未处置为 `null`） |
| `replyContent` | string \| null | 处置文案 / 不采纳原因（未处置为 `null`；单一列） |
| `handledByName` | string \| null | 处置人登录名（未处置 / 账号已删除为 `null`） |
| `createdAt` | string | 提交时间（`yyyy-MM-dd HH:mm:ss`） |
| `handledAt` | string \| null | 处置时间（未处置为 `null`） |

> 列表的处置字段来自处置任务表：**任务行缺失时保持 `null`**，不伪造成「未处置」。

---

## PUT /admin/feedbacks/{id}

**用途**：**处置**（通过 / 不采纳）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 反馈 ID |

### 请求体 `ModerationHandleReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `replyTitle` | string | 否 | 回执短标题（**≤40 字**）；留空按（类型 + 结论）用缺省文案 |
| `replyContent` | string | 条件 | 处置文案（**≤600 字**）。`outcome=handled` 时可空（缺省文案）；**`outcome=rejected` 时必填** —— 即为**不采纳原因**，纯空白 → `400` |
| `outcome` | string | 否 | `handled` 通过 / 已处理（**缺省**）｜ `rejected` 不采纳 / 退回；非法值 → `400` |

### 响应 `data`
`null`。

**处置动作**（同一事务）：更新 `moderation_task` 行（`status='handled'` / `outcome` / `reply_title` / `reply_content` / `handled_by` / `handled_at`）+ 投递站内回执（`reply_title` / `reply_content` 快照）。

> **已处置再处理 → `400`「该记录已处理」**（防结论被静默改写 + 回执重复投递）。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `outcome` 非法；**`rejected` 时 `replyContent` 缺失 / 纯空白**；`replyTitle` >40 字；`replyContent` >600 字；`status` 非法值；**已处置再处理** |
| `4001` | 反馈不存在 |
| `401` | 未带 / token 失效 |

### 备注
- **`outcome` 是任务表的物理列**（`status='handled'` 时必填）—— 「通过 / 不采纳」不靠文案推断。
- **文案只有一列**：不采纳原因并入 `replyContent`，无独立的「拒绝理由」字段。
- **回执文案按结论分流**：通过 → 「反馈已处理」，不采纳 → 「反馈未采纳」（正文即管理员填写的处置文案 / 不采纳原因）。投递判据 = 提交人 `userId` 有效（提交恒带登录态 ⇒ **恒投递**），投递与处置同事务。
- **不做批量处置**（一期不做，留作演进）。
