# 食在交大 · 项目规格说明（project_spec.md）

> 供 AI 辅助开发使用的**硬性约束文档**。所有代码生成必须遵守本文件规则。
> 本文件与 `tasks/`（开发单元拆分，含页面设计）共同构成项目工作流；本文件为唯一权威约束，tasks 必须与之保持一致。

---

## 0. 系统总体设计（工作流总览）

### 0.1 三层架构
- **微信小程序（学生端）**：uni-app + Vue3 + TS + Pinia，提供浏览 / 发布 / 评价 / 分享。页面设计见各 task 文件（task-02~task-10）。
- **Web 管理后台（后勤 / 管理员端）**：Vue3 + Vite + TS + Element Plus + ECharts，提供审核 / 发活动 / 看板 / CRUD。页面设计见 task-09。
- **后端 API**：Spring Boot 3.2 + Java 21 + MyBatis-Plus + MySQL 8.0，JWT 鉴权，金额以「分」存储。前后端共用，按角色授权。

### 0.2 角色模型（仅两种）
- `STUDENT`（校内邮箱注册，兼"平鉴官"）：浏览（游客亦可）、发布 / 更新菜品、提交 / 更新档口·食堂、写评价、分享。
- `ADMIN`（系统管理员 / 食堂后勤）：审核 UGC、发活动、看板、食堂 / 档口 / 菜品 CRUD + 上架下架、用户 / 管理员管理、Banner 管理。
- **无独立 `STALL_OWNER` 角色**，亦无 `/stall-owner/**` 路由。

### 0.3 双端模块地图

#### 0.3.1 小程序端（学生端）功能模块清单

> 标注图例：**【本期做】**=本期交付；**【本期不做(descoped)】**=用户拍板本期不做，相关页面后续由开发移除/隐藏；**【封口不做】**=已拍板永久不碰，无余量。

**【本期做】**（共 12 项能力，用户拍板定稿）
1. **浏览 / 搜索 / 筛选**：首页推荐、搜索 / 筛选、食堂列表、食堂详情、档口详情、菜品详情（含折扣价展示）。
2. **详情页 + 快捷申请**：食堂 / 档口 / 菜品详情；菜品含「申请下架/纠错」、档口含「申请关闭/纠错」、食堂含「申请调整/下架」快捷入口（从详情页发起，统一走贡献审核闭环）。
3. **评价**：发表评价、评价列表、评价「有用 👍」（一人一票，沿用 `uk_useful_user_review`）；评价用 `is_hidden` 控制可见性；支持删除本人评价。
4. **实体贡献体系（"我的-我要贡献"统一入口）**：发布菜品 / 提交档口 / 提交食堂 / 申请下架或变更（菜品·档口·食堂）；可从详情页快捷发起；统一审核闭环（复用原记录重提回 `pending`）；状态在「我的提交」聚合（分"实体/动态"两个标签，含已下架）。
5. **社区动态 / 发动态**：动态 Tab 独立入口 + 社交内容流；发布动态（含关联菜品 / 档口 / 食堂）；动态审核（后台）。
6. **喜欢（替代收藏）**：从「我的」进入，单一「喜欢 ❤️」概念；**原「收藏」页与 `/favorites` 后端端点/表已随收藏一并彻底删除**（见 descoped），喜欢的具体存储方案交架构师在 tasks 评估，spec 仅记录「收藏/favorites 彻底删除」决策（见 §3.x.6.7）。
7. **消息中心**：从「我的」进入，通知列表（由 `notification` 承载，见 §3.x.4 / §5.z D-A）。
8. **社区举报**：动态详情 + 反馈页，复用 `user_feedback` 表（`type=report` + `related_type/related_id` 关联被举报动态），**不新建举报表**（见 §5.z Q4-②）。
9. **账号注销**：接通 `DELETE /my/account`，二次确认 + 清 token 跳登录（见 §5.z Q4-①）。
10. **关联动态双向跳转**：菜品详情关联动态卡片式可点进动态详情 + 档口 / 食堂详情聚合关联动态。
11. **删除本人记录**：动态 / 菜品 / 评价支持删除本人记录（不止编辑重提），删除走各自接口。
12. **评论互动升级（仅动态评论区）**：仅动态评论区支持点赞 + 楼中楼（B站式：主楼 + 子回复 + 展开"共 N 条" + "回复 @某人" + 👍 幂等）；后端 `moment_comment` 以 `parentId` 承载一层回复，前端实现楼中楼交互。
- **Banner（轮播）**：承载活动 / 运营入口，点击跳公众号文章 / H5 外链（已有组件，角色明确）；见 §0.3.3 Banner 并入说明。

**【本期不做(descoped)】**（用户拍板，相关页面/接口由开发移除或隐藏）
- **活动（独立模块）**：用户拍板本期不做独立活动模块，**并入 Banner**（Banner 承载活动/运营跳转入口，无需独立活动列表/详情页）。**本期整体移除（后端 activity 模块/路由/表一并清理，活动统一经 Banner 触达）**，小程序端不再消费活动页、后台活动管理亦移除（见 §0.3.3）。
- **收藏（"收藏/收藏页"概念）**：用户拍板砍除。**后端 `/favorites` 端点与表一并彻底删除，非仅前端移除**；统一由「喜欢 ❤️」替代（语义见 §3.x.6.7），不再有独立收藏页、亦无 `/favorites` 后端能力。
- **浏览足迹**：用户拍板本期不做足迹列表/页面。个性化推荐所需浏览历史仍由后端 `view_log` 暂存（仅作推荐输入，不向用户暴露足迹列表/页面）；`pages/history` 由开发移除或隐藏。
- **美食清单（List）**：用户拍板本期不做，相关 `/lists` 页面 / 入口后续由开发移除或隐藏；`/lists/**` 接口本期不暴露给小程序端（见 §3.x.5 契约总表登记说明）。

**【封口不做】**（永久不碰，见 §5.z）
- **关注 / 粉丝流**：不建用户关系表、不留余量（Q5）。
- **成就 / 等级 / 成长体系**：不建 `achievement`/`user_achievement`（Q1）。
- **评论点赞 / 动态点赞体系（菜品/评价等非动态区）**：保持克制，不扩展点赞社交关系流；评价的「有用 👍」沿用 `uk_useful_user_review` 一人一票（既有约束，非新增点赞流）。仅动态评论区可按本期做第 12 项做 👍 幂等（不形成独立社交关系表）。
- **微信真推送通道**：通知仅落 `notification` 表 + 小程序内消息中心读取，不接入微信订阅消息/模板消息真推送。

#### 0.3.2 小程序端 TabBar 结构（明确 4 Tab）

> 用户拍板：小程序端 TabBar 固定为 **4 个 Tab**，收藏、消息中心、我要贡献/我的提交/喜欢 均收进「我的」页，**不占 TabBar**。

- **首页（home）**：推荐 / 搜索入口 / 食堂·档口导航。
- **发现（find）**：美食 / 动态广场聚合浏览与筛选。
- **动态（动态/社区 moment）**：社区动态广场、发布动态入口（独立动态 Tab）。
- **我的（profile）**：我要贡献（统一贡献入口）/ 我的提交（实体·动态双标签，含已下架）/ 喜欢 / 消息中心 / 账号注销 / 设置。

#### 0.3.3 Web 后台模块

> 用户拍板 Web 管理端本期范围与菜单分组如下；**活动管理 / 数据看板 / 报表导出 本期不做**（活动管理 descoped 且后端 `activity` 模块/路由/表本期整体移除，统一经 Banner 触达；`dashboard`/`report` 后端接口保留但后台不暴露入口）。

**本期做（菜单分组，4 个顶层分组）**
- **内容管理**：食堂（含档口）/ 菜品（含折扣价字段编辑，列表页用卡片网格呈现）/ 轮播 Banner（承载活动/运营外链跳转）。
- **审核与社区**：审核中心（菜品/档口/食堂/评价四类切换 + 状态分段的合并审核页）/ 动态管理（审核 + 运营删合并）/ **反馈与举报处理**（从原独立顶层组并入本组，处理他人举报与反馈，属审核域）。
- **用户与权限**：用户管理（含注销状态展示）/ 管理员管理（仅 super_admin 可见）/ 操作日志。
- **个人中心**（新增独立分组，操作对象为管理员本人）：账号设置（个人资料 / 修改密码）。
- **登录默认落地**：反馈与举报处理页（路径 `/dashboard/feedbacks`，属「审核与社区」组）。

**本期不做(descoped)**
- **活动管理**：并入 Banner，后台不单独做活动管理模块。
- **数据看板**：本期不做（无 ECharts 看板）。
- **报表导出**：本期不做（CSV 导出入口不暴露）。

