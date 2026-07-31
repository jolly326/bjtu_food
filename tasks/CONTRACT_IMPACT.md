# 契约影响评估与缺口清单（本期定稿 vs 现状）

> 文档性质：技术架构师契约影响评估（非需求文档，不修改 project_spec.md）。
> 评估依据：`project_spec.md`、`tasks/ARCH_DECISIONS_PHASE2.md`、`tasks/PAGE_PLAN_PROPOSAL.md` 与代码现状核查（backend / frontend / web）。
> 标注：✅ 已具备 / ⚠️ 缺失-需补齐 / 🔄 漂移-需对齐 / 🧹 待清理（移除）。
> 角色红线、金额分/元、camelCase 对外、emoji 语义等全局约束沿用 spec，不再重复。

---

## A. 逐条契约影响评估

### A.1 Banner 外链 URL（公众号/H5）
- **现状**：`Banner` 实体已含 `targetType`(DISH/ACTIVITY/URL/NONE) + `targetUrl` 字段；`BannerVO`/`BannerAdminVO` 已输出 `targetType/targetId/targetUrl`；后端 `/canteens/banners` 已按 `sortOrder` 返回。✅ 符合定稿要求。
- **缺口**：无（后端契约完整）。前端仅需在菜品/档口/食堂详情「申请下架/纠错」等入口之外，补充 `URL → web-view / 复制链接` 跳转逻辑（属前端 task，见 task-12.9）。
- **结论**：**已具备**，仅前端跳转实现。

### A.2 菜品折扣价字段（originalPrice / promoPrice）
- **现状**：`Dish` 实体**仅有 `price`（原价/现价，分）**，无折扣价双字段；`DishVO`/`DishAdminVO`/`DishAdminReq`/`DishPublishReq` 均无 `originalPrice`/`promoPrice`。
- **定稿要求**：菜品表加折扣价字段（`originalPrice`/`promoPrice`），Web 菜品页编辑，小程序详情页展示折扣价 + 申请下架/纠错。
- **缺口（⚠️ 缺失）**：
  1. 后端 `dish` 表需 ALTER 加 `original_price`(INT, 分)、`promo_price`(INT, 分, 可空)；实体 `Dish` + `DishVO` + `DishAdminVO` + `DishAdminReq` 加对应字段。
  2. Web 菜品表单 `DishDetailView` 增加折扣价编辑（展示元、提交分）。
  3. 小程序 `pages-detail/dish.vue` 展示折扣价（划线原价 + 促销价）。
- **契约建议**：复用 `price` 作为"原价/现价"语义有歧义——**建议**明确：保留 `price` 为常规价（分），新增 `originalPrice`(促销前原价,分) + `promoPrice`(促销价,分,可空)；`promoPrice` 非空时前端展示折扣。该建模与活动 `activityPrice/originPrice` 解耦（活动不并入菜品字段，见 A.3）。
- **结论**：**缺失，需后端 ALTER + 三端补齐**（task-12.9）。

### A.3 活动模块（activity）废弃 / 并入 Banner
- **现状**：`activity` 后端包（Entity/Controller/Service/AdminController）+ 前端 `pages-detail/activity.vue` + Web `views/activity/` 均**存在且完整**；`Activity` 实体时间字段仍为 DB `startTime/endTime`（与 spec §3.x.6.5 裁决 `startAt/endAt` 对外**漂移**）。
- **定稿要求**：活动（独立模块）移除并入 Banner；Web 不做活动管理。
- **缺口（🧹 待清理 → ✅ 已拍板：整体移除）**：
  1. 后端：✅ **已拍板：整体移除** `activity` 模块 + 路由 + `activity` 表（含 `Activity.java`/`ActivityController`/`ActivityAdminController`/`ActivityAdminReq` 等），非仅隐藏入口；`Banner.targetType` 枚举去掉 `ACTIVITY`（仅 DISH/URL/NONE，运营入口统一由 Banner URL 外链承载）。
  2. Web：移除 `activity/` 模块、路由 `activities`、`ActivityView`；菜单不再含"活动管理"。
  3. 小程序：移除 `pages-detail/activity.vue` + 菜品详情"活动入口"区块（定稿改由 Banner 承载运营入口）。
  4. `startAt/endAt` 对齐：活动整体移除，无需对齐（task-12.14 取消）。
