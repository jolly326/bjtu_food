# auth-api-contract Specification

## Purpose
定义登录与账号资料接口对学生端公开的响应契约：账号信息对象的字段集、校园邮箱的唯一来源、游客短标识的派生口径，以及新账号昵称必须一次性落定（不得残留占位值）。

## Requirements

### Requirement: 账号信息对象字段集

登录与账号资料接口返回的账号信息对象 SHALL 恰为 5 个字段：`id`、`username`、`nickname`、`avatar`、`bindEmail`。该对象 SHALL NOT 返回 `verified`、`email`、`status`、`guestShortId` 任一字段。

以下四条链路 SHALL 返回同一字段集（承载结构差异不影响字段集的一致性）：`POST /auth/wechat-login`、`POST /auth/verify-email`、`GET /auth/profile`、`PUT /auth/profile`。

端点路径、鉴权要求与请求体 SHALL NOT 因本要求变化。

#### Scenario: 新游客登录的响应字段集

- **WHEN** 端上以新 openid 调用 `POST /auth/wechat-login`
- **THEN** 响应中账号信息对象的字段恰为 `id`、`username`、`nickname`、`avatar`、`bindEmail` 五项，不出现 `verified`、`email`、`status`、`guestShortId`

#### Scenario: 四条链路字段集一致

- **WHEN** 分别调用微信登录、邮箱认证、资料读取、资料更新四条链路
- **THEN** 四条响应中的账号信息对象字段集完全相同（不因承载结构不同而出现字段差异）

#### Scenario: 被删字段不出参

- **WHEN** 检查任一登录 / 资料链路响应
- **THEN** 响应中不存在 `verified`、`email`、`status`、`guestShortId` 任一字段

#### Scenario: 管理端用户列表同样不出参认证布尔

- **WHEN** 检查 `GET /admin/users` 的响应行
- **THEN** 行内不存在 `verified` 字段；管理端展示认证态时 SHALL 由 `bindEmail` 非空派生

### Requirement: 认证态判据单一真源

系统的「是否已完成学号邮箱认证」SHALL 由 `bindEmail`（库列 `user.bind_email`）非空与否唯一决定：非空即已认证（可写 UGC），为 `null` 即游客态。系统 SHALL NOT 保留任何与之并存的认证布尔字段或认证时间字段作为判据来源（`user.verified` / `user.verified_at` 已退役且不得回流）。

服务端 SHALL 经单一工具方法派生该判据（`AuthStateUtil#isVerified`），小程序端 SHALL 经 `useUserStore().isVerified()` 单点派生；消费方 SHALL NOT 各自散写等价判断。

认证态 SHALL NOT 进入 JWT，服务端 SHALL 在请求时按实时读取的 `user.bind_email` 判定。

#### Scenario: 游客触发需认证的写操作

- **WHEN** 未认证（`bind_email` 为 `NULL`）账号请求需认证的 UGC 写接口
- **THEN** 服务端返回细分业务码 `4031` 与「请先完成学号邮箱认证」，不进入业务逻辑

#### Scenario: 认证成功后立即生效

- **WHEN** 账号完成邮箱验证码认证（写入 `bind_email`）
- **THEN** 该账号随后的需认证写操作被放行，无需等待 token 更新（认证态不入 JWT、实时查库）

#### Scenario: 解绑或注销后回落游客态

- **WHEN** 账号的 `bind_email` 被清空（替换绑定释放旧账号、账号注销）
- **THEN** 该账号立即被视为游客态（需认证接口返回 `4031`），不存在残留的「已认证」判据

#### Scenario: 两列退役后不得回流

- **WHEN** 检查库表结构、实体、VO 与接口响应
- **THEN** `user` 表无 `verified` / `verified_at` 列，实体与 VO 无对应字段，任一响应无 `verified` 出参

### Requirement: 校园邮箱唯一来源

账号信息对象中，校园邮箱的唯一出参字段 SHALL 为 `bindEmail`（未认证时为 `null`）。系统 SHALL NOT 提供第二个邮箱出参字段；学生端 SHALL NOT 由 `username` 推导邮箱，亦 SHALL NOT 对两个邮箱字段做兜底择优。

#### Scenario: 未认证账号邮箱为空

- **WHEN** 未完成邮箱认证的游客请求账号信息
- **THEN** `bindEmail` 为 `null`，且不存在其它可读出邮箱的字段

#### Scenario: 已认证账号邮箱为绑定邮箱

- **WHEN** 已完成邮箱认证的账号请求账号信息
- **THEN** `bindEmail` 等于其认证绑定的校园邮箱

#### Scenario: 端上不存在第二邮箱来源

- **WHEN** 检查学生端账号信息的邮箱展示实现
- **THEN** 展示值仅取自 `bindEmail`，不存在「两个邮箱字段择一」或「由 `username` 推导」的旁路

### Requirement: 游客短标识由消费端派生

游客短标识 SHALL NOT 作为任何接口的出参字段（学生端登录 / 资料链路与 Web 管理端账号列表一视同仁）。消费端 SHALL 由账号 `id` 派生该标识，形式为「食客 + ID 尾 4 位」；当 `id` 不可得时 SHALL 回退既有的本地游客标识兜底。

#### Scenario: 学生端游客态展示短标识

- **WHEN** 未认证账号进入「我的」页
- **THEN** 展示的游客短标识为「食客 + 该账号 ID 尾 4 位」，与账号一一对应且跨设备一致

#### Scenario: 管理端过滤与兜底展示由 id 现算

- **WHEN** 在 Web 管理端账号列表按短标识关键词过滤，或某行昵称为空需要兜底展示
- **THEN** 过滤与展示均使用由该行 `id` 现算出的「食客 + ID 尾 4 位」，结果与改造前由接口字段提供时一致

#### Scenario: 无账号 ID 时回退兜底

- **WHEN** 端上尚未取得账号 `id`（如静默登录未完成或登录失败）
- **THEN** 展示本地游客标识兜底，不出现空白或报错

#### Scenario: 接口不再出参短标识

- **WHEN** 检查学生端登录 / 资料链路响应与 Web 管理端账号列表响应
- **THEN** 响应中均不含游客短标识字段

### Requirement: 建号昵称一次性落定

新账号创建完成后，其昵称 SHALL 为「食客 + ID 尾 4 位」，SHALL NOT 停留在任何中间占位值。建号流程整体失败时 SHALL NOT 留下部分写入的账号（昵称与账号同时成立或同时不存在）。用户已自行修改的昵称 SHALL NOT 被后续静默登录覆盖。

#### Scenario: 新账号昵称即最终值

- **WHEN** 新 openid 首次静默登录建号成功
- **THEN** 该账号昵称即为「食客 + ID 尾 4 位」，不存在「先占位、后回填」的可观测中间态

#### Scenario: 建号失败不留占位昵称

- **WHEN** 建号流程的任一步骤失败
- **THEN** 不留下昵称为占位值的账号；该微信再次登录时走全新建号流程

#### Scenario: 重复登录不覆盖已改昵称

- **WHEN** 用户已自行修改昵称后再次静默登录
- **THEN** 其昵称保持用户设定的值，不被建号口径的默认值覆盖
