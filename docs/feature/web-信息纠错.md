# 信息纠错处理（B-07）

> 所属板块：**web（管理后台）** ｜ 鉴权：**🔑 口令**（`X-Admin-Token`）
> 返回：[功能总览](./README.md)

## 介绍

处理学生提交的**菜品信息纠错**（小程序「我要更新信息」模式，`POST /dishes/{id}/correction` 七字段结构化快照）的运营闭环。进页默认停在「待处理」列表；列表按状态服务端筛选；处理抽屉呈现**字段级对比卡**——原菜品 vs 用户提交七字段，有差异字段高亮。

处理动作两路：

- **一键采纳**（主按钮、二次确认）= 服务端把提交七字段写回目标菜品并置 `adopted`（回复固定「已采纳，菜品信息已更新」）；写回前经**两段式档口确认**——服务端按解析优先级定位档口（带 `stallId` ＞ `stallName` trim 精确匹配 ＞ `createIfMissing=true` 按名新建 `upsertStallByName`），均未命中时返回候选列表，管理员选定候选或确认新建后完成采纳。→ 已认证提交人收「菜品信息更新」站内回执。
- **不采纳**：进入子流程（回复 + 不采纳原因必填，`outcome` 固定 `rejected`）。

回执经站内通知（`correction_handle`，标题「菜品信息更新」）投递已认证提交人；游客不投递。

## UI

