# 知行食记 · 项目规格说明（project_spec.md）

> **基础规范基线（最高权威）**。所有 agent 与协作者必须服从本文件；冲突时以本文件为准。
> **文档为唯一权威，代码只在 UI 实现层提供指导**：规范/契约冲突一律以本文件裁定，**不得据代码现状反向推翻文档**（代码未实现的部分按本文件补齐，而非删改文档）；开发实现只需改代码消除静态错误，**编译与运行由用户执行**，不由 agent 代跑。
> 本文件只规定「不会轻易变、且所有端必须遵守」的基础规范。**多 agent 协作流程与角色权限见 `CODEBUDDY.md` 与 `.codebuddy/agents/`（原 `docs/WORKFLOW.md` 已删除，勿再引用）**。
> **唯一可修改者：技术负责人**（需求梳理师 + 架构师合并角色）。其余角色不得改动本文件，发现冲突须提技术负责人，不得自行绕过。
> **产品蓝图定版决议见 §7（2026-09-14 用户拍板）**：与前文冲突时以 §7 为准。

---

## 0. 系统总览

### 0.0 产品定型一页纸（防跑偏宪法 · 2026-09-13，最高优先级）

> 决策来源：2026-09-13 三轮 PM 问答拍板，逐字采纳。本页是产品形态的**终局定界**，效力高于本文件其他小节中的历史表述；本页未否决的内容仍按各小节执行。

**一句话**：交大人的「吃什么不踩雷」——校园菜品信息展示与检索平台：把食堂菜品结构化、可搜索、可信评价，用户反馈经安检与审核回流为高质量信息。

**五个定型支柱**

1. **信息展示优先**：把静态信息做扎实（名称 / 价格 / 档口 / 楼层 / 营业时间 / 口味标签 / 图片）；「售罄 / 今日供应」即时状态**一期不做**，二期评估。
2. **轻社区边界**：UGC 仅「评价」一种形态（1-5 星 + 文字 + 配图 ≤3 张），**终局形态已定，不再大改**；不做动态 / 关注 / 私聊 / 收藏。
3. **UGC 通道唯一**：菜品信息共建走「反馈表单（可配图）→ 管理员审阅录入 / 修改 / 上下架」；**不恢复学生直建菜卡接口**；信息发布源唯一 = 管理员。
4. **合规底线**：全部 UGC 过微信内容安检（msgSecCheck / imgSecCheck 三态：risky 拦截、review 人工复核、pass 放行）；评价对他端可见需 pass。
5. **轻运营**：无运营位（活动 / 公告 / 首页置顶均下线或定型不做）；菜品数据管理员手动维护；**Excel 批量导入推二期**（预留导入通道抽象，未来 OCR 菜单识别走同一通道）。

**平台边界**

- 用户：游客可浏览 / 搜索一切；评价 / 反馈须学号邮箱认证（防刷不防看）；认证口径不变（`@bjtu.edu.cn`）；不做多校区隔离（region 字段已预留）。
- 通知：**仅站内通知中心**，不做任何推送（微信订阅消息二期评估，需用户订阅）。
- 推荐心智：保持现热度算法（浏览 + 评分聚合）。**勘误：算法不含「收藏」维度——产品无收藏功能，历史文档 / 代码注释中的「收藏」为措辞残留（收藏功能不存在，措辞残留勘误，2026-09-13）**；算法未来演进（数据量增大后）须重新拍板，不得静默修改。
- 北极星指标：**周活 / 留存**（用户侧）；辅助观察：评价覆盖率、反馈处理时效（dashboard 一期不加新指标，跑一个月后评估）。

**演进预留（本期不做、已预留，做之前须重新拍板）**

- Excel 批量导入（二期）→ OCR 菜单识别（远期，同一导入通道）
- 微信订阅消息推送（二期）
- 菜品 / 档口「售罄 / 今日供应」即时状态（二期）
- 推荐算法演进（重新拍板）

**定型增补（2026-09-13 第二轮 PM 问答）**

- 「我的反馈列表」**定型不恢复**：反馈处理结果经站内通知回执闭环，历史不可查（保持轻量）。
- **账号注销本期落地**（合规硬需求，微信平台账号删除要求）：「我的」页底部注销入口 → 二次确认 → 匿名化（nickname→'已注销用户'、openid/unionid 解绑、status=deleted），评价/反馈保留但去身份化；token 立即失效（详见 §5.y）。
- 菜品下架（status=off）= **客户端完全不可见、评价保留**（现状登记为定型口径；恢复上架时重现）。
- dish.alias **别名搜索本期落地**（管理员配置，搜索命中 name 或 alias）。

**详细设计基线**：`docs/product-blueprint.md`（《产品定型总纲 v1.0》）——页面 / 流程 / 数据模型 / 接口 / 内容安全 / 算法 / 版本边界的全量固化版。**原则以本页为准，细节以总纲为准**，两者共同构成产品宪法。

> **裁决规则（后续开发必遵）**：任何新功能 / 改动若与本页冲突，**必须先修订本页（重新拍板）再动代码**。

### 0.1 角色模型（仅两种）
- `STUDENT`（**微信自动登录 + 校园邮箱认证**，兼"平鉴官"）：微信打开小程序即自动静默登录为**未认证账号（游客态，`verified=false`）**；通过 `@bjtu.edu.cn` 邮箱验证码认证后 `verified=true`，解锁写评价 / 评价点赞等 **UGC 写操作**（评价**支持配图 ≤3 张**，全部 UGC 过微信内容安检，见 §5.a）。**学生端无菜品写接口**：`POST`/`PUT`/`DELETE /dishes` 均已于 2026-09-13 全部下线（客户端零消费），菜品由管理员录入，学生提交菜品需求走意见反馈 `add` 类型由后台处理（学生提交档口 / 食堂 `/my/stalls` 已于 2026-08-18 随代码清理移除；社区/动态板块已于 2026-09-12 下线，见 §0.5）。游客（`verified=false`）仅可浏览公开数据 + 提交基础反馈（`POST /feedback` 公开，**可配图 ≤3 张**，同样过内容安检）。**无账号密码登录、无登录页、无登录按钮**（见 §5 认证红线）。
- `ADMIN`（系统管理员 / 食堂后勤）：审核 UGC、看板、食堂 / 档口 / 菜品 CRUD + 上架下架、用户 / 管理员管理。**管理后台登录沿用方案 C：管理员账号密码 + BCrypt + JWT（放弃微信开放平台扫码 / 复用小程序码）**，见 §1 认证与 §5 认证红线。
- **无独立 `STALL_OWNER` 角色，亦无 `/stall-owner/**` 路由。**
- **活动与公告（broadcast）已全链路下线（2026-09-13 拍板）**：小程序端零消费，`activity` 与 `broadcast` 的后端接口 / 实体、管理后台页面、库表全部删除，ADMIN 不再承担活动录入职责（详见 §0.5「已下线」）。

### 0.2 数据流闭环
1. **浏览**：首页（搜索框 → 筛选 → 瀑布流）/ 搜索 → 菜品详情 → 评价 / 分享（**无广播栏**，见 §2.1.4）。
2. **贡献（平鉴官）**：需 `verified=true`（游客未认证不可贡献）→ 提交 → `audit_status=pending` → 后台审核 → `approved` / `rejected`（回写 `reject_reason`）。**学生端无菜品写接口**（`POST`/`PUT`/`DELETE /dishes` 均已于 2026-09-13 全部下线），菜品由管理员录入后即 `approved`；学生菜品需求走反馈 `add` 类型，由管理员在后台据反馈手工录入，闭环无学生侧重提。
3. **运营（后勤）**：后台审 UGC / CRUD → 小程序即时体现。UGC 文本与配图先过微信内容安检（`sec_state`，见 §5.a），`review` 态进管理后台复核队列（放行 / 驳回）。

### 0.4 三端定位与数据链路（2026-08-05 拍板，强制）
- **小程序（`client/`）= 服务端 / 用户端**：学生使用，是**业务数据的唯一产生源头**（浏览、菜品评价、产品反馈——含新增菜品 / 纠错 / 推荐等反馈诉求；**学生端无菜品写接口**，菜品由管理员录入，见 §5.x）。
- **后端（`server/`）= 数据服务**：唯一数据存储与业务规则所在；小程序与 Web **共用同一套 API 契约**（`/admin/**` 供 Web，`/` 用户接口供小程序）。
- **Web 管理端（`web/`）= 辅助后端管理数据的 UI 工具（非用户端）**：职责 = 对小程序产生的数据做**管理（CRUD / 上下架 / 排序 / 配置）与审阅（UGC 审核 / 内容治理 / 操作日志 / 数据总览）**；Web 不产生业务数据，只消费与管理后端数据。
- **数据链路**：小程序产生数据 → 后端落库（MySQL）→ Web 经 `/admin/**` 读取与管理 → 小程序即时反映。
- **Web 端管理能力全景**：
  - 信息管理：食堂 / 档口 / 菜品（业务信息）
  - 内容审核：菜品评价 / 反馈
  - 用户与权限：学生账号 / 管理员账号（超管分层）。**Web 端权限从简（2026-09 拍板）**：普通 `admin` 可用全部管理功能（菜品/食堂/档口 CRUD、UGC 审核、反馈处理、看板、日志等不做角色区分）；唯一例外「管理员账号管理」仅 `SUPER_ADMIN`——web 路由守卫（`meta.role`）与后端 `@PreAuthorize` 双闸门口径一致；后端 `/admin/**` 仍保留 `ADMIN`/`SUPER_ADMIN` 硬闸门。
  - 系统：操作日志 / 工作台（待办 + 数据总览）
- 约束：**Web 端任何新增管理能力，必须以小程序已存在的数据对象为前提**；不得在 Web 端引入小程序不存在的数据模型或业务（原「活动模块」例外已随 2026-09-13 活动全链路下线作废）。

#### 0.4.1 工作台（DashboardView）契约（2026-08-18 拍板）
> **首屏定位**：Web 管理后台登录默认落地页为 `/dashboard`（`DashboardView.vue`，面包屑「工作台」）；登录成功或访问登录页时已登录均重定向 `/dashboard`。**工作台 = 待办 + 数据总览，非图表看板**（无 ECharts 图表，与「数据看板 / 统计报表」边界见下）。

- **一次请求**：工作台页面 onMounted 发起**单次** `GET /admin/dashboard?range=week`，一次返回全部待办数 / 待办明细 / 规模指标 / 近期操作；页面不逐项发多个接口。失败显示「工作台加载失败 + 重试」，成功渲染三块。
- **待办卡（1 项，显示 count，点击直达对应管理 tab；2026-09-12 随动态下线移除「待审核动态」、2026-09-12 随 apply 全链路下线移除「待审核申请」，见 change prelaunch-loop-closure）**：
  - 待处理反馈：`pendingFeedbackCount` → `/dashboard/audit?tab=feedback&section=feedback`（反馈处理）
- **5 项规模指标（指标卡，点击直达对应管理 tab；2026-09-12 随动态下线移除「动态」、2026-09-12 随 apply 全链路下线移除「申请」与「反馈」指标项，见 change prelaunch-loop-closure §0.4.1 收敛）**：
  | 指标 | 字段 | 跳转 |
  |---|---|---|
  | 食堂 | `totalCanteenCount` | `/dashboard/content?tab=canteen` |
  | 档口 | `totalStallCount` | `/dashboard/content?tab=canteen` |
  | 菜品 | `totalDishCount` | `/dashboard/content?tab=dish` |
  | 学生 | `totalUserCount` | `/dashboard/system?tab=account` |
  | 评价 | `totalReviewCount` | `/dashboard/audit?tab=review` |
- **统计口径（后端 `DashboardVO` 为准，经 `StatsController.overview`）**：
  - 规模指标：全量计数。学生数 `user.role='student'`；菜品数按 `audit_status='approved'`；其余（食堂/档口/反馈/评价）全表计数（动态指标已于 2026-09-12 移除；申请指标随 apply 全链路下线移除，见 change prelaunch-loop-closure）。
  - 待办 count：`feedback.status='pending'`（申请待办随 apply 全链路下线移除）。
  - 待办明细（5 条，按时间倒序）：`pendingFeedbacks`（`DashboardVO.TodoItem{ id,title,type,time }`）。
  - 近期操作：`recentLogs`（操作日志最近 10 条，`DashboardVO.RecentLogItem{ id,operator,action,target,time }`）。
  - **容错**：各统计项独立 try-catch，任一失败给默认值（0 / 空列表），保证工作台必能加载。
- **与「数据看板 / 统计报表」边界**：工作台（DashboardView）**不含 ECharts 图表、不含趋势/排行/上新指标**；`GET /admin/dashboard` 返回的 `DashboardVO` 虽含 `newDishCount/newReviewCount/hotDishes/hotCanteens/viewTrend/reviewTrend`（供历史/未来的图表看板复用），但当前 DashboardView **不消费这些图表字段**，仅渲染待办 + 规模指标 + 近期操作。图表看板非本期交付（无 ECharts 看板页面）。
- **契约实现状态**：前后端 `/admin/dashboard` 契约与字段命名已对齐（前端 `web/src/api/dashboard.ts` 的 `DashboardData` 声明与后端 `DashboardVO` 一一对应），登录首屏已落地。**2026-08-18 对账结论：spec 与代码一致，无待办缺口，无需新增开发 task**。