- **结论**：**漂移+待清理 → ✅ 已拍板整体移除**（task-12.10 / task-12.13）。

### A.4 社区举报复用 feedback 表（relatedType/relatedId）
- **现状**：`user_feedback` 表 ALTER 脚本（migration_phase2.sql §2.2）规划了 `related_type/related_id` + `type`(含 report) + `status/reply/handled_at/handler_id`，但**代码未落地**：`Feedback` 实体**缺 `relatedType`/`relatedId`**；`FeedbackReq` **缺 `relatedType`/`relatedId`**，且 `type` 枚举仅 suggestion/error/other（无 report）；`FeedbackServiceImpl` 未处理举报逻辑。
- **缺口（⚠️ 缺失）**：
  1. `Feedback` 实体补 `relatedType`(String?)/`relatedId`(Long?)；
  2. `FeedbackReq` 补 `relatedType?`/`relatedId?`，`type=report` 时必填校验（service 内或 @Validated 分组）；
  3. `FeedbackServiceImpl` 写 `related_type/related_id`；
  4. 后端 `POST /feedback` 路径不变（前端契约稳定），仅升级实现。
- **结论**：**缺失，需后端补齐**（task-12.7）。Web 反馈台 `FeedbackView` 已存在（可筛 `type=report`），补齐字段即可。

### A.5 账号注销 DELETE /my/account
- **现状**：`AuthController` **无** `DELETE /my/account`；无 `AccountController`；级联清理逻辑未实现。
- **定稿要求**：接通 `DELETE /my/account`，二次确认 + 清 token 跳登录。
- **缺口（⚠️ 缺失）**：
  1. 后端新增 `DELETE /my/account`（STU）：级联清理本人 `dish(created_by)`/`moment`(+`moment_comment`)/`review`/`favorite`/`view_log`/`notification`/`user_feedback`；逻辑删除（`user.status='deleted'`）或物理删除（选一，建议逻辑删除避免外键悬空）。
  2. SecurityConfig 白名单：该端点需登录（STU），无需改白名单。
  3. 前端 `pages/settings` 二次确认 + 清 token 跳登录（前端现有 settings 页）。
- **结论**：**缺失，需后端+前端补齐**（task-12.8）。

### A.6 关联动态双向跳转 + 聚合接口
- **现状**：`GET /moments?dishId=&stallId=` 已支持菜品/档口关联过滤（✅）；`moment.relatedType` 仅 `dish/stall/none`（**不支持 canteen**）。
- **定稿要求**：菜品详情关联动态可点进；档口/食堂详情聚合关联动态。
- **缺口（🔄 部分）**：
  1. 食堂（`canteen`）关联动态：当前 `relatedType` 无 `canteen`，食堂详情聚合需改为「按该食堂下全部档口 `stallId IN (...)` 聚合」，或扩展 `relatedType` 支持 `canteen`。**建议**：后端新增 `GET /moments?canteenId=` 聚合端点（按食堂下档口批量查），不污染 `relatedType` 语义（动态仍只关联 dish/stall，食堂聚合为派生查询）。
  2. 前端：`pages-detail/dish.vue` 关联动态卡（已占位）、`pages-detail/stall.vue` 关联动态、`pages-detail/canteen.vue` 关联动态聚合（需新增 canteenId 聚合调用）；动态详情反向 📍 chip 跳详情（已存在）。
  3. 小程序"删除本人记录"中 dynamic 删除已具备（`DELETE /my/moments/{id}`），菜品/评价删除见 A.11。
- **结论**：**部分已具备**，需补 canteen 聚合端点 + 前端三详情页聚合（task-12.6）。

