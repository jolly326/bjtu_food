# 知行食记 · 项目规格说明（project_spec.md）

> **基础规范基线（最高权威）**。所有 agent 与协作者必须服从本文件；冲突时以本文件为准。
> 本文件只规定「不会轻易变、且所有端必须遵守」的基础规范。**多 agent 协作流程见 `docs/WORKFLOW.md`**。
> **唯一可修改者：技术负责人**（需求梳理师 + 架构师合并角色）。其余角色不得改动本文件，发现冲突须提技术负责人，不得自行绕过。

---

## 0. 系统总览

### 0.1 角色模型（仅两种）
- `STUDENT`（**微信自动登录 + 校园邮箱认证**，兼"平鉴官"）：微信打开小程序即自动静默登录为**未认证账号（游客态，`verified=false`）**；通过 `@bjtu.edu.cn` 邮箱验证码认证后 `verified=true`，解锁发布 / 更新菜品、提交 / 更新档口·食堂、写评价、评论、点赞、动态等**社区写操作**。游客（`verified=false`）仅可浏览公开数据 + 提交基础反馈（`POST /feedback` 公开）。**无账号密码登录、无登录页、无登录按钮**（见 §5 认证红线）。
- `ADMIN`（系统管理员 / 食堂后勤）：审核 UGC、看板、食堂 / 档口 / 菜品 CRUD + 上架下架、用户 / 管理员管理、**录入活动（活动标题 / 描述 / 发布时间 / 公众号文章链接，手动录入，不经学生 UGC、不经审核流）**。**管理后台登录沿用方案 C：管理员账号密码 + BCrypt + JWT（放弃微信开放平台扫码 / 复用小程序码）**，见 §1 认证与 §5 认证红线。
- **无独立 `STALL_OWNER` 角色，亦无 `/stall-owner/**` 路由。**
- **活动功能（2026-08-12 拍板，收回「活动仅经 Banner 触达」旧决策）**：活动为**独立功能模块**，数据由后台运营（ADMIN）手动录入；小程序首页「万能区域」展示最近一条活动预览，点击进入独立「活动列表页」，列表页点击具体活动经微信 web-view 跳转对应公众号文章。**Banner 已整体移除（2026-08-18 拍板）**，活动不依附任何 Banner 类型，独立成表承载。

### 0.2 数据流闭环
1. **浏览**：首页推荐 / 搜索 → 详情 → 评价 / 分享。
2. **贡献（平鉴官）**：需 `verified=true`（游客未认证不可贡献）→ 提交 → `audit_status=pending` → 后台审核 → `approved` / `rejected`（回写 `reject_reason`，学生**复用原记录**重提回 `pending`）。
3. **运营（后勤）**：后台审 UGC / CRUD → 小程序即时体现。
4. **活动闭环（2026-08-12 拍板，收回「活动不独立成模块」旧决策）**：后台运营录入活动（活动标题 / 描述 / 发布时间 / 公众号文章链接，手动录入，不经学生 UGC、不经审核流）→ 首页「万能区域」展示最近一条活动预览（标题 + 发布时间）→ 点击单元格整列进入独立「活动列表页」（按发布时间倒序）→ 点击具体活动经微信 `web-view` 跳转对应公众号文章。活动为独立模块（Banner 已移除）。

### 0.4 三端定位与数据链路（2026-08-05 拍板，强制）
- **小程序（`client/`）= 服务端 / 用户端**：学生使用，是**业务数据的唯一产生源头**（浏览、UGC 提交、评价、动态、反馈）。
- **后端（`server/`）= 数据服务**：唯一数据存储与业务规则所在；小程序与 Web **共用同一套 API 契约**（`/admin/**` 供 Web，`/` 用户接口供小程序）。
- **Web 管理端（`web/`）= 辅助后端管理数据的 UI 工具（非用户端）**：职责 = 对小程序产生的数据做**管理（CRUD / 上下架 / 排序 / 配置）与审阅（UGC 审核 / 内容治理 / 操作日志 / 数据总览）**；Web 不产生业务数据，只消费与管理后端数据。
- **数据链路**：小程序产生数据 → 后端落库（MySQL）→ Web 经 `/admin/**` 读取与管理 → 小程序即时反映。
- **Web 端管理能力全景**：
  - 信息管理：食堂 / 档口 / 菜品（业务信息）+ 广播（首页配置；轮播 Banner 已移除）
  - 内容审核：评价 / 动态 / 反馈（含 UGC 申请）
  - 用户与权限：学生账号 / 管理员账号（超管分层）
  - 系统：操作日志 / 工作台（待办 + 数据总览）
  - （2026-08-12 拍板）**活动管理**：Web 端新增活动录入 / 列表管理能力，对应小程序新增的独立「活动」数据对象（活动标题 / 描述 / 发布时间 / 公众号文章链接）——此为首次在 Web 端引入小程序不存在的专用数据模型，属已拍板例外（活动模块整体新增）。
- 约束：**Web 端任何新增管理能力，必须以小程序已存在的数据对象为前提**；不得在 Web 端引入小程序不存在的数据模型或业务（**活动模块除外**：2026-08-12 拍板，活动为整体新增的独立数据对象，Web 端录入、小程序活动列表页消费、经 web-view 跳转公众号文章）。

#### 0.4.1 工作台（DashboardView）契约（2026-08-18 拍板）
> **首屏定位**：Web 管理后台登录默认落地页为 `/dashboard`（`DashboardView.vue`，面包屑「工作台」）；登录成功或访问登录页时已登录均重定向 `/dashboard`。**工作台 = 待办 + 数据总览，非图表看板**（无 ECharts 图表，与「数据看板 / 统计报表」边界见下）。

- **一次请求**：工作台页面 onMounted 发起**单次** `GET /admin/dashboard?range=week`，一次返回全部待办数 / 待办明细 / 规模指标 / 近期操作；页面不逐项发多个接口。失败显示「工作台加载失败 + 重试」，成功渲染三块。
- **待办卡（3 项，显示 count，点击直达对应管理 tab）**：
  - 待审核申请：`pendingApplyCount` → `/dashboard/audit?tab=feedback&section=apply`（UGC 申请审核）
  - 待审核动态：`pendingMomentCount` → `/dashboard/audit?tab=moment`（动态审核）
  - 待处理反馈：`pendingFeedbackCount` → `/dashboard/audit?tab=feedback&section=feedback`（反馈处理）
- **8 项规模指标（指标卡，点击直达对应管理 tab）**：
  | 指标 | 字段 | 跳转 |
  |---|---|---|
  | 食堂 | `totalCanteenCount` | `/dashboard/content?tab=canteen` |
  | 档口 | `totalStallCount` | `/dashboard/content?tab=stall` |
  | 菜品 | `totalDishCount` | `/dashboard/content?tab=dish` |
  | 学生 | `totalUserCount` | `/dashboard/system?tab=user` |
  | 动态 | `totalMomentCount` | `/dashboard/audit?tab=moment` |
  | 评价 | `totalReviewCount` | `/dashboard/audit?tab=review` |
  | 申请 | `totalApplyCount` | `/dashboard/audit?tab=feedback&section=apply` |
  | 反馈 | `totalFeedbackCount` | `/dashboard/audit?tab=feedback&section=feedback` |
