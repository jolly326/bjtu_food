# 微信静默登录与游客态（A-01）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免登录）
> 返回：[功能总览](./README.md)

## 干什么

打开小程序即用，**无需注册、无登录页**。微信静默换取身份；未完成学号邮箱认证的用户即「游客」——可浏览、可搜索，但**不可写 UGC**（写评价 / 重新评价）。

## UI

- 全局生效，**没有登录页**。
- 未认证用户点击「写评价」「重新评价」时，弹出 `AuthSheet` 认证引导弹层（入口不置灰）。
- 「我的」页用户卡展示昵称 / 头像 / 游客短标识；**游客短标识由 `id` 端上派生**（「食客 + ID 尾 4 位」），不再由接口单独出参（见「字段」节）。

## 操作

1. 小程序启动 → 检查本地是否已有 token：
   - **有** → 直接复用，仅调 `GET /auth/profile` 刷新资料（**不换取新 token**）；
   - **无** → 端上 `wx.login` 拿一次性 code → 调 `POST /auth/wechat-login`。
2. 后端 `code2Session` 换 openid → 查号：无则建游客号（`verified=0`），有则直接返回。
3. 端上保存 token，后续请求自动带 `Authorization: Bearer <token>`。
4. 用户无感知；**游客态是默认已登录状态**，不需要用户做任何操作。

### token 换新的五种时机（除这些以外一律沿用旧 token）

| 场景 | 是否换新 | 说明 |
|---|---|---|
| 冷启动且本地**无** token | ✅ | `wx.login` → `wechat-login` 建号/取号 |
| 冷启动且本地**有** token | ❌ | 仅 `GET /auth/profile` 刷新资料，**沿用旧 token** |
| 任意请求返回 401（含 token 满 7 天） | ✅ | 清本地态 → 自动静默重登 → **自动重试原请求**（用户无感） |
| 学号邮箱认证成功 | ✅ | 服务端返回新 token（认证态变化经实时查库生效，不依赖载荷） |
| 403 且提示需补 openid（用户确认后） | ✅ | 清本地态 → 走完整静默登录 |

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
| `id` | number | 用户 ID（数据库自增主键）。**游客短标识由本字段端上派生**，不再单独出参 |
| `username` | string | 学号 / 账号；**微信游客为 `wx_` + openid 后 16 位**（不含完整 openid，避免泄露） |
| `nickname` | string | 昵称；新建游客号默认「食客 + ID 后 4 位」 |
| `avatar` | string \| null | 头像地址（已转成可访问的绝对 URL） |
| `verified` | boolean | **是否已完成学号邮箱认证**：`true`=已认证（可写 UGC）/ `false`=游客态 |
| `bindEmail` | string \| null | 已认证绑定的校园邮箱（仅存认证关系，未认证为 null）。**校园邮箱的唯一出参来源** |

#### 已删除的冗余出参（2026-09-21 判定并落地）

| 删除字段 | 冗余性质（已核实） | 端上替代 |
|---|---|---|
| `email` | **恒为 NULL 的死字段**：微信体系下 `user.email` 无任何写入点（登录与认证只写 `bind_email`），该列仅为旧密码体系遗留、注销时置 NULL 释放唯一键 | 用 `bindEmail` 单源（端上 `bindEmail \|\| email` 双源兜底一并去掉） |
| `guestShortId` | **纯派生字段**：后端实现即「食客 + `id` 尾 4 位」，而 `id` 本就出参；建号时默认 `nickname` 也等于它 | 端上由 `id` 派生（无 `id` 时回退既有的本地游客 ID） |
| `status` | **端上零消费**：禁用 / 注销在登录侧（400）与 UGC 写操作侧（403）拦截，端上不做任何分支渲染 | 无需替代（管理端 `UserVO.status` 不受影响） |

### 附：JWT 载荷（排障用，非业务出参）

| 字段名 | 中文解释 |
|---|---|
| `userId` | 用户 ID（后端鉴权唯一依据，**禁止信任前端传来的 userId**） |
| `username` | 账号，仅用于日志/展示 |
| `iat` / `exp` | 签发时间 / 过期时间 |

