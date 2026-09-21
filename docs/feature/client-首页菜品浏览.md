# 首页菜品浏览（A-02）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免登录）
> 返回：[功能总览](./README.md)

## 干什么

看今天有什么可吃：**双列菜品卡片网格**（图 / 名 / 价 / 评分 / 位置），支持按**菜品大类**（横向标签栏）、食堂、价格区间筛选（**排序恒为服务端固定口径 = 热度，端上无排序入口**）。

## UI

> **2026-09-21 首页 UI 重构决议（已落地）**：本节的「初始态 / 吸顶态」结构与卡片排版即**当前实现**（change `home-ui-refresh`，`build:mp-weixin` EXIT 0、产物逐项核验）；`app` 主色已由暖砖红改为**橙色**（全站 token 层，定稿取值见 G4）。

### 页面两态（同一页面滚动切换）

**① 初始态（未上滑，Banner 完整展示）**

1. **顶部整块通栏 Banner**（**左右无左右边距，占据页面最顶部区域**——含状态栏背后）：Banner 即「知行食记」标题的**背景**——标题**叠加**绘制于 Banner 左上角（黑色粗体大号）；Banner 内容 = 主标题「今日推荐」（粗体深棕大字号）+ 副标题「发现食堂里的美味搭配」。**本期为静态运营位（无后端取数）**；**背景图用 `<image>` 单独加载**（URL 由页面常量 `BANNER_BG_SRC` 承载，正式资产到位后只改该常量；未配置 / 加载失败**回退渐变底** `--color-primary-soft → --bg-page-grad-to`，**不做任何组件手绘**——原占位插画组件已删除）；如需动态推荐另立 change；
2. Banner 下方**通栏搜索组件**：圆角白底输入框、灰色占位「搜索菜品、食堂、套餐」、左侧放大镜图标；**搜索框右侧对齐文字按钮「筛选」**（带下拉小箭头）→ 打开既有食堂 / 价格筛选面板（筛选维度不变，与新增的大类标签栏并列）；
3. **横向大类标签栏**（详见下）——横向可滑动，选中项下方橙色短下划线高亮，未选中为黑色常规字；
4. **菜品内容区**：双列等宽卡片网格，卡间水平 / 垂直间距统一，**禁止三列**；所有卡片完整展示、底部不半截截断。

> **图片占位策略（2026-09-21 走查定稿：本期所有图片先占位，禁止无限空转加载）**：① **菜品图**——加载中淡入、加载失败 → 立即回退「餐具」图标占位块（`DishCard.image-placeholder`，禁止裂图与无限等待）；② **Banner 背景图**——未配置 / 加载失败 → 回退渐变底（见上）；③ 列表请求失败 → 显示**失败重试块**（`RetryBlock`），不静默空转。**预览前置条件**：后端（含 MySQL）已部署在**云端**，本地无需起库、本地无开发环境依赖；预览仅要求小程序可访问后端接口——若接口 / 图片域名为 http 或未登记合法域名，在开发者工具勾选「不校验合法域名」即可。

**② 吸顶态（上滑后，Banner 完全滚出视口）**

- **标题「知行食记」+ 完整搜索框（占位文案不变、右侧保留「筛选」）+ 大类标签栏整体粘性吸顶**，常驻可见，**搜索框不消失**；
- Banner 区域完全滚出、不再显示；
- 菜品列表继续双列网格，卡片内部排版与初始态**完全一致**；可继续向下加载更多；
- 右上角微信原生胶囊始终不被遮挡。

### 卡片内部固定排版（自上而下）

| 顺序 | 内容 | 样式 |
|---|---|---|
| 1 | 菜品实拍图 | 圆角与卡片一致，高约占卡片 **52%** |
| 2 | 菜品名称 | **黑色加粗**，本卡片最大字号 |
| 3 | `食堂名称 \| 档口名称` | **浅灰常规小字，纯文字、无任何彩色背景标签** |
| 4 | 左：黄色实心五角星 + 数字评分；右：**价格（橙色突出）** | 同一行 |
| ~~5~~ | ~~`月售XXX份`~~ | **2026-09-21 决议：不引入**（项目无订单 / 销量数据源，见 G 项） |