- **统计口径（后端 `DashboardVO` 为准，经 `StatsController.overview`）**：
  - 规模指标：全量计数。学生数 `user.role='student'`；菜品数按 `audit_status='approved'`；其余（食堂/档口/动态/申请/反馈/评价）全表计数。
  - 待办 count：`apply.status='pending'`、`moment.audit_status='pending'`、`feedback.status='pending'`。
  - 待办明细（各 5 条，按时间倒序）：`pendingApplies` / `pendingMoments` / `pendingFeedbacks`（`DashboardVO.TodoItem{ id,title,type,time }`）。
  - 近期操作：`recentLogs`（操作日志最近 10 条，`DashboardVO.RecentLogItem{ id,operator,action,target,time }`）。
  - **容错**：各统计项独立 try-catch，任一失败给默认值（0 / 空列表），保证工作台必能加载。
- **与「数据看板 / 统计报表」边界**：工作台（DashboardView）**不含 ECharts 图表、不含趋势/排行/上新指标**；`GET /admin/dashboard` 返回的 `DashboardVO` 虽含 `newDishCount/newReviewCount/hotDishes/hotCanteens/viewTrend/reviewTrend`（供历史/未来的图表看板复用），但当前 DashboardView **不消费这些图表字段**，仅渲染待办 + 规模指标 + 近期操作。图表看板非本期交付（无 ECharts 看板页面）。
- **契约实现状态**：前后端 `/admin/dashboard` 契约与字段命名已对齐（前端 `web/src/api/dashboard.ts` 的 `DashboardData` 声明与后端 `DashboardVO` 一一对应），登录首屏已落地。**2026-08-18 对账结论：spec 与代码一致，无待办缺口，无需新增开发 task**。

### 0.3 一致性红线（全局，强制）
- 角色仅 `STUDENT` / `ADMIN`；**禁止** `STALL_OWNER` 或 `/stall-owner/**` 路由；`/admin/**` 仅 `ADMIN`（含 `SUPER_ADMIN` 分层，见 §5.x）。
- 菜品 / 档口 / 食堂均含独立 `audit_status`(pending/approved/rejected) + `reject_reason`（与上下架 `status` 解耦）。
- 活动为**独立功能模块**（2026-08-12 拍板，收回「`ACTIVITY` 已移除」旧红线）：活动数据由后台运营录入（活动标题 / 描述 / 发布时间 / 公众号文章链接），经独立「活动列表页」展示，点击经微信 `web-view` 跳转公众号文章；活动与菜品 / 档口 / 广播均不耦合，独立成表承载。（原「Banner 跳转用 `target_type` 枚举」红线随 Banner 移除，2026-08-18）
- 实体贡献「下架 / 变更」申请落**独立 `apply` 表**（不复用实体 `audit_status`）。
- 前端 UI 遵循 §4（spring 动效、即时反馈、半透材质、reduced-motion 降级）。
- **认证与鉴权（2026-08 拍板，微信登录体系）**：
  - **无账号密码登录**：小程序端**无密码、无登录页、无登录按钮、无注册页**；微信打开即静默登录（`POST /auth/wechat-login`），默认得到 `verified=false` 的游客态账号。
  - **`verified` 门槛**：社区写操作（发布/评论/点赞/动态等）鉴权从「需登录」改为「需 `verified=true`」；`verified` **不进 JWT**（JWT 仍只含 `userId`），后端按 `user.verified` 实时判定。
  - **游客权限矩阵**：游客可浏览全部公开数据 + `POST /feedback`（公开无需认证）；需认证功能**入口不置灰**，点击时弹认证引导。
  - **邮箱是唯一迁移 / 绑定凭证**：`@bjtu.edu.cn` 邮箱验证码认证即绑定当前微信；同一邮箱被新微信认证时**直接替换旧微信绑定**（旧数据归属跟到新绑定微信）；**不设解绑入口**。
  - **管理后台登录例外（方案 C）**：管理后台维持「管理员账号密码 + BCrypt + JWT」，与小程序微信登录体系解耦；`/admin/**` 仍仅 `ADMIN`（含 `SUPER_ADMIN`）。

---

## 1. 技术栈
- 后端：Spring Boot 3.2 + Java 21，ORM MyBatis-Plus 3.5.5（BaseMapper + XML，`resources/mapper/*.xml`）；API 文档 **SpringDoc OpenAPI（`/swagger-ui.html` + `/v3/api-docs`），不使用 Knife4j**。
- 小程序端：uni-app + Vue 3 (`<script setup>`) + TypeScript + Pinia，目录 `client/`。
- Web 管理端：Vue 3 + Vite + TypeScript + Element Plus，目录 `web/`，无 Pinia。**定位：辅助后端管理数据的 UI 工具（非用户端）**——只经 `/admin/**` 接口消费与管理小程序产生的数据，见 §0.4。
- 数据库：MySQL 8.0，库 `bjtu_food`，utf8mb4；**建表脚本唯一权威：`server/src/main/resources/db/schema.sql`**（`user.role` 默认 `'student'`）。
- 认证（微信登录体系，2026-08 拍板，详见 §5「认证与鉴权」）：JWT（7 天），`Authorization: Bearer {token}`。小程序端无账号密码，经 `POST /auth/wechat-login`（`code2Session` 静默建号/取号）获取 JWT；社区写操作需 `verified=true`。`verified` 不进 JWT，后端按 `user.verified` 实时判定。
- 管理后台登录（方案 C）：仍用「管理员账号密码 + BCrypt + JWT」；不引微信开放平台扫码，不复用小程序码。与小程序微信登录解耦。

## 2. 目录结构
- 后端按业务分包：`com.bjtufood.{auth|canteen|dish|review|content|upload|common}`，每模块 `controller/service(+impl)/mapper/entity/dto/` 四层，**禁止跨层调用**（Controller 不得直接调 Mapper）。
- 小程序 `client/src/`：`api/`、`types/`、`stores/`、`pages/`（**TabBar 固定 3 页：home / community / profile，2026-08-03 移除 find**——搜索改为首页顶部搜索框入口，跳转二级搜索页 `/pages/find/index`，非 tab 页；消息中心、我要贡献进 `profile`，不占 TabBar；**收藏功能已全量移除（2026-08-12 复核），无收藏入口**）、`components/`。

### 2.1 小程序页面架构（2026-08-15 复核；2026-08 微信登录体系同步）
> 与 `client/src/pages.json` 严格一致。当前共注册 **19 个页面**：主包 9 + `pages-detail` 分包 4 + `pages-user` 分包 6；其中 2 个为「待清理孤儿路由」（`publish-dish` / `submit-stall`，已注册但无 `navigateTo` 入口，见 §2.1.3），**实际可达 17 个**。**微信登录体系落地后新增「学号邮箱认证页」`pages/profile/verify/index`（第 10 个主包页）**（认证弹层 `AuthSheet` 的完整页形态，见 §5.y）；`pages/profile/notifications/index` 为「系统通知」（「我的」菜单进入，非孤儿）。页面级设计文档见 `docs/pages/`（索引 `docs/pages/readme.md`）。

