# web/corrections — 菜品问题反馈管理

**归属**：`CorrectionAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 处理学生提交的菜品问题反馈（`correction`）—— **两类**，**按 `type` 分派展示**：
>
> | `type` | 语义 | 提交形态 | 详情内容 |
> |---|---|---|---|
> | **`field`** | 信息有误 | **改动项快照**（未改动列留 NULL） | 提交项对照（`differences[]`） |
> | **`gone`** | 已经下架 | **一键提交**（差异项列恒 NULL），说明与图片选填 | `note` + `images` + `goneUserCount` |
>
> 提交行**只存提交事实**：处置状态 / 结论 / 处置文案 / 处置人 / 处置时间挂靠 [`moderation_task`](../../schema/moderation_task.md)（多态键 `('correction', type_id)`）。
>
> 🔴 **处置只归档、不改写菜品**：管理端**不写 `dish` / `stall`** —— 菜品资料修改与 `gone` 可逆下架由管理员在 [web/dishes.md](./dishes.md) 手动执行（**本流程不提供「删除菜品」**：删除会级联清空该菜全部评价，不可逆）。

---

## GET /admin/corrections

**用途**：反馈列表（分页）

### 请求参数
| 名称 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` / `pageSize` | number | 否 | 1 / 20 | 页码 / 每页条数（上限 **50**） |
| `status` | string | 否 | — | `pending` 待处置 / `handled` 已处置；**不传 = 全部**；非法值 `400` |
| `type` | string | 否 | — | `field` / `gone`；**不传 = 全部**（管理端据此分 Tab） |
| `dishId` | number | 否 | — | 按目标菜品筛选（从菜品视角看反馈） |

**排序**：`createdAt DESC`。

> `status` **以处置任务表为判据**；`handled` 的两种结论由出参 `outcome` 区分（`handled` 已受理 / `rejected` 不采纳）。

### 响应 `data` = `AdminPageResult<CorrectionAdminVO>`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 反馈 ID |
| `type` | string | `field` / `gone` |
| `dishId` / `dishName` | number / string | 目标菜品 ID / 名称（**实时回查，含已下架**；菜品已物理删除为 `null`） |
| `userId` / `userName` | number / string | 提交人（**恒为真实登录用户**：`userId` = `user.id`；注销后为「已注销用户」） |
| `name` / `price` / `canteenName` / `stallName` / `floor` | string / number / … | **提交的改动项快照**（未改动为 `null`；`price` 单位分；`gone` 型恒全 `null`） |
| `attributes` | object \| null | 提交的描述属性（**仅改动的维度**；键 = 维度 ID，值为用户提交的中文） |
| `images` | string[] | 提交的图片（`field` = 改动后的图片；`gone` = 选填补充；≤3 张） |
| `note` | string \| null | **补充说明**（≤200 字；仅 `gone` 型的选填补充） |
| `status` | string | `pending` / `handled` |
| `outcome` | string \| null | 处置结论：`handled` 已受理（管理员已手动处理）/ `rejected` 不采纳；**未处置为 `null`** |
| `replyTitle` | string \| null | 处置回执短标题（未处置为 `null`） |
| `replyContent` | string \| null | 处置文案 / 不采纳原因（未处置为 `null`；单一列） |
| `handledByName` | string \| null | 处置人登录名（未处置 / 账号已删除为 `null`） |
| `createdAt` | string | 提交时间（`yyyy-MM-dd HH:mm:ss`） |
| `handledAt` | string \| null | 处置时间（未处置为 `null`） |

> 列表**不展开各提交项**（那是详情的事）：列表给「谁、对哪道菜、什么类型、什么状态、什么时候」。

---

## GET /admin/corrections/{id}

**用途**：**单条详情（含提交项对照）**

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 反馈 ID |

### 响应 `data` = `CorrectionDetailVO`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` / `type` / `dishId` / `dishName` / `userId` / `userName` | — | 同列表 |
| `dishStatus` | string \| null | 目标菜品**当前**上下架状态（`on` / `off`，实时回查；菜品已物理删除为 `null`） |
| `differences` | object[] | **提交项对照**（**只列用户提交过的项**，见下）；`type=gone` 恒为空数组 |
| `goneUserCount` | number \| null | **N 人反馈已下架**（同用户对同菜品已去重）；**仅 `type=gone` 下发，仅作参考、不作阈值** |
| `note` / `images` | — | 同列表（`gone` 型的处置判读依据） |
| `status` / `outcome` / `replyTitle` / `replyContent` / `handledByName` / `createdAt` / `handledAt` | — | 同列表 |

