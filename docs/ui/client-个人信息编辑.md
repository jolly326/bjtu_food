# 个人信息编辑 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 页面：`pages/profile/index`（分包 `pages/profile/`）
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。


- 入口：**「我的主页」用户信息卡右侧「编辑个人信息」**（游客态与认证态同达，无认证拦截）。宿主主页见 [client-我的主页.md](./client-我的主页.md)。
- 页面形态：**独立页面**，`AppHeader` 页头标题「**个人信息**」+ 可滚动表单区；**返回**（文字） = `navigateBack`。二级页，**无 TabBar**。
- 构成（自上而下）：页头 → **头像行**（可改）→ **昵称行**（可改）→ **学号 / 校园邮箱 / 注册时间**（只读）→ 页面底部主按钮「**保存**」。

### 1. 字段区

- 分组卡形态：白底分组卡（`--radius-card` + `--shadow-card`），行间细分隔线（`--border-color`）；可改行取全站**浅底无边框输入项**语言（`--bg-soft` + `--radius-field`）。每行触控目标 ≥ 88rpx。

| 行 | 左侧 | 右侧 | 可编辑 |
|---|---|---|---|
| 头像 | 标签「头像」（`--font-body`） | 120rpx 圆形头像（无头像 `IconSvg name="user"` 灰底占位）+ `arrow` | **是**（换图） |
| 昵称 | 标签「昵称」 | `input`，占位「请输入昵称」，`maxlength=20` | **是** |
| 学号 | 标签「学号」 | 只读文本（`--text-secondary`） | 否 |
| 校园邮箱 | 标签「校园邮箱」 | 只读：认证态 = `bindEmail`；游客态 = 「未绑定校园邮箱」（`--text-tertiary`） | 否 |
| 注册时间 | 标签「注册时间」 | 只读：`createdAt`（`yyyy-MM-dd HH:mm:ss`）；游客态同显示 | 否 |

- 只读行**不放任何跳转入口**（身份认证动作单一入口 = 「我的」页宫格「身份认证」格）。

### 2. 头像更换

- 点整行 → `ActionSheet`「拍照 / 从相册选择」→ 选图（单张）→ 本地压缩（≤1MB、≤750×1334）→ 经 UGC 图片上传链路取回地址后**就地刷新预览**，地址暂存、随「保存」写库。
- 上传中给进行时反馈（头像位 loading），**禁止静默等待**；失败 → Toast 展示后端 `message`，**保留原头像**。

### 3. 保存链路

- 主按钮：主色实底 `AppButton`「**保存**」（页面底部，非行内）。**无改动或提交中禁用**（半透明）。
- 点击 → `PUT /auth/profile`（昵称与头像**至少一项**）→ 成功 Toast「保存成功」→ `navigateBack` 返回我的主页，由主页 `onShow` 重新读取资料并刷新信息卡。
- 失败 → Toast 提示 `message`，**停留本页**并保留已填内容；昵称命中敏感内容或内容安检 `risky` → 后端 `400`，同按 `message` 提示、不保存。

### 4. 数据回填与状态

- 进入即调 `GET /auth/profile` 回填四行；**加载中不呈现骨架屏 / loading 指示**（数据未返回时表单区静默空白）。
- 拉取失败 → 表单区渲染 `RetryBlock`「加载失败 · 点击重试」，重试成功前保存按钮禁用。

### 5. 无障碍与动效

- 头像行 `role="button"` + `aria-label="更换头像"`；昵称输入带 `aria-label="昵称"`；右侧 `arrow` 图标 `aria-hidden="true"`。
- `prefers-reduced-motion: reduce` 下取消可点行的 `background-color` 过渡（按压反馈退化为**直接换色**）；按压取 `--bg-soft`，**非** scale。

### 6. 接口数据字段（UI 精修用）

**页面**：`pages/profile/index`（分包 `pages/profile/`；二级页，**无 TabBar**）

