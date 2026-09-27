# 菜品详情 — 页面 UI 设计稿

> 所属端：学生端（微信小程序）
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。


- 页面：`pages/detail/dish/index`
  - 图片轮播（`images` 多图）：**仅手动滑动**（关闭自动轮播 `autoplay`）；多图显示指示点、单图不显示；无图显示中性占位。
  - 信息卡条目与顺序（自上而下，**UI 统一 Loop Round 22 重排 + Round 23 属性块还原；全卡只允许 1 条分隔线**）：
    ① **名称 + 价格组**（同一行、左右排布；菜名 = 卡片内最大字号）；
    ② **评分 + 位置**（同一行、左右排布）：左 = 有评分则「黄星 + 均分」（`ratingCount > 0`）、零评价则隐藏星并示浅灰「**暂无评分**」；右 = 定位小图标 + 「食堂 · 楼层 · 档口」；
    ③ **菜品简介**（`description` 默认最多 2 行，右下角「展开 / 收起」；**为空字符串则整块隐藏、不占用页面空间**）；
    ④ **唯一一条浅灰分隔线**（仅渲染在简介与四维之间；简介隐藏时这条线一并消失）；
    ⑤ **描述四维**（**Round 23 还原 Round 22 之前的样式**：无底色 / 无边框 / 无内边距，4 列水平等分居中，**每列「值在上（主字号）、标签在下（浅灰小字）」**）。
    - **模块垂直间距 `--spacing-md`；简介与分隔线间距 `--spacing-sm`**；除第 ④ 条外**禁止任何分割线**（模块靠垂直留白区分）；四维块自持 `--spacing-md` 上间距 ⇒ 简介缺失（分隔线同步消失）时留白仍成立。
    - 交互文字（本卡仅「展开 / 收起」）**只用文字配色，禁止实色填充按钮**（**权威条款即本稿**）；卡片内不做分享按钮（复用微信原生右上角分享）。
    - **卡片内不再有任何纠错入口**（R23 移出本卡）—— 全页唯一纠错落点为**底栏「反馈错误」按钮**（Round 25，见下「底栏」）。
    - **不显示**：评价人数（`ratingCount` 正常请求、端上丢弃不展示）、评分进度条 / 分布条、标签 chips、「信息更新于 X」、窗口号、距离。
  - 四维指标（**描述四维**）渲染口径：**逐维渲染，缺项不占位**——某维无值时不渲染该列（**不得**出现 `-` 空列凑满四列）；维度名与数据必须一致（「荤素」=`dietType`、「主料」=`ingredients`、「口味」=`flavorTags`、「冷热」=`serveTemp`）。**容器呈现（Round 23 还原 Round 22 之前的样式）**：**无底色 / 无边框 / 无内边距**，4 列水平等分居中，每列「**值在上、标签在下**」，标签贴底对齐（长值换行时各列标签仍在同一基线）；SHALL NOT 用输入框样式粗边框、SHALL NOT 用底色块包裹（「浅米色标签容器」方案已退役）。
    - **取值形态（R4）**：四维**下发机器值**（`dietType` / `serveTemp` 为单值，`ingredients` / `flavorTags` 为**数组**），**展示中文由后端字典端点下发**（模式对齐 `GET /dishes/meal-types`）——端上 SHALL NOT 硬编码「机器值 → 中文」映射表，亦 SHALL NOT 由后端直出中文（**web 端同样消费四维且需要表单选项**，直出中文只能解决一端、且会与 `mealType` 的字典模式形成两套并列）。多值以 `、` 连接后单行展示，超长按既有 `-webkit-line-clamp: 2` 换行收起。依据 PR-12（枚举展示须与后端常量表同源 + **全端盘点**）。详见功能文档 §6 R4 与 `project_spec.md` §7.40 第 4 项。
  - 价格口径：**展示唯一数据源 = `price`（常显现价）**；`originalPrice` 有值时并列**原价划线**；**不以任何第三个字段判折扣**（禁止双源写法——会导致不同步行展示过期价、折扣丢失），判据恒为 `originalPrice > price`（见 `project_spec.md` §7.26）。
  - 不展示独立「折扣价」——是否有折扣由「原价 × 现价」两态表达，无需第三个价格字段。
  - 评分口径（**Round 22 收敛后仍不放松**）：**均分与「有无评分」判定（`ratingCount`）MUST 同源同刻** —— 两者统一取**实时聚合**（一次查询同刻算出）。
    - **依据**：`ratingDistribution` 是实时聚合（`selectRatingDistribution`），缓存列会漂移 → 混用必然出现口径不一致（违反 PR-02 单一真源 / PR-08 聚合口径）。
    - **例外**：均分若因性能确需取缓存，须在 `project_spec.md` **显式登记为有意权衡**（**当前无此登记 → 默认实时**）。
    - **Round 22 已移除**：评分分布条（含 Round 19 的迷你条）与「N 人评」文案 —— 按用户规格「**不绘制评分进度条、不展示评价人数**」。
  - 评价区：
    - **标题行（Round 24 重排）**：**左 = 「评价 + 总数」合并为一个标题块** —— 数字与标题**同色**（SHALL NOT 用 `--text-tertiary` 灰字：灰字会把总数读成附属信息）、字号小半号（h2 40rpx → h3 36rpx）、等宽数字；**不用「评价（N）」括号式**；数字口径**恒为 total**（不随筛选变形，否则总数读作跳变）；在途 / 失败态**不渲染数字**。
      **右 = 两段式筛选胶囊「全部 / 有图」**（**替换原自定义 switch**）：槽为浅灰圆角胶囊（`--bg-soft`），选中项**白底 + 深色文字 + 极轻阴影**（在槽内浮起）、未选中项透明底 + 次级灰文字；默认「全部」。**语义依据**：「只看有图」是**视图过滤**（看完即切回），不是常驻偏好设置 ⇒ tab 语义比 switch 准确。
    - 评价条目（`ReviewItem` 的 `flat` 形态）：① 头像 + 昵称（600 档）+ 右上角 ⋮ 更多（ActionSheet：举报 / 删除）；② 星级（黄色实心）+ 分值 + **日期（仅 `YYYY-MM-DD`，不含时分 —— 菜品评价时效性弱）**同行；③ 正文；④ 配图 ≤3 张（有图才渲染）。**条目之间为纯留白 `--spacing-lg`，SHALL NOT 画分割线、SHALL NOT 加独立卡片边框**。
    - 排序：**时间倒序（新评价在前），唯一排序，无切换**
  - 状态呈现（强制，遵守既有红线）：① **加载中不呈现骨架屏 / loading 指示**（仅限「页面级首屏」；**用户主动点击「重新加载」**时可在公共 `RetryBlock` 内给**转圈**在途反馈 —— UI 统一 Loop Round 13 裁决，失败态已并入 `RetryBlock` 双 CTA 形态）——数据未返回时内容区空白静默（**本稿 + 全站 UI 红线**）；② **菜品不存在 / 详情拉取失败**须给出明确文案与恢复路径（**不得只留纯空白页**）；③ 评价区失败态沿用既有**可重试块**（现状保留）。
  - 交互与无障碍（本模块强制）：可点元素触控目标 **≥ 88rpx（44pt）**（筛选胶囊视觉紧凑 ≈48rpx，命中区经 `::after` **仅纵向**扩至 88rpx —— 横向不扩，避免左右两项热区互相窃取点击）；展开 / 收起控件带 `aria-expanded`；筛选胶囊为 `role="tablist"` / `role="tab"` + `aria-selected`；轮播容器与评价头像带 `aria-label`；`prefers-reduced-motion` 下动效降级为直接显示。
