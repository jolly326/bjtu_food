# 产品定型总纲 v1.6（2026-09-15）

> **v1.7 变更（2026-09-16 用户拍板「v1 基线冻结」，权威 `project_spec.md` §7.23 第 6 条）**：① **6 个零消费列删除**——`user.password` / `user.unionid` / `dish.reject_reason` / `dish.created_by` / `user_feedback.handler_id` / `user_feedback.contact`（数据模型表已同步；**表基线仍 10 张**）；② **`GET /reviews` 维度收敛**——`stallId`/`canteenId` 参数删除、`dishId` 必填；③ **评分口径定稿「通过即收录」**——聚合仅 `is_hidden=0`、无其他过滤，一次性重算 SQL 已交付用户（部署时可选执行）；④ **BCNF 声明**（见 `docs/database.md` 文首对账注）；⑤ **术语正名**——「机检 / 机审」统一为「微信内容安全检测（`msgSecCheck`/`imgSecCheck`）」，用户原话：「评价的文本与图片通过微信内容安全检测即收录发布，不通过即拒绝；无任何人工环节」；⑥ **v1 基线冻结**——任何改动须先修订 §7.23 并留痕 §8 再动代码。
> **v1.6 变更（2026-09-15 用户拍板两项，权威 `project_spec.md` §7.25）**：① **管理端「操作日志」全链删除**——删 `@AuditLog` 注解 / `AuditLogAspect` 切面 / `OperationLogConst` / `OperationLogAdminController` / `OperationLogVO` / `OperationLog` 实体 / `OperationLogMapper` / `OperationLogService`(+`Impl`) 与 4 处 `@AuditLog` 调用点；`operation_log` 表不再创建（`schema.sql` 末尾幂等段 `drop_operation_log_table`）；Web 删 `OperationLogView.vue` / `api/operationLog.ts` / `OPERATION_*` 常量；`GET /admin/operation-logs` 列入「已删端点」。**数据表基线 11 → 10**。**保留**：`ClientIpUtil`（仅服务 `RequestLoggingFilter` 访问日志）、`view_log` 浏览足迹（**不动**）。连带注销 v1.4 的「`OperationLogConst` 的 `category_*` 四值」待收尾项。② **Web IA 与页面扁平化**——一级导航 **4 项与路由 1:1**：**菜品** `/dashboard/content`（默认落地）/ **评价** `/dashboard/reviews` / **反馈** `/dashboard/feedback` / **学生账号** `/dashboard/system`；**删中间层**：原「信息管理」聚合壳 `ContentManageView`、原「用户与系统」分类卡层 `SystemManageView` 与其下 `AccountView` 透传壳（路由直指 `DishManageView` / `UserView`）；**页面层级统一 ≤2 层**，全站删 `.stat-inline` 只读统计、页头解释句与只读提示块；菜品详情删 `detail-tabs` / 「数据统计」分区与 2×StatCard / 页内 inline 编辑（统一走 `DishFormDialog`），评价详情抽屉抽公共组件 `ReviewDetailDialog`（评价 / 反馈两页共用，删除入口只保留一处）；反馈页举报类关联评价改主色文本链接「评价 #id →」（`?rid=` 深链）；旧 `/dashboard/audit` 兜底重定向保留并扩为 `tab=feedback*` / `apply*` → 反馈页、其余 → 评价页（整份保留 query）。**恢复须重新拍板**（PR-04）。
>
> **v1.5 变更（2026-09-15 用户拍板「取消人工复核」，权威 `project_spec.md` §7.24）**：① **内容安全判定归一为二态**——内容安全检测 `pass` / `review`（疑似）**一律放行**，仅 `risky`（含未知 / 缺失态 fail-closed 同按 risky）与图片违规 `87014` 拒绝（HTTP `400`、不落库）；**取消人工复核**（无复核队列、无放行 / 驳回动作）。② **内容安全态 `sec_state` 全链退役**——`review.sec_state` / `user_feedback.sec_state` 两列（存量库由 `schema.sql` 末尾幂等段 `drop_sec_state_columns` 清理）、`SecStateConst`、复核端点 `PUT /admin/reviews/{id}/sec-state`、`OperationLogConst.ACTION_REVIEW_SEC_STATE`、评价 / 反馈列表 `secState` 筛选入参、VO / DTO 的 `secState` 字段、`viewerId` 一并删除；**评价可见性与评分聚合口径收敛为仅 `is_hidden=0` 单一判据**。③ **管理端 IA 由 3 项改为 4 项**——删除 `AuditManageView`（「内容审核」聚合页），`ReviewAuditView` 改名 `ReviewManageView`（`/dashboard/reviews`，**评价管理**，只做隐藏 / 显示 / 删除），`FeedbackView` 独立为**反馈处理**页（`/dashboard/feedback`）；一级导航 = 信息管理 / 评价管理 / 反馈处理 / 用户与系统；旧 `/dashboard/audit` 兜底重定向。④ **保留的事后处置**：`is_hidden`、`PUT /admin/reviews/{id}/hide`、`DELETE /admin/reviews/{id}`、`DELETE /reviews/{id}`；**小程序端删除 `secState` 字段与「审核中」提示，保留 `isHidden`「已被隐藏」**。⑤ **一次性重算脚本 `db/fix_rating_by_sec_state.sql` 已删除**（前提列退役，原 Q-120 授权作废）。
>
> **v1.4 变更（2026-09-15 用户拍板，权威 `project_spec.md` §7.22 第 1 条：**撤销**原 Q-117「后台保留品类作归类用途」口径）**：① **品类（category）维度整链删除**——`category` 表、`dish.category_id` 列与 `idx_dish_category` 索引、`/admin/categories` 全链（Controller / Service / Mapper / 实体）、Web 品类维护页（`CategoryManage`）与首页配置入口（`HomeConfigView`）、菜品表单分类下拉、菜品列表品类筛选与分类列、端上 `api/category.ts` **全部移除**；**数据表基线 12 → 11**；② **定型口径**：**菜品按食堂 / 档口归属，不存在分类维度**（端上无、后台亦无）——原「品类只在后台存在、只服务管理员归类」的边界表述**整体作废**；③ 本文档数据模型表 / 关系式 / 接口清单同步收敛，`/admin/categories` 列入「已删端点」；④ **待对齐**：`db/schema.sql` / `seed_data.sql` 的品类残留（建表 / 列 / 索引 / 种子数据）与 `OperationLogConst` 的 `category_*` 四值尚未收口，登记见 `project_spec.md` §8「待收尾」。
>
> **v1.3 变更（对齐 `project_spec.md` §7.23 第 4 条 + 2026-09-15 阶段4 用户批准）**：① **`dish.audit_status` 全量退役**——**列与索引已删除**（`schema.sql` 末尾幂等段 `drop_dish_audit_status_column`；`DishConst.AUDIT_APPROVED` / `AuditStatusConst` / `normalize_dish_audit_status.sql` 一并删除）；**公开查询不再按该列过滤，`status='on'` 即公开展示**——原「列保留、仅作公开查询过滤」「存量非 `approved` 由归一脚本处理」口径作废；`dish.reject_reason` 保留为退役历史列（恒 NULL；**2026-09-16 v1.7 修订：该列已随零消费清理删除**）；② **`canteen.created_by` / `stall.created_by` 两列退役**（DROP 归入 `drop_canteen_stall_entity_fields`）；③ **`FeedbackAdminVO` 新增 `relatedDishName`**（仅 `relatedType='dish'` 填充，服务端批量查询、含已下架菜品），并登记跨端边界：**Web 不得调用公开 `GET /dishes/{id}` 取菜品名**；④ **Web 目录重组**——`web/src/views/` 收敛为 `audit/ content/ system/ layout/` 四目录（原 `admin/ canteen/ user/` 已合并，**路由 path / name 未变**）；⑤ **npm 为唯一包管理器**（仓库仅 `package-lock.json`，禁引入其他锁文件）；⑥ 测试资产登记（见 `architecture.md` §7）；⑦ seed 素材收敛：`uploads/images/seed/` 仅保留 Swagger 示例引用的 `tomato-egg.jpg`。
>
> **v1.2 变更（对齐 `project_spec.md` §7.23「项目框架与蓝图 v1」+ 2026-09-15 优化 Loop）**：① 纳入五条框架原则——菜品为唯一核心实体、食堂 / 档口仅为菜品筛选属性（随菜品 upsert 自动入库，**无独立建档 / 删除接口**）、UGC 仅评价 + 反馈两形态且**评价「有用」点赞需学号邮箱认证（`verified`，未认证 4031）**、学生对菜品的一切诉求 = 反馈（`add/error/report/suggestion`）、**菜品无独立审核**（`dish.audit_status` 退役为历史列，存量归一 `approved`；**该口径已被 v1.3 取代：列与索引已删除、`status='on'` 即公开展示**）且**反馈处理为唯一运营闭环**（不采纳 / 退回必填 `reject_reason`）；② 数据模型同步已删列（`dish.serve_period`/`limited`/`portion`、`review.tags`、`stall.business_hours`、食堂 / 档口实体语义 6 列）；③ 接口清单同步已删端点（`/dishes/hot|new|promotions|rising|recommend`、`/admin/admins`、`/admin/audit/**`、`/auth/admin/login`）与已删距离字段（后端 `DishVO.distance` 已删除，距离一律由端上本地计算）；④ `DataInitializer` 已整体删除，种子以 `db/seed_data.sql` 为唯一基线。

