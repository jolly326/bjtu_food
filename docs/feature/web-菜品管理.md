# 菜品管理（B-01）

> 所属板块：**web（管理后台）** ｜ 鉴权：**🔑 口令**（请求头 `X-Admin-Token` == 环境变量 `ADMIN_TOKEN`，未配置即 fail-closed 403）
> 返回：[功能总览](./README.md)

## 干什么

菜品的**增删改查与上下架**——全站**菜品信息的唯一录入源**（学生端无菜品写接口）。新增 / 编辑时若填了字典里还不存在的食堂、档口名，**自动按名建档**（同名不重复建档）。

## UI

- 导航「菜品」→ `/dashboard/content`（**管理端默认落地页**）。
- 页头：H1「菜品」+ [新增菜品] 按钮。
- 筛选条：食堂 / 价格 / 状态 + 关键词搜索（**当前为前端本地过滤**，见答疑）。
- 表格列：名称 / 位置（食堂·档口）/ 价格 / 评分 / 状态（行内 switch 上下架）。
- 弹窗：`DishFormDialog`（新增与编辑共用）——菜名、价格、原价（有值且高于现价即端上划线）、描述、图片（多图）、荤素、主料、口味、冷热、食堂·档口。

## 操作

1. 点 [新增菜品] → 填表单 → 保存（**录入即生效**，无审核环节）。
2. 点表格行 → 进菜品详情（[菜品详情查看](./web-菜品详情查看.md)）。
3. 行内 switch → 上架 / 下架（即时生效；下架后客户端完全不可见，评价保留）。
4. 编辑 → 同一弹窗，支持**部分更新**（只改其中几个字段）。
5. 删除 → 二次确认（提示「所选菜品下的 N 条评价将一并删除、不可恢复」）。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/dishes` | 🔑 | 菜品列表（分页，**含已下架**） |
| POST | `/admin/dishes` | 🔑 | 新增菜品（录入即生效；食堂 / 档口按名 upsert） |
| PUT | `/admin/dishes/{id}` | 🔑 | 编辑菜品（**部分更新**，未传字段不修改） |
| DELETE | `/admin/dishes/{id}` | 🔑 | 删除菜品（**级联删该菜评价 + 评价「有用」记录**） |

## 字段

### 请求 · `GET /admin/dishes`（query）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 20（服务端上限 100） |

> ⚠️ **当前只有这两个参数**：没有关键词 / 状态 / 食堂 等筛选参数，页面上那些筛选均为**前端本地过滤**（见答疑）。

### 响应 · `GET /admin/dishes`（`data` = `PageResult<DishAdminVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | DishAdminVO[] | 当前页菜品行数组 |
| `total` | number | 菜品总条数 |
| `page` | number | 实际生效页码（归一化后） |
| `pageSize` | number | 实际生效每页条数（归一化后） |
| `list` | DishAdminVO[] | 过渡期兼容字段，恒等于 `records` |

### 响应 · `DishAdminVO` 单行字段

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 菜品 ID |
| `stallId` | number | 所属档口 ID |
| `name` | string | 菜品名称 |
| `alias` | string \| null | **搜索别名**（逗号分隔，管理员配置；搜索时与菜名同权命中） |
| `price` | number | 现价，**单位：分** |
| `originalPrice` | number \| null | 原价（折扣前），单位分；**`originalPrice > price` 即视为有折扣**（2026-09-18 §7.26，`promoPrice` 已删除） |
| `description` | string | 菜品描述 |
| `images` | string[] | 菜品图片 URL 数组（首图作封面） |
| `status` | string | 上架状态：`on`=上架 / `off`=下架 |
| `avgRating` | number | 平均评分（缓存值，口径 = 仅未隐藏评价） |
| `ratingCount` | number | 评价数（同口径） |
| `createdAt` | string | 创建时间 |
| `updatedAt` | string | 更新时间（判断信息新鲜度） |
| `stallName` | string | 档口名称（联表） |
| `canteenName` | string | 食堂名称（联表） |
| `dietType` | string | 荤素 / 饮食属性：`meat`=荤 / `half`=半荤 / `veg`=素 / `halal`=清真 |
| `ingredients` | string | 主料 / 食材（逗号分隔**英文机器值**，管理端展示层映射中文）：`pork` 猪 / `beef` 牛 / `lamb` 羊 / `chicken` 鸡 / `duck` 鸭 / `fish` 鱼虾 / `egg` 蛋 / `tofu` 豆制品 / `mushroom` 菌菇 / `veg` 青菜 / `noodle` 面 / `rice` 米（见 `project_spec.md` §7.28） |
| `flavorTags` | string | 口味（逗号分隔**英文机器值**）：`spicy` 辣 / `numbing` 麻 / `sour` 酸 / `sweet` 甜 / `salty` 咸 / `umami` 鲜 / `light` 清淡 / `heavy` 重口 |
| `serveTemp` | string | 冷热：`hot`=热食 / `room`=常温 / `ice`=冰 |

