# UGC Media & Security Specification

## Purpose
约束评价与反馈两类 UGC 的**配图能力**与**微信内容安全检测**：配图张数与压缩规格、图片上传链路（微信云开发云存储中转 → `imgSecCheck` 送检 → COS 永久转存）、文本 `msgSecCheck` v2 **判定口径（2026-09-15 起为二态：`pass` / `review` 一律放行、仅 `risky` 拒绝）**、平台可迁移性。本 capability 为评价（`content-flow-visual` 写评价 / 评价区）与反馈（`feedback-forms-ux`）共用的横向能力；各 UX capability 只约束入口与展示形态，链路、规格与判定口径以本 capability 为唯一权威。

> **拍板来源（2026-09-13，防回退）**：本 capability 恢复评价/反馈配图并叠加内容安检，**推翻** 2026-09 `prelaunch-loop-closure` 期间「UGC 图片全量下线、评价区与反馈表单均无图片入口」的临时口径；相关旧条款已在各 spec 作废改写。
>
> **拍板来源（2026-09-15，防回退）**：**取消人工复核**——机检 `pass` 与 `review`（疑似）一律放行、仅 `risky` 拒绝；由该选择推导出内容安全态**单值化**，故**安检态字段 `sec_state`、其可见性规则与管理后台复核队列整体废止**（权威 `project_spec.md` §7.24）。原「`suggest` 三态」「`sec_state` 三态与可见性」「管理后台复核闭环」三条 Requirement 已就地改为**已废止**注记，恢复须重新拍板（PR-04）。

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

### Requirement: 文本安检（msgSecCheck v2，二态判定）

全部 UGC 文本（评价正文、反馈正文、用户昵称）在落库前 SHALL 经微信 `msgSecCheck` **v2** 检测：入参含提交人 `openid`（服务端据当前 userId 取得）、`scene`、`version=2`；`scene` 映射 SHALL 为**昵称=1、评价/反馈=2**（后续新增 UGC 形态须先登记 scene 值）。**判定 SHALL 为二态（2026-09-15 用户拍板「取消人工复核」）**：`pass` **与 `review`（疑似）均放行**（`SecSuggest.fromValue` 在判定入口把 `review` 归一为放行态）、`risky` 拦截提交并返回 HTTP 400（不新增错误码）；微信后续新增的**未知值或 `result.suggest` 缺失 SHALL fail-closed 按 `risky` 拦截**（宁可误拦、不放行）。判定**必须以 `result.suggest` 为准，SHALL NOT 只看 `errcode`**。安检调用 SHALL 统一收敛于后端 `ContentSecurityService`，业务模块不得自建安检调用；access_token SHALL 使用微信 **`stable_token`** 并缓存。前端对 400 违规 SHALL 提示内容存在违规且保留已填内容供修改重试。

#### Scenario: 评价文本二态判定

- **WHEN** 用户提交评价文本
- **THEN** `pass` 与 `review`（疑似）均直接落库并对外可见（无人工复核态）；`risky` 返回 400 拦截并提示、不落库

#### Scenario: 未知 / 缺失 suggest 值 fail-closed

- **WHEN** `msgSecCheck` 返回微信未来新增的未知 `suggest` 值，或响应中缺失 `result.suggest`
- **THEN** 判定为 `risky`，提交被 400 拦截（不放行，不落库）

#### Scenario: 反馈文本同样受检

- **WHEN** 游客提交带图或纯文本反馈
- **THEN** 反馈文本以 `scene=2` 受检，二态判定与评价一致（`risky` 拦 400、其余放行且不留复核标记）

#### Scenario: 昵称受检

- **WHEN** 用户修改昵称
- **THEN** 昵称以 `scene=1` 受检，`risky` 拦截、`pass` / `review` 放行

### Requirement: ~~安检状态字段与可见性~~（已废止：2026-09-15）

