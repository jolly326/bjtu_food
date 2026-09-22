# 搜索（A-03）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免登录）
> 返回：[功能总览](./README.md)

## 干什么

按关键词找菜。页面**只有三件事**（2026-09-22 用户拍板）：**搜索**（关键词检索菜品）、**搜索记录**（**纯本地**，不上服务端）、**猜你喜欢**（**后端随机推送菜品**——无推荐算法、无热度排序、无个性化，**完全随机**）。命中规则见「接口」节（`DishMapper.xml` 的 LIKE 匹配路）。

## UI

> 📐 页面 UI 设计稿已拆出 → [client-搜索.md（docs/ui）](../ui/client-搜索.md)（2026-09-22 拆分；**UI 口径以该文件为唯一真源**）

## 操作

1. 进入页（未输入）→ 展示「**搜索记录**」与「**猜你喜欢**」两块；**猜你喜欢由后端随机推送**（每次进入随机一批在售菜品名，见「字段」节）。
2. 输入关键词 → 调 `GET /dishes?keyword=…` → 展示结果列表；该词**写入本地搜索记录**（去重、最新在前、上限 4 条）。
3. 点「猜你喜欢」chip → 以该菜名发起搜索（并写入本地记录）。
4. 点「搜索记录」条目 → 以该词发起搜索。
5. 「搜索记录」**只存本地**（`uni.setStorageSync`）；**不上服务端、不跨设备同步**；支持清空；无记录时整块不渲染。
6. 点结果卡片 → 进菜品详情（A-04）。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/dishes?keyword=…` | 🔓 公开 | 关键词搜索：**同一端点**复用于首页网格与搜索（**原「筛选流」已随食堂 / 价格筛选全量下线，2026-09-22**）；对菜名 / 搜索别名 / 档口名 / 食堂名**四处**模糊匹配（`DishMapper.xml`） |
| GET | `/dishes/for-you`（原 `/dishes/hot-search`） | 🔓 公开 | **猜你喜欢（2026-09-22 用户拍板：改名 + 语义升级）**：**当前实现 = 每次请求随机抽取在售菜品名**下发（`[{ keyword }]`）——不看热度、不排序、**不做推荐算法**；**契约留扩展位**——将来升级为个性化 / 推荐算法时**端上契约不变**（仍 `keyword` 列表），只换服务端取数逻辑。⚠️ **必须去掉该端点的响应缓存**（现为 `@Cacheable(CACHE_DISH_HOT_SEARCH)`），否则「每次随机」退化为「全站同一份」。**改名 / 去缓存 / VO 与端上类型改名须三端 + 文档同批落地**（见文末差异节） |

## 字段

### 请求 · `GET /dishes`

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `keyword` | string | 否 | 关键词，**同时匹配菜名 `dish.name` / 搜索别名 `dish.alias` / 档口名 / 食堂名**（`DishMapper.xml` 四处模糊匹配；空则不筛选） |
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数（**搜索页固定传 50**）；服务端归一化上限 **100**（`PageUtil.MAX_PAGE_SIZE`，超限截断），实际生效值以响应 `page` / `pageSize` 为准 |

> **已删除参数（SHALL NOT 回流）**：`canteenId` / `stallId`（食堂 / 档口筛选）、`minPrice` / `maxPrice`（价格区间）——随食堂 / 价格筛选全量下线删除（2026-09-22，见 [client-首页菜品浏览](./client-首页菜品浏览.md) K3）；`sortBy` / `sortOrder`——排序恒为服务端热度口径（2026-09-21 收敛）。
> **`GET /dishes` 完整参数集 = `page` / `pageSize` / `keyword` / `mealType`**（`mealType` 供首页大类标签栏，搜索页不传）；权威参数表见 [client-首页菜品浏览](./client-首页菜品浏览.md) §字段。

### 响应 · `GET /dishes`（`data` = `PageResult<DishListItemVO>`）

**分页外壳字段**：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | DishListItemVO[] | 当前页命中菜品行数组（端上以它为准） |
| `total` | number | 命中总条数（服务端同口径统计）。**端上零读取**——搜索结果态判据 = 本次返回条数 / 结果数组是否为空（见文末「与当前代码的差异」） |
| `page` / `pageSize` | number | 服务端归一化后的实际页码 / 每页条数 |

**行字段 `DishListItemVO`（列表专用 8 字段；2026-09-22 列表 / 详情出参拆分，见 [client-首页菜品浏览](./client-首页菜品浏览.md) D 项）**：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 菜品 ID（列表 key / 跳转详情用） |
| `name` | string | 菜品名称 |
| `coverImage` | string | **封面图 URL**（= 原 `images[0]`；无图为空串）——列表只渲染首图，**不再下发图片数组** |
| `price` | number | **现价**（单位：分；端上转「元」展示，已含折扣） |
| `originalPrice` | number \| null | **原价**（单位：分，折扣前）；`originalPrice > price` 即表示有折扣、端上在原价上加删除线；无折扣为 null |
| `avgRating` | number | 平均评分（缓存值，口径 = 仅未隐藏评价）——结果卡只渲染评分，**不发 `ratingCount`** |
| `canteenName` | string | 食堂名称（**首页卡片第 3 段 + 搜索页结果卡底部位置行**；2026-09-22 复核纠正，见下注） |
| `stallName` | string | 档口名称（同 `canteenName`） |

> **8 字段在两端均全量消费（2026-09-22 复核纠正）**：搜索页结果卡**同样渲染位置行**——`FindResults.vue` 的底部行取 `item.sub`，由 `pages/find/index.vue` 用 `[d.canteen, d.stallName].join(' · ')` 组装。此前「搜索结果卡不渲染 `canteenName` / `stallName`」的描述**与代码相反**，已纠正；因此 [client-首页菜品浏览](./client-首页菜品浏览.md) D 项「不为搜索页另拆 VO」的判据成立，**不得**再以「搜索页多下发两个字段」为由拆分 VO。
> **详情专属字段不再随列表下发**：`description` / `images[]` / `floor` / `ratingCount` / `dietType` / `ingredients` / `flavorTags` / `serveTemp` 的字段表与语义**见 [client-菜品详情](./client-菜品详情.md)**（`DishDetailVO` 15 字段 + `ratingDistribution`），本文档不重复维护。
> **历史上已删出参（SHALL NOT 回流）**：`promoPrice` / `status` / `createdAt` / `canteenId` / `stallId` / `viewCount` / `tags` / `spiceLevel` / `region` / `windowNo` / `updatedAt` / `latitude` / `longitude`（2026-09-18 ~ 09-20 连续精简）；搜索结果卡片与详情页均**不显示距离**（无定位、无 Haversine）。

### 响应 · `GET /dishes/for-you`（`List<GuessLikeVO>`；原 `GET /dishes/hot-search` / `HotSearchVO`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `keyword` | string | **猜你喜欢词条 = 随机抽取的在售菜品名**；**端上唯一消费字段**（chip 文案 + 点击起搜） |

> **语义（2026-09-22 定稿）**：服务端**随机抽取**在售菜品（`status='on'`）的 `name` 下发——**不按热度排序、不做个性化、不与用户历史相关**（「完全随机」即需求本身）；条数由**服务端固定**（建议 6–10 条），端上**不写死条数与文案**、按返回渲染、空数组则整块不渲染。
> **⚠️ 该端点的响应缓存必须去掉**：现实现为 `@Cacheable(CACHE_DISH_HOT_SEARCH, key = "'all'")` ——「随机推送」语义下会让所有用户 / 所有次拿到**同一份**列表，与需求直接冲突；实现时须移除缓存注解，并核对缓存名是否还有其它使用者。
> **出参只有 `keyword`（2026-09-21 拍板、2026-09-22 落地）**：原 `heat`（派生热度分）**已删除**——随机语义下更无保留价值；小程序（`types/dish.ts` 早已收敛为 `{ keyword }`、`api/dish.ts` 映射时丢弃）与管理端（`web/src` 全仓无命中）**全端零消费**，属「零消费即删」。`docs/api-design.md` 的「代码待落地」已同步为已落地。
> **零消费即删**：若将来 chip 要展示图片 / 价格，再按端上真实消费扩字段（不为「以后可能用到」预埋）。

## 数据（读取）

| 表 | 读什么 | 中文解释 |
|---|---|---|
| `dish` | `name` | 关键词匹配对象：菜名。**2026-09-22 用户拍板：\`dish.alias\` 全局删除（菜品无需昵称）**——别名不再参与匹配，**匹配收敛为「菜名 / 档口名 / 食堂名」三处**（代码待落地，见文末差异节） |
| `dish` | `name`、`status` | **猜你喜欢**：随机抽取 `status='on'` 的菜品名（2026-09-22 起**不再读热度列**，热度分也已不出参） |

## 与当前代码的差异

| # | 项 | 事实 | 处置 |
|---|---|---|---|
| 1 | find 页筛选栏（**已落地**） | **已删除**：`pages/find/FilterBar.vue`、`findCanteenId` / `findPrice`、`doMixedSearch` 的 `canteenId` / `minPrice` / `maxPrice` 传参均已清零（`pages/find/index.vue` 注释：「搜索页不再持有任何筛选状态」） | — |
| 2 | 列表行出参（**已落地**） | **已拆为 `DishListItemVO` 8 字段**（`images[]` → `coverImage`）；搜索页映射同步直读 `d.coverImage` | — |
| 3 | **`total` 读法** | 端上零读取（结果态判据不使用它） | **保留字段**，不改（口径见响应表） |
| 4 | `hot-search` 出参 `heat`（**已落地 2026-09-22**） | 服务端原返回 `keyword` + `heat`；端上 `types/dish.ts` 早已收敛为 `{ keyword }`、`api/dish.ts` 映射时丢弃，管理端全仓零命中 | **已删除**：`HotSearchVO.heat`、`DishMapper.xml` 的 `AS heat` 投影（排序改用 `heatScoreExpr` 表达式）、service / mapper / controller 注释与 Swagger 描述同步；`docs/api-design.md` 「代码待落地」→ 已对齐 |
| 6 | 「猜你喜欢」改名 + 语义 + 去缓存（**已落地 2026-09-22，change `search-page-refresh`**） | 原为路径 `GET /dishes/hot-search`、`HotSearchVO`、**带 `@Cacheable(CACHE_DISH_HOT_SEARCH)`**、按热度派生 TOP10；端上 `HotSearch` / `getHotSearch()` / `hotSearchList` / `fetchHotSearch`、UI 文案「猜你想搜」 | **已落地**：① 路径 → **`/dishes/for-you`**（旧路径已删除，无兼容别名）；② 服务端 `HotSearchVO` → **`GuessLikeVO`**、**去缓存**（`CACHE_DISH_HOT_SEARCH` 常量同批退役）、取数改 **`ORDER BY RAND() LIMIT #{limit}`**（随机抽取在售菜品）；③ 端上 `GuessLike` / `getGuessLike()` / `guessLikeList` / `fetchGuessLike`、UI 文案 → **「猜你喜欢」**（chip 取色改浅黄底 + 深棕字） |
| 7 | 搜索页头部（**已落地 2026-09-22**） | 原为 `AppHeader variant="search"`（橙色整条 header + 内嵌输入框） | **已落地**：顶部改为两段式——`AppTitleBand`（返回 icon 占原标题位、与胶囊同一水平带）+ `SearchBar`（与首页**同源同款**：左搜索胶囊 + 右「搜索」按钮）；`AppHeader` 的 `search` variant 已退役。**色值**按暖橙黄色板 token 引用（`--bg-soft-yellow` / `--text-body`，带 fallback）；**色板全站替换属另一 change** |
| 8 | `dish.alias` 全局删除（**已落地 2026-09-22**，用户拍板：菜品无需昵称） | 原为：`schema.sql` 的 `alias` 列、`Dish` 实体 / `DishAdminReq` / `DishAdminVO`、`DishMapper.xml` 别名匹配路、Web 表单「搜索别名」输入与 `≤255` 校验、`seed_data.sql` 赋值 | **已落地**：**server** 删实体 / DTO 字段与 `d.alias LIKE` 匹配分支（含 `adminDishColumns` / resultMap）、删 `normalizeAlias` 与「搜索别名过长」错误码；**DB** `schema.sql` CREATE TABLE 不再建列 + 新增幂等段 `drop_dish_alias_column`（原 `add_dish_alias` 段整段删除）、`seed_data.sql` 经核对本就不含该列；**web** 表单输入项 / 初始与回填 / 载荷 / 校验 / 详情展示全删；文档同步 |
| 5 | 端上 `MixedResult.stall` 只写不读（**已落地 2026-09-22**） | 声明于 `pages/find/index.vue` 与 `FindResults.vue`，映射自 `d.stallName`，但模板**只渲染 `sub`**（其中已含档口名） | **已删除**两处声明与映射 |

> 本节按 2026-09-22 定稿规则设立：**正文只写最终设计形态，与现有代码的差异一律写在本节**；差异清零时保留标题并写「无」。
