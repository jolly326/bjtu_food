# web/feedback — 意见反馈与举报处置

**归属**：`FeedbackAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> `user_feedback` **一表两用**：**意见反馈**（非举报）与**举报**（`type='report'`）**同表、同端点**，按 `category` 分流列表。两类数据的处理流程完全相同（提交 → 管理员处理 → 回执）。

---

## GET /admin/feedbacks

**用途**：反馈 / 举报列表（分页）

### 请求参数
| 名称 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` | number | 否 | 1 | 页码 |
| `pageSize` | number | 否 | 20 | 每页条数；上限 **50** |
| `category` | string | 否 | — | **列表分流**：`feedback` = 所有非举报 / `report` = 只举报；**不传 = 全部**（全局检索） |
| `status` | string | 否 | — | `pending` 待处理 / `handled` 已处理；**不传 = 全部** |
| `type` | string | 否 | — | 页面内类型下拉 = **当前写入白名单** `bug` / `suggestion` / `other`；服务端查询白名单另含历史类型 `issue` / `add` / `error`（存量已清零，见 [schema/user_feedback.md](../../schema/user_feedback.md)「存量口径」）；非法值 `400` |
| `userId` | number | 否 | — | 按提交人筛选 |
| `keyword` | string | 否 | — | 关键词（**对反馈正文 或 管理员回复**模糊匹配） |

**排序**：`createdAt DESC`。

> **`category` 而不是枚举 `type`**：`feedback` 用一句话表达「非举报」，历史与将来新增的类型自动包含。**举报页固定传 `category=report`**（`type` 恒为 `report`，无需类型下拉）。

### 响应 `data` = `AdminPageResult<FeedbackAdminVO>`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 反馈 ID |
| `userId` | number | 提交人 ID（`0` = 游客） |
| `userNickname` | string | 提交人昵称（**匿名提交回落为「游客」**） |
| `type` | string | 反馈类型（`bug` / `suggestion` / `other` / `report`；服务端另容忍历史类型 `issue` / `add` / `error` 的存量行） |
| `sub` | string | 二级分类；仅历史 `suggestion` 存量可能为 `idea` / `problem`；其余行为空 |
| `subReasonId` | number \| null | **举报原因 ID**（`report_reason.id`；仅 `type='report'` 行非空） |
| `reasonLabel` | string \| null | 举报原因中文名（仅举报行非空；由 `report_reason` 字典实时翻译，停用原因仍可译出） |
| `content` | string | 反馈正文（空串兜底） |
| `images` | string[] | 配图**绝对 URL** 数组（≤3 张；恒为数组） |
| `status` | string | `pending` / `handled` |
| `outcome` | string \| null | 处理结论：`handled` / `rejected`；**`pending` 为 `null`**（由 `status` + `rejectReason` 派生，无物理列） |
| `reply` | string | 管理员回复（可为空串） |
| `rejectReason` | string | 不采纳原因（`rejected` 时非空） |
| `createdAt` | string | 提交时间（`yyyy-MM-dd HH:mm:ss`） |
| `updatedAt` | string | 最近更新时间 |
| `handledAt` | string | 处理时间（未处理为空串） |
| `relatedReview` | object \| null | **被举报评价摘要**（仅举报且 `relatedType='review'` 时非空；其余为 `null`） |

**`relatedReview` 结构**（`category=report` 时填充）：
| 字段 | 类型 | 说明 |
|---|---|---|
| `authorNickname` | string | 被举报评价的作者昵称（管理端可见） |
| `content` | string | 评价正文**摘要**（截断，完整内容去评价管理看） |
| `rating` | number | 该评价的评分（1~5） |
| `images` | string[] | 该评价配图绝对 URL（恒为数组） |
| `hidden` | boolean | **该评价当前是否已被隐藏** |

> `relatedDishName`（`relatedType='dish'`，菜品问题反馈场景）保留。

---

## PUT /admin/feedbacks/{id}

**用途**：**处置**（**通过 / 不采纳**；举报可联动隐藏被举报评价）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 反馈 / 举报记录 ID |

### 请求体 `FeedbackHandleReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `outcome` | string | 否 | `handled` 通过 / 已处理（**缺省**）｜ `rejected` 不采纳 / 退回 |
| `reply` | string | 条件 | 处理回复（**≤600 字**）。`handled` 时**可选**（缺省回执用固定文案）；`rejected` 时作为**「处理说明」**随回执下发 |
| `rejectReason` | string | 条件 | 不采纳原因（≤200 字）；**`outcome=rejected` 时必填**，纯空白 → `400` |
| `hideReview` | boolean | 否 | **是否联动隐藏被举报评价**（缺省 `false`）。仅 `category/type = report` 且 `relatedType='review'` 时消费；**受理举报通常应传 `true`** |

**联动口径**：
- `handled` + `hideReview=true` → 隐藏被举报评价（幂等：评价已隐藏 / 已删除时**静默成功**）；
- `handled` + `hideReview=false` → 只记录「已受理」，不动评价；
- `rejected` → `hideReview` **不消费**，`rejectReason` **必填**。

### 响应 `data`
`null`。

> **已处理（`status != 'pending'`）再处理 → `400`「该记录已处理」**（防结论被静默改写 + 回执重复投递）。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `outcome` 非法；`handled` 时 `reply` 超长；**`rejected` 时 `rejectReason` 缺失 / 纯空白 / 超长**；`type` / `status` 非法值；`hideReview` 取值非布尔；**已处理再处理** |
| `4001` | 记录不存在 |
| `401` | 未带 / token 失效 |

> **举报特有**：`hideReview=true` 但被举报评价已不存在 → **静默成功，不报错**（目标已达成）。

### 备注
- **合表理由**：两类数据流程完全相同，拆表 / 拆端点会把整套逻辑复制一份；**分流只发生在列表参数**（`category`），处理逻辑完全复用。
- **`outcome` 不落物理列**：由 `status` + `reject_reason` 派生（`rejected ⇒ reject_reason 非空`；`handled ⇒ reject_reason 为 NULL`）。
- **回执按 `type` 分流文案**：举报人收到「举报已受理 / 不予处理」、反馈人收到「反馈已处理 / 未采纳」；受理且顺带隐藏了评价时正文写明「已隐藏该评价」。投递判据 `user_id > 0`（静默登录的游客同样收到；匿名提交不投递），失败不阻塞处置。
- **联动隐藏是「一次性动作」而非状态**：恢复显示走 [web/reviews.md](./reviews.md) 的「恢复显示」。
- **不做批量处置**（一期不做，留作演进）。
