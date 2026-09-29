# 后端架构说明（server/）

> 建立于 2026-09-28。此前仓库无架构文档（`docs/architecture.md` 于 2026-09-27 删除后未补），
> 架构约定仅散落在各类的 javadoc 与 agent 指引中。本文是**后端架构的现行真源**。
> 各域的详细职责/依赖方向以 `server/src/main/java/com/bjtufood/*/package-info.java` 为准。

## 1. 技术栈与形态

| 项 | 选型 |
|---|---|
| 运行时 | Java 21 / Spring Boot 3.2.0 |
| 持久层 | MyBatis-Plus 3.5.5（`BaseMapper` + `resources/mapper/<域>/XxxMapper.xml`） |
| 数据库 | MySQL 8（结构唯一真源：`server/src/main/resources/db/schema.sql`） |
| 安全 | Spring Security 6（JWT 无状态；管理端口令 `X-Admin-Token`） |
| 鉴权模型 | 微信静默登录建号 → 游客态；学号邮箱认证后解锁 UGC 写（异常码 `4031`） |
| 文档 | SpringDoc OpenAPI 3（`/api/v1/swagger-ui/index.html`） |
| 测试 | JUnit 5 + Mockito + MockMvc 切片 + ArchUnit 架构护栏 |

**形态：模块化单体（modular monolith）**——单一部署单元，域间以**进程内契约**协作，不引 MQ、不拆微服务。
拆分动议出现时，域边界即拆分线（本文的边界约定因此是强制的）。

## 1.1 两端接口边界（2026-09-29 新增，**由 `FrontendApiIsolationTest` 强制**）

仓库含**两个前端**，消费**同一个后端**但走**两套互不通的鉴权**：

| 端 | 路径前缀 | 鉴权方式 | 过滤器 |
|---|---|---|---|
| `client/`（微信小程序） | `/api/v1/**` | `Authorization: Bearer <JWT>` | `JwtAuthFilter` |
| `web/`（管理后台） | `/api/v1/admin/**` | `X-Admin-Token: <口令>` | `AdminTokenFilter` |

**采用业界惯例：安全区隔离，而非「给每个客户端复制一份端点」。**
GitHub / Stripe / 各类 SaaS 均为「一个后端 + 一套 API + 两种鉴权」；真正的隔离在
**承载写操作与敏感数据的 `/admin/**`**——可在一个地方统一施加 IP 白名单、限频、审计、CORS 策略。

**允许共享**：**非敏感的公开只读字典/枚举端点**。它们在 `SecurityConfig` 内是 `permitAll`，
数据本身公开，两端复用不产生安全暴露，也避免为 web 复制冗余出口：

| 数据 | 端点 | 性质 |
|---|---|---|
| 筛选视图字典 | `GET /dishes/views` | 公开只读，**两端共用** |
| 描述属性维度字典 | `GET /dishes/attributes` | 公开只读，**两端共用**（2026-09-29 补齐，见下） |
| 举报原因字典 | `GET /feedback/report-reasons` | 公开只读，**两端共用** |
| 菜品 CRUD / 审核 / 用户状态 | `/admin/dishes/**` 等 | **管理端专属**（口令保护） |

> **`GET /dishes/attributes` 曾长期 404（2026-09-29 修复）**：web 端 `listDishAttributes()`
> 一直在调这个端点，而**后端从未实现**（本域只有按单菜的 `/dishes/{id}/attributes`），
> 导致管理后台「描述四维录入选项」始终为空。因 404 发生在网关层、后端日志收不到，该问题
> 长期无人察觉。现已补齐，并由 `FrontendApiIsolationTest#web_calledPathsExistInBackend` 兜底。

**护栏规则**（`mvn test` 阶段强制）：

1. **web 禁调需鉴权的学生端路径**（`/auth/**`、`/my/**`、`/upload/**`）——web 无 JWT 必然 401，
   且 `/my/**` 是用户私有数据，调用即越权；
2. **web 的非 admin 路径必须是公开只读端点**——越界的须改走 `/admin/**`；
3. **client 禁调 `/admin/**`**——管理端口令只在 web 侧配置；
4. **web 调用的路径必须在后端真实存在**——防「前端调了个不存在的端点却长期 404」。

> **维护约定**：web 需要**写操作**或**敏感数据**时，在 `/admin/**` 下另开端点；
> 仅当数据本身是公开只读枚举时才复用学生端端点。

## 2. 分包：按业务域，不按技术分层

顶层是**业务域**，不是 `controller/` `service/` 这类技术分层大包。

