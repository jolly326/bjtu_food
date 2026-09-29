# 知行食记

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![Java](https://img.shields.io/badge/Java-21-orange)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)
![uni-app](https://img.shields.io/badge/uni--app-Vue3-4FC08D)
![Vue3](https://img.shields.io/badge/Vue-3-42b883)
![TypeScript](https://img.shields.io/badge/TypeScript-5-blue)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

**校园美食发现与分享圈** — 帮助交大学生解决"每天不知道吃什么"的难题。

**产品定位（2026-09-13 定稿）**：**校园菜品信息展示与检索平台**——信息展示 + 搜索 + 认证评价 + 反馈驱动的**轻 UGC 信息共建**，不做重社区。

学生（平鉴官）浏览菜品、评价（可配图 ≤3 张）、提交反馈（含新增菜品需求，可配图 ≤3 张）→ 管理员据反馈与后台录入维护菜品信息 → 学生浏览、评价、分享，形成"**发现 → 决策 → 分享**"闭环。**学生端无菜品写接口**（菜品由管理员录入）；评价/反馈的文本与配图均过微信内容安检后展示。

> 定位为内容信息公示与美食点评圈，不涉及点单、支付、配送等外卖功能。

---

## 技术栈

| 后端 | 前端（小程序） | 前端（管理后台） |
|------|---------------|-----------------|
| Spring Boot 3.2 + Java 21 | uni-app (Vue 3 + TS) | Vue 3 + TypeScript |
| MySQL 8.0 + MyBatis-Plus | Vite + Pinia | Vite + Pinia + Vue Router |
| Spring Security + JWT | | ECharts + Element Plus |
| SpringDoc OpenAPI (Swagger UI) API 文档 | | |

---

## 用户角色

| 角色 | 说明 | 访问端 |
|------|------|--------|
| **游客** | 浏览菜品、评价、搜索，触发互动时提示登录 | 微信小程序 |
| **学生（兼平鉴官）** | 浏览、评价、提交反馈（含新增菜品/纠错/推荐等需求）、分享；**无菜品写能力** | 微信小程序 |
| **系统管理员（admin / 后勤）** | 食堂/档口/菜品后台 CRUD 与上下架、内容审核、评价审核、数据面板 | Web管理端完整版 |

> 注：无独立的"档口老板"身份；菜品由管理员后台录入（学生端无菜品写接口，学生需求经反馈 `add` 类型转交），档口/食堂信息后台拥有最终审核与控制权。

---

## 项目结构

```
bjtu_food/
├── server/                   # Spring Boot 后端（模块化单体，按业务域分包）
│   ├── src/main/java/com/bjtufood/
│   │   ├── auth/              # 认证授权（JWT + Spring Security + 微信登录/邮箱认证）
│   │   ├── canteen/           # 食堂/档口
│   │   ├── dish/              # 菜品（评分/推荐/搜索 + 描述属性字典/删除级联）
│   │   ├── review/            # 评价（含「有用」标记）
│   │   ├── moderation/        # UGC 内容审核（微信 msgSecCheck/imgSecCheck + 本地 DFA 词库）
│   │   ├── wechat/            # 微信平台集成（jscode2Session + stable_token，叶子域）
│   │   ├── banner/            # 首页轮播图
│   │   ├── feedback/          # 用户反馈（含举报）
│   │   ├── correction/        # 菜品信息纠错
│   │   ├── notification/      # 站内通知
│   │   ├── upload/            # 文件上传（云存储 + 本地两条链路）
│   │   └── common/            # 跨域共享层（零业务依赖）
│   ├── src/main/resources/
│   │   ├── db/                # schema.sql / seed_data.sql（建库建表唯一真源）
│   │   ├── mapper/<域>/       # MyBatis XML，与 Java 侧分包对称
│   │   └── sensitive_words.txt # 本地敏感词库（须置于 resources 根下才进 classpath）
│   ├── uploads/               # 上传图片本地存储（运行时自动创建，不入库）
│   └── .env.example           # 后端环境变量模板（唯一一份）
├── client/                  # 微信小程序端（uni-app）
├── web/                     # 管理后台端（Vue 3 + Element Plus）
└── docs/                    # 架构与契约文档（architecture.md 为后端架构真源）
```

---

## 核心功能

> 功能与接口契约的**唯一真源** = `docs/feature/`（16 份功能文档）；下表仅为速览。

| 模块 | 说明 | 状态 |
|------|------|:----:|
| 微信静默登录与游客态 | 打开即静默建号，默认**游客态**（无登录页、无登录按钮、无账号密码体系） | ✅ |
| 邮箱认证 | 学号邮箱验证码认证；**认证判据 = `bindEmail` 非空**，解锁 UGC 写操作（未认证返回 `4031`） | ✅ |
| 首页菜品浏览 | 轮播位 + 搜索入口 + 筛选视图标签栏（`GET /dishes/views`）+ 瀑布流，热度排序 | ✅ |
| 搜索 | 关键词检索；「猜你喜欢」每次随机抽在售菜品名（`GET /dishes/for-you`） | ✅ |
| 菜品详情 | 信息卡、动态描述属性（**值即中文**）、评价区、浏览计数 | ✅ |
| 评价（UGC） | 写 / 改 / 删本人评价（星级 + 文字 + 配图 ≤3 张）；评价不含「有用」语义 | ✅ |
| 菜品信息纠错 | 详情页底栏「反馈错误」入口，提交后由管理员采纳 / 拒绝 | ✅ |
| 意见反馈 | 反馈表单（意见 / 其他，配图 ≤3 张）+ 处理回执（走站内通知） | ✅ |
| 系统通知 | 站内通知中心（列表 / 未读数 / 全部已读）；**不做任何推送** | ✅ |
| 内容安全 | 全部 UGC（文本 + 配图）过微信内容安检，**仅 `risky` 拦截**（无人工复核队列） | ✅ |
| 个人资料 / 注销账号 | 昵称与头像编辑；账号注销 | ✅ |
| 隐私政策与用户协议 | 端内静态页（随包发布，无接口） | ✅ |
| 管理后台 | 食堂 / 档口 / 菜品录入与上下架、评价管理、反馈与纠错处理、用户管理、数据看板 | ✅ |

---

## 数据库（11 张表）

> 表清单**唯一真源** = `server/src/main/resources/db/schema.sql`（本节仅作速览，随演进可能滞后，以脚本为准）。

| 表 | 说明 |
|----|------|
| user | 用户（含微信 openid；认证态由 `bind_email` 是否非空派生，**无独立 `verified` 列**） |
| email_verification_code | 邮箱验证码 |
| canteen / stall | 食堂 / 档口（含楼层 / 窗口号 / 位置） |
| dish | 菜品（`price` 现价已含折扣、`original_price` 原价；`view_count` 为热度排序输入；描述属性存 `attributes` JSON，**值即中文**） |
| dish_attribute_dimension | 菜品描述属性维度（饮食属性 / 食材 / 口味 / 冷热；**无取值字典表**） |
| review | 评价（配图 `images` ≤3 张） |
| notification | 站内通知 |
| user_feedback | 用户反馈（意见 / 举报；配图 `images` ≤3 张） |
| dish_correction | 菜品信息纠错 |
| banner | 首页轮播图 |

> 已下线并删除的表：`broadcast` / `activity`（2026-09-13）、`category`（2026-09-15）、`review_useful`（2026-09-27 评价「有用」整链下线）、`dish_attribute_value`（改为「值即中文」后退役）。逐条见 `docs/feature/README.md`「已下线能力（防回退）」。

建表与种子脚本见 `server/src/main/resources/db/schema.sql`（建表）与 `server/src/main/resources/db/seed_data.sql`（种子数据），均自包含建库选库，可直接执行。

---

## 快速开始

### 环境要求
JDK 21+、Maven 3.8+、MySQL 8.0+、Node.js 18+、pnpm、微信开发者工具

### 数据库初始化

```bash
# 先建表（自包含建库选库，无需预先 CREATE DATABASE）
mysql -u root -p < server/src/main/resources/db/schema.sql
# 再灌种子数据（演示用户/菜品/评价/通知等）
mysql -u root -p < server/src/main/resources/db/seed_data.sql
```

### 环境变量（本地 .env）

复制 `server/.env.example` 为 `server/.env` 并填写（`spring-dotenv` 依赖会自动加载，优先级：系统环境变量 > `.env` > 默认值）：

- `SPRING_DATASOURCE_URL / USERNAME / PASSWORD`：数据库连接
- `JWT_SECRET`：JWT 密钥（**≥32 字节**，否则后端启动 fail-fast 报错）
- `ADMIN_TOKEN`：管理端口令（与 `web/.env.local` 的 `VITE_ADMIN_TOKEN` 一致；未配置则全部 `/admin` 返回 403）
- `WECHAT_APPID / WECHAT_SECRET`：微信小程序凭证（微信登录与内容安检 stable_token 必填）
- `COS_BUCKET / COS_SECRET_ID / COS_SECRET_KEY / COS_REGION`：腾讯云 COS 对象存储（**UGC 评价/反馈配图永久存储必填**，2026-09-13 起启用）
- `SPRING_MAIL_USERNAME / SPRING_MAIL_PASSWORD`：163 邮箱 + 授权码（邮箱认证必填）
- `CORS_ALLOWED_ORIGINS`：管理后台域名白名单
- `APP_PUBLIC_BASE_URL`：图片绝对地址前缀（默认 `http://localhost:8080/api/v1`，**须与接口版本前缀一致**）

> ⚠️ **API base 的版本段**：后端 `server.servlet.context-path = /api/v1`，故三端配置
> （`client/.env.development`、`client/.env.production`、`web/.env.local`）与源码默认值
> （`client/src/api/config.ts`、`web/src/api/config.ts`）**都必须以 `/api/v1` 结尾**，
> 否则端上表现为「全站 404」且后端日志收不到该请求，极难自查。
> 后端升 v2 时需同步这几处；`ApiVersionPrefixTest` 会在 `mvn test` 阶段拦截遗漏。

> 云托管部署时直接在云托管环境变量中配置同名变量，无需 `.env` 文件。

### 启动步骤

```bash
# 1. 启动后端（dev profile：开启 SQL 日志与 Swagger；基础配置已按安全默认关闭接口文档）
cd server && mvn spring-boot:run -Dspring-boot.run.profiles=dev
# → 访问 http://localhost:8080/api/v1/swagger-ui/index.html（接口文档）

# 2. 启动管理后台
cd web && pnpm install && pnpm dev
# → 访问 http://localhost:5173

# 3. 启动小程序
cd client && npm install && npm run dev:mp-weixin
# → 微信开发者工具导入 client/dist/dev/mp-weixin
```

### 接口契约与端上类型（单一真源）

后端 VO 是接口契约的**唯一真源**；端上类型由它生成，**不手工维护**。

```bash
# 1. 启动后端（契约抓取需要服务在运行）
cd server && mvn spring-boot:run

# 2. 刷新契约并重新生成端上类型
cd client
npm install                 # 首次需装 openapi-typescript
npm run gen:api:fresh       # 抓 /api/v1/v3/api-docs → openapi.json → src/types/generated/api.d.ts

# 3. 质量门禁（提交前必跑）
npm run verify              # = check:contract（4 条判据）+ type-check（类型检查）
```

| 文件 | 角色 |
|------|------|
| `server/**/view/*.java` 等 VO | **契约真源**，字段变更即契约变更 |
| `client/openapi.json` | 契约镜像（生成产物，已入库） |
| `client/src/types/generated/api.d.ts` | 端上 TS 类型（生成产物，**勿手改**） |
| `client/src/api/shared.ts` | VO 的具名 re-export + 归一化工具 |

> **改了后端 VO 之后必须 `npm run gen:api:fresh`**，否则端上 `type-check` 仍会全绿
> （它只对着旧产物检查），直到真机才发现字段变空。`OpenApiContractSyncTest`
> 会在 `mvn test` 阶段拦截产物陈旧，`check:contract` 的 4 条判据会拦截
> `RawRow` 回潮与契约 VO 的重复手写。

> **契约的「上游」与「下游」**：契约类型描述的是**后端出参（可空）**，
> 直接当端上模型会让 UI 处处判空。正确做法是在 api 层的 `toXxx` 归一函数里
> 一次性兜底成端上必填模型（`|| ''` / `?? 0`）——这层适配有业务语义，不该省。

> **范围**：契约生成与门禁当前只覆盖 `client/`。`web/` 处于待重构状态，
> 计划重构时再接入同一份 `openapi.json`。

### 测试账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 学生 | `2024001`（~`2024004`） | `123456` |
| 管理员 | `admin` | `123456` |

> 学生账号需先在「我的」页顶部用户卡（未认证态点击）唤起底部认证弹窗完成学号邮箱认证后，才能写评价 / 评价点赞等 UGC 写操作（学生端无菜品写接口，菜品由管理员录入）；
> ⚠️ 测试账号仅限本地开发环境；生产环境部署后请立即改密。

---

## 文档

| 文档 | 说明 |
|------|------|
| [docs/feature/README.md](docs/feature/README.md) | **技术规范基线**：仓库红线 / 产品定型一页纸 / 协作纪律 / 跨端边界 —— 见该文档「项目约定与红线」段（原 `CODEBUDDY.md`、`docs/project_spec.md` 均已于 2026-09-27 删除，内容承接至此） |
| `db/` 初始化与种子脚本 | 数据库结构 **唯一真源**（原 `docs/database.md` 已删除，2026-09-27） |
| [docs/architecture.md](docs/architecture.md) | **后端架构说明**：分包模型、跨域依赖规则（ArchTests 强制）、事件机制、事务边界、配置与密钥、可观测性、测试策略、**已知技术债** |
| [docs/feature/](docs/feature/) | 功能与接口契约总览（含认证模型 / 错误码 / 分页约定）—— 入口 [README](docs/feature/README.md)（原 `docs/api-design.md` 已删除，2026-09-27） |
| [docs/ui/](docs/ui/) | 页面 UI 设计稿与跨页通用口径 —— 入口 [README](docs/ui/README.md)（含 **UI 修正完成度台账**）（原 `docs/ui-design.md` 已删除，2026-09-27） |
| server/、client/、web/ | 后端（Spring Boot）、微信小程序（uni-app）、Web 管理后台（Vue3+Element Plus）源码 |
