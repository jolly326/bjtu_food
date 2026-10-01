# 首页菜品浏览（A-02）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免登录）
> 返回：[功能总览](./README.md)

## 介绍

看今天有什么可吃：**双列菜品卡片网格**（图 / 名 / 价 / 评分 / 位置），支持按**筛选视图**（横向标签栏，如「为你推荐」与各大类）筛选（**筛选与排序由服务端按所选视图唯一决定：推荐视图按会话种子伪随机序、大类视图热度倒序，端上无排序入口**）；无食堂 / 价格筛选入口。进入首页自动加载第 1 页，上滑触底自动加载下一页。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/dishes` | 🔓 公开 | 菜品分页列表：首页网格、搜索共用同一端点，**返回 `PageResult<DishListItemVO>`（列表专用 8 字段，见「字段」节）**；**只返回 `status='on'`（在售）菜品**；支持 `view` 筛选视图；**参数集恰为 5 项**（`page` / `pageSize` / `keyword` / `view` / `seed`） |
| GET | `/dishes/views` | 🔓 公开 | **首页筛选视图字典**：下发横向筛选栏数据源 `[{ key, label }]`（服务端按序下发）；含「为你推荐」等聚合视图与当前有在售菜品的大类视图（大类视图空类自动隐藏） |
| GET | `/banners` | 🔓 公开 | **首页顶部轮播图**：下发启用中的 Banner 清单 `[{ id, imageUrl }]`（服务端按 `sort_order` 升序），供首页顶部 16:10 轮播；**无请求参数** |

## 字段

### 请求 · `GET /dishes`（query 参数 / `DishQueryReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 10；**端上首页固定传 10**（`HOME_PAGE_SIZE`）；**服务端归一化上限 100**（`PageUtil.MAX_PAGE_SIZE`，超限截断为 100；`<=0` 回退 10） |
| `keyword` | string | 否 | 关键词，**同时匹配菜名 `name` / 档口名 `stall.name` / 食堂名 `canteen.name`**（三处模糊匹配；**关键词搜食堂名仍然可用**） |
| `view` | string | 否 | **筛选视图键**（值域由 `GET /dishes/views` 下发，如 `recommend` / `set_meal` / `noodle` /…）：**白名单校验，非法值 → 400**（PR-06），不静默降级；**缺省 = 默认视图**（「为你推荐」）。视图决定筛选条件与排序口径（见「排序口径」） |
| `seed` | string | 否 | **会话随机种子**：端上**冷启动生成一次、会话内恒定**（重进小程序才重掷），并随每次请求下发；翻页沿用同一值。服务端**仅对推荐类视图**（`sortKind=SEED_RANDOM`）且无 `keyword` 时按 `CRC32(CONCAT(seed,'-',id)), id` 做**稳定伪随机排序**；其余视图忽略本参数（按各自排序口径）。（端上无「主动换一批」入口，会话内自变只会被读成「界面不稳定」） |

> **参数集恰为 5 项**：`page` / `pageSize` / `keyword` / `view` / `seed`。筛选与排序由服务端按所选视图唯一决定（见「排序口径」），端上不传排序参数。
> **`view` 与 `keyword` 的组合口径**：两者可同时传，语义为 **AND**（先按视图筛、再按关键词模糊匹配）；**排序以视图的 `sortKind` 为准**（推荐类视图带 `keyword` 时退回热度序，因伪随机序要求无关键词）。**端上约定：首页网格传 `view`（不传 `keyword`）、搜索页传 `keyword`（不传 `view`）**，即两个入口各自只用一个维度——组合能力保留给服务端，端上不使用。

### 响应（`data` = `PageResult<DishListItemVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | DishListItemVO[] | **当前页数据行**（前端以它为准；**不含详情专属字段**，见下） |

> 分页壳恒为 `records` 一项（口径见本目录 [README](./README.md) 的「通用结构 · 分页结构」）；页码 / 每页条数由请求侧掌握，不回传；结束判据 = 本页返回条数 < `pageSize`。

### 响应 · `GET /dishes/views`（`List<DishViewVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `key` | string | **视图键**（端上点击后原样回传 `view=<key>` 用于筛选；如 `recommend` / `set_meal` / `noodle`……**无 null** —— 首项「为你推荐」也是普通键） |
| `label` | string | 标签文案（**端上直接全量渲染、零硬编码中文**） |