### 横向大类标签栏（2026-09-21 决议）

- **标签集合（7 项，单选、互斥）**：`全部` ｜ `套餐盖饭` ｜ `家常小炒` ｜ `面食粉类` ｜ `香锅干锅` ｜ `风味小吃` ｜ `汤饮甜品`
- **标签由后端下发**（`GET /dishes/meal-types`），**端上一个都不写死**：文案、顺序、子集、增删全由后端决定；
- **空类过滤**：后端只下发「当前有大类 = 该类」的在售菜品的大类，某类暂时空了自动隐藏、有菜自动出现，端上零改动；
- **单选**（大类之间菜品互斥），与下划线高亮 UI 一致；
- v1 **不显示计数**（省聚合查询）。

### 配色与文字层级（全站 token 层变更）

1. **主色 = 橙色（定稿，见 G4）**：两档——填充档 `--color-primary` `#C2410C`（主按钮 / 图标 / TabBar 激活图标）、文字档 `--color-primary-text` `#B93A0A`（价格 / TabBar 激活文字 / 选中态文字）；标签下划线按走查反馈取 `--color-primary-bright` `#EA580C`（图形级 ≥3:1）；**全站替换**（小程序 + Web 管理端）；
2. 页面背景：**浅米白 → 淡橙顶部渐变**；
3. 文字层级：标题 / 菜名 = 黑色粗体；正文 = 黑色常规；次要信息（食堂档口、时间等）= 浅灰。

### 底部 TabBar

固定 2 项：**首页**（橙色图标 + 橙色文字，激活）｜**我的**（灰色）。导航栏下方预留系统手势条安全区。**注：TabBar 固定 2 页为既有现状（§2 目录结构：home / mine），「移除其他 tab」无需改动。**

### 硬性约束（回归检查清单）

1. 永远**双列**菜品网格，拒绝三列；
2. 食堂档口只用**浅灰纯文字**，禁用绿色 / 彩色标签块；
3. 上滑后搜索框**必须常驻吸顶**，不能消失；
4. 所有菜品卡片**完整显示**，禁止半截截断；
5. 底部导航只保留**首页、我的**；
6. UI 布局**不得遮挡**右上角微信胶囊原生控件。

## 操作

1. 进入首页 → 自动加载第 1 页，**恒按热度倒序**（服务端固定口径，见下）。端上**不传排序参数**：原 `sortBy` / `sortOrder` 已删除（2026-09-21 决议，见「决议登记」C 项）——消除此前「首页默认流传 `heat`、筛选流与搜索流因不传参数而落到 `rating_count DESC` 分支」的口径分裂。
2. 上滑触底 → 自动加载下一页（无限加载；结束判据 = **本页返回条数 < `pageSize`**，**`total` 不是判据**——`filterTotal` 已随 MP-05/MP-06 删除，见「决议登记」F 项）。
3. 点**大类标签** → 按该大类筛选（`mealType=<枚举>`）→ 列表刷新，标签下划线切换。
4. 点搜索框右侧「筛选」→ 选食堂 / 价格区间 → 列表刷新（与大类标签**可叠加**）。
5. 点上滑后的吸顶头部 → 标题、搜索框、标签栏常驻，Banner 不回归。
6. 点卡片 → 进菜品详情（A-04）。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/dishes` | 🔓 公开 | 菜品分页列表：首页网格、大类 / 食堂 / 价格筛选、搜索共用同一端点；**只返回 `status='on'`（在售）菜品**；支持 `mealType` 大类筛选（2026-09-21 新增） |
| GET | `/dishes/meal-types` | 🔓 公开 | **菜品大类字典（2026-09-21 新增）**：下发标签栏数据源 `[{ key, label, order }]`，只含当前有在售菜品的大类 |
| GET | `/canteens` | 🔓 公开 | 食堂字典列表（筛选面板「食堂」的数据源）；**可选 `include=stalls`** 返回含档口树（反馈页位置联动用，首页不传）。**2026-09-21 决议：`GET /canteens/all` 删除并合入本端点**（见 E 项） |

## 字段

### 请求 · `GET /dishes`（query 参数 / `DishQueryReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 10（端上首页取 20；服务端有上限，超限被截断） |
| `keyword` | string | 否 | 关键词，**同时匹配菜名 `name` / 搜索别名 `alias` / 档口名 `stall.name` / 食堂名 `canteen.name`**（`DishMapper.xml` 四处模糊匹配） |
| `canteenId` | number | 否 | 按食堂筛选（筛选面板「食堂」） |
| `mealType` | string | 否 | **菜品大类筛选（2026-09-21 新增）**：单值，值域 = 大类枚举键（`set_meal` / `stir_fry` / `noodle` / `dry_pot` / `snack` / `soup_drink`）；**白名单校验，非法值 → 400**（PR-06），不静默降级 |
| `minPrice` | number | 否 | 价格下限，**单位：分**（端上由「元」在 API 层转换） |
| `maxPrice` | number | 否 | 价格上限，**单位：分** |

