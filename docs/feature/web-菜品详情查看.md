# 菜品详情查看（B-02）

> 所属板块：**web（管理后台）** ｜ 鉴权：**🔑 口令**（`X-Admin-Token`）
> 返回：[功能总览](./README.md)

## 干什么

看一道菜的**完整信息 + 全部评价（含被隐藏的）**，并在此页直接编辑 / 删除菜品。

## UI

> 📐 页面 UI 设计稿已拆出 → [web-菜品详情查看.md（docs/ui）](../ui/web-菜品详情查看.md)（2026-09-22 拆分；**UI 口径以该文件为唯一真源**）

## 操作

1. 从列表点行进详情 → 自动读取菜品与该菜评价（数据来自 store 已加载集合，不额外请求）。
2. 点「编辑」→ 打开与列表同一个 `DishFormDialog` → 保存回列表。
3. 点「删除」→ 二次确认 → 删除该菜（级联删评价）。
4. 点某条评价 → 抽屉查看详情（配图、正文、时间、显隐态）。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/dishes` | 🔑 | **本页实际数据源（菜品）**：从列表接口的集合中按 `id` 取对应行（见「字段」说明） |
| GET | `/admin/reviews` | 🔑 | **本页实际数据源（评价）**：按 `dishId` 筛选后展示（分页同评价列表页） |

> ⚠️ **Web 不得调用公开端点 `GET /dishes/{id}`**：DEV-04 已把 Web 侧的 `getById()` 封装移除；且公开端点只返回 `status='on'` 的菜品，**下架菜取不到名**。因此本页展示的评论文档语义（`GET /dishes/{id}` + `GET /reviews`）**仅适用于小程序**，Web 一律走 `/admin/**`。

## 字段

### 请求 · `GET /admin/dishes` / `GET /admin/reviews`（query）

本页复用列表页取数接口，**只传分页参数、无业务查询参数**（行数据在端上从已加载集合本地取）：

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1（菜品侧 / 评价侧各自分页） |
| `pageSize` | number | 否 | 每页条数，默认 20（服务端上限 100） |

> 菜品行按 URL 的 `{id}` 在端上已加载集合中匹配；评价行按 `dishId` 过滤。

### 响应 · 菜品侧（`DishAdminVO` 单行字段，19 个）

本页展示其中 `name` / `canteenName` / `stallName` / `avgRating` / `ratingCount` / `images` / `dietType` / `ingredients` / `flavorTags` / `serveTemp` / `description` / `price` / `originalPrice` / `status`，其余为列表页共用出参：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 菜品 ID |
| `stallId` | number | 所属档口 ID |
| `name` | string | 菜品名称（本页页头主标题） |
| `alias` | string \| null | 搜索别名（逗号分隔，管理员配置；与菜名同权命中） |
| `price` | number | 现价（单位：分，已含折扣） |
| `originalPrice` | number \| null | 原价（单位：分，折扣前）；`originalPrice > price` 时端上划线 |
| `description` | string | 菜品描述 |
| `images` | string[] | 菜品图片 URL 数组（首图作封面） |
| `status` | string | 上架状态：`on`=上架 / `off`=下架 |
| `avgRating` | number | 平均评分（缓存值，口径 = 仅未隐藏评价） |
| `ratingCount` | number | 评价数（同口径） |
| `createdAt` | string | 创建时间 |
| `updatedAt` | string | 更新时间 |
| `stallName` | string | 档口名称（联表） |
| `canteenName` | string | 食堂名称（联表） |
| `dietType` | string | 荤素 / 饮食属性：`meat`=荤 / `half`=半荤 / `veg`=素 / `halal`=清真 |
| `ingredients` | string | 主料 / 食材（逗号分隔**英文机器值**，管理端展示层映射中文）：`pork` 猪 / `beef` 牛 / `lamb` 羊 / `chicken` 鸡 / `duck` 鸭 / `fish` 鱼虾 / `egg` 蛋 / `tofu` 豆制品 / `mushroom` 菌菇 / `veg` 青菜 / `noodle` 面 / `rice` 米（见 `project_spec.md` §7.28） |
| `flavorTags` | string | 口味（逗号分隔**英文机器值**）：`spicy` 辣 / `numbing` 麻 / `sour` 酸 / `sweet` 甜 / `salty` 咸 / `umami` 鲜 / `light` 清淡 / `heavy` 重口 |
| `serveTemp` | string | 冷热：`hot`=热食 / `room`=常温 / `ice`=冰 |

### 响应 · 评价侧（`ReviewAdminVO` 单行字段，12 个）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 评价 ID |
| `userId` | number | 评价者用户 ID（页面据此在用户列表里匹配昵称） |
| `dishId` | number | 关联菜品 ID（页面据此筛出本菜评价） |
| `dishName` | string | 关联菜品名 |
| `userNickname` | string | 评价者昵称 |
| `userAvatar` | string | 评价者头像 URL |
| `rating` | number | 评分（1~5 星） |
| `content` | string | 评价正文 |
| `images` | string[] | 评价配图 URL 数组（≤3 张） |
| `createdAt` | string | 评价时间 |
| `isHidden` | number | 是否被隐藏：0=正常 / 1=已隐藏（**本页可见隐藏评价**） |
| `usefulCount` | number | 「有用」总数 |

## 数据（读取）

| 表 | 读什么 | 中文解释 |
|---|---|---|
| `dish` | 全字段 | 菜品信息卡 |
| `review` | 该菜全部评价（**含 `is_hidden=1`**） | 管理端可看到被隐藏内容 |
| `user` | `nickname`、`avatar` | 评价者昵称与头像 |
| `stall` / `canteen` | 名称 | 位置展示 |
