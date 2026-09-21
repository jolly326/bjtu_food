# auth-api-contract Specification

## Purpose
定义登录与账号资料接口对学生端公开的响应契约：账号信息对象的字段集、校园邮箱的唯一来源、游客短标识的派生口径，以及新账号昵称必须一次性落定（不得残留占位值）。

## Requirements

### Requirement: 账号信息对象字段集

登录与账号资料接口返回的账号信息对象 SHALL 恰为 6 个字段：`id`、`username`、`nickname`、`avatar`、`verified`、`bindEmail`。该对象 SHALL NOT 返回 `email`、`status`、`guestShortId` 任一字段。

以下四条链路 SHALL 返回同一字段集（承载结构差异不影响字段集的一致性）：`POST /auth/wechat-login`、`POST /auth/verify-email`、`GET /auth/profile`、`PUT /auth/profile`。

端点路径、鉴权要求与请求体 SHALL NOT 因本要求变化。

#### Scenario: 新游客登录的响应字段集

- **WHEN** 端上以新 openid 调用 `POST /auth/wechat-login`
- **THEN** 响应中账号信息对象的字段恰为 `id`、`username`、`nickname`、`avatar`、`verified`、`bindEmail` 六项，不出现 `email`、`status`、`guestShortId`

#### Scenario: 四条链路字段集一致

- **WHEN** 分别调用微信登录、邮箱认证、资料读取、资料更新四条链路
- **THEN** 四条响应中的账号信息对象字段集完全相同（不因承载结构不同而出现字段差异）

#### Scenario: 被删字段不出参

- **WHEN** 检查任一登录 / 资料链路响应
- **THEN** 响应中不存在 `email`、`status`、`guestShortId` 任一字段

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