#### 2.1.1 主包（9，微信登录体系落地后新增认证页 → 10）
| 路由 | 标题 | 设计文档 | 入口 |
|---|---|---|---|
| `pages/home/index` | 首页 | [首页.md](./pages/首页.md) | TabBar |
| `pages/find/index` | 搜索 | [搜索页.md](./pages/搜索页.md) | 首页搜索框 `navigateTo` |
| `pages/profile/index` | 我的 | [我的.md](./pages/我的.md) | TabBar |
| `pages/community/index` | 动态 | [动态页.md](./pages/动态页.md) | TabBar |
| `pages/feedback/index` | 意见反馈 | [意见反馈.md](./pages/意见反馈.md) | 「我的」菜单 |
| `pages/profile/notifications/index` | 系统通知 | — | 「我的」菜单 |
| `pages/profile/verify/index` | 学号邮箱认证 | — | 「我的」认证入口 / `AuthSheet` 弹层跳转 |
| `pages/activity/index` | 最新活动 | [最新活动.md](./pages/最新活动.md) | 首页万能区域 / 「我的」菜单 |
| `pages/about/index` | 关于我们 | [关于我们.md](./pages/关于我们.md) | 「我的」菜单 |
| `pages/webview/index` | 外部链接 | — | 活动 `web-view`（仅活动使用） |

#### 2.1.2 分包
**`pages-detail`（4）**
| 路由 | 标题 | 设计文档 | 入口 |
|---|---|---|---|
| `pages/pages-detail/moment` | 动态详情 | [动态详情.md](./pages/动态详情.md) | 动态/评价卡片点击 |
| `pages/pages-detail/review` | 发表评价 | [发布动态.md](./pages/发布动态.md) | 菜品详情「写评价」 |
| `pages/pages-detail/dish` | 菜品详情 | [菜品详情.md](./pages/菜品详情.md) | 卡片点击 |
| `pages/pages-detail/review-list` | 全部评价 | [全部评价页.md](./pages/全部评价页.md) | 菜品详情「查看全部评价」 |

**`pages-user`（6）**
| 路由 | 标题 | 设计文档 | 入口 |
|---|---|---|---|
| `pages/pages-user/publish-moment/index` | 发布动态 | [发布动态.md](./pages/发布动态.md) | 「我的」/ 动态页发布入口 |
| `pages/pages-user/my-moments/index` | 我的动态 | [我发布的.md](./pages/我发布的.md) | 「我的」菜单 |
| `pages/pages-user/publish-dish` | 发布菜品 | — | **孤儿，见 §2.1.3** |
| `pages/pages-user/submit-stall` | 提交档口 | — | **孤儿，见 §2.1.3** |
| `pages/pages-user/my-reviews/index` | 我的评价 | [我发布的.md](./pages/我发布的.md) | 「我的」菜单 |
| `pages/pages-user/profile-edit/index` | 个人信息 | [个人信息.md](./pages/个人信息.md) | 「我的」→ 头像/昵称 |

#### 2.1.3 待清理孤儿路由（已注册、无入口）
- `pages/pages-user/publish-dish`、`pages/pages-user/submit-stall`（发布菜品 / 提交档口）→ 原经 `ContributeSheet`「我要贡献」弹层进入，该弹层未实现、无入口。建议删除路由 + 目录。
- `pages/profile/notifications/index` 为系统通知（「我的」菜单进入，**非孤儿**）；旧「消息中心 / messages」路由已清理，职责由 `feedback`（意见反馈）+ `notifications`（系统通知）承接。

#### 2.1.4 关键设计决策与约束
- **TabBar 固定 3 页**：`home` / `community` / `profile`；搜索、意见反馈、活动、关于、消息中心均为二级页（经 TabBar 页内入口进入）。
- **首页三段式（无定位条，2026-08-15 拍板）**：（1）广播栏（**动态信息流**：最新评价/动态摘录，触摸暂停，**点击按类型路由** community→动态列表 / dish→菜品详情 / url→web-view；仅承载动态类，非动态类不进首页广播）/（2）万能区域（**水平一行网格**，每列独立跳转，当前「最新活动」→活动列表页，未来模块同排均分）/（3）瀑布流（`WaterfallList` 双列，综合热度排序，**距你距离由后端计算并随菜品下发展示（前端无定位条 UI）、无收藏**）。首页不显示定位条、不弹坐标授权。
- **搜索（2026-08-03）**：二级搜索页 `find`，非 tab。
- **活动为独立模块（2026-08-12）**：列表页 `activity` 展示运营活动，点击经 `web-view` 跳公众号文章。`web-view` 仅活动使用；广播外链仍「复制链接 + toast」（Banner 已移除）。
- **反馈合并（2026-08-15）**：原「反馈中心」(`messages-services`) 已并入 `feedback` 意见反馈页（提交表单 + 我的反馈记录同页）；早期「联系/contact」表单亦并入。无独立反馈中心/contact 路由。
- **反馈重设计（2026-08-17 拍板）**：`feedback` 页定为**收集用户诉求**的轻量单视图动态表单——**克制温度引导**（仅一行短标题「想说点啥，直接说」，不做大段文案）+ 口语化类型 chip + 类型与字段合一为一张大卡；类型前置单选必选（提个想法/推荐菜品/信息不对/App 有问题），字段随类型动态切换且**收集管理员所需关键结构化字段**（每类型必填 1 个，辅助选填，无冗余提示文案）；**不设登录守卫，任何人可提交**（`POST /feedback` 维持公开 PUB）；「新增菜品」从纠错二级细分提升为一级类型（后端扩 `add` 枚举）；新增附图上传（`Feedback.images` JSON 数组，Web 端缩略图展示）；纠错点含「已下架」作证流程（不要求正文，可照片/文本作证）；**不收集联系方式**（移除前端字段，后端 `contact` 列保留兼容历史）；**匿名心智**（底部「匿名提交 · 不记账号」）；移除「我的反馈」Tab（进度追踪后续另做，`GET /feedback/my` 保留）；举报继续走内容页弹窗不进本页。
- **食堂与档口降级为菜品属性（2026-08-15）**：学生决策主体是菜品，食堂/档口为 `dish.canteen` / `dish.stall`，仅在菜品详情「来源信息区」展示；无 `canteen`/`stall` 独立路由。
- **收藏功能已全量移除（2026-08-12 复核）**：无收藏入口。
- **微信登录体系（2026-08 拍板，详见 §5.y）**：小程序无登录页/登录按钮/注册/密码体系；微信打开即静默登录为游客态（`verified=false`）。「我的」页对游客展示认证引导卡片；需认证功能入口不置灰、点击弹 `AuthSheet` 认证弹层（学号邮箱 + 验证码），认证成功后自动继续原动作。「我的」页展示已绑定邮箱（`bind_email`）与认证状态。新增独立认证页 `pages/profile/verify/index`（`AuthSheet` 的完整页形态）。
- **发布统一组件**：`publish-moment`（发动态）与 `review`（发评价）复用统一 `PublishReview` 组件；`publish-dish`/`submit-stall` 为待清理孤儿（见 §2.1.3）。
- **「我发布的」聚合（待实施）**：规划中 `my-moments`（我的动态）+ `my-reviews`（我的评价）合并为单一「我发布的」页；当前代码仍两页并存（均由「我的」菜单进入），合并待实施。

