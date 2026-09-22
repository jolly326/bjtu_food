# 首页菜品浏览（A-02）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免登录）
> 返回：[功能总览](./README.md)
> 最后更新：**2026-09-22**（本日变更：Banner 接口化 16:10 多图轮播；首页结构改「Banner 整块正常流 + 搜索区/大类标签栏吸顶」；**食堂 / 价格筛选全量下线**并删除食堂字典端点；列表出参拆出 `DishListItemVO` 8 字段）

## 干什么

看今天有什么可吃：**双列菜品卡片网格**（图 / 名 / 价 / 评分 / 位置），支持按**菜品大类**（横向标签栏）筛选（**排序恒为服务端固定口径 = 热度，端上无排序入口**）。

> 2026-09-22 修订：原「食堂筛选 / 价格区间筛选」**已全量下线**——首页与搜索页均无这两个入口（见 K 项）。

## UI

> 📐 页面 UI 设计稿已拆出 → [client-首页菜品浏览.md（docs/ui）](../ui/client-首页菜品浏览.md)（2026-09-22 拆分；**UI 口径以该文件为唯一真源**）

## 操作

1. 进入首页 → 自动加载第 1 页，**恒按热度倒序**（服务端固定口径，见下）。端上**不传排序参数**：原 `sortBy` / `sortOrder` 已删除（2026-09-21 决议，见「决议登记」C 项）——消除此前「首页默认流传 `heat`、筛选流与搜索流因不传参数而落到 `rating_count DESC` 分支」的口径分裂（**注**：其中「筛选流」已于 2026-09-22 随筛选功能下线，见 K 项）。
2. 上滑触底 → 自动加载下一页（无限加载；结束判据 = **本页返回条数 < `pageSize`**，**`total` 不是判据**——`filterTotal` 已随 MP-05/MP-06 删除，见「决议登记」F 项）。
3. 点**大类标签** → 按该大类筛选（`mealType=<枚举>`）→ 列表刷新，标签下划线切换。
4. 上滑超过阈值（= **Banner 总高 − 固定标题带高**）→ **「搜索区 + 横向大类标签栏」吸顶常驻**，锁定位置 = **固定标题带下沿**（`top = 状态栏高 + 胶囊行高`）。**固定标题带常驻、不随滚动移动**（跨页统一位置、文案按页配置）；Banner 从标题带下方滑过并滚出视口（**不折叠、不定格、不残留背景**）；回滚不足一个阈值即取消吸顶，Banner 自标题带下方重新滚回。
5. 点**搜索胶囊**或右侧**「搜索」按钮** → 进搜索页（A-03）——两者为同一入口，**首页不存在任何筛选入口**（无「筛选」按钮、无筛选面板，2026-09-22 见 K 项）。
6. 点卡片 → 进菜品详情（A-04）。
7. 进入首页即**并行**发起 Banner 与列表请求（`GET /banners` + `GET /dishes`）：Banner 返回空 / 请求失败 → 顶部退化为**与菜品卡图片占位同款的灰底 + 菜品 icon 空态**（**不阻塞**首屏网格）；**多张**时端上自动轮播（4s 间隔）并显示指示点，**仅一张**时不自动轮播、不显示指示点；**Banner 无点击交互**（本期不做跳转）。
8. ~~点搜索框右侧「筛选」→ 选食堂 / 价格区间 → 列表刷新~~ **已全量下线（2026-09-22，见 K 项）**：首页不再有任何筛选入口，搜索页（find）的食堂 / 价格筛选胶囊同步删除。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/dishes` | 🔓 公开 | 菜品分页列表：首页网格、搜索共用同一端点，**返回 `PageResult<DishListItemVO>`（列表专用 8 字段，见「字段」节）**；**只返回 `status='on'`（在售）菜品**；支持 `mealType` 大类筛选（2026-09-21 新增）。**2026-09-22：`canteenId` / `minPrice` / `maxPrice` 三个筛选参数已随「食堂 / 价格筛选全量下线」删除**（见 K 项） |
| GET | `/dishes/meal-types` | 🔓 公开 | **菜品大类字典（2026-09-21 新增）**：下发标签栏数据源 `[{ key, label, order }]`，只含当前有在售菜品的大类 |
| GET | `/banners` | 🔓 公开 | **首页顶部轮播图（2026-09-22 新增）**：下发启用中的 Banner 清单 `[{ id, imageUrl }]`（服务端按 `sort_order` 升序），供首页顶部 16:10 轮播；**无请求参数** |

## 字段

### 请求 · `GET /dishes`（query 参数 / `DishQueryReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 10；**端上首页固定传 10**（`HOME_PAGE_SIZE`；2026-09-22 更正——原写「端上首页取 20」与代码不符）；**服务端归一化上限 100**（`PageUtil.MAX_PAGE_SIZE`，超限截断为 100；`<=0` 回退 10），**实际生效值以响应 `page` / `pageSize` 为准** |
| `keyword` | string | 否 | 关键词，**同时匹配菜名 `name` / 档口名 `stall.name` / 食堂名 `canteen.name`**（三处模糊匹配；**2026-09-22 用户拍板全局删除 `dish.alias`（菜品无需昵称），别名不再参与匹配**，代码待落地——见 [client-搜索](./client-搜索.md) 差异节；**关键词搜食堂名仍然可用**） |
| `mealType` | string | 否 | **菜品大类筛选（2026-09-21 新增）**：单值，值域 = 大类枚举键（`set_meal` / `stir_fry` / `noodle` / `dry_pot` / `snack` / `soup_drink`）；**白名单校验，非法值 → 400**（PR-06），不静默降级 |

> **参数集收敛为 4 项（2026-09-22，K3）**：`page` / `pageSize` / `keyword` / `mealType`。原 `canteenId` / `minPrice` / `maxPrice` 已随「食堂 / 价格筛选全量下线」删除，见下方「已删除参数」。

> **已删除参数（2026-09-22 决议：食堂 / 价格筛选全量下线，见 K 项）**：
> - `canteenId`（按食堂筛选）、`minPrice` / `maxPrice`（价格区间）——首页筛选面板与搜索页筛选胶囊**同时下线**后，三者**全端零发送**（端上 `types/dish.ts` 的 `DishQuery` 三字段、`api/dish.ts` 的 `searchDishesPage` 映射、`utils/price-filter.ts` 一并删除）；服务端删除 `DishQueryReq` 三字段、`DishServiceImpl` 对应条件与 `DishMapper.xml` 的 `if` 分支。**`mealType` 保留**（大类标签栏仍在用）。
> **已删除参数（2026-09-21 决议）**：
> - `stallId`（按档口筛选）——端上**零发送**：筛选只有「食堂 / 价格 / 大类」，无档口入口（**注**：其中食堂与价格已于 2026-09-22 下线，见 K 项）；管理端走 `/admin/dishes`。判据同 §7.23 第 6 条（已据此删除 `GET /reviews` 的 `stallId` / `canteenId`）。
> - `sortBy` / `sortOrder`——端上**零消费**：`DishSortBy` 四值中仅 `heat` 被首页默认流使用，且 `heat` 分支内部固定 `DESC`（`sortOrder` 对任何取值均无效果）；`rating` / `price` / `created_at` 无任何调用方。排序口径统一收敛为**服务端恒热度**（见「决议登记」C 项）。

### 响应（`data` = `PageResult<DishListItemVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | DishListItemVO[] | **当前页数据行**（前端以它为准；**不含详情专属字段**，见下） |
| `total` | number | 符合条件的总条数（服务端同口径统计）。**⚠️ 当前端上零读取**——`/dishes` 链路的结束判据是「本页返回条数 < `pageSize`」（`stores/dish.ts:305` / `:354`），原 `filterTotal` 已随 MP-05/MP-06 删除；`total` 仅在**详情评价区**被读取（`useDishPage.ts:110`） |
| `page` | number | 实际生效的页码（服务端归一化后的值） |
| `pageSize` | number | 实际生效的每页条数（同上） |

