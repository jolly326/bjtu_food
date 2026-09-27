# 首页菜品浏览 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 归属功能文档：[client-首页菜品浏览.md](../feature/client-首页菜品浏览.md)
> **口径分工**：**页面 UI 口径以本文件为唯一真源**；功能流程 / 接口 / 字段 / 落库以功能文档为准。
> 落点：`pages/home/index`（主包 · **TabBar 页**，`reLaunch` 切换，恒不显示返回箭头）。
> 预览图：`docs/ui/client-首页菜品预览.png`（仅观感对照，实现以本文正文为准）。
>
> ⚠️ **本文件只写「当前口径」**。逐轮变更、被推翻的方案、失败尝试与历史决议，一律记录在
> [`docs/UI_FRONTEND_LOOP.md`](../UI_FRONTEND_LOOP.md)（含「为什么试过六种表面方案都失败」的完整记录）。

**一页速查（当前口径）**

| # | 要点 |
|---|---|
| 1 | 顶部顺序恒定：**固定标题带 → 滚动区［Banner（四周留白圆角卡）→ 吸顶容器（搜索区 + 大类标签栏，一个组件）→ 双列网格］→ TabBar** |
| 2 | 页根 = flex 列且**显式定高**（`height: 100vh; height: 100dvh`）：`padding-top` = 标题带高、`padding-bottom` = 菜单栏 + 安全区；滚动区 `flex: 1; min-height: 0` |
| 3 | 吸顶容器用**原生 `position: sticky`**（`top: 0` = 滚动区顶 = 标题带下沿）；它是**全页唯一需要表面**的一条：**未吸顶完全透明、吸顶态铺背景图原样切片** |
| 4 | 标题带 / TabBar **恒透明**（内容不从它们背后经过）；全站只有**一处** `fixed` 页底壁纸 + **一层**统一纱 `--page-wash` |
| 5 | 搜索区 = **单颗胶囊**（放大镜 / 输入 / 清除 ✕ / **内嵌「搜索」按钮**），首页 `entry` 与搜索页 `input` 共用 `SearchBar` |
| 6 | 口径总纲：间距见 §7、字号见 §8、圆角阴影见 §9、色彩见 §10、回归清单见 §13 —— **改任何视觉值只改这些表** |

---

## 1. 页面结构（自上而下）

```
固定标题带（fixed，永久不动、恒透明）
└ 滚动区（flex: 1，顶边 = 标题带下沿，底边 = 菜单栏上沿）
   ├ Banner        ：四周留白（左右各 12px）的圆角图片卡（16:10，轮播）
   ├ 吸顶容器      ：搜索区 + 横向大类标签栏（一个组件；sticky 到顶后吸住）
   └ 双列菜品网格  ：紧随吸顶容器，只在滚动区内滚动
TabBar（fixed，透明底）
```

**骨架与边界**

- 页根（`.home-page`）是 flex 列，**显式 `height: 100vh; height: 100dvh`**（不再依赖全局 `.page` 兜底；`box-sizing: border-box` ⇒ 页根恰为一屏）；
- `padding-top` = 标题带高（带为 `fixed`、不占位）、`padding-bottom` = 菜单栏 + 安全区；
- 滚动区 `flex: 1; min-height: 0` ⇒ 内容未超过可视高时**既不出现滚动条、也没有可滚的空白区**；
- ⇒ **标题带与菜单栏背后没有任何内容经过**；**只有吸顶容器**有卡片从背后滚过（唯一需要表面的一条）。

**关键常量（由页面常量推导，禁硬编码裸值）**

| 常量 | 定义 | 375 宽机型 |
|---|---|---|
| `H_t` 标题带高 | 状态栏高 + `navBarHeight`（`(胶囊.top − 状态栏高) × 2 + 胶囊高`） | ≈ 88px |
| 吸顶容器高 | 上 padding 12 + 搜索行 + 标签行 44 + 下 8 | ≈ 100px |
| `H_b` Banner 高 | `max((min(屏宽, 720) − 2 × 12px) × 10/16, 160px)` | ≈ 219px |
| `B_b` Banner 下缘 | `H_t + 12 + H_b`（= 吸顶锁定点） | ≈ 319px |
| 网格起点 | `B_b + 吸顶容器高` | ≈ 419px（首屏露出第一行双列卡） |

**滚动驱动**：全页滚动 JS **只有一处** —— `@scroll` → 一个**离散开关** `pinned`（只在跨吸顶锁定点翻转一次，驱动吸顶容器的切片表面），**不做逐帧对齐**。

> ⛔ **页面级下拉刷新：本页不提供**（搜索页 / 系统通知 / 我的主页同）—— 下拉表现为普通滚动回弹。数据更新走「首屏拉取 + `onShow` 兜底 + 失败重试块」。

---

## 2. 固定标题带（跨页统一）

- **定位**：`position: fixed`，永久固定在页面左上角，不随滚动、不参与任何吸顶位移；
- **垂直对齐**：与微信右上角**原生胶囊同一条水平线**（高 = 状态栏 + 一行胶囊高，垂直中心对齐）；右上角为胶囊避让区，标题不得进入（超长省略）；
- **水平起点**：页面级 gutter `--spacing-md`（与搜索行 / 标签栏 / 网格同轴）；
- **文案按页配置**：首页 = 「知行食记」；位置 / 高度 / 对齐 / 配色跨页一致，各页只换文案；
- **配色**：`--text-primary` `#2D1F14`、`--font-title` 44rpx、`--weight-bold`；**不做白字、不描边、不加遮罩**（它落在页底「壁纸 + 一层纱」上）；
- **表面**：**恒透明、无任何表面、无透明度过渡**；纯文本**无点击行为**；
- 三处文字（左页面名 / 左「返回」/ 居中页面名）字号统一 `--font-title`，不提供按页覆盖。

