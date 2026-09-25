# 我的页 — 页面设计稿（UI 唯一真源）

> 所属端：学生端（微信小程序） ｜ 页面：`pages/mine/index`
> 「我的」页为**入口聚合页**（非独立功能）——四个入口指向的功能（我的评价 / 系统通知 / 意见反馈 / 个人资料）与「注销账号」各有其功能文档；本稿为该页面的**构成、交互与展示内容的唯一真源**。

- 页面：`pages/mine/index`（**TabBar 主根页**，经 `reLaunch` 切换、无参数，故**恒不显示返回箭头**）。
- 页面构成（自上而下，**四块 + TabBar**）：固定标题带「我的」→ **用户卡** → **功能宫格（一行 3 格）** → **「其他」分组列表** → 版本行 → `TabBar`。
- 静态短内容页：**不设常驻 `scroll-view`**，以自然文档滚动承载超高内容（超大字体 / 小屏），并保留 TabBar 避让留白（`padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom))`）。

### 1. 用户卡

- 形态：**白底一级身份卡**（`--bg-card` + `--shadow-card` + `--radius-card`），顶部 **6rpx 主色软条纹**（`--color-primary-soft`，**仅认证态显示**；游客态透明）。两态**表面语言一致**，差异只由条纹与内容表达。
- 内容（一行）：头像（120rpx 圆形；无头像时 `IconSvg name="user"` 灰底占位）→ 昵称 + 副行（**认证态 = 绑定邮箱** / **游客态 = `游客 {食客XXXX}`**）→ 右侧 `arrow` 图标。
- 交互：**整卡热区**（`role="button"`），点击 → **「我的主页」**（`pages/my-reviews/index`——用户信息卡 + 名下评价列表 + 个人信息编辑入口，见 [client-我的主页.md](./client-我的主页.md)）；**游客直接进入**（无认证拦截——游客在页内看到游客信息卡与认证引导空态；认证要求仅在**评价写操作**时给出）。按压反馈 = `--bg-soft` 底色（**非** scale）。
- 无障碍：`aria-label="查看我的主页"`。
- 认证动作**单一入口 = 宫格「身份认证」格**（用户卡不放「去认证」按钮）。

### 2. 功能宫格（一行 3 格）

- 形态：**一行三格**等宽等高白卡（`--radius-card` + `--shadow-card`），格间距 `--spacing-md`，**每格整格热区**。
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

### 3. 「其他」分组列表

- 形态：**白底分组卡**（`--radius-card` + `--shadow-card`），内部**三行独立列表项**，行间以细分隔线（`--border-color`）分隔；与宫格之间留 `--spacing-lg`。
- 行结构：左侧文字（`--font-body`）+ 右侧 `arrow` 图标（`--text-tertiary`），**整行热区**（`role="button"`，触控目标 ≥ 88rpx）。
- 三行固定：

| 行 | 文字色 | 点击行为 |
|---|---|---|
| 用户协议 | `--text-body` | → `pages/privacy/agreement` |
| 隐私政策 | `--text-body` | → `pages/privacy/index` |
| 注销账号 | **`--color-error`**（危险弱化，非主行动） | **二次确认弹窗**（确认按钮取危险色实值）→ 调注销端点并清本地态 |

- 按压反馈：`--bg-soft`（**非** scale）；无障碍：每行 `role="button"` + `aria-label` 等于文案。

### 4. 版本行

- 「其他」列表之下独立一行居中纯展示（`aria-hidden` 包裹）：「`知行食记 v{version}`」（`--font-tiny`、`--text-tertiary`）。

### 5. 页面展示内容口径（派生值）

| 展示项 | 来源 | 说明 |
|---|---|---|
| 昵称 | `UserInfoVO.nickname` | 用户卡主标题；未设置时游客显示「游客」、认证态显示「食客」 |
| 绑定邮箱 | `UserInfoVO.bindEmail` | 认证态用户卡副行（**校园邮箱唯一出参来源**） |
| 游客短标识 | **端上派生** = 「食客 + `id` 尾 4 位」（id 不可得回退本地游客 ID） | 游客态用户卡副行 |
| 认证态判据 | `bindEmail` 非空（单点收敛） | 决定用户卡两态与宫格「身份认证」徽章显隐 |
| 通知未读数 | `GET /my/notifications/unread-count` 的 `count` | 「系统通知」红点（仅未读 > 0 且登录态显示） |
| 版本号 | 构建期注入 `__APP_VERSION__`（源自 `manifest.json` versionName） | 版本行 |

### 6. TabBar

- 底部常驻，「我的」高亮（`showTab('profile')`）。TabBar 为**公共组件**（`components/TabBar.vue`，非本页专属），其结构 / 视觉规范见 [client-首页菜品浏览.md](./client-首页菜品浏览.md) 与全站 §4 视觉基线。

### 7. 动效

- `prefers-reduced-motion: reduce` 下取消 `.user-card` / `.grid-cell` / 列表行的 `background-color` 过渡（按压反馈退化为**直接换色**，不产生动画）。

### 8. 接口数据字段（UI 精修用）

**页面**：`pages/mine/index`（TabBar 主根页）

**出参消费**
| 接口 | 字段 | 端上用途 |
|---|---|---|
| `POST /auth/wechat-login` / `GET /auth/profile`（`UserInfoVO`，经 `stores/user`） | `id` | 派生猪标识「食客 + id 尾 4 位」 |
| | `username` | 认证态用户卡副行（学号） |
| | `nickname` / `avatar` | 用户卡主标题 / 头像 |
| | `bindEmail` | `isVerified()` 认证判据（两态与条纹、认证徽章） |
| | `createdAt` | 本页不展示（仅个人信息编辑页展示） |
| `GET /my/notifications/unread-count` | `count` | 宫格「系统通知」红点（仅认证态且 > 0） |

**入参提交**：`DELETE /auth/account` → 无请求体

**UI 组件**：公共 `AppHeader`(show-back=false) / `IconSvg` / `ImageFallback` / `TabBar`
**控件类型**：整卡热区、宫格 `grid-cell`、分组列表行、`uni.showModal`（注销二次确认）