> **已删除出参（2026-09-21 决议）**：`list`（过渡期兼容字段，`PageResult`）——**双重冗余**：① 两个消费端都**优先读 `records`**（`client/src/api/shared.ts` 的 `recordsOf` = `value.records || value.list || []`；`web/src/api/adapter.ts` 的 `pageRecords` 同构），服务端恒返回 `records` → `list` 分支永不命中，**全端零读取**；② `getList()` 派生自 `records` 且参与序列化 → **同一数组被 JSON 输出两次，列表响应体积≈翻倍**。`PageResult` 注释已自认「待全部消费方切换到 `records` 后可移除」，该条件现已满足。
> `total` / `page` / `pageSize` **予以保留**：`total` 是 `PageResult` 全局共用字段（详情评价区仍以它判结束），且「服务端同口径统计」本身有语义；`page` / `pageSize` 虽同为端上零读取（端上自持页码、以「本页条数 < `pageSize`」判结束），但低成本、排障仍有价值。
> **单看 `/dishes` 端点：三者均为零读取**（见 F 项）——若将来要极致收敛，可评估「列表端点专用极简分页载体」，非本批事项。

### 响应 · `GET /dishes/meal-types`（`List<MealTypeVO>`，2026-09-21 新增）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `key` | string | 大类枚举键（用于 `GET /dishes?mealType=` 筛选） |
| `label` | string | 中文标签（**端上直接渲染，不得在前端维护映射表**） |
| `order` | number | 标签栏展示顺序（升序） |

> **形态**：标签栏**不含「全部」**（「全部」= 不传 `mealType`，由端上固定渲染为第一项）；后端只下发**当前有在售菜品**的大类（空类自动隐藏）。
> **不做出参的部分**：大类**不进公开菜品出参**（`DishListItemVO` / `DishDetailVO`）——列表卡片不展示大类，筛选在后端完成，属「零消费即删」；后台 `DishAdminReq` / `DishAdminVO` 需要（录入下拉 + 编辑回填 + 列表筛选）。

### 响应 · `GET /banners`（`List<BannerVO>`，2026-09-22 新增）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | Banner ID（端上作轮播项的稳定 key） |
| `imageUrl` | string | 轮播图 URL（**素材统一 16:10**，端上 `aspectFill` 铺满 Banner 整块） |

> **形态**：只返回**启用中**（`status='on'`）的 Banner，按 `sort_order` 升序；**无分页**（运营位数量级极小，非分页返回 `List<T>`）。`imageUrl` 一律为**可直接渲染的绝对 URL**（与菜品图片同口径：库内可存相对路径，出参经 `ImageUrlUtil.toAbsoluteUrl` 转换，绝对地址原样返回）。
> **零消费即删**：`sort_order` / `status` 是服务端排序与过滤用的内部字段，端上零消费 → **不出参**；**v1 无跳转能力** → 不出参 `targetType` / `targetId` / `targetUrl`（端上 Banner 无点击交互）。将来要做点击跳转，须**先由 UI 文档定义交互**再扩字段。
> **空集合语义**：无启用 Banner 时返回**空数组 `[]`**（不返回 404、不返回 null），端上退化为占位图（见 UI 文档 §1）。

### 响应 · `DishListItemVO`（列表行；`GET /dishes`，首页网格与搜索结果共用）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 菜品 ID（列表 key / 跳转详情用） |
| `name` | string | 菜品名称 |
| `coverImage` | string | **封面图 URL**（= 原 `images[0]`；无图为空串）——列表只渲染首图，故**列表不再下发图片数组** |
| `price` | number | 现价，**单位：分**（端上转「元」展示） |
| `originalPrice` | number \| null | 原价（折扣前），单位分；**`originalPrice > price` 即视为有折扣**（端上画原价删除线），无折扣时 null |
| `avgRating` | number | 平均评分（缓存值，口径 = 仅未隐藏评价）——卡片只渲染评分，**列表不发 `ratingCount`** |
| `canteenName` | string | 食堂名称（卡片第 3 段位置行） |
| `stallName` | string | 档口名称（卡片第 3 段位置行） |

> **列表出参恰为 8 字段**（2026-09-22 用户拍板拆分，见 D 项）：字段集 =「首页卡片四段（图 / 名 / 位置 / 评分 + 价格）」的**真实渲染集合**，加跳转必需的 `id`——不做任何详情专属字段的下发。
> **不再随列表下发的详情专属字段**（字段集与语义**以 `docs/feature/client-菜品详情.md` 为真源，本文档不再重复维护**）：`description`（描述）、`images`（多图数组，列表只给首图 `coverImage`）、`floor`（楼层）、`ratingCount`（评价数）、`dietType`（荤素）、`ingredients`（主料）、`flavorTags`（口味）、`serveTemp`（冷热）、`ratingDistribution`（评分分布）。**`mealType` 亦不进公开出参**（见 H 项）。
> **8 字段在两端均全量消费（2026-09-22 复核纠正）**：搜索页结果卡**同样渲染位置行**——`FindResults.vue` 的底部行取 `item.sub`，由 `pages/find/index.vue` 用 `[d.canteen, d.stallName].join(' · ')` 组装（此前「搜索结果卡不渲染 `canteenName` / `stallName`」的描述与代码相反，已纠正）。**列表与搜索共用同一端点与同一 VO**，不为搜索页另拆分 VO（多一个 VO 即多一份契约维护成本）。
> `imagesJson`（图片 JSON 原文）为内部字段、**不出参**；`hasReviewed`（我是否评价过）已下线。
> **历史上已删出参（SHALL NOT 回流）**：`promoPrice` / `status`（公开接口恒只返回在售）/ `createdAt` / `canteenId` / `stallId` / `viewCount` / `tags` / `spiceLevel` / `region` / `windowNo` / `updatedAt` / `latitude` / `longitude`（2026-09-18 ~ 09-20 连续精简，见 `project_spec.md` §7.27~§7.31）。

## 数据（读取）

| 表 | 读什么 | 中文解释 |
|---|---|---|
| `dish` | **列表按 `listDishColumns`（`DishListItemVO` 8 字段所需列）、详情按 `detailDishColumns`**（2026-09-22 D 项拆分后的双列清单）；`meal_type` 只用于筛选与字典下发；`status='on'` 仅用于 WHERE 过滤、不出参 | 菜品主体。**列表不选**详情专属列（`description` / `images` 全量 / `diet_type` / `ingredients` / `flavor_tags` / `serve_temp` / `rating_count` 等——只在详情查询里取）；两场景均**不选** `status` / `view_count` / `created_at` / `updated_at` / `alias` / `stall_id` / `canteen_id` / `window_no` / 坐标（`DishMapper.xml` 的公开列清单注释明示）。**`meal_type` 不进公开出参**（2026-09-21 新增，见 H 项） |
| `stall` | 列表仅 `name`；详情加 `floor` | 档口名（列表卡片第 3 段）；楼层 `floor` **仅详情 `DishDetailVO` 需要**（2026-09-22 D 项拆分后列表不再读）。**不再读 `window_no`**：窗口号已随 §7.30 从公开出参删除，`DishMapper.xml` 的公开查询列同批收窄（注释明示「公开出参不含 …window_no…故此处不选」） |
| `canteen` | `name`（菜品列表 join 取食堂名） | 食堂名（**2026-09-20 §7.31 起不再读坐标；位置表达 = 食堂名 + 档口楼层**）；**公开侧已无食堂字典端点**（2026-09-22 随筛选功能全量下线删除，见 K4） |
| `banner` | `id`、`image_url`（仅启用项） | **首页顶部轮播图（2026-09-22 新增）**：`SELECT id, image_url ... WHERE status='on' ORDER BY sort_order ASC`；`sort_order` / `status` 仅用于排序与过滤、**不出参**；`image_url` 与菜品图片同口径（库内可存相对路径，出参转绝对 URL）。表结构见 `server/src/main/resources/db/schema.sql`（**新增后共 11 张表**） |

## 决议登记（A~K：现行设计形态与历史决议）

