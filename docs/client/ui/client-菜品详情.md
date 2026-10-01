# 菜品详情 — 页面 UI 设计稿

> 所属端：学生端（微信小程序）
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。


- 页面：`pages/detail/dish/index`
  - 图片轮播（`images` 多图）：**仅手动滑动**（关闭自动轮播 `autoplay`）；多图显示指示点、单图不显示；无图显示中性占位。
  - 信息卡条目与顺序（自上而下；全卡只允许 1 条分隔线）：
    ① **名称行**（flex 横向、垂直居中）：**菜名**（**最多 2 行截断、末尾省略**；卡片内最大字号）→ **「信息有误?」入口**（小图标 + 小号文字，**视觉权重压低**，位于价格左侧；**图标与文字同色 = `--text-tertiary`（暖褐灰 #7F6A55）**）→ **价格**（`.price-text` 现价 + `originalPrice` 原价划线）；
    ② **评分 + 位置**（同一行、左右排布）：左 = 有评分则「黄星 + 均分」（`ratingCount > 0`）、零评价则隐藏星并示浅灰「**暂无评分**」；右 = 定位小图标 + 「食堂 · 楼层 · 档口」；
    ③ **菜品简介**（`description` **固定最多 2 行截断、超出省略**，文本独占卡片整宽；**为空字符串则整块隐藏、不占用页面空间**）；
    ④ **唯一一条浅灰分隔线**（仅渲染在简介与四维之间；简介隐藏时这条线一并消失）；
    ⑤ **描述四维**（无底色 / 无边框 / 无内边距，4 列水平等分居中，**每列「值在上（主字号）、标签在下（浅灰小字）」**）。
    - **模块垂直间距 `--spacing-md`；简介与分隔线间距 `--spacing-sm`**；除第 ④ 条外**禁止任何分割线**（模块靠垂直留白区分）；四维块自持 `--spacing-md` 上间距 ⇒ 简介缺失（分隔线同步消失）时留白仍成立。
    - 卡内可点件仅「**信息有误?**」（**文字 + 小图标形态，禁实色填充按钮**）；卡片内不做分享按钮（复用微信原生右上角分享）。
    - **「信息有误?」= 全页唯一纠错入口** —— 点击跳**独立菜品纠错页**并携带当前菜品（落点唯一构造函数 `correctionUrl(dishId)`，禁止手拼 URL，见 [`client-菜品纠错.md`](./client-菜品纠错.md)）；`POST /dishes/{id}/correction` 属**公开写** ⇒ **免认证**，游客可直达（不经 `requireAuth`）。
    - **不显示**：评价人数（`ratingCount` 正常请求、端上丢弃不展示）、评分进度条 / 分布条、标签 chips、「信息更新于 X」、窗口号、距离。
  - 四维指标（**描述四维**）渲染口径：**逐维渲染，缺项不占位**——某维无值时不渲染该列（**不得**出现 `-` 空列凑满四列）；维度名与数据必须一致（「荤素」=`dietType`、「主料」=`ingredients`、「口味」=`flavorTags`、「冷热」=`serveTemp`）。**容器呈现**：**无底色 / 无边框 / 无内边距**，4 列水平等分居中，每列「**值在上、标签在下**」，标签贴底对齐（长值换行时各列标签仍在同一基线）；SHALL NOT 用输入框样式粗边框、SHALL NOT 用底色块包裹。
    - **取值形态（R4）**：四维**下发机器值**（`dietType` / `serveTemp` 为单值，`ingredients` / `flavorTags` 为**数组**），**展示中文由后端字典端点下发**（模式对齐 `GET /dishes/views`）——端上 SHALL NOT 硬编码「机器值 → 中文」映射表，亦 SHALL NOT 由后端直出中文（**web 端同样消费四维且需要表单选项**，直出中文只能解决一端、且会与视图字典的模式形成两套并列）。多值以 `、` 连接后单行展示，超长按既有 `-webkit-line-clamp: 2` 换行收起。依据 PR-12（枚举展示须与后端常量表同源 + **全端盘点**）。详见功能文档 §6 R4。
  - 价格口径：**展示唯一数据源 = `price`（常显现价）**；`originalPrice` 有值时并列**原价划线**；**不以任何第三个字段判折扣**（禁止双源写法——会导致不同步行展示过期价、折扣丢失），判据恒为 `originalPrice > price`。
  - 不展示独立「折扣价」——是否有折扣由「原价 × 现价」两态表达，无需第三个价格字段。
  - 评分口径：**均分与「有无评分」判定（`ratingCount`）MUST 同源同刻** —— 两者统一取**实时聚合**（一次查询同刻算出）。
    - **依据**：`ratingDistribution` 是实时聚合（`selectRatingDistribution`），缓存列会漂移 → 混用必然出现口径不一致（违反 PR-02 单一真源 / PR-08 聚合口径）。
    - **例外**：均分若因性能确需取缓存，须**显式登记为有意权衡**（**当前无此登记 → 默认实时**）。
    - 评分分布条（含迷你条）与「N 人评」文案不渲染 —— 按用户规格「**不绘制评分进度条、不展示评价人数**」。
  - 评价区：
    - **标题行**：**左 = 「评价 + 数字」合并为一个标题块** —— 数字与标题**同色**（SHALL NOT 用 `--text-tertiary` 灰字：灰字会把总数读成附属信息）、字号小半号（h2 40rpx → h3 36rpx）、等宽数字；**不用「评价（N）」括号式**；在途 / 失败态**不渲染数字**。
      **右 = 「写评价」轻量入口（主色线性笔形图标 `edit` + 主色文字，非按钮形态）** —— **无边框 / 无底色 / 无阴影 / 无胶囊槽**（视觉权重低于任何按钮，只表达「点击可写评价」）；**随评价卡片一同滚动（不吸顶、不固定）**；**文案恒为「写评价」—— 无「重新评价」双态**。
    - 评价条目（`ReviewItem` 的 `flat` 形态）：① 头像 + 昵称（600 档）+ 右上角 ⋮ 更多（ActionSheet：举报 / 删除）；② 星级（黄色实心）+ 分值 + **日期（仅 `YYYY-MM-DD`，不含时分 —— 菜品评价时效性弱）**同行；③ 正文；④ 配图 ≤3 张（有图才渲染）。**条目之间为 1rpx `--border-color` 分隔线（`--spacing-md` 行内距），SHALL NOT 加独立卡片边框**。
    - 排序：**时间倒序（新评价在前），唯一排序，无切换**
  - 状态呈现（强制，遵守既有红线）：① **加载中不呈现骨架屏 / loading 指示**（仅限「页面级首屏」；**用户主动点击「重新加载」**时可在公共 `RetryBlock` 内给**转圈**在途反馈）——数据未返回时内容区空白静默；② **菜品不存在 / 详情拉取失败**须给出明确文案与恢复路径（**不得只留纯空白页**）；③ 评价区失败态沿用既有**可重试块**。
  - 交互与无障碍（本模块强制）：可点元素触控目标 **≥ 88rpx（44pt）**；「写评价」入口与「信息有误?」入口均带 `role="button"` + `aria-label`（命中区经 `::after` **仅纵向**扩至 88rpx，视觉尺寸不变）；轮播容器与评价头像带 `aria-label`；`prefers-reduced-motion` 下动效降级为直接显示。