### 0.5 产品聚焦（2026-09-12 拍板，强制）

> 决策来源：用户产品决策——**聚焦快速上线、尽快回收真实反馈**。非下列四条主线的一律不投入。
> **产品定位（2026-09-13 定稿）**：**校园菜品信息展示与检索平台**——信息展示 + 搜索 + 认证评价 + 反馈驱动的**轻 UGC 信息共建**；不做重社区。菜品 UGC 通道 = **反馈表单（可配图）→ 管理员审阅录入 / 修改 / 上下架**；**不恢复学生直建菜卡接口**（`POST /dishes` 维持已下线状态，见 §0.1 / §3）。

**四大核心板块（唯一投入方向）：**

1. **菜品信息展示**：浏览 / 筛选 / 排序 / 菜品详情（食堂与档口降级为菜品属性，无独立路由）。首页 = 搜索框头部 + 筛选行 + 瀑布流（**无广播栏、无万能区域**）。
2. **搜索与查找**：二级搜索页 `find`（首页顶部搜索框进入）+ 结果列表，作为菜品发现主线保留并强化。
3. **用户 UGC —— 评价类**：**菜品评价是唯一的「评价」形态**（社区/动态下线后不再有第二种评价语义载体）。菜品详情页评价区（展示 + 内联加载）+ 底栏「写评价」提交；一人一菜一评（`uk_review_user_dish`），评价侧「有用」点赞一人一票（`uk_useful_user_review`）。**评价支持配图（≤3 张）**，文本与图片均须过微信内容安检（`sec_state`，`review` 态对非作者不可见，见 §5.a）。
4. **用户 UGC —— 反馈 / 贡献类**：**反馈本身就是 UGC，与评价同等重要，不得弱化**。含 ① 意见反馈页 `pages/me/feedback/index`（公开提交、不收集联系方式、匿名心智、类型化结构化字段，**支持配图 ≤3 张**；`relatedType`：`dish` = 信息纠错关联菜品）；② 内容页主动入口——**举报（`type=report`，关联类型 `relatedType=review`，即菜品详情评价卡三点菜单的「举报评价」；游客免认证，见 `client-auth-boundary`）**、纠错 / 信息不对 / 申请下架 / 推荐菜品 / 新增菜品；③ 档口 / 食堂的贡献提交（**菜品无独立贡献提交入口**：`POST`/`PUT`/`DELETE /dishes` 已于 2026-09-13 全部下线，学生新增菜品经反馈 `add` 类型、由管理员在后台录入）。**价值锚点：反馈类 UGC 是实时发现菜品信息错误与程序问题、驱动「信息更新优化 + 程序优化」的主通道**，是本项目快速上线收集反馈的核心机制。反馈文本与配图均过微信内容安检（`sec_state`，见 §5.a）。

> **③ 与 ④ 合称 UGC 全谱系**（评价 + 反馈 / 贡献），共同构成菜品信息迭代的输入源。本次下线的是「社区 / 动态」这种**社交广场形态**，**不是**下线 UGC 本身——UGC 全谱系保留并强化。

**已下线（不再投入，恢复须重新拍板）：**

- **社区板块（原动态信息流）整体下线（2026-09-12）**：社区广场流、社区详情、评论楼、社区发布页、「我发布的」页，以及其后端模块、用户端与后台接口、相关库表**全部删除干净**（页面 / 目录 / 路由 / 入口 / 接口 / 实体 / 建表脚本 / 种子数据 / 文档，一律不留残留与废弃标注）。
- **活动 + 公告（broadcast）全链路下线（2026-09-13）**：小程序端零消费，`activity` 与 `broadcast` 的后端接口 / 实体、管理后台页面、库表**全部删除干净**（页面 / 目录 / 路由 / 宫格入口 / 接口 / 实体 / 建表脚本 / 种子数据 / 文档，一律不留残留与废弃标注）；「我的」页宫格「最新活动」格随之移除；`web-view` 随活动下线退出小程序（全项目无 web-view 场景）；数据库基线 14 → 12 张表。
- 下线后：TabBar 收敛为 **home / mine 两页**；「我发布的」「最新活动」宫格移除，我的页宫格收敛为**一行 3 列**（意见反馈 / 系统通知 / 我的评价）；评价不再「同步生成社区内容」；通知类型保留 `dish_audit` 与 `feedback_handle`（反馈处理回执）。
- 系统通知（反馈 / 审核结果回执）为核心板块的支撑能力，保留。
- **执行口径**：本文件一经同步即为唯一权威；后续实现若与本文档冲突，**改代码、不改文档**（代码只在 UI 实现层提供指导）。开发交付以「静态错误清零」为准，编译 / 构建 / 真机运行由用户执行。

### 0.3 一致性红线（全局，强制）
- 角色仅 `STUDENT` / `ADMIN`；**禁止** `STALL_OWNER` 或 `/stall-owner/**` 路由；`/admin/**` 仅 `ADMIN`（含 `SUPER_ADMIN` 分层，见 §5.x）。
- 菜品 / 档口 / 食堂均含独立 `audit_status`(pending/approved/rejected) + `reject_reason`（与上下架 `status` 解耦）。
- 实体贡献「下架 / 变更」类诉求走**反馈类型承载**（`error` 关联菜品纠错/下架、「新增菜品」走 `add`），**无独立申请表**（`apply_action` 表已于 2026-09-12 随贡献链路下线全量删除，管理员经 Web 后台据反馈手工闭环）。
- 前端 UI 遵循 §4（动效从简、即时反馈、半透材质、reduced-motion 降级；MVP 动效边界以 `openspec/specs/client-ui-motion` 拍板结论为权威，见 §4.3）。
- **认证与鉴权（2026-08 拍板，微信登录体系）**：
  - **无账号密码登录**：小程序端**无密码、无登录页、无登录按钮、无注册页**；微信打开即静默登录（`POST /auth/wechat-login`），默认得到 `verified=false` 的游客态账号。
  - **`verified` 门槛**：UGC 写操作（写评价 / 评价点赞等）鉴权从「需登录」改为「需 `verified=true`」；`verified` **不进 JWT**（JWT claims 实况含 `userId` / `role` / `username` 三项稳定字段，不含 `verified`——实现决策：userId 供业务鉴权实时查 `user.verified`，role 供网关与方法级权限校验），后端按 `user.verified` 实时判定。**学生端无菜品写接口**（`POST`/`PUT`/`DELETE /dishes` 均已于 2026-09-13 全部下线），菜品由管理员录入。
  - **游客权限矩阵**：游客可浏览全部公开数据 + `POST /feedback`（公开无需认证）；需认证功能**入口不置灰**，点击时弹认证引导。
  - **邮箱是唯一迁移 / 绑定凭证**：`@bjtu.edu.cn` 邮箱验证码认证即绑定当前微信；同一邮箱被新微信认证时**直接替换旧微信绑定**（旧数据归属跟到新绑定微信）；**不设解绑入口**。
  - **管理后台登录例外（方案 C）**：管理后台维持「管理员账号密码 + BCrypt + JWT」，与小程序微信登录体系解耦；`/admin/**` 仍仅 `ADMIN`（含 `SUPER_ADMIN`）。

---

## 1. 技术栈
- 后端：Spring Boot 3.2 + Java 21，ORM MyBatis-Plus 3.5.5（BaseMapper + XML，`resources/mapper/*.xml`）；API 文档 **SpringDoc OpenAPI（`/swagger-ui.html` + `/v3/api-docs`），不使用 Knife4j**。
- 小程序端：uni-app + Vue 3 (`<script setup>`) + TypeScript + Pinia，目录 `client/`。
- Web 管理端：Vue 3 + Vite + TypeScript + Element Plus，目录 `web/`，无 Pinia。**定位：辅助后端管理数据的 UI 工具（非用户端）**——只经 `/admin/**` 接口消费与管理小程序产生的数据，见 §0.4。
- 数据库：MySQL 8.0，库 `bjtu_food`，utf8mb4；**建表脚本唯一权威：`server/src/main/resources/db/schema.sql`**（`user.role` 默认 `'student'`）。
- 认证（微信登录体系，2026-08 拍板，详见 §5「认证与鉴权」）：JWT（7 天），`Authorization: Bearer {token}`。小程序端无账号密码，经 `POST /auth/wechat-login`（`code2Session` 静默建号/取号）获取 JWT；UGC 写操作需 `verified=true`。`verified` 不进 JWT，后端按 `user.verified` 实时判定。
- 管理后台登录（方案 C）：仍用「管理员账号密码 + BCrypt + JWT」；不引微信开放平台扫码，不复用小程序码。与小程序微信登录解耦。

## 2. 目录结构
- 后端按业务分包：`com.bjtufood.{auth|canteen|dish|review|content|upload|common}`，每模块 `controller/service(+impl)/mapper/entity/dto/` 四层，**禁止跨层调用**（Controller 不得直接调 Mapper）。
- 小程序 `client/src/`：`api/`、`types/`、`stores/`、`pages/`（**TabBar 固定 2 页：home / mine（tab key 语义仍 `profile`；2026-09-06 目录收敛；2026-09-12 移除社区页）**；2026-08-03 移除 find——搜索改为首页顶部搜索框入口，跳转二级搜索页 `/pages/find/index`，非 tab 页；「我的」页功能入口收敛为**一行 3 列宫格**（见 §2.1.4），意见反馈 / 系统通知 / 我的评价入口进 mine 宫格，不占 TabBar；**收藏功能已全量移除（2026-08-12 复核），无收藏入口**）、`components/`。
- **前端组件组织原则（2026-09-06 立规；细则见 `.codebuddy/rules/client-components-org.md`，含于 QA 门禁与开发 agent）**：`components/` 仅容被 `pages` ≥2 个页面包直接使用（或经多页公共壳间接使用）的公用组件；`pages/<包>/index.vue` 为页面渲染主文件，页面内多次复用卡片/复杂模块应抽为**包内私有组件**，且**只做一级拆分**（禁二级细分/碎组件）；components 与 pages **双向定期治理**——components 中低复用或页间差异大者下沉至唯一使用包，pages 中多包高频复用者上提至 components；迁移不改变行为并须同步引用，type-check + `mp-weixin` 构建全绿。
- **小程序 `client/src/` 分层职责（2026-09-06 成文）**：
  - `api/`：按域一文件的 HTTP 契约薄层，只调 `utils` 层 http helper，不夹页面逻辑；
  - `types/`：跨层共享 DTO/类型；
  - `stores/`：全局状态（Pinia）与跨页共享编排；
  - `composables/`：跨页面/包复用的**逻辑编排**（`useXxx`，不持模板）；页面一次性编排留页面 script 或**就近放页面包私有**（如 `pages/me/feedback/useFeedback.ts`、`pages/detail/dish/useDishPage.ts`，不滞留 composables/）；
  - `utils/`：纯函数工具（时间/格式化/nav 等，不 import 页面）；
  - `theme/`：设计令牌（供 App.vue `page{}` 消费的键 + 真值登记）；
  - `components/`：仅 ≥2 页直接（或经公共壳间接）使用的公用组件；
  - `pages/`：**分包根按功能域组织**——`detail/`=内容阅读域（仅菜品详情）、`me/`=个人中心域；主包仅 home/find/mine（原社区板块页面与发布流程分包已于 2026-09-12 删除，`pages/activity/` 活动分包已于 2026-09-13 随活动全链路下线删除，见 §0.5）。
  - **import 路径风格**：同目录/兄弟文件用 `./X`，页面与包内子件用 `./X`；跨层一律 `@/…`；**禁止 `../` 跨层相对逃逸**；不引入 barrel/`index.ts` 重导出。
  - **`find` 留主包为刻意决策（2026-09-06）**：`pages/find/index`（搜索结果页）是首页顶部搜索框的即时二级页，保留主包以**避免分包加载闪断**；不作为独立分包。

### 2.1 小程序页面架构（2026-09-06 复核，与 `client/src/pages.json` 严格一致）
> 与 `client/src/pages.json` 严格一致。当前共注册 **9 个页面**：主包 3 + 2 个分包（共 6 页）。**2026-09-06 收敛**：路径收敛 Tab「我的」根页 `pages/profile` → `pages/mine`（`/pages/mine/index`）、个人信息编辑页 `pages/profile-edit` → `pages/profile`；分包聚合：菜品详情并入内容阅读域 `pages/detail/`，`profile`/`notifications`/`feedback` 并入个人中心域 `pages/me/`。**2026-09-12 社区板块下线（见 §0.5）：删除其四个页面与发布流程分包**；同日 prelaunch-loop-closure 收敛后**新增 `pages/me/my-reviews/index`（我的评价）与 `pages/me/privacy/index`（隐私政策与用户协议）两页**。**2026-09-13 活动 + 公告全链路下线（见 §0.5）：删除 `pages/activity/` 分包两页（活动列表 + web-view），分包 root 3→2，页面实况 11→9**（以 pages.json 为准）。`home` 的 `preloadRule` 仍为单一预载 `pages/detail/`。**独立「关于我们」页 `pages/about` 已删除**（目录 / 路由 / 入口一并移除，见 §2.1.4）。**无孤儿路由**（原 `publish-dish` / `submit-stall` 等孤儿路由已随发布页合并清理）。**学号邮箱认证走 `AuthSheet` 弹层（无独立认证页）**；「系统通知」为 `pages/me/notifications/index`（mine 宫格进入）。已按 2026-08-19 决策**不建逐页设计文档**（视觉与交互规范统一以本文件 §4 为唯一权威）。

