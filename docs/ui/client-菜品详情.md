# 菜品详情 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 归属功能文档：[client-菜品详情.md](../feature/client-菜品详情.md)
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。


- 页面：`pages/detail/dish/index`
  - 图片轮播（`images` 多图）：**仅手动滑动**（关闭自动轮播 `autoplay`）；多图显示指示点、单图不显示；无图显示中性占位。
  - 信息卡条目与顺序（自上而下，**卡内分隔线语言统一为一条**）：① 名称 + 价格行 → ② 位置行（食堂 · 楼层 · 档口名；**右侧并入「信息有误？」入口**，点击跳意见反馈页「更新信息」模式并预选当前菜品）→ ③ 描述（默认两行 + 展开 / 收起）→ ④ 四维指标（**荤素 / 主料 / 口味 / 冷热**）。**入口不单独占行**，且采用与「展开」入口**相同的文本档位**（主色 + 同字号字重；权威条款见 `openspec/specs/contribution-entry`）。**不显示**：标签 chips、「信息更新于 X」、评分 / 评价数（归评分区）、窗口号、距离。
  - 四维指标渲染口径：**逐维渲染，缺项不占位**——某维无值时不渲染该列（**不得**出现 `-` 空列凑满四列）；维度名与数据必须一致（「荤素」=`dietType`、「主料」=`ingredients`、「口味」=`flavorTags`、「冷热」=`serveTemp`）。
    - **取值形态（R4）**：四维**下发机器值**（`dietType` / `serveTemp` 为单值，`ingredients` / `flavorTags` 为**数组**），**展示中文由后端字典端点下发**（模式对齐 `GET /dishes/meal-types`）——端上 SHALL NOT 硬编码「机器值 → 中文」映射表，亦 SHALL NOT 由后端直出中文（**web 端同样消费四维且需要表单选项**，直出中文只能解决一端、且会与 `mealType` 的字典模式形成两套并列）。多值以 `、` 连接后单行展示，超长按既有 `-webkit-line-clamp: 2` 换行收起。依据 PR-12（枚举展示须与后端常量表同源 + **全端盘点**）。详见功能文档 §6 R4 与 `project_spec.md` §7.40 第 4 项。
  - 价格口径：**展示唯一数据源 = `price`（常显现价）**；`originalPrice` 有值时并列**原价划线**；**不以任何第三个字段判折扣**（禁止双源写法——会导致不同步行展示过期价、折扣丢失），判据恒为 `originalPrice > price`（见 `project_spec.md` §7.26）。
  - 不展示独立「折扣价」——是否有折扣由「原价 × 现价」两态表达，无需第三个价格字段。
  - 评分区：均分 + 评价数 + 评分分布（1~5 星各多少条）。
    - **卡内数字同源口径**：**同一「评分摘要」组件内的全部数字 MUST 同源同刻** —— 分布条（分子、分母）、「**N 人评分**」文案、均分三者 SHALL 统一取**实时聚合**（一次查询同刻算出，分母 = `sum(count)`）。
      - **依据**：`ratingDistribution` 是**实时聚合**（`selectRatingDistribution`），而缓存列会漂移 → 两部分混用必然出现「各星百分比之和 ≠ 100%」或「分布总占比 ≠ N 人评分」（违反 PR-02 单一真源 / PR-08 聚合口径）。
      - **例外**：均分若因性能确需取缓存，须在 `project_spec.md` **显式登记为有意权衡**并接受与分布的微差（**当前无此登记 → 默认三者全实时**）。
  - 评价区：
    - 标题行带**「只看有图」开关**（默认关）
    - 评价卡片：头像、昵称、星级、文字、配图 ≤3 张、时间
    - 排序：**时间倒序（新评价在前），唯一排序，无切换**
  - 状态呈现（强制，遵守既有红线）：① **加载中不呈现骨架屏 / loading 指示**（仅限「页面级首屏」；**用户主动点击「重新加载」**时可在公共 `RetryBlock` 内给**转圈**在途反馈 —— UI 统一 Loop Round 13 裁决，失败态已并入 `RetryBlock` 双 CTA 形态）——数据未返回时内容区空白静默（§4.8 与 `openspec/specs/client-ui-interaction-a11y`）；② **菜品不存在 / 详情拉取失败**须给出明确文案与恢复路径（**不得只留纯空白页**）；③ 评价区失败态沿用既有**可重试块**（现状保留）。
  - 交互与无障碍（本模块强制）：可点元素触控目标 **≥ 88rpx（44pt）**；展开 / 收起控件带 `aria-expanded`；轮播容器与评价头像带 `aria-label`；`prefers-reduced-motion` 下动效降级为直接显示。