- **页面底部无任何常驻操作栏** —— 滚动区底部 **取消 `calc(var(--action-bar-height) + env(safe-area-inset-bottom))` 预留**，回归常规底部留白。
- 「**写评价**」入口（**唯一落点 = 评价标题行右侧**；文案**恒为「写评价」**，无「重新评价」双态）—— 点击**直接打开空表单弹层**。
- **无任何写前判定**：弹层打开前**不调** `GET /my/reviews?dishId=`、**不预填本人旧评价**、**不显示「覆盖原评价」提示**；详情页**零用户态请求**。
- **提交口径**：恒 `POST /dishes/{id}/reviews` —— **同一用户对同一菜品的重复提交由服务端覆盖旧评价**（端上不区分首评 / 重评）。
- 未认证点「写评价」→ 跳转身份认证页 `pages/auth/index`（认证成功返回本页后由 onShow 续接，自动重新打开写评价表单）。
- **纠错入口**：「**信息有误?**」（信息卡名称行，见上「信息卡条目与顺序」）—— 全页唯一；写评价弹层内**不含**纠错入口。
- **分享**：由**微信右上角原生菜单**承担（`onShareAppMessage` 保留：分享菜名 + 现价 + 本页路径），分享能力不弱化。

---

## 接口数据字段（UI 精修用）

