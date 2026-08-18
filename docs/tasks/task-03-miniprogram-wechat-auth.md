# task-03 · 小程序：微信静默登录接入 + 游客态 + 认证页/引导 + 社区鉴权提示 + 「我的」页绑定邮箱展示

> 负责角色：**小程序开发工程师**。关联权威条款：`docs/project_spec.md` §0.1、§0.3「认证与鉴权」、§2.1、§4（UI 规范）、**§5.y 认证与鉴权**、§5.x。
> **依赖：task-01**（后端 `wechat-login` / `verify-email` / `email-code` 接口 + verified 鉴权）。

## 背景
用户已拍板：微信打开小程序自动静默登录为游客态（`verified=false`）；**无登录页 / 登录按钮 / 注册 / 密码体系**；邮箱验证码认证后 `verified=true` 解锁社区写操作；游客可浏览 + 提交基础反馈；需认证功能**入口不置灰**、点击弹认证引导；「我的」页展示已绑定邮箱（`bind_email`）；昵称保持「食客+ID 尾号」。详见 spec §5.y。

## 一、核心概念迁移（关键）
- 现有前端基于「未登录（无 token）→ 登录」模型（`userStore.isLoggedIn()` / `loginByPassword` / `loginByEmailCode` / `register`）。
- 新体系：**用户默认已登录（游客态 `verified=false`）**。「是否需要认证」判断从 `isLoggedIn()`（有 token）改为 **`isVerified()`（`userInfo.verified===true`）**。
- `AuthSheet` 从「登录表单」重构为「**学号邮箱认证弹层**」（`AuthForm` 改为认证表单：学号 + 验证码，无密码/无注册切换）。
- 「退出登录」语义改为「清除本地登录态」（微信重新打开仍静默登录），或按需保留。

## 二、改动清单

### 2.1 API 层（`client/src/api/user.ts` + `types/user.ts`）
- 移除 `loginByPassword` / `loginByEmailCode` / `register` / `resetPassword`（账号密码体系废弃）。
- `sendEmailCode` 的 `purpose` 类型改为 `'verify'`。
- 新增 `wechatLogin(code)`：`POST /auth/wechat-login` → 返回 `{ token, userInfo }`。
- 新增 `verifyEmail(code)`：`POST /auth/verify-email` → 返回更新后 `{ token, userInfo }`。
- `toUserInfo` 增加 `verified` / `bindEmail` / `guestShortId` 字段映射（`UserInfo` 扩展）。
- `UserInfo` 接口新增：`verified: boolean`、`bindEmail?: string`、`guestShortId?: string`（见 spec §5.y.2/5.y.4）。

### 2.2 静默登录接入（`client/src/stores/user.ts` + `App.vue` / 启动逻辑）
- `App.vue`（或 `main.ts` / 首页 onLoad）启动时：若本地有 token → 校验 / 刷新资料；若无 token → 调 `wx.login` 拿 `code` → `wechatLogin(code)` 静默登录（游客态）。
- 静默登录失败（401）→ `uni.$emit('auth:unauthorized')` 后**重新触发静默登录**（见 spec §5.x 401 处理）。
- `userStore` 语义重构：
  - `isLoggedIn()` 保留（有无 token / userInfo），新增 **`isVerified()`**（`userInfo?.verified === true`）。
  - `requireAuth(action)` 守卫从「未登录才弹」改为「**未认证（`verified=false`）才弹认证引导**」；已认证直接执行 action。
  - 移除密码登录 / 注册 actions；`fetchStats` 等基于登录态的调用改为基于 `isVerified()` 或登录态。

### 2.3 认证弹层重构（`client/src/components/AuthSheet.vue` + `AuthForm.vue` + `stores/authSheet.ts`）
- `AuthSheet` 标题由「登录认证」改为「学号邮箱认证」；正文由 `AuthForm` 的认证表单承载（学号 + 验证码，`purpose=verify`，无密码字段、无「去注册」切换）。
- `AuthForm` 提交走 `verifyEmail(code)`（而非登录）；成功后 `userStore` 更新 `verified=true` 并关闭弹层、执行待办（`runPending`）。
- `authSheet.requireAuth(action)` 语义不变（认证成功后执行原动作），但触发条件是「未认证」而非「未登录」。
- 认证表单需支持「获取验证码」发码（`sendEmailCode`，`purpose=verify`），60s 冷却（现有机制保留）。

### 2.4 独立认证页 `pages/profile/verify/index`（新增路由，见 spec §2.1.1）
- 完整页形态的学号邮箱认证（`AuthSheet` 弹层的全屏版），供「我的」页认证引导卡片进入。
- 复用认证表单组件（与 `AuthForm` 抽取同一认证表单逻辑），不另造。

### 2.5 「我的」页（`client/src/pages/profile/index.vue`）
- 游客态（`verified=false`）展示**认证引导卡片**（提示「认证解锁发布/评价/点赞」），点击进认证弹层或认证页。
- 已认证展示：昵称（食客+ID 尾号）、**已绑定邮箱 `bind_email`**、认证状态标识。
- 「退出登录」语义改为「清除本地登录态」（若保留）。
- 需认证功能入口**不置灰**，点击统一走 `requireAuth()`（弹认证引导）。

### 2.6 社区操作鉴权提示（发布 / 评价 / 点赞 / 评论 / 动态等入口）
- 所有社区写操作入口（`publish-moment`、`review`、点赞、评论、动态发布等）改为：点击时若 `verified=false` → 弹 `AuthSheet` 认证引导，认证成功后自动继续原动作；不置灰。
- 后端 403（未认证）时前端统一提示「请先完成学号邮箱认证」并弹认证引导。

### 2.7 游客权限保障
- 游客可浏览全部公开数据 + 提交 `POST /feedback`（意见反馈页不做认证守卫，维持现状可提交）。
- `feedback` 页不弹认证、游客可提交（spec §5.y.4）。

## 三、验收标准
1. 小程序启动无登录页 / 登录按钮：自动静默登录为游客态（`verified=false`），`App.vue` 无 token 时自动 `wechatLogin`。
2. `userStore` 有 `isVerified()`；社区写操作入口未认证时弹认证引导（`AuthSheet`）且不置灰。
3. 认证弹层 / 认证页为「学号邮箱 + 验证码」（`purpose=verify`），无密码 / 无注册切换；发码 60s 冷却。
4. 认证成功后 `verified=true`、关闭弹层并执行认证前待办动作；「我的」页展示已绑定邮箱。
5. 游客可浏览全部公开数据 + 提交意见反馈（`POST /feedback`），`feedback` 页无认证守卫。
6. 401（token 失效）→ 清 token + Toast + 重新静默登录；后端 403（未认证）→ 提示 + 弹认证引导。
7. 移除所有 `loginByPassword` / `register` / `resetPassword` 调用；`sendEmailCode` 仅 `purpose=verify`。
8. UI 遵循 §4（spring 动效、按压 `scale(var(--press-scale))`、图标走 `IconSvg`、底部弹层安全区避让、reduced-motion 降级）；不引入未登记图标 / 裸 hex / 裸 scale。
9. 复用 task 基础组件（`AppButton` / `IconSvg` 等），不新造。

## 四、依赖
- 依赖 task-01（后端 wechat-login / verify-email / email-code 接口 + verified 鉴权）。
- 无被依赖项。