### A.7 动态评论点赞 + 楼中楼
- **现状**：`moment_comment` 表 + 实体已含 `parentId`（一层楼中楼 ✅）；但**无点赞列**；`MomentController` **无评论点赞接口**；`ARCH_DECISIONS_PHASE2 §5 D1` 明确"评论 👍 本期不做"。
- **定稿要求**：仅动态评论区点赞 + 楼中楼（B站式：主楼 + 子回复 + 展开"共 N 条" + "回复 @某人" + 👍 幂等）。
- **缺口（⚠️ 缺失，与既有 D1 决策冲突）**：
  1. `moment_comment` 表 ALTER 加 `useful_count`(INT) + 唯一键 `uk_useful_user_comment(user_id, comment_id)`；
  2. 实体 `MomentComment` + `MomentCommentVO` 加 `usefulCount`/`useful`(当前用户是否点过)；
  3. 新增 `POST /moments/{id}/comments/{cid}/useful`（STU，幂等切换）+ `uk_useful_user_comment` 表；
  4. 前端评论区 UI：主楼/子回复扁平化 + "共 N 条"展开 + "回复 @某人" + 👍 幂等。
- **结论**：**缺失（推翻 D1 决策）**，需后端 ALTER + 接口 + 前端 UI（task-12.4）。⚠️ 该需求与 `ARCH_DECISIONS_PHASE2 §5 D1`「评论 👍 不做」直接冲突，需用户确认是否正式推翻该决策（见 §B 建议 2）。

### A.8 操作日志表与记录点
- **现状**：`operation_log` 表 + 实体 + `OperationLogServiceImpl` + `OperationLogAdminController`(`GET /admin/operation-logs`) + `OperationLogConst` **均已存在**；但 `ARCH_DECISIONS_PHASE2 §5 D7` 规划的 `@AuditLog` AOP 切面**是否实际埋点**需核查（切面类未在搜索结果中出现，疑似仅表+手动写入，缺 AOP 自动埋点）。
- **定稿要求**：Web 操作日志（后端表 + Web 页）。
- **缺口（🔄 部分）**：
  1. 确认 AOP 切面是否落地；若未落地，需补齐 `@AuditLog` 注解 + 切面（覆盖审核通过/退回、动态下架/删除、反馈处理等 action 写 `operation_log`）。
  2. Web 端 `OperationLogView` 已存在（路由 `operation-logs`），菜单需归入"用户与权限/操作日志"。
- **结论**：**基本已具备**，需确认 AOP 埋点完整性 + Web 菜单分组（task-12.11 / task-12.13）。

### A.9 实体贡献体系（菜品/档口/食堂 新增+下架/变更）审核闭环
- **现状**：
  - 学生发布菜品：`POST /dishes` + `PUT /dishes/{id}` + `GET /my/dishes` ✅
  - 学生提交档口/食堂：`POST /my/stalls` + `GET /my/stalls`(MyStallController) ✅
  - 审核：`/admin/audit?type=dish|stall|canteen|moment` + approve/reject ✅
  - 前端 `profile/my-publish.vue`（我的发布）+ `publish-dish.vue` + `submit-stall.vue` 存在，但**无"我要贡献"统一入口**与"我的提交"分实体/动态双标签聚合（含已下架）。
- **定稿要求**：
  - "我的-我要贡献"统一入口：发布菜品/提交档口/提交食堂/申请下架或变更（菜品·档口·食堂）；可从详情页快捷发起；统一审核闭环；状态在「我的提交」聚合（分"实体/动态"两个标签，含已下架）。