> **已删除参数（2026-09-21 决议）**：
> - `stallId`（按档口筛选）——端上**零发送**：筛选只有「食堂 / 价格 / 大类」，无档口入口；管理端走 `/admin/dishes`。判据同 §7.23 第 6 条（已据此删除 `GET /reviews` 的 `stallId` / `canteenId`）。
> - `sortBy` / `sortOrder`——端上**零消费**：`DishSortBy` 四值中仅 `heat` 被首页默认流使用，且 `heat` 分支内部固定 `DESC`（`sortOrder` 对任何取值均无效果）；`rating` / `price` / `created_at` 无任何调用方。排序口径统一收敛为**服务端恒热度**。
> ✅ **代码已落地（2026-09-21，change `api-slimming`）**：三个参数已从 `DishQueryReq` 删除，`DishMapper.xml` 已去掉排序 `<choose>` 分支（原 `<otherwise>` = `ORDER BY d.rating_count DESC, d.avg_rating DESC` 一并移除），查询**恒按 `heatScoreExpr` 倒序**——「不传参数 = 热度」成立，默认流 / 筛选流 / 搜索流三路排序口径一致（详见「决议登记」C 项）。
> ✅ **`mealType` 已落地（2026-09-21，change `home-ui-refresh`）**：`dish.meal_type` 列（`schema.sql` 幂等存储过程）+ 全量种子赋值 + `DishQueryReq.mealType` 白名单校验 + `GET /dishes/meal-types` 字典端点均已实现（见 H 项）。

### 响应（`data` = `PageResult<DishVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | DishVO[] | **当前页数据行**（前端以它为准） |
| `total` | number | 符合条件的总条数（服务端同口径统计）。**⚠️ 当前端上零读取**——`/dishes` 链路的结束判据是「本页返回条数 < `pageSize`」（`stores/dish.ts:305` / `:354`），原 `filterTotal` 已随 MP-05/MP-06 删除；`total` 仅在**详情评价区**被读取（`useDishPage.ts:110`） |
| `page` | number | 实际生效的页码（服务端归一化后的值） |
| `pageSize` | number | 实际生效的每页条数（同上） |

> **已删除出参（2026-09-21 决议）**：`list`（过渡期兼容字段，`PageResult`）——**双重冗余**：① 两个消费端都**优先读 `records`**（`client/src/api/shared.ts` 的 `recordsOf` = `value.records || value.list || []`；`web/src/api/adapter.ts` 的 `pageRecords` 同构），服务端恒返回 `records` → `list` 分支永不命中，**全端零读取**；② `getList()` 派生自 `records` 且参与序列化 → **同一数组被 JSON 输出两次，列表响应体积≈翻倍**。`PageResult` 注释已自认「待全部消费方切换到 `records` 后可移除」，该条件现已满足。
> `total` / `page` / `pageSize` **予以保留**：`total` 是 `PageResult` 全局共用字段（详情评价区仍以它判结束），且「服务端同口径统计」本身有语义；`page` / `pageSize` 虽同为端上零读取（端上自持页码、以「本页条数 < `pageSize`」判结束），但低成本、排障仍有价值。
> **单看 `/dishes` 端点：三者均为零读取**（见 F 项）——若将来要极致收敛，可评估「列表端点专用极简分页载体」，非本批事项。
> ✅ **代码已落地（2026-09-21，change `api-slimming`）**：`list` 已从 `PageResult` 删除并经运行时实测——`/dishes`、`/dishes/{id}/reviews`、`/admin/*` 壳字段均为 `records,total,page,pageSize`，**零 `list` 输出**。

