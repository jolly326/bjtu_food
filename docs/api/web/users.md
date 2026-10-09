# web/users — 用户管理

**归属**：`UserAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 治理学生用户账号 —— **启用 / 禁用**、**解绑认证邮箱**、**删除账号（注销）**。管理端只掌控「账号能不能用 / 认证绑定 / 账号存续」，**不代用户改资料**。

---

## GET /admin/users

**用途**：用户列表（分页）

### 请求参数
| 名称 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` / `pageSize` | number | 否 | 1 / 20 | 页码 / 每页条数（上限 **50**） |
| `status` | string | 否 | — | `active` / `disabled` / `deleted`；**不传 = 全部** |
| `keyword` | string | 否 | — | 关键词（**昵称 / 账号 / 绑定邮箱**模糊匹配） |

**排序**：`createdAt DESC`。

### 响应 `data` = `AdminPageResult<UserVO>`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 用户 ID |
| `username` | string | 账号标识（微信建号为 `wx_<openid 尾 16 位>`；注销后为 `deleted_{id}`） |
| `nickname` | string | 昵称（游客默认「食客 + ID 尾 4 位」；注销后为「已注销用户」） |
| `avatar` | string | 头像绝对 URL（无为空串） |
| `status` | string | `active` / `disabled` / `deleted` |
| `wechatBound` | boolean | 是否已绑定微信（**只给布尔，不暴露 `openid` 明文**） |
| `bindEmail` | string | 已认证绑定的校园邮箱（空串 = 未认证；**认证态唯一判据**） |
| `createdAt` | string | 注册时间（`yyyy-MM-dd HH:mm:ss`） |
| `updatedAt` | string | 最近更新时间 |

> 认证状态**不单独出参**：按 `bindEmail` 是否为空**派生**（与小程序端同口径）。

---

## PUT /admin/users/{id}/status

**用途**：**启用 / 禁用**（只改 `status`）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 用户 ID |

### 请求体 `UserStatusReq`
| 字段 | 类型 | 必填 | 校验（服务端 `@Valid`） | 取值域 |
|---|---|---|---|---|
| `status` | string | **是** | `@NotBlank`「状态不能为空」 | **仅允许** `active`（启用）/ `disabled`（禁用）；传 `deleted` 或其它值 → `400`「非法的状态：xxx」 |