### 0.4 核心实体
食堂 Canteen、档口 Stall、菜品 Dish、评价 Review、活动 Activity（含 `official_account_url`，**本期整体移除：后端 activity 模块/路由/表一并清理，活动统一经 Banner 触达**，见 §3.x.5 / §3.x.6.5）、用户 User、Banner（含 `target_type` 枚举 DISH/ACTIVITY/URL/NONE + `target_id`/`target_url`，**本期承载活动/运营跳转入口**）。
- UGC 审核字段：菜品 / 档口 / 食堂均含独立 `audit_status`（pending/approved/rejected）与 `reject_reason`（退回原因，可空），与上下架 `status`（on/off、open/closed）解耦。
- 字段约束与建模见后端各模块；小程序 / 后台页面组织维度（菜品大类、口味偏好枚举）见 task-04 / task-08；接口契约与状态机见 §3.x。
- **本期新增实体（已拍板，详见 `tasks/ARCH_DECISIONS_PHASE2.md` §5.z）**：社区动态 `moment`、动态评论 `moment_comment`、消息通知 `notification`；`user_feedback` 升级补 `type`/`status`/`reply`/`handled_at`/`handler_id`/`related_type`/`related_id`（后两列为**社区举报关联**，复用 feedback 表，不新建举报表）。以上均**本期做**。
- **`view_log`（浏览记录表，本期仅作推荐输入，不暴露给用户）**：承载个性化推荐读取（§3.x.4 D-D），**不向用户展示「浏览足迹」页面（descoped，见 §0.3.1）**；加唯一键 `uk_view_user_target` 去重（§5.z D-B）。
- **美食清单 `list`/`list_item`（本期 descoped，不交付）**：实体与 `/lists/**` 接口已登记于 §3.x.5，但**小程序端本期不做美食清单功能**，相关页面/入口由开发移除或隐藏（用户拍板，见 §0.3.1）。
- **下架/变更申请（新建独立 `apply` 表，已拍板）**：菜品/档口/食堂的**下架申请与变更申请采用新建独立 `apply` 表**（专存「新增/下架/变更」三类申请，走统一审核状态机），**不复用现有审核状态机表**（即不与菜品/档口/食堂实体自身的 `audit_status` 审核字段混用，也不复用 `content` 审核现有表）。该决策仅记录「新建独立 `apply` 表」建模结论；Web 审核中心如何呈现档口/食堂审核（新增独立菜单或并入现有审核模块）仍交架构师在 `tasks/` 评估，spec 不预设呈现方式。详见 §3.x.1 / §3.x.5（apply 契约登记）。
- **明确不建的实体（已拍板封口）**：
  - **无成就/等级/用户成长体系**（Q1）：不建 `achievement`/`user_achievement` 等表，不做积分/等级/勋章，task-08 已彻底冻结、不占位。
  - **无用户关系表**（Q5）：关注/粉丝流本期不碰，不建任何 `user_relation`/`follow` 表，不留余量。
  - **无置顶/话题/精选运营干预字段**（Q2）：社区排序不干预，流量靠热度 + 关联动态反哺。

### 0.5 数据流 / 审核闭环
1. **浏览闭环**：打开 → 首页推荐 / 搜索 → 钻取到详情 → 看评价 / 分享。
2. **贡献闭环（平鉴官）**：我的发布 → 提交菜品 / 档口 → `audit_status=pending` → 后台审核 → `approved` → 展示；`rejected` 回写 `reject_reason`，学生编辑后**复用原记录**重提回 `pending`。
3. **运营闭环（后勤）**：后台审 UGC / 改 CRUD / 配 Banner（承载活动与运营外链跳转，含公众号文章/H5）→ 小程序即时体现；活动不独立成模块，统一经 Banner 触达。

### 0.6 工作流文档关系与一致性红线
- `project_spec.md`（本文件）：最高权威——硬性技术约束（角色、路由权限、API、UI 规范见 §4）+ 系统总体设计（§0）。
- `tasks/`：`task-01`~`task-10` 开发单元拆分与验收标准——必须引用本文件接口 / 角色 / UI 约束，页面设计直接写在各 task 文件内（不再有独立场景文档）。
- **一致性红线**：角色仅 `STUDENT` / `ADMIN`；**禁止任何 `STALL_OWNER` 角色或 `/stall-owner/**` 路由**（现有 `StallOwnerController` 为待清理遗留，学生发布/重提必须落到 `/dishes` 学生端接口，见 §3.x.5）；`/admin/**` 仅 `ADMIN`；活动仅后台录入且必含 `official_account_url`；菜品 / 档口 / 食堂均须含 `audit_status`+`reject_reason` 审核字段（与上下架 `status` 解耦）；Banner 跳转类型必须用 `target_type` 枚举（DISH/ACTIVITY/URL/NONE）；前端 UI 遵循本文件 §4（spring 动效、即时反馈、半透明材质、reduced-motion 降级）。
- **小程序 MVP 一致性红线（布局 / 动效 / 图标 / 语义，强制）**：
  1. **布局不得溢出 750rpx（禁止横向滚动 / 内容裁切）**：所有小程序页面根容器与内容宽度必须以 750rpx 视口为基准，统一使用 rpx + flex 响应式布局；任何页面（home / find / profile / pages-detail/dish 等）均不得出现内容超出屏幕、被裁切或横向滚动条。宽度相关数值不得硬编码超出视口（如定宽 px 滥用、未约束的横向 flex 子项、未 `box-sizing:border-box` 的 padding 撑破）。
  2. **动效简化红线（MVP 真机）**：MVP 真机禁止复杂 CSS `@keyframes`、长位移 transform 动画（已验证会抖动 / 位移异常 / 撑破布局）。小程序端仅允许 uni-app 原生 `<transition>` 组件与简单 CSS `transition`（ opacity 渐隐、≤8rpx 轻微位移），不使用会撑破布局的动画。手势驱动的 Sheet / 抽屉仍遵循 §4.4（spring 0.8/0.3 + 手势中断），但入场不得用复杂 keyframe。**不修改 §4.3 Web 端 spring 动效规范**（仅约束小程序 MVP 真机）。
  3. **图标 emoji 化（占位策略）**：MVP 阶段统一使用 **emoji 作为图标占位**，不再依赖从 iconfont 下载 SVG。示例映射：点赞/有用 = 👍、喜欢 = ❤️、热门 = 🔥、限时 = ⏰、猜你喜欢 = 💡、分享 = 📤、评价 = 💬、搜索 = 🔍、位置 = 📍。语义约束：**『喜欢』与『收藏』只保留一个爱心语义（❤️），『有用/点赞』用 👍 区分，二者不可混用 emoji**（禁止同一语义用两个 emoji，也禁止爱心承载「点赞/有用」语义）。
  4. Web 管理端 UI 必须遵循 §4 设计 token 与自封装组件（`DataTable`/`FormDialog`/`ConfirmDialog`/`StatusTag`/`SearchInput`/`ImageUpload`），列表三态齐全、破坏性操作二次确认（见 task-09 验收标准）。

---

## 1. 技术栈

- 后端：Spring Boot 3.2 + Java 21，ORM 使用 MyBatis-Plus 3.5.5（BaseMapper + XML 自定义 SQL，XML 位于 `resources/mapper/*.xml`）
- 小程序端：uni-app + Vue 3 (Composition API + `<script setup>`) + TypeScript + Pinia，目录 `frontend/`
- Web 管理端：Vue 3 + Vite + TypeScript + Element Plus + ECharts，目录 `web/`，无 Pinia（页面各自请求）
- 数据库：MySQL 8.0，库名 `bjtu_food`，utf8mb4；表结构以数据库设计文档 / 建表脚本为唯一权威；**建表脚本路径：`backend/src/main/resources/db/schema.sql`（由开发工程师创建，覆盖全部实体表 + 索引 + 唯一键）；其中 `user.role` 字段默认值为 `'student'`（注册默认角色，admin 由后台显式置位）。**
- 认证：JWT（7 天过期），`Authorization: Bearer {token}`；密码 BCrypt 存储

## 2. 目录结构

- 后端按业务模块分包：`com.bjtufood.{auth|canteen|dish|review|content|upload|common}`（注：`favorite` 包随 `/favorites` 端点与表本期彻底删除而移除，见 §0.3.1 descoped），每模块内分 `controller/ service(+impl)/ mapper/ entity/ dto/` 四层，禁止跨层调用（Controller 不得直接调 Mapper）
- 后端公共组件放 `common/`：`config/`（MyBatisPlusConfig、WebMvcConfig、CorsConfig）、`exception/GlobalExceptionHandler`、`util/`
- 小程序端 `frontend/src/`：`api/`（按模块拆分 + `http.ts` 统一封装）、`types/`、`stores/`（Pinia）、`pages/`（**TabBar 固定 4 页：home 首页 / find 发现 / moment 动态 / profile 我的**；收藏、消息中心从 `profile` 进入，不占 TabBar；`pages-detail/` 为二级页；发布菜品 / 提交档口 / 我的发布入口归入 `profile` 个人中心，不再有独立的 `stall-owner/` 分包）、`components/`（全局通用组件）
- Web 管理端 `web/src/`：`api/`（含 `adapter.ts` 字段名适配层）、`views/`、`components/`、`router/`
- 上传图片存 `uploads/images/YYYY/MM/{uuid}.{ext}`，数据库只存相对路径 `/images/...`

