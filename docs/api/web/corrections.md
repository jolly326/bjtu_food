# web/corrections — 菜品问题反馈管理

**归属**：`CorrectionAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 处理学生提交的菜品问题反馈（`dish_correction`）—— **两类**，**按 `type` 分派**：
>
> | `type` | 语义 | 处置动作 |
> |---|---|---|
> | **`field`** | 信息有误（**改动项快照**，未改动列留 NULL） | **逐项核对 → 采纳（部分可选）或拒绝** |
> | **`gone`** | 已经下架（**一键提交**，差异项列恒 NULL，说明 / 图片选填） | **仅「下架」或「拒绝」** |
>
> 🔴 **`gone` 处置不得提供「删除」** —— 下架 = `status=off`（可逆、保留评价）；删除 = 物理删除 + 级联删除评价（不可逆），**仅在 [web/dishes.md](./dishes.md) 由管理员主动执行**。

---

## GET /admin/corrections

**用途**：反馈列表（分页）

### 请求参数
| 名称 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` / `pageSize` | number | 否 | 1 / 20 | 页码 / 每页条数（上限 **50**） |
| `status` | string | 否 | — | `pending` / `adopted` / `rejected`；**不传 = 全部** |
| `dishId` | number | 否 | — | 按目标菜品筛选 |
| `type` | string | 否 | — | `field` / `gone`；**不传 = 全部** |

**排序**：`createdAt DESC`。

### 响应 `data` = `AdminPageResult<DishCorrectionAdminVO>`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 反馈 ID |
| `dishId` / `dishName` | number / string | 目标菜品 ID / 名称（**实时回查，含已下架**；菜品已物理删除为 null） |
| `userId` / `userNickname` | number / string | 提交人（**匿名提交 `userId = 0`** 且昵称为空，后台显示为「游客」）；静默登录的游客为真实 ID |
| `status` | string | `pending` / `adopted` / `rejected` |
| `changeCount` | number | **差异项数量**（一眼看出「改了几项」） |
| `floorImpact` | string \| null | 含 `floor` 改动时的连带影响提示（同档口菜品数） |
| `reply` / `rejectReason` | string | 处理回复 / 不采纳原因 |
| `handledAt` | string | 处理时间（未处理为空串） |
| `createdAt` | string | 提交时间 |
| `updatedAt` | string | 最近更新时间 |

> 列表**不展开各项内容**（那是详情的事）：列表给「改了几项 + 是否涉及楼层」。

---

## GET /admin/corrections/{id}

**用途**：**单条详情（含 `differences` 对照）**

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 反馈 ID |

### 响应 `data` = `DishCorrectionDetailVO`

在列表字段基础上，把快照内容与对照一并给出：

| 字段 | 类型 | 说明 |
|---|---|---|
| `differences` | object[] | **采纳清单**（**仅仍有差异的项**，见下） |
| `type` | string | `field` / `gone` |
| … | | 其余同列表 VO |

**`differences[]` 结构**（**核心：采纳清单**，`GET` 详情返回；只列「仍有差异」的项）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `field` | string | 差异项键：`name` / `price` / `canteenName` / `stallName` / `floor` / `images` / `attributes.<维度ID>`（**可直接作为 `acceptedFields` 的取值**） |
| `label` | string | 中文名（如「菜品名称」「饮食属性」），端上直接渲染 |
| `oldValue` | any | **当前菜品 / 档口的实时值** |
| `newValue` | any | 用户提交的值 |
| `affectsOthers` | boolean | **是否连带影响同档口其它菜品**（仅 `floor` 项为 `true`，采纳即写回目标档口 `stall.floor`，同档口菜品一并生效）—— UI 须高亮提示 |

> **只列「仍有差异」的项**：快照是提交当时的差异；若管理员在采纳前已手动改成相同值，该项已无差异，不再列入。
> **`oldValue` 取实时值**：快照只存「提交的新值」（未改动列 NULL），原值必须联表回查 `dish` / `stall`。

