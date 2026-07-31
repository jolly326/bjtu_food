# 页面 / 功能清单提案（细化到具体界面）

> 文档目的：供产品负责人（用户）逐条选择 / 裁剪的**界面级清单**。
> 编写依据：
> - `project_spec.md`（唯一权威约束：角色、红线、§0.3 双端模块地图、§3.x 契约、§4 UI 规范、§5 开发约束）
> - `tasks/ARCH_DECISIONS_PHASE1.md`（一期 DDL/契约/白名单定稿）
> - `tasks/task-01~05`（一期已建/待增强界面）、`task-06`（轻社区概要）、`task-07`（足迹）、`task-08`（成就占位）
> - 当前代码现状：`frontend/src/pages/**`、`web/src/views/**` 现有文件结构
>
> 术语对齐要求（务必沿用，见 §0.6 红线 3 与 §3.x.6）：
> - 图标 emoji 化：🔍 搜索、📍 位置、❤️ 喜欢/收藏（单一语义，**不可与点赞混用**）、👍 有用/点赞、💡 猜你喜欢、🔥 热门、⏰ 限时、📤 分享、💬 评价、➕ 发布。
> - 金额：前端展示元，提交/存储分（§3 金额红线）。
> - 布局：小程序根容器严格 750rpx 视口，正文禁止横向滚动（§0.6 红线 1 / §4.9）。
> - 动效：小程序 MVP 从简，仅 `<transition>` + 简单 CSS transition；Web 遵循 §4.3 spring 规范（§0.6 红线 2）。
> - 角色仅 `STUDENT` / `ADMIN`，无 `STALL_OWNER`（§0.2 / §0.6 红线）。
> - 字段名对外 camelCase（§3.x.6.1）；Web 经 `api/adapter.ts` 转换，禁止 snake 泄漏视图层（§5.x）。
>
> 标注约定：**【已建】** = 一期已落地文件，本次仅复核内容；**【待增强】** = 已建文件但需补充板块/字段；**【新增】** = 需新建页面/模块。

---

## 一、小程序端（学生，uni-app）

### 1. 已有界面复核（home / find / dish-detail / canteen-detail / stall-detail / favorite / profile）

#### 1.1 首页 `pages/home` —【已建 / 待增强】
现状（task-01）：Banner 轮播 + 食堂横滑入口 + 热门双列瀑布流；已移除「今日上新 / 猜你喜欢 / 食堂精选轮播」。
建议「当前该放什么内容」（自上而下板块）：
- **Banner 轮播区**：`ImageSwiper` 复用；点击按 `target_type`（DISH/ACTIVITY/URL/NONE）跳转（§3.x.2）。限时活动运营位由后台 Banner 承担，不单独建表（§3.x.3）。
- **食堂入口横滑**：卡片 = 图 + 名称 + 营业状态徽标（open/closed）；点击进 `pages-detail/canteen`（游客可进）。
- **热门菜品瀑布流**：`WaterfallList` 双列；首屏 `/dishes/hot`，上拉追加 `GET /dishes?sortBy=heat`（§3.x.4）。卡片 `DishCard`（图 + 名 + 价格(分→元) + 评分 + ❤️ favoriteCount）。
- **【待增强】社区广场轻入口卡**：在瀑布流顶部或 Banner 下方插入一张「动态」入口卡（emoji 💬 / 🔥），点击跳转新增「社区广场」Tab（见 2.1）。理由：首页是流量集散地，轻社区需要首页级曝光；若社区一期不做则可省略。
- **交互**：卡片 tap `scale(0.97)`；瀑布流滚动橡皮筋；reduced-motion 降级。