#### 2.1.5 已移除（历史保留）
- `settings`（设置，2026-08-03）→ 设置项内嵌 `profile`，无独立路由。
- `activity-detail`（活动详情，2026-08-12）→ 活动直接经 `web-view` 跳转，无中间详情页。
- `my-publish` / `my-submissions`（2026-08-15）→ 由 `my-moments` / `my-reviews` 取代（合并规划见 §2.1.4）。
- `review-list`（档口/食堂维度聚合评价）→ 取消独立跳转，改内联；菜品维度「全部评价」保留为独立页（§2.1.2）。
- `dish` 原底部弹层 `DishDetailSheet` 已弃用（2026-08-12 复核恢复为独立二级页 `pages-detail/dish`）。
- `notify`（旧消息中心，历史）→ 职责由 `profile` 消息区块 + `feedback` 承接；`messages` 残留路由见 §2.1.3。
- **账号密码登录体系（2026-08 微信登录体系拍板移除）**：无登录页 / 注册页 / 密码修改 / 密码重置；`AuthSheet` 从「登录表单」重构为「学号邮箱 + 验证码认证弹层」（详见 §5.y）。「退出登录」语义改为「清除本地登录态」（微信重新打开仍静默登录）。
- Web `web/src/`：`api/`(含 `adapter.ts`)、`views/`、`components/`、`router/`。
- 上传图片存 `uploads/images/YYYY/MM/{uuid}.{ext}`，DB 只存相对路径 `/images/...`。

## 3. API 基础规范
- 统一响应：`{ code: number, message: string, data: T }`；成功 `code=200`；异常由 `GlobalExceptionHandler` 统一包装，Controller 不得裸抛。
- 错误码：`200` 成功 / `400` 参数 / `401` 未登录 / `403` 无权限 / `500` 服务器错误；**禁止自定义非标错误码**（如 1001/600）。
- 认证：JWT 经 `JwtAuthFilter`；白名单：`/auth/wechat-login`、`/auth/email-code`、`/auth/verify-email`、`GET /auth/profile`、`GET /dishes/**`、`GET /canteens/**`、`GET /stalls/**`、`GET /reviews`、`GET /moments/**`、`GET /broadcasts`、`GET /categories`、`/feedback`（公开提交）、Swagger 相关；学生社区写操作需 `verified=true`（见 §5 认证与鉴权），不再依赖 `STUDENT` 角色；`/admin/**` 仅 `ADMIN`。**移除 `/auth/login`、`/auth/register`、`/auth/password/reset`（废除账号密码登录）**。
- 分页：`PageResult<T>{ records, total, page, pageSize }`，用 MP 分页插件；单页非分页接口返回 `List<T>`。
- 金额：存储与传输一律「分」（int/Long）；分↔元转换必须在 api 层统一（`utils/money` 的 `fenToYuan`/`yuanToFen`），**禁止页面/组件层裸算**；前端统一展示已为元的 `price`（不得再在模板 `/100`）。
- 数据隔离：`dish.created_by=当前用户`，学生仅读写自己提交；从 `SecurityUtil.getCurrentUserId()` 取用户，禁止信任前端 userId。
- **接口契约 / 状态机 / 字段命名裁决（UGC 审核、Dish、Review、User、喜欢语义、学生 UGC 路径等）**：新增接口须先在 `server/src/main/resources/db/schema.sql` 与代码注释中登记契约再实现，不得绕过本文件红线。

## 4. UI 设计规范（Apple Design 风格）

### 4.1 适用范围与八原则
- 适用端：微信小程序（uni-app）、Web 管理后台（Vue3 + Element Plus）。
- 八原则：Purpose / Agency / Responsibility / Familiarity / Flexibility / Simplicity / Craft / Delight；流体交互四要素：即时响应、1:1 直接操控、可中断、速度 / 动量接力。

### 4.2 视觉 Token（基线）
- 品牌主色：朱砂红 `#9B2A1D`（浅色模式主色，故宫红墙灵感；**2026-08-16 拍板，由暖杏色 `#D4884C` 进一步定调为朱砂红 `#9B2A1D`**，沉稳庄重、有文化厚度，不冷不土；深色模式主色为同色相提亮朱砂红 `#C45A3C`，见 `client/src/theme/tokens.ts` 与 `App.vue`）。小程序按钮统一 `AppButton`（primary 取朱砂红 `#9B2A1D`，outline/text 沿用）/ 管理端侧栏同步改用朱砂红（替代旧深红 `#6B1010`）。实际色值以 `client/src/uni.scss` 的 `$uni-color-primary`（小程序，已对齐 `#9B2A1D`）与 `web/` 主题变量（管理端）为准，本文仅定权威色号。
- 圆角：卡片 `16px`；底部弹层 `20px 20px 0 0`。材质模糊 `blur(20px) saturate(180%)`；按下缩放 `0.97`；弹层阴影 `0 -8px 30px rgba(0,0,0,0.12)`。
- 小程序自研组件（新页面必须复用）：`ImageSwiper/DishCard/WaterfallList/Rating/TagLabel/CardSection/EmptyState/AppButton/CustomTabBar/MomentCard/SearchBar/StatusBadge/UsefulButton/ImageFallback/SectionTitle/StallCardSingle/ImageUploader/RelatedPickerSheet`；**`CategoryTabs`、`Loading` 组件已于清理提交 f9560c6 删除**——加载态改内联骨架屏（不再依赖 Loading 组件），分类切换由 `SegmentTabs`/筛选条替代，设计规范见 `docs/pages/TEMPLATE.md` 已校准。
- 管理端：Element Plus + 自封装 `DataTable/FormDialog/ConfirmDialog/StatusTag/ImageUpload`；**`SearchInput` 组件已于清理提交 f9560c6 删除**，管理端搜索统一用 `el-input`，文档 `docs/web-ui.md` §七已校准。
- **小程序图标统一使用 SVG 矢量图标**（本地 `client/src/assets/icons` 优先，缺失从阿里云矢量库 Iconfont 经 MCP 拉取）：搜索=ic-search、位置=ic-location、喜欢=ic-heart、有用/点赞=ic-thumb、热门=ic-fire、限时=ic-clock、猜你喜欢=ic-lightbulb、分享=ic-share、评价=ic-comment、发布=ic-plus、举报=ic-report（图标映射见本 § 上文列表）。语义唯一：ic-heart=喜欢（不与点赞混用）、ic-thumb=有用/点赞；**收藏功能已移除，无收藏图标**。**禁止 emoji 字符充当图标**。

### 4.3 动效系统（Motion）
| 交互 | Damping | Response |
| --- | --- | --- |
| 常规 UI | 1.0 | 0.3–0.4 |
| 抽屉 / Sheet | 0.8 | 0.3 |
| 旋转 / 翻动 | 0.8 | 0.4 |
| 位置重排 | 1.0 | 0.4 |
- 默认全站 `damping 1.0`；仅手势带动量时加回弹 `0.8`。可中断：永远从当前屏幕呈现值起步。Web 用 Motion/Framer Motion：`1.0≈bounce 0`、`0.8≈bounce 0.2`。

### 4.4 交互反馈
- 点按：按下即时 `scale(0.97)`；命中区 +~10px 滞回，可按住拖离取消。
- 抽屉 / Sheet：spring `0.8/0.3`，手势可中断、按速度符号决定提交 / 回弹（阈值 ~50%）。
- 弹窗：锚定触发源，进出同路径、缓动镜像对称。
- 滚动橡皮筋：`(over·dim·k)/(dim+k·|over|)`，`k≈0.55`。

### 4.5 材质与层级
- 半透导航 / 工具条 / 抽屉：`backdrop-filter: blur(20px) saturate(180%)` + 半透底；材质权重编码层级（结构区更重更暗，交互元素更轻更亮）；不叠两层轻透面。

### 4.6 字体排版
- tracking 随字号（大标题 `-0.02em`，正文 `0`）；leading 反比（大标题 ~1.05，正文 ~1.5）；系统字体优先；`rem`/`em` 随用户字号缩放。