### 响应 · `GET /dishes/meal-types`（`List<MealTypeVO>`，2026-09-21 新增）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `key` | string | 大类枚举键（用于 `GET /dishes?mealType=` 筛选） |
| `label` | string | 中文标签（**端上直接渲染，不得在前端维护映射表**） |
| `order` | number | 标签栏展示顺序（升序） |

> **形态**：标签栏**不含「全部」**（「全部」= 不传 `mealType`，由端上固定渲染为第一项）；后端只下发**当前有在售菜品**的大类（空类自动隐藏）。
> **不做出参的部分**：大类**不进公开 `DishVO`**——列表卡片不展示大类，筛选在后端完成，属「零消费即删」；后台 `DishAdminReq` / `DishAdminVO` 需要（录入下拉 + 编辑回填 + 列表筛选）。

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

> `imagesJson`（图片 JSON 原文）为内部字段，**不出参**；`hasReviewed`（我是否评价过）已下线；**`mealType`（菜品大类）不进公开出参**（2026-09-21 决议，见 H 项）。
> **公开 `DishVO` 字段精简（2026-09-18 用户拍板，见 `project_spec.md` §7.27）**：已删出参 `promoPrice` / `status`（公开接口恒只返回 `status='on'`，服务端已过滤）/ `createdAt` / `canteenId` / `stallId` / `viewCount`（只为热度服务，口径 = 一直累计）。**公开 VO 24 → 18 字段**。**（2026-09-20 §7.28：`spiceLevel` / `region` 已被 `dietType` / `ingredients` / `flavorTags` / `serveTemp` 替换；§7.29：`tags` 已删除；§7.30：`windowNo` / `updatedAt` 已删；§7.31：`latitude` / `longitude` 已删 → 公开 VO 最终 **15 字段**。）**

### 响应 · `GET /canteens`（`List<CanteenInfoVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 食堂 ID（**端上筛选用它**） |
| `name` | string | 食堂名称（**端上展示用它**） |

> `GET /canteens` 收敛为**最小字典出参**：端上 `getCanteenList()` 只消费 `id` / `name`（跨端 DTO 显式定型，禁 `any` 逃逸）。
> **已删除出参（2026-09-21 决议）**：`location`（位置描述）、`description`（简介）、`images`（图片 URL 数组）——三个字段**全端零消费**（小程序只读 `id` / `name`；管理端走 `/admin/canteens`，用另一个 VO `CanteenAdminVO`，不经 `CanteenInfoVO`）。
> ⚠️ 注意区分：§7.31 保留的是 `canteen.location` **数据库列**（留档用），与「是否作为 `GET /canteens` 公开出参」是两件事。✅ **代码已落地（2026-09-21，change `api-slimming`）**：`CanteenInfoVO` 已收敛为 `id` / `name`（运行时实测 `GET /canteens` 单行字段恰为 `id,name`）。

## 数据（读取）

