# 微信静默登录与游客态（A-01）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免登录）
> 返回：[功能总览](./README.md)

## 干什么

打开小程序即用，**无需注册、无登录页**。微信静默换取身份；未完成学号邮箱认证的用户即「游客」——可浏览、可搜索，但**不可写 UGC**（写评价 / 重新评价）。

## UI

> 📐 页面 UI 设计稿已拆出 → [client-微信静默登录与游客态.md（docs/ui）](../ui/client-微信静默登录与游客态.md)（2026-09-22 拆分；**UI 口径以该文件为唯一真源**）

## 操作

1. 小程序启动 → 检查本地是否已有 token：
   - **有** → 直接复用，仅调 `GET /auth/profile` 刷新资料（**不换取新 token**）；
   - **无** → 端上 `wx.login` 拿一次性 code → 调 `POST /auth/wechat-login`。
2. 后端 `code2Session` 换 openid → 查号：无则建游客号（**游客态 = `bind_email` 为 NULL**），有则直接返回。
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
| `bindEmail` | string \| null | 已认证绑定的校园邮箱（仅存认证关系，游客态为 null）。**校园邮箱的唯一出参来源，同时是认证状态的唯一判据**——非空即已认证（可写 UGC），null 即游客态 |

#### 已删除的冗余出参（2026-09-21 / 2026-09-22 判定并落地）

| 删除字段 | 冗余性质（已核实） | 端上替代 |
|---|---|---|
| `verified` | **派生冗余**：`bindEmail` 非空的布尔镜像（服务端历史写入路径「邮箱认证 / 释放绑定替换 / 注销」三处恒成对写，无独立语义）；DB 列 `user.verified` / `user.verified_at` 已同批 DROP | 端上 `useUserStore().isVerified()` 单点派生（`!!bindEmail`），页面 / 组件不得散写判空 |
| `email` | **恒为 NULL 的死字段**：微信体系下 `user.email` 无任何写入点（登录与认证只写 `bind_email`），该列仅为旧密码体系遗留、注销时置 NULL 释放唯一键 | 用 `bindEmail` 单源（端上 `bindEmail \|\| email` 双源兜底一并去掉） |
| `guestShortId` | **纯派生字段**：后端实现即「食客 + `id` 尾 4 位」，而 `id` 本就出参；建号时默认 `nickname` 也等于它 | 端上由 `id` 派生（无 `id` 时回退既有的本地游客 ID） |
| `status` | **端上零消费**：禁用 / 注销在登录侧（400）与 UGC 写操作侧（403）拦截，端上不做任何分支渲染 | 无需替代（管理端 `UserVO.status` 不受影响） |

### 附：JWT 载荷（排障用，非业务出参）

| 字段名 | 中文解释 |
|---|---|
| `userId` | 用户 ID（后端鉴权唯一依据，**禁止信任前端传来的 userId**） |
| `username` | 账号，仅用于日志/展示 |
| `iat` / `exp` | 签发时间 / 过期时间 |

⚠️ **认证态不进 JWT**（后端按 `user.bind_email` 非空实时查库判定，判据唯一真源 `AuthStateUtil`）；**`role` 字段已随 `user.role` 列退役**，JWT 不含角色。

⚠️ **载荷是明文（base64url 编码），不是密文**：任何人拿到 token 都能解出上述字段——所以载荷里**不得放任何敏感信息**（openid、邮箱、手机号均不在其中）。签名只保证「改不了」，不保证「看不见」。

## 数据（落库）

**只有一张 `user` 表**（新 openid 时 `INSERT` 一行；老 openid 时 `SELECT` 该行），建号流程对**同一行**写两次：

| 对象 | 操作 | 中文解释 |
|---|---|---|
| `user` 表（建号新行） | INSERT | 写入 `openid`（唯一）、`username`（`wx_`+尾16位）、`nickname`（**先写占位值「食客新友」**）、`status='active'`（游客态无需额外标记：`bind_email` 保持 NULL 即游客） |
| 同一行的 `nickname` 列 | UPDATE（建号流程第二步） | 最终昵称依赖建号后的自增 `id`，故 INSERT 拿到 `id` 后再回填为「食客 + ID 后 4 位」 |

> **为什么是两步**：最终昵称含自增主键尾 4 位（`id` 由数据库在 INSERT 时生成），而 `nickname` 列为 `NOT NULL`、INSERT 时必须给值 → 只能「先插占位值，拿到 `id` 后 UPDATE 回填」。
> **原子性（2026-09-21 已修复）**：建号两步写入现处于**同一事务**内——建号已抽为独立 Bean `UserService.createWechatGuest`（`@Transactional`，`AuthServiceImpl` 不再持有建号私有方法）。之所以必须抽到独立 Bean：Spring 事务基于代理，同类方法内部自调用不生效；同时这样可让远程 `code2Session` 调用留在事务之外。回填前另有「昵称是否仍为占位值」判等，避免覆盖任何已存在的用户昵称。若回填失败，事务回滚，不会留下昵称为占位值的账号。

**边界**：账号被 `disabled` → 400「账号已被禁用」；已 `deleted` → 400「账号已注销」。

## 与当前代码的差异

**无（文档与代码一致）**——截至 2026-09-22，本文档六段所述口径（含 5 字段出参、`bindEmail` 单源认证判据、`user.verified` / `user.verified_at` 两列退役）已在三端落地。

> 本节按 2026-09-22 定稿规则设立：**正文只写最终设计形态，与现有代码的差异一律写在本节**（含「已拍板未落地」条目）；差异清零时保留标题并写「无」，使「本节为空」本身成为可读信息。
> 最近一次清零：2026-09-22（change `auth-verified-field-removal`——`verified` 出参 + `user.verified` / `user.verified_at` 两列全链下线，认证判据统一为 `bindEmail` 非空；`email` / `status` / `guestShortId` 的收敛此前已落地）。
