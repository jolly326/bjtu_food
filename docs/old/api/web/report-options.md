# web/report-options — 举报原因管理

**归属**：`ReportOptionAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 维护 client 举报弹层的「原因」单选字典（[`GET /report-options`](../client/reviews.md#get-report-options) 的数据源）—— `id`（原因 ID）+ `name`（中文）+ 启停。新增原因**免发版、免客户端改动**。
> 与反馈类型字典（[web/feedback-options.md](./feedback-options.md)）**同族**：表结构同形（`id` / `name` / `status` / `updated_at`）、不变量与出参形状完全一致，差异只在「被哪张工单表引用」。

---

## GET /admin/report-options

**用途**：原因列表（**含已停用**）

### 请求参数
无（**不分页**）。

### 响应 `data` = `OptionAdminVO[]`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 原因 ID（客户端提交字段名 = `optionId`，见 [client/reviews.md](../client/reviews.md#post-reviewsidreport)；落库列 = `report.option_id`） |
| `name` | string | 中文名（端上直接渲染） |
| `status` | string | `on` / `off` |
| `usageCount` | number | **被举报记录引用次数**（`report.option_id = id`）—— 删除前判断 + 列表展示 |
| `updatedAt` | string | 更新时间（`yyyy-MM-dd HH:mm:ss`） |

**排序**：按 `id` 升序（字典不设排序位）。

---

## POST /admin/report-options

**用途**：新增（默认**启用**、排最后）

### 请求体 `OptionSaveReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | **是** | 中文名（1~32 字，如「垃圾广告 / 营销刷屏」） |

> **只填 `name`** —— 原因 ID 由后端生成，是历史举报的数据锚点。

### 响应 `data` = `OptionAdminVO`
新建的原因（含 DB 时钟写入的 `updatedAt`）。

---

## PUT /admin/report-options/{id}

**用途**：改名（只改 `name`）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 原因 ID |

### 请求体 `OptionRenameReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | **是** | 中文名（1~32 字） |

### 响应 `data`
`null`。

---

## PUT /admin/report-options/{id}/status

**用途**：**启停**

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 原因 ID |

### 请求体 `OptionStatusReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `status` | string | **是** | `on` 启用 / `off` 停用 |

### 响应 `data`
`null`。

> **幂等**：把 `status` 置为原值直接返回成功（不刷新 `updated_at`）。

---

## DELETE /admin/report-options/{id}

**用途**：删除（**被举报记录引用返回 `400`**）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 原因 ID |

### 响应 `data`
`null`。

> **删除约束**：**被举报记录引用过就禁止删除**（`report.option_id` 存的就是原因 ID）—— 删掉会让历史举报翻不出中文。下线一律用**停用**。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `name` 为空 / 超长 / 新增重名；`status` 非法；**删除仍被举报记录引用的原因**；**停用最后一条启用**；**启用数已达上限 8** |
| `4001` | 原因不存在（`PUT` / `DELETE` 目标不存在） |
| `401` | 未带 / token 失效 |

### 备注
- **数据锚在 ID**：原因 ID 由后端生成、历史举报按它落库；`name` 只是展示文案，**改名免费**（历史举报的中文翻译实时生效）。
- **删除用「引用即禁删」而非级联**：管理员必须显式把历史记录归到别的原因（或直接停用），不做静默改写历史。
- **强制至少 1 条启用**：举报是 UGC 治理入口，不接受「把入口配空」；**启用 ≤8 条**（单选弹层可用性约束）。
- **字典不设排序位**：列表与管理端维护界面一律按 `id` 升序；新建项排在末尾（`id` 自增）。
