# 反馈处理（B-04）

> 所属板块：**web（管理后台）** ｜ 鉴权：**🔑 口令**（`X-Admin-Token`）
> 返回：[功能总览](./README.md)

## 介绍

处理学生**问题反馈（`issue`）**并回看存量历史类型数据的运营闭环。进页默认停在「待处理」列表；列表按状态 / 类型 / 关键词服务端过滤。

处理动作为人工处理：

- **已处理**：填回复（必填）→ 标记 `handled`；
- **不采纳 / 退回**：填回复（必填）+ 不采纳原因（必填）→ 同样落到 `handled` 终态（结论差异由 `outcome=rejected` 承载；反馈无「退回重提」链路）。

实体操作人工完成：如据此修改菜品（经菜品管理）、存量举报类点「评价 #id →」走评价管理深链（`?rid=`）定位隐藏 / 删除。处理结果经站内通知回执（`feedback_handle`，仅已认证提交人；游客不投递）。

## UI

> 📐 页面 UI 设计稿已拆出 → [web-反馈处理.md（docs/ui）](../ui/web-反馈处理.md)（**UI 口径以该文件为唯一真源**）

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
| `type` | string | 否 | 反馈类型：`issue`；存量历史类型 `suggestion` / `add` / `error` / `bug` / `report` / `other` 保留筛选项（仅存量查询）；**非法值 400** |
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

### 响应 · `FeedbackAdminVO` 单行字段

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 反馈 ID |
| `userId` | number \| null | 提交人用户 ID；**游客提交为 null**（页面显示「游客」） |
| `userNickname` | string | 提交人昵称 |
| `type` | string | 反馈类型：`issue` 问题反馈；存量历史类型 `suggestion` / `add` / `error` / `bug` / `report` / `other` 仍可能出现于老数据（未登记值类型列回落原值展示） |
| `sub` | string \| null | **二级分类，仅存量类型有值**：`suggestion` → `idea`=想法 / `problem`=问题；`report` → 举报原因机器值（经字典端点 `GET /feedback/report-reasons` 翻译为原因文案，如「举报 · 垃圾广告 / 营销刷屏」；**仅展示、不作筛选维度**）。`issue` 恒为 null |
| `content` | string | 反馈正文；存量 `report` 可空 |
| `images` | string[] | 反馈配图 URL 数组（COS 绝对地址，≤3 张） |
| `relatedType` | string \| null | 关联对象类型，仅存量数据携带（存量举报 = `review`）；`issue` 恒为 null |
| `relatedId` | number \| null | 关联对象 ID，仅存量数据携带（存量举报 = 评价 ID）；`issue` 恒为 null |
| `relatedDishName` | string \| null | 关联菜品名称（仅存量 `relatedType=dish` 时填充，含已下架菜品；其余为 null；web 据此外显关联菜品名并支持深链检索） |
| `status` | string | 处理状态：`pending`=待处理 / `handled`=已处理 |
| `outcome` | string \| null | 处理结论：`handled`=通过/已处理（缺省）/ `rejected`=不采纳/退回；**未处理为 null**（由 status + rejectReason 派生，非物理列） |
| `reply` | string \| null | 管理员回复内容（学生收到的回执正文） |
| `rejectReason` | string \| null | 不采纳原因（结论为 rejected 时非空，1~200 字） |
| `createdAt` | string | 提交时间 |
| `handledAt` | string \| null | 处理时间 |

> **无 `contact`**（产品定型「不收集联系方式」）；**无 `secState`**（无人工复核，内容安全由微信检测前置把关）。

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
| 400 | 回复为空 / 超长；rejected 未填原因；type 非法；反馈不存在；重复处理（已 handled） | 参数与业务校验 |
| 403 | 口令缺失或错误 | `X-Admin-Token` 校验失败 |

## 数据（落库）

| 表 / 列 | 变化 | 中文解释 |
|---|---|---|
| `user_feedback.status` | `pending` → `handled` | 处理状态（终态唯一，无中途态） |
| `user_feedback.reply` | 写入 | 管理员回复（回执正文） |
| `user_feedback.reject_reason` | 写入 / 保持 NULL | 不采纳原因；`handled` 结论时恒 NULL（结论差异由此列是否非空承载） |
| `user_feedback.handled_at` | 写入当前时间 | 处理时间 |
| `notification` | INSERT（异步） | 向**已认证**提交人投递回执：`feedback_handle`（含回复；不采纳时含原因）；游客不投递、不阻塞 |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

**无（文档与代码一致）**
