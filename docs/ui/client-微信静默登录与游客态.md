# 微信静默登录与游客态 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 归属功能文档：[client-微信静默登录与游客态.md](../feature/client-微信静默登录与游客态.md)
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。
> 落点：**无独立登录页** —— 全局登录态（微信打开即静默登录为游客态，**无登录页 / 无登录按钮 / 无注册密码体系**）；身份认证承载于**独立认证页 `pages/auth/index`**（由各需认证入口跳转进入，见 [client-邮箱认证.md](./client-邮箱认证.md)）。


- 全局生效，**没有登录页**。
- 未认证用户点击「写评价」「重新评价」时，跳转身份认证页 `pages/auth/index`（入口不置灰）。
- 「我的」页用户卡展示昵称 / 头像 / 游客短标识；**游客短标识由 `id` 端上派生**（「食客 + ID 尾 4 位」），非接口出参（见「字段」节）。

## 接口数据字段（UI 精修用）

**页面**：无独立页面（**全局登录态**，存于 `stores/user`，由 `App.vue` 的 onLaunch 触发）

### 组件清单（本界面需要哪些组件）

| # | 组件 / 载体 | 来源 | 在本功能里做什么 |
|---|---|---|---|
| 1 | `App.vue`（onLaunch） | `client/src/App.vue` | 启动即调 `useUserStore().silentLogin()`（静默登录唯一入口） |
| 2 | `stores/user.ts` | `client/src/stores/user.ts` | 持有 `token` + `userInfo`；`isVerified()` 由 `bindEmail` 非空**单点派生**；`forceLogout()` 清态 |
| 3 | `api/http.ts` | `client/src/api/http.ts` | 401 → 清本地态 + 自动静默重登 + **自动重试原请求一次**（并发去重，用户无感） |
| 4 | `api/user.ts` | `client/src/api/user.ts` | `wechat-login` / `profile` / `email-code` / `verify-email` / `deleteAccount` 调用封装 |
| 5 | `utils/guest.ts` | `client/src/utils/guest.ts` | `id` 不可得时回退本地游客 ID（`getLocalGuestLabel()`），保证短标识不空白 |
| 6 | 认证引导跳转 | `stores/auth` + `pages/auth/index` | 需认证动作（写评价 / 删评价）与 `4031` 分流 → 跳**独立认证页**（入口**不置灰**） |
| 7 | 用户卡（呈现位，非本功能组件） | `pages/mine/index.vue` / `pages/my-reviews/index.vue` 内联 | 登录态与游客态**唯一可见落点**（昵称 / 头像 / 副行） |

> 本功能**无专属界面 / 无登录页 / 无登录按钮 / 无注册密码体系**：启动即已登录（游客态亦已登录），故不新增任何组件。

### 有哪些数据要显示、显示在哪个组件

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `token` | `POST /auth/wechat-login`（`LoginVO.token`） | JWT 凭证（有效期 7 天） | **无界面**（写本地 storage，后续请求头 `Authorization: Bearer`） | 零可见 UI |
| 2 | `userInfo.id` | 同上 / `GET /auth/profile` | 用户 ID | 「我的」页与「我的主页」用户卡 | 端上**派生**游客短标识「食客 + id 尾 4 位」（id 不可得回退本地游客 ID） |
| 3 | `userInfo.nickname` | 同上 | 昵称 | 两页用户卡主标题 | 空值时游客显「游客」、认证态显「食客」 |
| 4 | `userInfo.avatar` | 同上 | 头像地址 | 两页用户卡头像位（`ImageFallback`；空 / 失败 → `IconSvg name="user"` 灰底） | 120rpx 圆形 |
| 5 | `userInfo.username` | 同上 | 学号（游客为 `wx_` + openid 尾 16 位） | 用户卡副行（**仅认证态**：我的页 / 我的主页） | 次级灰小字，只读 |
| 6 | `userInfo.bindEmail` | 同上 | 已绑定校园邮箱 | 用户卡**副行（认证态）**+ 认证条纹 / 徽章显隐 | **认证判据唯一来源**：非空 = 已认证；`null`/空 = 游客态 |
| 7 | `userInfo.createdAt` | 同上 | 注册时间 | **本功能零消费**；仅「个人信息编辑」页只读展示 | — |
| 8 | 401 处理结果 | `api/http.ts` | 重登结果 | 无界面（清 token + Toast + 重试原请求） | 用户无感；重登失败 → 清态回落游客态 |

**入参提交**：`POST /auth/wechat-login` → `code`（`wx.login` 换得的一次性凭证；端上**不传 openid**）
**错误码**：`400` 微信登录未配置 / 凭证无效或已过期 / 账号已被禁用 / 账号已注销 / 创建账号失败｜`500` 微信登录服务暂不可用
**控件类型**：无（纯 store + 请求层；本功能不渲染任何控件）