> **v1.1 变更**：纳入第四 / 五 / 六轮 PM 问答决议 —— 菜品图片来源与首图必填规则、价格由纠错反馈驱动、录入即上架、通知仅回执、管理端单管理员无角色、上线门槛（先图后上）、详情页展示信息更新时间；并落地首图双校验（后端 + 管理端表单）与 `updatedAt` 字段。

> **效力说明**：本文件与 `project_spec.md` §0.0《产品定型一页纸》共同构成产品宪法。
> 冲突时：**原则以 §0.0 为准，细节以本文件为准**。任何改动若与本文件冲突，**必须先修订本文件（重新拍板）再动代码**。
> 本文件全部内容以「代码中已实现 + 2026-09-13 三轮 PM 问答拍板」为事实源，不含未经确认的发挥。

---

## 1. 产品定位与边界

**一句话**：交大人的「吃什么不踩雷」——校园菜品信息展示与检索平台：把食堂菜品结构化、可搜索、可信评价，用户反馈经安检与审核回流为高质量信息。

**做（四条主线，唯一投入方向）**
1. **菜品信息展示**：菜品为唯一核心实体，食堂 / 档口仅为其筛选属性；展示静态信息（名称、价格、食堂 / 档口、楼层、窗口号、口味辣度、风味 / 菜系、图片）。
2. **搜索与查找**：关键词（含别名）、多维筛选（食堂 / 价格 / 辣度等；**品类维度已于 2026-09-15 整链删除，端上 / 后台均无**）、热搜；「猜你喜欢」接口已随端上零消费下线，浏览足迹仅作浏览量当日去重判据与浏览计数来源。
3. **评价类 UGC**：认证学生（`verified=true`）对菜品打星 + 文字 + 配图；可点赞（「有用」，需认证，未认证 4031 弹 `AuthSheet`）、可删除本人评价。
4. **反馈 / 贡献类 UGC**：意见反馈、新增菜品（`add`）、纠错 / 申请下架（`error`）、举报（`report`）——管理员在反馈处理中审阅后录入 / 修改 / 上下架，结果以站内回执通知（不采纳 / 退回必填 `reject_reason`）。

