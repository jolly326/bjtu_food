# client/auth — 认证与账号

**归属**：`AuthController`
**通用约定**（响应信封 / 错误码 / 鉴权标记）见 [api/README.md](../README.md)

> 业务规则（游客态语义、认证判据、账号合并策略、匿名化清单）见 [func/client/](../../func/client/)。

---

## POST /auth/wechat-login

**鉴权**：🔓 公开 ｜ **用途**：微信静默登录（无注册页、无密码）

### 请求体 `WechatLoginReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `code` | string | **是** | `wx.login` 返回的**一次性临时凭证**（只能用一次，短时效） |

### 响应 `data` = `LoginVO`
| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `token` | string | 否 | 服务端签发的 JWT；后续需登录接口带 `Authorization: Bearer <token>` |
| `userInfo` | object | 否 | 账号信息（下表） |

**`userInfo` = `UserInfoVO`（4 字段）**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 用户 ID |
| `nickname` | string | 否 | 昵称；新建游客号默认「食客 + ID 尾 4 位」 |
| `avatar` | string | **是** | 头像绝对 URL；无头像为 `null` |
| `bindEmail` | string | **是** | 已认证绑定的校园邮箱；**`null` = 游客态**（非空即已认证） |

> `bindEmail` 是**校园邮箱与认证状态的唯一判据**——端上 `isVerified()` 由它派生，页面不得散写判空。
> **无 `username` 出参**：学号 / 账号标识属服务端内部字段（保留在 `user` 表与 JWT 载荷供日志）。

### 错误码
| code | 条件 |
|---|---|
| 400 | 账号已被禁用 / 已注销；`code` 失效（微信侧校验失败） |

### 备注
- 新 openid 自动建号；`status='deleted'` 的账号**不复用**，同一微信重新登录会创建**全新账号**。
- **无感重登**：端上任意请求收到 401 时清本地态、自动重登并重试原请求一次。

---

## GET /auth/profile

**鉴权**：🔑 需登录（游客态可读）｜ **用途**：读取当前账号资料

### 请求参数
无。

### 响应 `data` = `UserInfoVO`（4 字段）

字段表同 `POST /auth/wechat-login` 的 `userInfo`。

> 启动时若本地已有 token，调本端点**刷新资料**（不换取新 token）。

---

## PUT /auth/profile

**鉴权**：🔑 需登录 ｜ **用途**：更新昵称 / 头像

### 请求体 `ProfileUpdateReq`
| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `nickname` | string | 否 | 最长 **20 字**；变更时过本地敏感词 + 微信内容安检 |
| `avatar` | string | 否 | 仅允许：站内 `/images/`、`/uploads/`、**或 `POST /upload/cloud-image` 返回的 COS 绝对地址**（头像与 UGC 配图同走该链路，见 [upload.md](./upload.md)）|

> **两者至少填一项**，都为空 → 400。
> 采用**按字段局部更新**（未传字段不动），避免整行覆盖导致并发写丢失。

### 响应 `data` = `UserInfoVO`（4 字段）

### 错误码
| code | 条件 |
|---|---|
| 400 | 昵称超长 / 头像地址不合法 / 两项皆空 / 内容安检 `risky` |
| 401 | 未登录 |

### 副作用
| 目标 | 变化 |
|---|---|
| `user.nickname` | UPDATE（局部） |
| `user.avatar` | UPDATE（局部） |

---

## POST /auth/email-code

**鉴权**：🔓 公开 ｜ **用途**：发送学号邮箱验证码

### 请求体 `EmailCodeReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | **是** | **学号**（⚠️ **与库表 `user.username` 同名异义** —— 后者是账号标识 `wx_<openid 尾 16 位>`）；校园邮箱由服务端推导为 `{username}@bjtu.edu.cn`，端上不传 email |

### 响应 `data`
`null`（成功即 `code=200`）。成功文案由 `message` 承担。

> **验证码不在响应中返回**（经邮件下发，服务端仅存 BCrypt 散列）。

