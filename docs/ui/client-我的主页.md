# 我的主页 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 页面：`pages/my-reviews/index`（分包 `pages/my-reviews/`）
> 归属功能文档：评价列表 → [client-我的评价.md](../feature/client-我的评价.md)；信息卡「编辑个人信息」 → [client-个人资料.md](../feature/client-个人资料.md)
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。


- 入口：「我的」页**用户卡点击**（**游客直接进入**，无认证拦截；认证要求仅在发表 / 删除评价时给出）。
- 页面构成（自上而下，**页头 + 用户信息卡 + 评价区**）：`AppHeader`（标题「**我的主页**」+ 返回箭头）→ **用户信息卡** → **评价区**（区块标题「我的评价」+ 本人评价列表）。
- 滚动承载：不设常驻 `scroll-view`，以自然文档滚动承载内容，触底触发评价列表加载更多；底部留 `env(safe-area-inset-bottom)` 安全区留白。二级页，**无 TabBar**。

### 1. 用户信息卡

- 形态：**白底一级身份卡**（`--bg-card` + `--shadow-card` + `--radius-card`），顶部 **6rpx 主色软条纹**（`--color-primary-soft`，**仅认证态显示**；游客态透明）。两态**表面语言一致**，差异只由条纹与内容表达。
- 内容（一行）：头像（120rpx 圆形；无头像时 `IconSvg name="user"` 灰底占位）→ 昵称 + 副行（**认证态 = 学号（username）** / **游客态 = `游客 {食客XXXX}`**）→ 右侧「**编辑个人信息**」主色描边胶囊（`--font-tiny`，触控目标 ≥ 88rpx）。
- 交互：**胶囊为唯一热区**（`role="button"`、`aria-label="编辑个人信息"`），点击 → 个人信息编辑页 `pages/profile/index`（见 [client-个人信息编辑.md](./client-个人信息编辑.md)）；**卡片本体不可点**（避免与编辑入口语义重叠）。按压反馈 = `--bg-soft` 底色（**非** scale）。
- 认证动作**单一入口 = 「我的」页宫格「身份认证」格**，信息卡不放「去认证」按钮。

### 2. 评价区

- **区块标题「我的评价」仅在评价列表有数据时渲染**（`SectionTitle` 组件）；列表为空时不渲染标题，页面止于信息卡下方的空区。
- 评价卡复用**公共组件 `ReviewItem`**（与菜品详情评价区**同一实现**，视觉与交互完全一致：头像 / 昵称 / 星级 / 正文 / 配图网格 / 时间 / 右上角三点），本人视角专属信息经组件可选 props 注入：
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
| 学号 | `UserInfoVO.username` | 认证态信息卡副行（校园身份标识，**只读展示**，编辑见 `client-个人信息编辑.md`） |
| 游客短标识 | **端上派生** = 「食客 + `id` 尾 4 位」 | 游客态信息卡副行 |
| 认证态判据 | `bindEmail` 非空（单点收敛） | 决定信息卡主色条纹与副行内容 |
| 本人评价列表 | `GET /my/reviews` 的 `PageResult<MyReviewVO>`（`records` / `total`） | 评价区数据源（`MyReviewVO` 字段口径见功能文档） |

### 5. 动效

- `prefers-reduced-motion: reduce` 下取消胶囊行的 `background-color` 过渡（按压反馈退化为**直接换色**）。
- 不引入装饰性入场动效；进入页面内容立即可见。

### 6. 接口数据字段（UI 精修用）

**页面**：`pages/my-reviews/index`

**出参消费**
| 接口 | 字段 | 端上用途 |
|---|---|---|
| `GET /my/reviews`（`PageResult<MyReviewVO>`） | `records[].id` | 卡片 key / 删除目标 |
| | `records[].rating` / `content` / `images` / `createdAt` | 星级 / 正文 / 配图 ≤3 / 时间 |
| | `records[].userNickname` / `userAvatar` | 昵称 / 头像（ReviewItem 可选 props） |
| | `records[].dishName` | 菜名行（本人视角专属） |
| | `records[].userId` | 本人判定 |
| | `records[].dishId` | **零界面消费**（仅用于详情页 `?dishId` 过滤入参） |
| | `total` | 触底判断（分页壳仅 `records` / `total`；页码由请求侧掌握） |
| `UserInfoVO`（经 `stores/user`） | `avatar` / `nickname` / `username` / `bindEmail` / `id` | 信息卡头像 / 昵称 / 学号副行 / 认证判据 / 游客短标识派生 |

**入参提交**：`GET /my/reviews` → `page` / `pageSize=20`（仅认证态发起）；`DELETE /reviews/{id}` → 无请求体

**UI 组件**：公共 `AppHeader`(标题「我的主页」) / `ReviewItem` / `ActionSheet` / `RetryBlock` / `IconSvg` / `ImageFallback`
**控件类型**：自然文档滚动（`onReachBottom` 分页）、`ActionSheet` 底部动作菜单、`uni.showModal`（删除二次确认）
