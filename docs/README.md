# bjtu_food — 文档索引

本目录是项目文档的**唯一入口**。按「事实类型」分层，每个事实只有一个归属处，避免同一信息在多处各写一份而漂移。

## 分层边界（改文档前先确认归属）

| 目录 | 回答什么问题 | 唯一真源 |
|---|---|---|
| [`api/`](./api/README.md) | 传什么、收什么 | 接口与字段契约（端点签名、参数、响应结构） |
| [`schema/`](./schema/README.md) | 怎么存 | 数据库设计（表 / 列 / 约束 / 索引）；**库结构唯一真源** |
| [`func/client/`](./func/client/README.md) 的 `[A-D]-*/` | 为什么这么算 | 学生端**业务规则**（按 A 浏览与发现 / B UGC 表达与治理 / C 账号与身份 / D 系统与合规 四板块分目录） |
| [`ui/client/`](./ui/client/README.md) | 长什么样 | 学生端**视觉与交互**（页面稿、形态基线） |
| [`func/web/`](./func/web/README.md) | 管理端怎么用 | 管理后台各功能板块的设计文档与进度看板 |
| [`ui/web/`](./ui/web/README.md) | 管理端长什么样 | 管理后台**视觉与交互**（设计变量 + 公共组件基线 + 页面稿） |
| [`perf/`](./perf/perf-00-度量口径与基线.md) | 多快 | 性能度量口径与基线 |
| [`secur/`](./secur/README.md) | **安全吗** | **安全设计真源**（威胁模型 / 鉴权 / 防护机制 / 网络边界）；按端分包 [`client/`](./secur/client/README.md) · [`web/`](./secur/web/README.md) · [`server/`](./secur/server/README.md) |


> **同一事实只写一处**。需要跨目录说明时用链接，不要复制正文。
> feature 文档只写业务规则，字段签名一律以 `api/` 为准；UI 文档只写视觉，字段同理。
> [`todo.md`](./todo.md) 是**待决事项清单（非真源）**：登记需要所有者拍板的开放议题，结论一经拍板即写入对应真源并从该文件移除。

## 技术栈

- **后端**：Java 21 · Spring Boot 3.2 · MyBatis-Plus · MySQL 5.7（TDSQL-C）
- **学生端**：Vue 3 + TypeScript · uni-app（微信小程序）
- **管理端**：Vue 3 + TypeScript + Vite（设计与进度见 [`func/web/`](./func/web/README.md)）
- **质量门禁**：后端 JUnit 5 + ArchUnit；前端 `vue-tsc` / `check:contract` / `check:ui` / `check:doc`（文档-代码一致性）/ `vitest`

## 已知取舍（不是待办，勿反复讨论）

以下为**已明确接受**的现状，均有充分理由，不作为缺陷跟踪：
| 项 | 理由 |
|---|---|
| **管理端引入 Pinia 但当前零 store**（`web/src/stores/` 为空目录） | 状态管理是管理端的既定架构位（会话、字典缓存都要落在这儿）；先注册、后按需建 store，避免为临时缓存另立一套模块级单例。**不算冗余依赖** |
| **管理端全量引入 Element Plus 样式**（`element-plus/dist/index.css`，与 vite 的按需 resolver 并存） | `ElementPlusResolver` 只为**模板内 `el-*` 组件**注入样式；`ElMessage` / `ElMessageBox` 是**函数式 API**，其样式不在按需注入范围内 ⇒ 必须全量引入。两者并存是Element Plus 的既有事实，不是「按需引入名存实亡」 |
| **列表缩略图不做懒加载**（两端一致） | 缩略图有 CSS 固定尺寸（无 CLS 风险），且列表本身已分页（单屏图片数有界）；加 `loading="lazy"` 收益不抵首屏延迟抖动 |