**页面**：`pages/detail/dish/index`（分包 `pages/detail/`；二级页，**无 TabBar**）+ 本页私有弹层 `ReviewComposer` / `ReportModal`

### 组件清单（本界面需要哪些组件）

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `ImageSwiper` | 页内私有 `pages/detail/dish/ImageSwiper.vue` | 顶部大图轮播（`autoplay=false` 仅手动滑动；多图显示指示点、单图不显示；无图 / 破图 → 统一占位 `ImagePlaceholder`（灰底 + `image-broken`），块高不变） |
| 2 | `DishInfoCard` | 页内私有 | 信息卡：① **名称行**（菜名 ≤2 行 + **「信息有误?」入口** + 价格）→ ② 评分 / 位置同行 → ③ 简介（**固定 2 行截断、无展开**）→ ④ **唯一分隔线**（简介存在时）→ ⑤ 描述四维（无底色 4 列等分居中、值在上 / 标签在下） |
| 3 | `DishReviewSection` | 页内私有 | 评价卡：`SectionTitle`「评价」+ 数字（经 `count`）+ **「写评价」轻量入口**（图标 + 文字，标题行右侧）+ 评价条目列表 / 空态（**纯文本「暂无评价」**）/ 失败态 |
| 4 | `ReviewComposer` | 页内私有 | 写评价底部弹层（字段与呈现见本文件「页内承载物 · 一、写评价」）—— **只承载评价表单，不含纠错入口**（纠错在信息卡名称行「信息有误?」） |
| 5 | `ReportModal` | 页内私有 | 举报评价底部弹层（见本文件「页内承载物 · 三、举报评价」） |
| 6 | `CardSection` | 公共 `components/CardSection.vue` | **两张**卡外壳（信息 / 评价） |
| 7 | `SectionTitle` | 公共 `components/SectionTitle.vue` | 「评价」标题 + 可选 **`count`**（标题右侧同色 / 小半号 / 等宽数字）；`#extra` 槽承载右侧可点件（本页「**写评价**」轻量入口、find 页「清空」） |
| 8 | `ReviewItem` | 公共 `components/ReviewItem.vue` | 单条评价卡（头像 / 昵称 / 星级 / 时间 / 正文 / 配图 / 三点） |
| 9 | `ActionSheet` | 公共 `components/ActionSheet.vue` | 评价三点菜单：本人「删除评价」/ 他人「举报评价」（危险红） |
| 10 | `RetryBlock` | 公共 `components/RetryBlock.vue` | 评价首屏 / 刷新失败「加载失败 · 点击重试」 |
| 11 | `ImagePicker` | 公共（经 `ReviewComposer`） | 评价配图 ≤3 张 |
| 12 | `IconSvg` | 公共 `components/IconSvg.vue` | 导航返回（**文字「返回」**） / 定位 `location` / 星（`star` 线性 · `star-filled`，评分行与评价卡用）/ 三点 `more-v` / **「信息有误?」前导小图标**（线性；与文字同色 `--text-tertiary`）/ 空态与失败示意 `empty` · `report` |
| 13 | 顶部改用公共 **`AppTitleBand`**（透明；左「返回」+ 居中**菜名随滚动淡入**）、**hero 卡**（滚动区首块）（**页面已无底部操作栏**，`pages/detail/dish/index.vue` 内联） | 公共 + `pages/detail/dish/index.vue` 内联 | 与首页 §11 **同构**：标题带**恒透明**、滚动区从带下沿开始 ⇒ 无内容从带背后经过（**零切片 / 零实底切换 / 零承接条**）；hero **随滚动 1:1 上移、在标题带下沿被裁**（"移出屏幕"，与首页 Banner 同语言）；滚动 JS **只保留菜名淡入 1 项** |
| — | `swiper`（`ImageSwiper` 内）/ `textarea` / `open-type="share"` button | uni 内置控件 | 图片轮播 / 评价输入 / 分享 |

