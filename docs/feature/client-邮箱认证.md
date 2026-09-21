# 邮箱认证（A-14）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 发码公开 / 核验需登录**
> 返回：[功能总览](./README.md)

## 干什么

用**学号邮箱**（`{学号}@bjtu.edu.cn`）做身份认证，认证通过后 `verified=true`，解锁 UGC 写操作（写评价、评价点赞）。仅限校内邮箱域名，**无密码、无注册**。

## UI

- **弹层形态**：`AuthSheet`（无独立认证页）。
- 步骤：输入学号 → 点「发送验证码」→ 输入 6 位验证码 → 提交。
- 页面提示：认证后可用哪些能力（写评价等），以及验证码有效期。

## 操作

1. 在 `AuthSheet` 里填**学号**（端上自动推导校园邮箱 `{学号}@bjtu.edu.cn`）。
2. 点发送 → 调 `POST /auth/email-code` → 邮箱收 6 位验证码。
3. 输入验证码 → 调 `POST /auth/verify-email`。
4. 成功 → 返回新 token + 账号信息（`verified=true`、`bindEmail` 已写入）→ 弹层关闭，UGC 入口解锁。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/auth/email-code` | 🔓 公开 | 发送学号邮箱验证码（6 位，10 分钟有效） |
| POST | `/auth/verify-email` | 需登录（当前微信账号） | 校验验证码 → 绑定邮箱 → `verified=1`，返回**新 token** |

## 字段

### 请求 · `POST /auth/email-code`（`EmailCodeReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `username` | string | 否（二选一） | **学号**；填它即自动推导邮箱 `{username}@bjtu.edu.cn`，无需再传 email |
| `email` | string | 否（二选一） | 校园邮箱；不传时由 `username` 推导。格式非法 → 400 |
| `purpose` | string | 否 | 验证码用途，当前**仅支持 `verify`**（学号邮箱认证）；为空时默认 `verify` |

### 响应 · `POST /auth/email-code`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `message` | string | 固定文案「验证码已发送」；**验证码不会在响应中返回**（经邮件下发，服务端存哈希） |

### 限频（防刷）

| 规则 | 中文解释 |
|---|---|
| 同一邮箱 60 秒冷却 | 防止连点重复发码 |
| 同 IP 每分钟 ≤3 次 | 防瞬时并发耗尽 SMTP 配额 |
| 同 IP 每小时 ≤10 次 | 补齐「换邮箱绕过 60s 冷却」的缺口 |

### 请求 · `POST /auth/verify-email`（`VerifyEmailReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `code` | string | **是** | 6 位邮箱验证码（纯空白 → 400）。**绑定邮箱由验证码记录推导**，用户无需再传邮箱 |

### 响应 · `POST /auth/verify-email`（`data` = `LoginResp`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `token` | string | **新的 JWT**（认证后换取；端上需替换本地 token） |
| `userInfo` | object | 账号信息对象（`UserInfoVO`，字段见下方「`userInfo` 字段」表） |

**`userInfo` 字段（`UserInfoVO`，9 个）**：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 用户 ID（数据库自增主键） |
| `username` | string | 学号 / 账号（微信游客为 `wx_` + openid 后 16 位） |
| `nickname` | string | 昵称 |
| `avatar` | string \| null | 头像地址（已转成可访问的绝对 URL） |
| `verified` | boolean | 是否已完成学号邮箱认证：**本接口返回必为 `true`**（认证成功后才走本响应） |
| `bindEmail` | string \| null | 已认证绑定的校园邮箱：**本接口返回已写入的绑定邮箱**（校园邮箱唯一出参来源） |

> **已删除出参（2026-09-21 §7.32，已落地）**：~~`email`~~（恒为 NULL 死字段）、~~`status`~~（端上零消费）、~~`guestShortId`~~（纯派生，改由端上按 `id` 派生）。本接口与 `POST /auth/wechat-login` 共用 `UserInfoVO`，字段集须保持一致。

### 错误码

| code | 含义 | 中文解释 |
|---|---|---|
| 400 | 验证码错误 / 不存在或已过期 | 验证码消费为原子操作（`UPDATE ... WHERE used_at IS NULL`），防并发重复使用 |
| 400 | 账号已被禁用 / 已注销 | 状态异常不允许认证 |
| 401 | 未登录 | 核验接口需先有登录态 |

### 认证时的账号合并规则

| 场景 | 处理 | 中文解释 |
|---|---|---|
| 无历史邮箱绑定 | 直接绑定当前微信账号 | 常见路径 |
| 该邮箱已被**另一个微信**绑定 | **替换绑定**：旧微信 `verified=0`、`bind_email=NULL`；业务数据归属迁移到当前微信 | 避免一人多号数据分裂 |
| 存在历史「邮箱注册账号」 | 归属迁移后把旧账号标记 `deleted` 并释放邮箱唯一键 | 只保留当前微信账号 |

## 数据（落库）

| 表 / 列 | 变化 | 中文解释 |
|---|---|---|
| `email_verification_code` | INSERT / UPDATE | 写入 `code_hash`（哈希存储）、`expires_at`（10 分钟）、`used_at`（消费后置时间，防重复使用） |
| `user.verified` | → 1 | 认证状态（**不进 JWT**，后端实时查库判定） |
| `user.bind_email` | → 学号邮箱 | 认证绑定关系（用于「替换绑定」判定） |
| `user.verified_at` | → 当前时间 | 认证时间 |
