# task-06 · 契约缺口补齐（原 task-12.1~12.13，吸收 CONTRACT_IMPACT 缺口）

> 文档性质：技术负责人派工任务（待办，含已拍板决策）。
> 权威顺序：`docs/project_spec.md`（§0.3 一致性红线、§3 API、§5.x 三端红线）> 本任务 > 原 `tasks/CONTRACT_IMPACT.md` 活缺口台账（内容已吸收进本任务）> 代码现状。
> 来源：原 `tasks/CONTRACT_IMPACT.md`（活缺口台账）+ `tasks/task-12-miniapp-web-scope.md`（12.1~12.13 定稿）。**内容要点已全部吸收进本任务。**
> 说明：`CONTRACT_IMPACT.md` / `PAGE_PLAN_PROPOSAL.md` 已从 tasks/ 删除，本任务作为其缺口项的正式落点。

## 目标
按定稿能力补齐后端/小程序/Web 三端契约缺口，含已拍板方案（独立 `apply_action` 表、活动整体移除、favorites 彻底删除等）。多数子项已拍板可直接实施。

## 状态
🔄 **待办 / 部分已拍板**。以下子项为契约缺口，按依赖拓扑推进。

---

## §1 实体贡献统一入口 + 我的提交聚合（原 task-12.1）【✅ 已拍板：新建独立 apply_action 表】
**归属**：后端 + 小程序 + Web 审核中心。

- **后端**：新建独立 `apply_action` 表（不复用实体字段），核心字段 `id`/`applicant_id`/`entity_type`(DISH/STALL/CANTEEN)/`entity_id`(新增类可空)/`apply_type`(NEW/CLOSE/CHANGE)/`status`(pending/approved/rejected)/`payload`(JSON 快照)/`reject_reason`/`created_at`/`updated_at`/`handled_by`/`handled_at`；统一审核状态机 `pending→approved|rejected`（rejected 必填原因，approved 触发副作用：新增写实体/下架置 off|closed/变更写回）；索引 `uk_entity_action_pending`（防重复待审）+`idx_applicant`+`idx_status`+`idx_entity`。
  - 学生端点：`POST /my/apply`（STU，`{ entityType, entityId?, applyType, payload }`，重提校验 pending 时 409）；`GET /my/submissions`（聚合本人 apply + 本人 moment，分实体/动态双标签，含已下架）。
  - 审核端点（ADMIN）：`GET /admin/apply?status=&entityType=&action=`；`POST /admin/apply/{id}/approve`；`POST /admin/apply/{id}/reject`(`{rejectReason}`)。
- **小程序**：profile「我要贡献」统一入口（Sheet：发布菜品/提交档口/提交食堂/申请下架或变更，均经 `POST /my/apply`）；详情页快捷「申请下架/纠错」（菜品）、「申请关闭/纠错」（档口）、「申请调整/下架」（食堂）；「我的提交」聚合页（实体/动态双标签）。
- **Web**：dish 类申请并入「菜品审核」，stall/canteen 类新增「档口审核」「食堂审核」菜单（共用审核组件，仅 entityType 过滤与副作用不同）。
- **红线**：不把申请字段塞回 dish/stall/canteen 实体（独立表承载）。

## §2 动态评论点赞 + 楼中楼（原 task-12.4）【✅ 已拍板：推翻 D1，补 useful_count + 接口】
**归属**：后端 + 小程序评论区。
- `moment_comment` ALTER 加 `useful_count`(INT, default 0) + 唯一键 `uk_useful_user_comment(user_id, comment_id)`（复刻 `uk_useful_user_review`）。
- `MomentComment` + `MomentCommentVO` 加 `usefulCount`/`useful`；新增 `POST /moments/{id}/comments/{cid}/useful`（STU，幂等切换，返 `{ useful, usefulCount }`）；关系表 `moment_comment_useful`。
- 评论区 UI：主楼 + 子回复扁平化（B站式），「共 N 条」展开/收起，「回复 @某人」可点；👍 幂等切换（emoji 语义唯一：👍=有用）。
- ⚠️ 明确推翻 `ARCH_DECISIONS_PHASE2 §5 D1`「评论 👍 不做」。

