# 知行食记

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![Java](https://img.shields.io/badge/Java-21-orange)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)
![uni-app](https://img.shields.io/badge/uni--app-Vue3-4FC08D)
![Vue3](https://img.shields.io/badge/Vue-3-42b883)
![TypeScript](https://img.shields.io/badge/TypeScript-5-blue)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

**校园美食发现与分享平台** —— 帮大学生解决"每天不知道吃什么"的难题。

学生浏览、搜索校园菜品，发表评价与纠错反馈，帮助彼此做出就餐决策；管理员通过后台维护菜品信息，让数据在"**发现 → 决策 → 分享**"的闭环中持续保持新鲜。

> 定位为菜品信息展示与美食点评，不涉及点单、支付、配送等外卖功能。

## 功能特性

**学生端（微信小程序）**

- 打开即用：微信静默登录，无需注册账号
- 首页：轮播推荐、筛选标签、菜品瀑布流、热度排序
- 搜索：关键词检索，附「猜你喜欢」个性化推荐
- 菜品详情：基本信息、属性标签、评价列表、浏览计数
- 评价：星级 + 文字 + 配图，支持编辑与删除
- 纠错与反馈：一键提交菜品信息纠错、意见建议（支持配图）
- 站内通知：处理结果与回执直达通知中心
- 个人资料：昵称、头像编辑，账号注销

**管理后台（Web）**

- 食堂 / 档口 / 菜品的录入、上下架与维护
- 评价审核、用户反馈与纠错处理
- 用户管理与数据看板

**内容安全**：学生发布的文字与图片均经过内容安全检测后展示。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | Spring Boot · Java · MySQL · MyBatis-Plus |
| 小程序 | uni-app · Vue 3 · TypeScript · Pinia |
| 管理后台 | Vue 3 · TypeScript · Element Plus · ECharts |

## 项目结构

```
bjtu_food/
├── server/    # 后端服务（Spring Boot）
├── client/    # 微信小程序（uni-app）
├── web/       # 管理后台（Vue 3）
└── docs/      # 项目文档（架构、功能契约、UI 设计稿）
```

## 快速开始

### 环境要求

JDK 21+、Maven 3.8+、MySQL 8.0+、Node.js 18+、pnpm、微信开发者工具

### 1. 初始化数据库

> **本仓库不维护初始化脚本。** 库结构唯一真源 = [`docs/schema/`](./docs/schema/README.md)。

```bash
# 建库建表：按 docs/schema/ 各表文档的「列定义 / 索引」逐表 CREATE TABLE
# 演示数据：自行准备（或按各表文档的说明录入）
```

### 2. 配置环境变量

复制 `server/.env.example` 为 `server/.env` 并按需填写：

| 变量 | 说明 |
|---|---|
| `SPRING_DATASOURCE_URL / USERNAME / PASSWORD` | 数据库连接 |
| `JWT_SECRET` | JWT 密钥（≥ 32 字节） |
| `ADMIN_TOKEN` | 管理后台访问口令（与 `web/.env.local` 的 `VITE_ADMIN_TOKEN` 保持一致） |
| `WECHAT_APPID / WECHAT_SECRET` | 微信小程序凭证 |
| `COS_*` | 对象存储配置（评价 / 反馈配图存储） |
| `SPRING_MAIL_USERNAME / SPRING_MAIL_PASSWORD` | 邮箱服务（学生认证验证码） |

### 3. 启动

```bash
# 后端（接口文档：http://localhost:8080/api/v1/swagger-ui/index.html）
cd server && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 管理后台（http://localhost:5173）
cd web && pnpm install && pnpm dev

# 小程序（微信开发者工具导入 client/dist/dev/mp-weixin）
cd client && npm install && npm run dev:mp-weixin
```

### 测试账号

| 角色 | 用户名 | 密码 |
|---|---|---|
| 学生 | `2024001` ~ `2024004` | `123456` |
| 管理员 | `admin` | `123456` |

> 测试账号仅限本地开发使用；部署到生产环境后请立即修改默认密码。

## 文档

| 文档 | 说明 |
|---|---|
| [功能设计（学生端）](docs/func/client/README.md) | 学生端功能说明 |
| [功能设计（管理端）](docs/func/web/README.md) | 管理后台功能说明 |
| [接口契约](docs/api/README.md) | 端点 / 入参 / 出参 / 错误码（`client/` 学生端 · `web/` 管理端） |
| [数据库设计](docs/schema/README.md) | 表 / 列 / 索引（库结构唯一真源） |
| [UI 设计规范（学生端）](docs/ui/client/README.md) | 学生端页面设计稿与跨页通用口径 |
| [UI 设计规范（管理端）](docs/ui/web/README.md) | 管理端页面设计稿与公共组件基线 |

## 贡献指南

欢迎任何形式的贡献——报告 Bug、提交功能建议、改进文档，或直接发 Pull Request。

### 参与流程

1. Fork 本仓库，从 `main` 切出特性分支（`git checkout -b feat/amazing-feature`）
2. 按上面的「快速开始」搭好本地环境，完成改动
3. 提交前跑通各端自检：

   ```bash
   cd server && mvn -B test        # 后端测试
   cd client && npm run verify     # 小程序：契约门禁 + 类型检查
   cd web && pnpm type-check       # 管理后台：类型检查
   ```

4. 使用 [Conventional Commits](https://www.conventionalcommits.org/) 风格提交（如 `feat(client): ...`、`fix(server): ...`、`docs: ...`）
5. 发起 PR，说明改动动机与影响范围，等待 Review

### 报告问题

- Bug 与功能建议请提 [Issue](https://github.com/jolly326/bjtu_food/issues)，尽量附上复现步骤、运行环境与预期行为

### 项目约定

- 遵循现有代码风格与目录结构；目录组织、UI 红线与跨端边界见 [docs/func/client/README.md](docs/func/client/README.md)
- 涉及后端接口变更时，请同步更新契约产物与相关文档（`cd client && npm run gen:api:fresh`）
- UI 改动以 [docs/ui/client/](docs/ui/client/) 的设计规范为准
- 数据库结构变更请先改 `docs/schema/` 对应表文档，再执行 ALTER；**文档是唯一真源**

## License

MIT
