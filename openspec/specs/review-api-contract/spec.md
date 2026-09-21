# Review API Contract Specification

## Purpose

定义评价与浏览相关端点对学生端与下游系统公开的外部契约：路径形态、排序与筛选语义、重新评价的覆盖语义、公开列表字段范围，以及「有用」能力的下线。

## Requirements

### Requirement: RESTful 子资源路径

评价与浏览 SHALL 以资源嵌套表达归属：评价列表为 `GET /dishes/{id}/reviews`，发表评价为 `POST /dishes/{id}/reviews`（请求体 SHALL NOT 再携带菜品 ID），浏览上报为 `POST /dishes/{id}/views`。当前用户的评价集合为 `GET /my/reviews`，并 SHALL 支持按菜品过滤（`dishId`），用于详情页判定「我是否已评价」并取回我的评价 ID。旧的「查询参数表达归属」「路径动词」形态 SHALL NOT 继续提供。

#### Scenario: 评价列表按子资源取

- **WHEN** 客户端请求某菜品的评价列表
- **THEN** 请求 `GET /dishes/{id}/reviews` 并仅以路径表达菜品归属，分页参数仍通过查询串传递

#### Scenario: 发表评价归属由路径锁定

- **WHEN** 客户端发表某菜品的评价
- **THEN** 请求 `POST /dishes/{id}/reviews`，请求体不含菜品 ID，归属由路径决定

#### Scenario: 我的评价按菜过滤

- **WHEN** 客户端带 `dishId` 请求 `GET /my/reviews`
- **THEN** 仅返回当前用户对该菜品的评价（用于判定已评价及取回评价 ID），不因分页边界丢结果

#### Scenario: 浏览上报

- **WHEN** 客户端上报菜品浏览
- **THEN** 请求 `POST /dishes/{id}/views`，同一用户同一菜品每日只计一次

### Requirement: 评价排序唯一为时间倒序

公开评价列表 SHALL 以发表时间倒序排列（新评价在前），且 SHALL NOT 接受排序参数；系统 SHALL NOT 再提供「按有用数置顶」等第二排序口径。

#### Scenario: 默认与唯一排序

- **WHEN** 客户端请求评价列表
- **THEN** 返回结果按发表时间倒序排列，请求不携带排序参数，传入排序参数不生效

### Requirement: 只看有图筛选

评价列表 SHALL 支持「只看有图」筛选：请求 `hasImage=1` 时仅返回带图评价，缺省或 `0` 时不过滤。

#### Scenario: 只看带图评价

- **WHEN** 客户端以 `hasImage=1` 请求评价列表
- **THEN** 返回结果中每条评价均含至少一张配图，且总数按该筛选口径统计

### Requirement: 重新评价（覆盖式）

系统 SHALL 提供 `PUT /reviews/{id}`：**覆盖更新同一条评价**（评分 / 文字 / 配图），SHALL NOT 新建行；`uk_review_user_dish` 唯一占位不变。更新后发表时间 SHALL 刷新为当前时间（重评即新发布），隐藏标记 SHALL 重置为未隐藏，评分聚合 SHALL 重算一次。鉴权 SHALL 为作者本人且需完成认证（未认证返回既有认证错误码，非本人返回无权限）。内容安全检测 SHALL 与首次发表同口径：**文本在发表 / 重评请求内经 `msgSecCheck` v2 送检**；**图片经上传链路（`POST /upload/images`）的 `imgSecCheck` 送检**——重评仅校验图片为受信 COS 地址、**不重复送检**（进入系统的每张图均已过检，避免重复计费）；违规内容拒绝且不落库。重评次数 SHALL NOT 限制（一人一菜一票，改多次仍为一票）。

#### Scenario: 覆盖同一行并置顶

- **WHEN** 作者提交 `PUT /reviews/{id}` 且内容合规
- **THEN** 该评价行被覆盖为新评分 / 文字 / 配图，发表时间刷新为当前，在时间倒序列表中排到最前，且不产生新评价行

#### Scenario: 隐藏标记重置并重算聚合

- **WHEN** 一条曾被隐藏的评价被作者重新评价
- **THEN** 该评价恢复为未隐藏并计入评分聚合，菜品均分与评价数同步刷新

#### Scenario: 鉴权与归属

- **WHEN** 未认证用户或非评价作者调用该端点
- **THEN** 分别返回认证错误码 / 无权限，评价内容不变

#### Scenario: 违规内容被拒

- **WHEN** 重新评价的文本或图片未通过微信内容安全检测
- **THEN** 请求被拒绝且原评价内容保持不变

### Requirement: 公开评价列表字段范围

公开评价行 SHALL 仅包含：`id`、`userId`、`userNickname`、`userAvatar`、`rating`、`content`、`images`、`createdAt`。它 SHALL NOT 返回 `dishId`、`dishName`、`isHidden`（菜品归属由路径决定、菜品名联表冗余、隐藏态在公开列表恒为未隐藏）——三者仅在「我的评价」响应中返回。分页壳 SHALL 遵循 `pagination-contract` 的统一约定（`records` / `total` / `page` / `pageSize`，不含 `list` 兼容字段），本能力不再单独声明分页壳字段集。

#### Scenario: 公开列表字段收敛

- **WHEN** 检查 `GET /dishes/{id}/reviews` 的单行字段
- **THEN** 恰为上述 8 个字段，不含 `dishId` / `dishName` / `isHidden`

#### Scenario: 我的评价保留作者视角字段

- **WHEN** 检查 `GET /my/reviews` 的单行字段
- **THEN** 在公开 8 字段之外包含 `dishId` / `dishName` / `isHidden`

#### Scenario: 分页壳归口统一约定

- **WHEN** 检查 `GET /dishes/{id}/reviews` 与 `GET /my/reviews` 的响应壳
- **THEN** 壳字段与 `pagination-contract` 一致（`records` / `total` / `page` / `pageSize`，无 `list`）

### Requirement: 「有用」能力下线

评价「有用」SHALL 全链下线：投票端点 SHALL NOT 提供；评价响应 SHALL NOT 含 `useful` / `usefulCount`；管理端评价响应 SHALL NOT 含有用计数；存储该投票的表与计数列 SHALL 被移除（幂等迁移）。

#### Scenario: 端点不再提供

- **WHEN** 客户端尝试调用评价「有用」投票端点
- **THEN** 该端点不存在（请求失败），客户端界面亦无「有用」按钮

#### Scenario: 字段与存储清理

- **WHEN** 检查评价相关响应与管理端评价列表
- **THEN** 均无有用计数 / 已赞态字段，且投票表与计数列已从库中移除
