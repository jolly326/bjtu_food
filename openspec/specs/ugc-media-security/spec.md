# UGC Media & Security Specification

## Purpose
约束评价与反馈两类 UGC 的**配图能力**与**微信内容安全检测**：配图张数与压缩规格、图片上传链路（微信云开发云存储中转 → `imgSecCheck` 送检 → COS 永久转存）、文本 `msgSecCheck` v2 三态处理、安检状态字段 `sec_state` 的可见性规则与管理后台复核闭环、平台可迁移性。本 capability 为评价（`content-flow-visual` 写评价 / 评价区）与反馈（`feedback-forms-ux`）共用的横向能力；各 UX capability 只约束入口与展示形态，链路、规格与状态机以本 capability 为唯一权威。

> **拍板来源（2026-09-13，防回退）**：本 capability 恢复评价/反馈配图并叠加内容安检，**推翻** 2026-09 `prelaunch-loop-closure` 期间「UGC 图片全量下线、评价区与反馈表单均无图片入口」的临时口径；相关旧条款已在各 spec 作废改写。

## Requirements

### Requirement: 配图张数与压缩规格

评价与反馈各 SHALL 最多支持 **3 张**配图；配图在提交前 SHALL 经 `wx.compressImage` 压缩至**最长边 ≤1334 且文件 ≤1MB**；格式 SHALL 限 jpg/jpeg/png/webp 白名单。单张压缩或上传失败时 SHALL 提示移除或重试，失败图片 SHALL NOT 混入提交载荷；超出 3 张时入口 SHALL 不再接受新增。客户端在**选择时本地校验**张数与格式，服务端对最终载荷 SHALL 再校验（张数 ≤3、URL 域名白名单），双闸一致。

#### Scenario: 三张上限

- **WHEN** 用户在评价弹层或反馈表单尝试添加第 4 张图片
- **THEN** 入口不响应或提示已达上限，提交载荷中 `images` 不超过 3 项

#### Scenario: 压缩后上传

- **WHEN** 用户选择任一超限原图（>1MB 或最长边 >1334）
- **THEN** 客户端经 `wx.compressImage` 压缩达标后再走上传链路，不把原始超限图直接送检

#### Scenario: 失败图不混入提交

- **WHEN** 某张图片压缩或安检转存失败
- **THEN** 提示失败并允许移除或重试，提交载荷不含该失败图的引用

### Requirement: 图片上传链路（云存储中转 → 送检 → COS 转存）

UGC 配图 SHALL 走统一链路：前端 `wx.cloud.uploadFile` 上传至**微信云开发云存储**（中转，免域名白名单）→ 前端**逐张调用** `POST /api/upload/images`（**单张契约**：JSON 入参 `{ fileId: string }` 为单个云存储 fileID；需登录、游客亦可、无 `verified` 门槛）→ 后端经 tcb `batchdownloadfile` 拉取该张 → `imgSecCheck` 送检（违规微信 code `87014` 该张拒绝，HTTP 400）→ 通过后转存 **COS 永久存储**并返回该张 COS URL（出参 `{ url: string }`）。多张配图 SHALL 逐张独立上传，**单张失败（违规 / 超限 / 拉取失败）SHALL 仅该张返回 400，前端提示后跳过、SHALL NOT 中断其余图片**；≤3 张总量约束由评价 / 反馈提交载荷校验兜底（服务端对最终载荷再校验张数 ≤3、URL 域名白名单）。评价 / 反馈提交载荷中的 `images` SHALL 为该接口返回的 COS URL；数据库（`review.images` / `user_feedback.images`）存 COS URL。既有 multipart `POST /api/upload/image`（用户头像、后台菜品图）SHALL 保留且不承载 UGC 配图。

#### Scenario: 端到端成功链路

- **WHEN** 用户完成配图选择并提交评价
- **THEN** 图片经云存储中转、imgSecCheck 通过、COS 转存，评价载荷 `images` 为 COS URL，公开展示直接使用 COS URL

#### Scenario: 单张违规跳过不中断

- **WHEN** 3 张配图逐张上传中有 1 张 `imgSecCheck` 判定违规（87014）
- **THEN** 仅该张上传请求返回 HTTP 400 与违规提示，该张不产生 COS 副本；其余 2 张各自独立上传成功（前端提示后跳过失败张、不中断），提交载荷 `images` 仅含成功张的 COS URL

#### Scenario: UGC 配图不走 multipart 通道

- **WHEN** 审查评价 / 反馈的图片上传调用
- **THEN** 全部经 `POST /api/upload/images`（fileId），multipart `/api/upload/image` 仅承载头像与后台菜品图

### Requirement: 文本安检（msgSecCheck v2，三态处理）

