# 我的页 — 页面设计稿（UI 唯一真源）

> 所属端：学生端（微信小程序） ｜ 页面：`pages/mine/index`
> 「我的」页为**入口聚合页**（非独立功能）——四个入口指向的功能（我的评价 / 系统通知 / 意见反馈 / 个人资料）与「注销账号」各有其功能文档；本稿为该页面的**构成、交互与展示内容的唯一真源**。

- 页面：`pages/mine/index`（**TabBar 主根页**，经 `reLaunch` 切换、无参数，故**恒不显示**返回**（文字）**）。
- 页面构成（自上而下，**双卡片分组 + 版本行 + TabBar**）：固定标题带「我的」→ **卡片 A（个人信息 + 快捷功能）** → *（卡片间距 `--spacing-lg`）* → **卡片 B（账号设置）** → 版本行（在**两张卡片外部**下方）→ `TabBar`。
- **分组依据（Apple HIG · 语义分组）**：个人身份信息与个人快捷操作属**同一语义组**（卡片 A）；账号协议、隐私政策、账号注销属**独立设置语义组**（卡片 B）。两卡之间**必须留白分隔**，不合并为一张 —— 避免"我的主页入口"与"危险操作（注销）"同处一卡造成的语义混淆与误触。
- 静态短内容页：**不设常驻 `scroll-view`**，以自然文档滚动承载超高内容（超大字体 / 小屏），并保留 TabBar 避让留白（`padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom))`）。

### 1. 双卡片外层容器

- **共 2 张圆角白卡**，两卡**共用同一套全局 Token**（`--bg-card` + `--shadow-card` + `--radius-card`，与菜品详情页卡片完全一致）；横向外边距 `--spacing-md`，纵向由卡片间距 `0`（首卡）+ `--spacing-lg`（卡间）控制。

| 卡片 | 收纳内容 | 卡内结构 |
|---|---|---|
| **卡片 A** | **个人信息 + 快捷功能** | 用户信息段 → *（浅细分隔线）* → 功能宫格段（一行 3 格，见 §3） |
| **卡片 B** | **账号设置** | 三行列表：用户协议 / 隐私政策 / 注销账号（行间细分隔线，见 §4） |
- **两卡间距**：`--spacing-lg`（**留白分隔**，非分隔线 —— 卡片本身已是完整边界，再加分隔线会形成双重边界噪声）。
- **卡片数量**：本页共 **2 张语义卡**（「个人信息 + 快捷功能」/「账号设置」），圆角与阴影各渲染 2 次。
- 外部形态与卡内两段**均为纯容器合并**：模块自身**不带** `--bg-card` / `--shadow-card` / `--radius-card` / 外边距，只保留卡内 padding 与分隔线。
- 版本行**不纳入**任何卡片，仍位于**卡片 B 外部**下方居中。
- **禁止**：不得把账号设置并回卡片 A（会丢失语义分组）；不得给卡片 A / B 之外的元素再套白卡（版本行、标题带均不套卡）。

### 2. 用户信息模块（**卡片 A 内第 1 段**）

- 形态：**卡片 A 内第 1 段**（见上「双卡片外层容器」），顶部 **6rpx 主色软条纹**（`--color-primary-soft`，**仅认证态显示**；游客态透明）。两态**表面语言一致**，差异只由条纹与内容表达。
- 内容（一行）：头像（120rpx 圆形；无头像时占位经公共 `ImagePlaceholder`（`name="user"`，灰底））→ 昵称 + 副行（**认证态 = 校园邮箱 `bindEmail`** / **游客态 = `游客 {食客XXXX}`**）→ 右侧 `arrow` 图标。
- 交互：**整段热区**（`role="button"`），点击 → **「我的主页」**（`pages/my-reviews/index`——用户信息卡 + 名下评价列表 + 个人信息编辑入口，见 [client-我的主页.md](./client-我的主页.md)）；**游客直接进入**（无认证拦截——游客在页内看到游客信息卡与认证引导空态；认证要求仅在**评价写操作**时给出）。按压反馈 = `--bg-soft` 底色（**非** scale）。
- 无障碍：`aria-label="查看我的主页"`。
- 认证动作**单一入口 = 宫格「身份认证」格**（用户信息模块不放「去认证」按钮）。
- **模块底部**：一条浅细分隔线（`--border-color`），与卡片 A 内下方功能宫格隔开。