#### 1.2 搜索 / 发现 `pages/find` —【已建 / 待增强】
现状（task-02）：搜索框 + 分类宫格 + 热搜/黑马/新上架榜 + 多维筛选结果页。
建议「当前该放什么内容」：
- **搜索框（联想）**：输入 debounce 300ms 调 `GET /dishes/suggest`（dish/stall/canteen 混合联想，见 ARCH §2.1）；点击按 type 跳转。
- **分类宫格**：8 类（面食/盖饭/麻辣烫/早餐/夜宵/快餐/小吃/饮品），前端静态常量 `constants/categories.ts`，点击 → `GET /dishes?tag=`（ARCH §4，不新建字典表）。
- **热搜榜 TOP10**：`GET /dishes/hot-search`（一期为菜品热度派生词条，ARCH §5）；点击 → `keyword=` 检索。
- **新晋黑马榜**：`GET /dishes/rising`；**新上架榜**：`GET /dishes/new`。
- **多维筛选结果页**：食堂/价格区间(分)/口味标签/评分排序；结果 `WaterfallList` 双列无限加载；无结果显示 EmptyState + 清除筛选。
- **【待增强】搜索结果内「关联动态」提示**：若菜品在结果卡上有动态数徽标（🔥N），点击进菜品详情「关联动态」区（task-03 占位 → task-06 填充）。

#### 1.3 菜品详情 `pages-detail/dish.vue` —【已建 / 待增强】（task-03）
建议「当前该放什么内容」（自上而下）：
- **头部**：大图轮播 `ImageSwiper` + 名称 + 价格(分→元) + 评分(avgRating/ratingCount) + ❤️ 喜欢（toggle）。
- **位置链路**：食堂 → 楼层(floor) → 档口 → 窗口号(windowNo) + 营业时间(businessHours)；节点可钻取（§3.x.6.3 扩展字段）。
- **属性标签**：辣度(spiceLevel 🌶️级数)/分量(portion)/供应时段(servePeriod ⏰)/是否限量(limited)（TagLabel）。
- **评价区**：晒图过滤 `isWithImage`；**有用 👍** 切换（幂等，受 `uk_useful_user_review`）；排序「最新 / 最有用」。
- **活动入口**：若挂活动，展示活动价 + 跳转 `pages-detail/activity` + 公众号链接 `officialAccountUrl`（§3.x.6.5）。
- **【待增强】关联动态区块**：`GET /moments?dishId=`（task-06 契约），展示挂了该菜品的社区动态缩略卡；一期未上线显 EmptyState（task-03 AC-7）。这是社区与菜品的**核心关联点**。

#### 1.4 食堂详情 `pages-detail/canteen.vue` —【已建 / 待增强】
建议板块：
- 食堂头图 + 名称 + 营业状态；楼层/窗口导览（来自档口 `floor`/`windowNo`）。
- **档口列表**（按楼层分组）：每个档口卡 → 跳 `pages-detail/stall`。
- **菜品速览**：该食堂热门/最新菜品 `WaterfallList`。
- **【待增强】食堂关联动态入口**：聚合挂了该食堂档口/菜品的动态（复用 `GET /moments?stallId=` 或按食堂聚合）。

#### 1.5 档口详情 `pages-detail/stall.vue` —【已建 / 待增强】
建议板块：
- 档口头图 + 名称 + 楼层/窗口号/营业时间。
- **菜品列表**：该档口全部在售菜品（评分/价格/❤️）；点击进菜品详情。
- **【待增强】档口关联动态**：`GET /moments?stallId=`，展示相关动态。

#### 1.6 收藏 `pages/favorite` —【已建 / 待增强】
建议板块：
- 我的喜欢列表（❤️，复用 `GET /favorites` 分页）；双列卡片；点击进详情；再点取消。
- **【待增强】收藏夹/清单联动**：若做美食清单（`/lists`），此处可汇总「我的清单」入口（见三、可选清单 L4）。

#### 1.7 我的 `pages/profile/index.vue` —【已建 / 待增强】
现状：含登录/注册/找回密码 + 个人中心入口（我的发布 `my-publish`、发布菜品、提交档口）。
建议「当前该放什么内容」板块：
- **未登录态**：登录/注册/找回密码表单（已建）。
- **已登录态个人中心**：
  - 用户信息卡：头像 + 昵称 + 等级占位（task-08 三期占位）。
  - 功能宫格：我的发布(`my-publish`)、我的评价、我的喜欢(`favorite`)、我的动态(**新增**，task-06)、浏览足迹(**新增**，task-07)、美食清单(**新增可选**，L4)。
  - 设置区：反馈入口(🔧，**新增可选**，L3)、关于 / 隐私、退出登录。