**`differences[]` 结构**（**提交项对照**，只列「用户提交过的项」）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `field` | string | 对照项键：`name` / `price` / `canteenName` / `stallName` / `floor` / `images` / `attributes.<维度ID>`（**仅作展示键**） |
| `label` | string | 中文名（如「菜品名称」「饮食属性」），端上直接渲染 |
| `oldValue` | any | **当前菜品 / 档口的实时值**（菜品已物理删除为 `null`） |
| `newValue` | any | 用户提交的值 |
| `same` | boolean | 提交值是否与当前值**一致**（`true` = 已一致，端上据此标注「已一致 / 有差异」） |

> **只列用户提交过的项**：`field` 型只提交改动项 ⇒ 对照即「用户说了什么 vs 现在是什么」，供管理员阅读判断。
> **`oldValue` 取实时值**：快照只存「提交的新值」（未改动列 NULL），原值必须联表回查 `dish` / `stall`。
> **`gone` 型不返回对照**（`differences` 恒空）：无可对照的原值，处置只依赖 `note` + `images` + 菜品当前状态。
> ⚠️ **菜品已被物理删除**时（`correction` 不设外键，记录仍在）：`oldValue` 各项为 `null`，无从对照。

---

## POST /admin/corrections/{id}/handle

**用途**：**标记已处理**（接受反馈）—— 只归档处置结论 + 投递站内回执，**不改写菜品**

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 反馈 ID |

### 请求体 `DishCorrectionHandleReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `replyTitle` | string | 否 | 回执短标题（≤40 字）；缺省「已收到你的反馈」 |
| `replyContent` | string | 否 | **处理附注**（≤600 字），随回执下发；缺省「感谢反馈，我们已核实处理」 |

### 响应 `data`
`null`。

**处置动作**（同一事务）：更新 `moderation_task` 行（`status='handled'` / `outcome='handled'` / `reply_title` / `reply_content` / `handled_by` / `handled_at`）+ 投递站内回执。

> **已处置再处理 → `400`「该反馈已处理」**（防结论被静默改写 + 回执重复投递）。
> **菜品资料修改 / `gone` 可逆下架不在本端点**：管理员核实后到 [web/dishes.md](./dishes.md) 手动执行，再回来标记已处理。

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
| `400` | `outcome` 非 `rejected`（驳回端点）；**驳回时 `replyContent` 缺失 / 纯空白**；`replyTitle` >40 字；`replyContent` >600 字；`status` / `type` 非法值；**已处置再处理**（「该反馈已处理」） |
| `4001` | 反馈不存在 |
| `401` | 未带 / token 失效 |

### 备注
- **处置只归档、不改写菜品**：`标记已处理` / `驳回` 都只写 `moderation_task` + 投递回执，**绝不写 `dish` / `stall` / 提交行** —— 「用户说了什么」与「菜品最终是什么」解耦，避免自动写回带来的误操作与跨域复杂度。菜品资料与上下架统一回到 [web/dishes.md](./dishes.md) 手动执行。
- **对照只列「用户提交过的项」**：`field` 型只提交改动项，故 `differences[]` 逐项给「当前值（实时回查）/ 提交值 + `same` 是否一致」。
- **楼层归属档口**：`floor` 提交值仅作记录，实际楼层由管理员在 [web/stalls.md](./stalls.md) 手动修改**目标档口**的 `stall.floor`（改动会连带该档口所有菜品）。
- **`outcome` 是任务表的物理列**：`handled`（已受理，管理员已手动处理）/ `rejected`（不采纳）可从行本身判定；**文案只有一列**（`replyContent`），不采纳原因并入其中。
- **回执文案按 `type` 与结论分流**：标记已处理（`field` / `gone` 通用）缺省「已收到你的反馈 / 感谢反馈，我们已核实处理」；驳回按类型分「菜品信息未采纳」/「菜品仍在售」。投递判据 = 提交人 `userId`（提交恒带登录态 ⇒ **恒投递**），投递与处置同一事务。
- **不做批量处理**（一期不做，留作演进）。
