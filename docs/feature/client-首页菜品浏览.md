# 首页菜品浏览（A-02）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免登录）
> 返回：[功能总览](./README.md)

## 介绍

看今天有什么可吃：**双列菜品卡片网格**（图 / 名 / 价 / 评分 / 位置），支持按**菜品大类**（横向标签栏）筛选（**排序恒为服务端固定口径 = 热度，端上无排序入口**）；无食堂 / 价格筛选入口。进入首页自动加载第 1 页，上滑触底自动加载下一页。

## UI

> 📐 页面 UI 设计稿已拆出 → [client-首页菜品浏览.md（docs/ui）](../ui/client-首页菜品浏览.md)（**UI 口径以该文件为唯一真源**）

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/dishes` | 🔓 公开 | 菜品分页列表：首页网格、搜索共用同一端点，**返回 `PageResult<DishListItemVO>`（列表专用 8 字段，见「字段」节）**；**只返回 `status='on'`（在售）菜品**；支持 `mealType` 大类筛选；**参数集恰为 4 项**（`page` / `pageSize` / `keyword` / `mealType`） |
| GET | `/dishes/meal-types` | 🔓 公开 | **菜品大类字典**：下发标签栏数据源 `[{ key, label, order }]`，只含当前有在售菜品的大类 |
| GET | `/banners` | 🔓 公开 | **首页顶部轮播图**：下发启用中的 Banner 清单 `[{ id, imageUrl }]`（服务端按 `sort_order` 升序），供首页顶部 16:10 轮播；**无请求参数** |

## 字段

### 请求 · `GET /dishes`（query 参数 / `DishQueryReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 10；**端上首页固定传 10**（`HOME_PAGE_SIZE`）；**服务端归一化上限 100**（`PageUtil.MAX_PAGE_SIZE`，超限截断为 100；`<=0` 回退 10），**实际生效值以响应 `page` / `pageSize` 为准** |
| `keyword` | string | 否 | 关键词，**同时匹配菜名 `name` / 档口名 `stall.name` / 食堂名 `canteen.name`**（三处模糊匹配；**关键词搜食堂名仍然可用**） |
| `mealType` | string | 否 | **菜品大类筛选**：单值，值域 = 大类枚举键（`set_meal` / `stir_fry` / `noodle` / `dry_pot` / `snack` / `soup_drink`）；**白名单校验，非法值 → 400**（PR-06），不静默降级 |

> **参数集恰为 4 项**：`page` / `pageSize` / `keyword` / `mealType`。排序为服务端固定口径（见「排序口径」），端上不传排序参数。

### 响应（`data` = `PageResult<DishListItemVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | DishListItemVO[] | **当前页数据行**（前端以它为准；**不含详情专属字段**，见下） |
| `total` | number | 符合条件的总条数（服务端同口径统计）。**⚠️ 本列表链路端上零读取**——结束判据是「本页返回条数 < `pageSize`」 |
| `page` | number | 实际生效的页码（服务端归一化后的值） |
| `pageSize` | number | 实际生效的每页条数（同上） |

> `total` / `page` / `pageSize` **予以保留**：`total` 是 `PageResult` 全局共用字段，且「服务端同口径统计」本身有语义；`page` / `pageSize` 为自描述元数据，排障有价值。

### 响应 · `GET /dishes/meal-types`（`List<MealTypeVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `key` | string | 大类枚举键（用于 `GET /dishes?mealType=` 筛选） |
| `label` | string | 中文标签（**端上直接渲染，不得在前端维护映射表**） |
| `order` | number | 标签栏展示顺序（升序） |

> **形态**：标签栏**不含「全部」**（「全部」= 不传 `mealType`，由端上固定渲染为第一项）；后端只下发**当前有在售菜品**的大类（空类自动隐藏）。
> **不做出参的部分**：大类**不进公开菜品出参**（`DishListItemVO` / `DishDetailVO`）——列表卡片不展示大类，筛选在后端完成；后台 `DishAdminReq` / `DishAdminVO` 需要（录入下拉 + 编辑回填 + 列表筛选）。

