# 架构设计与快速上手指南（食在交大 bjtu_food）

> 本文档**以当前代码为准**，描述整体架构、技术栈、部署方式、前端状态管理与本地快速启动步骤。
> 供新成员快速上手。

## 1. 系统架构

```
┌─────────────┐      ┌──────────────────┐      ┌──────────────┐
│  小程序端    │      │   后端服务         │      │   MySQL 8    │
│ (client)    │ ───▶ │ (server, Spring) │ ───▶ │  bjtu_food   │
│ uni-app+Vue3│      │  context-path:/api│      └──────────────┘
└─────────────┘      │  JWT 认证          │
                     └──────────────────┘
┌─────────────┐             ▲
│  管理后台    │  ───────────┘
│ (web)       │  Vue3+Element Plus
└─────────────┘
```

### 1.1 端
| 端 | 目录 | 技术栈 | 说明 |
|---|---|---|---|
| 小程序 | `client/` | uni-app + Vue3 + TS + Pinia | 学生端（9 页，见 spec §2.1） |
| 后端 | `server/` | Spring Boot + Java + MyBatis-Plus + JWT | REST API（context-path=/api） |
| 管理后台 | `web/` | Vue3 + Vite + TS + Element Plus | 仅 ADMIN（**默认落地页 = 信息管理·菜品页 `/dashboard/content?tab=dish`**，2026-09-15 工作台下线后取代原 `/dashboard`；无全局聚合看板） |

### 1.2 后端分层（包结构）
```
com.bjtufood/
├── auth/        # 认证：微信登录/邮箱认证/JWT/Security（昵称变更过 msgSecCheck scene=1）
├── dish/        # 菜品：列表/详情/浏览埋点/评分聚合（学生端写接口 POST·PUT·DELETE /dishes 已于 2026-09-13 全量下线，录入归 /admin/dishes）
├── review/      # 评价 + 评分聚合事件（提交过 msgSecCheck scene=2；images/sec_state，见 §2.5）
├── canteen/     # 食堂/档口
├── feedback/    # 用户反馈（提交过 msgSecCheck scene=2；images/sec_state，见 §2.5）
├── notify/      # 消息通知
├── history/     # 浏览足迹（view_log）
├── upload/      # 图片上传：multipart 头像/菜品图 + UGC 配图（云存储中转 → imgSecCheck → COS 转存，见 §2.5）
└── common/      # Result/异常/JWT 切面/操作日志；**工具包唯一真源 = `common/utils`**（复数，2026-09-15 登记；原 `common/util` 单数包已合并废弃、勿再引用）；common.security.ContentSecurityService（msgSecCheck/imgSecCheck/stable_token 缓存）
```

> **2026-09-15 品类维度整链删除（spec §7.22 第 1 条，用户撤销原 Q-117「后台保留归类用途」口径）**：原 `com.bjtufood.content.category` 包（`Category` 实体 / `CategoryMapper` / `CategoryService` / `CategoryServiceImpl` / `controller/admin/CategoryAdminController`）已整体移除，故上文包树中 **`content/`（category 品类）域不再存在**（该空目录亦应一并清除，不留残留）；`dish` 域同步去掉品类字段与 `/admin/categories` 端点。**定型口径：菜品按食堂 / 档口归属，不存在分类维度。** 待收尾项（`db/*.sql` 品类残留、`OperationLogConst` 的 `category_*` 四值）见 `project_spec.md` §8「待收尾」。

## 2. 认证与安全模型

### 2.1 微信登录（游客态）
- `POST /auth/wechat-login`：`code` → 微信 code2Session → openid 唯一取号
- 新 openid 自动建号（`username=wx_+openid尾16位`，`verified=0`）
- token 有效期 **7 天**（`application.yml` `jwt.expiration=604800000ms`，2026-09-15 DOC-08 修订，原「长期有效（不设超时）」表述有误）；注销/禁用走 `TokenBlacklist`