#### 2.1.1 主包（3；2026-09-12 移除社区广场页）
| 路由 | 标题 | 入口 |
|---|---|---|
| `pages/home/index` | 首页 | TabBar |
| `pages/mine/index` | 我的 | TabBar（目录 `pages/mine`，tab key 语义仍 `profile`） |
| `pages/find/index` | 搜索 | 首页顶部搜索框入口，`navigateTo`（二级页，非 Tab） |

#### 2.1.2 分包（2 个 root / 共 6 页；2026-09-12 随社区板块下线删除 1 个 root，同批次 prelaunch-loop-closure 回加 my-reviews / privacy 两页；2026-09-13 随活动全链路下线删除 `pages/activity/` 分包）
| 分包 root | 页面 | 标题 | 入口 |
|---|---|---|---|
| `pages/detail/`（内容阅读域） | `pages/detail/dish/index` | 菜品详情 | 卡片点击 |
| `pages/me/`（个人中心域） | `pages/me/profile/index` | 个人信息 | mine 用户卡（认证态）→ 头像/昵称（目录由 `profile-edit` 收敛） |
| | `pages/me/notifications/index` | 系统通知 | mine 宫格 |
| | `pages/me/feedback/index` | 意见反馈 | mine 宫格 |
| | `pages/me/my-reviews/index` | 我的评价 | mine 宫格「我的评价」格（`requireAuth`，本人评价列表 + 删除，复用 `DELETE /reviews/{id}`） |
| | `pages/me/privacy/index` | 隐私政策与用户协议 | mine 底部静态信息区合规入口 |

> **预载**：`pages/home/index` 在 wifi 下预载 `pages/detail/`（该分包现仅承载菜品详情）。

> **注**：原 spec 的 `pages/pages-user/my-reviews`（旧路径）、`publish-dish`、`submit-stall` 及 `pages/profile/verify`（独立认证页）**均已不在 pages.json**（认证走 `AuthSheet` 弹层；「我的评价」已于 2026-09-12 以新路径 `pages/me/my-reviews/index` 回加为在册页面，见 §2.1.2 / §2.1.4）。**社区板块相关四页（社区广场 / 社区详情 / 「我发布的」/ 社区发布页）已于 2026-09-12 删除**，发布统一 = 评价提交（菜品详情底栏「写评价」），不再有独立发布流程页（见 §0.5）。

#### 2.1.4 关键设计决策与约束
- **TabBar 固定 2 页（2026-09-12 收敛）**：`home` / `mine`（目录名；tab key 语义 `profile`）；原「动态」Tab 随社区板块下线移除。搜索、意见反馈、消息中心等均为二级页（经 TabBar 页内入口进入）。「关于我们」独立页已删除（2026-09-06），团队/邮箱等文案不再展示；mine 底部静态信息区 = 版本/学校两行纯展示（aria-hidden）+ 一行可点合规入口「隐私政策 · 用户协议」（跳 `pages/me/privacy/index`，该区域唯一可点元素）。
- **首页结构（以代码为准，2026-09-12 校准）**：`pages/home/index` = ① **顶部搜索框**（`AppHeader` 的 `home` variant，暖砖红底，点击进入二级搜索页 `find`）/ ② **筛选行**（`FilterBar`：全部食堂 / 全部价格两枚胶囊 + 常驻筛选图标）/ ③ **瀑布流**（`HomeContent` → `WaterfallList` 双列 `DishCard`，综合热度排序，**距你距离由端上本地计算**——`stores/dish.ts` 的 `withLocalDistance` 以 Haversine 用本机定位坐标与菜品坐标在客户端写回 `distance` 并参与本地重排；口径拍板「本地计算」：坐标不出端、隐私更优、离线友好；前端无定位条 UI、无收藏）。首页不显示定位条、不弹坐标授权。
  - **spec 旧描述作废（代码从未落地，不得再据此开发）**：原「广播栏（动态信息流 ticker）」与「万能区域（水平一行网格，承载活动入口）」**均不存在于代码**——前者随动态板块下线作废，后者本未实现；活动入口不在首页（活动已于 2026-09-13 全链路下线，见 §0.5），`broadcast` 表亦已随公告下线删除。
  - **后端距离字段口径（2026-09 拍板登记）**：后端 `DishVO.distance` / `CanteenInfoVO.distance` **字段弃用保留**——请求携带坐标时后端仍可能返回该字段（历史兼容），但**以端上本地计算为准**，小程序不消费后端下发的 `distance`；后端不对「个人化距离服务」做契约承诺。
- **搜索（2026-08-03）**：二级搜索页 `find`，非 tab；**属核心板块「搜索与查找」，不得降级或移除**（见 §0.5）。
- **反馈合并（2026-08-15）**：原「反馈中心」(`messages-services`) 已并入 `feedback` 意见反馈页（提交表单 + 我的反馈记录同页）；早期「联系/contact」表单亦并入。无独立反馈中心/contact 路由。
- **反馈重设计（2026-08-17 拍板；2026-09 按 ARCH-009 校准）**：`feedback` 页定为**收集用户诉求**的轻量单视图动态表单——**克制温度引导**（仅一行短标题「想说点啥，直接说」，不做大段文案）+ 口语化类型 chip + 类型与字段合一为一张大卡；类型前置单选必选，**实况 3 类**（提个想法 `suggestion` / 推荐菜品 `add` / 信息不对 `error`；原第 4 类「App 有问题」已并入「提个想法」的问题域，不再单列），字段随类型动态切换且**收集管理员所需关键结构化字段**（每类型必填 1 个，辅助选填，无冗余提示文案）；**不设登录守卫，任何人可提交**（`POST /feedback` 维持公开 PUB）；「新增菜品」从纠错二级细分提升为一级类型（后端扩 `add` 枚举）；纠错点含「已下架」作证流程（不要求正文，**文本作证**为主 + **可选配图作证**——「可照片作证」曾随 2026-09-12 UGC 图片下线作废，**2026-09-13 随评价/反馈配图恢复以带安检的配图形式回归**，见 §5.a）；**不收集联系方式**（移除前端字段，后端 `contact` 列保留兼容历史）；**匿名心智（2026-09 随反馈回执能力演进校准，见 change `prelaunch-loop-closure` 的 `feedback-receipt` spec）**：原底部「匿名提交 · 不记账号」静态文案已移除，改为**提交成功 toast 按认证态二分告知**——游客（`verified=false`）明确提示「未记账号、结果无法单独通知你」（游客提交不保留归属、管理员处理后不投递回执通知），已认证提示「数个工作日内查看并处理」（回执经系统通知投递；实况见 `pages/me/feedback/useFeedback.ts` 提交成功分支）；移除「我的反馈」Tab（进度追踪后续另做；`GET /feedback/my` 已随反馈中心下线删除）；举报继续走内容页弹窗不进本页。
- **食堂与档口降级为菜品属性（2026-08-15）**：学生决策主体是菜品，食堂/档口为 `dish.canteen` / `dish.stall`，仅在菜品详情「来源信息区」展示；无 `canteen`/`stall` 独立路由。
- **收藏功能已全量移除（2026-08-12 复核）**：无收藏入口。
- **「我的」页 IA（2026-09-06 方案 B 重构；2026-09-12 随动态下线由 2×2 改 1×3，2026-09-12 随 prelaunch-loop-closure 改回 2×2，2026-09-13 随活动全链路下线收敛为一行 3 列）**：`pages/mine/index` 自上而下 = 用户卡 + **一行 3 列功能宫格**（「意见反馈｜系统通知｜我的评价（有未读显示红点，无未读/未登录不显示）」）+ **底部静态信息区（含隐私政策/用户协议合规入口）**（`知行食记 v{version}` · 学校，居中小号浅灰纯展示）。宫格每格整格热区：意见反馈→`/pages/me/feedback/index`、系统通知→`/pages/me/notifications/index` 正常跳转。**「我发布的」格已随社区板块下线移除、「最新活动」格已随活动全链路下线移除**（均含其在 `utils/feature-gates.ts` 的登记项；「最新活动」为最后一项在册登记，移除后 feature-gates 无在册条目）。原「一行两卡 + 三行浅色入口列表 + 中部单行版本号」整体删除。
- **微信登录体系（2026-08 拍板，详见 §5.y）**：小程序无登录页/登录按钮/注册/密码体系；微信打开即静默登录为游客态（`verified=false`）。「我的」页用户卡点击二分：游客整卡点击直接弹 `AuthSheet` 认证弹层（学号邮箱 + 验证码），认证态点击进个人信息编辑页 `/pages/me/profile/index`；需认证的写操作（写评价 / 点赞）沿用「不置灰、点击弹认证、认证后继续原动作」口径（原「我发布的」认证门已随动态下线移除）。「我的」页展示已绑定邮箱（`bind_email`）与认证状态。**认证走 `AuthSheet` 弹层，无独立认证页**（2026-08-19 复核：`pages/profile/verify/index` 已不在 pages.json）。
- **发布收敛为评价（2026-09-12，替代原「发布社区内容」相关拍板）**：**唯一 UGC 发布路径 = 菜品详情底栏「写评价」**（星级必选 + ≤500 字选填正文 + **可选配图 ≤3 张**（2026-09-13 恢复，见 §5.a），弹层提交 `POST /reviews`）；**不再有独立发布页、不再有「关联对象」表单、不再有「同步到社区」**。原社区发布流程页与「我发布的」页及其路由、宫格入口、feature-gates 登记项**均已删除**；`publish-dish`/`submit-stall` 孤儿路由此前已合并清理。**评价是唯一的「评价类」UGC**，用户查看自己的贡献经菜品详情评价区（一人一菜一评）。
- **取消「评价同步生成社区内容」**：后端评价接口的「同步到社区」布尔字段与社区服务的联动方法已删除；评价可见性只由评价自身审核态 / `is_hidden` 决定。
- **软键盘适配要求保留**：评价弹层软键盘弹起时输入区须上推且提交按钮不被遮挡（`adjust-position` + 足量 `cursor-spacing` + 按 `keyboardheightchange` 在内容尾部注入等高空隙）。

#### 2.1.5 已移除（历史保留）
- `settings`（设置，2026-08-03）→ 设置项内嵌 mine（「我的」根页），无独立路由。
- `activity-detail`（活动详情，2026-08-12）→ 活动直接经 `web-view` 跳转，无中间详情页；**活动列表页与 web-view 页已随 2026-09-13 活动全链路下线整体删除**（`pages/activity/` 分包移除，见 §0.5）。
- `my-publish` / `my-submissions`（2026-08-15）→ 由「我发布的」页承接（该页已于 2026-09-12 随社区板块删除）。`my-reviews`（旧路径 `pages/pages-user/my-reviews`）曾随评价收敛移除，**2026-09-12 已以新路径 `pages/me/my-reviews/index` 回加为在册页面**（mine 宫格「我的评价」进入，见 §2.1.2 / §2.1.4）；菜品详情评价区仍是评价的主消费场景，两者并存不冲突。
- **社区板块四页（2026-09-12）**：社区广场 / 社区详情 / 「我发布的」/ 社区发布页 → 随社区板块下线**整体删除**（目录 / 路由 / 入口 / 接口 / 库表 / 文档，见 §0.5）。
- `review-list`（档口/食堂维度聚合评价）→ 取消独立跳转，改内联；菜品维度「全部评价」保留为独立页（§2.1.2）。
- `dish` 原底部弹层 `DishDetailSheet` 已弃用（2026-08-12 复核恢复为独立二级页 `pages/detail/dish/index`）。
- `notify`（旧消息中心，历史）→ 职责由 mine 消息区块（系统通知 / 我发布的等宫格入口）+ `feedback` 承接；`messages` 残留路由已随孤儿清理移除（见 §2.1）。
- **账号密码登录体系（2026-08 微信登录体系拍板移除）**：无登录页 / 注册页 / 密码修改 / 密码重置；`AuthSheet` 从「登录表单」重构为「学号邮箱 + 验证码认证弹层」（详见 §5.y）。「退出登录」语义改为「清除本地登录态」（微信重新打开仍静默登录）。
- Web `web/src/`：`api/`(含 `adapter.ts`)、`views/`、`components/`、`router/`。
- 上传图片存 `uploads/images/YYYY/MM/{uuid}.{ext}`，DB 只存相对路径 `/images/...`。