## 3. API 设计规范

- 所有接口统一响应结构：`{ code: number, message: string, data: T }`；成功 `code=200`；异常由 `GlobalExceptionHandler` 统一包装，Controller 不得裸抛/裸返回
- 错误码：`200` 成功、`400` 参数错误、`401` 未登录、`403` 无权限、`500` 服务器错误
- 认证：JWT 经 `JwtAuthFilter` 解析入 SecurityContext；角色仅两种：**`STUDENT`**（校内邮箱注册的学生）、**`ADMIN`**（系统管理员 / 食堂后勤）；白名单公开接口：`/auth/login|register|email-code|password/reset`、`GET /dishes/**`、`GET /canteens/**`；学生写操作（发布菜品 / 评价）需 `STUDENT`；`/admin/**` 仅 `ADMIN`。**不再有独立的 `STALL_OWNER` 角色，亦无 `/stall-owner/**` 路由。**
- 分页统一返回 `PageResult<T> { records, total, page, pageSize }`，请求参数 `page`/`pageSize`；使用 MyBatis-Plus 分页插件
- 金额单位：后端存储与传输一律用「分」（int/Long），前端展示层负责 `/100` 转元；前端提交时转回分
- 数据隔离：菜品由学生发布，`dish.created_by = 当前用户`；学生仅能读写自己提交的菜品（"我的发布"），管理员可读写全部；从 `SecurityUtil.getCurrentUserId()` 取用户，禁止信任前端传入的 userId

## 3.x API 契约总表与领域契约（权威补充）

> 本节约束所有已定义 / 待开发的接口契约，新增接口须先在此登记再实现。路径风格统一为 `kebab-case`；分页统一返回 `PageResult<T>{ list, total }`（实际代码以 `common/result/PageResult` 为准，含 `list`/`total` 字段）。金额一律「分」。

### 3.x.1 UGC 审核状态机（菜品 / 档口 / 食堂）

- 三实体在原有「上下架 / 营业状态」字段（`status`）之外，**新增独立的审核字段** `audit_status`（字符串，与上下架解耦）：
  - 取值：`pending`（待审核）/ `approved`（已通过）/ `rejected`（已退回）。
  - 学生 UGC 提交后写入 `audit_status=pending`；后台审核通过置 `approved`、退回置 `rejected` 并回写 `reject_reason`。
  - 小程序端只展示 `audit_status=approved` 且上架/营业中的记录（后端过滤，前端不兜底）。
  - **新增字段 `reject_reason`（VARCHAR，可空）**：仅 `audit_status=rejected` 时由后台填写，回显至学生「我的发布」审核状态页。
- **退回后重提裁决（字段级闭环）**：学生编辑 rejected 记录并「重新提交」时，**复用原记录**（同一 `id`），更新业务字段后将 `audit_status` 重置为 `pending`、`reject_reason` 置空；**不新建记录**。理由：保持 ID 稳定，避免历史收藏/清单/评价悬空，简化后台审核列表的去重与统计。后台审核接口按 `audit_status` 过滤，无需关心记录是否新建。

> **下架/变更申请与本案解耦（已拍板）**：菜品/档口/食堂的**下架申请、变更申请**不进本状态机、不复用实体自身 `audit_status`，而是落到**新建独立 `apply` 表**（专存新增/下架/变更三类申请，走独立统一审核状态机，见 §0.4 / §3.x.5）。本 §3.x.1 的 `audit_status` 仅覆盖「新增实体（菜品/档口/食堂）的首次提交审核」；下架/变更走 `apply` 表，二者审核流互不混入。
- 状态流转图：
  ```
  学生提交 ──▶ pending ──┬─(通过)──▶ approved ◀──┐
                        │                         │(学生编辑重提, 复用原记录)
                        └─(退回+原因)──▶ rejected ┘
  approved 记录可被后台 CRUD 下架/删除（status 变化，不影响 audit_status）。
  ```

### 3.x.2 Banner 跳转类型枚举

- Banner 实体跳转字段约定（统一，`type` 字段规范为 `target_type` 枚举，历史 `type` 值 `dish`/`url` 纳入枚举）：
  - `DISH`：跳菜品详情，需填 `target_id`（菜品 ID），`target_url` 留空。
  - `ACTIVITY`：跳活动详情，需填 `target_id`（活动 ID），`target_url` 留空。**（注：本期 `activity` 表/模块整体移除，活动统一经 Banner 触达，故 ACTIVITY 类型本期不消费；活动触达改由 `URL` 类型承载公众号文章/H5 外链，见 §0.5 / §3.x.6.5）**
  - `URL`：跳外部链接，需填 `target_url`，`target_id` 留空。
  - `NONE`：纯展示不跳转，两者均留空。
- 小程序端跳转行为：`DISH`→`/pages-detail/dish?id=`；`ACTIVITY`→`/pages-detail/activity?id=`；`URL`→`web-view`/复制链接（按微信能力）；`NONE`→无点击响应。
- 后台 Banner 表单联动：`target_type` 切换时，仅对应目标字段可编辑（DISH/ACTIVITY 选 ID、URL 填链接、NONE 全禁用），提交时校验「类型↔目标字段」一致性，违反返回 400。

### 3.x.3 运营置顶 / 干预裁决（MVP）

- **MVP 砍掉「运营可置顶 / 可干预」**。理由：MVP 以算法生成（热度/上新/推荐）即可满足首页决策信息需求，置顶能力增加后台表单complexity与数据模型成本，且与"克制"原则冲突；运营如需强推，可通过 Banner（已有运营能力）承接，不污染菜品/列表排序。
- 因此：
  - 菜品/档口/食堂实体**不引入** `is_pinned` 或独立置顶表；首页「今日上新」「热门」「猜你喜欢」均为纯算法生成，无任何手工置顶字段。
  - 原 task-03 中"运营可置顶 / 运营可干预"字样删除，统一为算法生成（见 task-03 修订）。

### 3.x.4 「猜你喜欢」推荐规则（MVP 规则版，无 ML）

- 接口：`GET /dishes/recommend`（公开，无需登录；登录态带 token 时个性化更强）。
- 打分公式（后端计算，仅对 `audit_status=approved` 且上架菜品生效）：
  - **热度分** `heat = w1*viewCount + w2*ratingCount*scale + w3*avgRating*scale`（权重常量在 service 内定义，默认 w1=1, w2=5, w3=20，可后续调参）。
  - **个性化加权**：登录用户取最近 N（默认 20）条浏览记录，提取其「菜品大类 / 口味标签」，对命中同大类或同标签的候选菜品加 `bonus`（默认 +30% 热度分）。
  - **降级**：未登录或无浏览历史时，直接按热度分降序（等同于「热门」个性化弱化版）。
  - 排除已在「热门」首位重复曝光的强约束不强制；去重交由前端处理；同一用户近期已浏览菜品可降权但不剔除。
- 入参：`page`、`pageSize`（默认 10）；可选 `excludeIds`（逗号分隔，前端用于排除已展示项）。
- 出参：`PageResult<DishVO>{ list, total }`，`DishVO` 复用现有结构。
- **⚠ 性能约束（已拍板 D-D，必须修的性能债）**：
  - `DishServiceImpl.recommendDishes()` **不得将全表加载到内存再排序**；必须改为 **SQL 分页**（`ORDER BY heat DESC` + `LIMIT`/`OFFSET` 或 MP 分页插件），个性化加权在 SQL 层或分页后少量候选上计算。
  - 「猜你喜欢 / 热门 / 广场」列表接口**应用内 Caffeine 短 TTL 缓存（60s）+ 写失效**：菜品/动态变更（发布、审核通过、浏览计数更新等写操作）时主动 `invalidate` 对应缓存键；TTL 仅作兜底防雪崩，不依赖其保证实时性。
  - 浏览足迹 `view_log` 个性化读取为该推荐的**唯一存储**（task-07），避免双写（见 §3.x.4 原备注）。

### 3.x.5 API 契约总表

> 鉴权列：`PUB`=公开（游客可访问白名单）、`STU`=需 STUDENT、`ADM`=仅 ADMIN（`/admin/**`）。`records` 列仅列要点，完整 DTO 见各模块 service/entity。

