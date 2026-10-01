# 我的主页 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 页面：`pages/my-reviews/index`（分包 `pages/my-reviews/`）
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。


- 入口：「我的」页**用户卡点击**（**游客直接进入**，无认证拦截；认证要求仅在发表 / 删除评价时给出）。
- 页面构成（自上而下，**页头 + 用户信息卡 + 评价区**）：`AppHeader`（标题「**我的主页**」+ **返回**（文字））→ **用户信息卡** → **评价区**（区块标题「我的评价」+ 本人评价列表）。
- 滚动承载：页根定高 + **`scroll-view`（`flex: 1`）** 承载内容，触底由滚动区 `@scrolltolower` 触发评价列表加载更多（页面自身不滚动，`onReachBottom` 不触发）；底部留 `env(safe-area-inset-bottom)` 安全区留白。二级页，**无 TabBar**。

### 1. 用户信息卡

- 形态：**白底一级身份卡**（`--bg-card` + `--shadow-card` + `--radius-card`），顶部 **6rpx 主色软条纹**（`--color-primary-soft`，**仅认证态显示**；游客态透明）。两态**表面语言一致**，差异只由条纹与内容表达。
- 内容（一行）：头像（120rpx 圆形；无头像时 `IconSvg name="user"` 灰底占位）→ 昵称 + 副行（**认证态 = 绑定邮箱 `bindEmail`** / **游客态 = `游客 {食客XXXX}`**）→ 右侧「**编辑个人信息**」主色描边胶囊（`--font-tiny`，触控目标 ≥ 88rpx）。
- 交互：**胶囊为唯一热区**（`role="button"`、`aria-label="编辑个人信息"`），点击 → 个人信息编辑页 `pages/profile/index`（见 [client-个人信息编辑.md](./client-个人信息编辑.md)）；**卡片本体不可点**（避免与编辑入口语义重叠）。按压反馈 = `--bg-soft` 底色（**非** scale）。
- 认证动作**单一入口 = 「我的」页宫格「身份认证」格**，信息卡不放「去认证」按钮。

### 2. 评价区

- **区块标题「我的评价」仅在评价列表有数据时渲染**（`SectionTitle` 组件）；列表为空时不渲染标题，页面止于信息卡下方的空区。
- 评价卡复用**公共组件 `ReviewItem`**（与菜品详情评价区**同一实现**，视觉与交互完全一致：头像 / 昵称 / 星级 / 正文 / 配图网格 / 时间 / 右上角三点），本人视角专属信息经组件可选 props 注入；**本页以 `flat` 模式嵌入单张白色列表卡**（圆角 `16rpx` + `--shadow-card`），评价行之间以 `1rpx` `--border-color` 分隔线分隔（最上 / 最下无线）——**与系统通知页同语言**：
  - **菜名行**（`dishName`）：meta 行下展示关联菜品名，辨识是哪道菜的评价。
- 删除链路（与菜品详情评价区同款）：卡片右上角**三点** → 底部 `ActionSheet`「删除评价」（危险红动作项）→ **二次确认**弹窗 → 调 `DELETE /reviews/{id}`，成功后卡片本地移除；**删空列表后区块标题随之下线**并给轻提示「暂无评价，去菜品详情写一条吧」。
- 分页：上滑触底加载更多（**时间倒序**，唯一排序、无切换）；加载中**不呈现骨架屏**。
- 失败态：列表区渲染 `RetryBlock`「加载失败 · 点击重试」。

### 3. 空态（评价区）

| 状态 | 呈现 |
|---|---|
| 游客态（未认证） | 空态文案「暂无评价，完成身份认证后可发表评价」 |
| 认证态且从未发表 | 静默——**不渲染「我的评价」标题、不留占位行**；仅删除导致清空时给轻提示「暂无评价，去菜品详情写一条吧」 |

### 4. 展示内容口径（派生值）

| 展示项 | 来源 | 说明 |
|---|---|---|
| 头像 | `UserInfoVO.avatar` | 信息卡头像；无值时 `IconSvg name="user"` 灰底占位 |
| 昵称 | `UserInfoVO.nickname` | 信息卡主标题 |
| 绑定邮箱 | `UserInfoVO.bindEmail` | 认证态信息卡副行（**本页渲染完整邮箱**；「我的」页用户卡同样渲染 `bindEmail`，两页同源同字符串；`username` 已移出出参）。非副行的**只读展示**见 `client-个人信息编辑.md` |
| 游客短标识 | **端上派生** = 「食客 + `id` 尾 4 位」 | 游客态信息卡副行 |
| 认证态判据 | `bindEmail` 非空（单点收敛） | 决定信息卡主色条纹与副行内容 |
| 本人评价列表 | `GET /my/reviews` 的 `PageResult<MyReviewVO>`（`records` / `total`） | 评价区数据源（`MyReviewVO` 字段口径见功能文档） |

### 5. 动效

- `prefers-reduced-motion: reduce` 下取消胶囊行的 `background-color` 过渡（按压反馈退化为**直接换色**）。
- 不引入装饰性入场动效；进入页面内容立即可见。

### 6. 接口数据字段（UI 精修用）

**页面**：`pages/my-reviews/index`（分包 `pages/my-reviews/`；二级页，**无 TabBar**）