- 底栏：左侧固定按钮「**写评价**」（会话内判定为已评价或提交成功后，本地写回态就地切「**重新评价**」）。未认证 → 跳转身份认证页 `pages/auth/index`（认证成功返回本页后由 onShow 续接，自动重新打开写评价表单）。
  - **判定时机**：用户点击「写评价」时（表单打开前）调 `GET /my/reviews?dishId=`——已评价打开**预填旧值**的弹层（提交走 `PUT /reviews/{id}`）；**详情页首屏零用户态请求**。
  - 右侧「**反馈错误**」按钮（**Round 25：替换原「去分享」**）—— 跳意见反馈页「更新信息」模式并**预选当前菜品**（落点唯一构造函数 `feedbackUrl('update', dishId)`，禁止手拼 URL）；`POST /dishes/{id}/correction` 属**公开写** ⇒ **免认证**，游客可直达（不经 `requireAuth`）。两钮等宽、主次分明（写评价 / 重新评价 = 主色实底，反馈错误 = 白底主色描边）。
  - **全页纠错入口唯一**（Round 25）：信息卡内无入口（R23 已移出）、写评价抽屉内亦无 —— 唯一落点即底栏本按钮，避免同一纠错表单出现两个入口。
  - **分享**：底栏不再占位 —— 由**微信右上角原生菜单**承担（`onShareAppMessage` 保留：分享菜名 + 现价 + 本页路径），分享能力不弱化。