> **已废止（2026-09-15 用户拍板「取消人工复核」，权威 `project_spec.md` §7.24）**：原 Requirement「安检状态字段与可见性」整体作废——`review.sec_state` / `user_feedback.sec_state` **三态**（`pass`/`review`/`rejected`，默认 `pass`）、`review` 态仅由机检写入、公开条件 `is_hidden=0` **且** `sec_state='pass'`、`review` / `rejected` 对非作者不可见（后端过滤）、作者本人「安检复核中 / 未过审」态、以及 VO / 请求字段 `secState` 的约定，**均不再适用**。
>
> **退役落地（不可回退）**：`sec_state` 两列**已删除**（CREATE TABLE 不再创建，存量库由 `schema.sql` 末尾幂等段 `drop_sec_state_columns` 清理，可重跑）；`SecStateConst`、实体 / DTO / VO 的 `secState` 字段、Mapper 过滤条件（含 `DishMapper` 评分聚合的 `sec_state='pass'`）同批删除；`viewerId`（原仅服务「作者本人放行 `review` 态」）一并删除。
>
> **现口径（SHALL）**：机检结论 SHALL NOT 落库、SHALL NOT 构成可见性闸门；评价公开展示条件 SHALL 为 **`is_hidden=0` 单一判据**（后端过滤，前端不兜底），作者本人视角不再有「审核中」标识（仅保留 `isHidden`「已被隐藏」）。评价 / 反馈 VO 与提交请求的 camelCase 字段**仅保留 `images`**（≤3 项 COS URL 数组），**SHALL NOT 出现 `secState`**。

### Requirement: ~~管理后台复核闭环~~（已废止：2026-09-15）

> **已废止（2026-09-15 用户拍板「取消人工复核」，权威 `project_spec.md` §7.24）**：原 Requirement「管理后台复核闭环」整体作废——复核队列、`secState='review'` 筛选、**放行**（`sec_state`→`pass`）与**驳回**（`sec_state`→`rejected`）、端点 `PUT /admin/reviews/{id}/sec-state`（入参 `{ state: "pass" | "rejected" }`）**均已删除**。
>
> **现口径（SHALL）**：管理后台 SHALL NOT 提供任何内容复核队列、安检筛选或放行 / 驳回动作（机检 `pass` / `review` 一律放行、`risky` 已在提交侧拦截，后台无复核职责）。评价只提供**事后处置**：`PUT /admin/reviews/{id}/hide`（隐藏 / 显示）与 `DELETE /admin/reviews/{id}`（删除），页面为**「评价」页** `/dashboard/reviews`（导航名于 2026-09-15 IA 扁平化后由「评价管理」改为「评价」，路径不变）。**反馈详情 SHALL 仍展示配图（≤3 张可放大）**（该条保留），页面为**「反馈」页** `/dashboard/feedback`（同上由「反馈处理」改名）；交互细则见 `web-admin-feedback-loop`。**（2026-09-15 追加：管理端「操作日志」全链已删除，后台 SHALL NOT 提供任何操作留痕 / 审计能力，见 `project_spec.md` §7.25 第 1 条。）**

### Requirement: 平台可迁移（不绑定云托管）

COS 存储、内容安检与上传接口 SHALL 不与微信云托管耦合：后端 SHALL 可整体迁移至独立服务器，链路结构不变；届时小程序对后端的上传请求域名改走**备案域名白名单**（替代云开发云存储中转的免白名单通道），COS 为配图的永久存储不随部署形态变化。实现 SHALL 将云开发拉取（tcb）与 COS 转存抽象为可替换的存储适配层，禁止在业务模块内硬编码云托管专属调用。

#### Scenario: 后端迁移不影响数据

- **WHEN** 后端从云托管整体迁移至独立服务器
- **THEN** 既有 COS URL 的配图照常展示，安检与上传链路仅需切换域名白名单配置，业务代码不改

#### Scenario: 存储适配可替换

- **WHEN** 审查上传实现
- **THEN** 云开发拉取与 COS 转存收敛于适配层（upload 模块内），review/feedback 模块不感知具体存储通道