**`type=gone` 专用字段**（**不返回 `differences[]`**）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | 恒 `gone` |
| `note` | string \| null | 用户**选填**补充说明（≤200 字），**处置时必须展示给管理员** |
| `images` | string[] | 用户**选填**图片（≤3 张），**处置时必须展示** |
| `goneUserCount` | number | **N 人反馈已下架**（同用户已去重）；**仅作参考、不作阈值** |
| `dishName` / `dishStatus` | string | 目标菜品名 / 当前 `status`（`on` / `off`） |

> ⚠️ **菜品已被物理删除**时（`dish_correction` 不设外键，记录仍在）：`gone` 型**只能驳回并说明**；`field` 型返回 `4001`。

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
| `acceptedFields` | string[] | **是** | **采纳哪些差异项**（取值 = `differences[].field`）。**空数组 → `400`**（要拒绝请走 `PUT`） |
| `stallId` | number | 否 | 两段式第二段：选定的**既有档口** |
| `createIfMissing` | boolean | 否 | 两段式第二段：确认**按提交的档口名新建档口**（默认 `false`） |
| `reply` | string | 否 | **可选附注**（**≤600 字**），随回执下发；缺省用固定文案 |

### 响应 `data`
`null`，或两段式第一段的 `StallConfirmVO`。

**两段式档口确认**（仅当本次反馈含 `stallName` 改动、且提交的档口名**未精确匹配**现有档口时触发）：
1. **第一段**（不带 `stallId` / `createIfMissing`）→ **不执行采纳**，返回 `StallConfirmVO`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `needStallConfirm` | boolean | `true` |
| `candidates` | object[] | 候选档口 `[{ id, name }]` |

2. **第二段**：带 `stallId`（挂靠既有档口）或 `createIfMissing: true`（按提交名新建）再次调用 → 执行采纳。

> 目的：**防止错别字产生重复档口**。
> **属性采纳必须「按维度合并」，SHALL NOT 整体覆盖**：快照只含改动维度，采纳时保留 `dish.attributes` 其余键，仅对本次勾选采纳的维度写入解析后的**取值 ID**；勾选采纳且提交值为**空数组**（用户清空该维度）⇒ **删除该键**，**不落空数组**。
> **`images` 采纳的地址口径**：快照存的是**绝对 URL**，`dish.images` 是相对路径列 ⇒ 写入前**必须把绝对 URL 还原为站内相对路径**。

---

## PUT /admin/corrections/{id}

**用途**：**拒绝**（不采纳）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 反馈 ID |

### 请求体 `DishCorrectionHandleReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `rejectReason` | string | **是** | 不采纳原因（≤200 字，纯空白 → `400`） |
| `reply` | string | 否 | 处理说明（**≤600 字**），随回执下发 |

### 响应 `data`
`null`。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `acceptedFields` 缺失 / 为空 / 含未知项；`rejectReason` 缺失或纯空白；`reply` / `rejectReason` 超长；`images` 采纳后**序列化超 1024 字**；**已处理（`status != pending`）再处理** → 采纳端点「该反馈已处理」/ 拒绝端点「该纠错已处理」 |
| `4001` | 反馈不存在；**目标菜品已被物理删除**（`field` 采纳时） |
| `401` | 未带 / token 失效 |

### 备注
- **只列「仍有差异」的项**：采纳清单反映**此刻**事实；「采纳一个和当前值一样的值」→ `400`。
- **`acceptedFields` 必填、空数组 400**：显式优先；「什么都不采纳」是**拒绝**（有独立动作 + 必填原因）。
- **楼层联动必须提示**：`floor` 属档口，采纳会**连带改同档口所有菜品**；`differences[].affectsOthers` 标记该行，UI 据此二次确认（受影响菜品数由列表 `floorImpact` 给出）。
- **两段式档口确认不简化**：它是「错别字 → 重复档口」的唯一防线。
- **回执文案按 `type` 分流**：`field` 采纳（全部 / 部分项）/ 拒绝；`gone` 分「已下架」/「仍在售」两套文案。部分采纳必须逐项告知未采纳项。投递判据 = `userId` 有效（静默登录的游客亦投递；匿名不投递），失败不阻塞。
- **不做批量采纳**（一期不做）。