| 模块 | Method | Path | 鉴权 | 入参要点 | 出参要点 |
| --- | --- | --- | --- | --- | --- |
| auth | POST | `/auth/register` | PUB | email(@bjtu.edu.cn)+code+password+nickname | token+user |
| auth | POST | `/auth/login` | PUB | email+password | token+user |
| auth | POST | `/auth/email-code` | PUB | email | void |
| auth | POST | `/auth/password/reset` | PUB | email+code+newPassword | void |
| auth | GET | `/auth/profile` | STU | — | user+stats |
| user(admin) | GET/PUT/DELETE | `/admin/users`… | ADM | — | 学生账号列表/禁用 |
| canteen | GET | `/canteens/banners` | PUB | — | `List<BannerVO>`（按 sort_order） |
| canteen | GET | `/canteens` | PUB | — | `List<CanteenInfoVO>`（open） |
| canteen | GET | `/canteens/images` | PUB | — | `Map<name,url[]>` |
| canteen | GET | `/canteens/stallDetail` | PUB | canteenName+stallName | `StallDetailVO` |
| canteen | GET | `/canteens/all` | PUB | — | 食堂+档口树 |
| canteen | GET | `/stalls` | PUB | canteenId | 档口列表（open） |
| canteen(admin) | CRUD | `/admin/canteens`…`/admin/stalls`… | ADM | — | 食堂/档口 CRUD+上下架 |
| banner(admin) | CRUD | `/admin/banners` | ADM | title,images,**target_type**,target_id/target_url,sortOrder,status | void/列表 |
| dish | GET | `/dishes/recommend` | PUB | page,pageSize,excludeIds? | `PageResult<DishVO>` |
| dish | GET | `/dishes/hot` | PUB | — | `List<DishVO>`（TOP） |
| dish | GET | `/dishes/new` | PUB | — | `List<DishVO>`（created_at desc） |
| dish | GET | `/dishes/promotions` | PUB | — | `List<DishVO>`（含活动价） |
| dish | GET | `/dishes` | PUB | keyword,canteenId,stallId,tag,minPrice,maxPrice,sortBy,sortOrder,page,pageSize | `PageResult<DishVO>` |
| dish | GET | `/dishes/{id}` | PUB | — | `DishVO`（含 isFavorited/hasReviewed） |
| dish | POST | `/dishes/{id}/view` | STU | — | void（记浏览量） |
| dish(publish) | POST | `/dishes`（学生发布） | STU | DishPublishReq（name,price分,images,stallId,tags,desc,audit_status→pending） | id |
| dish(publish) | PUT | `/dishes/{id}`（学生编辑/重提） | STU | 同上；仅本人 created_by；重提置 audit_status=pending,reject_reason=null | void |
| dish(publish) | GET | `/my/dishes` | STU | audit_status? | 我的发布列表（含审核态+reject_reason） |
| dish(admin) | CRUD | `/admin/dishes` | ADM | DishAdminReq | 菜品 CRUD+上架下架 |
| content(admin) | GET | `/admin/audit?type=dish\|stall\|canteen&status=pending` | ADM | page,pageSize,type,status | 待审列表 `PageResult<AuditVO>` |
| content(admin) | POST | `/admin/audit/{type}/{id}/approve` | ADM | — | 置 audit_status=approved |
| content(admin) | POST | `/admin/audit/{type}/{id}/reject` | ADM | rejectReason(必填) | 置 audit_status=rejected+写 reject_reason |
| apply | POST | `/my/apply` | STU | applyType(NEW/CLOSE/CHANGE),entityType(DISH/STALL/CANTEEN),entityId?,payload | **【本期做】** 提交下架/变更申请（新建独立 `apply` 表，见 §0.4 / §3.x.1；不复用实体 `audit_status`）。 |
| apply | GET | `/my/apply` | STU | status? | 我的申请列表（含审核态） |
| apply(admin) | GET | `/admin/apply?status=pending` | ADM | page,pageSize,applyType?,entityType? | **【本期做】** 申请审核列表 `PageResult<ApplyVO>`（Web 审核中心呈现方式交架构师评估，见 §0.4） |
| apply(admin) | POST | `/admin/apply/{id}/approve` | ADM | — | 通过申请（按 applyType 落地下架/变更到目标实体） |
| apply(admin) | POST | `/admin/apply/{id}/reject` | ADM | rejectReason(必填) | 退回申请 |
| review | POST | `/reviews` | STU | rating,content,images(≤3) | void（uk 约束一人一菜一条） |
| review | GET | `/reviews?dishId=` | PUB | dishId,sort,isWithImage | `PageResult<ReviewVO>` |
| review(admin) | GET/PUT/DELETE | `/admin/reviews` | ADM | isHidden? | 评价审核（is_hidden 控制） |
| favorite | — | `/favorites` | — | — | **【本期彻底删除】** 后端 `/favorites` 端点与表一并彻底删除（非仅前端移除），「喜欢 ❤️」具体方案交架构师（见 §0.3.1 descoped / §3.x.6.7），本行仅作移除登记。 |
| feedback | POST | `/feedback` | STU | type,content,contact?,**relatedType?**,**relatedId?** | void（type=report 时 relatedType=relatedId 必填，关联被举报动态；复用 user_feedback 表，不新建举报表） |
| user | DELETE | `/my/account` | STU | confirm? | void（**Q4-① 账号注销**：物理或逻辑删除本人账号 + 级联清理本人 dish/moment(+moment_comment)/review/favorite/view_log/notification/user_feedback；不建用户关系表） |
| upload | POST | `/upload/image` | STU | file(≤5MB,jpg/jpeg/png/webp) | url（相对路径） |
| activity(admin) | CRUD | `/admin/activities` | ADM | name,dishId,activityPrice分,originPrice分,startAt,endAt,**official_account_url**(必填),status | **【本期整体移除】** 后台活动管理移除（并入 Banner）；后端 `activity` 模块/路由/表本期一并清理，活动统一经 Banner 触达；无需对齐 `startAt`/`endAt`（见 §3.x.6.5）。 |
| activity | GET | `/activities` | PUB | — | **【本期整体移除】** 小程序端不消费活动列表（并入 Banner，后端 activity 表/路由清理） |
| activity | GET | `/activities/{id}` | PUB | — | **【本期整体移除】** 小程序端不消费活动详情（同上） |
| moment | GET/POST | `/moments`… | STU/PUB | content,images?,relatedType?(DISH/STALL/CANTEEN),relatedId? | **【本期做】** 社区动态列表/发布（关联菜品/档口/食堂，供菜品详情关联动态双向跳转） |
| moment | GET/DELETE | `/moments/{id}` | STU(PUB 读/本人删) | — | **【本期做】** 动态详情（含举报入口）、删除本人动态 |
| moment_comment | GET/POST | `/moments/{id}/comments` | STU/PUB | content | **【本期做】** 动态评论（一人多条，非点赞流） |
| moment | GET | `/my/moments` | STU | — | **【本期做】** 我的动态（删除本人记录入口） |
| notification | GET/PUT | `/notifications`…`/notifications/read` | STU | — | **【本期做】** 消息中心列表/已读（异步落库，见 §5.z D-A；非微信真推送） |
| feedback | POST | `/feedback` | STU | type,content,contact?,**relatedType?**,**relatedId?** | void（type=report 时 relatedType=relatedId 必填，关联被举报动态；复用 user_feedback 表，不新建举报表） |
| list | CRUD | `/lists`…`/lists/share/{token}` | STU/PUB | name,dishIds,shareToken | **【本期不做(descoped)】** 美食清单接口已登记但小程序端本期不交付，相关页面/入口由开发移除或隐藏（用户拍板，见 §0.3.1） |
| dashboard(admin) | GET | `/admin/dashboard` | ADM | range | **【本期不做(descoped)】** Web 数据看板本期不做，后台不暴露入口（后端接口保留） |
| report(admin) | GET | `/admin/reports/dishes\|reviews\|users\|moments/export` | ADM | startAt?,endAt? | **【本期不做(descoped)】** 报表导出本期不做（CSV 接口保留但后台不暴露） |
| report(admin) | GET | `/admin/reports/summary/export` | ADM | startAt?,endAt? | **【本期不做(descoped)】** 汇总报表本期不做 |