- **缺口（⚠️ 缺失 → ✅ 已拍板：新建独立申请表）**：
  1. **下架/变更申请**：当前学生**只能发布/编辑重提**，无「申请下架/变更」独立通道（详情页"申请下架/纠错"按钮需后端承接）。**✅ 已拍板建模**：**新建独立 `apply_action` 表**，不复用现有实体字段。核心字段：`id`/`applicant_id`/`entity_type`(DISH/STALL/CANTEEN, 对齐 spec entityType)/`entity_id`(新增类可空)/`apply_type`(NEW/CLOSE/CHANGE, 对齐 spec applyType)/`status`(pending/approved/rejected)/`payload`(JSON 字段快照)/`reject_reason`/`created_at`/`updated_at`/`handled_by`/`handled_at`；统一审核状态机 `pending→approved|rejected`（rejected 必填原因）；索引 `uk_entity_applytype_pending`（防重复待审）+ `idx_applicant`/`idx_status`/`idx_entity`。学生统一端点 `POST /my/apply{ entityType, entityId?, applyType, payload }`（字段名与枚举对齐 spec §3.x.5）；审核端点 `GET /admin/apply` + `approve`/`reject`；我的提交 `GET /my/submissions`。Web 审核中心：dish 类并入"菜品审核"，stall/canteen 类新增「档口审核」「食堂审核」菜单（task-12.13）。
  2. 前端「我的提交」聚合页（双标签：实体/动态，含已下架）：`my-publish.vue` 升级为聚合页，或新建 `my-submissions.vue`。
  3. 详情页快捷「申请下架/纠错」（菜品）、「申请关闭/纠错」（档口）、「申请调整/下架」（食堂）：前端 Sheet 调用 `POST /my/apply`。
- **结论**：**基础闭环已具备，缺"下架/变更申请"建模 + 统一入口/聚合页**（task-12.1）。✅ "下架/变更申请"模型**已拍板：新建独立 apply_action 表**（见 §B 建议 3 / task-12.1）。

### A.10 收藏/足迹/清单 页面与入口移除
- **现状**：前端 `pages/favorite/`、`pages/history/`、`pages/lists/` **均存在**；spec 已 descoped 收藏(砍)、浏览足迹、美食清单。
- **定稿要求**：移除收藏页与接口（改为"喜欢"），移除浏览足迹、美食清单页面与入口；TabBar 4 Tab（首页/发现/动态/我的）。
- **缺口（🧹 待清理 → ✅ 已拍板：彻底删除端点）**：
  1. 前端删除 `pages/favorite/`、`pages/history/`、`pages/lists/` 及其路由/TabBar 入口；profile 功能宫格移除"我的收藏/浏览足迹/美食清单"独立入口，**保留"我的喜欢"(👍/like 菜品/动态点赞)入口**（✅ 已拍板：彻底删除 /favorites 端点与表，但喜欢(like)不随收藏移除）。
  2. 后端：✅ **已拍板：彻底删除 `/favorites` 端点（Controller/Service/Mapper）+ `favorite` 表 + `Favorite` 实体/DTO**，非仅隐藏；`AuthController` 注销级联清理中移除 favorite 引用。
- **结论**：**待清理（前端为主） → ✅ 已拍板：彻底删除 /favorites 端点与表 + 前端收藏页/TabBar**（task-12.12）。

### A.11 删除本人记录（动态/菜品/评价）
- **现状**：
  - 动态：`DELETE /my/moments/{id}` ✅
  - 菜品：`DELETE /dishes/{id}`（仅本人 created_by）— 需确认是否存在 ✅（spec §3.x.5 已登记）
  - 评价：学生删除本人评价接口——**现状核查**：`/reviews` 仅有 POST/GET；`/admin/reviews` 有 DELETE（物理删除，admin）；**学生端删除本人评价端点缺失**（⚠️）。
- **缺口（⚠️ 缺失）**：
  1. 新增 `DELETE /my/reviews/{id}`（STU，仅本人 userId，级联清理 useful 关联）。
  2. 前端：动态/菜品/评价详情页"删除"入口（动态已具备，菜品/评价需补）。
- **结论**：**动态/菜品已具备，评价删除端点缺失**（task-12.5）。

### A.12 Web 菜单重构为分组版 + 默认落地反馈与举报处理
- **现状**：`web/src/router/index.ts` 为**扁平路由**（无分组），默认落地 `/dashboard`（非反馈）。菜单项：canteens/content-review/review-review/users/admins/account/banners/activities/moments/feedbacks/reports/operation-logs。
- **定稿要求**：菜单分组（内容管理/审核与社区/用户与权限/反馈），默认落地"反馈与举报处理"。
- **缺口（⚠️ 缺失）**：
  1. `AdminLayout` 侧边栏改为分组（内容管理：食堂含档口/菜品/轮播；审核与社区：菜品审核/评价审核/动态管理；用户与权限：用户管理/管理员管理/账号设置/操作日志；反馈：反馈与举报处理）。
  2. 默认路由 `/` → `/dashboard/feedbacks`（或登录后 redirect 到 feedback）。
  3. 移除 `activities`（C1 已拍板整体移除）、`reports`（✅ C5 已拍板移除，定稿 Web 不做报表导出）。