### 有哪些数据要显示、显示在哪个组件

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `id` | `GET /dishes/{id}`（`DishDetailVO`） | 菜品 ID | 无界面（页面 `key` / 请求路径 / 分享路径） | 零可见 UI |
| 2 | `name` | 同上 | 菜名 | ① `DishInfoCard` 名称行 ② `AppTitleBand` 居中标题（滚动后渐显） ③ `ReviewComposer` 副标题 ④ 分享标题 | 名称行**最多 2 行截断**；导航标题按滚动量淡入 |
| 3 | `price` | 同上（元） | 现价 | `DishInfoCard` 价格行 `.price-text` | **唯一价格数据源**（主色）；不以第三字段判折扣 |
| 4 | `originalPrice` | 同上 | 原价 | `DishInfoCard` 价格行 `.origin-price` | 仅 `originalPrice > price` 渲染，三级灰 + 删除线 |
| 5 | `images` | 同上 | 菜品图集 | `ImageSwiper`（页面派生 `heroImages`） | `aspectFill`，加载完成淡入；空数组 → 占位 |
| 6 | `description` | 同上 | 菜品描述 | `DishInfoCard` 描述行 `.desc-content`（**无展开 / 收起入口**） | **固定最多 2 行截断**（超出省略）；文本独占卡片整宽 |
| 7 | `canteenName` / `floor` / `stallName` | 同上（端上别名 `canteen`） | 食堂 / 楼层 / 档口 | `DishInfoCard` 位置行 `.loc-text`（页面派生 `locationText`） | 「食堂 · 楼层 · 档口」；缺项兜底「未知位置」（**本行右侧不挂任何入口**；纠错入口唯一落点 = 上方**名称行「信息有误?」**） |
| 8 | `dietType` / `ingredients` / `flavorTags` / `serveTemp` | 同上（**机器值**：单值 / 数组 / 数组 / 单值） | 荤素 / 主料 / 口味 / 冷热 | `DishInfoCard` 四维区 `.dim-col`（`dim-val` + `dim-label`） | **逐维渲染、缺项不占位**；多值以 `、` 连接，长值 2 行收起 |
| 10 | `avgRating` / `ratingCount` | 同上（端上别名 `rating`） | 均分 / 评价人数 | `DishInfoCard` 第二行左侧（`.rating-group` / `.rating-empty`） | 均分 1 位小数（`utils/dish.formatRating`）；`ratingCount = 0` → 隐藏星、示浅灰「暂无评分」（不靠字段缺失判断）；**评价人数不渲染** |
| 11 | `ratingDistribution[].star` / `.count` | 同上 | 星级 / 该星级条数 | **不渲染**（用户规格「不绘制评分进度条」） | 字段保留在接口契约中，端上不消费 |
| 12 | `records[].id` | `GET /dishes/{id}/reviews`（`PageResult<ReviewVO>`） | 评价 ID | `ReviewItem` 列表 `key` / 删除与举报目标 | 零可见 UI |
| 13 | `records[].userAvatar` | 同上 | 评价者头像 | `ReviewItem` 头像位 | 空 / 破图 → `IconSvg name="user"` 灰底 |
| 14 | `records[].userNickname` | 同上 | 评价者昵称 | `ReviewItem` 昵称行 | 空 → 「匿名用户」（注销账号显示「已注销用户」） |
| 15 | `records[].rating` | 同上 | 评分（1~5） | `ReviewItem` meta 行（实心黄星 + 数值） | 最低渲染 1 颗星 |
| 16 | `records[].createdAt` | 同上 | 发表时间 | `ReviewItem` meta 行 `.review-time` | **`formatDate`（仅 `YYYY-MM-DD`）**；重评后取新时间（自然置顶） |
| 17 | `records[].content` | 同上 | 评价正文 | `ReviewItem` 正文 `.review-content` | 二级灰、`pre-wrap` |
| 18 | `records[].images` | 同上 | 评价配图（≤3） | `ReviewItem` 配图网格 | 3 等分小方图，点击预览；破图 → 统一占位 `ImagePlaceholder` |
| 19 | `records[].userId` | 同上 | 评价者用户 ID | `ActionSheet` 动作项显隐（与 `userInfo.id` 比对：本人「删除评价」/ 他人「举报评价」） | 服务端另有「非本人 → 403」兜底 |
| 20 | ~~`total`~~ → **`records[].length` 派生** | 同上 | 已加载评价条数 | `DishReviewSection` 标题块内 `.section-count`（经 `SectionTitle` 的 `count`，页面传 `:count="reviewList.length"`） | **恒为纯数字**（与标题同色、小半号、等宽；**SHALL NOT 用灰字**）；**无「有图 N」变体**；在途期与失败态不渲染。分页壳只下发 `records`（`total` 不返回）；标题数字取**已加载条数**（翻页后随加载增长，非服务端总数）。 |
| 21 | `records`（**分页壳唯一字段**） | 分页壳 | 当前页数据行 | 触底加载结束判据：**本页返回条数 < 请求的 `pageSize`**（`page` / `pageSize` / `total` 服务端**均不回传**） | 整页条数时需再取一页才判到底（见 `usePagedList` 空页处理：空页不触碰列表、不闪空态） |
| 23 | 加载 / 失败 / 不存在态 | 端上 `detailError` / `detailNotFound`（`4001` / 缺 id） | — | `.detail-fail` 块 | 不存在 → 「这道菜已不在了」+「它可能已被下架或移除」+ **仅「返回」**；网络失败 → 「这道菜暂时打不开」+「重新加载」+「返回」；加载中**静默空白**（无骨架屏） |
| 24 | 评价三态 | 端上 `reviewPending` / `reviewFailed` / 列表长度 | 在途 / 失败 / 空 | `DishReviewSection` | **头部（标题 + 数字 + 「写评价」入口）恒渲染**；在途 **列表区整块不渲染**（不误闪空态，数字亦不渲染）；失败 → `RetryBlock`；零评价 → **纯文本「暂无评价」**（**无副文案、无引导按钮**） |

