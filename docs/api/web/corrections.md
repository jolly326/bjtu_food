# web/corrections — 菜品问题反馈管理

**归属**：`CorrectionAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 处理学生提交的菜品问题反馈（`correction`）—— **两类**，**按 `type` 分派**：
>
> | `type` | 语义 | 处置动作 |
> |---|---|---|
> | **`field`** | 信息有误（**改动项快照**，未改动列留 NULL） | **逐项核对 → 采纳（部分可选）或驳回** |
> | **`gone`** | 已经下架（**一键提交**，差异项列恒 NULL） | **仅「下架」或「驳回」** |
>
> 提交行**只存提交事实**：处置状态 / 结论 / 处置文案 / 处置人 / 处置时间挂靠 [`moderation_task`](../../schema/moderation_task.md)（多态键 `('correction', type_id)`）。
>
> 🔴 **`gone` 处置不得提供「删除」** —— 下架 = `status=off`（可逆、保留评价）；删除 = 物理删除 + 级联删除评价（不可逆），**仅在 [web/dishes.md](./dishes.md) 由管理员主动执行**。

---

## GET /admin/corrections

**用途**：反馈列表（分页）

### 请求参数
| 名称 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` / `pageSize` | number | 否 | 1 / 20 | 页码 / 每页条数（上限 **50**） |
| `status` | string | 否 | — | `pending` 待处置 / `handled` 已处置；**不传 = 全部**；非法值 `400` |
| `type` | string | 否 | — | `field` / `gone`；**不传 = 全部**（管理端据此分 Tab） |
| `dishId` | number | 否 | — | 按目标菜品筛选 |

**排序**：`createdAt DESC`。

> `status` **以处置任务表为判据**；`handled` 的两种结论由出参 `outcome` 区分（`handled` 采纳或已下架 / `rejected` 不采纳）。

### 响应 `data` = `AdminPageResult<CorrectionAdminVO>`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 反馈 ID |
| `type` | string | `field` / `gone` |
| `dishId` / `dishName` | number / string | 目标菜品 ID / 名称（**实时回查，含已下架**；菜品已物理删除为 `null`） |
| `userId` / `userName` | number / string | 提交人（**恒为真实登录用户**：`userId` = `user.id`；注销后为「已注销用户」） |
| `name` / `price` / `canteenName` / `stallName` / `floor` | string / number / … | **提交的改动项快照**（未改动为 `null`；`price` 单位分；`gone` 型恒全 `null`） |
| `attributes` | object \| null | 提交的描述属性（**仅改动的维度**；键 = 维度 ID） |
| `images` | string[] | 提交的图片（`field` = 改动后的图片；`gone` = 选填补充；≤3 张） |
| `note` | string \| null | **补充说明**（≤200 字；仅 `gone` 型的选填补充） |
| `status` | string | `pending` / `handled` |
| `outcome` | string \| null | 处置结论：`handled` 采纳或已下架 / `rejected` 不采纳；**未处置为 `null`** |
| `replyTitle` | string \| null | 处置回执短标题（未处置为 `null`） |
| `replyContent` | string \| null | 处置文案 / 不采纳原因（未处置为 `null`；单一列） |
| `handledByName` | string \| null | 处置人登录名（未处置 / 账号已删除为 `null`） |
| `changeCount` | number | **差异项数量**（一眼看出「改了几项」） |
| `floorImpact` | string \| null | 含 `floor` 改动时的连带影响提示（同档口菜品数） |
| `createdAt` | string | 提交时间 |
| `handledAt` | string \| null | 处置时间（未处置为 `null`） |

> 界面**不展开各项内容**（那是详情的事）：列表给「改了几项（`changeCount`）+ 是否涉及楼层（`floorImpact`）」。

---

## GET /admin/corrections/{id}

**用途**：**单条详情（含 `differences` 对照）**

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 反馈 ID |

### 响应 `data` = `CorrectionDetailVO`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` / `type` / `dishId` / `dishName` / `userId` / `userName` | — | 同列表 |
| `dishStatus` | string \| null | 目标菜品**当前**上下架状态（`on` / `off`，实时回查；菜品已物理删除为 `null`） |
| `differences` | object[] | **采纳清单**（**仅仍有差异的项**，见下）；`type=gone` 恒为空数组 |
| `goneUserCount` | number \| null | **N 人反馈已下架**（同用户已去重）；**仅 `type=gone` 下发，仅作参考、不作阈值** |
| `note` / `images` | — | 同列表（`gone` 型的处置判读依据） |
| `status` / `outcome` / `replyTitle` / `replyContent` / `handledByName` / `createdAt` / `handledAt` | — | 同列表 |

**`differences[]` 结构**（**核心：采纳清单**，只列「仍有差异」的项）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `field` | string | 差异项键：`name` / `price` / `canteenName` / `stallName` / `floor` / `images` / `attributes.<维度ID>`（**可直接作为 `acceptedFields` 的取值**） |
| `label` | string | 中文名（如「菜品名称」「饮食属性」），端上直接渲染 |
| `oldValue` | any | **当前菜品 / 档口的实时值** |
| `newValue` | any | 用户提交的值 |
| `affectsOthers` | boolean | **是否连带影响同档口其它菜品**（仅 `floor` 项为 `true`，采纳即写回目标档口 `stall.floor`，同档口菜品一并生效）—— UI 须高亮提示 |

