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
| 管理后台 | `web/` | Vue3 + Vite + TS + Element Plus | 仅 ADMIN（登录首屏 `/dashboard` 工作台，非 ECharts 看板） |

### 1.2 后端分层（包结构）
```
com.bjtufood/
├── auth/        # 认证：微信登录/邮箱认证/JWT/Security（昵称变更过 msgSecCheck scene=1）
├── dish/        # 菜品：列表/详情/浏览埋点/评分聚合/统计（学生端写接口 POST·PUT·DELETE /dishes 已于 2026-09-13 全量下线，录入归 /admin/dishes）
├── review/      # 评价 + 评分聚合事件（提交过 msgSecCheck scene=2；images/sec_state，见 §2.5）
├── canteen/     # 食堂/档口
├── content/     # category 品类
├── feedback/    # 用户反馈（提交过 msgSecCheck scene=2；images/sec_state，见 §2.5）
├── notify/      # 消息通知
├── history/     # 浏览足迹（view_log）
├── upload/      # 图片上传：multipart 头像/菜品图 + UGC 配图（云存储中转 → imgSecCheck → COS 转存，见 §2.5）
└── common/      # Result/异常/工具/JWT 切面/操作日志；common.security.ContentSecurityService（msgSecCheck/imgSecCheck/stable_token 缓存）
```

## 2. 认证与安全模型

### 2.1 微信登录（游客态）
- `POST /auth/wechat-login`：`code` → 微信 code2Session → openid 唯一取号
- 新 openid 自动建号（`username=wx_+openid尾16位`，`verified=0`）
- token 长期有效（不设超时），注销/禁用走 `TokenBlacklist`

### 2.2 邮箱认证（解锁写操作）
- `POST /auth/email-code` → 发 `@bjtu.edu.cn` 验证码（60s 限频、6 位、10 分钟有效）
- `POST /auth/verify-email` → 校验验证码、绑定邮箱、`verified→1`
- 写操作接口用 `@RequireVerified` 切面（未认证抛 `4031`）

### 2.3 角色与权限
- `student` / `admin` / `super_admin`（管理管理员）
- Security：URL 白名单 + `/admin/**` 规则 + 方法级 `@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")` 纵深防御

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
- 需 ADMIN 角色登录（后端 `DataInitializer` 仅在 dev profile 下创建 admin，且不打印口令）

## 5. 前端状态管理（Pinia store）

| Store | 职责 |
|---|---|
| `user` | 登录态、token、profile；`forceLogout` 联动重置各 store |
| `dish` | 菜品列表/详情/筛选/猜你喜欢/评价；竞态守卫（filterFetchSeq） |
| `theme` | 深色模式（手动/跟随系统） |
| `location` | 定位/距离计算 |
| `notify` | 未读红点（`reset` 供登出联动） |
| `review` | 评价状态 |

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
- 验证码 IP 维度限频待补
- `<PressCard>` 按压组件待抽取
- `NotificationController` 直调 Mapper（分层红线，建议下沉 Service）
- ~~4031 非标码需 spec 豁免登记~~（已在 spec §3 登记豁免）
- ~~通知接口 verified 口径待统一~~（`@RequireVerified` 已补齐）
