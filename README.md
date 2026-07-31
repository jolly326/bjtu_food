# 食在交大

**校园美食发现与分享社区** — 帮助交大学生解决"每天不知道吃什么"的难题。

学生（平鉴官）发布 / 更新菜品与档口信息 → 管理员审核 → 学生浏览、评价、分享，形成"**发现 → 决策 → 分享**"闭环。优惠活动由后勤在 Web 后台统一发放。

> 定位为内容信息公示与美食点评社区，不涉及点单、支付、配送等外卖功能。
> DBMS 数据库管理系统课程设计，404 小组开发。

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
├── backend/                   # Spring Boot 后端
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
├── frontend/                  # 微信小程序端（uni-app）
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
cd backend && mvn spring-boot:run
# → 访问 http://localhost:8080/api/doc.html

# 2. 启动管理后台
cd web && pnpm install && pnpm dev
# → 访问 http://localhost:5173

# 3. 启动小程序
cd frontend && npm install && npm run dev:mp-weixin
# → 微信开发者工具导入 frontend/dist/dev/mp-weixin
```

### 测试账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 学生 | `20240001` | `123456` |
| 管理员 | `admin001` | `123456` |

---

## 文档

| 文档 | 说明 |
|------|------|
| [project_spec.md](project_spec.md) | **基础规范基线（最高权威）**：技术栈 / 目录 / 跨端边界 / **不可违背的红线** / 协作纪律。所有 agent 必须服从；仅技术负责人可修改 |
| [docs/WORKFLOW.md](docs/WORKFLOW.md) | **多 agent 协作流程**：需求 → 定样 → 拆 task → 开发 → 质量门禁 → 踩坑回流；含各角色交接物与阅读顺序 |
| [tasks/](tasks/) | 开发执行单元（task-XX）：验收标准 + 接口契约 + 依赖，每个 task 必须引用 spec 条款 |
| [.codebuddy/agents/](.codebuddy/agents/) | 各角色权限文件（能改 / 不能改） |
| backend/、frontend/、web/ | 后端（Spring Boot）、微信小程序（uni-app）、Web 管理后台（Vue3+Element Plus）源码 |

### 多 Agent 协作阅读顺序

新 agent / 协作者进入项目，按此顺序阅读，避免漏看红线、各读各的：

1. `project_spec.md`（基础规范基线，先懂红线）
2. `.codebuddy/agents/` 中**自己的角色**文件（懂权限边界）
3. `tasks/` 中认领的 `task-XX`（懂验收与契约）
4. `docs/WORKFLOW.md`（懂流程与交接）