> 说明：`content`（UGC 审核）后端包已存在；`activity` 后端包本期整体移除（模块/路由/表一并清理，活动统一经 Banner 触达）。`dish(publish)` 学生发布/重提接口已由 `/dishes` 学生端接口承载（`POST /dishes`、`PUT /dishes/{id}`、`GET /my/dishes`），stall-owner 残留已在 task-01 清理删除（见 §5 红线与影响面清单）。
> **实体贡献（定稿 §0.3.1 第 4 项）契约归属**：「我要贡献」统一入口与详情页快捷申请中，**新增实体（菜品/档口/食堂首次提交）复用既有 UGC 审核闭环接口**（`POST /dishes` 系列、`POST /my/stalls`、以及 `/admin/audit` 审核），不新增端点；**下架/变更类申请则走新建独立 `apply` 表**（见 §0.4 / §3.x.1，不复用实体 `audit_status` 审核闭环，对应端点 `/my/apply`、`/admin/apply` 已登记于 §3.x.5）。「我的提交」聚合页（实体/动态双标签，含已下架）由前端聚合 `GET /my/dishes`、`GET /my/stalls`、`GET /my/moments`、`GET /my/apply` 等「我的」接口实现，无需新接口。「喜欢 ❤️」替代收藏，底层 `/favorites` 已彻底删除（见 §3.x.6.7），移除独立收藏页。
> **Web 后台菜单与登录落地（定稿 §0.3.3）**：后台菜单为 4 个顶层分组——「内容管理（食堂含档口/菜品含折扣价/Banner）/ 审核与社区（审核中心合并页/动态管理/反馈与举报处理）/ 用户与权限（用户管理/管理员管理/操作日志）/ 个人中心（账号设置，操作对象为管理员本人）」。反馈与举报处理从原独立顶层组并入「审核与社区」（属处理他人提交/举报的审核域）；账号设置从「用户与权限」拆出至独立「个人中心」组（与"管理他人"语义分离）。登录默认落地「反馈与举报处理」（`/dashboard/feedbacks`）。菜品列表页本期用卡片网格呈现（与食堂/Banner 统一）。活动管理/数据看板/报表导出本期不暴露入口（descoped）。Web 前端路由前缀维持 `/dashboard/**`（本期不与后端 `/admin/**` 对齐，留作后续）。操作日志 `operation_log` 表 + AOP 记录关键操作（增删改/审核/上下架/注销处理），见 §5.z Q4 与 ARCH_DECISIONS_PHASE2 §0。

### 3.x.6 三端一致性裁决（权威补充，2025-10 审计结论）

> 本节为接口契约的「唯一裁决层」。三端（backend / frontend / web）**必须**以本节字段名、路径、状态码为准；凡与历史代码不一致的，以本节为准，三端按 §5 影响面清单整改。涉及「emoji 图标」「动效简化」「web UI 重构」需求的字段新增，必须先在本节登记再实现。

#### 3.x.6.1 字段命名唯一真相（camelCase 对外 / snake_case 仅 DB）

- **对外 JSON 一律 camelCase**（后端 Jackson 默认即 camelCase；前端 TS、web 经 `adapter.ts` 转换后对外亦 camelCase）。
- **DB 列名 snake_case**，由 MyBatis-Plus 自动驼峰映射；**严禁**在 VO/DTO 上直接暴露 snake_case 字段名（如 `favorite_count`、`official_account_url`、`is_hidden` 仅允许出现在 DB 与 web `adapter.ts` 内部，不得进入 VO 或小程序 types）。
- **收藏量字段统一命名为 `favoriteCount`**（DishVO 当前误用 `collectCount`，后端须改名，见 §5 影响面 P1）。
- **收藏/喜欢语义统一为单一概念「喜欢 ❤️」**：前端已合并 `like`/`favorite` 为单一概念 `favorite`；原「收藏」`/favorites` 后端端点与表**本期已彻底删除**（见 §0.3.1 descoped），「喜欢 ❤️」的具体存储方案交架构师在 tasks 评估，spec 仅记录「收藏/favorites 彻底删除」决策；无论底层如何实现，**禁止 `like`/`favorite` 双体系、禁止 `like_count` 字段、禁止 `/likes` 端点**，对外仍统一为单一 `favorite` 语义 + `DishVO.favoriteCount` 计数（详见 §3.x.6.7）。

#### 3.x.6.2 Banner 契约（target_type 枚举强制；type 仅遗留兼容）

- 对外 VO 字段：`id`(Long)、`title`、`subtitle`、`images`(List<String>)、`targetType`(枚举 DISH/ACTIVITY/URL/NONE)、`targetId`(Long?)、`targetUrl`(String)、`canteenId`(Long?)、`sortOrder`(Integer)、`status`(enabled/disabled)。
- 后端 `BannerVO` 必须输出 `targetType/targetId/targetUrl`（**不再输出历史 `type` 字段**，或 `type` 仅作内部兼容、前端/web 一律消费 `targetType`）。
- web `adapter.ts` 已正确转换 `targetType`，但 `type` 字段回退默认 `'dish'`、且 `status` 在 web 内部使用 `active/inactive` 双轨——**裁决**：web 侧 `Banner` 类型内 `type` 字段标记为 `@deprecated`，仅保留 `targetType`；web 内部 `active/inactive` 与后端 `enabled/disabled` 的映射维持现状（adapter 已处理），但 web 类型定义不得再向视图层暴露 `type`。

#### 3.x.6.3 Dish 契约（收藏量 + 图片解析）

- `DishVO`：`price`(int 分)、`favoriteCount`(int，原 `collectCount` 改名)、`avgRating`(BigDecimal)、`ratingCount`(int)、`viewCount`(int)、`images`(List<String>，由 `imagesJson` 解析，原 JSON 字段 `@JsonIgnore`)、`tags`(String 逗号分隔)、`isNew`(Boolean)、`status`(on/off)、`isFavorited`(Boolean?，依赖「喜欢」底层存储，方案见 §3.x.6.7)、`hasReviewed`(Boolean?)。
- 小程序 `types/dish` 消费 `favoriteCount`（当前读 `collectCount ?? favoriteCount` 兼容，待后端改名后去除兼容分支，见 §5）。

#### 3.x.6.4 Review 契约（状态码 + 字段名）

- `ReviewVO`（公开）：`id`、`userId`、`dishId`、`userNickname`、`userAvatar`、`rating`(1-5 int)、`content`、`images`(List)、`createdAt`。**不对外暴露 `isHidden`/`hasSensitive`**（仅 admin VO 携带）。
- **状态使用 `isHidden`（Integer 0/1）**，非 `isDeleted`。公开列表只返回 `isHidden=0` 且未删除记录；后台审核用 `PUT /admin/reviews/{id}/hide` 切换 `isHidden`，**不使用物理删除的隐藏语义**（删除走 `DELETE /admin/reviews/{id}`，物理删除）。
- 小程序 `types/review`：`isWithImage`、`sort` 入参名维持（已对齐 spec）；字段 `userId`/`userName` 命名对齐 VO（`userName` 由 `userNickname` 映射，前端 `toReview` 已兼容双名，待统一）。

#### 3.x.6.5 Activity 契约（本期整体移除）

- **本期整体移除**：后端 `activity` 模块/路由/`activity` 表一并清理，活动统一经 Banner 触达（见 §0.3.1 / §0.3.3 / §3.x.5），**无需对齐 `startAt`/`endAt`**（原 `ActivityVO` 时间字段 `startAt`/`endAt` 命名裁决随模块移除不再适用，亦不再要求后端 `ActivityAdminReq` 改名）。
- 历史对外字段（`id`、`title`、`description`、`coverImage`、`dishId`、`activityPrice`(int 分)、`originPrice`(int 分)、`officialAccountUrl`(必填)、`startAt`、`endAt`、`status`(enabled/disabled)、`sortOrder`）仅作存档，本期不再实现。
- web `adapter.ts` 原 `activityToLegacy` 兼容分支随模块移除一并删除。

#### 3.x.6.6 User 契约（无 stall 概念）

- `UserVO`：`id`、`username`、`email`、`nickname`、`avatar`、`role`(student/admin)、`status`(active/disabled)、`createdAt`。
- **移除 `UserVO.stallId` 概念**：原 web `userToLegacy` 含 `stall_id` 属 stall-owner 残留，后端 `UserVO` 无此字段，web adapter 须删除 `stall_id` 映射（见 §5 红线）。

#### 3.x.6.7 「喜欢/收藏」语义统一裁决

- **结论：原「收藏」`/favorites` 后端端点与表本期已彻底删除**（非仅前端移除，见 §0.3.1 descoped）。前端「喜欢 ❤️」概念的**具体后端存储方案交架构师在 tasks 评估**（spec 不预设表/端点形态），但对外语义约束不变。
- **单一概念红线（强制）**：前端合并 `like`/`favorite` 为单一「喜欢 ❤️」概念，语义为「一人一菜一次，再点取消」；**禁止 `like`/`favorite` 双体系、禁止 `like_count` 字段、禁止 `/likes` 端点**；对外统一 `favorite` 语义 + `DishVO.favoriteCount` 计数（小程序端 `likeDish/unlikeDish` 仅是对喜欢能力的语义重命名，不得触发新端点 / 不得回退到已删除的 `/favorites`）。
- `DishVO.favoriteCount` 即「喜欢总数」，前端 ❤️ 旁展示该值（字段命名见 §3.x.6.1 / §5.x 红线）。

#### 3.x.6.8 学生 UGC 提交路径裁决（红线补充）

- 学生提交档口/食堂走 `POST /my/stalls`（`MyStallController`，STUDENT 鉴权），**非** `/stall-owner/**`。该路径合规，但须在契约总表登记（历史 spec 仅登记了 `/admin/audit` 审核，未登记学生提交入口）。
- 学生发布菜品维持 `POST /dishes`、`PUT /dishes/{id}`、`GET /my/dishes`（§3.x.5 已登记）。

