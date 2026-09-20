# 首页菜品浏览（A-02）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免登录）
> 返回：[功能总览](./README.md)

## 干什么

看今天有什么可吃：**菜品卡片瀑布流**（图 / 名 / 价 / 评分 / 位置），支持按食堂、价格区间筛选与排序（热度默认）。

## UI

- TabBar「首页」→ `pages/home/index`（瀑布流 `WaterfallList` 内部直渲 `DishCard`）。
- 顶部：搜索入口（跳 A-03）、筛选条（**食堂 / 价格**）。
- 卡片内容：菜品图、名称、价格（元）、评分与评价数、食堂·档口。**（2026-09-20 §7.31：卡片不再显示距离。）**
- ~~首次进入弹**一次性定位提示**（用于「距你 Xm」排序展示）。~~ **已删除（2026-09-20 §7.31：坐标 / 距离概念全链下线——无定位提示、无定位权限申请）**

## 操作

1. 进入首页 → 自动加载第 1 页（`sortBy=heat` 热度倒序）。
2. 上滑触底 → 自动加载下一页（无限加载，按 `total` 判断结束）。
3. 点筛选条 → 选食堂 / 价格区间 → 列表刷新。
4. 点卡片 → 进菜品详情（A-04）。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/dishes` | 🔓 公开 | 菜品分页列表：首页瀑布流、筛选、搜索共用同一端点；**只返回 `status='on'`（在售）菜品** |
| GET | `/canteens` | 🔓 公开 | 食堂字典列表（首页「食堂」筛选条的数据源） |

## 字段

### 请求 · `GET /dishes`（query 参数 / `DishQueryReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 10（端上首页取 20；服务端有上限，超限被截断） |
| `keyword` | string | 否 | 关键词，**同时匹配菜名 `name` 与搜索别名 `alias`** |
| `canteenId` | number | 否 | 按食堂筛选（筛选条「食堂」） |
| `stallId` | number | 否 | 按档口筛选 |
| `minPrice` | number | 否 | 价格下限，**单位：分**（端上由「元」在 API 层转换） |
| `maxPrice` | number | 否 | 价格上限，**单位：分** |
| `sortBy` | string | 否 | 排序维度：`heat`（热度，首页默认）/ `rating`（评分）/ `price`（价格）/ `created_at`（上架时间） |
| `sortOrder` | string | 否 | 排序方向：`asc` 升序 / `desc` 降序（首页用 `heat + desc`） |

### 响应（`data` = `PageResult<DishVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | DishVO[] | **当前页数据行**（前端以它为准） |
| `total` | number | 符合条件的总条数（触底加载是否结束的判据） |
| `page` | number | 实际生效的页码（服务端归一化后的值） |
| `pageSize` | number | 实际生效的每页条数（同上） |
| `list` | DishVO[] | 过渡期兼容字段，**恒等于 `records`**，新代码勿用 |

### 响应 · `DishVO`（单行菜品）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 菜品 ID |
| `name` | string | 菜品名称 |
| `price` | number | 现价，**单位：分**（端上转「元」展示） |
| `originalPrice` | number \| null | 原价（折扣前），单位分；**`originalPrice > price` 即视为有折扣**（端上画原价删除线），无折扣时为 null |
| `description` | string | 菜品描述 |
| `images` | string[] | 菜品图片 URL 数组（首图作封面；无图为空数组） |
| `stallName` | string | 档口名称 |
| `canteenName` | string | 食堂名称 |
| `floor` | string | 档口所在楼层（如 1F / 2F） |
| `avgRating` | number | 平均评分（缓存值，口径 = 仅未隐藏评价） |
| `ratingCount` | number | 评价数（同上口径） |
| `dietType` | string | 荤素 / 饮食属性：`meat`=荤 / `half`=半荤 / `veg`=素 / `halal`=清真 |
| `ingredients` | string | 主料 / 食材（逗号分隔**英文机器值**，端上映射中文展示）：`pork` 猪 / `beef` 牛 / `lamb` 羊 / `chicken` 鸡 / `duck` 鸭 / `fish` 鱼虾 / `egg` 蛋 / `tofu` 豆制品 / `mushroom` 菌菇 / `veg` 青菜 / `noodle` 面 / `rice` 米（**映射唯一真源 = client `api/dish.ts`**，见 `project_spec.md` §7.28） |
| `flavorTags` | string | 口味（逗号分隔**英文机器值**，端上映射中文展示）：`spicy` 辣 / `numbing` 麻 / `sour` 酸 / `sweet` 甜 / `salty` 咸 / `umami` 鲜 / `light` 清淡 / `heavy` 重口（**吸收原「辣度」语义**） |
| `serveTemp` | string | 冷热：`hot`=热食 / `room`=常温 / `ice`=冰 |