```
com.bjtufood
├── auth         认证与账号（登录、邮箱认证、资料、注销、归属迁移）
├── dish         菜品（展示/搜索/管理/描述属性字典/删除级联）
├── review       评价（写 + 只读查询契约）
├── feedback     用户反馈（含举报）
├── correction   菜品信息纠错
├── canteen      食堂与档口
├── notification 站内通知
├── moderation   UGC 内容审核（微信 msgSecCheck/imgSecCheck + 本地 DFA 词库）
├── wechat       微信平台集成（叶子域：jscode2Session + stable_token）
├── banner       首页轮播图
├── upload       图片上传（两条链路 + 尺寸/魔数校验）
└── common       跨域共享层（零业务依赖）
```

`common` 内部按**关注点**再分一层（2026-09-28 收口；此前 `config` 一包混装三类关注点，
读某个 `Config` 还得先判断它属于 Web 还是 DB）：

| 子包 | 内容 | 判定依据 |
|---|---|---|
| `config` | CORS / Swagger / Jackson / Async / WebMvc / 两个 Filter | **Web 与 Spring 基础设施** |
| `persistence` | `MybatisPlusConfig`、`MybatisMetaObjectHandler`、`StringListTypeHandler` | **持久化设施**，无业务语义 |
| `ratelimit` | `IpRateLimiter` | 承载**业务风控知识**（各端点「该限几次」由 Controller 自定），非基础设施 |
| `result` / `exception` / `utils` / `annotation` | 统一响应、全局异常、通用工具、注解 | — |

> ⚠️ **移包的隐藏约束**：MyBatis XML 里的 type alias 用的是**全限定名字符串**，
> 不参与编译（`mvn compile` 通过），也不被 `search_codebase` 索引。
> 移动 `@TableField(typeHandler=…)` 对应的类时，必须同步改
> `resources/mapper/**.xml`，否则运行期报 `Could not resolve type alias`。

**为何不用技术分层**：`controller/service/mapper` 横切切开会打散 11 个内聚的业务域，
一个「菜品」需求要横跨 5 个顶层包才能读完；且无法表达「哪些域之间不许互相调用」。
域内仍保持五层一致：`controller`（+`controller/admin`）/ `service`(+`impl`) / `mapper` / `entity` / `dto`。

## 3. 依赖规则（**由 ArchTests 在 `mvn test` 强制**）

| # | 规则 | 理由 |
|---|---|---|
| 1 | 某域 `mapper` / `entity` 仅本域可访问 | 跨域必须走 Service 契约或事件；表结构不外泄 |
| 2 | 某域 `service.impl` 仅本域可访问 | 依赖倒置：跨域只依赖**接口** |
| 3 | `common` 零业务域依赖 | 通用件不拖入业务实现；业务常量/领域判定归属主域 |
| 4 | `wechat` 零业务域依赖 | 平台集成是被三方共用的**最底层**，必须保持叶子，否则与 auth 形成包级环 |
| 5 | `controller` 不直连 `mapper` | 绕过 Service 的业务口径与事务边界 |

规则实现在 `server/src/test/java/com/bjtufood/ArchTests.java`，6 个用例。

**已知豁免**：`auth.support`（`SecurityUtil`/`JwtUtil`/`AuthStateUtil`）**允许**被
`correction`/`feedback`/`notification`/`review` 直接引用。它们是**无状态只读静态门面**
（当前登录人 / 令牌解析 / 认证态判据），不碰库、不含编排；auth 的**有状态能力**
（用户查询、归属迁移、注销）已全部要求经 `UserService` 契约访问并受规则 1 保护。
理由与触发条件（若 `support` 长出有状态 Bean 则须改接口并补规则）写在 `ArchTests` 类注释里。

## 4. 跨域协作的两种方式

### 4.1 同步读：Service 只读契约
跨域取数据一律经对方 Service 的**专用只读方法**（按 id 批量取投影），
返回**只读 DTO**（如 `UserBriefVO`/`StallBriefVO`），**不返回实体**。
典型：`UserService.mapBriefByIds`、`DishService.mapNameByIds`、`StallService.listBriefCandidates`。

### 4.2 跨域写：领域事件（auth 为唯一发布方）
写侧**不允许**跨域直连 Mapper。auth 在事务内发布事件，各域自行清理自己的表：

| 事件 | 发布方 | 订阅方 |
|---|---|---|
| `UserOwnershipMigratedEvent` | auth（账号归属迁移） | review（先清冲突再改 user_id）、feedback、notification |
| `UserAccountClosedEvent` | auth（账号注销） | notification |
| `DishDeletedEvent` | dish（菜品删除） | review（级联清理评价） |
| `ReviewSubmittedEvent` | review（评价提交） | dish（异步重算评分） |

