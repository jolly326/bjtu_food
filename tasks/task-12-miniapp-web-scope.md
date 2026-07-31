# task-12 · 小程序端 + Web 管理端本期范围补齐

> 文档性质：技术架构师派工任务拆分（基于 `tasks/CONTRACT_IMPACT.md` 契约缺口 + 最终定稿）。
> 强制约束：角色仅 STUDENT/ADMIN；金额分/元；对外 camelCase；emoji 语义唯一（❤️=喜欢、👍=有用/点赞）；小程序 750rpx 布局 + 动效从简；Web 复用自封装组件 + 三态 + 二次确认。
> **不修改 `project_spec.md`**（需求梳理师职责）；如发现与 spec 冲突，列于本文 §6 交需求梳理师。
> **不写业务代码**（架构侧仅拆任务 + 验收标准 + 依赖）。

---

## 0. 本期交付目标（定稿 12 项能力映射）

| # | 定稿能力 | 对应子任务 |
| --- | --- | --- |
| 1 | 浏览/搜索（食堂·档口·菜品） | 已具备（task-01~05），本期权重为"详情页聚合关联动态"（task-12.6） |
| 2 | 详情页 + 快捷申请下架/纠错/关闭 | task-12.1 + task-12.6 |
| 3 | 评价（发表/列表/👍） | 已具备，补"删除本人评价"（task-12.5） |
| 4 | 实体贡献体系（统一入口+快捷+我的提交聚合） | task-12.1（✅ 已拍板：新建独立 apply_action 表 + 统一审核状态机） |
| 5 | 社区动态/发动态 + 审核 | 已具备（task-06），补评论点赞（task-12.4） |
| 6 | 喜欢（替代收藏） | task-12.12（✅ 已拍板：彻底删除 /favorites 端点与收藏页，但保留喜欢(👍/like 菜品/动态点赞)功能，喜欢不随收藏移除） |
| 7 | 消息中心 | 已具备（task-09） |
| 8 | 社区举报（复用 feedback） | task-12.7 |
| 9 | 账号注销 | task-12.8 |
| 10 | 关联动态双向跳转 + 聚合 | task-12.6 |
| 11 | 删除本人记录（动态/菜品/评价） | task-12.5 |
| 12 | 评论互动升级（点赞+楼中楼） | task-12.4 |
| Banner | 轮播外链（公众号/H5） | task-12.9（前端跳转） |
| 菜品折扣价 | originalPrice/promoPrice | task-12.9 |
| 活动废弃 | 整体移除 | task-12.10（✅ 已拍板：整体移除后端 activity 模块/路由/表） |
| Web 菜单 | 分组版 + 默认落地反馈 | task-12.13 |

---

## 1. task-12.1 · 实体贡献统一入口 + 详情页快捷 + 我的提交聚合

**归属端**：小程序（主） + 后端（下架/变更申请建模）

**背景**：现有 `POST /dishes`、`PUT /dishes/{id}`、`GET /my/dishes`、`POST /my/stalls`、`GET /my/stalls`、审核台（`/admin/audit`）已具备基础闭环，但缺：
- "我要贡献"统一入口（发布菜品/提交档口/提交食堂/申请下架或变更）
- 详情页快捷「申请下架/纠错」（菜品）、「申请关闭/纠错」（档口）、「申请调整/下架」（食堂）
- "我的提交"聚合页（分"实体/动态"两标签，含已下架）

**后端缺口（✅ 已拍板：新建独立申请表，走统一审核状态机）**：
- **统一申请模型**：新建独立 `apply_action` 表，**不复用现有实体字段**。表核心字段：
  - `id`(PK)、`applicant_id`(学生 userId)、`entity_type`(DISH/STALL/CANTEEN, 对齐 spec entityType)、`entity_id`(关联实体 id，新增类申请时可为空/待回填)、`apply_type`(`NEW`/`CLOSE`/`CHANGE`, 对齐 spec applyType)、`status`(`pending`/`approved`/`rejected`)、`payload`(JSON，承载新增/变更字段快照，如菜品名/价/图、档口营业时间等)、`reject_reason`(退回时填写)、`created_at`/`updated_at`/`handled_by`/`handled_at`。
  - 统一审核状态机：`pending → approved | rejected`；`rejected` 必填 `reject_reason`；`approved` 触发副作用（新增类写实体、下架类置实体 `status=off/closed`、变更类写回实体字段）。
  - 索引：`uk_entity_action_pending` 约束同实体同动作仅一条待审（避免重复提交）；`idx_applicant`、`idx_status`、`idx_entity`。