#### 组件清单（本界面需要哪些组件）

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `AppHeader` | 公共 `components/AppHeader.vue` | 页头：居中标题「我的主页」+ **返回**（文字）（`@back` → `backToHome`） |
| 2 | `ImageFallback` | 公共 `components/ImageFallback.vue` | 信息卡头像（加载失败回退统一占位 `ImagePlaceholder`，禁裂图） |
| 3 | `IconSvg` | 公共 `components/IconSvg.vue` | 无头像时的 `user` 灰底占位 |
| 4 | `ReviewItem` | 公共 `components/ReviewItem.vue` | 评价卡（`flat` 模式，与菜品详情评价区同一实现）；本人视角专属菜名经 `dish-name` prop 注入；`@more` 上抛三点动作；**嵌入单张白色列表卡、行间 1rpx 分隔线** |
| 5 | `ActionSheet` | 公共 `components/ActionSheet.vue` | 三点菜单「删除评价」（危险红动作项） |
| 6 | `RetryBlock` | 公共 `components/RetryBlock.vue` | 首屏加载失败「加载失败 · 点击重试」 |
| 7 | 信息卡 `.profile-strip` / 区块标题（页内内联） | `pages/my-reviews/index.vue` 内联 | 头像 + 昵称 / 副行 + 「编辑个人信息」胶囊；「我的评价」标题（**有数据才渲染**） |
| 8 | 评价列表卡 `.review-card`（页内内联） | 页内内联 | **单张白色轻量列表卡**（圆角 `16rpx` + `--shadow-card`）收纳全部评价行；行间 `1rpx` `--border-color` 分隔线（最上 / 最下无线） |
| — | 自然文档滚动（`onReachBottom`）+ `uni.showModal` | uni 内置 | 触底分页 + 删除二次确认 |

#### 有哪些数据要显示、显示在哪个组件

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `avatar` | `UserInfoVO`（`stores/user`，源自登录 / `GET /auth/profile`） | 头像地址 | 信息卡头像位 | 120rpx 圆形；空 / 失败 → `IconSvg name="user"` 灰底 |
| 2 | `nickname` | 同上 | 昵称 | 信息卡主标题 `.strip-nickname` | 空值时游客显「游客」、认证态显「食客」 |
| 3 | `bindEmail` | 同上 | 绑定校园邮箱 | 信息卡副行 `.strip-sub`（**认证态**） | 空值回落 `--` |
| 4 | 游客短标识 | 端上由 `id` 派生「食客 + id 尾 4 位」 | 游客标识 | 信息卡副行（**游客态**） | 次级灰小字 |
| 5 | `bindEmail` 非空 | 同上 | 认证判据（单点收敛） | 信息卡顶部**主色软条纹** `.is-verified` | 仅认证态显示 |
| 6 | `records[].id` | `GET /my/reviews`（`PageResult<MyReviewVO>`） | 评价 ID | `ReviewItem` 列表 `key` / 删除目标 | 零可见 UI |
| 7 | `records[].dishName` | 同上（本人视角专属） | 关联菜品名 | `ReviewItem` 菜名行 `.review-dish` | 次级加粗小字，单行省略 |
| 8 | `records[].rating` | 同上 | 评分 | `ReviewItem` 星级行（1~5 实心黄星 + 数值） | 1 位小数 |
| 9 | `records[].content` | 同上 | 评价正文 | `ReviewItem` 正文 | 二级灰，`pre-wrap` |
| 10 | `records[].images` | 同上 | 评价配图（≤3） | `ReviewItem` 配图网格 | 3 等分小方图，点击预览；破图 → 统一占位 `ImagePlaceholder` |
| 11 | `records[].createdAt` | 同上 | 发表时间 | `ReviewItem` meta 行 | `formatDate`（仅 `YYYY-MM-DD` —— 评价条目口径随 `ReviewItem` 全站统一） |
| 12 | `records[].userNickname` / `userAvatar` | 同上 | 昵称 / 头像 | `ReviewItem` 昵称与头像位 | 昵称空 → 「匿名用户」 |
| 13 | `records[].userId` | 同上 | 评价者用户 ID | `ReviewItem` 动作显隐判定（与当前用户 `id` 比对 → 本人「删除评价」） | 零可见 UI |
| 14 | `records[].dishId` | 同上 | 关联菜品 ID | **零界面消费**（仅供详情页 `GET /my/reviews?dishId=` 过滤入参） | — |
| 15 | `records` / `total` | 分页壳 | 当前页行 / 本人评价总条数 | 列表渲染 + `onReachBottom` 加载更多（时间倒序） | 端上以 `records` 为准 |
| 16 | 空态 / 失败态 | 端上 `isGuest` / `loadFailed` / `emptiedByDelete` | 空 / 失败 / 删空 | 空态 `EmptyState`（游客「暂无评价，完成身份认证后可发表评价」/ 删空「暂无评价，去菜品详情写一条吧」）· `RetryBlock` | 失败先于空态；游客**无认证拦截** |

**入参提交**：`GET /my/reviews` → `page` / `pageSize=20`（**仅认证态发起**）｜`DELETE /reviews/{id}` → 无请求体（归属由 token 判定）
**错误码**：`401` 未登录（请求层静默重登重试一次）｜`4031` 邮箱未认证（游客**静默**：不渲染失败态，仅渲染引导空态）｜`403` 非本人｜`400` 评价不存在
**控件类型**：自然文档滚动（`onReachBottom` 分页）、`ActionSheet` 底部动作菜单、`uni.showModal` 删除二次确认、胶囊行热区（`pressed` = `--bg-soft`，非 scale）