### 3. 功能宫格模块（**卡片 A 内第 2 段**，一行 3 格）

- 形态：**卡片 A 内一行三列等宽布局**，**每格无独立白色卡片外壳**（无 `--bg-card` / `--shadow-card` / `--radius-card`）；列间距 `--spacing-md`；**每格整格热区**。
- 顺序固定：

| # | 格 | 图标键 | 点击行为 | 附加 |
|---|---|---|---|---|
| 1 | 意见反馈 | `lightbulb-fill` | → `pages/feedback/index` | — |
| 2 | 系统通知 | `bell` | → `pages/notifications/index` | 右上**未读红点** |
| 3 | 身份认证 | `badge-check` | 未认证 → 跳转身份认证页 `pages/auth/index`（认证动作单一入口）；已认证 → Toast「已完成身份认证」 | 右上**已认证徽章**（主色圆点，仅认证态显示） |

- 格内：96rpx 圆底 chip（`--color-primary-soft`）内嵌主色图标（图标在上）+ 标签在下（`--font-subtitle`、`--weight-semibold`）。**无副标题**（图标 + 标签自明）。
- 角标：**「系统通知」右上红点**（14rpx、`--color-error`，贴 chip 右上角、**不遮图标主体**，仅未读数 > 0 且登录态显示）；**「身份认证」已认证主色圆点**（同位、`--color-primary`）。
- 按压反馈：`hover-class="pressed"` → `--bg-soft`（**非** scale）。
- 无障碍：每格 `role="button"` + `aria-label`（等于标签文案）；角标为装饰，`aria-hidden="true"`。
- **模块底部**：**不加分隔线** —— 宫格是卡片 A 的**最后一块内容**（**两卡之间靠留白分隔，不靠线**）。

### 4. 「其他」列表模块 —— **独立第二张卡片（卡片 B）**

- 形态：**独立第二张圆角白卡**（`--bg-card` + `--shadow-card` + `--radius-card`，与卡片 A **完全同款**）；与卡片 A 之间留 **`--spacing-lg`** 外边距。它**就是**卡片 B。
- 内部**三行独立列表项**，行间以细分隔线（`--border-color`）分隔。
- **模块底部不加分隔线**（作为卡片 B 内最后一块内容）。
- 行结构：左侧文字（`--font-body`）+ 右侧 `arrow` 图标（`--text-tertiary`），**整行热区**（`role="button"`，触控目标 ≥ 88rpx）。
- 三行固定：

| 行 | 文字色 | 点击行为 |
|---|---|---|
| 用户协议 | `--text-body` | → `pages/privacy/agreement` |
| 隐私政策 | `--text-body` | → `pages/privacy/index` |
| 注销账号 | **`--color-error`**（危险弱化，非主行动） | **二次确认弹窗**（确认按钮取危险色实值）→ 调注销端点并清本地态 |

- 按压反馈：`--bg-soft`（**非** scale）；无障碍：每行 `role="button"` + `aria-label` 等于文案。

#### 4.1 页内承载物：注销账号（危险弱化行 + 二次确认）


- 行：`.more-row--danger` —— 文字取 `--color-error` + 右箭头 `IconSvg name="arrow"`，**非主行动**。
- 点击 → **二次确认**（`uni.showModal`，确认钮取危险色实值，确认文案「确认注销」），说明后果：昵称头像清空、评价与反馈保留但去身份化、**不可恢复**（重新登录将创建全新账号，旧数据不会回来）。
- 成功后 `useUserStore.forceLogout()` 清本地 token 与用户态，页面回落游客态（旧 token 已被服务端拉黑）。

| 数据 | 来源 | 显示在哪个组件 | 呈现形式 |
|---|---|---|---|
| 弹窗后果说明 | **端内静态文案**（非接口） | `uni.showModal` 正文 | 「注销后账号将匿名化且不可恢复：重新登录将创建全新账号，你的评价与反馈会保留但不关联身份，也不会回到新账号。」 |
| 行文案「注销账号」 | 端内静态文案 | `.more-row--danger` | 错误色文字 + 右箭头 |
| 注销结果 | `DELETE /auth/account` 成功（`data` = null） | 无界面（Toast「账号已注销」） | — |
| 失败提示 | `400` / `401` 响应 `message` | 无界面（Toast 直透，兜底「注销失败，请稍后重试」） | — |