### 响应 · `GET /banners`（`List<BannerVO>`）

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
| `coverImage` | string | **封面图 URL**（无图为空串）——列表只渲染首图，仅下发封面单值 |
| `price` | number | 现价，**单位：分**（端上转「元」展示） |
| `originalPrice` | number \| null | 原价（折扣前），单位分；**`originalPrice > price` 即视为有折扣**（端上画原价删除线），无折扣时 null |
| `avgRating` | number | 平均评分（缓存值，口径 = 仅未隐藏评价）——卡片只渲染评分，**列表不发 `ratingCount`** |
| `canteenName` | string | 食堂名称（卡片第 3 段位置行） |
| `stallName` | string | 档口名称（卡片第 3 段位置行） |

> **列表出参恰为 8 字段**：字段集 =「首页卡片四段（图 / 名 / 位置 / 评分 + 价格）」的**真实渲染集合**，加跳转必需的 `id`——不做任何详情专属字段的下发。
> **详情专属字段不在列表出参中**（字段集与语义**以 `docs/feature/client-菜品详情.md` 为真源，本文档不重复维护**）：`description`（描述）、`images`（多图数组，列表只给首图 `coverImage`）、`floor`（楼层）、`ratingCount`（评价数）、`dietType`（荤素）、`ingredients`（主料）、`flavorTags`（口味）、`serveTemp`（冷热）、`ratingDistribution`（评分分布）。**`mealType` 亦不进公开出参**（见「菜品大类字段」节）。
> **8 字段在消费场景均全量消费**（含位置行渲染）：**列表与搜索共用同一端点与同一 VO**，不为搜索另拆分 VO（多一个 VO 即多一份契约维护成本）。
> `imagesJson`（图片 JSON 原文）为内部字段、**不出参**。

## 数据（读取）

| 表 | 读什么 | 中文解释 |
|---|---|---|
| `dish` | **列表按 `listDishColumns`（`DishListItemVO` 8 字段所需列）、详情按 `detailDishColumns`**（双列清单）；`meal_type` 只用于筛选与字典下发；`status='on'` 仅用于 WHERE 过滤、不出参 | 菜品主体。**列表不选**详情专属列（`description` / `images` 全量 / `diet_type` / `ingredients` / `flavor_tags` / `serve_temp` / `rating_count` 等——只在详情查询里取）；两场景均**不选** `status` / `view_count` / `created_at` / `updated_at` / `stall_id` / `canteen_id`（`DishMapper.xml` 的公开列清单注释明示）。**`meal_type` 不进公开出参**（见「菜品大类字段」节） |
| `stall` | 列表仅 `name`；详情加 `floor` | 档口名（列表卡片第 3 段）；楼层 `floor` **仅详情 `DishDetailVO` 需要**（列表不读），公开查询列不选窗口号 |
| `canteen` | `name`（菜品列表 join 取食堂名） | 食堂名（**位置表达 = 食堂名 + 档口楼层，不读坐标列**）；**公开侧无食堂字典端点** |
| `banner` | `id`、`image_url`（仅启用项） | **首页顶部轮播图**：`SELECT id, image_url ... WHERE status='on' ORDER BY sort_order ASC`；`sort_order` / `status` 仅用于排序与过滤、**不出参**；`image_url` 与菜品图片同口径（库内可存相对路径，出参转绝对 URL）。表结构见 `server/src/main/resources/db/schema.sql`（**库表基线共 11 张表**） |

## 设计决议（现行口径）

> 本节按「一个决策一个小节」组织（`### A` ~ `### E`），可被目录 / 锚点直接跳转；**各小节只写最终设计形态**，实现进度一律见文末「与当前代码的差异」。

### A. 排序口径

`GET /dishes` 的排序**恒为服务端热度口径**（`DishMapper.xml` 的 `heatScoreExpr` 倒序），端上不传排序参数。默认流 / 搜索流排序一致——合 `project_spec.md` §7.17 第 2 条「热度优先、不设排序入口」。

### B. 列表 / 详情出参拆分

`GET /dishes` 与 `GET /dishes/{id}` 各自只装自己的消费集：

| VO | 使用端点 | 字段集 |
|---|---|---|
| `DishListItemVO` | `GET /dishes`（首页网格 + 搜索结果），**8 字段** | `id` / `name` / `coverImage` / `price` / `originalPrice` / `avgRating` / `canteenName` / `stallName` |
| `DishDetailVO` | `GET /dishes/{id}`（详情页；字段集以 `docs/feature/client-菜品详情.md` 为真源） | `id` / `name` / `price` / `originalPrice` / `description` / `images[]` / `stallName` / `canteenName` / `floor` / `avgRating` / `ratingCount` / `dietType` / `ingredients` / `flavorTags` / `serveTemp` + `ratingDistribution` |