**内容运营规则（2026-09-13 第四 / 五 / 六轮拍板）**
- **菜品图片**：管理员（运营 / 后勤）实拍上传，每道菜 1-3 张；**首图必填** —— 无图不录入、不上架，管理端表单与后端双校验（已上架老数据不受影响）；不使用网图，**不启用占位图**。
- **价格 / 促销价**：以管理员录入为准（**不设有效期字段**）；学生发现不符走「信息纠错」反馈，管理员核实后修改 —— 价格准确性由反馈闭环驱动。
- **录入即上架**：管理端录入 / 修改直接生效，**无二次审核**。**（2026-09-15 修订：原「操作日志（operation_log）留痕」已随操作日志全链删除作废——管理端不提供任何操作留痕 / 审计追溯，见 v1.6 变更与 `project_spec.md` §7.25 第 1 条。）**
- **通知**：**仅「反馈处理回执」一种站内通知**，不做其它通知与推送。
- **管理端**：**单一使用者、无角色概念、无登录** —— Web 后台是本地数据操作工具，打开即用；已删除 `/auth/admin/login`、`/admin/admins`（管理员账号管理）、`PUT /admin/users/{id}/role`（角色切换）与 `super_admin` 角色。**保留 `/admin/users` 用户列表与禁用**（运营需要）。
- **管理端安全**：后端部署在公网，因此 `/admin/**` 由 `AdminTokenFilter` 校验请求头 `X-Admin-Token` 是否等于环境变量 **`ADMIN_TOKEN`**；Web 侧在本地 `.env.local` 配 `VITE_ADMIN_TOKEN`（同一口令）自动携带，用户无感。**未配置口令时 fail-closed：全部管理端请求返回 403**，避免公网裸奔。
- **上线门槛**：现有 31 道菜需**逐道补齐实拍图后上线**（先图后上，不用占位图）；上线后随拍随补、随纠错随改。

**明确不做（防跑偏红线）**
- 社交 / 动态 / 社区广场、关注、私聊、收藏、点赞之外的互动
- 活动、公告、运营位、首页人工置顶
- 学生直接建菜品卡（`POST /PUT /DELETE /dishes`）—— 信息发布源唯一 = 管理员
- 售罄 / 今日供应即时状态（二期评估）
- 微信订阅消息推送（二期评估）
- Excel 批量导入 / OCR 菜单识别（二期，导入通道预留）
- 多校区数据隔离（`region` 字段预留，一期不做）

