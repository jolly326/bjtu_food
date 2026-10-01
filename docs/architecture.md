# 后端架构说明（server/）

> 本文是**后端架构的现行真源**。各域的详细职责 / 依赖方向以 `server/src/main/java/com/bjtufood/*/package-info.java` 为准。

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

## 1.1 两端接口边界（**由 `FrontendApiIsolationTest` 强制**）

仓库含**两个前端**，消费**同一个后端**但走**两套互不通的鉴权**：

| 端 | 路径前缀 | 鉴权方式 | 过滤器 |
|---|---|---|---|
| `client/`（微信小程序） | `/api/v1/**` | `Authorization: Bearer <JWT>` | `JwtAuthFilter` |
| `web/`（管理后台） | `/api/v1/admin/**` | `X-Admin-Token: <口令>` | `AdminTokenFilter` |

**采用业界惯例：安全区隔离，而非「给每个客户端复制一份端点」。**
GitHub / Stripe / 各类 SaaS 均为「一个后端 + 一套 API + 两种鉴权」；真正的隔离在
**承载写操作与敏感数据的 `/admin/**`**——可在一个地方统一施加 IP 白名单、限频、审计、CORS 策略。

**允许共享**：**非敏感的公开只读字典 / 枚举端点**。它们在 `SecurityConfig` 内是 `permitAll`，
数据本身公开，两端复用不产生安全暴露，也避免为 web 复制冗余出口：

| 数据 | 端点 | 性质 |
|---|---|---|
| 筛选视图字典 | `GET /dishes/views` | 公开只读，**两端共用** |
| 举报原因字典 | `GET /report-reasons` | 公开只读，**两端共用**（字典不是「反馈提交」的子资源，故独立于 `/feedback/**`） |
| 菜品 CRUD / 审核 / 用户状态 | `/admin/dishes/**` 等 | **管理端专属**（口令保护） |

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

`common` 内部按**关注点**再分一层：

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

规则实现在 `server/src/test/java/com/bjtufood/ArchTests.java`，7 个用例。

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

> ⚠️ **已知包级环**：`beFreeOfCycles()` 未启用，ArchUnit 1.3 无法对其做有效豁免。
>
> | 环 | 性质 | 处置 |
> |---|---|---|
> | `dish → review → dish` | **发布 / 订阅**：`RatingUpdateListener` 订阅 `ReviewSubmittedEvent` 重算评分，语义单向（review 毫不知情），无技术债 | **保留**。仅因监听器签名引用了 review 的事件类型而成包级环 |
>
> 不把事件类挪到 `common` 来「骗过」检测——那会违背上段「事件归发布方包」的约定，属于为工具而扭曲设计。
> 待 ArchUnit 支持对 `beFreeOfCycles()` 生效的豁免后启用环检测，并对本条环豁免。

**同步 vs 异步**：归属迁移 / 注销的监听器用同步 `@EventListener`（须与发布方事务同进同退）；
评分重算用 `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`（聚合失败只告警，不阻断 UGC 主链路）。

## 5. 事务边界

- 写操作 Service 方法加 `@Transactional(rollbackFor = Exception.class)`。
- **跨域写必须整体在一个事务里**：事件用同步 `@EventListener`（非 `AFTER_COMMIT`），
  监听器失败即整体回滚——这是「归属迁移要么全成、要么全不成」的保证。
- 通知投递是**例外**：`@Async` + `REQUIRES_NEW` 独立事务，回执不拖累主流程。

## 6. 配置管理

**统一用 `@ConfigurationProperties` 类型化绑定**（`@EnableConfigurationProperties` 显式登记于启动类），
**禁止 `@Value` 散读配置**。理由：类型化后「是否已配置」只有一处判据，且单测可直接构造配置对象，
不必反射改被测类的私有字段。

配置类**随所属域走**，不集中塞进 `common`（否则会违反规则 3）：
`wechat.config.WechatProperties`、`auth.config.JwtProperties`、`auth.config.AdminProperties`。

> 新增配置类需在**启动类**与**相关测试上下文**同步登记（显式注册的代价）。
> 本项目**不用** `@ConfigurationPropertiesScan`：切片测试用 `@ContextConfiguration` 取代主配置，
> 扫描式注册对其无效。

**密钥红线**：一切凭据由环境变量注入，仓库不存明文。`JwtUtil` 启动期 fail-fast 拒绝弱密钥 / 仓库默认密钥；
`AdminTokenFilter` 未配置口令时 **fail-closed 拒绝全部 `/admin`**。

## 7. 接口与路径

- 统一前缀 `server.servlet.context-path = /api/v1`（版本段，破坏性变更时新增 v2 并保留 v1 过渡期）。
  端点注解**不含版本段**——升版只改这一行配置。
- `SecurityConfig` 白名单与 `AdminTokenFilter` 作用域判定**均按「应用内路径」**（已剥离 context-path），
  故升版无需改动。
