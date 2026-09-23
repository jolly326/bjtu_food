# 我的页 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 归属功能文档：[client-我的页.md](../feature/client-我的页.md)
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。

- 页面：`pages/mine/index`（**TabBar 主根页**，经 `reLaunch` 切换、无参数，故**恒不显示返回箭头**）。
- 页面构成（自上而下，**三块 + TabBar**）：固定标题带「我的」→ **用户卡** → **功能宫格** → **底部信息区** → `TabBar`。
- 静态短内容页：**不设常驻 `scroll-view`**，以自然文档滚动承载超高内容（超大字体 / 小屏），并保留 TabBar 避让留白（`padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom))`）。

### 1. 用户卡

- 形态：**白底一级身份卡**（`--bg-card` + `--shadow-card` + `--radius-card`），顶部 **6rpx 主色软条纹**（`--color-primary-soft`，**仅认证态显示**；游客态透明）。两态**表面语言一致**，差异只由条纹与内容表达。
- 内容（一行）：头像（120rpx 圆形；无头像时 `IconSvg name="user"` 灰底占位）→ 昵称 + 副行（**认证态 = 绑定邮箱** / **游客态 = `游客 {食客XXXX}`**）→ 游客态右侧「**去认证**」按钮（主色文字 + `1rpx` 主色描边胶囊）→ 右侧 `arrow` 图标。
- 交互：**整卡热区**（`role="button"`），点击**二分** —— 游客 → 弹 `AuthSheet`；认证态 → 进个人信息编辑页。按压反馈 = `--bg-soft` 底色（**非** scale）。
- 无障碍：`aria-label` 随态变化（认证态「查看或编辑个人资料」/ 游客「游客身份」）；「去认证」按钮有独立 `aria-label`（点击需 `@tap.stop` 避免触发整卡）。

### 2. 功能宫格

- 形态：**一行三列**等宽等高白卡（`--radius-card` + `--shadow-card`），格间距 `--spacing-md`，**每格整格热区**。
- 顺序固定：**意见反馈**（icon `report`）→ **系统通知**（icon `bell`）→ **我的评价**（icon `star`）。
- 格内：96rpx 圆底 chip（`--color-primary-soft`）内嵌主色图标（图标在上）+ 标签在下（`--font-subtitle`、`--weight-semibold`）。
- 角标：**「系统通知」右上红点**（14rpx、`--color-error`，贴 chip 右上角、**不遮图标主体**）；**仅未读数 > 0 时显示**（无未读 / 未登录不显示）。
- 按压反馈：`hover-class="pressed"` → `--bg-soft`（**非** scale）。
- 无障碍：每格 `role="button"` + `aria-label`（等于标签文案）；红点为装饰，`aria-hidden="true"`。

### 3. 底部信息区

- 与宫格之间留**大片留白**（上下各一次 `--spacing-xl`），位于 TabBar 之上。
- **两行纯展示**（由 `aria-hidden` 子容器包裹）：「`知行食记 v{version}`」·「北京交通大学 · 校园美食分享圈」（`--font-tiny`、`--text-tertiary`、行高 1.5、居中）。
- **一行合规入口**（该区域**唯一可点元素**）：
  - 「隐私政策 · 用户协议」：主色文字（`--color-primary-text`）+ 主色描边胶囊 → `pages/privacy/index`。
  - 「注销账号」：**danger 弱化**（`--color-error` 文字与描边、`opacity: .8`，非主行动）→ 二次确认弹窗。
- ⚠️ **无障碍红线**：`aria-hidden` **不得挂在整个底部容器上** —— 否则合规入口对辅助技术不可见；只挂在「两行纯展示」的子容器上。

### 4. TabBar

- 底部常驻，「我的」高亮（`showTab('profile')`）。TabBar 为**公共组件**（`components/TabBar.vue`，非本页专属），其结构 / 视觉规范见 [client-首页菜品浏览.md](./client-首页菜品浏览.md) 与全站 §4 视觉基线。

### 5. 动效

- `prefers-reduced-motion: reduce` 下取消 `.user-card` / `.grid-cell` 的 `background-color` 过渡（按压反馈退化为**直接换色**，不产生动画）。

### 6. 待办（未拍板）

- 底部「注销账号」当前为**小字号文字链**形态；README 待办 #5 建议**重设计**（转 UI/UX 出稿，**不动接口**）。