| 表 | 读什么 | 中文解释 |
|---|---|---|
| `dish` | **公开查询列收窄**（非全量）：只选 15 字段所需列 + **筛选用的 `meal_type`**；`status='on'` 仅用于 WHERE 过滤、不出参 | 菜品主体：名称 / 价格 / 原价 / 图片 / 荤素 / 主料 / 口味 / 冷热 / 评分缓存。**不选** `status` / `view_count` / `created_at` / `updated_at` / `alias` / `stall_id` / `canteen_id` / `window_no` / 坐标（`DishMapper.xml` 的 `publicDishColumns` 注释明示）。**`meal_type` 仅参与筛选与字典下发，不进公开出参**（2026-09-21 新增，见 H 项） |
| `stall` | `name`、`floor` | 档口名与楼层。**不再读 `window_no`**：窗口号已随 §7.30 从公开出参删除，`DishMapper.xml` 的公开查询列同批收窄（注释明示「公开出参不含 …window_no…故此处不选」） |
| `canteen` | `name` | 食堂名（**2026-09-20 §7.31 起不再读坐标；位置表达 = 食堂名 + 档口楼层**） |

## 决议登记（原「答疑」内的冗余清理决议与留痕）

### 字段冗余审阅留痕（2026-09-21）

> 依据：端上（`client/src`）与管理端（`web/src`）消费点 grep + 服务端实现核实。
> **2026-09-21 用户决议：删除下列冗余字段**——本文档「字段」各表已按决议收敛为**目标契约**。
> ⚠️ **代码尚未改动**：删除属公开契约变更，须随契约变更 change 一并落地（各表下方已就地标注同步状态）；未同步前，服务端仍会输出/接收这些字段（多余但无害）。

**A. 零消费出参 / 参数（2026-09-21 决议：删除）**

| 项 | 位置 | 冗余性质（已核实） | 删除后影响 |
|---|---|---|---|
| `list` | `PageResult`（全部列表接口共用） | **双重冗余**：① 两个消费端都**优先读 `records`**（`client/src/api/shared.ts` 的 `recordsOf` = `value.records \|\| value.list \|\| []`；`web/src/api/adapter.ts` 的 `pageRecords` 同构），服务端恒返回 `records` → `list` 分支永不命中；② `getList()` 派生自 `records` 且参与序列化 → **同一数组被 JSON 输出两次，列表响应体积≈翻倍**（20 行/页时最明显）。代码注释自认「待全部消费方切换到 records 后可移除」——**该条件已满足** | 无（无消费方读它） |
| `location` / `description` / `images` | `CanteenInfoVO`（`GET /canteens`） | 小程序**零消费**：`types/canteen.ts` 明示「最小 DTO：消费方仅读 id/name」，`api/canteen.ts` 只映射 id/name；管理端走 `/admin/canteens`（另一 VO `CanteenAdminVO`），不经此 VO。**注意**：§7.31 保留的是 `canteen.location` **列**，与「是否作为公开出参」是两件事 | 无（`GET /canteens` 收敛为 id/name 最小字典） |
| `stallId` | `DishQueryReq`（`GET /dishes`） | 两个消费端**零发送**：首页 / 搜索的筛选只有「食堂 / 价格 / 大类」；管理端走 `/admin/dishes`。判据同 §7.23 第 6 条（已据此删除 `GET /reviews` 的 `stallId` / `canteenId`） | 无（档口筛选入口本就不存在） |
| `sortBy` / `sortOrder`（**整参数删除**） | `DishQueryReq` | 端上 `DishSortBy` 四值中**仅 `heat` 被首页默认流发送**，而 `heat` 分支内部固定 `DESC`（`sortOrder` 对任何取值都无效果）；`rating` / `price` / `created_at` 零调用方（端上无排序入口，口径见 §7.17 第 2 条「不设排序入口」） | 排序口径收敛为**服务端恒热度**，见 C 项 |
| 响应 `page` / `pageSize` | `PageResult` | 两个消费端**只发不读**（端上自持页码，结束判据用「本页条数 < `pageSize`」；`web` 同） | **保留**（低成本自描述元数据，删无收益、排障仍有价值） |

**B. 文档过期项（已就地修正）**