---

## 2. 角色与状态机

| 角色 | 获取方式 | 能力 |
|---|---|---|
| **游客** | 打开小程序 → `wx.login` 静默登录自动建号（`verified=0`） | 浏览 / 搜索 / 看详情 / 提交反馈（可配图）；**不能**写评价、点赞、看系统通知 |
| **认证学生** | 学号 + `@bjtu.edu.cn` 邮箱验证码（`verified=1`） | 游客全部 + 写评价（可配图）、评价有用、删除本人评价、系统通知与回执 |
| **管理员 ADMIN**（user 表仅承载学生、无角色字段；`user.role` 列已于 2026-09-15 删除，无 `SUPER_ADMIN` 权限分层） | Web 管理后台**无登录体系**：请求头 `X-Admin-Token` == 环境变量 `ADMIN_TOKEN`（`AdminTokenFilter`，未配置 fail-closed 403），打开即用 | 菜品录入（**录入即生效、无菜品审核**）与上下架；食堂 / 档口为筛选属性字典，**随菜品按名 upsert 自动入库**，无独立建档 / 删除（仅新增 / 改名 / 列表查看）；反馈处理（**唯一运营闭环**，回复必填、不采纳 / 退回必填 `reject_reason`）；评价**事后处置**（隐藏 / 显示 / 删除；**2026-09-15 取消人工复核后不再承担安检复核**，见 v1.5 变更）；学生账号管理。**~~操作日志~~ 已删除（2026-09-15，见 v1.6 变更）** |

**状态迁移**
```
游客 ──学号邮箱验证码──▶ 认证学生 ──注销（匿名化）──▶ 游客（新 openid 取号 = 全新游客号）
游客 ──触发需认证写操作──▶ 后端 4031 + 前端弹认证引导（AuthSheet）
```
- 游客触发需认证写操作：后端返回 **4031**（非 403），前端提示「请先完成学号邮箱认证」并弹认证弹层。
- **注销**为匿名化：评价 / 反馈保留但去身份化（昵称显示「已注销用户」），`openid` 解绑，`status=deleted`，token 立即失效；同一微信再次打开会建立**新游客号**。

---

## 3. 信息架构与页面（9 页，底部 Tab 2 项）

**TabBar（固定两项）**：`首页` / `我的`（无中心按钮）

| 分包 root | 页面 | 路径 | 功能与交互要点 |
|---|---|---|---|
| 主包 | 首页 | `pages/home/index` | 顶部搜索框（跳 `find`）、筛选（食堂 / 价格 / 排序；**品类维度已整链删除、无品类筛选**，spec §7.19 第 3 条与 §7.22 第 1 条）、菜品瀑布流（综合热度排序，分页加载，距离由端上本地计算）、无结果时展示贡献卡片 |
| 主包 | 搜索 | `pages/find/index` | **发现态**（无词 / 无筛选）：搜索历史（≤4 条，可清空与单条删）+ 猜你想搜；**结果态**：结果列表 + 筛选 + 无结果引导（换词提示 + 去反馈「推荐菜品」）；请求中静默、请求失败显示「加载失败 · 点击重试」 |
| 主包 | 我的 | `pages/mine/index` | 用户卡（默认头像占位、昵称 / 游客编号；游客点 → 认证弹层，认证点 → 编辑资料）、**一行 3 列宫格**（意见反馈 / 系统通知 / 我的评价，通知未读红点）、底部信息区（版本 / 学校 / **隐私胶囊 + 注销账号**） |
| detail | 菜品详情 | `pages/detail/dish/index` | 菜品图、名称、价格（原价 / 促销价）、档口 · 食堂 · 楼层 · 窗口号、辣度 / 风味 / 菜系、评分与分布、评价列表（有用数优先，含配图；**无「审核中」标**——2026-09-15 取消人工复核后仅保留 `isHidden`「已被隐藏」标注）、写评价入口（需认证）、「有用」点赞（需认证，未认证 4031 弹 `AuthSheet`）、「信息有误？」纠错入口、**信息更新于 X（dish.updated_at，今日 / 昨天 / N 天前 / 具体日期）**、进入时上报浏览埋点 |
| feedback | 意见反馈 | `pages/feedback/index` | 反馈类型（建议 / 新增菜品 / 纠错 / 举报等）、文本 + 配图 ≤3（压缩后上传）、游客可提交、提交后经安检入库，管理员处理 → 站内回执 |
| notifications | 系统通知 | `pages/notifications/index` | 需认证；反馈回执与系统通知列表、已读标记 |
| my-reviews | 我的评价 | `pages/my-reviews/index` | 本人评价列表（分页、删除、空态双口径），删除后菜品评分刷新 |
| profile | 个人资料 | `pages/profile/index` | 昵称（改昵称过 `msgSecCheck` scene=1）、头像（产品不支持上传，默认占位）；注销入口在「我的」页底部 |
| privacy | 隐私政策 | `pages/privacy/index` | 政策与用户协议、数据保留与注销说明 |

