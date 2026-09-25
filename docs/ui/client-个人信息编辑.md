# 个人信息编辑 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 页面：`pages/profile/index`（分包 `pages/profile/`）
> 归属功能文档：[client-个人资料.md](../feature/client-个人资料.md)
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。


- 入口：**「我的主页」用户信息卡右侧「编辑个人信息」**（游客态与认证态同达，无认证拦截）。宿主主页见 [client-我的主页.md](./client-我的主页.md)。
- 页面形态：**独立页面**，`AppHeader` 页头标题「**个人信息**」+ 可滚动表单区；返回箭头 = `navigateBack`。二级页，**无 TabBar**。
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

**页面**：`pages/profile/index`

**出参消费**
| 接口 | 字段 | 端上用途 |
|---|---|---|
| `GET /auth/profile`（`UserInfoVO`） | `avatar` / `nickname` | 可编辑两行的回填 |
| | `username` / `bindEmail` / `createdAt` | 学号 / 校园邮箱 / 注册时间三只读行 |
| | `id` | 本页不展示（不参与渲染） |

**入参提交**
| 接口 | 字段 |
|---|---|
| `PUT /auth/profile` | `nickname` / `avatar`（**至少一项**；`avatar` 须为站内 `/images/`、`/uploads/` 或 `cloud://`） |
| 头像上传链路 | `wx.cloud.uploadFile` 得 `fileID` → `POST /upload/cloud-image`（`fileId`）→ 取回绝对 URL 暂存，随保存写入 |

**错误码**：`400` 昵称和头像至少填写一项 / 昵称包含敏感内容 / 头像地址不合法｜`401` 未登录｜`4031` 不适用（本页免认证）

**UI 组件**：公共 `AppHeader` / `AppButton` / `IconSvg`
**控件类型**：`scroll-view`、`input`（`maxlength=20` 昵称）、`uni.chooseImage` 选图、`ActionSheet`（拍照 / 相册）、固定底部 `submit-bar`、`RetryBlock` 失败态
