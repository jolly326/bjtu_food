# 校园食堂信息系统 - 食在交大

一套帮助北京交通大学学生解决"中午/晚上吃什么"选择困难问题的菜品展示小程序。

## 项目简介

本项目是一个校园食堂菜品展示与互动小程序（微信小程序），核心功能包括：

- **浏览菜品**：按食堂/档口浏览、搜索筛选、查看菜品详情与评价
- **互动评价**：学生对菜品进行星级评分和图文评价
- **收藏清单**：收藏喜欢的菜品，创建美食清单并分享给微信好友
- **管理后台**：食堂管理员管理菜品和查看数据，系统管理员管理用户和审核评价

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.x | 应用框架 |
| Java | 21 | 运行环境 |
| Maven | — | 构建工具 |
| MySQL | 8.0 | 数据库 |
| MyBatis-Plus | 3.5.5 | ORM 持久层 |
| Spring Security | — | 安全认证 |
| JWT (jjwt) | 0.12.3 | 无状态 Token |
| Knife4j | 4.5.0 | API 文档 |

### 前端

| 技术 | 用途 |
|------|------|
| uni-app (Vue3 + TS) | 跨端开发框架 |
| Vite | 构建工具 |
| 微信小程序 | 目标平台 |

## 项目结构

```
bjtu_food/
├── backend/                          # 后端项目（Spring Boot）
│   ├── pom.xml                       # Maven 依赖配置
│   ├── sensitive_words.txt           # 敏感词库
│   ├── uploads/                      # 上传文件存储
│   └── src/
│       ├── main/
│       │   ├── java/com/bjtufood/
│       │   │   ├── BjtuFoodApplication.java   # 启动类
│       │   │   ├── common/                    # 公共模块
│       │   │   │   ├── config/                # 跨域、Knife4j、Jackson、WebMvc 配置
│       │   │   │   ├── result/                # 统一响应 Result + PageResult
│       │   │   │   ├── exception/             # 全局异常处理 + 自定义异常
│       │   │   │   ├── constant/              # 角色/菜品常量
│       │   │   │   └── utils/                 # JWT工具、敏感词过滤
│       │   │   ├── auth/                      # 认证模块
│       │   │   ├── canteen/                   # 食堂档口模块
│       │   │   ├── dish/                      # 菜品模块
│       │   │   ├── review/                    # 评价模块
│       │   │   ├── favorite/                  # 收藏模块
│       │   │   ├── list/                      # 清单模块
│       │   │   └── upload/                    # 文件上传模块
│       │   └── resources/
│       │       ├── application.yml            # 主配置
│       │       ├── application-dev.yml        # 开发环境配置
│       │       └── mapper/                    # MyBatis XML 映射
│       └── test/
│           └── java/com/bjtufood/
│               └── BjtuFoodApplicationTests.java
├── frontend/                         # 前端项目（uni-app）
│   ├── src/
│   │   ├── pages/                    # 小程序页面
│   │   ├── api/                      # API 请求层（按模块拆分）
│   │   ├── components/               # 通用组件
│   │   └── ...
│   ├── package.json
│   └── vite.config.ts
└── docs/                             # 项目文档
    ├── 项目总体设计方案.md
    ├── 前端开发环境配置指南.md
    ├── 校园食堂信息系统需求规格说明书(1).docx
    └── 前后端设计文档.docx
```

## 模块架构

```
所有模块仅依赖 common，互不直接依赖

评价/收藏模块 → 发布 Spring 事件 → dish 模块监听并更新评分/收藏数
清单模块       → 接口注入 FavoriteService → 一键收藏
新增功能只需新建模块，已有模块不受影响
```

## API 概览

共 **42 个接口**，7 个模块：

| 模块 | 公开 | 需登录 | 管理端 | 说明 |
|------|------|--------|--------|------|
| auth | 2 | 2 | 3 | 登录注册、用户信息、用户管理 |
| canteen | 2 | 0 | 6 | 食堂/档口查询与管理 |
| dish | 4 | 1 | 6 | 菜品浏览、搜索、管理、统计 |
| review | 1 | 3 | 3 | 评价提交、编辑、审核 |
| favorite | 0 | 3 | 0 | 收藏切换、列表、批量 |
| list | 1 | 4 | 0 | 清单创建、分享、一键收藏 |
| upload | 0 | 1 | 0 | 图片上传 |

API 文档地址（启动后）：http://localhost:8080/api/doc.html

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- Node.js 18+
- 微信开发者工具（最新稳定版）

### 后端启动

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS bjtu_food DEFAULT CHARSET utf8mb4;"

# 2. 启动后端
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 前端启动

```bash
cd frontend
npm install
npm run dev:mp-weixin
```

编译后在微信开发者工具中导入 `frontend/dist/dev/mp-weixin` 目录预览。

## 数据库设计

核心 8 张表：

| 表名 | 说明 |
|------|------|
| user | 用户（学生/食堂管理员/系统管理员） |
| canteen | 食堂 |
| stall | 档口（从属于食堂） |
| dish | 菜品（从属于档口） |
| review | 评价（用户对菜品的评分和图文评价） |
| favorite | 收藏关系（用户与菜品的多对多） |
| item_list | 美食清单 |
| list_item | 清单项（清单与菜品的关联） |

## 事件驱动设计

评价提交或收藏操作后，不会直接调用 dish 模块的代码，而是通过 Spring 事件机制解耦：

```
ReviewService 提交评价 → 发布 ReviewSubmittedEvent
                                      ↓
                RatingUpdateListener 监听 → 重算菜品平均评分

FavoriteService 收藏/取消 → 发布 FavoriteChangedEvent
                                      ↓
                CollectCountListener 监听 → 更新菜品收藏量
```

## License

MIT
