# 菜品详情（A-04）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免登录）
> 返回：[功能总览](./README.md)

> 口径说明：本文档为该功能的最终设计说明；与现有代码的差异见文末「与当前代码的差异」。

## 介绍

看一道菜的**完整信息**（图、价、原价删除线、描述、荤素 / 主料 / 口味 / 冷热、食堂 · 楼层 · 档口名）与**全部可见评价**（含评分分布）。评价区**按时间倒序展示（新评价在前，唯一排序）**，支持「只看有图」筛选；支持**重新评价**——旧评价作废、留下最新，新评价时间取当前、排到最前。评价分页加载的结束判据 = **已加载条数 ≥ `total`**（末页恰好满页时避免多发一次空请求）；切换「只看有图」时**重置 `page=1` 并清空列表**再拉取。

## UI

> 📐 页面 UI 设计稿已拆出 → [client-菜品详情.md（docs/ui）](../ui/client-菜品详情.md)（**UI 口径以该文件为唯一真源**）

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/dishes/{id}` | 🔓 公开 | 菜品详情。**登录态与游客态返回结构完全一致**（无用户态分支） |
| GET | `/dishes/{id}/reviews` | 🔓 公开 | **评价列表（菜品子资源）**：**唯一排序 = 时间倒序**；支持「只看有图」筛选；仅返回未隐藏评价 |

## 字段

### 请求 · `GET /dishes/{id}`

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `id` | number | **是** | 菜品 ID（路径参数，如 `/dishes/1`） |

### 响应 · `GET /dishes/{id}`（`data` = `DishDetailVO`）

`DishDetailVO`（**详情专用**，**共 16 字段** = 15 基础字段 + `ratingDistribution` 评分分布）。

> **列表 / 详情出参拆分**：`GET /dishes` 列表返回**列表专用 8 字段** `DishListItemVO`（`id` / `name` / `coverImage` / `price` / `originalPrice` / `avgRating` / `canteenName` / `stallName`）；下表 15 字段即**详情专属字段集**，由 `GET /dishes/{id}` 独占下发。

`DishDetailVO` 单行字段（15 个；不含坐标字段，见「位置与距离口径」）：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 菜品 ID |
| `name` | string | 菜品名称 |
| `price` | number | **现价**（单位：分；端上转「元」展示，已含折扣） |
| `originalPrice` | number \| null | **原价**（单位：分，折扣前）；`originalPrice > price` 即表示有折扣、端上在原价上加删除线；无折扣为 null |
| `description` | string | 菜品描述 |
| `images` | string[] | 菜品图片 URL 数组（首图作封面；无图为空数组） |
| `stallName` | string | 档口名称 |
| `canteenName` | string | 食堂名称 |
| `floor` | string | 档口所在楼层（如 1F / 2F） |
| `avgRating` | number \| null | 平均评分（**详情页为实时聚合值**，口径 = 仅未隐藏评价）；**该菜品零评价时为 `null`**（端上按「暂无评分」呈现）—— 见下文「可空性约定（R12）」 |
| `ratingCount` | number | 评价数（同上口径） |
| `dietType` | string | 荤素 / 饮食属性，**下发机器值**（`meat` / `half` / `veg` / `halal`），展示中文由**后端字典**提供（见 §6 R4） |
| `ingredients` | string[] | 主料 / 食材，**机器值数组**（如 `["chicken","rice"]`），展示中文由**后端字典**提供 |
| `flavorTags` | string[] | 口味，**机器值数组**（如 `["spicy","sour"]`），展示中文由**后端字典**提供 |
| `serveTemp` | string | 冷热，**下发机器值**（`hot` / `room` / `ice`），展示中文由**后端字典**提供 |

> **R4**：四维**下发机器值**（保留筛选 / 统计锚点），**展示中文由后端只读字典端点 `GET /dishes/attributes` 下发**（真源 `DishAttributeConst`，模式对齐 `GET /dishes/meal-types`，见 `project_spec.md` §7.34）——client 与 web **共用同一份字典**，端上**不维护硬编码映射表**；字典须同时服务 client 展示与 web 表单选项（下拉 / chips 必须有选项列表），故中文由后端统一提供（见 `project_spec.md` §7.40 第 4 项与 PR-12 的「全端盘点」机制）。
> **多值数组化**：`ingredients` / `flavorTags` 出参为 `string[]`（落库为 JSON 数组，幂等迁移 `migrate_dish_multivalue_json`），消除两端 CSV 解析。
> 英文机器值与中文的完整对应关系见 `project_spec.md` §7.28（**值域真源**，映射载体为字典端点）。
> **字典项 `field` 命名约束（R13）**：字典项的 `field` 取值 **MUST 等于本 VO 的字段名**（`dietType` / `ingredients` / `flavorTags` / `serveTemp`）—— 端上 / 管理端据此**直接匹配**渲染，**SHALL NOT** 另建「字典 `field` → VO 字段」的第二套映射。
> **持久层形态**：`images` / `ingredients` / `flavorTags` 由 `StringListTypeHandler` 在持久层直出 `List<String>`（无 JSON 原文中转出参）；绝对 URL 转换在 Service 层完成。

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `ratingDistribution` | object[] | 评分分布数组，固定 5 项（每星级一条），用于画 5 星柱状条；口径 = 仅未隐藏评价 |

`ratingDistribution` 数组项（`RatingDistributionVO`）：`star`（星级 1~5）、`count`（该星级评价条数）。

> **数组顺序（R11）**：SHALL **按 `star` 降序（5 → 1）** 下发 —— 与页面展示顺序**一致**；端上**直接按序渲染，SHALL NOT 自行排序**。
> **可空性**：本字段**恒在**（不为 `null`）；无任何评价时为 5 条 `count: 0` → 端上按「合计为 0」决定不渲染评分卡（**不靠字段缺失判断**）。

> **「我是否已评价」不在本 VO 出参中，详情页首屏也不做该判定** —— 判定时机 = 用户**点击「写评价」时**（表单打开前调「我的评价（按菜过滤）」）：已评价打开预填旧值的重评弹层，未评价打开空表单。首屏对游客与登录用户**完全一致、零用户态请求**；`updatedAt` 不出参。
> 价格展示只消费 `price`（现价）与 `originalPrice`（原价），折扣由 `originalPrice > price` 表达，**无独立「折扣价」字段**。
> **口径归属登记**：`originalPrice > price` 是**端上算的业务判定**（PR-02「同一业务口径的权威方固定为后端」的例外口径）—— 折扣以两态表达，判据极简且无歧义。
> **位置与距离口径**：服务端不出参坐标、端上不算距离、不申请定位权限（manifest 不声明 `scope.userLocation`）；位置表达仅用 **食堂名 · 楼层 · 档口名**（`canteenName` / `floor` / `stallName`）。
> **可空性约定（R12）**：本 VO 的 `null` 边界**共两处** ——
> ① **`originalPrice`**：无折扣时为 `null`（端上判 `originalPrice > price` 决定划线）；
> ② **`avgRating`**：该菜品**评价数为 0** 时后端返回 `null`（`DishServiceImpl.applyRatingSummaryFromDistribution` 在 `total == 0` 时置 `null`，端上以 `?? 0` 兜底并呈现「暂无评分」）。
> 其余字段的边界：`images` / `ratingDistribution` **恒为数组**（无图 / 无评价时为空数组或 5 条 `count: 0`，**不为 `null`**）；`description` / `floor` / `stallName` / `canteenName` 由后端以**空串**兜底（`DishMapper.xml` 的 `COALESCE`），端上可去掉 `|| ''` 兜底。

### 请求 · `GET /dishes/{id}/reviews`

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `id` | number | **是** | 菜品 ID（**路径参数**，RESTful 子资源，如 `/dishes/1/reviews`） |
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 20（**详情页固定传 10**） |
| `hasImage` | number | 否 | **「只看有图」筛选**：`1` = 只看有图 / `0` 或缺省 = 不限。**语义 = 仅返回配图非空的评价**（`total` 按该筛选口径统计）。**端上切换筛选时必须重置 `page=1` 并清空列表** |

> **实现注（R12）**：`hasImage` 的存储层判定基于 JSON 语义（`JSON_VALID` + `JSON_LENGTH`）——属实现细节；**契约语义（「配图非空」）不变**。

### 响应 · `GET /dishes/{id}/reviews`（`data` = `PageResult<ReviewVO>`）

> **公开列表出参 = `ReviewVO` 8 字段**：不含 `dishId`（恒等于路径）/ `dishName`（联表冗余）——两者仅「我的评价」视角需要；公开列表恒为未隐藏评价，不下发 `isHidden`。

**分页壳字段**：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | ReviewVO[] | 当前页评价行数组（端上以它为准） |
| `total` | number | 该菜品可见评价总条数 |
| `page` | number | 服务端归一化后的实际页码 |
| `pageSize` | number | 服务端归一化后的实际每页条数 |

> **分页壳恒为 `records` / `total` / `page` / `pageSize` 四项**，SHALL NOT 输出 `list` 或任何与 `records` 恒等派生的兼容字段（见 `pagination-contract`）。
> **`page` / `pageSize` 的归一化规则（R11）**：回传的是经 `PageUtil.normalize` 归一化后的**实际生效值**（`page < 1 → 1`；`pageSize < 1 → 10`；**`pageSize > 100 → 100`**），且取的是 `IPage.getCurrent()` / `getSize()` 而**非 Controller 原始入参**。**这正是这两个字段存在的意义** —— 入参越界 / 超限时，端上只能从这里得知服务端**真正用了什么**（否则它们才是冗余字段）。

`ReviewVO` 单行字段（**公开视角，8 字段**）：

> **双视角命名（R9）**：本表为 **`ReviewVO`（公开视角，8 字段）**。`GET /my/reviews` 返回的是**本人视角、10 字段**（本表 8 字段 + `dishId` / `dishName`）—— **两视角 MUST 是两个类型**（`ReviewVO` / `MyReviewVO`），端上 **SHALL NOT** 用单一 interface 复用。

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 评价 ID |
| `userId` | number | 评价者用户 ID（端上据此判定是否本人，决定「删除 / 举报」入口的**显隐**）。**（R7：鉴权仍由服务端把关「非本人 → 403」；ID 非敏感。此为有意的双重判定权衡，非冗余，见文末 R14）** |
| `userNickname` | string | 评价者昵称（账号注销后为「已注销用户」） |
| `userAvatar` | string | 评价者头像 URL |
| `rating` | number | 评分（1~5 星） |
| `content` | string | 评价文字内容 |
| `images` | string[] | 评价配图 URL 数组（COS 绝对地址，≤3 张，无图空数组） |
| `createdAt` | string | 评价发表时间（**重新评价后取新时间**）。**端上 SHALL NOT 改名**，统一用 `createdAt` |

### 异常分支 · `GET /dishes/{id}`（R8）

| 场景 | 响应 | 端上处置 |
|---|---|---|
| `id` 不存在（含已下架） | **`code = 4001`** + `message = "菜品不存在"` | 展示「菜品不存在」文案 + **返回首页**的恢复路径 |
| **菜品已下架**（`status = 'off'`） | **同「不存在」**：`code = 4001` + `message = "菜品不存在"` | 同上（**下架对外等价于不存在**） |
| `id` 非法（非数字） | `400`（参数绑定失败） | 按「不存在」处理即可 |
| 网络 / 服务端故障 | 传输层错误或 `5xx` | 展示**可重试**的失败态（与「不存在」**区别对待**） |

> **`4001` = 资源不存在（通用细分码）**：与 `4031`（邮箱未认证）**同源的细分码思路** —— 让端上**不必解析 `message` 文本**即可给出差异化引导（「不存在」→ 恢复路径；「参数非法」→ 提示）。
> **下架口径**：公开接口恒只返回在售（见 §7.27）→ 「下架」对外**等价于不存在**，不设专用码、**不新增字段**（`DishDetailVO` 仍为 16 字段）。

## 数据（读取 / 落库）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `dish` | 读全字段 | 菜品主体信息 |
| `stall` / `canteen` | 读名称、楼层 | 归属位置（食堂 / 楼层 / 档口名）；**不读坐标、不算距离**（见「位置与距离口径」） |
| `review` | 读（过滤 `is_hidden=0`）；「重新评价」会 UPDATE 同一行（`created_at` 刷新为新时间） | 可见评价列表 |
| `view_log` | 写 | **访问日志**（append-only）：`GET /dishes/{id}` 每次成功响应写入一行，供时间窗口聚合（口径见 [client-浏览计数](./client-浏览计数.md)） |

## 设计决议

**「我是否已评价」的判定时机：用户点击「写评价」时（表单打开前）。**

- **详情页首屏零用户态请求**：仅「详情 + 公开评价」并行，游客与登录行为完全一致；「我的评价」数据只在**我的评价**一处下发（单一真源）——详情 VO 不含用户态字段，评价列表响应**不**外挂 `myReview`（分页壳恒为 `records`/`total`/`page`/`pageSize` 四项，不挂业务字段）；
- **判定动作**：已认证用户点击「写评价」→ 端上先调 `GET /my/reviews?dishId=` → **已评价**则以重评模式打开弹层（预填本人旧值 + 明示「本次提交将覆盖原评价」，提交走 `PUT /reviews/{id}`）；**未评价**打开空表单。判定失败静默按未评价处理（此时提交若命中唯一键冲突 → 400「您已评价过该菜品」提示兜底）；
- **底栏文案为会话内双态**：由端上本地「我的评价」态驱动——初始「写评价」；判定为已评价或提交成功后（本地写回，POST 出参返回新评价 ID）就地切「重新评价」；
- **评价作者标识 `userId`**：公开评价出参携带，端上据此决定评价卡「删除 / 举报」入口显隐（服务端另有「非本人 → 403」鉴权）——**保留 `userId`，不引入 `isMine`**：`isMine` 须由服务端按请求者计算，会让公开评价列表变为「半用户态」；本人视角下恒真属零信息冗余。**有意的双重判定权衡。**

**R14（`userId` vs `isMine`）：采用 `userId`，不设 `isMine`。**

端上持有「当前用户 id」并与 `userId` 比对，决定「删除 / 举报」入口显隐（服务端另有「非本人 → 403」鉴权）：

1. `isMine` 须由服务端**按请求者**计算 → 会让**公开评价列表变为「半用户态」**（需解析可选 token），与 **R3**（保持详情与公开列表的「纯公开」属性、缓存 / CDN 友好）**直接矛盾**；
2. 在本人视角（`GET /my/reviews`）中 `isMine` **恒为 `true`** —— 恒真字段，属零信息冗余；
3. `userId` 是公开列表**本就该下发**的作者标识（头像 / 昵称归属所需），端上做一次比对不引入新真源。

详见 `project_spec.md` §7.40 第 14 条。

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

**无（文档与代码一致）**