### 4.7 可达性与降级
- `prefers-reduced-motion: reduce` → 交叉淡入、去弹性过冲；`prefers-reduced-transparency` → 去模糊；`prefers-contrast: more` → 近实底 + 边框。
- 小程序：无 Pointer Events，用 touch + 自记速度历史；`backdrop-filter` 真机部分支持降级纯色半透 + 阴影；动画只用 `transform`/`opacity`。

### 4.8 组件级约定
- 卡片 tap `scale(0.97)`、入场 spring `1.0/0.3`；TabBar spring `1.0/0.3`；抽屉 / Sheet §4.4 `0.8/0.3` + 手势中断；列表 / 瀑布流滚动橡皮筋；Toast 四态同帧触发；列表页三态（Loading/EmptyState/正常）。
- **活动列表页（2026-08-12 新增）**：
  - **header 设计**：统一二级页规范——左上角返回箭头（ibenefit `backToHome` reLaunch 首页）、居中加粗标题「最新活动」、右上角留空；浅色背景，遵循 §4 一致性。
  - **卡片设计**：纵向列表按发布时间倒序；标题稍大字号加粗、发布时间灰色小字（相对时间如「2小时前」/「昨天」）位于标题下方、简要描述更小字号置于底部；卡片 tap 反馈 `scale(0.97)`、入场 spring `1.0/0.3`（与全局卡片一致）。
  - **手势交互**：整卡 `@tap` 经微信 `web-view` 跳转对应公众号文章链接（活动唯一 web-view 场景，见 §2.1）。
- **广播栏（2026-08-12 新增，首页顶部；内容定位见 §2.1.4 首页三段式，与 `docs/pages/首页.md` §二 对齐）**：
  - **内容**：动态信息流 ticker——学生对菜品的最新评价 / 动态摘录，纯文本循环滚动；每条格式「评价人：菜品名 ⭐评分 摘录」；每次显示一条，不展示头像；仅承载动态类（`community` / `dish` / `url`），非动态类（`NOTICE` / `ACTIVITY` 等）不进首页广播。
  - **动效**：垂直滚动 ticker（位移 ≤8rpx 起步、缓动循环，`prefers-reduced-motion` 降级为静态轮播或停留），遵循 §4.9「动效从简」红线——禁长 keyframe、禁大位移；轮播间隔约 3s，可中断（用户触屏暂停）。
  - **手势交互**：整栏 `@tap` 按当前条目 `broadcastType` 路由分发：`community`→动态列表页（`pages/community/index`）、`dish`→菜品详情页、`url`→web-view（复制链接 + toast）；非动态类不进此流。

