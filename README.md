# 知行食记

![Python](https://img.shields.io/badge/Python-3.12-3776AB)
![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)
![uni-app](https://img.shields.io/badge/uni--app-Vue3-4FC08D)
![Vue3](https://img.shields.io/badge/Vue-3-42b883)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

**校园菜品展示与轻量反馈平台** —— 帮学生解决「今天吃什么」。

菜品（食堂 / 楼层 / 档口 / 价格）由运营录入；学生**一句话搜菜**，从结果信息卡里**看图选菜**，必要时**提交一段文字反馈**。

## 产品形态（学生端单页全屏）

- **单一结果态**：首页即当前条件下的在售位置结果集。冷启动是全部在售，输入一句话是推荐结果，清空回全量；布局恒定，只有数据变化。
- **结果卡**：1 列竖向滚动，一卡 = 一个在售位置（16:9 菜品图 + 菜名 + 食堂 / 楼层 / 档口 + 确定售价 + 描述常驻）；描述超长时在卡内展开全文，无遮罩、无弹窗、无独立详情页。
- **页头**：轻量 brand strip（品牌 + 日期），无操作按钮；页头之下直接是结果区。
- **搜索**：底部单行悬浮胶囊（输入框 + 主色放大镜），输入一句话即替换结果集；不设筛选入口、不设结果条。
- **无 TabBar / 无「我的」/ 无左右翻页**；全匿名（微信 `openid` 静默识别，身份不在任何界面展示）。
- **反馈 = 一段文字**（产品 / 菜品两类，≤500 字），提交前走微信内容安全检测 `msgSecCheck`。

## 一句话搜索口径

| 环节 | 口径 |
|---|---|
| 硬约束 | 只认三类：**完整菜名**、**食堂名**、**价格上限**（`N 元` / `N 块`） |
| 召回 | 硬约束过滤后，按「query 向量 ↔ 菜品描述向量」余弦相似度排序 |
| 条数 | 最多 30 条，**不设相似度下限**（小目录下搜索表现为结果集重排） |
| 命中收窄 | 食堂名 / 价格命中时按行收窄，只渲染命中的卡 |
| 向量 | 保存菜品时按 `description` 预计算，`embedding_model` 记录模型；检索只信任同模型向量 |

## 闭环

```
运营录入在售菜品 → 学生搜索 / 看图选菜 → 提交文字反馈 → 运营端查看并标记已处理
```

## 管理后台（运营端）

- **食堂 / 档口 / 菜品**维护（菜品本体 + 多个位置：图 / 描述 / 价格共享；同名不同档口是两道菜）。
- **档口批量操作**：批量启用 / 停用 / 改所属食堂。
- **反馈处理**：列表查看 + 标记已处理（只保留「未处理 / 已处理」，无状态机）。
- **登录**：账号直接建在 `admin` 表，口令存 BCrypt 哈希；无注册入口。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | **Python · FastAPI · SQLAlchemy · MySQL 8.0（TDSQL-C）** |
| 小程序 | uni-app · Vue 3 · TypeScript |
| 管理后台 | Vue 3 · TypeScript · Element Plus |

> **刻意不引入**（防后人顺手加）：Redis（限频走进程内）· 用户认证与角色体系 · 操作审计表 · 数据导出 · 人工口味标签（口味交给 `description` + 向量）。

## 项目结构

```
bjtu_food/
├── server/        # 后端服务（FastAPI）
├── client/        # 微信小程序（uni-app）
├── web/           # 管理后台（Vue 3）
└── docs/          # 设计文档 —— 唯一真源（架构 / 功能 / 契约 / 库结构 / UI / 安全）
```

> 三端环境变量与启动步骤见下方「快速开始」与各端 README。

## 快速开始

### 环境要求

Python **3.12** · Node.js 18+ · npm · 微信开发者工具 · **MySQL 8.0+**（直接用云端库，本地不建库）

### 1. 后端环境

```bash
cd server
python -m venv .venv && .venv\Scripts\activate      # Windows；macOS/Linux 用 source .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt
```

> 网络慢时加国内源：`-i https://pypi.tuna.tsinghua.edu.cn/simple`

### 2. 库结构

> **本仓库不维护建表 / 种子 SQL 脚本。** 库结构唯一真源 = [`docs/schema/`](docs/schema/)（7 张表），变更流程为「先改表文档 → 直连远程库执行 → 回写文档」。

### 3. 配置环境变量

**后端**：以 `server/.env.example` 为模板创建 **`server/.env.development`**（按 `APP_PROFILE` 加载）；个人差异写 `server/.env.local`（覆盖前者）；生产用 `server/.env.production` 或进程环境变量（均不入库）：

| 变量 | 说明 |
|---|---|
| `APP_PROFILE` | 档位：`development`（默认）/ `production` |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | 数据库连接 |
| `JWT_SECRET` | **学生端** JWT 签名密钥 |
| `ADMIN_JWT_SECRET` | **运营端** JWT 签名密钥 —— 🔴 **必须与学生端不同**（否则学生 token 可冒充运营端） |
| `WECHAT_APPID` / `WECHAT_SECRET` | 微信小程序凭证（换 openid / 内容安全检测） |
| `COS_*`（含必填 `COS_DOMAIN`） | 对象存储（菜品 / 食堂 / 档口封面图）；`COS_DOMAIN` 缺省时拒绝启动 |
| `EMBEDDING_*` | 一句话搜索的语义向量服务：`local`（本机哈希，默认）/ `http`（OpenAI 兼容，如硅基流动 `BAAI/bge-m3`） |
| `SYSTEM1_*` | 可选精排服务（`rerank`）；开启后对向量 top-30 重排，失败自动退回向量排序 |
| `CORS_ALLOW_ORIGINS` | 管理后台来源白名单 |

**管理后台**：`web/.env.development`（连本机）/ `web/.env.production`（连云端），Vite 标准机制，开箱即用。

**小程序**：两套 env 已入库，本地开发**开箱即用**，无需复制：

| 文件 | mode | 用途 |
|---|---|---|
| `client/.env.development` | development（`npm run dev:mp-weixin`） | 连**本机后端** `http://127.0.0.1:8000` |
| `client/.env.production` | production（`npm run build:mp-weixin` / `npm run dev:mp-weixin:cloud`） | 连**云端** |

> 环境变量**构建期内联** —— 改完 env 必须重新编译。
> 真机预览请把「本机局域网 IP」写进 `client/.env.development.local`（不入库）：模拟器 `127.0.0.1` 指本机，**真机上 `127.0.0.1` 指手机自己**。

### 4. 启动

```bash
# 后端（🔴 单 worker 启动；接口文档 http://localhost:8000/docs）
cd server && uvicorn app.main:app --reload --port 8000

# 管理后台（http://localhost:5173）
cd web && npm install && npm run dev

# 小程序 —— 用微信开发者工具导入 client/dist/dev/mp-weixin
cd client && npm install
npm run dev:mp-weixin          # 本地后端（默认，读 .env.development）
npm run dev:mp-weixin:cloud    # 同一份代码连云端后端
npm run build:mp-weixin        # 发布构建（连云端）
```

> **运营端账号没有注册入口** —— 账号行直接建在 `admin` 表，口令存 **BCrypt 哈希**；改密同样是直连库更新哈希（见 [`docs/schema/admin.md`](./docs/schema/admin.md)）。
> **学生端没有账号** —— 打开即用，冷启动静默换取 openid。

### 5. 切换环境（编译 / 部署）

小程序**没有运行期切换**：地址在构建期内联，换环境 = 换命令重新编译。

```bash
cd client
npm run check:env                 # 确认接下来编译出来的包连哪个后端
npm run dev:mp-weixin             # 本地开发 → 连本机 http://127.0.0.1:8000
npm run dev:mp-weixin:cloud       # 同一份代码连云端数据
npm run verify && npm run build:mp-weixin   # 发布构建（连云端）
```

## 文档

| 文档 | 说明 |
|---|---|
| [功能设计（学生端）](docs/func/client/约定与红线.md) | 搜索找菜 · 文本反馈 · 静默识别 + 项目约定与红线 |
| [功能设计（运营端）](docs/func/web/约定与红线.md) | 食堂 / 档口 / 菜品 / 反馈处理 / 登录 |
| [接口契约](docs/api/CONVENTIONS.md) | 端点 / 入参 / 出参 / 错误码（通用约定在最前） |
| [数据库设计](docs/schema/) | 7 张表的列 / 索引 / 约束（**库结构唯一真源**） |
| [UI 设计（学生端）](docs/ui/client/菜品首页.md) | 单页设计稿，自带视觉规格与视觉基线 |
| [UI 设计（运营端）](docs/ui/web/) | 登录 / 食堂 / 档口 / 菜品 / 反馈处理，各页面稿自带视觉规格 |
| [安全设计](docs/secur/S-01-身份与凭证.md) | 身份凭证 · 鉴权与归属 · 限频 · 凭据与部署边界 |

## 贡献指南

欢迎任何形式的贡献 —— 报告 Bug、提交功能建议、改进文档，或直接发 Pull Request。

### 参与流程

1. Fork 本仓库，从 `main` 切出特性分支（`git checkout -b feat/amazing-feature`）
2. 按上面的「快速开始」搭好本地环境，完成改动
3. 提交前跑通各端自检：

   ```bash
   cd server && pytest                # 后端：单测 + 分层护栏
   cd client && npm run verify        # 小程序：契约 / 图标 / UI token / 文档一致性 / 类型 / 单测 / 构建
   cd web && npm run verify           # 管理后台：token 门禁 / 单测 / lint / 类型 / 构建
   ```

4. 使用 [Conventional Commits](https://www.conventionalcommits.org/) 风格提交（如 `feat(client): ...`、`fix(server): ...`、`docs: ...`）
5. 发起 PR，说明改动动机与影响范围，等待 Review

### 报告问题

- Bug 与功能建议请提 [Issue](https://github.com/jolly326/bjtu_food/issues)，尽量附上复现步骤、运行环境与预期行为

### 项目约定

- 🔴 **文档优先**：改设计先改 `docs/` 对应真源，再动代码；文档与代码不一致时以 **`docs/`** 为准
- 涉及后端字段 / 端点变更时，须刷新端上契约产物（`cd client && npm run gen:api:fresh`）
- UI 改动以各端页面稿为准：`docs/ui/client/菜品首页.md` · `docs/ui/web/*.md`；视觉取值由 `client/src/theme/` 与 `web/src/styles/theme.css` 单点承载
- 数据库变更：**先改 `docs/schema/` 表文档，再直连远程库执行**，两者必须一致

## License

MIT