**入参提交**：`DELETE /auth/account` → **无请求体、无参数**（注销对象 = JWT 当前用户，端上**不传 userId**；且该请求**跳过 401 静默登录重试** —— 静默登录会建出新游客号，重试会误删新账号）
**错误码**：`400` 账号已注销（终态保护）/ 账号已被禁用，无法注销｜`401` 请先登录

### 5. 版本行

- **两张卡片之外**、独立一行居中纯展示（`aria-hidden` 包裹）：「`知行食记 v{version}`」（`--font-tiny`、`--text-tertiary`）—— 文案、字号、浅灰弱展示样式不变，**不纳入任何卡片容器**。

### 6. 页面展示内容口径（派生值）

| 展示项 | 来源 | 说明 |
|---|---|---|
| 昵称 | `UserInfoVO.nickname` | 用户卡主标题；未设置时游客显示「游客」、认证态显示「食客」 |
| 校园邮箱 | `UserInfoVO.bindEmail` | 认证态用户卡副行（**本页与「我的主页」信息卡统一渲染完整校园邮箱**，同源同字符串；`username` 已移出出参） |
| 游客短标识 | **端上派生** = 「食客 + `id` 尾 4 位」（id 不可得回退本地游客 ID） | 游客态用户卡副行 |
| 认证态判据 | `bindEmail` 非空（单点收敛） | 决定用户卡两态与宫格「身份认证」徽章显隐 |
| 通知未读数 | `GET /my/notifications/unread-count` 的 `count` | 「系统通知」红点（仅未读 > 0 且登录态显示） |
| 版本号 | 构建期注入 `__APP_VERSION__`（源自 `manifest.json` versionName） | 版本行 |

### 7. TabBar

- 底部常驻，「我的」高亮（`showTab('profile')`）。TabBar 为**公共组件**（`components/TabBar.vue`，非本页专属），其结构 / 视觉规范见 [client-首页菜品浏览.md](./client-首页菜品浏览.md) 与全站 §4 视觉基线。

### 8. 动效

- `prefers-reduced-motion: reduce` 下取消 `.user-card` / `.grid-cell` / 列表行的 `background-color` 过渡（按压反馈退化为**直接换色**，不产生动画）；本稿**不新增任何动效**。

### 9. 接口数据字段（UI 精修用）

**页面**：`pages/mine/index`（**TabBar 主根页**，经 `reLaunch` 切换、无参数）

#### 组件清单（本界面需要哪些组件）

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `AppHeader` | 公共 `components/AppHeader.vue` | 页头：标题「我的」，`:show-back="false"`（TabBar 主根页恒不显示**返回**（文字）） |
| 2 | `ImageFallback` | 公共 `components/ImageFallback.vue` | 用户卡头像（加载失败回退统一占位 `ImagePlaceholder`） |
| 3 | `IconSvg` | 公共 `components/IconSvg.vue` | 头像空态 `user`、行右箭头 `arrow`、宫格图标（`lightbulb-fill` / `bell` / `badge-check`） |
| 4 | `TabBar` | 公共 `components/TabBar.vue` | 底部常驻菜单（首页 / 我的，本页高亮 `showTab('profile')`） |
| 5 | 卡片 A 外壳 `.mine-card`（页内内联） | `pages/mine/index.vue` 内联 | **白卡容器**（`--bg-card` + `--shadow-card` + `--radius-card`），收纳下列两个模块（用户信息 / 功能宫格） |
| 6 | 用户信息模块 `.user-card`（页内内联） | 页内内联 | **卡片 A 内**第 1 段：头像 + 昵称 / 副行 + 右箭头；整段热区 → 「我的主页」 |
| 7 | 功能宫格 `.grid`（页内内联） | 页内内联 | **卡片 A 内**第 2 段：**一行三列内联宫格**（**去掉每格独立白卡外壳**）：意见反馈 / 系统通知 / 身份认证（含角标） |
| 8 | 卡片 B 外壳 =「其他」列表 `.more-group`（页内内联） | 页内内联 | **独立第二张白卡**（与卡片 A 同款 Token，上外边距 `--spacing-lg`）：三行：用户协议 / 隐私政策 / 注销账号（危险弱化） |
| 9 | 版本行 `.app-footer`（页内内联） | 页内内联 | 「知行食记 v{version}」+ 副文案（`aria-hidden`），**位于大卡之外** |
| 10 | `useNotifyStore` | `stores/notify` | 未读通知数（宫格红点真源；`onShow` 时仅认证态刷新） |
| — | `uni.showModal` / `uni.navigateTo` / `uni.reLaunch` | uni 内置 | 注销二次确认 / 各入口跳转 / TabBar 切页 |