| 项 | 理由 |
|---|---|
| 限流 / Token 黑名单 / 验证码封禁计数 / **管理端登录失败与锁定计数** 存 JVM 内存（**限流真源**见 [`secur/server/通用防护机制.md`](./secur/server/通用防护机制.md)） | 单容器云托管部署，重启清零可接受（**已拍板 2026-10-06 接受现状**）；**扩到 ≥2 实例前必须下沉 Redis** |
| 验证码枚举风险靠限频兜底 | 根因是发码端点匿名、表无 `user_id`（见 [schema/email_verification_code.md](./schema/email_verification_code.md)）；根治需产品确认是否要求登录后发码 |
| `canteen` / `stall` 的 `images` / `location` / `description` 列保留且**无公开（client）消费方**（只随管理端出参下发，在 A1 / A2 详情抽屉内维护 —— [schema/stall.md](./schema/stall.md) / [schema/canteen.md](./schema/canteen.md)） | **已定「保留不删」**（不下发到学生端，也不在清理范围；见 [A1](./func/web/A-主数据维护/A1-食堂管理.md) / [A2](./func/web/A-主数据维护/A2-档口管理.md)） |
| 菜品搜索用 `%keyword%` 前缀通配 | 全表扫描 + 计算列排序必然 filesort。改检索形态需产品确认（涉及搜索体验） |
| **首页主列表 / 猜你喜欢排序不可索引**（`ORDER BY CRC32(CONCAT(seed,'-',id)), d.id`；`DishMapper.xml` 的 `selectDishPage` / `selectGuessLike`） | **已核实为可接受现状**（2026-10-06 直连生产库**只读**核对）：`dish` 实况 **25 行**，`EXPLAIN` 确为 `type=ALL` + `Using filesort`，该量级代价可忽略。排序的业务目的 = **会话种子稳定伪随机序**（`seed` 端上冷启动生成一次、会话内恒定 ⇒ 翻页不重不漏且内容不自变；重进小程序重掷 ⇒ 整体重洗，冷启动期每道菜都有机会被看到）—— 任何「可索引」改法都要把 `seed` 换成固定盐 / 预生成序，即**变更产品语义**（已否决「按天全局种子：全站同序、当天无新鲜度」），而加缓存对本形态无收益（键须含 `seed`，命中率≈0）。**触发条件（任一满足即必须改，不由「感觉慢」触发）**：① `SELECT COUNT(*) FROM dish WHERE status='on'` **> 5000 行**；② `GET /dishes` / `GET /dishes/for-you` 服务端 SQL 耗时 **P95 > 200 ms**（含 filesort）。触发后按序评估：**限制候选集**（先按索引过滤取 N 条再内存排序 —— 会引入「老菜永不出现」的选择偏差，候选池口径须产品确认）→ **全局日级种子 + 生成列**（新鲜度口径须产品确认） |
| **菜品种类用独立字典表 + 单值列**（`dish_category` 表 + `dish.category_id`；种类不并入属性维度 / 取值表，也不并入 `dish.attributes` JSON） | 「种类」需**互斥 + 必填**（一道菜恰属一种、后台录入必填），而描述属性是 **m:n 可空**（存 `dish.attributes` JSON）—— 两种关系不同，故各有自己的表与端点；且 JSON 值无法建索引 ⇒ 独立列 + `idx_dish_category` 同时满足「互斥必填语义 + 可索引筛选」。理由真源见 [schema/dish_category.md](./schema/dish_category.md) |
| 菜品问题反馈页售价按**字符串**比对（`12.5` 与 `12.50` 判为有改动） | 属展示格式差异，改为数值比对会变更提交口径，需产品确认 |
| **无 IP 白名单 / 安全组** | 🔴 **平台不支持**：微信云托管仅有「公网 / 内网」两个开关，无安全组、无端口暴露、无内网穿透能力；且校园网出口 IP 共享，白名单会退化为「全校可访问」。**勿重复尝试**，详见 [`secur/server/部署与网络边界.md`](./secur/server/部署与网络边界.md) |
| **限频样板 / DTO 映射保留重复** | 9 处 IP 限频样板、`dish` / `review` 两处「实体 → VO」映射虽相似，但错误文案 / 目标类型各异，抽公共层仅省 ~10 行、回归风险高 ⇒ **已定「保留不重构」**（限频机制见 [`secur/server/通用防护机制.md`](./secur/server/通用防护机制.md)）|
| **`server/src/test` 保留「回归锁定」类注释** | 测试里「退役 / 2026-XX 口径变更 / 原 X 已改为」等叙述是**测试意图 / 防回归说明**，非历史残留；按零残留规则「回归锁定类注释保留」条款保留，清理反损可维护性 |
| **内容治理不做累积处分**（`user` 表无处分计数列，库内**无处分留痕表**） | 不做「累计 N 次 → 警告 / 封禁」的梯度处置：次数阈值属运营口径，而带恢复期限的中间档位需额外状态位 + 到期自动恢复任务 + 一份处分审计表，维护与故障面不抵治理收益。内容治理只有两道实时动作 —— **机审 `risky` 即时拦截**（不落库、不计数，口径见 [secur/client/滥用与内容防护.md](./secur/client/滥用与内容防护.md)）与**管理端人工启停账号**（[`PUT /admin/users/{id}/status`](./api/web/users.md)） |
| **管理端不做操作日志**（**库内无审计表**，无日志端点、无日志页面） | 管理端账号仅 1 行（单管理员运营），写操作可按业务表自身的 `updatedAt` / 时间线定位；为「逐请求留痕」付出的一张表 + 每条写操作一次 DB 往返，收益不抵（**已定：整体不做**） |
| **安全告警不落库、只推送**（webhook：`ALERT_WEBHOOK_URL` / `ALERT_WEBHOOK_SECRET`） | 告警的职责是「**立刻知道**」（群消息实时到达）；拦截行为（登录失败锁定 / ID 枚举拦截）在请求链路上实时生效，不依赖告警记录。落库只会多一张与业务无关的表与一条旁路写路径（**已定：不落库**） |
| **纠错提交恒需登录、配图 ≤3 张** | 菜品问题反馈（`correction`）的提交入口一律需登录态 ⇒ `user_id` **恒为真实用户**（外键 `fk_correction_user`，无级联，见 [schema/correction.md](./schema/correction.md)），处理回执**恒可达**。配图与全站 UGC 同档 **≤3 张**，`field` / `gone` 两类**同一上限**（不按类型分档） |
| **字典类不设排序位，一律按 `id` 升序** | 字典项量级小（种类 6 / 反馈类型 3 / 举报原因 6 / 视图 7 / 维度与取值各十数条）；排序位带来的成本是「排序列 + 索引 + 全量拖拽提交端点 + 前后端交互」，收益仅是调整展示次序（**已定：按 `id` 排**） |
| **字典类展示名字段统一 `name`** | 同义异名（`label` / `name` 混用）会让「字典名」在表间、端点间漂移；统一为 `name` 后，`dish_category` / `dish_attribute_value` / `feedback_option` / `report_option` / `dish_filter_view` 与出参一一对应 |
| **`dish_filter_view` 由 `category_id` 单列驱动** | 首页 tab 的筛选需求收敛为「按某种类筛」与「不筛（为你推荐）」两态 ⇒ 视图只存一个种类 ID（`NULL` = 为你推荐）；可配条件语言（字段 / 操作符白名单）整体不做，免发版目标由「加一行 + 选种类」达成 |
| **`user` 行永不物理删除**（注销为匿名化） | 物理删除会让历史评价 / 反馈 / 举报 / 纠错失去归属；因此指向 `user` 的四条外键（`notification.user_id` 级联，`feedback.user_id` / `report.user_id` / `correction.user_id` 无级联）都只作引用完整性兜底，其余关系由应用层维护（见 [schema/README §外键与级联](./schema/README.md)） |
| **四类用户提交各自成表 + 处置外置**（`review` 由评价域自持；`feedback` / `report` / `correction` **只存提交事实**，append-only） | 「提交事实」与「管理侧处置」的**生命周期、读方、可变性完全不同**：提交行永不改写（历史可追溯），处置状态机独立演进。故三张工单表**无状态列、无 `updated_at`**，一律不含处置文案。**举报指向的评价被删时举报行保留**（`fk_report_review` 为 `ON DELETE SET NULL`，显式表达「被举报评价已删除」，而非连带消失）⇒ 无孤儿、无需清理任务。见 [schema/moderation_task.md](./schema/moderation_task.md) |
| **处置任务 1:1 预建、多态键无外键**（提交时**同一事务**写入 `moderation_task` 的 `status='pending'` 行） | 「未处置」是**显式行**而不是空值列：管理端状态筛选与待处置计数都只需任务表单表（`COUNT` / 唯一索引 `(type, type_id)` 上的 `EXISTS`）。归属用多态键 `(type, type_id)` 表达而**不设外键**（单列无法指向三张父表），引用完整性由提交事务保证；代价是**没有数据库级兜底**，换来的是工单表不反向依赖处置层 |
| **处置文案只有一列 `reply_content`**（不采纳原因并入正文；`outcome` 显式落列） | 「通过 / 不采纳」是**结论**、「为什么」是**文案**：两者分开表达（`outcome` 判分支、`reply_content` 给用户看），比「回复列 + 拒绝理由列」少一列、少一套两列互斥校验，且不采纳时**必填校验只落在一条路径**上。回执正文与任务行同句两份、同事务写入（快照一致性） |
| **分类标签全字典化（`*_option`）** | 反馈类型与举报原因各一张同形字典表（`id` / `name` / `status` / `updated_at`，共用的不变量：数据锚在 ID、改名免费、**被引用即禁删**、至少 1 条启用、启用 ≤8）。新增 / 改名 / 下线**免发版、免客户端改动**；代价是端上多一次字典拉取与一次 ID → 中文翻译（管理端翻译读全量含停用项） |
| **通知来源多域、与任务表无结构关联** | 通知是**用户侧消息通道**（消息中心 / 未读徽标 / `is_read`，随账号注销清空），触发源含三类工单的处置回执**与**「管理员隐藏 / 删除某人的评价」这类**无工单**的来源；因此两侧**互不加外键**（通知不加来源键、任务行不加 `notification_id`，避免消息表反向依赖业务表）。两份文案是**有意保留的同句快照**：管理端回显读 `moderation_task`，站内消息读 `notification`。见 [schema/notification.md](./schema/notification.md) |
| **管理端前端隐显入口按角色收敛在 `utils/permissions.ts`** | 页面只写 `v-if="canWrite()"` / `v-if="canDelete()"`，**不在页面内自行解析角色**（一份判据、一处维护）；🔴 它只是体验，真正的门控在服务端 `AdminAuthFilter` |
| **颜色变量「真源 → `generated-colors.css`」手工同步**（不恢复 `scripts/gen-css-vars.ts` / `npm run gen:tokens`） | 颜色真源（`client/src/theme/tokens.ts`）与生成物之间可能漂移，靠改值流程（[`ui/client/设计变量.md`](./ui/client/设计变量.md) §5）+ `npm run verify` 约束；**已定「保留手工维护」**，不为颜色生成引入构建期脚本 |

## 变更流程

1. 改**代码**时同步更新对应层文档（先按上表定位归属）。
2. 改**契约**（后端 VO / 端点）后需刷新端上类型：由 agent 本地启动后端（**仅限**抓取契约快照 `GET /v3/api-docs`）并执行 `client/` 下 `npm run gen:api:fresh`，随后跑 `npm run verify`；**用户无需手动刷新**（授权与红线见 `.codebuddy/rules`「文档优先同步」§一.8 例外）。
3. 改**库表**：先改 [`schema/`](./schema/README.md) 对应表文档（唯一真源），再执行 ALTER；**禁只 ALTER 不同步文档**。
4. 提交前跑质量门禁：
   - 后端：`./mvnw test`
   - 前端：`cd client && npm run verify`（契约 / token / 文档一致性 / 类型 / 单测五道）

## 约定

- 文档只写**当前口径**：变更过程与修订轮次不维护（需回溯从 git 历史查）。
- 本目录**入库 git 跟踪**（真源须可 diff、可回滚）；**文档内不得出现任何凭据**（口令 / token / Webhook 地址 / 密钥），一律经环境变量与 `server/.env` 流转。
- 范围外的能力不建文档。
- 命名：目录内用 `<板块前缀>-<主题>.md`。

