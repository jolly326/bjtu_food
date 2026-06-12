# 校园食堂信息系统 - 食在交大

一套帮助北京交通大学学生解决"中午/晚上吃什么"选择困难问题的校园美食信息展示平台。

## 项目简介

本项目是一个校园食堂菜品展示与互动系统，包含**微信小程序端**和**浏览器管理后台端**，核心功能包括：

- **浏览菜品**：按食堂/档口浏览、关键词搜索、查看菜品详情与评价
- **互动评价**：学生对菜品进行 1-5 星评分和图文评价（带敏感词过滤）
- **收藏清单**：收藏喜欢的菜品，创建美食清单并分享给微信好友
- **管理后台**：食堂管理员管理菜品和数据统计，系统管理员管理用户和审核评价

> 项目为 **DBMS 数据库管理系统** 课程设计，404 小组开发。

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.x | 应用框架 |
| Java | 21 | 运行环境 |
| Maven | — | 构建工具 |
| MySQL | 8.0 | 数据库 |
| MyBatis-Plus | 3.5.5 | ORM 持久层 |
| Spring Security | — | 安全认证（JWT） |
| Knife4j | 4.5.0 | API 在线文档 |

### 前端（小程序端）

| 技术 | 用途 |
|------|------|
| uni-app (Vue 3 + TypeScript) | 跨端开发框架 |
| Vite | 构建工具 |
| Pinia | 状态管理 |

### 前端（管理后台端）

| 技术 | 用途 |
|------|------|
| Vue 3 + TypeScript | 框架 |
| Vite | 构建工具 |
| Vue Router | 路由 |
| Pinia | 状态管理 |
| ECharts | 数据可视化图表 |
| Element Plus | UI 组件库 |

## 项目结构

```
bjtu_food/
├── backend/                          # 后端项目（Spring Boot）
│   ├── pom.xml                       # Maven 依赖管理
│   ├── src/main/java/com/bjtufood/
│   │   ├── BjtuFoodApplication.java  # 启动类
│   │   ├── common/                   # 公共模块（配置/异常/响应/工具）
│   │   ├── auth/                     # 用户认证模块
│   │   ├── canteen/                  # 食堂档口模块
│   │   ├── dish/                     # 菜品模块
│   │   ├── review/                   # 评价模块
│   │   ├── favorite/                 # 收藏模块
│   │   ├── list/                     # 美食清单模块
│   │   └── upload/                   # 文件上传模块
│   ├── src/main/resources/
│   │   ├── application.yml           # 主配置文件
│   │   ├── application-dev.yml       # 开发环境配置
│   │   └── mapper/                   # MyBatis XML 映射
│   ├── uploads/                      # 上传文件存储目录
│   └── sensitive_words.txt           # 敏感词库
├── frontend/                         # 微信小程序端（uni-app）
│   ├── src/
│   │   ├── pages/                    # 页面（8个：首页/发现/收藏/个人中心/食堂详情/菜品详情/评价/档口详情）
│   │   ├── api/                      # API 请求层（按模块拆分）
│   │   ├── components/               # 通用组件（13个）
│   │   ├── stores/                   # Pinia 状态管理
│   │   └── types/                    # TypeScript 类型定义
│   ├── package.json
│   └── vite.config.ts
├── web/                              # 管理后台端（Vue 3）
│   ├── src/
│   │   ├── views/                    # 10个视图页面
│   │   ├── api/                      # API 请求层
│   │   ├── components/               # 通用组件
│   │   └── stores/                   # 状态管理
│   ├── package.json
│   └── vite.config.ts
└── docs/                             # 项目文档
    ├── README.md                     # 文档导航
    ├── 项目完整文档-食在交大.md       # 综合设计文档
    ├── 核心功能详细设计与实现.md       # 核心功能设计（含类图/时序图/流程图）
    ├── 数据库设计与命令行操作指南.md   # 数据库字段详解与SQL操作
    ├── 项目启动指南.md                # 启动与环境配置
    ├── frontend/                     # 前端UI设计规范
    ├── api/                          # API接口文档
    └── docx/                         # 原始归档文档
```

## 后端模块架构

项目后端采用模块化设计，所有业务模块**仅依赖 common 公共模块**：

```
             auth(认证)    canteen(食堂)    upload(上传)
                  \            |            /
                   \           |           /
                    ┌──────────┴──────────┐
                    │       common        │  ← 公共模块
                    └──────────┬──────────┘
                    ┌──────────┴──────────┐
                   /           |           \
            review(评价)    dish(菜品)    favorite(收藏)
                  \            |            /
                   \    list(清单)         /
```

**模块间通信：**
- **事件驱动**：review(评价) 和 favorite(收藏) 发布 Spring ApplicationEvent，dish(菜品) 模块监听并更新评分/收藏数
- **Service 注入**：list(清单) 模块通过 Service 接口注入调用 favorite 的批量收藏方法

## 数据库设计

共 **10 张表**：