**入参提交**
| 接口 | 字段 |
|---|---|
| `GET /dishes/{id}/reviews` | `page` / `pageSize=10` |
| `POST /dishes/{id}/reviews` | `rating` / `content` / `images`（≤3；详见本文件「页内承载物 · 一、写评价」）；**重复提交由服务端覆盖旧评价** |
| `POST /reviews/{id}/report`（举报） | `reason`（必选）/ `content`（可空）/ `images`（≤3；详见本文件「页内承载物 · 三、举报评价」） |
| `DELETE /reviews/{id}` | 无请求体（详见本文件「页内承载物 · 二、删除本人评价」） |

**错误码**：`4001` 菜品不存在 / 已下架（专属文案 + 仅返回，**不可重试**）｜网络 / `5xx`（可重试：重新加载 + 返回）｜`400` 评价参数或安检失败｜`403` 非本人｜`4031` 邮箱未认证（跳身份认证页 `pages/auth/index`，返回后由 onShow 续接原动作）
**控件类型**：页面级滚动（顶部 `AppTitleBand` + 滚动区 `flex: 1`，与首页 §11 同构）、**hero 卡随滚动移出（无 sticky 定格、无承接条）**、`onReachBottom` 触底分页、`onPageScroll`（**仅**驱动居中菜名淡入）、`BaseSheet` 底部抽屉 ×2、`ActionSheet` 三点菜单、`textarea`、星级单选、**评价标题行「写评价」轻量入口**（图标 + 文字，`role="button"`）、**信息卡名称行「信息有误?」文字入口**（`role="button"`）（**页面底部无操作栏**；分享由微信原生菜单承担，**无** `open-type="share"` 按钮）

---

## 页内承载物（写评价 · 删除本人评价 · 举报评价）

> **无独立页面** —— 均落在本页（`pages/detail/dish/index`）内的三个交互承载物：**写评价**（底部弹层 `ReviewComposer`）、**删除本人评价**（评价卡三点菜单 + `ActionSheet` + 二次确认）、**举报评价**（底部弹层 `ReportModal`）。

### 一、写评价（`ReviewComposer` 底部弹层）

入口 = **评价标题行右侧「写评价」轻量入口**（主色线性笔形图标 + 主色文字，**非按钮形态**；唯一落点；文案恒定，**无「重新评价」态**）。