## 3. API 基础规范
- 统一响应：`{ code: number, message: string, data: T }`；成功 `code=200`；异常由 `GlobalExceptionHandler` 统一包装，Controller 不得裸抛。
- 错误码：`200` 成功 / `400` 参数 / `401` 未登录 / `403` 无权限 / `500` 服务器错误；**禁止自定义非标错误码**（如 1001/600）。**例外（2026-08-19 登记豁免）**：`4031` = 邮箱未认证（`@RequireVerified` 触发），与 `403`（普通无权限，含越权访问管理接口）区分，供前端「需先认证 vs 无权限」分流提示；前端 `http.ts` 据此分别处理。
- **内容安全安检（2026-09-13）**：全部 UGC（评价 / 反馈的文本与配图）须经微信内容安检（文本 `msgSecCheck` v2 / 图片 `imgSecCheck`）；安检**违规一律以 `400` 返回**（文本 `suggest=risky` / 图片微信 code `87014`），**不新增错误码**；`suggest=review` 落 `sec_state=review` 进人工复核、不拦截提交（契约细则见 §5.a 与 `docs/api-design.md` §4）。
- 认证：JWT 经 `JwtAuthFilter`；白名单（实际 `SecurityConfig.PUBLIC_ANY_METHOD`，同路径已含 `/api` 前缀）为任意方法放行：`/auth/wechat-login`、`/auth/email-code`、`/auth/verify-email`、`/auth/admin/login`（管理后台登录，方案 C）、`/feedback`（公开提交）；`GET` 仅放行公开浏览：`/canteens`、`/stalls`、`/dishes`、`/reviews`、`/categories`、静态图片 `/images/**`；学生 UGC 写操作需 `verified=true`（见 §5 认证与鉴权），不再依赖 `STUDENT` 角色；`/admin/**` 仅 `ADMIN`（含 `SUPER_ADMIN`）。**移除 `/auth/login`、`/auth/register`、`/auth/password/reset`（废除账号密码登录）**。
- 分页：`PageResult<T>{ records, total, page, pageSize }`，用 MP 分页插件；单页非分页接口返回 `List<T>`。
- 金额：存储与传输一律「分」（int/Long）；分↔元转换必须在 api 层统一（`utils/money` 的 `fenToYuan`/`yuanToFen`），**禁止页面/组件层裸算**；前端统一展示已为元的 `price`（不得再在模板 `/100`）。
- 数据隔离：从 `SecurityUtil.getCurrentUserId()` 取用户，禁止信任前端 userId；UGC `created_by=当前用户`。**学生端无菜品写接口**——`POST`/`PUT`/`DELETE /dishes` 均已于 2026-09-13 全部下线（客户端零消费），菜品由管理员录入（`/admin/dishes/**`），学生提交菜品需求走意见反馈 `add` 类型由后台处理；`dish.created_by` 仅留痕历史学生提交，不再作为学生侧写权限依据。
- **接口契约 / 状态机 / 字段命名裁决（UGC 审核、Dish、Review、User、喜欢语义、学生 UGC 路径等）**：新增接口须先在 `server/src/main/resources/db/schema.sql` 与代码注释中登记契约再实现，不得绕过本文件红线。

## 4. UI 设计规范（Apple Design 风格）

### 4.1 适用范围与八原则
- 适用端：微信小程序（uni-app）、Web 管理后台（Vue3 + Element Plus）。
- 八原则：Purpose / Agency / Responsibility / Familiarity / Flexibility / Simplicity / Craft / Delight；流体交互四要素：即时响应、1:1 直接操控、可中断、速度 / 动量接力。

### 4.2 视觉 Token（基线）
- 品牌主色：暖砖红 `#C45549`（浅色模式主色；**2026-09-06 复核，由朱砂红 `#9B2A1D` 定调为 `#C45549`**，更明快亲和、与故宫红墙同色相；深色模式主色见 `client/src/theme/tokens.ts` 的 `primary-dark`）。小程序按钮统一 `AppButton`（primary 取 `#C45549`，outline/text 沿用）/ 管理端侧栏同步改用同色（替代旧深红 `#6B1010`）。**色值为全站唯一事实源**：以 `client/src/theme/tokens.ts`（`COLOR_MAP.primary`）与 `App.vue` 的 `page` 浅色块为准；`client/uni.scss` 为已废弃的浅色 token 快照（其 `#7A241A` 陈旧且与事实源冲突，待清理，见 tokens.ts 注释「删除 uni.scss 后」）。裸 hex 例外（`<swiper>` 指示点）须在 `tokens.ts` 的 `SWIPER_INDICATOR_*` 登记，主色变更须同步（原 `web-view` progressbar 例外 `WEBVIEW_PROGRESSBAR_COLOR` 已随活动下线、`web-view` 退出小程序移除）。
- 圆角：卡片 `16px`；底部弹层 `20px 20px 0 0`。材质模糊 `blur(20px) saturate(180%)`；按压反馈为 bg-soft/opacity（**scale 按压已废止**，见 §4.9；Web 端 `scale(var(--press-scale))` 为登记豁免，同见 §4.9）；弹层阴影 `0 -8px 30px rgba(0,0,0,0.12)`。
- 小程序自研组件（新页面必须复用）：公共 `components/`（实况：`AppButton/AppHeader/AuthSheet/BaseSheet/ActionSheet/ListPickerSheet/ReportModal/CardSection/FilterBar/SectionTitle/TagLabel/TabBar/IconSvg`）与页内私有组件（按 §2「前端组件组织原则」下沉，如 `pages/home/DishCard.vue`、`pages/home/HomeContent.vue`）；**UGC 配图组件（多图选择 / `wx.compressImage` 压缩 / 预览 / 删除，上限 3 张）随 2026-09-13 配图拍板恢复建设**（原 `ImageUploader` 曾于 change `prelaunch-loop-closure` 随 UGC 图片下线删除；恢复后作为「写评价弹层」与「意见反馈表单」共享组件，落位遵循 §2 组件组织原则），图片上传另有用户头像与后台菜品图两条独立链路；`TabBar` 实况路径 `components/TabBar.vue`。（社区板块的卡片与图片墙组件已于 2026-09-12 随板块下线删除；**`CategoryTabs`、`Loading` 已于清理提交 f9560c6 删除**——分类切换由 `FilterBar`/筛选条替代；**`EmptyState`、`StateView` 已于 2026-09-06 随状态占位清理变更删除**——列表/信息流不设空态占位，**失败态按 MP-012 呈现「加载失败 · 点击重试」块（登记见 `openspec/specs/client-page-structure`）**；页面细则以本文件 §4 为准）。
- 管理端：Element Plus + 自封装 `DataTable/FormDialog/ConfirmDialog/StatusTag/ImageUpload`；**`SearchInput` 组件已于清理提交 f9560c6 删除**，管理端搜索统一用 `el-input`（原 `docs/web-ui.md` 已删除，勿再引用）。
- **小程序图标统一使用 SVG 矢量图标**（本地 `client/src/assets/icons` 优先，缺失从阿里云矢量库 Iconfont 经 MCP 拉取）：搜索=ic-search、位置=ic-location、喜欢=ic-heart、有用/点赞=ic-thumb、热门=ic-fire、限时=ic-clock、猜你喜欢=ic-lightbulb、分享=ic-share、评价=ic-comment、发布=ic-plus、举报=ic-report（图标映射见本 § 上文列表）。语义唯一：ic-heart=喜欢（不与点赞混用）、ic-thumb=有用/点赞；**收藏功能已移除，无收藏图标**。**禁止 emoji 字符充当图标**。

### 4.3 动效系统（Motion）
> **MVP 动效边界（已拍板，权威口径 `openspec/specs/client-ui-motion`，2026-09 归档 capability）**：① 内容立即可见、不依赖动画显现；② **不引入装饰性入场动效**（列表/卡片静态呈现，无上浮淡入/交错延迟/滑入）；③ **弹层/弹窗开合去 transition，瞬开瞬关**；④ 按压反馈仅 `:active` opacity 弱化、不缩放。下表 spring 参数**不作为当前实现要求**，仅作为未来恢复动效时的参数基线留存，不构成双重口径。

| 交互 | Damping | Response |
| --- | --- | --- |
| 常规 UI | 1.0 | 0.3–0.4 |
| 抽屉 / Sheet | 0.8 | 0.3 |
| 旋转 / 翻动 | 0.8 | 0.4 |
| 位置重排 | 1.0 | 0.4 |
- 默认全站 `damping 1.0`；仅手势带动量时加回弹 `0.8`。可中断：永远从当前屏幕呈现值起步。Web 用 Motion/Framer Motion：`1.0≈bounce 0`、`0.8≈bounce 0.2`。（同上：仅动效恢复期适用；当前 MVP 以本节顶部 capability 拍板为准）

### 4.4 交互反馈
- 点按：按下即时 `background: var(--color-bg-soft)`（或 `opacity` 微降）作按压反馈（2026-09-12 拍板：废止 `scale(0.97)` 按压——mp-weixin 下 `transform: scale` 按压易致卡片边缘溢出/裁剪，小程序端统一 bg-soft 按压语言；Web 端豁免见 §4.9）；命中区 +~10px 滞回，可按住拖离取消。
- 抽屉 / Sheet：**开合 transition 已去除、瞬开瞬关**（§4.3 capability 拍板）；**下拉关闭手势保留**（仅向下拖拽、阈值 ~120px，松手超阈值关闭否则回弹，见 §4.9）。「spring `0.8/0.3` + 手势可中断、按速度符号决定提交/回弹（阈值 ~50%）」仅作为未来恢复动效时的基线。
- 弹窗：**瞬开瞬关**（无进出过渡）；「锚定触发源、进出同路径、缓动镜像对称」仅作为未来恢复动效时的基线。
- 滚动橡皮筋：`(over·dim·k)/(dim+k·|over|)`，`k≈0.55`。

### 4.5 材质与层级
- 半透导航 / 工具条 / 抽屉：`backdrop-filter: blur(20px) saturate(180%)` + 半透底；材质权重编码层级（结构区更重更暗，交互元素更轻更亮）；不叠两层轻透面。

### 4.6 字体排版
- tracking 随字号（大标题 `-0.02em`，正文 `0`）；leading 反比（大标题 ~1.05，正文 ~1.5）；系统字体优先；`rem`/`em` 随用户字号缩放。

### 4.7 可达性与降级
- `prefers-reduced-motion: reduce` → 降级为仅 opacity 过渡；MVP 拍板后主要过渡动效已去除（弹层瞬开瞬关、无入场动效，见 §4.3 capability），本条仅在未来恢复动效时适用；`prefers-reduced-transparency` → 去模糊；`prefers-contrast: more` → 近实底 + 边框。
- 小程序：无 Pointer Events，用 touch + 自记速度历史；`backdrop-filter` 真机部分支持降级纯色半透 + 阴影；动画只用 `transform`/`opacity`。

### 4.8 组件级约定
- 卡片 tap 反馈走 `bg-soft`/opacity，**无入场动效**（§4.3 capability 拍板）；TabBar 切换不做过渡动效；抽屉 / Sheet 开合瞬开瞬关 + 下拉关闭手势（§4.4）；列表 / 瀑布流滚动橡皮筋；Toast 四态同帧触发；列表/信息流页**不设加载中骨架与 loading 指示**（随 2026-09-06 状态占位清理移除），**失败态按 MP-012 呈现「加载失败 · 点击重试」行内块**（失败 ≠ 无数据；分页失败静默，登记见 `openspec/specs/client-page-structure`）、**空态**仅首页贡献卡片与搜索无结果引导两处显式例外（登记见 `openspec/specs/client-page-structure` / `contribution-entry`），其余保持空白静默。
- **首页无广播栏**：首页结构 = 搜索框头部 + 筛选行 + 瀑布流（见 §2.1.4）；`broadcast` 表已于 2026-09-13 随公告全链路下线删除，不存在广播数据来源。