| 表名 | 说明 |
|------|------|
| user | 用户（学生/管理员） |
| email_verification_code | 邮箱验证码记录 |
| canteen | 食堂 |
| stall | 档口（从属于食堂） |
| dish | 菜品（从属于档口） |
| review | 评价（用户对菜品的评分和图文评价） |
| favorite | 收藏关系（用户与菜品） |
| item_list | 美食清单 |
| list_item | 清单项（清单与菜品的关联） |
| banner | 首页轮播图配置 |

详情参见 [docs/数据库设计与命令行操作指南.md](docs/数据库设计与命令行操作指南.md)

## API 概览

共 **55 个 REST 接口**，前缀统一为 `/api`：

| 模块 | 公开 | 需登录 | 管理端 | 合计 |
|------|:----:|:------:|:------:|:----:|
| auth（认证） | 4 | 3 | 3 | 10 |
| canteen（食堂档口） | 6 | 0 | 8 | 14 |
| dish（菜品） | 4 | 1 | 4 | 9 |
| review（评价） | 1 | 3 | 3 | 7 |
| favorite（收藏） | 0 | 3 | 0 | 3 |
| list（清单） | 1 | 4 | 0 | 5 |
| upload（上传） | 0 | 1 | 0 | 1 |
| banner（轮播） | 0 | 0 | 4 | 4 |
| stats（统计） | 0 | 0 | 3 | 3 |
| **合计** | **16** | **15** | **25** | **55** |

API 文档（启动后访问）：`http://localhost:8080/api/doc.html`

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- Node.js 18+（前端）/ 20+（管理后台）
- pnpm（管理后台使用）
- 微信开发者工具（最新稳定版）

### 1. 数据库初始化

```bash
# 方法一：使用远程数据库（默认配置，无需操作）
# 方法二：创建本地数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS bjtu_food DEFAULT CHARSET utf8mb4;"

# 导入基础数据
mysql -h 39.105.66.79 -u bjtu -p bjtu_food < docs/bjtu_food_base.sql
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

验证：浏览器访问 `http://localhost:8080/api/doc.html`

### 3. 启动管理后台

```bash
cd web
pnpm install
pnpm dev
```

访问：`http://localhost:5173`，使用 `admin001 / 123456` 登录

### 4. 启动微信小程序端

```bash
cd frontend
npm install
npm run dev:mp-weixin
```

在微信开发者工具中导入 `frontend/dist/dev/mp-weixin` 目录预览。

### 预置测试账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 普通用户 | `20240001` | `123456` |
| 管理员 | `admin001` | `123456` |

### 常见问题

| 问题 | 解决方案 |
|------|---------|
| 后端连不上数据库 | 检查 `application.yml`/`application-dev.yml` 中的数据库配置 |
| 图片无法显示 | 检查 `app.public-base-url` 配置和上传目录 `./uploads/images` |
| 小程序图片不显示 | 微信开发者工具设置→不校验合法域名；真机调试需同一局域网 |
| 验证码发送失败 | 配置 QQ 邮箱 SMTP 并获取授权码 |
| 端口冲突 | `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`

## 核心功能设计亮点

### JWT 无状态认证

登录成功后服务端签发 JWT Token（有效期7天），后续请求通过 `Authorization: Bearer <token>` 头传递。`JwtAuthFilter` 拦截每个请求并自动解析用户身份。

### 事件驱动解耦

评价提交和收藏操作不直接调用菜品模块，而是通过 Spring 事件机制解耦：

```
评价提交 → 发布 ReviewSubmittedEvent → [事务提交后] → 重算菜品平均分
收藏/取消 → 发布 FavoriteChangedEvent → [事务提交后] → 同步菜品收藏数
```

### 搜索与排序

菜品列表支持多维度搜索（关键词、食堂、档口、价格区间、标签）和多种排序方式（评分、价格、收藏量、时间、综合热度）。默认排序公式：`favorite_count * 3 + view_count + rating_count * 5`

### 菜品详情联合查询

单个菜品详情接口同时返回：基本信息、评分分布（1-5星各星级人数）、当前用户收藏状态、当前用户评价状态，避免多次网络请求。

## 更多文档

| 文档 | 位置 | 说明 |
|------|------|------|
| 综合设计文档 | [docs/项目完整文档-食在交大.md](docs/项目完整文档-食在交大.md) | 完整的设计规格说明 |
| 核心功能设计 | [docs/核心功能详细设计与实现.md](docs/核心功能详细设计与实现.md) | 类图/时序图/流程图设计 |
| 启动配置指南 | [docs/项目启动指南.md](docs/项目启动指南.md) | 环境搭建与启动步骤 |
| 数据库文档 | [docs/数据库设计与命令行操作指南.md](docs/数据库设计与命令行操作指南.md) | 表结构详解和SQL操作 |
| 小程序API | [docs/api/frontend-api-docs.md](docs/api/frontend-api-docs.md) | 前端接口说明 |
| 管理端API | [docs/api/web-api-docs.md](docs/api/web-api-docs.md) | 后台管理接口说明 |
| 测试指南 | [docs/api/后端接口测试指南.md](docs/api/后端接口测试指南.md) | 接口测试步骤与示例 |
| UI设计规范 | [docs/frontend/前端ui设计prompt.md](docs/frontend/前端ui设计prompt.md) | 小程序UI视觉规范 |