- **【待增强】发布入口聚合**：底部悬浮 ➕（task-06 三选一 Sheet：发动态 / 提交菜品 / 提交档口）。

---

### 2. 【轻量化社区】（用户重点要求，详细设计）

> 依据 `task-06`（二期概要）展开为界面级方案。核心定位：**图文动态流 + 关联菜品/档口标签反哺 + 先审后发（复用 §3.x.1 状态机）**。
> 红线约束：动态 `audit_status=pending → approved/rejected`，reject 回写 `reject_reason`（§3.x.1）；动态浏览 PUB、发布 STU（task-06 §5）。

#### 2.1 社区广场（动态信息流 feed）—【新增】`pages/community`（或 `pages/moments`）
- **入口**：底部 TabBar 第四 Tab（task-06 §2「首页底部 Tab『动态』页，新 Tab」）；首页也可置「动态」轻入口卡（1.1 待增强）。
- **板块构成**：
  - 顶部 Tab 切换：**推荐 / 最新 / 关注（关注一期可选，见 L2）**。
  - **动态卡片流**（纵向单列，`WaterfallList` 双列可选）：每张卡片含
    - 发布者头像 + 昵称 + 发布时间（相对时间）。
    - 正文文本（截断 `-webkit-line-clamp`，点击进详情）。
    - 图片（≤9，首图大图 + 九宫格缩略，点击预览）。
    - **关联标签**：若挂菜品/档口，显示 📍「某某菜品/档口」chip，点击跳对应详情。
    - 互动栏：**👍 有用**（计数）+ 💬 评论数（从简，点击进详情）；❤️ 喜欢不适用于动态（红线：❤️ 仅收藏语义，动态不引入喜欢，避免语义膨胀）。
  - 空态 EmptyState（无动态/审核中）。
- **交互**：卡片 tap `scale(0.97)`；下拉刷新 + 上拉无限加载（PUB 列表 `GET /moments`）；reduced-motion 降级。

#### 2.2 动态详情 —【新增】`pages-detail/moment.vue`
- **板块**：
  - 发布者信息 + 关注按钮（可选）。
  - 正文全文 + 图片九宫格（点击大图预览，支持左右滑）。
  - **关联对象卡**：菜品/档口 chip，点击跳详情（社区 → 菜品的反向导流）。
  - **互动区**：👍 有用（toggle 幂等，复用 `POST /reviews/{id}/useful` 同款语义可另立 `POST /moments/{id}/useful`，**或**一期仅展示计数不互动，从简）；💬 评论列表（见 2.5）。
  - **审核态提示**（仅作者本人可见）：若 `audit_status=pending` 显「审核中」；`rejected` 显「已退回：{reject_reason}」+ 编辑重提入口（复用原记录，§3.x.1）。

#### 2.3 发布动态 —【新增】`pages/publish-moment`（或 Sheet 内嵌）
- **入口**：右下角悬浮 ➕ → 三选一 Sheet（发动态 / 提交菜品 / 提交档口，task-06 §2）；选「发动态」进本页或底部 Sheet。
- **板块（表单）**：
  - 正文输入框（多行，字数上限提示，如 500 字）。
  - 图片上传（≤9 张，`/upload/image`，单张 ≤5MB jpg/jpeg/png/webp，§4.2）。
  - **关联对象选择**（可选）：📍 选择菜品（搜索 `GET /dishes?keyword=` 联想）/ 档口（`GET /stalls`）；可不选（纯自由动态）。
  - 提交按钮 → `POST /moments`（STU，置 `audit_status=pending`）。
- **交互**：Sheet spring 0.8/0.3 + 手势中断（§4.4）；提交成功 Toast 四态反馈；跳回社区广场或「我的动态」。