## 4. UI 设计规范（Apple Design 风格）

### 4.1 适用范围与核心原则
- **适用端**：微信小程序（uni-app + Vue3 + TS）、Web 管理后台（Vue3 + Element Plus + ECharts）。
- **八原则**：Purpose（克制）、Agency（用户掌控 + 可撤销）、Responsibility（隐私 / 安全）、Familiarity（沿用熟悉隐喻）、Flexibility（适配设备与能力）、Simplicity（非极简）、Craft（细节即信任）、Delight（前七之和）。
- **流体交互四要素**：即时响应、1:1 直接操控、可中断（随时抓回反向）、速度 / 动量接力。

### 4.2 视觉 Token（基线，可整项目调整）
- 主色：管理端侧边栏深红 `#6B1010`；小程序端按钮统一 `AppButton`（primary/outline/text 三种）。
- 圆角：卡片 `16px`；底部弹层 `20px 20px 0 0`。
- 材质模糊：`blur(20px) saturate(180%)`；按下缩放 `0.97`；弹层阴影 `0 -8px 30px rgba(0,0,0,0.12)`。
- 组件库：小程序端自研 `ImageSwiper / DishCard / WaterfallList / Rating / TagLabel / CardSection / CategoryTabs / EmptyState / Loading / AppButton`（新页面必须复用）；管理端 Element Plus + 自封装 `DataTable / FormDialog / ConfirmDialog / StatusTag / SearchInput / ImageUpload`。
- **小程序图标策略（MVP）**：统一使用 **emoji 作为图标占位**，不引入 iconfont SVG。映射见本文件 §0.6 一致性红线第 3 条。管理端仍用 Element Plus 官方 icon（`@element-plus/icons-vue`），不强制 emoji。
- 通用：列表页三态（Loading / EmptyState / 正常）；删除等破坏性操作二次确认；图片上传单张 ≤5MB jpg/jpeg/png/webp，评价图最多 3 张。

### 4.3 动效系统（Motion）
| 交互 | Damping | Response |
| --- | --- | --- |
| 常规 UI（卡片 / 按钮 / 导航） | `1.0` | `0.3–0.4` |
| 抽屉 / 底部弹层（Sheet） | `0.8` | `0.3` |
| 旋转 / 翻动 | `0.8` | `0.4` |
| 位置重排（拖拽排序） | `1.0` | `0.4` |

- **默认全站 `damping 1.0`**（无过冲）；仅当手势本身带动量时才加回弹（`0.8`：被甩卡片、拖拽释放的抽屉、轮播吸附）。菜单凭空淡入加过冲会显廉价。
- **可中断**：永远从「当前屏幕上的呈现值」起步，绝不从目标值；手势驱动动画用 spring，不用 CSS `transition` / `@keyframes`（无法平滑抓取反向）。
- **速度接力**：手势释放速度交 spring 初速度（px/s，无需归一化）；拖拽与动画无缝缝合。
- **动量落点**：指数衰减 `current + (v/1000)·d/(1−d)`，`d≈0.998`，再吸附最近 snap 点（抽屉上拉 50% 阈值、轮播居中）。
- Web 端用 Motion / Framer Motion：`damping 1.0 ≈ bounce 0`，`0.8 ≈ bounce 0.2`。

### 4.4 交互反馈
- **点按**：按下即时 `transform: scale(0.97)`（不等 `click` / `touchend`）；命中区加 ~10px 滞回，可按住拖离取消。
- **抽屉 / Sheet**：spring `0.8/0.3`，手势可拖拽中断、按速度符号决定提交 / 回弹（阈值 ~50%），边界橡皮筋。
- **弹窗 / Popover**：`transform-origin` 锚定触发源，进出同路径、缓动镜像对称。
- **滚动橡皮筋**：`(over·dim·k)/(dim + k·|over|)`，`k≈0.55`，渐进阻力不硬停。

### 4.5 材质与层级
- 半透明导航 / 工具条 / 抽屉：`backdrop-filter: blur(20px) saturate(180%)` + 半透底，内容在其下滚动（非固定实条）。
- 材质权重编码层级：结构区更重更暗，交互元素更轻更亮；不叠两层轻透面。大面更厚（更强模糊 + 更深阴影）。
- 滚动边缘渐隐代替硬分割线；入场「实体化」（blur + scale 同动）而非单纯淡入。

### 4.6 字体排版
- tracking 随字号（大标题 `-0.02em`，正文 `0`）；leading 反比（大标题 ~1.05，正文 ~1.5）。
- 层级用「字重 + 字号 + 行高」一组构建；系统字体优先；间距 `rem`/`em` 随用户字号缩放。

### 4.7 可达性与降级
- `prefers-reduced-motion: reduce` → 交叉淡入，去弹性过冲，保留 opacity / color 变化。
- `prefers-reduced-transparency: reduce` → 半透面更实 / 去模糊。
- `prefers-contrast: more` → 近实底 + 对比边框。
- **小程序适配**：无 Pointer Events，用 `touch` 事件 + 自记最近几次 `touchmove` 速度历史；`backdrop-filter` 真机部分支持，降级纯色半透 + 阴影；动画只用 `transform` / `opacity`，临近运动 `will-change` 提示。

### 4.8 组件级约定（映射任务）
| 组件 | 规范 | 任务 |
| --- | --- | --- |
| 卡片（食堂 / Banner / 菜品） | tap `scale(0.97)`、入场 spring `1.0/0.3` | 03 / 04 / 05 / 06 / 10 |
| TabBar / 顶部切换（4 Tab：首页/发现/动态/我的） | spring `1.0/0.3`；图标态即时 | 01 / 03 |
| 抽屉 / Sheet（菜品详情、发表评价、发布菜品、提交档口、审核详情） | §4.4 `0.8/0.3` + 手势中断 + 速度接力 | 06 / 07 / 08 / 09 |
| 弹窗（确认 / 退回原因） | §4.4 锚定触发源 | 08 / 09 |
| 列表 / 瀑布流 | 滚动橡皮筋、行 hover 即时（Web） | 03 / 04 / 09 |
| Toast / 反馈 | 四态（status / completion / warning / error）同帧触发 | 全任务 |

> UI 约定由 `task-01` 落地为 token 与基础动画工具，其余任务复用。

### 4.9 小程序 MVP 红线（布局 / 动效 / 图标）

> 本节为 MVP 真机约束，强制要求与 `tasks/` 验收绑定。完整条目见 §0.6 一致性红线第 1–3 条；此处给出落地细则。

- **布局（750rpx 视口）**：页面根容器宽度视为 750rpx；所有横向布局用 `display:flex` + `flex-wrap`/子项 `flex:1` 或 `min-width:0` 防溢出；定宽值谨慎使用，图片/卡片用 `width:100%` + `box-sizing:border-box`；禁止产生横向滚动条；长文本用 `-webkit-line-clamp` 截断而非撑高溢出。每个页面须通过「真机 750rpx 视口无横向滚动 / 无裁切」验收。
- **动效（从简）**：仅使用 uni-app `<transition>`（如 `fade` / `fade-slide`，位移 ≤8rpx）与简单 CSS `transition`（opacity / transform 轻量）；禁止 `@keyframes` 长动画、禁止 `translate` 大位移与 `scale>1` 入场撑破布局；手势 Sheet / 抽屉仍走 §4.4（spring 0.8/0.3 + 手势中断），但其入场不做复杂 keyframe。reduced-motion 降级保留。
- **图标（emoji 占位）**：按 §0.6 第 3 条映射；新增语义需在 task 内登记 emoji，不得在组件内私自引入 iconfont SVG。语义唯一：❤️=喜欢/收藏（二选一语义）、👍=有用/点赞，互不混用。

### 4.10 Web 管理端 UI 一致性要求