- **结论**：**缺失，需 Web 重构**（task-12.13）。

### A.13 活动表单 ActivityAdminReq → startAt/endAt 对齐
- **现状**：`Activity` 实体 DB 列 `startTime/endTime`；`ActivityAdminReq` 字段未核查但 spec §3.x.6.5 要求对外 `startAt/endAt`。
- **定稿要求**：若活动保留，则对齐 startAt/endAt。
- **结论**：**漂移**——若 A.3 决定移除活动则无需；若保留则需对齐（依赖 A.3 决策）。

---

## B. 更优技术方案 / 契约建议（避免冗余）

1. **活动模块清理力度**：建议**整体移除** `activity` 模块（后端包 + 前端页 + Web 模块 + 路由），而非仅"隐藏入口"。理由：定稿明确定义"活动（独立模块移除并入 Banner）"，Banner 的 `ACTIVITY` target_type 亦应一并移除（Banner 仅承载 URL 外链/H5，不再跳活动详情）。若保留 activity 表仅作历史数据，则 `Banner.targetType` 枚举去掉 `ACTIVITY`。→ **✅ 已拍板：整体移除**（见 task-12.10）。
2. **评论点赞推翻 D1**：定稿要求"评论区点赞"，与 `ARCH_DECISIONS_PHASE2 §5 D1`「评论 👍 不做」冲突。建议**接受定稿**，补 `moment_comment.useful_count` + `uk_useful_user_comment` + `POST /moments/{id}/comments/{cid}/useful`，并在 task 中明确"推翻 D1 决策"。（C2 仍待拍板确认是否正式推翻 D1）
3. **下架/变更申请建模**：建议最小成本方案——复用现有审核状态机。**✅ 已拍板（修正原建议）：新建独立 `apply_action` 表**（非复用实体字段），`entity_type`(dish/stall/canteen)/`action`(create/close/modify)/`status`(pending/approved/rejected)/`payload`(JSON)/`reject_reason`；统一审核状态机；学生统一 `POST /my/apply`，审核 `GET /admin/apply` + `approve`/`reject`，我的提交 `GET /my/submissions`；Web 审核中心 dish 类并入"菜品审核"，stall/canteen 类新增「档口审核」「食堂审核」。→ **✅ 已拍板：新建独立申请表**（见 task-12.1 / task-12.13）。
4. **喜欢 vs 收藏**：定稿"移除收藏页与接口"，但 spec §3.x.6.7 保留 `/favorites`（喜欢单一概念）。建议**保留 `/favorites` 端点**，仅前端移除独立收藏页 + TabBar 入口，"喜欢"从"我的"进（profile 功能宫格保留"我的喜欢"）。若用户要彻底删 `/favorites` 端点+表，则需评估动态/菜品 ❤️ 计数展示影响。→ **✅ 已拍板：彻底删除 `/favorites` 端点与表**（原建议"保留"被推翻，见 task-12.12）。
5. **Web 报表导出(reports)**：定稿"Web 不做数据看板、报表导出"。当前 `reports` 路由 + `ReportView` 存在，建议**移除**（与活动一并清理）。`ARCH_DECISIONS_PHASE2` 已建导出端点，移除需后端一并弃用。→ ✅ **已拍板：移除 reports 模块**（本期不做报表导出，见 task-12.13）。

---

## C. 契约冲突/待用户拍板项（交需求梳理师）