- **学生入口端点**（STU）：
  - `POST /my/apply`（STU，统一提交：`{ entityType, entityId?, applyType, payload }`）—— 新增/下架/变更申请统一收口，后端按 `applyType` + `entityType` 校验（下架/变更类 `entityId` 必填；新增类可空）。
  - 重提校验：同 `(entityType, entityId, applyType)` 已存在 `pending` 时返回 409（前端提示"已有待审申请"），不新建重复记录。
- **审核中心端点**（ADMIN，复用现有 `/admin/audit` 范式或新增）：
  - `GET /admin/apply?status=&entityType=&action=`：审核列表（替代原先散落各实体 `audit_status` 的审核台分流）。
  - `POST /admin/apply/{id}/approve`、`POST /admin/apply/{id}/reject`（`{ rejectReason }`）：通过/退回，触发副作用。
- **我的提交聚合**（STU）：
  - 新增 `GET /my/submissions`（STU）：聚合 `apply_action` 表本人记录 + 本人 moment，返回 `List<SubmissionVO>{ type(apply/moment), id, entityType, action, title(preview), status(auditStatus), createdAt }`，前端分"实体/动态"两标签；实体标签含已 approved 且实体已下架（`status=off/closed`）的提交。
  - 注：新增类申请在 approved 前无实体 id，列表以 `payload` 预览标题展示。

**验收标准**：
- [ ] 后端 `apply_action` 表落库 + 统一状态机；`pending/approved/rejected` 流转正确，rejected 必填原因。
- [ ] 小程序"我的"页有「我要贡献」统一入口，Sheet 提供：发布菜品/提交档口/提交食堂/申请下架或变更——均经 `POST /my/apply` 统一收口。
- [ ] 菜品详情「申请下架/纠错」、档口详情「申请关闭/纠错」、食堂详情「申请调整/下架」Sheet 可发起，调用 `POST /my/apply`（`applyType=CLOSE/CHANGE` + `entityId`）。
- [ ] 「我的提交」页分"实体/动态"两标签；实体标签含 dish/stall/canteen 申请（含已下架 `status=off/closed`）；动态标签含本人 moment（含审核态）。
- [ ] 审核中心（Web）呈现所有 `apply_action` 申请（按 entityType/action 分流），通过即触发副作用，退回填原因；学生重提复用原记录（409 拦截重复 pending）。
- [ ] **不把申请字段塞回 dish/stall/canteen 实体**（独立表承载，符合 C3 已拍板方案）。

**依赖**：task-06（社区）、task-01~05（详情页基础）。后端建模已拍板，可直接实现。

---

## 2. task-12.4 · 动态评论点赞 + 楼中楼

**归属端**：后端（主） + 小程序（UI）

**背景**：`moment_comment` 已含 `parentId`（一层楼中楼 ✅），但无点赞列/接口；`ARCH_DECISIONS_PHASE2 §5 D1` 原决策"评论 👍 不做"——**定稿推翻该决策**，要求评论区点赞。

**后端缺口**：
- `moment_comment` 表 ALTER 加 `useful_count`(INT, default 0)；新建 `uk_useful_user_comment(user_id, comment_id)` 唯一键（复刻 `uk_useful_user_review`）。
- 实体 `MomentComment` + `MomentCommentVO` 加 `usefulCount`(Integer) + `useful`(Boolean, 当前用户是否点过，需 join 关系表或冗余标记)。
- 新增 `POST /moments/{id}/comments/{cid}/useful`（STU，幂等切换，返 `{ useful: boolean, usefulCount: int }`）；关系表 `moment_comment_useful(user_id, comment_id)`。
- `MomentCommentServiceImpl` 维护计数 + 唯一键；被点赞通知（低频，沿用 D-A 异步）。

