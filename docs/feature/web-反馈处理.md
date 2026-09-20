# 反馈处理（唯一运营闭环）（B-04）

> 所属板块：**web（管理后台）** ｜ 鉴权：**🔑 口令**（`X-Admin-Token`）
> 返回：[功能总览](./README.md)

## 干什么

处理学生的**全部诉求**（建议 / 投稿 / 纠错 / 举报）：**采纳**（据此录入、修改或下架菜品）或**不采纳（必填原因）**，标记处理后向已认证提交人投递站内回执。这是全项目**唯一有待处理态的运营对象**。

## UI

- 导航「反馈」→ `/dashboard/feedback`（导航项带**待处理徽标**，替代原工作台看板）。
- 筛选：状态（**默认待处理** / 已处理 / 全部）+ 类型 + 关键词搜索。
- 表格列：类型 / 关联对象 / 内容 / 提交人 / 时间 / 状态。
- 处理抽屉：左看内容与关联对象（含配图 ≤3 张），右写回复与结论。

## 操作

1. 进页 → 默认停在「待处理」列表。
2. 点「处理」→ 抽屉打开 → 查看内容、配图与关联对象。
3. **采纳 / 已处理**：填回复（必填）→ 提交，标记 `handled`。
4. **不采纳 / 退回**：填回复（必填）+ 不采纳原因（必填）→ 提交，同样落到 `handled` 终态（反馈无「退回重提」链路）。
5. 处理动作需人工完成实体操作，**没有「一键转菜品」**：
   - 投稿类（`add`）：据此到 [菜品管理](./web-菜品管理.md) 录入菜品后再标记处理；
   - 纠错类（`error`）：点「菜品名 →」跳到菜品列表定位修改；
   - 举报类（`report`）：点「评价 #id →」直达 [评价管理](./web-评价管理.md) 深链（`?rid=`）定位隐藏 / 删除。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/feedbacks` | 🔑 | 反馈列表（分页；不传 status 返回全部，供回看） |
| PUT | `/admin/feedbacks/{id}` | 🔑 | 处理反馈（标记 handled + 写回复 / 结论 / 处理时间 + 触发回执） |

## 字段

### 请求 · `GET /admin/feedbacks`（query）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `status` | string | 否 | 处理状态：`pending`=待处理 / `handled`=已处理；不传 = 全部 |
| `type` | string | 否 | 反馈类型：`suggestion` / `add` / `error` / `report`（历史 `bug` / `other` 可筛选存量）；**非法值 400** |
| `userId` | number | 否 | 按提交用户过滤 |
| `keyword` | string | 否 | 关键词，对**反馈内容 / 管理员回复**模糊匹配 |
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 10（上限 100） |

### 响应 · `GET /admin/feedbacks`（`data` = `PageResult<FeedbackAdminVO>`）

**分页壳字段**：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | FeedbackAdminVO[] | 当前页反馈行数组（端上以它为准） |
| `total` | number | 符合条件的反馈总条数 |
| `page` | number | 服务端归一化后的实际页码 |
| `pageSize` | number | 服务端归一化后的实际每页条数 |
| `list` | FeedbackAdminVO[] | 过渡期兼容字段，恒等于 `records`，新代码勿用 |

### 响应 · `FeedbackAdminVO` 单行字段

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 反馈 ID |
| `userId` | number \| null | 提交人用户 ID；**游客提交为 null**（页面显示「游客」） |
| `userNickname` | string | 提交人昵称 |
| `type` | string | 反馈类型：`suggestion` 提个想法 / `add` 推荐菜品 / `error` 信息不对 / `report` 举报（历史存量可能为 `bug`/`other`） |
| `sub` | string \| null | **二级分类，仅 `suggestion` 有值**：`idea`=想法 / `problem`=问题（后台展示为「建议·想法 / 建议·问题」，**仅展示、不作筛选维度**） |
| `content` | string | 反馈内容（三类均把结构化字段拼在本字段） |
| `images` | string[] | 反馈配图 URL 数组（COS 绝对地址，≤3 张） |
| `relatedType` | string \| null | 关联对象类型：纠错 = `dish`；举报 = `review`；其他为 null |
| `relatedId` | number \| null | 关联对象 ID：纠错 = 菜品 ID；举报 = 评价 ID；其他为 null |
| `relatedDishName` | string \| null | **关联菜品名称**：仅 `relatedType='dish'` 时由服务端批量回填，**不区分上/下架、含已下架菜品**；其他关联类型 / 菜品已物理删除 → null（页面退回「菜品#id」占位，**不发起二次请求**） |
| `status` | string | 处理状态：`pending`=待处理 / `handled`=已处理 |
| `outcome` | string \| null | 处理结论：`handled`=通过/已处理（缺省）/ `rejected`=不采纳/退回；**未处理为 null**（由 status + rejectReason 派生，非物理列） |
| `reply` | string \| null | 管理员回复内容（学生收到的回执正文） |
| `rejectReason` | string \| null | 不采纳原因（结论为 rejected 时非空，1~200 字） |
| `createdAt` | string | 提交时间 |
| `handledAt` | string \| null | 处理时间 |

> **无 `contact`**（产品定型「不收集联系方式」，列已删）；**无 `secState`**（取消人工复核，安检态列已退役）。

### 请求 · `PUT /admin/feedbacks/{id}`（`FeedbackHandleReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `id` | number | **是** | 反馈 ID（路径参数） |
| `reply` | string | **是** | 管理员回复内容，1~1000 字；**纯空白视为未填写 → 400**（回复必填，否则学生收到空回执） |
| `outcome` | string | 否 | 处理结论：`handled`=通过/已处理（**缺省值**）/ `rejected`=不采纳/退回；非法值 → 400 |
| `rejectReason` | string | 条件必填 | 不采纳原因，1~200 字；**`outcome=rejected` 时必填**（纯空白 → 400「请填写不采纳原因」）；`outcome=handled` 时不消费（保持 NULL） |

### 响应 · `PUT /admin/feedbacks/{id}`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `data` | null | 无载荷；成功即 `code=200` |

### 错误码

| code | 含义 | 中文解释 |
|---|---|---|
| 400 | 回复为空 / 超长；rejected 未填原因；type 非法；反馈不存在 | 参数与业务校验 |
| 403 | 口令缺失或错误 | `X-Admin-Token` 校验失败 |

## 数据（落库）

| 表 / 列 | 变化 | 中文解释 |
|---|---|---|
| `user_feedback.status` | `pending` → `handled` | 处理状态（终态唯一，无中途态） |
| `user_feedback.reply` | 写入 | 管理员回复（回执正文） |
| `user_feedback.reject_reason` | 写入 / 保持 NULL | 不采纳原因；`handled` 结论时恒 NULL（结论差异由此列是否非空承载） |
| `user_feedback.handled_at` | 写入当前时间 | 处理时间 |
| `notification` | INSERT（异步） | 向**已认证**提交人投递 `feedback_handle` 回执（含回复；不采纳时含原因）；游客不投递、不阻塞 |