#### 组件清单（本界面需要哪些组件）

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `AppHeader` | 公共 `components/AppHeader.vue` | 页头：居中标题「个人信息」+ **返回**（文字）（`@back` → `backToHome`） |
| 2 | `AppButton` | 公共 `components/AppButton.vue` | 固定底部 `submit-bar` 内「**保存**」主按钮（`loading` = 保存中；无改动 / 提交中禁用） |
| 3 | `IconSvg` | 公共 `components/IconSvg.vue` | 无头像时 `user` 灰底占位 + 头像行右箭头 `arrow` |
| 4 | 分组信息卡 `.info-card` + 行 `.info-row`（页内内联） | `pages/profile/index.vue` 内联 | 五行结构：头像 / 昵称 / 学号 / 校园邮箱 / 注册时间 |
| 5 | `scroll-view`(scroll-y) | uni 内置控件 | 表单滚动区（底部留 `--action-bar-height` + 安全区避让） |
| 6 | `input`（昵称） | uni 内置控件 | 昵称录入（占位「请输入昵称」） |
| 7 | `uni.chooseImage` | uni 内置控件 | 头像选图（单张、压缩；`album` + `camera`，由系统自带选择面板承载） |
| 8 | `uploadAvatarImage` | `client/src/api/upload.ts` | 头像上传链路：取回站内地址 → 本地暂存（**随「保存」才落库**） |

> **无** `ActionSheet`、**无** `RetryBlock`、**无** 独立弹层：本页为单一分组卡 + 固定底栏结构（口径差异见文末）。

#### 有哪些数据要显示、显示在哪个组件

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `avatar` | `GET /auth/profile`（`UserInfoVO`，经 `stores/user`） | 头像地址 | 头像行右侧（`image` + `getImageUrl`） | 104rpx 圆角方图；空 / 失败 → `IconSvg name="user"` 灰底；上传中半透明 |
| 2 | `nickname` | 同上 | 昵称 | 昵称行 `input`（**回填**） | 右对齐输入；占位「请输入昵称」 |
| 3 | `username` | 同上 | 学号 / 账号 | 学号行右侧只读文本 `.info-value` | 空值回落 `--` |
| 4 | `bindEmail` | 同上 | 校园邮箱 | 校园邮箱行右侧只读文本（`.info-value-email`） | 唯一来源；空值回落 `--`，长文本可换行不溢出 |
| 5 | `createdAt` | 同上 | 注册时间 | 注册时间行右侧只读文本 | `yyyy-MM-dd HH:mm:ss`；空值回落 `--` |
| 6 | `id` | 同上 | 用户 ID | **不渲染**（本页零消费） | — |
| 7 | 上传结果 `url` | `POST /upload/cloud-image`（经云存储中转） | 头像新地址 | 头像行**就地刷新预览**（本地暂存，未落库） | Toast「上传成功，请点击保存」 |
| 8 | 保存中态 | 端上 `saving` | 提交在途 | `AppButton`（`loading`） | 防重复提交 |
| 9 | 失败提示 | `400` / `401` 响应 `message` | 失败原因 | 无界面（Toast 直透，兜底「保存失败」） | 停留本页并保留已填内容 |

**入参提交**
| 接口 | 字段 |
|---|---|
| `PUT /auth/profile` | `nickname` / `avatar`（**至少一项**；`avatar` 须为站内 `/images/`、`/uploads/` 或 `cloud://`） |
| 头像上传链路 | `wx.cloud.uploadFile` 得 `fileID` → `POST /upload/cloud-image`（入参 `fileId`）→ 取回绝对 URL 暂存，随保存写入 |

**错误码**：`400` 昵称和头像至少填写一项 / 昵称包含敏感内容 / 头像地址不合法｜`401` 未登录｜`4031` 不适用（本页免认证）
**控件类型**：`scroll-view`、`input`、`uni.chooseImage` 单张选图、固定底部 `submit-bar` + `AppButton`

> **⚠️ 与当前代码的差异（待对齐）**
> ① 昵称 `input` 代码为 `maxlength="16"`，本文档正文口径与后端契约均为**20 字**；
> ② 头像更换当前**直接调 `uni.chooseImage`**（系统自带拍照 / 相册选择），**未使用 `ActionSheet`「拍照 / 从相册选择」**；
> ③ 资料回填由全局 `stores/user`（静默登录 / 资料刷新）承担，页面本身**不发 `GET /auth/profile`**，故**无加载失败 `RetryBlock` 态**；
> ④ 保存成功文案为 Toast「已保存」+ 延迟 `navigateBack`，返回「我的主页」由其 `onShow` 刷新信息卡。