### 4.9 小程序 MVP 红线（布局 / 动效 / 图标 / 组件渲染）
- **布局（750rpx 视口）**：根容器视为 750rpx；横向用 `flex` + `flex-wrap`/`flex:1`/`min-width:0` 防溢出；图片 / 卡片 `width:100%` + `box-sizing:border-box`；禁止横向滚动条；长文本 `-webkit-line-clamp` 截断。每页须通过「真机 750rpx 无横向滚动 / 无裁切」。
- **动效（从简）**：仅 uni-app `<transition>`（位移 ≤8rpx）与简单 CSS `transition`（opacity/transform 轻量）；禁止 `@keyframes` 长动画、大位移、`scale>1` 入场；手势 Sheet / 抽屉仍走 §4.4，入场不做复杂 keyframe。
- **图标（SVG 矢量）**：按 §4.2 映射（本地 `assets/icons` 优先 + Iconfont 兜底）；新增语义须登记图标名并将 SVG 下载至 `client/src/assets/icons`，禁止 emoji 字符当图标，不得私自引入未登记图标。语义唯一：ic-heart=喜欢、ic-thumb=有用/点赞，互不混用。
- **组件渲染（禁止 wx:for 内具名 slot 分发）**：小程序多列 / 瀑布流组件**禁止**在父组件用 `<template #x>` 向子组件同名 `<slot name="x">` 分发——uni-app 编译 mp-weixin 后父组件 N 个同名 slot 片段无法正确映射，子组件不消费该 slot 时整块**空白不渲染**（实测 `WaterfallList`：find/canteen 残留 `#card` 调用导致菜品区整块空白，阻断级 bug，2026-07-31）。`WaterfallList` 已内部 `import DishCard` 直接渲染，**禁止再向其传具名 slot**，统一 `<WaterfallList :list @card-click="goToDetail"/>` 经事件上抛父级。
- **小程序页面级 / 组件级 UI 设计细则（三态强制、AppButton 类型白名单、表单页 scroll-view 强制、emoji 登记前置、未生效设置禁虚假控制、金额 api 层统一、关联对象走正式 API、负向操作弱化、Sheet/SegmentTabs/ReviewItem/FeedbackForm 等组件抽取契约等 28 条）由小程序开发工程师按优先级落地；本文件仅定最高红线。**
- **UI 全量审计红线（2026-08-02 补充，BLOCKER 级，违反即阻断）**：以下规则自 2026-08-02 全量审计结果提炼，**与上方四条红线同属强制，新增/整改页面不得回退**：
  - **固定底栏避让**：任何含固定底栏（`submit-bar` / `comment-bar` / `action-bar`）的页面，其 `.scroll-wrap` 必须加 `padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom))`，**禁止**内容被底栏遮挡（BLOCKER 级）。
  - **事件绑定统一 `@tap`**：小程序内所有可点元素事件绑定统一用 `@tap`，**禁止**混用 `@click`（uni-app 编译 mp-weixin 时 `@click` 行为与 `@tap` 不一致，易致命中区/手势异常）。
  - **按压反馈统一走 `background: var(--color-bg-soft)`（或 `opacity` 微降），废止 `transform: scale` 按压（2026-09-12 拍板）**：mp-weixin 下 scale 按压易致卡片边缘溢出/裁剪，小程序端（`client/`）统一 bg-soft 按压语言；**适用范围 = 小程序端**——Web 管理端（`web/`）登记豁免：mp-weixin 的边缘溢出问题在 DOM 端不成立，`scale(var(--press-scale))` 按压（`web/src/styles/variables.css` 的 `--press-scale` + `web/src/directives/press.ts`）**维持使用，不受本条与 grep-zero 约束**。小程序端非按压强调 scale 仍须量化独立 token 登记。原小程序侧 `--press-scale` token 作废（`client/src/theme/tokens.ts` 不再有该键）。可点元素按下反馈（`.pressed` 类、`@tap` 触发元素的 `:active`、`.sheet-option`、`.cell`、action icon 等）一律不得写裸 `scale(...)`。适用范围覆盖**所有交互元素**：`.pressed` 类、`@tap` 触发元素的 `:active`、`.sheet-option`、`.cell`、action icon 等一律不得写裸 scale 值。**grep 期望 0 处裸 `scale(...)`（范围限 `client/`；`web/` 的 `scale(var(--press-scale))` 为本条已登记豁免）**（`pages/me/profile/index.vue` 的注释说明除外，仅注释、非样式规则），整改后须复验此 grep-zero 期望不破。⚠️ **裸 scale 红线（按压 scale 已废止，改走 bg-soft）**：仅「非按压强调 scale」（如 tab 选中放大高亮 `scale(1.05)`）须量化为独立 token（如 `--tab-active-scale`）并在 `client/src/theme/tokens.ts` 登记，方不作为 grep-zero 违规——未登记的非按压 `scale(...)` 仍计入 grep-zero 违规。
  - **图标统一走 `IconSvg`**：所有功能 / 情感图标一律经 `<IconSvg name="…" />` 渲染 `client/src/assets/icons` 下 SVG，**禁止**手写 `<text>+</text>`、`content: '+'`、`✦` 等文本 / Unicode 字符当图标（与 §4.2 / §4.9 emoji 红线同源强化）。⚠️ **`IconSvg` 必须注册中性 `empty` 占位键，缺失/未注册键禁止静默回退到语义图标**：`IconSvg` 内部**不得**采用 `ICONS[name] || ICONS.dish` 这类「未命中键静默落到语义图标（如 `dish` 碗）」的回退写法——拼写错误 / 未注册键（如 `name="empty"`）会无声渲染成菜品碗，造成「空状态显示菜品碗」这类静默语义 bug。须注册专用 `empty` 中性占位键（不可见/中性占位 SVG），缺失键渲染该占位键而非语义图标；**`IconSvg` 现已在 dev 环境（`import.meta.env?.DEV`）对未知 `name` 触发 `console.warn`（仍暂回退 `dish` 以保渲染，但告警已落地）**，便于及时发现拼写/注册遗漏。⚠️ **审计须 diff 字符串字面量 icon 与 `ICONS` keys，防未注册键漏网**：凡以**字符串字面量**向 `SettingCell` / `TabBar` / `ContributeSheet` / `AppButton` 等组件传入 `icon`/`name` 属性（而非动态键），审计时须与该组件实际读取的 `ICONS` 注册键做 diff，确认每个字面量均已注册；未注册键（如第八轮 `profile/index.vue:58` 的 `folder` 未注册、静默成碗）即便 dev `console.warn` 也不得放过，须登记整改——`console.warn` 仅辅助发现、不替代静态 diff 核查。⚠️ **中性占位必须为 `empty`（非 `dish`），且覆盖「IconSvg 回退目标」与「任何硬编码 ImageFallback / 破图占位」两处**：① `IconSvg` 的回退目标（含 dev 告警后的兜底落点）必须落在 `empty` 中性占位键，**不得**保留 `dish` 语义图标在中性占位语境的残留；② `ImageFallback.vue` 等全局图片裂图兜底组件的模板**硬编码**占位（如 `name="dish"`）一律改为 `name="empty"`——破图 / 空态语境禁止用语义图标（碗 `dish`）冒充中性占位（头像 / 档口 / 评价图加载失败全显示成碗属静默语义 bug，且该类硬编码不触发未注册告警，是第九轮新发现的全局兜底组件高危盲区）。⚠️ **审计须 grep 模板 `name="dish"` / `name="empty"` 逐文件核对中性语境**：凡模板出现 `name="empty"` 须确认确为中性占位语义；凡出现 `name="dish"` 须确认是「菜品 / 档口图语义」而非破图 / 空态占位冒充——两处（IconSvg 回退目标 + ImageFallback 等硬编码兜底）须同时落 `empty`，方算 IconSvg 红线收口。⚠️ **中性占位边界细化（第十轮收官补强）**：仅当组件语义**明确**为「菜品」时（如 `DishCard` 的菜品图占位）才可用 `dish` 作图片占位；**食堂卡 / 档口关联 / 关于页 / 通用轮播等容器语义≠菜品的中性场景一律用 `empty`**（如 `home` 食堂卡、`find` 搜索建议 `suggestIcon`、`RelatedPickerSheet` 非菜品关联项、`settings` 关于页、`ImageSwiper` 通用轮播等），不得用 `dish` 冒充中性占位。图标语义契约（10 轮迭代已稳定）：`thumb`=有用/点赞、`heart`=喜欢、`star`=评分，三者互不混用。
  - **底部抽屉 / 弹窗规范**：各类底部抽屉 / 弹窗须含 `env(safe-area-inset-bottom)` 安全区避让；开合**瞬开瞬关**（无进出 transition，§4.3 capability 拍板），不保留进出场缓动与 reduced-motion 交叉淡入降级条款（无过渡动效时自然满足）。
  - **`<swiper indicator-active-color>` / `<swiper indicator-color>` 裸 hex 为例外**：该原生属性（含激活态 `indicator-active-color` 与非激活态 `indicator-color`）不支持 `var()`，允许写裸 hex，但**须在 `client/src/theme/tokens.ts` 注释登记**（注明对应 token 名，便于全局改色时同步），不作为红线违规。
  - **UGC 配图与内容安检（2026-09-13 拍板恢复）**：评价与反馈**恢复配图**（各 ≤3 张，`wx.compressImage` 压缩至**最长边 ≤1334 且文件 ≤1MB** 后上传），**全部 UGC（文本 + 图片）须过微信内容安检**（链路与契约详见 §5.a）；配图入口须复用统一配图组件，**禁止**页面内联复制「+ 添加图片」上传逻辑 / 裸加号文本（`IconSvg name="plus"` 在配图组件内部作为「添加图片」触发语义使用，页面级仍仅用于非图片「添加」）。**用户头像单图上传**（`pages/me/profile/index.vue`，内联 `uni.chooseImage` + 受 `canSubmit` 门控的「延迟上传」流程，登记为合法例外）与后台录入菜品图片（Web 端）维持既有链路，不占用 UGC 配图契约。⚠️ **历史口径作废登记**：原「UGC 图片已全量下线、评价区与反馈表单均无图片入口、不得再新增 UGC 配图入口」（2026-09 `prelaunch-loop-closure`）条款**随本条一并作废**——此前按该口径落地的走查清单条款不再执行；grep 自检时以本条与 §5.a 口径为准（历史拍板记录保留「曾下线 → 2026-09-13 恢复为带安检的配图」演进说明）。
  - **分区标题复用 `SectionTitle`**：所有分区 / 区块标题一律渲染 `<SectionTitle title="…" />`；`CardSection` 内部**不另起**一套标题语言（不得手写 `.section-head`+`.section-title` 竖条 / 纯文字标题模拟 accent 条），表单内字段级 label 属字段语义允许纯 text。
  - **颜色全走语义 token（禁裸 hex）**：所有颜色（含限时 / 促销 / 热门等标签底色、`IconSvg` 的 `color` 属性、文字色、边框色、背景色）必须引用语义 token（如 `var(--color-hot)` / `var(--color-promo)` / `var(--color-primary)` 等），**禁止**在模板 / 组件样式中写裸 hex（如 `#FF6B6B` / `#FFB400`）。原生 API 不接受 `var()` 的颜色例外（如 `<swiper indicator-active-color>`、`uni.showModal` 的 `confirmColor` 等）**必须集中在 `client/src/theme/tokens.ts` 注释登记**（注明对应 token 名与用途，便于全局改色时同步）；且该常量须路由经过注册常量（如 swiper 指示点色统一经 `SWIPER_INDICATOR_ACTIVE_COLOR` 引用），**禁止在页面内联写裸 hex**——即裸 hex 只能出现在 `uni.scss` 的登记处，业务代码一律引用注册常量，登记后方不作为红线违规。
  - **底部 Sheet 统一下拉关闭手势**：所有 bottom-sheet（`FilterSheet` / `RelatedPickerSheet` 等现存弹层）必须统一支持下拉关闭手势——仅向下拖拽、阈值约 `120px`、松手超过阈值 `emit('close')` 否则回弹（拖拽跟手属 1:1 直接操控交互，不在「瞬开瞬关」禁止范围）。**禁止**个别 sheet 仅支持 mask 点击关闭、缺失下拉手势（与 §4.4 Sheet 口径同源强化）。
  - **审计时先查本段已登记例外清单，确认未登记才计违规**：上述各红线中凡标注「已登记合法例外」「为例外」「登记后不作为红线违规」之处，须以本段（§4.9 UI 全量审计红线）逐条登记的例外为准；审计 / 复验时发现疑似违规，**先查本段已登记例外清单，确认确未登记才计为违规**。未在本段登记的裸 hex / 内联逻辑等一律按红线违规处理。
- **Web 管理端 UI 细则（三栏布局、统一组件 `PageContainer/PageSection/...` 等）由 web 开发工程师按 web/src 既有组件约定落地；本文件 §4 定基础 token 与红线（视觉规范唯一权威在本文件，原 `docs/web-ui.md` 已删除）。**

## 5. 开发约束
- **平台定位**：仅美食信息公示与点评，**不涉及下单 / 支付 / 外卖**；无售罄 / 库存概念；浏览对游客开放；列表默认按热度降序，可切评分 / 价格，长列表无限滚动，无结果显空状态。
- 命名：Java PascalCase、字段 / 方法 camelCase；DB snake_case（MP 自动驼峰）；前端 TS camelCase，Web 经 `api/adapter.ts` 转换，禁止 View 层直接处理字段名。
- 所有 API 响应含 `code/message/data`；前端 `http.ts` 判定 `code!==200` 抛异常，页面 try-catch，Store fetch 失败置空数组不向上抛。
- Controller 入参 DTO + `@Validated`；Service 写操作 `@Transactional`；评分 / 点赞计数走 Spring 事件异步维护，禁止主流程内联重算。
- 内容审核流：学生提交 `audit_status=pending` → 管理员 `approved/rejected`（退回必填 `reject_reason` 并回显）；小程序仅展示 `approved` 且上架 / 营业中；评价 `is_hidden` 控制可见性；Web「菜品审核」「评价审核」为独立模块。**学生端无菜品写接口**（`POST`/`PUT`/`DELETE /dishes` 均已于 2026-09-13 全部下线），菜品由管理员录入后即 `approved`，**学生侧无编辑重提**（历史存量 pending 菜品仍可由后台审核）；学生菜品需求走反馈 `add` 类型由后台手工录入闭环。下架 / 变更类诉求走反馈 `error` 类型（关联菜品）承载，无独立申请表（`apply_action` 已于 2026-09-12 随贡献链路下线删除，见 §0.3）。
- **认证**：微信打开静默登录（`wechat-login`）即游客态；UGC 写操作需 `verified=true`（邮箱验证码认证）；**废除账号密码 / 注册**（管理后台登录例外，见 §5.y.5）。评价一人一菜一条（`uk_review_user_dish`）、点赞一人一票（`uk_useful_user_review`），业务代码须与唯一键一致。
- 小程序请求超时 8s、管理端 5s；API 基地址集中 `api/config.ts` 的 `API_BASE_URL`，禁止硬编码 URL。
- **首页不消费广播数据**：首页信息来源仅为菜品接口（推荐 / 筛选结果），不设信息流广播位；原运营广播 `Broadcast` 实体（ADMIN 录入通知条）方案已废弃，`broadcast` 表已于 2026-09-13 随公告全链路下线删除（见 §0.5）。