#### 2.4 我的动态 —【新增】`pages/my-moments`（或并入 profile 功能宫格）
- **板块**：
  - 状态分段：**全部 / 审核中(pending) / 已退回(rejected)**。
  - 列表卡片同社区广场卡样式，但退回态显 `reject_reason` 红字 + 「编辑重提」按钮（复用原记录，`audit_status→pending`，`reject_reason` 清空，§3.x.1）。
  - 数据源 `GET /my/moments`（STU，含审核态）。

#### 2.5 动态互动（点赞/评论，从简）—【新增 / 从简】
> 产品经理决策点：互动深度直接决定后端成本。两种方案：
- **方案 A（推荐·极简）**：动态仅支持 👍 有用计数（toggle 幂等，一人一票，复用 `uk_useful_user_moment` 同构约束）；评论**一期不做**，动态详情仅显「💬 N 条评论（规划中）」占位或完全不显评论入口。理由：与点评主链路（菜品评价）解耦，社区先做「内容消费 + 有用」，降低 MVP 复杂度，符合 §3.x.3 克制原则。
- **方案 B（含评论）**：动态详情下方评论区，复用评价 `review` 思路但独立 `moment_comment` 表（一人一动态一条或树形），增 `POST /moments/{id}/comments` 等接口。成本显著高于 A。
- **互动语义红线**：动态用 👍=有用（与菜品评价「有用」语义统一，§0.6 红线 3），**不引入 ❤️ 喜欢到动态**（❤️ 仅收藏语义）。

#### 2.6 社区与菜品的关联闭环（重要，对应 task-03「关联动态」占位）
- 菜品详情「关联动态」区块（1.3 待增强）调 `GET /moments?dishId=` → 展示挂了该菜品的动态缩略卡。
- 档口/食堂详情（1.4/1.5 待增强）同理按 `related_stall_id` 聚合。
- 动态详情反向挂 📍 chip 跳菜品/档口详情 → 形成「内容 ↔ 实体」双向导流。
- 此关联是提案中社区价值的核心，建议**必做**（即使社区整体一期只做 A 方案）。

---

### 3. 其他可考虑界面（让用户选，标注推荐度）

| 编号 | 界面 | 板块/交互概要 | 推荐度 | 关联 task |
| --- | --- | --- | --- | --- |
| L1 | 消息 / 通知中心 `pages/notify` | 审核结果通知（动态/菜品通过或退回）、被 👍/评论提醒、活动上线推送；列表 + 已读/清空。 | ⭐⭐⭐ 推荐（社区上线后刚需） | 新（需 `notification` 表/接口） |
| L2 | 关注 / 粉丝 `pages/follow` | 关注其他学生/平鉴官；社区广场「关注」流依赖此。 | ⭐ 一期可不做的（克制） | 新（用户关系表） |
| L3 | 反馈入口 `pages/feedback`（或 Sheet） | 类型(功能建议/内容纠错/其他) + 内容 + 联系方式选填 → `POST /feedback`（已存在 STU 接口，§3.x.5）。 | ⭐⭐⭐ 推荐必做（接口已存在，仅缺前端入口） | 复用 `/feedback` |
| L4 | 美食清单 `pages/lists` | 自建清单(命名+菜品) + 分享(`/lists/share/{token}` 已存在 PUB) + 一键收藏(`POST /favorites/batch`)。 | ⭐⭐ 可选（增强收藏体验） | 复用 `/lists` 契约 |
| L5 | 设置 `pages/settings` | 关于/隐私政策/清除缓存/账号注销/通知开关。 | ⭐⭐ 推荐（基础体验） | 新（轻量） |
| L6 | 浏览足迹 `pages/history` | 时间倒序浏览过的菜品/档口/食堂；清空/删单条（`GET /my/history`，task-07）。 | ⭐⭐⭐ 推荐（接 task-07，反哺「猜你喜欢」） | task-07 |
| L7 | 成就/等级 `pages/achievement` | 美食家等级 + 勋章展示（task-08 三期占位）。 | ⭐ 三期再做 | task-08 |

---

## 二、Web 管理后台（admin，Vue3 + Element Plus）