---

## 3. Banner 区

### 3.1 结构与交互

- **四周留白**：左右各 `--spacing-md`（12px）、**上缘距标题带下沿** `--spacing-md`（滚动区 `padding-top` 承担）、**下缘距吸顶容器** `--spacing-lg`（吸顶容器 `padding-top` 承担）；四角 `--radius-card`；
- 它是滚动区首块**「浮起来」的图片卡**：随页面上滑、在**标题带下沿被裁**（不会从带背后经过）；
- **卡内零端上文本**（素材自带的文案属图片内容）；**无点击交互**（v1 纯展示；做跳转须另立 change）。

### 3.2 比例 16:10（锁定）

- 总高 = `max((min(屏宽, 720) − 左右各 12px) × 10/16, 160px)`；**取宽必须与 `App.vue` 宽屏限宽（720px）同源**，否则 ≥768px 窗口下比例失真；
- 太扁（16:9）画面局促、太高（4:3）会把搜索区与首行卡片挤出首屏；**加载中 / 失败一律同高占位**（块高不变 ⇒ 下方内容不跳变）。

### 3.3 素材约定

| 项 | 约定 |
|---|---|
| 主体 / 氛围 | 真实校园食堂感食物摄影；暖橙光线、干净桌面、轻微景深 |
| 构图 | 主体居中偏下、四周留白干净；**画面内不出现端上文字** |
| 质感 | 真实摄影感，不做插画式营销海报（本产品是信息工具） |
| 比例 | **一律 16:10**（如 1500×938）；混比例会导致撑高 / 压扁 |

**本地图落位**：放 `client/src/static/images/`，引用写绝对路径 `/static/images/xxx.jpg`（不走打包哈希）；命名 kebab-case、单张 ≤ 300KB。
⚠️ 小程序 WXSS 的 `background-image: url()` **取不到包内本地路径**（真机报错）⇒ 本地图只能用 ① 绝对定位的 `<image>` 层（壁纸即此路）② base64 ③ 网络 URL。

### 3.4 轮播（数据来自 `GET /banners`）

- 整块 = 一个 `<swiper>`，每项 `<image mode="aspectFill">`；**严格按返回顺序渲染**（服务端已按 `sort_order` 升序、只返回启用项），端上不排序、不写死 URL；
- **多张**：自动轮播（`AUTOPLAY_INTERVAL = 4000ms`）+ 循环 + 底部居中指示点 + 可手滑；
- **仅一张**：不自动轮播、**不显示指示点**（避免「只有一个点」的假轮播）；
- **空数组 / 请求失败**：整块退化为灰底空态 —— `var(--bg-soft)` + 居中**中性 `empty` 图标**（`--text-tertiary`，尺寸建议 120）；**不写任何文字说明、不加白卡 / 投影 / 渐变**；图标键**取中性 `empty`，不得用 `dish`**；
- Banner 请求与列表**并行**，失败**不阻塞**首屏网格；**单张图加载失败**只该张降级为灰底 + `empty`，其余张不受影响。

---

## 4. 搜索区（单胶囊）

> 本区是首页的**第二视觉中心**，也是全页**唯一的行动号召**（唯一实心主色块）。

### 4.1 结构

| 项 | 设计值 |
|---|---|
| 结构 | **单颗胶囊**：自左向右 = 放大镜 / 输入框（entry 模式为说明文案）/ 有值时清除 ✕ / **内嵌「搜索」按钮**（填充档主色 + 白字，与胶囊等高、四周留 `--spacing-xs`）—— 首页与搜索页**同一组件** `SearchBar` |
| 胶囊 | 白底 `--bg-card` + `--radius-pill` + `--shadow-card`（极轻）；放大镜 `--text-placeholder` |
| 胶囊内文案 | 「**搜索菜品、食堂、档口**」（`--text-placeholder`） |
| 搜索按钮 | 填充档 `--color-primary-fill` `#B4531A` + 白字 `--color-on-primary`，`--radius-pill`，**按文字定宽、不与胶囊等分** |
| 高度 | 胶囊与按钮同高 = `--capsule-h`（与微信原生胶囊同高） |
| 触达 | 均可点区域 ≥ 88rpx（`::after` 透明扩张，不改视觉尺寸） |

**三条设计判断**：① 胶囊 `flex: 1` 占满剩余宽度，明确承担「主要搜索入口」；② 按钮内嵌不贴死（不做矩形、不两件并列）；③ 搜索区与标签栏靠**间距层级**区分（块内 8px / 块间 16px），**不加分隔线、不加容器底色**。

### 4.2 占位文案口径（两页同源）

占位**只列真实可搜的维度**：搜索走 `GET /dishes?keyword`，服务端只匹配**菜名 / 档口名 / 食堂名**；**「套餐」不是可搜维度**（项目无套餐实体，`meal_type` 大类不参与关键词匹配），**不得**写入占位。该文案由 `components/SearchBar.vue` 的默认 `placeholder` **单点承载**，改动须两页同批。

### 4.3 取色依据

| 组合 | 实测 | 门槛 | 判定 |
|---|---|---|---|
| 白字 on `#B4531A`（采用） | **5.01:1** | 4.5:1 | ✅ |
| 白字 on `#E67E22`（不采用） | **2.85:1** | 4.5:1 | ❌ 不可作填充底 |