### 5.a UGC 配图与内容安检（2026-09-13 拍板，强制）

> 覆盖评价与反馈两类 UGC 的配图能力与微信内容安全检测；本节与 §0.5 产品定位、§3 错误码、§4.9 UI 红线联动。**此节推翻此前「评价区与反馈表单均无图片入口」的临时口径**（2026-09 `prelaunch-loop-closure` 期间形态），相关走查条款已按 §4.9 作废登记。

- **产品定位前提**：菜品 UGC 通道 = **反馈表单（可配图）→ 管理员审阅录入 / 修改 / 上下架**；**不恢复学生直建菜卡接口**（`POST /dishes` 维持已下线）。
- **配图规格**：评价与反馈各**最多 3 张**；前端经 `wx.compressImage` 压缩至**最长边 ≤1334 且文件 ≤1MB** 再上传；格式白名单 jpg/jpeg/png/webp。
- **图片存储链路（云存储中转 → 送检 → 转存）**：前端 `wx.cloud.uploadFile` 传**微信云开发云存储**（免域名白名单）→ 后端经 tcb `batchdownloadfile` 拉取 → `imgSecCheck` 送检 → 转存 **COS 永久存储**；小程序不直传 COS。
- **接口**：新增 `POST /api/upload/images`——**单张契约**：入参 `{ fileId: string }`（单个云存储 fileID），出参 `{ url: string }`（该张 COS URL）；多张配图由前端**逐张调用**，**单张失败（违规 / 超限 / 拉取失败）该张返回 400、前端提示后跳过、不中断其余图片**；≤3 张总量约束由评价 / 反馈提交载荷校验兜底（服务端对最终载荷再校验张数 ≤3 与 URL 域名白名单）。需登录、游客亦可，**无 `verified` 门槛**——反馈公开可提交，配图随之。既有 multipart `POST /api/upload/image` 保留（用户头像 / 后台菜品图）。
- **文本安检**：`msgSecCheck` v2（`openid` + `scene` + `version=2`）；`scene` 映射：**昵称=1、评价/反馈=2**；`suggest` 三态——`pass` 放行、`review` 落 `sec_state=review` 进人工复核（不拦截提交）、`risky` 拦截（HTTP `400`）。
- **图片安检**：`imgSecCheck`；违规（微信 code `87014`）拦截（HTTP `400`）。
- **安检状态字段**：`review.sec_state` / `user_feedback.sec_state`（**三态**：`pass` 通过 / `review` 机检待人工复核 / `rejected` 人工复核驳回，默认 `pass`；**不加新表**，列随两表现有结构扩展）。
- **可见性规则**：`sec_state='review'` 与 `sec_state='rejected'` 均**对非作者不可见**（后端过滤，前端不兜底），作者本人可见并呈现「安检复核中 / 未过审」态；管理后台可**放行**（→`pass`，恢复公开展示）或**驳回**（→`rejected`，持续对非作者不可见，作者侧呈现未过审态）。评价公开展示条件 = `is_hidden=0` **且** `sec_state='pass'`；`sec_state` 独立于 `is_hidden`（管理员隐藏语义不变）。
- **access_token**：安检调用统一使用微信 **`stable_token`** 并缓存，不走过期即弃的普通 access_token。
- **错误码与合规**：安检违规一律 `400`，不新增错误码；《隐私政策》采集类型须覆盖 UGC 文本与配图（见 `openspec/specs/privacy-compliance`）。
- **面向未来（不绑定云托管）**：COS / 安检 / 上传接口均不与云托管耦合，后端可整体迁移独立服务器；届时小程序上传域名改走**备案域名白名单**，链路结构不变。
- **管理后台（Web）**：新增**评价安检复核队列**（`sec_state=review` 列表 + 放行 / 驳回动作，接口 `PUT /admin/reviews/{id}/sec-state`）+ **反馈配图展示**（详情 ≤3 张可放大）。
- **环境变量**：新增 `COS_BUCKET` / `COS_SECRET_ID` / `COS_SECRET_KEY` / `COS_REGION`（见 `docs/architecture.md` §3.2 与 README 环境变量清单）。

### 5.y 认证与鉴权（微信登录体系，2026-08 拍板，强制）

> 全量拍板决策，替换旧「邮箱注册 + 密码登录」体系。管理后台登录例外见本 § 末尾。

#### 5.y.1 认证模型
- **废除账号密码登录**：小程序端无登录页 / 登录按钮 / 注册页 / 密码修改 / 密码重置；`/auth/login`、`/auth/register`、`/auth/password`、`/auth/password/reset` 及其 DTO（`LoginReq`/`RegisterReq`/`PasswordResetReq`/`PasswordUpdateReq`/`LoginResp` 密码相关）废弃移除。
- **微信自动静默登录**：微信打开小程序即调用 `POST /auth/wechat-login`（携带 `code`），后端 `code2Session` 换取 `openid`，按 `openid` 取号；不存在则自动建号。用户**默认即已登录的未认证账号**（`verified=false`，游客态），不做「未登录」概念（见 §5.y.3 游客态语义）。
- **认证解锁写操作**：`@bjtu.edu.cn` 邮箱验证码认证通过 → `verified=true`，解锁 UGC 写操作（写评价 / 评价点赞 / 菜品 / 档口·食堂提交等；原「发动态 / 评论动态」随动态板块下线移除）。**无收藏功能（全量移除确认）**。
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
- **合并动作**：认证时若该邮箱已存在历史 `user` 记录（旧账号），将旧账号的业务数据（菜品 / 评价 / 反馈等 `created_by`）**归属转移到当前微信账号**，并清理旧微信占位/旧记录；若邮箱无历史记录，则仅绑定 + 置 `verified=1`，无需迁移。
- **未邮箱注册过的用户无历史数据，无需迁移**。
- **新微信替换**：同一邮箱已被另一微信认证过 → 新微信认证时替换该邮箱绑定，旧微信账号下该邮箱的历史数据归属跟到新微信（旧微信变回游客态，其 `bind_email` 清空 / `verified` 置 0）。

#### 5.y.4 游客态语义与权限矩阵
- **游客态** = 已登录的未认证账号（`verified=false`）。前端「游客」标识用 `getGuestShortId()`（=「食客+ID 尾 4 位」）。
- **权限矩阵**：
  - 浏览全部公开数据（菜品 / 评价 / 食堂 / 档口）→ 游客可。
  - `POST /feedback`（基础反馈提交）→ **公开，无需认证**。
  - UGC 写操作（写评价 / 评价点赞 / 删本人评价）→ 需 `verified=true`。**学生端无菜品写接口**（`POST`/`PUT`/`DELETE /dishes` 均已于 2026-09-13 全部下线，客户端零消费）：菜品由管理员录入，学生提交菜品需求走意见反馈 `add` 类型由后台处理。（学生提交档口/食堂 `/my/stalls` 与美食清单模块已于 2026-08-18 随代码清理移除；发动态 / 评论动态已于 2026-09-12 随动态下线移除）
  - **系统通知（`/my/notifications/*`）→ 服务端认证专属（`verified=true`）、前端游客可直达（2026-09 校准，见归档 capability `openspec/specs/client-auth-boundary`）**：通知是按 `userId` 归属的账号私有数据（`/my/` 前缀；服务端 `@RequireVerified` 校验，游客请求被拒），内容为内容贡献者的行为反馈（审核结果）；游客（`verified=false`）无任何可产生通知的 UGC 写操作来源，个人通知恒空，**不属公开数据**。**前端边界（替代原 `authLocked=true` 入口锁定口径，前端已无该机制）**：「我的」页「系统通知」入口对游客**直接放行**进入通知页，页面**不挂 `AuthSheet`、不弹认证引导**；游客无个人通知或请求被拒时**静默处理**（不呈现空态提示 / 错误态 / 认证引导，「暂无通知」轻提示仅 `verified=true` 展示，失败静默、靠下拉刷新恢复）；游客态**不拉取未读数**（红点仅在 `verified=true` 时刷新）。本条**不适用**下方「入口不置灰、点击弹认证」口径。
  - **入口不置灰**：需认证功能入口对游客可见且可点；点击时弹**认证引导**（`AuthSheet`，触发「学号邮箱 + 验证码」认证），认证成功后自动继续原动作。
- 昵称保持「食客+ID 尾号」；`bind_email`（学号邮箱）**仅存认证关系、不公开**，可在「我的」页展示绑定邮箱。

#### 5.y.5 接口契约
- `POST /auth/wechat-login`（公开）— 入参 `{ code }`（微信 `wx.login` 临时凭证）；后端 `code2Session` → 按 `openid` 取号 / 自动建号 → 返回 `LoginResp{ token, userInfo(含 verified/绑定的 bind_email/昵称) }`。JWT 7 天。（实现约束，不改契约语义：微信 `jscode2session` 响应为 `Content-Type: text/plain`，后端须以「先取 String 再 JSON 解析」或等价方式处理，**禁止依赖 `MappingJackson2HttpMessageConverter` 自动转换**；见 `docs/architecture.md` §3.3）。
- `POST /auth/email-code`（公开，改造）— 入参 `{ username(学号), email(可空，自动推导 {学号}@bjtu.edu.cn), purpose }`；`purpose` 改为 `verify`（认证用途，替代旧 `login`/`register`/`reset`）；60s 限频、10min 有效。
- `POST /auth/verify-email`（公开，新增）— 入参 `{ code }` + 从当前微信账号上下文绑定：校验验证码 → 绑定邮箱 → 触发数据迁移合并（见 5.y.3）→ 置 `verified=1`、写 `bind_email`/`verified_at` → 返回更新后 `LoginResp`。
- `GET /auth/profile`（登录即游客可读）— 返回当前账号信息含 `verified`、`bindEmail`（是否已认证 / 绑定邮箱）、昵称、头像、`guestShortId`。
- `DELETE /auth/account`（登录，新增，2026-09-13 合规）— **注销账号（匿名化，非物理删除）**：事务内将 user 行置为 `nickname='已注销用户'`、`username=deleted_{id}`、avatar/email/password→NULL、**openid/unionid→NULL（解绑，允许同一微信重新静默登录创建新游客号）**、verified→0、bind_email/verified_at→NULL、status→'deleted'；评价/反馈/通知/浏览记录**保留**（展示昵称经 join 自然匿名化，评分聚合不破坏）；email_verification_code 按邮箱清理；当前 token 经 TokenBlacklist（token+userId 双维度）立即失效 + status=deleted 持久兜底；幂等（重复调用 400「账号已注销」）；@AuditLog 埋点。前端：底部合规区「注销账号」→ 二次确认 → 成功后 forceLogout（skipAuthRetry 防 401 重试误删新游客号）。
- 鉴权：UGC 写操作改为**校验 `verified`**；`/admin/**` 仍仅 `ADMIN`；**系统通知 `/my/notifications/*` 属认证专属，服务端按 `verified=true` 校验（游客请求被拒、个人通知恒空；前端不拉取未读数，入口游客直达见 §5.y.4）**。
- **管理后台登录（方案 C）**：维持 `/auth/admin/login`（管理员账号密码 + BCrypt + JWT），与小程序微信登录体系解耦；`/admin/**` 校验 `ADMIN` / `SUPER_ADMIN`。管理端 token 为 **12 小时短期过期**（`ADMIN_TOKEN_EXPIRATION_MS`，降低泄露风险），学生端维持 7 天（`application.yml`）——两者策略分离为既定实现。