> 现状模块（已建）：`dashboard`、`canteen`（含 CanteensView/StallDetailView/DishDetailView/ContentReviewView/ReviewReviewView）、`activity`、`banner`、`user`、`admin`（AdminManageView/AccountSettingsView）、`login`。
> 强制约束：列表/表单全面复用自封装组件 `DataTable/FormDialog/ConfirmDialog/StatusTag/SearchInput/ImageUpload`（§4.10）；三态齐全；破坏性操作二次确认；字段 camelCase 经 adapter（§5.x）；仅 ADMIN（§0.2）。

### 1. 已有模块复核（列表页 + 表单/详情抽屉 字段与操作）

#### 1.1 仪表盘 `dashboard/DashboardView.vue` —【已建 / 待增强】
- **板块**：核心指标卡（用户数、菜品数、待审 UGC 数、评价数、动态数[社区上线后]）；ECharts 趋势（浏览/评价/发布趋势）；待办入口（待审跳审核台）。
- **数据源**：`GET /admin/dashboard`（range 参数）。
- **【待增强】社区指标**：动态发布量、待审动态数卡（社区上线后补）。

#### 1.2 菜品管理 `canteen/DishDetailView.vue` + 列表 —【已建 / 待增强】（task-05）
- **列表页字段**：缩略图、名称、档口/食堂、价格(分→元展示)、评分(avgRating/ratingCount)、❤️ favoriteCount、状态(status on/off)、审核态(audit_status)、操作(编辑/上架下架/删除)。
- **表单/详情抽屉字段**（§3.x.6.3 + ARCH §1.3）：
  - 基础：名称、价格(分)、图片(≤多张 `ImageUpload`)、描述、所属档口(stallId)。
  - **新增属性标签**：辣度(spiceLevel `el-select` 0-3)、分量(portion `el-select` 0-2)、供应时段(servePeriod 多选 tag breakfast/lunch/dinner/midnight)、是否限量(limited `el-switch`)。
  - 标签 tags（含品类 key）、审核态只读 + reject_reason 回显。
- **操作**：CRUD + 上架/下架（`/admin/dishes`）。

#### 1.3 档口管理 `canteen/StallDetailView.vue` + 列表 —【已建 / 待增强】
- **列表字段**：名称、所属食堂、楼层、窗口号、营业状态(open/closed)、操作。
- **表单字段**（ARCH §1.3）：名称、所属食堂(canteenId)、**楼层(floor)、窗口号(windowNo)、营业时间(businessHours)**、状态。
- **操作**：CRUD + 营业/停业切换。

#### 1.4 食堂管理 `canteen/CanteensView.vue` + 详情 —【已建】
- **列表/表单字段**：名称、图片、状态(open/closed)、描述；一期**不扩** canteen 级 business_hours（ARCH §1.3 红线）。
- **操作**：CRUD + 开关。

#### 1.5 评价审核 `canteen/ReviewReviewView.vue` —【已建】
- **列表字段**：菜品、用户、评分(rating 1-5)、内容、图片标识、状态(isHidden 0/1)、操作。
- **抽屉/操作**：`PUT /admin/reviews/{id}/hide` 切换隐藏；`DELETE` 物理删除（二次确认）；按 `isHidden` 过滤。
- **【待增强】「有用」列**：显示 usefulCount（§3.x.6.4），便于运营识别优质评价。

#### 1.6 菜品/档口/食堂审核 `canteen/ContentReviewView.vue` —【已建】
- **列表**：类型切换(dish/stall/canteen) × 状态(pending/approved/rejected)；字段名/图/提交人/时间。
- **抽屉**：详情预览 + **通过**(`/admin/audit/{type}/{id}/approve`) / **退回**(填 `rejectReason` 必填，`/admin/audit/{type}/{id}/reject`)。
- **【待增强】动态审核类型**：类型切换新增 `moment`（task-06 契约 `/admin/audit?type=moment`），复用同款抽屉。

#### 1.7 用户管理 `user/UserView.vue` —【已建】
- **列表字段**：昵称、邮箱(@bjtu.edu.cn)、角色(student/admin)、状态(active/disabled)、注册时间、操作。
- **操作**：禁用/启用、角色置位(admin 显式，§1 默认 student)、删除（二次确认）。