> 本节按「一个决策一个小节」组织（`### A` ~ `### K`），可被目录 / 锚点直接跳转；**各小节只写最终设计形态与历史决议，实现进度一律见文末「与当前代码的差异」**。
> A~C 组的依据：端上（`client/src`）与管理端（`web/src`）消费点 grep + 服务端实现核实；**2026-09-21 用户决议：删除下列冗余字段**——本文档「字段」各表即**最终契约**（已落地）。

### A. 零消费出参 / 参数（2026-09-21 决议：删除）

| 项 | 位置 | 冗余性质（已核实） | 删除后影响 |
|---|---|---|---|
| `list` | `PageResult`（全部列表接口共用） | **双重冗余**：① 两个消费端都**优先读 `records`**（`client/src/api/shared.ts` 的 `recordsOf` = `value.records \|\| value.list \|\| []`；`web/src/api/adapter.ts` 的 `pageRecords` 同构），服务端恒返回 `records` → `list` 分支永不命中；② `getList()` 派生自 `records` 且参与序列化 → **同一数组被 JSON 输出两次，列表响应体积≈翻倍**（20 行/页时最明显）。代码注释自认「待全部消费方切换到 records 后可移除」——**该条件已满足** | 无（无消费方读它） |
| `location` / `description` / `images` | `CanteenInfoVO`（原 `GET /canteens`，**公开端点已随筛选功能下线整体删除**，见 K4） | 小程序**零消费**：`types/canteen.ts` 明示「最小 DTO：消费方仅读 id/name」，`api/canteen.ts` 只映射 id/name；管理端走 `/admin/canteens`（另一 VO `CanteenAdminVO`），不经此 VO。**注意**：§7.31 保留的是 `canteen.location` **列**，与「是否作为公开出参」是两件事 | 无（`GET /canteens` 收敛为 id/name 最小字典） |
| `stallId` | `DishQueryReq`（`GET /dishes`） | 两个消费端**零发送**：首页 / 搜索的筛选只有「食堂 / 价格 / 大类」；管理端走 `/admin/dishes`。判据同 §7.23 第 6 条（已据此删除 `GET /reviews` 的 `stallId` / `canteenId`） | 无（档口筛选入口本就不存在） |
| `sortBy` / `sortOrder`（**整参数删除**） | `DishQueryReq` | 端上 `DishSortBy` 四值中**仅 `heat` 被首页默认流发送**，而 `heat` 分支内部固定 `DESC`（`sortOrder` 对任何取值都无效果）；`rating` / `price` / `created_at` 零调用方（端上无排序入口，口径见 §7.17 第 2 条「不设排序入口」） | 排序口径收敛为**服务端恒热度**，见 C 项 |
| 响应 `page` / `pageSize` | `PageResult` | 两个消费端**只发不读**（端上自持页码，结束判据用「本页条数 < `pageSize`」；`web` 同） | **保留**（低成本自描述元数据，删无收益、排障仍有价值） |

### B. 文档过期项（2026-09-21 已就地修正）

| 项 | 原文 | 现状 |
|---|---|---|
| `stall.window_no` | 数据表写「读 `name` / `floor` / `window_no`」 | `DishMapper.xml` 的 `publicDishColumns` **明确不选 `window_no`**（§7.30 已删该出参，注释亦声明「公开出参不含 …window_no…故此处不选」）→ 已改为只读 `name` / `floor`（**2026-09-22 列表 / 详情拆分后**：列表仅读 `name`、详情加 `floor`） |
| `keyword` 匹配范围 | 「同时匹配菜名 `name` 与搜索别名 `alias`」 | `DishMapper.xml` 实为**四处**模糊匹配：菜名 / 别名 / 档口名 / 食堂名 → 已补全 |

### C. 排序口径（2026-09-21 决议：收敛为单一热度口径）

`GET /dishes` 的排序**恒为服务端热度口径**（`DishMapper.xml` 的 `heatScoreExpr` 倒序）；`sortBy` / `sortOrder` 参数已删除（见 A 组）。默认流 / 筛选流 / 搜索流三路排序一致，端上不传排序参数——合 §7.17 第 2 条「热度优先、不设排序入口」。

### D. 列表 / 详情出参拆分（2026-09-22 用户拍板：拆）

**背景（问题）**：拆分前列表与详情**共用 `DishVO`**（`DishDetailVO extends DishVO`，仅多 `ratingDistribution`）→ `GET /dishes` 每行下发 15 字段，其中 **8 个在列表链路零消费**：`ratingCount`、`description`、`floor`、`dietType`、`ingredients`、`flavorTags`、`serveTemp`、多图（`images[1..]`）；列表真实消费仅 **8 项**（`id` / `name` / 首图 / `price` / `originalPrice` / `avgRating` / `canteenName` / `stallName`）。20 行/页 ≈ **每页多下发约 160 个字段值**，且菜品属性维度每增一项就线性放大——用户判定「列表回传详情专属字段」**不合理**。

**决议：拆为两个 VO，各自只装自己的消费集**：

| VO | 使用端点 | 字段集 |
|---|---|---|
| `DishListItemVO` | `GET /dishes`（首页网格 + 搜索结果），**8 字段** | `id` / `name` / `coverImage` / `price` / `originalPrice` / `avgRating` / `canteenName` / `stallName` |
| `DishDetailVO` | `GET /dishes/{id}`（详情页；字段集以 `docs/feature/client-菜品详情.md` 为真源） | `id` / `name` / `price` / `originalPrice` / `description` / `images[]` / `stallName` / `canteenName` / `floor` / `avgRating` / `ratingCount` / `dietType` / `ingredients` / `flavorTags` / `serveTemp` + `ratingDistribution` |

- **实现要求**：`GET /dishes` 一律返回 `DishListItemVO`（**列表不再有 `images` 数组，只有 `coverImage` 单值**）；详情接口保持 `DishDetailVO`；`DishMapper.xml` 按场景拆两份列清单（`listDishColumns` / `detailDishColumns`），**禁止列表查询图省事选全列**；
- **端上连带**：`client/src/types/dish.ts` 拆 `DishListItem` / `DishDetail`；`api/dish.ts` 的 `toDish` 按列表 VO 重写映射（删掉 `images` / `ratingCount` / `description` 等列表不消费字段的映射）；`Dish.image` 派生字段随之取消，卡片直读 `coverImage`；
- **原结论作废**：2026-09-21「登记为有意过度下发、保留不拆」**作废**——其收益仅「少一次查询」，代价却是**全部列表请求永久多传详情字段**；
- **同步项（本次同步范围 = `docs/feature` + `docs/ui`）**：`docs/feature/client-菜品详情.md`（`DishDetailVO` 口径）、`docs/feature/client-搜索.md`（列表行改 `DishListItemVO`）、`docs/feature/README.md`（VO 索引）须同步（进度见「与当前代码的差异」）。

### E. 食堂字典端点合并（2026-09-21 决议：合并；2026-09-22 整体删除）

依据（消费链已核实）：

- `GET /canteens` → `api/canteen.ts` `getCanteenList()` → `stores/dish.ts` `fetchCanteens()` → `canteenList` → **首页 / 搜索页 `FilterBar` 食堂下拉**（按 id 筛选、按 name 回显）；另有 5 分钟节流后台刷新 `refreshCanteensIfStale()`（保证管理端改名最终可见）；
- `GET /canteens/all` → `getCanteensWithStalls()` → 唯一消费方 `pages/feedback/useFeedback.ts`（反馈页「推荐菜品」位置两级联动）。

**结论：不是无意义，但设计价值退化。** 出参收敛为 `id` / `name`（A 组）后，`CanteenInfoVO` 恰为 `CanteenWithStallsVO` 食堂层的**子集投影**——两端点在「食堂字典」能力上完全重叠，`/canteens` 仅剩「**不带档口树**」这一载荷差异。该差异有实际价值：`listWithStalls()` 需额外查全部档口、批量算 `avgRating`、拼图片绝对 URL（`CanteenServiceImpl.java:56-67`），而首页只需 7 条食堂 × 2 字段。