| 项 | 原文 | 现状 |
|---|---|---|
| `stall.window_no` | 数据表写「读 `name` / `floor` / `window_no`」 | `DishMapper.xml` 的 `publicDishColumns` **明确不选 `window_no`**（§7.30 已删该出参，注释亦声明「公开出参不含 …window_no…故此处不选」）→ 已改为只读 `name` / `floor` |
| `keyword` 匹配范围 | 「同时匹配菜名 `name` 与搜索别名 `alias`」 | `DishMapper.xml` 实为**四处**模糊匹配：菜名 / 别名 / 档口名 / 食堂名 → 已补全 |

**C. 口径不一致（2026-09-21 决议：收敛为单一热度口径）**

`sortBy` 的**缺省分支不是热度**：`DishMapper.xml:135-164` 中 `<when sortBy=='heat'>` 走 `heatScoreExpr`，而 `<otherwise>`（即**不传 `sortBy`**）= `ORDER BY d.rating_count DESC, d.avg_rating DESC`。

- 首页**默认流**由 `getHotDishesPage` 显式传 `heat+desc` → 落 heat ✅；
- **食堂筛选流**（`stores/dish.ts`）与**搜索流**（`pages/find`）**不传 `sortBy`** → 落 `<otherwise>`，实际按「评价数 → 均分」排序；
- 而端上注释写「端上不传 sortBy：列表顺序唯一由后端排序口径决定（§7.17 第 2 条『热度优先』；PR-02）」——**与后端缺省分支不符**。

→ **决议（2026-09-21）：采用方案 ①——收敛为单一热度口径**（合 §7.17 第 2 条「热度优先、不设排序入口」）。落地要求：

1. **服务端**：`DishMapper.xml` 删去 `sortBy` / `sortOrder` 的 `<choose>` 分支，恒按热度表达式（`heatScoreExpr`）倒序；
2. **参数**：`DishQueryReq` 删 `sortBy` / `sortOrder`（见 A 组）；
3. **端上**：`api/dish.ts` 的 `getHotDishesPage` 不再传排序参数、`types/dish.ts` 的 `DishSortBy` 类型删除；`stores/dish.ts` 与 `pages/find` 的「端上不传 sortBy：顺序由后端口径决定（热度优先）」注释随之**成立**（届时注释与实现一致，无需改写语义）；
4. `sortOrder` 一并删除的理由：`heat` 分支内部固定 `DESC`，该参数对任何取值都无效果，属纯冗余。

> 未落地前，筛选流 / 搜索流的实际排序仍是 `<otherwise>` 的「评价数 → 均分」——与文档目标契约不一致，属**已知待同步项**。

**D. 结构观察（非缺陷；2026-09-21 决议：保留，不拆 VO）**

列表与详情**共用 `DishVO`**（`DishDetailVO extends DishVO`，仅多 `ratingDistribution`）→ `GET /dishes` 每行下发的 15 字段中，**8 个在列表链路零消费**（**2026-09-21 复核修正**，上一版误记为 7 个）：`ratingCount`、`description`、`floor`、`dietType`、`ingredients`、`flavorTags`、`serveTemp`、多图（`images[1..]`）。列表链路实际消费 7 个：`id`（列表 key / 跳转）、`name`、`price`、首图、`originalPrice`（搜索结果卡画删除线）、`avgRating`（两张卡片都只渲染评分）、`canteenName` + `stallName`。20 行/页 ≈ 每页多下发约 160 个字段值。

**登记为「有意过度下发」**：收益是列表与详情共用一条查询列与一个 VO（少一次请求、少一份列映射真源），代价是列表多传若干字符串；当前校园规模（7 食堂）下不构成问题。若将来列表页性能吃紧，再评估拆 `DishListItemVO`（届时须同步 `dish-field-contract` capability）。

**E. `GET /canteens` 与 `GET /canteens/all` 端点合并（2026-09-21 决议：合并，删除 `/canteens/all`）**

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

**F. 复核新增（2026-09-21）：`total` 定性修正 + 2 处端上「只写不读」**