#### 1.8 Banner 管理 `banner/BannerManageView.vue` —【已建】
- **列表字段**：标题、图、跳转类型(targetType 枚举 DISH/ACTIVITY/URL/NONE)、状态(enabled/disabled)、排序(sortOrder)。
- **表单字段**（§3.x.6.2）：title、subtitle、images、**targetType**（切换联动：DISH/ACTIVITY 选 ID、URL 填 targetUrl、NONE 禁用）、sortOrder、status。提交校验「类型↔目标」一致（违返 400）。

#### 1.9 活动管理 `activity/ActivityView.vue` —【已建】
- **列表字段**：标题、关联菜品、活动价/原价(分→元)、时间(startAt/endAt)、状态(enabled/disabled)、操作。
- **表单字段**（§3.x.6.5）：name、description、coverImage、dishId、activityPrice(分)、originPrice(分)、**officialAccountUrl(必填，公众号推文链接，红线)**、startAt、endAt、status、sortOrder。

#### 1.10 管理员管理 `admin/AdminManageView.vue` + 账号设置 `admin/AccountSettingsView.vue` —【已建】
- 管理员账号 CRUD + 个人账号设置（改密等）；仅 ADMIN。

---

### 2. 运营管理可补充模块（让用户选）

| 编号 | 模块 | 列表页 + 表单/抽屉字段与操作 | 推荐度 | 关联 |
| --- | --- | --- | --- | --- |
| W1 | 反馈处理 `admin/FeedbackView` | 列表：类型、内容、联系方式、提交人、时间、状态(待处理/已处理)；抽屉：详情 + 标记处理/回复（后端 `POST /feedback` 已存在，缺 admin 查询/处理接口）。 | ⭐⭐⭐ 推荐（前端已发反馈，后台需闭环） | 新 admin 接口 |
| W2 | 社区内容审核 `admin/MomentReviewView` | 复用内容审核台类型新增 `moment`（1.6 待增强）；列表同 ContentReviewView；动态详情预览卡（图+文+关联对象）。 | ⭐⭐⭐ 必做（若社区一期做） | task-06 |
| W3 | 数据报表导出 `admin/ReportView` | 菜品/评价/用户/动态多维统计；导出 Excel/CSV（ECharts 数据 + 导出按钮）。 | ⭐⭐ 可选 | 新（复用 dashboard 数据） |
| W4 | 操作日志 `admin/OperationLogView` | 列表：操作人、动作、对象、IP、时间；只读查询（需 `operation_log` 表 + AOP 埋点）。 | ⭐⭐ 可选（合规/审计，成本中） | 新（表+切面） |
| W5 | 动态管理（非审核）`admin/MomentManageView` | 与普通内容管理并列：动态 CRUD + 强制下架/隐藏（区别于审核态，应对已 approved 但违规内容）。 | ⭐⭐ 可选（与 W2 配合） | task-06 |
| W6 | 美食清单管理 `admin/ListView` | 若小程序做 L4 清单，后台可查看/下架违规清单。 | ⭐ 可选 | 复用 `/lists` |

---

### 3. 每个模块「列表页 + 表单/详情抽屉」通用规范（强制，§4.10 / §5.x）
- 列表页：三态（Loading/Empty/正常）、`SearchInput` 过滤、`StatusTag` 状态、`DataTable` 渲染、批量选择 + 批量操作（如批量通过/下架）。
- 表单/抽屉：`FormDialog` 承载；枚举用 `el-select`/多选 tag/`el-switch`；金额字段展示元、提交分；图片 `ImageUpload`；破坏性操作经 `ConfirmDialog` 二次确认。
- 字段命名：经 `api/adapter.ts` 转 camelCase，视图层零 snake（§5.x 红线）。
- 权限：非 ADMIN 访问 `/admin/**` → 403（§0.2）。

---

## 三、待用户决策的选项清单（选择题，附推荐度）

> 便于项目经理向你（用户）逐条呈现。推荐度：🔴 必做 / 🟡 推荐 / ⚪ 可选 / ⛔ 不建议一期。

