# web/reviews — 评价管理

**归属**：`ReviewAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 治理学生评价的可见性 —— **隐藏 / 恢复显示 / 删除**。`is_hidden` 是**唯一可见性判据**；隐藏后公开列表与「我的评价」都不可见（**含作者本人**），评分聚合只统计 `is_hidden = 0`。

---

## GET /admin/reviews

**用途**：评价列表（分页）

### 请求参数
| 名称 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` | number | 否 | 1 | 页码 |
| `pageSize` | number | 否 | 20 | 每页条数；上限 **50** |
| `hidden` | boolean | 否 | — | 按隐藏状态筛选；**不传 = 全部** |
| `dishId` | number | 否 | — | 按菜品筛选 |
| `userId` | number | 否 | — | 按提交人筛选 |
| `keyword` | string | 否 | — | 评价正文关键词（模糊匹配） |

**排序**：`createdAt DESC`（**重复提交保留 `createdAt`**，覆盖不改时间）。

### 响应 `data` = `AdminPageResult<ReviewAdminVO>`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 评价 ID |
| `userId` | number | 评价者用户 ID |
| `userNickname` / `userAvatar` | string | 评价者昵称 / 头像（联表带出）。头像为**绝对 URL**；作者无头像（游客 / 未设置 / 已注销）→ **`null`**，前端走统一占位（灰底 + 人形），不出 `openid` 等敏感字段 |
| `dishId` / `dishName` | number / string | 关联菜品 ID / 名称 |
| `rating` | number | 评分（1~5） |
| `content` | string | 评价正文（空串兜底） |
| `images` | string[] | 配图**绝对 URL** 数组（≤3 张；恒为数组） |
| `hidden` | boolean | **是否被隐藏**（库列 `is_hidden` TINYINT 由持久层映射为 boolean） |
| `createdAt` | string | 发表时间（`yyyy-MM-dd HH:mm:ss`；**重复提交保留原值**） |

> **不出 `updatedAt`** —— `review` 表没有 `updated_at` 列；`createdAt` **仅表达「首次发表时间」**（覆盖更新不刷新它 ⇒ 不承载「最后活动时间」语义，见 [schema/review.md](../../schema/review.md)）。

---

## PUT /admin/reviews/{id}/hidden

**用途**：**设置隐藏状态**（`true` 隐藏 / `false` 恢复显示）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 评价 ID |

### 请求体 `ReviewHiddenReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `hidden` | boolean | **是** | `true` 隐藏 / `false` 恢复显示 |
| `note` | string | 否 | **可选附注**（≤200 字）：隐藏时随回执下发给作者；**恢复显示时忽略**（置 NULL） |

### 响应 `data`
`null`。

> 端点用 `hidden`（**状态资源**）而非 `hide`（动词）：`hide` 与「可传 `hidden=false`」自相矛盾。
> 隐藏向作者投递站内回执；附注有则随回执展示、无则用固定文案；投递失败**不阻塞**处置。

---

## DELETE /admin/reviews/{id}

**用途**：**删除**（物理删除 + 触发评分重算 + 回执）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 评价 ID |

### 响应 `data`
`null`。

> **物理删除**（`review` 无软删列），并触发菜品评分重算；**不可逆** ⇒ UI **二次确认**。删除亦向作者投递站内回执。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `hidden` 缺失 / 非布尔；`note` 超 200 字 |
| `4001` | 评价不存在（`PUT` / `DELETE` 目标不存在） |
| `401` | 未带 / token 失效 |

### 备注
- **动作恰好三个**：隐藏 / 恢复显示 / 删除。**无内容安全态处置端点** —— 机审 `pass` / `review` 直接放行、`risky` 直接拒绝（不存在待复核队列）。
- **回执口径**：隐藏 / 删除向作者投递登录级回执（判据 = 有可归属 `userId`）；投递失败不阻塞处置。
- **「重新评价即恢复可见」保持现状**（覆盖提交时 `is_hidden` 重置为 0）：重新提交是全新内容、重新过机审。
- **附注落库而非只发通知**：处置可追溯（隐藏附注写 `review.hidden_note`）。