**决议（2026-09-21 修订：由「保留」改为「合并」）**：一个端点能表达的能力不留第二个公开端点——多一个接口就多一份契约维护成本，且调用方改造只是一行。收敛为**唯一公开食堂字典端点 `GET /canteens`**，新增可选参数 `include=stalls`：

1. **不传 `include`**：返回食堂字典 `id` / `name`（即现 `CanteenInfoVO` 收敛后的形态）→ **首页 / 搜索调用不变**（仍 `get('/canteens')`）；
2. **`include=stalls`**：返回含档口树（即现 `/canteens/all` 的语义）→ **反馈页只改调用方式**：`get('/canteens', { include: 'stalls' })`，端上映射逻辑不变；
3. **删除 `GET /canteens/all`**：`CanteenController` 去掉该方法；`project_spec.md` §8 已登记的 `GET /canteens/all` → `GET /canteens?include=stalls` 收敛项，**本次由「待拍板」转为「采纳」**；
4. **连带清理档口层零消费出参**：`stalls[]` 现返回 `location` / `floor` / `windowNo` / `description` / `images` / `avgRating`，而端上只读 `stalls[].id` / `name`（`api/canteen.ts:24-31`）→ 一并收敛，并**删除 `CanteenServiceImpl.listWithStalls()` 的 `batchAvgRating` 批查**（每次调用省一次查询，属白算）；
5. **服务端白算**：`listCanteens()` 仍执行 `imageUrlUtil.parseAndToAbsoluteUrls(canteen.getImages())`（`:49`）——出参收敛后纯属白算，**随出参收敛一并删除**；
6. **Swagger 用途描述订正**：`CanteenController` 的 `@Tag` 写「用于首页、**食堂页、档口详情页**」——「食堂档口独立页」已随 §7.23 第 6 条 ⑤（已下线 8 项）删除，须订正为「首页 / 搜索筛选条 + 反馈页位置联动（`include=stalls`）」。

> **后续（2026-09-22）**：本端点先由 `GET /canteens` **重命名为 `GET /filters`**（见 J 项），随后**随食堂 / 价格筛选全量下线整体删除**（见 K4）——公开侧**不再存在食堂字典端点**。E 项的端点合并结论（`/canteens/all` 已删除、档口树能力并入 `include=stalls`）随之**作废**。

### F. 复核新增（2026-09-21）：`total` 定性修正 + 2 处端上「只写不读」

| 项 | 事实（已核实） |
|---|---|
| `/dishes` 的 `total` | **端上零读取**：首页 / 搜索的结束判据 = 「本页返回条数 < `pageSize`」（`stores/dish.ts:305` / `:354`，注释明示「避免 `total` 语义不一致导致误判到底」）；原 `filterTotal` 已随 MP-05/MP-06 删除；全仓读 `total` 的只有详情评价区（`useDishPage.ts:110`）。**本页原写「按 `total` 判断结束」属错误描述，已就地更正（「操作」第 2 条、响应表 `total` 行、保留说明三处）** |
| 端上 `MixedResult.ratingCount` | **只写不读**：`pages/find/index.vue:286` 把 `ratingCount` 映射进结果项，而 `FindResults.vue:88` 声明 `ratingCount?: number` 后**模板从未渲染**（只渲染 `rating` 与 `originalPrice`） |
| 端上 `Dish.image` | **派生冗余**：`api/dish.ts` 的 `toDish` 同时产出 `image`（= `images[0]`）与 `images`；首页卡片读 `image` |

> 结论：**契约层冗余已清完**（A 组四项已决议删除）。本组三项均属**端上实现层**的零消费 / 派生字段（不动接口）：`total` 的处置 = **保留字段**（理由见「响应」节说明）；另两项的**处置动作统一登记在文末「与当前代码的差异」节**（本组只留事实）。

### G. 首页 UI 重构决议（2026-09-21）

| # | 决议 | 说明 / 影响 |
|---|---|---|
| G1 | **页面改两态结构**：初始态（Banner 完整）+ 吸顶态（标题 / 完整搜索框 / 大类标签栏整体吸顶，Banner 滚出） **（2026-09-22 修订：吸顶容器去掉「标题」，见 I2）** | 现实现仅「暖砖红头部（搜索框）+ 筛选行」，**无 Banner、无标签栏、无整体吸顶** → 本次为首屏结构重构
| G2 | **卡片改 5 段固定排版**，但 **`月售XXX份` 不引入**（用户否决） | 项目无订单 / 销量数据源（管理端 `todayOrders` 曾因「恒为 0、与无下单定位相悖」删除）；卡片实际为 4 段：图 → 菜名 → `食堂 \| 档口` → 星+评分（左）/ 价格（右） |
| G3 | **位置行分隔符改 `食堂名称 \| 档口名称`**（原「食堂 · 档口」），**浅灰纯文字、禁用彩色标签块** | 与首页卡片既有「无彩标签」口径一致，仅分隔符变化 |
| G4 | **主色由暖砖红改为橙色，全站 token 层变更（含 Web 管理端）——2026-09-21 定稿：两档取色** | **填充档** `--color-primary` = `#C2410C`（全站主力用途：填充底 / 图标 / 标签下划线 / 星标 / TabBar 激活图标，兼作承载白字的底色）：其上**白字 5.18:1** ✅、对白卡 **5.18:1** / 对页底 **4.69:1** ✅ ≥3:1；**文字档** `--color-primary-text` = `#B93A0A`（价格 `--color-price`、TabBar 激活文字、选中态文字、**主色浅底上的文字**）：对白卡 **5.72:1** / 对页底 **5.18:1** / 对浅底 `#FCE8D6` **4.81:1** ✅ ≥4.5:1；`--color-primary-soft` = `#FCE8D6`；含品牌色通道的阴影通道随主色 = `194, 65, 12`（`rgba(194, 65, 12, α)`）。**适用边界**：填充档**不得**作主色浅底上的文字（该组合仅 **4.35:1**）——已按此把端上 16 处「主色文字压浅底」切到文字档；Web 深色主题文字档取提亮值 `#F97336`→`#F97316`（实测 ≥4.63:1）。**取色理由（重要）**：初版曾取更亮的 `#EA580C` 作图形档，但实测其**作填充底白字仅 3.56:1、作浅底文字亦仅 3.56:1（两门槛均不达）**，而全站 **21 处**以主色作「填充底 + 白字」→ 故填充档必须取白字安全值，**两档而非三档**。已同步修订 `client-visual-language` delta（两档 + 适用边界 + 浅底场景）与 `project_spec.md` §4 / §7.34。触达 `theme/tokens.ts`、`generated-colors.css`、Web `variables.css`；另：`IconSvg` 传 `var()` 会回落近黑（data-uri 不解析 var），本批已把 59 处图标取色改为 `COLOR_MAP` 真源实色 |
| G5 | **页面背景改「浅米白 → 淡橙顶部渐变」——2026-09-21 定稿** | `--bg-page-grad-from` = `#FFF9F3` → `--bg-page-grad-to` = `#FFEFE0`（取较浅淡橙端，保证图形档对渐变最深处仍 ≥3:1）；渐变以 token 声明并登记于 `ui-token-system` |
| G6 | **Banner 为静态运营位（无后端取数、无推荐算法）——2026-09-21 走查回退修订：背景图单独加载 + 占据页面最顶部区域** | Banner = **顶部整块背景**（左右通栏无间隙、上移「状态栏 + 标题行」两带、**含状态栏背后**，9.5 定稿），作为「知行食记」标题的背景（标题叠加绘制其上，z-index 抬升）；**背景图用 `<image>` 单独加载**（`mode="aspectFill"` 铺满，URL 由页面脚本常量 `BANNER_BG_SRC` 承载——正式资产到位后只改该常量；未配置 / 加载失败回退渐变底 `--color-primary-soft → --bg-page-grad-to`）。**不再用组件手绘背景**：原占位组件 `pages/home/HomeBannerArt.vue` 已按 PR-05 删除（零消费不留存，git 历史可找回）。折叠几何不变：窗口 + 补偿 ≡ H（H = 状态栏高 + 标题行高 + 284rpx），1:1 跟手、无跳变 |
| G7 | **搜索框右侧「筛选」按钮 = 打开既有食堂 / 价格筛选面板（筛选维度不变，与大类标签栏并列、可叠加）——确认** **（2026-09-22 作废：筛选入口与面板整体删除，见 K1）** | 原「两行头部（搜索框一行 + 筛选按钮一行）」作废（`home-filter` delta 已 MODIFIED）；`FilterBar` 的食堂 / 价格维度与 `stores/dish.ts` 的筛选状态沿用，仅改交互载体（文字按钮 + 下拉箭头）与锚定位置 |
| G8 | **硬性约束 6 条**（双列禁三列 / 档口无彩标签 / 搜索框常驻吸顶 / 卡片不截断 / TabBar 仅 2 项 / 不遮胶囊）作为回归检查清单 | 其中「TabBar 仅 2 项」**已是现状**（§2：home / mine），无需改动 |
| G9 | **TabBar 仅首页 / 我的**，首页橙色高亮 | 无改动（现状一致），仅配色随 G4 变化 |
| G10 | **删除首页末尾「贡献卡」（想吃啥没找到？告诉我们）+ 星形尺寸对齐文本**（2026-09-21 走查） | ① 贡献卡整卡删除（含「清除筛选」次级动作与首页贡献入口 → 意见反馈预选「推荐菜品」链路消失），`HomeContent` 的 `filtered` prop / `clear-filter` 事件 / `scopeEmpty` / `goContribute` 及页面侧 `hasFilter` / `onClearFilter` 随之零消费移除（PR-05）；首页内容流以菜品网格 + 触底提示收尾，硬性约束新增第 7 条「无引导 / 补录类组件」；② 卡片第 4 段星形 24 → **30rpx**（24px 网格星形自带留白，光学补偿后与 24rpx 评分文本视觉等高） |