全部 UGC 文本（评价正文、反馈正文、用户昵称）在落库前 SHALL 经微信 `msgSecCheck` **v2** 检测：入参含提交人 `openid`（服务端据当前 userId 取得）、`scene`、`version=2`；`scene` 映射 SHALL 为**昵称=1、评价/反馈=2**（后续新增 UGC 形态须先登记 scene 值）。`suggest` 三态处理 SHALL 一致：`pass` 正常落库；`review` 正常落库并置 `sec_state='review'` 进人工复核（**不拦截提交**）；`risky` 拦截提交并返回 HTTP 400（不新增错误码）。安检调用 SHALL 统一收敛于后端 `ContentSecurityService`，业务模块不得自建安检调用；access_token SHALL 使用微信 **`stable_token`** 并缓存。前端对 400 违规 SHALL 提示内容存在违规且保留已填内容供修改重试。

#### Scenario: 评价文本三态

- **WHEN** 用户提交评价文本
- **THEN** `pass` 直接落库展示；`review` 落库 `sec_state='review'`（提交成功但暂不公开）；`risky` 返回 400 拦截并提示

#### Scenario: 反馈文本同样受检

- **WHEN** 游客提交带图或纯文本反馈
- **THEN** 反馈文本以 `scene=2` 受检，三态处理与评价一致

#### Scenario: 昵称受检

- **WHEN** 用户修改昵称
- **THEN** 昵称以 `scene=1` 受检，`risky` 拦截、`review`/`pass` 按既有昵称流程处理

### Requirement: 安检状态字段与可见性

`review.sec_state` 与 `user_feedback.sec_state` SHALL 取值 **`pass`/`review`/`rejected` 三态**（`pass`=通过；`review`=机检 `msgSecCheck` `suggest=review` 落库、待人工复核；`rejected`=管理端人工复核驳回），默认 `pass`，以列扩展落地（**不新建表**）；`review` 态仅由机检写入，管理端复核接口 SHALL NOT 写入 `review`（只写人工结论 `pass`/`rejected`）。`sec_state` SHALL 独立于 `is_hidden`（评价管理员隐藏语义不变），评价公开展示条件 SHALL 为 `is_hidden=0` **且** `sec_state='pass'`。`sec_state='review'` 与 `sec_state='rejected'` 的记录 SHALL 同口径对非作者不可见（**后端过滤，前端不兜底**）；作者本人 SHALL 可见并呈现「安检复核中 / 未过审」态（评价在「我的评价」可见，反馈本身无公开列表页、不受此影响）。VO 与提交请求字段命名 SHALL 为 camelCase 的 `images`（≤3 项 COS URL 数组）与 `secState`。

#### Scenario: 复核态对非作者不可见

- **WHEN** 非作者用户浏览含 `sec_state='review'` 评价的菜品评价列表
- **THEN** 该评价不出现在列表与计数中（后端过滤），作者本人在「我的评价」可见并带复核中标识

#### Scenario: 状态字段命名一致

- **WHEN** 检查评价 / 反馈相关接口的 JSON 载荷
- **THEN** 配图字段为 `images`、安检态字段为 `secState`（camelCase），无第二套并行命名

### Requirement: 管理后台复核闭环

管理后台 SHALL 提供评价安检复核队列：可筛选 `secState='review'` 并展示正文、配图与所属菜品，提供**放行**（`sec_state`→`pass`，恢复公开展示）与**驳回**（`sec_state`→`rejected`，持续对非作者不可见，作者侧呈现未过审态）两个动作，经 `PUT /admin/reviews/{id}/sec-state`（入参 `{ state: "pass" | "rejected" }`）。反馈详情 SHALL 展示配图（≤3 张可放大）。交互细则见 `web-admin-feedback-loop`。

#### Scenario: 放行恢复公开展示

- **WHEN** 管理员对复核态评价执行放行
- **THEN** `sec_state` 置 `pass`，评价恢复对非作者的公开展示

#### Scenario: 驳回持续不可见

- **WHEN** 管理员对复核态评价执行驳回
- **THEN** 该评价对非作者持续不可见，不恢复展示

### Requirement: 平台可迁移（不绑定云托管）

COS 存储、内容安检与上传接口 SHALL 不与微信云托管耦合：后端 SHALL 可整体迁移至独立服务器，链路结构不变；届时小程序对后端的上传请求域名改走**备案域名白名单**（替代云开发云存储中转的免白名单通道），COS 为配图的永久存储不随部署形态变化。实现 SHALL 将云开发拉取（tcb）与 COS 转存抽象为可替换的存储适配层，禁止在业务模块内硬编码云托管专属调用。

#### Scenario: 后端迁移不影响数据

- **WHEN** 后端从云托管整体迁移至独立服务器
- **THEN** 既有 COS URL 的配图照常展示，安检与上传链路仅需切换域名白名单配置，业务代码不改

#### Scenario: 存储适配可替换

- **WHEN** 审查上传实现
- **THEN** 云开发拉取与 COS 转存收敛于适配层（upload 模块内），review/feedback 模块不感知具体存储通道