---

## 5. 大类标签栏

### 5.1 规格

| 项 | 设计值 |
|---|---|
| 数据来源 | `GET /dishes/meal-types` → `[{ value, label, order }]`：首项由后端下发 `{ value: null, label: "为你推荐" }`，后续为在售分类；**字典顺序 = 后端返回顺序（推荐首项 + 常量声明序），与热度无关**——热度只决定各流内菜品顺序，不决定标签顺序；**端上只消费 `value` / `label`**，按返回顺序**全量直出**（不前置硬编码「全部」） |
| 空类自动隐藏 | 后端只下发当前有在售菜品的大类，端上零改动 |
| 标签样式 | **纯文字导航**——无胶囊底、无边框、无背景块 |
| 未选中 / 选中 | 字色 `--text-body` / `--text-title`；字重 `--weight-regular` / `--weight-semibold` |
| 选中下划线 | `--color-primary-amber` `#F5A623`，高 6rpx、全圆角、长度贴合文字、紧随文字 4rpx |
| 交互 | 横向可滑动 + **单选**；点击切换即刷新；**点击已选中项不发请求**；不显示计数 |

### 5.2 光学间距（唯一来源）

- 标签行高 **88rpx**（触达下限，不得压低）；文字在行内**上偏置 24rpx**、下划线紧随文字 **4px**；
- 「搜索区 → 标签文字」= 行内偏置（≈**12px**，唯一来源）；「下划线 → 卡片首行」= 容器下 padding `--spacing-sm` + 行底余量 ≈ **16px**；
- **下划线不得吸到行底**（会离文字 ≈16px、与标签脱开）；行外 padding 与行内偏置**不叠加**。

### 5.3 展示与切换（实现口径）

**展示**：① 渲染项 = 后端响应直出展开（空类自动隐藏）；② 横向可滑动（`scroll-x`，隐藏滚动条）+ **选中项自动滚入视口**（稳定 id：`mt-tab-{value}` / `mt-tab-recommend`）；③ 下划线是**常驻节点 + opacity 切换**（不做 `v-if`，避免行高跳动；纯装饰 `aria-hidden`，选中语义由字重 + `aria-label` 表达）。

**切换链路（一次点击）**

| 步骤 | 行为 |
|---|---|
| ① 点击 | `value === activeValue` → **直接 return，不发请求** |
| ② 写态 | `dishStore.setHomeMealType(value)` → `filterMealType = value` + `fetchHomeDishes(true)` |
| ③ 保留旧列表 | 重置分页但**不清空 `homeList`**（清空会让 `scroll-view` 把滚动位置钳回顶部：切标签弹回页首） |
| ④ 分页复位 | `homePage=1` / `homeFinished=false` / `homePageLimited=false` / `homeError=false` |
| ⑤ 竞态守卫 | `homeFetchSeq++`，**过期响应一律丢弃**（连点多个大类不串序） |
| ⑥ 不回顶 | 页面不调用任何回顶逻辑 |
| ⑦ 在途 | 切类**不触发**「静默加载中」（`HomeContent` 只订阅 `LOADING_KEY_HOME`）⇒ 旧列表继续在屏、无闪白 |
| ⑧ 结果 | 新数据到达替换列表；失败 → `homeError=true`，有数据则静默保留，空才渲染 `RetryBlock` |

**字典可用性**：首屏 `onLoad` **不 await** 字典（`void fetchMealTypes()`）⇒ 字典失败不阻塞列表（降级为 `[{ value: null, label: "为你推荐" }]`）；`onShow` 兜底重试（**仅「从未成功」时**）；`fetchMealTypes` 内顺带校正选中项（所选大类已不在字典 → 自动回落「为你推荐」）。

### 5.4 排序口径（端上无排序入口）

| 项 | 口径 |
|---|---|
| 默认（「为你推荐」流） | **会话种子稳定伪随机序**：`ORDER BY CRC32(CONCAT(seed,'-',id)), id`；`seed` 在每次列表 reset 时由端上**重掷**、翻页沿用 ⇒ 每次进入整体重洗、同次浏览顺序稳定（翻页不重不漏） |
| 大类 / 搜索流 | **服务端固定热度倒序**：`heatScoreExpr = view_count × 1 + rating_count × 100 + avg_rating × 20`；带 `mealType` / `keyword` 时 `seed` 不参与 |
| 可选排序 | **无**（无按钮 / 面板 / 下拉 / 胶囊，不传任何排序参数） |
| 传参 | `GET /dishes` 恰 5 项：`page` / `pageSize` / `keyword` / `mealType` / `seed` |
| 防回退 | **不得**新增「综合 / 最新 / 价格↑↓ / 距离」排序入口（距离能力已下线）；恢复须另立 change |

**筛选维度**：首页**无任何筛选入口**（唯一维度 = 大类标签栏）；搜索页（find）**无食堂 / 价格筛选胶囊**。

---

## 6. 菜品卡片（四段固定排版）

### 6.1 阅读顺序与权重

**菜品图 → 菜名 → 食堂/档口 → 评分/价格**

| 层级 | 元素 | 处理 |
|---|---|---|
| 第一 | 菜品图 | 大图、真实摄影，占卡片 ≈52% 高 |
| 第二 | 菜名 | 文字层第一级：32rpx + `--weight-semibold` + `--text-title`（可 2 行）——「第一级」由**位置 + 字重 + 深色**表达，不等于字号最大 |
| 第三 | 食堂 / 档口 | 浅灰小字、纯文字（无图标、无标签块） |
| 第四 | 评分 / 价格 | 评分左（暖黄星 + 数字，**常规字重**、安静）；价格右（文字档橙、**粗体 36rpx**，全卡最大字号 = 信息强调） |