- 底栏：左侧固定按钮「**写评价**」（会话内判定为已评价或提交成功后，本地写回态就地切「**重新评价**」）。未认证 → 跳转身份认证页 `pages/auth/index`（认证成功返回本页后由 onShow 续接，自动重新打开写评价表单）。
  - **判定时机**：用户点击「写评价」时（表单打开前）调 `GET /my/reviews?dishId=`——已评价打开**预填旧值**的弹层（提交走 `PUT /reviews/{id}`）；**详情页首屏零用户态请求**。
  - 右侧「**去分享**」按钮保留（`open-type="share"`，配 `onShareAppMessage` 分享菜名 + 现价 + 本页路径）。两钮等宽、主次分明（写评价 / 重新评价 = 主色实底，去分享 = 白底主色描边）。

---

## 接口数据字段（UI 精修用）

**页面**：`pages/detail/dish/index`（分包 `pages/detail/`；二级页，**无 TabBar**）+ 本页私有弹层 `ReviewComposer` / `ReportModal`

### 组件清单（本界面需要哪些组件）

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `ImageSwiper` | 页内私有 `pages/detail/dish/ImageSwiper.vue` | 顶部大图轮播（`autoplay=false` 仅手动滑动；多图显示指示点、单图不显示；无图 / 破图 → `empty` 中性占位，块高不变） |
| 2 | `DishInfoCard` | 页内私有 | 信息卡四段：① 名称 + 价格 → ② 位置行（右侧并入「信息有误？」） → ③ 描述（2 行 + 展开 / 收起） → ④ 四维指标 |
| 3 | `DishSummaryCard` | 页内私有 | 综合评分卡：均分 + 评价人数 + 5 条评分分布条 |
| 4 | `DishReviewSection` | 页内私有 | 评价卡：`SectionTitle`「评价」+ 数量 + 「只看有图」开关 + 评价列表 / 空态 / 失败态 |
| 5 | `ReviewComposer` | 页内私有 | 写评价 / 重新评价底部弹层（字段与呈现见 [client-写评价.md](./client-写评价.md)） |
| 6 | `ReportModal` | 页内私有 | 举报评价底部弹层（见 [client-举报评价.md](./client-举报评价.md)） |
| 7 | `CardSection` | 公共 `components/CardSection.vue` | 三张卡外壳（信息 / 评分 / 评价） |
| 8 | `SectionTitle` | 公共 `components/SectionTitle.vue` | 「综合评分」「评价」标题；评价卡经 `#extra` 槽承载数量 + 开关 |
| 9 | `ReviewItem` | 公共 `components/ReviewItem.vue` | 单条评价卡（头像 / 昵称 / 星级 / 时间 / 正文 / 配图 / 三点） |
| 10 | `ActionSheet` | 公共 `components/ActionSheet.vue` | 评价三点菜单：本人「删除评价」/ 他人「举报评价」（危险红） |
| 11 | `RetryBlock` | 公共 `components/RetryBlock.vue` | 评价首屏 / 刷新失败「加载失败 · 点击重试」 |
| 12 | `ImagePicker` | 公共（经 `ReviewComposer`） | 评价配图 ≤3 张 |
| 13 | `IconSvg` | 公共 `components/IconSvg.vue` | 导航返回 `arrow-left` / 定位 `location` / 星（`star` 线性 · `star-filled`）/ 三点 `more-v` / 空态与失败示意 `empty` · `report` |
| 14 | 顶部改用公共 **`AppTitleBand`**（透明；左「返回」+ 居中**菜名随滚动淡入**）、**hero 卡**（滚动区首块）、底部操作栏 `.action-bar`（页内内联） | 公共 + `pages/detail/dish/index.vue` 内联 | 与首页 §11 **同构**（UI 统一 Loop Round 16，2026-09-27 裁决 c）：标题带**恒透明**、滚动区从带下沿开始 ⇒ 无内容从带背后经过（**零切片 / 零实底切换 / 零承接条**）；hero **随滚动 1:1 上移、在标题带下沿被裁**（"移出屏幕"，与首页 Banner 同语言）；滚动 JS **只保留菜名淡入 1 项** |
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
| 7 | `canteenName` / `floor` / `stallName` | 同上（端上别名 `canteen`） | 食堂 / 楼层 / 档口 | `DishInfoCard` 位置行 `.loc-text`（页面派生 `locationText`） | 「食堂 · 楼层 · 档口」；缺项兜底「未知位置」；右侧「信息有误？」→ 反馈页 `update` 模式 |
| 8 | `dietType` / `ingredients` / `flavorTags` / `serveTemp` | 同上（**机器值**：单值 / 数组 / 数组 / 单值） | 荤素 / 主料 / 口味 / 冷热 | `DishInfoCard` 四维区 `.dim-col`（`dim-val` + `dim-label`） | **逐维渲染、缺项不占位**；多值以 `、` 连接，长值 2 行收起 |
| 9 | `field` / `value` / `label` / `order` | `GET /dishes/attributes`（经 `stores/dish-attribute`） | 四维字典 | `DishInfoCard` 四维维度名与中文值 | 端上零硬编码映射；未命中 → 该维不渲染 |
| 10 | `avgRating` / `ratingCount` | 同上（端上别名 `rating`） | 均分 / 评价人数 | `DishSummaryCard` 左侧（`.summary-score` / `.summary-count`） | 均分 1 位小数；`ratingCount = 0` → 「还没有评分」空态（不靠字段缺失判断） |
| 11 | `ratingDistribution[].star` / `.count` | 同上 | 星级 / 该星级条数 | `DishSummaryCard` 右侧 5 条分布行 | 星图标 + 星级数字 + 分布条（宽 = `count / ratingCount`）+ 条数；恒 5 项、**按 star 降序、端上不排序** |
| 12 | `records[].id` | `GET /dishes/{id}/reviews`（`PageResult<ReviewVO>`） | 评价 ID | `ReviewItem` 列表 `key` / 删除与举报目标 | 零可见 UI |
| 13 | `records[].userAvatar` | 同上 | 评价者头像 | `ReviewItem` 头像位 | 空 / 破图 → `IconSvg name="user"` 灰底 |
| 14 | `records[].userNickname` | 同上 | 评价者昵称 | `ReviewItem` 昵称行 | 空 → 「匿名用户」（注销账号显示「已注销用户」） |
| 15 | `records[].rating` | 同上 | 评分（1~5） | `ReviewItem` meta 行（实心黄星 + 数值） | 最低渲染 1 颗星 |
| 16 | `records[].createdAt` | 同上 | 发表时间 | `ReviewItem` meta 行右侧 `.review-time` | `formatDateTime`；重评后取新时间（自然置顶） |
| 17 | `records[].content` | 同上 | 评价正文 | `ReviewItem` 正文 `.review-content` | 二级灰、`pre-wrap` |
| 18 | `records[].images` | 同上 | 评价配图（≤3） | `ReviewItem` 配图网格 | 3 等分小方图，点击预览；破图 `empty` 占位 |
| 19 | `records[].userId` | 同上 | 评价者用户 ID | `ActionSheet` 动作项显隐（与 `userInfo.id` 比对：本人「删除评价」/ 他人「举报评价」） | 服务端另有「非本人 → 403」兜底 |
| 20 | `total` | 同上 | 可见评价总条数 | `DishReviewSection` 标题右侧 `.review-count` | 全量 = 「N」；「只看有图」开启 = 「有图 N」；在途期与失败态不显示 |
| 21 | `records` / `total` / `page` / `pageSize` | 分页壳 | 分页信息 | 触底加载结束判据（已加载条数 ≥ `total`） | `page` / `pageSize` 为服务端归一化值，**端上零渲染** |
| 22 | `records[0].id` / `.rating` / `.content` / `.images` | `GET /my/reviews?dishId=&page=1&pageSize=1` | 本人评价 | 底栏双态（「写评价」/「重新评价」）+ `ReviewComposer` 预填（`reviewId` + `prefill`） | 判定失败**静默按未评价**处理 |
| 23 | 加载 / 失败 / 不存在态 | 端上 `detailError` / `detailNotFound`（`4001` / 缺 id） | — | `.detail-fail` 块 | 不存在 → 「这道菜已不在了」+「它可能已被下架或移除」+ **仅「返回」**；网络失败 → 「这道菜暂时打不开」+「重新加载」+「返回」；加载中**静默空白**（无骨架屏） |
| 24 | 评价三态 | 端上 `reviewPending` / `reviewFailed` / 列表长度 | 在途 / 失败 / 空 | `DishReviewSection` | 在途 **整块不渲染**（不误闪空态）；失败 → `RetryBlock`；「只看有图」无结果 → 「暂无带图评价」；零评价 → 鼓励态 + 「写第一条评价」 |

