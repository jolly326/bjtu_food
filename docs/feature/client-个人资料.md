# 个人资料（A-13）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（游客态也可读 / 可改）
> 返回：[功能总览](./README.md)

## 干什么

修改本人的**昵称**与**头像**，并读取当前账号资料（含认证状态）。头像走上传链路取回地址后再保存。

## UI

- 入口：「我的」页 → 用户卡 → `pages/profile/index`。
- 页面元素：头像（可点击更换）、昵称（可编辑输入）、游客短标识 / 认证状态展示、「保存」按钮。
- 头像更换走图片选择 → 压缩 → 上传 → 回填 URL。

## 操作

1. 进页 → 调 `GET /auth/profile` 回填昵称、头像、认证状态。
2. 点头像 → 选图 → 压缩 → 上传 → 回填新地址。
3. 改昵称 → 输入（≤20 字）。
4. 点保存 → `PUT /auth/profile`（**昵称与头像至少填一项**）→ 成功后局部刷新。
5. 昵称命中敏感内容或微信机检 `risky` → 400 提示，不保存。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/auth/profile` | 需登录（游客态可读） | 读取当前账号资料 |
| PUT | `/auth/profile` | 需登录 | 更新昵称 / 头像（至少一项） |

## 字段

### 请求 · `PUT /auth/profile`（`ProfileUpdateReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `nickname` | string | 否 | 新昵称，最长 **20 字**；变更时本地敏感词过滤 + 微信 `msgSecCheck` v2（`scene=1` 资料场景），`risky` → 400 |
| `avatar` | string | 否 | 头像地址，**仅允许站内 `/images/`、`/uploads/` 或微信云存储 `cloud://`**（不合法 → 400「头像地址不合法」） |

> **`nickname` 与 `avatar` 至少填一项**，两者都空 → 400「昵称和头像至少填写一项」。
> 更新采用**按字段局部更新**（未传字段不动），避免整行覆盖导致并发写丢失。

### 响应 · `GET /auth/profile` 与 `PUT /auth/profile`（同结构）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 用户 ID |
| `username` | string | 学号 / 账号（游客为 `wx_` + openid 后 16 位） |
| `email` | string \| null | 校园邮箱（游客为 null） |
| `nickname` | string | 昵称 |
| `avatar` | string \| null | 头像地址（已转可访问的绝对 URL） |
| `status` | string | 账号状态：`active` / `disabled` / `deleted` |
| `verified` | boolean | 是否已完成学号邮箱认证（true 才可写 UGC） |
| `bindEmail` | string \| null | 已认证绑定的校园邮箱 |
| `guestShortId` | string | 游客短标识（「食客 + ID 后 4 位」） |

> **不含 `openid`**（隐私：不下发微信标识）；**不含 `role`**（该列已退役）。

## 数据（落库）

| 表 / 列 | 变化 | 中文解释 |
|---|---|---|
| `user.nickname` | UPDATE（局部） | 新昵称（防敏感词 + 微信内容安检） |
| `user.avatar` | UPDATE（局部） | 新头像地址（仅站内路径或云存储） |

## 答疑

### Q：昵称和头像也需要微信的安全验证

**A：核实结论：昵称已经做了；头像只做了「地址合法性」校验，没送内容安检——建议补上，但要先定口径。**

现状：`PUT /auth/profile` 里**昵称变更会过 `msgSecCheck` v2（`scene=1` 资料场景）**，`risky` 直接 400；**头像只走 `imageUrlUtil.isValidAvatar()`**（仅允许站内 `/images/`、`/uploads/`、`cloud://`），**不送 `imgSecCheck`**。原先没做的原因：小程序头像走微信自身的头像/云存储链路，不是自由选图上传。

**但这是真实缺口，建议补**（方案二选一）：
① **推荐：后端补送检**——`cloud://` 头像与 UGC 配图同链路，保存前调用与 `/upload/images` 同一套 `imgSecCheck`（复用 `stable_token` 缓存，不要新开 token 逻辑）；若为 H5 `multipart` 上传，则在 `POST /upload/image` 加 `imgSecCheck`。改动集中在后端一处，端上零改动。
② 若走端上限制（只允许微信头像），则需确认微信头像本身已在平台侧合规——风险是「拿到 URL 后仍可被替换成任意 cloud 文件」。

另需你定一条：**头像违规时是「整单保存失败并提示」还是「只丢头像、其余保存」**——我建议**整单失败 + 明确提示**（与昵称同口径，避免用户以为换上了）。

这条属 `spec §5.a` 合规清单增补，**须技术负责人先登记再改代码**。