> **后续（2026-09-22）**：G1 / G7 / G8 中与**吸顶范围**、**筛选入口**相关的结论已被推翻——① 吸顶容器不再含「标题」，= 搜索区 + 大类标签栏（见 I2，推翻 G1）；② 首页「筛选」按钮与食堂 / 价格面板**整体删除**（见 K1，推翻 G7）；③ 硬性约束由 6 条扩为 **13 条**（UI 文档 §6），G8 仅作历史基线。G6 的作废情形已由 I1 标注。**G4（暖砖红两档）已被 2026-09-22 用户拍板的暖橙黄色板全量替换**——`#C2410C` / `#B93A0A` / `#EA580C` 与页底 `#FFF9F3` / `#FFEFE0` **自即日起不再使用**（仅作历史留痕），新色值与替换映射见 [docs/ui/client-首页菜品浏览.md](../ui/client-首页菜品浏览.md) §4 / §4.1。

### H. 菜品大类字段决议（2026-09-21）

> **背景**：横向大类标签栏（UI 第 4 项）需要「**大类**」维度——它与 `ingredients` / `flavorTags` / `serveTemp` / `dietType` 这些**属性维度不是一类字段**：属性是**多值、横切**（一道菜可同时是辣、清真、热食），大类是**单值、互斥、全量覆盖**（一个菜品只属一个大类；一个大类含多个菜品）。

**H1 存储形态：选项 1 —— 枚举列（用户拍板）**

- `dish` 表新增 **`meal_type`** 单值枚举列（`VARCHAR`），值域由**后端常量**定义（与 `heatScoreExpr` 同风格：口径只留一处真源）；
- **不新建字典表、不建外键、不恢复任何品类链路**——§7.22 第 1 条（品类整链删除：`category` 表 / `dish.category_id` / `admin/categories` / 后台品类页）**仍然有效**，本次只是「给 dish 加一个字段」；
- 代价：新增 / 修改大类需改后端常量并**发版**（大类为低频变更，可接受）；若将来要后台自助增删，可平滑升级为「字典表 + 外键」形态（另立 change，需先撤销 §7.22 第 1 条）。

**H2 枚举值（键 → 中文标签）**

| 枚举键 | 中文标签 |
|---|---|
| `set_meal` | 套餐盖饭 |
| `stir_fry` | 家常小炒 |
| `noodle` | 面食粉类 |
| `dry_pot` | 香锅干锅 |
| `snack` | 风味小吃 |
| `soup_drink` | 汤饮甜品 |

「全部」不是枚举值——它是端上固定渲染的第一项，对应**不传** `mealType`。

**H3 判定口径（后台录入与数据修正的唯一判据）**

> **按「菜名与做法形态」判，不看主料、不看口味。**

| 形态特征 | 归入 |
|---|---|
| 「饭 + 菜成套」（盖饭 / 套餐） | `set_meal` 套餐盖饭 |
| 「炒 / 烧 / 煮 / 宫保 / 鱼香」等家常热菜 | `stir_fry` 家常小炒 |
| 「粉 / 面 / 馍 / 拉面」（一碗主食） | `noodle` 面食粉类 |
| 「干锅 / 香锅 / 麻辣烫 / 冒菜」 | `dry_pot` 香锅干锅 |
| 「烧烤 / 点心 / 包子 / 烧卖 / 肠粉 / 炸物」 | `snack` 风味小吃 |
| 「粥 / 汤 / 奶茶 / 甜品」 | `soup_drink` 汤饮甜品 |

**已定的 5 处边界**：骨汤麻辣烫 → `dry_pot`；冒脑花 → `dry_pot`；香辣虾 → `stir_fry`；糖醋里脊 → `stir_fry`；烧烤整组（烤五花肉 / 烤茄子 / 羊肉串 / 烤馕 / 烤冷面）→ `snack`。

**H4 归属清单（种子 31 道：全量覆盖、无空类）**

| 大类 | 菜品 |
|---|---|
| `set_meal` 套餐盖饭（3） | 黄焖鸡米饭、招牌烤肉饭、咖喱鸡排饭 |
| `stir_fry` 家常小炒（9） | 宫保鸡丁、水煮牛肉、回锅肉、番茄炒蛋、土豆烧牛肉、香辣虾、糖醋里脊、鱼香茄子、宫保虾球 |
| `noodle` 面食粉类（4） | 牛肉拉面、兰州牛肉面、羊肉泡馍、炒粉 |
| `dry_pot` 香锅干锅（3） | 干锅花菜、骨汤麻辣烫、冒脑花 |
| `snack` 风味小吃（9） | 鲜肉小笼、广式肠粉、烤五花肉、烤茄子、烤冷面、羊肉串、烤馕、鲜虾烧卖、叉烧包 |
| `soup_drink` 汤饮甜品（3） | 皮蛋瘦肉粥、珍珠奶茶、杨枝甘露 |

**H5 落地要求**

1. **库**：`schema.sql` 幂等段以**存储过程**（先判列存在再 `ADD COLUMN`）新增 `meal_type`，**禁直连 ALTER**；`seed_data.sql` 为全部菜品赋值（上表）；
2. **筛选**：`GET /dishes?mealType=<枚举键>`，**白名单校验、非法值 400**（PR-06），精确等值匹配（单值列）；
3. **字典**：新增只读端点 `GET /dishes/meal-types`，返回 `[{ key, label, order }]`，**只含当前有在售菜品的大类**（空类自动隐藏）；
4. **出参**：**公开菜品出参（`DishListItemVO` / `DishDetailVO`）都不加 `mealType`**（卡片不展示 → 零消费即删）；后台 `DishAdminReq` / `DishAdminVO` 增加（录入下拉 + 编辑回填 + 列表筛选）；
5. **端上**：标签栏完全由字典端点驱动（不写死任何标签与中文映射），单选，切换即重置分页；
6. **数据修正（同批）**：珍珠奶茶、杨枝甘露 的 `ingredients` 现误标为 `rice`（米）→ 改正（可新增 `drink` 枚举值或留空），否则详情页「主料」会显示「米」；
7. **合规登记**：本项目属「给 dish 新增字段」，但承担**分类语义**，须在 `project_spec.md` §7.23 **显式登记**「新增单值大类字段替代原品类分类能力」，避免被后续误判为「品类维度复活」；
8. **关联影响**：`GET /dishes` 参数集由 6 项增至 7 项，会**修改** `api-slimming` 已定稿的 `dish-list-query` capability（后者声明「恰为六项」）——已在 change `home-ui-refresh` 的 delta 中一并 MODIFY。**（2026-09-22 后续：食堂 / 价格三参数随 K3 删除后，参数集进一步收敛为 4 项 —— `page` / `pageSize` / `keyword` / `mealType`。）**

