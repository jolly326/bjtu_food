# web/report-reasons — 举报原因管理

**归属**：`ReportReasonAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 维护 client 举报弹层的「原因」单选字典（[`GET /report-reasons`](../client/reviews.md#get-report-reasons) 的数据源）—— `id`（原因 ID）+ `label`（中文）+ 顺序 + 启停。新增原因**免发版、免客户端改动**。

---

## GET /admin/report-reasons

**用途**：原因列表（**含已停用**）

### 请求参数
无（**不分页**）。

### 响应 `data` = `ReportReasonAdminVO[]`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 原因 ID（客户端提交字段名 = `reasonId`，见 [reviews.md](../client/reviews.md#post-reviewsidreport)；落库列 = `user_feedback.sub_reason_id`） |
| `label` | string | 中文标签（端上直接渲染） |
| `order` | number | 展示顺序（升序） |
| `status` | string | `on` / `off` |
| `feedbackCount` | number | **被举报记录引用次数**（`type='report'` 且 `sub_reason_id = id`）—— 删除前判断 + 列表展示 |
| `updatedAt` | string | 更新时间（`yyyy-MM-dd HH:mm:ss`） |

**排序**：按 `order` 升序。

---

## POST /admin/report-reasons

**用途**：新增（默认**启用**、排最后）

### 请求体 `ReportReasonSaveReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `label` | string | **是** | 中文标签（1~32 字，如「垃圾广告 / 营销刷屏」） |

> **只填 `label`** —— 原因 ID 由后端生成，是历史举报的数据锚点。

### 响应 `data` = `ReportReasonAdminVO`
新建的原因。

---

## PUT /admin/report-reasons/{id}

**用途**：改名（只改 `label`）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 原因 ID |

### 请求体 `ReportReasonRenameReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `label` | string | **是** | 中文标签（1~32 字） |

### 响应 `data`
`null`。

---

## PUT /admin/report-reasons/{id}/status

**用途**：**启停**

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 原因 ID |

### 请求体 `ReportReasonStatusReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `status` | string | **是** | `on` 启用 / `off` 停用 |

### 响应 `data`
`null`。

---

## PUT /admin/report-reasons/sort

**用途**：**整体提交顺序**（拖拽后一次提交）

### 请求体 `SortItemsReq`
`items`：`[{ id, order }]` 数组，整体替换顺序。**边界（全量行 / 非法提交 → `400`）见 [api/README 的「拖拽排序提交」](../README.md#拖拽排序提交)**。

### 响应 `data`
`null`。

---

## DELETE /admin/report-reasons/{id}

**用途**：删除（**被举报记录引用返回 `400`**）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 原因 ID |

### 响应 `data`
`null`。

> **删除约束**：**被举报记录引用过就禁止删除**（`user_feedback.sub_reason_id` 存的就是原因 ID）—— 删掉会让历史举报翻不出中文。下线一律用**停用**。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `label` 为空或超长 / 重名；`status` 非法；**删除仍被举报记录引用的原因**；**停用最后一条启用**；**启用数已达上限 8**；排序提交非法 |
| `4001` | 原因不存在（`PUT` / `DELETE` 目标不存在） |
| `401` | 未带 / token 失效 |

### 备注
- **数据锚在 ID**：原因 ID 由后端生成、历史举报按它落库；`label` 只是展示文案，**改名免费**。
- **删除用「引用即禁删」而非级联**：管理员必须显式把历史记录归到别的原因（或直接停用），不做静默改写历史。
- **强制至少 1 条启用**：举报是 UGC 治理入口，不接受「把入口配空」；**启用 ≤8 条**（单选弹层可用性约束）。
- **排序一律「拖拽 + 批量端点」**；新建项默认排最后。