### 6.2 四段规格

| 段 | 内容 | 样式 |
|---|---|---|
| 1 | 菜品实拍图 | 圆角同卡片；比例 **3:2**（≈卡片高 52–55%，兼顾信息密度与 CLS=0）；无图 / 失败 → `var(--bg-soft)` 灰底 + 居中 `IconSvg name="dish"`（`--text-tertiary`） |
| 2 | 菜名 | `--font-subtitle` 32rpx `--weight-semibold` `--text-title`；超 2 行省略；**不压在图片上** |
| 3 | `食堂名称 \| 档口名称` | `--font-body` 28rpx `--text-subtitle` 常规；**纯文字**；超长单行省略 |
| 4 | 左：黄色实心星 + 数字；右：价格 | 同行两端对齐；评分 `--font-body` **`--weight-regular`** `--text-body`，星 34rpx（= 评分文字 + 6rpx 光学补偿）、`--color-star`；价格 `--font-h3` 36rpx `--weight-bold` `--color-price` |

> **组内间距**：菜名 → 位置行 4px（同「文字组」，紧）；位置行 → 评分/价格行 8px（跨组，松）。
> **字重预算（全卡只有两处「重」）**：菜名 `--weight-semibold` + 价格 `--weight-bold`；位置行与**评分行均常规** —— 一屏 4 行若 3 行都加深，第一眼没有落点。价格允许比菜名大（36 > 32）：价格是「信息强调」，菜名的第一级由位置 + 深棕 + 可 2 行表达。

### 6.3 容器与按压

卡片 = `--bg-card` + `--radius-card` + `--shadow-card`（极轻）+ `overflow: hidden`（图片裁进圆角）；按压 = `hover-class` 透明度微降（`opacity: .88`），**不用 `transform: scale`**（避免与图片淡入的合成层叠加抖动）。**不引入「月售 XX 份」**（无数据源）；列表末尾无引导 / 补录组件。

### 6.4 实现口径

**① 列表渲染**：数据源 `dishStore.homeList`；**奇偶分列**（一次 `forEach`，O(n)，两列宽度恒定）；列表项 `key = wf-${id}`（**不掺列内序号**，否则加载更多会整列重建 → 闪烁）；`HomeContent` 是**纯展示组件**（分页 / 加载态全在 store）；首屏与切类在途**整块不渲染**（无骨架屏、无 loading），触底加载是**另一个 loading key**（只在列表末尾出「正在加载更多…」），失败且无数据 → `RetryBlock`（失败 ≠ 空态）。

**② 分页与封顶**

| 项 | 值 |
|---|---|
| 单页条数 | `HOME_PAGE_SIZE = 10` |
| 结束判据 | **本页返回条数 < `pageSize`**（⚠️ 不读 `total`） |
| 页数上限 | `HOME_MAX_PAGES = 10` ⇒ 最多 100 条（防列表无上限增长导致分列全量重算） |
| 达顶表现 | 「已展示前 100 个结果，切换大类可查看更多」（不静默截断） |
| 触底提前量 | `LOWER_THRESHOLD_PX = 300` |

**③ 图片懒加载**：`<image lazy-load>` + `getThumbImageUrl(coverImage)`（`_thumb` 规格 + 相对→绝对，详情页大图才用原图）；`@load` → `opacity` 淡入（`--duration-slow`）；`@error` → 灰底 + `dish` 图标（禁裂图）；容器**固定 3:2 等比盒** ⇒ 加载前后块高一致（CLS = 0）。

**④ 价格**：API 层 `fenToYuan` 转元；展示恒 `¥{formatPrice(price)}`（`utils/money`）；**页面 / 组件内禁止裸算金额**；**本卡不展示划线原价**（`originalPrice` 首页零消费，仅搜索页结果行消费）。

**⑤ 标签**：**本卡无标签**（无 chips / 角标 / 大类文字）；位置行是纯文本 `食堂名称 | 档口名称`；`mealType` 不在卡片出现。防回退：不得加彩色标签块或「折扣 / 新品」角标。

**⑥ 点击**：整卡可点（`role="button"` + `aria-label="{菜名}，{价格}元"`）；链路 `DishCard @tap` → `emit('select')` → `HomeContent.goToDetail` → `navigateTo(dishDetailUrl(id))`；卡片内**无任何二级可点件**。

**⑦ 响应式**：全量 `rpx`（750 设计宽）；Banner 与壁纸用**实测** `windowWidth` / `windowHeight`（**禁 `vh`**，壁纸层全站只有一处）；双列 = `flex` + 两列 `flex: 1 1 0` + `gap: 12px`；滚动区底部预留 `var(--tabbar-height) + env(safe-area-inset-bottom)`；标签栏 `scroll-x` 隐藏滚动条 ⇒ **页面不出现横向滚动条**。

---

## 7. 间距系统（唯一真源）

> 全部用 token，**禁止裸值近似**。左右外边距恒为页面 gutter `--spacing-md`（与标签栏首项文字缘、网格同轴）。

### 7.1 顶部各块