> 本页为**静态短内容页**：页根定高 + **`scroll-view`（`.mine-scroll`）** 承载超高内容（大字体 / 小屏），采用「`min-height` → `height` + 滚动区」形态。

#### 有哪些数据要显示、显示在哪个组件

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `nickname` | `UserInfoVO`（`stores/user`，源自 `POST /auth/wechat-login` / `GET /auth/profile`） | 昵称 | 用户卡主标题 `.nickname` | 认证态 = 昵称；空值时游客显「游客」、认证态显「食客」 |
| 2 | `avatar` | 同上 | 头像地址 | 用户卡头像位（`ImageFallback`；空 / 失败 → `IconSvg name="user"` 灰底） | 120rpx 圆形 |
| 3 | `bindEmail` | 同上 | 校园邮箱 | 用户卡副行 `.user-id`（**仅认证态**） | 次级灰小字 |
| 4 | 游客短标识 | **端上派生** = 「食客 + `id` 尾 4 位」（`id` 不可得回退 `getLocalGuestLabel()`） | 游客标识 | 用户卡副行（**仅游客态**） | 「游客 食客XXXX」 |
| 5 | `bindEmail` | 同上 | 已绑定校园邮箱 | **认证判据**：用户卡顶部 6rpx 主色软条纹（认证态显示）+ 宫格「身份认证」主色徽章 | 非空 = 已认证 |
| 6 | `count` | `GET /my/notifications/unread-count`（经 `stores/notify`） | 未读通知总数 | 宫格「系统通知」右上**红点** | 仅认证态且 `> 0` 显示 |
| 7 | 版本号 `appVersion` | **构建期注入** `__APP_VERSION__`（源自 `manifest.json` 的 `versionName`，运行时读不到 manifest） | 版本号 | 版本行 `.app-footer` | 「知行食记 v{version}」+「北京交通大学 · 校园美食分享圈」 |
| 8 | `createdAt` | `UserInfoVO` | 注册时间 | **本页不展示**（仅「个人信息编辑」页只读展示） | — |

**静态入口（无接口数据）**
| 区域 | 条目 | 点击行为 | 附加 |
|---|---|---|---|
| 用户卡 | 查看我的主页 | `navigateTo(pages/my-reviews/index)`（**游客直接进入**，无认证拦截） | 认证动作**不放这里** |
| 宫格 | 意见反馈 | → `pages/feedback/index` | — |
| 宫格 | 系统通知 | → `pages/notifications/index` | 未读红点 |
| 宫格 | 身份认证 | 未认证 → `requestAuth()` 跳 `pages/auth/index`；已认证 → Toast「已完成身份认证」 | 已认证主色圆点（认证动作**单一入口**） |
| 其他 | 用户协议 / 隐私政策 | → `pages/privacy/agreement` / `pages/privacy/index` | — |
| 其他 | 注销账号 | 二次确认 → `DELETE /auth/account` | 危险弱化色（`--color-error`） |

**入参提交**：`DELETE /auth/account`（注销账号行）→ 无请求体、无参数（对象 = JWT 当前用户；随后 `forceLogout()` 清本地态）
**错误码**：`400` 账号已注销 / 账号已被禁用，无法注销｜`401` 请先登录
**控件类型**：自然文档滚动（无常驻 `scroll-view`）、**双卡片结构（卡片 A = 个人信息 + 功能宫格，卡片 B = 账号设置；卡间留白 `--spacing-lg`）**、用户信息模块整段热区、宫格格（`role="button"` + `aria-label`）、列表行（≥88rpx）、`uni.showModal` 二次确认（注销确认钮取**主色实值**，与删除类危险操作口径不同）