### 2.2 邮箱认证（解锁写操作）
- `POST /auth/email-code` → 发 `@bjtu.edu.cn` 验证码（60s 限频、6 位、10 分钟有效）
- `POST /auth/verify-email` → 校验验证码、绑定邮箱、`verified→1`
- 写操作接口用 `@RequireVerified` 切面（未认证抛 `4031`）

### 2.3 角色与权限
- `user.role` 仅 `student` / `admin` **两层数据语义**（账号归属区分，不作权限分层；`super_admin` 已移除，spec §7.10 / §7.23）
- Security：URL 白名单 + JWT 过滤器 + `@RequireVerified` 切面（未认证 4031）；**`/admin/**` 不走角色**——由 `AdminTokenFilter` 校验请求头 `X-Admin-Token` == 环境变量 `ADMIN_TOKEN`（未配置 fail-closed 403，校验通过后置 `ROLE_ADMIN` 授权放行）；`POST /upload/image` 亦由该口令守卫（2026-09-15 B4）

### 2.4 安全加固（已落实）
- JWT 密钥从环境变量注入，启动 fail-fast 拒绝弱密钥
- 上传：扩展名白名单 + magic number + UUID 重命名 + 失败清理 + 缩略图白底
- 敏感信息：VO 不返回 openid；updateProfile 仅更新昵称/头像；selectList 投影必要列
- 分页上限统一 `PageUtil.normalize`

### 2.5 UGC 内容安检与配图存储链路（2026-09-13 拍板，契约见 spec §5.a / api-design.md §4）

- **`ContentSecurityService`**（`common.security`）：统一封装微信内容安检——文本 `msgSecCheck` v2（`openid` + `scene` + `version=2`；scene：昵称=1、评价/反馈=2；`suggest` 三态 pass/review/risky）、图片 `imgSecCheck`（违规 code `87014` 拦截）；access_token 统一走 **`stable_token`** 并缓存。review/feedback/auth 各业务模块只调该服务，**不得自建安检调用**。
- **安检态落库**：`suggest=review` → `review.sec_state` / `user_feedback.sec_state = 'review'`（对非作者不可见，管理后台 `PUT /admin/reviews/{id}/sec-state` 放行/驳回）；`risky` / `87014` → HTTP 400 拦截。
- **UGC 图片上传链路（云存储中转 → 送检 → COS 转存）**：

```
小程序                         后端                          微信/腾讯云
─────────                     ─────────                     ─────────
wx.cloud.uploadFile ──────▶ 微信云开发云存储（中转，免域名白名单）
        │
POST /upload/images ──────▶ UploadController
  { fileId }（单张，前端逐张调用）│ tcb batchdownloadfile 拉取原图
                              │ imgSecCheck 送检 ── 违规 87014 → 400
                              │   （单张失败该张 400，前端跳过不中断其余图片）
                              ▼
                            转存 COS（永久存储，images 列存 COS URL）
```

- **平台可迁移（面向未来）**：COS / 安检 / 上传接口均不绑定云托管，后端可整体迁移独立服务器；届时小程序上传域名改走备案域名白名单，链路结构不变。

### 2.6 限流 / 去重 / 吊销三组件职责边界（2026-09-15 登记，刻意不合并）

三个组件**名字相近但职责正交、互不替代**，评审与重构时**不得以「重复实现」为由合并、互调或删其一**：

| 组件 | 位置 | 职责 | 触发后果 |
|---|---|---|---|
| `IpRateLimiter` | `common/config/IpRateLimiter.java` | **请求节流**（按来源 IP 限频，如 `/auth/email-code` 同 IP 每分钟 ≤3 次、每小时 ≤10 次） | **阻断请求**，返回 `400` |
| `ViewRateLimiter` | `dish/config/ViewRateLimiter.java` | **幂等去重**（同一用户对同一菜品按自然日只计 1 次浏览，配 `view_log` upsert） | **不阻断请求**（请求正常成功，仅不重复计数 / 不重复写库） |
| `TokenBlacklist` | `auth/config/TokenBlacklist.java` | **JWT 吊销**（注销 / 禁用后使已签发 token 立即失效） | **鉴权失败**，返回 `401` |