> **请求体只含 `status` 一个字段**（`{ "status": "disabled" }`）：启停类端点**全站统一用 `status` 字符串枚举**，不采用 `{ "disabled": true }` 之类布尔形态（同 [web/report-reasons.md](./report-reasons.md#put-adminreport-reasonsidstatus) 的 `ReportReasonStatusReq`）。

### 响应 `data`
`null`。

> **禁用的完整语义（三层，缺一不可）**：
> ① **拒新登录** —— `POST /auth/wechat-login` → `400`「账号已被禁用」；
> ② **已签发 token 即时失效** —— 禁用时拉黑该用户所有 token，恢复 `active` 时解除（JVM 内存态，重启清零）；
> ③ **写操作实时判定** —— 非 `active` 一律 `403`「账号已被禁用」（JWT 载荷不含 status，每次实时查库）。
>
> **不发禁用回执**（通用「处置回执」口径的明确例外）—— 用户被禁用后无法登录 ⇒ 看不到站内通知。

---

## DELETE /admin/users/{id}/email

**用途**：**解绑认证邮箱**（`bind_email` → NULL，账号立即回落游客态）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 用户 ID |

### 响应 `data`
`null`。

> **语义边界**：
> - **只置空 `bind_email` 一列** —— 认证态唯一判据即该列非空，置 NULL 即实时回落游客态（UGC 写实时被拒 `4031`「请先完成学号邮箱认证」）；**不改 `status`、不动 `email`（账号标识）**，登录与已发表内容不受影响。
> - **不代绑新邮箱** —— 重新认证必须由用户本人走邮箱验证码流程（见 [client/auth.md](../client/auth.md)）。
> - **不投递回执** —— 端上 UGC 写被 `4031` 实时拦截并引导重新认证，用户可自助恢复，无需站内通知。
> - **写操作纳入 `UserStateWriteLock`** —— 与启停 / 注销共用同一 userId 临界区（状态写与绑定写不交错）。
> - 🔴 **解绑即吊销该 userId 全部既有 token** —— 认证态由 `bind_email` 派生，解绑改变了账号的能力边界；端上收到 `401` 后走静默登录换发新 token，用户无感。

---

## POST /admin/users/{id}/kick

**用途**：**踢下线**（吊销该用户**全部**已签发 token）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 用户 ID |

### 响应 `data`
`null`。

> **与「禁用」的区别（两者不可互相替代）**：
> - **禁用**（`PUT /{id}/status`）会拦住新登录 + 拦写 ⇒ 用户**完全用不了**，适用于「确认账号失守，先停掉」；
> - **踢下线**不改动账号**任何列**（状态、绑定邮箱、内容归属全部不变），只让已签发的 token 立即失效 ⇒ 用户重新登录（小程序静默登录自动换发）即可继续用。
>
> **典型场景**：确认 token 被滥用、但账号本身无过错时，用踢下线止损而不是禁用账号（见 [`secur/web/审计与告警.md`](../../secur/web/审计与告警.md) §2.2「一键停用账号」的轻量替代）。

---

## DELETE /admin/users/{id}

**用途**：**删除账号**（= 管理端代用户注销；**匿名化，非物理删除**）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 用户 ID |

### 响应 `data`
`null`。

> **语义与本人自注销 [`DELETE /auth/account`](../client/auth.md) 完全同口径**（匿名化清单见该契约，两条入口共用同一服务端实现与 `UserStateWriteLock` 临界区）：
> - `user` 行**匿名化**：昵称 →「已注销用户」、`username` → `deleted_{id}`、`avatar` / `email` / `openid` / `bind_email` → `NULL`、`status` → `deleted`；**不删行**（历史评价 / 反馈归属不丢）。
> - 绑定邮箱的历史验证码随注销删除；站内通知按 `userId` 全删；该 userId 全部已签发 token 拉黑（管理端拿不到对方 token，仅 userId 维度）。
> - **终态保护**：目标已是 `deleted` → `400`「账号已注销」；目标为 `disabled` → `400`「账号已被禁用，无法注销」（与本人口径一致）。
> - **不发回执** —— 账号注销后无法登录 ⇒ 看不到站内通知（同禁用例外口径）。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `status` 缺失 / 空串（「状态不能为空」）｜ `status` 非法（含试图写 `deleted`）｜ 解绑目标未绑定邮箱（「该用户未绑定邮箱」）｜ 重复注销（「账号已注销」）｜ 注销已禁用账号（「账号已被禁用，无法注销」） |
| `4001` | 用户不存在（`PUT` / `DELETE` / `POST {id}/kick` 目标不存在）→ 文案「用户不存在」 |
| `401` | 未带 / token 失效 |

### 备注
- **不做**：改昵称 / 头像 / 邮箱（学生自助，代改会绕过认证流程尤其邮箱绑定；管理端对邮箱只持有「解绑」）。
- **`user` 行永不物理删除** —— 删除账号为匿名化注销，否则历史评价 / 反馈会失去归属。
- **禁用必须「三层齐备」**：只拦新登录 ⇒ 已登录对象仍可写；只拉黑 token ⇒ 重启后又放行；三层叠加才在「内存态黑名单 + 单容器」架构下拿到足够强的效果。
- **启停 / 解绑 / 注销 / 踢下线四条写入口共用同一 userId 临界区**（`UserStateWriteLock`）——「DB 写 + 黑名单写」终态由最后一个临界区决定，不交错。
- **`violationCount` / `mutedUntil` 不出参与不可编辑**：违规累积与限言由系统按阈值自动写入（见 [`secur/client/滥用与内容防护.md`](../../secur/client/滥用与内容防护.md) §4），管理端只通过「启用 / 禁用」对封禁结果做人工干预。