| 相邻关系 | 间距 | token |
|---|---|---|
| 标题带下沿 → Banner 上缘 | **12px** | `--spacing-md`（滚动区 `padding-top`） |
| Banner 左右缘 → 屏幕左右缘 | **12px** | `--spacing-md` |
| Banner 下缘 → 搜索区 | **16px** | `--spacing-lg`（吸顶容器 `padding-top`） |
| **搜索区下沿 → 标签文字** | **≈12px** | 标签行 `padding-top` 24rpx（行内偏置，**唯一来源**） |
| 标签栏下沿 → 网格首行 | **≈16px** | 容器 `padding-bottom` `--spacing-sm` + 标签行底余量 ≈8px |
| 网格左右外边距 / **卡片间距（行 · 列）** | **12px** | `--spacing-md` |

> **层级规则**：块之间 > 块内部，且块之间 > 卡片间距 —— 块间 16px（`--spacing-lg`）、块内 8px（`--spacing-sm`）、卡片间距 12px（`--spacing-md`）。
> **标签栏纵向**：行内偏置 / 行底余量与行外 padding **不叠加**（行外上 0、行外下 `--spacing-sm`）。

### 7.2 卡片内间距

| 位置 | 间距 | token |
|---|---|---|
| 图片底 → 菜名 | 8px | 信息区 `padding-top` `--spacing-sm` |
| 菜名 → 食堂 / 档口 | 4px | `--spacing-xs` |
| 食堂 / 档口 → 评分 / 价格 | 8px | `--spacing-sm` |
| 卡片左右 / 底部内边距 | 12px | `--spacing-md` |

### 7.3 触达尺寸

| 可点件 | 视觉尺寸 | 命中区 |
|---|---|---|
| 搜索胶囊 / 内嵌「搜索」按钮 | 高 `--capsule-h` | `::after` 上下各扩 16rpx → ≥88rpx |
| 大类标签 | 行高 88rpx（含行内偏置） | 即本体（宽 = 文字 + 左右各 24rpx） |
| 菜品卡片 | 整卡 | 即本体（`role="button"`） |

---

## 8. 字体层级（唯一真源）

| 元素 | 字号 | 字重 | 字色 |
|---|---|---|---|
| 固定标题带 | `--font-title` 44rpx | `--weight-bold` | `--text-primary` `#2D1F14` |
| 搜索胶囊内文案 | `--font-body` 28rpx | `--weight-regular` | `--text-placeholder` |
| 搜索按钮 | `--font-body` 28rpx | `--weight-semibold` | 白 `--color-on-primary` |
| 标签栏（未选中 / 选中） | `--font-body` 28rpx | `--weight-regular` / `--weight-semibold` | `--text-body` / `--text-title` |
| 菜名 | `--font-subtitle` 32rpx | `--weight-semibold` | `--text-title` `#2D1F14` |
| 食堂 / 档口 | `--font-body` 28rpx | `--weight-regular` | `--text-subtitle` `#7F6A55` |
| 评分 | `--font-body` 28rpx | `--weight-regular` | `--text-body` `#4A3520` |
| 价格 | `--font-h3` 36rpx | `--weight-bold` | `--color-price` `#B4531A` |
| 触底 / 加载更多提示 | `--font-small` 24rpx | `--weight-regular` | `--text-tertiary` |

> **判据**：菜名必须是卡片文字层第一级（位置行 / 评分行不得抢）；「搜索」按钮文案不得小于相邻占位字号；正文级文本**不得低于 12px**。
> **与设计稿的两处有意偏差**：① 搜索按钮字重用 `--weight-semibold`（小程序端 `500` 多数机型无真字重、会回落 400）；② 卡片圆角取全站 `--radius-card` 32rpx（跨页白卡一致性优先，8rpx 差异在双列小卡上不可辨）。

---

## 9. 圆角与阴影

| 元素 | 圆角 | 阴影 |
|---|---|---|
| 搜索胶囊 | `--radius-pill` | `--shadow-card`（极轻） |
| 搜索按钮 | `--radius-pill` | 无 |
| 菜品卡片 | `--radius-card` 32rpx | `--shadow-card`（极轻） |
| 菜品图 | 与卡片一致（`overflow: hidden`） | 无 |
| 大类标签栏 | — | **无阴影**（无表面） |

> **阴影口径**：校园信息工具的阴影必须**轻**（低透明度、小位移、大模糊）—— 厚重阴影读作「营销感」。
> 若要改卡片圆角档位，属**全站圆角标度调整**（改 `App.vue` 的 `--radius-card`），**不得**只在本页局部覆盖。

---

## 10. 色彩系统（唯一真源）

### 10.1 主色语义分档（禁止混用）

| 分档 | Token | 色值 | 职责 |
|---|---|---|---|
| 填充 / 文字档 | `--color-primary-fill` = `--color-primary-text` = `--color-price` | **`#B4531A`** | 「搜索」按钮填充底、价格、选中态 / TabBar 激活文字 |
| 图形档 | `--color-primary-amber`（= `--color-primary-bright`） | **`#F5A623`** | 标签选中下划线等**纯图形** |
| 橙档 | `--color-primary-orange` | **`#E67E22`** | 关键图标（**不得**作填充底 / 正文色） |
| 浅底档 | `--color-primary-yellow` / `--bg-soft-yellow` | `#FFD166` / `#FFF3D6` | 角标、chip 底（只作浅底 / 装饰） |
| 点击态 / 深强调 | `--color-orange-deep` | `#D35400` | 按钮点击态、图形级强调 |
| 星色（独立语义色） | `--color-star` | **`#FBBF24`** | 评分星；**不随主色换肤** |

**三条硬规则**：① 价格不得用 `#E67E22`；② 星级不得用 `#F5A623` 或任何主色档；③ 「搜索」按钮填充底不得用 `#E67E22`（白字仅 2.85:1）。

