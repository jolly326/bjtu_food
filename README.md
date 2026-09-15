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
├── server/                   # Spring Boot 后端
│   ├── src/main/java/com/bjtufood/
│   │   ├── auth/              # 认证授权（JWT + Spring Security + 微信登录/邮箱认证）
│   │   ├── canteen/           # 食堂/档口
│   │   ├── dish/              # 菜品（评分/推荐/热度）
│   │   ├── review/            # 评价（含「有用」标记）
│   │   ├── content/           # 内容审核（菜品/档口/食堂）
│   │   ├── feedback/          # 用户反馈
│   │   ├── notify/            # 消息通知
│   │   ├── upload/            # 文件上传
│   │   └── common/            # 公共模块（配置/异常/响应/工具）
│   ├── uploads/               # 上传图片存储
│   └── sensitive_words.txt    # 敏感词库
├── client/                  # 微信小程序端（uni-app）
└── web/                       # 管理后台端（Vue 3 + Element Plus）
```

---

## 核心功能

| 模块 | 说明 | 状态 |
|------|------|:----:|
| 微信登录与邮箱认证 | 微信静默登录、游客自动建号；「我的」页未认证用户卡点击唤起底部认证弹窗，学号邮箱认证（verified）解锁写评价 / 点赞等 UGC 写操作 | ✅ |
| 食堂档口 | 食堂列表（定位距离排序）、档口展示 | ✅ |
| 菜品浏览 | 搜索、个性化推荐（猜你喜欢/热门）、新品/促销、详情、热门排行 | ✅ |
| 就餐评价 | 写评价（星级+文字+配图 ≤3 张）、列表、过滤、「有用」标记 | ✅ |
| 内容审核 | 学生提交评价等 UGC 后台审核流（菜品由管理员录入，学生发布菜品已下线）；全部 UGC（文本+配图）过微信内容安检（msgSecCheck/imgSecCheck），`review` 态进后台复核队列 | ✅ |
| 意见反馈 | 反馈表单（意见/推荐菜品/纠错/举报，支持配图 ≤3 张）、安检后展示、处理回执 | ✅ |
| 消息通知 | 菜品审核通知 + 未读红点 | ✅ |
| 管理后台 | 信息管理 / 内容审核 / 评价安检复核队列（放行/驳回） / 用户与系统 / 数据看板 | ✅ |

---

## 数据库（11 张表）

| 表 | 说明 |
|----|------|
| user | 用户（含微信 openid、认证 verified 字段） |
| email_verification_code | 邮箱验证码 |
| canteen / stall / dish | 食堂 / 档口 / 菜品（菜品含折扣、辣度、风味（菜系）等扩展字段；食堂/档口为菜品属性） |
| review / review_useful | 评价（含配图 images ≤3 张、安检态 sec_state）/ 评价「有用」标记 |
| notification | 消息通知 |
| user_feedback | 用户反馈（含举报/纠错/推荐菜品；配图 images ≤3 张、安检态 sec_state） |
| view_log | 浏览足迹（个性化推荐用） |
| operation_log | 后台操作日志 |

> 原 `broadcast`（广播条）与 `activity`（最新活动）两表已于 2026-09-13 随活动/公告全链路下线删除（基线 14 → 12）。

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
- `WECHAT_APPID / WECHAT_SECRET`：微信小程序凭证（微信登录与内容安检 stable_token 必填）
- `COS_BUCKET / COS_SECRET_ID / COS_SECRET_KEY / COS_REGION`：腾讯云 COS 对象存储（**UGC 评价/反馈配图永久存储必填**，2026-09-13 起启用）
- `SPRING_MAIL_USERNAME / SPRING_MAIL_PASSWORD`：163 邮箱 + 授权码（邮箱认证必填）
- `CORS_ALLOWED_ORIGINS`：管理后台域名白名单
- `APP_PUBLIC_BASE_URL`：图片绝对地址前缀（默认 `http://localhost:8080/api`）

> 云托管部署时直接在云托管环境变量中配置同名变量，无需 `.env` 文件。

### 启动步骤

```bash
# 1. 启动后端
cd server && mvn spring-boot:run
# → 访问 http://localhost:8080/api/swagger-ui/index.html（接口文档）

# 2. 启动管理后台
cd web && pnpm install && pnpm dev
# → 访问 http://localhost:5173

# 3. 启动小程序
cd client && npm install && npm run dev:mp-weixin
# → 微信开发者工具导入 client/dist/dev/mp-weixin
```

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
| [docs/project_spec.md](docs/project_spec.md) | **技术规范基线**：技术栈 / 目录 / 跨端边界 / 不可违背的设计与实现红线 |
| [docs/database.md](docs/database.md) | 数据库设计（11 张表 + ER 图，与 schema.sql 一致） |
| [docs/api-design.md](docs/api-design.md) | 功能/接口设计总览：全部接口契约、认证模型、错误码、分页约定 |
| [docs/ui-design.md](docs/ui-design.md) | UI 设计规范：设计 Token、深色模式、15 页页面地图、组件与一致性红线 |
| [docs/architecture.md](docs/architecture.md) | 架构设计 / 部署说明（微信云托管）/ 本地快速上手 / 前端状态管理 |
| [docs/testing.md](docs/testing.md) | 接口测试文档：自动化可测项 + 需人工验证项清单与步骤 |
| [docs/WORKFLOW.md](docs/WORKFLOW.md) | 开发协作流程：需求 → 定样 → 拆任务 → 开发 → 质量门禁 → 经验回流 |
| server/、client/、web/ | 后端（Spring Boot）、微信小程序（uni-app）、Web 管理后台（Vue3+Element Plus）源码 |
