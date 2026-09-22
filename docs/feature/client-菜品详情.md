# 菜品详情（A-04）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免登录）
> 返回：[功能总览](./README.md)

> ⚠️ **口径说明**：本文档为 QA 讨论拍板后的**目标口径**。标注「待实现」的条目代码尚未落地，落地前以现行接口为准；差异清单见文末「已拍板待实现清单」。

## 干什么

看一道菜的**完整信息**（图、价、原价删除线、描述、荤素 / 主料 / 口味 / 冷热、食堂 · 楼层 · 档口名）与**全部可见评价**（含评分分布）。评价区**按时间倒序展示（新评价在前，唯一排序）**，支持「只看有图」筛选；支持**重新评价**——旧评价作废、留下最新，新评价时间取当前、排到最前。

## UI

> 📐 页面 UI 设计稿已拆出 → [client-菜品详情.md（docs/ui）](../ui/client-菜品详情.md)（2026-09-22 拆分；**UI 口径以该文件为唯一真源**）

## 操作

1. 进入页 → 并行拉三件：详情 `GET /dishes/{id}`、评价 `GET /dishes/{id}/reviews`、我的评价 `GET /my/reviews?dishId=`（判定底栏按钮态）→ 同时上报浏览（A-05）。
2. 上滑加载更多评价（分页）：**结束判据 = 已加载条数 ≥ `total`**（**不得**用「本页返回条数 < `pageSize`」——末页恰好满页时会多发一次空请求）。
3. 切「只看有图」→ **重置 `page=1` 并清空列表**再拉取；空态文案「暂无带图评价」。
4. 写评价（未评价态）→ 底栏「写评价」→ `ReviewComposer` 弹层（见 [client-写评价](./client-写评价.md)）。
5. **重新评价**（已评价态）：底栏「重新评价」→ `ReviewComposer` **预填旧值**（评分 / 文字 / 配图）→ 提交走 `PUT /reviews/{id}` → 旧内容被覆盖、新评价时间取当前、排到列表最前；**提交成功后底栏保持「重新评价」**。
6. 评价卡三点菜单「举报」→ `ReportModal`（A-09）。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/dishes/{id}` | 🔓 公开 | 菜品详情。**登录态与游客态返回结构完全一致**（无用户态分支） |
| GET | `/dishes/{id}/reviews` | 🔓 公开 | **评价列表（菜品子资源）**：**唯一排序 = 时间倒序**；支持「只看有图」筛选（待实现）；仅返回未隐藏评价 |
| GET | `/my/reviews?dishId={id}` | **🔐 认证** | **我的评价（按菜过滤）**：判定「我是否已评价此菜」并取回我的评价 `id`（供底栏「重新评价」预填）；**修复「我的评价在第 2 页找不到」的分页边界 bug** |
| PUT | `/reviews/{id}` | **🔐 认证**（作者本人） | **重新评价（覆盖式，待实现）**：覆盖同一行的评分 / 文字 / 配图，时间取当前、排到最前 |

> 写评价 `POST /reviews` 见 [client-写评价](./client-写评价.md)；删除本人评价 `DELETE /reviews/{id}` 见 [client-删除本人评价](./client-删除本人评价.md)。

## 字段

### 请求 · `GET /dishes/{id}`

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `id` | number | **是** | 菜品 ID（路径参数，如 `/dishes/1`） |

### 响应 · `GET /dishes/{id}`（`data` = `DishDetailVO`）

`DishDetailVO` = `DishVO` 全字段 + `ratingDistribution`。

`DishVO` 单行字段（15 个；2026-09-20 §7.31 起删 `latitude` / `longitude`）：

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
| `avgRating` | number | 平均评分（缓存值，口径 = 仅未隐藏评价） |
| `ratingCount` | number | 评价数（同上口径） |
| `dietType` | string | 荤素 / 饮食属性：`meat`=荤 / `half`=半荤 / `veg`=素 / `halal`=清真 |
| `ingredients` | string | 主料 / 食材（逗号分隔**英文机器值**，端上映射中文展示）：`pork` 猪 / `beef` 牛 / `lamb` 羊 / `chicken` 鸡 / `duck` 鸭 / `fish` 鱼虾 / `egg` 蛋 / `tofu` 豆制品 / `mushroom` 菌菇 / `veg` 青菜 / `noodle` 面 / `rice` 米（**映射唯一真源 = client `api/dish.ts`**，见 `project_spec.md` §7.28） |
| `flavorTags` | string | 口味（逗号分隔**英文机器值**，端上映射中文展示）：`spicy` 辣 / `numbing` 麻 / `sour` 酸 / `sweet` 甜 / `salty` 咸 / `umami` 鲜 / `light` 清淡 / `heavy` 重口（**吸收原「辣度」语义**） |
| `serveTemp` | string | 冷热：`hot`=热食 / `room`=常温 / `ice`=冰 |

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `ratingDistribution` | object[] | 评分分布数组，固定 5 项（每星级一条），用于画 5 星柱状条；口径 = 仅未隐藏评价 |

`ratingDistribution` 数组项（`RatingDistributionVO`）：`star`（星级 1~5）、`count`（该星级评价条数）。

> `hasReviewed`（我是否已评价）已下线且**不恢复**——**「我是否已评价」由端上单独调 `GET /my/reviews?dishId=` 判定**（见 §接口），故游客与登录的 `GET /dishes/{id}` 返回结构一字不差。~~详情页额外消费 `updatedAt` 展示「信息更新于 X」~~ ——**已于 2026-09-20 去掉（见 `project_spec.md` §7.30），`updatedAt` 不再出参**。
> 价格展示只消费 `price`（现价）与 `originalPrice`（原价），折扣由 `originalPrice > price` 表达；**`promoPrice`（折扣价）字段已全链删除（2026-09-18 用户拍板，见 `project_spec.md` §7.26 与文末清单 #5）**。
> **位置与距离口径（2026-09-20 §7.31）**：`latitude` / `longitude` 已随「坐标 + 距离」概念**全链下线**——服务端不出参坐标、端上不算距离、不申请定位权限（`scope.userLocation` 撤声明）；位置表达仅用 **食堂名 · 楼层 · 档口名**（`canteenName` / `floor` / `stallName`）。

### 请求 · `GET /dishes/{id}/reviews`

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `id` | number | **是** | 菜品 ID（**路径参数**，RESTful 子资源，如 `/dishes/1/reviews`） |
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 20（**详情页固定传 10**） |
| `hasImage` | number | 否 | **「只看有图」筛选（待实现）**：`1`=只看有图 / `0` 或缺省=不限；筛选条件 `images IS NOT NULL AND images <> '' AND images <> '[]'`。**端上切换筛选时必须重置 `page=1` 并清空列表** |
| ~~`sort`~~ | — | — | **已废除（待实现）**：排序唯一为时间倒序，端上与后端均不再持有排序切换 |

### 响应 · `GET /dishes/{id}/reviews`（`data` = `PageResult<ReviewVO>`）

> **公开列表出参收敛（2026-09-20 §7.30）**：不再返回 `dishId` / `dishName` / `isHidden`（`dishId` 恒等于路径、`dishName` 联表冗余、`isHidden` 公开列表恒 null）——三者仅「我的评价」需要。

**分页壳字段**：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | ReviewVO[] | 当前页评价行数组（端上以它为准） |
| `total` | number | 该菜品可见评价总条数 |
| `page` | number | 服务端归一化后的实际页码 |
| `pageSize` | number | 服务端归一化后的实际每页条数 |
| `list` | ReviewVO[] | 过渡期兼容字段，恒等于 `records`，新代码勿用 |

`ReviewVO` 单行字段（**拍板后口径**）：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 评价 ID |
| `userId` | number | 评价者用户 ID（端上据此判定是否本人，决定「删除 / 举报」） |
| `userNickname` | string | 评价者昵称（账号注销后为「已注销用户」） |
| `userAvatar` | string | 评价者头像 URL |
| `rating` | number | 评分（1~5 星） |
| `content` | string | 评价文字内容 |
| `images` | string[] | 评价配图 URL 数组（COS 绝对地址，≤3 张，无图空数组） |
| `createdAt` | string | 评价发表时间（**重新评价后取新时间**；端上 API 层映射为 `createTime`） |

### 请求 · `GET /my/reviews?dishId={id}`（我的评价 · 按菜过滤）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `dishId` | number | 否 | **按菜品过滤**（详情页判定「我是否已评价」时必传）；不传 = 全部我的评价（见 [client-我的评价](./client-我的评价.md)） |
| `page` / `pageSize` | number | 否 | 分页，同通用约定 |

### 响应 · `GET /my/reviews`（`data` = `PageResult<ReviewVO>`）

分页壳同通用；行字段 = `ReviewVO` **本人视角**（公开列表 8 字段 + `dishId` / `dishName` / `isHidden`）。详情页**只用它判定「我已评价」并取回评价 `id`**（供 `PUT /reviews/{id}` 预填 / 底栏「**重新评价**」入口切换）。

### 请求 · `PUT /reviews/{id}`（重新评价，待实现）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `id` | number | **是** | 评价 ID（路径参数） |
| `rating` | number | **是** | 新评分，1~5 星 |
| `content` | string | 否 | 新文字评价，≤500 字 |
| `images` | string[] | 否 | 新配图 URL 数组，≤3 条 COS 绝对地址 |

> **不收 `dishId`**：归属由路径 `{id}` 锁定，**不可换菜**。

### 响应与语义 · `PUT /reviews/{id}`

`data` 为 `null`；语义与副作用：

| 项 | 口径 |
|---|---|
| 语义 | **覆盖更新同一行**（非删行重建）：唯一键 `uk_review_user_dish` 不变、无并发窗口；旧内容不留残留 |
| `created_at` | **刷新为当前时间**（"重评即新发布"，时间倒序下自然置顶）；`updated_at` 同步刷新 |
| `is_hidden` | **重置为 0**（旧评价作废、新评价从零开始；旧内容已不留残留，新内容仍过安检） |
| 评分聚合 | 发 `ReviewSubmittedEvent` 重算一次（口径不变：仅计 `is_hidden=0`） |
| 次数 | **不限次**（一人一菜只有一票，改多少次也只是一票）；滥用时再加简单限频 |
| 安检 | 与提交同口径：文本 `msgSecCheck` v2（`scene=2`）在请求内送检；图片经 `POST /upload/images` 转存链路的 `imgSecCheck` 送检（**重评不重复送检**，仅校验受信 COS 地址）；`risky` → 400 |
| 鉴权 | 作者本人 + `@RequireVerified`（未认证 4031）；非本人 → 403 |

## 数据（读取 / 落库）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `dish` | 读全字段 | 菜品主体信息 |
| `stall` / `canteen` | 读名称、楼层 | 归属位置（食堂 / 楼层 / 档口名）；**不再读坐标、不算距离（2026-09-20 §7.31）** |
| `review` | 读（过滤 `is_hidden=0`）/ UPDATE（重评覆盖同一行） | 可见评价列表；重新评价覆盖评分/文字/配图并刷新时间 |
| ~~`review_useful`~~ | **随「有用」下线整表删除（待实现）** | 有用投票记录与计数 |
| `view_log` | 写（A-05） | 浏览足迹与当日去重 |

## 已拍板待实现清单

以下条目已在 QA 讨论中拍板，**代码未落地前以现行接口为准**；均须先由技术负责人修订 `project_spec.md` / `docs/api-design.md` 再开发：

| # | 拍板项 | 影响面 |
|---|---|---|
| 1 | **删除评价「有用」**（端点 / `ReviewVO.usefulCount`、`useful` / `ReviewAdminVO.usefulCount` / `review_useful` 表 / `review.useful_count` 列 / 端上按钮 / Web 列 / 级联清理） | 跨三端 + 库表 |
| 2 | **评价排序改为时间倒序**（新评价在前，唯一排序，`sort` 参数废除） | `ReviewMapper.xml` 默认分支 |
| 3 | **「只看有图」筛选**（`GET /reviews?hasImage=1`） | 后端一条筛选条件 + 端上开关（切换重置分页） |
| 4 | **重新评价 `PUT /reviews/{id}`**（覆盖 + 刷新 `created_at` + `is_hidden` 重置 0 + 不限次）——**入口口径**：底栏按「我是否已评价」切成「写评价 / **重新评价**」双态，提交成功后就地保持「重新评价」 | 后端 1 个端点 + 端上底栏双态与 `ReviewComposer` 预填入参 |
| 5 | **删除菜品「折扣价」`promoPrice`**（`price` 现价 + `originalPrice` 原价两态表达折扣，判据 `originalPrice > price`；`promo_price` 属冗余副本）——**文档层已于 2026-09-18 修订完毕（spec §7.26），已于 2026-09-20 三端 + 库表落地** | 跨三端 + 库表：client 搜索/详情、web 菜品管理/详情/表单、`DishVO`/`DishMapper.xml`、`schema.sql`/`seed_data.sql`（spec / api-design 已就位） |
| 6 | **公开 `DishVO` 出参精简**（删 `status`（公开接口恒只返回在售）/ `createdAt` / `canteenId` / `stallId` / `viewCount`（只为热度服务、口径 = 一直累计）） | server `DishVO`/`DishAdminVO`（`viewCount`）/`DishMapper.xml`；client `types/dish.ts`/`api/dish.ts`/`utils/share-state.ts`；web `api/adapter.ts`/`types/index.ts`（`view_count` 映射） |
| 7 | **接口 RESTful 化**（本批两域）：`GET /dishes/{id}/reviews`、`POST /dishes/{id}/reviews`、`POST /dishes/{id}/views`、`GET /my/reviews?dishId=`——**文档层已改，待代码落地**（收敛清单见 `project_spec.md` §7.30） | 后端路径 + 三端 `api` 层 |
| 8 | **详情模块优化**：① 「我是否已评价」定死方案 (a)——加 `GET /my/reviews?dishId=`（修分页边界 bug）；② 公开评价列表不再返回 `dishId`/`dishName`/`isHidden`；③ 公开 `DishVO` 删 `windowNo`/`updatedAt`（**20 → 17 字段**，以 `project_spec.md` §7.30 为准）；④ 信息卡去「评分/评价」列、去「信息更新于 X」——**文档层已改，待代码落地**（见 `project_spec.md` §7.30） | server `DishVO`/`ReviewController`/`DishMapper.xml`/`ReviewMapper.xml`；client `useDishPage.ts`/`DishInfoCard.vue`/`types/dish.ts` |
| 9 | **坐标与距离概念全链下线**（`DishVO`/`CanteenInfoVO` 删 `latitude`/`longitude`、`canteen` 表两列幂等 DROP、端上删 `distance`/Haversine/`CAMPUS_CENTER`/`formatDistance`/`location` store/`geo-prompt` 与首页·搜索·详情距离展示、`manifest.json` 撤 `scope.userLocation` 与 `getLocation`；公开 `DishVO` **17 → 15 字段**）——**文档层已于 2026-09-20 修订完毕（spec §7.31），已于 2026-09-20 三端 + 库表落地** | server `Canteen`/`CanteenInfoVO`/`CanteenServiceImpl`/`DishVO`/`DishMapper.xml`/`schema.sql`/`seed_data.sql`；client `types/dish.ts`/`api/dish.ts`/`utils/location.ts`/`utils/format.ts`/`stores/location.ts`/`stores/dish.ts`/`pages/home/geo-prompt.ts`/`DishCard.vue`/`FindResults.vue`/`DishInfoCard.vue`/`useDishPage.ts`/`manifest.json` |
| 10 | **详情模块审计整改批（2026-09-20 审计产出，用户拍板「P0 先修、其余按推荐处理」）**：① **价格展示双源修复**（`hasPromo` 改判 `originalPrice > price`、展示值恒取 `price`——见 P0-1）；② **分页结束判据改用 `total`**（见 P0-3）；③ **清死代码**（`DishReviewSection` 零消费 `loading` prop + `useDishPage.reviewLoading`、`ReviewItem` 死 emit `like`、空壳 `onShow`、`sharedDish.stallId` 只写不读）；④ **术语正名**（「机检 / 机审」→「**微信内容安全检测**」，含本模块 4 处）；⑤ **token 收敛**（硬编码 `border-radius: 16px` → `var(--radius-card)`、`--radius-pill` fallback 冗余）；⑥ **触控目标 ≥ 88rpx** 与 `aria-expanded` / 轮播 `aria-label` / 头像 `aria-label`；⑦ **详情页大图关自动轮播**；⑧ **页面失败 / 不存在态文案**（加载态遵守既有「不设骨架 / 空白静默」红线）；⑨ **四维逐维渲染、缺项不占位** | client `pages/detail/dish/**`、`api/review.ts`、`types/review.ts`；**无库表变更**（与 #1–#9 可并行，互不阻塞） |

（独立建议、未拍板：`ratingCount < 3` 不展示均分——大众点评"达 10 条才算星级"的思路，见 [README 待办 #11](./README.md#需拍板的待办清单答疑产出的未决项)。）