- 统一响应 `Result<T>{code,message,data}` / 分页 `PageResult<T>`；错误码仅
  `200/400/401/403/4001/4031/500`（`4001`=资源不存在、`4031`=邮箱未认证，二者为细分码）；
  `GlobalExceptionHandler` 兜底，Controller 不得裸抛。
- **响应压缩**：`server.compression` 已启用（JSON / 文本类，≥1KB 才压），显著降低移动端传输体积。

## 8. 可观测性

- **Actuator**（prod 暴露面收敛为 `health`/`info`，路径 `/api/v1/actuator/health`，
  并在 `SecurityConfig` 白名单放行以支持探针）。
- **日志**：`logback-spring.xml` 按 profile 分环境；prod 异步写文件（按天 + 100MB 滚动，保留 30 天 / 上限 10GB）。
- **traceId**：`RequestTraceIdFilter`（`HIGHEST_PRECEDENCE+10`）为每个请求分配 `traceId`
  写入 MDC 并回写 `X-Trace-Id` 响应头，使一次请求内跨域调用日志可串联。
  外部传入的 `X-Trace-Id` 仅在匹配 `[A-Za-z0-9_-]{1,64}` 时复用（防日志伪造）。

## 9. 测试策略

| 层 | 手段 | 说明 |
|---|---|---|
| 架构 | `ArchTests`（ArchUnit） | 依赖规则，违规即构建失败 |
| 契约 | `ApiVersionPrefixTest` / `FrontendApiIsolationTest` / `PageResultContractTest` | 版本前缀、前后端安全区隔离、分页壳只有 `records` |
| 契约 | `OpenApiContractSyncTest` | **端上生成产物是否仍跟得上 VO**（见 §9.1） |
| 接口 | `SmokeApiTest`（`@WebMvcTest` 切片） | 六链路契约；显式 `@Import` 被测 Bean，零 DB 依赖 |
| 单测 | Mockito 打桩 | Service 边界：值域校验、权限判据、跨域契约调用 |
| 上下文 | `BjtuFoodApplicationTests` | 装配完整性（Bean 缺失 / 循环依赖即红） |
| 启动 | `mvn -o -B clean test` | 离线可跑，CI 基线 |
| 端上 | `npm run verify`（client） | `check:contract` + `vue-tsc --noEmit`，提交前必跑 |

环检测（`beFreeOfCycles()`）因 ArchUnit 豁免机制对其无效而未启用；相关包级环见第 10 节 P1。

### 9.1 契约单一真源

**目标**：端上响应类型由后端 VO 生成，后端字段变更须在端上编译期暴露——杜绝「后端改字段、端上编译全绿、真机字段变空」。

**方案**：后端 VO 为真源 → SpringDoc 导出 `openapi.json` → `openapi-typescript`
生成端上 TS 类型 → 端上 `toXxx` 适配器只做**有业务语义的**转换
（分→元、字段别名、零值兜底），不承担「猜字段存在与否」的职责。

```
后端 VO ──(运行中服务 /api/v1/v3/api-docs)──> client/openapi.json
        ──(openapi-typescript)──> client/src/types/generated/api.d.ts
        ──(shared.ts 逐个具名 re-export)──> client/src/api/*.ts 的强类型入参
```

**生成类型本身也会过期**，故配了拦截：

| 漂移场景 | 后果 | 拦截者 |
|---|---|---|
| 后端改 VO，未重新生成 | 端上 `type-check` 全绿（它只对着旧产物检查），真机字段变空 | `OpenApiContractSyncTest`（`mvn test` 阶段） |
| 端上回退 `RawRow` 弱类型 | 契约保障出现缺口，且无声扩张 | 判据 3：**扫 `src/` 全树**（api / stores / composables / pages / types / utils） |
| 契约 VO 被重复手写 | 同一份契约出现第二个真源，必然漂移 | 判据 4：手写的 interface 若与契约**字段名 + 可空性全同**即失败 |
| re-export 名与契约脱节 | 悬空导出 / 掩盖字段改名 | 判据 2：re-export 名须在 `api.d.ts` 中有真实定义 |

> **判据 4 只拦「纯镜像」**：字段名或可空性有差异的是**合法的归一化展示模型**
> （如 `client` 的 `ReportReason`——契约字段全 `?`，端上在 api 层兜底成必填；
> `DishListItem` 更是把 `canteenName`→`canteen`、分→元），那层适配有业务语义，
> 拦了反而会逼人写 `as any`。

> **判据均以负向测试验收**（故意注入违规 → 确认为红）：护栏自身失效比没有护栏更危险。
> 生成文件是 **TS 语法**（块尾 `};`、字段 `f?: T;` 带分号），按 JSON 形态写的正则不会匹配且不报错。

> **范围**：`client/` + `server/`。`web/` 处于待重构状态，**刻意不施加护栏**——
> 对一个即将重写的目录做约束只会制造二次清理的噪音。