- 全面复用自封装组件 `DataTable / FormDialog / ConfirmDialog / StatusTag / SearchInput / ImageUpload`，禁止页面裸写 Element Plus 表格/表单以绕过设计 token。
- 列表页三态（Loading / EmptyState / 正常）必须齐全；删除等破坏性操作一律经 `ConfirmDialog` 二次确认。
- 设计 token（主色深红 `#6B1010`、圆角、材质、状态色）统一由 `task-01`/设计系统导出，页面不得硬编码覆盖（见 task-09 重构验收）。
- **后台模块设计点（定稿 §0.3.3）**：
  - **Banner 支持外链跳转**（承载活动/运营入口）：`target_type`=URL 时填 `targetUrl`，DISH 时填 `targetId`；表单联动校验（见 §3.x.2）。
  - **菜品页加「折扣价」字段**：后台菜品表单须含折扣价编辑入口（与原价/活动价区分，仅作信息展示字段，非下单价）；不引入下单/支付概念（见 §5 平台定位）。
  - **审核中心合并页范式**：审核中心（菜品/档口/食堂/评价四类 + 状态分段）统一采用「列表分段（待审/已审/已退回）+ 详情抽屉 + 通过/退回(填原因) + 批量操作」范式；动态管理合并审核与运营删（见 §0.3.3）。
  - **动态管理合并审核与运营删**：动态管理页同时承载审核通过与运营删除（不拆两模块）。
  - **举报详情展示**：反馈与举报处理页展示举报「类型 + 摘要 + 关联 ID + 复制链接」；**后台不内嵌小程序预览**，仅提供复制链接供管理员在微信侧查看。
  - **操作日志**：记录关键操作（增删改 / 审核 / 上下架 / 注销处理），供「用户与权限-操作日志」页查询。
  - **页面布局与信息组织规范（新增）**：见 §4.11。后台页面须按 §4.11 的「三栏布局 + 三类页面模板（T1 列表 / T2 详情 / T3 抽屉表单）+ 统一组件（PageContainer/PageSection/PageHeader/FilterBar/StatCard/FormField）」实现，消除页头/统计卡/筛选区五花八门写法；视觉基调维持 §4 Apple 克制风（深红/半透材质/spring 动效），并在该基调内做视觉刷新（统一留白/层级/材质深度、打磨组件质感与排版），不切换设计语言。

### 4.11 Web 页面布局与信息组织规范（本期定样）

> 本节为 Web 管理端页面级 UI 契约，与 §4.2 设计 token、§4.10 后台一致性要求配套。所有后台页面（列表/详情/审核/表单）必须遵循本节，由 web-dev 在重构阶段落地（spec 定样先行，实施另排期）。

#### 4.11.1 整体布局（三栏）
- 结构：Sidebar（固定 220px，`--color-primary-dark` 深红底，可折叠）+ Main（flex:1）；顶栏 60px，`backdrop-filter` 半透材质（§4.5）。
- 内容区：内边距 `var(--space-6)`（24px）；**统一最大宽度 `1280px` 居中**（`max-width:1280px; margin:0 auto`，现有 AccountSettingsView 的 1400 一并收敛），宽屏防表格/表单无限拉伸；纵向 `overflow-y:auto`。
- 顶栏 `SearchInput` 仅承担"本页关键词模糊搜索"单一职责；所有结构化筛选（状态/类型/实体/动作）下沉到页面内「筛选区」，不占顶栏，保证每页搜索行为一致。
- 响应式断点（新增）：`≥1280px` 侧栏常驻、内容区居中；`960–1279px` 侧栏常驻、内容区铺满（去 max-width）；`<960px` 侧栏默认折叠为浮层、内容区铺满。

#### 4.11.2 三类页面模板
- **T1 列表页**：`PageHeader`（标题 + 计数 Badge + 主操作按钮） → `FilterBar`（分段 Tab / 下拉筛选，可选） → `DataArea`（Loading/EmptyState/正常 三态） → 分页/批量区。覆盖：食堂/Banner/菜品（卡片网格）、用户/管理员/动态/反馈/日志/审核中心（DataTable）。
- **T2 详情页**（食堂/档口/菜品）：`PageContainer header`（缩略图 + 名称 + 副标题 + 编辑/删除 `panel-actions`） → 多个 `PageSection`（基本信息 / 数据统计 / 子列表）。图片管理并入基本信息编辑态内的 `ImageUpload`，不另开弹窗。
- **T3 抽屉/表单**（新增/编辑/审核/反馈处理）：新增编辑走 `FormDialog`；查看/审核/处理走 `FormDialog :footer=false` + 标准底部操作条——主操作（通过/保存）居右 primary，危险操作（退回/删除/下架）居右 danger 且经 `ConfirmDialog`，取消居左；必填提示统一出现在字段下方或操作条上方。

#### 4.11.3 卡片网格 vs 表格选择规则
- 图片主导实体（食堂/档口/菜品/Banner）→ **卡片网格**；行数据主导（用户/管理员/动态/反馈/日志/审核）→ **DataTable**。
- 据此本期：菜品列表由现状表格**改为卡片网格**，与食堂/Banner 统一范式。

#### 4.11.4 统一组件（新增轻量封装）
- `PageContainer`（页面根容器：title/breadcrumb/maxWidth + header/default 插槽）、`PageSection`（分节卡片）、`PageHeader`、`FilterBar`（含 `FilterSelect`）、`StatCard`（统计卡，数值色走 token，禁止硬编码）、`FormField`（label + control + error 统一结构）。
- 复用 §4.2 自封装组件族 `DataTable/FormDialog/ConfirmDialog/StatusTag/SearchInput/ImageUpload`；**禁止页面裸写 `el-table`/`el-select`/原生 `input` 绕过 token**（§4.10 红线）。
- 统计卡统一 `StatCard`，消除用户管理/详情页/看板三套样式；数值色统一走 token。

#### 4.11.5 视觉基调与视觉刷新
- **基调**：维持 §4 Apple 克制风——主色深红 `#6B1010`、圆角 16px、半透材质 `blur(20px) saturate(180%)`、spring 动效（§4.3/§4.4）、列表三态齐全、破坏性操作二次确认。
- **本期视觉刷新（在 Apple 克制基调内，不切换设计语言）**：统一留白节奏与层级（结构区更重更暗、交互元素更轻更亮）、强化材质深度与阴影、打磨组件质感与排版（tracking/leading 见 §4.6）、统一空态/加载骨架；消除此前页头 4 种写法、筛选区 3 种、统计卡 3 套、详情抽屉范式分裂等不一致的视觉噪音。

## 5. 开发约束

- **平台定位**：仅提供美食信息公示与点评，**不涉及下单 / 支付 / 外卖**；无"售罄 / 库存"概念，档口营业状态仅作信息展示；浏览对游客开放；列表默认按「热度」降序，可切评分 / 价格，长列表无限滚动，无结果显空状态 + 清除筛选。
- 命名：Java 类 PascalCase、方法/字段 camelCase；数据库表/字段 snake_case（MyBatis-Plus 自动驼峰映射）；前端 TS 接口字段 camelCase，管理端与后端 snake_case 差异经 `api/adapter.ts` 转换，禁止在 View 层直接处理字段名转换
- 所有 API 响应必须包含 `code/message/data` 结构；前端 `http.ts` 统一判定 `code !== 200` 抛异常，页面层 try-catch，Store 的 fetch 方法失败时置空数组不向上抛
- Controller 入参用 DTO + `@Validated` 校验；Service 写操作加 `@Transactional`；评分/点赞计数通过 Spring 事件（`ReviewSubmittedEvent` → 重算 `dish.avg_rating`）异步维护，禁止在主流程内联重算
- 内容审核流：学生提交的菜品、档口 / 食堂基础信息均 `audit_status=pending` → 管理员 `approved/rejected`，小程序端只展示 `approved` 且上架/营业中；评价用 `is_hidden` 控制可见性。Web 后台「菜品审核」与「评价审核」为两个独立模块。管理员对食堂 / 档口 / 菜品有后台 CRUD（上架 / 下架 / 修改 / 删除）最终控制权。审核状态 `audit_status` 与上下架 `status` 解耦（见 §3.x.1）；退回必须填 `reject_reason` 并回显学生端；学生编辑重提**复用原记录**、`audit_status` 回到 `pending`、`reject_reason` 清空（不新建记录）。**下架 / 变更申请不进本审核流，落到新建独立 `apply` 表（专存新增/下架/变更申请，走独立统一审核状态机，不复用实体 `audit_status`），见 §0.4 / §3.x.1 / §3.x.5**。
- 注册仅限 `@bjtu.edu.cn` 校园邮箱 + 验证码；评价一人一菜一条（`uk_review_user_dish`）、点赞一人一票（`uk_useful_user_review`）——业务代码必须与唯一键约束一致
- 小程序端请求超时 8s、管理端 5s；API 基地址集中在 `api/config.ts` 的 `API_BASE_URL`，禁止在业务代码硬编码 URL

### 5.z 二期已拍板技术决策（架构约束，强制）

> 以下为用户拍板的技术决策，全部落于 `tasks/ARCH_DECISIONS_PHASE2.md §5.x`，本期强制遵循：

