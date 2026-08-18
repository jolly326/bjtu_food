# task-01 · 后端：微信登录体系（User 表变更 + wechat-login / verify-email / email-code + verified 鉴权 + 数据合并 / 绑定替换）

> 负责角色：**后端开发工程师**。关联权威条款：`docs/project_spec.md` §0.1、§0.3「认证与鉴权」、§1、§3、**§5.y 认证与鉴权**、§5.x。
> 本任务为微信登录体系的**后端核心**，是小程序端（task-03）与管理后台（task-02）落地的前置依赖。

## 背景
用户已拍板：废除账号密码登录，微信打开小程序自动静默登录为游客态（`verified=false`），邮箱验证码认证解锁社区写操作（`verified=true`），邮箱为唯一迁移 / 绑定凭证，同一邮箱新微信认证替换旧绑定、不设解绑入口。详见 spec §5.y。

## 一、接口契约（新增 / 改造 / 移除）

### 1.1 新增 `POST /auth/wechat-login`（公开）
- 入参：`{ code: string }`（`wx.login` 临时凭证）。
- 处理：后端调微信 `code2Session` 换 `openid`（+ `unionid` 若有）→ 按 `user.openid` 取号：
  - 存在：更新 `last_login_at`，返回既有账号。
  - 不存在：**自动建号**：`username='wx_'+openid 尾 16 位`、昵称默认「食客+ID 尾 4 位」、`role=student`、`status=active`、`verified=0`、`openid=…`。
- 出参：`LoginResp{ token, userInfo }`；`userInfo` 含 `id / nickname / avatar / verified / bindEmail(可空) / guestShortId`。JWT 7 天。
- 错误：`code2Session` 失败 → `400`「微信登录失败」。

### 1.2 改造 `POST /auth/email-code`（公开）
- 入参：`{ username(学号), email(可空，自动推导 {学号}@bjtu.edu.cn), purpose }`。
- `purpose` 枚举收窄为 `verify`（认证用途），**移除 `login`/`register`/`reset`**。
- 规则不变：同邮箱同用途 60s 限频、验证码 10min 有效、不随响应返回（邮件发送）。
- 出参：`{ message: "验证码已发送" }`。

### 1.3 新增 `POST /auth/verify-email`（公开，需已微信登录态）
- 入参：`{ code: string }` + 当前微信账号上下文（`SecurityUtil` 取 `userId`）。
- 处理（顺序）：
  1. 校验验证码（与当前学号邮箱匹配）失败 → `400`。
  2. 按邮箱查询历史 `user` 记录（见 §5.y.3 数据迁移合并）：
     - 该邮箱无历史账号：直接绑定当前微信 → `bind_email=邮箱`、`verified=1`、`verified_at=now`。
     - 该邮箱存在历史账号（旧邮箱注册用户）：**数据归属转移**——将旧账号的业务数据（`dish.created_by` / `review.user_id` / `moment` / `feedback` 等）改挂到当前微信账号，并处理旧记录清理（若旧账号即当前微信则跳过）。
     - 该邮箱已被**另一微信**绑定（`verified=1`）：**替换绑定**——新微信 `verified=1`、`bind_email=邮箱`；旧微信 `verified=0`、`bind_email=NULL`、`verified_at=NULL`（旧微信数据归属跟到新微信，若旧微信账号与当前微信不同则按迁移规则转移）。
  3. 返回更新后 `LoginResp`。
- 关键约束：**邮箱是唯一迁移 / 绑定凭证**；**不设解绑入口**（后端不提供解绑接口）；`verified` 不进 JWT。

### 1.4 鉴权改造：需登录 → 需 verified
- 社区写操作（发布菜品 `POST /dishes` 系列、写评价 `POST /reviews`、评论、点赞、动态 `POST /moments` 等）鉴权由「需 `STUDENT` 角色 / 需登录」改为「**需 `verified=true`**」。（注：`POST /my/stalls` 学生提交档口/食堂功能已于 2026-08-18 随代码清理移除）
- 实现：新增注解或切面（如 `@RequireVerified`，或基于 `SecurityUtil` 校验 `user.verified`）；`verified` 不进 JWT，每次请求按 `user.verified` 实时判定。
- 游客（`verified=false`）访问需 verified 接口 → `403`「请先完成学号邮箱认证」（错误码沿用标准，不自定义）。
- `GET /auth/profile`、`GET /feedback/my` 等「本人数据读取」仍允许游客态读取（`verified=false` 亦可），但社区写操作必须 verified。