## §3 删除本人记录（原 task-12.5）
**归属**：后端（评价删除）+ 小程序。
- 动态 `DELETE /my/moments/{id}` ✅ 已具备；菜品 `DELETE /dishes/{id}`（本人 created_by）✅ 已登记。
- **新增 `DELETE /my/reviews/{id}`**（STU，仅本人 userId，级联清理 `uk_useful_user_review` 关联），返回 200/403/404。
- 小程序：评价列表/详情删除本人评价；动态/菜品删除本人记录。

## §4 关联动态双向跳转 + 聚合（原 task-12.6）
**归属**：后端（canteen 聚合）+ 小程序三详情页。
- `GET /moments?dishId=&stallId=` ✅ 已支持；**新增 `GET /moments?canteenId=`**（按食堂下全部档口 `stallId IN (SELECT id FROM stall WHERE canteen_id=?)` 聚合，复用 publicList 过滤，不污染 relatedType 语义）。
- 三详情页「关联动态」区块可点进动态详情；动态详情反向 📍 chip 跳详情（已具备）。

## §5 社区举报（原 task-12.7）【Q4-② 强制交付】
**归属**：后端（feedback 补字段）+ 小程序（举报入口）。
- `Feedback` 实体补 `relatedType`(String?)/`relatedId`(Long?)；`FeedbackReq` 补 `relatedType?`/`relatedId?`，`type=report` 时必填校验（违返 400）；`FeedbackServiceImpl` 写 `related_type`/`related_id`。
- 小程序动态详情「举报」→ `POST /feedback`(`type=report`, `relatedType='moment'`, `relatedId`)；反馈页通用反馈不变。
- 不新建举报表（红线）。Web `FeedbackView` 可筛 `type=report`，补相关列。

## §6 账号注销（原 task-12.8）【Q4-① 强制交付】
**归属**：后端（端点）+ 小程序（二次确认）。
- `AccountController` `DELETE /my/account` 已实现（见 task-12.8/A.15），级联清理本人 dish/moment(+comment)/review/view_log/notification/user_feedback；建议逻辑删除（`user.status='deleted'`）；注销即失效当前 token。
- 🔴 **阻断缺口**：`notification` 表未写入 `schema.sql`（实体/Mapper 存在，DB 无表 → 注销级联清理报 SQL 错误）。须在 `schema.sql` 补建 `notification` 表。
- 小程序 `pages/settings` 账号注销：二次确认 + 清 token 跳登录。

## §7 菜品折扣价 + Banner 外链跳转（原 task-12.9）
**归属**：后端（折扣价字段）+ 小程序 + Web。
- `dish` 表 ALTER 加 `original_price`(INT,分)/`promo_price`(INT,分,可空)；`Dish`/`DishVO`/`DishAdminVO`/`DishAdminReq` 加 `originalPrice`/`promoPrice`；`promoPrice` 非空视为有折扣。
- 小程序菜品详情展示折扣价（划线原价 + 促销价 + 限时标识，task-05 已落地）；Web 菜品表单可编辑折扣价（展示元、提交分）。
- Banner 点击：`URL`→webview/复制链接（**注意 task-07 将移除 webview，URL 跳转改复制链接**）、`DISH`→菜品详情、`NONE`→无响应。
- 金额分/元转换仅前端展示层（§3 红线）。

## §8 活动模块整体移除（原 task-12.10）【✅ 已拍板：整体移除】
**归属**：后端 + 小程序 + Web。
- 后端：彻底删除 `backend/.../activity/*` 包（实体/Controller/AdminController/AdminReq/Service/Mapper）+ `activity` 表；`Banner.targetType` 枚举去 `ACTIVITY`（仅 DISH/URL/NONE）。
- 小程序：删 `pages-detail/activity.vue` + 菜品详情「活动入口」区块。
- Web：删 `views/activity/` + 路由 `activities` + `ActivityView`；菜单不含「活动管理」。
- 一致性：无孤儿路由/死链；全仓搜索 `activity`/`Activity` 清零。