| 项 | 事实（已核实） | 处置 |
|---|---|---|
| `/dishes` 的 `total` | **端上零读取**：首页 / 搜索的结束判据 = 「本页返回条数 < `pageSize`」（`stores/dish.ts:305` / `:354`，注释明示「避免 `total` 语义不一致导致误判到底」）；原 `filterTotal` 已随 MP-05/MP-06 删除；全仓读 `total` 的只有详情评价区（`useDishPage.ts:110`）。**本页原写「按 `total` 判断结束」属错误描述，已就地更正（「操作」第 2 条、响应表 `total` 行、保留说明三处）** | **保留字段**：`total` 是 `PageResult` 全局共用（评价链路仍以它判结束）且「同口径统计」有语义；文档定性已更正 |
| 端上 `MixedResult.ratingCount` | **只写不读**：`pages/find/index.vue:286` 把 `DishVO.ratingCount` 映射进结果项，而 `FindResults.vue:88` 声明 `ratingCount?: number` 后**模板从未渲染**（只渲染 `rating` 与 `originalPrice`） | 建议清理（同 MP-05/MP-06 对 `filterTotal` 的处理：删映射 + 删类型声明）——属端上死字段，非契约变更 |
| 端上 `Dish.image` | **派生冗余**：`api/dish.ts` 的 `toDish` 同时产出 `image`（= `images[0]`）与 `images`；首页卡片读 `image` | 可选收敛：卡片改读 `images[0]`、删 `image`；建议随下次端上清理一并做 |

> 结论：**契约层冗余已清完**（A 组四项已决议删除）；F 组是**端上实现层**的零消费 / 派生字段，不动接口，可并入端上清理批次。

### G. 首页 UI 重构决议（2026-09-21）