> 注册页面数 **9**（主包 3 + **6 个分包 root 各 1 页**：`detail` / `profile` / `notifications` / `feedback` / `my-reviews` / `privacy`），与 `pages.json` 一致；个人中心域五页自 **2026-09-15** 起**各自独立分包**（原 `me` 聚合分包已删除，root 2→6、页面数与主包体积不变，见 spec §2.1.2 / §8）；新增 / 删除页面须同步 spec `client-page-structure`。

---

## 4. 核心流程

1. **浏览 / 搜索**：首页（筛选）→ 瀑布流 → 详情（**2026-09-15 修订：原「首页（品类 / 筛选）」的品类入口已随品类维度整链删除移除，见 §3 与 v1.4 变更**）；搜索页发现态 → 结果态（筛选）→ 详情。请求中静默、失败可重试、无结果走引导。
2. **评价**：认证用户 → 星 + 文 + 图（≤3）→ 后端 `msgSecCheck`（scene=2）→ `pass` **与 `review`（疑似）均立即放行可见**（**无人工复核、无「审核中」态**，见 v1.5 变更）；`risky` 拦截并提示（不落库）。
3. **反馈**：游客亦可提交 → 文本 `msgSecCheck`（scene=2）、图片 `imgSecCheck` → 入库 → 管理员处理：**回复必填**；不采纳 / 退回必填 `reject_reason` → 生成站内通知回执（游客不投递、不阻塞）。
4. **认证**：学号 → 60s 限频发送验证码（10 分钟有效）→ 校验 → 绑定邮箱与微信 → `verified=1` → 刷新 token。
5. **注销**：底部入口 → 二次确认（明示不可恢复）→ `DELETE /auth/account` 匿名化 → 本地清态 → 新游客态。
6. **通知回执**：管理员处理反馈 → `notification` → 通知中心 → 已读标记 + 未读红点。（**评价侧无审核结论通知**——2026-09-15 取消人工复核后已无复核动作，见 v1.5 变更。）
7. **管理端信息维护**：菜品录入（含别名；食堂 / 档口按名 upsert 自动入库，**录入即生效、无独立审核环节**）→ 上下架（下架 = 客户端完全不可见、评价保留）→ 反馈处理（唯一运营闭环）→ 评价**事后处置**（隐藏 / 显示 / 删除，**无安检复核**）。（**2026-09-15 修订：原「→ 操作日志留痕」已随操作日志全链删除移除，见 v1.6 变更与 `project_spec.md` §7.25 第 1 条。**）

---