### 10.2 色板与取色边界

**落地**：`client/src/theme/tokens.ts`（`COLOR_MAP` / `CSS_VARS` 真源）→ `theme/generated-colors.css` → `App.vue` `@import`（Web 端只同步主色系）。

- **背景**：`--bg-page` `#FFF8EF` ｜ `--bg-card` `#FFFFFF` ｜ `--bg-soft` `#EDE9E5`（chip / 分段槽底色；**图片占位灰底为 `--bg-placeholder`**，Round 31）｜ `--bg-soft-orange` `#FFE8D1` ｜ `--bg-soft-yellow` `#FFF3D6`；
- **文字四档**：`--text-title` `#2D1F14`（15.1:1）｜ `--text-body` `#4A3520`（10.9:1）｜ `--text-subtitle` `#7F6A55`（4.86:1）｜ `--text-placeholder` `#B5A594`（2.39:1，**仅作输入占位**）；
- **功能色**：success `#2E7D32` ｜ warning `#E67E22` ｜ error `#C62828` ｜ info `#1565C0`。

**取色边界（红线：承载白字的填充底 ≥4.5:1、承载信息的文字 ≥4.5:1、图形 ≥3:1）**

| 组合 | 实测 | 判定 |
|---|---|---|
| 白字 on `#E67E22` | 2.85:1 | ❌ 不可作填充底 |
| 白字 on `#D35400` | 3.88:1 | ⚠️ 仅点击态 / 图形 |
| `#E67E22` / `#D35400` 作文字 on 页底 | 2.85 / 3.68:1 | ❌ 不可作正文 |
| `#F5A623` 描边 / 图形 on 白 | 2.03:1 | ❌ 不可作唯一可辨边界（⇒ 搜索框不加描边） |
| `#F5A623` 短下划线（选中另有字重承载） | 2.03:1 | ✅ 装饰豁免 |
| 白字 on `#B4531A` ／ `#B4531A` 作文字 on 页底 | 5.01 / 4.75:1 | ✅ |

### 10.3 星色口径

评分星 **SHALL** 用独立语义 token `--color-star`（`#FBBF24`），**SHALL NOT** 归入主色档、**SHALL NOT** 随主色换肤；空星 `--color-star-empty` `#E5E5EA`。

---

## 11. 背景与滚动

### 11.1 背景 = 全站唯一的 `fixed` 页底壁纸 + 一层统一纱

| 层 | 内容 | 实现 |
|---|---|---|
| ① 壁纸 | 本地图 `client/src/static/images/home-bg.jpg`（750×1501、≈115KB） | `components/PageWallpaper.vue` 的 `<image mode="aspectFill">`（本地图**必须走 `<image>`**）；盒子 = 满宽 × **实测视口高**（内联 `heightPx`，`wx.getWindowInfo().windowHeight`，**禁 `vh`**） |
| ② 纱 | 全站 token `--page-wash` = `rgba(255,248,239,0.6)`（唯一定义处 = `App.vue` 的 `page{}`） | 同组件内一个 `<view>` 的 `background-color`，**铺满整张壁纸、处处相同**（无分段 / 无渐变）。**α 是「背景突出度 ↔ 文字可读性」的唯一旋钮**，改一处即全站生效 |
| ③ 内容 | 标题带 / 滚动区（Banner + 吸顶容器 + 网格）/ TabBar | 壁纸层 `z-index: var(--z-page-bg)`（**−1**）⇒ 天然在内容之下，内容无需补 `z-index` |

**为什么不需要给横条做「表面」**：滚动区被结构性夹在「标题带下沿 ↔ 菜单栏上沿」之间 ⇒ 标题带 / 菜单栏背后**始终只有壁纸本体**，不铺任何表面即天然连续（历史六种表面方案的全部失败记录见 loop 文档）。
**唯一例外 = 吸顶容器**（卡片会从它背后滚过）：`v-if="pinned"` 只在吸顶态渲染**背景图原样切片** —— 外层 `overflow: hidden` + 内层页底同款壁纸（**同 `src`**、同实测盒高），按**标题带高 `titleBandPx`** 上移贴回视口原点（与 `AppTitleBand.height`、页面 `padding-top` **三者同源**，不再二次测量）；未吸顶时完全透明。

**硬性要求**

1. 壁纸 SHALL 由 `<image>` 渲染；**SHALL NOT** 写成 WXSS `background-image: url(/static/…)`；纱 SHALL 由 `--page-wash` **单点**承载、随壁纸整张统一；
2. 壁纸盒高 `heightPx` SHALL 由调用方实测 `windowHeight` 下发；**SHALL NOT** 用 `vh`，也不得由多个组件各自测算；
3. 标题带 / TabBar **SHALL NOT** 铺任何表面；吸顶容器 **SHALL** 未吸顶完全透明、吸顶态铺背景图切片（内层 `absolute`，`fixed` 会逃出裁切）；**SHALL NOT** 用磨砂 / 纯色底 / 半透明蒙版、**SHALL NOT** 逐帧下发偏移（会滞后 1–2 帧 ⇒ 撕裂）；
4. **吸顶容器的位移 SHALL 由原生粘性定位承担**（`position: sticky` + `top: 0`，写在滚动区内）；**SHALL NOT** 用「滚动回调 + `setData` 下发 `transform` / `top`」模拟（跨线程延迟 ⇒ 慢半拍 / 闪现）；
5. 滚动区 **SHALL `flex: 1` + `min-height: 0`**，页根 **SHALL 显式 `height: 100vh; height: 100dvh`**，上下边界由 `padding-top` / `padding-bottom` 界定 —— **SHALL NOT** 让滚动区延伸到标题带 / 菜单栏之下；
6. 壁纸 ≤300KB、宽度 ≥750；**换壁纸 = 换文件**（或改 `PageWallpaper.vue` 的 `src` 默认值），不改任何布局代码；
7. 接入方式：页面根第一行加 `<PageWallpaper fixed />`（自带 `z-index: -1`，**不需调整该页任何既有层级**；未传 `heightPx` 时自测）。**全部 11 页已接入**（主包 home / find / mine；分包 detail·dish、profile、auth、notifications、feedback、my-reviews、privacy、privacy·agreement）；分包页固定横条（`AppHeader` / `dish-nav` / `action-bar` / `submit-bar`）保持**实底**。