- **无写前判定**：点击入口**直接打开空表单** —— 不调 `GET /my/reviews?dishId=`、**不预填旧值**、**不显示覆盖提示**；**重复提交由服务端覆盖旧评价**。
- 弹层：`ReviewComposer` 底部弹层（统一下拉关闭手势，阈值约 120px），内容为：星级选择 + 文字输入框 + 配图选择器（`ImagePicker`，自动压缩 ≤1MB、≤750×1334）。
- 未认证用户点入口 → 跳转身份认证页 `pages/auth/index`（入口不置灰；认证成功返回本页后由 onShow 续接，自动重新打开写评价表单）。

#### 组件清单

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `ReviewComposer` | 页内私有 `pages/detail/dish/ReviewComposer.vue` | 弹层本体：菜名 + 星级 + 正文 + 配图 + 提交（承载表单语义） |
| 2 | `BaseSheet` | 公共 `components/BaseSheet.vue` | 弹层骨架：遮罩 / grabber / 下滑关闭 / 安全区 / 标题「写评价」/ 右上关闭钮 / `scroll-body`（小屏内部滚动） |
| 3 | `ImagePicker` | 公共 `components/ImagePicker.vue` | 配图选择：≤3 张、压缩 ≤1MB 且 ≤750×1334、安检上传后回传 COS URL；提交中禁选 |
| 4 | `IconSvg` | 公共 `components/IconSvg.vue` | 星级图标：未选 `star`（线性浅灰）/ 已选 `star-filled`（实心黄） |
| 5 | `textarea`（`maxlength=500` + `n/500` 计数） | uni 内置控件 | 正文录入（选填，`auto-height`，上限 320rpx 后由弹层滚动承接） |
| 6 | 提交钮（页内 `view`） | `ReviewComposer.vue` 内联 | 主色实底；未选星 / 提交中禁用（文案「提交中…」） |

#### 数据映射

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `dishName`（宿主页 prop） | `DishDetailVO.name` | 菜名 | `ReviewComposer` 顶部 `.rc-dish` | 次级灰小字，单行省略 |
| 6 | `rating`（本地表单态） | 用户点选（1~5 星） | 评分 | 星级行 + 提示 `.rc-star-tip` | 未选时「点击星星打分（必填）」；已选「已评 N 星」 |
| 7 | `content`（本地表单态） | 用户输入 | 正文（≤500） | textarea + 右下 `.rc-count` | 实时 `n/500` 计数 |
| 8 | `images`（本地表单态） | `ImagePicker` v-model | 配图（≤3） | `ImagePicker` 网格 | 上传中 loading / 满额 `3/3` 计数格 |
| 9 | `submitting`（本地态） | 提交在途 | 提交状态 | 提交钮文案与禁用态 + `ImagePicker` 禁用 | 「提交中…」/ 半透明 |
| 10 | 提交结果 `{ id }` | `POST /dishes/{id}/reviews`（**重复提交即覆盖旧评价**） | 评价 ID | 无界面（随 `submitted` 事件上抛，宿主页据此刷新评价列表） | 端上不区分首评 / 重评 |
| 11 | 失败提示 | `400` / `4031` 响应 `message` | 未通过原因 | 无界面（Toast 直透，兜底「发布失败，请稍后重试」） | — |

**入参提交**：`POST /dishes/{id}/reviews`（路径 `{id}` = 菜品 ID，body 不收 `dishId`；**重复提交 = 覆盖本人对该菜的旧评价**）→ `rating`（必填 1~5）/ `content`（选填 ≤500）/ `images`（选填 ≤3，COS 绝对地址）；配图**前置**经 `POST /upload/cloud-image`（入参 `fileId` → 出参 `url`）

**错误码**

| code | 含义 | 端上处置 |
|---|---|---|
| 400 | 评分越界 / 正文或配图超限 / 文本或图片内容安检违规 | Toast 直透 `message`，弹层不关闭 |
| **4031** | 邮箱未认证 | 跳身份认证页 `pages/auth/index`（认证成功返回后由宿主页 onShow 续接并重开弹层） |