**验收标准**：
- [ ] 评论区 UI：主楼 + 子回复扁平化（B站式），"共 N 条"展开/收起，"回复 @某人"可点。
- [ ] 每条评论 👍 幂等切换（再点取消），计数实时更新，不抛错。
- [ ] 楼中楼：回复某评论 `parentId` 非空，展示"回复 @昵称"。
- [ ] 后端 `useful_count` + `uk_useful_user_comment` 唯一约束生效；一人一票。
- [ ] emoji：👍=有用/点赞，不混用 ❤️。

**依赖**：task-06（moment_comment 基础）。**明确推翻 ARCH_DECISIONS_PHASE2 §5 D1**（在 PR 描述标注）。

---

## 3. task-12.5 · 删除本人记录（动态/菜品/评价）

**归属端**：后端（评价删除） + 小程序（UI）

**背景**：
- 动态：`DELETE /my/moments/{id}` ✅ 已具备。
- 菜品：`DELETE /dishes/{id}`（本人 created_by）✅ 已登记。
- 评价：**学生端删除本人评价端点缺失**（⚠️）。现有 `DELETE /admin/reviews/{id}` 为 admin 物理删除。

**后端缺口**：
- 新增 `DELETE /my/reviews/{id}`（STU，仅本人 userId，级联清理 `uk_useful_user_review` 关联）；返回 200/403/404。

**验收标准**：
- [ ] 小程序动态详情/我的动态：删除本人动态（已具备）。
- [ ] 小程序菜品详情：删除本人发布菜品（仅 created_by 本人可删）。
- [ ] 小程序评价列表/我的评价：删除本人评价（走 `DELETE /my/reviews/{id}`）。
- [ ] 后端归属校验 `SecurityUtil.getCurrentUserId()`，禁止删他人。

**依赖**：task-03（评价列表）、task-06（动态删除已具备）。

---

## 4. task-12.6 · 关联动态双向跳转 + 聚合接口

**归属端**：后端（canteen 聚合） + 小程序（三详情页）

**背景**：`GET /moments?dishId=&stallId=` ✅ 已支持；`moment.relatedType` 仅 dish/stall/none（无 canteen）。定稿要求食堂详情也聚合关联动态。

**后端缺口**：
- 新增 `GET /moments?canteenId=`（PUB）：按该食堂下全部档口 `stallId IN (SELECT id FROM stall WHERE canteen_id=?)` 聚合查询，复用 publicList 过滤逻辑（不污染 `relatedType` 语义，动态仍只关联 dish/stall）。

**验收标准**：
- [ ] 菜品详情「关联动态」区块（`GET /moments?dishId=`）卡片可点进动态详情。
- [ ] 档口详情「关联动态」（`GET /moments?stallId=`）。
- [ ] 食堂详情「关联动态」（`GET /moments?canteenId=` 聚合）。
- [ ] 动态详情反向 📍 chip 跳菜品/档口详情（已具备）。
- [ ] 双向跳转不破 750rpx 布局。

**依赖**：task-06（moment 关联基础）。

---

## 5. task-12.7 · 社区举报（前端 + 后端 feedback 复用）

**归属端**：后端（补字段） + 小程序（举报入口）

**背景**：`POST /feedback` 端点存在但 `Feedback` 实体缺 `relatedType`/`relatedId`，`FeedbackReq` 无 report 类型。定稿要求动态详情 + 反馈页举报，复用 feedback 表。

**后端缺口**：
- `Feedback` 实体补 `relatedType`(String?)/`relatedId`(Long?)。
- `FeedbackReq` 补 `relatedType?`/`relatedId?`；`type=report` 时 `relatedType='moment'` + `relatedId` 必填（`@Validated` 分组或 service 校验，违返 400）。
- `FeedbackServiceImpl` 写 `related_type`/`related_id`；`status=pending`。
- Web `FeedbackView` 已可筛 `type=report`，补 `relatedType/relatedId` 展示列。