| 编号 | 冲突点 | 现状 | 定稿要求 | 建议 | 需拍板 |
| --- | --- | --- | --- | --- | --- |
| C1 | 活动模块 | 完整存在 | 移除并入 Banner | 整体移除 activity 模块 + Banner.targetType 去 ACTIVITY | ✅ 已拍板：整体移除 |
| C2 | 评论点赞 | D1 决策"不做" | 要求做 | 推翻 D1，补 useful_count + 接口 | ✅ 已拍板：推翻 D1（补 useful_count + 接口，见 task-12.4） |
| C3 | 下架/变更申请 | 无独立通道 | 要求"申请下架/变更" | ✅ 新建独立 apply_action 表（统一审核状态机） | ✅ 已拍板：新建独立申请表 |
| C4 | 收藏/喜欢 | /favorites 保留(spec) | "移除收藏页与接口" | ✅ 彻底删除 /favorites 端点与表 + 前端收藏页/TabBar | ✅ 已拍板：彻底删除端点（喜欢 like 保留） |
| C5 | Web 报表导出 | reports 存在 | 不做 | 移除 reports 模块 | ✅ 已拍板：移除 reports 模块 |
| C6 | Activity startAt/endAt | 实体 startTime/endTime | 对齐(若保留) | 依赖 C1 | 依赖 C1 |

---

## D. 影响面清单（按端）

### 后端（backend）
- `dish/entity/Dish.java`：加 `originalPrice`/`promoPrice`（+ ALTER dish 表）
- `dish/dto/DishVO.java` + `DishAdminVO.java` + `DishAdminReq.java`：加折扣价字段
- `feedback/entity/Feedback.java`：加 `relatedType`/`relatedId`
- `feedback/dto/FeedbackReq.java`：加 `relatedType`/`relatedId` + report 校验
- `moment/entity/MomentComment.java`：加 `usefulCount`（+ ALTER + uk_useful_user_comment 表）
- `moment/dto/MomentCommentVO.java`：加 `usefulCount`/`useful`
- `moment/controller/MomentController.java`：加评论点赞接口
- `auth/controller/AuthController.java` 或新建 `AccountController`：加 `DELETE /my/account`
- `dish/controller/DishController.java`：加 `DELETE /dishes/{id}`(本人)
- `apply` 新模块：新建 `apply_action` 表 + 实体/Service/Mapper/Controller（✅ C3 已拍板独立申请表）；`POST /my/apply`(STU) + `GET /admin/apply` + `approve`/`reject`(ADMIN) + `GET /my/submissions`(STU)
- `review`：加 `DELETE /my/reviews/{id}`
- `moment/controller/MomentController.java`：加 `GET /moments?canteenId=` 聚合
- `activity/*`：✅ 已拍板整体移除（模块/路由/`activity` 表）
- `favorite` 模块 + `favorite` 表：✅ 已拍板彻底删除（C4）
- 操作日志 AOP 切面：确认/补齐 `@AuditLog`

### 小程序（frontend）
- `pages-detail/dish.vue`：折扣价展示 + 关联动态 + 申请下架/纠错 + 删除本人
- `pages-detail/stall.vue`：关联动态 + 申请关闭/纠错
- `pages-detail/canteen.vue`：关联动态聚合 + 申请调整/下架
- `pages-detail/moment.vue`：评论点赞 + 楼中楼 UI + 删除本人
- `pages/profile/*`：「我要贡献」统一入口 + 「我的提交」聚合（实体/动态双标签）
- `pages/settings`：账号注销二次确认
- `pages/feedback`：举报入口（relatedType=relatedId）
- `pages/favorite`、`pages/history`、`pages/lists`：✅ 已拍板彻底删除（C4，含 /favorites 端点）
- `pages-detail/activity.vue`：✅ 已拍板整体移除（C1）

### Web（web）
- `router/index.ts`：分组菜单 + 默认落地 feedback
- `views/layout/AdminLayout.vue`：侧边栏分组
- `views/activity/*`：✅ 已拍板整体移除（C1）
- `views/admin/ReportView.vue`：按 C5 移除
- `views/canteen/DishDetailView.vue`：折扣价编辑
- `views/admin/FeedbackView.vue`：举报件筛选（relatedType 展示）
- `views/admin/OperationLogView.vue`：归入"用户与权限"
