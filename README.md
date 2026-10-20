# 知行食记

![Python](https://img.shields.io/badge/Python-3.12-3776AB)
![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688)
![MySQL](https://img.shields.io/badge/MySQL-5.7-4479A1)
![uni-app](https://img.shields.io/badge/uni--app-Vue3-4FC08D)
![Vue3](https://img.shields.io/badge/Vue-3-42b883)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

**校园菜品展示与轻量反馈平台** —— 帮学生解决「今天吃什么」。

学生看海报了解档口、刷卡片决定吃什么、吃完顺手点一个反馈；运营在后台录入菜品与档口。数据在「**浏览 → 决策 → 体验 → 反馈 → 影响下一次推荐**」的闭环里持续更新。

## 产品形态（三个 tab）

| Tab | 做什么 |
|---|---|
| **发现** | 一屏一张档口**竖版海报**，左右滑动看更多；纯展示、不跳转、图上零文本 |
| **美食** | **扑克式单卡**，一次一道；左滑右滑划走，点「去品尝」记下要吃的 |
| **记忆** | 按月回看吃过的菜（月内按日期分组），顺手点 **喜欢 / 一般 / 不喜欢** |

- **无搜索、无「我的」、无菜品详情页** —— 信息全在卡片上，用户只需滑动和点按钮。
- **全匿名**：微信 `openid` 静默识别，身份**不在任何界面展示**；不做注册 / 登录 / 认证 / 注销。
- **零自由输入**：学生侧只有「去品尝」按钮、三态评分（喜欢 / 一般 / 不喜欢）、纠错选项 —— 没有文字与图片 UGC。
- **群体风味评级**：卡片左上字母 + 右上「风味评级 / 档位词」（S 臻荐 · A 极荐 · B 推荐 · C 尚可 · D 平实），投票人数不足 3 人为「待评」。

## 闭环

```
运营录入菜品 → 学生刷「美食」 → 点「去品尝」 → 线下体验 → 回「记忆」评分 → 评分影响推荐权重 → 帮下一个学生决策
```

## 管理后台（运营端）

- **食堂 / 档口 / 菜品 / 海报** 的录入与维护
- **评分数据**查看（只读）、**菜品纠错**的处置与**纠错选项字典**维护

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | **Python · FastAPI · SQLAlchemy · MySQL 5.7（TDSQL-C）** |
| 小程序 | uni-app · Vue 3 · TypeScript |
| 管理后台 | Vue 3 · TypeScript · Element Plus |

> **刻意不引入**（防后人顺手加，详见 `docs/`）：内容安全机审（学生侧零自由输入）· Redis（限频走进程内）· 用户认证与角色权限 · 操作审计表 · 数据导出。

## 项目结构

```
bjtu_food/
├── server/    # 后端服务（FastAPI）
├── client/    # 微信小程序（uni-app）
├── web/       # 管理后台（Vue 3）
└── docs/      # 设计文档 —— 唯一真源（定位 / 功能 / 契约 / 库结构 / UI / 安全）
```

## 快速开始

### 环境要求

Python **3.12**（conda）· Node.js 18+ · npm · 微信开发者工具 · MySQL **5.7+**

### 1. 后端环境

```bash
conda create -n bjtu-food python=3.12 -y
conda activate bjtu-food
pip install -r server/requirements.txt -r server/requirements-dev.txt
```

> 网络慢时加国内源：`-i https://pypi.tuna.tsinghua.edu.cn/simple`

### 2. 初始化数据库

> **本仓库不维护初始化脚本。** 库结构唯一真源 = [`docs/schema/`](./docs/schema/README.md)。

```bash
# 建库建表：按 docs/schema/ 各表文档的「列定义 / 索引」逐表 CREATE TABLE
# 演示数据：自行准备（运营端账号直接写入 admin 表）
```

### 3. 配置环境变量

复制 `server/.env.example` 为 `server/.env`：

| 变量 | 说明 |
|---|---|
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | 数据库连接 |
| `JWT_SECRET` | **学生端** JWT 签名密钥 |
| `ADMIN_JWT_SECRET` | **运营端** JWT 签名密钥 —— 🔴 **必须与学生端不同**（否则学生 token 可冒充运营端） |
| `WECHAT_APPID` / `WECHAT_SECRET` | 微信小程序凭证（换 openid） |
| `COS_*` | 对象存储（档口海报 / 菜品图） |

### 4. 启动

```bash
# 后端（🔴 单 worker 启动；接口文档 http://localhost:8080/docs）
cd server && uvicorn app.main:app --reload --port 8080

# 管理后台（http://localhost:5173）
cd web && npm install && npm run dev

# 小程序（用微信开发者工具导入 client/dist/dev/mp-weixin）
cd client && npm install && npm run dev:mp-weixin
```

> **运营端账号没有注册入口** —— 账号行直接建在 `admin` 表，口令存 **BCrypt 哈希**；改密同样是直连库更新哈希（步骤见 [`docs/schema/admin.md`](./docs/schema/admin.md)）。
> **学生端没有账号** —— 打开即用，冷启动静默换取 openid。

## 文档

| 文档 | 说明 |
|---|---|
| [项目总览与文档树](docs/README.md) | 产品定位 · 分层边界 · 技术栈 · 变更流程 |
| [功能设计（学生端）](docs/func/client/README.md) | 三个 tab 的业务规则 + 项目约定与红线 |
| [功能设计（运营端）](docs/func/web/README.md) | 食堂 / 档口 / 菜品 / 反馈 / 纠错 / 登录 |
| [接口契约](docs/api/README.md) | 端点 / 入参 / 出参 / 错误码（通用约定在最前） |
| [数据库设计](docs/schema/README.md) | 9 张表的列 / 索引 / 约束（**库结构唯一真源**） |
| [UI 设计（学生端）](docs/ui/client/README.md) | 三页设计稿 + 公共组件与形态基线 + 设计变量 |
| [UI 设计（运营端）](docs/ui/web/README.md) | 列表 + 弹窗范式 + 公共组件基线 |
| [安全设计](docs/secur/README.md) | 身份凭证 · 鉴权与归属 · 限频 · 凭据与部署边界 |

## 贡献指南

欢迎任何形式的贡献 —— 报告 Bug、提交功能建议、改进文档，或直接发 Pull Request。

### 参与流程

1. Fork 本仓库，从 `main` 切出特性分支（`git checkout -b feat/amazing-feature`）
2. 按上面的「快速开始」搭好本地环境，完成改动
3. 提交前跑通各端自检：

   ```bash
   pytest                          # 后端：pytest + 分层护栏
   cd client && npm run verify     # 小程序：契约门禁 + 类型检查
   cd web && npm run verify        # 管理后台：token 门禁 + 单测 + lint + 类型检查 + 构建
   ```

4. 使用 [Conventional Commits](https://www.conventionalcommits.org/) 风格提交（如 `feat(client): ...`、`fix(server): ...`、`docs: ...`）
5. 发起 PR，说明改动动机与影响范围，等待 Review

### 报告问题

- Bug 与功能建议请提 [Issue](https://github.com/jolly326/bjtu_food/issues)，尽量附上复现步骤、运行环境与预期行为

### 项目约定

- 🔴 **文档优先**：改设计先改 `docs/` 对应真源，再动代码；文档与代码不一致时以 **`docs/`** 为准
- 涉及后端字段 / 端点变更时，须刷新端上契约产物（`cd client && npm run gen:api:fresh`）
- UI 改动以 `docs/ui/client/`（学生端）与 `docs/ui/web/`（运营端）的设计规范为准
- 数据库变更：**先改 `docs/schema/` 表文档，再执行 `ALTER`**，两者必须一致

## License

MIT