| # | 决议 | 说明 / 影响 |
|---|---|---|
| G1 | **页面改两态结构**：初始态（Banner 完整）+ 吸顶态（标题 / 完整搜索框 / 大类标签栏整体吸顶，Banner 滚出） | 现实现仅「暖砖红头部（搜索框）+ 筛选行」，**无 Banner、无标签栏、无整体吸顶** → 本次为首屏结构重构 |
| G2 | **卡片改 5 段固定排版**，但 **`月售XXX份` 不引入**（用户否决） | 项目无订单 / 销量数据源（管理端 `todayOrders` 曾因「恒为 0、与无下单定位相悖」删除）；卡片实际为 4 段：图 → 菜名 → `食堂 \| 档口` → 星+评分（左）/ 价格（右） |
| G3 | **位置行分隔符改 `食堂名称 \| 档口名称`**（原「食堂 · 档口」），**浅灰纯文字、禁用彩色标签块** | 与首页卡片既有「无彩标签」口径一致，仅分隔符变化 |
| G4 | **主色由暖砖红改为橙色，全站 token 层变更（含 Web 管理端）——2026-09-21 定稿：两档取色** | **填充档** `--color-primary` = `#C2410C`（全站主力用途：填充底 / 图标 / 标签下划线 / 星标 / TabBar 激活图标，兼作承载白字的底色）：其上**白字 5.18:1** ✅、对白卡 **5.18:1** / 对页底 **4.69:1** ✅ ≥3:1；**文字档** `--color-primary-text` = `#B93A0A`（价格 `--color-price`、TabBar 激活文字、选中态文字、**主色浅底上的文字**）：对白卡 **5.72:1** / 对页底 **5.18:1** / 对浅底 `#FCE8D6` **4.81:1** ✅ ≥4.5:1；`--color-primary-soft` = `#FCE8D6`；含品牌色通道的阴影通道随主色 = `194, 65, 12`（`rgba(194, 65, 12, α)`）。**适用边界**：填充档**不得**作主色浅底上的文字（该组合仅 **4.35:1**）——已按此把端上 16 处「主色文字压浅底」切到文字档；Web 深色主题文字档取提亮值 `#F97336`→`#F97316`（实测 ≥4.63:1）。**取色理由（重要）**：初版曾取更亮的 `#EA580C` 作图形档，但实测其**作填充底白字仅 3.56:1、作浅底文字亦仅 3.56:1（两门槛均不达）**，而全站 **21 处**以主色作「填充底 + 白字」→ 故填充档必须取白字安全值，**两档而非三档**。已同步修订 `client-visual-language` delta（两档 + 适用边界 + 浅底场景）与 `project_spec.md` §4 / §7.34。触达 `theme/tokens.ts`、`generated-colors.css`、Web `variables.css`；另：`IconSvg` 传 `var()` 会回落近黑（data-uri 不解析 var），本批已把 59 处图标取色改为 `COLOR_MAP` 真源实色 |
| G5 | **页面背景改「浅米白 → 淡橙顶部渐变」——2026-09-21 定稿** | `--bg-page-grad-from` = `#FFF9F3` → `--bg-page-grad-to` = `#FFEFE0`（取较浅淡橙端，保证图形档对渐变最深处仍 ≥3:1）；渐变以 token 声明并登记于 `ui-token-system` |
| G6 | **Banner 为静态运营位（无后端取数、无推荐算法）——2026-09-21 走查回退修订：背景图单独加载 + 占据页面最顶部区域** | Banner = **顶部整块背景**（左右通栏无间隙、上移「状态栏 + 标题行」两带、**含状态栏背后**，9.5 定稿），作为「知行食记」标题的背景（标题叠加绘制其上，z-index 抬升）；**背景图用 `<image>` 单独加载**（`mode="aspectFill"` 铺满，URL 由页面脚本常量 `BANNER_BG_SRC` 承载——正式资产到位后只改该常量；未配置 / 加载失败回退渐变底 `--color-primary-soft → --bg-page-grad-to`）。**不再用组件手绘背景**：原占位组件 `pages/home/HomeBannerArt.vue` 已按 PR-05 删除（零消费不留存，git 历史可找回）。折叠几何不变：窗口 + 补偿 ≡ H（H = 状态栏高 + 标题行高 + 284rpx），1:1 跟手、无跳变 |
| G7 | **搜索框右侧「筛选」按钮 = 打开既有食堂 / 价格筛选面板（筛选维度不变，与大类标签栏并列、可叠加）——确认** | 原「两行头部（搜索框一行 + 筛选按钮一行）」作废（`home-filter` delta 已 MODIFIED）；`FilterBar` 的食堂 / 价格维度与 `stores/dish.ts` 的筛选状态沿用，仅改交互载体（文字按钮 + 下拉箭头）与锚定位置 |
| G8 | **硬性约束 6 条**（双列禁三列 / 档口无彩标签 / 搜索框常驻吸顶 / 卡片不截断 / TabBar 仅 2 项 / 不遮胶囊）作为回归检查清单 | 其中「TabBar 仅 2 项」**已是现状**（§2：home / mine），无需改动 |
| G9 | **TabBar 仅首页 / 我的**，首页橙色高亮 | 无改动（现状一致），仅配色随 G4 变化 |

> ⚠️ **落地顺序**：G4 / G5 是**全站 token 变更**，须与视觉规范（§4）同步修订后再动代码；本页 UI 重构属独立 change，与「契约精简」批次**解耦**（后者不改视觉）。

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
4. **出参**：**公开 `DishVO` 不加 `mealType`**（卡片不展示 → 零消费即删）；后台 `DishAdminReq` / `DishAdminVO` 增加（录入下拉 + 编辑回填 + 列表筛选）；
5. **端上**：标签栏完全由字典端点驱动（不写死任何标签与中文映射），单选，切换即重置分页；
6. **数据修正（同批）**：珍珠奶茶、杨枝甘露 的 `ingredients` 现误标为 `rice`（米）→ 改正（可新增 `drink` 枚举值或留空），否则详情页「主料」会显示「米」；
7. **合规登记**：本项目属「给 dish 新增字段」，但承担**分类语义**，须在 `project_spec.md` §7.23 **显式登记**「新增单值大类字段替代原品类分类能力」，避免被后续误判为「品类维度复活」；
8. **关联影响**：`GET /dishes` 参数集由 6 项增至 7 项，会**修改** `api-slimming` 已定稿的 `dish-list-query` capability（后者声明「恰为六项」）——须在该 change 的 delta 中一并 MODIFY，见「落地顺序」。