> 判据：三者解决的是三个不同问题——「防刷」「计数幂等」「凭证失效」，其**输入维度、判定时机、失败语义**均不相同（`400` / 成功 / `401`）。此前多次被误判为「三套重复的限流实现」，本条为**职责边界的正式登记**。

## 3. 部署（微信云托管）

### 3.1 环境信息
| 项 | 值 |
|---|---|
| 云托管环境 | `prod-d7g2z0sge0919e273` |
| 服务名 | `bjtu-food` |
| 小程序访问 | `wx.cloud.callContainer`（`X-WX-SERVICE: bjtu-food`），`context-path=/api` |
| 数据库 | MySQL 8（`bjtu_food`） |

### 3.2 必需环境变量（云托管/生产）
| 变量 | 说明 |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | 数据库账号 |
| `SPRING_MAIL_USERNAME` / `PASSWORD` | 网易 163 SMTP 邮箱与授权码 |
| `JWT_SECRET` | ≥32 字节强随机密钥（**禁止默认值**） |
| `WECHAT_APPID` / `WECHAT_SECRET` | 微信小程序凭据（登录 + 安检 stable_token） |
| `COS_BUCKET` / `COS_SECRET_ID` / `COS_SECRET_KEY` / `COS_REGION` | 腾讯云 COS 对象存储（**UGC 配图永久存储**，2026-09-13 起必填；见 §2.5 链路） |
| `APP_PUBLIC_BASE_URL` | 图片完整 URL 前缀（头像 / 后台菜品图；UGC 配图为 COS URL 不经此前缀） |
| `CORS_ALLOWED_ORIGINS` | 管理后台浏览器源（白名单） |
| `ADMIN_TOKEN` | 管理端口令（`AdminTokenFilter` 校验请求头 `X-Admin-Token`；**未配置时 fail-closed 403**，web 侧 `VITE_ADMIN_TOKEN` 与之同值，2026-09-15 CF-01 补登记） |
| `WECHAT_CLOUD_ENV` | 微信云开发环境 ID（UGC 配图云存储 fileID 校验 / tcb 拉取用，2026-09-15 CF-01 补登记） |
| `UPLOAD_PATH` / `UPLOAD_URL_PREFIX` | 本地 multipart 上传目录（默认 `./uploads/images`）与图片访问前缀（默认 `/images`，web 管理端菜品图链路，2026-09-15 CF-01 补登记） |

> `spring-dotenv`：本地读 `server/.env`；云托管读同名环境变量。仓库不保留任何明文凭据。

### 3.3 排障注记：微信 `jscode2session` 响应为 `text/plain`
微信 `https://api.weixin.qq.com/sns/jscode2session` 实测以 **`HTTP 200 + Content-Type: text/plain`** 返回 JSON 体（而非 `application/json`）。因此后端**禁止**用 `restTemplate.getForObject(url, Map.class)`（或任何依赖 `MappingJackson2HttpMessageConverter` 自动转换的写法）——该方法按 Content-Type 选转换器，找不到可读 `text/plain → Map` 的转换器即抛 `RestClientException`，导致**真实 code 登录同样失败**。

正确做法：**先按 `String.class` 读取，再用 Jackson（`ObjectMapper`）手工反序列化**（或等价方式），使解析不依赖上游 Content-Type。锁定实现见 `server/src/main/java/com/bjtufood/auth/service/WechatService.java`（`code2Session` 内 `getForObject(url, String.class)` + `parseJsonBody`），回归用例见 `server/src/test/java/com/bjtufood/auth/WechatServiceTest.java`。

> 事故记录：**2026-09-13 曾因此缺陷导致全部微信登录返回 `400「微信登录服务异常，请稍后重试」`（P0，直接阻断登录闭环）**。修改本服务或替换 HTTP 客户端时，务必保留「不依赖 Content-Type 解析」这一约束。

## 4. 本地快速启动