### 4.9 小程序 MVP 红线（布局 / 动效 / 图标 / 组件渲染）
- **布局（750rpx 视口）**：根容器视为 750rpx；横向用 `flex` + `flex-wrap`/`flex:1`/`min-width:0` 防溢出；图片 / 卡片 `width:100%` + `box-sizing:border-box`；禁止横向滚动条；长文本 `-webkit-line-clamp` 截断。每页须通过「真机 750rpx 无横向滚动 / 无裁切」。
- **动效（从简）**：仅 uni-app `<transition>`（位移 ≤8rpx）与简单 CSS `transition`（opacity/transform 轻量）；禁止 `@keyframes` 长动画、大位移、`scale>1` 入场；手势 Sheet / 抽屉仍走 §4.4，入场不做复杂 keyframe。
- **图标（SVG 矢量）**：按 §4.2 映射（本地 `assets/icons` 优先 + Iconfont 兜底）；新增语义须登记图标名并将 SVG 下载至 `client/src/assets/icons`，禁止 emoji 字符当图标，不得私自引入未登记图标。语义唯一：ic-heart=喜欢、ic-thumb=有用/点赞，互不混用。
- **组件渲染（禁止 wx:for 内具名 slot 分发）**：小程序多列 / 瀑布流组件**禁止**在父组件用 `<template #x>` 向子组件同名 `<slot name="x">` 分发——uni-app 编译 mp-weixin 后父组件 N 个同名 slot 片段无法正确映射，子组件不消费该 slot 时整块**空白不渲染**（实测 `WaterfallList`：find/canteen 残留 `#card` 调用导致菜品区整块空白，阻断级 bug，2026-07-31）。`WaterfallList` 已内部 `import DishCard` 直接渲染，**禁止再向其传具名 slot**，统一 `<WaterfallList :list @card-click="goToDetail"/>` 经事件上抛父级。
- **小程序页面级 / 组件级 UI 设计细则（三态强制、AppButton 类型白名单、表单页 scroll-view 强制、emoji 登记前置、未生效设置禁虚假控制、金额 api 层统一、关联对象走正式 API、负向操作弱化、Sheet/SegmentTabs/ReviewItem/FeedbackForm 等组件抽取契约等 28 条）由小程序开发工程师按优先级落地；本文件仅定最高红线。**
- **UI 全量审计红线（2026-08-02 补充，BLOCKER 级，违反即阻断）**：以下规则自 2026-08-02 全量审计结果提炼，**与上方四条红线同属强制，新增/整改页面不得回退**：
  - **固定底栏避让**：任何含固定底栏（`submit-bar` / `comment-bar` / `action-bar`）的页面，其 `.scroll-wrap` 必须加 `padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom))`，**禁止**内容被底栏遮挡（BLOCKER 级）。
  - **事件绑定统一 `@tap`**：小程序内所有可点元素事件绑定统一用 `@tap`，**禁止**混用 `@click`（uni-app 编译 mp-weixin 时 `@click` 行为与 `@tap` 不一致，易致命中区/手势异常）。
  - **按压缩放统一 `var(--press-scale)`（非按压强调 scale 须量化 token）**：可点元素按下反馈一律 `transform: scale(var(--press-scale))`，`--press-scale` 固定 `0.97`，**禁止**任何裸 `scale(0.9/0.95/0.97/0.985/0.99)` 数值散落。适用范围覆盖**所有交互元素**：`.pressed` 类、`@tap` 触发元素的 `:active`、`.sheet-option`、`.cell`、action icon 等一律不得写裸 scale 值。**grep 全仓应 0 处裸 `scale(...)`**（`pages/profile/index.vue` 的注释说明除外，仅注释、非样式规则），整改后须复验此 grep-zero 期望不破。⚠️ **裸 scale 红线须区分「按压 scale」与「非按压强调 scale」**：按压一律 `scale(var(--press-scale))`；**非按压强调 scale（如 tab 选中放大高亮 `scale(1.05)`）须量化为独立 token（如 `--tab-active-scale`）并在 `client/src/uni.scss` 登记**，方不作为 grep-zero 违规——未登记的非按压 `scale(...)` 仍计入 grep-zero 违规。
  - **图标统一走 `IconSvg`**：所有功能 / 情感图标一律经 `<IconSvg name="…" />` 渲染 `client/src/assets/icons` 下 SVG，**禁止**手写 `<text>+</text>`、`content: '+'`、`✦` 等文本 / Unicode 字符当图标（与 §4.2 / §4.9 emoji 红线同源强化）。⚠️ **`IconSvg` 必须注册中性 `empty` 占位键，缺失/未注册键禁止静默回退到语义图标**：`IconSvg` 内部**不得**采用 `ICONS[name] || ICONS.dish` 这类「未命中键静默落到语义图标（如 `dish` 碗）」的回退写法——拼写错误 / 未注册键（如 `name="empty"`）会无声渲染成菜品碗，造成「空状态显示菜品碗」这类静默语义 bug。须注册专用 `empty` 中性占位键（不可见/中性占位 SVG），缺失键渲染该占位键而非语义图标；**`IconSvg` 现已在 dev 环境（`import.meta.env?.DEV`）对未知 `name` 触发 `console.warn`（仍暂回退 `dish` 以保渲染，但告警已落地）**，便于及时发现拼写/注册遗漏。⚠️ **审计须 diff 字符串字面量 icon 与 `ICONS` keys，防未注册键漏网**：凡以**字符串字面量**向 `SettingCell` / `CustomTabBar` / `ContributeSheet` / `AppButton` 等组件传入 `icon`/`name` 属性（而非动态键），审计时须与该组件实际读取的 `ICONS` 注册键做 diff，确认每个字面量均已注册；未注册键（如第八轮 `profile/index.vue:58` 的 `folder` 未注册、静默成碗）即便 dev `console.warn` 也不得放过，须登记整改——`console.warn` 仅辅助发现、不替代静态 diff 核查。⚠️ **中性占位必须为 `empty`（非 `dish`），且覆盖「IconSvg 回退目标」与「任何硬编码 ImageFallback / 破图占位」两处**：① `IconSvg` 的回退目标（含 dev 告警后的兜底落点）必须落在 `empty` 中性占位键，**不得**保留 `dish` 语义图标在中性占位语境的残留；② `ImageFallback.vue` 等全局图片裂图兜底组件的模板**硬编码**占位（如 `name="dish"`）一律改为 `name="empty"`——破图 / 空态语境禁止用语义图标（碗 `dish`）冒充中性占位（头像 / 档口 / 评价图加载失败全显示成碗属静默语义 bug，且该类硬编码不触发未注册告警，是第九轮新发现的全局兜底组件高危盲区）。⚠️ **审计须 grep 模板 `name="dish"` / `name="empty"` 逐文件核对中性语境**：凡模板出现 `name="empty"` 须确认确为中性占位语义；凡出现 `name="dish"` 须确认是「菜品 / 档口图语义」而非破图 / 空态占位冒充——两处（IconSvg 回退目标 + ImageFallback 等硬编码兜底）须同时落 `empty`，方算 IconSvg 红线收口。⚠️ **中性占位边界细化（第十轮收官补强）**：仅当组件语义**明确**为「菜品」时（如 `DishCard` 的菜品图占位）才可用 `dish` 作图片占位；**食堂卡 / 档口关联 / 关于页 / 通用轮播等容器语义≠菜品的中性场景一律用 `empty`**（如 `home` 食堂卡、`find` 搜索建议 `suggestIcon`、`RelatedPickerSheet` 非菜品关联项、`settings` 关于页、`ImageSwiper` 通用轮播等），不得用 `dish` 冒充中性占位。图标语义契约（10 轮迭代已稳定）：`thumb`=有用/点赞、`heart`=喜欢、`star`=评分，三者互不混用。
  - **底部抽屉 / 弹窗规范**：`ReportModal` / `ContributeSheet` / `ApplySheet` / `FilterSheet` / share-sheet 等底部抽屉须含 `env(safe-area-inset-bottom)` 安全区避让，进出场缓动 `cubic-bezier(0.32,0.72,0,1) 0.3s`，并对 `prefers-reduced-motion: reduce` 交叉淡入降级（去弹性过冲）。
  - **`<swiper indicator-active-color>` / `<swiper indicator-color>` 裸 hex 为例外**：该原生属性（含激活态 `indicator-active-color` 与非激活态 `indicator-color`）不支持 `var()`，允许写裸 hex，但**须在 `client/src/uni.scss` 注释登记**（注明对应 token 名，便于全局改色时同步），不作为红线违规。
  - **图片添加统一用 `ImageUploader` / `IconSvg`**：新增图片入口一律走全局 `ImageUploader` 组件或 `IconSvg name="plus"` 触发，**禁止**在页面内联复制「+ 添加图片」逻辑 / 裸加号文本。以下两类为**已登记合法例外**（非违规，不强制替换）：① **单图头像上传**（`pages/profile/index.vue` 头像；`pages/pages-detail/review.vue` 评价单图）——单图场景；② **受 `canSubmit` 门控的「延迟上传」流程**（先存临时路径、提交时才逐个上传）——内联 `uni.chooseImage` 可避免破坏提交校验时序。完整多图流页面（`publish-dish` / `submit-stall`）仍须强制走 `ImageUploader`。
  - **分区标题复用 `SectionTitle`**：所有分区 / 区块标题一律渲染 `<SectionTitle title="…" />`；`CardSection` 内部**不另起**一套标题语言（不得手写 `.section-head`+`.section-title` 竖条 / 纯文字标题模拟 accent 条），表单内字段级 label 属字段语义允许纯 text。
  - **颜色全走语义 token（禁裸 hex）**：所有颜色（含限时 / 促销 / 热门等标签底色、`IconSvg` 的 `color` 属性、文字色、边框色、背景色）必须引用语义 token（如 `var(--color-hot)` / `var(--color-promo)` / `var(--color-primary)` 等），**禁止**在模板 / 组件样式中写裸 hex（如 `#FF6B6B` / `#FFB400`）。原生 API 不接受 `var()` 的颜色例外（如 `<swiper indicator-active-color>`、`uni.showModal` 的 `confirmColor` 等）**必须集中在 `client/src/uni.scss` 注释登记**（注明对应 token 名与用途，便于全局改色时同步）；且该常量须路由经过注册常量（如 swiper 指示点色统一经 `SWIPER_INDICATOR_ACTIVE_COLOR` 引用），**禁止在页面内联写裸 hex**——即裸 hex 只能出现在 `uni.scss` 的登记处，业务代码一律引用注册常量，登记后方不作为红线违规。
  - **底部 Sheet 统一下拉关闭手势 + reduced-motion 降级**：所有 bottom-sheet（`ApplySheet` / `ContributeSheet` / `NicknameSheet` / `FilterSheet` / `RelatedPickerSheet` 等）必须统一支持下拉关闭手势——仅向下拖拽、阈值约 `120px`、松手超过阈值 `emit('close')` 否则回弹；并须对 `prefers-reduced-motion: reduce` 做降级（去弹性过冲、交叉淡入）。**禁止**个别 sheet 仅支持 mask 点击关闭、缺失下拉手势或降级（与 §4.4 Sheet 弹簧 + 手势中断同源强化）。
  - **审计时先查本段已登记例外清单，确认未登记才计违规**：上述各红线中凡标注「已登记合法例外」「为例外」「登记后不作为红线违规」之处，须以本段（§4.9 UI 全量审计红线）逐条登记的例外为准；审计 / 复验时发现疑似违规，**先查本段已登记例外清单，确认确未登记才计为违规**。未在本段登记的裸 hex / 内联逻辑等一律按红线违规处理。
- **Web 管理端 UI 细则 / 页面模板（三栏布局、T1/T2/T3 模板、统一组件 `PageContainer/PageSection/...`、视觉刷新）见 `docs/web-ui.md`，由 web-dev 落地；本文件仅定基础 token 与红线。**