### A. 小程序端决策
| 选项 | 问题 | 推荐 | 影响 |
| --- | --- | --- | --- |
| A1 | **轻量化社区是否一期做？** | 🔴 必做（核心诉求） | 新增 community/my-moments/publish-moment/moment-detail + TabBar 第四 Tab；后端 `moment` 表 + 接口（task-06） |
| A2 | 社区互动深度：方案 A（仅 👍 有用）还是 方案 B（含评论）？ | 🔴 A（极简，从简原则） | B 需 `moment_comment` 表 + 多接口 |
| A3 | 首页是否加「动态」轻入口卡？ | 🟡 推荐 | 首页 1.1 待增强一处 |
| A4 | 消息/通知中心（L1）是否一期做？ | 🟡 推荐（社区上线后刚需） | 新 notification 体系 |
| A5 | 浏览足迹（L6 / task-07）是否做？ | 🟡 推荐（反哺猜你喜欢） | 新 `view_log` 表 + 接口 |
| A6 | 关注/粉丝（L2）是否一期做？ | ⚪ 可选（克制，建议二期） | 用户关系表 |
| A7 | 反馈入口（L3）是否做？ | 🔴 必做（接口已存在） | 仅前端 Sheet/页 |
| A8 | 美食清单（L4）是否做？ | ⚪ 可选 | 复用 `/lists` |
| A9 | 设置页（L5）/ 成就（L7）是否一期做？ | ⚪ L5 轻量推荐；L7 三期 | L5 新轻量页；L7 task-08 |

### B. Web 后台决策
| 选项 | 问题 | 推荐 | 影响 |
| --- | --- | --- | --- |
| B1 | 社区内容审核（W2）是否做？ | 🔴 必做（若 A1 做） | 复用审核台 + type=moment |
| B2 | 反馈处理（W1）是否做？ | 🔴 必做（闭环 L3） | 新 admin 反馈查询/处理接口 |
| B3 | 数据报表导出（W3）是否做？ | 🟡 推荐 | 新导出能力 |
| B4 | 操作日志（W4）是否做？ | ⚪ 可选（合规成本中） | `operation_log` 表 + AOP |
| B5 | 动态管理/下架（W5）是否做？ | 🟡 推荐（与 W2 配合） | 同 task-06 |
| B6 | 美食清单管理（W6）是否做？ | ⚪ 可选（依赖 A8） | 复用 `/lists` |

---

## 四、核心建议（需求梳理师视角）

1. **必做界面（推荐本期锁定）**：
   - 小程序：社区广场、动态详情、发布动态、我的动态（task-06 实体化）；菜品详情「关联动态」区块（task-03 占位填充）；反馈入口（L3，接口已存在）；浏览足迹（L6，反哺推荐）。
   - Web：社区内容审核（复用审核台 + moment 类型）、反馈处理（闭环）。
2. **社区与菜品关联是核心价值**：务必打通「菜品详情 → 关联动态」+「动态 → 📍 跳菜品/档口」双向导流（§2.6），否则社区沦为孤立信息流。
3. **克制原则（呼应 §3.x.3 / §0.6）**：社区一期仅做「内容消费 + 👍 有用 + 关联反哺」，评论/关注/消息中心可后置；避免 MVP 复杂度失控与动效/布局红线冲突。
4. **一致性红线不可破**：无 `STALL_OWNER`；金额分/元；camelCase 对外；emoji 语义唯一（❤️ 仅收藏、👍=有用）；750rpx 布局；动效从简。
5. **建议推进顺序**：A1/A2/A7/B1/B2 先定（决定后端 `moment` 表与接口范围）→ 小程序社区四界面 + Web 审核 → 其次 L6 足迹、L1 消息（若做）→ 最后 L2/L4/L8 等增强。

> 注：本文档为**提案/清单**，不修改 `task-01~08`、`ARCH_*.md`、`project_spec.md` 或任何代码；落地时社区相关细化为 task-06 派工文档，其他新增界面补对应 task 文件。