---

## 接口数据字段（UI 精修用）

**页面**：`pages/detail/dish/index`（分包 `pages/detail/`；二级页，**无 TabBar**）+ 本页私有弹层 `ReviewComposer` / `ReportModal`

### 组件清单（本界面需要哪些组件）

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `ImageSwiper` | 页内私有 `pages/detail/dish/ImageSwiper.vue` | 顶部大图轮播（`autoplay=false` 仅手动滑动；多图显示指示点、单图不显示；无图 / 破图 → 统一占位 `ImagePlaceholder`（灰底 + `image-broken`，Round 31），块高不变） |
| 2 | `DishInfoCard` | 页内私有 | 信息卡（Round 22 重排 + Round 23 属性块还原）：① 名称 + 价格组 → ② 评分 / 位置同行 → ③ 简介（2 行 + 展开 / 收起）→ ④ **唯一分隔线**（简介存在时）→ ⑤ 描述四维（无底色 4 列等分居中、值在上 / 标签在下；**卡内无纠错入口**） |
| 3 | `DishReviewSection` | 页内私有 | 评价卡：`SectionTitle`「评价」+ 总数（经 `count`）+ **「全部 / 有图」两段式筛选胶囊** + 评价条目列表 / 空态 / 失败态 |
| 4 | `ReviewComposer` | 页内私有 | 写评价 / 重新评价底部弹层（字段与呈现见 [client-写评价.md](./client-写评价.md)）—— **只承载评价表单，不含纠错入口**（纠错在底栏「反馈错误」，Round 25） |
| 5 | `ReportModal` | 页内私有 | 举报评价底部弹层（见 [client-举报评价.md](./client-举报评价.md)） |
| 6 | `CardSection` | 公共 `components/CardSection.vue` | **两张**卡外壳（信息 / 评价）—— 原第三张「评分」卡已并入信息卡（UI 统一 Loop Round 19） |
| 7 | `SectionTitle` | 公共 `components/SectionTitle.vue` | 「评价」标题 + 可选 **`count`**（标题右侧同色 / 小半号 / 等宽数字）；`#extra` 槽承载右侧可点件（本页「全部 / 有图」胶囊、find 页「清空」）（原「综合评分」标题随评分卡移除） |
| 8 | `ReviewItem` | 公共 `components/ReviewItem.vue` | 单条评价卡（头像 / 昵称 / 星级 / 时间 / 正文 / 配图 / 三点） |
| 9 | `ActionSheet` | 公共 `components/ActionSheet.vue` | 评价三点菜单：本人「删除评价」/ 他人「举报评价」（危险红） |
| 10 | `RetryBlock` | 公共 `components/RetryBlock.vue` | 评价首屏 / 刷新失败「加载失败 · 点击重试」 |
| 11 | `ImagePicker` | 公共（经 `ReviewComposer`） | 评价配图 ≤3 张 |
| 12 | `IconSvg` | 公共 `components/IconSvg.vue` | 导航返回（**文字「返回」**） / 定位 `location` / 星（`star` 线性 · `star-filled`，评分行与评价卡用）/ 三点 `more-v` / 空态与失败示意 `empty` · `report` |
| 13 | 顶部改用公共 **`AppTitleBand`**（透明；左「返回」+ 居中**菜名随滚动淡入**）、**hero 卡**（滚动区首块）、底部操作栏 `.action-bar`（页内内联） | 公共 + `pages/detail/dish/index.vue` 内联 | 与首页 §11 **同构**（UI 统一 Loop Round 16，2026-09-27 裁决 c）：标题带**恒透明**、滚动区从带下沿开始 ⇒ 无内容从带背后经过（**零切片 / 零实底切换 / 零承接条**）；hero **随滚动 1:1 上移、在标题带下沿被裁**（"移出屏幕"，与首页 Banner 同语言）；滚动 JS **只保留菜名淡入 1 项** |
| ~~—~~ | ~~`DishSummaryCard`（综合评分卡）~~ | — | **已于 UI 统一 Loop Round 19 删除** ✅ —— 均分 / 人数 / 分布并入 `DishInfoCard` 评分行（同源同刻不变） |
| — | `swiper`（`ImageSwiper` 内）/ `textarea` / `open-type="share"` button | uni 内置控件 | 图片轮播 / 评价输入 / 分享 |