### 5.z 已拍板架构决策（强制）
- **D-A** 通知异步写 `notification` 用 `@Async` + 有界线程池，不引 MQ。
- **D-B** `view_log` 记录浏览足迹：当前（2026-08-19）采用**应用层 upsert**（`HistoryService.recordDishView` 存在则更新 `updated_at`、不存在则插入），未加唯一键（`uk_view_user_target` 为可选增强，待需严格去重时再补 DDL）。`addViewCount` 时同步写入足迹。
- **D-C** ~~报表导出返回 CSV 文件流，不引 Apache POI~~（报表导出功能已随 `ReportExportView` 清理移除，2026-08-18；此决策作废，留档备查）。
- **D-D** 推荐 / 热门（热门菜品 / 猜你喜欢 / 热搜词条 / 新晋黑马四个高频读接口）用 Caffeine 短 TTL 缓存(60s) + 写失效（`@CacheEvict`；`common/config/CacheConfig.java` **已落地实现**，2026-09 核实）；`recommendDishes()` 改 SQL 分页。
- **D-E** schema 漂移治理：启动时 fail-fast 校验或 CI 步骤。
- **Q1** 不建成就 / 等级 / 成长体系（无 `achievement`/`user_achievement`）。
- **Q2** 不置顶 / 话题 / 精选运营干预，不干预内容排序（无个性化分发）。
- **Q4** 必须交付：②举报复用 `user_feedback`(`related_type`/`related_id`)，不新建举报表（当前举报对象为评价，`related_type='review'`）③删除本人记录（**仅评价**；`DELETE /reviews/{id}`。原含菜品删除，该能力已随学生端菜品写接口 `DELETE /dishes/{id}` 于 2026-09-13 全部下线）。（①`DELETE /my/account` 账号注销接口因无前端入口已随清理移除，2026-08-18；④「关联对象双向跳转」随社区板块 2026-09-12 整体下线作废）
- **Q5** 不碰关注 / 粉丝流，不建用户关系表。
- **D-工作台（2026-08-18 对账拍板）**：Web 管理后台登录默认落地页为 `/dashboard`（`DashboardView`，工作台 = 待办 + 数据总览），契约见 §0.4.1。**工作台不含 ECharts 图表看板**；`/admin/dashboard` 返回的 `DashboardVO` 虽含趋势/排行/上新等图表字段（供后续图表看板复用），当前 DashboardView 不消费，图表看板非本期交付。前后端契约已对齐、登录首屏已落地，**无开发缺口，不需要为此新建开发 task**（本决策记录即对账结论，勿为已存在代码再拆 task）。

### 5.x 三端一致性红线（强制，违反即阻断级缺陷）
- **字段命名**：对外 JSON 一律 camelCase；跳转目标类字段统一 `targetType`/`targetId`/`targetUrl`（原 Banner/广播契约，二者均已移除后保留为通用跳转字段规范）；评价状态 `isHidden`(0/1) 非 `isDeleted`；UGC 配图字段统一 `images`（字符串数组，≤3 项 COS URL）、安检态字段统一 `secState`(`pass`/`review`/`rejected` 三态)（评价 / 反馈 VO 与提交请求标准字段，见 §5.a）；Web `snake_case` 仅允许 `api/adapter.ts` 内部，禁止进入 `types/` 或视图层。**（`favoriteCount`/`isFavorited` 已随收藏模块移除而废弃，不再作为字段命名约束）**。
- **错误码统一**：成功 200 / 参数 400 / 未登录 401 / 无权限 403 / 服务器 500（**4031 邮箱未认证例外见 §3**）；**401 统一处理**（2026-08-19 更新）：小程序 `http.ts` 对 401 先确保静默登录再自动重试一次，仍失败才 `handleUnauthorized`（清 token + Toast + 重新微信静默登录），`handleUnauthorized` 有并发去重防登录风暴；**不再用 `uni.$emit('auth:unauthorized')` 事件总线**（规避 HMR 重复订阅泄漏）；**web `http.ts`（管理后台）** 补齐 401 拦截（清 `localStorage.token` + 跳转管理后台登录页 `/login`，方案 C 仍用账号密码）。
- **喜欢 / 收藏单一概念（收藏全量移除，2026-08-12 复核）**：原 `favorite`/`/favorites` 端点、表、字段（`favoriteCount`、`isFavorited`）已彻底删除；**前端不得保留任何「收藏」入口或按钮**（含 `pages/mine/index.vue` 的「我的收藏」、`pages/detail/dish/index.vue` 底部收藏按钮、`my-favorites` 页），统一移除。语义仅保留 `ic-heart=喜欢`（点赞/喜欢，非收藏）；禁止 `like`/`favorite` 双体系、禁止 `like_count`。`DishVO` 不再含 `favoriteCount`/`isFavorited`（历史口径混淆已废）。
- **状态枚举**：Dish `status` on/off；Canteen/Stall `status` open/closed；Web 内部 `active/inactive` 须经 adapter 映射回后端枚举。（Banner 及 Broadcast/Activity 已随公告/活动下线移除）
- **User 无 stall**：`UserVO` 不含 `stallId`；web `userToLegacy` 的 `stall_id` 映射须删除。
- **学生 UGC 路径**：学生端**无菜品写接口**（`POST`/`PUT`/`DELETE /dishes` 均已于 2026-09-13 全部下线，客户端零消费）；**菜品由管理员录入**（`/admin/dishes/**`），学生提交菜品需求走意见反馈 `add` 类型（`POST /feedback`）由后台处理。学生端保留的 UGC 写操作 = 写评价 / 评价点赞 / 删本人评价——均需 `verified=true`（见 §5.y 权限矩阵）；反馈与举报公开免认证；`POST /dishes/{id}/view`（浏览埋点）与全部 GET 接口保留。严禁 `/stall-owner/**`。（学生提交档口/食堂 `POST /my/stalls` 已随功能移除，2026-08-18）
- **分页结构**：列表接口统一 `PageResult<T>{ records, total, page, pageSize }`；单页非分页返回 `List<T>`。
- **整改影响面清单（谁改什么）以本文件各红线条款为准，不再另立文档。**

## 6. 协作纪律
- 本文件为**唯一权威基础规范**；多 agent 协作流程与角色权限见 `CODEBUDDY.md` 与 `.codebuddy/agents/`（原 `docs/WORKFLOW.md` 已删除，勿再引用）。
- **仅技术负责人可修改本文件**；其余角色（后端 / 小程序 / Web / 质量把控工程师）发现与本文件或代码冲突时，须提技术负责人裁定，不得自行绕过或改本文件。
- 踩坑经验回流：实测证伪的方案（如 §4.9 组件渲染红线）由技术负责人提炼进本文件红线。

## 7. 产品蓝图定版决议（2026-09-14 用户拍板）

> 本章为用户于 2026-09-14 逐条拍板的最终口径。**与前文冲突时以本章为准**（本章明确声明继承的条款除外）。后续任何改动须重新拍板，不得静默修改。

### 7.1 首发范围：校本部先上
- 仅北京交通大学校本部食堂/档口/菜品上线，其余校区后续追加。
- 线上现状已吻合（2026-09-14 核实）：`canteen` 7 条（学一/学二/学三/明湖/嘉园/清真/留园，均为本部）、`stall` 14 条、`dish` 31 条，无需清理。
- 首版无校区维度筛选需求；若未来扩展多校区，数据模型变更须重新拍板。

### 7.2 首版核心定位：找吃的
- 迭代资源优先投入**搜索 / 筛选 / 推荐 / 导航**，目标是让学生快速决定「今天吃什么」。
- 口碑（评分/评价/晒图）与社区（榜单/分享）不作为首版重点。

### 7.3 数据维护：学生投稿 + 管理员审核（投稿与反馈合并，不独立建模块）
- **投稿不是独立模块**，而是 feedback 的一种 `type`：
  - `type=add`：新增菜品投稿
  - `type=error`：信息纠错
  - `type=report`：举报（见 §7.4）
- 载体字段已具备：`FeedbackReq` 的 `type` / `content` / `images`（≤3 张 COS 绝对地址）/ `contact` / `relatedType` / `relatedId`。
- **首版落地（零开发）**：学生在反馈入口选择「新增菜品 / 信息纠错」提交 → 管理员在 `/admin/feedbacks` 按 `type` 筛选 → **手动**到菜品管理页新增。
- **红线澄清（重要）**：本条**不推翻**「学生端菜品直发 / 编辑 / 删除接口已下线」的既有决议。学生提交的是**待审核的反馈记录**，不直接产生菜品数据，**不得恢复任何学生端菜品写接口**（无 `PUT/DELETE /dishes/{id}`，无学生直发入口）。
- **小程序端文案要求**：投稿类目必须明示「提交后由管理员审核后上架」，禁止呈现为「提交即生效」。
- **二期**：管理端反馈列表增加「~~一键转菜品~~」（自动带出名称/配图/关联档口）—— ~~一键转菜品~~ **已作废（§7.13 第 1 条，2026-09-14 用户拍板）**：投稿仅作管理员参考信息，须人工编辑后录入，不得直接生成菜品数据。

### 7.4 UGC 审核：先发后审 + 举报，纯人工巡查
- 评价与配图**实时可见**（先发后审），**不做**举报自动隐藏阈值。
- 举报链路已闭环：`feedback(type=report)` + `relatedType=review` + `relatedId`；小程序端 `ReportModal` 已挂首页；管理端 `FeedbackAdminController` 可按 `type` 筛选。
- 处置手段均已具备，**无需新增开发**：`PUT /admin/reviews/{id}/hide`、`PUT /admin/reviews/{id}/sec-state`、`DELETE /admin/reviews/{id}`。
- 运营要求：管理员须定期巡查反馈与评价列表，无自动化兜底。
- **机制澄清（2026-09-14 核实）**：评价侧**无 `audit_status` 字段、无人工前置审核态**。`review` 表仅有 `sec_state`（`pass`/`review`/`rejected`，默认 `pass`）与 `is_hidden`（默认 `0`）。「先发后审」的实际机制为：机检 `pass` → 立即对外可见（`is_hidden=0 AND sec_state='pass'`）；机检 `risky` → 提交时即被 `checkText` 抛 400 拦截、不落库；机检 `review` → 仅作者本人可见（前端提示「审核中」），进管理员复核队列。管理员事后通过 `PUT /admin/reviews/{id}/hide`、`PUT /admin/reviews/{id}/sec-state`、`DELETE /admin/reviews/{id}` 处置。**代码零改动即满足本条决议。**
  另：红线中「只展示 approved 且未隐藏数据」不适用于评价实体，勿据此为评价引入人工前置审核。

### 7.5 UGC 准入门槛：verified=1 且 openid 非空（双约束）
- 发评价 / 发图必须**同时**满足：邮箱认证通过（`verified=1`）**且**账号具备微信 `openid`。
- 依据（2026-09-14 代码核实）：
  - 登录方式仅有 `POST /auth/wechat-login`（有 openid）与 `POST /auth/email-code`（无 openid）；**无学号密码登录**，`user.password` 为历史遗留字段。
  - 微信 `msgSecCheck v2` 必填 openid；`ReviewServiceImpl` 在 openid 为 NULL 时「跳过机审放行」。
  - 若只校验 `verified`，恰好放行无 openid 的邮箱账号，机检被完全绕过 —— 故必须双约束。
- 邮箱登录账号：补做一次微信登录（取得 openid）后即可发 UGC。
- 机检失败为 **fail-closed**（`ContentSecurityServiceImpl` 抛 500 拦截，不放行）；图片 >1MB 直接拒绝上传。
- **适用范围**：本双约束仅适用于**公开可见的评价内容**。反馈 / 投稿 / 举报不在此约束内（见 §7.7 第 1 条）。
> **准入范围收窄（2026-09-14 补充）**：本双约束**仅作用于 `POST /reviews`（写评价正文与配图）**。评价「有用」点赞（`POST /reviews/{id}/useful`）仅需登录，不受本约束（见 §7.8 第 3 条）。

### 7.6 收藏：不存在，且不做（措辞勘误）
- 产品确认**无收藏功能**（2026-08-12 全量移除复核），后续也不做。
- 三端代码零残留；仅 `DishQueryReq.java:42`、`DishServiceImpl.java:109`、`DishServiceImpl.java:419` 三处说明性注释提及，属历史措辞。
- **勘误记录**：2026-09-14 某次需求确认的选项描述中曾误用「收藏」一词，系提问措辞失误，非功能存在。此后文档与需求表述一律不得将「收藏」作为功能项。

### 7.7 落地任务分档
**上线前必做**
1. UGC 准入双约束**仅作用于评价（含评价配图）**：写评价 / 评价传图须 `verified=1 && openid != null`；不满足时返回 **403**，message 用「请先完成邮箱认证并使用微信登录」（沿用现有 fail-closed 403 风格）。小程序端对无 openid 账号给出「请先微信登录」引导。
   **反馈（含投稿 `type=add`、纠错 `type=error`、举报 `type=report`）不设认证门槛**，保持公开免认证 —— 否则会把 §7.3 的学生投稿通道掐死（新用户未认证即无法投稿，与该条决议自相矛盾）。
   反馈虽免认证，服务端机检照常执行：有 openid 时正常调 `msgSecCheck v2`；无 openid 时**不跳过**，而是落库标记 `sec_state=review` 进入管理员人工复核队列。反馈内容**不对外公开展示**（仅管理员在后台可见），故跳过机审的暴露面可接受，但不能静默放行。
2. 反馈入口文案：投稿类目标注「提交后由管理员审核后上架」。

**二期**
3. 管理端反馈列表「~~一键转菜品~~」—— ~~一键转菜品~~ **已作废（§7.13 第 1 条，2026-09-14 用户拍板）**：投稿仅作管理员参考信息，须人工编辑后录入，不得直接生成菜品数据。