> **只列「仍有差异」的项**：快照是提交当时的差异；若管理员在采纳前已手动改成相同值，该项已无差异，**不列入采纳清单**。
> **`oldValue` 取实时值**：快照只存「提交的新值」（未改动列 NULL），原值必须联表回查 `dish` / `stall`。
> **`gone` 型不返回对照**（`differences` 恒空）：无可对照的原值，处置只依赖 `note` + `images` + 菜品当前状态。

> ⚠️ **菜品已被物理删除**时（`correction` 不设外键，记录仍在）：`gone` 型**只能驳回并说明**；`field` 型详情返回空 `differences`，采纳返回 `4001`。

---

## POST /admin/corrections/{id}/adopt

**用途**：**采纳**（`field` **逐项**；`gone` 采纳动作 = **置 `status=off`**）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 反馈 ID |

### 请求体 `DishCorrectionAdoptReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `acceptedFields` | string[] | **是**（`field` 型） | **采纳哪些差异项**（取值 = `differences[].field`）。**空数组 → `400`**（要驳回请走 `PUT`）；**`gone` 型忽略本字段** |
| `stallId` | number | 否 | 两段式第二段：选定的**既有档口** |
| `createIfMissing` | boolean | 否 | 两段式第二段：确认**按提交的档口名新建档口**（默认 `false`） |
| `replyContent` | string | 否 | **可选附注**（**≤600 字**），随回执下发；缺省用固定文案 |

### 响应 `data`
`null`，或两段式第一段的 `StallConfirmVO`。

**两段式档口确认**（**仅当本次采纳了食堂 / 档口项**、且提交的档口名**未精确匹配**现有档口时触发）：
1. **第一段**（不带 `stallId` / `createIfMissing`）→ **不执行采纳**，返回 `StallConfirmVO`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `needStallConfirm` | boolean | `true` |
| `candidates` | object[] | 候选档口 `[{ id, name }]`（按 `id` 升序） |

2. **第二段**：带 `stallId`（挂靠既有档口）或 `createIfMissing: true`（按提交名新建）再次调用 → 执行采纳。

**处置归档**（与写回同一事务）：`moderation_task` 置 `handled` / `outcome='handled'`，处置文案缺省「已采纳，菜品信息已更新」（`gone` 型为「已下架，感谢反馈」、标题「菜品已下架」），并投递站内回执。

> 目的：**防止错别字产生重复档口**。
> **属性采纳必须「按维度合并」，SHALL NOT 整体覆盖**：快照只含改动维度，采纳时保留 `dish.attributes` 其余键，仅对本次勾选采纳的维度写入解析后的**取值 ID**；勾选采纳且提交值为**空数组**（用户清空该维度）⇒ **删除该键**，**不落空数组**。
> **`images` 采纳的地址口径**：快照存的是**绝对 URL**，`dish.images` 是相对路径列 ⇒ 写入前**必须把绝对 URL 还原为站内相对路径**。

---

## PUT /admin/corrections/{id}

**用途**：**驳回**（不采纳）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 反馈 ID |

### 请求体 `ModerationHandleReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `replyTitle` | string | 否 | 回执短标题（≤40 字）；缺省按类型给「菜品信息未采纳」（`gone` 型为「**菜品仍在售**」） |
| `replyContent` | string | **是** | **不采纳原因**（≤600 字，纯空白 → `400`「请填写不采纳原因」） |
| `outcome` | string | 否 | 固定 `rejected`（缺省即 `rejected`；其他值 → `400`） |

### 响应 `data`
`null`。

> **`gone` 型驳回**语义为「经核实仍在售」，理由须写明原因（如「今日临时售罄，明天恢复」）。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `acceptedFields` 缺失 / 为空 / 含未知项 / **该项此刻已无差异**；`replyContent` 缺失或纯空白；`replyTitle` >40 字 / `replyContent` >600 字；`outcome` 非 `rejected`；`images` 采纳后**序列化超 1024 字**；`status` / `type` 非法值；**已处置再处理**（采纳端点「该反馈已处理」/ 驳回端点「该纠错已处理」） |
| `4001` | 反馈不存在；**目标菜品已被物理删除**（`field` 采纳时） |
| `401` | 未带 / token 失效 |

### 备注
- **只列「仍有差异」的项**：采纳清单反映**此刻**事实；「采纳一个和当前值一样的值」→ `400`。
- **`acceptedFields` 必填、空数组 400**：显式优先；「什么都不采纳」是**驳回**（有独立动作 + 必填文案）。
- **楼层联动必须提示**：`floor` 属档口，采纳会**连带改同档口所有菜品**；`differences[].affectsOthers` 标记该行，UI 据此二次确认（受影响菜品数由列表 `floorImpact` 给出）。
- **两段式档口确认不简化**：它是「错别字 → 重复档口」的唯一防线。
- **回执文案按 `type` 与结论分流**：`field` 采纳（全部 / 部分项）/ 驳回；`gone` 分「已下架」/「仍在售」两套文案。部分采纳必须逐项告知未采纳项。投递判据 = 提交人 `userId`（提交恒带登录态 ⇒ **恒投递**），投递与处置同事务。
- **不做批量采纳**（一期不做）。
