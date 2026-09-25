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
  - 状态呈现（强制，遵守既有红线）：① **加载中不呈现骨架屏 / loading 指示**——数据未返回时内容区空白静默（§4.8 与 `openspec/specs/client-ui-interaction-a11y`）；② **菜品不存在 / 详情拉取失败**须给出明确文案与恢复路径（**不得只留纯空白页**）；③ 评价区失败态沿用既有**可重试块**（现状保留）。
  - 交互与无障碍（本模块强制）：可点元素触控目标 **≥ 88rpx（44pt）**；展开 / 收起控件带 `aria-expanded`；轮播容器与评价头像带 `aria-label`；`prefers-reduced-motion` 下动效降级为直接显示。
- 底栏：左侧固定按钮「**写评价**」（会话内判定为已评价或提交成功后，本地写回态就地切「**重新评价**」）。未认证 → 跳转身份认证页 `pages/auth/index`（认证成功返回本页后由 onShow 续接，自动重新打开写评价表单）。
  - **判定时机**：用户点击「写评价」时（表单打开前）调 `GET /my/reviews?dishId=`——已评价打开**预填旧值**的弹层（提交走 `PUT /reviews/{id}`）；**详情页首屏零用户态请求**。
  - 右侧「**去分享**」按钮保留（`open-type="share"`，配 `onShareAppMessage` 分享菜名 + 现价 + 本页路径）。两钮等宽、主次分明（写评价 / 重新评价 = 主色实底，去分享 = 白底主色描边）。

---

## 接口数据字段（UI 精修用）

**页面**：`pages/detail/dish/index`（+ 本页私有弹层 `ReviewComposer` / `ReportModal`）

**出参消费**
| 接口 | 字段 | 端上用途 |
|---|---|---|
| `GET /dishes/{id}`（`DishDetailVO`） | `id` / `name` / `price` / `originalPrice` | 主键 / 菜名 / 现价 / 划线原价（`> price` 才展示） |
| | `images` / `image` | 轮播多图 / 首图派生（端上由 `images[0]` 现算） |
| | `description` / `canteen` / `stallName` / `floor` | 描述（2 行展开）/ 位置行三段 |
| | `dietType` / `ingredients` / `flavorTags` / `serveTemp` | 四维机器值 → 经 `GET /dishes/attributes` 译中文 |
| | `rating` / `ratingCount` / `ratingDistribution[]`（`star` / `count`） | 评分区均分 / 人数 / 分布条（三者同源实时聚合） |
| `GET /dishes/{id}/reviews`（`PageResult<ReviewVO>`） | `records[].id` / `userId` / `userNickname` / `userAvatar` / `rating` / `content` / `images` / `createdAt` | 评价卡全字段 / 本人判定（决定三点菜单给「删除」还是「举报」） |
| `GET /dishes/attributes` | `field` / `value` / `label` / `order` | 四维字典翻译（端上零硬编码映射） |
| `GET /my/reviews?dishId=` | `records[0].id` / `rating` / `content` / `images` | 写评价弹层「是否已评价」判定 + 重评预填 |
| `GET /feedback/report-reasons` | `value` / `label` / `order` | 举报原因单选列表（提交用 `value`） |

**入参提交**
| 接口 | 字段 |
|---|---|
| `POST /dishes/{id}/reviews` / `PUT /reviews/{id}` | `rating` / `content` / `images`（≤3） |
| `POST /feedback`（举报） | `type='report'` / `sub`（原因 value）/ `relatedType='review'` / `relatedId`（评价 id） |
| `GET /dishes/{id}/reviews` | `page` / `pageSize=10` / `hasImage`（只看有图） |
| `GET /my/reviews` | `dishId` / `page=1` / `pageSize=1` |

**UI 组件**：公共 `IconSvg` / `ActionSheet` / `CardSection` / `SectionTitle` / `ReviewItem` / `RetryBlock` / `BaseSheet` / `ImagePicker`；页内私有 `ImageSwiper` / `DishInfoCard` / `DishSummaryCard` / `DishReviewSection` / `ReviewComposer` / `ReportModal`
**控件类型**：页面级滚动 + `position: sticky` hero、`onReachBottom` 触底分页、`onPageScroll`、`BaseSheet` 底部抽屉、三点 `ActionSheet`、开关（只看有图）、`textarea`、星级选择、`open-type="share"`