### I. 首页顶部 Banner 轮播接口化决议（2026-09-22）

> **用户拍板**：Banner **不再用端上静态资源**（原页面常量 `BANNER_BG_SRC` 的口径作废），改为**后端接口下发 + 多图轮播**；同时**修正首屏结构**为「Banner 整块占满屏幕上部（含状态栏背后、左上角叠标题）→ 搜索区 → 横向大类标签栏 → 双列网格」。**UI 细节（16:10 定档、标题带、吸顶范围）以 [docs/ui/client-首页菜品浏览.md](../ui/client-首页菜品浏览.md) §1 / §1.1 / §5 为唯一真源。**

| # | 决议 | 说明 / 影响 |
|---|---|---|
| I1 | **Banner 接口化（推翻 G6 的「静态运营位」）** | 新增公开只读端点 `GET /banners` → `[{ id, imageUrl }]`；端上轮播渲染，**不写死 URL、不排序、不写死张数**。G6 中「无后端取数」「URL 由页面常量 `BANNER_BG_SRC` 承载」两条**作废**（G6 其余结论——整块占据页面最顶部、含状态栏背后、`aspectFill` 铺满、失败回退占位图——继续有效）；G6「无推荐算法」**仍有效**：本节只做运营位轮播，不引入个性化推荐 |
| I2 | **结构修正：Banner 为正常流首块，吸顶容器只含搜索区 + 标签栏（推翻 G1 与既有折叠几何）** | Banner 整块（含状态栏背后与左上角标题）**随页面滚出即消失**——**不折叠、不定格、不作为吸顶容器背景**；吸顶态顶端**不重复渲染**「知行食记」标题。原「Banner 图层垫在头部三行背后 + 位移补偿（`bannerShift` / `stickyShift` / `contentPadTop`）+ 折叠几何 H = 状态栏高 + 标题行高 + 284rpx」全部作废；两条硬性约束随之改写：搜索框吸顶 → **搜索区 + 标签栏同组吸顶**（UI 文档 §6 第 3 条），新增「Banner 滚出后不残留图片背景」与「吸顶态不重复渲染标题」 |
| I3 | **宽高比锁定 16:10** | Banner 总高 = 屏宽 × 10/16（375 宽 ≈234px）+ 最小高度兜底（≥ 状态栏 + 标题带 + 运营内容最小可视高 ≈120px）；**素材一律 16:10 出图**（混比例会导致轮播切换时块高抖动、吸顶阈值漂移）；加载中 / 失败使用**同高占位图**。完整论证见 UI 文档 §1.1 |
| I4 | **库：新增 `banner` 表（10 → 11 张）** | `banner(id, image_url, sort_order, status, created_at, updated_at)`；`status` 取 `on` / `off`（与 `dish.status` 同风格，**不复活**已随下线删除的 `enabled` / `disabled` 枚举）。落库口径：**只改 `server/src/main/resources/db/schema.sql`（幂等段，判表 / 判列存在再建）与 `seed_data.sql`，禁直连 ALTER** |
| I5 | **管理端录入本期不做** | `/admin/banners` CRUD 与后台页面**本期不落地**（小程序为唯一消费端、运营位数量级极小），素材先由 `seed_data.sql` 维护；需要运营自助录入时**另立 change**（届时新增 `/admin/banners` 并同步 `docs/feature` 相应文档）。**本期不产生任何小程序端写接口** |
| I6 | **契约红线对齐** | 出参仅 `id`（轮播 key）+ `imageUrl`；`sort_order` / `status` 服务端内部用、不出参；**无跳转字段**（端上零点击交互）；空集合返回 `[]`（非 404 / null）；Banner 请求与菜品列表**并行**、Banner 失败不阻塞首屏；统一响应 `{ code, message, data }` / camelCase / 错误码沿用 `project_spec.md` §3 |
| I7 | **与 §0.0「轻运营 / 无运营位」的冲突登记（须技术负责人处理）** | §0.0 现含「轻运营（无运营位，Excel 批量导入推二期）」。本次是**首页顶部轮播运营位的有限恢复**，与 2026-09-13 活动 / 公告（`activity` / `broadcast`）下线**不冲突**（后者是活动与公告业务能力，本次仅是图片轮播位）。该冲突登记**不在本次文档同步范围**（本次仅同步 `docs/feature` + `docs/ui`），如需回写 `project_spec.md` 由技术负责人处理；本节保留该冲突说明以免被后续误判为「运营模块复活」 |
| I8 | **不触碰既有契约** | `GET /dishes` 及其参数（含 `mealType`）、`GET /dishes/meal-types`、当时的菜品公开出参**均不变**；本次只新增一个只读端点与一张表。**后续修订**：`GET /canteens` 改名后随即随筛选功能下线一并删除（J / K 项）；列表出参拆出 `DishListItemVO`（D 项） |

### J. 筛选接口重命名决议（2026-09-22）

> **用户拍板**：原 `GET /canteens`（食堂字典）**重命名为 `GET /filters`（筛选选项接口）**，理由是「用 filter 命名方便后期拓展筛选功能」——不再把端点绑定在「食堂」这一资源语义上。

| # | 决议 | 说明 / 影响 |
|---|---|---|
| J1 | **路径：`GET /canteens` → `GET /filters`** | 顶层公开只读端点，鉴权仍 🔓；`/dishes`、`/dishes/meal-types` 等**路径不变**（筛选项端点与菜品列表端点分工不变） |
| J2 | **出参形态本期不变** | 仍为 `[{ id, name }]`（即原 `CanteenInfoVO` 收敛后的形态）；**不加分组包装层**（`key` / `label` / `options` 只在真正接入第二个维度、且端上消费时才加——零消费即删）。**2026-09-22 修订：`GET /dishes?canteenId=` 参数已随「食堂 / 价格筛选全量下线」删除**（见 K3），本端点亦不再服务筛选面板（见 K4） |
| J3 | **删除 `include=stalls` 与档口树出参（2026-09-22 用户拍板：方案 B）** | 删除范围：①参数 `include=stalls`；②档口树出参（食堂节点下的 `stalls[]` 及档口节点 VO `StallDetailVO`）；③后端 `CanteenServiceImpl.listWithStalls()` 及其中 `batchAvgRating` 批查（随出参一并删除，属白算）；④端上 `api/canteen.ts` 的 `getCanteensWithStalls()` 与 `types/canteen.ts` 的 `CanteenWithStalls` / 档口节点类型；⑤反馈页 `useFeedback.ts` 中消费 `canteenTree.stalls` 的档口级联逻辑。删除后 `GET /filters` **无参数、恒返回食堂选项扁平列表**。**反馈页「位置」字段的替代形态以 `docs/feature/client-意见反馈.md` 为真源**（本文件不定义其表单结构），但删除后**不得阻塞反馈提交** |
| J4 | **落地要求（禁留旧路径别名）** | ①后端 `CanteenController` 的 `@GetMapping("/canteens")` → `@GetMapping("/filters")`，并按 J3 **去掉 `include` 参数**（`listFilters()` 直返食堂选项）；②`SecurityConfig` 公开白名单 `/canteens/**` → `/filters/**`；③端上 `client/src/api/canteen.ts`：`getCanteenList()` 的 `'/canteens'` → `'/filters'`，`getCanteensWithStalls()` **整体删除**（含 `types/canteen.ts` 对应类型）；④同步 `docs/feature/client-意见反馈.md` 中 `GET /canteens?include=stalls` 的引用（**本次同步范围 = `docs/feature` + `docs/ui`**；`api-design.md` / `project_spec.md` 等不在范围内）；⑤**不保留 `/canteens` 兼容别名**（无第三方消费方，留别名即产生两份契约真源） |
| J5 | **命名口径（2026-09-22 修订）** | 原口径「筛选接口 = 首页 / 搜索筛选面板的选项数据源」**已不成立**——筛选面板全量下线后（K1 / K2），该端点在公开侧零消费。路径名 `filters` 由用户拍板保留、作为**后续筛选能力扩展的预留命名**（届时新维度优先并入本端点；菜品大类仍走 `GET /dishes/meal-types`） |