> **形态（视图字典运营化解耦）**：筛选栏**全量由后端直出**——顺序 = `DishViewConst.ALL` 声明序（首项为「为你推荐」），端上**不前置拼接、不补兜底项**。**端上只认 `key` + `label`**。
> **空类自动隐藏**：仅对「按大类取数」的视图生效（该大类当前无在售菜品即不下发，有菜自动出现）；聚合视图（「为你推荐」等）恒下发。
> **不做出参的部分**：菜品大类**不进公开菜品出参**（`DishListItemVO` / `DishDetailVO`）——列表卡片不展示大类，筛选在后端完成；后台 `DishAdminReq` / `DishAdminVO` 仍需该字段（录入下拉 + 编辑回填 + 列表筛选）。
> **扩展位**：将来加「折扣」等新视图，只改服务端（视图常量 + 解析分支 + SQL 片段），**端上零改动**（详见 D5 第 8 条）。

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
| `avgRating` | number \| null | 平均评分（读缓存列 `dish.avg_rating`，口径 = 仅未隐藏评价）——卡片渲染评分，**零评价时 `null`（不渲染评分区）** |
| `canteenName` | string | 食堂名称（卡片第 3 段位置行） |
| `stallName` | string | 档口名称（卡片第 3 段位置行） |

> **列表出参恰为 8 字段**：字段集 =「首页卡片四段（图 / 名 / 位置 / 评分 + 价格）」的**真实渲染集合**，加跳转必需的 `id`——不做任何详情专属字段的下发。
> **详情专属字段不在列表出参中**（字段集与语义**以 `docs/client/feature/client-菜品详情.md` 为真源，本文档不重复维护**）：`description`（描述）、`images`（多图数组，列表只给首图 `coverImage`）、`floor`（楼层）、`attributes`（动态描述属性，值即中文）。**菜品大类（`mealType`）亦不进公开出参**（见「筛选视图与菜品大类字段决议」节）。
> **8 字段在消费场景均全量消费**（含位置行渲染）：**列表与搜索共用同一端点与同一 VO**，不为搜索另拆分 VO（多一个 VO 即多一份契约维护成本）。
> `imagesJson`（图片 JSON 原文）为内部字段、**不出参**。

## 数据（读取）

| 表 | 读什么 | 中文解释 |
|---|---|---|
| `dish` | **列表按 `listDishColumns`（`DishListItemVO` 8 字段所需列）、详情按 `detailDishColumns`**（双列清单）；`meal_type` 只用于筛选与字典下发；`status='on'` 仅用于 WHERE 过滤、不出参 | 菜品主体。**列表不选**详情专属列（`description` / `images` 全量 / `attributes` / `floor` 等——只在详情查询里取）；两场景均**不选** `status` / `view_count` / `created_at` / `updated_at` / `stall_id` / `canteen_id`（`DishMapper.xml` 的公开列清单注释明示）。**`meal_type` 不进公开出参**（见「菜品大类字段」节） |
| `stall` | 列表仅 `name`；详情加 `floor` | 档口名（列表卡片第 3 段）；楼层 `floor` **仅详情 `DishDetailVO` 需要**（列表不读），公开查询列不选窗口号 |
| `canteen` | `name`（菜品列表 join 取食堂名） | 食堂名（**位置表达 = 食堂名 + 档口楼层，不读坐标列**）；**公开侧无食堂字典端点** |
| `banner` | `id`、`image_url`（仅启用项） | **首页顶部轮播图**：`SELECT id, image_url ... WHERE status='on' ORDER BY sort_order ASC`；`sort_order` / `status` 仅用于排序与过滤、**不出参**；`image_url` 与菜品图片同口径（库内可存相对路径，出参转绝对 URL）。表结构见 `server/src/main/resources/db/schema.sql`（唯一真源，不在文档写死张数） |

## 设计决议（现行口径）

> 本节按「一个决策一个小节」组织（`### A` ~ `### E`），可被目录 / 锚点直接跳转；**各小节只写最终设计形态**，实现进度一律见文末「与当前代码的差异」。

### A. 排序口径