### 错误码
| code | 条件 |
|---|---|
| 400 | 学号为空 / 非校园邮箱域名 / 发送过于频繁 / SMTP 未配置或发送失败 |

### 限频
| 维度 | 阈值 | 目的 |
|---|---|---|
| 同一邮箱 | **60 秒**冷却 | 防连点重复发码 |
| 同 IP | 每分钟 **≤3** 次 | 防瞬时并发耗尽 SMTP 配额 |
| 同 IP | 每小时 **≤10** 次 | 补齐「换邮箱绕过 60s 冷却」的缺口 |

---

## POST /auth/verify-email

**鉴权**：🔑 需登录 ｜ **用途**：校验验证码并完成学号邮箱认证

### 请求体 `VerifyEmailReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `code` | string | **是** | 6 位邮箱验证码；**绑定邮箱由验证码记录推导**，端上无需传邮箱 |

### 响应 `data` = `UserInfoVO`（4 字段）

`bindEmail` 已写入为本次认证的邮箱。**字段集与 `POST /auth/wechat-login` 的 `userInfo` 严格一致**。

### 错误码
| code | 条件 |
|---|---|
| 400 | 验证码错误 / 不存在或已过期 / 账号已被禁用或已注销 |
| 401 | 未登录 |

### 备注
- **验证码原子消费**：以 `UPDATE ... WHERE used_at IS NULL` 判定，抢占失败即视为已被并发使用。
- **JWT 不含认证态** —— 后端实时查库判定，认证成功后**无需重发 token**；端上直接用本响应刷新用户态即可。
- 邮箱已被他人绑定时执行**替换绑定 + 归属迁移**（策略见 feature 文档）。

---

## DELETE /auth/account

**鉴权**：🔑 需登录 ｜ **用途**：注销当前账号（匿名化，非物理删除）

### 请求参数
无。注销对象 = JWT 中的当前用户，**端上不传用户标识**。

### 响应 `data`
`null`（成功即 `code=200`）

### 错误码
| code | 条件 |
|---|---|
| 400 | 账号已注销（终态保护，重复调用）/ 账号已被禁用，无法注销 |
| 401 | 未登录 |

### 副作用：匿名化清单

| 对象 | 变化 |
|---|---|
| `user.nickname` | → `'已注销用户'` |
| `user.username` | → `'deleted_{id}'`（释放唯一键占用） |
| `user.avatar` / `email` / `openid` / `bind_email` | → `NULL` |
| `user.status` | → `'deleted'`（持久化兜底，拦截重新登录与 UGC 写） |
| `email_verification_code` | 按 `email` 与 `bind_email` 两批 DELETE |
| `notification` | 按 `user_id` 全删（账号维度过程性数据） |
| Token 黑名单 | 拉黑当前 token + 该 userId（其余设备历史 token 一并失效） |
| `review` / `user_feedback` | **保留** —— 评价与反馈匿名化展示，评分聚合不破坏 |

### ⚠️ 端上必须跳过 401 静默重登

**请求层对 `DELETE /auth/account` 禁用 401 自动重试。**

理由：静默登录会为同一微信**建出新账号**，自动重试将误删新创建的账号。

### 备注
**不可恢复** —— 注销即解绑 `openid`，同一微信重新登录创建全新账号，历史数据不归属新账号。无冷静期、无撤回入口。

---

## 关联：JWT 与认证态

| 项 | 说明 |
|---|---|
| 载荷 | `userId` / `username` / `iat` / `exp`，有效期 **7 天** |
| 载荷可读 | base64url 编码，**非加密** —— 故严禁放入 openid、邮箱、手机号等敏感信息 |
| 认证态 | **不进 JWT**；后端按 `user.bind_email` 非空实时查库判定（`AuthStateUtil`） |
| 角色 | 不进 JWT；学生态 authorities 由过滤器固定授予 |

存储映射见 [schema/user.md](../../schema/user.md)。