> **J 组最终结局（2026-09-22，见 K4）**：筛选功能全量下线后，食堂字典在公开侧**零消费** → `GET /filters`（连同其前身 `GET /canteens`）**整体删除**，J1~J4 的「改名 / 保留 `include=stalls`」动作**全部作废**（端点已不存在，无路径可改、无参数可留）；J5 的命名约定仅作历史留痕。

### K. 食堂 / 价格筛选全量下线决议（2026-09-22）

> **用户拍板（经商议）**：**首页与搜索页的食堂筛选、价格筛选全量下线**——首页不再有「筛选」按钮与筛选面板，搜索页（find）不再有食堂 / 价格筛选胶囊，相关接口能力随之收敛。**UI 侧同步口径见 [docs/ui/client-首页菜品浏览.md](../ui/client-首页菜品浏览.md) §1 / §2.1 / §6。**

| # | 决议 | 说明 / 影响 |
|---|---|---|
| K1 | **首页筛选面板整体删除** | 删除 `pages/home/HomeFilterPanel.vue` 组件、搜索框右侧「筛选」文字按钮（下拉箭头）与面板开关（`filterOpen` / `toggleFilterPanel` / `closeFilterPanel`）及事件（`@filter` / `@canteen-select` / `@price-select` / `onCanteenSelect` / `onPriceSelect` / `selectedCanteenId`）。**食堂与价格两个维度一并下线**（用户拍板「价格筛选也全面下线」）→ 首页**不再有任何筛选入口** |
| K2 | **搜索页（find）筛选栏一并删除** | `pages/find/FilterBar.vue`（「全部食堂」+「全部价格」两颗胶囊及各自下拉面板，模板内已无第三颗真控件）**零消费、整体删除**；`pages/find/index.vue` 删除 `findCanteenId` / `findPrice` 状态、`onFindCanteenSelect` / `onFindPriceSelect`，以及 `doMixedSearch` 的 `canteenId` / `minPrice` / `maxPrice` 传参与退出结果态时的筛选重置。搜索页头部回到「输入框 + 结果」 |
| K3 | **接口收敛：`GET /dishes` 删除三个筛选参数** | 删除 `canteenId`（按食堂筛选）、`minPrice` / `maxPrice`（价格区间）——两页下线后**全端零发送**（`client/src/types/dish.ts` 的 `DishQuery` 三字段、`api/dish.ts` 的 `searchDishesPage` 映射、`utils/price-filter.ts` 整体零消费）。服务端连带删除 `DishQueryReq` 三字段、`DishService` / `DishServiceImpl` 对应条件与 `DishMapper.xml` 的相关 `if` 分支。**参数集由 7 项收敛为 4 项**：`page` / `pageSize` / `keyword` / `mealType` |
| K4 | **`GET /filters`（原 `GET /canteens`）整体删除** | 筛选入口全量下线后，食堂字典在公开侧**零消费** → 端点连同下游一并删除：后端 `CanteenController` 的公开 `GET /canteens` 方法、`CanteenInfoVO` 公开出参、`CanteenServiceImpl` 的 `listCanteens()` 与档口树方法、`SecurityConfig` 白名单 `/canteens/**` 项；端上 `api/canteen.ts`（`getCanteenList()` / `getCanteensWithStalls()`）与 `types/canteen.ts` 整体删除。**不保留任何兼容路径**。J 组的改名（`/canteens` → `/filters`）与 J3 的 `include=stalls` 删除随本次**整体作废**。**反馈页「位置」不再有公开字典数据源**——其字段形态（自由文本填写等）**以 `docs/feature/client-意见反馈.md` 为真源**，且**不得阻塞反馈提交** |
| K5 | **端上连带清理（零消费即删）** | `stores/dish.ts`：`canteenList` / `fetchCanteens` / `refreshCanteensIfStale` / `CANTEEN_REFRESH_INTERVAL_MS` / `filterTab` / `filterList` / `filterLoadingMore` / `filterPageLimited` / `filterPrice` / `filterError` / `setHomePrice` / `defaultFilterTab` / `fetchFilterDishes` / `loadMoreFilterDishes` 等筛选专属状态与方法（**以实际消费点 grep 结果为准，删净不留死代码**）；`types/filter-tab.ts`（`FilterTab`）与 `types/dish.ts` 的 `DishQuery` 三字段删除；`utils/price-filter.ts` 删除；`api/canteen.ts` 与 `types/canteen.ts` **整体删除**（`getCanteenList()` / `getCanteensWithStalls()` 与档口树类型均零消费，见 K4）；0 字节残留空文件 `components/FilterBar.vue` 删除 |
| K6 | **不受影响（保留）** | ① **大类标签栏**：`GET /dishes/meal-types` 与 `mealType` 参数保留（独立维度，不在本次下线范围）；② **搜索关键词**：`keyword` 保留，仍**同时匹配菜名 / 别名 / 档口名 / 食堂名**（关键词搜食堂名仍可用）；③ 卡片上的 `食堂名 \| 档口名` 展示保留（`DishListItemVO.canteenName` / `stallName`）；④ `canteen` / `stall` 两张**表**保留（菜品展示、管理端录入与详情页位置表达仍需要），但**公开侧不再有食堂字典端点**（K4） |
| K7 | **防回退** | 首页**不得再出现筛选按钮 / 筛选面板**、搜索页**不得再有食堂 / 价格筛选胶囊**；`GET /dishes` 的 `canteenId` / `minPrice` / `maxPrice` **SHALL NOT 回流**（恢复须重新拍板——筛选不属「功能聚焦四条主线」） |

## 与当前代码的差异

**端上实现层清理与结构落地（不动接口）**——以下各项**已于 2026-09-22 全部落地**：