**入参提交**
| 接口 | 字段 |
|---|---|
| `GET /dishes/{id}/reviews` | `page` / `pageSize=10` / `hasImage`（「只看有图」） |
| `GET /my/reviews` | `dishId` / `page=1` / `pageSize=1` |
| `POST /dishes/{id}/reviews` · `PUT /reviews/{id}` | `rating` / `content` / `images`（≤3；详见 [client-写评价.md](./client-写评价.md)） |
| `POST /feedback`（举报） | `type='report'` / `sub` / `relatedType='review'` / `relatedId` |
| `DELETE /reviews/{id}` | 无请求体（详见 [client-删除本人评价.md](./client-删除本人评价.md)） |

**错误码**：`4001` 菜品不存在 / 已下架（专属文案 + 仅返回，**不可重试**）｜网络 / `5xx`（可重试：重新加载 + 返回）｜`400` 评价参数或安检失败｜`403` 非本人｜`4031` 邮箱未认证（跳身份认证页 `pages/auth/index`，返回后由 onShow 续接原动作）
**控件类型**：页面级滚动（顶部 `AppTitleBand` + 滚动区 `flex: 1`，与首页 §11 同构）、**hero 卡随滚动移出（无 sticky 定格、无承接条）**、`onReachBottom` 触底分页、`onPageScroll`（**仅**驱动居中菜名淡入）、`BaseSheet` 底部抽屉 ×2、`ActionSheet` 三点菜单、「只看有图」开关（`role="switch"`）、`textarea`、星级单选、`open-type="share"` 分享按钮