### 11.2 触摸与边界行为

| 场景 | 行为 |
|---|---|
| 快速猛滑 | 由渲染层原生完成，无逻辑层介入、无额外过渡 |
| 横向拖标签栏 / Banner 手滑 | 不改变滚动位置、不改变任何布局状态 |
| 切换大类 | 仅重新请求刷新列表，**不重置滚动位置** |
| 上拉加载更多 | 新卡追加在列表底部，滚动位置不变 |
| Banner 数据未到 / 失败 | 占位**高度仍按 16:10**，块高不变 |
| 吸顶态 | 容器吸在标题带下沿（**不压标题带**），滚动继续时网格在其下穿行并被切片遮挡 |

### 11.3 图片占位策略（禁止无限空转）

① 菜品图失败 → 灰底 + `dish` 图标（唯一真源 `pages/home/DishCard.vue`）；② Banner 空 / 失败 / 单张失败 → 灰底 + 中性 `empty`，块高仍 16:10；③ 列表请求失败 → `RetryBlock`。
**预览排查**：标签栏只剩「全部」或不出现 → 字典请求失败（查后端 `GET /dishes/meal-types`、开发者工具「不校验合法域名」、是否最新构建产物）；Banner 恒为占位 → 查 `GET /banners` 是否非空、图片 URL 是否可达。

---

## 12. 组件拆分

| 组件 | 文件 | 职责 |
|---|---|---|
| `AppTitleBand` | `components/AppTitleBand.vue` | 固定标题带（跨页统一：主 Tab 页标题 / 二级页返回；恒透明） |
| `PageWallpaper` | `components/PageWallpaper.vue` | **全站**背景层：壁纸 + 纱；页面级 `<PageWallpaper fixed />`，容器内 `absolute` 用法唯一消费方 = 首页吸顶切片（同 `src`） |
| `SearchBar` | `components/SearchBar.vue` | **单颗搜索胶囊**（放大镜 / 输入 / 清除 ✕ / 内嵌「搜索」按钮）—— 首页 `entry` 与搜索页 `input` 共用 |
| `HomeBanner` | `pages/home/HomeBanner.vue` | 16:10 轮播（自持 `GET /banners`；多张自动 + 指示点；空 / 单张失败降级） |
| `HomeMealTabs` | `pages/home/HomeMealTabs.vue` | 大类标签栏（横向滑动 + 单选 + 短下划线；文案顺序全来自字典） |
| `DishCard` | `pages/home/DishCard.vue` | 单张菜品卡（四段排版 + 懒加载图 + 整卡点击） |
| `HomeContent` | `pages/home/HomeContent.vue` | 双列网格（奇偶分列 + 触底提示 + 失败重试块）；**纯展示组件** |
| `TabBar` | `components/TabBar.vue` | 底部导航（首页 / 我的） |
| 通用 | `IconSvg` / `RetryBlock` | 图标（`star-filled` / `arrow` 等）；**图片占位统一由 `ImagePlaceholder` 承载**（`image-broken`/ 失败重试块 |
| 编排 | `utils/useNavMetrics` / `stores/dish.ts` | 顶部度量 / 列表流 · 字典 · 分页 · loading key · 竞态守卫 |

> 页面 `pages/home/index.vue` 只负责：flex 骨架与页面内距、Banner 高度、壁纸层高度下发、数据编排。**滚动 JS 只有一处**：`@scroll` → 离散开关 `pinned`。

---

## 13. 硬性约束（回归检查清单）