事件一律放**发布方**包内。订阅方在 `event/` 下建 Listener。

> ⚠️ **已知包级环（2026-09-28 复核）**：`beFreeOfCycles()` 仍不可启用，ArchUnit 1.3 无法对其做有效豁免。
>
> | 环 | 性质 | 处置 |
> |---|---|---|
> | `dish → review → dish` | **发布/订阅**：`RatingUpdateListener` 订阅 `ReviewSubmittedEvent` 重算评分，语义单向（review 毫不知情），无技术债 | **保留**。仅因监听器签名引用了 review 的事件类型而成包级环 |
> | ~~`canteen → review → dish → canteen`~~ | **真实技术债**：canteen（菜品属性字典）反向依赖 review 拉取派生展示值 | **已偿还**：均分上移至 `CanteenAdminController#fillAvgRatings` 编排，由 `ArchTests#canteenBusinessLayers_mustNotDependOnReview` 锁死 |
>
> 不把事件类挪到 `common` 来「骗过」检测——那会违背上段「事件归发布方包」的约定，属于为工具而扭曲设计。
> 待 ArchUnit 支持对 `beFreeOfCycles()` 生效的豁免后，即可直接加回并豁免第 1 条。

**同步 vs 异步**：归属迁移/注销的监听器用同步 `@EventListener`（须与发布方事务同进同退）；
评分重算用 `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`（聚合失败只告警，不阻断 UGC 主链路）。

## 5. 事务边界

- 写操作 Service 方法加 `@Transactional(rollbackFor = Exception.class)`。
- **跨域写必须整体在一个事务里**：事件用同步 `@EventListener`（非 `AFTER_COMMIT`），
  监听器失败即整体回滚——这是「归属迁移要么全成、要么全不成」的保证。
- 通知投递是**例外**：`@Async` + `REQUIRES_NEW` 独立事务，回执不拖累主流程。

## 6. 配置管理

**统一用 `@ConfigurationProperties` 类型化绑定**（`@EnableConfigurationProperties` 显式登记于启动类），
**禁止 `@Value` 散读配置**。理由：`wechat.appid`/`wechat.secret` 曾被两个类各绑一次，
「是否已配置」判据也分裂成两处独立实现；且类型化后单测可直接构造配置对象，
不必反射改被测类的私有字段。

配置类**随所属域走**，不集中塞进 `common`（否则会违反规则 3）：
`wechat.config.WechatProperties`、`auth.config.JwtProperties`、`auth.config.AdminProperties`。

> 新增配置类需在**启动类**与**相关测试上下文**同步登记（显式注册的代价）。
> 本项目**不用** `@ConfigurationPropertiesScan`：切片测试用 `@ContextConfiguration` 取代主配置，
> 扫描式注册对其无效。

**密钥红线**：一切凭据由环境变量注入，仓库不存明文。`JwtUtil` 启动期 fail-fast 拒绝弱密钥/仓库默认密钥；
`AdminTokenFilter` 未配置口令时 **fail-closed 拒绝全部 `/admin`**。

## 7. 接口与路径

- 统一前缀 `server.servlet.context-path = /api/v1`（版本段，破坏性变更时新增 v2 并保留 v1 过渡期）。
  端点注解**不含版本段**——升版只改这一行配置。
- `SecurityConfig` 白名单与 `AdminTokenFilter` 作用域判定**均按「应用内路径」**（已剥离 context-path），
  故升版无需改动。
- 统一响应 `Result<T>{code,message,data}` / 分页 `PageResult<T>`；错误码仅
  `200/400/401/403/4001/4031/500`（`4001`=资源不存在、`4031`=邮箱未认证，二者为细分码）；
  `GlobalExceptionHandler` 兜底，Controller 不得裸抛。

## 8. 可观测性

- **Actuator**（prod 暴露面收敛为 `health`/`info`，路径 `/api/v1/actuator/health`，
  并在 `SecurityConfig` 白名单放行以支持探针）。
- **日志**：`logback-spring.xml` 按 profile 分环境；prod 异步写文件（按天+100MB 滚动，保留 30 天/上限 10GB）。
- **traceId**：`RequestTraceIdFilter`（`HIGHEST_PRECEDENCE+10`）为每个请求分配 `traceId`
  写入 MDC 并回写 `X-Trace-Id` 响应头，使一次请求内跨域调用日志可串联。
  外部传入的 `X-Trace-Id` 仅在匹配 `[A-Za-z0-9_-]{1,64}` 时复用（防日志伪造）。

## 9. 测试策略