- **实现要求**：`GET /dishes` 一律返回 `DishListItemVO`（列表只有 `coverImage` 单值，无 `images` 数组）；详情接口保持 `DishDetailVO`；`DishMapper.xml` 按场景拆两份列清单（`listDishColumns` / `detailDishColumns`），**禁止列表查询图省事选全列**；
- **端上形态**：`client/src/types/dish.ts` 分为 `DishListItem` / `DishDetail` 两型；`api/dish.ts` 的列表 / 详情映射按各自 VO 编写；卡片直读 `coverImage`。

### C. 首页 UI 口径

> UI 细节（首屏结构、吸顶、色板、间距刻度、硬性约束 13 条）以 [docs/ui/client-首页菜品浏览.md](../ui/client-首页菜品浏览.md) 为唯一真源；本节只登记跨端共享的口径。

| # | 决议 | 说明 / 影响 |
|---|---|---|
| C1 | **卡片为 4 段固定排版**：图 → 菜名 → `食堂名称 \| 档口名称` → 星+评分（左）/ 价格（右） | **位置行分隔符为 `食堂名称 \| 档口名称`**，浅灰纯文字、禁用彩色标签块；**`月售XXX份` 不引入**（项目无订单 / 销量数据源）；星形 34rpx（「星形 = 评分文字 + 6rpx」光学口径） |
| C2 | **全站主色板与页面背景渐变**以 UI 文档 §4 为唯一真源（含两档取色、对比度口径与适用边界） | 触达 `theme/tokens.ts`、`generated-colors.css`、Web `variables.css`；`IconSvg` 图标取色以 `COLOR_MAP` 真源实色为准（data-uri 不解析 `var()`） |
| C3 | **硬性约束 13 条**（双列禁三列 / 档口无彩标签 / 搜索框常驻吸顶 / 卡片不截断 / TabBar 仅 2 项 / 不遮胶囊 / 无引导 / 补录类组件等）作为回归检查清单 | 清单全文见 UI 文档 §6 |
| C4 | **TabBar 仅首页 / 我的**，首页橙色高亮 | 配色随 C2 |

### D. 菜品大类字段决议

> **背景**：横向大类标签栏需要「**大类**」维度——它与 `ingredients` / `flavorTags` / `serveTemp` / `dietType` 这些**属性维度不是一类字段**：属性是**多值、横切**（一道菜可同时是辣、清真、热食），大类是**单值、互斥、全量覆盖**（一个菜品只属一个大类；一个大类含多个菜品）。

**D1 存储形态：单值枚举列**

- `dish` 表为 **`meal_type`** 单值枚举列（`VARCHAR`），值域由**后端常量**定义（与 `heatScoreExpr` 同风格：口径只留一处真源）；
- **不新建字典表、不建外键**——`meal_type` 只是 dish 的一个字段；
- 新增 / 修改大类需改后端常量并**发版**（大类为低频变更，可接受）；若将来要后台自助增删，可升级为「字典表 + 外键」形态（另立 change，需先调整 §7.22 第 1 条）。

**D2 枚举值（键 → 中文标签）**

| 枚举键 | 中文标签 |
|---|---|
| `set_meal` | 套餐盖饭 |
| `stir_fry` | 家常小炒 |
| `noodle` | 面食粉类 |
| `dry_pot` | 香锅干锅 |
| `snack` | 风味小吃 |
| `soup_drink` | 汤饮甜品 |

「全部」不是枚举值——它是端上固定渲染的第一项，对应**不传** `mealType`。

**D3 判定口径（后台录入与数据修正的唯一判据）**

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

**D4 归属清单（种子 31 道：全量覆盖、无空类）**

| 大类 | 菜品 |
|---|---|
| `set_meal` 套餐盖饭（3） | 黄焖鸡米饭、招牌烤肉饭、咖喱鸡排饭 |
| `stir_fry` 家常小炒（9） | 宫保鸡丁、水煮牛肉、回锅肉、番茄炒蛋、土豆烧牛肉、香辣虾、糖醋里脊、鱼香茄子、宫保虾球 |
| `noodle` 面食粉类（4） | 牛肉拉面、兰州牛肉面、羊肉泡馍、炒粉 |
| `dry_pot` 香锅干锅（3） | 干锅花菜、骨汤麻辣烫、冒脑花 |
| `snack` 风味小吃（9） | 鲜肉小笼、广式肠粉、烤五花肉、烤茄子、烤冷面、羊肉串、烤馕、鲜虾烧卖、叉烧包 |
| `soup_drink` 汤饮甜品（3） | 皮蛋瘦肉粥、珍珠奶茶、杨枝甘露 |