## 5. 开发约束
- **平台定位**：仅美食信息公示与点评，**不涉及下单 / 支付 / 外卖**；无售罄 / 库存概念；浏览对游客开放；列表默认按热度降序，可切评分 / 价格，长列表无限滚动，无结果显空状态。
- 命名：Java PascalCase、字段 / 方法 camelCase；DB snake_case（MP 自动驼峰）；前端 TS camelCase，Web 经 `api/adapter.ts` 转换，禁止 View 层直接处理字段名。
- 所有 API 响应含 `code/message/data`；前端 `http.ts` 判定 `code!==200` 抛异常，页面 try-catch，Store fetch 失败置空数组不向上抛。
- Controller 入参 DTO + `@Validated`；Service 写操作 `@Transactional`；评分 / 点赞计数走 Spring 事件异步维护，禁止主流程内联重算。
- 内容审核流：学生提交 `audit_status=pending` → 管理员 `approved/rejected`（退回必填 `reject_reason` 并回显）；小程序仅展示 `approved` 且上架 / 营业中；评价 `is_hidden` 控制可见性；Web「菜品审核」「评价审核」为独立模块。学生编辑重提**复用原记录**、`reject_reason` 清空。下架 / 变更申请落独立 `apply` 表（见 §0.3）。
- **认证**：微信打开静默登录（`wechat-login`）即游客态；社区写操作需 `verified=true`（邮箱验证码认证）；**废除账号密码 / 注册**（管理后台登录例外，见 §5.y.5）。评价一人一菜一条（`uk_review_user_dish`）、点赞一人一票（`uk_useful_user_review`），业务代码须与唯一键一致。
- 小程序请求超时 8s、管理端 5s；API 基地址集中 `api/config.ts` 的 `API_BASE_URL`，禁止硬编码 URL。
- **广播栏动态来源（2026-08-15 复核，与 §2.1.4 / `docs/pages/首页.md` §二 对齐）**：广播栏承载**动态信息流**——学生对菜品的最新评价 / 动态摘录（`community` 类，按时间倒序循环滚动）；每条展示「评价人：菜品名 ⭐评分 摘录」纯文本，不展示头像。整栏 `@tap` 按 `broadcastType` 路由：`community`→动态列表页、`dish`→菜品详情页、`url`→web-view；**仅承载动态类，非动态类（`NOTICE` / `ACTIVITY` / 食堂档口类）不进首页广播**。运营广播 `Broadcast` 实体（ADMIN 录入通知条）方案已废弃，与「评价 ticker 跳转 community」旧方案一并收回。
- **活动数据结构（2026-08-12 拍板）**：活动由后台运营录入，存储字段含「活动标题 / 活动描述 / 发布时间 / 公众号文章链接」；首页万能区域展示最近一条活动（标题 + 发布时间，标题截前 15 字），整列点击进入活动列表页；活动列表页按发布时间倒序排列全部活动，点击具体活动跳转对应公众号文章链接（微信 web-view）。活动为独立数据对象，后端有独立活动实体与 CRUD 接口（Web 录入、小程序列表页消费），不与菜品 / 档口 / 广播耦合。

### 5.y 认证与鉴权（微信登录体系，2026-08 拍板，强制）

> 全量拍板决策，替换旧「邮箱注册 + 密码登录」体系。管理后台登录例外见本 § 末尾。

#### 5.y.1 认证模型
- **废除账号密码登录**：小程序端无登录页 / 登录按钮 / 注册页 / 密码修改 / 密码重置；`/auth/login`、`/auth/register`、`/auth/password`、`/auth/password/reset` 及其 DTO（`LoginReq`/`RegisterReq`/`PasswordResetReq`/`PasswordUpdateReq`/`LoginResp` 密码相关）废弃移除。
- **微信自动静默登录**：微信打开小程序即调用 `POST /auth/wechat-login`（携带 `code`），后端 `code2Session` 换取 `openid`，按 `openid` 取号；不存在则自动建号。用户**默认即已登录的未认证账号**（`verified=false`，游客态），不做「未登录」概念（见 §5.y.3 游客态语义）。
- **认证解锁社区**：`@bjtu.edu.cn` 邮箱验证码认证通过 → `verified=true`，解锁社区写操作（发布 / 评论 / 点赞 / 动态 / 菜品 / 档口·食堂提交等）。**无收藏功能（全量移除确认）**。
- **绑定与替换**：同一邮箱认证后绑定当前微信；**不设解绑入口**；新微信用同一邮箱认证时**直接替换旧微信绑定**（旧邮箱绑定关系的账号历史数据归属跟到新微信账号）。邮箱是唯一迁移 / 绑定凭证。

#### 5.y.2 User 表结构变更
> `user` 表新增（`server/src/main/resources/db/schema.sql` 为准）：
- `openid` VARCHAR(64) **NULL DEFAULT NULL**，**唯一索引 `uk_user_openid`**（微信静默登录取号依据）。**须可空**：微信游客/已认证建号必写 openid，而历史学号账号 openid=NULL；InnoDB 唯一键允许多个 NULL，故 seed/历史多账号不冲突（若沿用 `NOT NULL DEFAULT ''` 会与唯一键冲突，阻断建库）。
- `unionid` VARCHAR(64) NULL（同主体多应用时用；未提供可空）。
- `verified` TINYINT NOT NULL DEFAULT 0（0=游客未认证 / 1=已邮箱认证，`verified` 不进 JWT，后端实时判定）。
- `bind_email` VARCHAR(128) NULL（**仅存认证关系，不公开**；与 `email` 的关系见数据迁移规则）。
- `verified_at` DATETIME NULL（认证时间）。
- `username` 语义调整：游客建号 `username='wx_'+openid 尾 16 位`；昵称默认「食客+ID 尾 4 位」（`getGuestShortId` 语义，见 §5.y.4）。
- 旧 `email` 列保留作为历史迁移凭证（见 5.y.3 数据迁移）；旧 `password` 列可保留兼容历史（管理端 admin 仍用密码）但学生侧不再使用。

#### 5.y.3 数据迁移合并规则（历史邮箱账号）
- **迁移凭证唯一 = 校园邮箱**：旧邮箱注册账号的用户，用新微信进入后，通过「学号邮箱 + 验证码」认证（`verify-email`）触发自动合并。
- **合并动作**：认证时若该邮箱已存在历史 `user` 记录（旧账号），将旧账号的业务数据（菜品 / 评价 / 动态 / 反馈等 `created_by`）**归属转移到当前微信账号**，并清理旧微信占位/旧记录；若邮箱无历史记录，则仅绑定 + 置 `verified=1`，无需迁移。
- **未邮箱注册过的用户无历史数据，无需迁移**。
- **新微信替换**：同一邮箱已被另一微信认证过 → 新微信认证时替换该邮箱绑定，旧微信账号下该邮箱的历史数据归属跟到新微信（旧微信变回游客态，其 `bind_email` 清空 / `verified` 置 0）。

#### 5.y.4 游客态语义与权限矩阵
- **游客态** = 已登录的未认证账号（`verified=false`）。前端「游客」标识用 `getGuestShortId()`（=「食客+ID 尾 4 位」）。
- **权限矩阵**：
  - 浏览全部公开数据（菜品 / 评价 / 动态 / 食堂 / 档口 / 活动）→ 游客可。
  - `POST /feedback`（基础反馈提交）→ **公开，无需认证**。
  - 社区写操作（发布菜品 / 写评价 / 发动态 / 评论 / 点赞 / 更新本人记录）→ 需 `verified=true`。（学生提交档口/食堂 `/my/stalls` 与美食清单模块已于 2026-08-18 随代码清理移除）
  - **系统通知（`/my/notifications/*`）→ 认证专属（`verified=true`）**：通知是按 `userId` 归属的账号私有数据（`/my/` 前缀），内容为内容贡献者的行为反馈（审核结果 / 收到评论 / 点赞）。游客（`verified=false`）无任何可产生通知的社区写操作来源，通知列表恒空、未读恒为 0，故**不属公开数据、不对游客开放**。「我的」页「系统通知」入口 `authLocked=true`，游客点击弹认证引导；游客态**不拉取未读数**（红点仅在 `verified=true` 时刷新）。
  - **入口不置灰**：需认证功能入口对游客可见且可点；点击时弹**认证引导**（`AuthSheet`，触发「学号邮箱 + 验证码」认证），认证成功后自动继续原动作。