**验收标准**：
- [ ] 小程序动态详情「举报」入口 → `POST /feedback`(`type=report`, `relatedType='moment'`, `relatedId={momentId}`, `content`)。
- [ ] 小程序反馈页（通用反馈）→ `POST /feedback`(`type=suggestion/error/other`)。
- [ ] 后端 `user_feedback` 表 `related_type`/`related_id` 落库；Web 反馈台可筛举报件并查看关联动态。
- [ ] 不新建举报表（红线）。

**依赖**：task-09（feedback 基础）、task-06（moment）。

---

## 6. task-12.8 · 账号注销（前端 + 后端端点）

**归属端**：后端（端点） + 小程序（二次确认）

**背景**：`AuthController` 无 `DELETE /my/account`。定稿要求接通 + 二次确认 + 清 token 跳登录。

**后端缺口**：
- 新增 `DELETE /my/account`（STU，可选 `{ confirm: boolean }`）：级联清理本人 `dish(created_by)`/`moment`(+`moment_comment`)/`review`/`favorite`/`view_log`/`notification`/`user_feedback`；建议**逻辑删除**（`user.status='deleted'`，与现有 active/disabled 并存），避免外键悬空；注销即失效当前 token（JWT 短失效或黑名单）。
- SecurityConfig：无需改白名单（STU 登录即通）。

**验收标准**：
- [ ] 小程序 `pages/settings` 账号注销：二次确认弹窗 + 风险提示文案。
- [ ] 注销成功：清本地 token + 跳登录页。
- [ ] 后端级联清理本人数据，归属校验严格，不误删他人。
- [ ] 注销后该用户数据不再展示（逻辑删除过滤）。

**依赖**：task-09（设置页基础）、Q4-① 决策（已拍板强制交付）。

---

## 7. task-12.9 · 菜品折扣价 + Banner 外链跳转

**归属端**：后端（折扣价字段） + 小程序（展示+跳转） + Web（编辑）

**背景**：
- 菜品无折扣价字段（A.2 缺失）。
- Banner `targetType=URL` + `targetUrl` 已支持外链（A.1 已具备，仅前端跳转实现）。

**后端缺口**：
- `dish` 表 ALTER 加 `original_price`(INT, 分)、`promo_price`(INT, 分, 可空)。
- 实体 `Dish` + `DishVO` + `DishAdminVO` + `DishAdminReq` 加 `originalPrice`/`promoPrice`（分）。
- `promoPrice` 非空时视为有折扣。

**验收标准**：
- [ ] 后端 DishVO 输出 `originalPrice`/`promoPrice`（分）；Web 菜品表单可编辑折扣价（展示元、提交分）。
- [ ] 小程序菜品详情展示折扣价（划线原价 + 促销价 + ⏰ 限时标识）。
- [ ] 小程序 Banner 点击：`URL`→`web-view`/复制链接（公众号文章/H5）；`DISH`→菜品详情；`NONE`→无响应。
- [ ] 金额分/元转换仅在前端展示层（spec §3 红线）。

**依赖**：task-03（菜品详情）、task-05（Web 菜品表单）。

---

## 8. task-12.10 · 活动模块整体移除（清理）

**归属端**：后端（清理） + 小程序（移除页） + Web（移除模块）

**背景**：`activity` 模块完整存在，定稿要求移除并入 Banner（**已拍板：整体移除**——非仅隐藏）。