> **TS 4.9 约束**（client 当前 `typescript@4.9` + `vue-tsc@1.8`）：
> `type X = Y['K']` 之后**不可**用 `X.A` 点号访问（TS2713/TS2702），
> `interface X extends Y['K']` 亦不支持（TS2499：interface 只能继承标识符）。
> 故 `shared.ts` 采用**逐个具名 re-export**——这是 4.9 下的标准做法，非权宜之计。

## 10. 已知技术债（TODO）

| # | 债务 | 影响 | 偿还路径 |
|---|---|---|---|
| P1 | `dish → review` 因事件订阅成包级环 | 包级环编译期不报错、只在运行时爆炸 | 语义单向，属可接受的发布 / 订阅形态；待 ArchUnit 支持对 `beFreeOfCycles()` 生效的豁免后，随环检测规则一并恢复 |
| P2 | 12 个 Service 实现中 10 个零单测 | 事务边界 / 权限判据等易错逻辑无回归保护 | 逐个补 Mockito 单测 |
| P2 | `WechatAccessTokenProvider` 失败文案沿用「内容安全检测服务」措辞 | 该类同时服务 upload，上传失败场景措辞不贴切 | 统一为「微信服务」口径（会变更 API 返回文本，需评估） |
| P2 | **`web/`（管理后台）及其后端接口软冻结** | `web/` 计划整体重构；现有 `api/http.ts` 5s 固定超时、无请求取消、`X-Admin-Token` 硬编码在 env 明文；且未接入契约生成类型（仍是手写 `RawXxx`）。后端 `server/` 的 `/admin/**` 接口标注 `@Deprecated(forRemoval=true)` 软冻结（保留运行、待重构移除），详见 `docs/client/feature/README.md`「管理端冻结」段 | **已明确由用户后期自行重构**，本轮不介入；重构时可复用 `client/openapi.json` 生成产物 |
| P2 | `client/src/types/generated` 未覆盖全部 41 个端点 | 契约共 160 个 schema，端上已 re-export 16 个（dish/review/user/notify/banner/feedback/upload 主链路已强类型化）；管理端专属 VO（`DishAdminVO` / `ReviewAdminVO` 等）与**入参 DTO**（`DishCorrectionReq` / `ReviewReq` 等）仍为手写 | 入参侧优先（写错会直接 400）；逐模块补 re-export，`check:contract` 已阻止 `RawRow` 回潮 |
| P2 | `web/api/feedback.ts` 的 `ReportReason` 多声明了后端**不存在**的 `order: number`（后端 `record ReportReason(value,label)` 只有 2 字段，运行时该值恒 `undefined`）；`web/api/dish.ts#listMealTypes` 读 `raw.order ?? 0` 得到**恒 0 假值**，其 `.sort()` 退化为**恒返回 0 的比较器**（等于没排） | 字段恒空 / 排序失效 | 留待 web 重构时一并处理 |

**内容审核覆盖**：四条 UGC 写链路的**文本**均已过微信 `msgSecCheck v2`
（评价 / 反馈 / 昵称 / **菜品纠错**），**图片**均已过 `imgSecCheck`（`/upload/cloud-image` 上传时统一执行）。
菜品纠错把 `name` / `canteenName` / `stallName` / `attributes` 四类**用户自由文本**合并为**单次**调用送检
（`msgSecCheck` 按调用计费，逐字段送检会放大 4 倍额度），`scene=2`；纯 `price` / `images` 改动跳过送检以省额度，
由 `CorrectionModerationTest`（10 用例）锁定「合并一次 / 四字段全覆盖 / risky 拦截不落库 / 纯结构化跳过」。
本地词库 `sensitive_words.txt` 共 160 条，`LocalSensitiveFilter#init()` 为 **fail-fast**
（词库缺失或解析出 0 条即拒绝启动），由 `LocalSensitiveFilterTest` 锁定「≥100 条有效词条」。

唯一残留边界是「**取不到 openid 时跳过机审**」（`msgSecCheck v2` 的 openid 必填），
触发条件为登录态缺失——微信静默登录失败、非微信端 H5 联调。
该场景下由本地 `LocalSensitiveFilter` 词库兜底；**因小程序端强制静默登录，生产环境触发概率极低**，
且产品已按既有口径备案，故不单列为债务。

## 11. 变更约束

- 涉及表结构变更**直连远程库执行**（凭据取 `server/.env` 的 `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD`），
  并同步回写 `db/schema.sql` / `seed_data.sql`；破坏性操作（`DROP` / `TRUNCATE` / 大范围 `UPDATE`·`DELETE`）
  执行前须先告知对象与影响，并先用 `information_schema` 核对目标结构。
- 改跨域依赖前先想：是该加只读契约、还是发领域事件？**不要**新增跨域 Mapper 引用。
- 破坏性接口变更需评估端上同步（当前端上与后端独立排期）。