`GET /dishes` 的筛选与排序**由所选视图决定**（视图的 `sortKind`），端上不传排序参数（无 `sortBy` / `sortOrder`）：

| 视图的 `sortKind` | 条件 | 排序 |
|---|---|---|
| `SEED_RANDOM`（推荐类，如「为你推荐」） | 无 `keyword` 且带 `seed` | `ORDER BY CRC32(CONCAT(seed, '-', id)), id`——**稳定伪随机序**：同一 seed ⇒ 同一全序（`id` 决胜键防哈希碰撞）⇒ 翻页不重不漏；换 seed ⇒ 整体重洗 |
| `DISCOUNT_DESC`（折扣类，扩展位） | 折扣视图 | 折扣力度倒序，热度作决胜键 |
| `HEAT`（大类 / 搜索 / 其余） | 其余一切请求 | `heatScoreExpr` **热度倒序**（原口径不变） |

- **`seed` 生命周期 = 会话级**：端上**冷启动（重进小程序）生成一次**，会话内恒定——切视图 / 首屏 / 失败重试**都不重掷**，翻页沿用同一值 ⇒ 同一次会话内顺序完全稳定（内容不会自己变），重进小程序才整体重洗。**非**每次 reset 重掷（内容在会话内自变，而端上无「主动换一批」入口 ⇒ 用户只读成「界面不稳定」）；**非** `ORDER BY RAND()`（真随机会导致跨页重复 / 漏项）；**非**按天全局种子（全站同序、当天无新鲜度）。
- **范围**：`seed` **只对推荐类视图生效**（服务端按 `sortKind` 判断，其余视图忽略）——端上**可无脑随请求下发**，无需判断视图语义。
- 合「排序口径由服务端唯一决定、端上无排序入口」原则：`seed` 是**数据顺序种子**而非排序参数，端上不提供任何排序 UI。

### B. 列表 / 详情出参拆分

`GET /dishes` 与 `GET /dishes/{id}` 各自只装自己的消费集：

| VO | 使用端点 | 字段集 |
|---|---|---|
| `DishListItemVO` | `GET /dishes`（首页网格 + 搜索结果），**8 字段** | `id` / `name` / `coverImage` / `price` / `originalPrice` / `avgRating` / `canteenName` / `stallName` |
| `DishDetailVO` | `GET /dishes/{id}`（详情页；字段集以 `docs/client/feature/client-菜品详情.md` 为真源） | `id` / `name` / `price` / `originalPrice` / `description` / `images[]` / `stallName` / `canteenName` / `floor` / `avgRating` / `attributes` |

- **实现要求**：`GET /dishes` 一律返回 `DishListItemVO`（列表只有 `coverImage` 单值，无 `images` 数组）；详情接口保持 `DishDetailVO`；`DishMapper.xml` 按场景拆两份列清单（`listDishColumns` / `detailDishColumns`），**禁止列表查询图省事选全列**；
- **端上形态**：`client/src/types/dish.ts` 分为 `DishListItem` / `DishDetail` 两型；`api/dish.ts` 的列表 / 详情映射按各自 VO 编写；卡片直读 `coverImage`。

### C. 首页 UI 口径

> UI 细节（首屏结构、吸顶、色板、间距刻度、硬性约束 13 条）以 [`docs/client/ui/`](../ui/) 为唯一真源；本节只登记跨端共享的口径。

| # | 决议 | 说明 / 影响 |
|---|---|---|
| C1 | **卡片为 4 段固定排版**：图 → 菜名 → `食堂名称 \| 档口名称` → 星+评分（左）/ 价格（右） | **位置行分隔符为 `食堂名称 \| 档口名称`**，浅灰纯文字、禁用彩色标签块；**`月售XXX份` 不引入**（项目无订单 / 销量数据源）；星形 34rpx（「星形 = 评分文字 + 6rpx」光学口径） |
| C2 | **全站主色板与页面背景渐变**以 UI 文档 [client-首页菜品浏览.md](../ui/client-首页菜品浏览.md) §10 为唯一真源（含两档取色、对比度口径与适用边界） | 触达 `theme/tokens.ts`、`generated-colors.css`、Web `variables.css`；`IconSvg` 图标取色以 `COLOR_MAP` 真源实色为准（data-uri 不解析 `var()`） |
| C3 | **硬性约束 13 条**（双列禁三列 / 档口无彩标签 / 搜索框常驻吸顶 / 卡片不截断 / TabBar 仅 2 项 / 不遮胶囊 / 无引导 / 补录类组件等）作为回归检查清单 | 清单全文见 UI 文档 §6 |
| C4 | **TabBar 仅首页 / 我的**，首页橙色高亮 | 配色随 C2 |

