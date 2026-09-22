# 菜品管理（B-01）

> 所属板块：**web（管理后台）** ｜ 鉴权：**🔑 口令**（请求头 `X-Admin-Token` == 环境变量 `ADMIN_TOKEN`，未配置即 fail-closed 403）
> 返回：[功能总览](./README.md)

## 干什么

菜品的**增删改查与上下架**——全站**菜品信息的唯一录入源**（学生端无菜品写接口）。新增 / 编辑时若填了字典里还不存在的食堂、档口名，**自动按名建档**（同名不重复建档）。

## UI

> 📐 页面 UI 设计稿已拆出 → [web-菜品管理.md（docs/ui）](../ui/web-菜品管理.md)（2026-09-22 拆分；**UI 口径以该文件为唯一真源**）

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

> ⚠️ **当前只有这两个参数**：没有关键词 / 状态 / 食堂 等筛选参数，页面上那些筛选均为**前端本地过滤**（服务端加参数的建议见 `README.md`「需拍板的待办清单」第 7 项）。

### 响应 · `GET /admin/dishes`（`data` = `PageResult<DishAdminVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | DishAdminVO[] | 当前页菜品行数组 |
| `total` | number | 菜品总条数 |
| `page` | number | 实际生效页码（归一化后） |
| `pageSize` | number | 实际生效每页条数（归一化后） |

> **`list`（过渡期兼容字段）已删除（2026-09-21 change `api-slimming`）**：两个消费端均优先读 `records`，`list` 属零读取，且其派生自 `records` 并参与序列化 → 同一数组被输出两次、列表响应体积≈翻倍。分页壳收敛为 `records` / `total` / `page` / `pageSize`（本页涉及的全部管理端列表接口同批收敛）。

### 响应 · `DishAdminVO` 单行字段

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 菜品 ID |
| `stallId` | number | 所属档口 ID |
| `name` | string | 菜品名称 |
| ~~`alias`~~ | — | **已删除（2026-09-22 用户拍板：菜品无需昵称，`dish.alias` 全局删除）**——搜索命中收敛为「菜名 / 档口名 / 食堂名」三处；本表不再收录该字段（**已落地 2026-09-22，change `search-page-refresh`**：实体 / DTO / mapper 匹配路 / 表单 / 校验 / 错误码全链删除） |
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
| `mealType` | string \| null | **菜品大类（2026-09-21 新增）**：枚举键 `set_meal` 套餐盖饭 / `stir_fry` 家常小炒 / `noodle` 面食粉类 / `dry_pot` 香锅干锅 / `snack` 风味小吃 / `soup_drink` 汤饮甜品。**中文标签由 `GET /dishes/meal-types` 下发，管理端不得硬编码**；该字段**不进公开菜品出参**（`DishListItemVO` / `DishDetailVO`，卡片不展示） |

> **无审核态字段**（菜品无独立审核，`audit_status` 列已退役）；`imagesJson`（图片原文）为内部字段，不出参。**`viewCount`（浏览量）已删出参（2026-09-18 §7.27）**——仅作热度排序服务，Web 无展示消费，`api/adapter.ts` 的 `view_count` 映射同步删。

### 请求 · `POST /admin/dishes` 与 `PUT /admin/dishes/{id}`（`DishAdminReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `stallId` | number | 否 | 所属档口 ID（选择字典里已有的档口时用）；与下面两个名称都缺失且解析不出来 → 400「请指定所属档口」 |
| `stallName` | string | 否 | **档口名（按名 upsert）**：字典有同名 → 复用其 ID；没有 → **自动建档**；「其他」等空值语义名称不建档 |
| `canteenName` | string | 否 | **食堂名（按名 upsert）**：**仅当 `stallName` 触发新建档口时才消费**，作为新档口的所属食堂；不传则新档口不挂食堂 |
| `name` | string | 新增必填 | 菜品名称 |
| ~~`alias`~~ | — | — | **已删除（2026-09-22 用户拍板）**：菜品不再有搜索别名——后台表单「搜索别名」输入项、`总长 ≤255` 校验、「alias 超长」错误码与「`null`=不修改 / 空串=清空」语义**一并退役**；`seed_data.sql` 经核对本就不含该列、`schema.sql` 以幂等段 `drop_dish_alias_column` DROP 列（**已落地 2026-09-22**） |
| `price` | number | 新增必填 | 现价，**单位：分**（12 元传 1200） |
| `originalPrice` | number | 否 | 原价（折扣前），单位分；`originalPrice > price` 即端上原价划线（`promoPrice` 已删除，2026-09-18 §7.26） |
| `description` | string | 否 | 菜品描述 |
| `images` | string[] | 否 | 菜品图片 URL 数组（单图放一个元素） |
| `dietType` | string | 否 | 荤素 / 饮食属性：`meat` / `half` / `veg` / `halal` |
| `ingredients` | string | 否 | 主料 / 食材（逗号分隔**英文机器值**，如 `pork,egg`） |
| `flavorTags` | string | 否 | 口味（逗号分隔**英文机器值**，如 `spicy,umami`） |
| `serveTemp` | string | 否 | 冷热：`hot` / `room` / `ice` |
| `mealType` | string | 表单必填 | **菜品大类（2026-09-21 新增）**：枚举键（值域见 `GET /dishes/meal-types`）。表单为**下拉选择、不可自由输入**，且为**必填**（不选即前端拦截，依据 §7.34「一个菜品恰属一个大类」）；**唯一例外**：字典加载完成且为空（库中尚无在售菜品）时放行空选并提示稍后补选，避免「菜品 ↔ 大类」互相依赖成死锁。服务端亦做白名单校验（非法值 → 响应体 `code=400`）|
| `status` | string | 否 | 状态：`on`=上架 / `off`=下架（**行内 switch 只传这一个字段**，依赖部分更新能力） |

> 名称类入参须 trim 后非空且长度 ≤64，非法 → 400。

### 响应 · `POST` / `PUT` / `DELETE`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `data` | null | 无载荷；成功即 `code=200` |

### 错误码

| code | 含义 | 中文解释 |
|---|---|---|
| 400 | 档口不存在 / 请指定所属档口 / 名称非法 / **菜品大类不合法**（原「alias 超长」已随别名删除退役，2026-09-22） | 参数与业务校验失败（**注意**：本项目业务错误统一为 HTTP 200 + 响应体 `code` 字段，前端据 `body.code` 分流）|
| 403 | 口令缺失或错误 | `X-Admin-Token` 校验失败（fail-closed） |

## 数据（落库）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `dish` | INSERT / UPDATE / DELETE | 菜品全字段；删除时级联清该菜评价 |
| `canteen` | 按名 upsert | 传了字典中不存在的食堂名 → 自动建档（`sort_order` 默认 0、`created_by` 置空） |
| `stall` | 按名 upsert | 传了不存在的档口名 → 自动建档（带所属食堂 `canteen_id`） |
| `review` / `review_useful` | 级联 DELETE | 删除菜品时一并清理其评价与「有用」记录 |
