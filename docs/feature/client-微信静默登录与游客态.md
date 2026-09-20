# 微信静默登录与游客态（A-01）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免登录）
> 返回：[功能总览](./README.md)

## 干什么

打开小程序即用，**无需注册、无登录页**。微信静默换取身份；未完成学号邮箱认证的用户即「游客」——可浏览、可搜索，但**不可写 UGC**（写评价 / 评价点赞）。

## UI

- 全局生效，**没有登录页**。
- 未认证用户点击「写评价」「有用」时，弹出 `AuthSheet` 认证引导弹层（入口不置灰）。
- 「我的」页用户卡展示昵称 / 头像 / 游客短标识。

## 操作

1. 小程序启动 → 端上 `wx.login` 拿临时 code → 自动调 `POST /auth/wechat-login`。
2. 后端 `code2Session` 换 openid → 查号：无则建游客号（`verified=0`），有则直接返回。
3. 端上保存 JWT，后续请求自动带 `Authorization: Bearer <token>`。
4. 用户无感知；**游客态是默认已登录状态**，不需要用户做任何操作。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/auth/wechat-login` | 🔓 公开 | 微信静默登录：`wx.login` 的 code 换 openid，新 openid 自动建号，返回 token + 账号信息 |

## 字段

### 请求（`WechatLoginReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `code` | string | **是** | 微信 `wx.login` 返回的**一次性临时凭证**；后端用它调 `code2Session` 换 openid（只能用一次，短时效） |

### 响应（`data`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `token` | string | 服务端签发的登录凭证（JWT，有效期 **7 天**）；后续需登录接口在请求头带 `Authorization: Bearer <token>` |
| `userInfo` | object | 账号信息对象，结构见下表 |

### 响应 · `userInfo`（`UserInfoVO`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 用户 ID（数据库自增主键） |
| `username` | string | 学号 / 账号；**微信游客为 `wx_` + openid 后 16 位**（不含完整 openid，避免泄露） |
| `email` | string \| null | 校园邮箱；微信游客为 `null` |
| `nickname` | string | 昵称；新建游客号默认「食客 + ID 后 4 位」 |
| `avatar` | string \| null | 头像地址（已转成可访问的绝对 URL） |
| `status` | string | 账号状态：`active`=正常 / `disabled`=已禁用 / `deleted`=已注销 |
| `verified` | boolean | **是否已完成学号邮箱认证**：`true`=已认证（可写 UGC）/ `false`=游客态 |
| `bindEmail` | string \| null | 已认证绑定的校园邮箱（仅存认证关系，未认证为 null） |
| `guestShortId` | string | 游客短标识：「食客 + ID 后 4 位」，端上展示用 |

### 附：JWT 载荷（排障用，非业务出参）

| 字段名 | 中文解释 |
|---|---|
| `userId` | 用户 ID（后端鉴权唯一依据，**禁止信任前端传来的 userId**） |
| `username` | 账号，仅用于日志/展示 |
| `iat` / `exp` | 签发时间 / 过期时间 |

⚠️ **`verified` 不进 JWT**（后端按 `user.verified` 实时查库判定）；**`role` 字段已随 `user.role` 列退役**，JWT 不含角色。

## 数据（落库）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `user` | INSERT（新 openid）/ SELECT（老 openid） | 写入 `openid`（唯一）、`username`（`wx_`+尾16位）、`nickname`、`status='active'`、`verified=0` |
| `user.nickname` | 建号后 UPDATE | 用自增 ID 回填为「食客 + ID 后 4 位」 |

**边界**：账号被 `disabled` → 400「账号已被禁用」；已 `deleted` → 400「账号已注销」。

## 答疑

### Q：JWT 是什么？为什么要用 openid 换 JWT？是否有冗余设计？

**A：不冗余，两者职责不同，建议保留现状。**

① JWT（JSON Web Token）是服务端用密钥签名的**无状态凭证**（`头.载荷.签名` 三段），载荷含 `userId`/`username`；服务端只验签名、不查 session。小程序没有 Cookie/session 载体，JWT 是最合适方案，也省掉「服务端存会话 + 每请求查库」。

② **openid 不能直接当接口凭证**：它是微信侧长期不变的明文标识——泄露即可被永久冒充，且无法吊销。「谁是谁」（身份标识）与「本次会话已通过登录校验」（短期证明）必须分开。所以链路是：`wx.login` 拿一次性 code → 后端 `code2Session`（appid/secret 只在服务端，绝不下发）→ 得 openid → 查/建 `user` → 签发 JWT 下发。

③ 真正可讨论的冗余只有两处，都不建议本期动：`username` 放进 JWT 只服务日志/展示（可省，但改它是契约变更）；注销/禁用靠 `TokenBlacklist`（内存）revoke，**重启即失效**（代码已用 `user.status` 做持久化兜底，属已登记的已知取舍）。