1. 永远**双列**网格，拒绝三列；
2. 食堂档口只用**浅灰纯文字**，禁用彩色标签块；
3. 上滑跨过吸顶锁定点后，「搜索区 + 标签栏」**同组吸顶**、不能消失，锁定位置在**固定标题带下沿**（不得压到标题带）；
4. 所有菜品卡片**完整显示**，禁止半截截断；
5. 底部导航只保留**首页、我的**；
6. 布局**不得遮挡**右上角微信胶囊；
7. 内容流**无引导 / 补录类组件**；
8. 顶部顺序固定（标题带 → Banner → 吸顶容器 → 双列网格），不得插入 / 颠倒；吸顶容器是**一个组件**（搜索与筛选不得拆开）；Banner **随上滑在标题带下沿被裁**；
9. **固定标题带**是顶部唯一由端上渲染的文本（位置 / 高度 / 对齐 / 配色跨页一致）；Banner 图上不得出现端上文本；
10. **Banner 比例锁定 16:10**（含最小高度兜底；左右各 12px + `--radius-card` 圆角 + 上距标题带 12px）；定高取宽**必须与 `App.vue` 宽屏限宽 720px 同源**；
11. Banner 是正常流首块：滚出后**不得**在任何横条背后残留图片 / 不得做定格背景；标题带独立固定层；
12. Banner 加载中 / 失败**块高不变**；空 / 失败 / 破图占位一律走公共 **`ImagePlaceholder`**（灰底 `--bg-placeholder` + **图片破损图标 `image-broken`**，Round 31 全站统一 —— SHALL NOT 各页自绘占位、SHALL NOT 再用 `dish` / `empty` 顶替）；
13. 首页**不得存在任何筛选入口**；搜索页无食堂 / 价格筛选胶囊；
14. **搜索区为单颗胶囊**（内嵌「搜索」按钮）：**不得改回「左胶囊 + 右独立按钮」两件结构、不得等分为两块**；
15. 顶部各块间距以 **§7** 为唯一真源，**不得裸值近似**；纵向间距不得在标签行内外各叠一层；
16. 标题带 / TabBar **恒透明**；吸顶容器**未吸顶透明 / 吸顶铺背景图切片**；**不得**用磨砂 / 纯色底 / 蒙版；全站只有**一处** `fixed` 壁纸 + **一层**纱；
17. 吸顶位移**由 `position: sticky` 承担**，**不得**用滚动回调下发 `transform` / `top`（含切片层）；
18. 字号刻度：菜名 32rpx（半粗）＞ 位置行 = 评分 28rpx（均常规）；价格 36rpx（粗体）；正文级不得低于 12px；标签栏光学间距按 §5.2；搜索区控件内距按 §4.1；
19. 色彩**必须语义分档**（§10.1 三条硬规则）；
20. 背景 = 本地壁纸 + 纱（§11.1）：壁纸走 `<image>`、页面级**有且仅有一处**（禁 `vh`）、纱由 `--page-wash` 单点承载；**不得复制第二份壁纸源**；
21. 全站 11 页 SHALL 已接入 `<PageWallpaper fixed />`；新增页面同样在根节点第一行接入。

---

## 14. 不建议做的设计（防回退）

1. **不做彩色标签块**（标签栏保持纯文字 + 短下划线）；
2. **不做多种橙色混用**（按钮 / 价格 / 星级 / 下划线各司其职）；
3. **不做强阴影卡片**（校园工具要轻，不做电商促销卡）；
4. **不做满屏渐变**（全站只有一层统一纱）；
5. **不把菜名压在图片上**（图片是图片，文字是文字）。

---

## 附：接口与字段（UI 精修用）

**数据来源（3 个接口，无第 4 个）**

| 接口 | 用途 | 何时调用 |
|---|---|---|
| `GET /banners` | 顶部 16:10 轮播（`[{ id, imageUrl }]`；已按 `sort_order` 升序、只返回启用项；**无跳转字段**） | `HomeBanner` 自身 `onMounted`（与列表**并行**；失败不阻塞首屏） |
| `GET /dishes/meal-types` | 大类标签栏字典（`[{ value, label, order }]`；首项「为你推荐」；端上只消费 `value` / `label`） | 页面 `onLoad`（`void`，**不 await**）+ `onShow`「从未成功」时兜底重试 |
| `GET /dishes` | 双列网格主数据（`PageResult<DishListItemVO>`，只含在售） | `onLoad` 首拉、切大类、重试、触底加载 |

**字段定义（`DishListItemVO` 8 字段 ↔ 端上 `DishListItem`）**

| # | 接口字段 | 端上字段 | 类型 | 解释 | 消费位置 |
|---|---|---|---|---|---|
| 1 | `id` | `id` | number | 菜品 ID | 列表 `key`（`wf-${id}`）+ 跳详情参数 |
| 2 | `name` | `name` | string | 菜品名称 | `DishCard` 第 2 段（最多 2 行） |
| 3 | `coverImage` | `coverImage` | string | 封面图 URL（无图空串） | `DishCard` 第 1 段（`getThumbImageUrl`）；空 / 失败 → 灰底 + `dish` |
| 4 | `price` | `price` | number | 现价（**接口单位 = 分**，API 层 `fenToYuan`） | `DishCard` 第 4 段右：`¥{formatPrice}` |
| 5 | `originalPrice` | `originalPrice` | number \| null | 原价（分 → 元）；`> price` 视为有折扣 | **本页零消费**（首页不展示划线原价） |
| 6 | `avgRating` | `rating`（别名） | number | 平均评分（缓存值，仅未隐藏评价口径） | `DishCard` 第 4 段左：`star-filled` + `formatRating` |
| 7 | `canteenName` | `canteen`（别名） | string | 食堂名称 | `DishCard` 第 3 段 |
| 8 | `stallName` | `stallName` | string | 档口名称 | 同上 |
| — | `total` | — | number | 符合条件总条数 | **端上零读取**（结束判据 = 本页条数 < `pageSize`） |

> **本页不消费**：`description` / `images` / `floor` / `ratingCount` / `dietType` / `ingredients` / `flavorTags` / `serveTemp` / `mealType` / `status` / `viewCount` / 坐标与距离。

**分页与入参**：`pageSize = 10`｜最多 10 页 = 100 条封顶｜触底提前量 300px｜`GET /dishes` → `mealType`（「为你推荐」不传）/ `page` / `pageSize=10`；**不传** `keyword`（首页无关键词）、**不传任何排序参数**。

**控件类型**：`scroll-view`（`scroll-y` + `scrolltolower` + `lower-threshold=300`）、`swiper` 轮播、`position: sticky` 吸顶容器（吸顶态铺背景图切片）、`fixed` 标题带、`fixed` 壁纸层、`scroll-x` 标签栏。