### 有哪些数据要显示、显示在哪个组件

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `id` | `GET /dishes/{id}`（`DishDetailVO`） | 菜品 ID | 无界面（页面 `key` / 请求路径 / 分享路径） | 零可见 UI |
| 2 | `name` | 同上 | 菜名 | ① `DishInfoCard` 名称行 ② `AppTitleBand` 居中标题（滚动后渐显） ③ `ReviewComposer` 副标题 ④ 分享标题 | 导航标题按滚动量淡入 |
| 3 | `price` | 同上（元） | 现价 | `DishInfoCard` 价格行 `.price-text` | **唯一价格数据源**（主色）；不以第三字段判折扣 |
| 4 | `originalPrice` | 同上 | 原价 | `DishInfoCard` 价格行 `.origin-price` | 仅 `originalPrice > price` 渲染，三级灰 + 删除线 |
| 5 | `images` | 同上 | 菜品图集 | `ImageSwiper`（页面派生 `heroImages`） | `aspectFill`，加载完成淡入；空数组 → 占位 |
| 6 | `description` | 同上 | 菜品描述 | `DishInfoCard` 描述行 `.desc-content` + 展开 / 收起入口 | 默认 2 行截断；换菜时复位收起 |
| 7 | `canteenName` / `floor` / `stallName` | 同上（端上别名 `canteen`） | 食堂 / 楼层 / 档口 | `DishInfoCard` 位置行 `.loc-text`（页面派生 `locationText`） | 「食堂 · 楼层 · 档口」；缺项兜底「未知位置」（**Round 23：本行右侧不再挂纠错入口**，入口已移至 `ReviewComposer` 菜名行右侧） |
| 8 | `dietType` / `ingredients` / `flavorTags` / `serveTemp` | 同上（**机器值**：单值 / 数组 / 数组 / 单值） | 荤素 / 主料 / 口味 / 冷热 | `DishInfoCard` 四维区 `.dim-col`（`dim-val` + `dim-label`） | **逐维渲染、缺项不占位**；多值以 `、` 连接，长值 2 行收起 |
| 9 | `field` / `value` / `label` / `order` | `GET /dishes/attributes`（经 `stores/dish-attribute`） | 四维字典 | `DishInfoCard` 四维维度名与中文值 | 端上零硬编码映射；未命中 → 该维不渲染 |
| 10 | `avgRating` / `ratingCount` | 同上（端上别名 `rating`） | 均分 / 评价人数 | `DishInfoCard` 第二行左侧（`.rating-group` / `.rating-empty`） | 均分 1 位小数（`utils/dish.formatRating`）；`ratingCount = 0` → 隐藏星、示浅灰「暂无评分」（不靠字段缺失判断）；**评价人数不渲染**（Round 22） |
| 11 | `ratingDistribution[].star` / `.count` | 同上 | 星级 / 该星级条数 | **不渲染**（Round 22：用户规格「不绘制评分进度条」） | 字段保留在接口契约中，端上不消费 |
| 12 | `records[].id` | `GET /dishes/{id}/reviews`（`PageResult<ReviewVO>`） | 评价 ID | `ReviewItem` 列表 `key` / 删除与举报目标 | 零可见 UI |
| 13 | `records[].userAvatar` | 同上 | 评价者头像 | `ReviewItem` 头像位 | 空 / 破图 → `IconSvg name="user"` 灰底 |
| 14 | `records[].userNickname` | 同上 | 评价者昵称 | `ReviewItem` 昵称行 | 空 → 「匿名用户」（注销账号显示「已注销用户」） |
| 15 | `records[].rating` | 同上 | 评分（1~5） | `ReviewItem` meta 行（实心黄星 + 数值） | 最低渲染 1 颗星 |
| 16 | `records[].createdAt` | 同上 | 发表时间 | `ReviewItem` meta 行 `.review-time` | **`formatDate`（仅 `YYYY-MM-DD`，Round 24 起评价条目不含时分）**；重评后取新时间（自然置顶） |
| 17 | `records[].content` | 同上 | 评价正文 | `ReviewItem` 正文 `.review-content` | 二级灰、`pre-wrap` |
| 18 | `records[].images` | 同上 | 评价配图（≤3） | `ReviewItem` 配图网格 | 3 等分小方图，点击预览；破图 → 统一占位 `ImagePlaceholder` |
| 19 | `records[].userId` | 同上 | 评价者用户 ID | `ActionSheet` 动作项显隐（与 `userInfo.id` 比对：本人「删除评价」/ 他人「举报评价」） | 服务端另有「非本人 → 403」兜底 |
| 20 | `total` | 同上 | 可见评价总条数 | `DishReviewSection` 标题块内 `.section-count`（经 `SectionTitle` 的 `count`） | **恒为 total 纯数字**（与标题同色、小半号、等宽；**SHALL NOT 用灰字**）；**不再有「有图 N」变体**（Round 24）；在途期与失败态不渲染 |
| 21 | `records` / `total` / `page` / `pageSize` | 分页壳 | 分页信息 | 触底加载结束判据（已加载条数 ≥ `total`） | `page` / `pageSize` 为服务端归一化值，**端上零渲染** |
| 22 | `records[0].id` / `.rating` / `.content` / `.images` | `GET /my/reviews?dishId=&page=1&pageSize=1` | 本人评价 | 底栏双态（「写评价」/「重新评价」）+ `ReviewComposer` 预填（`reviewId` + `prefill`） | 判定失败**静默按未评价**处理 |
| 23 | 加载 / 失败 / 不存在态 | 端上 `detailError` / `detailNotFound`（`4001` / 缺 id） | — | `.detail-fail` 块 | 不存在 → 「这道菜已不在了」+「它可能已被下架或移除」+ **仅「返回」**；网络失败 → 「这道菜暂时打不开」+「重新加载」+「返回」；加载中**静默空白**（无骨架屏） |
| 24 | 评价三态 | 端上 `reviewPending` / `reviewFailed` / 列表长度 | 在途 / 失败 / 空 | `DishReviewSection` | **头部（标题 + 数字 + 筛选胶囊）恒渲染**；在途 **列表区整块不渲染**（不误闪空态，数字亦不渲染）；失败 → `RetryBlock`；「有图」筛选无结果 → 「暂无带图评价」+ 副文案「切换到「全部」查看所有评价」；零评价 → 「还没有人评价这道菜」+ 副文案「你的第一条评价，能帮同学避雷」+ 「写第一条评价」 |