> 📐 页面 UI 设计稿已拆出 → [web-信息纠错.md（docs/ui）](../ui/web-信息纠错.md)（**UI 口径以该文件为唯一真源**）

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/corrections` | 🔑 | 纠错列表（分页；`status` 筛选） |
| POST | `/admin/corrections/{id}/adopt` | 🔑 | **一键采纳**（仅 `pending`；两段式档口确认，七字段写回目标菜品 → `adopted`） |
| PUT | `/admin/corrections/{id}` | 🔑 | 不采纳（回复 + 不采纳原因必填，`outcome` 固定 `rejected`） |

## 字段

### 请求 · `GET /admin/corrections`（query）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `status` | string | 否 | 处理状态：`pending`=待处理 / `adopted`=已采纳 / `rejected`=已拒绝；不传 = 全部；**非法值 400** |
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 10（上限 100） |

### 响应 · `GET /admin/corrections`（`data` = `PageResult<CorrectionAdminVO>`）

**分页壳字段**：`records` / `total`（口径见 [README 通用结构](./README.md)）。

### 响应 · `CorrectionAdminVO` 单行字段

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 纠错 ID |
| `dishId` | number | 目标菜品 ID |
| `dishName` | string \| null | **目标菜品名**：服务端按 `dish_id` **实时回查** `dish` 补齐（**含已下架菜品**；菜品可能已被改名）；菜品已物理删除 → null（页面退回「菜品#id」占位） |
| `userId` | number \| null | 提交人用户 ID；**游客提交为 null**（页面按 userId 显示「用户#id」/「游客」，不消费服务端昵称 enrich） |
| `snapshot` | object | **用户提交的七字段快照**（见下表）；`price` 已由接口层分→元收口 |
| `status` | string | 处理状态：`pending`=待处理 / `adopted`=已采纳 / `rejected`=已拒绝 |
| `reply` | string \| null | 管理员回复内容（学生收到的回执正文）；一键采纳固定「已采纳，菜品信息已更新」 |
| `rejectReason` | string \| null | 不采纳原因（`rejected` 时非空，1~200 字） |
| `createdAt` | string | 提交时间 |
| `handledAt` | string \| null | 处理时间 |

`snapshot`（`CorrectionSnapshot`，七字段结构化快照）：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `name` | string | 提交的菜品名称（≤64 字） |
| `price` | number | 提交的现价，**单位「元」**（接口层已由分→元） |
| `canteenName` | string \| null | 提交的食堂名称（文本） |
| `stallName` | string \| null | 提交的档口名称（文本） |
| `flavorTags` | string[] | 提交的口味标签机器值数组（恒为数组，空提交为空数组） |
| `ingredients` | string[] | 提交的食材机器值数组（恒为数组） |
| `images` | string[] | 提交的菜品图片 URL 数组（COS **绝对地址**，≤9 张，空提交为空数组） |

### 请求 · `POST /admin/corrections/{id}/adopt`

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `id` | number | **是** | 纠错 ID（路径参数）；前置：`status=pending`，否则 400（重复调用 → 400「该纠错已处理」） |
| `stallId` | number | 否 | 指定档口 ID（第二段从候选中选定后回传） |
| `createIfMissing` | boolean | 否 | 确认按提交 `stallName` 新建档口（走 `upsertStallByName`） |

**档口解析优先级**：带 `stallId` ＞ `stallName` trim 精确匹配 ＞ `createIfMissing=true` 走 `upsertStallByName` 按名新建。**均未命中 → HTTP 200、不执行采纳**，返回：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `needStallConfirm` | boolean | 恒 `true`，端上据此打开**两段式档口确认对话框** |
| `candidates` | {id, name}[] | 候选档口：提交食堂命中时 = 该食堂下档口，否则 = 全量档口 |

采纳动作：提交七字段（`snapshot` 内的 `name` / `price` / `canteenName` / `stallName` / `flavorTags` / `ingredients` / `images`）写回目标菜品，**可空快照字段不覆盖既有值**（提交为空的字段保持菜品原值）→ `status=adopted`、`reply=「已采纳，菜品信息已更新」`、`handled_at=now` → 已认证提交人收「菜品信息更新」回执。不采纳沿用 `PUT /admin/corrections/{id}` 链路。

### 请求 · `PUT /admin/corrections/{id}`（不采纳）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `id` | number | **是** | 纠错 ID（路径参数）；前置：`status=pending`，否则 400 |
| `reply` | string | **是** | 管理员回复内容，1~1000 字；**纯空白视为未填写 → 400** |
| `rejectReason` | string | **是** | 不采纳原因，1~200 字（纯空白 → 400「请填写不采纳原因」） |
| `outcome` | string | — | **固定 `rejected`**（服务端固定，不消费入参） |

### 响应 · 三端点

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `data` | null / 对象 | `adopt` 档口未决时为 `{needStallConfirm, candidates}`；其余成功场景为 null |

### 错误码

| code | 含义 | 中文解释 |
|---|---|---|
| 400 | 回复 / 原因为空或超长；status 非法；纠错不存在；重复处理（已 adopted / rejected）；`createIfMissing` 缺少 `stallName` 等档口参数非法 | 参数与业务校验 |
| 4001 | 采纳时目标菜品已不存在 | 资源不存在 |
| 403 | 口令缺失或错误 | `X-Admin-Token` 校验失败 |

## 数据（落库）

| 表 / 列 | 变化 | 中文解释 |
|---|---|---|
| `dish_correction.status` | `pending` → `adopted` / `rejected` | 处理状态（终态唯一，无中途态） |
| `dish_correction.reply` | 写入 | 管理员回复（回执正文）；一键采纳固定「已采纳，菜品信息已更新」 |
| `dish_correction.reject_reason` | 写入 / 保持 NULL | 不采纳原因；`adopted` 时恒 NULL |
| `dish_correction.handled_at` | 写入当前时间 | 处理时间 |
| `dish` | UPDATE（**仅一键采纳**） | 提交七字段写回（`name` / `price`（分）/ `canteen_name` / `stall_name` / `flavor_tags` / `ingredients` / `images`；档口经两段式解析定位，空快照字段保持原值）；不改变上下架状态 |
| `stall` | INSERT（仅 `createIfMissing` 采纳） | `upsertStallByName` 按提交档口名新建（归属提交食堂） |
| `notification` | INSERT（异步） | 向**已认证**提交人投递回执：`correction_handle`、标题「菜品信息更新」（**采纳 / 拒绝均投递**，含回复；拒绝时含不采纳原因）；游客不投递、不阻塞 |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

**无（文档与代码一致）**