### 1.5 移除 / 废弃（账号密码体系）
- 移除对 STUDENT 生效的：`POST /auth/login`、`POST /auth/register`、`PUT /auth/password`、`PUT /auth/password/reset`。
- 相关 DTO（`LoginReq` / `RegisterReq` / `PasswordResetReq` / `PasswordUpdateReq` / `LoginResp` 密码相关）在 STUDENT 路径废弃；`LoginResp` 保留（返回 wechat-login / verify-email 用）。
- `SecurityConfig` 白名单同步：公开 = `/auth/wechat-login`、`/auth/email-code`、`/auth/verify-email`、`GET /auth/profile`、`GET /feedback/my`、公开浏览 GET 前缀、`/feedback`（提交）、Swagger；**移除** `/auth/login`、`/auth/register`、`/auth/password/reset`。
- 学生侧不再校验密码；`password` 列仅管理员（后台）保留使用。

## 二、User 表结构变更（`server/src/main/resources/db/schema.sql`）
在 `user` 表新增：
```sql
`openid`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '微信 openid（静默登录取号依据）',
`unionid`      VARCHAR(64)  NULL     DEFAULT NULL COMMENT '微信 unionid（同主体多应用，可空）',
`verified`     TINYINT      NOT NULL DEFAULT 0 COMMENT '认证状态：0=游客未认证 / 1=已邮箱认证',
`bind_email`   VARCHAR(128) NULL     DEFAULT NULL COMMENT '已认证绑定邮箱（仅存认证关系，不公开）',
`verified_at`  DATETIME     NULL     DEFAULT NULL COMMENT '认证时间',
UNIQUE KEY `uk_user_openid` (`openid`)
```
- `username` 语义调整（游客建号 `wx_`+openid 尾 16 位）；旧 `email` 列保留作为历史迁移凭证；`password` 列保留（管理员后台用）。
- 在 `schema.sql` 中登记接口契约注释（见 spec §3「接口契约登记」）。

## 三、数据迁移合并规则（详见 spec §5.y.3）
- 迁移凭证唯一 = 校园邮箱。
- 认证触发自动合并；无历史邮箱记录则仅绑定 + `verified=1`。
- 未邮箱注册过的新用户无历史数据，无需迁移。
- 新微信替换旧绑定（旧数据归属跟到新微信）。

## 四、验收标准（后端）
1. `user` 表含 `openid`（唯一索引）/`unionid`/`verified`/`bind_email`/`verified_at`，`schema.sql` 同步且可建表。
2. `POST /auth/wechat-login`：新 openid 自动建号（`username='wx_'+openid 尾 16 位`、昵称「食客+ID 尾 4 位」、`verified=0`）；已有 openid 返回原账号；返回 `token`+`userInfo(含 verified/bindEmail/guestShortId)`。
3. `POST /auth/email-code` `purpose=verify` 发送验证码（60s 限频、10min 有效）；`purpose` 不再接受 `login/register/reset`。
4. `POST /auth/verify-email` 校验码后：无历史邮箱 → 绑定 + `verified=1`；有历史邮箱 → 业务数据归属转移到当前微信；已被他微信绑定 → 替换绑定且旧微信回游客态。均返回更新后 `LoginResp`。
5. 鉴权改造：游客（`verified=false`）访问社区写接口返回 `403` 及「请先完成学号邮箱认证」；`verified=true` 可正常写。
6. `POST /auth/login`、`POST /auth/register`、`PUT /auth/password(/reset)` 对 STUDENT 不再可用（移除或仅 admin）；`SecurityConfig` 白名单与 spec §3/§5.y 一致。
7. 管理后台 `/admin/**` 仍仅 `ADMIN`（本任务不触碰，见 task-02）。
8. 无自定义非标错误码；响应统一 `code/message/data`。

## 五、技术实现要点
- 分层：`controller` 薄、`service` 承载业务、`mapper` 仅数据访问；写操作用 `@Transactional`（尤其数据迁移合并需事务保证一致性）。
- `code2Session` 用微信 `jscode2session` 接口；`appid`/`secret` 走配置（`application.yml`）。
- 数据合并涉及多表 `created_by`/`user_id` 归属转移，须事务包裹并处理唯一键冲突（如 `uk_review_user_dish`）。
- `verified` 判定不进 JWT；`SecurityUtil` 扩展读取 `user.verified`。

## 六、依赖
- 无外部 task 依赖（后端核心，独立起步）。
- 被依赖：task-02（管理后台新字段）、task-03（小程序静默登录接入）。