### D. 筛选视图与菜品大类字段决议

> **背景**：横向筛选栏上的一个按钮，是「**用户想看什么**」的**导航视角**（本文档称 **view / 筛选视图**），与「**菜品是什么**」的入库字段（`dish.meal_type`，本文档称 **菜品大类**）**不是一回事**。物理大类只是「按 `meal_type` 取数」的一类视图；将来「折扣」等视图按别的口径取数，同一个 `view` 参数位即可承载。
> **视图 ≠ 数据字段**：视图的 `key` 是视图标识（端上回传），其取数参数（大类视图时 = `meal_type` 值）才是底层字段值。
> **与 `attributes` 的关系**：`attributes` 的**属性维度**可**多值、横切**（一道菜可同时是辣、清真、热食），大类是**单值、互斥、全量覆盖**——两者不是一类字段。

**D1 存储形态：单值枚举列（仅「菜品大类」字段）**

- `dish` 表为 **`meal_type`** 单值枚举列（`VARCHAR`），值域由**后端常量 `DishViewConst` 的「大类视图」派生**（口径只留一处真源）；
- **不新建字典表、不建外键**——`meal_type` 只是 dish 的一个字段；
- 新增 / 修改大类 → 在 `DishViewConst` 的视图清单里加 / 改一行并**发版**（大类为低频变更，可接受）；若将来要后台自助增删，可升级为「视图表 + 后台」形态（另立 change）；
- **筛选视图清单**同样硬编码在 `DishViewConst`（不建表）；每个视图 = `key` / `label` / 取数形态 `kind`；顺序 = 声明序。

**D2 视图清单（当前：1 聚合 + 6 大类）**

| 视图 `key` | 中文 `label` | 取数形态 `kind` | 取数参数 `param` |
|---|---|---|---|
| `recommend` | 为你推荐 | `RECOMMEND`（无筛选 + 种子伪随机序） | — |
| `set_meal` | 套餐盖饭 | `MEAL_TYPE`（`meal_type` 等值 + 热度序） | `set_meal` |
| `stir_fry` | 家常小炒 | `MEAL_TYPE` | `stir_fry` |
| `noodle` | 面食粉类 | `MEAL_TYPE` | `noodle` |
| `dry_pot` | 香锅干锅 | `MEAL_TYPE` | `dry_pot` |
| `snack` | 风味小吃 | `MEAL_TYPE` | `snack` |
| `soup_drink` | 汤饮甜品 | `MEAL_TYPE` | `soup_drink` |

> **「为你推荐」不是大类**：它是 `kind = RECOMMEND` 的**聚合视图**（全库在售菜的聚合入口，不筛 `meal_type`）；它的 `key` 是普通键（与视图清单其余项同构，无 `value: null` 特例），端上与其他视图同等对待、无 null 特例。
> **菜品大类（`meal_type`）的值域** = 上表 `MEAL_TYPE` 视图的 `param` 集合（`DishViewConst.mealTypeValues()`）——后台录入的白名单即取此集合。

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

