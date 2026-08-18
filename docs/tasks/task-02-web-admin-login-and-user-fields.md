# task-02 · 后端 + Web：管理后台登录维持（方案 C）+ 用户列表展示新字段

> 负责角色：**后端开发工程师（登录部分）+ Web 开发工程师（用户列表部分）**。
> 关联权威条款：`docs/project_spec.md` §0.1、§1、§5.y.5「管理后台登录（方案 C）」、§5.x。
> **依赖：task-01**（User 表新增 `openid/unionid/verified/bind_email` 等字段）。

## 背景
用户已拍板管理后台登录采用**方案 C**：暂留管理员账号密码（BCrypt + JWT 不变）；**放弃**微信开放平台扫码（A，收费）与复用小程序码（B，体验差）。小程序端废除密码登录，**但管理后台仍是账号密码登录**，二者解耦。同时 Web 用户列表需展示微信登录体系落地后的新字段（`verified` / `openid` / 绑定邮箱等）。

## 一、后端：管理后台登录维持（方案 C）

### 1.1 现状确认
- 管理后台沿用「管理员账号密码 + BCrypt + JWT」；`/admin/**` 仅 `ADMIN`（含 `SUPER_ADMIN`）。
- task-01 移除了对 STUDENT 生效的 `/auth/login` 等；**管理后台登录接口不得被误删**。

### 1.2 后端动作
- 确认管理后台登录端点（管理员专用，如 `/auth/admin/login` 或现有 admin 登录路由）保留，仅校验 `ADMIN` / `SUPER_ADMIN` 角色。
- 若 task-01 统一调整了 `/auth/*` 白名单，须确保管理员登录接口仍可公开访问（登录前无 token）。
- `user.password` / BCrypt 校验逻辑**仅用于管理员**；学生侧不校验密码。
- 若存在复用关系（学生登录与管理员登录共用 DTO/Service），拆分以保证「学生无密码、管理员有密码」不冲突。

## 二、Web：用户列表展示新字段（`web/src/views/user/UserView.vue` 等）

### 2.1 契约
- 用户列表接口（现有 `/admin/**` 用户管理）在返回中新增字段（经 `api/adapter.ts` 转换，Web View 层不直接处理 `snake_case`）：
  - `verified`（是否已认证，0/1）→ 映射为「已认证 / 未认证」状态标签。
  - `openid`（微信 openid，用于展示微信绑定关系；可脱敏展示尾号）。
  - `bindEmail`（绑定邮箱，仅认证过才有；**注意 `bind_email` 仅存认证关系、不公开给小程序，但 Web 管理端为管理用途可展示**）。
  - `guestShortId`（游客短标识「食客+ID 尾 4 位」，昵称展示辅助）。

### 2.2 Web 动作（`UserView.vue`）
- 在用户信息列 / 状态列新增「认证状态」展示：`verified` 用 `StatusTag`（已认证 / 未认证）。
- 用户信息 meta 行补充：微信绑定（openid 脱敏尾号）、绑定邮箱（有则展示）。
- 筛选条可新增「认证状态」筛选（可选，若实现需走 `api/adapter.ts` 映射后端枚举）。
- 复用管理端统一组件（`DataTable` / `StatusTag` / `FilterSelect`）；不得在 View 层裸处理 `snake_case`（见 spec §5.x 字段命名红线）。

## 三、验收标准
1. 管理后台仍可用「管理员账号密码 + BCrypt + JWT」登录 `/admin/**`；不受 task-01 移除学生密码登录影响。
2. 用户列表返回含 `verified` / `openid`（脱敏）/ `bindEmail` 字段；`UserView.vue` 展示「认证状态」标签与微信绑定 / 绑定邮箱信息。
3. Web 字段命名符合 spec §5.x：`snake_case` 仅限 `api/adapter.ts` 内部，View 层用 camelCase；`favoriteCount`/`isFavorited`/`stall_id` 等废弃字段不得出现。
4. 无自定义非标错误码；响应统一 `code/message/data`。
5. 仅 ADMIN 可访问用户管理（`/admin/**` 权限不变）。

## 四、依赖
- 依赖 task-01 的 User 新字段接口。
- 被依赖：无（管理后台展示独立完成）。