- **D-A 通知/审核结果写入解耦**：用 Spring `@Async` + **有界线程池**异步写 `notification`（审核结果/评论/@👍 等触发点调用 `AsyncNotificationService`），**不引入 MQ**；主流程不阻塞等待通知落库。
- **D-B view_log 去重**：`view_log` 加唯一键 `uk_view_user_target(user_id, target_type, target_id)`；`HistoryServiceImpl.record()` 改为 upsert（`INSERT ... ON DUPLICATE KEY UPDATE created_at`），同一对象只留最新一行（DDL 见 `migration_phase3.sql` 追加段）。
- **D-C 报表导出实现路径**：后端返回 **CSV 文件流**（`text/csv` + `Content-Disposition`），**零依赖、不引入 Apache POI**；xlsx 留作可选增强（本期不做）。
- **D-D 推荐/热门/广场缓存 + 推荐改 SQL 分页**：「猜你喜欢/热门/广场」列表用应用内 **Caffeine 短 TTL 缓存（60s）+ 写失效**；`DishServiceImpl.recommendDishes()` 全表内存排序**必须改为 SQL 分页**（性能债，见 §3.x.4）。
- **D-E schema 漂移根因治理**：将 `check_schema` 脚本升级为**启动时 fail-fast 校验或 CI 步骤**，防止表结构漂移回归。
- **Q1 成就/成长体系封口**：不建积分/等级/成就/勋章，不建 `achievement`/`user_achievement` 表，task-08 彻底冻结不占位（不预留任何字段/接口）。
- **Q2 轻量社区封口**：不设计置顶/话题/精选运营干预，社区排序不干预（热度 + 关联动态反哺）。
- **Q4 本期必须交付**：①`DELETE /my/account` 账号注销（物理/逻辑删除 + 级联清理本人数据，不建用户关系表）；② 社区举报复用 `user_feedback` 表（`related_type`/`related_id` 关联被举报动态），不新建举报表；③ **删除本人记录**：动态（`DELETE /moments/{id}` 本人）、菜品（`DELETE /dishes/{id}` 本人 created_by）、评价（学生删除本人评价走对应接口，非仅编辑重提）——三者均支持删除本人记录；④ 关联动态双向跳转：菜品详情内关联动态可点进动态详情，档口/食堂详情聚合关联动态。
- **Q5 关注/粉丝流封口**：不碰、不建用户关系表、不留任何余量。

### 5.x 三端一致性红线（2025-10 审计补充）

> 与 §3.x.6 配套。以下为强制一致约束，违反即视为阻断级缺陷。

- **字段命名红线**：对外 JSON 一律 camelCase；`favoriteCount` 为收藏量唯一字段名（`DishVO.collectCount` 为历史误用，必须改名）；`targetType`(枚举) 为 Banner 跳转唯一字段名；评价状态为 `isHidden`(0/1) 而非 `isDeleted`。web 端 `snake_case` 仅允许出现在 `api/adapter.ts` 内部转换，禁止进入 `types/` 或视图层。（注：活动 `startAt`/`endAt` 命名裁决随 Activity 模块本期整体移除不再适用，见 §3.x.6.5。）
- **错误码统一（三端一致）**：
  - 成功 `200`；参数错误 `400`；未登录/登录失效 `401`；无权限 `403`；服务器错误 `500`。
  - **401 统一处理**：小程序 `http.ts` 已 `uni.$emit('auth:unauthorized')` + 清 token + Toast；**web `http.ts` 当前缺失 401 拦截**（直接 `throw` 业务异常），必须补齐：检测到 `code===401` 时清 `localStorage.token` 并跳转登录/emit 事件（见 §5 影响面 W1）。
  - **禁止自定义非标错误码**（如 1001、600 等）；业务失败一律 `code=400` 或 `500` + `message` 描述。
- **喜欢/收藏单一概念红线**：禁止 `like`/`favorite` 双体系、禁止 `like_count` 字段、禁止 `/likes` 端点；原 `/favorites` 后端端点与表本期已彻底删除（见 §0.3.1 descoped / §3.x.6.7），「喜欢 ❤️」具体存储交架构师评估，但对外须统一 `favorite` 语义 + `DishVO.favoriteCount` 计数（§3.x.6.7）。
- **状态枚举红线**：
  - Banner `status`：`enabled`/`disabled`；`targetType`：`DISH`/`ACTIVITY`/`URL`/`NONE`。
  - Dish `status`：`on`/`off`；Canteen/Stall `status`：`open`/`closed`。web 侧内部 `active`/`inactive` 仅作 UI 态，提交/展示必须经 adapter 映射回后端枚举。（注：Activity `status`：`enabled`/`disabled` 枚举随 Activity 模块本期整体移除不再适用，见 §3.x.6.5。）
- **User 无 stall 红线**：`UserVO` 不含 `stallId`；web `userToLegacy` 的 `stall_id` 映射为 stall-owner 残留，必须删除（§5 影响面 W2）。
- **学生 UGC 路径红线**：学生提交档口/食堂仅 `POST /my/stalls`（STUDENT），发布菜品仅 `POST /dishes` 系列；严禁任何 `/stall-owner/**` 路由（§3.x.6.8）。
- **分页结构红线**：列表接口统一返回 `PageResult<T>{ list, total, page, pageSize }`（或 `records` 兼容，但三端 `pageRecords/recordsOf` 已兼容 `list`/`records`/`数组`）；单页非分页接口（如 `/dishes/hot`）返回 `List<T>` 而非包 `PageResult`。

### 5.y 三端影响面清单（2025-10 审计整改，仅方案，由对应工程师执行）

> 本清单仅登记「谁改什么」，不含业务源码变更。优先级：P1=阻断不一致（字段名/路径错误）、P2=健壮性、P3=清理。

**后端工程师（backend）**
- P1-B1：将 `DishVO.collectCount` 改名为 `favoriteCount`（同步 `DishServiceImpl` 赋值、`DishAdminVO` 如复用）。影响 mapper XML 若用 `collect_count` 映射需确认驼峰别名。
- P1-B2：`BannerVO` 确保输出 `targetType/targetId/targetUrl`，移除/弃用 `type` 历史字段对外暴露（DB 列保留兼容）。
- P1-B3：（随 Activity 模块本期整体移除，本条不再适用，改为清理 `activity` 模块/路由/`activity` 表，见 §3.x.6.5）。
- P2-B4：`ReviewVO` 公开场景不得带 `isHidden`/`hasSensitive`（已有，仅确认 admin VO 与公开 VO 分流）。
- P3-B5：`MyStallController`（`/my/stalls`）补登记到 §3.x.5 契约总表注释（已在 §3.x.6.8 登记，无需改代码）。
- 注：`/favorites` 后端端点与表本期彻底删除（§3.x.6.7），「喜欢 ❤️」具体存储方案交架构师在 tasks 评估；本条原「Favorite 模块无需改动」结论已失效。

**小程序工程师（frontend）**
- P1-F1：`types/dish.ts` 的 `favoriteCount` 消费在 B1 改名后去掉 `collectCount ??` 兼容分支（当前 `raw.collectCount ?? raw.favoriteCount`）。
- P2-F2：`review.ts` 的 `toReview` 去除 `userId ?? user_id` 等多余 snake 兼容（VO 已 camelCase），保留 `userNickname`→`userName` 映射。
- P3-F3：确认 `banner.ts` 仅消费 `targetType`（已正确），移除注释中对 `type` 分发的误导描述（§3.x.6.2）。
- 注：`likeDish`/`unlikeDish` 语义重命名指向的 `/favorites` 已彻底删除（§3.x.6.7），「喜欢 ❤️」具体存储交架构师评估；小程序端不得回退到已删除的 `/favorites`，亦不得触发 `/likes` 新端点。

**Web 管理后台工程师（web）**
- P1-W1：`api/http.ts` 补齐 401 处理（清 `localStorage.token` + 跳转登录/emit），对齐小程序（§5.x 错误码）。
- P1-W2：`api/adapter.ts` 的 `userToLegacy` 删除 `stall_id` 映射（stall-owner 残留，§5.x User 红线）；`types` 中 `User.stall_id` 同步删除。
- P2-W3：`banner.ts` `toBanner` 将 `type` 字段标记 `@deprecated`，视图层仅用 `targetType`；`toApi` 继续输出 `targetType`（已正确）。
- P2-W4：`adapter.ts` `activityToLegacy` 随 Activity 模块整体移除一并删除（见 §3.x.6.5）；`dishToLegacy` 去 `raw.favorite_count` 兼容（B1 后仅 `favoriteCount`）。
- P3-W5：`review.ts` 的 `create` 调用 `POST /reviews`（非 admin 路径）属误用，确认是否删除（评价由学生端提交，后台仅审核 hide/delete）。
- **P3-W6（本期定样，实施另排期）**：Web 页面布局与信息组织对齐 §4.11 + 侧边栏 4 分组对齐 §0.3.3——① 侧边栏改为 4 组（反馈与举报处理并入审核与社区、账号设置拆出至个人中心）；② 各页面按 T1/T2/T3 模板重构，引入 `PageContainer/PageSection/PageHeader/FilterBar/StatCard/FormField`；③ 菜品列表改卡片网格；④ 删除遗留未挂载的 `views/dashboard/DashboardView.vue` 与空 `views/activity/` 目录（descoped）。路由前缀维持 `/dashboard/**` 本期不变。纯前端重构，不碰后端/字段名/错误码。