⚠️ **`verified` 不进 JWT**（后端按 `user.verified` 实时查库判定）；**`role` 字段已随 `user.role` 列退役**，JWT 不含角色。

⚠️ **载荷是明文（base64url 编码），不是密文**：任何人拿到 token 都能解出上述字段——所以载荷里**不得放任何敏感信息**（openid、邮箱、手机号均不在其中）。签名只保证「改不了」，不保证「看不见」。

## 数据（落库）

**只有一张 `user` 表**（新 openid 时 `INSERT` 一行；老 openid 时 `SELECT` 该行），建号流程对**同一行**写两次：

| 对象 | 操作 | 中文解释 |
|---|---|---|
| `user` 表（建号新行） | INSERT | 写入 `openid`（唯一）、`username`（`wx_`+尾16位）、`nickname`（**先写占位值「食客新友」**）、`status='active'`、`verified=0` |
| 同一行的 `nickname` 列 | UPDATE（建号流程第二步） | 最终昵称依赖建号后的自增 `id`，故 INSERT 拿到 `id` 后再回填为「食客 + ID 后 4 位」 |

> **为什么是两步**：最终昵称含自增主键尾 4 位（`id` 由数据库在 INSERT 时生成），而 `nickname` 列为 `NOT NULL`、INSERT 时必须给值 → 只能「先插占位值，拿到 `id` 后 UPDATE 回填」。
> **原子性（2026-09-21 已修复）**：建号两步写入现处于**同一事务**内——建号已抽为独立 Bean `UserService.createWechatGuest`（`@Transactional`，`AuthServiceImpl` 不再持有建号私有方法）。之所以必须抽到独立 Bean：Spring 事务基于代理，同类方法内部自调用不生效；同时这样可让远程 `code2Session` 调用留在事务之外。回填前另有「昵称是否仍为占位值」判等，避免覆盖任何已存在的用户昵称。若回填失败，事务回滚，不会留下昵称为占位值的账号。

**边界**：账号被 `disabled` → 400「账号已被禁用」；已 `deleted` → 400「账号已注销」。

## 出参精简落地状态（2026-09-21 已落地）

> 本节与 `project_spec.md` §7.32 决议、`api-design.md` §8 差异登记册一致（该行已置「已对齐」）；落地由 change `auth-response-slimming` 承载。

| 项 | 服务端（已落地） | 客户端（已落地） |
|---|---|---|
| 删 `email` | `UserInfoVO` 去 `email`；`AuthServiceImpl.toUserInfo` 与 `buildProfileMap` **两处承载结构**同步去除 | `types/user.ts` 去 `email`；`api/user.ts` 去 `email` 映射与账号信息侧的邮箱推导（认证表单发码所需推导保留）；`pages/profile` / `pages/mine` 改读 `bindEmail` 单源 |
| 删 `guestShortId` | `UserInfoVO` 与**管理端 `UserVO`** 均去 `guestShortId`（判据不因端而异），`UserServiceImpl.toVO` 同步；`buildGuestShortId` 无出参消费方，规则仅保留为**建号默认昵称**的私有实现 | 小程序 `pages/mine` 由 `id` 派生「食客 + ID 尾 4 位」，`id` 不可得时回退本地游客标识；**web** `types/index.ts` / `api/adapter.ts` 去声明与映射，`UserView` 的过滤与昵称兜底展示改由 `id` 现算（局部函数） |
| 删 `status` | `UserInfoVO` 去 `status`（管理端 `UserVO.status` 保留——管理端需展示与操作账号状态） | `types/user.ts` 去 `status`（端上无消费点） |

**门禁**：`mvn -q compile -DskipTests` / `client npm run type-check` / `web npm run build` 均 EXIT 0（2026-09-21）。**运行时冒烟**（四条链路字段集对照）仍需起服务后由用户执行，见 change `auth-response-slimming` 任务 5.2。