**控件类型**：`BaseSheet` 底部弹层（统一下拉关闭手势，阈值 ≈120px；`prefers-reduced-motion` 降级）、星级单选、`textarea`、图片选择网格

---

### 二、删除本人评价（三点菜单 + `ActionSheet` + 二次确认）

**两处宿主入口**（同一链路，共用公共组件）：

1. 菜品详情页：**每条评价卡右上角常驻竖三点**（更多操作，触控目标 ≥ 88rpx）→ 点击**底部弹出动作菜单**（`ActionSheet`，页面级通用组件）→ 本人的评价显示「删除评价」动作项（**危险红色态**：图标与文字同为错误色；他人的评价同位置为「举报评价」）→ 点击后进入二次确认。
2. 「我的主页」（[`client-我的主页.md`](./client-我的主页.md)）评价区：评价卡右上角三点 → 「删除评价」。

删除前有**二次确认**弹窗（不可恢复，故必须确认）：确认按钮取危险色实值（`MODAL_CONFIRM_DANGER_COLOR`），文案「确定删除这条评价吗？删除后不可恢复。」

#### 组件清单

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `ReviewItem` | 公共 `components/ReviewItem.vue` | 评价卡本体 + **右上角常驻竖三点**（`IconSvg name="more-v"`，触控目标 ≥88rpx）→ `@more` 上抛被点评价 |
| 2 | `ActionSheet` | 公共 `components/ActionSheet.vue`（骨架 = `BaseSheet`） | 底部动作菜单，「删除评价」动作项（**危险红**：`icon` + `text` 同取错误色） |
| 3 | `uni.showModal` | uni 内置控件 | 二次确认弹窗：标题「删除评价」/ 正文「确定删除这条评价吗？删除后不可恢复。」/ 确认钮取危险色实值 |
| 4 | 列表容器（`.review-list` / `.list`） | 宿主页内联 | 删除成功后**本地移除该条**（`list.filter`），不整页重拉 |
| 5 | `RetryBlock`（仅「我的主页」） | 公共 | 删除后 / 首屏刷新失败的「加载失败 · 点击重试」兜底 |

> **无专属页面与专属弹层组件**：全部落在公共 `ReviewItem` + 公共 `ActionSheet` 上，两个宿主页共用同一链路。

#### 数据映射

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `id` | `ReviewVO.id`（详情页）/ `MyReviewVO.id`（我的主页） | 评价 ID | 无界面（作 `DELETE /reviews/{id}` 路径参数） | 零可见 UI |
| 2 | `userId` | 同上 | 评价者用户 ID | `ReviewItem` 的 `current-user-id` 比对 → **决定三点菜单给谁** | 本人 → 「删除评价」；他人 → 「举报评价」 |
| 3 | 当前用户 `id` | `useUserStore().userInfo.id` | 本人身份 | 同上（页面侧传入 `:current-user-id`） | 零可见 UI |
| 4 | 删除结果 | `DELETE /reviews/{id}` 成功（`data` = null） | 成功 | 宿主评价列表 | 该卡片就地消失 + Toast「评价已删除」；删空后区块标题下线（我的主页） |
| 5 | 失败提示 | `400` / `403` / `4031` / `401` 响应 `message` | 失败原因 | 无界面（Toast 直透，兜底「删除失败」） | `4031` 需先跳身份认证页 |

**出参消费**：无（`Result<Void>`，成功即 `code=200`）
**入参提交**：`DELETE /reviews/{id}` → **无请求体、无参数**（归属由服务端按 token 判定，端上**不传 userId**）

**错误码**

| code | 含义 | 端上处置 |
|---|---|---|
| 400 | 评价不存在 | Toast 提示，本地不移除 |
| 403 | 只能删除自己的评价 | Toast 提示（正常不可达：入口已按 `userId` 收口） |
| **4031** | 邮箱未完成认证 | 跳身份认证页 `pages/auth/index` |
| 401 | 未登录 | 请求层静默重登并重试一次 |

**控件类型**：`ActionSheet` 底部动作菜单（整行热区，危险红动作项）+ `uni.showModal` 二次确认（破坏性操作必备）

---

### 三、举报评价（`ReportModal` 底部弹层）