## 5. 数据模型（10 张表，唯一权威 `server/src/main/resources/db/schema.sql`；**2026-09-15 品类表下线后基线 12 → 11（见 v1.4 变更），随后操作日志表下线 11 → 10（见 v1.6 变更）**）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `user` | id, username, email, nickname, avatar, status, openid, verified, bind_email, verified_at | 微信取号（openid 唯一）；**user 表仅承载学生**（`role` 与 `last_login_at` 列已于 2026-09-15 冗余清理删除；`role` 曾为 student/admin 两层数据语义，admin 值无生产者）；~~`password` / `unionid`~~ **两列已于 2026-09-16 零消费清理删除**（管理端口令制 + 学生无密码体系、单应用无 unionid 消费；BCrypt 仅用于验证码哈希）；`status` 含 `active/disabled/deleted`；注销后 openid 置空 |
| `canteen` | name, images, location, description, latitude, longitude, sort_order | 食堂（**筛选属性字典**：实体语义列 `status`/`audit_status`/`reject_reason` 与归属列 `created_by` 均已下线；仅新增 / 改名 / 列表查看，无删除；随菜品 upsert 自动建档） |
| `stall` | canteen_id, name, images, location, floor, window_no, description, sort_order | 档口（楼层 / 窗口号保留；**仅为菜品筛选属性字典**，`business_hours`、实体语义列与 `created_by` 已下线） |
| `dish` | stall_id, name, **alias**, price, original_price, promo_price, description, images, tags, region, spice_level, status, view_count, avg_rating, rating_count | 菜品；**金额一律「分」**；`alias` 逗号分隔供搜索；`region` = 风味 / 菜系（非校区）；`serve_period`/`limited`/`portion`/`audit_status`/**`category_id`**/**`reject_reason`**/**`created_by`** 已下线（后两者 2026-09-16 零消费清理删除）；`status=off` 客户端不可见（**`status='on'` 即公开展示，菜品无独立审核、公开查询不按审核列过滤**）；「不采纳 / 退回」语义由 `user_feedback.reject_reason` 承载 |
| `review` | user_id, dish_id, rating, content, images, useful_count, is_hidden | 评价（一人一菜一评）；`review.tags` 已下线；**`sec_state` 列已全链退役（2026-09-15 取消人工复核）**，评价可见性唯一判据 = `is_hidden=0`；配图 ≤3 |
| `review_useful` | user_id, review_id | 评价「有用」标记（唯一性由业务保证） |
| `notification` | user_id, type, title, content, related_id, is_read | 站内通知 / 反馈回执（`feedback_handle` 为唯一在产类型） |
| `user_feedback` | user_id, type, **sub**, content, images, status, reply, **reject_reason**, related_type, related_id, handled_at | 反馈 / 举报 / 纠错 / 新增菜品（类型白名单 `suggestion/add/error/report`）；**`sub` = 二级类型 `idea`/`problem`（仅 `suggestion` 有效，写入白名单、非法 400，后台展示「建议·想法 / 建议·问题」，不新增筛选维度）**；`related_type='review'` 表示举报评价；处理结论 `outcome`（handled/rejected）为请求级字段，`rejected` 必填 `reject_reason`（随回执展示）；~~`contact` / `handler_id`~~ **两列已于 2026-09-16 零消费清理删除**（产品定型「不收集联系方式」；单口令即单人、不追究操作人身份） |
| `email_verification_code` | email, code_hash, purpose, expires_at, used_at | 认证验证码（`code_hash` 存储） |
| `view_log` | user_id, target_type, target_id | 浏览埋点（**浏览量当日去重判据（HistoryService 判重）与浏览计数来源**；「猜你喜欢」接口已下线，**与管理员操作留痕无关，2026-09-15 明确保留不动**） |

> **已删除表：`operation_log`（操作日志）——2026-09-15 全链删除（见 v1.6 变更与 `project_spec.md` §7.25 第 1 条）**：表不再创建（存量库由 `schema.sql` 末尾幂等段 `drop_operation_log_table` 清理），原字段 `admin_id` / `action` / `target_type` / `target_id` / `ip` 与索引 `idx_op_admin_time` / `idx_op_target` 一并作废；**表基线 11 → 10**。对照 `docs/database.md` §2 / §3.11。

**关系**：`canteen 1─n stall 1─n dish`；`dish 1─n review 1─n review_useful`；`user 1─n {review, review_useful, notification, user_feedback, view_log}`。**~~`dish.category_id → category`~~ 已随品类维度整链删除移除（2026-09-15，`category` 表与本表去 `category_id`，见 v1.4 变更）**。
**全局约束**：金额以「分」存储与传输；无外键（应用层保证）；评价 / 反馈配图 ≤3 张（COS 绝对地址 JSON）。
**已下线列（2026-09-15 用户拍板「取消人工复核」）**：`review.sec_state` / `user_feedback.sec_state`（原三态安检态 `pass`/`review`/`rejected`）——**随人工复核取消失效并全链退役**，CREATE TABLE 不再创建、存量库由 `schema.sql` 末尾幂等段 `drop_sec_state_columns` 清理（可重跑）；**该次为纯列级变更、表基线未动**（**当时为 11 张；后随 v1.6 操作日志表下线，当前基线为 10 张**）；`SecStateConst` / 复核端点 / VO·DTO 的 `secState` 字段 / 列表筛选入参同批删除，一次性重算脚本 `db/fix_rating_by_sec_state.sql` 已删除（见 v1.5 变更）。

---

## 6. 接口契约（现网实际路径）

**公开 / 游客可读**：`GET /canteens`、`/canteens/all`、`/dishes`（搜索 / 筛选 / 排序，keyword 命中 name 或 alias）、`/dishes/hot-search`、`/dishes/{id}`、`POST /auth/wechat-login`、`POST /feedback`（公开提交）。**已删端点（勿再引用）**：`/dishes/hot|new|promotions|rising|recommend`（2026-09-14 端上零消费下线）、`/categories` 与 `/dishes/{dishId}/reviews`（2026-09-15 三端零调用删除，评价走 `GET /reviews?dishId=`）。
**登录态**：`POST /dishes/{id}/view`、`POST /reviews`、`DELETE /reviews/{id}`、`POST /reviews/{id}/useful`（**需 `verified=true`**，未认证 4031）、`GET /my/reviews`、`GET /my/notifications`、`/my/notifications/unread-count`、`PUT /my/notifications/{id}/read`、`GET|PUT /auth/profile`、`POST /auth/email-code`、`POST /auth/verify-email`、`DELETE /auth/account`（注销）、`POST /upload/images`（fileId→COS URL）、`POST /upload/image`（multipart，管理端用）。
**管理端 `/admin/**`**（无登录体系，`X-Admin-Token` 口令把关）：`dishes`（含食堂 / 档口按名 upsert）、`/canteens`、`/stalls`、`/reviews`（**`{id}/hide` 隐藏·显示 / `DELETE {id}` 删除——均为事后处置**；原 `secState` 筛选与 `{id}/sec-state` 端点已随 2026-09-15「取消人工复核」删除）、`feedbacks`（处理结论 `outcome`，`rejected` 必填 `reject_reason`）、`users`。**已删端点（勿再引用）**：`/admins`（管理员账号管理）、`/audit/**`（实体审核）、`/auth/admin/login`、**`/operation-logs`（操作日志；2026-09-15 全链删除，见 v1.6 变更）**、**`/categories`（品类维度整链删除；2026-09-15 用户撤销原 Q-117「后台保留归类用途」口径，`category` 表 / `dish.category_id` / Web 品类维护页一并移除，spec §7.22 第 1 条）**、**`/dashboard`（工作台总览；2026-09-15 用户拍板「去工作台」，页面与接口一并删除，spec §0.4.1）**。
**管理后台信息架构（2026-09-15 IA 扁平化重写，见 v1.6 变更；一级导航 4 项、与路由 1:1、页面层级 ≤2）**：**菜品** `/dashboard/content`（`DishManageView`，含菜品详情 `/dashboard/content/dishes/:dishId`）/ **评价** `/dashboard/reviews`（`ReviewManageView`，隐藏·显示·删除 = 事后处置）/ **反馈** `/dashboard/feedback`（`FeedbackView`，唯一运营闭环）/ **学生账号** `/dashboard/system`（`UserView`）；**默认落地页 = `/dashboard/content`**（带 `?tab=dish` 的历史写法作废）；**无「全局聚合看板」**——待办可见性由**「反馈」入口徽标**（待处理反馈数）+ 各业务页**表格 footer 统计**承担；**全站禁 `.stat-inline` 只读统计块、禁页头解释句与只读提示块**。**已删除的中间层 / 页面（不得重建）**：`ContentManageView`（原「信息管理」聚合壳）、`SystemManageView`（原「用户与系统」分类卡层）、`AccountView`（透传壳）、`AuditManageView`（原「内容审核」聚合页）、`OperationLogView`（操作日志，2026-09-15 全链删除）。**原「内容审核」聚合页（`AuditManageView`）与 `/dashboard/audit` 路由已删除**，旧深链兜底重定向**保留并扩为**（`tab=feedback*` / `apply*` → `/dashboard/feedback`，其余 → `/dashboard/reviews`，**整份保留 query**）。**公共组件**：评价详情抽屉抽为 `ReviewDetailDialog`（评价 / 反馈两页共用，删除入口只保留一处）。

**错误码（固定）**：`200 / 400 / 401 / 403 / 4031 / 500`；安检违规、违规图片、未配置存储等一律 **400**；游客触发需认证写操作 **4031**。

---

## 7. 内容安全与审核（合规底线）

- **文本**：`msgSecCheck` v2（`content`≤2500 字、`openid`、`scene`、`version=2`）——`scene=1` 昵称，`scene=2` 评价与反馈。
- **图片**：`imgSecCheck`（≤1MB、最长边 ≤1334，前端 `wx.compressImage` 压缩后上传；87014 = 违规拦截）。
- **判定（2026-09-15 归一为二态、无人工复核）**：以 `result.suggest` 为准（**不得只看 errcode**）——`pass` **与 `review`（疑似）均放行**（`SecSuggest.fromValue("review") → PASS`）/ `risky` 拦截（HTTP `400`、不落库）。未知 / 缺失值 fail-closed 按 risky（拦 `400`）；安检上游异常（已配置凭据时）fail-closed 返回 500；未配置凭据（本地开发）跳过；`openid` 为 NULL 的历史账号跳过检测。**内容安全检测结论不落库、不构成可见性闸门**（评价可见性唯一判据 `is_hidden=0`）。
- **链路**：`wx.cloud.uploadFile` → 云存储 fileID → 后端 `tcb/batchdownloadfile` 拉取 → `imgSecCheck` → 转存 **COS**（永久）→ 返回 URL；`access_token` 用 stable_token 缓存。
- **复核（已废止，2026-09-15）**：~~管理后台按 `secState=review` 筛选，放行（`pass`）/ 驳回（`rejected`）~~ ——**取消人工复核**：`secState` 列与复核端点全链退役，管理端不设复核队列，仅保留事后处置（隐藏 / 显示 / 删除）。（**2026-09-15 修订：原「并埋操作日志」已随操作日志全链删除移除，见 v1.6 变更。**）

---

## 8. 搜索与推荐算法（定型，改动须重新拍板）

- **热度排序**（`DishMapper.xml` 实际）：`view_count×1 + rating_count×100 + avg_rating×20`（浏览 + 评价数 + 评分聚合，**无收藏维度**）。
- **搜索**：`keyword` 命中 `name` 或 `alias`（管理员配别名）；筛选支持食堂 / 价格 / 辣度 / 标签（**`categoryId` 已随品类维度整链删除移除，`DishQueryReq` 无该字段，见 v1.4 变更**）；排序 /排除 ID。
- **热搜 TOP10**：由菜品热度派生（一期无搜索词埋点）。
- **猜你喜欢**：**已随 2026-09-14 端上零消费接口下线整体删除**（`GET /dishes/recommend` 不存在）；`view_log` 浏览足迹现仅作**浏览量当日去重判据（HistoryService 判重）与浏览计数来源**（2026-09-15 口径修正）。

---

## 9. 视觉与交互规范（要点）

- 全局 token 化（`App.vue` 令牌真源），**无裸 hex / 无裸 `scale(` / 事件统一 `@tap`**；按压反馈为 `hover-class="pressed"`（背景微变）而非换色。
- 「我的」页：用户卡 → 一行 3 列宫格（等宽等高、浅粉圆底线性图标、整格热区、通知未读红点）→ 大片留白 → 底部信息区（版本 / 学校 + 隐私胶囊 + 注销账号 danger 弱化）。
- 列表 / 详情**无骨架屏与 loading 占位**（数据未返回保持静默）；首屏 / 刷新失败显示「加载失败 · 点击重试」；分页失败静默可重试。
- 无障碍：可点元素 `role` + `aria-label`，动态提示进 `aria-live`，`prefers-reduced-motion` 降级。

---

## 10. 非功能与合规

- **鉴权**：JWT 7 天（仅学生端，token 仅含 userId）；`TokenBlacklist`（token + userId 双维度）；`/admin/**` 无登录体系，由 `AdminTokenFilter` 校验 `X-Admin-Token` == `ADMIN_TOKEN`（未配置 fail-closed 403）；游客 `4031` 引导认证。
- **合规**：隐私政策页 + 采集节点告知 + **账号注销（匿名化）**；UGC 全部过微信内容安检。
- **部署与可迁移**：COS 存储、微信安检接口、业务接口均**不绑定微信云托管**，后端可整体迁移独立服务器（届时小程序上传域名走备案域名白名单；`callContainer` 仅作为当前传输层）。
- **数据库红线**：表结构变更只改 `schema.sql`（含旧库幂等迁移块），**禁止直连 ALTER**；覆盖远端库前先 `mysqldump` 备份。
- **北极星**：周活 / 留存（辅助观察：评价覆盖率、反馈处理时效）。

---

## 11. 版本边界

- **一期（当前范围）**：本文件第 3 节 9 个页面 + 第 4 节 7 条流程 + 第 5 节 **10 张表**（2026-09-15 操作日志表下线后；见 v1.6 变更）+ 第 6 节接口 + 第 7 节安检。
- **二期预留（做之前须重新拍板）**：Excel 批量导入 → OCR 菜单识别（同一导入通道）、微信订阅消息推送、「售罄 / 今日供应」即时状态、推荐算法演进。
- **回归通道**：被下线能力（动态 / 活动 / 公告 / 学生建卡 / 收藏）如需回归，须在 §0.0 与本文件重新登记后再实现。

---

## 12. 默认口径（未单独拍板、按现状固化的默认项；有异议请指出后修订）

1. 评价列表默认按**最新**排序，可切「有用」；评价按 **`is_hidden=0`**（或本人视角）对外可见——**安检不构成可见性闸门**（2026-09-15 取消人工复核、`sec_state` 已全链退役，见 v1.5 变更与 spec §7.24）。
2. 头像**不支持上传**，全站使用默认占位头像（`user.avatar` 允许站内 / cloud 地址但产品无上传入口）。
3. 系统通知为**认证专属**（游客入口直达、**不弹认证引导、静默处理**——无个人通知或请求被拒时不呈现错误态/认证引导，仅 `verified=true` 展示「暂无通知」轻提示，游客不拉未读数；对齐 spec §5.y.4，2026-09-15 DOC-12 修订，原「入口直达提示认证」表述作废）。
4. 反馈类型写入白名单 = `suggestion` / `add` / `error` / `report`（`bug` / `other` 为历史遗留枚举位，无生产者、禁止新增），举报复用该表并置 `related_type='review'`。