**动作（✅ 已拍板：整体移除 backend activity 模块/路由/表）**：
- **后端**：彻底删除 `backend/.../activity/*` 包（含 `Activity.java` 实体 / `ActivityController` / `ActivityAdminController` / `ActivityAdminReq` / Service/Mapper 等）；删除 `activity` 表（DDL 清理）；清理 `Banner.targetType` 枚举中的 `ACTIVITY`（仅保留 `DISH`/`URL`/`NONE`，运营入口统一由 Banner URL 外链承载）。
- **小程序**：删 `pages-detail/activity.vue` + 菜品详情"活动入口"区块（定稿改由 Banner 承载运营入口）。
- **Web**：删 `views/activity/` + 路由 `activities` + `ActivityView`；菜单不再含"活动管理"。
- **一致性**：无孤儿路由/死链；搜索"activity"/"Activity"全仓清零（除无关词）。

**验收标准**：
- [ ] 后端 `activity` 包整体移除（实体/Controller/AdminController/AdminReq/Service/Mapper），`activity` 表已清；`Banner.targetType` 无 `ACTIVITY`。
- [ ] 小程序 `pages-detail/activity.vue` 删除 + 菜品详情"活动入口"区块移除。
- [ ] Web `views/activity/` + 路由 `activities` + `ActivityView` 移除。
- [ ] 运营入口统一由 Banner 承载（URL 外链/H5）。
- [ ] 全仓无 `activity`/`Activity` 残留引用（除词形巧合无关项）。

**依赖**：**已拍板**：整体移除，可直接实施，不再阻塞。task-12.13 同步移除 activity 菜单项。task-12.14（Activity startAt 对齐）随之取消。

---

## 9. task-12.11 · Web 操作日志（后端表 + Web 页）

**归属端**：后端（AOP 埋点确认） + Web（菜单分组）

**背景**：`operation_log` 表 + 实体 + `OperationLogAdminController`(`GET /admin/operation-logs`) + `OperationLogView` 已存在；需确认 `@AuditLog` AOP 切面是否实际埋点（ARCH §5 D7 规划但未在搜索结果确认）。

**后端缺口**：
- 确认/补齐 `@AuditLog(action, targetType)` 注解 + AOP 切面，覆盖：审核通过/退回、动态下架/删除、反馈处理等写操作自动写 `operation_log`（从 SecurityContext 取 adminId、HttpServletRequest 取 ip）。

**验收标准**：
- [ ] 关键 admin 写操作自动落 `operation_log`（无需手动埋点每个方法）。
- [ ] Web `OperationLogView` 归入"用户与权限/操作日志"菜单，只读查询，三态齐全。

**依赖**：已具备基础，仅补 AOP 埋点完整性。

---

## 10. task-12.12 · 收藏(favorites)端点彻底删除 + 收藏/足迹/清单 页面与入口移除（喜欢 like 保留）

**归属端**：小程序（主） + 后端（✅ 已拍板：彻底删除 `/favorites` 端点与表）

**背景**：`pages/favorite/`、`pages/history/`、`pages/lists/` 存在，定稿 descoped；`/favorites` 端点与表在 spec §3.x.6.7 原登记（**已拍板彻底删除**）。注意：**喜欢(👍/like 菜品/动态点赞)功能不随收藏移除，本期保留**（spec 喜欢红线保留，仅 favorites 删除，喜欢方案交架构师评估）。

**动作（✅ 已拍板：彻底删除 /favorites 端点 + favorite 表 + Favorite 实体/DTO，前端删除收藏页/TabBar 入口）**：
- **后端**：彻底删除 `/favorites` 端点（Controller/Service/Mapper）+ `favorite` 表（DDL 清理）；删除 entity `Favorite`/`Favorite.java`、DTO、唯一键 `uk_useful_user_review` 之外的 favorite 关联；`AuthController` 注销级联清理中移除 favorite 引用。
- **前端**：删除 `pages/favorite/`、`pages/history/`、`pages/lists/` 及其路由/TabBar 入口；profile 功能宫格移除"我的收藏/浏览足迹/美食清单"独立入口。
- **喜欢(like)保留说明**：仅移除独立的收藏(favorites)实体与收藏页，菜品/动态的 👍/like 点赞交互**本期保留、不随收藏移除**；"喜欢替代收藏"在本期的准确含义为「用已有的 like 点赞能力替代独立收藏页/收藏实体」，而非删除喜欢概念。喜欢方案的细化（如是否扩建关系表）交架构师后续评估，但现有 like 能力不得删除。

