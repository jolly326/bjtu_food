# 知行食记

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![Java](https://img.shields.io/badge/Java-21-orange)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)
![uni-app](https://img.shields.io/badge/uni-app-Vue3-4FC08D)
![Vue3](https://img.shields.io/badge/Vue-3-42b883)
![TypeScript](https://img.shields.io/badge/TypeScript-5-blue)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

**校园美食发现与分享社区** — 帮助交大学生解决"每天不知道吃什么"的难题。

学生（平鉴官）发布 / 更新菜品与档口信息 → 管理员审核 → 学生浏览、评价、分享，形成"**发现 → 决策 → 分享**"闭环。优惠活动由后勤在 Web 后台统一发放。

> 定位为内容信息公示与美食点评社区，不涉及点单、支付、配送等外卖功能。

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
| **学生（兼平鉴官）** | 浏览、发布/更新菜品、提交/更新档口·食堂信息、评价、分享 | 微信小程序 |
| **系统管理员（admin / 后勤）** | 食堂/档口/菜品后台 CRUD 与上下架、内容审核、评价审核、活动管理、数据面板 | Web管理端完整版 |

> 注：无独立的"档口老板"身份，菜品与档口基础数据均由学生 UGC 共建，后台拥有最终审核与控制权。

---

## 项目结构

```
bjtu_food/
├── server/                   # Spring Boot 后端
│   ├── src/main/java/com/bjtufood/
│   │   ├── common/            # 公共模块（配置/异常/响应/工具）
│   │   ├── auth/              # 认证模块（JWT + Spring Security）
│   │   ├── canteen/           # 食堂档口模块
│   │   ├── dish/              # 菜品模块（含评分/收藏数事件监听）
│   │   ├── review/            # 评价模块
│   │   ├── content/           # 内容审核模块
│   │   ├── favorite/          # 收藏模块
│   │   ├── list/              # 美食清单模块
│   │   └── upload/            # 文件上传模块
│   ├── uploads/               # 上传图片存储
│   └── sensitive_words.txt    # 敏感词库
├── client/                  # 微信小程序端（uni-app）
└── web/                       # 管理后台端（Vue 3 + Element Plus）
```

---

## 核心功能

| 模块 | 说明 | 状态 |
|------|------|:----:|
| 用户认证 | 密码/邮箱验证码登录、注册、找回密码、JWT 鉴权 | ✅ |
| 食堂档口管理 | CRUD、排序、状态管理 | ✅ |
| 菜品管理 | CRUD、标签、审核状态（pending/approved/rejected） | ✅ |
| 内容审核 | 学生提交内容审核：菜品 / 档口 / 食堂（通过/驳回）、评价与晒图（隐藏/删除） | 🚧 |
| 活动/特价管理 | 限时活动、新品标记、倒计时 | 🚧 |
| 首页上新流与活动流 | "今日上新""限时活动"时间线 | 🚧 |
| 轮播图管理 | CRUD、排序、跳转配置 | ✅ |
| 菜品浏览搜索 | 列表、搜索、详情、热门排行、浏览量统计 | ✅ |
| 就餐评价 | 提交/编辑/删除、敏感词过滤、"喜欢"投票 | ✅ / 🚧 |
| 收藏 | （后端保留，前端MVP暂不使用） | ⏸️ |
| 美食清单 | 创建/查看/删除/分享/一键收藏 | ✅ |
| 图片上传 | jpg/png/webp，≤5MB，按日期分目录 | ✅ |

---

## 数据库（11 张表）

| 表 | 说明 |
|----|------|
| user | 用户（student / admin） |
| email_verification_code | 邮箱验证码 |
| canteen | 食堂 |
| stall | 档口（从属食堂，无档口老板绑定；基础信息可由学生提交、后台 CRUD） |
| dish | 菜品（含审核状态、活动字段） |
| review | 评价 |
| review_useful | 评价"喜欢"投票 |
| favorite | 收藏关系 |
| item_list / list_item | 美食清单 |
| banner | 轮播图 |

详情参见 [docs/数据库设计与命令行操作指南.md](docs/数据库设计与命令行操作指南.md)

---

## 快速开始

### 环境要求
JDK 21+、Maven 3.8+、MySQL 8.0+、Node.js 18+、pnpm、微信开发者工具

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
| 学生 | `20240001` | `123456` |
| 管理员 | `admin001` | `123456` |

> ⚠️ 测试账号仅限本地开发环境；生产环境部署后请立即改密。

---

## 文档

| 文档 | 说明 |
|------|------|
| [project_spec.md](project_spec.md) | **技术规范基线**：技术栈 / 目录 / 跨端边界 / 不可违背的设计与实现红线 |
| [docs/WORKFLOW.md](docs/WORKFLOW.md) | 开发协作流程：需求 → 定样 → 拆任务 → 开发 → 质量门禁 → 经验回流 |
| server/、client/、web/ | 后端（Spring Boot）、微信小程序（uni-app）、Web 管理后台（Vue3+Element Plus）源码 |