### 7.8 第二轮对齐决议（2026-09-14 用户拍板）

1. **管理端菜品直接生效**：管理员在后台**新增/编辑**菜品时，`audit_status` 直接置 `approved`（管理员即权威）。理由：`dish.audit_status` 建表默认 `pending`，而小程序端**仅展示 `approved`**，若不显式置位则后台新录入的菜品全部不可见（2026-09-14 实测：现有 31 条为种子脚本写入的 approved）。「审核中心」保留给学生投稿链路（feedback → 管理员人工录入）使用，不作为管理员自录菜品的必经环节。
2. **投稿形态**：学生投稿新菜品沿用**现有反馈表单**（自由文本 + 配图 ≤3 张），**不新增结构化字段**；由管理员人工解析后在菜品管理页录入。`type=add` 为新增菜品、`type=error` 为信息纠错。
3. **点赞准入**：评价「有用」（`POST /reviews/{id}/useful`）**仅需登录（STUDENT）**，**不适用 §7.5 双约束**。理由：点赞不产生公开可见内容、不触发内容机检。§7.5 的双约束**仅约束「产生公开可见内容并需机检」的评价正文与配图（`POST /reviews`）**。
4. **处理反馈**：举报/反馈被处理时**通知提交人**（复用既有 `TYPE_FEEDBACK_HANDLE` 通知机制），并对外承诺 **48 小时内处理**（小程序端反馈/举报说明文案需体现该承诺）。

### 7.9 字段语义定型决议（2026-09-14 用户拍板 · 第 4 批）

1. **`dish.region` 定型为「风味/菜系」维度（保留并补齐入口）**：权威值域为 `东北 / 川湘 / 粤式 / 西北 / 清真 / 其他`（以线上现有数据为准）。管理后台菜品表单须提供「风味/菜系」下拉（当前 web 端零维护入口，导致后台录入的菜品该栏恒空）；小程序菜品详情页展示该维度（现有展示逻辑保留，空值显示 `-`）。**注意**：该字段不是「校区」，文档与 UI 文案一律使用「风味 / 菜系」表述。
2. **「新品」标下线**：`DishVO.isNew` 与端上「新品」标签（`DishInfoCard` 的 `if (d.isNew) list.push('新品')`、`TagLabel` 的 `tag-new` 分支/样式）**全部删除**。理由：后端从未赋值该字段，端上标签永不显示，属零消费（2026-09-14 核实）。
3. **餐段 `dish.serve_period` 下线**：字段、实体/VO/DTO 映射、Mapper 列引用、管理后台表单与详情展示、小程序端数据映射**全部删除**。理由：端上无任何展示与筛选消费，属零消费。
4. **限量 `dish.limited` 下线**：同上全部删除。理由：线上 0 条数据使用、端上无展示规则（2026-09-14 核实）。
5. **数据库列清理**：`dish.serve_period`、`dish.limited` 两列按数据库红线处理——**只允许改 `server/src/main/resources/db/` 下的幂等脚本**，禁止直连 ALTER；脚本须带存在性判断，保证重复执行安全、不影响既有数据。

### 7.10 板块与身份降级决议（2026-09-14 用户拍板 · 第 5 批）

1. **小程序端 5 个零消费入口整体下线**（端上无任何页面/组件消费，2026-09-14 跨端核实）：`GET /dishes/hot`、`GET /dishes/new`、`GET /dishes/promotions`、`GET /dishes/rising`、`GET /dishes/recommend`，以及小程序端「档口菜品列表」取数函数。连带删除仅服务于它们的 service/mapper/缓存与小程序端零消费 store 成员、死 api 模块。
   **保留**：`GET /dishes`（首页瀑布流与筛选）、`GET /dishes/{id}`、`POST /dishes/{id}/view`、`GET /dishes/hot-search`（首页热搜在用）。
   说明：菜品**促销价与划线原价**（`promo_price`/`original_price` 字段与端上折扣标）**保留不变**，本次下线的只是「促销专区」这个独立入口。
2. **管理端「操作人身份」降级**：管理端已改为环境变量共享口令（无登录体系），认定「单口令即单人」，**不追究操作人身份**——移除 `user_feedback.handler_id` 与 `operation_log.admin_id` 的写入与前端相关自保护分支。二者列保留在库中(记为 retired 列)，不再保证有值；「反馈处理人/操作日志操作人」的可追溯性**明示降级**。若日后需要身份追溯，须重新拍板（例如注入固定操作人标识）。
3. **`review.tags` 列删除**：美团式写评（评价打标签）确认**不做**，从 `schema.sql` 与线上库删除该列（幂等迁移）。
4. **管理后台「账号设置」入口删除**：该入口指向未注册路由（点击空白），彻底删除；学生账号管理仍在 `/dashboard/system?tab=account`。同时移除管理后台顶部「管理员/超级管理员」的假角色判断，文案固定为「管理员」。

### 7.11 上线相关决议（2026-09-14 用户拍板 · 第 6 批）

1. **不新增「找吃的」之外的入口**：随机推荐（今天吃什么）、榜单、分享、搜索历史**均不做**。首页保持现状——顶部搜索框、筛选行、瀑布流、**热门搜索词（保留）**。
   **澄清（重要）**：热门搜索词**已上线在用**（`pages/home` 的热搜词 UI + `GET /dishes/hot-search`），本次决议是「不新增其它入口」，**不包含删除热搜词**，该接口与 UI 保留。
2. **分页契约收口**：后端 `PageResult<T>` 已补齐 `records` / `page` / `pageSize`（2026-09-14 实现），`list` 保留为只读派生字段（与 `records` 恒等值）作过渡兼容；`page`/`pageSize` 取归一化后的实际生效值。前端两端的 `records || list` 兼容兜底**暂予保留**，待稳定后再收敛（收敛动作另行排期）。
3. **举报处置为纯人工、无自动动作**：举报超时不自动隐藏或自动回复，仅在管理端置顶提醒人工处理。**举报去重（2026-09-14 实现）**：同一登录用户对同一被举报对象（`type=report` + `relatedType` + `relatedId`）重复提交时不再新增记录，返回业务提示；匿名（游客）举报因无身份标识**不做去重**（此边界登记备查）。
4. **上线前菜品数据逐条校对**：现有 31 条种子菜品由用户逐条核对名称/价格/描述/标签/风味，并补齐首图（2026-09-14 核实：31 条**全部缺首图**）。校对清单见 `docs/loop/dish-proofread-checklist.md`（含疑似重复项 id=6 与 id=24）。

### 7.12 验收与运营口径决议（2026-09-14 用户拍板 · 第 7 批）

1. **上线放行采用「三项并行」验收标准**：
   - ① **构建门禁**：`server` 编译（`mvn -q clean compile -DskipTests`）（**必须 clean**：增量编译会被既有 class 掩盖缺失 import 等错误，见 §7.13 第 3 条）、`client` 类型检查与构建（`type-check` + `build:mp-weixin`）、`web` 构建（含 `vue-tsc`）**三端全部通过**；
   - ② **手工走查清单**：由用户按 `docs/loop/launch-checklist.md` 在小程序真机与管理后台逐项走查并打勾归档；
   - ③ **线上自动冒烟**：由 agent 对线上环境执行接口级冒烟（读接口、鉴权、上传指纹、准入错误码等）。
   三项**并行**作为放行依据，缺一不可。
2. **不做「只读管理员」口令**：首版维持单一口令（`ADMIN_TOKEN`），不新增只读角色。（若日后需向食堂/档口方开放数据查看，须重新拍板。）
3. **菜品下架后，其评价保留可查**：菜品 `status=off` 不影响学生查看自己的评价（`/my/reviews` 不下架过滤，2026-09-14 核实 `ReviewMapper` 的 `selectReviewPageByUserId` 未按 `d.status` 过滤，**现状即符合，零改动**）。仅菜品本身在端上不可达。
4. **注销确认弹窗保留主色观感**：不改为危险色，仅把内联裸 hex 收敛为 `theme/tokens.ts` 中登记的常量（登记名 `MODAL_CONFIRM_PRIMARY_COLOR`，值同主色），满足 §4.9 裸 hex 红线。

### 7.13 二期范围与文案落位决议（2026-09-14 用户拍板 · 第 8 批）

1. **明确否决「一键转菜品」**：管理后台**不做**「把学生投稿一键转为正式菜品」的能力。理由（用户原话要旨）：投稿内容**必须由管理员编辑核对后再录入**，不能直接生成菜品数据。因此 §7.3/§7.7 中「二期：反馈一键转菜品」的表述**作废**，改为：投稿仅作为管理员的**参考信息**（含名称/档口/价格描述与配图），管理员在菜品表单中人工编辑后保存。
   若将来需要降低录入成本，**只允许**做「将投稿内容预填到菜品表单」的辅助能力，且**保存前必须由管理员编辑确认**；该能力须重新拍板后方可开发。
2. **文案落位（已定条款的执行）**：
   - 小程序反馈页：投稿类目（推荐菜品 / 信息不对）明示「**提交后由管理员审核，确认后才会展示**」（呼应 §7.3）；
   - 反馈提交区说明「**我们会在 48 小时内处理你的反馈，处理结果将通过站内通知告知**」（呼应 §7.8 第 4 条）。
3. **门禁口径更正**：构建门禁中后端编译**必须使用 `mvn clean compile`**（而非增量 `mvn compile`）。原因：增量编译会被既有 class 文件掩盖缺失 import 等编译错误（2026-09-14 实际发生：`DishServiceImpl` 缺失 `HistoryService` import 在增量编译下未暴露，`clean compile` 才报错）。
4. **D 批次收尾**：热度公式抽为单一片段/常量（`DishMapper.xml` 的 `heatScoreExpr` 与 Java 侧权重常量须保持等价，新晋黑马为独立口径保持不动）；`user` 表名（MySQL 保留字）经评估**保持现状**并在实体注释登记风险与注意事项。

### 7.14 数据可信度与体验调优（2026-09-14 用户拍板 · 第 9 批）

1. **浏览量去重**：`POST /dishes/{id}/view` 改为「**同一用户对同一菜品每天只计 1 次**」。复用既有 `view_log` 表（`user_id` + `target_type='dish'` + `target_id`，按自然日 `Asia/Shanghai` 判定），当月已存在记录则不再自增 `dish.view_count` 且不重复插记录（幂等）。允许"先查后插"的极小并发竞态，不加唯一键。目的：首页热度主要由浏览量驱动，需防刷、保证热度真实。
2. **评价「有用」参与排序**：对外公开的评价列表（按菜品 / 档口 / 食堂）**默认按「有用数」置顶**（`useful_count DESC, created_at DESC`），使点赞具备实际作用；`/my/reviews`（本人视角）仍按时间倒序。
3. **被隐藏的评价对作者本人可见并标注**：`/my/reviews` 不再过滤 `is_hidden`，作者可看到自己的被隐藏评价并显示「已被隐藏」标注，避免"评价凭空消失"。公开列表仍仅展示未隐藏且机检通过（`sec_state='pass'`）的评价；`sec_state='review'`（审核中）对作者本人放行的既有规则不变。两种状态文案需可区分：`review`=审核中、`isHidden`=已被隐藏。
4. **下线档口营业时间 `stall.business_hours`**：产品确认**不需要营业时间**（端上零消费，数据为种子值），故从实体 / VO / Mapper / 管理后台表单与展示 / `schema.sql`（含幂等 DROP COLUMN 迁移）中全部移除。`stall.floor`（楼层）与 `stall.window_no`（窗口号）**保留**（端上有消费）。
5. **口径澄清**：从产品视角，**食堂与档口是「菜品」的附属维度**（菜品必有归属档口，档口归属食堂），不是需要独立经营数据管理的实体；因此不为档口引入营业时间、经营状态等经营属性。

### 7.15 录入动线与热度权重决议（2026-09-14 用户拍板 · 第 10 批）

1. **食堂与档口改为「随菜品一起维护」**：撤销独立的餐厅/档口管理动线，管理后台的录入主路径改为——在**菜品表单内**直接选择或新增所属食堂与档口；`dish.stall_id` 外键约束**保持不变**（菜品必须归属档口）。
   - 内容管理页随之收敛（不再提供独立的食堂/档口编辑页；是否保留只读一览由 UI/UX 设计师裁决，见 `docs/loop/design/dish-entry-flow.md`）。
   - 新增档口/食堂所需的接口沿用既有 `/admin/canteens`、`/admin/stalls`（后台录入默认 `audit_status=approved`，不变）。
   - **不得**因此放宽外键或允许菜品无归属档口。
2. **热度权重维持现状**：首页热度 `heatScoreExpr`（`view_count×1 + rating_count×5×20 + avg_rating×20`）**予以认可**，本期不调整：保持「评价数 / 评分主导、浏览量辅助」的口碑优先口径，降低刷浏览量对排名的影响。若日后需要调整，须重新拍板并在 `DishMapper.xml` 的 `heatScoreExpr` 与 `DishHeatWeights` 两处同步修改。