- 昵称保持「食客+ID 尾号」；`bind_email`（学号邮箱）**仅存认证关系、不公开**，可在「我的」页展示绑定邮箱。

#### 5.y.5 接口契约
- `POST /auth/wechat-login`（公开）— 入参 `{ code }`（微信 `wx.login` 临时凭证）；后端 `code2Session` → 按 `openid` 取号 / 自动建号 → 返回 `LoginResp{ token, userInfo(含 verified/绑定的 bind_email/昵称) }`。JWT 7 天。
- `POST /auth/email-code`（公开，改造）— 入参 `{ username(学号), email(可空，自动推导 {学号}@bjtu.edu.cn), purpose }`；`purpose` 改为 `verify`（认证用途，替代旧 `login`/`register`/`reset`）；60s 限频、10min 有效。
- `POST /auth/verify-email`（公开，新增）— 入参 `{ code }` + 从当前微信账号上下文绑定：校验验证码 → 绑定邮箱 → 触发数据迁移合并（见 5.y.3）→ 置 `verified=1`、写 `bind_email`/`verified_at` → 返回更新后 `LoginResp`。
- `GET /auth/profile`（登录即游客可读）— 返回当前账号信息含 `verified`、`bindEmail`（是否已认证 / 绑定邮箱）、昵称、头像、`guestShortId`。
- 鉴权：社区写操作改为**校验 `verified`**；`/admin/**` 仍仅 `ADMIN`；**系统通知 `/my/notifications/*` 属认证专属，服务端按 `verified=true` 校验（游客恒空、前端不拉取）**。
- **管理后台登录（方案 C）**：维持 `/auth/admin/login`（管理员账号密码 + BCrypt + JWT），与小程序微信登录体系解耦；`/admin/**` 校验 `ADMIN` / `SUPER_ADMIN`。

### 5.z 已拍板架构决策（强制）
- **D-A** 通知异步写 `notification` 用 `@Async` + 有界线程池，不引 MQ。
- **D-B** `view_log` 加唯一键 `uk_view_user_target`，`record()` 改 upsert。
- **D-C** ~~报表导出返回 CSV 文件流，不引 Apache POI~~（报表导出功能已随 `ReportExportView` 清理移除，2026-08-18；此决策作废，留档备查）。
- **D-D** 推荐 / 热门 / 广场用 Caffeine 短 TTL 缓存(60s) + 写失效；`recommendDishes()` 改 SQL 分页。
- **D-E** schema 漂移治理：启动时 fail-fast 校验或 CI 步骤。
- **Q1** 不建成就 / 等级 / 成长体系（无 `achievement`/`user_achievement`）。
- **Q2** 不置顶 / 话题 / 精选运营干预，社区排序不干预。
- **Q4** 必须交付：②社区举报复用 `user_feedback`(`related_type`/`related_id`)，不新建举报表 ③删除本人记录（动态 / 菜品 / 评价）④关联动态双向跳转。（①`DELETE /my/account` 账号注销接口因无前端入口已随清理移除，2026-08-18）
- **Q5** 不碰关注 / 粉丝流，不建用户关系表。
- **D-工作台（2026-08-18 对账拍板）**：Web 管理后台登录默认落地页为 `/dashboard`（`DashboardView`，工作台 = 待办 + 数据总览），契约见 §0.4.1。**工作台不含 ECharts 图表看板**；`/admin/dashboard` 返回的 `DashboardVO` 虽含趋势/排行/上新等图表字段（供后续图表看板复用），当前 DashboardView 不消费，图表看板非本期交付。前后端契约已对齐、登录首屏已落地，**无开发缺口，不需要为此新建开发 task**（本决策记录即对账结论，勿为已存在代码再拆 task）。

### 5.x 三端一致性红线（强制，违反即阻断级缺陷）
- **字段命名**：对外 JSON 一律 camelCase；跳转目标类字段统一 `targetType`/`targetId`/`targetUrl`（原 Banner 契约，Banner 已移除后适用于广播/动态等）；评价状态 `isHidden`(0/1) 非 `isDeleted`；Web `snake_case` 仅允许 `api/adapter.ts` 内部，禁止进入 `types/` 或视图层。**（`favoriteCount`/`isFavorited` 已随收藏模块移除而废弃，不再作为字段命名约束）**。
- **错误码统一**：成功 200 / 参数 400 / 未登录 401 / 无权限 403 / 服务器 500；**401 统一处理**：小程序 `uni.$emit('auth:unauthorized')` + 清 token + Toast + **重新触发微信静默登录（`wechat-login`）**；**web `http.ts`（管理后台）** 补齐 401 拦截（清 `localStorage.token` + 跳转管理后台登录页 `/login`，方案 C 仍用账号密码）。
- **喜欢 / 收藏单一概念（收藏全量移除，2026-08-12 复核）**：原 `favorite`/`/favorites` 端点、表、字段（`favoriteCount`、`isFavorited`）已彻底删除；**前端不得保留任何「收藏」入口或按钮**（含 `pages/profile/index.vue` 的「我的收藏」、`pages-detail/dish.vue` 底部收藏按钮、`my-favorites` 页），统一移除。语义仅保留 `ic-heart=喜欢`（点赞/喜欢，非收藏）；禁止 `like`/`favorite` 双体系、禁止 `like_count`。`DishVO` 不再含 `favoriteCount`/`isFavorited`（历史口径混淆已废）。
- **状态枚举**：Dish `status` on/off；Canteen/Stall `status` open/closed；Broadcast/Activity `status` enabled/disabled；Web 内部 `active/inactive` 须经 adapter 映射回后端枚举。（Banner 已移除）
- **User 无 stall**：`UserVO` 不含 `stallId`；web `userToLegacy` 的 `stall_id` 映射须删除。
- **学生 UGC 路径**：发布菜品仅 `POST /dishes` 系列，写评价 / 评论 / 点赞 / 动态等社区写操作——均需 `verified=true`（见 §5.y 权限矩阵）；严禁 `/stall-owner/**`。（学生提交档口/食堂 `POST /my/stalls` 已随功能移除，2026-08-18）
- **分页结构**：列表接口统一 `PageResult<T>{ records, total, page, pageSize }`；单页非分页返回 `List<T>`。
- **整改影响面清单（谁改什么）以本文件各红线条款为准，不再另立文档。**

## 6. 协作纪律
- 本文件为**唯一权威基础规范**；多 agent 协作流程与交接物见 `docs/WORKFLOW.md`。
- **仅技术负责人可修改本文件**；其余角色（后端 / 小程序 / Web / 质量把控工程师）发现与本文件或代码冲突时，须提技术负责人裁定，不得自行绕过或改本文件。
- 踩坑经验回流：实测证伪的方案（如 §4.9 组件渲染红线）由技术负责人提炼进本文件红线。