**D5 落地要求**

1. **库**：`schema.sql` 幂等段以**存储过程**（先判列存在再 `ADD COLUMN`）维护 `meal_type`，**禁直连 ALTER**；`seed_data.sql` 为全部菜品赋值（上表）；
2. **筛选**：`GET /dishes?mealType=<枚举键>`，**白名单校验、非法值 400**（PR-06），精确等值匹配（单值列）；
3. **字典**：只读端点 `GET /dishes/meal-types`，返回 `[{ key, label, order }]`，**只含当前有在售菜品的大类**（空类自动隐藏）；
4. **出参**：**公开菜品出参（`DishListItemVO` / `DishDetailVO`）不含 `mealType`**（卡片不展示 → 零消费即删）；后台 `DishAdminReq` / `DishAdminVO` 含（录入下拉 + 编辑回填 + 列表筛选）；
5. **端上**：标签栏完全由字典端点驱动（不写死任何标签与中文映射），单选，切换即重置分页；
6. **种子数据**：珍珠奶茶、杨枝甘露 的 `ingredients` 不标注 `rice`（米），避免详情页「主料」显示「米」；
7. **合规登记**：`meal_type` 的分类语义登记于 `project_spec.md` §7.23（单值大类字段）。

### E. 首页 Banner 轮播接口化决议

> **UI 细节（16:10 定档、标题带、吸顶范围）以 [docs/ui/client-首页菜品浏览.md](../ui/client-首页菜品浏览.md) §1 / §1.1 / §5 为唯一真源。**

| # | 决议 | 说明 / 影响 |
|---|---|---|
| E1 | **Banner 由后端接口下发（`GET /banners`），端上轮播渲染** | 端上**不写死 URL、不排序、不写死张数**；Banner 为**静态运营位**（无推荐算法），本节只做运营位轮播，**不引入个性化推荐** |
| E2 | **首屏结构：Banner 为正常流首块，吸顶容器只含搜索区 + 标签栏** | Banner 整块（含状态栏背后与左上角标题）**随页面滚出即消失**——**不折叠、不定格、不作为吸顶容器背景**；吸顶态顶端**不重复渲染**「知行食记」标题。硬性约束：搜索区 + 标签栏同组吸顶（UI 文档 §6 第 3 条）、「Banner 滚出后不残留图片背景」、「吸顶态不重复渲染标题」 |
| E3 | **宽高比锁定 16:10** | Banner 总高 = 屏宽 × 10/16（375 宽 ≈234px）+ 最小高度兜底（≥ 状态栏 + 标题带 + 运营内容最小可视高 ≈120px）；**素材一律 16:10 出图**（混比例会导致轮播切换时块高抖动、吸顶阈值漂移）；加载中 / 失败使用**同高占位图**。完整论证见 UI 文档 §1.1 |
| E4 | **库：`banner` 表（库表基线 11 张之一）** | `banner(id, image_url, sort_order, status, created_at, updated_at)`；`status` 取 `on` / `off`（与 `dish.status` 同风格）。落库口径：**只改 `server/src/main/resources/db/schema.sql`（幂等段，判表 / 判列存在再建）与 `seed_data.sql`，禁直连 ALTER** |
| E5 | **管理端无 Banner 录入入口** | Banner 素材由 `seed_data.sql` 维护（运营位数量级极小，小程序为唯一消费端）；管理端如需自助录入，须另立 change 新增 `/admin/banners` 并同步 `docs/feature` 相应文档。**小程序端无任何 Banner 写接口** |
| E6 | **契约红线对齐** | 出参仅 `id`（轮播 key）+ `imageUrl`；`sort_order` / `status` 服务端内部用、不出参；**无跳转字段**（端上零点击交互）；空集合返回 `[]`（非 404 / null）；Banner 请求与菜品列表**并行**、Banner 失败不阻塞首屏；统一响应 `{ code, message, data }` / camelCase / 错误码沿用 `project_spec.md` §3 |
| E7 | **`GET /dishes` 及 `GET /dishes/meal-types` 契约不受 Banner 模块影响** | Banner 模块只新增一个只读端点与一张表 |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

**无（文档与代码一致）**