## §9 Web 操作日志（原 task-12.11）
**归属**：后端（AOP 埋点确认）+ Web。
- `operation_log` 表 + 实体 + `OperationLogAdminController`(`GET /admin/operation-logs`) + `OperationLogView` 已存在；确认/补齐 `@AuditLog(action, targetType)` 注解 + AOP 切面（覆盖审核通过/退回、动态下架/删除、反馈处理等写操作，从 SecurityContext 取 adminId、HttpServletRequest 取 ip）。
- Web `OperationLogView` 归入「用户与权限/操作日志」菜单，只读查询。

## §10 收藏(favorites)端点彻底删除 + 页移除（原 task-12.12）【✅ 已拍板：彻底删除端点与表；喜欢 like 保留】
**归属**：小程序 + 后端。
- 后端：彻底删除 `/favorites` 端点（Controller/Service/Mapper）+ `favorite` 表 + `Favorite` 实体/DTO；`AuthController` 注销级联清理中移除 favorite 引用。
- 前端：删除 `pages/favorite`/`pages/history`/`pages/lists` 及其路由/TabBar 入口（已完成，见 task-02）；profile 宫格移除「我的收藏/浏览足迹/美食清单」独立入口。
- **喜欢(like)保留**：仅移除独立收藏(favorites)实体与页，菜品/动态 👍/like 点赞能力本期保留，不随收藏移除（`DishVO.favoriteCount` 保留）。

## §11 Web 菜单重构（原 task-12.13）
**归属**：Web。
- `AdminLayout` 侧边栏分组：内容管理（食堂含档口/菜品含折扣价/轮播）/ 审核与社区（菜品审核含 dish 类 apply、档口审核、食堂审核、评价审核、动态管理）/ 用户与权限（用户/管理员/账号/操作日志）/ 反馈（反馈与举报处理）。
- 默认路由 `/` → 登录后 redirect `/dashboard/feedbacks`。
- 移除 `activities`/`reports` 菜单项（C1/C5 已拍板）。

## §12 首页广播 + find 分类/口味筛选（CONTRACT_IMPACT A.14/A.17 补充）
- **广播**：新建 `broadcast` 表 + `Broadcast`/`BroadcastVO` + `GET /broadcasts`（公开 GET，enabled，按 sort_order 升序 + created_at 降序）；首页竖直 ticker 按 `broadcast_type` 分发跳转（task-05 W6 已落地前端，后端建表/接口为缺口）。
- **find 分类**：新增 `GET /categories`（公开 GET）；`DishQueryReq` 增加 `spiceLevel` + `DishMapper.xml selectDishPage` 增加 `spice_level` 等值过滤（find 口味筛选）。

## §13 个人中心统计 VO（CONTRACT_IMPACT A.16）
- `UserStatsVO` 调整为 `publishedDishCount`/`pendingDishCount`/`favoriteCount`(占位0)/`reviewCount`（收藏数待裁定口径）；`GET /auth/stats` 聚合；前端 `StatsRow` 三宫格按此落地。

## 依赖拓扑
```
已拍板（C1/C2/C3/C4/C5，见原 CONTRACT_IMPACT §C）
  ├─ §1 实体贡献（C3 已拍板：新建 apply_action 表）
  ├─ §2 评论点赞（推翻 D1）
  ├─ §3 删除本人评价、§4 canteen 聚合、§5 举报、§6 注销(含 notification 补表)、§7 折扣价、§9 日志、§10 favorites 删除 —— 互不依赖，可并行
  ├─ §8 活动整体移除（C1 已拍板）
  └─ §11 Web 菜单重构（依赖 §8/§9）
```
- 后端契约缺口实现前，须先在本任务登记（活缺口台账），再交开发实现（§3 红线）。