**验收标准**：
- [ ] 后端 `/favorites` 端点 + `favorite` 表整体移除；全仓无 `Favorite`/`favorites` 残留（除词形巧合无关项）。
- [ ] TabBar 固定 4 Tab（首页/发现/动态/我的），无收藏/足迹/清单 Tab。
- [ ] 无孤儿路由/死链。
- [ ] 注销级联清理逻辑中无 favorite 引用。

**依赖**：**已拍板**：彻底删除端点，可直接实施，不再阻塞。

---

## 11. task-12.13 · Web 菜单重构（分组版 + 默认落地反馈 + 审核中心）

**归属端**：Web（主）

**背景**：`router/index.ts` 扁平路由，默认 `/dashboard`；定稿要求分组菜单 + 默认落地"反馈与举报处理"。

**动作**：
- `AdminLayout` 侧边栏改为分组：
  - **内容管理**：食堂(含档口)/菜品(含折扣价)/轮播(Banner)
  - **审核与社区**：菜品审核/评价审核/动态管理
  - **用户与权限**：用户管理/管理员管理/账号设置/操作日志
  - **反馈**：反馈与举报处理
- 默认路由：`/` → 登录后 redirect `/dashboard/feedbacks`（或 `dashboard` 默认子路由为 feedback）。
- 按 C1/C5 决策移除 `activities`/`reports` 菜单项。

**Web 审核中心方案（针对 C3 新建独立 `apply_action` 表的呈现，✅ 已拍板）**：

> 推荐方案：**并入现有审核范式 + 新增「档口审核」「食堂审核」菜单项**，不另起"申请审核"独立大模块。

- **理由**：
  1. 现有 Web 已有"菜品审核/评价审核/动态管理"审核范式（统一状态机：pending/approved/rejected + 退回原因），`apply_action` 表同样走 `pending → approved|rejected`，语义一致，应复用同一范式而非新建并列范式，降低维护与认知成本。
  2. `apply_action` 表的 `entityType` 区分 dish/stall/canteen、`action` 区分 create/close/modify。其中 `dish` 类申请（新增/下架/变更）**并入现有"菜品审核"菜单**（`GET /admin/apply?entityType=dish` 复用该页面 + 行为分流：新增→建实体、下架→置 off、变更→写回字段）。
  3. `stall` 类申请新增**「档口审核」菜单项**（`GET /admin/apply?entityType=stall`），`canteen` 类申请新增**「食堂审核」菜单项**（`GET /admin/apply?entityType=canteen`）。理由：档口/食堂原先无独立审核页面（仅 dish/moment 在审核台），新建独立申请表后必须有承载入口，且二者业务字段（营业时间/位置/名称）差异大，拆开更清晰。
  4. 三个审核页（菜品/档口/食堂）共用同一审核组件（列表三态 + 通过/退回二次确认 + 退回填原因），仅 `entityType` 过滤与审批副作用不同。
- **不推荐**"新建一个顶层'申请审核中心'把所有实体申请堆一起"：会弱化档口/食堂的专属字段审阅，且与现有"菜品审核"范式割裂。
- **菜单最终结构**：
  - 内容管理：食堂(含档口)/菜品(含折扣价)/轮播(Banner)
  - **审核与社区**：菜品审核(含 dish 类 apply_action) / **档口审核(stall 类 apply_action)** / **食堂审核(canteen 类 apply_action)** / 评价审核 / 动态管理
  - 用户与权限：用户管理/管理员管理/账号设置/操作日志
  - 反馈：反馈与举报处理

**验收标准**：
- [ ] 侧边栏 4 分组，结构与定稿一致。
- [ ] 登录默认落地"反馈与举报处理"。
- [ ] 列表页三态齐全、破坏性操作二次确认（§4.10）。
- [ ] 移除 activity/report 菜单（若 C1/C5 拍板移除）。