| # | 项 | 事实 | 处置（已落地） |
|---|---|---|---|
| 1 | `pages/find` 的 `MixedResult.ratingCount` | 只写不读：映射进结果项但 `FindResults.vue` 模板从未渲染（只渲染 `rating` 与 `originalPrice`） | **已删除**映射与类型声明（同 MP-05/MP-06 对 `filterTotal` 的处理） |
| 2 | `Dish.image` | 派生冗余：`api/dish.ts` 的 `toDish` 同时产出 `image`（= `images[0]`）与 `images`；首页卡片读 `image` | **已收敛**：列表改读 `coverImage`（由 `DishListItemVO` 直出，端上不再有 `images` 数组与 `image` 派生字段） |
| 3 | 首页顶部结构 | 旧实现为「Banner 垫在头部行背后 + 上滑折叠 + 下缘定格为背景」 | **已落地新口径**：固定标题带（`position: fixed`、与微信胶囊同带、`z-index` 高于容器）+ Banner 正常流首块（16:10、滚出即消失）+ 吸顶容器 = 搜索区 + 标签栏（锁定于标题带下沿，阈值 = Banner 总高 − 标题带高）；原折叠几何与 `bannerShift` / `stickyShift` / `contentPadTop` 位移补偿已删除 |
| 4 | 搜索区结构 | 旧实现为「单颗搜索框（右侧筛选按钮）」 | **已落地**：左搜索胶囊 + 右独立「搜索」按钮（同高、间距 `--spacing-sm`、同为 `--radius-pill`，按钮填充档主色 + 白字）；纵向间距由吸顶容器 padding 承担（Banner→搜索区 `--spacing-md`、搜索区→标签栏 `--spacing-sm`、标签栏→网格 `--spacing-md`），与 UI 文档 §1.2 一一对应 |
| 5 | 菜品卡样式 | 卡片四段样式块缺失 → 卡片渲染为无样式文本 | **已修复**：`.card-info` / `.card-name` / `.card-stall` / `.stall-text` / `.card-meta` / `.card-rating` / `.rating-text` / `.card-price` 等样式已补全 |
| 6 | `AppHeader` 的 `home` variant | 首页头部改用页面自持后零消费 | **已删除**（variant 收敛为 `search` / `default`，相关样式与 props 同批移除） |
| 7 | 标题带与胶囊的垂直对齐 | 标题带行高原取「胶囊高」（32px），致标题中心比胶囊中心高 ≈6px | **已修**：行高改取 `navBarHeight`（= `(胶囊.top − 状态栏高) × 2 + 胶囊高`，保证胶囊在行内垂直居中）→ 标题与胶囊同一条水平线 |
| 8 | 顶部表面过渡（透明 ↔ 不透明） | 原为硬切换：标题带永久透明（网格会透到标题后）、容器常驻切片 | **已落地方案 C「纱式淡出」**：标题带改为**与页面底同源的渐变切片**按 `scrollTop` 在 `[H_b − 60, H_b]` 由 0 → 1 渐显（只柔化 Banner、不淡化标题）；容器保持「初始透明 → 锁定前后切同源切片」；完整口径见 UI 文档 §1.3 |
| 9 | **切大类标签「弹回首页顶部」（bug，2026-09-22）** | 根因：`setHomeMealType` → `fetchHomeDishes(true)` 先 `homeList = []`，且 `HomeContent` 在 loading 期间整块不渲染 → 内容高度塌为 0 → `scroll-view` 把滚动位置钳回 0 | **已修**：切大类改走 `keepList = true` + 独立 loading key（`homeSwap`）——新数据到手前**旧列表留在屏上**（stale-while-revalidate），内容不塌陷 → 滚动位置自然保持。§5 边界行为「点标签不重置滚动位置」由此**真正成立** |
| 11 | 字号 / 间距刻度收敛（2026-09-22） | ① 卡片位置行、评分文字 24rpx（窄屏折合 ≈10px，低于 12px 可读下限）；② 卡片组内两处间距同为 8px（无分组层级）；③ 搜索胶囊内「图标↔文字」4px（过贴）与「搜索」按钮左右内距 12px（按钮偏窄）；④ 标签栏光学间距 上 18px / 下 26–33px（不对称） | **已落地**：① 位置行、评分文字 → `--font-body`(28rpx)，星形 30 → 34rpx（保持「星形 = 文字 + 6rpx」光学口径）；② 菜名→位置行 `--spacing-xs`(4px)、位置行→评分行 `--spacing-sm`(8px)；③ 胶囊内 gap → `--spacing-sm`(8px)、按钮左右内距 → `--spacing-lg`(16px)；④ 标签文字行内上偏置 24rpx（下划线仍紧随 4px）→ 光学间距 ≈20px / ≈24px。口径见 UI 文档 §1.2 / §2 / §3 / §6 第 17 条 |
| 10 | 顶部间隙层级 / 字号 / 滚动开销（清扫） | ① 旧口径块间 12px `--spacing-md` 与卡片间距同值 → 吸顶块与首行卡片糊在一起；② 「搜索」按钮文案 24rpx **小于**相邻占位 28rpx；③ 页面侧未下发 `--capsule-h`（回落写死 32px，与真机胶囊高可能不符）；④ 触底提示 22rpx 低于 12px 可读下限；⑤ `@scroll` 每帧无条件写值 | **已修**：① 块间改 `--spacing-lg`(16px)、块内保持 `--spacing-sm`(8px)，确立「块间 > 卡片间距 > 块内」层级（UI 文档 §1.2）；② 按钮文案 →`--font-body`；③ 容器内联下发真实胶囊高；④ 触底提示 →`--font-small`；⑤ 滚动回调量化到整数 px + 值未变短路，位移改 `translate3d` 抬升合成层，`lower-threshold=300` 提前预取，卡片补 `hover-class` 按压反馈（§4.9 同族） |

> 本节按 2026-09-22 定稿规则设立：**正文只写最终设计形态，与现有代码的差异一律写在本节**（含「已拍板未落地」条目）；差异清零时保留标题并写「无」。**正文（含「决议登记」A~K）只承载最终设计形态与历史决议，任何「代码还没改成这样」的进度的说明都只出现在本节**。
> **契约层（接口 / 字段 / 库表）差异（2026-09-22）**：**I 组已落地**——交付 = ①`schema.sql` 幂等建 `banner` 表 + `seed_data.sql` 灌初始素材（`docs/database.md` 同步 11 张表）；②后端 `banner` 模块四层（controller / service(+impl) / mapper / entity / dto）与 `GET /banners`；③端上：Banner 改接口轮播（16:10）、删除端上常量 `BANNER_BG_SRC`、首屏结构改为「Banner 正常流首块 + 搜索区/标签栏吸顶」（含移除既有折叠 / 位移补偿逻辑）。**UI 侧同步项见 [docs/ui/client-首页菜品浏览.md](../ui/client-首页菜品浏览.md) §5 注记。**
> **D 项（列表 / 详情出参拆分）已落地（2026-09-22）**：`DishVO` 已删除，`GET /dishes` 已改返 `DishListItemVO`（8 字段，`images[]` → `coverImage`，不再下发 `description` / `floor` / `ratingCount` / `dietType` / `ingredients` / `flavorTags` / `serveTemp`），`DishMapper.xml` 已拆 `listDishColumns` / `detailDishColumns`，端上 `types/dish.ts` 已拆 `DishListItem` / `DishDetail`、`api/dish.ts` 的列表 / 详情映射已重写。**关联文档须同步（限 `docs/feature` + `docs/ui`）**：`docs/feature/client-菜品详情.md`（详情 VO 口径）、`docs/feature/client-搜索.md`（列表行字段）、`docs/feature/README.md`（VO 索引）。
> **K 项（食堂 / 价格筛选全量下线 + 食堂字典端点删除）已落地（2026-09-22）**：均未保留——端上 `pages/home/HomeFilterPanel.vue` 与搜索框「筛选」入口、`pages/find/FilterBar.vue` 与 `findCanteenId` / `findPrice`、`stores/dish.ts` 筛选链路与 `canteenList`、`api/canteen.ts` / `types/canteen.ts`、`types/filter-tab.ts`、`utils/price-filter.ts`、0 字节 `components/FilterBar.vue`；后端 `DishQueryReq.canteenId` / `minPrice` / `maxPrice` 与 `DishMapper.xml` 对应条件、`CanteenController` 的公开 `GET /canteens`、`CanteenInfoVO`、`SecurityConfig` 白名单 `/canteens/**`。均已按 K1~K5 删除；**`GET /filters` 已不存在**（J 组改名动作随之作废）。**关联文档须同步（限 `docs/feature` + `docs/ui`）**：`docs/feature/client-搜索.md`（find 筛选栏下线 + 参数收敛 + 列表行 VO）、`docs/feature/client-意见反馈.md`（位置字段不再依赖字典）、`docs/feature/README.md` 与 `docs/ui/client-意见反馈.md`。
> 其余历史项均已落地（2026-09-21 change `api-slimming` / `home-ui-refresh`）：A 组四项删除、C 组排序收敛、H 组大类字段（云库 `meal_type` 分布已核验 9/9/4/3/3/3）；E 组端点合并当时已落地，**其端点已于 2026-09-22 随筛选功能整体删除**（见 K4）。
> **唯一遗留（跨文档待定稿，不属本模块范围）**：反馈页「推荐菜品」的**位置字段形态**——食堂字典端点已删、档口树已删，端上当前为**最小实现**（位置选择器仅剩「其他」自定义输入，**不阻塞提交**）；其最终形态（自由文本等）以 `docs/feature/client-意见反馈.md` 为真源。
> **不在本次同步范围**：`project_spec.md` §0.0「轻运营 / 无运营位」冲突登记与 §8 契约登记、`docs/api-design.md`、`docs/database.md` 表数（10 → 11）——本次文档同步范围限定 `docs/feature` + `docs/ui`。