### 4.1 后端（server）
```bash
cd server
# 1. 复制环境变量模板并填真实值
cp .env.example .env
# 2. 建库建表（MySQL 已启动）
mysql -u root -p < src/main/resources/db/schema.sql
# 可选：导入示例数据
mysql -u root -p bjtu_food < src/main/resources/db/seed_data.sql
# 3. 启动
mvn spring-boot:run
# 服务 http://localhost:8080/api
```

### 4.2 小程序（client）
```bash
cd client
npm install
npm run dev:mp-weixin   # 微信开发者工具导入 dist/dev/mp-weixin
# 或 npm run dev:h5
```
- 本地联调：`VITE_API_BASE_URL=http://127.0.0.1:8080/api`
- 真机预览：`VITE_API_BASE_URL=http://<局域网IP>:8080/api`

### 4.3 管理后台（web）
```bash
cd web
npm install
npm run dev   # http://localhost:5173
```
- 无登录体系：本地 `.env.local` 配 `VITE_ADMIN_TOKEN`（与后端环境变量 `ADMIN_TOKEN` 同值）即打开即用（`AdminTokenFilter` 校验请求头 `X-Admin-Token`，未配置 fail-closed 403）

### 4.4 前端目录与包管理器约定（2026-09-15 登记）

- **包管理器统一为 npm（唯一）**：仓库仅保留 `client/package-lock.json` 与 `web/package-lock.json` **两个锁文件**；**禁止引入 `yarn.lock` / `pnpm-lock.yaml` / `bun.lockb` 等任何其他锁文件**（多锁并存会导致依赖树漂移与 CI / 本地不一致）。安装与运行一律 `npm install` / `npm run *`，文档命令不得写成 `yarn` / `pnpm`。
- **Web 视图目录重组（`web/src/views/`）**：收敛为**四个目录**——`audit/`（`AuditManageView` / `FeedbackView` / `ReviewAuditView`）、`content/`（`ContentManageView` / `DishManageView` / `DishDetailView`；**2026-09-15 品类维度整链删除后 `CategoryManage` / `HomeConfigView` 已移除，`ContentManageView` 收敛为「菜品」单一视图**）、`system/`（`SystemManageView` / `UserView` / `OperationLogView` / `AccountView`）、`layout/`（`AdminLayout`）；原 **`admin/` / `canteen/` / `user/` 三目录已合并删除**（`git` 中体现为 `R` 重命名）。
- **路由未变（兼容承诺）**：`path` 与 `name` 均保持原值——`/dashboard/content`（`contentManage`）、`/dashboard/content/dishes/:dishId`（`dishDetail`）、`/dashboard/audit`（`auditManage`）、`/dashboard/system`（`systemManage`），故书签 / 深链不受目录重组影响。新增页面须按业务归属放入上述四目录，**不得再新建松散目录**。

## 5. 前端状态管理（Pinia store）

| Store | 职责 |
|---|---|
| `user` | 登录态、token、profile；`forceLogout` 联动重置各 store |
| `dish` | 菜品列表/详情/筛选/评价；竞态守卫（filterFetchSeq）；本地距离写回（withLocalDistance） |
| `location` | 定位/距离计算 |
| `notify` | 未读红点（`reset` 供登出联动） |
| `auth-sheet` | `AuthSheet` 认证弹层全局编排（打开/关闭、认证成功回调，2026-09-15 DOC-10 对齐实况） |
| `route` | 跨页路由辅助（2026-09-15 DOC-10 对齐实况） |

> 原表中的 `theme`（深色模式）与 `review` store 已不存在——项目无深色模式（spec §4.2 S4-04）、评价状态由页面编排承载（2026-09-15 DOC-10）。

### 5.1 登录态一致性
- `forceLogout` 会联动 `dishStore.resetUserScopedData` + `notifyStore.reset`，避免换用户串数据
- `http.ts` 401 触发静默登录重试；403/4031 分级提示；`handleUnauthorized` 并发去重