> `imagesJson`（图片 JSON 原文）为内部字段，**不出参**；`hasReviewed`（我是否评价过）已下线。
> **公开 `DishVO` 字段精简（2026-09-18 用户拍板，见 `project_spec.md` §7.27）**：已删出参 `promoPrice` / `status`（公开接口恒只返回 `status='on'`，服务端已过滤）/ `createdAt` / `canteenId` / `stallId` / `viewCount`（只为热度服务，口径 = 一直累计）。**公开 VO 24 → 18 字段**。**（2026-09-20 §7.28：`spiceLevel` / `region` 已被 `dietType` / `ingredients` / `flavorTags` / `serveTemp` 替换；§7.29：`tags` 已删除；§7.30：`windowNo` / `updatedAt` 已删；§7.31：`latitude` / `longitude` 已删 → 公开 VO 最终 **15 字段**。）**

### 响应 · `GET /canteens`（`List<CanteenInfoVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 食堂 ID（**端上筛选用它**） |
| `name` | string | 食堂名称（**端上展示用它**） |
| `location` | string | 食堂位置描述 |
| `description` | string | 食堂简介 |
| `images` | string[] | 食堂图片 URL 数组 |

> 端上 `getCanteenList()` 只消费 `id` / `name`，其余字段不透传（跨端 DTO 显式定型，禁 `any` 逃逸）。

## 数据（读取）

| 表 | 读什么 | 中文解释 |
|---|---|---|
| `dish` | 全量字段（过滤 `status='on'`） | 菜品主体：名称/价格/图片/荤素/主料/口味/冷热/评分缓存/浏览量 |
| `stall` | `name`、`floor`、`window_no` | 档口名与位置（楼层、窗口号） |
| `canteen` | `name` | 食堂名（**2026-09-20 §7.31 起不再读坐标；位置表达 = 食堂名 + 档口楼层**） |

## 答疑

### Q：dish 的 `avg_rating`、`rating_count` 应该存固定字段吗？难道不是从数据库计算拿到的吗？

**A：这是「有意的反范式缓存」，不是设计失误。**
真源仍是 `review` 表（任何时刻可由 `review` 重算，库侧口径 = 仅 `is_hidden=0` 的评价参与聚合）；`dish` 上这两个值只是**缓存副本**，由 `POST /reviews` 发 `ReviewSubmittedEvent` → `RatingUpdateListener`（`@Async` AFTER_COMMIT）**异步重算**。不实时聚合的理由：首页瀑布流、`sortBy=heat/rating` 排序、列表卡片都要用均分与评价数，若每次实时 `COUNT/AVG` 做整表分组，热点页必崩，且聚合结果无法与 `dish` 主查询放进同一次索引扫描（现库已有 `idx_dish_heat(status, view_count, rating_count, avg_rating)`）。这是「缓存副本 + 唯一写入口」的正范式补丁，**与 BCNF 不冲突**；风险在于「不能出现第二个写点」，故口径统一收敛到同一事件（见 A-06 / B-03）。

**② ~~`dish.tags` 拆 m:n 表的问题~~ 已作废**：`dish.tags`（必吃推荐 / 招牌菜）字段已于 2026-09-20 用户拍板删除（MVP 期间作用微乎其微，见 `project_spec.md` §7.29）；标签维度不存在，本页不再讨论标签建模。