**依赖**：task-12.10（C1）、task-12.11（操作日志）、C5 拍板。

---

## 12. task-12.14 · Activity startAt/endAt 对齐（已取消）

**归属端**：后端（仅若 C1 保留活动）

**背景**：`Activity` 实体 DB `startTime/endTime` 与 spec §3.x.6.5 裁决 `startAt/endAt` 对外漂移。

**动作**：C1 **已拍板：整体移除 activity 模块**，故本任务**取消**（无 Activity 实体，无需对齐 startAt/endAt）；相关清理并入 task-12.10。

**验收标准**：
- [ ] （取消）无 Activity 实体残留，无需 VO 对齐。

**依赖**：C1 已拍板（整体移除），本任务取消。

---

## §5. 依赖拓扑（本期）

```
C1/C2/C3/C4/C5 已拍板（需求梳理师/用户；C2 评论点赞推翻 D1、C5 报表导出移除均已在定稿确认）
   │
   ├─► task-12.1（实体贡献 + 我的提交聚合）[C3 已拍板：新建独立 apply_action 表]
   ├─► task-12.4（评论点赞 + 楼中楼）[推翻 D1]
   ├─► task-12.5（删除本人记录：评价删除端点）
   ├─► task-12.6（关联动态聚合：canteenId 端点）
   ├─► task-12.7（社区举报：feedback 字段）
   ├─► task-12.8（账号注销端点）
   ├─► task-12.9（菜品折扣价 + Banner 跳转）
   ├─► task-12.10（活动整体移除）[C1 已拍板：整体移除 backend activity 模块/路由/表]
   ├─► task-12.11（操作日志 AOP 埋点）
   ├─► task-12.12（收藏(favorites)端点彻底删除 + 页移除；喜欢 like 保留）[C4 已拍板：彻底删除 /favorites 端点与表，喜欢(👍/like)不随收藏移除]
   ├─► task-12.13（Web 菜单分组 + 默认反馈 + 审核中心）[依赖 12.10/12.11；C1 已拍板]
   └─► task-12.14（Activity startAt 对齐）[已取消：C1 整体移除 activity]
```

**并行性**：task-12.4/5/6/7/8/9/11 互不依赖，可并行；task-12.1/10/12 已拍板不再阻塞；task-12.13 依赖 12.10/12.11（C1 整体移除 activity、C5 移除 reports 均已拍板，菜单同步移除 activities/reports）；task-12.14 取消。

---

## §6. 与 spec 冲突项（交需求梳理师，禁止自行改 spec）

| 编号 | 冲突 | 处理 |
| --- | --- | --- |
| S1 | 评论点赞：定稿要求做，但 `ARCH_DECISIONS_PHASE2 §5 D1` 决策"不做" | 本文 task-12.4 推翻 D1；建议需求梳理师同步 spec §3.x.5 / ARCH §5 D1 备注"定稿已推翻" |
| S2 | 活动模块：定稿移除，但 spec §0.3.1 / §0.4 仍将 Activity 列为本期做实体 | ✅ 已拍板：整体移除 activity 模块/路由/表；由需求梳理师更新 spec（移除 Activity 相关本期条目） |
| S3 | 收藏/喜欢：定稿"移除收藏页与接口，但保留喜欢(like)点赞"，spec §3.x.6.7 保留 `/favorites` 单一概念 | ✅ 已拍板：彻底删除 `/favorites` 端点与表 + 前端收藏页/TabBar，但**喜欢(👍/like 菜品/动态点赞)本期保留、不随收藏移除**；建议需求梳理师同步 spec 移除 favorites 相关条目、并明确喜欢(like)红线保留 |
| S4 | 报表导出：定稿 Web 不做，但 spec §0.3.3 / §3.x.5 登记 `/admin/reports/*/export` | ✅ C5 已拍板移除；建议需求梳理师更新 spec 移除报表导出本期条目 |

> 以上 S1~S4 均**不自行修改 spec**，待用户拍板后由需求梳理师统一回填。