**入参提交**
| 接口 | 字段 |
|---|---|
| `GET /dishes/{id}/reviews` | `page` / `pageSize=10` / `hasImage`（筛选口径：**全部 = 0 / 有图 = 1**） |
| `GET /my/reviews` | `dishId` / `page=1` / `pageSize=1` |
| `POST /dishes/{id}/reviews` · `PUT /reviews/{id}` | `rating` / `content` / `images`（≤3；详见 [client-写评价.md](./client-写评价.md)） |
| `POST /feedback`（举报） | `type='report'` / `sub` / `relatedType='review'` / `relatedId` |
| `DELETE /reviews/{id}` | 无请求体（详见 [client-删除本人评价.md](./client-删除本人评价.md)） |

**错误码**：`4001` 菜品不存在 / 已下架（专属文案 + 仅返回，**不可重试**）｜网络 / `5xx`（可重试：重新加载 + 返回）｜`400` 评价参数或安检失败｜`403` 非本人｜`4031` 邮箱未认证（跳身份认证页 `pages/auth/index`，返回后由 onShow 续接原动作）
**控件类型**：页面级滚动（顶部 `AppTitleBand` + 滚动区 `flex: 1`，与首页 §11 同构）、**hero 卡随滚动移出（无 sticky 定格、无承接条）**、`onReachBottom` 触底分页、`onPageScroll`（**仅**驱动居中菜名淡入）、`BaseSheet` 底部抽屉 ×2、`ActionSheet` 三点菜单、**「全部 / 有图」两段式筛选胶囊**（`role="tablist"` / `role="tab"` + `aria-selected`）、`textarea`、星级单选、底栏「写评价 / 重新评价」+「**反馈错误**」按钮（分享由微信原生菜单承担，**无** `open-type="share"` 按钮）
