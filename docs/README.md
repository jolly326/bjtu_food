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

## 技术栈

- **后端**：Java 17 · Spring Boot 3 · MyBatis-Plus · MySQL 5.7（TDSQL-C）· Redis
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
| 限流 / Token 黑名单 / 验证码封禁计数 / **管理端登录失败与锁定计数** 存 JVM 内存 | 单容器云托管部署，重启清零可接受（**已拍板 2026-10-06 接受现状**）；**扩到 ≥2 实例前必须下沉 Redis** |
| 验证码枚举风险靠限频兜底 | 根因是发码端点匿名、表无 `user_id`（见 [schema/email_verification_code.md](./schema/email_verification_code.md)）；根治需产品确认是否要求登录后发码 |
| `canteen` / `stall` 多列保留但**无编辑入口、无 UI 消费**（`images` / `location` / `description` / `sort_order`；其中 `sort_order` 兼作**内部排序位** —— [schema/stall.md](./schema/stall.md) / [schema/canteen.md](./schema/canteen.md)） | **已定「保留不删」**（不建管理入口，也不在清理范围；见 [A1](./func/web/A-主数据维护/A1-食堂管理.md) / [A2](./func/web/A-主数据维护/A2-档口管理.md)） |
| 菜品搜索用 `%keyword%` 前缀通配 | 全表扫描 + 计算列排序必然 filesort。改检索形态需产品确认（涉及搜索体验） |
| **首页主列表 / 猜你喜欢排序不可索引**（`ORDER BY CRC32(CONCAT(seed,'-',id)), d.id`；`DishMapper.xml` 的 `selectDishPage` / `selectGuessLike`） | **已核实为可接受现状**（2026-10-06 直连生产库**只读**核对）：`dish` 实况 **25 行**，`EXPLAIN` 确为 `type=ALL` + `Using filesort`，该量级代价可忽略。排序的业务目的 = **会话种子稳定伪随机序**（`seed` 端上冷启动生成一次、会话内恒定 ⇒ 翻页不重不漏且内容不自变；重进小程序重掷 ⇒ 整体重洗，冷启动期每道菜都有机会被看到）—— 任何「可索引」改法都要把 `seed` 换成固定盐 / 预生成序，即**变更产品语义**（已否决「按天全局种子：全站同序、当天无新鲜度」），而加缓存对本形态无收益（键须含 `seed`，命中率≈0）。**触发条件（任一满足即必须改，不由「感觉慢」触发）**：① `SELECT COUNT(*) FROM dish WHERE status='on'` **> 5000 行**；② `GET /dishes` / `GET /dishes/for-you` 服务端 SQL 耗时 **P95 > 200 ms**（含 filesort）。触发后按序评估：**限制候选集**（先按索引过滤取 N 条再内存排序 —— 会引入「老菜永不出现」的选择偏差，候选池口径须产品确认）→ **全局日级种子 + 生成列**（新鲜度口径须产品确认） |
| **菜品种类保留独立列 + 系统维度标志**（`dish.meal_type_id` + `dish_attribute_dimension.system`；种类取值不并入 `dish.attributes` JSON） | 「种类」需**互斥 + 必填**（一道菜恰属一种、后台录入必填），普通描述维度无该约束；且 JSON 值无法建索引 ⇒ 独立列 + `idx_dish_meal_type` 是**唯一同时满足「互斥必填语义 + 可索引筛选」**的形态。理由真源见 [schema/dish_attribute_dimension.md](./schema/dish_attribute_dimension.md) |
| 菜品问题反馈页售价按**字符串**比对（`12.5` 与 `12.50` 判为有改动） | 属展示格式差异，改为数值比对会变更提交口径，需产品确认 |
| **无 IP 白名单 / 安全组** | 🔴 **平台不支持**：微信云托管仅有「公网 / 内网」两个开关，无安全组、无端口暴露、无内网穿透能力；且校园网出口 IP 共享，白名单会退化为「全校可访问」。**勿重复尝试**，详见 [`secur/server/部署与网络边界.md`](./secur/server/部署与网络边界.md) |
| **限频样板 / DTO 映射保留重复** | 9 处 IP 限频样板、`dish` / `review` 两处「实体 → VO」映射虽相似，但错误文案 / 目标类型各异，抽公共层仅省 ~10 行、回归风险高 ⇒ **已定「保留不重构」**（限频机制见 [`secur/server/通用防护机制.md`](./secur/server/通用防护机制.md)）|
| **`server/src/test` 保留「回归锁定」类注释** | 测试里「退役 / 2026-XX 口径变更 / 原 X 已改为」等叙述是**测试意图 / 防回归说明**，非历史残留；按零残留规则「回归锁定类注释保留」条款保留，清理反损可维护性 |
| **违规累积计数为「尽力而为」** | 机审命中后的计数写在**独立事务**里并对异常吞掉 + 记 ERROR：计数失败绝不能把一次明确的「内容违规」拦截变成 500 不明故障。丢一次计数的代价远小于误报故障 |
| **审计「变更前后值」快照只覆盖删除类 + 状态跃迁类** | 快照是为「**误删后重建**」存在的；纯新增 / 普通字段编辑已有 `target_id` + 时间线足以定位，全量字段级快照会让审计表体积与写入成本成倍上升 |
| **管理端前端隐显入口按角色收敛在 `utils/permissions.ts`** | 页面只写 `v-if="canWrite()"` / `v-if="canDelete()"`，**不在页面内自行解析角色**（一份判据、一处维护）；🔴 它只是体验，真正的门控在服务端 `AdminAuthFilter` |

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