| 层 | 手段 | 说明 |
|---|---|---|
| 架构 | `ArchTests`（ArchUnit） | 依赖规则，违规即构建失败 |
| 接口 | `SmokeApiTest`（`@WebMvcTest` 切片） | 六链路契约；显式 `@Import` 被测 Bean，零 DB 依赖 |
| 单测 | Mockito 打桩 | Service 边界：值域校验、权限判据、跨域契约调用 |
| 上下文 | `BjtuFoodApplicationTests` | 装配完整性（Bean 缺失/循环依赖即红） |
| 启动 | `mvn -o -B clean test` | 离线可跑，CI 基线 |

> 架构护栏的价值已被验证：2026-09-28 曾用 ArchUnit 环检测检出 2 个真实包级环
> （详见第 10 节）。因豁免机制对环检测无效，该规则已撤下并留档待偿还。

## 10. 已知技术债（TODO）

| # | 债务 | 影响 | 偿还路径 |
|---|---|---|---|
| P1 | `dish → review` 因事件订阅成包级环 | 包级环编译期不报错、只在运行时爆炸 | 语义单向，属可接受的发布/订阅形态；待 ArchUnit 支持对 `beFreeOfCycles()` 生效的豁免后，随环检测规则一并恢复 |
| P2 | 12 个 Service 实现中 10 个零单测 | 事务边界/权限判据等易错逻辑无回归保护 | 逐个补 Mockito 单测 |
| P2 | `WechatAccessTokenProvider` 失败文案沿用「内容安全检测服务」措辞 | 该类同时服务 upload，上传失败场景措辞不贴切 | 统一为「微信服务」口径（会变更 API 返回文本，需评估） |
| P2 | `web/`（管理后台）计划整体重构 | 现有 `api/http.ts` 5s 固定超时、无请求取消、`X-Admin-Token` 硬编码在 env 明文 | 重构时统一处理：超时可配置、AbortController 透传、口令改走登录态 |

> **已偿还（2026-09-29 架构评审 + 内容审核收口）**：
> - ~~**菜品纠错链路无微信机审**~~ —— `CorrectionServiceImpl#submit` 已接入 `msgSecCheck v2`：
>   把 `name` / `canteenName` / `stallName` / `attributes` 四类**用户自由文本**合并为
>   **单次**调用送检（`msgSecCheck` 按调用计费，逐字段送检会放大 4 倍额度），
>   `scene=2`；纯 `price` / `images` 改动跳过送检以省额度。
>   由 `CorrectionModerationTest`（10 用例）锁定「合并一次 / 四字段全覆盖 / risky 拦截不落库 / 纯结构化跳过」。
> - ~~**本词库形同虚设**~~ —— `sensitive_words.txt` 由 6 条补至 160 条，
>   且 `LocalSensitiveFilter#init()` 改为 **fail-fast**：词库缺失或解析出 0 条即拒绝启动，
>   杜绝「兜底静默失效」。`LocalSensitiveFilterTest` 锁定「≥100 条有效词条」。
> - ~~`client/`、`web/` 的 API base 仍为 `/api`~~ —— 三端 7 处已统一为 `/api/v1`，
>   并新增 `ApiVersionPrefixTest` 在 `mvn test` 阶段强制（后端升版时该测试会红，强制同步端上）。
> - ~~根目录 `.env.example` 与 `server/.env.example` 双份冲突~~ —— 过时的那份已删除。
> - ~~`canteen → review → dish → canteen` 三方包级环~~ —— 已于 2026-09-28 由
>   `CanteenAdminController#fillAvgRatings` 编排偿还，并由定向护栏锁死。

> **审核覆盖现状（2026-09-29 复核）**：四条 UGC 写链路的**文本**均已过微信 `msgSecCheck v2`
> （评价 / 反馈 / 昵称 / **菜品纠错**），**图片**均已过 `imgSecCheck`（`/upload/cloud-image` 上传时统一执行）。
> 唯一残留边界是「**取不到 openid 时跳过机审**」（`msgSecCheck v2` 的 openid 必填），
> 触发条件为登录态缺失——微信静默登录失败、非微信端 H5 联调。
> 该场景下由本地 `LocalSensitiveFilter` 词库兜底；**因小程序端强制静默登录，生产环境触发概率极低**，
> 且产品已按既有口径备案，故不单列为债务。

## 11. 变更约束

- 涉及表结构变更**只改 `db/schema.sql` / `seed_data.sql`**，禁直连 ALTER。
- 改跨域依赖前先想：是该加只读契约、还是发领域事件？**不要**新增跨域 Mapper 引用。
- 破坏性接口变更需评估端上同步（当前端上与后端独立排期）。