入口 = 菜品详情页评价卡片右上角**三点菜单** → 「举报评价」（危险红色态）。

- 弹层：`ReportModal` **底部弹层**（BaseSheet 统一骨架：遮罩 / grabber / 下滑关闭手势 / 安全区），内容自上而下：
  1. 处理承诺行：「请选择举报原因，举报将在 48 小时内处理」（次级浅灰小字）；
  2. **原因单选列表**：选项来自后端字典 `GET /report-reasons`（弹层打开时实时拉取，端上零硬编码；展示顺序 = 后端下发次序）；每行整行热区（浅底圆角），**单选**——选中行主色文字加粗 + 主色浅底；**无文本输入框**；
  3. 提交钮：主色实底「提交举报」，未选中原因 / 提交中禁用（半透明）。
- 字典加载失败：列表区显示「举报原因加载失败，请关闭后重试」（次级浅灰，重开弹层重拉）。
- 提交成功 → Toast「举报已提交」并关闭弹层。

#### 组件清单

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `ReportModal` | 页内私有 `pages/detail/dish/ReportModal.vue` | 弹层本体：处理承诺行 + 原因单选列表 + 提交钮（含字典拉取与选中态） |
| 2 | `BaseSheet` | 公共 `components/BaseSheet.vue` | 弹层骨架：遮罩 / grabber / 下滑关闭手势 / 安全区 / 标题「举报评价」/ 右上关闭钮 |
| 3 | 提交钮（页内 `view`） | `ReportModal.vue` 内联 | 主色实底「提交举报」，未选中原因 / 提交中禁用（半透明） |
| 4 | `ReviewItem`（宿主侧） | 公共 `components/ReviewItem.vue` | 入口所在：评价卡右上角常驻三点（`@more`） |
| 5 | `ActionSheet`（宿主侧） | 公共 `components/ActionSheet.vue` | 三点动作菜单，「举报评价」动作项（危险红：图标 + 文字） |
| 6 | `useReport.ts`（宿主侧编排） | 页内私有 | `openReport(reviewId)` / `submitReport(reasonValue)` 与提交中状态 |

#### 数据映射

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `label` | `GET /report-reasons` | 原因中文标签 | `ReportModal` 原因单选行 `.rp-option-text` | 每行一个原因，整行热区、单选；选中 = 主色文字加粗 + 浅主色底 |
| 2 | `value` | 同上 | 原因机器值 | `ReportModal` 选中态判定 + 提交入参 `reason` | **端上零硬编码**（不落屏展示） |
| 3 | `order` | 同上 | 展示顺序 | `ReportModal` 列表顺序 | 按后端返回顺序渲染，**端上不排序** |
| 4 | 被举报评价 `id` | `ReviewVO.id`（宿主页经 `openReport(rv.id)` 注入） | 被举报评价 ID | 无界面（提交时作路径参数 `{id}`） | 零可见 UI |
| 5 | 字典加载态 | 端上 `reasons` / `reasonsFailed` | 加载中 / 失败 | `ReportModal` `.rp-empty` | 「加载中…」/ 失败「举报原因加载失败，请关闭后重试」（重开弹层重拉） |
| 6 | 承诺行 | 端内静态文案 | 处理预期 | `ReportModal` `.rp-note` | 「请选择举报原因，举报将在 48 小时内处理」（次级灰小字） |
| 7 | 提交中态 | 端上 `submitting` | 提交在途 | 提交钮文案与禁用态 | 「提交中…」+ 半透明，防重复提交 |

**出参消费**：`GET /report-reasons` → `value` / `label`（两项全消费，见上表；顺序由服务端下发次序表达，端上不读序号字段）
**入参提交**：`POST /reviews/{id}/report` → 路径 `{id}` = 被举报评价 ID；body `reason`（选中原因 `value`，必选）/ `content`（可空）/ `images`（可空 ≤3）
**错误码**：`400` 举报原因缺失 / 非法 / 重复举报 / 超频 / 补充文本安检违规｜**`4001`** 被举报评价不存在（含已隐藏 / 已删除）
**控件类型**：`BaseSheet` 底部弹层（遮罩 / grabber / 下滑关闭 / 安全区）、单选项行（`role="radio"`，整行热区）、禁用态提交钮