> **无审核态字段**（菜品无独立审核，`audit_status` 列已退役）；`imagesJson`（图片原文）为内部字段，不出参。**`viewCount`（浏览量）已删出参（2026-09-18 §7.27）**——仅作热度排序服务，Web 无展示消费，`api/adapter.ts` 的 `view_count` 映射同步删。

### 请求 · `POST /admin/dishes` 与 `PUT /admin/dishes/{id}`（`DishAdminReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `stallId` | number | 否 | 所属档口 ID（选择字典里已有的档口时用）；与下面两个名称都缺失且解析不出来 → 400「请指定所属档口」 |
| `stallName` | string | 否 | **档口名（按名 upsert）**：字典有同名 → 复用其 ID；没有 → **自动建档**；「其他」等空值语义名称不建档 |
| `canteenName` | string | 否 | **食堂名（按名 upsert）**：**仅当 `stallName` 触发新建档口时才消费**，作为新档口的所属食堂；不传则新档口不挂食堂 |
| `name` | string | 新增必填 | 菜品名称 |
| `alias` | string | 否 | 搜索别名，中英文逗号分隔，总长 ≤255（超限 400）；后端保存前 trim、去空项、去重。**`null`=不修改；空串=清空别名** |
| `price` | number | 新增必填 | 现价，**单位：分**（12 元传 1200） |
| `originalPrice` | number | 否 | 原价（折扣前），单位分；`originalPrice > price` 即端上原价划线（`promoPrice` 已删除，2026-09-18 §7.26） |
| `description` | string | 否 | 菜品描述 |
| `images` | string[] | 否 | 菜品图片 URL 数组（单图放一个元素） |
| `dietType` | string | 否 | 荤素 / 饮食属性：`meat` / `half` / `veg` / `halal` |
| `ingredients` | string | 否 | 主料 / 食材（逗号分隔**英文机器值**，如 `pork,egg`） |
| `flavorTags` | string | 否 | 口味（逗号分隔**英文机器值**，如 `spicy,umami`） |
| `serveTemp` | string | 否 | 冷热：`hot` / `room` / `ice` |
| `status` | string | 否 | 状态：`on`=上架 / `off`=下架（**行内 switch 只传这一个字段**，依赖部分更新能力） |

> 名称类入参须 trim 后非空且长度 ≤64，非法 → 400。

### 响应 · `POST` / `PUT` / `DELETE`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `data` | null | 无载荷；成功即 `code=200` |

### 错误码

| code | 含义 | 中文解释 |
|---|---|---|
| 400 | 档口不存在 / 请指定所属档口 / alias 超长 / 名称非法 | 参数与业务校验失败 |
| 403 | 口令缺失或错误 | `X-Admin-Token` 校验失败（fail-closed） |

## 数据（落库）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `dish` | INSERT / UPDATE / DELETE | 菜品全字段；删除时级联清该菜评价 |
| `canteen` | 按名 upsert | 传了字典中不存在的食堂名 → 自动建档（`sort_order` 默认 0、`created_by` 置空） |
| `stall` | 按名 upsert | 传了不存在的档口名 → 自动建档（带所属食堂 `canteen_id`） |
| `review` / `review_useful` | 级联 DELETE | 删除菜品时一并清理其评价与「有用」记录 |

## 答疑

### Q：展示菜品的基本信息，并能够快速供我找到对应菜品

**A：拆成两条答，现状 + 缺口。**

**① 基本信息展示：够用，可小幅增强。**
表格现为 名称 / 位置（食堂·档口）/ 价格 / 评分 / 状态 + 行内上下架 switch；出参 `DishAdminVO` 共 20 项（见上「字段」）。建议增强（非必须）：菜品图缩略图列、荤素 / 口味列、`updatedAt` 列（判断信息新鲜度）。
**注意跨端红线**：Web 只经 `/admin/**` 取数——`api/dish.ts` 里调用公开端点的 `getById()` 封装已按 DEV-04 移除，**勿重建**。

**② 快速找到：这是真缺口。**
已核实后端 `GET /admin/dishes` **只接 `page`/`pageSize`，没有任何筛选/搜索参数**；而 Web 的筛选条（状态 / 折扣 / 食堂 / 档口 / 关键词）**全是前端本地过滤**，数据靠 `getAll()` 循环翻页把全量拉到 store（`pageSize=100`）。菜品量上千后这是明确性能债；且关键词只匹配 `name`（`toLowerCase().includes`），**不命中 `alias`**，与客户端搜索口径不一致。

**建议按此优先级改（a + b 建议合成一个后端 task）**：
- a) `GET /admin/dishes` 增加 `keyword`（匹配 name / alias）+ `canteenId` / `stallId` + `status` 查询参数，与公开 `DishQueryReq` 同构、复用既有 Mapper 条件，Web 切回**服务端分页筛选**；
- b) 列表默认按 `updatedAt` / `createdAt` 倒序（刚录入的菜在最上面，贴合「录完要核对」的真实工作流）；
- c) 关键词纳入 `alias`（与端上搜索同口径）；
- d) 可选：状态默认「全部」但下架项沉底，便于回看刚下架的菜。

**a + b 属「既有端点新增查询参数」（非新增端点、非库表变更），可由技术负责人直接登记，不需要重新拍板**——你点头我就拆任务。