1. **库**：`schema.sql` 幂等段以**存储过程**（先判列存在再 `ADD COLUMN`）维护 `meal_type`，**禁直连 ALTER**；`seed_data.sql` 为全部菜品赋值（D4 表）；
2. **筛选**：`GET /dishes?view=<视图键>`，**白名单校验、非法值 400**（PR-06）；大类视图精确等值匹配（单值列）；
3. **字典**：只读端点 `GET /dishes/views`，按 `DishViewConst.ALL` 声明序下发 `[{ key, label }]`；**大类视图空类自动隐藏**，聚合视图恒下发；
4. **出参**：**公开菜品出参（`DishListItemVO` / `DishDetailVO`）不含 `mealType`**（卡片不展示 → 零消费即删）；后台 `DishAdminReq` / `DishAdminVO` 含（录入下拉 + 编辑回填 + 列表筛选）；
5. **端上**：筛选栏 100% 由字典端点直出渲染（端上不前置拼接、**不写任何兜底项**），单选，切换即重置分页；**字典未到位 / 失败 ⇒ 筛选栏整体不渲染**（不留空栏），列表仍按「不传 `view`」的默认视图加载 —— 标签文案（含「为你推荐」）是**服务端资产**，端上零文案；
6. **种子数据**：珍珠奶茶、杨枝甘露 不标 `rice`（米）属性，避免详情页显示「米」；
7. **合规登记**：`meal_type` 的分类语义为**单值大类字段**（非多值，与 `attributes` 里可多值的属性维度区分）。
8. **视图扩展位**：新增筛选视图**只在服务端改动，端上永远零改动**——
   - **加一个大类视图**（如「早餐」）：`DishViewConst.ALL` 加一行 `MEAL_TYPE`（`key` / `label` / `param`）+ 种子里给相关菜打 `meal_type`；
   - **加一个「新语义」视图**（如「折扣」）：`DishViewConst.ALL` 加一行 + `Kind` 加取值 + `DishViewResolver` 加一个分支 + `DishMapper.xml` 加对应 WHERE / ORDER BY 片段（**一次**）；之后同类视图只加清单行；
   - **改文案 / 调顺序 / 删除视图**：只改 `DishViewConst.ALL`；
   - **端上契约恒定**：端上只认 `key` + `label` 并把 `key` 回传为 `view`，故以上任何变更端上零改动。

### E. 首页 Banner 轮播接口化决议

> **UI 细节（16:10 定档、标题带、吸顶范围）以 [`docs/client/ui/`](../ui/) §1 / §3.3 / §11 为唯一真源。**

| # | 决议 | 说明 / 影响 |
|---|---|---|
| E1 | **Banner 由后端接口下发（`GET /banners`），端上轮播渲染** | 端上**不写死 URL、不排序、不写死张数**；Banner 为**静态运营位**（无推荐算法），本节只做运营位轮播，**不引入个性化推荐** |
| E2 | **首屏结构：Banner 为正常流首块，吸顶容器只含搜索区 + 标签栏** | Banner 整块（含状态栏背后与左上角标题）**随页面滚出即消失**——**不折叠、不定格、不作为吸顶容器背景**；吸顶态顶端**不重复渲染**「知行食记」标题。硬性约束：搜索区 + 标签栏同组吸顶（UI 文档 §13 第 3 条）、「Banner 滚出后不残留图片背景」、「吸顶态不重复渲染标题」 |
| E3 | **宽高比锁定 16:10** | Banner 总高 = 屏宽 × 10/16（375 宽 ≈234px）+ 最小高度兜底（≥ 状态栏 + 标题带 + 运营内容最小可视高 ≈120px）；**素材一律 16:10 出图**（混比例会导致轮播切换时块高抖动、吸顶阈值漂移）；加载中 / 失败使用**同高占位图**。完整论证见 UI 文档 §3.3 |
| E4 | **库：`banner` 表** | `banner(id, image_url, sort_order, status, created_at, updated_at)`；`status` 取 `on` / `off`（与 `dish.status` 同风格）。落库口径：**只改 `server/src/main/resources/db/schema.sql`（幂等段，判表 / 判列存在再建）与 `seed_data.sql`，禁直连 ALTER** |
| E5 | **无 Banner 录入入口** | Banner 素材由 `seed_data.sql` 维护（运营位数量级极小，小程序为唯一消费端）。**小程序端无任何 Banner 写接口** |
| E6 | **契约红线对齐** | 出参仅 `id`（轮播 key）+ `imageUrl`；`sort_order` / `status` 服务端内部用、不出参；**无跳转字段**（端上零点击交互）；空集合返回 `[]`（非 404 / null）；Banner 请求与菜品列表**并行**、Banner 失败不阻塞首屏；统一响应 `{ code, message, data }` / camelCase / 错误码沿用全站统一口径（`200` / `400` / `401` / `403` / `4001` / `4031` / `500`） |
| E7 | **`GET /dishes` 及 `GET /dishes/views` 契约不受 Banner 模块影响** | Banner 模块只新增一个只读端点与一张表 |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

**无（文档与代码一致）**
