# 菜品纠错管理（B-11）

> 所属端：**管理后台（Web）** ｜ 鉴权：🔑 管理员
> 返回：[管理后台功能总览](./README.md)

## 介绍

处理学生在 A-16 提交的**菜品信息纠错**。纠错是「局部提交（patch）」——只带**改动项**，未带的字段表示「不改动」。管理员的动作是**采纳**（写回 `dish`）或**拒绝**（留痕原因）。

**与相邻功能的边界**：
- 与 B-09 / B-10 同为 UGC 治理，但**只有纠错会写回主数据**，因此它是唯一「会改动学生可见内容」的治理动作，处置需更谨慎。
- 纠错**不接受**「部分采纳」：一次处理要么整体采纳、要么整体拒绝。若只想采纳其中几项，管理员拒绝本条后自行到 B-05 修改菜品（避免引入「部分写回」的状态复杂度）。

**采纳流程（两段式档口确认）**：
1. **前置校验**：纠错须为 `pending`（重复处理 `400`）；目标菜品须存在（已物理删除 → `4001`）。
2. **解析归属档口**：用纠错提交的 `stallName` 匹配现有档口——
   - **命中** → 直接写回并完成采纳；
   - **未命中** → **不写回**，返回候选档口列表（`needStallConfirm=true`），由管理员二选一：
     - 指定既有档口 `stallId` 再次调用；
     - 或 `createIfMissing=true`，按提交的档口名（及食堂名）新建档口后挂靠。
   > 两段式的意义：学生填的档口名是**自由文本**，直接按名新建会产生同名重复档口；先给候选让管理员确认，是唯一能避免主数据污染的时机。

**写回语义**：按改动项写回 `dish`；**可空字段不覆盖既有值**（未提交的字段保持原样）。写回后纠错归档为 `adopted`，`reply` 落固定文案。

**拒绝**：`reply` + `rejectReason` 均必填（学生将看到这两项）；纯空白 `400`。

**回执**：采纳与拒绝**都**投递站内通知（学生端 A-11）；仅提交人为「可归属用户」（非游客、已邮箱认证）时投递；投递失败不影响处理结果。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/corrections` | 🔑 | 纠错列表（分页 + 筛选） |
| POST | `/admin/corrections/{id}/adopt` | 🔑 | 采纳（可能返回「需档口确认」，见下） |
| PUT | `/admin/corrections/{id}` | 🔑 | 拒绝；非 pending 返回 `400`；不存在返回 `4001` |

### 请求参数（`GET /admin/corrections`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 20 |
| `status` | string | 否 | `pending` / `adopted` / `rejected`；不传 = 全部 |
| `dishId` | number | 否 | 按目标菜品筛选 |

### 请求 · `POST /admin/corrections/{id}/adopt`（`CorrectionAdoptReq`，本段可为空请求体）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `stallId` | number | 否 | 第二段：管理员选定的既有档口 ID（不存在 → `400`） |
| `createIfMissing` | boolean | 否 | 第二段：确认按提交档口名新建档口（与 `stallId` 互斥，二者都传以 `stallId` 为准） |

> **响应二义性（必须按 `data` 判断）**：
> - `data = null` → 采纳已完成；
> - `data = StallConfirmVO{needStallConfirm=true, candidates[]}` → **未执行采纳**，需管理员确认档口后再次调用。

### 请求 · `PUT /admin/corrections/{id}`（`CorrectionRejectReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `reply` | string | 是 | 处理回复（1~1000 字；纯空白 → `400`） |
| `rejectReason` | string | 是 | 不采纳原因（1~200 字；纯空白 → `400`） |

## 字段

### 响应 · `CorrectionAdminVO`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 纠错 ID |
| `dishId` | number | 目标菜品 ID |
| `dishName` | string \| null | 目标菜品名（菜品已删除为 `null`） |
| `userId` | number \| null | 提交人 ID（**匿名提交为 `null`**） |
| `userNickname` | string \| null | 提交人昵称 |
| `name` | string \| null | 提交的菜品名（`null` = 未改动此项） |
| `price` | number \| null | 提交的现价（分；`null` = 未改动） |
| `canteenName` | string \| null | 提交的食堂名（自由文本；`null` = 未改动） |
| `stallName` | string \| null | 提交的档口名（自由文本；`null` = 未改动） |
| `attributes` | object \| null | 提交的描述属性（键 = `fieldKey`，**值 = 中文文本 / 数组**；`null` = 未改动） |
| `images` | string[] | 提交的配图绝对 URL 数组（无图空数组） |
| `status` | string | `pending` / `adopted` / `rejected` |
| `reply` | string \| null | 处理回复（未处理为 `null`） |
| `rejectReason` | string \| null | 不采纳原因（非 rejected 为 `null`） |
| `createdAt` | string | 提交时间 |
| `handledAt` | string \| null | 处理时间 |

### 响应 · `StallConfirmVO`（仅「需档口确认」时返回）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `needStallConfirm` | boolean | 恒为 `true`（本次未执行采纳） |
| `candidates` | object[] | 候选档口 `[{id, name}]`：提交食堂名命中现有食堂时 = 该食堂下全部档口；未命中 = 全量档口 |

### 错误码

| 码 | 场景 |
|---|---|
| `400` | 纠错已处理（非 pending）；`stallId` 不存在；拒绝时回复 / 原因为空或超长 |
| `4001` | 纠错不存在；**目标菜品已被物理删除**（无法采纳） |

## 数据（读写）

| 表 | 读写什么 | 中文解释 |
|---|---|---|
| `dish_correction` | 查、写 `status` / `reply` / `reject_reason` / `handled_at` / 实际挂靠 `stall_name` | 纠错记录 |
| `dish` | 采纳时按改动项写回（可空字段不覆盖）、存在性校验 | 目标菜品 |
| `stall` | 按名查找 / 新建 / 回填实际档口名 | 归属档口（B-04） |
| `canteen` | 新建档口时按名 upsert | 所属食堂（B-03） |
| `user` | 读 `nickname` 回填提交人 | 提交人 |
| `notification` | 写入回执（仅可归属用户） | 处理回执（学生端 A-11） |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

- **新增 `attributes` / `images` 出参**：现有管理端列表不下发这两项，管理员无法核对纠错内容。
- **两段式档口确认改为显式契约**：现有实现已具备该逻辑，但响应「`null` = 成功 / 非 `null` = 需确认」的二义性未文档化，易被误读为「采纳失败」。本功能将其固化为契约。
- **新增「不接受部分采纳」约束**：明确一次处理要么全采纳、要么全拒绝。