## 6. 关键设计决策
1. **评分聚合异步化**：`RatingUpdateListener` 用 `@Async("taskExecutor")` AFTER_COMMIT 重算，不阻塞提交
2. **浏览足迹去重 upsert**：`recordDishView` 存在则更新、不存在则插入，支撑猜你喜欢
3. **tags 精确匹配**：用 `FIND_IN_SET` 替代 `LIKE '%tag%'`，消除子串误匹配（tags 值域固定，未拆表）
4. **分页统一**：`PageUtil.normalize` 上限约束 + `IPage` 返回
5. **activity/broadcast 全链路下线（2026-09-13）**：后端 activity/ 模块与 content 下 broadcast 能力、`/activities`、`/broadcasts`、`/admin/activities`、`/admin/broadcasts` 接口、库表两表与小程序「最新活动」入口均已删除（原「activity 接入待开放」决策作废），恢复须重新拍板
6. **UGC 配图 + 微信内容安检（2026-09-13 拍板，QA 门禁契约校准）**：评价与反馈恢复配图（各 ≤3 张，`wx.compressImage` 压缩至最长边 ≤1334 且文件 ≤1MB）；全部 UGC（文本+图片）过微信内容安检（`ContentSecurityService`：msgSecCheck v2 scene 映射昵称=1/评价反馈=2、imgSecCheck；stable_token 缓存）；图片链路 = 云开发云存储中转 → `imgSecCheck` → COS 永久存储（新接口 `POST /upload/images` 为**单张契约** `{ fileId } → { url }`、前端逐张调用、单张失败跳过，multipart `/upload/image` 保留）；安检态 `sec_state`（**三态** pass/review/rejected，rejected=人工驳回，与 review 同对非作者不可见）、后台可放行（→pass）/驳回（→rejected）；链路不绑定云托管、可整体迁移独立服务器（届时上传域名走备案域名白名单）。此拍板推翻 2026-09「UGC 图片全量下线、无图片入口」的临时口径（spec §4.9 已登记演进说明）

## 7. 已知技术债（见 api-design.md §9）
- ~~验证码 IP 维度限频待补~~（已解决：`/auth/email-code` 已接入 `IpRateLimiter`，2026-09-15 DOC-10 收敛）
- `<PressCard>` 按压组件待抽取
- ~~`NotificationController` 直调 Mapper（分层红线，建议下沉 Service）~~（已解决：逻辑已下沉 `NotificationService`，2026-09-15 DOC-10 收敛）
- ~~4031 非标码需 spec 豁免登记~~（已在 spec §3 登记豁免）
- ~~通知接口 verified 口径待统一~~（`@RequireVerified` 已补齐）

### 7.1 测试资产登记（2026-09-15）

- `server/src/test/java/` 现有 **4 个**用例：`BjtuFoodApplicationTests`（`contextLoads` 冒烟，需数据库）、`auth/WechatServiceTest`（微信 `jscode2session` 返回 `text/plain` 的解析回归，见 §3.3）、`content/security/ContentSecurityServiceTest`（内容安检服务单测）、**`SmokeApiTest`（MockMvc 六链路接口冒烟：登录 / 菜品详情 / 评价写赞含 4031 / 反馈含 `sub` 严格 400 / 上传口令 403 / 管理端口令，16 用例 58 断言，`@WebMvcTest` 切片 + 打桩，不依赖数据库，`mvn -q -Dtest=SmokeApiTest test` 可离线运行）**。
- **更正过时描述**：不再存在「`mvn test` 仅含一个冒烟用例 / 无业务用例」的说法。
- 前端无单测脚本，质量靠类型检查 + lint + 真机 / 模拟器验证（**由用户执行；agent 不代跑真机验证**）。

### 7.2 seed 演示素材收敛（2026-09-15）

- `server/uploads/images/seed/` **仅保留 `dishes/tomato-egg.jpg`**（Swagger 示例引用所需：`DishAdminReq`、`DishAdminController`、`ProfileUpdateReq`、`AuthController` 的 `@Schema` example 均引用该路径）；其余 **12 件已删除**（`canteens/` 2 件、`dishes/` 9 件、`stalls/` 1 件）。
- `db/seed_data.sql` 不引用图片文件路径，故素材收敛**不影响建库 / 导种子**流程。
- 后续若新增演示图，须同步更新上述 Swagger example 与本节登记。
